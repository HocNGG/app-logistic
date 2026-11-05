# 00 — PHÂN TÍCH NGHIỆP VỤ (Business Analysis)

> Nguồn: `_intake/so-do-nghiep-vu.pdf` (4 trang) · đề bài "Bài tập đánh giá năng lực — Vị trí thực tập Lập trình Odoo — Đề số 4"
> Trạng thái: **FROZEN v1.0** — mọi agent (BE/FE/Review) đọc file này trước khi làm bất kỳ việc gì.

---

## 0. TL;DR — 60 giây

Công ty là **forwarder gom hàng lẻ xuất khẩu** (consolidation / LCL + air cargo). Khách là các **shop bán hàng online**. Hàng đi theo 2 đường vào kho: **lấy tận nơi** hoặc **khách tự mang tới**. Sau khi cân – đo – đóng gói, nhiều đơn của **nhiều khách** được gom vào **một chuyến** (chuyến bay / chuyến tàu), làm thủ tục, bay/đi biển, giao cho đại lý nước ngoài, rồi **tính cước theo kg → lập Debit → khoá Debit → hoá đơn → thu tiền → khoá tháng**.

Điểm cốt lõi của đề bài (và của cách chấm điểm):

| Con số | Ý nghĩa |
|---|---|
| **19 bước** | Toàn bộ luồng chuẩn, chia 10 pha, trải theo **6 phòng ban** |
| **10 bước** | Odoo 17 **đã có sẵn** — chỉ dùng, cấm viết lại |
| **3 bước** | Chỉ cần **cấu hình** (tier validation, lock date, dashboard) |
| **6 bước** | **Phải viết code** |
| **3 module** | `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh` — phần duy nhất được phép viết mới |
| **6 nhánh** | Ngoại lệ, mỗi nhánh 5 bước, **cuối nhánh bắt buộc quay về luồng chính** |

**Câu thần chú của dự án:** *Native trước — cấu hình trước — chỉ viết code khi Odoo 17 thật sự không có.* Xem `docs/01-tai-su-dung-odoo17.md`.

---

## 1. Bài toán kinh doanh

> "Một công ty giao nhận (doanh nghiệp giả định) nhận gửi hàng đi nước ngoài cho các shop bán hàng online. Hàng được lấy tận nơi hoặc khách tự mang tới kho, sau đó được cân đo, đóng gói, gom theo chuyến bay hoặc chuyến tàu rồi gửi đi.
> Công ty có các bộ phận chăm sóc khách hàng, kho, tài xế, chứng từ và kế toán. Hiện mọi việc trao đổi qua Zalo và Excel nên hay nhầm lẫn, chậm trễ và khó kiểm soát."

**Nỗi đau thật (pain points) — dùng làm tiêu chí nghiệm thu, không phải trang trí:**

| # | Nỗi đau | Hệ quả | Tính năng phải giải quyết |
|---|---|---|---|
| P1 | Trao đổi qua Zalo | Mất dấu yêu cầu, không ai chịu trách nhiệm | Mọi giao tiếp nằm trong **chatter** (`mail.thread`), có `mail.activity` + người phụ trách |
| P2 | Excel rời rạc | Số kg / số kiện / cước lệch nhau giữa các bộ phận | **Một nguồn số liệu duy nhất**: cân thực tế trên `stock.move.line` → chảy lên Debit |
| P3 | Gom chuyến bằng tay | Quá tải, rớt chuyến, gỡ đơn thủ công | `lab_chuyen` + kiểm tải trọng + **tier validation** khi duyệt |
| P4 | Không ai biết hàng đang ở đâu | Khách hỏi liên tục, CS phải gọi điện | `lab_hanhtrinh` + **portal access_token** cho khách tự tra cứu |
| P5 | Debit bị sửa sau khi đã gửi khách | Thất thoát, không đối soát được | **Khoá Debit** bằng tier validation + lock date (native) |
| P6 | Điều tài xế qua điện thoại | Trùng chuyến, quên lấy hàng, không có bằng chứng | `lab_laixe` + app KMP: **ảnh · ký · xác nhận** |

---

## 2. Actors — 6 phòng ban

