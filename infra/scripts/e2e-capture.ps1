# =====================================================================
# e2e-capture.ps1 - Chay that luong "tai xe chup anh + ky + gui so kien"
# qua HTTP API (khong qua UI), roi kiem chung bang SQL.
#
# Muc dich (AGENTS.md Luat 9): moi tinh nang phai co bang chung chay that.
# Script nay la bang chung cho endpoint POST /driver/jobs/<id>/capture.
#
# Vi du:
#   powershell -File infra/scripts/e2e-capture.ps1
#   powershell -File infra/scripts/e2e-capture.ps1 -JobId 5
#   powershell -File infra/scripts/e2e-capture.ps1 -Restore -JobId 5   # tra job ve en_route
#   powershell -File infra/scripts/e2e-capture.ps1 -SkipDbCheck
#
# Yeu cau: Odoo dang chay o $BaseUrl, tai khoan tai xe co mat khau $Password,
# va co it nhat 1 job o trang thai assigned/en_route (chay reset-demo.ps1 neu het).
#
# QUY UOC: file nay CHI dung ky tu ASCII. Ly do da kiem chung: neu file .ps1
# mat BOM ma co dau gach ngang Unicode, PowerShell 5.1 doc bang ANSI -> ky tu
# U+201D bi coi la dau nhay dong -> script gay cu phap kho hieu.
# =====================================================================
param(
    [string]$BaseUrl = "http://localhost:8069",
    [string]$LoginName = "taixe01",
    [string]$Password = "lab@2026",
    [int]$JobId = 0,
    [switch]$Restore,
    [switch]$SkipDbCheck
)

$ErrorActionPreference = "Stop"

# PS 5.1: $PSScriptRoot rong trong param() default -> tinh trong than ham.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
$composeFile = Join-Path $repoRoot "infra\docker-compose.yml"
$dbName = "odoo17_lab"

$script:Fail = 0
function Step($msg) { Write-Output ""; Write-Output "--- $msg" }
function Ok($msg) { Write-Output "  [OK]   $msg" }
function Bad($msg) { Write-Output "  [FAIL] $msg"; $script:Fail++ }
function Info($msg) { Write-Output "         $msg" }

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        [string]$Token,
        $WebSession,
        [switch]$NewSession
    )
    $p = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        UseBasicParsing = $true
        TimeoutSec      = 30
    }
    if ($WebSession) { $p.WebSession = $WebSession }
    if ($Token) { $p.Headers = @{ "X-Lab-Token" = $Token } }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $p.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $p.ContentType = "application/json"
    }

    $result = @{ Status = 0; Content = ""; Session = $WebSession }
    try {
        if ($NewSession) {
            $r = Invoke-WebRequest @p -SessionVariable sess
            $result.Session = $sess
        }
        else {
            $r = Invoke-WebRequest @p
        }
        $result.Status = [int]$r.StatusCode
        $result.Content = $r.Content
    }
    catch {
        $resp = $_.Exception.Response
        if (-not $resp) {
            Write-Output "  [LOI] Khong ket noi duoc $($p.Uri): $($_.Exception.Message)"
            return $null
        }
        $result.Status = [int]$resp.StatusCode
        $stream = $resp.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $result.Content = $reader.ReadToEnd()
    }
    $result.Json = $null
    try { $result.Json = $result.Content | ConvertFrom-Json } catch { }
    return $result
}

function Docker-Sql($sql) {
    $out = & docker compose -f $composeFile exec -T db psql -U odoo -d $dbName -t -A -F '|' -c $sql 2>&1
    return $out
}

function Get-SqlValue($sql) {
    # Chi lay dong du lieu, bo cac dong thong bao cua psql (UPDATE 1, DELETE 2...).
    $rows = @(Docker-Sql $sql) | Where-Object { $_ -ne $null -and "$_".Trim() -ne "" -and "$_" -notmatch '^(UPDATE|DELETE|INSERT) \d+$' }
    return ($rows -join "")
}

