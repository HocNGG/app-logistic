# =====================================================================
# verify-demo-lon.ps1 - Dem du lieu that trong DB bang JSON-RPC (khong can docker).
#
#   powershell -File infra/scripts/verify-demo-lon.ps1
#
# Dang nhap admin (chi doc). In bang dem theo trang thai + moc thoi gian.
# File ASCII-only. Khong dung read_group (API 17 doi tham so) va khong dung
# shorthand ngay kieu "30D" (chi co trong search view) - tinh ISO date tai day.
# =====================================================================
param(
    [string]$Base = "http://localhost:8069",
    [string]$Db = "odoo17_lab",
    [string]$Login = "admin",
    [string]$Password = "admin",
    [switch]$Quiet
)

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tmp = Join-Path $repoRoot ".tmp"
if (-not (Test-Path $tmp)) { New-Item -ItemType Directory -Path $tmp | Out-Null }
$jar = Join-Path $tmp "cookies-verify.txt"
$bodyFile = Join-Path $tmp "rpc-verify.json"
if (Test-Path $jar) { Remove-Item $jar -Force }
$enc = New-Object System.Text.UTF8Encoding($false)

function Json-Rpc([string]$path, [string]$json) {
    [System.IO.File]::WriteAllText($bodyFile, $json, $enc)
    $raw = & curl.exe -s -S --max-time 60 -b $jar -c $jar -H "Content-Type: application/json" --data-binary ("@" + $bodyFile) ($Base + $path) 2>&1
    $txt = (@($raw | ForEach-Object { [string]$_ }) -join "`n").Trim()
    try { return ($txt | ConvertFrom-Json) } catch { return $null }
}

function Call-Kw([string]$model, [string]$method, [string]$argsJson) {
    $json = '{"jsonrpc":"2.0","method":"call","params":{"model":"' + $model + '","method":"' + $method + '","args":' + $argsJson + ',"kwargs":{}}}'
    $r = Json-Rpc "/web/dataset/call_kw" $json
    if ($null -eq $r) { return $null }
    if ($r.error) { return ("ERR:" + $r.error.data.name) }
    return $r.result
}

# domain = mang leaf JSON, vd '["state","=","new"]' hoac nhieu leaf ngan cach boi ';;'
function Count([string]$model, [string[]]$leaves) {
    $dom = "["
    $parts = @()
    foreach ($l in $leaves) { if ($l -ne "") { $parts += ("[" + $l + "]") } }
    $dom += ($parts -join ",") + "]"
    return (Call-Kw $model "search_count" ("[" + $dom + "]"))
}

function IsoDate([int]$daysAgo, [int]$hour = 0) {
    $d = (Get-Date).ToUniversalTime().Date.AddDays(-$daysAgo).AddHours($hour)
    return $d.ToString("yyyy-MM-dd HH:mm:ss")
}

# ---------------------------------------------------------------- dang nhap
$auth = Json-Rpc "/web/session/authenticate" ('{"jsonrpc":"2.0","method":"call","params":{"db":"' + $Db + '","login":"' + $Login + '","password":"' + $Password + '"}}')
if ($null -eq $auth -or $null -eq $auth.result -or $null -eq $auth.result.uid) {
    Write-Output "  [FAIL] khong dang nhap duoc bang JSON-RPC"
    exit 1
}
Write-Output ("  dang nhap: uid=" + $auth.result.uid + " (" + $Login + ")")

Write-Output ""
Write-Output "=== 1) Quy mo tong ==="
foreach ($m in @("lab.yeu.cau.lay.hang", "lab.chuyen", "lab.chuyen.line", "lab.hanh.trinh", "lab.hanh.trinh.line", "sale.order", "stock.picking", "account.move", "res.partner", "product.template", "fleet.vehicle", "hr.employee", "res.users", "helpdesk.ticket")) {
    Write-Output ("  {0,-24} {1}" -f $m, (Count $m @()))
}

Write-Output ""
Write-Output "=== 2) Viec lay hang theo trang thai ==="
$total = 0
foreach ($s in @("new", "assigned", "en_route", "picked_up", "confirmed", "cancelled", "failed")) {
    $c = Count "lab.yeu.cau.lay.hang" @(('"state","=","' + $s + '"'))
    if ($c -is [int]) { $total += $c }
    Write-Output ("  {0,-12} {1}" -f $s, $c)
}
Write-Output ("  {0,-12} {1}" -f "TONG", $total)