> ⚠️ **Điểm vênh trong tài liệu gốc:** trang 1 ghi *"5 phòng ban"*, trang 2 ghi *"6 phòng ban"* và vẽ **5 cột** (CS/SALE · TÀI XẾ · KHO · CHỨNG TỪ · KẾ TOÁN · SALE ADMIN) + 1 làn NGOẠI LỆ.
> **Cách chốt:** 5 phòng ban vận hành + 1 làn ngoại lệ = 6 "làn" trên sơ đồ. Cột "KẾ TOÁN · SALE ADMIN" tách thành 2 nhóm quyền riêng (`lab.group_account`, `lab.group_sale_admin`) vì quyền khác nhau. Ghi vào `docs/09-open-questions.md`.

| Phòng ban | Người dùng | Trách nhiệm chính | Odoo group (kế hoạch) |
|---|---|---|---|
| **CS / SALE** | Sale, CSKH | Nhận yêu cầu, báo giá, theo dõi, báo khách, xử lý khiếu nại | `sales_team.group_sale_salesman` + `lab.group_cs` |
| **TÀI XẾ** | Tài xế, điều phối | Đi lấy hàng tận nơi, chụp ảnh, ký biên nhận, xác nhận | `lab.group_driver` (portal-level, không vào backend) |
| **KHO** | Thủ kho, kho bay, kho tàu, kho chờ | Nhận, cân, đo, dán mã kiện, đóng gói, xuất kho, kiểm đếm | `stock.group_stock_user` + `lab.group_warehouse` |
| **CHỨNG TỪ** | Nhân viên chứng từ | Booking, AWB/HBL, manifest, hải quan | `lab.group_docs` |
| **KẾ TOÁN** | Kế toán công nợ, kế toán tổng hợp | Debit, kiểm & khoá, hoá đơn, thu tiền, đối soát, khoá tháng | `account.group_account_invoice` + `account.group_account_manager` |
| **SALE ADMIN** | Quản trị kinh doanh | Duyệt đơn vào chuyến, duyệt giảm tải, mở khoá Debit, dashboard sếp | `lab.group_manager` (+ duyệt tier validation) |
| **SẾP / BAN GIÁM ĐỐC** | BGĐ | Nhìn dashboard, không thao tác nghiệp vụ | `lab.group_director` (read-only + dashboard) |

**Nguyên tắc phân quyền (ghi vào DoD):** mỗi phòng ban **chỉ thấy** dữ liệu của mình + dữ liệu chung; dùng **`ir.rule` của Odoo 17** chứ không dùng code Python kiểm tra quyền trong business flow.

---

## 3. Luồng chuẩn — 10 pha / 19 bước

> Đối chiếu số học: `19 = 16 bước có số ①→⑯ + 3 bước không số (Tài xế đi lấy hàng · Cân–đo–dán mã kiện · Báo cáo)`.
> Phân lớp: **10 native (xanh) = ①③④⑦⑧⑫⑬⑮⑯ + Cân–đo–dán mã kiện** · **3 config (hổ phách) = ⑥ ⑭ Báo cáo** · **6 custom (đỏ) = ② Tài xế ⑤ ⑨ ⑩ ⑪**.

### Bảng 19 bước — bảng gốc của toàn dự án

