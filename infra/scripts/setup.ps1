#Requires -Version 5.1
<#
.SYNOPSIS
    SETUP TẤT CẢ bằng MỘT lệnh — idempotent, chạy lại nhiều lần không hỏng.

.DESCRIPTION
    Thay cho chuỗi thao tác tay: vendor OCA -> restart -> cài module -> kiểm DB -> chạy test -> kiểm tĩnh -> build app.

    Nguyên tắc:
      * BƯỚC NÀO XONG RỒI THÌ BỎ QUA (idempotent) — chạy lại nhanh.
      * Mỗi bước in PASS/SKIP/FAIL rõ ràng; cuối cùng in BẢNG TỔNG HỢP + exit code.
      * Không bao giờ "im lặng đi tiếp" khi bước quan trọng fail.
      * Không sửa file ai cả; chỉ gọi các script/ lệnh đã có.

.PARAMETER SkipOdoo
    Bỏ qua toàn bộ phần Odoo (docker + module + test).

.PARAMETER SkipApp
    Bỏ qua phần app KMP (compile + test).

.PARAMETER OdooOnly
    Chỉ làm phần Odoo (bỏ app). Tương đương -SkipApp.

.PARAMETER Reinstall
    Ép nâng cấp (``-u``) 5 module dự án kể cả khi đã installed.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/setup.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/setup.ps1 -SkipApp
#>
[CmdletBinding()]
param(
    [string]$Repo = "",
    [switch]$SkipOdoo,
    [switch]$SkipApp,
    [switch]$OdooOnly,
    [switch]$Reinstall
)

