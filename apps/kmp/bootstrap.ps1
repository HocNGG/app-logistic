#Requires -Version 5.1
<#
.SYNOPSIS
    Bootstrap môi trường build cho app KMP: kiểm JDK, tải Gradle, sinh gradle wrapper.

.DESCRIPTION
    Repo này cố tình KHÔNG commit file nhị phân `gradle-wrapper.jar`.
    Script này tạo nó một lần, đúng chuẩn: tải Gradle về rồi để Gradle tự sinh wrapper.

    Chạy MỘT LẦN trên terminal thật (PowerShell), KHÔNG chạy trong sandbox/CI không có mạng.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File apps\kmp\bootstrap.ps1

.NOTES
    Sau khi xong, dùng:
        cd apps\kmp
        .\gradlew.bat --version
        .\gradlew.bat :composeApp:run
#>

param(
    [string]$GradleVersion = "9.8.2",
    [string]$JdkPath       = "C:\Program Files\Java\jdk-21.0.10",
    [switch]$SkipDownload
)

$ErrorActionPreference = "Stop"
# Invoke-WebRequest của PS 5.1 cực chậm với file lớn vì vẽ progress bar -> tắt đi
$ProgressPreference = "SilentlyContinue"

# Kiểm tra zip có toàn vẹn không (chống file tải dở do Ctrl+C giữa chừng)
function Test-ZipFile([string]$path) {
    if (-not (Test-Path $path)) { return $false }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        $fs = [System.IO.File]::OpenRead($path)
        try {
            $zip = New-Object System.IO.Compression.ZipFile($fs)
            $n = $zip.Entries.Count
            $zip.Dispose()
            return ($n -gt 0)
        } finally { $fs.Dispose() }
    } catch { return $false }
}
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $here

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "    OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "    !   $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "`nLỖI: $m" -ForegroundColor Red; Pop-Location; exit 1 }

# ---------------------------------------------------------------------------
Step "1/5 Kiểm tra JDK"

if (-not (Test-Path $JdkPath)) {
    $found = @()
    foreach ($d in @("C:\Program Files\Java","C:\Program Files\Eclipse Adoptium","C:\Program Files\Microsoft","C:\Program Files\Amazon Corretto")) {
        if (Test-Path $d) { $found += Get-ChildItem $d -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk.*21|21.*jdk' } | Select-Object -ExpandProperty FullName }
    }
    if ($found.Count -gt 0) { $JdkPath = $found[0]; Warn "Không thấy $JdkPath, dùng $JdkPath" }
    else { Die "Không tìm thấy JDK 21. Cài: winget install EclipseAdoptium.Temurin.21.JDK" }
}

$javac = Join-Path $JdkPath "bin\javac.exe"
if (-not (Test-Path $javac)) { Die "$JdkPath không có javac.exe — đây là JRE, không phải JDK. Cài JDK 21." }
Ok "JDK: $JdkPath"

# JAVA_HOME cho phiên này VÀ các phiên sau (máy có cả JDK 25 trong PATH → dễ chọn nhầm)
$env:JAVA_HOME = $JdkPath
try {
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $JdkPath, "User")
    Ok "JAVA_HOME = $JdkPath (phiên này + user)"
} catch {
    # Một số môi trường (sandbox/CI) chặn ghi registry — không sao, phiên này đã có JAVA_HOME
    Warn "Đã đặt JAVA_HOME cho PHIÊN NÀY, nhưng không ghi được vào registry user."
    Warn "Muốn đặt vĩnh viễn, mở PowerShell thật và chạy:"
    Warn ('  [Environment]::SetEnvironmentVariable("JAVA_HOME","' + $JdkPath + '","User")')
}

# ---------------------------------------------------------------------------
Step "2/5 Tìm Gradle"

$gradleExe = $null
$g = Get-Command gradle -ErrorAction SilentlyContinue
if ($g) { $gradleExe = $g.Source; Ok "Đã có Gradle trên PATH: $gradleExe" }

$cacheRoot = Join-Path $env:LOCALAPPDATA "gradle-bootstrap"
$distDir   = Join-Path $cacheRoot "gradle-$GradleVersion"

if (-not $gradleExe) {
    if (Test-Path (Join-Path $distDir "bin\gradle.bat")) {
        $gradleExe = Join-Path $distDir "bin\gradle.bat"
        Ok "Dùng bản đã tải trước: $gradleExe"
    }
}

