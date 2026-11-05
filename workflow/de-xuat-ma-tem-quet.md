# ĐỀ XUẤT VÒNG 2 — CHỨC NĂNG TỪNG ROLE · MÃ ĐƠN & THỨ TỰ · TEM + QUÉT MÃ

> **Trạng thái: ĐỀ XUẤT, chưa sửa một dòng code nào.** Đo ngày 2026-11-02 trên DB `odoo17_lab`.
> Anh duyệt §6 thì tôi mới rebuild (§7).

---

## 1. Tổng kết chức năng theo vai trò — làm ở đâu

Ký hiệu: 🟢 có sẵn · 🟡 phải làm mới · 🔴 phải bỏ · ⚪ chỉ đọc

### 1.1 Tài xế — `taixe01…taixe05` — **APP (duy nhất)**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Xem việc được giao trong hôm nay (khung giờ, địa chỉ, shop, số kiện dự kiến) | APP | 🟢 |
| **Bắt đầu đi** (`assigned → en_route`) | APP | 🟢 |
| **Chụp ảnh + Ký biên nhận + đếm số kiện/kg** (`→ picked_up`) | APP | 🟢 |
| **Báo thất bại** + lý do (`→ failed`) | APP | 🟢 |
| Quét tem kiện khi lấy hàng | APP | 🟡 (P1) |
| Xem lại biên nhận đã gửi | APP | 🟢 |
| Bất cứ việc gì trên web | WEB | 🔴 không cấp |

### 1.2 Trưởng nhóm tài xế — `nhomtruong01` — **APP**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Danh sách việc `Mới` cần phân công | APP | 🟢 |
| **Gán tài xế** (sau khi gán nút khoá + "Đã gán") | APP | 🟢 (đã sửa 02/11) |
| Xem khung giờ cả ngày của cả nhóm | APP | 🟢 |
| Gom chuyến / sửa đơn | WEB | 🔴 không cấp |

### 1.3 Kho — `kho01` — **APP** (đây là thay đổi lớn nhất)

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Xem việc tài xế **đang về** để dọn dock | APP | 🟡 |
| **Nhận hàng** → tự sinh phiếu nhập `WH/IN` | APP | 🟢 |
| Nhập **số kiện thực tế** khi nhận (báo thiếu/thừa) | APP | 🟡 (P1) |
| **Quét tem IN** khi nhận kiện | APP | 🟡 |
| **Xuất kho**: danh sách phiếu `WH/OUT` theo chuyến đã chốt → đối chiếu kiện → **Xác nhận xuất** | APP | 🟡 **mới hoàn toàn** |
| **Quét tem OUT** khi xuất kiện lên chuyến | APP | 🟡 |
| In lại tem (khi tem rách/mất) | APP | 🟡 (P1) |
| Tra tồn / kiểm kê / xem lịch sử phiếu | WEB | ⚪ (chỉ đọc, không menu vận hành) |

### 1.4 CS — `cs01` — **WEB (duy nhất)** · bỏ app

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Tạo/sửa **Khách hàng (Shop)** | WEB | 🟢 |
| Tạo/sửa **Yêu cầu lấy hàng** (địa chỉ, liên hệ, khung giờ, số kiện) | WEB | 🟢 |
| Xử lý **ngoại lệ ①③** (khách chưa xác định, thiếu địa chỉ/khung giờ) | WEB | 🟡 (nút Huỷ việc) |
| **Gán tài xế** ngay trên form YC | WEB | 🟡 **mới** |
| **Gom đơn vào chuyến + Mở chuyến** | WEB | 🟡 **mới (cấp quyền)** |
| In tem IN cho tài xế (nếu dùng máy in văn phòng) | WEB | 🟡 |
| Màn "CS · Điều phối" trên app | APP | 🔴 **bỏ** |

### 1.5 Manager (Trưởng phòng duyệt) — `manager01` — **WEB**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Mở chuyến · **Chốt booking** · **Khởi hành** · **Đã đến nơi** · **Đã giao khách** · **Khoá chuyến** | WEB | 🟢 |
| Duyệt tier validation (chuyến 2 cấp; **debit 1 cấp — chặn post**) | WEB | 🟢 |
| Xem tổng kết tem/kiện/lệch | WEB | 🟡 (P1) |

### 1.6 Chứng từ — `chungtu01` — **WEB**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Hành trình: nhập **AWB/HBL**, sân bay, ETD/ETA, đổi mốc | WEB | 🟢 |
| Đính **POD**, xử lý sự cố (`exception/returned/compensated`) | WEB | 🟢 |

### 1.7 Kế toán — `ketoan01` — **WEB**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Hoá đơn khách (`sale.order → invoice`) | WEB | 🟢 |
| Phiếu ghi nợ **Debit** (journal DEBIT) + đối soát | WEB | 🟢 |

### 1.8 Sếp — `sep` — **WEB + APP (đọc)**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Bảng điều khiển (KPI, xu hướng 7 ngày, cơ cấu trạng thái) | WEB | 🟢 |
| Tab **Tổng quan** trên app | APP | 🟢 (giữ) |
| Bảng tổng kết **tem & kiện** | WEB | 🟡 (P1) |

### 1.9 Admin — `admin` — **WEB**

| Chức năng | Bề mặt | Trạng thái |
|---|---|---|
| Toàn quyền cấu hình, tạo đơn bán, sửa dữ liệu | WEB | 🟢 |
| Màn **Chuyển tài khoản** (công cụ demo) | APP | 🟢 (giữ, ẩn với người khác) |

**Chốt:** APP phục vụ **3 vai trò tay chân** (tài xế · trưởng nhóm · kho) + 1 màn đọc cho sếp.
WEB phục vụ **5 vai trò văn phòng** (CS · manager · chứng từ · kế toán · admin).

---

## 2. Mã đơn hàng & thời gian tạo — trả lời đúng câu anh thắc mắc

### 2.1 Hiện trạng (đo từ `ir_sequence`)

| Loại | Mã hiện tại | Sinh bởi | Tuần tự? |
|---|---|---|---|
| Yêu cầu lấy hàng | `LH/26/4538` | sequence `lab.yeu.cau.lay.hang`, prefix `LH/%(y)s/`, pad **4** | ✅ tăng dần |
| Đơn bán | `S01499` | sequence `sale.order`, prefix `S`, pad **5** | ✅ tăng dần |
| Phiếu kho | `WH/IN/00446`, `WH/OUT/…` | sequence chuẩn Odoo | ✅ |
| Chuyến | `CH/AIR/26/0257` | sequence chuyến | ✅ |
| Hành trình | `AWB-DEMO-003`, `HBL-DEMO-004` | nhập tay | ❌ |
| **Kiện** | **chưa có** (`stock_quant_package` = 0 dòng) | — | — |

