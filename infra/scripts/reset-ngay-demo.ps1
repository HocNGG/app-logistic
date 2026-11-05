# =====================================================================
# reset-ngay-demo.ps1 - Doi "hom nay" cua bo du lieu demo ve dung HOM NAY.
#
#   powershell -File infra/scripts/reset-ngay-demo.ps1            # ap dung
#   powershell -File infra/scripts/reset-ngay-demo.ps1 -DryRun    # chi in, khong sua
#
# VI SAO CAN: seed-demo-lon.py neo cung `hom_nay = 2026-10-29` (co dinh de tai lap
# duoc so lieu trong tai lieu). Hom nay la 2026-11-02 => KHONG con viec nao roi vao
# "hom nay", ma app tai xe chi tra ve viec co khung gio HOM NAY (`/driver/jobs`).
# Ket qua: mo app tai xe thay 0 viec, khong test duoc luong.
#
# CACH LAM: dich toan bo cot ngay/gio cua 3 model nghiep vu di dung
# (hom_nay - 2026-10-29) ngay. Nho vay cau truc lich su (7 ngay, hom qua, hom nay)
# giu nguyen, chi doi moc.
#
# Chi dung cho DB demo. Khong dung cho production.
# File ASCII-only (theo quy uoc infra/scripts).
# =====================================================================
param(
    [string]$Db = "odoo17_lab",
    [string]$Container = "db",
    [string]$User = "odoo",
    [string]$Anchor = "2026-10-29",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composeFile = Join-Path $repoRoot "infra\docker-compose.yml"

function Psql([string]$sql) {
    $raw = & docker compose -f $composeFile exec -T $Container psql -U $User -d $Db -v ON_ERROR_STOP=1 -c $sql 2>&1
    $code = $LASTEXITCODE
    $txt = (@($raw | ForEach-Object { [string]$_ }) -join "`n").Trim()
    if ($code -ne 0) { throw ("psql loi (exit $code):`n" + $txt) }
    return $txt
}

function Count-Today([string]$state) {
    $dom = ""
    if ($state -ne "") { $dom = " and state = '" + $state + "'" }
    $sql = "select count(*) from lab_yeu_cau_lay_hang where khung_gio_tu::date = current_date" + $dom + ";"
    $out = Psql $sql
    $line = ($out -split "`n" | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -First 1)
    if ($null -eq $line) { return 0 }
    return [int]$line.Trim()
}

Write-Output "=== reset-ngay-demo ==="

$deltaTxt = Psql ("select (current_date - date '" + $Anchor + "') as so_ngay;")
$deltaLine = ($deltaTxt -split "`n" | Where-Object { $_ -match '^\s*-?\d+\s*$' } | Select-Object -First 1)
$delta = [int]$deltaLine.Trim()
Write-Output ("neo (anchor)     : " + $Anchor)
Write-Output ("hom nay          : " + (Get-Date).ToString("yyyy-MM-dd"))
Write-Output ("can dich         : " + $delta + " ngay")

Write-Output ""
Write-Output "--- TRUOC KHI DICH ---"
Write-Output ("viec co khung gio hom nay : " + (Count-Today ""))
foreach ($s in @("new", "assigned", "en_route", "picked_up")) {
    Write-Output ("  " + $s.PadRight(10) + ": " + (Count-Today $s))
}

if ($delta -eq 0) {
    Write-Output ""
    Write-Output "Du lieu da roi vao hom nay - khong can dich. Xong."
    exit 0
}

# Cap nhat: chi cac cot NGAY/GIO nghiep vu. create_date/write_date giu nguyen
# (do la dau vet ky thuat, sua se lam sai lich su ghi nhan).
#
# Dung `interval '<n> days'` chu KHONG dung `+ <n>`: Postgres chi cho phep
# `date + integer`; voi `timestamp` thi phai la `+ interval` (da gap loi that:
# "operator does not exist: timestamp without time zone + integer").
$shift = "interval '" + $delta + " days'"
$updates = @(
    "update lab_yeu_cau_lay_hang set khung_gio_tu = khung_gio_tu + $shift",
    "       , khung_gio_den = khung_gio_den + $shift",
    "       , ngay_giao = ngay_giao + $shift",
    "       , ngay_xac_nhan = ngay_xac_nhan + $shift",
    " where khung_gio_tu is not null or ngay_giao is not null or ngay_xac_nhan is not null;",
    "update lab_chuyen set ngay_di = ngay_di + $delta",
    "       , ngay_den_du_kien = ngay_den_du_kien + $delta",
    "       , cut_off_datetime = cut_off_datetime + $shift",
    " where ngay_di is not null or ngay_den_du_kien is not null or cut_off_datetime is not null;",
    "update lab_hanh_trinh set ngay_bay = ngay_bay + $shift",
    "       , ngay_den = ngay_den + $shift",
    "       , pod_date = pod_date + $shift",
    " where ngay_bay is not null or ngay_den is not null or pod_date is not null;"
)
$sql = ($updates -join "`n")

if ($DryRun) {
    Write-Output ""
    Write-Output "--- DRY RUN: cau lenh se chay ---"
    Write-Output $sql
    exit 0
}

Write-Output ""
Write-Output "--- DANG DICH ---"
Write-Output (Psql $sql)

Write-Output ""
Write-Output "--- SAU KHI DICH ---"
Write-Output ("viec co khung gio hom nay : " + (Count-Today ""))
foreach ($s in @("new", "assigned", "en_route", "picked_up")) {
    Write-Output ("  " + $s.PadRight(10) + ": " + (Count-Today $s))
}

Write-Output ""
Write-Output "--- 5 viec tieu bieu cua hom nay (dung de test app tai xe) ---"
Write-Output (Psql "select id, name, state, to_char(khung_gio_tu,'HH24:MI') as gio, so_kien_du_kien, driver_user_id from lab_yeu_cau_lay_hang where khung_gio_tu::date = current_date order by state, id limit 5;")

Write-Output ""
Write-Output "Xong. Mo app tai xe (dang nhap taixe01..05) de thay viec hom nay."
