# 06 — KỊCH BẢN DEMO VIDEO (3 phút)

> Yêu cầu của sếp: *"Nếu cần thì dùng opus 5.5 làm product demo video, VieNeu AI, .. 'wow moment'. Hoàn thành việc bằng AI… hạn chế thủ công."*
> File này là **kịch bản + pipeline + checklist**, không phải ý tưởng.

---

## 1. Nguyên tắc

1. **3:00 là giới hạn cứng.** Người chấm xem 30 tập như tập này. Cắt mạnh tay.
2. **Kể theo vai FDE**, không kể theo tính năng: "tôi đến công ty này, họ đang làm bằng Zalo và Excel, đây là thứ tôi giao".
3. **Chỉ kể 1 luồng + 2 ngoại lệ.** 19 bước để trong sơ đồ, không đưa vào video.
4. **Wow moment phải là thật**: dữ liệu thật, chạy thật, cùng lúc 2 thiết bị.
5. **Mọi cảnh đều có phương án B** (§7).

---

## 2. Wow moment (chốt)

| | |
|---|---|
| **Cảnh** | **"Một đơn, bốn màn hình"** |
| **Nội dung** | CS kéo 1 đơn vào chuyến ở app desktop → **app tài xế (màn thứ 2) nhận việc trong 2 giây** → tài xế chụp ảnh + ký + xác nhận → Odoo cập nhật picking → **khách mở link thấy timeline mới** |
| **Vì sao wow** | Chứng minh đây là **một hệ thống**, không phải 4 demo rời. Người xem hiểu ngay giá trị "hết Zalo, hết Excel". |
| **Rủi ro** | Cần 2 màn hình/cửa sổ + track page cùng chạy. |
| **Dự phòng** | Nếu không đủ màn hình: quay 2 clip rồi ghép, hoặc dùng 1 màn hình với 3 cửa sổ cạnh nhau + zoom. |
| **Wow phụ (nếu kịp)** | CS nói tiếng Việt → AI tạo yêu cầu lấy hàng (`docs/05` I-04 ứng viên A). |

---

## 3. Storyboard 8 cảnh

| # | Thời lượng | Cảnh | Trên màn hình | Lời bình (voice-over) | Cách quay |
|---|---|---|---|---|---|
| **1** | 0:00–0:15 | **Vấn đề** | Split screen: bảng Excel lộn xộn + chat Zalo "đơn này ai lấy?" | "Công ty gom hàng lẻ này đang chạy bằng Zalo và Excel. Mỗi chuyến bay là một cuộc gọi điện." | Ảnh tĩnh + chữ động (Remotion) |
| **2** | 0:15–0:30 | **Định vị** | Sơ đồ 19 bước (`page-2.png`) thu nhỏ, highlight 3 module đỏ | "19 bước, 6 phòng ban. Odoo 17 đã làm được 10 bước. Tôi chỉ viết 3 module: chuyến, tài xế, hành trình." | Ảnh sơ đồ + animation highlight |
| **3** | 0:30–1:00 | **Chuẩn bị 1 đơn** | App duo UI: pane trái danh sách yêu cầu, pane phải chi tiết. Kéo 1 đơn sang chuyến | "CS nhận đơn từ shop. Không cần Zalo nữa — mọi thứ nằm trong một chỗ." | Playwright / quay màn hình app |
| **4** | 1:00–1:15 | **Ngoại lệ: quá tải** | Thanh tải chuyến chuyển **đỏ 120%** → nút "Giảm tải" → cần trưởng nhóm duyệt → 95% | "Chuyến vượt tải. Hệ thống không cho gỡ đơn — phải có người duyệt. Đơn về danh sách chờ chuyến sau." | Playwright (Odoo web) |
| **5** | 1:15–1:45 | **★ WOW** | App tài xế (cửa sổ 2) nhận việc → ảnh → ký → xác nhận → Odoo picking done | "Tài xế nhận việc ngay trên app. Chụp ảnh, ký biên nhận, xác nhận số kiện. Không cần gọi điện." | 2 cửa sổ cạnh nhau |
| **6** | 1:45–2:10 | **Hành trình & khách** | Timeline ⑨→⑩→⑪ + trình duyệt mở link `access_token` của khách | "Khách tự mở link thấy hàng đang ở đâu. CS không phải nghe điện thoại nữa." | Playwright (browser) |
| **7** | 2:10–2:35 | **Tiền** | Debit tạo từ chuyến → **duyệt khoá** → hoá đơn → thu tiền → khoá tháng | "Cước tính theo kg. Debit khoá lại sau khi duyệt — không ai sửa được sau khi gửi khách." | Playwright (Odoo) |
| **8** | 2:35–3:00 | **Kết** | Dashboard sếp + dòng chữ: 3 module · 10 bước native · 2.1k dòng code · 6/6 ngoại lệ | "Ba module. Mười bước Odoo đã có. Sáu nhánh ngoại lệ. Đây là phần mềm chạy được, không phải slide." | Remotion + dashboard thật |