### 2.2 Vấn đề
1. **Thứ tự hiển thị đang ngược yêu cầu**: `lab.yeu.cau.lay.hang._order = "id desc"` ⇒ **mới nhất lên đầu**.
   App thì sắp theo `khung_gio_tu asc` (giờ hẹn), web thì theo `id desc`. Hai nơi hai kiểu.
   Anh muốn: **tạo càng sớm → nằm càng trên** (xử lý FIFO), đúng nghiệp vụ kho.
2. **Không thấy "tạo lúc nào"** trên danh sách ⇒ không biết đơn nào đang chờ lâu.
3. Mã đơn bán `S01499` đọc không biết năm; nhưng đổi prefix sẽ phải sửa 149 bản ghi ⇒ **không đáng**.

### 2.3 Đề xuất (không phá dữ liệu cũ)

| Việc | Cách làm | Ảnh hưởng |
|---|---|---|
| **Cũ nhất lên đầu** | Thêm `default_order="create_date asc, id asc"` cho tree YC lấy hàng và tree Đơn bán; thêm bộ lọc nhanh **"Chờ xử lý (cũ → mới)"** và **"Mới nhất"** | chỉ view, không đổi `_order` toàn cục (an toàn) |
| **App: cùng thứ tự** | Đổi order API: danh sách **chờ xử lý** dùng `create_date asc, id asc`; giữ tab "Hôm nay" dùng `khung_gio_tu asc` | 2 dòng code/API |
| **Thấy thời gian tạo** | Thêm cột **"Tạo lúc"** (`create_date`, format `dd/MM HH:mm`) + cột **"STT"** (số sequence) lên list web và card app | model đã có field, chỉ hiện |
| **Mã dễ kiểm soát** | Giữ nguyên prefix hiện có; **in kèm số STT** trên tem; thêm cột "Tuổi đơn" (giờ kể từ lúc tạo) ở web | 1 compute field |
| **Mã kiện (mới)** | Sequence `lab.kien` prefix `KIEN/%(y)s/`, pad 6 → `KIEN/26/000123` | sequence mới |
| **Mã tem (mới)** | Không cần mã riêng: tem in chính **mã kiện** + QR | 0 sequence mới |

---

## 3. Tem & quét mã (chuỗi logistics) — thiết kế

### 3.1 Mục tiêu
Dán tem lên **từng kiện**; quét tem để (a) xem ngay thông tin kiện, (b) **xác nhận** đúng kiện tại đúng
mốc, (c) phát hiện lệch để **lập lại kế hoạch**, (d) có **bảng tổng kết** đối soát.

### 3.2 Mô hình dữ liệu — dùng đồ có sẵn của Odoo (Luật 1, không tự chế model `kien`)

| Thành phần | Dùng gì | Ghi chú |
|---|---|---|
| **Kiện** | `stock.quant.package` (đã có trong DB, đang 0 dòng) | `name` = `KIEN/26/000123`; gắn `lab_yeu_cau_lay_hang_id` (field mới Many2one tới YC) |
| **Phiếu nhập/xuất** | `stock.picking` (`WH/IN`, `WH/OUT`) | đã có; kiện gắn qua `move_line.result_package_id` |
| **Tem** | **QWeb report PDF** khổ 100×70 mm | chưa có report nào ⇒ làm mới |
| **QR** | URL công khai `<host>/lab/track/<access_token>?k=<mã kiện>` | dùng `access_token` **đã có** trên YC + endpoint `/lab/api/v1/track/<token>` **đã có** ⇒ chỉ thêm trang HTML + tham số kiện |
| **Nhật ký quét** | model **mới** `lab.scan.event` (đề xuất, xin duyệt N3) | cần để có bảng tổng kết + chống trùng; không thay thế chứng từ nào của Odoo |

`lab.scan.event`: `kien_id`, `job_id`, `picking_id`, `loai` (`in`/`nhan`/`xuat`/`giao`), `ket_qua`
(`ok`/`sai_kien`/`trung`), `user_id`, `thiet_bi` (`web`/`app`), `client_uuid` (unique), `thoi_diem`.

### 3.3 Nội dung 2 loại tem

| Tem **IN** (dán khi lấy hàng / nhập kho) | Tem **OUT** (dán khi xuất chuyến) |
|---|---|
| Mã kiện `KIEN/26/000123` (to, đậm) | Mã kiện (to, đậm) |
| Mã YC `LH/26/4538` + **STT** | Mã chuyến `CH/AIR/26/0257` |
| Shop + SĐT liên hệ | Điểm đến (sân bay/đại lý) + AWB/HBL |
| Số kiện `3/7` (kiện thứ 3 của 7) | Số kiện `3/7` |
| Kg + CBM | Kg + CBM |
| **QR** (mở trang tra cứu) | **QR** |
| Ngày tạo tem + người in | Ngày xuất + chuyến |

### 3.4 Bốn điểm quét (mỗi điểm = 1 lần xác nhận)

| # | Điểm quét | Ai · Bề mặt | Quét xong thì |
|---|---|---|---|
| ① | Lấy hàng tại shop | Tài xế · APP (P1) / web (P0) | kiện `picked_up`; đủ/thiếu so với dự kiến → ghi lệch |
| ② | Nhập kho | Kho · APP | kiện `in` kho; sinh/khớp `WH/IN` |
| ③ | Xuất chuyến | Kho · APP | kiện lên chuyến; khớp `WH/OUT` |
| ④ | Giao khách | Manager/Chứng từ · WEB | POD + `delivered` |

Quy tắc quét (đề xuất):
- Quét **sai mốc** (ví dụ quét tem OUT khi chưa nhập kho) ⇒ chặn + báo lý do.
- Quét **trùng** ⇒ báo "đã quét ở mốc X lúc …" (không tạo bản ghi mới).
- Quét **kiện không thuộc chuyến** ⇒ cảnh báo đỏ + nút "Chuyển sang chuyến đúng".
- Mỗi lần quét trả JSON gọn: `{kien, job, chuyen, trang_thai, canh_bao}` để app hiện tức thì.

### 3.5 In tem & in lại
- Web: nút **In tem** trên form YC (in IN) / form chuyến (in OUT, in hàng loạt theo chuyến).
- In lại: bắt buộc nhập lý do → ghi vào `lab.scan.event` loại `in_lai` (audit).
- App: P1 mở PDF chia sẻ; P2 in máy in nhiệt Bluetooth.

