# =====================================================================
# app-switch-demo.ps1 - Chup chuoi "chuyen vai tro bang dropdown tren thanh tren cung"
#
#   admin (dang nhap) -> dropdown tren thanh tren -> taixe01 -> VAN con dropdown -> cs01
#   -> VAN con dropdown -> dang xuat -> dang nhap truc tiep taixe01 -> KHONG con dropdown
#
# Yeu cau: may ao dang chay, app da cai, Odoo o :8069, `adb reverse` (script tu bat).
# Quy uoc: file ASCII-only. Dung Write-Host cho tien trinh (Write-Output bi phep gan nuot mat).
# =====================================================================
param(
    [string]$OutDir = "docs/review",
    [string]$Base = "http://127.0.0.1:8069",
    [string]$Db = "odoo17_lab"
)

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "Khong thay adb tai $adb" }
$outFull = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
if (-not (Test-Path $outFull)) { New-Item -ItemType Directory -Path $outFull -Force | Out-Null }

function Ui {
    & $adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    $tmp = Join-Path $env:TEMP ("lab-ui-" + [guid]::NewGuid().ToString('N') + ".xml")
    & $adb pull /sdcard/ui.xml $tmp 2>&1 | Out-Null
    return [System.IO.File]::ReadAllText($tmp, [System.Text.Encoding]::UTF8)
}

function Bounds {
    param($ui, $pattern)
    $m = [regex]::Match($ui, '<node[^>]*text="[^"]*' + $pattern + '[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $null }
    return @{
        x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
        text = [regex]::Match($m.Value, 'text="([^"]*)"').Groups[1].Value
    }
}

# Bam vao node co text khop regex; neu chua thay thi cuon trong menu roi tim lai.
function TapScroll {
    param($pattern, [string]$label, [int]$maxSwipe = 6)
    for ($k = 0; $k -le $maxSwipe; $k++) {
        $b = Bounds (Ui) $pattern
        if ($b) {
            & $adb shell input tap $b.x $b.y | Out-Null
            Write-Host ("  [tap] '" + $b.text + "' @ " + $b.x + "," + $b.y)
            return $true
        }
        & $adb shell input swipe 360 780 360 520 280 | Out-Null
        Start-Sleep -Milliseconds 900
    }
    Write-Host ("  [..] khong thay '" + $label + "'")
    return $false
}

function Shot([string]$name) {
    $full = Join-Path $outFull $name
    & $adb shell screencap -p /sdcard/lab-shot.png | Out-Null
    & $adb pull /sdcard/lab-shot.png $full 2>&1 | Out-Null
    & $adb shell rm /sdcard/lab-shot.png | Out-Null
    if (-not (Test-Path $full)) { throw "Khong chup duoc: $full" }
    Write-Host ("  [anh] " + $name + " (" + [math]::Round((Get-Item $full).Length / 1KB, 0) + " KB)")
}

function SwitcherLabel {
    $b = Bounds (Ui) '\u25BE'
    if ($b) { Write-Host ("  [CO nut chuyen vai tro] '" + $b.text + "'"); return $b.text }
    Write-Host "  [KHONG co nut chuyen vai tro]"
    return ""
}

& $adb reverse tcp:8069 tcp:8069 | Out-Null

# ---------------------------------------------------------------- 1. dang nhap admin
Write-Host "=== 1) Dang nhap admin ==="
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "infra\scripts\app-shot.ps1") `
    -Login admin -Password admin -Out (Join-Path $OutDir "app-switch-1-admin.png") -Base $Base -Db $Db -LoginWaitSec 14 2>&1 |
    Select-String -Pattern "tap\]|OK\]" | ForEach-Object { Write-Host ("  " + $_.Line) }

Write-Host ""
Write-Host "=== 2) Nut chuyen vai tro o man admin ==="
$nhanAdmin = SwitcherLabel

Write-Host ""
Write-Host "=== 3) Mo menu, chon taixe01 (1 click) ==="
$null = TapScroll '\u25BE' "nut chuyen vai tro"
Start-Sleep -Seconds 2
Shot "app-switch-2-menu.png"
$null = TapScroll 'taixe01' "dong taixe01"
Start-Sleep -Seconds 6
Shot "app-switch-3-driver.png"
$nhanDriver = SwitcherLabel

Write-Host ""
Write-Host "=== 4) Dang la taixe01: mo menu, chon cs01 ==="
$null = TapScroll '\u25BE' "nut chuyen vai tro (tai xe)"
Start-Sleep -Seconds 2
$null = TapScroll 'cs01' "dong cs01"
Start-Sleep -Seconds 6
Shot "app-switch-4-cs.png"
$nhanCs = SwitcherLabel

Write-Host ""
Write-Host "=== 5) Dang xuat roi dang nhap TRUC TIEP taixe01 ==="
$null = TapScroll 'ng xu.t' "nut Dang xuat"
Start-Sleep -Seconds 3
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot "infra\scripts\app-shot.ps1") `
    -Login taixe01 -Password lab@2026 -Out (Join-Path $OutDir "app-switch-5-taixe-truc-tiep.png") -Base $Base -Db $Db -LoginWaitSec 12 2>&1 |
    Select-String -Pattern "tap\]|OK\]" | ForEach-Object { Write-Host ("  " + $_.Line) }
$nhanTrucTiep = SwitcherLabel

Write-Host ""
Write-Host "=== KET LUAN ==="
Write-Host ("  admin                 : " + $nhanAdmin)
Write-Host ("  sau khi sang taixe01 : " + $nhanDriver + "   (ky vong con nut)")
Write-Host ("  sau khi sang cs01    : " + $nhanCs + "   (ky vong con nut, nhan 'CS ...')")
Write-Host ("  login truc tiep taixe01: '" + $nhanTrucTiep + "'   (ky vong RONG = khong co nut)")
