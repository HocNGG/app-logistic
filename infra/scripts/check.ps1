<#
.SYNOPSIS
    Kiểm tra TĨNH offline cho addons/lab_* — không cần Docker, không cần Odoo, không cần DB.

.DESCRIPTION
    Vì sao có script này: môi trường agent không có mạng và Docker Desktop chưa bật,
    nên KHÔNG thể cài module hay chạy odoo-bin. Script này là bằng chứng tối thiểu
    chứng minh code đúng cú pháp, XML hợp lệ và phân quyền nhất quán — trước khi
    người dùng bật Docker để cài thật.

    Sáu mục kiểm (đúng P0-06):

      [1] Cú pháp Python      : python -m py_compile cho MỌI file .py trong addons/lab_*
      [2] XML well-formed     : parse bằng System.Xml (.NET), kèm kiểm root <odoo>
      [3] ir.model.access.csv : đúng 8 cột (4 cột id + 4 perm), giá trị 0/1,
                                và đối chiếu xmlid model/nhóm có thật trong code
      [4] audit-native-first  : gọi infra/scripts/audit-native-first.ps1 (RV-01 dùng)
      [5] Manifest & __init__ : đủ key, version 17.0.x.y.z, mọi model/test đều được import
      [6] Encoding            : .ps1 phải UTF-8 CÓ BOM · CSV phải UTF-8 KHÔNG BOM
      [7] YAML (bonus)        : docker-compose.yml không có TAB (YAML cấm TAB thụt lề)

    KHÔNG kiểm được ở đây (phải chạy thật khi có Docker):
      * module cài được / upgrade được;
      * test Odoo xanh (--test-tags);
      * ir.rule có lọc đúng record hay không.

.PARAMETER Repo
    Gốc repository. Mặc định tự suy ra từ vị trí script (infra/scripts -> repo).

.PARAMETER SkipAudit
    Bỏ qua mục [4] (dùng khi chỉ muốn kiểm nhanh phần khác).

.PARAMETER ShowDetails
    In đầy đủ chi tiết của mọi mục (mặc định chỉ in chi tiết khi FAIL/WARN).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/check.ps1

.EXAMPLE
    powershell -File infra/scripts/check.ps1 -SkipAudit -ShowDetails

.NOTES
    Exit code: 0 = tất cả PASS (có WARN vẫn 0) · 1 = có mục FAIL.
    File này phải là UTF-8 CÓ BOM (PowerShell 5.1 cần BOM để đọc đúng tiếng Việt).
#>
param(
    [string]$Repo = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),
    [switch]$SkipAudit,
    [switch]$ShowDetails
)

# Script gọi công cụ ngoài (python, audit) nên tự kiểm $LASTEXITCODE thay vì 'Stop'.
$ErrorActionPreference = 'Continue'

$script:MaxDetail = 20

