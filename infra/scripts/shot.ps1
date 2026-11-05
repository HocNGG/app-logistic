# =====================================================================
# shot.ps1 - Chup anh web bang Chrome/Edge headless (khong can Playwright)
#
# Vi du:
#   powershell -File infra/scripts/shot.ps1 -Url "file:///C:/.../demo/landing/index.html" -Out demo/landing.png
#   powershell -File infra/scripts/shot.ps1 -Url "http://localhost:8069/web/login" -Out docs/review/odoo-login.png -Width 1440 -Height 900
#   powershell -File infra/scripts/shot.ps1 -Url "..." -OutFull docs/review/landing-full.png -Width 1440 -Height 3000
#
# Quy uoc: file ASCII-only + BOM UTF-8 (PS 5.1 doc file khong BOM se loi dau Unicode).
# =====================================================================
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$Width = 1440,
    [int]$Height = 900,
    [int]$WaitMs = 1500,
    # Chrome tren Windows co cua so toi thieu ~500px -> muon test 390 that thi dat
    # -Width 390 -Scale 2: cua so = 780 device px, layout = 780/2 = 390 CSS px.
    [int]$Scale = 1
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Find-Browser {
    $cands = @(
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
    )
    foreach ($c in $cands) { if (Test-Path $c) { return $c } }
    throw "Khong tim thay Chrome/Edge. Cai Chrome hoac sua danh sach trong shot.ps1."
}

$browser = Find-Browser
$outPath = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $repoRoot $Out }
$dir = Split-Path -Parent $outPath
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
if (Test-Path $outPath) { Remove-Item $outPath -Force }

$tmpProfile = Join-Path $env:TEMP ("shot-profile-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
# LUU Y: khong dat ten bien la $args (bien tu dong cua PowerShell).
$chromeArgs = @(
    "--headless=new",
    "--disable-gpu",
    "--hide-scrollbars",
    "--no-first-run",
    "--disable-extensions",
    "--force-device-scale-factor=$Scale",
    # Cho phep video tu chay trong headless (mac dinh Chrome chan autoplay khi khong co
    # tuong tac nguoi dung -> anh chup se chi thay khung den).
    "--autoplay-policy=no-user-gesture-required",
    "--mute-audio",
    "--user-data-dir=$tmpProfile",
    # Chrome hieu --window-size la CSS px; --force-device-scale-factor chi nhan ban raster.
    # (Truoc day toi nhan them $Scale -> 390 thanh 780 CSS => roi vao layout tablet. Da gap that.)
    "--window-size=$Width,$Height",
    "--virtual-time-budget=$WaitMs",
    "--screenshot=$outPath",
    $Url
)
# Chrome ghi thong bao ("N bytes written to file ...") ra stderr. Voi PS 5.1 va
# $ErrorActionPreference = "Stop", stderr cua lenh native bi bien thanh loi terminating
# lam script dung du anh da ghi. Start-Process khong di qua pipeline nen tranh duoc.
$proc = Start-Process -FilePath $browser -ArgumentList $chromeArgs -Wait -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 400

if (-not (Test-Path $outPath)) { throw "Chrome khong tao duoc anh (exit=$($proc.ExitCode)): $outPath" }
$bytes = [System.IO.File]::ReadAllBytes($outPath)
$sig = ($bytes[0..7] | ForEach-Object { $_.ToString("X2") }) -join " "
if ($sig -ne "89 50 4E 47 0D 0A 1A 0A") { throw "File khong phai PNG hop le ($sig)" }
Remove-Item $tmpProfile -Recurse -Force -ErrorAction SilentlyContinue
Write-Output ("  [OK] {0}  ({1}x{2}, {3:N0} KB)  PNG header OK" -f $outPath, $Width, $Height, ($bytes.Length / 1KB))