| # | Pha | Phòng ban | Việc xảy ra | Odoo artifact (theo sơ đồ) | Lớp | Ghi chú triển khai |
|---|---|---|---|---|---|---|
| ① | 1. Tiếp nhận yêu cầu | CS/SALE | Shop nhắn đơn, sale báo giá | `sale.order` | 🟩 Native | Báo giá = `sale.order` state `draft`→`sent`; portal cho shop tự đặt |
| ② | 2. Lấy hàng / tự mang | CS/SALE → TÀI XẾ | Tạo yêu cầu lấy hàng, gán tài xế | `lab_laixe` | 🟥 **Custom** | Model `lab.yeu.cau.lay.hang` |
| — | 2. Lấy hàng / tự mang | TÀI XẾ | Tài xế đi lấy: **ảnh · ký · xác nhận** | `lab_laixe` | 🟥 **Custom** | App KMP (offline-first) |
| ③ | 3. Nhận tại kho | KHO | Kiểm đếm, nhận vào kho | `stock.picking` | 🟩 Native | Picking type *Receipt*; nếu chưa rõ khách → **KHO CHỜ** (nhánh ①) |
| — | 3. Nhận tại kho | KHO | **Cân · đo · dán mã kiện** | `stock.move.quantity` | 🟩 Native + field | `stock.move`/`stock.move.line` + field khối lượng (xem `docs/02`) |
| ④ | 4. Đóng gói | KHO | Đóng kiện, dán nhãn | `stock.quant.package` | 🟩 Native | `stock.quant.package` + `stock.package.type` |
| ⑤ | 5. Mở chuyến · booking | KHO / CHỨNG TỪ | Mở chuyến bay/tàu, booking hãng | `lab_chuyen` | 🟥 **Custom** | `lab.chuyen` + `lab.chuyen.line` |
| ⑥ | 5. Mở chuyến · booking | SALE ADMIN | **Duyệt đơn vào chuyến** | `base_tier_validation` | 🟨 Config | Cấu hình 2 cấp: trưởng nhóm → manager |
| ⑦ | 6. Xuất kho | KHO | Xuất kho lên chuyến | `stock.picking` | 🟩 Native | Picking type *Delivery*; gắn `lab_chuyen` |
| ⑧ | 6. Xuất kho | CHỨNG TỪ | **AWB · HBL · manifest** | `mail.thread` + QWeb | 🟩 Native | Report QWeb PDF + chatter lưu số AWB/HBL |
| ⑨ | 7. Hành trình | CHỨNG TỪ | Hàng đi (transit) | `lab_hanhtrinh` | 🟥 **Custom** | Mốc hành trình, không phải `stock.picking` |
| ⑩ | 7. Hành trình | CHỨNG TỪ | Đến nơi (arrived) | `lab_hanhtrinh` | 🟥 **Custom** | Cùng model, khác `stage` |
| ⑪ | 7. Hành trình | CHỨNG TỪ / đại lý | Đại lý giao khách | `lab_hanhtrinh` | 🟥 **Custom** | POD (proof of delivery) |
| ⑫ | 8. Tính cước | KẾ TOÁN / SALE | Cước theo **kg** | `sale.order.line` | 🟩 Native | Product UoM = kg + `product.pricelist` bậc thang |
| ⑬ | 8. Tính cước | KẾ TOÁN | Lập **Debit** | `account.move` | 🟩 Native | Journal *Debit*; `move_type = out_invoice` |
| ⑭ | 9. Kiểm & thu tiền | KẾ TOÁN → SALE ADMIN | **Kiểm & khoá Debit** | `base_tier_validation` | 🟨 Config | Khoá = chặn sửa (`lock` + tier) |
| ⑮ | 9. Kiểm & thu tiền | KẾ TOÁN | Hoá đơn · thu tiền | `account.payment` | 🟩 Native | Payment register + đối soát ngân hàng |
| ⑯ | 9. Kiểm & thu tiền | KẾ TOÁN | Đối soát · khoá tháng | `period_lock_date` | 🟩 Native | `res.company.period_lock_date` |
| — | 10. Báo cáo | SẾP | Doanh thu · sản lượng · dashboard | `pivot` · `spreadsheet` | 🟨 Config | Pivot + graph view; dashboard ở CE 17 (xem `docs/01` §6) |

**Đọc bảng này thế nào:** cột *Lớp* là **hợp đồng**. Nếu một agent viết code Python cho một dòng 🟩 hoặc 🟨 → **Review agent bắt buộc reject** (trừ khi có ADR giải trình trong `docs/adr/`).

---

## 4. Hai đường vào kho (đề bài cho phép cả hai)

| Đường | Đường đi trên sơ đồ | Chuỗi bước | Điểm khác biệt kỹ thuật |
|---|---|---|---|
| **Đến lấy tận nơi** | Đường xám liền | ① → ② *(tạo yêu cầu + tài xế đi lấy)* → ③ → … | Có `lab.yeu.cau.lay.hang`; tài xế xác nhận trên app; có ảnh + chữ ký + số kg ước tính |
| **Khách tự mang tới kho** | Đường cam nét đứt | ① → ~~②~~ → ③ → … | Không có yêu cầu lấy hàng; kho nhận trực tiếp. **Bắt buộc** gắn được `sale.order` hoặc đưa vào **KHO CHỜ** (nhánh ①) |

**Yêu cầu kỹ thuật:** một field `lab.pickup_mode` (`pickup` / `drop_off`) trên `sale.order` + `stock.picking` để mọi báo cáo/sơ đồ trạng thái biết đơn đi đường nào. Không được coi "tự mang" là trường hợp lỗi.

---

## 5. Sáu nhánh ngoại lệ — trái tim của bài