function Buoc([string]$m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function ThongTin([string]$m) { Write-Host ("    " + $m) -ForegroundColor Gray }

function New-KetQua {
    param(
        [string]$Ten,
        [ValidateSet('PASS', 'WARN', 'FAIL')][string]$TrangThai,
        [string]$TomTat,
        [string[]]$ChiTiet = @()
    )
    return [pscustomobject]@{
        Ten       = $Ten
        TrangThai = $TrangThai
        TomTat    = $TomTat
        ChiTiet   = @($ChiTiet)
    }
}

function Show-KetQua($kq, [bool]$duChiTiet) {
    $mau = switch ($kq.TrangThai) {
        'PASS' { 'Green' }
        'WARN' { 'Yellow' }
        'FAIL' { 'Red' }
    }
    Write-Host ("    [{0}] {1}" -f $kq.TrangThai, $kq.Ten) -ForegroundColor $mau
    if ($kq.TomTat) { Write-Host ("          " + $kq.TomTat) }
    if (($duChiTiet -or $kq.TrangThai -ne 'PASS') -and $kq.ChiTiet.Count -gt 0) {
        $kq.ChiTiet | Select-Object -First $script:MaxDetail | ForEach-Object {
            Write-Host ("          - " + $_) -ForegroundColor $(if ($kq.TrangThai -eq 'FAIL') { 'Red' } else { 'Yellow' })
        }
        if ($kq.ChiTiet.Count -gt $script:MaxDetail) {
            Write-Host ("          ... còn " + ($kq.ChiTiet.Count - $script:MaxDetail) + " dòng nữa") -ForegroundColor Yellow
        }
    }
}

function Get-FileTrongAddons([string]$Loc) {
    $goc = Join-Path $Repo 'addons'
    if (-not (Test-Path $goc)) { return @() }
    return @(
        Get-ChildItem -Path $goc -Recurse -File -Filter $Loc -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\oca\\' -and $_.FullName -notmatch '\\__pycache__\\' }
    )
}

# ===========================================================================
# [1] Cú pháp Python
# ===========================================================================
function Test-CuPhapPython {
    $files = @(Get-FileTrongAddons '*.py')
    if ($files.Count -eq 0) {
        return New-KetQua 'Cú pháp Python (py_compile)' 'FAIL' 'Không tìm thấy file .py nào trong addons/ (chưa có module?)'
    }

    $python = $null
    foreach ($ten in @('python', 'py')) {
        $cmd = Get-Command $ten -ErrorAction SilentlyContinue
        if ($cmd) { $python = $cmd.Source; break }
    }
    if (-not $python) {
        return New-KetQua 'Cú pháp Python (py_compile)' 'FAIL' 'Không tìm thấy python/py trong PATH — cài Python 3.10+'
    }

    # Không để py_compile rải __pycache__ vào repo.
    if (-not $env:PYTHONPYCACHEPREFIX) {
        $tmp = if ($env:TEMP) { $env:TEMP } else { '.' }
        $env:PYTHONPYCACHEPREFIX = Join-Path $tmp 'applogictics-pycache'
    }

    $out = & $python -m py_compile @($files.FullName) 2>&1
    $code = $LASTEXITCODE
    $chiTiet = @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })

    if ($code -eq 0) {
        return New-KetQua 'Cú pháp Python (py_compile)' 'PASS' ("{0} file .py biên dịch OK" -f $files.Count)
    }
    return New-KetQua 'Cú pháp Python (py_compile)' 'FAIL' ("python -m py_compile exit {0} — {1} file" -f $code, $files.Count) $chiTiet
}