---

## 4. Lời bình đầy đủ (dùng trực tiếp cho VieNeu)

```
00:00  Công ty gom hàng lẻ xuất khẩu này đang chạy bằng Zalo và Excel.
       Mỗi chuyến bay là một cuộc gọi điện. Mỗi đơn hàng là một dòng Excel dễ sai.

00:15  Bài toán có mười chín bước, sáu phòng ban.
       Mười bước Odoo mười bảy đã làm được, chỉ cần cấu hình.
       Tôi viết thêm ba module: chuyến gom lô, điều tài xế, và hành trình quốc tế.

00:30  Đây là màn hình làm việc của chăm sóc khách hàng.
       Bên trái là yêu cầu mới. Bên phải là chuyến sắp chốt. Không cần mở thêm tab nào.

01:00  Chuyến này vượt tải một trăm hai mươi phần trăm.
       Hệ thống không cho gỡ đơn ngay — phải có trưởng nhóm duyệt.
       Đơn bị gỡ quay về danh sách chờ chuyến sau, không biến mất.

01:15  Cùng lúc đó, tài xế nhận việc trên app. Ảnh chụp, chữ ký, số kiện thực tế.
       Không cần gọi điện xác nhận. Mất mạng vẫn làm được, có mạng thì tự đồng bộ.

01:45  Khách hàng tự mở đường dẫn tra cứu. Hàng đã lên máy bay, đã đến, đại lý đã giao.
       Bộ phận chăm sóc khách hàng không phải nghe điện thoại hỏi hàng đang ở đâu nữa.

02:10  Cước tính theo ki-lô-gam, lấy từ cân thực tế ở kho.
       Debit được duyệt và khoá lại. Sau khi khoá, không ai sửa được. Muốn sửa phải mở khoá có lý do.

02:35  Ba module. Mười bước Odoo đã có sẵn. Sáu nhánh ngoại lệ.
       Đây là phần mềm chạy được — không phải slide.
```

**Quy tắc đọc:** thuật ngữ Việt hóa theo cách người Việt nói — "debit" đọc là *"đê-bít"*, "AWB" đọc *"ây-đắp-liu-bi"* hoặc nói "vận đơn hàng không", "CBM" nói "mét khối". Không đọc tiếng Anh kiểu Mỹ.

---

## 5. Pipeline (đã kiểm chứng công cụ)

```
[1] KỊCH BẢN     claude-opus-5-5  ($4/$20 per MTok, 1M context, adaptive thinking)
                  → đưa docs/00 + docs/01 + docs/02 + file này vào 1 lần
                  → yêu cầu: 8 cảnh, mỗi cảnh có mục tiêu + câu chốt
                  → NGƯỜI đọc lại và sửa 30-50%
        ↓
[2] SEED DATA    script sinh dữ liệu demo (BE-01) → DB sạch, cùng 1 câu chuyện
        ↓
[3] QUAY         Playwright recordVideo (1920×1080)
                  · context = browser.newContext({recordVideo:{dir,size}})
                  · ⚠️ file video CHỈ được ghi khi browserContext.close()
                  · show actions (cursor/click highlight) = true
                 App KMP: quay màn hình (OBS/Windows Game Bar) 60fps
        ↓
[4] VOICE        VieNeu AI → pre-generate WAV vào demo/voice/
                  · POST https://api.vieneu.io/api/v1/audio/speech
                  · Authorization: Bearer vn_sk_...
                  · engine hiện tại CHỈ `v4`; voice id lấy từ GET /v1/voices?engine=v4
                    (tên tiếng Việt, ví dụ "Ngọc Lan") — resolve lúc chạy, đừng hardcode id
                  · tính tiền theo ký tự, tối thiểu 50 ký tự → gộp câu cho hiệu quả
        ↓
[5] DỰNG         Remotion (tiêu đề, highlight, số liệu động)
                  npx remotion render <entry> <composition-id> out/video.mp4
                 hoặc ffmpeg nếu chỉ cắt/ghép/ghép audio
        ↓
[6] XEM LẠI 3 LẦN, mỗi lần cắt bớt. Lần 3 phải ≤ 3:00.
```

**Phụ đề:** phụ đề tiếng Việt **hard-code** vào video (người xem hay tắt tiếng). Xuất cả `demo/script.vi.srt`.

---

## 6. Trình tự sản xuất (không làm ngược)

