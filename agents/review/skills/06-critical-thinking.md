## Dùng khi nào

Làn 6 — làn **chỉ RV-01 làm được**: phản biện thiết kế, không phải bắt lỗi cú pháp. Chạy cuối mỗi phase, bắt buộc ở P6 (đo 6 điểm quay về) và trước demo.
Đầu vào: `docs/09-open-questions.md`, `docs/adr/`, `docs/review/red-team.md`, `docs/06-demo-video.md` §7 (phương án B — I-12 gọi là "§fallback"), `docs/ai-log.md`.
Đầu ra: danh sách câu hỏi **chưa có câu trả lời cụ thể** + issue P1/P2. "Trả lời chung chung" = chưa trả lời.

---

## 1. Bốn artifact bắt buộc (`docs/05` I-12)

| Artifact | Tối thiểu | Ai viết | RV kiểm bằng | Thiếu → |
|---|---|---|---|---|
| `docs/09-open-questions.md` | **≥ 12** câu (đủ 4 phần: câu hỏi · giả định tạm · mức `H/M/L` · cách kiểm chứng), hiện đã có 18 dòng `OQ-xx` | cả 3 | đếm dòng; soi 3 câu ngẫu nhiên xem giả định có hành động tương ứng trong code/ADR không | P1 |
| `docs/adr/` | **≥ 5** ADR, mỗi cái có **phương án bị loại + lý do** | BE/FE đề xuất, RV duyệt | đọc mục "Alternatives rejected" | P1 |
| `docs/review/red-team.md` | **≥ 10** kịch bản (6 cột: kịch bản · chỗ nổ · phát hiện sớm · xử lý · trạng thái), hiện đã có 15 | RV-01 | đối chiếu §4 + §5 bên dưới | P0 nếu 0 file |
| `docs/06-demo-video.md` §7 (Phương án B cho từng cảnh) | Phương án B cho mọi rủi ro R1–R10 (`docs/03` §6) | RV + FE | mỗi R có phương án, không có "sẽ cố gắng" | P1 |

Nếu `docs/06-demo-video.md` chưa tồn tại → issue P1 (thiếu artifact), không tự tạo thay người khác.

## 2. Cách review `docs/09-open-questions.md`

Định dạng thật của file (giữ nguyên, không tự chế): bảng `ID | Câu hỏi | Giả định tạm | Mức | Ai | Kiểm chứng bằng | Trạng thái`, mức **`H`** = đã chạy thật · **`M`** = đọc source/docs chính thức · **`L`** = suy luận.
Kiểm 3 điều:
1. Mỗi giả định có **cách kiểm chứng** cụ thể (test/grep/ADR), không phải "sẽ xem lại".
2. Câu có mức `L` phải có marker tương ứng trong code: `rg -n "ASSUMPTION" addons/ apps/kmp` — số marker khớp số câu `L` đang dùng.
3. **Câu hỏi đã được giải quyết bằng code mà bảng chưa cập nhật trạng thái** → P1 (đây là lỗi phổ biến nhất; "Cách dùng file này" §2 của `docs/09` đã quy định).
Câu đã chốt trong `docs/00` §9 (công thức cước, 1 công ty, 2 cấp duyệt) **không** tính vào 12 — phải là câu còn mở. Câu hỏi không có giả định ("cần xác nhận lại") = vô dụng → P1.

## 3. Cách review `docs/adr/`

ADR hợp lệ = 5 dòng: **Bối cảnh · Quyết định · Phương án bị loại + lý do · Hệ quả · Người duyệt**.
Bộ ADR tối thiểu của dự án: ADR-001 `lab_core` · ADR-002 dashboard CE (`spreadsheet.dashboard`) · ADR-003 chuyến vs `stock_picking_batch` + bộ OCA `tms` (`docs/01` §3.2) · ADR-004 `lab_api` (vì sao cần controller, auth) · ADR-005 cầu nối `helpdesk` (`docs/07` ⑥) · ADR-006 Material 2 vs Material 3.
Fail: ADR chỉ có "quyết định", không có phương án bị loại → **không phải ADR**, là ghi chú (P2).

## 4. Cách review `docs/review/red-team.md`

Định dạng thật: 6 cột `# | Kịch bản | Chỗ sẽ nổ | Phát hiện sớm | Xử lý | Trạng thái`; trạng thái dùng `⏳ Px` (chưa xong) · `✅` (đã có bằng chứng) · `📋 backlog`.
Kiểm: mỗi dòng "chỗ sẽ nổ" phải chỉ **model/màn cụ thể** (không viết "hệ thống chậm"); mỗi dòng `✅` phải chỉ được bằng chứng (test/lệnh).
**Luật của file:** trước demo, **không còn dòng `⏳` nào ở các nhánh được demo**. Còn `⏳` ở nhánh demo → P0, hoặc cắt nhánh đó khỏi demo và nói trước là "chưa làm", **không giấu**.
`📋 backlog` chỉ hợp lệ khi việc đó ngoài 19 bước và không phá Luật 1–5.

