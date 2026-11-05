<#
.SYNOPSIS
    Vendor (tải về) module OCA nhánh 17.0 vào addons/oca/ — chạy MỘT LẦN khi có mạng.

.DESCRIPTION
    Vì sao phải có script này: shell của agent KHÔNG có mạng ra ngoài
    (mọi lệnh tải đều fail với "schannel: SEC_E_NO_CREDENTIALS"), nên việc tải
    OCA do NGƯỜI DÙNG chạy. Script idempotent: chạy lại nhiều lần không hỏng gì.

    Hai module bắt buộc (AGENTS.md/docs/01 §3.2):

      * base_tier_validation  -> repo OCA/server-ux   (KHÔNG phải server-tools!)
      * helpdesk_mgmt         -> repo OCA/helpdesk

    Cách làm: clone nguyên repo (--depth 1 --branch 17.0) vào addons/oca/_repos/,
    sau đó COPY riêng thư mục module ra addons/oca/<tên module>/.

    Vì sao phải copy ra một cấp? Odoo chỉ quét addons_path thêm MỘT cấp thư mục
    con. Nếu để nguyên addons/oca/server-ux/base_tier_validation thì Odoo KHÔNG
    thấy module (nằm sâu 2 cấp). Nên addons/oca/<module>/ là bố cục bắt buộc.

    addons/oca/ là vendor READ-ONLY (AGENTS.md Luật 10): không ai sửa tay file
    trong đó. Repo gốc nằm ở addons/oca/_repos/ và bị .gitignore.

.PARAMETER Repo
    Gốc repository. Mặc định: thư mục cha của infra/ (tự suy ra từ vị trí script).

.PARAMETER Update
    Đã clone rồi vẫn fetch lại nhánh 17.0 mới nhất (git fetch --depth 1 + reset).

.PARAMETER Force
    Ghi đè module đã có trong addons/oca/ (mặc định: bỏ qua, để giữ idempotent).

.PARAMETER IncludeTms
    Clone thêm OCA/stock-logistics-transport (bộ tms) vào _repos CHỈ ĐỂ ĐỌC
    code cho ADR-003. KHÔNG copy module nào ra addons/oca (không cài tms).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/vendor-oca.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/vendor-oca.ps1 -IncludeTms

.NOTES
    File này phải là UTF-8 CÓ BOM (PowerShell 5.1 cần BOM để đọc đúng tiếng Việt).
#>
[CmdletBinding()]
param(
    [string]$Repo = "",
    [switch]$Update,
    [switch]$Force,
    [switch]$IncludeTms
)