### 3.6 Trang tra cứu công khai (QR)
`GET /lab/track/<access_token>?k=<mã kiện>` → HTML tiếng Việt, **không cần đăng nhập**: mã kiện, shop,
trạng thái mới nhất, 4 mốc thời gian (lấy hàng → nhập kho → xuất chuyến → giao), số kiện.
Dùng đúng quy ước: **không lộ giá/không lộ dữ liệu khách khác**.

### 3.7 Chống trùng & audit
- `client_uuid` (app sinh) + unique index ⇒ quét 2 lần chỉ 1 dòng (đã có tiền lệ ở `capture`).
- Rate limit như các API khác (login 30/phút, nghiệp vụ 120/phút).
- Mọi lần quét đều ghi `user_id` + `thiet_bi` ⇒ truy được ai làm ở đâu.

---

## 4. Bảng tổng kết & lập lại kế hoạch

### 4.1 Bảng tổng kết (web, cho Sếp/Manager)
| Chỉ số | Ý nghĩa |
|---|---|
| Tem IN phát hành / Tem OUT phát hành (ngày) | khối lượng đóng gói — xuất |
| Số kiện đã quét ở từng mốc | tiến độ thực tế |
| **Tỉ lệ quét đúng lần đầu** | chất lượng dán tem/đóng gói |
| **Số kiện lệch** (thiếu/thừa/sai chuyến) | vấn đề cần xử lý |
| **Cycle time 3 mốc**: shop → kho → chuyến | đo bằng chênh lệch thời điểm quét |
| Danh sách kiện đang "kẹt" (quá N giờ chưa mốc kế tiếp) | chỗ cần lập lại kế hoạch |

### 4.2 Lập lại kế hoạch từ dữ liệu quét
- Kiện lệch ⇒ tạo **activity** cho CS kèm mã kiện + lý do.
- Kiện kẹt > 24h ⇒ đưa vào "Chuyến quá tải/giảm tải" (nhánh ②) hoặc đổi chuyến (nút có sẵn trên dòng chuyến).
- App kho có nút **"Báo lệch"** ngay trên màn quét.

---

## 5. Thứ tự thi công (đề xuất)

### P0 — để demo được "chuỗi tem" (≈ 2,5 ngày)
| # | Việc | Ước lượng |
|---|---|---|
| P0-1 | Sequence `lab.kien` + tự sinh **N kiện** khi tài xế capture (N = số kiện thực tế) | 3h |
| P0-2 | Report **tem IN** 100×70 (QWeb PDF) + nút In tem trên YC | 4h |
| P0-3 | Trang **tra cứu công khai** `/lab/track/<token>?k=…` (QR mở bằng camera bất kỳ) | 3h |
| P0-4 | API **quét** `POST /lab/api/v1/scan` + `lab.scan.event` (audit, chống trùng) | 4h |
| P0-5 | Web: ô **quét mã** + hiện kết quả (máy quét USB = bàn phím) | 3h |
| P0-6 | Đổi thứ tự danh sách **cũ nhất trước** + cột **STT** + **Tạo lúc** (web + app) | 2h |
| P0-7 | 7 việc phân vùng đã duyệt ở vòng 1 (P0-1…P0-7 tài liệu `phan-vung-app-web.md`) | đã ước lượng |

### P1 (≈ 1,5 ngày)
Tem **OUT** + in hàng loạt · quét camera **trong app** (ZXing) · bảng tổng kết §4.1 · in lại tem có lý do ·
kho nhập số kiện thực tế khi nhận.

### P2 (không làm trong demo)
Máy in nhiệt Bluetooth · quét GS1 · quét offline rồi đồng bộ · bản đồ tuyến.

### Rủi ro
| Rủi ro | Giảm |
|---|---|
| Thêm model `lab.scan.event` có thể bị coi là "thêm model không cần" | chỉ lưu **sự kiện quét** (không phải chứng từ); chứng từ vẫn là `stock.picking`; có ADR 5 dòng |
| In tem sai khổ / QR không đọc được | test in PDF ở 100×70 và quét bằng 2 điện thoại khác nhau trước khi demo |
| Sinh 7 kiện tự động khi capture có thể sai số | cho sửa số kiện trước khi in; ghi rõ "in lại" khi lệch |
| Quét làm thay đổi tồn kho ngoài ý muốn | quét **chỉ xác nhận** ở P0; việc validate `WH/OUT` vẫn là nút riêng của kho (P0-4 vòng 1) |

---

## 6. Checklist xét duyệt

**A. Phân vùng (đã nêu ở vòng 1, nhắc lại để chốt cùng lúc)**

| # | Quyết định | Đề xuất |
|---|---|---|
| D1 | Bỏ màn **CS · Điều phối** khỏi app | ✅ |
| D2 | CS được **ghi chuyến** trên web (Chốt booking vẫn của manager) | ✅ |
| D3 | **Kho rời web** — thêm màn Xuất kho trên app | ✅ |
| D4 | Gán tài xế ở 2 nơi (web: CS · app: trưởng nhóm) | ✅ |
| D5 | Giữ app **Sếp · Tổng quan** | ✅ |
| D6 | Kho **chỉ đọc** web (tra tồn) | ✅ |

**B. Mã đơn & thứ tự (mới)**

| # | Quyết định | Đề xuất |
|---|---|---|
| N1 | Danh sách **cũ nhất lên đầu** (`create_date asc`) — áp cho web YC + Đơn bán, và app danh sách "chờ xử lý" | ✅ |
| N2 | Hiện thêm cột **STT** (số sequence) và **Tạo lúc** trên mọi danh sách | ✅ |
| N3 | Giữ prefix mã hiện tại (`LH/26/…`, `S0xxxx`) — **không** đổi để khỏi sửa 149 đơn | ✅ |
| N4 | Mã kiện mới `KIEN/26/000123` (sequence riêng, pad 6) | ✅ |

**C. Tem & quét (mới)**

| # | Quyết định | Đề xuất |
|---|---|---|
| N5 | Kiện dùng **`stock.quant.package`** (không tạo model `kien`) | ✅ (Luật 1) |
| N6 | Cho phép tạo model **`lab.scan.event`** để audit + bảng tổng kết | ⬜ cần anh gật (kèm ADR 5 dòng) |
| N7 | Tem in **PDF 100×70mm**, QR = URL tra cứu công khai | ✅ |
| N8 | P0 chỉ làm **ô quét trên web** (máy quét USB); quét camera trong app để P1 | ✅ (nhanh, chắc) |
| N9 | 4 điểm quét: lấy hàng · nhập kho · xuất chuyến · giao khách | ✅ |
| N10 | Bảng tổng kết tem/kiện cho Sếp + Manager (P1) | ✅ |

