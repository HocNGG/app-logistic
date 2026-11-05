# reset-demo.ps1 — dựng lại DB demo từ đầu, 1 lệnh
#
# Mục đích (AGENTS.md Luật 8): trước mỗi lần demo phải có thể reset về trạng thái
# biết trước trong < 60 giây. Script này là "nút reset" đó.
#
# Cách dùng:
#   powershell -File infra/scripts/reset-demo.ps1              # reset bình thường
#   powershell -File infra/scripts/reset-demo.ps1 -KeepData    # chỉ upgrade module
#   powershell -File infra/scripts/reset-demo.ps1 -Snapshot    # sao lưu DB trước khi reset
#
# Yêu cầu: docker compose đang chạy (infra/docker-compose.yml)

param(
    [string]$Db      = "odoo17_lab",
    [string]$DbUser  = "odoo",
    [string]$Modules = "lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api",
    [switch]$KeepData,
    [switch]$Snapshot
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sw = [System.Diagnostics.Stopwatch]::StartNew()

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "LỖI: $msg" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------------------
Step "Kiểm tra docker compose"
docker compose -f "$root\infra\docker-compose.yml" ps --quiet
if ($LASTEXITCODE -ne 0) { Fail "docker compose không chạy. Chạy: docker compose -f infra/docker-compose.yml up -d" }

$compose = @("compose", "-f", "$root\infra\docker-compose.yml")

# ---------------------------------------------------------------------------
if ($Snapshot) {
    Step "Sao lưu DB $Db"
    $stamp  = Get-Date -Format "yyyyMMdd-HHmmss"
    $outDir = Join-Path $root "infra\snapshots"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $file = Join-Path $outDir "$Db-$stamp.sql"
    docker @compose exec -T db pg_dump -U $DbUser -F p -f - $Db | Set-Content -Path $file -Encoding UTF8
    Write-Host "   -> $file"
}

# ---------------------------------------------------------------------------
if (-not $KeepData) {
    Step "Dừng Odoo (để không còn kết nối tới DB)"
    docker @compose stop odoo | Out-Null

    Step "Xoá DB $Db"
    docker @compose exec -T db dropdb -U $DbUser --if-exists --force $Db
    if ($LASTEXITCODE -ne 0) { Write-Host "   (dropdb cảnh báo, tiếp tục)" -ForegroundColor Yellow }

    Step "Cài lại toàn bộ module + dữ liệu demo"
    docker @compose run --rm odoo odoo -c /etc/odoo/odoo.conf -d $Db -i $Modules --stop-after-init
    if ($LASTEXITCODE -ne 0) { Fail "cài module thất bại (xem log ở trên)" }
} else {
    Step "Chỉ nâng cấp module (-u $Modules)"
    docker @compose run --rm odoo odoo -c /etc/odoo/odoo.conf -d $Db -u $Modules --stop-after-init
    if ($LASTEXITCODE -ne 0) { Fail "nâng cấp module thất bại" }
}

# ---------------------------------------------------------------------------
Step "Khởi động lại Odoo"
docker @compose start odoo | Out-Null

Step "Kiểm tra HTTP"
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8069/web/login" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $ok) { Fail "Odoo chưa lên sau 60s. Xem: docker compose -f infra/docker-compose.yml logs odoo" }

$sw.Stop()
Write-Host "`nXONG trong $([math]::Round($sw.Elapsed.TotalSeconds,1))s" -ForegroundColor Green
Write-Host "  Odoo : http://localhost:8069"
Write-Host "  DB   : $Db"
Write-Host "  Demo : đăng nhập bằng user trong infra/scripts/seed-users.md"
Write-Host ""
Write-Host "Ghi chú: nếu > 60s, ghi vào docs/review/red-team.md (Luật 8 yêu cầu < 60s)." -ForegroundColor Yellow