# ===========================================================================
# [2] XML well-formed + root <odoo>
# ===========================================================================
function Test-Xml {
    $files = @(Get-FileTrongAddons '*.xml')
    if ($files.Count -eq 0) {
        return New-KetQua 'XML hợp lệ' 'FAIL' 'Không tìm thấy file .xml nào trong addons/'
    }

    $loi = @()
    $saiRoot = @()
    foreach ($f in $files) {
        $hien = $f.FullName.Replace($Repo + '\', '')
        try {
            $doc = New-Object System.Xml.XmlDocument
            try { $doc.XmlResolver = $null } catch { }
            $doc.Load($f.FullName)
        }
        catch {
            $loi += ("{0}: {1}" -f $hien, $_.Exception.Message)
            continue
        }
        # File dữ liệu Odoo (data/, security/) BẮT BUỘC root <odoo>.
        $laFileOdooData = ($f.DirectoryName -match '\\security$' -or $f.DirectoryName -match '\\data$')
        if ($laFileOdooData -and $doc.DocumentElement.Name -ne 'odoo') {
            $saiRoot += ("{0}: root là <{1}> — Odoo yêu cầu <odoo>" -f $hien, $doc.DocumentElement.Name)
        }
    }

    $chiTiet = @($loi + $saiRoot)
    if ($chiTiet.Count -eq 0) {
        return New-KetQua 'XML hợp lệ (parse + root <odoo>)' 'PASS' ("{0} file .xml OK" -f $files.Count)
    }
    return New-KetQua 'XML hợp lệ (parse + root <odoo>)' 'FAIL' ("{0} lỗi / {1} file" -f $chiTiet.Count, $files.Count) $chiTiet
}

# ===========================================================================
# [3] ir.model.access.csv + đối chiếu xmlid model/nhóm
# ===========================================================================
function Get-ModelDaKhaiBao {
    # (?<![A-Za-z0-9_]) : KHÔNG khớp đuôi của comodel_name / inverse_name /
    # related_name — nếu thiếu lookbehind này, bộ kiểm sẽ "phát hiện" cả
    # res.partner, sale.order, ir.attachment… như model tự khai báo.
    $mau = '(?<![A-Za-z0-9_])_name\s*=\s*[''""]([A-Za-z0-9_.]+)[''""]'
    $ketQua = @{}
    foreach ($f in @(Get-FileTrongAddons '*.py')) {
        $text = Get-Content -Path $f.FullName -Raw -Encoding UTF8
        $laAbstract = ($text -match 'models\.AbstractModel')
        $module = ($f.FullName -replace '.*\\addons\\([^\\]+)\\.*', '$1')
        foreach ($m in [regex]::Matches($text, $mau)) {
            $ketQua[$m.Groups[1].Value] = [pscustomobject]@{
                File     = $f.Name
                Module   = $module
                Abstract = $laAbstract
            }
        }
    }
    return $ketQua
}

function Get-NhomDaKhaiBao {
    $ketQua = @()
    $f = Join-Path $Repo 'addons\lab_core\security\lab_core_groups.xml'
    if (-not (Test-Path $f)) { return @() }
    try {
        $doc = New-Object System.Xml.XmlDocument
        $doc.Load($f)
        foreach ($r in @($doc.DocumentElement.ChildNodes)) {
            if ($r.NodeType -eq 'Element' -and $r.GetAttribute('model') -eq 'res.groups') {
                $ketQua += $r.GetAttribute('id')
            }
        }
    }
    catch {
        return @()
    }
    return $ketQua
}

function Test-AclCsv {
    $files = @(Get-FileTrongAddons 'ir.model.access.csv')
    if ($files.Count -eq 0) {
        return New-KetQua 'ir.model.access.csv' 'FAIL' 'Không có file ir.model.access.csv nào — Luật 3 yêu cầu mỗi model mới phải có ACL'
    }

    $cotChuan = @('id', 'name', 'model_id:id', 'group_id:id', 'perm_read', 'perm_write', 'perm_create', 'perm_unlink')
    $models = Get-ModelDaKhaiBao
    $nhoms = Get-NhomDaKhaiBao

    # Odoo đặt xmlid của ir.model = 'model_' + tên model (thay '.' bằng '_').
    # Chiều NGƯỢC LẠI không suy được chắc chắn (model_lab_hanh_trinh có thể là
    # lab.hanh.trinh hoặc lab.hanhtrinh) -> so sánh bằng ánh xạ XUÔI.
    $xmlidTheoModel = @{}
    foreach ($ten in $models.Keys) {
        $xmlidTheoModel['model_' + $ten.Replace('.', '_')] = $ten
    }

    $loi = @()
    $canhBao = @()
    $tongDong = 0
    $modelCoAcl = @{}
    $idDaGap = @{}

    foreach ($f in $files) {
        $hien = $f.FullName.Replace($Repo + '\', '')
        $dong = @(Get-Content -Path $f.FullName -Encoding UTF8)
        if ($dong.Count -eq 0) { $loi += ("{0}: file rỗng" -f $hien); continue }

        $cot = @($dong[0].Split(',') | ForEach-Object { $_.Trim() })
        $saiCot = $false
        if ($cot.Count -ne $cotChuan.Count) {
            $loi += ("{0}: header có {1} cột, phải đúng {2} cột ({3})" -f $hien, $cot.Count, $cotChuan.Count, ($cotChuan -join ','))
            $saiCot = $true
        }
        else {
            for ($i = 0; $i -lt $cotChuan.Count; $i++) {
                if ($cot[$i] -ne $cotChuan[$i]) {
                    $loi += ("{0}: cột {1} là '{2}', phải là '{3}'" -f $hien, ($i + 1), $cot[$i], $cotChuan[$i])
                    $saiCot = $true
                }
            }
        }
        if ($saiCot) { continue }

        if ($dong.Count -eq 1) {
            $canhBao += ("{0}: CHỈ có header, 0 dòng ACL (hợp lệ nếu module chưa có model lưu DB — xem README module)" -f $hien)
            continue
        }

        $rows = @(Import-Csv -Path $f.FullName -Encoding UTF8)
        foreach ($r in $rows) {
            $tongDong++
            $id = "$($r.id)".Trim()
            if ([string]::IsNullOrWhiteSpace($id)) { $loi += ("{0}: có dòng ACL thiếu id" -f $hien); continue }
            if ($idDaGap.ContainsKey($id)) { $loi += ("{0}: id '{1}' bị trùng (đã có ở {2})" -f $hien, $id, $idDaGap[$id]) }
            else { $idDaGap[$id] = $hien }

            $modelXmlid = "$($r.'model_id:id')".Trim()
            # Hai dạng HỢP LỆ trong Odoo 17:
            #   1) model_<tên>            — model do CHÍNH dự án khai (lab_*)
            #   2) <module>.model_<tên>   — model NATIVE của module khác, ví dụ
            #      base.model_res_partner, sale.model_sale_order.
            # Gate cũ chỉ nhận dạng (1) ⇒ báo FAIL oan cho các dòng ACL mở quyền trên
            # model native (đã chạy thật: cs01 đọc được sale.order, menu Khách hàng chạy).
            # Sửa 2026-11-03 sau review RV-01 (P2-10).
            $modelXmlidHopLe = $modelXmlid -match '^(?:[a-z0-9_]+\.)?model_[A-Za-z0-9_]+$'
            if (-not $modelXmlidHopLe) {
                $loi += ("{0}: {1} — model_id:id phải dạng 'model_<tên>' hoặc '<module>.model_<tên>'" -f $hien, $id)
            }
            else {
                $modelCoAcl[$modelXmlid] = $true
                # Chỉ đối chiếu ngược với model của dự án (model_lab*), KHÔNG đối chiếu
                # model native của module khác (danh sách model chỉ quét addons/lab_*).
                if ($modelXmlid -match '^model_lab' -and -not $xmlidTheoModel.ContainsKey($modelXmlid)) {
                    $moduleCuaCsv = ($f.FullName -replace '.*\\addons\\([^\\]+)\\.*', '$1')
                    $goiY = @($models.Keys |
                            Where-Object { $models[$_].Module -eq $moduleCuaCsv } |
                            ForEach-Object { 'model_' + $_.Replace('.', '_') })
                    $them = if ($goiY.Count -gt 0) { ' — xmlid đúng của module ' + $moduleCuaCsv + ': ' + ($goiY -join ', ') } else { '' }
                    $loi += ("{0}: {1} — không có model nào trong addons/lab_* có xmlid '{2}'{3}" -f $hien, $id, $modelXmlid, $them)
                }
            }

            $nhomXmlid = "$($r.'group_id:id')".Trim()
            if ([string]::IsNullOrWhiteSpace($nhomXmlid)) {
                $canhBao += ("{0}: {1} — group_id rỗng = ACL cho MỌI user (global); chỉ dùng khi thật cần" -f $hien, $id)
            }
            elseif ($nhomXmlid -like 'lab_core.*') {
                $tenNhom = $nhomXmlid.Substring('lab_core.'.Length)
                if ($nhoms -notcontains $tenNhom) {
                    $loi += ("{0}: {1} — nhóm '{2}' không có trong lab_core/security/lab_core_groups.xml" -f $hien, $id, $nhomXmlid)
                }
            }

            foreach ($perm in @('perm_read', 'perm_write', 'perm_create', 'perm_unlink')) {
                $gt = "$($r.$perm)".Trim()
                if ($gt -ne '0' -and $gt -ne '1' -and $gt -ne '') {
                    $loi += ("{0}: {1} — {2} = '{3}' (phải là 0/1)" -f $hien, $id, $perm, $gt)
                }
            }
        }
    }

    # Mỗi model cụ thể (không trừu tượng) của lab_* phải có ít nhất một dòng ACL.
    foreach ($tenModel in $models.Keys) {
        if ($tenModel -notlike 'lab.*') { continue }
        if ($models[$tenModel].Abstract) { continue }
        $xmlid = 'model_' + $tenModel.Replace('.', '_')
        if (-not $modelCoAcl.ContainsKey($xmlid)) {
            $loi += ("model '{0}' ({1}) KHÔNG có dòng ACL nào — thiếu = AccessError khi chạy" -f $tenModel, $models[$tenModel].File)
        }
    }

    $tomTat = ("{0} file, {1} dòng ACL, {2} model lab.* (đã khai báo)" -f $files.Count, $tongDong, $models.Count)
    $chiTiet = @($loi + $canhBao)
    if ($loi.Count -gt 0) { return New-KetQua 'ir.model.access.csv (8 cột + xmlid)' 'FAIL' $tomTat $chiTiet }
    if ($canhBao.Count -gt 0) { return New-KetQua 'ir.model.access.csv (8 cột + xmlid)' 'WARN' $tomTat $chiTiet }
    return New-KetQua 'ir.model.access.csv (8 cột + xmlid)' 'PASS' $tomTat
}

# ===========================================================================
# [4] audit-native-first.ps1 (của RV-01)
# ===========================================================================
function Test-AuditNativeFirst {
    $audit = Join-Path $Repo 'infra\scripts\audit-native-first.ps1'
    if (-not (Test-Path $audit)) {
        return New-KetQua 'audit-native-first.ps1' 'FAIL' ('Không thấy ' + $audit)
    }
    if ($SkipAudit) {
        return New-KetQua 'audit-native-first.ps1' 'WARN' 'Đã bỏ qua vì có -SkipAudit'
    }

    ThongTin '(đang chạy audit-native-first.ps1 — log: docs/review/audit-native-first.log)'
    $maThoat = 0
    try {
        & $audit -Repo $Repo
        $maThoat = $LASTEXITCODE
    }
    catch {
        return New-KetQua 'audit-native-first.ps1' 'FAIL' ('audit ném lỗi: ' + $_.Exception.Message)
    }

    if ($maThoat -eq 0) {
        return New-KetQua 'audit-native-first.ps1' 'PASS' 'Exit 0 — không có vi phạm P0/P1'
    }
    return New-KetQua 'audit-native-first.ps1' 'FAIL' ("Exit {0} — xem docs/review/audit-native-first.log" -f $maThoat)
}

# ===========================================================================
# [5] __manifest__.py + __init__.py (model/test phải được import)
# ===========================================================================
function Test-ManifestVaInit {
    $gocAddons = Join-Path $Repo 'addons'
    $modules = @(Get-ChildItem -Path $gocAddons -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'lab_*' } | Sort-Object Name)
    if ($modules.Count -eq 0) {
        return New-KetQua '__manifest__.py & __init__.py' 'FAIL' 'Không có module lab_* nào trong addons/'
    }

    $loi = @()
    $canhBao = @()
    $keyBatBuoc = @('name', 'version', 'category', 'summary', 'depends', 'data', 'license', 'installable')
    $licenseChoPhep = @('LGPL-3', 'AGPL-3', 'GPL-3', 'OPL-1', 'Other OSI approved licence', 'LGPL-3 or any later version')

    foreach ($mod in $modules) {
        $mf = Join-Path $mod.FullName '__manifest__.py'
        if (-not (Test-Path $mf)) {
            $loi += ($mod.Name + ': thiếu __manifest__.py')
            continue
        }
        if (-not (Test-Path (Join-Path $mod.FullName '__init__.py'))) {
            $loi += ($mod.Name + ': thiếu __init__.py (model sẽ "biến mất" mà không báo lỗi)')
        }
        $text = Get-Content -Path $mf -Raw -Encoding UTF8

        foreach ($key in $keyBatBuoc) {
            if ($text -notmatch "['""]$key['""]\s*:") {
                $loi += ($mod.Name + ': __manifest__.py thiếu key "' + $key + '"')
            }
        }

        $mVer = [regex]::Match($text, '["'']version["'']\s*:\s*["'']([^"'']+)["'']')
        if (-not $mVer.Success) { $loi += ($mod.Name + ': không đọc được version') }
        elseif ($mVer.Groups[1].Value -notmatch '^17\.0\.\d+\.\d+\.\d+$') {
            $loi += ($mod.Name + ': version "' + $mVer.Groups[1].Value + '" không đúng dạng 17.0.x.y.z')
        }

        $mLic = [regex]::Match($text, '["'']license["'']\s*:\s*["'']([^"'']+)["'']')
        if ($mLic.Success -and ($licenseChoPhep -notcontains $mLic.Groups[1].Value)) {
            $canhBao += ($mod.Name + ': license "' + $mLic.Groups[1].Value + '" không thuộc danh sách thường dùng')
        }

        # depends: module lab_* phải tồn tại tại chỗ
        $mDep = [regex]::Match($text, '["'']depends["'']\s*:\s*\[([^\]]*)\]', 'Singleline')
        if ($mDep.Success) {
            foreach ($d in [regex]::Matches($mDep.Groups[1].Value, '["'']([A-Za-z0-9_]+)["'']')) {
                $dep = $d.Groups[1].Value
                if ($dep -like 'lab_*' -and -not (Test-Path (Join-Path $gocAddons $dep))) {
                    $loi += ($mod.Name + ': depends "' + $dep + '" nhưng addons/' + $dep + ' không tồn tại')
                }
            }
        }

        # models/__init__.py và tests/__init__.py phải import mọi file (lỗi kinh điển)
        foreach ($thuMuc in @('models', 'tests')) {
            $dir = Join-Path $mod.FullName $thuMuc
            if (-not (Test-Path $dir)) { continue }
            $initFile = Join-Path $dir '__init__.py'
            if (-not (Test-Path $initFile)) {
                $loi += ($mod.Name + '/' + $thuMuc + ': thiếu __init__.py (không chạy/không nạp gì cả)')
                continue
            }
            $initText = Get-Content -Path $initFile -Raw -Encoding UTF8
            foreach ($py in @(Get-ChildItem -Path $dir -File -Filter '*.py')) {
                if ($py.Name -eq '__init__.py') { continue }
                $ten = [System.IO.Path]::GetFileNameWithoutExtension($py.Name)
                if ($initText -notmatch ("from\s+\.\s+import\s+" + [regex]::Escape($ten) + "\b")) {
                    $loi += ($mod.Name + '/' + $thuMuc + '/__init__.py: thiếu "from . import ' + $ten + '"')
                }
            }
        }
    }

    $tomTat = ("{0} module lab_*" -f $modules.Count)
    $chiTiet = @($loi + $canhBao)
    if ($loi.Count -gt 0) { return New-KetQua '__manifest__.py & __init__.py' 'FAIL' $tomTat $chiTiet }
    if ($canhBao.Count -gt 0) { return New-KetQua '__manifest__.py & __init__.py' 'WARN' $tomTat $chiTiet }
    return New-KetQua '__manifest__.py & __init__.py' 'PASS' $tomTat
}

# ===========================================================================
# [6] Encoding: .ps1 PHẢI có BOM, .csv KHÔNG được có BOM
# ===========================================================================
function Test-Encoding {
    $loi = @()
    $canhBao = @()

    $ps1 = @(Get-ChildItem -Path (Join-Path $Repo 'infra\scripts') -Recurse -File -Filter '*.ps1' -ErrorAction SilentlyContinue)
    foreach ($f in $ps1) {
        $hien = $f.FullName.Replace($Repo + '\', '')
        $b = [System.IO.File]::ReadAllBytes($f.FullName)
        if ($b.Length -lt 3 -or $b[0] -ne 0xEF -or $b[1] -ne 0xBB -or $b[2] -ne 0xBF) {
            $loi += ($hien + ': thiếu UTF-8 BOM — PowerShell 5.1 sẽ đọc sai tiếng Việt')
        }
    }

    foreach ($f in @(Get-FileTrongAddons 'ir.model.access.csv')) {
        $hien = $f.FullName.Replace($Repo + '\', '')
        $b = [System.IO.File]::ReadAllBytes($f.FullName)
        if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) {
            $loi += ($hien + ': CSV CÓ BOM — Odoo đọc header thành ''\ufeffid'' và cài đặt lỗi')
        }
    }

    $tong = $ps1.Count + (@(Get-FileTrongAddons 'ir.model.access.csv')).Count
    $chiTiet = @($loi + $canhBao)
    if ($loi.Count -gt 0) { return New-KetQua 'Encoding (.ps1 BOM / .csv không BOM)' 'FAIL' ("{0} file đã kiểm" -f $tong) $chiTiet }
    return New-KetQua 'Encoding (.ps1 BOM / .csv không BOM)' 'PASS' ("{0} file OK ({1} file .ps1, phần còn lại là CSV)" -f $tong, $ps1.Count)
}

# ===========================================================================
# [7] YAML (bonus): TAB là lỗi YAML
# ===========================================================================
function Test-YamlTab {
    $files = @()
    $infra = Join-Path $Repo 'infra'
    if (Test-Path $infra) {
        $files = @(Get-ChildItem -Path $infra -File -Include '*.yml', '*.yaml' -ErrorAction SilentlyContinue)
        $files += @(Get-ChildItem -Path $infra -File -Filter '*.yml' -ErrorAction SilentlyContinue)
        $files = @($files | Sort-Object FullName -Unique)
    }
    if ($files.Count -eq 0) {
        return New-KetQua 'YAML không có TAB (kiểm tra cơ bản)' 'WARN' 'Không thấy file .yml nào trong infra/'
    }

    $loi = @()
    foreach ($f in $files) {
        $hien = $f.FullName.Replace($Repo + '\', '')
        $soDong = 0
        foreach ($line in (Get-Content -Path $f.FullName -Encoding UTF8)) {
            $soDong++
            if ($line -match '^\s*\t' -or $line -match '\t\s*$') {
                $loi += ("{0}:{1}: có TAB (YAML cấm TAB trong thụt lề)" -f $hien, $soDong)
            }
        }
    }
    if ($loi.Count -gt 0) {
        return New-KetQua 'YAML không có TAB (kiểm tra cơ bản)' 'FAIL' ("{0} file" -f $files.Count) $loi
    }
    return New-KetQua 'YAML không có TAB (kiểm tra cơ bản)' 'PASS' ("{0} file .yml không có TAB — kiểm tra đầy đủ cần: docker compose config" -f $files.Count)
}

# ===========================================================================
# MAIN
# ===========================================================================
Write-Host ''
Write-Host ('=' * 70) -ForegroundColor Cyan
Write-Host ' CHECK TĨNH — AppLogictics (offline: không cần Docker, Odoo hay DB)' -ForegroundColor Cyan
Write-Host ('=' * 70) -ForegroundColor Cyan
Write-Host (' Repo: ' + $Repo)
Write-Host (' Thời điểm: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))

$ds = New-Object System.Collections.ArrayList

function Test-ViewManifest {
    # Mục đích: FE-01 chỉ ra rằng XML hợp lệ + audit xanh KHÔNG chứng minh view được nạp.
    # View chỉ "sống" khi đường dẫn nằm trong 'data' của __manifest__.py.
    # Kiểm 3 thứ:
    #   A) mọi file trong <module>/views/*.xml phải được khai trong data  -> thiếu = FAIL (menu không hiện)
    #   B) mọi entry data kết thúc .xml/.csv phải tồn tại trên đĩa        -> thiếu = FAIL (upgrade lỗi)
    #   C) menuitems.xml (nếu có) nên khai CUỐI trong data                -> sai thứ tự = WARN
    $gocAddons = Join-Path $Repo 'addons'
    $modules = @(Get-ChildItem -Path $gocAddons -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'lab_*' } | Sort-Object Name)
    if ($modules.Count -eq 0) {
        return New-KetQua 'View khai trong manifest' 'FAIL' 'Không có module lab_* nào trong addons/'
    }

    $loi = @(); $canhBao = @(); $soView = 0; $soKhai = 0

    foreach ($mod in $modules) {
        $mf = Join-Path $mod.FullName '__manifest__.py'
        if (-not (Test-Path $mf)) { continue }
        $text = Get-Content -Raw -Encoding UTF8 $mf

        # Tách khối 'data' để không nhầm với 'demo' hay 'assets'
        $khoiData = ''
        $mData = [regex]::Match($text, "'data'\s*:\s*\[(.*?)\]", 'Singleline')
        if (-not $mData.Success) { $mData = [regex]::Match($text, '"data"\s*:\s*\[(.*?)\]', 'Singleline') }
        if ($mData.Success) { $khoiData = $mData.Groups[1].Value }

        $khaiBao = @([regex]::Matches($khoiData, "['""]([^'""]+\.(?:xml|csv))['""]") |
                ForEach-Object { $_.Groups[1].Value })
        $soKhai += $khaiBao.Count

        # A) view có trên đĩa nhưng không khai
        $thuMucView = Join-Path $mod.FullName 'views'
        if (Test-Path $thuMucView) {
            foreach ($f in (Get-ChildItem $thuMucView -Filter '*.xml' -File -ErrorAction SilentlyContinue)) {
                $soView++
                $rel = 'views/' + $f.Name
                if ($khaiBao -notcontains $rel) {
                    $loi += ($mod.Name + ': ' + $rel + ' có trên đĩa nhưng KHÔNG khai trong data -> menu/view sẽ không nạp')
                }
            }
        }

        # B) khai nhưng không có file
        foreach ($k in $khaiBao) {
            $full = Join-Path $mod.FullName ($k -replace '/', [IO.Path]::DirectorySeparatorChar)
            if (-not (Test-Path $full)) {
                $loi += ($mod.Name + ': data khai "' + $k + '" nhưng KHÔNG có file -> upgrade sẽ lỗi')
            }
        }

        # C) menuitems nên ở cuối
        $iMenu = [array]::IndexOf(($khaiBao -join '|').Split('|'), 'views/menuitems.xml')
        if ($iMenu -ge 0 -and $iMenu -lt ($khaiBao.Count - 1)) {
            $canhBao += ($mod.Name + ': menuitems.xml nên khai CUỐI trong data (hiện ở vị trí ' + ($iMenu + 1) + '/' + $khaiBao.Count + ')')
        }
    }

    $tomTat = ('{0} file view / {1} entry data' -f $soView, $soKhai)
    if ($loi.Count -gt 0) {
        return New-KetQua 'View khai trong manifest' 'FAIL' (($loi + @($tomTat)) -join ' | ')
    }
    if ($canhBao.Count -gt 0) {
        return New-KetQua 'View khai trong manifest' 'WARN' (($canhBao + @($tomTat)) -join ' | ')
    }
    if ($soView -eq 0) {
        return New-KetQua 'View khai trong manifest' 'WARN' ('chưa có file view nào (đúng nếu Wave B chưa xong) — ' + $tomTat)
    }
    return New-KetQua 'View khai trong manifest' 'PASS' ('mọi view đều được khai và tồn tại — ' + $tomTat)
}

Buoc '[1/8] Cú pháp Python'
$kq = Test-CuPhapPython; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails

Buoc '[2/8] XML well-formed'
$kq = Test-Xml; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails

Buoc '[3/8] ir.model.access.csv'
$kq = Test-AclCsv; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails

Buoc '[4/8] audit-native-first.ps1'
$kq = Test-AuditNativeFirst; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails

Buoc '[5/8] __manifest__.py & __init__.py'
$kq = Test-ManifestVaInit; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails

Buoc '[6/8] Encoding'
$kq = Test-Encoding; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails

Buoc '[7/8] YAML (bonus)'
$kq = Test-YamlTab; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails


Buoc '[8/8] View khai trong manifest'
$kq = Test-ViewManifest; [void]$ds.Add($kq); Show-KetQua $kq $ShowDetails
# --- Tổng kết ---------------------------------------------------------------
$soFail = @($ds | Where-Object { $_.TrangThai -eq 'FAIL' }).Count
$soWarn = @($ds | Where-Object { $_.TrangThai -eq 'WARN' }).Count
$soPass = @($ds | Where-Object { $_.TrangThai -eq 'PASS' }).Count

Write-Host ''
Write-Host ('-' * 70) -ForegroundColor Cyan
Write-Host (' BẢNG TỔNG HỢP — {0} PASS · {1} WARN · {2} FAIL' -f $soPass, $soWarn, $soFail) -ForegroundColor Cyan
foreach ($x in $ds) {
    $mau = switch ($x.TrangThai) { 'PASS' { 'Green' } 'WARN' { 'Yellow' } 'FAIL' { 'Red' } }
    Write-Host ("   {0,-6} {1}" -f $x.TrangThai, $x.Ten) -ForegroundColor $mau
}
Write-Host ('-' * 70) -ForegroundColor Cyan

if ($soFail -gt 0) {
    Write-Host ' KẾT LUẬN: FAIL — phải sửa hết mục FAIL trước khi handoff.' -ForegroundColor Red
    exit 1
}
if ($soWarn -gt 0) {
    Write-Host ' KẾT LUẬN: PASS (có cảnh báo) — đọc kỹ các mục WARN ở trên.' -ForegroundColor Yellow
    exit 0
}
Write-Host ' KẾT LUẬN: PASS — tất cả mục xanh.' -ForegroundColor Green
exit 0