# ---------------------------------------------------------------------------
if (-not $gradleExe -and -not $SkipDownload) {
    Step "3/5 Tải Gradle $GradleVersion"
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

    try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

    $zip = Join-Path $cacheRoot "gradle-$GradleVersion-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

    # File cũ có thể là bản tải dở (Ctrl+C) -> phải kiểm tra, không tin sự tồn tại
    if (Test-Path $zip) {
        if (Test-ZipFile $zip) {
            Ok "Dùng file đã tải trước: $([math]::Round((Get-Item $zip).Length/1MB,1)) MB"
        } else {
            Warn "File zip cũ bị hỏng/tải dở -> xoá và tải lại"
            Remove-Item $zip -Force
        }
    }

    if (-not (Test-Path $zip)) {
        Write-Host "    tải $url  (~130 MB, chỉ 1 lần)"
        $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
        $t0 = Get-Date
        if ($curl) {
            # curl.exe nhanh hơn nhiều và có thanh tiến độ
            & curl.exe -L --fail --progress-bar --max-time 1800 -o $zip $url
            if ($LASTEXITCODE -ne 0) { Die "curl tải thất bại (exit $LASTEXITCODE). Kiểm tra mạng/proxy." }
        } else {
            Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 1800
        }
        $sec = [int]((Get-Date) - $t0).TotalSeconds
        Write-Host "    xong sau ${sec}s"
    }

    if (-not (Test-ZipFile $zip)) { Die "File zip tải về không hợp lệ. Xoá $zip rồi chạy lại." }
    Ok "Đã có $([math]::Round((Get-Item $zip).Length/1MB,1)) MB (zip hợp lệ)"

    Write-Host "    giải nén..."
    if (Test-Path $distDir) { Remove-Item $distDir -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $cacheRoot -Force
    $extracted = Get-ChildItem $cacheRoot -Directory | Where-Object { $_.Name -like "gradle-$GradleVersion*" } | Select-Object -First 1
    if (-not $extracted) { Die "Giải nén xong nhưng không thấy thư mục gradle-$GradleVersion" }
    if ($extracted.FullName -ne $distDir) { Rename-Item $extracted.FullName $distDir -Force }
    $gradleExe = Join-Path $distDir "bin\gradle.bat"
    if (-not (Test-Path $gradleExe)) { Die "Không thấy bin\gradle.bat sau khi giải nén" }
    Ok "Gradle sẵn sàng: $gradleExe"
} elseif (-not $gradleExe) {
    Die "Không có Gradle và đã -SkipDownload. Cài: winget install Gradle.Gradle"
}

# ---------------------------------------------------------------------------
Step "4/5 Sinh gradle wrapper (gradlew + gradlew.bat + gradle-wrapper.jar)"
& $gradleExe wrapper --gradle-version $GradleVersion --distribution-type bin
if ($LASTEXITCODE -ne 0) { Die "gradle wrapper thất bại" }

if (-not (Test-Path "gradle\wrapper\gradle-wrapper.jar")) { Die "Không sinh được gradle-wrapper.jar" }
Ok "wrapper.jar: $((Get-Item 'gradle\wrapper\gradle-wrapper.jar').Length) bytes"

# ---------------------------------------------------------------------------
Step "5/5 Kiểm tra"
Write-Host "    JAVA_HOME = $env:JAVA_HOME"
& .\gradlew.bat --version
if ($LASTEXITCODE -ne 0) { Die "gradlew --version thất bại" }

Pop-Location
Write-Host @"

================================================================
XONG. Từ giờ chỉ cần 3 lệnh:

    cd apps\kmp
    .\gradlew.bat --version              # phải thấy dòng "JVM: 21.x"
    .\gradlew.bat :composeApp:run        # CỬA SỔ APP hiện ra

Lần chạy đầu sẽ tải thư viện Kotlin/Compose (~5 phút).
Muốn thấy app thì nhìn CỬA SỔ, không mở trình duyệt.
App không có cổng — nó gọi RA Odoo http://localhost:8069.

Nếu bước "run" báo thiếu dependency: chạy lại, mạng chậm thì nó tải tiếp.
================================================================
"@ -ForegroundColor Green
