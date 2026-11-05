# Smoke test HTTP cho "Chuyen tai khoan" LAP CHUOI (admin -> taixe01 -> cs01)
# KHONG dung docker: chi goi HTTP toi Odoo dang chay o :8069.
# Route lab_api deu auth="user" => phai giu COOKIE phien bang curl -c/-b.
# Chay: pwsh -File infra/scripts/smoke-admin-switch.ps1

$ErrorActionPreference = "Continue"
$root = "C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics"
$base = "http://localhost:8069/lab/api/v1"
$tmp = Join-Path $root ".tmp"
if (-not (Test-Path $tmp)) { New-Item -ItemType Directory -Path $tmp | Out-Null }
$bodyFile = Join-Path $tmp "smoke-body.json"
$jarA = Join-Path $tmp "cookies-a.txt"
$jarB = Join-Path $tmp "cookies-b.txt"
foreach ($f in @($jarA, $jarB)) { if (Test-Path $f) { Remove-Item $f -Force } }

$script:fail = 0
function Say([string]$text) { Write-Output $text }
function Section([string]$text) { Write-Output ""; Write-Output ("=== " + $text + " ===") }
function Check([string]$what, [string]$got, [string]$want) {
    if ($got -eq $want) { Write-Output ("  [OK]   " + $what + " = " + $got) }
    else { Write-Output ("  [FAIL] " + $what + " = " + $got + "  (ky vong " + $want + ")"); $script:fail = $script:fail + 1 }
}
function CheckBool([string]$what, $got, $want) {
    if ([bool]$got -eq [bool]$want) { Write-Output ("  [OK]   " + $what + " = " + $got) }
    else { Write-Output ("  [FAIL] " + $what + " = " + $got + "  (ky vong " + $want + ")"); $script:fail = $script:fail + 1 }
}

function Curl-Lab([string[]]$argv) {
    $raw = & curl.exe @argv 2>&1
    $text = (@($raw | ForEach-Object { [string]$_ }) -join "`n").Trim()
    $idx = $text.LastIndexOf("`n")
    $b = if ($idx -ge 0) { $text.Substring(0, $idx).Trim() } else { "" }
    $s = if ($idx -ge 0) { $text.Substring($idx + 1).Trim() } else { $text }
    return @{ body = $b; status = $s }
}
function Lab-Post([string]$path, [string]$json, [string]$token, [string]$jar) {
    [System.IO.File]::WriteAllText($bodyFile, $json, (New-Object System.Text.UTF8Encoding($false)))
    $argv = @("-s", "-S", "--max-time", "40", "-X", "POST", "-H", "Content-Type: application/json")
    if ($token) { $argv += @("-H", ("X-Lab-Token: " + $token)) }
    if ($jar) { $argv += @("-b", $jar, "-c", $jar) }
    $argv += @("--data-binary", ("@" + $bodyFile), "-w", "`n%{http_code}", ($base + $path))
    return Curl-Lab $argv
}
function Lab-Get([string]$path, [string]$token, [string]$jar) {
    $argv = @("-s", "-S", "--max-time", "40")
    if ($token) { $argv += @("-H", ("X-Lab-Token: " + $token)) }
    if ($jar) { $argv += @("-b", $jar, "-c", $jar) }
    $argv += @("-w", "`n%{http_code}", ($base + $path))
    return Curl-Lab $argv
}
function J($r) { try { return ($r.body | ConvertFrom-Json) } catch { return $null } }

# ---------------------------------------------------------------- 1. admin login
Section "1) Dang nhap admin"
$r = Lab-Post "/auth/login" '{"login":"admin","password":"admin"}' $null $jarA
Check "POST /auth/login" $r.status "200"
$j = J $r
$tokA = $j.data.token
$rolesA = @($j.data.roles)
Say ("  name='" + $j.data.name + "' roles=" + ($rolesA -join ","))
CheckBool "login admin canSwitch" $j.data.canSwitch $true
if ($rolesA -notcontains "admin") { Say "  [FAIL] roles thieu 'admin'"; exit 1 }

