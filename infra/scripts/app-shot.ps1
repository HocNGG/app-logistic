# =====================================================================
# app-shot.ps1 - Dang nhap app tren may ao roi chup 1 anh (nghiem thu UI)
#
# Vi du:
#   powershell -File infra/scripts/app-shot.ps1 -Login taixe01 -Password lab@2026 -Out docs/review/app-driver.png
#   powershell -File infra/scripts/app-shot.ps1 -Login cs01 -Password lab@2026 -Out docs/review/app-cs.png -TapText "YC/LH/26/0003"
#
# Yeu cau: may ao dang chay (adb devices thay emulator-5554), app da cai, Odoo chay o
# http://localhost:8069 va `adb reverse tcp:8069 tcp:8069` (script tu bat).
#
# Quy uoc: file ASCII-only + BOM UTF-8 (PS 5.1 doc file khong BOM se loi dau Unicode).
# =====================================================================
param(
    [string]$Login = "taixe01",
    [string]$Password = "lab@2026",
    [string]$Out = "docs/review/app-shot.png",
    [string]$Base = "http://127.0.0.1:8069",
    [string]$Db = "odoo17_lab",
    [string[]]$TapText = @(),
    [int]$LoginWaitSec = 14
)

# PS 5.1: adb ghi thong bao ra stderr; voi ErrorActionPreference=Stop thi stderr cua lenh
# native bi bien thanh loi terminating (da gap that). Dung Continue + kiem tra ket qua bang
# `throw` tuong minh o cac buoc quan trong.
$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "Khong thay adb tai $adb" }

function Shot($relPath) {
    $full = if ([System.IO.Path]::IsPathRooted($relPath)) { $relPath } else { Join-Path $repoRoot $relPath }
    $dir = Split-Path -Parent $full
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    & $adb shell screencap -p /sdcard/lab-shot.png | Out-Null
    & $adb pull /sdcard/lab-shot.png $full 2>&1 | Out-Null
    & $adb shell rm /sdcard/lab-shot.png | Out-Null
    if (-not (Test-Path $full)) { throw "Khong chup duoc anh: $full" }
    Write-Output ("  [OK] " + $full + " (" + [math]::Round((Get-Item $full).Length/1KB,0) + " KB)")
}

function GetUi {
    & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    $tmp = Join-Path $env:TEMP "app-shot-ui.xml"
    & $adb pull /sdcard/ui.xml $tmp 2>&1 | Out-Null
    return [System.IO.File]::ReadAllText($tmp, [System.Text.Encoding]::UTF8)
}

function CenterOf($ui, $text) {
    $m = [regex]::Match($ui, '<node[^>]*text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $null }
    return @{
        x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    }
}

function TapText($ui, $text) {
    $c = CenterOf $ui $text
    if (-not $c) { Write-Output ("  [..] khong thay '" + $text + "'"); return $false }
    & $adb shell input tap $c.x $c.y | Out-Null
    Write-Output ("  [tap] '" + $text + "' @ " + $c.x + "," + $c.y)
    return $true
}

# 1) Ghi prefs: diem vao + thong tin dang nhap (khong co token -> man hinh dang nhap)
$prefs = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="baseUrl">$Base</string>
<string name="db">$Db</string>
<string name="login">$Login</string>
<boolean name="realDataSource" value="true" />
</map>
"@
$tmpPrefs = Join-Path $env:TEMP "app-shot-prefs.xml"
[System.IO.File]::WriteAllText($tmpPrefs, $prefs, [System.Text.UTF8Encoding]::new($false))
& $adb push $tmpPrefs /data/local/tmp/prefs.xml 2>&1 | Out-Null
& $adb shell am force-stop com.applogictics | Out-Null
& $adb shell "run-as com.applogictics cp /data/local/tmp/prefs.xml shared_prefs/applogictics_session.xml" | Out-Null
& $adb reverse tcp:8069 tcp:8069 | Out-Null
& $adb logcat -c | Out-Null

# 2) Mo app -> Vao app -> dien mat khau -> bam Dang nhap (app TU chuyen man)
& $adb shell am start -n com.applogictics/vn.lab.logistics.MainActivity | Out-Null
Start-Sleep -Seconds 8
$ui = GetUi
# Tab "Dang nhap" luon hien o thanh tren -> bam tab, KHONG bam nut "Vao app" (nut nam duoi,
# bam vao chu de truot - da gap that).
if ($ui -notmatch 'Mật khẩu hoặc API key') {
    if (-not (TapText $ui "Đăng nhập")) { TapText $ui "Vào app" | Out-Null }
    Start-Sleep -Seconds 4
    $ui = GetUi
}
if ($ui -match 'Mật khẩu hoặc API key') {
    # cuon de thay o mat khau
    & $adb shell input swipe 360 1100 360 520 250 | Out-Null
    Start-Sleep -Seconds 2
    $ui = GetUi
    $pwY = 0
    foreach ($m in [regex]::Matches($ui, '<node[^>]*class="android.widget.EditText"[^>]*>')) {
        $t = [regex]::Match($m.Value, 'text="([^"]*)"').Groups[1].Value
        $b = [regex]::Match($m.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if ($t -eq '') { $pwY = [int](([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2) }
    }
    if ($pwY -gt 0) {
        & $adb shell input tap 360 $pwY | Out-Null
        Start-Sleep -Milliseconds 900
        & $adb shell input text $Password | Out-Null
        Start-Sleep -Milliseconds 900
        # An ban phim: gui IME action DONE = keyevent 66 (ENTER) vao o mat khau.
        # Bai hoc 2026-10-30 (da thu 3 cach tren may ao 720x1280):
        #   * ESC (111): ban phim VAN mo => nut Dang nhap bi che => bam vao ban phim;
        #   * BACK (4):  quay ve tab "Gioi thieu" => khong bao gio dang nhap;
        #   * tap (360,210): trung TAB "Dang nhap" => reset form.
        # ENTER chi dong ban phim / submit IME, khong doi tab.
        & $adb shell input keyevent 66 | Out-Null
        Start-Sleep -Seconds 2
        # nut dang nhap: phan tu clickable rong nhat trong nua duoi
        $ui = GetUi
        $btn = $null
        foreach ($m in [regex]::Matches($ui, '<node[^>]*clickable="true"[^>]*>')) {
            $b = [regex]::Match($m.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
            if (-not $b.Success) { continue }
            $l = [int]$b.Groups[1].Value; $t = [int]$b.Groups[2].Value
            $r = [int]$b.Groups[3].Value; $bo = [int]$b.Groups[4].Value
            if (($r - $l) -gt 500 -and $bo -le 1275) { $btn = @{ x = [int](($l + $r) / 2); y = [int](($t + $bo) / 2) } }
        }
        if ($btn) { & $adb shell input tap $btn.x $btn.y | Out-Null; Write-Output ("  [tap] Dang nhap @ " + $btn.x + "," + $btn.y) }
    }
    Start-Sleep -Seconds $LoginWaitSec
}

# 3) Cac cu bam them (mo chi tiet, mo capture...) truoc khi chup
foreach ($t in $TapText) {
    $ui = GetUi
    TapText $ui $t | Out-Null
    Start-Sleep -Seconds 3
}

# 4) Chup + in text dang thay (de biet dang o man nao)
Shot $Out
$ui = GetUi
Write-Output "  --- text tren man hinh ---"
[regex]::Matches($ui, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique | Select-Object -First 16 | ForEach-Object { Write-Output ("    '" + $_ + "'") }