## 5. Ngân hàng 25 câu hỏi red-team (dùng để đối chiếu §4)

### A. Tải chuyến & điều phối

| Câu hỏi | Vì sao quan trọng | Trả lời TỐT | Trả lời DỞ |
|---|---|---|---|
| Chuyến quá tải 120% thì hệ thống làm gì? | Chỗ phân biệt "chặn" và "kiểm soát" | Cảnh báo đỏ + chặn duyệt vào chuyến + wizard giảm tải có tier duyệt → line `waitlist` → gán chuyến khác, có test | `raise UserError` cấm vượt tải (không giảm tải được) hoặc chỉ log |
| Hãng dời cut-off sớm 6 giờ, 40 đơn đang `booked`? | Rủi ro thật hằng ngày | `base_automation` tạo activity cho người phụ trách + danh sách đơn chưa picked so với cut-off mới | "sửa `cut_off_datetime` là xong" |
| Gỡ 1 đơn khỏi chuyến: dữ liệu gì mồ côi? | Luật 5 + D6 | Không `unlink`: line → `removed`/`waitlist` + `ly_do_go` + `message_post` lên cả `sale.order`; picking giữ `lab_chuyen_id` làm vết | Xoá line hoặc set null |
| 2 tài xế cùng nhận 1 job? | Tranh chấp dữ liệu | Kiểm state khi accept; người sau nhận `UserError` "Việc đã được nhận"; 1 picking duy nhất | Cả 2 `accepted`, sinh 2 picking |
| Cùng 1 kiện bị gán vào 2 chuyến? | Manifest in trùng, sai tải trọng | Ràng buộc unique/constraint ở `lab.chuyen.line` cho line active | Không kiểm gì |

### B. Ngoại lệ & mất mát

| Câu hỏi | Vì sao quan trọng | Trả lời TỐT | Trả lời DỞ |
|---|---|---|---|
| Phát hiện thiếu kiện **sau khi đã xuất**? | Ngoại lệ ④ — dễ bị bỏ | Backorder native + đối chiếu `stock.quant.package` với manifest; nếu mất → credit note + ticket | Tạo model `kien_thieu`; sửa `quantity` cho khớp |
| Khách không lấy hàng 30 ngày? | Đề bài không nói, nhưng chủ doanh nghiệp sẽ hỏi | Nêu quy trình: nhắc bằng activity → lưu kho có phí → thanh lý; ghi backlog vì ngoài 19 bước | "ngoài phạm vi" rồi im |
| Hàng mất trên đường: ai chịu, chứng từ nào? | REA: mất hàng phải có bút toán | Ticket `helpdesk_mgmt` + credit note `account.move` + cập nhật `lab.hanh.trinh.stage` | Chỉ post chatter |
| KHO CHỜ có hàng 7 ngày chưa gán khách? | Ngoại lệ ① dễ thành "kho ảo" | Báo cáo/activity cảnh báo; test khẳng định hàng không lọt kho chính | Không ai biết hàng đang ở đâu |
| Hàng hỏng do đóng gói, khách đòi bồi thường? | REA "bồi thường" | Ticket → kiểm tra → credit note hoặc return picking (native), có vết | Sửa giá Debit trực tiếp |

### C. Tiền & kế toán

| Câu hỏi | Vì sao quan trọng | Trả lời TỐT | Trả lời DỞ |
|---|---|---|---|
| Debit đã khoá mà cần sửa 1 dòng? | ⑭ + audit trail | "Yêu cầu mở khoá": `group_manager` + lý do + chatter; nếu đã gửi khách → credit note/điều chỉnh, **không** sửa lịch sử | Mở khoá im lặng, hoặc sửa DB |
| Debit đã gửi khách mới phát hiện sai giá? | Không được sửa chứng từ đã phát hành | Credit note + Debit mới (native), giữ nguyên chứng từ cũ | Sửa `account.move` đã post |
| Lệch kg giữa picking và Debit? | "Một nguồn số liệu" (pain P2) | 1 nguồn: `stock.move.line` → snapshot `lab.chuyen.line.khoi_luong_kg` → Debit; có test REA khẳng định bằng nhau | 2 nơi nhập tay, không đối chiếu |
| Đổi air ↔ sea **sau khi** Debit đã khoá, cước đổi? | Ngoại lệ ⑤ + cước divisor 6000/1000 | Chặn chuyển khi Debit locked; nếu buộc → mở khoá có duyệt → tính lại chargeable → Debit điều chỉnh | Sửa `loai_van_chuyen`, cước không tính lại |
| Kế toán đã `period_lock_date`, CS cần Debit bù tháng trước? | Không được phá lock | Bút toán điều chỉnh kỳ hiện tại (native) + ghi chú lý do | Nhờ admin xoá lock date |

