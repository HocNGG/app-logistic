# audit-native-first.ps1 — RV-01 chạy trước mỗi verdict. KHÔNG sửa code.
#
# Kiểm tra tự động 12 luật native-first + Odoo 17 idioms (AGENTS.md Luật 1-3).
# Kết quả ghi vào docs/review/audit-native-first.log (append) và in ra console.
#
# Cách dùng:
#   powershell -File infra/scripts/audit-native-first.ps1
#   powershell -File infra/scripts/audit-native-first.ps1 -Repo C:\path\to\repo
#
# Exit code: 0 = không có vi phạm P0/P1 · 1 = có vi phạm (xem log)

param(
    [string]$Repo = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
)

$ErrorActionPreference = 'Stop'
$log = Join-Path $Repo 'docs\review\audit-native-first.log'
New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null

# --- công cụ tìm kiếm: ưu tiên ripgrep, fallback Select-String -----------------
$hasRg = $null -ne (Get-Command rg -ErrorAction SilentlyContinue)
$tool = if ($hasRg) { 'rg' } else { 'Select-String' }

function Find-Hits([string]$pattern, [string[]]$paths, [string]$glob) {
    $existing = $paths | Where-Object { Test-Path (Join-Path $Repo $_) }
    if (-not $existing) { return @() }

    if ($hasRg) {
        $rgArgs = @('--line-number', '--no-heading', '--color', 'never')
        if ($glob) { $rgArgs += @('--glob', $glob) }
        $rgArgs += $pattern
        $rgArgs += ($existing | ForEach-Object { Join-Path $Repo $_ })
        $out = & rg @rgArgs 2>$null
        return @($out)
    }

    # Fallback: duyệt đệ quy bằng Get-ChildItem rồi Select-String
    $results = @()
    foreach ($rel in $existing) {
        $full = Join-Path $Repo $rel
        $files = @()
        if (Test-Path -Path $full -PathType Container) {
            $gciArgs = @{ Path = $full; File = $true; Recurse = $true; ErrorAction = 'SilentlyContinue' }
            if ($glob) { $gciArgs.Filter = $glob }
            $files = @(Get-ChildItem @gciArgs)
        } else {
            $files = @(Get-Item -Path $full)
        }
        foreach ($f in $files) {
            Select-String -Path $f.FullName -Pattern $pattern -ErrorAction SilentlyContinue | ForEach-Object {
                $results += ("{0}:{1}:{2}" -f $_.Path, $_.LineNumber, $_.Line.Trim())
            }
        }
    }
    return @($results)
}

# --- bảng luật ----------------------------------------------------------------
# A1 kiểm riêng (whitelist model). A12 kiểm riêng (secrets).
$rules = @(
    @{ Id='A2';  Pat='\battrs\s*=|\bstates\s*=\s*"';           Paths=@('addons'); Glob='*.xml'; Sev='P0'; Why='attrs/states bị bỏ ở Odoo 17' }
    @{ Id='A2b'; Pat='states\s*=\s*[\{\[]';                   Paths=@('addons'); Glob='*.py';  Sev='P0'; Why='field kwarg states= bị bỏ ở 17' }
    @{ Id='A3';  Pat='quantity_done|qty_done|reserved_uom_qty'; Paths=@('addons'); Glob='*.py';  Sev='P0'; Why='17.0 dùng quantity + picked' }
    @{ Id='A4';  Pat='has_group\(';                            Paths=@('addons'); Glob='*.py';  Sev='P0'; Why='phân quyền phải bằng ir.rule' }
    @{ Id='A5';  Pat='\.sudo\(\)';                             Paths=@('addons'); Glob='*.py';  Sev='P1'; Why='sudo() phải có comment lý do' }
    @{ Id='A6';  Pat='stock\.package(?![.\w])';                Paths=@('addons'); Glob='*.py';  Sev='P0'; Why='17.0 là stock.quant.package' }
    @{ Id='A7';  Pat='<list\b';                                Paths=@('addons'); Glob='*.xml'; Sev='P0'; Why='17.0 dùng <tree>' }
    @{ Id='A8';  Pat='[^_.]read_group\(';                      Paths=@('addons'); Glob='*.py';  Sev='P1'; Why='17.0 dùng _read_group' }
    @{ Id='A9';  Pat='hard_lock_date';                         Paths=@('addons'); Glob='*.py';  Sev='P0'; Why='17.0 dùng fiscalyear_lock_date' }
    @{ Id='A10'; Pat='SavepointCase|models\.Constraint\b|is_storable'; Paths=@('addons'); Glob='*.py'; Sev='P0'; Why='tên của 18/19, không có ở 17' }
    @{ Id='A11'; Pat='\.cr\.commit\(\)|env\.cr\.commit\(\)';   Paths=@('addons'); Glob='*.py';  Sev='P0'; Why='không commit thủ công' }
    @{ Id='A13'; Pat='\bprops\.value\b|this\.state\s*=';       Paths=@('addons'); Glob='*.js';  Sev='P1'; Why='OWL 2: props.record.data[name], useState' }
)

