# generate-voice.ps1 — sinh voice-over tiếng Việt cho video demo (VieNeu AI)
#
# Vì sao pre-generate: demo KHÔNG được phụ thuộc mạng/API. Chạy script này 1 lần,
# commit file .wav vào demo/voice/ (Luật 8 + docs/06 §5).
#
# Thông tin đã kiểm chứng (10/2026):
#   Base URL : https://api.vieneu.io/api/v1
#   Auth     : Authorization: Bearer vn_sk_...   (hoặc X-API-Key)
#   Engine   : chỉ còn `v4` (v3 đã ngừng từ 2026-09-24)
#   Voices   : voice id là TÊN TIẾNG VIỆT (vd "Ngọc Lan") -> resolve runtime qua GET /v1/voices?engine=v4
#   Speech   : POST /v1/audio/speech  (OpenAI-compatible, sync/stream)
#   Async    : POST /v1/tts -> GET /v1/tts/{jobId}
#   Tính tiền: theo ký tự, tối thiểu 50 ký tự -> gộp câu cho hiệu quả
#   Trial    : 7 ngày, 50 call/ngày; key `vn_test_` giới hạn 100 từ
#
# ⚠️ Tên field trong body: bản docs nói "OpenAI-compatible" nhưng KHÔNG công bố chi tiết
#    trong trang overview. Trước khi chạy thật, mở https://docs.vieneu.io/openapi.json
#    và đối chiếu field `input` / `text` / `voice` / `model`. Dùng -DryRun để xem trước request.
#
# Cách dùng:
#   $env:VIENEU_API_KEY = "vn_sk_..."
#   powershell -File demo/voice/generate-voice.ps1 -DryRun
#   powershell -File demo/voice/generate-voice.ps1 -Voice "Ngọc Lan"

