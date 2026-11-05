# =====================================================================
# v2-shots.ps1 - Chup bo anh nghiem thu redesign v2 (dark navy + lime)
#
#   powershell -File infra/scripts/v2-shots.ps1            # web 1440 + 390
#   powershell -File infra/scripts/v2-shots.ps1 -Only web-login
#
# Yeu cau: Odoo chay :8069, Playwright trong demo/tools (can quyen full-access:
# Chrome profile nam ngoai workspace). File ASCII-only.
# =====================================================================
param(
    [string]$Base = "http://localhost:8069",
    [string]$OutDir = "docs/review/v2",
    [string]$Only = ""
)

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$out = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out -Force | Out-Null }

$c = (& curl.exe -s -o NUL -w "%{http_code}" --max-time 6 "$Base/web/login" 2>$null)
Write-Output ("Odoo: " + $c)
if ($c -ne "200") { Write-Output "  [FAIL] Odoo chua chay"; exit 1 }

function Shot([string]$name, [string]$url, [string]$login, [string]$password, [int]$w, [int]$h, [int]$wait) {
    if ($Only -ne "" -and $name -notlike ("*" + $Only + "*")) { return }
    $file = Join-Path $out ($name + ".png")
    Write-Output ("  -> " + $name + "  (" + $w + "x" + $h + ")")
    $nodeArgs = @("infra/scripts/shot-auth.mjs", "--base", $Base, "--url", $url, "--out", $file,
              "--login", $login, "--password", $password, "--width", "$w", "--height", "$h", "--wait", "$wait")
    & node @nodeArgs 2>&1 | Select-Object -Last 2 | ForEach-Object { "     " + $_ }
}

function ShotAnon([string]$name, [string]$path, [int]$w, [int]$h, [int]$wait) {
    if ($Only -ne "" -and $name -notlike ("*" + $Only + "*")) { return }
    $file = Join-Path $out ($name + ".png")
    Write-Output ("  -> " + $name + "  (" + $w + "x" + $h + ", KHONG dang nhap)")
    & node @("infra/scripts/shot-page.mjs", "--base", $Base, "--url", $path, "--out", $file,
             "--width", "$w", "--height", "$h", "--wait", "$wait") 2>&1 | Select-Object -Last 1 | ForEach-Object { "     " + $_ }
}

Write-Output ""
Write-Output "=== WEB 1440 ==="
ShotAnon "web-login-1440" "/web/login" 1440 900 2500
Shot "web-jobs-1440"      "/web#menu_id=334&action=489"         "cs01"      "lab@2026" 1440 900 4000
Shot "web-chuyen-1440"    "/web#menu_id=332&action=487"         "manager01" "lab@2026" 1440 900 4000
Shot "web-dashboard-1440" "/web#menu_id=338&action=493"         "sep"       "lab@2026" 1440 900 4500

Write-Output ""
Write-Output "=== WEB 390 (mobile) ==="
ShotAnon "web-login-390" "/web/login" 390 844 2500
Shot "web-jobs-390"       "/web#menu_id=334&action=489"         "cs01"      "lab@2026" 390 844 4000
Shot "web-dashboard-390"  "/web#menu_id=338&action=493"         "sep"       "lab@2026" 390 844 4500

Write-Output ""
Write-Output "=== Anh da chup ==="
Get-ChildItem $out -Filter "*.png" | Sort-Object Name | ForEach-Object { "  {0,-34} {1,6} KB" -f $_.Name, [math]::Round($_.Length/1KB,0) }