# --- model whitelist (A1) -----------------------------------------------------
# Chỉ những model dưới đây được phép tồn tại. Model khác = P0 (trừ khi có ADR ghi rõ).
# Nguồn: docs/02 §2 (model inventory) + docs/07 (wizard của 6 nhánh ngoại lệ).
$allowedModels = @(
    # lab_core (mixin, không có model nghiệp vụ)
    'lab.flow.mixin',
    # lab_chuyen — bước ⑤⑥⑦⑬⑭
    'lab.chuyen', 'lab.chuyen.line',
    # lab_laixe — bước ②
    'lab.yeu.cau.lay.hang', 'lab.pickup.line',
    # lab_hanhtrinh — bước ⑨⑩⑪
    'lab.hanh.trinh', 'lab.hanh.trinh.line',
    # lab.scan.event — NHẬT KÝ QUÉT TEM (duyệt 2026-11-02, ADR-010).
    # Không trùng native: Odoo không có bảng log quét; chứng từ vẫn là stock.picking.
    'lab.scan.event',
    # cấu hình
    'lab.config.settings',
    # wizard (transient) — nhánh ngoại lệ ②⑤⑥ + bước ⑬
    'lab.giam.tai.wizard', 'lab.chuyen.kho.wizard',
    'lab.gan.khach.wizard', 'lab.lap.debit.wizard',
    # wizard GAN TAI XE tren web (duyet 2026-11-03, P0-1 vong phan vung: CS roi app
    # nen phai gan duoc tai xe ngay tren form Yeu cau lay hang)
    'lab.yeu.cau.lay.hang.gan.tai.xe'
)
# tên model bị cấm tuyệt đối (trùng native)
$bannedModels = @('debit', 'cuoc', 'phi_van_chuyen', 'kien_hang', 'kien', 'lo_hang', 'khoi_luong', 'tai_xe', 'khieu_nai')

# ---------------------------------------------------------------------------
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
"`n===== AUDIT $stamp (tool: $tool) =====" | Add-Content $log -Encoding UTF8

$results = @()
foreach ($r in $rules) {
    $hits = Find-Hits -pattern $r.Pat -paths $r.Paths -glob $r.Glob
    # Bo qua addons/oca/** : do la ma OCA vendor (read-only), khong phai code du an.
    $hits = @($hits | Where-Object { $_ -notmatch '[\\/]oca[\\/]' })
    # Cho phep sudo() co chu thich: them "# sudo-ok: <ly do>" CUNG DONG hoac dong ngay tren.
    if ($r.Id -eq 'A5') {
        $hits = @($hits | Where-Object { $_ -notmatch 'sudo-ok' })
    }
    # A11 (cr.commit): cung co che mien tru CO GHI LY DO, danh cho script seed/tool.
    # Ly do hop le: seed du lieu lon theo lo, commit tung lo de khong giu mot transaction
    # khong lo (rollback se mat toan bo 300+ ban ghi). Code NGHIEP VU khong duoc mien tru.
    if ($r.Id -eq 'A11') {
        $hits = @($hits | Where-Object { $_ -notmatch 'audit-ok' })
    }
    # A13 (OWL 1 `this.state`): `this.state = useState({...})` LA OWL 2 HOP LE — day la
    # property thuong giu reactive state, khong phai OWL 1. Chi tinh vi pham khi gan state
    # KHONG qua useState. (RV-01 xep A13 vao nhom pre-existing / false-positive.)
    if ($r.Id -eq 'A13') {
        $hits = @($hits | Where-Object { $_ -notmatch 'useState\(' })
    }
    $results += [pscustomobject]@{ Id=$r.Id; Sev=$r.Sev; Hits=$hits.Count; Why=$r.Why; Lines=$hits }
}