> "Phần bị bỏ qua nhiều nhất khi làm phần mềm · mỗi nhánh đều phải quay được về luồng chính."
> Đây là chỗ phân biệt **người làm demo** và **người hiểu nghiệp vụ**. Mỗi nhánh: 5 bước, 1 công cụ Odoo native, 1 điểm quay về.

| # | Nhánh | Kích hoạt | 5 bước | Công cụ Odoo | Quay về luồng chính bằng |
|---|---|---|---|---|---|
| ① | **Hàng chưa rõ khách** | Nhận hàng mà không biết của shop nào | Kho nhận → dán mã kiện tạm → đưa vào **KHO CHỜ** → đối chiếu shop → **gán khách** | `stock.location` (KCHO) · `sale.order` | Gán `partner_id` + gắn `sale.order` → hàng rời KHO CHỜ sang bước ③/④ |
| ② | **Rớt chuyến / giảm tải** | Chuyến quá tải, hãng đổi lịch | Phát hiện quá tải → đề xuất giảm tải → **trưởng nhóm duyệt** → gỡ đơn khỏi chuyến → đơn về **chờ chuyến sau** | `base_tier_validation` + `lab_chuyen` | Đơn ở trạng thái `waitlist` → gán lại chuyến khác |
| ③ | **Đơn thiếu thông tin** | Thiếu người nhận / địa chỉ / cân nặng | Kiểm thông tin lô → đánh dấu thiếu → báo CS bổ sung → CS cập nhật → **quay lại duyệt chuyến** | `mail.activity` | `activity` đóng → đơn quay lại ⑥ duyệt chuyến |
| ④ | **Xuất thiếu kiện** | Xuất kho xong mới phát hiện thiếu kiện | Lập phiếu xuất → kiểm đếm kiện → phát hiện thiếu → tìm/bổ sung → bồi thường nếu mất | `stock.picking` (backorder) | Bổ sung xong → đóng picking; nếu mất → credit note (native) |
| ⑤ | **Chuyển kho bay ↔ tàu** | Đổi phương án vận chuyển | Yêu cầu chuyển → duyệt chuyển kho → lập phiếu chuyển kho → kho đích nhận → cập nhật chuyến mới | `stock.warehouse` (2 kho: KHO BAY, KHO TÀU) | Internal transfer hoàn tất → cập nhật `lab_chuyen` + `lab_hanhtrinh` |
| ⑥ | **Khiếu nại · trả hàng** | Hàng hỏng, khách không nhận | Khách báo sự cố → mở ticket → kho kiểm tra → xử lý/xuất trả → đóng ticket | `helpdesk_mgmt` (OCA) | Ticket đóng → nếu trả hàng: phiếu xuất trả (native return picking) |

**Nguyên tắc bất di bất dịch (đưa vào Definition of Done):** một ngoại lệ **không được** tạo ra luồng song song vĩnh viễn. Nó phải có **trạng thái vào** và **trạng thái ra** rõ ràng, và ra thì **luôn** quay lại luồng chính. Nếu thiết kế không chỉ ra được "điểm quay về" → thiết kế sai.

Chi tiết triển khai từng nhánh: `docs/07-exception-flows.md`.

---

## 6. Ba module phải viết (và vì sao Odoo native không đủ)

| Module | Làm gì | Vì sao Odoo native không có | Model |
|---|---|---|---|
| **`lab_chuyen`** | Chuyến **gom lô**: booking, giờ chốt, tải trọng theo chuyến bay/tàu, duyệt đơn vào chuyến, giảm tải | Odoo **không có khái niệm gom nhiều đơn của nhiều khách vào một chuyến bay/tàu**. `stock.picking` là chứng từ kho, không phải *chuyến*. `delivery.carrier` là *phương thức vận chuyển*, không có sức chứa, không có cut-off, không gom nhiều khách | `lab.chuyen`, `lab.chuyen.line` |
| **`lab_laixe`** | Điều **tài xế** đi lấy hàng tận nơi: gán tài xế, lộ trình, ảnh, chữ ký, xác nhận | Module `fleet` quản lý **xe của công ty** (bảo dưỡng, chi phí), **không** điều phối chuyến đi lấy hàng. Không có khái niệm "đi lấy hàng cho đơn này" | `lab.yeu.cau.lay.hang`, `lab.pickup.line` |
| **`lab_hanhtrinh`** | **Hành trình quốc tế**: hàng đi → đến kho đối tác → đã giao khách. Chuyển kho bay ↔ tàu | `stock` **chỉ quản lý kho của chính mình**. Hàng đã ra khỏi kho công ty (trên máy bay / trên tàu / ở kho đối tác nước ngoài) thì `stock.picking` đã `done` — không còn gì để theo dõi | `lab.hanh.trinh`, `lab.hanh.trinh.line` |

