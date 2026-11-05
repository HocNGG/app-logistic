# 07 — SÁU NHÁNH NGOẠI LỆ (chi tiết triển khai)

> Nguồn: trang 3 của `so-do-nghiep-vu.pdf` — *"Phần bị bỏ qua nhiều nhất khi làm phần mềm · mỗi nhánh đều phải quay được về luồng chính"*.
> Nguyên tắc tuyệt đối: **một nhánh = một đường vào + một đường ra + một điểm quay về kiểm chứng được bằng test.**

---

## 0. Bảng tổng hợp

| # | Nhánh | Model bị tác động | Điểm vào | Điểm quay về | Test |
|---|---|---|---|---|---|
| ① | Hàng chưa rõ khách | `stock.picking` (`lab_is_cho_khach`), `stock.location` | ③ Nhận tại kho | Gán khách → ③/④ | `test_cho_khach.py` |
| ② | Rớt chuyến / giảm tải | `lab.chuyen.line`, `lab.chuyen` | ⑤/⑥ Mở chuyến | `waitlist` → chuyến khác | `test_giam_tai.py` |
| ③ | Đơn thiếu thông tin | `sale.order`, `mail.activity` | ①/③ | Đóng activity → ⑥ | `test_thieu_thong_tin.py` |
| ④ | Xuất thiếu kiện | `stock.picking` (backorder) | ⑦ Xuất kho | Backorder done → đóng chuyến | `test_thieu_kien.py` |
| ⑤ | Chuyển kho bay ↔ tàu | `stock.picking` (internal), `stock.warehouse` | ⑤/⑦ | Kho đích nhận → chuyến mới | `test_chuyen_kho.py` |
| ⑥ | Khiếu nại · trả hàng | `helpdesk.ticket`, `stock.picking` (return) | ⑨⑩⑪ Hành trình | Ticket đóng → credit note | `test_khieu_nai.py` |

---

## ① Hàng chưa rõ khách

**Tình huống:** "Nhận hàng nhưng chưa biết của shop nào."

| Bước (theo sơ đồ) | Thao tác người dùng | Dữ liệu thay đổi | Ghi chú |
|---|---|---|---|
| 1. Kho nhận hàng | Tạo `stock.picking` receipt **không có partner** | `partner_id = False`, `lab_is_cho_khach = True` | Không dùng chặn required ở đây — cần cho phép |
| 2. Dán mã kiện tạm | Nhập mã kiện tay `CHOTMP-0001`, gắn `stock.quant.package` | `stock.quant.package.name` | Mã kiện tạm dùng sequence riêng |
| 3. Đưa vào KHO CHỜ | Validate picking → vị trí đích `KHO CHỜ` | `stock.move.location_dest_id` | Location nội bộ, không bán được |
| 4. Đối chiếu với shop | Màn hình "Hàng chờ" lọc `lab_is_cho_khach` | — | Có thể dùng pivot/kanban filter |
| 5. Gán khách · về luồng chính | Chọn shop → click "Gán khách" | `partner_id` set, `lab_is_cho_khach = False` | **Điểm quay về** |

**Thiết kế Odoo:**

- `stock.location` mới: `KHO CHỜ` (usage `internal`, không thuộc warehouse cha? → thuộc `KHO CHÍNH` để report tồn gộp).
- Màn hình: action `lab_core.action_hang_cho` — `stock.picking` với domain `[('lab_is_cho_khach','=',True),('state','=','done')]`, group_by partner.
- Wizard "Gán khách": `lab.gan.khach.wizard` (transient) → set partner + tạo `sale.order` nếu chưa có.
- Khi gán: tạo internal transfer `KHO CHỜ → Stock` (native) để tồn kho phản ánh đúng — **không** sửa trực tiếp quant.

**Sai lầm thường gặp:**