# A1 — model whitelist
# Loai false-positive: comodel_name= / inverse_name= / related_name= cung khop "_name\s*="
# (rg khong ho tro lookbehind -> loc SAU khi tim, thay vi dung regex lookbehind)
$modelHits = Find-Hits -pattern "_name\s*=\s*['""]([a-z_.]+)['""]" -paths @('addons') -glob '*.py' |
    Where-Object { $_ -notmatch 'comodel_name|inverse_name|related_name' } |
    Where-Object { $_ -notmatch '[\\/]oca[\\/]' }   # bo qua ma OCA vendor (read-only)
$foundModels = @()
foreach ($h in $modelHits) {
    if ($h -match "['""]([a-z_][a-z_.]*)['""]") { $foundModels += $matches[1] }
}
$foundModels = $foundModels | Sort-Object -Unique
$outside = $foundModels | Where-Object { $_ -like 'lab.*' -and $allowedModels -notcontains $_ }
$banned  = $foundModels | Where-Object { $bannedModels -contains $_ }

# A12 — secrets
$secretHits = Find-Hits -pattern "(password|api_key|apikey|token)\s*=\s*['""][^'""]{8,}['""]" -paths @('addons', 'apps') -glob '*.py' |
    Where-Object { $_ -notmatch '[\\/]tests[\\/]' } |   # password trong test = fixture new_test_user, khong phai secret
    Where-Object { $_ -notmatch '[\\/]oca[\\/]' }

# ---------------------------------------------------------------------------
$p0 = 0; $p1 = 0
foreach ($x in $results) {
    if ($x.Hits -gt 0) {
        if ($x.Sev -eq 'P0') { $p0++ } else { $p1++ }
        Add-Content $log ("FAIL {0} [{1}] {2} hit — {3}" -f $x.Id, $x.Sev, $x.Hits, $x.Why) -Encoding UTF8
        $x.Lines | Select-Object -First 10 | ForEach-Object { Add-Content $log "     $_" -Encoding UTF8 }
    } else {
        Add-Content $log ("PASS {0}" -f $x.Id) -Encoding UTF8
    }
}

Add-Content $log '--- models (lab.*) found ---' -Encoding UTF8
if ($foundModels) { $foundModels | ForEach-Object { Add-Content $log "  $_" -Encoding UTF8 } }
else { Add-Content $log '  (chưa có addons/ — bình thường ở P0)' -Encoding UTF8 }

if ($outside) {
    $p0++
    Add-Content $log ("FAIL A1 model ngoài whitelist: {0}" -f ($outside -join ', ')) -Encoding UTF8
}
if ($banned) {
    $p0++
    Add-Content $log ("FAIL A1b model BỊ CẤM (trùng native): {0}" -f ($banned -join ', ')) -Encoding UTF8
}
if ($secretHits.Count -gt 0) {
    $p0++
    Add-Content $log ("FAIL A12 secret hardcode: {0} hit" -f $secretHits.Count) -Encoding UTF8
    $secretHits | Select-Object -First 5 | ForEach-Object { Add-Content $log "     $_" -Encoding UTF8 }
} else { Add-Content $log 'PASS A12' -Encoding UTF8 }

$verdict = if ($p0 -gt 0) { 'BLOCKED (có vi phạm P0)' } elseif ($p1 -gt 0) { 'CHANGES-REQUESTED (chỉ P1)' } else { 'PASS native-first' }
Add-Content $log ("KET LUAN: {0} — {1} P0, {2} P1 — {3}" -f $stamp, $p0, $p1, $verdict) -Encoding UTF8

Write-Host ""
Write-Host "Audit native-first: $p0 P0 · $p1 P1  ->  $verdict" -ForegroundColor $(if ($p0) { 'Red' } elseif ($p1) { 'Yellow' } else { 'Green' })
Write-Host "Log: $log"
if (-not $hasRg) { Write-Host "Ghi chú: chưa có ripgrep (rg), đã dùng Select-String (chậm hơn). Cài: winget install BurntSushi.ripgrep.MSVC" -ForegroundColor Yellow }

exit $(if ($p0 -gt 0 -or $p1 -gt 0) { 1 } else { 0 })
