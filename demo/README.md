# demo/ — sản xuất video demo 3 phút

Kịch bản đầy đủ: `docs/06-demo-video.md`. Thư mục này chỉ chứa **công cụ + tài nguyên**.

```
demo/
├── README.md                  file này
├── capture/
│   └── record-demo.mjs        Playwright quay cảnh 3,4,6,7 (Odoo web)
├── voice/
│   ├── generate-voice.ps1     sinh voice-over tiếng Việt (VieNeu AI)
│   └── *.wav                  file đã sinh (commit để demo offline)
├── assets/                    logo, ảnh minh hoạ cảnh 1-2
└── out/
    ├── applogictics-demo.mp4  BẢN CUỐI
    └── script.vi.srt          phụ đề
```

## Trình tự sản xuất

```powershell
# 1. Sinh voice-over (chạy 1 lần, KHÔNG gọi lúc demo)
$env:VIENEU_API_KEY = "vn_sk_..."
powershell -File demo/voice/generate-voice.ps1

# 2. Quay cảnh Odoo web (cần Odoo chạy + seed data)
cd demo/capture
npm install
node record-demo.mjs --scenes 3,4,6,7

# 3. Quay cảnh app KMP (thủ công, OBS/Game Bar, 1920x1080 60fps)
#    -> demo/capture/raw/app-driver.mp4

# 4. Dựng: cắt, ghép, chèn tiêu đề, ghép audio
#    (Remotion hoặc ffmpeg; xem docs/06 §5)

# 5. Xuất bản cuối
#    -> demo/out/applogictics-demo.mp4 (<= 3:00, 1080p, phụ đề tiếng Việt burn-in)
```

## Quy tắc

- **Không** gọi API ngoài (VieNeu, LLM) trong lúc demo. Mọi tài nguyên phải nằm trên đĩa.
- Mọi cảnh quay từ **dữ liệu thật** trong DB demo — không mock (Luật 9).
- File `demo/out/applogictics-demo.mp4` phải xong **trước demo ≥ 24h** (checklist T-24h, `docs/06` §8).
- Video là **lớp dự phòng cuối**: demo live hỏng → chiếu video, không sửa lỗi trước mặt người chấm quá 30 giây.
