# app-shots-v2.ps1 - boot emulator (software GPU), install APK, login, chup man hinh.
# ASCII-only. Dung: powershell -File infra/scripts/app-shots-v2.ps1
$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$emu = Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
$apk = Join-Path $repoRoot "apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk"
$outDir = Join-Path $repoRoot "docs\review\v2"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

function Dump {
    & $adb shell uiautomator dump /sdcard/u.xml 2>&1 | Out-Null
    $f = Join-Path $env:TEMP "lab-u.xml"
    & $adb pull /sdcard/u.xml $f 2>&1 | Out-Null
    return [System.IO.File]::ReadAllText($f)
}
function Tap($x, $y) { & $adb shell input tap $x $y 2>&1 | Out-Null }
function Shot($name) {
    $full = Join-Path $outDir $name
    & $adb shell screencap -p /sdcard/s.png 2>&1 | Out-Null
    & $adb pull /sdcard/s.png $full 2>&1 | Out-Null
    & $adb shell rm /sdcard/s.png 2>&1 | Out-Null
    if (Test-Path $full) { Write-Output ("  [OK] " + $name + " (" + [math]::Round((Get-Item $full).Length / 1KB, 0) + " KB)") }
    else { Write-Output ("  [FAIL] " + $name) }
}

# ---------------------------------------------------------------- 1. may ao
$dev = (& $adb devices 2>&1 | Out-String)
if ($dev -notmatch "emulator-5554\s+device") {
    Write-Output "=== bat may ao (swiftshader, no-audio) ==="
    Start-Process -FilePath $emu -ArgumentList "-avd", "Pixel_7", "-no-snapshot-load", "-gpu", "swiftshader_indirect", "-no-audio" -WindowStyle Minimized
    for ($i = 1; $i -le 80; $i++) {
        Start-Sleep -Seconds 3
        $b = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($b -eq "1") { Write-Output ("  [OK] boot sau " + ($i * 3) + "s"); break }
    }
} else { Write-Output "=== may ao da chay ===" }
& $adb reverse tcp:8069 tcp:8069 2>&1 | Out-Null
Write-Output ("  reverse: " + ((& $adb reverse --list 2>&1 | Out-String).Trim()))

# ---------------------------------------------------------------- 2. cai APK
Write-Output "=== cai APK ==="
& $adb install -r $apk 2>&1 | Select-Object -Last 1 | ForEach-Object { "  " + $_ }

# ---------------------------------------------------------------- 3. dang nhap + chup
function Login-Shot([string]$login, [string]$password, [string]$shotName) {
    Write-Output ("=== " + $login + " ===")
    $prefs = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`n<map>`n<string name=`"baseUrl`">http://127.0.0.1:8069</string>`n<string name=`"db`">odoo17_lab</string>`n<string name=`"login`">$login</string>`n<boolean name=`"realDataSource`" value=`"true`" />`n</map>"
    $tmp = Join-Path $env:TEMP "lab-prefs.xml"
    [System.IO.File]::WriteAllText($tmp, $prefs, (New-Object System.Text.UTF8Encoding($false)))
    & $adb shell am force-stop com.applogictics 2>&1 | Out-Null
    & $adb push $tmp /data/local/tmp/p.xml 2>&1 | Out-Null
    & $adb shell "run-as com.applogictics cp /data/local/tmp/p.xml shared_prefs/applogictics_session.xml" 2>&1 | Out-Null
    & $adb shell am start -n com.applogictics/vn.lab.logistics.MainActivity 2>&1 | Out-Null
    Start-Sleep -Seconds 9
    $ui = Dump
    if ($ui -notmatch 'Mật khẩu') {
        $m = [regex]::Match($ui, '<node[^>]*text="Đăng nhập"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if ($m.Success) {
            $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
            $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
            Tap $x $y; Write-Output ("  tap tab Dang nhap @ $x,$y"); Start-Sleep -Seconds 3; $ui = Dump
        }
    }
    $pwY = 0; $pwX = 0
    foreach ($m in [regex]::Matches($ui, '<node[^>]*class="android.widget.EditText"[^>]*>')) {
        $b = [regex]::Match($m.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $b.Success) { continue }
        $pwY = [int](([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2)
        $pwX = [int](([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2)
    }
    if ($pwY -eq 0) { Write-Output "  [FAIL] khong thay o mat khau"; Shot $shotName; return }
    Write-Output ("  o mat khau @ $pwX,$pwY")
    Tap $pwX $pwY; Start-Sleep -Milliseconds 800
    & $adb shell input text $password 2>&1 | Out-Null; Start-Sleep -Milliseconds 800
    & $adb shell input keyevent 66 2>&1 | Out-Null; Start-Sleep -Seconds 2
    # Nut dang nhap nam DUOI o mat khau, rong nhat
    $ui = Dump
    $btn = $null
    foreach ($m in [regex]::Matches($ui, '<node[^>]*clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')) {
        $l = [int]$m.Groups[1].Value; $t = [int]$m.Groups[2].Value
        $r = [int]$m.Groups[3].Value; $bo = [int]$m.Groups[4].Value
        if (($r - $l) -gt 500 -and $t -gt $pwY) { $btn = @{ x = [int](($l + $r) / 2); y = [int](($t + $bo) / 2) } }
    }
    if ($btn) { Tap $btn.x $btn.y; Write-Output ("  tap nut Dang nhap @ " + $btn.x + "," + $btn.y) }
    else { Write-Output "  [WARN] khong thay nut dang nhap -> thu toa do du phong 360,815"; Tap 360 815 }
    Start-Sleep -Seconds 14
    Shot $shotName
    $ui = Dump
    [regex]::Matches($ui, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -First 8 | ForEach-Object { "    '" + $_ + "'" }
}

Login-Shot "taixe04" "lab@2026" "app-driver-v2.png"
Login-Shot "sep" "lab@2026" "app-dashboard-v2.png"
Write-Output "=== xong ==="