# $PSScriptRoot RONG khi dung trong param() default tren Windows PowerShell 5.1
if ([string]::IsNullOrWhiteSpace($Repo)) { $Repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

# Script gọi công cụ ngoài (git) nên KHÔNG dùng 'Stop' cho lỗi native —
# ta tự kiểm tra $LASTEXITCODE ở từng bước.
$ErrorActionPreference = 'Continue'

$script:SoLoi = 0
$script:SoCanhBao = 0

function Buoc([string]$m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function ThongTin([string]$m) { Write-Host "    $m" }
function Ok([string]$m) { Write-Host "    [OK]   $m" -ForegroundColor Green }
function CanhBao([string]$m) { Write-Host "    [WARN] $m" -ForegroundColor Yellow; $script:SoCanhBao++ }
function Loi([string]$m) { Write-Host "    [FAIL] $m" -ForegroundColor Red; $script:SoLoi++ }

function Invoke-Git([string[]]$GitArgs) {
    $out = & git @GitArgs 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output   = @($out | ForEach-Object { "$_" })
    }
}

function Get-ManifestVersion([string]$ModuleDir) {
    $mf = Join-Path $ModuleDir '__manifest__.py'
    if (-not (Test-Path $mf)) { return $null }
    $text = Get-Content -Path $mf -Raw -Encoding UTF8
    $m = [regex]::Match($text, '["'']version["'']\s*:\s*["'']([^"'']+)["'']')
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Get-ManifestDepends([string]$ModuleDir) {
    $mf = Join-Path $ModuleDir '__manifest__.py'
    if (-not (Test-Path $mf)) { return @() }
    $text = Get-Content -Path $mf -Raw -Encoding UTF8
    $m = [regex]::Match($text, '["'']depends["'']\s*:\s*\[([^\]]*)\]', 'Singleline')
    if (-not $m.Success) { return @() }
    $deps = @()
    foreach ($x in [regex]::Matches($m.Groups[1].Value, '["'']([A-Za-z0-9_]+)["'']')) {
        $deps += $x.Groups[1].Value
    }
    return $deps
}

# ===========================================================================
Buoc 'Kiểm tra môi trường'
$gitCmd = Get-Command git -ErrorAction SilentlyContinue
if (-not $gitCmd) {
    Loi 'Không tìm thấy git trong PATH. Cài Git for Windows rồi chạy lại: https://git-scm.com/download/win'
    Write-Host "`nKẾT LUẬN: FAIL — thiếu git" -ForegroundColor Red
    exit 1
}
ThongTin ("git: " + $gitCmd.Source)

$ocaDir = Join-Path $Repo 'addons\oca'
$cacheDir = Join-Path $ocaDir '_repos'
New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
ThongTin ("addons/oca: " + $ocaDir)

# ===========================================================================
# Danh sách repo + module cần vendor.
# ⚠️ BẪY ĐÃ GHI TRONG docs/01 §3.2: base_tier_validation nằm ở OCA/server-ux,
#    KHÔNG phải OCA/server-tools. Copy nhầm repo -> không có module -> cài fail.
# ===========================================================================
$nguon = @(
    [pscustomobject]@{
        Ten     = 'OCA-server-ux'
        Url     = 'https://github.com/OCA/server-ux.git'
        Modules = @('base_tier_validation')
        GhiChu  = 'base_tier_validation = OCA/server-ux (KHÔNG phải server-tools) — dùng cho ⑥ duyệt đơn vào chuyến và ⑭ khoá Debit'
    },
    [pscustomobject]@{
        Ten     = 'OCA-helpdesk'
        Url     = 'https://github.com/OCA/helpdesk.git'
        Modules = @('helpdesk_mgmt')
        GhiChu  = 'helpdesk_mgmt = nhánh ngoại lệ ⑥ (khiếu nại / trả hàng)'
    }
)

$script:DaVendor = @()

foreach ($src in $nguon) {
    Buoc ("Repo " + $src.Ten + " — " + $src.GhiChu)
    $repoDir = Join-Path $cacheDir $src.Ten

    if (Test-Path (Join-Path $repoDir '.git')) {
        if ($Update) {
            ThongTin ("đã có bản clone -> fetch lại nhánh 17.0: " + $repoDir)
            $r = Invoke-Git @('-C', $repoDir, 'fetch', '--depth', '1', 'origin', '17.0')
            if ($r.ExitCode -ne 0) {
                Loi ("git fetch thất bại: " + $src.Url)
                $r.Output | Select-Object -First 10 | ForEach-Object { ThongTin ("   " + $_) }
            }
            else {
                $r2 = Invoke-Git @('-C', $repoDir, 'reset', '--hard', 'FETCH_HEAD')
                if ($r2.ExitCode -ne 0) { Loi 'git reset --hard FETCH_HEAD thất bại' } else { Ok 'đã cập nhật bản clone' }
            }
        }
        else {
            Ok ("đã có bản clone — bỏ qua (dùng -Update để cập nhật): " + $repoDir)
        }
    }
    else {
        ThongTin ("clone --depth 1 --branch 17.0 " + $src.Url)
        $r = Invoke-Git @('clone', '--depth', '1', '--branch', '17.0', $src.Url, $repoDir)
        if ($r.ExitCode -ne 0) {
            Loi ("clone thất bại: " + $src.Url)
            ThongTin 'Nguyên nhân thường gặp: (a) máy không có mạng ra github.com; (b) proxy/tường lửa; (c) nhánh 17.0 không tồn tại.'
            $r.Output | Select-Object -First 15 | ForEach-Object { ThongTin ("   " + $_) }
            continue
        }
        Ok 'clone xong'
    }

    foreach ($mod in $src.Modules) {
        $from = Join-Path $repoDir $mod
        $to = Join-Path $ocaDir $mod

        if (-not (Test-Path (Join-Path $from '__manifest__.py'))) {
            Loi ("không thấy module '" + $mod + "' trong " + $repoDir + ' (repo/nhánh có thể đã đổi)')
            continue
        }

        if (Test-Path $to) {
            if (-not $Force) {
                Ok ("module " + $mod + " đã có ở addons/oca — bỏ qua (dùng -Force để ghi đè)")
            }
            else {
                ThongTin ("-Force: xoá và copy lại " + $to)
                try {
                    Remove-Item -Path $to -Recurse -Force -ErrorAction Stop
                    Copy-Item -Path $from -Destination $to -Recurse -Force -ErrorAction Stop
                    Ok ("copy lại " + $mod)
                }
                catch {
                    Loi ("copy " + $mod + " thất bại: " + $_.Exception.Message)
                    continue
                }
            }
        }
        else {
            try {
                Copy-Item -Path $from -Destination $to -Recurse -Force -ErrorAction Stop
                Ok ("copy " + $mod + " -> addons/oca/" + $mod)
            }
            catch {
                Loi ("copy " + $mod + " thất bại: " + $_.Exception.Message)
                continue
            }
        }

        # --- Kiểm tra kết quả: __manifest__.py + version 17.0.x ---
        if (-not (Test-Path (Join-Path $to '__manifest__.py'))) {
            Loi ($mod + ': SAU KHI COPY vẫn không có __manifest__.py — vendor hỏng')
            continue
        }
        $ver = Get-ManifestVersion $to
        if (-not $ver) {
            CanhBao ($mod + ': không đọc được "version" trong __manifest__.py')
        }
        elseif ($ver -notlike '17.0.*') {
            Loi ($mod + ': version "' + $ver + '" KHÔNG phải nhánh 17.0 — sai nhánh, không cài được trên Odoo 17')
        }
        else {
            Ok ($mod + ': version ' + $ver)
        }

        # --- Cảnh báo nếu module OCA depends module khác CÙNG repo mà chưa vendor ---
        foreach ($dep in (Get-ManifestDepends $to)) {
            if ((Test-Path (Join-Path $repoDir $dep)) -and -not (Test-Path (Join-Path $ocaDir $dep))) {
                CanhBao ($mod + ' depends "' + $dep + '" — module này nằm cùng repo ' + $src.Ten + ' nhưng CHƯA được vendor vào addons/oca. Nếu cài báo thiếu dependency, thêm "' + $dep + '" vào $nguon rồi chạy lại -Force.')
            }
        }

        $script:DaVendor += [pscustomobject]@{ Module = $mod; Repo = $src.Ten; Version = $ver }
    }
}

# ===========================================================================
if ($IncludeTms) {
    Buoc 'Clone OCA/stock-logistics-transport (bộ tms) — CHỈ ĐỂ ĐỌC CODE cho ADR-003'
    $tmsDir = Join-Path $cacheDir 'OCA-stock-logistics-transport'
    if (Test-Path (Join-Path $tmsDir '.git')) {
        Ok ('đã có bản clone (chỉ để đọc): ' + $tmsDir)
    }
    else {
        $r = Invoke-Git @('clone', '--depth', '1', '--branch', '17.0',
            'https://github.com/OCA/stock-logistics-transport.git', $tmsDir)
        if ($r.ExitCode -ne 0) { Loi 'clone OCA/stock-logistics-transport thất bại' }
        else { Ok ('clone xong: ' + $tmsDir) }
    }
    if (Test-Path $tmsDir) {
        ThongTin 'Các file cần đọc để chốt ADR-003 (xem docs/adr/ADR-003-evidence-needed.md):'
        foreach ($p in @(
                'tms\__manifest__.py',
                'tms\models\tms_order.py',
                'tms\models\tms_route.py',
                'tms\models\tms_driver.py',
                'tms\models\tms_stage.py'
            )) {
            $full = Join-Path $tmsDir $p
            if (Test-Path $full) { ThongTin ('   có   ' + $full) } else { ThongTin ('   KHÔNG ' + $full) }
        }
        ThongTin '⚠️ KHÔNG copy tms ra addons/oca — script này chỉ tải về để đọc.'
    }
}

# ===========================================================================
Buoc 'Kết quả vendor'
$timThay = @()
foreach ($d in (Get-ChildItem -Path $ocaDir -Directory -ErrorAction SilentlyContinue)) {
    if ($d.Name -eq '_repos') { continue }
    if ($d.Name -eq '__pycache__') { continue }
    $ver = Get-ManifestVersion $d.FullName
    $timThay += [pscustomobject]@{ Module = $d.Name; Version = $ver }
}
if ($timThay.Count -eq 0) {
    CanhBao 'addons/oca chưa có module nào (vendor chưa chạy được hoặc chưa chạy)'
}
else {
    ThongTin 'Module OCA hiện có trong addons/oca:'
    $timThay | ForEach-Object { ThongTin ("   - " + $_.Module + "  (" + $_.Version + ")") }
}

foreach ($m in @('base_tier_validation', 'helpdesk_mgmt')) {
    if (-not (Test-Path (Join-Path $ocaDir ($m + '\__manifest__.py')))) {
        CanhBao ('THIẾU ' + $m + ' — lab_chuyen (depends base_tier_validation) và nhánh ⑥ (helpdesk_mgmt) chưa cài được')
    }
}

Write-Host ''
ThongTin 'Nhắc lại: addons/oca là VENDOR READ-ONLY — không sửa tay (AGENTS.md Luật 10).'
ThongTin 'Kiểm tra tĩnh sau khi vendor:  powershell -File infra/scripts/check.ps1'

Write-Host '' ('-' * 60)
if ($script:SoLoi -gt 0) {
    Write-Host ("KẾT LUẬN: FAIL — {0} lỗi, {1} cảnh báo" -f $script:SoLoi, $script:SoCanhBao) -ForegroundColor Red
    exit 1
}
Write-Host ("KẾT LUẬN: PASS — {0} cảnh báo" -f $script:SoCanhBao) -ForegroundColor Green
exit 0