| Bước | Khi nào | Ghi chú |
|---|---|---|
| Chốt storyboard + lời bình | P0/P1 | Có thể sửa, nhưng phải chốt trước P7 |
| Quay cảnh 3,4,6,7 (Odoo web) | Cuối P5 | Playwright script hoá — quay lại được nhiều lần |
| Quay cảnh 5 (app + Odoo) | Cuối P7 | Cần 2 cửa sổ; quay 3 lần, chọn bản tốt |
| Sinh voice | P9 | Sau khi lời bình chốt |
| Dựng + xuất | P9 | Remotion/ffmpeg |
| Xem lại + cắt | P9 | 3 vòng |

**Cấm:** quay video trước khi tính năng chạy thật.

---

## 7. Phương án B cho từng cảnh

| Cảnh | Rủi ro | Phương án B |
|---|---|---|
| 1, 2 | Không có ảnh Excel/Zalo thật | Tự tạo ảnh minh hoạ có nhãn "mô phỏng" |
| 3 | Duo UI lỗi khi resize | Quay ở kích thước cố định 1440×900, không kéo |
| 4 | Tier validation chưa cấu hình | Quay bằng user manager đã duyệt sẵn |
| **5 (wow)** | App không nhận việc realtime | Quay 2 clip riêng + ghép; hoặc dùng chung 1 cửa sổ với tab chuyển |
| 6 | Portal access_token lỗi | Mở bằng route Odoo có sẵn + quay trước |
| 7 | PDF font tiếng Việt lỗi | Dùng ảnh chụp báo cáo đã render đúng |
| 8 | Dashboard chưa có | Dùng pivot view (native) |
| Toàn bộ | Máy demo hỏng | **Chiếu video** — video phải xong trước demo ≥ 24h |

---

## 8. Checklist trước demo (T-60 / T-15 / T-0)

### T-24h
- [ ] Video MP4 xuất bản cuối, xem trên máy demo (không chỉ máy làm).
- [ ] File audio trong `demo/voice/` phát được offline.
- [ ] Snapshot DB `odoo17_lab_demo`.
- [ ] `reset-demo.ps1` chạy < 60s.
- [ ] App: `runDistributable` chạy được, không chỉ `run`.
- [ ] Có bản `packageUberJarForCurrentOS` dự phòng (không cần WiX/signature).

### T-60 phút
- [ ] Odoo chạy, login 4 user (CS, tài xế, kế toán, manager) OK.
- [ ] DB đúng snapshot; dữ liệu demo đúng kịch bản.
- [ ] App login được bằng API key.
- [ ] 2 cửa sổ app sẵn sàng (CS + tài xế).
- [ ] Trình duyệt mở sẵn link track.
- [ ] Tắt thông báo, tắt Wi-Fi nếu demo offline, sạc pin.

### T-15 phút
- [ ] Chạy thử luồng chính 1 lần **không có script**.
- [ ] Chạy thử cảnh 4 (quá tải → duyệt → giảm tải).
- [ ] Chạy thử cảnh 5 (app nhận việc).
- [ ] Kiểm tra PDF in ra tiếng Việt đúng.
- [ ] Đóng mọi app khác (Slack, Discord), tắt auto-update.

### T-0
- [ ] Bắt đầu bằng **câu chuyện**, không bằng "đây là app của em".
- [ ] Nếu hỏng: nói rõ "phần này em có video quay sẵn" → chiếu. Không sửa lỗi trước mặt người chấm quá 30 giây.

---

## 9. Ba câu hỏi sẽ bị hỏi (chuẩn bị sẵn câu trả lời)

| Câu hỏi | Trả lời phải có |
|---|---|
| "Sao không dùng module có sẵn / sao không mua phần mềm?" | Chỉ ra **10 bước đã dùng native** + 3 module vì Odoo không có consolidation/driver dispatch/journey → dẫn `docs/01` |
| "Nếu 1.000 đơn/ngày thì sao?" | Nêu cụ thể: list phân trang, `_read_group` thay `read_group`, attachment trên storage, hàng đợi đồng bộ app, index; nêu cái **sẽ nổ trước** (báo cáo pivot + attachment) |
| "AI ở đâu, hay chỉ là demo?" | Chỉ vào AI thật: voice, command palette, gợi ý giảm tải — **kèm prompt log** và lần AI trả lời sai đã bị bắt (`docs/ai-log.md`) |

---

## 10. Định nghĩa "video xong"

- [ ] ≤ 3:00, 1080p, có phụ đề tiếng Việt.
- [ ] 8 giây đầu đã nêu vấn đề (không logo, không "xin chào").
- [ ] Có đúng **1** wow moment được đặt tên và nó dùng dữ liệu thật.
- [ ] Voice-over tiếng Việt tự nhiên, thuật ngữ đọc đúng cách người Việt nói.
- [ ] Không có cảnh nào là mock/giả lập.
- [ ] Có ảnh chụp sơ đồ 19 bước + con số "3 module · 10 native · 6 ngoại lệ".
- [ ] File: `demo/out/applogictics-demo.mp4` + `demo/script.vi.srt`.