---

## 7. Sau khi anh duyệt — tôi làm theo thứ tự này

1. **Chốt tài liệu**: ghi các quyết định đã duyệt vào `workflow/phan-vung-app-web.md` (phần "đã chốt").
2. **Vòng 1 (phân vùng)**: P0-3 → P0-1/P0-2 → P0-4/P0-5 → P0-6/P0-7 như đã nêu.
3. **Vòng 2 (mã + tem)**: P0-1…P0-6 của §5.
4. **Dọn nợ UI**: sửa **36 lỗi tương phản** trong `:composeApp:selfCheck` (app vẫn đang dùng lime/mint làm
   chữ trên nền sáng) — phải xanh trước khi demo.
5. Sau mỗi mục: **1 ảnh chụp + 1 câu SQL/API kiểm chứng**, không báo "xong" bằng lời.

Chỉ cần anh trả lời kiểu: **"D1–D6 ok, N1–N5 ok, N6 ok/N6 bỏ, N7–N10 ok — làm theo §7"**.

---

# BỔ SUNG 2026-11-02 (b) — PHIẾU NHẬP, PHIẾU XUẤT, KÊNH KHÁCH TỰ MANG TỚI KHO

## 8. Phiếu nhập kho (`WH/IN`) — là gì, giá trị gì, và một lỗ hổng đang có

### 8.1 Định nghĩa
**Phiếu nhập kho** = chứng từ ghi nhận **hàng đi từ bên ngoài vào kho của mình**. Trong Odoo là
`stock.picking` với loại `incoming`, vị trí `Nhà cung cấp (Partners/Vendors)` → `Kho (Stock)`.
Sinh tự động ở bước ⑤ (kho bấm "Nhận hàng", `picked_up → confirmed`).

### 8.2 Giá trị — 4 việc nó làm (đây là lý do Luật 4 bắt buộc phải có)
1. **Tăng tồn kho**: từ lúc validate, số kiện/kg nằm trong kho của mình — có cơ sở nói "hàng này đang ở kho tôi".
2. **Truy vết & chống mất hàng**: mỗi kiện vào kho có phiếu + ngày + người nhận ⇒ tranh chấp với shop là có bằng chứng.
3. **Đối soát với phiếu xuất**: `nhập − xuất = còn trong kho`. Không có phiếu nhập thì con số "còn kho" vô nghĩa.
4. **Cơ sở tính tiền & giá vốn**: cước/giá vốn gắn với khối lượng, mà khối lượng được **chốt tại thời điểm nhập**
   (`lab_weight_kg`, `lab_package_count` snapshot) — không phải số dự kiến của shop.

### 8.3 LỖ HỔNG ĐANG CÓ (đo 2026-11-02 — cần sửa trước demo)
```sql
select count(*) from stock_picking where origin like 'LH/%';        -- 3
select count(*) from stock_move   where picking_id in (…) ;          -- 0
```
Ba phiếu nhập `WH/IN/00487`, `WH/IN/00486`, `WH/IN/00045` đều ở `draft` và **có 0 dòng hàng**.
Nghĩa là: phiếu **chỉ có phần đầu phiếu**, chưa có `stock.move` ⇒ **tồn kho không tăng**, phiếu không có giá trị kho.

**Đề xuất sửa (P0):**
- Tạo **1 sản phẩm đại diện `KIỆN GOM LẺ`** (`type = product`, không theo lô/serial) — dùng làm dòng hàng cho mọi
  phiếu kho của nghiệp vụ gom hàng (vì hàng trong kiện là của khách, không phải sản phẩm mình bán).
- Khi nhận: sinh **đúng `stock.move`** với `quantity = so_kien_thuc_te`, gắn `stock.quant.package` (mỗi kiện 1 package)
  và **validate** phiếu ⇒ tồn kho = số kiện thực tế đang giữ.
- Phiếu xuất (`WH/OUT`) làm ngược lại ⇒ trừ tồn. Hai đầu khớp thì "còn kho" mới đúng.

## 9. Phiếu xuất (`WH/OUT`) — sinh ở mốc nào?

### 9.1 Hiện trạng
**Không** sinh khi gán hàng vào chuyến. Hiện phải vào **Kho › Delivery Orders** tạo tay rồi tự gắn "Chuyến".
Vì vậy câu "phiếu out là phiếu có khi gán chuyến cho hàng đó" — **đúng về ý nghĩa, nhưng hệ thống chưa làm tự động**.

### 9.2 Đề xuất mốc sinh (chọn 1)
| Mốc | Ưu | Nhược |
|---|---|---|
| (a) Khi **thêm dòng** vào chuyến | sớm | còn đổi/gỡ đơn ⇒ phải huỷ–tạo lại phiếu nhiều lần |
| **(b) Khi bấm "Chốt booking"** ← **đề xuất** | chuyến đã chốt = danh sách hàng đã chắc; kho có phiếu để chuẩn bị trước khi xe tới | nếu sau đó gỡ đơn phải sửa phiếu (hiếm, chấp nhận) |
| (c) Khi "Khởi hành" | muộn | kho không có phiếu để soạn hàng trước |

**Cách làm (b):** `action_confirm_booking` → nếu chuyến chưa có phiếu xuất thì sinh **1 phiếu `WH/OUT`** cho cả chuyến:
- `origin` = mã chuyến, `lab_chuyen_id` = chuyến, `partner_id` = khách của chuyến,
- dòng hàng = `KIỆN GOM LẺ` × tổng số kiện của các dòng `booked`, gắn package của từng kiện,
- trạng thái `assigned` (chờ kho xác nhận).
Sau đó **kho** mở app → **Kho · Xuất kho** → quét/nhập số kiện → **Xác nhận xuất** ⇒ `done` (trừ tồn).
Chốt: **một chuyến = một phiếu xuất** (đơn giản, dễ đối soát; nhiều phiếu chỉ khi chuyến gộp nhiều kho — P2).

## 10. LUỒNG MỚI — khách TỰ MANG HÀNG TỚI KHO (kho tự tạo đơn)