# $PSScriptRoot RONG khi dung trong param() default tren Windows PowerShell 5.1
if ([string]::IsNullOrWhiteSpace($Repo)) { $Repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if ($OdooOnly) { $SkipApp = $true }

$ErrorActionPreference = 'Continue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

$ComposeFile = Join-Path $Repo 'infra\docker-compose.yml'
$DbName      = 'odoo17_lab'
$DbUser      = 'odoo'
$OcaModules  = @('base_tier_validation', 'helpdesk_mgmt')
$LabModules  = @('lab_core', 'lab_chuyen', 'lab_laixe', 'lab_hanhtrinh', 'lab_api', 'lab_ui')

$results = [System.Collections.ArrayList]@()
function Record($name, $status, $detail) {
    [void]$results.Add([pscustomobject]@{ Buoc = $name; KetQua = $status; ChiTiet = $detail })
    $color = switch ($status) { 'PASS' { 'Green' } 'SKIP' { 'Gray' } 'WARN' { 'Yellow' } 'FAIL' { 'Red' } default { 'White' } }
    Write-Host ("  [{0,-4}] {1}" -f $status, $name) -ForegroundColor $color
    if ($detail) { Write-Host ("         {0}" -f $detail) -ForegroundColor DarkGray }
}

function Section($t) { Write-Host ""; Write-Host ("=== " + $t + " ===") -ForegroundColor Cyan }
function Die($m, $step) { Record $step 'FAIL' $m; Write-Host ""; Write-Host ("DUNG LAI: " + $m) -ForegroundColor Red; Write-Summary; exit 1 }

function Dc { param([Parameter(ValueFromRemainingArguments = $true)]$Rest) & docker compose -f $ComposeFile @Rest }

function Write-Summary {
    Write-Host ""
    Write-Host "================ TONG HOP ================" -ForegroundColor Cyan
    $results | Format-Table -AutoSize | Out-String | Write-Host
    $fails = @($results | Where-Object { $_.KetQua -eq 'FAIL' }).Count
    $warns = @($results | Where-Object { $_.KetQua -eq 'WARN' }).Count
    if ($fails -gt 0)      { Write-Host ("KET QUA: FAIL ($fails buoc loi)") -ForegroundColor Red }
    elseif ($warns -gt 0)  { Write-Host ("KET QUA: PASS (co $warns canh bao)") -ForegroundColor Yellow }
    else                   { Write-Host "KET QUA: PASS - tat ca buoc deu xanh" -ForegroundColor Green }
    Write-Host "==========================================" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "  AppLogictics - setup mot lenh (idempotent)" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "  repo : $Repo"
  $modeTxt = @(); if ($SkipOdoo) { $modeTxt += "bo Odoo" }; if ($SkipApp) { $modeTxt += "bo app" }; if ($modeTxt.Count -eq 0) { $modeTxt = @("day du") }
Write-Host ("  che do: " + ($modeTxt -join ", "))

# ===========================================================================
Section 'BUOC 1 - Kiem tra moi truong'

if (-not (Test-Path $ComposeFile)) { Die "khong thay $ComposeFile" 'Moi truong' }

$dockerOk = $false
try { docker version --format '{{.Server.Version}}' 2>$null | Out-Null; $dockerOk = ($LASTEXITCODE -eq 0) } catch { }
if (-not $SkipOdoo -and -not $dockerOk) {
    Die "Docker Desktop chua chay. Mo Docker Desktop, doi 'Engine running', roi chay lai." 'Moi truong'
}
if (-not $SkipOdoo) { Record 'Docker daemon' 'PASS' 'Engine san sang' } else { Record 'Docker daemon' 'SKIP' 'bo qua (-SkipOdoo)' }

# ===========================================================================
if (-not $SkipOdoo) {

Section 'BUOC 2 - Bat Postgres + Odoo'

Write-Host '         build image (lan dau ~1-2 phut, co odoo-test-helper)...' -ForegroundColor DarkGray
Dc build odoo 2>&1 | Out-Null
Write-Host '         dang bat container (lan dau co the lau)...' -ForegroundColor DarkGray
$t0 = Get-Date
Dc up -d 2>&1 | ForEach-Object { if ($_ -match 'Creating|Created|Starting|Started|Recreat|Error|error') { Write-Host ('         ' + $_) -ForegroundColor DarkGray } }
if ($LASTEXITCODE -ne 0) { Die 'docker compose up -d that bai' 'Bat container' }
Write-Host ('         container len sau ' + [int]((Get-Date) - $t0).TotalSeconds + 's') -ForegroundColor DarkGray

Write-Host '         doi Postgres san sang...' -ForegroundColor DarkGray
$ready = $false
for ($i = 1; $i -le 45; $i++) {
    Dc exec -T db pg_isready -U $DbUser 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    if ($i % 5 -eq 0) { Write-Host ('         ... ' + $i + 's') -ForegroundColor DarkGray }
    Start-Sleep -Seconds 1
}
if (-not $ready) { Die 'Postgres khong san sang sau 120s' 'Bat container' }
Record 'Postgres' 'PASS' 'pg_isready OK'

# ===========================================================================
Section 'BUOC 3 - Vendor module OCA'

$needVendor = $false
foreach ($m in $OcaModules) {
    if (-not (Test-Path (Join-Path $Repo "addons\oca\$m\__manifest__.py"))) { $needVendor = $true }
}

if (-not $needVendor) {
    Record 'Vendor OCA' 'SKIP' ("da co: " + ($OcaModules -join ', '))
} else {
    $v1 = Join-Path $Repo 'infra\scripts\vendor-oca.ps1'
    $v2 = Join-Path $Repo 'infra\scripts\vendor-oca-zip.ps1'
    $ok = $false
    if (Test-Path $v1) {
        Write-Host '         thu git clone...' -ForegroundColor DarkGray
        & powershell -NoProfile -ExecutionPolicy Bypass -File $v1 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ok = $true }
    }
    if (-not $ok -and (Test-Path $v2)) {
        Write-Host '         git that bai -> thu tai ZIP...' -ForegroundColor Yellow
        & powershell -NoProfile -ExecutionPolicy Bypass -File $v2 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ok = $true }
    }
    $missing = @($OcaModules | Where-Object { -not (Test-Path (Join-Path $Repo "addons\oca\$_\__manifest__.py")) })
    if ($missing.Count -gt 0) { Die ("thieu module: " + ($missing -join ', ') + " — kiem tra mang/proxy") 'Vendor OCA' }
    Record 'Vendor OCA' 'PASS' ("da co: " + ($OcaModules -join ', '))
}

# ===========================================================================
Section 'BUOC 4 - Nap lai config (addons_path)'

Dc restart odoo 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Die 'restart odoo that bai' 'Restart Odoo' }