function Save-TestImage([string]$vPath, [int]$w, [int]$h, [bool]$jpeg) {
    try {
        Add-Type -AssemblyName System.Drawing -ErrorAction Stop
        $bmp = New-Object System.Drawing.Bitmap $w, $h
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.Clear([System.Drawing.Color]::FromArgb(255, 30, 64, 175))
        $g.FillEllipse([System.Drawing.Brushes]::White, 40, 30, $w - 80, $h - 60)
        $g.Dispose()
        if ($jpeg) { $bmp.Save($vPath, [System.Drawing.Imaging.ImageFormat]::Jpeg) }
        else { $bmp.Save($vPath, [System.Drawing.Imaging.ImageFormat]::Png) }
        $bmp.Dispose()
        return $true
    }
    catch {
        # Fallback khong phu thuoc System.Drawing: PNG 1x1 hop le (68 byte).
        $png1x1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=="
        [System.IO.File]::WriteAllBytes($vPath, [System.Convert]::FromBase64String($png1x1))
        return $false
    }
}

# =====================================================================
# 0. Odoo song khong?
# =====================================================================
Step "0. Kiem tra Odoo o $BaseUrl"
$health = Invoke-Api -Method "GET" -Path "/web/login"
if ($null -eq $health) { Bad "Khong ket noi duoc Odoo. Chay: docker compose -f infra/docker-compose.yml up -d"; exit 1 }
if ($health.Status -eq 200) { Ok "Odoo tra 200 o /web/login" }
else { Bad "Odoo khong tra loi (status=$($health.Status))"; exit 1 }

# =====================================================================
# 1. Dang nhap tai xe -> token 8 gio
# =====================================================================
Step "1. Dang nhap '$LoginName' -> token"
$authResp = Invoke-Api -Method "POST" -Path "/lab/api/v1/auth/login" -NewSession -Body @{
    login    = $LoginName
    password = $Password
}
if ($null -eq $authResp) { Bad "Khong goi duoc endpoint dang nhap"; exit 1 }
if ($authResp.Status -eq 200 -and $authResp.Json.ok -eq $true) {
    $token = $authResp.Json.data.token
    $webSession = $authResp.Session
    Ok "Dang nhap thanh cong: uid=$($authResp.Json.data.uid) name='$($authResp.Json.data.name)' jobsToday=$($authResp.Json.data.jobsToday)"
    Info "token = $($token.Substring(0, 8))... (chi hien 8 ky tu dau)"
}
else {
    Bad "Dang nhap that bai: status=$($authResp.Status) body=$($authResp.Content)"
    exit 1
}

# =====================================================================
# 2. Lay job
# =====================================================================
Step "2. Lay viec hom nay cua tai xe"
$jobsResp = Invoke-Api -Method "GET" -Path "/lab/api/v1/driver/jobs" -Token $token -WebSession $webSession
if ($jobsResp.Status -ne 200 -or $jobsResp.Json.ok -ne $true) {
    Bad "GET /driver/jobs that bai: status=$($jobsResp.Status) body=$($jobsResp.Content)"
    exit 1
}
$jobs = @($jobsResp.Json.data)
Ok "Co $($jobs.Count) viec trong pham vi (assigned/en_route/picked_up)"
foreach ($j in $jobs) { Info "id=$($j.id) $($j.ref) state=$($j.state) window=$($j.windowFrom)-$($j.windowTo) shop='$($j.shop)'" }