Write-Output ""
Write-Output "=== 3) Chuyen theo trang thai ==="
foreach ($s in @("draft", "open", "booking_confirmed", "departed", "arrived", "delivered", "closed", "cancelled")) {
    Write-Output ("  {0,-18} {1}" -f $s, (Count "lab.chuyen" @(('"state","=","' + $s + '"'))))
}

Write-Output ""
Write-Output "=== 4) Hanh trinh theo trang thai ==="
foreach ($s in @("draft", "booked", "departed", "in_transit", "arrived", "handed_to_agent", "delivered", "closed", "exception", "returned", "compensated")) {
    Write-Output ("  {0,-18} {1}" -f $s, (Count "lab.hanh.trinh" @(('"stage","=","' + $s + '"'))))
}

Write-Output ""
Write-Output "=== 5) Moc thoi gian & ngoai le ==="
$t0 = IsoDate 0 0
$t7 = IsoDate 7 0
$t30 = IsoDate 30 0
Write-Output ("  job co ngay_giao hom nay : " + (Count "lab.yeu.cau.lay.hang" @(('"ngay_giao",">=","' + $t0 + '"'))))
Write-Output ("  job co ngay_giao 7 ngay  : " + (Count "lab.yeu.cau.lay.hang" @(('"ngay_giao",">=","' + $t7 + '"'))))
Write-Output ("  job co ngay_giao 30 ngay : " + (Count "lab.yeu.cau.lay.hang" @(('"ngay_giao",">=","' + $t30 + '"'))))
Write-Output ("  job tao trong 30 ngay    : " + (Count "lab.yeu.cau.lay.hang" @(('"create_date",">=","' + $t30 + '"'))))
Write-Output ("  job that bai             : " + (Count "lab.yeu.cau.lay.hang" @('"state","=","failed"')))
Write-Output ("  job khong co khach       : " + (Count "lab.yeu.cau.lay.hang" @('"partner_id.ref","=","DEMO-LON-UNKNOWN"')))
$thieu = 0
$rows = Call-Kw "lab.yeu.cau.lay.hang" "search_read" '[[["so_kien_thuc_te",">",0]],["so_kien_thuc_te","so_kien_du_kien"],0,500,"id desc"]'
if ($rows -isnot [string]) { foreach ($r in $rows) { if ($r.so_kien_thuc_te -lt $r.so_kien_du_kien) { $thieu++ } } }
Write-Output ("  job thieu kien           : " + $thieu)
Write-Output ("  chuyen qua tai >100%     : " + (Count "lab.chuyen" @('"ti_le_tai",">",100')))
Write-Output ("  hoa don khach            : " + (Count "account.move" @('"move_type","=","out_invoice"')))
Write-Output ("  phieu ghi no (DEBIT)     : " + (Count "account.move" @('"journal_id.code","=","DEBIT"')))
Write-Output ("  phieu nhap kho WH/IN     : " + (Count "stock.picking" @('"picking_type_code","=","incoming"')))
Write-Output ("  phieu xuat kho WH/OUT    : " + (Count "stock.picking" @('"picking_type_code","=","outgoing"')))
Write-Output ("  phieu chuyen kho noi bo  : " + (Count "stock.picking" @('"picking_type_code","=","internal"')))

Write-Output ""
Write-Output "=== 6) KPI dashboard cua Sep (goi dung ham) ==="
$kpi = Call-Kw "lab.yeu.cau.lay.hang" "lab_dashboard_kpi" '["30d"]'
if ($kpi -is [string]) { Write-Output ("  [WARN] " + $kpi) }
else {
    $json = $kpi | ConvertTo-Json -Compress -Depth 4
    Write-Output ("  " + $json.Substring(0, [Math]::Min(700, $json.Length)))
}

Write-Output ""
Write-Output "=== 7) 5 viec moi nhat ==="
$last = Call-Kw "lab.yeu.cau.lay.hang" "search_read" '[[],["name","shop_name","state","so_kien_du_kien","ngay_giao"],0,5,"id desc"]'
if ($last -is [string]) { Write-Output ("  [WARN] " + $last) } else { $last | ForEach-Object { Write-Output ("  {0,-14} {1,-30} {2,-10} kien={3}  {4}" -f $_.name, $_.shop_name, $_.state, $_.so_kien_du_kien, $_.ngay_giao) } }

Write-Output ""
Write-Output "=== 8) Journal ke toan (de biet ma Debit) ==="
$j = Call-Kw "account.journal" "search_read" '[[],["name","code","type"]]'
if ($j -is [string]) { Write-Output ("  [WARN] " + $j) } else { $j | ForEach-Object { Write-Output ("  {0,-34} code={1,-10} type={2}" -f $_.name, $_.code, $_.type) } }