### 10.1 Vấn đề
Luồng hiện tại giả định **luôn có tài xế tới shop lấy**, nên: `new → assigned → en_route → picked_up → confirmed`.
Khách tự mang tới kho thì **3 bước giữa không tồn tại**, và **kho hiện không có quyền tạo** yêu cầu
(ACL `lab.yeu.cau.lay.hang` của nhóm Kho = `1,1,0,0` — thiếu `create`).

### 10.2 Thiết kế

**Field mới trên `lab.yeu.cau.lay.hang`:**
| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `kenh_nhan` | Selection `[('tai_shop','Tài xế tới shop lấy'), ('tai_kho','Khách tự mang tới kho')]`, default `tai_shop`, required | chọn kênh ngay khi tạo |
| `nguoi_giao_ten` / `nguoi_giao_sdt` | Char | ai mang hàng tới quầy (ghi tên/SĐT thay vì tài xế) |
| `gio_nhan_tai_kho` | Datetime | thời điểm kho nhận tại quầy |
| `bien_nhan_quay` | Binary/attachment | ảnh biên nhận/CCCD/phiếu giao của khách (tuỳ chọn) |

**State machine thêm 1 nhánh:**
```
KÊNH TÀI XẾ (tai_shop):   new → assigned → en_route → picked_up → confirmed
KÊNH TẠI KHO (tai_kho):   new ───────────────────────────────────→ confirmed
                                (kho bấm "Nhận tại quầy")
```
- Method mới `action_nhan_tai_quay()`: chỉ cho `kenh_nhan = tai_kho`, chỉ từ `new`; **sinh `WH/IN` ngay** như §8,
  ghi `gio_nhan_tai_kho`, `so_kien_thuc_te`, in tem IN.
- Không nới lỏng `action_confirm` (giữ nguyên luật "phải `picked_up`") để không mở đường nhận khống.

**Quyền:** ACL `lab.yeu.cau.lay.hang` cho nhóm **Kho** `1,1,0,0 → 1,1,1,0` (kho tạo được đơn tại quầy).
Đơn bán (`sale.order`) **không** mở cho kho — CS gắn đơn sau (`sale_order_id` vốn cho phép trống).

**Trên app (kho — bề mặt chính):** màn mới **"Kho · Nhận tại quầy"**:
1. Bấm **+ Nhận hàng tại quầy** → nhập nhanh: shop (chọn/tạo mới được không? **không** — CS tạo khách; kho chọn
   từ danh sách, nếu chưa có thì ghi tên vào `ghi_chu` và CS bổ sung sau), SĐT người giao, số kiện, kg, ghi chú.
2. **Nhận** → hệ thống tạo YC `kenh_nhan = tai_kho`, state `confirmed`, sinh `WH/IN`, in **tem IN** ngay tại quầy.
3. Kiện vào sổ "đang ở kho chờ chuyến".

**Trên web:** menu YC vẫn để kho **xem**; CS thấy các đơn `tai_kho` để gắn đơn bán + gom chuyến như thường.

**Bảng tổng kết:** tách 2 kênh — số kiện/kg **nhận qua tài xế** vs **nhận tại quầy**; tỉ lệ kênh; cycle time
(kênh tại kho chỉ có 2 mốc: nhận → xuất chuyến).

### 10.3 Vì sao thiết kế như vậy (không tạo model mới)
- YC lấy hàng vốn là "phiếu tiếp nhận hàng" — thêm 1 field chọn kênh là đủ, không cần model `don_tai_quay`.
- `WH/IN` vẫn là chứng từ duy nhất cho sự kiện "hàng vào kho" ⇒ Luật 4 được giữ, kế toán không bị hai nguồn.
- Kho tạo được YC nhưng **không** tạo được đơn bán ⇒ không phá phân vùng "tiền và giá là của CS/kế toán".

## 11. THỜI GIAN TẠO ĐƠN — làm cụ thể thế nào

| Nơi | Hiện tại | Sau khi sửa |
|---|---|---|
| Web list YC / Đơn bán | không có cột thời gian, sắp `id desc` (mới nhất lên đầu) | thêm cột **"Tạo lúc"** `dd/MM HH:mm` + cột **"Tuổi đơn"** (giờ kể từ lúc tạo); `default_order="create_date asc, id asc"` ⇒ **đơn cũ nằm trên**; thêm bộ lọc "Chờ xử lý (cũ → mới)" / "Mới nhất" |
| App (mọi danh sách) | sắp theo `khung_gio_tu asc` | danh sách **chờ xử lý** sắp `create_date asc, id asc`; card hiện thêm dòng **"Tạo: 02/11 14:35 · 3 giờ trước"** |
| Form web | có `create_date` (Odoo tự có) | hiện thành dòng thông tin "Tạo lúc … bởi …" ở đầu form |
| Tem in | không có | in kèm **"Tạo: dd/MM HH:mm"** để truy vết giấy |
| Bảng tổng kết | — | thêm chỉ số **"Tuổi đơn trung bình"** và **"Đơn chờ lâu nhất"** |

## 12. Checklist bổ sung (xin duyệt cùng D1–D6 và N1–N10)

| # | Quyết định | Đề xuất |
|---|---|---|
| N11 | **Sửa WH/IN để có dòng hàng thật + validate** (tạo product `KIỆN GOM LẺ`, qty = số kiện) ⇒ tồn kho mới đúng | ✅ nên làm P0 |
| N12 | **WH/OUT sinh tự động khi bấm "Chốt booking"** (1 chuyến = 1 phiếu), kho xác nhận trên app | ✅ (mốc b) |
| N13 | Thêm field **`kenh_nhan`** + nhánh state **tại quầy** (`new → confirmed`) | ✅ |
| N14 | **Cấp quyền tạo YC cho Kho** (ACL `1,1,0,0 → 1,1,1,0`) | ✅ |
| N15 | App có màn **"Kho · Nhận tại quầy"** + in tem IN tại quầy | ✅ |
| N16 | Kho **không** được tạo/ sửa **đơn bán** (giữ cho CS/admin) | ✅ |

Ước lượng thêm cho §8–§10: **≈ 1,5 ngày** (WH/IN dòng hàng 4h · WH/OUT auto 3h · kênh tại quầy + ACL 3h ·
app màn nhận tại quầy 4h · test 2h).
---

# BỔ SUNG 2026-11-02 (c) — AI SINH TEM, AI IN, AI DÁN, AI QUÉT

## 13. Vòng đời một cái tem (đây là chỗ dễ thiết kế sai nhất)