- ❌ Sửa `partner_id` trên picking đã done → không sinh bút toán chuyển kho, tồn kho sai.
- ❌ Tạo warehouse mới "KHO CHỜ" → báo cáo tách rời, phải chuyển kho thủ công.
- ❌ Cho phép bán hàng từ `KHO CHỜ` → kho ảo.

**Điều kiện nghiệm thu:**

1. Tạo picking không partner → được.
2. Validate → hàng nằm ở `KHO CHỜ`, không nằm ở Stock.
3. Gán khách → sinh internal transfer → hàng ở Stock.
4. Test: `test_cho_khach.py::test_gán_khách_chuyển_đúng_vị_trí`.

---

## ② Rớt chuyến / giảm tải

**Tình huống:** "Chuyến quá tải hoặc hãng đổi lịch."

| Bước | Thao tác | Dữ liệu | Ghi chú |
|---|---|---|---|
| 1. Phát hiện quá tải | Chuyến có `ti_le_tai > 100` → banner đỏ | compute | Hoặc hãng đổi lịch → sửa `cut_off_datetime` |
| 2. Đề xuất giảm tải | Wizard hiện danh sách đơn, đề xuất đơn gỡ (theo khách ưu tiên thấp / đơn trễ) | — | Có thể dùng AI gợi ý (backlog) |
| 3. Trưởng nhóm duyệt | **Tier validation** — không có duyệt thì không gỡ được | `lab.chuyen.tier_review_ids` | Bắt buộc, đây là bước kiểm soát |
| 4. Gỡ đơn khỏi chuyến | Click "Gỡ" trên line → nhập lý do | `line.state = 'waitlist'`, `ly_do_go` | **Không xoá line** |
| 5. Đơn về chờ chuyến sau | Màn "Đơn chờ" → chọn chuyến mới → gán | `line.chuyen_id` đổi | **Điểm quay về** |

**Thiết kế Odoo:**

- Wizard `lab.giam.tai.wizard` (transient): chọn nhiều line, chọn lý do (`overload`, `carrier_change`, `customer_request`).
- Tier validation: định nghĩa trên `lab.chuyen` operation `giam_tai` — dùng `base_tier_validation` với `review_operation` mở rộng, hoặc đơn giản hơn: wizard yêu cầu `self.env.user.has_group('lab_core.group_manager')` **qua field `approved_by`** + `ir.rule`. → **Chọn cách của Odoo:** dùng `base_tier_validation` với `review_ids` trên wizard, không dùng `has_group` trực tiếp.
- State chuyến **không đổi** khi giảm tải; chỉ đổi khi huỷ cả chuyến (`cancelled`).
- Thông báo: `message_post` trên chuyến + trên `sale.order` bị gỡ.

**Sai lầm thường gặp:**

- ❌ `unlink()` line → mất dấu vết, không kiểm toán được.
- ❌ Chặn giảm tải bằng `raise UserError` nếu > 100% → không giảm tải được khi chính chuyến đó quá tải (nghịch lý).
- ❌ Không có trạng thái `waitlist` → đơn bị "treo" không ai biết.

**Nghiệm thu:**

1. Chuyến 120% → banner đỏ, nút giảm tải sáng.
2. Gỡ đơn không có duyệt → bị chặn (tier validation).
3. Duyệt → gỡ → line `waitlist`, chuyến còn 95%.
4. Gán lại chuyến khác → `booked`, chuyến cũ đã gỡ.
5. Test: `test_giam_tai.py::test_giam_tai_requires_approval`.

---

## ③ Đơn thiếu thông tin

**Tình huống:** "Thiếu người nhận / địa chỉ / cân nặng."

| Bước | Thao tác | Dữ liệu |
|---|---|---|
| 1. Kiểm thông tin lô | Màn checklist (widget/field) trên `sale.order` | Các field bắt buộc: người nhận, địa chỉ, kg |
| 2. Đánh dấu thiếu | Click "Báo thiếu thông tin" | `lab_is_exception = True`, `lab_exception_type = 'missing_info'` |
| 3. Báo CS bổ sung | Tạo `mail.activity` type "Bổ sung thông tin" cho CS phụ trách | `res.users` = `sale.order.user_id` |
| 4. CS cập nhật | CS sửa đơn, upload bằng chứng | Chatter |
| 5. Quay lại duyệt chuyến | Activity `done` → đơn được phép vào ⑥ | **Điểm quay về:** chặn nếu còn activity mở |