Write-Host '         doi Odoo tra ve 200 (lan dau ~30-60s)...' -ForegroundColor DarkGray
$up = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8069/web/login' -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch { }
    if ($i % 5 -eq 0) { Write-Host ('         ... ' + ($i * 2) + 's') -ForegroundColor DarkGray }
    Start-Sleep -Seconds 2
}
if (-not $up) { Die 'Odoo khong tra ve 200 sau 90s — xem: docker compose logs odoo' 'Restart Odoo' }
Record 'Odoo web' 'PASS' 'http://localhost:8069 tra 200'

# ===========================================================================
Section 'BUOC 5 - Cai module (OCA truoc, roi module du an)'

function Get-ModuleStates {
    $q = "select name||'='||state from ir_module_module where name like 'lab\_%' or name in ('base_tier_validation','helpdesk_mgmt') order by name;"
    $out = Dc exec -T db psql -U $DbUser -d $DbName -t -A -c $q 2>&1
    $map = @{}
    foreach ($line in $out) {
        $s = ([string]$line).Trim()
        if ($s -match '^([a-z_]+)=(\w+)$') { $map[$Matches[1]] = $Matches[2] }
    }
    return $map
}

$states = Get-ModuleStates
$ocaInstalled = @($OcaModules | Where-Object { $states[$_] -eq 'installed' }).Count -eq $OcaModules.Count

if ($ocaInstalled) {
    Record 'Cai OCA' 'SKIP' 'da installed'
} else {
    Dc exec -T odoo odoo -c /etc/odoo/odoo.conf -d $DbName -i ($OcaModules -join ',') --stop-after-init 2>&1 |
        Tee-Object -Variable ocaLog | Out-Null
    $states = Get-ModuleStates
    $bad = @($OcaModules | Where-Object { $states[$_] -ne 'installed' })
    if ($bad.Count -gt 0) {
        $tail = ($ocaLog | Select-Object -Last 25) -join [Environment]::NewLine
        Record 'Cai OCA' 'FAIL' ("chua installed: " + ($bad -join ', '))
        Write-Host $tail -ForegroundColor DarkRed
        Die 'cai OCA that bai — log o tren' 'Cai OCA'
    }
    Record 'Cai OCA' 'PASS' 'installed ca ' + $OcaModules.Count + ' module'
}

$labInstalled = @($LabModules | Where-Object { $states[$_] -eq 'installed' }).Count -eq $LabModules.Count
$mode = if ($labInstalled -and $Reinstall) { '-u' } elseif ($labInstalled) { '' } else { '-i' }

if ($mode -eq '') {
    Record 'Cai module du an' 'SKIP' 'da installed (dung -Reinstall de ep nang cap)'
} else {
    Write-Host '         dang cai module (1-3 phut, dung tat cua so)...' -ForegroundColor DarkGray
    Dc exec -T odoo odoo -c /etc/odoo/odoo.conf -d $DbName $mode ($LabModules -join ',') --stop-after-init 2>&1 |
        Tee-Object -Variable labLog | Out-Null
    $states = Get-ModuleStates
    $bad = @($LabModules | Where-Object { $states[$_] -ne 'installed' })
    if ($bad.Count -gt 0) {
        $tail = ($labLog | Select-Object -Last 40) -join [Environment]::NewLine
        Record 'Cai module du an' 'FAIL' ("chua installed: " + ($bad -join ', '))
        Write-Host $tail -ForegroundColor DarkRed
        Die 'cai module du an that bai — log o tren' 'Cai module du an'
    }
    Record 'Cai module du an' 'PASS' ("$mode thanh cong " + $LabModules.Count + ' module')
}