> ⚠️ **MỤC NÀY ĐÃ BỊ THAY bằng §15.** Chốt 2026-11-02: **không** để tài xế dán/quét tem; tem chỉ sinh
> tại kho. Giữ lại để thấy lý do đổi (bản này đòi in tem trước ở văn phòng + tài xế dán tại shop —
> thêm 2 bước và thêm rủi ro thất lạc tem).

Câu hỏi đúng: *"tài xế quét cái gì, tem của ai dán?"* — Nếu tem chỉ sinh ra **sau khi** tài xế đếm kiện
(như bản đề xuất ban đầu) thì **lúc tài xế tới shop làm gì có tem mà quét**. Phải chốt vòng đời tem TRƯỚC.

### 13.1 Nguyên tắc chọn (theo thực tế hàng gom lẻ)
- Tem **phải tồn tại trước** khi tài xế tới shop ⇒ phát hành **ngay khi CS tạo Yêu cầu lấy hàng**
  (số lượng = **số kiện dự kiến**), không phải lúc tài xế đếm.
- Shop nhỏ thường **không có máy in** ⇒ mặc định **kho/văn phòng in theo tuyến, tài xế mang theo**;
  shop in từ link tra cứu là **tuỳ chọn** (shop nào có máy in thì gửi file trước).
- Một kiện = **một mã QR duy nhất**, dùng lại ở **mọi mốc** (không in tem mới cho mỗi mốc).

### 13.2 Bảng chốt — 5 mốc, 1 mã kiện

| Mốc | Ai **in** tem | Ai **dán** | Ai **quét** | Quét để làm gì |
|---|---|---|---|---|
| **0. Tạo YC** (CS, web) | Hệ thống sinh N mã kiện (`KIEN/26/…`) theo số kiện dự kiến; **CS bấm In tem** | — | — | — |
| **1. Chuẩn bị tuyến** (kho/văn phòng) | In 1 lượt cho cả tuyến (A4 8 tem/trang hoặc 100×70) | — | — | — |
| **2. Tại shop** (tài xế, app) | *(không in)* | **Tài xế dán** (hoặc shop dán nếu đã nhận file) | **Tài xế quét từng tem** sau khi dán | Xác nhận "kiện này đã lấy", khớp đúng YC; thiếu/thừa là biết ngay tại chỗ |
| **3. Nhập kho** (kho, app) | In lại nếu tem rách | — | **Kho quét** | Xác nhận kiện đã vào kho (`WH/IN`), mốc bắt đầu tính tồn |
| **4. Xuất chuyến** (kho, app) | In **tem chuyến** (mã chuyến + AWB) | Kho dán tem chuyến lên thùng gộp | **Kho quét tem kiện** | Xác nhận kiện đúng chuyến (`WH/OUT`) |
| **5. Giao khách** (manager/chứng từ, web) | — | — | Quét (nếu có máy quét) / tick tay | Đóng kiện vào POD |

### 13.3 Hai loại tem, đừng lẫn
1. **Tem IN (tem kiện)** — dán **lên từng kiện**, QR = mã kiện. Sinh lúc tạo YC, dùng cho mốc 2·3·4.
2. **Tem OUT (tem chuyến)** — dán **lên thùng gộp/pallet của chuyến**, QR = mã chuyến + AWB/HBL.
   Sinh lúc **Chốt booking** (cùng lúc sinh `WH/OUT`). Dùng ở cổng xuất và khi giao.

### 13.4 Xử lý ca "số kiện thực tế ≠ dự kiến" (bắt buộc phải có, nếu không là hỏng luồng)
- **Thực tế nhiều hơn**: tài xế bấm **"＋ Kiện không tem"** trong app → nhập số lượng → hệ thống sinh mã
  `KIEN/26/000…-T` (T = tạm), ghi log `loai = khong_tem`; kho **in bù** ở mốc 3 rồi **quét lại** để thay mã tạm.
- **Thực tế ít hơn**: các tem còn lại bấm **"Không lấy được kiện này"** + lý do ⇒ kiện sang `cancelled`,
  không tính vào `WH/IN`, bảng tổng kết ghi nhận lệch.
- Trường hợp tem rách/mất: in lại (có lý do) — mã giữ nguyên, log `in_lai`.

### 13.5 Vì sao tài xế quét lại có giá trị (dù chính họ dán)
- **Chống dán sai kiện** giữa 2 shop trong cùng tuyến (mã thuộc YC khác ⇒ app báo đỏ ngay).
- **Xác nhận thời điểm** "kiện này lấy lúc 14:32" ⇒ đo được thời gian chờ ở mỗi shop.
- **Không cần giấy**: chụp ảnh + ký vẫn giữ (bằng chứng pháp lý), quét là **dữ liệu số** cho bảng tổng kết.

### 13.6 Thứ tự ưu tiên (điều chỉnh lại P0/P1)
| Việc | Vòng | Ghi chú |
|---|---|---|
| Sinh mã kiện **lúc tạo YC** (N = số kiện dự kiến) | **P0** | đổi so với bản trước (trước đây sinh lúc capture) |
| In tem IN theo lô (A4 nhiều tem/trang) + nút In tem trên YC | **P0** | |
| Web: ô quét mã + `POST /lab/api/v1/scan` + `lab.scan.event` | **P0** | |
| App: quét camera tại shop + "Kiện không tem" | **P1** | |
| Tem OUT (tem chuyến) in khi chốt booking | **P1** | |
| In bù khi tem rách + log | **P1** | |

## 14. Cheklist bổ sung (vòng đời tem)

| # | Quyết định | Đề xuất |
|---|---|---|
| L1 | Mã kiện **phát hành ngay khi tạo YC** (số kiện dự kiến), không đợi tài xế đếm | ✅ |
| L2 | **Kho/văn phòng in tem theo tuyến**, tài xế mang theo; shop in từ link là tuỳ chọn | ✅ |
| L3 | **Tài xế dán tem tại shop** rồi quét lại từng tem | ✅ |
| L4 | Có nút **"Kiện không tem"** + in bù cho ca phát sinh | ✅ |
| L5 | **Tem OUT là tem chuyến** (không phải tem kiện), in khi Chốt booking | ✅ |
| L6 | Một mã kiện **dùng lại** ở mọi mốc (không in tem mới mỗi mốc) | ✅ |
---

# CHỐT 2026-11-02 (d) — TEM SINH TẠI KHO, TÀI XẾ KHÔNG DÙNG TEM