**Thiết kế Odoo (thuần native):**

- `mail.activity.type` mới: "Bổ sung thông tin" (`data/mail_activity_type_data.xml`).
- Chặn: trên `lab.chuyen.line`, `@api.constrains` hoặc nút "Gán vào chuyến" kiểm tra `sale_order_id.activity_ids` rỗng — **dùng `activity_ids` của native**, không tạo field riêng.
- Nếu thiếu cân nặng: đánh dấu ở `stock.move.line` và chặn tính cước (`chargeable_weight > 0`).

**Sai lầm thường gặp:**

- ❌ Tạo model `thong_tin_thieu`.
- ❌ Dùng boolean riêng thay `mail.activity` → mất tính năng nhắc hạn, mất chuông thông báo native.

**Nghiệm thu:** đơn có activity mở → nút "Duyệt vào chuyến" bị disable kèm tooltip tiếng Việt giải thích; đóng activity → duyệt được.

---

## ④ Xuất thiếu kiện

**Tình huống:** "Xuất kho xong mới phát hiện thiếu kiện."

| Bước | Thao tác | Dữ liệu |
|---|---|---|
| 1. Lập phiếu xuất | Validate delivery | `stock.picking` |
| 2. Kiểm đếm kiện | Đối chiếu `stock.quant.package` với manifest | màn manifest |
| 3. Phát hiện thiếu | Đánh dấu kiện thiếu | backorder |
| 4. Tìm / bổ sung | Kho tìm, xuất bổ sung | picking mới hoặc backorder |
| 5. Bồi thường nếu mất | Credit note / hoá đơn bồi thường | `account.move` |

**Thiết kế Odoo (native tuyệt đối):**

- Khi validate picking mà chưa đủ số lượng → Odoo **hỏi tạo backorder** (native wizard). Dùng đúng cơ chế này.
- Manifest đối chiếu: `stock.picking` với `package` — view `stock.quant.package` list group by picking.
- Bồi thường: `account.move` credit note (native) — **không** tự tạo journal entry.
- Field hỗ trợ: `stock.picking.lab_package_count` + `lab_weight_kg` để so sánh nhanh.

**Sai lầm thường gặp:**

- ❌ Tự `stock.move.quantity = 0` cho phần thiếu → mất history.
- ❌ Tạo model `kien_thieu`.

**Nghiệm thu:** validate thiếu → backorder sinh ra với đúng số kiện thiếu → xuất bổ sung → cả 2 picking `done` → chuyến vẫn cân đối.

---

## ⑤ Chuyển kho bay ↔ tàu

**Tình huống:** "Đổi phương án vận chuyển."

| Bước | Thao tác | Dữ liệu |
|---|---|---|
| 1. Yêu cầu chuyển | Tạo "yêu cầu chuyển kho" từ chuyến | `lab.chuyen` → wizard |
| 2. Duyệt chuyển kho | Manager duyệt (tier validation) | review_ids |
| 3. Lập phiếu chuyển kho | Internal transfer `KHO BAY → KHO TÀU` | `stock.picking` type internal |
| 4. Kho đích nhận | Validate picking | quant đổi |
| 5. Cập nhật chuyến mới | Gán line sang `lab.chuyen` mới (sea) | **Điểm quay về** |

**Thiết kế Odoo:**

