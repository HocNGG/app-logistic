#Requires -Version 5.1
<#
.SYNOPSIS
    Vendor module OCA nhánh 17.0 vào addons/oca/ — bản TẢI ZIP (không cần git).

.DESCRIPTION
    Dùng khi `vendor-oca.ps1` (bản dùng git clone) thất bại vì mạng/proxy/SSL.
    Bản này chỉ cần HTTP(S) thường: tải ZIP từ codeload.github.com rồi giải nén.

    Module bắt buộc (docs/01 §3.2):
      * base_tier_validation -> OCA/server-ux   (KHÔNG phải server-tools!)
      * helpdesk_mgmt        -> OCA/helpdesk

    Bố cục bắt buộc: addons/oca/<tên_module>/
    Vì Odoo chỉ quét addons_path thêm MỘT cấp con — để nguyên
    addons/oca/server-ux/base_tier_validation thì Odoo KHÔNG thấy.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/vendor-oca-zip.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File infra/scripts/vendor-oca-zip.ps1 -IncludeTms
#>
[CmdletBinding()]
param(
    [string]$Repo = "",
    [string[]]$Modules = @('base_tier_validation', 'helpdesk_mgmt'),
    [switch]$IncludeTms
)

# Không dùng 'Stop' toàn cục: script gọi HTTP và ta muốn tự bắt lỗi từng bước.
$ErrorActionPreference = 'Continue'

# $PSScriptRoot RONG khi dung trong param() default tren Windows PowerShell 5.1 -> phai tinh trong than script
if ([string]::IsNullOrWhiteSpace($Repo)) { $Repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

function Ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green }
function Info($m) { Write-Host "  ..     $m" -ForegroundColor Gray }
function Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Err($m)  { Write-Host "  [LOI]  $m" -ForegroundColor Red }

# module -> repo
$map = @{
    'base_tier_validation' = 'server-ux'
    'helpdesk_mgmt'        = 'helpdesk'
}
if ($IncludeTms) { $map['tms'] = 'stock-logistics-transport' }

$ocaDir = Join-Path $Repo 'addons\oca'
$tmpDir = Join-Path $env:TEMP ("oca-zip-" + [guid]::NewGuid().ToString('N').Substring(0, 8))

Write-Host ''
Write-Host '=== Vendor OCA 17.0 bang ZIP ===' -ForegroundColor Cyan
Write-Host "  repo     : $Repo"
Write-Host "  dich     : $ocaDir"
Write-Host "  module   : $($Modules -join ', ')"
Write-Host ''

New-Item -ItemType Directory -Force -Path $ocaDir | Out-Null
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

# Tải mỗi repo MỘT lần (nhiều module có thể cùng repo)
$repoFiles = @{}
$failed = 0
$reposNeeded = @()
foreach ($m in $Modules) {
    if (-not $map.ContainsKey($m)) { Warn "chua biet repo cua module '$m' -> bo qua"; continue }
    $r = $map[$m]
    if ($reposNeeded -notcontains $r) { $reposNeeded += $r }
}

foreach ($r in $reposNeeded) {
    $zip  = Join-Path $tmpDir "$r.zip"
    $url  = "https://codeload.github.com/OCA/$r/zip/refs/heads/17.0"
    Info "tai $url"
    $done = $false
    try {
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 300
        $done = $true
    } catch {
        Warn ("Invoke-WebRequest that bai: " + $_.Exception.Message)
        if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
            Info 'thu curl.exe...'
            & curl.exe -sS -L --fail --max-time 300 -o $zip $url
            if ($LASTEXITCODE -eq 0 -and (Test-Path $zip)) { $done = $true }
        }
    }
    if (-not $done -or -not (Test-Path $zip) -or (Get-Item $zip).Length -lt 1000) {
        Err "khong tai duoc $r (kiem tra mang/proxy)"
        $failed++; continue
    }
    Ok ("da tai {0} ({1:N0} KB)" -f $r, ((Get-Item $zip).Length / 1KB))

    $dest = Join-Path $tmpDir $r
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    try {
        Expand-Archive -Path $zip -DestinationPath $dest -Force
    } catch {
        Err ("giai nen that bai: " + $_.Exception.Message); $failed++; continue
    }
    # ZIP cua GitHub co 1 thu muc goc: <repo>-17.0/
    $root = Get-ChildItem $dest -Directory | Select-Object -First 1
    if (-not $root) { Err "khong thay thu muc goc sau khi giai nen $r"; $failed++; continue }
    $repoFiles[$r] = $root.FullName
    Ok "giai nen -> $($root.Name)"
}

Write-Host ''
Write-Host '--- copy module ra addons/oca/ ---' -ForegroundColor Cyan

$copied = @()
foreach ($m in $Modules) {
    if (-not $map.ContainsKey($m)) { continue }
    $r = $map[$m]
    if (-not $repoFiles.ContainsKey($r)) { Err "$m : thieu repo $r (da fail o tren)"; $failed++; continue }

    $src = Join-Path $repoFiles[$r] $m
    if (-not (Test-Path $src)) {
        Err "$m : khong thay thu muc module trong repo $r"
        Warn ("cac module co trong repo: " + ((Get-ChildItem $repoFiles[$r] -Directory | Select-Object -ExpandProperty Name) -join ', '))
        $failed++; continue
    }

    $dst = Join-Path $ocaDir $m
    if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
    Copy-Item $src $dst -Recurse -Force

    $mf = Join-Path $dst '__manifest__.py'
    if (-not (Test-Path $mf)) { Err "$m : thieu __manifest__.py"; $failed++; continue }
    $mtxt = Get-Content -Raw -Encoding UTF8 $mf
    if ($mtxt -match "'version'\s*:\s*'([^']+)'") {
        $ver = $Matches[1]
        if ($ver -like '17.0*') { Ok "$m -> addons/oca/$m (version $ver)" } else { Warn "$m co version $ver (khong phai 17.0.*)" }
    } else { Warn "$m : khong doc duoc version trong manifest" }
    $copied += $m
}

# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '--- ket qua ---' -ForegroundColor Cyan
if ($copied.Count -gt 0) {
    Ok ("da vendor: " + ($copied -join ', '))
    Write-Host ''
    Write-Host 'Buoc tiep theo:' -ForegroundColor Cyan
    Write-Host '  1) Kiem tra Odoo thay module:'
    Write-Host '     docker compose -f infra/docker-compose.yml exec odoo odoo shell -d odoo17_lab --no-http'
    Write-Host "     >>> env['ir.module.module'].search([('name','in',['base_tier_validation','helpdesk_mgmt'])])"
    Write-Host '  2) Cai lai module du an:'
    Write-Host '     docker compose -f infra/docker-compose.yml exec odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api --stop-after-init'
} else {
    Err 'khong vendor duoc module nao'
}

if ($failed -gt 0) { Warn ("co $failed buoc that bai — xem log o tren") }

Try { Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue } catch { }
Write-Host ''
exit $(if ($failed -gt 0) { 1 } else { 0 })