# ===========================================================================
Section 'BUOC 6 - Chay test suite (gate Luat 7)'

Write-Host '         dang chay test suite (1-3 phut)...' -ForegroundColor DarkGray
# ⚠️ PHAI dung `run --rm --no-deps` (container RIENG), KHONG dung `exec`:
# suite co HttpCase -> tien trinh test tu mo mot HTTP server o cong 8069. Neu chay
# bang `exec` trong container dang phuc vu, cong 8069 da bi server chinh giu ->
# log "OSError: [Errno 98] Address already in use", va cac request url_open() lai
# bay sang SERVER THAT. Hệ quả da gap that 2026-10-28: 16 test do 401 vi du lieu
# tao trong transaction cua test khong ton tai o server that — khong phai loi code.
# Container rieng co network namespace rieng -> cong 8069 trong no con trong.
$testLog = Dc run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d $DbName `
    --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh,/lab_api --stop-after-init 2>&1
$testExit = $LASTEXITCODE
$verdict = ($testLog | Select-String -Pattern 'odoo\.tests\.result' | Select-Object -Last 1)

if (-not $verdict) {
    Record 'Test suite' 'WARN' 'khong thay dong odoo.tests.result — xem log'
} elseif ($verdict.Line -match '0 failed,\s*0 error') {
    Record 'Test suite' 'PASS' $verdict.Line.Trim()
} else {
    Record 'Test suite' 'FAIL' $verdict.Line.Trim()
    ($testLog | Select-String -Pattern 'FAIL:|ERROR:' | Select-Object -First 20) | ForEach-Object { Write-Host ('    ' + $_.Line) -ForegroundColor DarkRed }
}

# ===========================================================================
Section 'BUOC 6b - Chuan hoa timezone (Postgres image thieu alias Asia/Saigon)'
# Postgres cua image KHONG co ten Asia/Saigon (chi co Asia/Ho_Chi_Minh) -> bao cao cua user
# dat tz do se loi: time zone Asia/Saigon not recognized. Da gap that 2026-10-28.
$tzOut = Dc exec -T db psql -U $DbUser -d $DbName -t -A -c "update res_partner set tz = 'Asia/Ho_Chi_Minh' where tz like 'Asia/Saigon%'" 2>&1
if ($LASTEXITCODE -eq 0) { Record 'Timezone' 'PASS' 'da chuan hoa Asia/Saigon -> Asia/Ho_Chi_Minh' }
else { Record 'Timezone' 'WARN' ('khong chuan hoa duoc: ' + ($tzOut -join ' ')) }

# BUOC 6c: dat mui gio cho user DEMO.
# Ly do (do that 2026-10-28): KPI /sep/kpi dem "hom nay" theo tz cua user; user demo khong
# set tz -> Odoo lay UTC -> tu 00:00 den 07:00 gio VN, so lieu "hom nay" bi tinh sang hom
# truoc (lech dung vao gio lam viec buoi sang). Dat tz cho 9 tai khoan demo.
$demoUsers = "'cs01','kho01','chungtu01','ketoan01','manager01','sep','taixe01','taixe02','taixe03'"
$tzUsers = Dc exec -T db psql -U $DbUser -d $DbName -t -A -c "update res_partner p set tz = 'Asia/Ho_Chi_Minh' from res_users u where u.partner_id = p.id and u.login in ($demoUsers) and coalesce(p.tz,'') <> 'Asia/Ho_Chi_Minh'" 2>&1
if ($LASTEXITCODE -eq 0) { Record 'Timezone user' 'PASS' 'da dat Asia/Ho_Chi_Minh cho 9 tai khoan demo' }
else { Record 'Timezone user' 'WARN' ('khong dat duoc tz: ' + ($tzUsers -join ' ')) }

Section 'BUOC 7 - Kiem tra tinh (chay lai duoc offline)'

$check = Join-Path $Repo 'infra\scripts\check.ps1'
if (Test-Path $check) {
    $cLog = & powershell -NoProfile -ExecutionPolicy Bypass -File $check 2>&1
    $cExit = $LASTEXITCODE
    $line = ($cLog | Select-String -Pattern 'KET LUAN' | Select-Object -Last 1)
    if ($cExit -eq 0) { Record 'check.ps1' 'PASS' ($(if ($line) { $line.Line.Trim() } else { 'exit 0' })) }
    else { Record 'check.ps1' 'FAIL' ($(if ($line) { $line.Line.Trim() } else { "exit $cExit" })) }
} else { Record 'check.ps1' 'SKIP' 'khong tim thay' }

$audit = Join-Path $Repo 'infra\scripts\audit-native-first.ps1'
if (Test-Path $audit) {
    $aLog = & powershell -NoProfile -ExecutionPolicy Bypass -File $audit 2>&1
    $a = ($aLog | Select-String -Pattern 'Audit native-first' | Select-Object -Last 1)
    if ($a -and $a.Line -match '0 P0') { Record 'audit native-first' 'PASS' $a.Line.Trim() }
    else { Record 'audit native-first' 'FAIL' ($(if ($a) { $a.Line.Trim() } else { 'khong co ket qua' })) }
} else { Record 'audit native-first' 'SKIP' 'khong tim thay' }

} # end if -not SkipOdoo

# ===========================================================================
if (-not $SkipApp) {

Section 'BUOC 8 - App KMP (compile + test, offline)'

$kmp = Join-Path $Repo 'apps\kmp'
if (-not (Test-Path (Join-Path $kmp 'gradlew.bat'))) {
    Record 'App KMP' 'SKIP' 'chua co gradlew — chay: powershell -File apps\kmp\bootstrap.ps1'
} else {
    $jdk = 'C:\Program Files\Java\jdk-21.0.10'
    if (Test-Path $jdk) { $env:JAVA_HOME = $jdk } else { Write-Host '         (!) khong thay JDK 21 o duong dan mac dinh' -ForegroundColor Yellow }

    Push-Location $kmp
    try {
        $gLog = & .\gradlew.bat --offline --console=plain :composeApp:selfCheck 2>&1
        $gExit = $LASTEXITCODE
        if ($gExit -eq 0) {
            $line = ($gLog | Select-String -Pattern 'KET QUA: ' | Select-Object -Last 1)
            Record 'App selfCheck' 'PASS' ($(if ($line) { $line.Line.Trim() } else { 'exit 0' }))
        } else {
            Record 'App selfCheck' 'FAIL' "exit $gExit"
            ($gLog | Select-Object -Last 15) | ForEach-Object { Write-Host ('    ' + $_) -ForegroundColor DarkRed }
        }

        # desktopTest chi co khi da khai bao kotlin-test (composeApp/build.gradle.kts)
        $tLog = & .\gradlew.bat --offline --console=plain :composeApp:desktopTest 2>&1
        $tExit = $LASTEXITCODE
        if ($tExit -eq 0) {
            Record 'App desktopTest' 'PASS' 'unit test xanh'
        } elseif ($tLog -match 'Task .*desktopTest.* not found') {
            Record 'App desktopTest' 'SKIP' 'chua khai bao source set test'
        } else {
            Record 'App desktopTest' 'FAIL' "exit $tExit"
            ($tLog | Select-Object -Last 15) | ForEach-Object { Write-Host ('    ' + $_) -ForegroundColor DarkRed }
        }
    } finally { Pop-Location }
}

} # end if -not SkipApp

# ===========================================================================
Write-Summary
$fails = @($results | Where-Object { $_.KetQua -eq 'FAIL' }).Count
exit $(if ($fails -gt 0) { 1 } else { 0 })
