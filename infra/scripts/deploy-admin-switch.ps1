# Deploy + smoke test cho tinh nang "Chuyen tai khoan" (admin).
# Chay: pwsh -File infra/scripts/deploy-admin-switch.ps1
# ASCII-only, khong em dash (bai hoc tu su co parser PowerShell).

$ErrorActionPreference = "Continue"
$root = "C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics"
$cf = "$root\infra\docker-compose.yml"
$base = "http://localhost:8069/lab/api/v1"
$tmp = Join-Path $root ".tmp"
if (-not (Test-Path $tmp)) { New-Item -ItemType Directory -Path $tmp | Out-Null }
$bodyFile = Join-Path $tmp "smoke-body.json"

function Say([string]$text) { Write-Output $text }
function Section([string]$text) { Write-Output ""; Write-Output ("=== " + $text + " ===") }

# Goi curl, tach dong cuoi = HTTP status.
function Curl-Lab([string[]]$argv) {
    $raw = & curl.exe @argv 2>&1
    $lines = @($raw | ForEach-Object { [string]$_ })
    $text = ($lines -join "`n").Trim()
    $idx = $text.LastIndexOf("`n")
    $b = if ($idx -ge 0) { $text.Substring(0, $idx).Trim() } else { "" }
    $s = if ($idx -ge 0) { $text.Substring($idx + 1).Trim() } else { $text }
    return @{ body = $b; status = $s }
}

function Lab-Post([string]$path, [string]$json, [string]$token) {
    [System.IO.File]::WriteAllText($bodyFile, $json, (New-Object System.Text.UTF8Encoding($false)))
    $argv = @("-s", "-S", "--max-time", "40", "-X", "POST", "-H", "Content-Type: application/json")
    if ($token) { $argv += @("-H", ("X-Lab-Token: " + $token)) }
    $argv += @("--data-binary", ("@" + $bodyFile), "-w", "`n%{http_code}", ($base + $path))
    return Curl-Lab $argv
}

function Lab-Get([string]$path, [string]$token) {
    $argv = @("-s", "-S", "--max-time", "40")
    if ($token) { $argv += @("-H", ("X-Lab-Token: " + $token)) }
    $argv += @("-w", "`n%{http_code}", ($base + $path))
    return Curl-Lab $argv
}

# ---------------------------------------------------------------- 1. upgrade module
Section "1) Upgrade lab_core,lab_api (container rieng: run --rm --no-deps)"
$out = docker compose -f $cf run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_core,lab_api --stop-after-init 2>&1
$errs = @($out | Select-String -Pattern "ERROR|CRITICAL|Traceback")
if ($errs.Count -gt 0) {
    $errs | Select-Object -Last 10 | ForEach-Object { Say ("  [err] " + $_) }
} else {
    Say "  [OK] khong co ERROR / CRITICAL / Traceback"
}

# ---------------------------------------------------------------- 2. restart
Section "2) Restart odoo + cho /web/login = 200"
$rc = docker compose -f $cf restart odoo 2>&1 | Select-Object -Last 1
Say ("  " + $rc)
$code = "000"
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    $code = (& curl.exe -s -o NUL -w "%{http_code}" --max-time 4 "http://localhost:8069/web/login" 2>$null)
    if ($code -eq "200") { Say ("  [OK] Odoo 200 sau " + ($i * 2) + "s"); break }
}
if ($code -ne "200") { Say ("  [FAIL] http_code=" + $code); exit 1 }

# ---------------------------------------------------------------- 3. admin login
Section "3) Dang nhap admin"
$r = Lab-Post "/auth/login" '{"login":"admin","password":"admin"}' $null
Say ("  POST /auth/login -> " + $r.status)
$j = $null
try { $j = $r.body | ConvertFrom-Json } catch { }
if (-not $j -or -not $j.ok) { Say ("  [FAIL] body=" + $r.body); exit 1 }
$adminToken = $j.data.token
$adminRoles = @($j.data.roles)
Say ("  name='" + $j.data.name + "' roles=" + ($adminRoles -join ",") + " login=" + $j.data.login + " uid=" + $j.data.uid)
if ($adminRoles -notcontains "admin") { Say "  [FAIL] roles KHONG co 'admin' -> app se khong mo man Chuyen tai khoan"; exit 1 }
if ($adminRoles[0] -ne "admin") { Say ("  [WARN] 'admin' khong dung dau: " + ($adminRoles -join ",")) } else { Say "  [OK] 'admin' dung dau roles" }

