# =====================================================================
# seed-demo-lon.ps1 - Ap bo du lieu mau LON vao DB (6 thang lich su).
#
#   powershell -File infra/scripts/seed-demo-lon.ps1              # ap lan dau / ap lai (idempotent)
#   powershell -File infra/scripts/seed-demo-lon.ps1 -Reset       # xoa phan da sinh roi tao lai
#   powershell -File infra/scripts/seed-demo-lon.ps1 -NoUpgrade   # bo qua buoc -u module
#
# Chay TRONG docker (odoo shell) => can quyen docker (named pipe). File ASCII-only.
# =====================================================================
param(
    [switch]$Reset,
    [switch]$NoUpgrade,
    [string]$Db = "odoo17_lab"
)

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$cf = Join-Path $repoRoot "infra\docker-compose.yml"
$py = Join-Path $repoRoot "addons\lab_core\tools\seed_demo_lon.py"

if (-not (Test-Path $py)) { throw "Khong thay file sinh du lieu: $py" }

Write-Output "=== 0) Kiem tra Odoo dang chay ==="
$code = (& curl.exe -s -o NUL -w "%{http_code}" --max-time 6 "http://localhost:8069/web/login" 2>$null)
Write-Output ("  /web/login = " + $code)
if ($code -ne "200") { Write-Output "  [FAIL] Odoo chua san sang, bat Docker Desktop + infra truoc"; exit 1 }

if (-not $NoUpgrade) {
    Write-Output ""
    Write-Output "=== 1) Upgrade module (ACL/admin + data) ==="
    $out = docker compose -f $cf run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d $Db -u lab_core,lab_laixe,lab_chuyen,lab_hanhtrinh --stop-after-init 2>&1
    $bad = @($out | Select-String -Pattern "CRITICAL|Traceback \(most recent")
    if ($bad.Count -gt 0) { $bad | Select-Object -Last 6 | ForEach-Object { Write-Output ("  [err] " + $_) } } else { Write-Output "  [OK] khong co Traceback/CRITICAL" }
}

Write-Output ""
Write-Output "=== 2) Chay bo sinh du lieu trong odoo shell ==="
if ($Reset) { $env:LAB_SEED_RESET = "1"; Write-Output "  [che do] LAB_SEED_RESET=1" } else { $env:LAB_SEED_RESET = "" }
$started = Get-Date
$cmd = 'cmd /c "docker compose -f "' + $cf + '" run --rm --no-deps -T odoo odoo shell -d ' + $Db + ' --no-http < "' + $py + '""'
Write-Output ("  lenh: " + $cmd)
$log = Join-Path $repoRoot ".tmp\seed-demo-lon.log"
$out2 = cmd /c ('docker compose -f "' + $cf + '" run --rm --no-deps -T odoo odoo shell -d ' + $Db + ' --no-http < "' + $py + '"') 2>&1
$out2 | Set-Content -Path $log -Encoding UTF8
$out2 | Select-Object -Last 40 | ForEach-Object { Write-Output ("  " + $_) }
$secs = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
Write-Output ("  thoi gian: " + $secs + "s  (log: .tmp\seed-demo-lon.log)")

Write-Output ""
Write-Output "=== 3) Restart Odoo de xoa cache (an toan cho UI) ==="
docker compose -f $cf restart odoo 2>&1 | Select-Object -Last 1 | ForEach-Object { Write-Output ("  " + $_) }
$code2 = "000"
for ($i = 1; $i -le 45; $i++) {
    Start-Sleep -Seconds 2
    $code2 = (& curl.exe -s -o NUL -w "%{http_code}" --max-time 4 "http://localhost:8069/web/login" 2>$null)
    if ($code2 -eq "200") { Write-Output ("  [OK] Odoo 200 sau " + ($i * 2) + "s"); break }
}
if ($code2 -ne "200") { Write-Output ("  [FAIL] http=" + $code2) }

Write-Output ""
Write-Output "=== 4) Kiem nhanh bang API (read-only) ==="
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "infra\scripts\verify-demo-lon.ps1")
