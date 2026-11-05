#Requires -Version 5.1
<#
.SYNOPSIS
    Tuỳ chọn: tải Three.js về đĩa để bật WebGL thật cho landing (mặc định dùng CSS 3D).

.DESCRIPTION
    Vì sao cần script riêng: shell của agent KHÔNG có mạng, nên việc tải do NGƯỜI dùng chạy.
    Vì sao phải vendor thay vì dùng CDN: phòng demo có thể không có mạng -> CDN = trang trắng.

    Tải về: demo/vendor/three/three.min.js
    Landing (demo/landing/index.html) tự phát hiện `window.THREE`:
      - có  -> dùng scene WebGL (nếu bản nâng cấp được bật)
      - không -> giữ nguyên CSS 3D (không vỡ gì)

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/vendor-three.ps1
#>
[CmdletBinding()]
param(
    [string]$Repo = "",
    [string]$Version = "r160"
)

# $PSScriptRoot RONG khi dung trong param() default tren Windows PowerShell 5.1
if ([string]::IsNullOrWhiteSpace($Repo)) { $Repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

$ErrorActionPreference = 'Continue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

$dest = Join-Path $Repo 'demo\vendor\three'
$file = Join-Path $dest 'three.min.js'

Write-Host ''
Write-Host '=== Vendor Three.js (tuy chon) ===' -ForegroundColor Cyan

if (Test-Path $file) {
    Write-Host ('  [SKIP] da co: ' + $file + ' (' + [math]::Round((Get-Item $file).Length/1KB,0) + ' KB)') -ForegroundColor Gray
    Write-Host '  Muon tai lai: xoa file roi chay lai.' -ForegroundColor Gray
    exit 0
}

New-Item -ItemType Directory -Force -Path $dest | Out-Null

$urls = @(
    "https://unpkg.com/three@0.160.0/build/three.min.js",
    "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js"
)

$ok = $false
foreach ($u in $urls) {
    Write-Host ('  dang tai ' + $u) -ForegroundColor Gray
    try {
        Invoke-WebRequest -Uri $u -OutFile $file -UseBasicParsing -TimeoutSec 120
    } catch {
        if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
            & curl.exe -sS -L --fail --max-time 120 -o $file $u
        }
    }
    if ((Test-Path $file) -and (Get-Item $file).Length -gt 100000) { $ok = $true; break }
    if (Test-Path $file) { Remove-Item $file -Force }
}

if (-not $ok) {
    Write-Host '  [LOI] khong tai duoc Three.js (kiem tra mang/proxy).' -ForegroundColor Red
    Write-Host '        KHONG sao: landing van chay bang CSS 3D (0 dependency).' -ForegroundColor Yellow
    exit 1
}

Write-Host ('  [OK] ' + $file + ' (' + [math]::Round((Get-Item $file).Length/1KB,0) + ' KB)') -ForegroundColor Green
Write-Host ''
Write-Host '  Bat WebGL trong landing: them dong nay truoc </body> cua demo/landing/index.html:' -ForegroundColor Cyan
Write-Host '    <script src="../vendor/three/three.min.js"></script>' -ForegroundColor Gray
Write-Host '  Landing tu kiem `window.THREE`; neu khong co thi giu CSS 3D.' -ForegroundColor Gray
Write-Host ''
exit 0