# --- che do Restore: tra job ve en_route de chay lai duoc nhieu lan ---
if ($Restore) {
    Step "R. Restore: tra job ve en_route + xoa bang chung cu"
    $target = $JobId
    if (-not $target) {
        if ($jobs.Count -eq 0) { Bad "Khong co job nao de restore - chay reset-demo.ps1"; exit 1 }
        $target = $jobs[0].id
    }
    $sql = "delete from ir_attachment where lab_lay_hang_id = $target or (res_model = 'lab.yeu.cau.lay.hang' and res_id = $target and res_field = 'chu_ky'); " +
    "update lab_yeu_cau_lay_hang set state = 'en_route', so_kien_thuc_te = 0, khoi_luong_uoc_kg = 0, lab_client_uuid = null, ngay_giao = null where id = $target;"
    Docker-Sql $sql | Where-Object { "$_".Trim() -ne "" } | ForEach-Object { Info "$_" }
    Ok "Job $target da ve en_route, da xoa anh/chu ky cu. Chay lai script (khong -Restore) de test."
    exit 0
}

# Chon job de test: uu tien -JobId, khong thi job assigned/en_route dau tien
$job = $null
if ($JobId) {
    $job = $jobs | Where-Object { $_.id -eq $JobId } | Select-Object -First 1
    if (-not $job) {
        Bad "Job id=$JobId khong nam trong pham vi cua $LoginName (hoac da doi trang thai)"
        exit 1
    }
}
else {
    $job = $jobs | Where-Object { $_.state -in @("assigned", "en_route") } | Select-Object -First 1
}
if (-not $job) {
    Bad "Khong con job nao o trang thai assigned/en_route - chay: powershell -File infra/scripts/reset-demo.ps1"
    exit 1
}
Ok "Chon job id=$($job.id) $($job.ref) (state=$($job.state))"

# =====================================================================
# 3. Anh + chu ky (that, khong gia lap)
# =====================================================================
Step "3. Tao anh hang hoa (JPEG) + chu ky (PNG)"
$tmpDir = Join-Path $env:TEMP "e2e-capture"
if (-not (Test-Path $tmpDir)) { New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null }
$photoPath = Join-Path $tmpDir "anh-hang.jpg"
$signPath = Join-Path $tmpDir "chu-ky.png"

$realPhoto = Save-TestImage $photoPath 320 240 $true
[void](Save-TestImage $signPath 400 160 $false)
$photoB64 = [System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes($photoPath))
$signB64 = [System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes($signPath))
if ($realPhoto) { Ok "Anh JPEG $((Get-Item $photoPath).Length) byte, chu ky PNG $((Get-Item $signPath).Length) byte" }
else { Ok "Fallback PNG 1x1 (System.Drawing khong dung duoc) - API van kiem dung magic bytes" }

# =====================================================================
# 4. Gui capture (clientUuid de chong trung)
# =====================================================================
Step "4. POST /driver/jobs/$($job.id)/capture"
$clientUuid = [guid]::NewGuid().ToString()
$soKien = 5
$khoiLuong = 42.5
Info "clientUuid=$clientUuid packages=$soKien weightKg=$khoiLuong"

$body = @{
    photoBase64     = $photoB64
    signatureBase64 = $signB64
    packages        = $soKien
    weightKg        = $khoiLuong
    clientUuid      = $clientUuid
}
$cap = Invoke-Api -Method "POST" -Path "/lab/api/v1/driver/jobs/$($job.id)/capture" -Token $token -WebSession $webSession -Body $body
Info "status=$($cap.Status)"
if ($cap.Status -eq 200 -and $cap.Json.ok -eq $true) {
    Ok "Capture thanh cong: state=$($cap.Json.data.state) ($($cap.Json.data.stateLabel)) attachment=$($cap.Json.data.attachment_ids -join ',')"
    if ($cap.Json.data.state -ne "picked_up") { Bad "Trang thai phai la picked_up, thuc te: $($cap.Json.data.state)" }
}
else {
    Bad "Capture that bai: $($cap.Content)"
    exit 1
}