# ---------------------------------------------------------------- 4. GET accounts
Section "4) GET /admin/accounts (token admin)"
$g = Lab-Get "/admin/accounts" $adminToken
Say ("  -> " + $g.status)
$items = @()
try { $items = @(($g.body | ConvertFrom-Json).data.items) } catch { Say ("  [FAIL] body=" + $g.body) }
Say ("  so tai khoan = " + $items.Count)
$items | ForEach-Object { Say ("    " + $_.login.PadRight(14) + " | " + $_.name + " | " + (@($_.roles) -join ",")) }
$want = @("cs01", "kho01", "chungtu01", "ketoan01", "manager01", "sep", "taixe01", "taixe02", "taixe03", "nhomtruong01")
$have = @($items | ForEach-Object { $_.login })
$missing = @($want | Where-Object { $have -notcontains $_ })
if ($missing.Count -eq 0) { Say "  [OK] du 10 tai khoan demo" } else { Say ("  [WARN] thieu: " + ($missing -join ",")) }

# ---------------------------------------------------------------- 5. switch
Section "5) POST /admin/switch -> taixe01"
$s = Lab-Post "/admin/switch" '{"login":"taixe01"}' $adminToken
Say ("  -> " + $s.status)
$newToken = $null
try {
    $j2 = $s.body | ConvertFrom-Json
    $newToken = $j2.data.token
    Say ("  ok=" + $j2.ok + " login=" + $j2.data.login + " name='" + $j2.data.name + "' roles=" + (@($j2.data.roles) -join ",") + " uid=" + $j2.data.uid)
    Say ("  driver_name='" + $j2.data.driver_name + "' jobsToday=" + $j2.data.jobsToday + " expiresAt=" + $j2.data.expiresAt)
    Say ("  token moi khac token admin: " + ($newToken -ne $adminToken))
} catch { Say ("  [FAIL] body=" + $s.body) }

# ---------------------------------------------------------------- 6. chan quyen
Section "6) Kiem chan quyen (ky vong 403)"
$g2 = Lab-Get "/admin/accounts" $newToken
Say ("  token taixe01 -> /admin/accounts = " + $g2.status + "  (ky vong 403)")
try { $e = $g2.body | ConvertFrom-Json; Say ("    error=" + $e.error.code + " | " + $e.error.message) } catch { Say ("    body=" + $g2.body) }

$r3 = Lab-Post "/auth/login" '{"login":"cs01","password":"lab@2026"}' $null
$csToken = $null
try { $csToken = ($r3.body | ConvertFrom-Json).data.token } catch { }
$g3 = Lab-Get "/admin/accounts" $csToken
Say ("  token cs01     -> /admin/accounts = " + $g3.status + "  (ky vong 403)")

# ---------------------------------------------------------------- 7. loi tham so
Section "7) Loi tham so"
$e1 = Lab-Post "/admin/switch" '{"login":"khong_ton_tai"}' $adminToken
Say ("  login khong ton tai -> " + $e1.status + "  (ky vong 404)")
$e2 = Lab-Post "/admin/switch" '{}' $adminToken
Say ("  thieu login         -> " + $e2.status + "  (ky vong 400)")
$e3 = Lab-Post "/admin/switch" '{"login":"admin"}' $adminToken
Say ("  chuyen sang admin   -> " + $e3.status + "  (ky vong 403)")

# ---------------------------------------------------------------- 8. smoke web
Section "8) Web van song"
$w = (& curl.exe -s -o NUL -w "%{http_code}" --max-time 10 "http://localhost:8069/web/login" 2>$null)
Say ("  /web/login -> " + $w)
$w2 = (& curl.exe -s -o NUL -w "%{http_code}" --max-time 10 "http://localhost:8069/web" 2>$null)
Say ("  /web       -> " + $w2 + "  (303/302 = ve login khi khong co cookie)")

Say ""
Say "=== XONG ==="