- 2 warehouse `KHO BAY`, `KHO TÀU` (route `Internal Transfers`).
- Wizard `lab.chuyen.kho.wizard`: chọn kho đích + chuyến mới → tạo internal picking (native `stock.picking.create`) → gợi ý gán lại line.
- Cập nhật `lab_chuyen.loai_van_chuyen` và chargeable divisor (6000 ↔ 1000) — **đây là hệ quả quan trọng: cước thay đổi**, phải tính lại `lab_chargeable_weight` và cảnh báo kế toán nếu Debit đã lập.
- Nếu Debit đã khoá → không cho chuyển, hoặc phải mở khoá (liên kết ⑭).

**Sai lầm thường gặp:**

- ❌ Sửa `loai_van_chuyen` trực tiếp trên chuyến → cước cũ không tính lại → lệch tiền.
- ❌ Dùng `stock.quant` để "chuyển" (không có chứng từ).

**Nghiệm thu:** chuyển 1 lô air → sea: tồn 2 kho đúng, manifest mới đúng, cước tính lại, có chatter ghi lý do.

---

## ⑥ Khiếu nại · trả hàng

**Tình huống:** "Hàng hỏng, khách không nhận."

| Bước | Thao tác | Dữ liệu |
|---|---|---|
| 1. Khách báo sự cố | Tạo ticket từ `lab.hanh.trinh` | `helpdesk.ticket` |
| 2. Mở ticket | Team "Khiếu nại", stage "Mới" | native |
| 3. Kho kiểm tra | Ghi kết quả kiểm tra | chatter + attachment |
| 4. Xử lý / xuất trả | Return picking (native) hoặc xử lý tại chỗ | `stock.picking` return |
| 5. Đóng ticket | Stage "Đóng" + credit note nếu bồi thường | **Điểm quay về:** `lab.hanh.trinh.stage = closed` |

**Thiết kế Odoo:**

- `helpdesk_mgmt` (OCA): team "Khiếu nại", stage: Mới → Đang kiểm tra → Chờ xử lý → Đã xử lý → Đóng.
- Liên kết: `helpdesk.ticket` ← `lab.hanh.trinh` (m2o `hanhtrinh_id` trên ticket hoặc o2m trên hành trình). **Hướng depends:** `lab_hanhtrinh` phải thấy ticket → nếu `lab_hanhtrinh` không depends `helpdesk_mgmt`, dùng field `helpdesk_ticket_id` khai báo có điều kiện hoặc dùng `mail.activity` + `project.task` làm phương án B.
- **Quyết định kiến trúc:** `lab_hanhtrinh` **không depends** `helpdesk_mgmt` (giữ module nghiệp vụ độc lập). Thay vào đó, `helpdesk_mgmt` (module thứ 3) thêm field `lab_hanhtrinh_id` qua `_inherit` trong một module cầu nối nhỏ `lab_helpdesk_bridge` **hoặc** dùng `mail.activity` type "Khiếu nại" + `lab.hanh.trinh.stage='exception'`. → **Chọn phương án 2** (không thêm module), ghi ADR-005.
- Return: native return wizard của `stock.picking`.

**Sai lầm thường gặp:**

- ❌ Tạo model `khieu_nai`.
- ❌ Xoá booking/hành trình để "reset" khi khách trả hàng.

**Nghiệm thu:** mở ticket từ hành trình → kiểm tra → return picking trỏ đúng picking gốc → đóng ticket → hành trình `closed` → báo cáo khiếu nại hiện 1 case.

---

## Checklist Review cho cả 6 nhánh

| Kiểm tra | Cách đo |
|---|---|
| Có đường vào rõ ràng | Bấm từ màn hình luồng chính, ≤ 2 click |
| Có đường ra rõ ràng | Test khẳng định state cuối |
| Không tạo thực thể mồ côi | `search` không còn record treo ở trạng thái trung gian |
| Không viết lại Odoo | Không có model mới cho nhánh nào |
| Có chatter | Mọi bước chuyển state đều `message_post` |
| Có tiếng Việt | Không lộ "New", "Confirm" |
| Có test | 1 file test/nhánh, ≥ 2 case (happy + edge) |

---

*Quay lại: `docs/00-nghiep-vu.md` §5.*
