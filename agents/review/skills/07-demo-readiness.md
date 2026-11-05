## Dùng khi nào

Làn 7 (bonus nhưng bắt buộc trước S4/P9) — chạy cuối mỗi phase để đo "cảnh này quay 3 lần có hỏng lần nào không", và toàn bộ ở P9.
Đầu vào: `docs/06-demo-video.md` (§3 storyboard · §2 wow moment · §7 phương án B · §8 checklist T-24h/T-60/T-15/T-0), `infra/scripts/reset-demo.ps1`, `demo/voice/`, build `apps/kmp`.
Đầu ra: verdict demo (`READY` / `NOT-READY`) + issue P0 cho mọi dead click, dữ liệu giả, hoặc bước không nhảy tới được trong 10 giây.

---

## 1. Cổng demo — chạy kịch bản `docs/06` **3 lần**

| Lần | Điều kiện | Đạt khi |
|---|---|---|
| L1 | Từ trạng thái sạch (`reset-demo.ps1`) | đi hết script, ≤ 3:00, không lỗi |
| L2 | Ngay sau L1, **không reset** | vẫn chạy (chứng minh demo lặp lại được) |
| L3 | Reset + tắt mạng ở cảnh AI (nếu có) | phương án B hoạt động, không màn hình chết |

3/3 đạt → `READY`. Bất kỳ lần nào hỏng → `NOT-READY` + P0 ghi rõ cảnh nào, giây thứ mấy.

## 2. Checklist môi trường (kiểm bằng mắt, có ảnh)

| # | Mục | Đạt khi |
|---|---|---|
| E1 | **DB snapshot** | có bản dump trước demo (`pg_dump odoo17_lab > backup_<ngày>.sql`), đường dẫn ghi trong `docs/06` |
| E2 | **Seed reset 1 lệnh** | `powershell -File infra/scripts/reset-demo.ps1` xong < 60s, dữ liệu đủ cảnh |
| E3 | **Offline fallback** | rút mạng: app vẫn mở, màn error có tiếng Việt + nút thử lại; Odoo local vẫn chạy |
| E4 | **App: chạy từ repo hay bản đóng gói** | ghi rõ cảnh nào dùng `./gradlew :composeApp:run`, cảnh nào dùng `runDistributable`; **không** trộn trong 1 cảnh |
| E5 | Trình duyệt | 1 profile sạch, zoom 100%, tắt notification, tắt extension |
| E6 | Đồng hồ + độ phân giải | 1920×1080, taskbar tự ẩn, không hiện thông báo OS khi quay |

## 3. Ba lớp fallback (`docs/04` §9)

1. `cd apps/kmp; ./gradlew :composeApp:run` — lớp chính, luôn chạy nếu có JDK.
2. `./gradlew :composeApp:runDistributable` — kiểm tra bản đóng gói (jlink dễ `ClassNotFoundException` **lúc chạy**, không phải lúc build).
3. **Video quay sẵn** — lớp cuối, phát được offline, có **phụ đề tiếng Việt**.

Cả 3 lớp phải được **kiểm thật trong ngày demo**, không chỉ "đã từng chạy". Thiếu lớp 2 → P1; thiếu lớp 3 → P0.

## 4. Timing ≤ 3:00 — bám đúng storyboard `docs/06` §3