# ---------------------------------------------------------------- 2. accounts
Section "2) GET /admin/accounts (admin)"
$g = Lab-Get "/admin/accounts" $tokA $jarA
Check "GET /admin/accounts" $g.status "200"
$items = @((J $g).data.items)
Say ("  so tai khoan = " + $items.Count)
$have = @($items | ForEach-Object { $_.login })
$want = @("taixe01", "taixe02", "taixe03", "nhomtruong01", "cs01", "kho01", "chungtu01", "ketoan01", "manager01", "sep")
$missing = @($want | Where-Object { $have -notcontains $_ })
if ($missing.Count -eq 0) { Say "  [OK]   du 10 tai khoan demo" } else { Say ("  [FAIL] thieu: " + ($missing -join ",")); $script:fail = $script:fail + 1 }

# ---------------------------------------------------------------- 3. admin -> taixe01
Section "3) Chuyen admin -> taixe01"
$s = Lab-Post "/admin/switch" '{"login":"taixe01"}' $tokA $jarA
Check "POST /admin/switch (taixe01)" $s.status "200"
$j1 = J $s
$tok1 = $j1.data.token
Say ("  login=" + $j1.data.login + " roles=" + (@($j1.data.roles) -join ",") + " uid=" + $j1.data.uid)
CheckBool "switch taixe01 canSwitch" $j1.data.canSwitch $true
$dj = Lab-Get "/driver/jobs" $tok1 $jarA
Check "GET /driver/jobs (dang la taixe01)" $dj.status "200"

# ---------------------------------------------------------------- 4. taixe01 -> cs01 (LAP CHUOI)
Section "4) Dang la taixe01 van chuyen tiep duoc -> cs01"
$g2 = Lab-Get "/admin/accounts" $tok1 $jarA
Check "GET /admin/accounts (phien do admin chuyen)" $g2.status "200"
$s2 = Lab-Post "/admin/switch" '{"login":"cs01"}' $tok1 $jarA
Check "POST /admin/switch (cs01)" $s2.status "200"
$j2 = J $s2
$tok2 = $j2.data.token
Say ("  login=" + $j2.data.login + " roles=" + (@($j2.data.roles) -join ",") + " uid=" + $j2.data.uid)
CheckBool "switch cs01 canSwitch" $j2.data.canSwitch $true
$cj = Lab-Get "/cs/jobs" $tok2 $jarA
Check "GET /cs/jobs (dang la cs01)" $cj.status "200"
try { $cd = (J $cj).data; Say ("  so viec CS thay = " + @($cd.items).Count + " · tai xe = " + @($cd.drivers).Count) } catch { }

# ---------------------------------------------------------------- 5. cs01 -> sep
Section "5) cs01 -> sep (chuyen lan 3)"
$s3 = Lab-Post "/admin/switch" '{"login":"sep"}' $tok2 $jarA
Check "POST /admin/switch (sep)" $s3.status "200"
$j3 = J $s3
$tok3 = $j3.data.token
Say ("  login=" + $j3.data.login + " roles=" + (@($j3.data.roles) -join ","))
$kj = Lab-Get "/sep/kpi" $tok3 $jarA
Check "GET /sep/kpi (dang la sep)" $kj.status "200"

# ---------------------------------------------------------------- 6. login truc tiep taixe01 => KHONG duoc chuyen
Section "6) Dang nhap TRUC TIEP taixe01 => khong co quyen chuyen"
$rd = Lab-Post "/auth/login" '{"login":"taixe01","password":"lab@2026"}' $null $jarB
Check "POST /auth/login (taixe01)" $rd.status "200"
$jd = J $rd
$tokD = $jd.data.token
CheckBool "login taixe01 canSwitch" $jd.data.canSwitch $false
$gd = Lab-Get "/admin/accounts" $tokD $jarB
Check "taixe01 truc tiep -> /admin/accounts" $gd.status "403"
$sd = Lab-Post "/admin/switch" '{"login":"cs01"}' $tokD $jarB
Check "taixe01 truc tiep -> /admin/switch" $sd.status "403"

# ---------------------------------------------------------------- 7. login truc tiep cs01
Section "7) Dang nhap TRUC TIEP cs01"
$rc = Lab-Post "/auth/login" '{"login":"cs01","password":"lab@2026"}' $null (Join-Path $tmp "cookies-c.txt")
Check "POST /auth/login (cs01)" $rc.status "200"
$jc = J $rc
CheckBool "login cs01 canSwitch" $jc.data.canSwitch $false

# ---------------------------------------------------------------- 8. tong ket
Write-Output ""
if ($script:fail -eq 0) { Write-Output "=== KET QUA: TAT CA DAT ===" } else { Write-Output ("=== KET QUA: " + $script:fail + " MUC KHONG DAT ===") }