### D. Tài xế, offline, token

| Câu hỏi | Vì sao quan trọng | Trả lời TỐT | Trả lời DỞ |
|---|---|---|---|
| Tài xế offline gửi capture 2 lần (retry)? | Trùng dữ liệu thật | `client_uuid` idempotent, BE dedupe (trả lại kết quả cũ), 1 bộ attachment | Trùng ảnh + trùng số kiện |
| Mất mạng giữa lúc xác nhận? | D3 + R3 | Outbox + badge "3 việc chờ đồng bộ"; không cho confirm khi thiếu ảnh/chữ ký kể cả offline | Mất dữ liệu hoặc confirm rỗng |
| Tài xế nghỉ việc, 7 job đang `assigned`? | Job treo = khách không được lấy hàng | Reassign qua UI (đổi `driver_id`) + activity cho điều phối; token cũ hết hạn/thu hồi | Job treo, không ai thấy |
| Token tài xế bị lộ (mất máy)? | Bảo mật | Token 8h + lưu hash + revoke; scope theo user nên thiệt hại hẹp | Token sống vĩnh viễn, không revoke |
| Tài xế nhập kg sai (1000 thay vì 100)? | Cước sai → Debit sai | Validate biên so với `khoi_luong_uoc_kg`, cảnh báo lệch > 3×; kho cân lại là nguồn chuẩn | Tin số tài xế làm cước |

### E. Scale, dữ liệu, rủi ro demo

| Câu hỏi | Vì sao quan trọng | Trả lời TỐT | Trả lời DỞ |
|---|---|---|---|
| 1.000 đơn/ngày thay vì 10: cái gì nổ trước? | Câu bắt buộc phải trả lời trong demo (`docs/05` I-12) | Trả lời cụ thể: list không phân trang → N+1; compute không `store`; upload đồng bộ; đề xuất index + `store=True` + pagination + xử lý outbox theo lô | "Odoo scale tốt" |
| Khách forward link `/track` qua Zalo? | Rò dữ liệu nội bộ | Token hết hạn + whitelist field; không giá, không tài xế, không đối tác | Trả cả object `lab.hanh.trinh` |
| DB demo trắng sáng ngày demo? | Luật 8 | `reset-demo.ps1` + snapshot + 3 lớp fallback (`docs/06`) | "chắc không sao" |
| LLM trả lời sai ngay lúc demo AI? | Luật 9 — cấm giả lập | AI chỉ **gợi ý**, người bấm xác nhận; log prompt; có phương án không-AI | AI tự ghi dữ liệu, không kiểm chứng |
| Chỉ còn 1 ngày: cắt gì? | Kiểm tra ưu tiên FDE | Cắt P8 dashboard/backlog, **giữ** ngoại lệ + test + tiếng Việt (`AGENTS.md` §7) | Cắt test hoặc ngoại lệ để kịp demo |

## 6. Chấm điểm làn 6 (0–5)

| Điểm | Mô tả |
|---|---|
| 0 | Không có artifact nào; trả lời "chắc ổn" |
| 3 | Đủ 4 artifact nhưng câu trả lời chung; ADR thiếu phương án bị loại; red-team không nêu cách chặn |
| 5 | Đủ + mỗi câu có giả định + test tương ứng; ADR có ≥ 1 phương án bị loại **có số liệu**; red-team ≥ 10 kịch bản có trạng thái; trả lời được câu 1.000 đơn/ngày bằng số cụ thể |

Làn 6 = 0 ở lần review cuối → **BLOCKED** (không được coi là "nice to have": đây là tiêu chí chấm của sếp).

## Checklist nhanh

- [ ] `docs/09-open-questions.md` ≥ 12 câu, mỗi câu có giả định tạm + mức chắc chắn
- [ ] `docs/adr/` ≥ 5 ADR, mỗi ADR có phương án bị loại + lý do
- [ ] ADR-003 đã trả lời bộ OCA `tms` (`docs/01` §3.2) — dù chọn hay không
- [ ] `docs/review/red-team.md` ≥ 10 kịch bản, có cột "cách chặn + trạng thái"
- [ ] `docs/06` §7 có phương án B cho từng cảnh + đủ R1–R10
- [ ] 25 câu §5 đã đối chiếu: câu nào chưa trả lời cụ thể đã thành issue P1
- [ ] Câu "1.000 đơn/ngày" có câu trả lời **cụ thể** dùng được trong demo
- [ ] Không có chỗ nào "chỉ đúng khi demo": hardcode, magic number, `if demo`