| # | Mốc | Cảnh | Checkpoint |
|---|---|---|---|
| 1 | 0:00–0:15 | Vấn đề (Zalo + Excel) — **8 giây đầu đã nêu vấn đề** | 0:08 |
| 2 | 0:15–0:30 | Định vị: 19 bước, 10 native, chỉ viết 3 module | 0:30 |
| 3 | 0:30–1:00 | Chuẩn bị 1 đơn trên duo UI: kéo đơn sang chuyến | 1:00 |
| 4 | 1:00–1:15 | Ngoại lệ quá tải: đỏ 120% → duyệt giảm tải → 95% | 1:15 |
| 5 | 1:15–1:45 | ★ WOW: tài xế nhận việc → ảnh → ký → xác nhận → picking done | 1:45 |
| 6 | 1:45–2:10 | Hành trình ⑨⑩⑪ + khách mở link `access_token` | 2:10 |
| 7 | 2:10–2:35 | Cước kg → Debit → duyệt khoá → hoá đơn → thu tiền → khoá tháng | 2:35 |
| 8 | 2:35–3:00 | Kết: dashboard + "3 module · 10 bước native · 6/6 ngoại lệ" | 3:00 |

Bấm đồng hồ tại **4 checkpoint** (0:30 · 1:15 · 2:10 · 2:35). Lệch > 5 giây → P1. Vượt 3:00 → P1.
Không được cắt cảnh 1 và cảnh 8 để "dành thời gian cho WOW": không có vấn đề ở đầu và kết luận ở cuối là mất cả câu chuyện FDE.

## 5. Wow moment — đúng **một**, đã chốt ở `docs/06` §2

**"Một đơn, bốn màn hình"**: CS kéo 1 đơn vào chuyến ở app desktop → app tài xế nhận việc trong ~2 giây → ảnh + ký + xác nhận → Odoo cập nhật picking → khách mở link thấy timeline mới. (Wow phụ nếu kịp: voice tiếng Việt → tạo yêu cầu lấy hàng, ứng viên A của `docs/05` I-04.)
Kiểm: cả 4 màn dùng **cùng 1 đơn thật** (mã đơn + shop có trong seed), số kg khớp `stock.move.line`, độ trễ "2 giây" đã **đo thật**; phương án dự phòng "ghép 2 clip / 1 màn hình 3 cửa sổ" đã thử chạy. Sửa dữ liệu live cho đẹp → P0.

## 6. Không AI giả, không dead click

```powershell
rg -n "if demo|isDemo|fakeAi|mockAi|delay\(3|đang suy nghĩ|thinking" apps/kmp/composeApp/src
rg -n "TODO|sắp ra mắt|coming soon|NotImplemented" apps/kmp/composeApp/src addons/lab_*/views
```
Tiêu chí: mỗi nút trong đường demo **phải phản hồi trong < 500ms**; AI (nếu có) phải gọi dữ liệu thật và trả lời được **1 câu hỏi ngoài kịch bản** do người quay tự đặt. Không trả lời được → **cắt AI khỏi demo**, không diễn (Luật 9). AI giả → P0.

## 7. Voice-over tiếng Việt (pre-generated)

- File trong `demo/voice/*.wav`, sinh **trước demo ≥ 1 ngày** (VieNeu AI hoặc TTS dự phòng) — **không** gọi API lúc demo (`docs/05` I-03).
- Nghe lại toàn bộ 1 lượt: thuật ngữ đọc theo `agents/_shared/skills/vietnamese-business-language.md` §2 — **AWB** "ây-đắp-liu-bi", **manifest** "ma-ni-fét", **HBL** "H-B-L", **Debit** giữ nguyên 2 âm tiết, **CBM** "xi-bi-em". Đọc như tiếng Anh → P1.
- Tốc độ ~0.95×, không cắt giữa câu; mỗi cảnh có **phụ đề tiếng Việt** khớp audio.
- Có phương án B: nếu audio lỗi → phụ đề + nhạc, đã thử phát được offline.

## 8. PDF in đúng tiếng Việt

In thử **5 report** (`docs/02` §8): AWB, HBL, Manifest (landscape), Phiếu giao hàng (POD), Debit note.
Kiểm: dấu hiện đủ (ế, ộ, ữ, ạ), không ô vuông/tofu, tổng kg/CBM khớp màn hình, số trang đúng, header không tràn.
Lỗi font = P0 cho demo (R5) vì "lỗi font là lỗi demo hay gặp nhất" (`docs/03` P4).