param(
    [string]$ApiKey = $env:VIENEU_API_KEY,
    [string]$Engine = "v4",
    [string]$Voice  = "",
    [string]$OutDir = "$PSScriptRoot",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$Base = "https://api.vieneu.io/api/v1"

if (-not $ApiKey -and -not $DryRun) {
    Write-Host "Thiếu API key. Đặt `$env:VIENEU_API_KEY = 'vn_sk_...'" -ForegroundColor Red
    exit 1
}

$Headers = @{ Authorization = "Bearer $ApiKey"; "Content-Type" = "application/json" }

# ---------------------------------------------------------------------------
# Lời bình — khớp timeline docs/06 §4. Giữ mỗi đoạn <= ~40 từ để audio gọn.
# Đọc thuật ngữ theo cách người Việt nói (debit = "đê-bít", AWB = "vận đơn hàng không").
# ---------------------------------------------------------------------------
$Lines = @(
    @{ File = "01-mo-dau.wav";  Text = "Công ty gom hàng lẻ xuất khẩu này đang chạy bằng Zalo và Excel. Mỗi chuyến bay là một cuộc gọi điện. Mỗi đơn hàng là một dòng Excel dễ sai." },
    @{ File = "02-dinh-vi.wav"; Text = "Bài toán có mười chín bước, sáu phòng ban. Mười bước Odoo mười bảy đã làm được, chỉ cần cấu hình. Tôi viết thêm ba module: chuyến gom lô, điều tài xế, và hành trình quốc tế." },
    @{ File = "03-cs.wav";      Text = "Đây là màn hình làm việc của chăm sóc khách hàng. Bên trái là yêu cầu mới. Bên phải là chuyến sắp chốt. Không cần mở thêm tab nào." },
    @{ File = "04-qua-tai.wav"; Text = "Chuyến này vượt tải một trăm hai mươi phần trăm. Hệ thống không cho gỡ đơn ngay, phải có trưởng nhóm duyệt. Đơn bị gỡ quay về danh sách chờ chuyến sau, không biến mất." },
    @{ File = "05-wow.wav";     Text = "Cùng lúc đó, tài xế nhận việc trên app. Chụp ảnh, ký biên nhận, nhập số kiện thực tế. Không cần gọi điện xác nhận. Mất mạng vẫn làm được, có mạng thì tự đồng bộ." },
    @{ File = "06-hanh-trinh.wav"; Text = "Khách hàng tự mở đường dẫn tra cứu. Hàng đã lên máy bay, đã đến, đại lý đã giao. Bộ phận chăm sóc khách hàng không phải nghe điện thoại hỏi hàng đang ở đâu nữa." },
    @{ File = "07-tien.wav";    Text = "Cước tính theo ki lô gam, lấy từ cân thực tế ở kho. Phiếu ghi nợ được duyệt và khoá lại. Sau khi khoá, không ai sửa được. Muốn sửa phải mở khoá và có lý do." },
    @{ File = "08-ket.wav";     Text = "Ba module. Mười bước Odoo đã có sẵn. Sáu nhánh ngoại lệ. Đây là phần mềm chạy được, không phải slide." }
)

# Nếu có voice-lines.json (nguồn duy nhất, khớp docs/06 §4) thì dùng file đó thay vì mảng trên.
$linesJson = Join-Path $PSScriptRoot "voice-lines.json"
if (Test-Path $linesJson) {
    $json = Get-Content $linesJson -Raw -Encoding UTF8 | ConvertFrom-Json
    $Lines = $json | ForEach-Object { @{ File = "$($_.id).wav"; Text = $_.text } }
    Write-Host "Dùng lời bình từ voice-lines.json ($($Lines.Count) đoạn)"
}

# ---------------------------------------------------------------------------
# 1. Resolve voice nếu chưa chỉ định
# ---------------------------------------------------------------------------
if (-not $Voice) {
    if ($DryRun) {
        Write-Host "[DryRun] Sẽ gọi GET $Base/voices?engine=$Engine để lấy danh sách giọng." -ForegroundColor Yellow
        $Voice = "<tên giọng đầu tiên trả về, vd: Ngọc Lan>"
    } else {
        Write-Host "==> Lấy danh sách giọng (engine=$Engine)"
        $voices = Invoke-RestMethod -Uri "$Base/voices?engine=$Engine" -Headers $Headers -Method Get
        $list = if ($voices.data) { $voices.data } elseif ($voices.voices) { $voices.voices } else { $voices }
        $Voice = $list[0].name
        if (-not $Voice) { $Voice = $list[0].id }
        Write-Host "    Dùng giọng: $Voice"
        Write-Host "    (các giọng có: " + (($list | Select-Object -First 8 | ForEach-Object { $_.name }) -join ', ') + ")"
    }
}

# ---------------------------------------------------------------------------
# 2. Sinh audio từng đoạn
# ---------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$total = $Lines.Count
$i = 0

foreach ($line in $Lines) {
    $i++
    $out = Join-Path $OutDir $line.File
    $chars = $line.Text.Length

    # Body theo dạng OpenAI-compatible. ⚠️ đối chiếu docs.vieneu.io/openapi.json nếu lỗi 422.
    $body = @{
        model           = "vieneu-$Engine"
        voice           = $Voice
        input           = $line.Text
        response_format = "wav"
        speed           = 0.95     # chậm hơn một chút cho dễ nghe thuật ngữ
    } | ConvertTo-Json -Depth 5

    Write-Host ("[{0}/{1}] {2}  ({3} ký tự)" -f $i, $total, $line.File, $chars)

    if ($DryRun) {
        Write-Host "        POST $Base/audio/speech"
        Write-Host "        $($body -replace '\s+', ' ')"
        continue
    }

    if ($chars -lt 50) {
        Write-Host "        ⚠ dưới 50 ký tự (mức tính tiền tối thiểu) — gộp câu nếu có thể" -ForegroundColor Yellow
    }

    try {
        $resp = Invoke-WebRequest -Uri "$Base/audio/speech" -Headers $Headers -Method Post `
                                  -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -OutFile $out
        $size = (Get-Item $out).Length
        Write-Host ("        -> {0} ({1:N0} bytes)" -f $line.File, $size) -ForegroundColor Green
    } catch {
        Write-Host "        LỖI: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "        Kiểm tra: field body (đối chiếu openapi.json), hạn mức key, hạn mức 50 call/ngày (trial)." -ForegroundColor Yellow
    }

    Start-Sleep -Milliseconds 400   # tránh rate limit 300/phút
}

Write-Host "`nXong. File audio nằm trong: $OutDir"
Write-Host "Bước tiếp: ghép audio với video (ffmpeg / Remotion), xem docs/06 §5."