> Đây là bản **thay thế §13–§14**, anh đã chốt bằng lời:
> *"đừng để tài xế dán, nào kiện về kho thì dán; lúc nhận hàng thì kho in tem nhận, nhận xong quét tem nhận;
> tới lúc xuất thì tạo tem xuất, lúc xuất ra khỏi kho thì quét cái mã xuất đó."*

## 15. Vòng đời tem (bản chốt)

### 15.1 Nguyên tắc
- **Tài xế KHÔNG dán tem, KHÔNG quét tem.** Tài xế giữ nguyên cách làm hiện tại: *Bắt đầu đi → Chụp ảnh
  + Ký + Đếm số kiện/kg → gửi*. Không thêm thao tác nào cho tài xế.
- **Tem chỉ sinh ra tại kho**, đúng lúc kiện nằm trong tay kho.
- **Tem nhận** và **tem xuất** là **hai tem khác nhau**, dán ở hai thời điểm khác nhau, in tại kho.
- Mỗi tem có **mã riêng** + **QR riêng**, dùng cho đúng một mốc.

### 15.2 Bảng chốt — 2 lần in tem, 2 lần quét

| Mốc | Ai làm | Hành động | Hệ thống làm gì |
|---|---|---|---|
| **1. Nhận hàng** | Kho (APP) | Tài xế mang kiện về → kho bấm **Nhận hàng & in tem nhận** | Sinh `stock.picking WH/IN` **có dòng hàng thật** + sinh **N mã kiện** (`KIEN/26/000123`, N = `so_kien_thuc_te`); in **N tem nhận** |
| **2. Dán tem nhận** | Kho (tay) | Dán lên từng kiện | — |
| **3. Quét tem nhận** | Kho (APP) | Quét từng tem để xác nhận đã dán đúng | Ghi `lab.scan.event` loại `nhan`; kiện `in_stock`; validate `WH/IN` ⇒ **tồn kho tăng** |
| **4. Tạo tem xuất** | Kho / tự động khi **Chốt booking** | Bấm **In tem xuất** cho các kiện của chuyến | Sinh **N tem xuất** (`XUAT/26/000456`) mỗi tem chứa: mã xuất + mã kiện + mã chuyến + AWB/HBL + điểm đến |
| **5. Xuất kho** | Kho (APP) | Dán tem xuất lên kiện → quét từng tem khi đưa lên xe | Ghi `lab.scan.event` loại `xuat`; đủ kiện ⇒ **Xác nhận xuất** ⇒ validate `WH/OUT` ⇒ **tồn kho giảm** |
| **6. Giao khách** | Manager/Chứng từ (WEB) | Tick/quét trên phiếu | POD + `delivered` |

### 15.3 Hai loại tem — nội dung
| Tem **NHẬN** (`KIEN/26/000123`) | Tem **XUẤT** (`XUAT/26/000456`) |
|---|---|
| Mã kiện (to) · QR (tra cứu công khai) | Mã xuất (to) · QR |
| Mã YC `LH/26/4538` + **STT** | Mã kiện gốc + Mã chuyến `CH/AIR/26/0257` |
| Shop + SĐT · `3/7` kiện · kg · CBM | Điểm đến + AWB/HBL · `3/7` kiện · kg |
| **Tạo lúc: dd/MM HH:mm** · người nhận | **Xuất lúc** · người xuất |

### 15.4 Vì sao bản này tốt hơn bản trước (bỏ 2 bước, bỏ 2 rủi ro)
- **Bỏ** việc in tem trước ở văn phòng rồi chuyển cho tài xế (thất lạc tem, in sai số kiện dự kiến).
- **Bỏ** việc tài xế phải quét tại shop (thêm thao tác ngoài đường, mạng chập chờn).
- Số kiện trên tem = **số kiện thực tế tài xế đếm** (không phải số dự kiến) ⇒ không còn ca "thiếu/thừa tem".
- Mọi thao tác tem đều ở **một nơi có Wi-Fi + máy in**: kho.

### 15.5 Trạng thái kiện (vòng đời)
```
(kiện chưa tồn tại)  →  [Nhận hàng]  →  in_stock  →  [In tem xuất]  →  packed
   →  [Quét xuất]  →  shipped  →  [Giao khách]  →  delivered
                              ↘  cancelled (không lấy được / huỷ chuyến)
```
Mỗi chuyển trạng thái đều ghi `lab.scan.event` (ai, lúc nào, thiết bị) ⇒ bảng tổng kết đọc từ đây.

### 15.6 Chống trùng & ca lệch
- Quét **tem nhận 2 lần** ⇒ báo "đã nhận lúc …", không cộng tồn.
- Quét **tem xuất 2 lần** ⇒ báo "đã xuất lúc …".
- Quét **tem xuất của chuyến khác** ⇒ chặn + nút "Đổi sang chuyến đúng".
- **Cho phép xuất thiếu** nhưng bắt nhập lý do; kiện thiếu sang `cancelled` và ghi vào bảng tổng kết.
- Tem rách/mất: **in lại** (mã giữ nguyên) + log `in_lai` có lý do.

### 15.7 Việc phải sửa, theo thứ tự (thay cho §5 của bản (c))
| # | Việc | Vòng |
|---|---|---|
| 1 | `WH/IN` có **dòng hàng thật** (product "KIỆN GOM LẺ", qty = số kiện) + validate ⇒ tồn tăng | **P0** |
| 2 | Sinh **N mã kiện** ngay khi kho bấm *Nhận hàng* + sequence `KIEN/26/` | **P0** |
| 3 | Report **tem nhận** (PDF 100×70 + bản A4 nhiều tem/trang) — in ngay sau khi nhận | **P0** |
| 4 | API **quét** + `lab.scan.event` + ô quét trên web (máy quét USB) & app (P1 camera) | **P0** (web) / **P1** (app camera) |
| 5 | Sinh **tem xuất** khi Chốt booking (`XUAT/26/…`), in theo chuyến | **P0** |
| 6 | Màn app **Kho · Xuất kho**: danh sách tem/kiện của chuyến → quét → **Xác nhận xuất** (validate `WH/OUT` ⇒ tồn giảm) | **P0** |
| 7 | Trang tra cứu QR công khai (2 loại tem cùng trỏ về 1 trang, hiện mốc nhận/xuất) | **P0** |
| 8 | Kiện thiếu → lý do + `cancelled` | **P1** |