# =====================================================================
# 5. Gui LAI cung clientUuid -> phai la duplicate, khong tao bo anh thu 2
# =====================================================================
Step "5. Gui lai y het (mo phong mat mang / bam 2 lan) -> phai duplicate"
$cap2 = Invoke-Api -Method "POST" -Path "/lab/api/v1/driver/jobs/$($job.id)/capture" -Token $token -WebSession $webSession -Body $body
Info "status=$($cap2.Status)"
if ($cap2.Status -eq 200 -and $cap2.Json.ok -eq $true -and $cap2.Json.data.duplicate -eq $true) {
    Ok "Chong trung hoat dong: duplicate=true, attachment=$($cap2.Json.data.attachment_ids -join ',')"
}
else {
    Bad "Lan gui lai PHAI tra duplicate=true - thuc te: $($cap2.Content)"
}

# =====================================================================
# 6. Job bien khoi danh sach "viec hom nay"
# =====================================================================
Step "6. Job da roi khoi danh sach viec dang lam"
$jobsAfter = Invoke-Api -Method "GET" -Path "/lab/api/v1/driver/jobs" -Token $token -WebSession $webSession
$stillThere = @($jobsAfter.Json.data) | Where-Object { $_.id -eq $job.id -and $_.state -in @("assigned", "en_route") }
if ($stillThere.Count -eq 0) { Ok "Job $($job.id) khong con o assigned/en_route" }
else { Bad "Job van con trong danh sach dang lam" }

# =====================================================================
# 7. Kiem chung bang SQL (khong tin API, tin DB)
# =====================================================================
if (-not $SkipDbCheck) {
    Step "7. Kiem chung DB (lab_yeu_cau_lay_hang + ir_attachment)"
    $rowText = Get-SqlValue "select state || '|' || so_kien_thuc_te || '|' || khoi_luong_uoc_kg || '|' || coalesce(lab_client_uuid,'-') || '|' || (ngay_giao is not null) from lab_yeu_cau_lay_hang where id = $($job.id);"
    Info "DB: $rowText"
    # numeric cua Postgres co the in "42.5" hoac "42.50" -> chap nhan ca hai.
    if ($rowText -match "^picked_up\|$soKien\|42\.50?\|$clientUuid\|t") {
        Ok "DB dung: picked_up, $soKien kien, $khoiLuong kg, clientUuid khop, ngay_giao da set"
    }
    else {
        Bad "DB khong khop ky vong (picked_up|$soKien|$khoiLuong|$clientUuid|t)"
    }

    $anhText = Get-SqlValue "select count(*) from ir_attachment where lab_lay_hang_id = $($job.id);"
    Info "ir_attachment (anh hang): $anhText"
    if ($anhText -match "^1$") { Ok "Dung 1 anh cho 1 lan capture (gui lai khong tao them)" }
    else { Bad "So anh phai la 1, thuc te: $anhText" }

    $kyText = Get-SqlValue "select count(*) from ir_attachment where res_model = 'lab.yeu.cau.lay.hang' and res_id = $($job.id) and res_field = 'chu_ky';"
    Info "ir_attachment (chu ky, res_field=chu_ky): $kyText"
    if ($kyText -match "^1$") { Ok "Chu ky luu bang attachment=True (khong co cot chu_ky trong bang - dung Odoo)" }
    else { Bad "Phai co 1 chu ky, thuc te: $kyText" }

    $msgText = Get-SqlValue "select count(*) from mail_message where model = 'lab.yeu.cau.lay.hang' and res_id = $($job.id);"
    Info "mail_message (chatter): $msgText"
    if ($msgText -match "^\d+$" -and [int]$msgText -ge 1) { Ok "Chatter co $msgText tin nhan (message_post chay duoc)" }
    else { Bad "Chatter rong - message_post khong ghi duoc" }
}

# =====================================================================
# Ket qua
# =====================================================================
Write-Output ""
if ($script:Fail -eq 0) {
    Write-Output "=== KET QUA: DAT - luong capture chay that tu dau den cuoi ==="
    exit 0
}
else {
    Write-Output "=== KET QUA: THAT BAI - $($script:Fail) loi ==="
    exit 1
}