## 9. Drill "người phỏng vấn ngắt lời" — nhảy tới bất kỳ bước nào ≤ 10 giây

Chuẩn bị sẵn: 1 cửa sổ Odoo ở màn hình chính, 1 app KMP, 1 tab `/track`, 1 thư mục PDF đã in, 1 file lịch sử chatter.

| Đích đến | Cách nhảy ≤ 10 giây |
|---|---|
| Chuyến cụ thể | Bookmark `lab.chuyen` + search theo mã `CH/AIR/26/0001` |
| Debit đã khoá | Bookmark filter "Debit đã khoá" (`account.move` journal Debit, state posted) |
| Nhánh ② giảm tải | Mở chuyến quá tải từ dashboard (đã cài sẵn filter) |
| Nhánh ① kho chờ | Menu action `lab_core.action_hang_cho` (bookmark) |
| Nhánh ⑥ khiếu nại | Bookmark ticket đang mở |
| App tài xế | Đã login sẵn, màn "Việc hôm nay" |
| `/track` của khách | Tab sẵn với token trong URL |
| Báo cáo doanh thu | Bookmark pivot đã cấu hình |
| Chứng từ PDF | Thư mục `demo/pdf/` mở sẵn |

Luyện: người review hô 5 đích **ngẫu nhiên**, mỗi đích ≤ 10 giây. Trượt 2/5 → P1. Không chuẩn bị bookmark → P1.

## 10. Checklist T-60 / T-15 / T-0

Bản chính thức là `docs/06` §8 (có thêm **T-24h**). Bảng dưới là **cách RV-01 kiểm** — nếu lệch, `docs/06` thắng.

| Mốc | Việc phải xong | Xong khi |
|---|---|---|
| **T-60'** | Snapshot DB | `pg_dump odoo17_lab` mới, đã copy sang ổ khác |
| **T-60'** | Seed lại | `reset-demo.ps1` chạy 1 lần, dữ liệu đủ mọi cảnh |
| **T-60'** | Bản đóng gói | `runDistributable` build + chạy thử (không chỉ `run`) |
| **T-60'** | Chứng từ | In 5 PDF, kiểm dấu tiếng Việt (§8) |
| **T-60'** | Voice-over | Nghe lại toàn bộ 1 lượt (§7) |
| **T-15'** | Chạy kịch bản lần cuối | Bấm đồng hồ, ≤ 3:00 |
| **T-15'** | Môi trường | Đóng app thừa, tắt notification, cắm sạc |
| **T-15'** | 9 đích của §9 | Mở sẵn tab/window + file video fallback |
| **T-15'** | Kết nối | Odoo `http://localhost:8069` login được |
| **T-0** | Mở đầu | Đồng hồ chạy, nói câu 8 giây đúng script |
| **T-0** | Kỷ luật | Không bấm ngoài đường demo; click lạc → nói rõ "quay lại" |
| **T-0** | Người thứ 2 | Giữ: đường dẫn snapshot, video fallback, log lỗi |

## Checklist nhanh

- [ ] Kịch bản `docs/06` chạy 3/3 lần đạt, lần 2 không cần reset
- [ ] Snapshot DB + `reset-demo.ps1` < 60s đã kiểm trong ngày demo
- [ ] 3 lớp fallback đều chạy được; có video quay sẵn + phụ đề
- [ ] Timing ≤ 3:00 đo bằng đồng hồ thật; 8 giây đầu nêu vấn đề
- [ ] Wow moment có **tên** trong `docs/06`, dùng dữ liệu thật
- [ ] Không AI giả, không dead click; AI trả lời được 1 câu ngoài kịch bản
- [ ] Voice-over pre-generated, nghe lại, thuật ngữ đọc kiểu Việt
- [ ] 5 PDF in đúng dấu tiếng Việt; drill nhảy 5/5 đích ≤ 10 giây