**Cộng thêm 1 module keo (khuyến nghị, không nằm trong 3 module bắt buộc): `lab_core`.**
Lý do: nếu `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh` cùng cần nhóm quyền, sequence, mixin ngoại lệ, field mở rộng trên `res.partner`/`stock.picking`, mà không có module chung thì chúng **phải depends lẫn nhau** → phụ thuộc vòng, cài đặt sai thứ tự, không upgrade được. Đây là **thực hành chuẩn của Odoo** (xem `addons/sale_stock`, `addons/stock_delivery` đều là module keo).
→ Xem ADR-001 trong `docs/adr/ADR-001-lab-core.md`.

---

## 7. Những thứ **KHÔNG** viết lại

Sơ đồ ghi rõ: *"Những thứ KHÔNG viết lại: các bước màu xanh dùng model có sẵn"*.

`res.partner` · `sale.order` · `stock.picking` · `stock.move` · `stock.move.line` · `stock.quant.package` · `account.move` · `account.payment` · `mail.activity` · `mail.thread` · `base_automation` · `base_tier_validation` · `ir.rule` · `res.groups` · `portal` · `spreadsheet_dashboard` · `uom.uom` · `product.pricelist` · `stock.warehouse` · `stock.location` · `helpdesk_mgmt`

**"Debit chính là `account.move`. Cước theo kg làm được bằng product đơn vị kg + bảng giá."** — Đây là câu quan trọng nhất trong tài liệu gốc. Không có model `debit`, không có model `cuoc`, không có model `khoi_luong`.

---

## 8. Yêu cầu phi chức năng

| Nhóm | Yêu cầu | Nguồn |
|---|---|---|
| **Ngôn ngữ** | UI tiếng Việt (vi_VN), thuật ngữ nghiệp vụ Việt: *chuyến, kiện, cước, debit, kho chờ, giảm tải* | Đề bài tiếng Việt |
| **Vai trò** | 6 nhóm quyền, dữ liệu cách ly theo phòng ban | Sơ đồ cột |
| **Kiểm soát** | Chatter mọi nơi, `mail.activity` cho việc tồn, audit trail | Pain P1 |
| **Bút toán** | Mỗi sự kiện kinh tế phải sinh bút toán — không "quên" Debit | Pain P5, REA |
| **Mobile** | App **KMP**, chạy **desktop target** để demo | Đề bài |
| **AI** | Demo bằng AI, giảm thủ công, "wow moment" | Ảnh của sếp |
| **UI/UX** | Responsive, "iPhone 18 duo UI", REA, critical thinking | Ảnh của sếp |
| **Demo** | Video demo sản phẩm, kể chuyện như một FDE | Ảnh của sếp |

Giải mã chi tiết các yêu cầu từ ảnh của sếp: `docs/05-yeu-cau-cua-sep.md`.

---

## 9. Khoảng trống thông tin — phải chốt trước khi code

Ghi vào `docs/09-open-questions.md`. **Giả định mặc định** (dùng khi chưa có câu trả lời, agent phải ghi rõ trong PR khi dùng giả định):

1. **Đơn giá cước**: tính theo `max(kg thực tế, kg thể tích)` với hệ số 6000 (air) / 1000 (sea). *Giả định.*
2. **Tiền tệ**: VND cho nội địa, USD cho cước quốc tế → cần multi-currency. *Giả định.*
3. **Hoá đơn điện tử VN**: Odoo 17 có `l10n_vn` native nhưng **không** có kết nối nhà cung cấp HĐĐT VN. *Ngoài phạm vi* — ghi chú lối mở (`account.move` đã đủ dữ liệu để tích hợp).
4. **Số kho**: 3 (`KHO CHÍNH`/nhận hàng, `KHO BAY`, `KHO TÀU`) + `KHO CHỜ` là **location** trong kho chính, không phải warehouse mới.
5. **Số công ty**: 1 công ty, nhiều địa chỉ (đại lý nước ngoài = `res.partner` type `delivery` + `is_company`).
6. **Luồng duyệt**: 2 cấp (trưởng nhóm → manager) cho: duyệt vào chuyến (⑥), giảm tải (nhánh ②), mở khoá Debit (⑭).
7. **Ai được mở khoá Debit**: chỉ `lab.group_manager`, phải nhập lý do → sinh `mail.message` + credit note nếu đã gửi khách.
8. **Ngoại lệ ④ "xuất thiếu kiện"**: dùng **backorder** của `stock.picking`, không tạo model mới.
9. **Khiếu nại**: `helpdesk_mgmt` (OCA 17.0). Nếu không cài được → dùng `mail.activity` + `project.task`. Có ADR.
10. **Dashboard ở CE**: bản 17.0 Community — cần xác nhận `spreadsheet_dashboard` có trong CE hay chỉ EE. Xem `docs/01` §6 + ADR-002.
11. **KMP desktop demo**: Windows (máy demo) là target bắt buộc; macOS/Linux là bonus.
12. **Ai là "sếp" trong demo**: dashboard read-only, không thao tác nghiệp vụ.