### 15.8 Checklist chốt (thay L1–L6)
| # | Nội dung | Trạng thái |
|---|---|---|
| M1 | Tài xế **không** dùng tem (không dán, không quét) | ✅ anh đã chốt |
| M2 | **Tem nhận** in tại kho ngay khi bấm *Nhận hàng*, số lượng = số kiện thực tế | ✅ anh đã chốt |
| M3 | Nhận xong **quét tem nhận** để xác nhận (validate `WH/IN`) | ✅ anh đã chốt |
| M4 | **Tem xuất** tạo cho chuyến; **quét tem xuất khi hàng rời kho** (validate `WH/OUT`) | ✅ anh đã chốt |
| M5 | Tem nhận và tem xuất là **2 tem khác nhau**, cùng in ở kho | ✅ suy ra từ trên |
| M6 | Tài xế giữ nguyên: ảnh + ký + đếm kiện (không thêm thao tác) | ✅ |
---

# §16. BẢN CHỐT THI CÔNG (sau 3 quyết định của anh)

## 16.1 Ba quyết định vừa chốt
| # | Nội dung | Ảnh hưởng |
|---|---|---|
| M7 | **Mỗi kiện một tem xuất** (quét từng kiện khi lên xe) | kiểm được thiếu/thừa theo từng kiện; màn xuất kho hiện tiến độ `đã quét 7/12` |
| M8 | **Một nút nhanh "Nhận hàng & in tem"** | kho bấm 1 lần: nhận + sinh mã kiện + mở PDF tem để in |
| M9 | **Cho phép xuất thiếu kiện** (bắt buộc nhập lý do) | kiện thiếu → `cancelled`; bảng tổng kết hiện "xuất thiếu N kiện" |

## 16.2 Hạ tầng có sẵn (đo 2026-11-02) — không phải tạo thêm
| Thành phần | Hiện có |
|---|---|
| Loại phiếu nhập | `stock.picking.type` id **1** — `Receipts / incoming / warehouse YourCompany` |
| Loại phiếu xuất | `stock.picking.type` id **2** — `Delivery Orders / outgoing / warehouse YourCompany` |
| Sản phẩm storable | 24 sản phẩm `type = product` (dùng chúng để test; sẽ tạo thêm 1 sản phẩm đại diện) |
| Package | `stock.quant.package` (0 dòng — sẽ dùng làm kiện) |
| Sequence | `lab.yeu.cau.lay.hang` (LH/26/…), `sale.order` (S0xxxx) |

## 16.3 Thứ tự thi công + tiêu chí nghiệm thu (mỗi mục phải có bằng chứng)

| # | Việc | Tiêu chí nghiệm thu (bằng chứng) | Ước lượng |
|---|---|---|---|
| **1** | Sản phẩm đại diện `KIỆN GOM LẺ` (`type=product`, tracking none, UoM Units, `default_code=KIEN-GOM`) | `select id,name,type from product_template where default_code='KIEN-GOM';` → 1 dòng | 0,5h |
| **2** | `action_confirm` sinh `WH/IN` **có `stock.move`** (qty = `so_kien_thuc_te`) và **validate** | `select sp.name, sp.state, count(sm.id) from stock_picking sp join stock_move sm on sm.picking_id=sp.id where sp.origin='LH/...' group by 1,2;` → state `done`, moves ≥ 1; `stock.quant` tăng đúng số kiện | 4h |
| **3** | Sequence `lab.kien` (`KIEN/%(y)s/` pad 6) + sinh N kiện khi bấm **"Nhận hàng & in tem"** | `select count(*) from stock_quant_package where lab_yeu_cau_id=<id>;` = `so_kien_thuc_te` | 3h |
| **4** | Report **tem nhận** (100×70 + bản A4 nhiều tem/trang) — bấm 1 nút ra PDF | Mở PDF: đủ trường §15.3 + QR; quét QR bằng điện thoại → mở trang tra cứu | 4h |
| **5** | Sequence `lab.xuat` (`XUAT/%(y)s/` pad 6) + sinh **N tem xuất** khi chốt booking, mỗi kiện 1 mã | `select count(*) from stock_quant_package where lab_chuyen_id=<cid> and lab_ma_xuat is not null;` = số kiện của chuyến | 3h |
| **6** | API **quét** `POST /lab/api/v1/scan` + model `lab.scan.event` (audit, `client_uuid` unique) | Quét 2 lần cùng mã → lần 2 trả `trung=true`, DB chỉ 1 dòng | 4h |
| **7** | App **Kho · Nhận hàng**: 1 nút *Nhận hàng & in tem* + ô quét tem nhận (tiến độ `đã quét x/ N`) → validate `WH/IN` | Ảnh màn app + SQL: `WH/IN` state `done` | 5h |
| **8** | App **Kho · Xuất kho**: danh sách tem/kiện của chuyến → quét từng kiện (`7/12`) → **Xác nhận xuất** (thiếu thì bắt lý do) → validate `WH/OUT` | Ảnh + SQL: `WH/OUT` `done`, `stock.quant` giảm đúng số kiện đã quét | 6h |
| **9** | Trang tra cứu QR công khai: hiện 2 mốc (nhận/xuất), mã kiện, mã xuất, chuyến | Mở URL trên điện thoại không đăng nhập | 3h |
| **10** | Đổi thứ tự danh sách + cột **Tạo lúc** / **STT** / **Tuổi đơn** (web + app) | Ảnh list: đơn cũ nằm trên, cột thời gian hiện `dd/MM HH:mm` | 2h |

Tổng ≈ **35h** cho chuỗi tem (chưa tính 7 việc phân vùng D1–D6 và 36 lỗi tương phản app).

## 16.4 Điều kiện kỹ thuật đã chốt để khỏi tranh luận giữa chừng
- Kiện = `stock.quant.package` + **inherit thêm 4 field** (`lab_yeu_cau_id`, `lab_chuyen_id`, `lab_ma_xuat`, `lab_xuat_luc`) — **không** tạo model `kien`.
- `lab.scan.event`: model nhỏ **9 field** (`kien_id, job_id, picking_id, loai, ket_qua, user_id, thiet_bi, client_uuid, thoi_diem`) + ADR 5 dòng. Nếu anh **không** muốn model này thì phương án B là ghi vào chatter của phiếu — nhưng bảng tổng kết sẽ phải quét `mail_message` (chậm, xấu) ⇒ **tôi đề xuất vẫn tạo**.
- Nút *Nhận hàng & in tem* là **1 endpoint** `POST /lab/api/v1/kho/jobs/<id>/nhan` (mở rộng endpoint có sẵn) trả về `{picking, packages[], pdf_url}`.
- Quét là **xác nhận**, không phải ghi sổ: chỉ khi bấm *Xác nhận xuất* / *Hoàn tất nhận* mới `button_validate`.