---

## 10. Glossary — bắt buộc dùng đúng thuật ngữ

| Thuật ngữ | Nghĩa | Trong Odoo |
|---|---|---|
| **Gom hàng lẻ / consolidation** | Gom nhiều lô nhỏ của nhiều khách vào 1 container/chuyến | `lab.chuyen` |
| **Chuyến** | Một chuyến bay hoặc chuyến tàu cụ thể, có cut-off và tải trọng | `lab.chuyen` |
| **Kiện** | Đơn vị đóng gói vật lý | `stock.quant.package` |
| **Debit** (phiếu ghi nợ) | Bảng kê cước gửi khách, tiền đề của hoá đơn | `account.move` (`move_type=out_invoice`) |
| **Khoá Debit** | Chặn sửa Debit sau khi đã duyệt/gửi | tier validation + posted state |
| **AWB** | Air Waybill — vận đơn hàng không | Report QWeb + field trên `lab.hanh.trinh` |
| **HBL** | House Bill of Lading — vận đơn đường biển (cấp house) | Report QWeb |
| **Manifest** | Bảng kê hàng của chuyến | Report QWeb gom theo `lab.chuyen` |
| **Tải trọng** | Sức chứa chuyến (kg / CBM) | field trên `lab.chuyen` |
| **Giảm tải** | Gỡ bớt đơn khỏi chuyến khi quá tải | nhánh ② + tier validation |
| **KHO CHỜ** | Khu vực hàng chưa xác định được khách | `stock.location` |
| **POD** | Proof of Delivery | attachment/field trên `lab.hanh.trinh` |
| **CBM** | Cubic meter — m³ | field `lab_volume` |
| **Chargeable weight** | Kg dùng để tính cước = max(actual, volumetric) | custom compute trong `lab_core` |
| **Tier validation** | Duyệt nhiều cấp | `base_tier_validation` (OCA) |
| **FDE** | Forward Deployed Engineer — kỹ sư triển khai tại khách hàng | Vai trò ứng tuyển |
| **REA** | Resources–Events–Agents: mô hình kế toán/ERP — mỗi sự kiện kinh tế phải có tài nguyên, sự kiện, tác nhân | Mô hình kiểm chứng bút toán — xem `agents/_shared/skills/rea-model.md` |

---

## 11. Điều gì làm bài này "được" hay "không được"

**Sẽ trượt nếu:**

- Viết model `debit`, `cuoc`, `kien_hang`, `lo_hang`… → viết lại Odoo.
- Dùng `stock.picking` để mô tả chuyến bay → sai bản chất.
- Làm 6 nhánh ngoại lệ thành 6 if/else trong 1 hàm → không phải Odoo.
- Phân quyền bằng `if user.has_group()` trong business method thay vì `ir.rule`.
- Bỏ qua nhánh ngoại lệ vì "demo cho nhanh".

**Sẽ đạt nếu:**

- 10 bước native dùng **đúng** model, không thêm bảng.
- 6 bước custom nằm gọn trong 3 module, **tuân thủ convention Odoo 17** (naming, view syntax mới, security CSV, i18n, test).
- 6 nhánh ngoại lệ có **điểm quay về** bằng chứng minh được bằng dữ liệu.
- Debit khoá được, mở khoá được, có vết (audit trail).
- Demo kể được câu chuyện 3 phút bằng AI, có app KMP thật chạy trên desktop.

---

*Tiếp theo: `docs/01-tai-su-dung-odoo17.md` — bảng tái sử dụng chi tiết.*
