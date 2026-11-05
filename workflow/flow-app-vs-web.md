# MỘT ĐƠN HÀNG ĐI TRỌN ĐƯỜNG — BƯỚC NÀO LÀM Ở APP, BƯỚC NÀO LÀM Ở WEB

> Viết 2026-11-02, đo trực tiếp trên DB `odoo17_lab` đang chạy và trên APK v5 cài trong
> máy tính bảng Samsung SM-X133 (kết nối cáp USB-C).
> Cách đọc: mỗi bước ghi rõ **ai bấm · bấm ở đâu (APP hay WEB) · bấm gì · trạng thái trước → sau ·
> sinh chứng từ gì · kiểm bằng câu SQL nào**. Câu SQL chạy được ngay:
>
> ```powershell
> docker compose -f infra/docker-compose.yml exec -T db psql -U odoo -d odoo17_lab -c "<câu SQL>"
> ```

---

## 0. Hai bề mặt và tài khoản

| Bề mặt | Thiết bị / địa chỉ | Dùng cho ai |
|---|---|---|
| **APP** (Kotlin Multiplatform) | Máy tính bảng Samsung SM-X133, APK `composeApp-debug.apk`, đăng nhập `http://192.168.1.12:8069` (cùng Wi-Fi với máy chạy Odoo) | **Tài xế · Trưởng nhóm tài xế · CS · Kho · Sếp** |
| **WEB** (Odoo 17) | `http://localhost:8069` trên máy chạy Odoo | **CS · Manager · Kế toán · Kho · Sếp · Admin** |

Mật khẩu chung `lab@2026` (riêng `admin` = `admin`).

| Tài khoản | Nhân vật | Màn hình mở ra ở APP |
|---|---|---|
| `taixe01`…`taixe05` | tài xế đi lấy hàng | **Tài xế · Việc hôm nay** |
| `nhomtruong01` | trưởng nhóm tài xế | **Trưởng nhóm tài xế · Phân công** |
| `cs01` | CS điều phối | **CS · Điều phối** |
| `kho01` | kho | **Kho · Nhận hàng** |
| `sep` | giám đốc | **Sếp · Tổng quan** |
| `chungtu01` · `ketoan01` · `manager01` · `admin` | chứng từ · kế toán · quản lý · quản trị | **"Vai trò khác" → làm việc trên WEB** (app cố tình báo vậy, không giả vờ có màn) |

**Nguyên tắc phân vùng (quan trọng nhất của tài liệu này):**

> **APP = tay chân tại hiện trường** (nhận việc, chụp ảnh, ký, đếm kiện, giao nhận).
> **WEB = đầu não văn phòng** (gom chuyến, chốt booking, xuất kho, khởi hành, đến nơi, giao khách,
> tiền, đối soát).
> Chặng ②③④⑤ **chỉ có trên app** — form Yêu cầu lấy hàng trên web **không có nút đổi trạng thái**.
> Chặng ⑥⑦⑧⑨⑩⑪⑫⑬ **chỉ có trên web** — app không có màn chuyến/hành trình/kế toán.

---

## 1. Bảng tổng — 1 đơn đi từ "khách muốn gửi" tới "giao xong, tiền xong"

| # | Bước | Ai | **Nơi** | Bấm gì | Trạng thái sau | Chứng từ / dữ liệu sinh ra |
|---|---|---|---|---|---|---|
| ① | Tạo khách (lần đầu) | `cs01` | **WEB** | Gom hàng lẻ › Khách hàng (Shop) → Tạo | — | `res.partner` (`customer_rank=1`) |
| ② | Tạo Yêu cầu lấy hàng | `cs01` | **WEB** | Gom hàng lẻ › Yêu cầu lấy hàng → Tạo | `Mới` (`new`) | `lab.yeu.cau.lay.hang` |
| ③ | Gán tài xế | `nhomtruong01` hoặc `cs01` | **APP** | Phân công / Điều phối → mở việc → chọn tài xế → **Gán tài xế** | `Đã gán tài xế` (`assigned`) | `driver_user_id` |
| ④ | Bắt đầu đi | `taixe01` | **APP** | Tài xế › Việc hôm nay → mở việc → **Bắt đầu đi** | `Đang đến lấy` (`en_route`) | (chatter) |
| ⑤ | Lấy hàng: ảnh + ký + đếm kiện | `taixe01` | **APP** | mở việc → **Chụp ảnh & ký biên nhận** → Ảnh → Ký → số kiện/kg → **Gửi & xác nhận** | `Đã lấy được hàng` (`picked_up`) | ảnh (`ir.attachment`) + `chu_ky` (PNG) + `so_kien_thuc_te` |
| ⑥ | Kho nhận hàng | `kho01` | **APP** | Kho · Nhận hàng → chọn việc → **Nhận hàng** | `Đã về kho` (`confirmed`) | **Tự sinh phiếu nhập `WH/IN/…`** (REA) |
| ⑦ | Gom việc vào chuyến | `cs01`/`manager01` | **WEB** | Chuyến → mở chuyến → tab *Đơn hàng trong chuyến* → Thêm dòng → **Mở chuyến** | chuyến `Nháp → Đang mở` | `lab.chuyen.line` (`booked`), `ti_le_tai` |
| ⑧ | Chốt booking | `manager01` | **WEB** | chuyến → **Chốt booking** | `Đã chốt booking` | `so_booking`, hãng, cut-off |
| ⑨ | Xuất kho (hàng rời kho) | `kho01` | **WEB** | Kho › Delivery Orders → tạo phiếu, gắn **Chuyến** → **Validate** | phiếu `Done` | `stock.picking` `WH/OUT/…` |
| ⑩ | Khởi hành | `manager01` | **WEB** | chuyến → **Khởi hành**; Hành trình: nhập AWB/HBL, đổi mốc statusbar | chuyến `Đã khởi hành`; hành trình `departed`/`in_transit` | `lab.hanh.trinh.so_awb` / `so_hbl` |
| ⑪ | Đã đến nơi | `manager01` | **WEB** | chuyến → **Đã đến nơi**; hành trình → `arrived` | `Đã đến nơi` | mốc `ngay_den` |
| ⑫ | Giao khách + biên bản (POD) | `manager01`/`chungtu01` | **WEB** | chuyến → **Đã giao khách**; hành trình `handed_to_agent → delivered` + nhập `pod_date`, đính kèm POD | `Đã giao khách` | `pod_date` + tệp POD |
| ⑬ | Khoá chuyến | `manager01` | **WEB** | chuyến → **Khoá chuyến**; hành trình `closed` | `Đã khoá` | chốt số liệu chuyến |
| ⑭ | Hoá đơn khách | `ketoan01` | **WEB** | Bán hàng › Đơn bán → **Create Invoice** → **Post** | hoá đơn `Đã vào sổ` | `account.move` `INV/…` |
| ⑮ | Phiếu ghi nợ (Debit) + duyệt | `ketoan01` → `manager01` | **WEB** | Kế toán › Misc → bút toán journal **DEBIT** + gắn Chuyến → Post (**bị chặn**) → manager **Validate** → Post lại | `draft → posted` | `DEBIT/…` |
| ⑯ | Đối soát | `sep` | **WEB** (hoặc APP) | Bảng điều khiển trên web / tab **Tổng quan** trên app | — | KPI, biểu đồ 7 ngày |

Tổng kết bằng một câu: **1 bước tạo khách + 1 bước tạo yêu cầu + 4 bước hiện trường trên APP + 10 bước
văn phòng trên WEB + 1 bước đối soát.**

---

## 2. Chi tiết từng bước (bấm được ngay, có số thật)

### Bước ① — Tạo khách (chỉ làm khi khách mới)
- **WEB** → **Gom hàng lẻ › Khách hàng (Shop)** (`/web#menu_id=339&action=496`) → **New**.
- Nhập: tên shop, điện thoại, địa chỉ lấy hàng, ghi chú → **Lưu**.
- Đường nhanh: ngay trong form Yêu cầu lấy hàng, gõ tên ở ô **Shop** → **Tạo và chỉnh sửa**.
- **Kiểm**: `select id,name,phone,customer_rank from res_partner where customer_rank>0 order by id desc limit 5;`

### Bước ② — Tạo Yêu cầu lấy hàng (WEB)
- **WEB** → **Gom hàng lẻ › Yêu cầu lấy hàng** (`/web#menu_id=334&action=489`) → **Tạo**.
- Chọn **Shop**, địa chỉ lấy, người liên hệ + SĐT, **khung giờ lấy**, số kiện dự kiến, kg.
- **Lưu ý bắt buộc để app nhìn thấy việc**: khung giờ phải là **HÔM NAY** (app tài xế lọc theo ngày).
- Kết quả: bản ghi `Mới`, **chưa** sinh chứng từ kho/tiền.

### Bước ③ — Gán tài xế (APP)
- Đăng nhập APP bằng `nhomtruong01` (màn **Trưởng nhóm tài xế · Phân công**) hoặc `cs01` (**CS · Điều phối**).
- Mở việc `Mới` → chọn tài xế trong danh sách (chỉ 5 người nhóm `driver`: `Trần Văn Hùng · taixe01` …) → **Gán tài xế**.
- Trạng thái: `new → assigned`. Kiểm: `select state, driver_user_id from lab_yeu_cau_lay_hang where id=<id>;`

### Bước ④ — Tài xế bắt đầu đi (APP)
- Đăng nhập APP bằng tài xế vừa được gán → màn **Tài xế · Việc hôm nay**.
- Mở việc → **Bắt đầu đi**. Kết quả: `assigned → en_route`.
- Không thấy việc? Xem **§5 bẫy 1** (chỉ việc có khung giờ hôm nay).

### Bước ⑤ — Lấy hàng tại shop: ảnh + chữ ký + số kiện (APP)
- Cùng màn → **Chụp ảnh & ký biên nhận** → 3 bước: **Ảnh** → **Ký** (vẽ tay) → **số kiện / kg** → **Gửi & xác nhận**.
- Kết quả: `en_route → picked_up`; lưu `so_kien_thuc_te`, `khoi_luong_uoc_kg`, `chu_ky`, 1..n ảnh.
- **Kiểm**:
  ```sql
  select state, so_kien_thuc_te, khoi_luong_uoc_kg, (chu_ky is not null) as co_chu_ky
  from lab_yeu_cau_lay_hang where id=<id>;
  select count(*) from ir_attachment where res_model='lab.yeu.cau.lay.hang' and res_id=<id>;
  ```

### Bước ⑥ — Kho nhận hàng → **tự sinh phiếu nhập** (APP)
- Đăng nhập APP bằng `kho01` → **Kho · Nhận hàng** → chọn việc `Đã lấy được hàng` → **Nhận hàng**.
- Kết quả: `picked_up → confirmed` + **Odoo tự tạo `stock.picking` WH/IN** và gắn vào việc.
- **Kiểm**:
  ```sql
  select j.name, j.state, sp.name as phieu_nhap, sp.state
  from lab_yeu_cau_lay_hang j join stock_picking sp on sp.id=j.stock_picking_id where j.id=<id>;
  ```
- Đây là mốc **REA**: hàng vào kho **phải** có phiếu, không có chuyện "trạng thái đổi mà không có chứng từ".

> **Từ bước ⑦ trở đi, app hết vai trò.** Tài xế/kho không bấm gì thêm; mọi thứ còn lại là văn phòng.

### Bước ⑦ — Gom việc vào chuyến (WEB) — **bước dễ vấp nhất**

**ĐIỀU KIỆN TIÊN QUYẾT: phải có ĐƠN BÁN (`sale.order`) cho shop đó.**
Dòng chuyến trỏ tới **Đơn bán**, không trỏ tới Yêu cầu lấy hàng. YC lấy hàng chỉ *có thể* gắn đơn
(`sale_order_id`, cho phép trống) — không thay thế được đơn bán.

0. **Tạo đơn bán** (nếu chưa có): **WEB** → **Bán hàng › Đơn bán › Tạo** → chọn khách → thêm dòng
   sản phẩm (cước) → **Xác nhận** (state `sale`). Đơn có sẵn để thử: `S01499`…`S01492` (130 đơn
   ở trạng thái `sale`/`sent`).

1. **WEB** → **Chuyến** (`/web#menu_id=332&action=487`) → mở chuyến (ví dụ `CH/AIR/26/0257`).
2. Tab **Đơn hàng trong chuyến** → **Thêm dòng**.
3. Cột **Đơn hàng của shop** → gõ **mã đơn** (`S0149`…) → chọn trong danh sách.
   - **Gõ tên shop KHÔNG ra** — `sale.order` tìm theo mã đơn (đo 2026-11-02: gõ `Medico` → 0 kết
     quả; gõ `S0149` → 8 kết quả). Muốn tìm rộng hơn bấm **Search More…**.
   - Dropdown đã lọc `state in ('sale','sent')` — không còn đơn nháp/đã huỷ để chọn nhầm.
4. Nhập **Số kiện** / **Khối lượng (kg)** / **Thể tích (CBM)** (snapshot chốt với khách), bấm ra
   ngoài ô để lưu dòng. Kiểm **Sức chứa / Đã dùng / Tỉ lệ tải %** đã tăng.
5. Nếu chuyến đang `Nháp` → bấm **Mở chuyến** → `open`.
- **Kiểm**: `select name,state,ti_le_tai from lab_chuyen where id=<cid>;`

**BẪY "Invalid fields: Đơn hàng trong chuyến"** (đo 2026-11-02, tái hiện được):
bấm **Chốt booking** khi trong tab còn **một dòng có dữ liệu nhưng chưa chọn "Đơn hàng của shop"**
(điển hình: bấm *Thêm dòng*, gõ Số kiện rồi bấm luôn Chốt booking). Nút header buộc Odoo **lưu
form trước**, gặp trường bắt buộc rỗng ⇒ hộp đỏ, ô đó viền đỏ. Dòng **hoàn toàn trống** thì Odoo tự
bỏ qua, không lỗi. Sửa: chọn đơn cho dòng đó, hoặc xoá dòng (thùng rác), hoặc **Huỷ/Esc** bỏ thay
đổi rồi bấm lại.

**Ai thêm dòng được**: `manager01`, `kho01`, `chungtu01`, `sale_admin`. **`cs01` chỉ ĐỌC chuyến**
(ACL `lab.chuyen` của nhóm CS là `1,0,0,0`) ⇒ mở form sẽ không có nút Thêm dòng/Lưu.


### Bước ⑧ — Chốt booking (WEB)
- **WEB** → chuyến → **Chốt booking** → điền `số booking`, hãng, cut-off, ngày đi.
- Kết quả: `open → booking_confirmed`.
- Duyệt 2 cấp (nếu demo): form chuyến → **Request Validation** → cấp 1 `sale_admin` → cấp 2 `manager` (chỉ chuyến bay).

### Bước ⑨ — Xuất kho WH/OUT (WEB)
- **WEB** → **Kho › Delivery Orders** → tạo phiếu xuất từ kho bay/tàu, **ghi `Chuyến`** = chuyến vừa chốt → **Validate**.
- Vì sao làm tay: tab *Phiếu xuất kho* trong chuyến chỉ để **xem** (chưa nối nút tạo — xem §6).
- **Kiểm**: `select name,state,origin from stock_picking where lab_chuyen_id=<cid>;`

### Bước ⑩ — Khởi hành (WEB)
- **WEB** → chuyến → **Khởi hành** (`booking_confirmed → departed`).
- **WEB** → **Hành trình** (`/web#menu_id=336&action=491`) → mở chuyến bay → nhập **số AWB** (đường bay) /
  **số HBL** (đường biển), sân bay đi/đến, ETD/ETA → bấm **statusbar** đổi mốc `booked → departed → in_transit`.
- **Kiểm**: `select name,stage,so_awb,so_hbl from lab_hanh_trinh where id=<hid>;`

### Bước ⑪ — Đã đến nơi (WEB)
- chuyến → **Đã đến nơi** (`departed → arrived`); hành trình → mốc `arrived`.

### Bước ⑫ — Giao khách + POD (WEB)
- chuyến → **Đã giao khách** (`arrived → delivered`).
- hành trình: statusbar `handed_to_agent → delivered`, nhập `pod_date`, đính kèm biên bản giao hàng.
- **Kiểm**: `select stage,pod_date from lab_hanh_trinh where id=<hid>;`

### Bước ⑬ — Khoá chuyến (WEB)
- chuyến → **Khoá chuyến** (`delivered → closed`); hành trình → `closed`. Đơn coi như **hoàn thiện về vận tải**.

### Bước ⑭ — Hoá đơn khách (WEB)
- **Bán hàng › Đơn bán** → mở đơn `sale` → **Create Invoice** → **Post** → `INV/…` ở trạng thái **Đã vào sổ**.

### Bước ⑮ — Phiếu ghi nợ Debit + duyệt khoá (WEB) — **bẫy tiền**
1. **Kế toán › Misc** → tạo bút toán trên journal **DEBIT**, gắn **Chuyến**, 2 dòng: Nợ 131 / Có 511.
2. Bấm **Post** → **BỊ CHẶN**: `Validation process is still open for at least one record`.
3. `manager01` mở phiếu → **Validate** → `ketoan01` **Post** lại → `posted`.
- Đây là chủ đích thiết kế (kiểm soát 2 người), không phải lỗi.

### Bước ⑯ — Đối soát (WEB, hoặc APP với `sep`)
- **WEB** → **Bảng điều khiển** (`/web#menu_id=338&action=493`); **APP** → `sep` → tab **Tổng quan** (KPI + biểu đồ 7 ngày).

---

## 3. Ranh giới app ↔ web — bảng tra nhanh

| Việc | APP | WEB |
|---|---|---|
| Tạo/sửa khách | ✗ | ✓ |
| Tạo Yêu cầu lấy hàng | ✗ | ✓ |
| Gán tài xế | **✓ (chỉ ở đây)** | ✗ (chỉ xem) |
| Bắt đầu đi · chụp ảnh · ký · đếm kiện | **✓ (chỉ ở đây)** | ✗ (chỉ xem) |
| Kho nhận hàng, lấy số phiếu WH/IN | **✓ (chỉ ở đây)** | ✗ (chỉ xem) |
| Gom việc vào chuyến, mở chuyến | ✗ | ✓ |
| Chốt booking · khởi hành · đến nơi · giao khách · khoá chuyến | ✗ | ✓ |
| Hành trình AWB/HBL, mốc vận chuyển | ✗ | ✓ |
| Xuất kho WH/OUT | ✗ | ✓ |
| Hoá đơn, Debit, duyệt khoá | ✗ | ✓ |
| Xem KPI/đối soát | ✓ (tab Tổng quan, `sep`) | ✓ (Bảng điều khiển) |

**Một câu để nhớ**: *App lo 4 bước "ngoài đường" — Web lo 10 bước "trong phòng".*

---

## 4. Kịch bản test 20 phút (số liệu thật, hôm nay 2026-11-02)

Chuẩn bị: Odoo đang chạy (`http://localhost:8069` trả 200), tablet đã cài APK và đăng nhập
`http://192.168.1.12:8069`. Dữ liệu hôm nay **đã được dời về đúng ngày**: 65 việc có khung giờ hôm nay
(`5 Mới · 20 Đã gán · 10 Đang đến lấy · 16 Đã lấy được hàng`) — nhờ `infra/scripts/reset-ngay-demo.ps1`.

| # | Việc | Ai · Nơi | Thao tác | Phải thấy |
|---|---|---|---|---|
| 1 | Tạo việc mới | `cs01` · **WEB** | Yêu cầu lấy hàng → Tạo: shop bất kỳ, **khung giờ hôm nay**, 3 kiện, 8 kg → Lưu | bản ghi `Mới`, mã `LH/26/…` |
| 2 | Gán tài xế | `nhomtruong01` · **APP** | Phân công → việc vừa tạo → `Trần Văn Hùng · taixe01` → **Gán tài xế** | chip **Đã gán tài xế** |
| 3 | Bắt đầu đi | `taixe01` · **APP** | Tài xế → mở việc → **Bắt đầu đi** | **Đang đến lấy** |
| 4 | Lấy hàng | `taixe01` · **APP** | **Chụp ảnh & ký** → Ảnh → Ký → 3 kiện/8 kg → **Gửi & xác nhận** | **Đã lấy được hàng** |
| 5 | Về kho | `kho01` · **APP** | Kho · Nhận hàng → **Nhận hàng** | **Đã về kho** + số phiếu **WH/IN/…** |
| 6 | Vào chuyến | `manager01` · **WEB** | Chuyến `CH/AIR/26/0257` → Đơn hàng trong chuyến → Thêm dòng → **Mở chuyến** | `Đang mở`, tỉ lệ tải tăng |
| 7 | Chốt booking | `manager01` · **WEB** | **Chốt booking** + số booking | **Đã chốt booking** |
| 8 | Xuất kho | `kho01` · **WEB** | Kho › Delivery Orders → tạo phiếu + gắn chuyến → **Validate** | phiếu `Done` |
| 9 | Khởi hành + AWB | `manager01` · **WEB** | chuyến **Khởi hành**; Hành trình `AWB-DEMO-002` → nhập AWB → statusbar `departed → in_transit` | chuyến **Đã khởi hành** |
| 10 | Đến nơi | `manager01` · **WEB** | **Đã đến nơi**; hành trình `arrived` | **Đã đến nơi** |
| 11 | Giao khách | `manager01` · **WEB** | **Đã giao khách**; hành trình `delivered` + `pod_date` | **Đã giao khách** |
| 12 | Khoá | `manager01` · **WEB** | **Khoá chuyến** | **Đã khoá** |
| 13 | Hoá đơn | `ketoan01` · **WEB** | Đơn bán → Create Invoice → Post | `INV/…` **Đã vào sổ** |
| 14 | Debit + duyệt | `ketoan01`→`manager01` · **WEB** | bút toán DEBIT → Post (**lỗi**) → Validate → Post lại | `posted` |
| 15 | Đối soát | `sep` · **WEB/APP** | Bảng điều khiển / Tổng quan | KPI nhích lên |

Việc có sẵn để test nhanh chặng ③④⑤ **hôm nay**: `LH/26/4289` (15:00, tài xế `taixe01`),
`LH/26/4297` (13:00, `taixe01`), `LH/26/4300` (12:00, `taixe03`).

---

## 5. Tám bẫy phải biết trước khi test

1. **App tài xế chỉ trả việc có khung giờ HÔM NAY** (việc của hôm qua không hiện dù đúng người).
   Dời dữ liệu về hôm nay: `powershell -File infra\scripts\reset-ngay-demo.ps1`.
2. **Form Yêu cầu lấy hàng trên web không có nút chuyển trạng thái** — ③④⑤ chỉ làm được trên app.
3. **Nút chụp ảnh chỉ bật khi việc ở `Đã gán` / `Đang đến lấy`.**
4. **Tab "Phiếu xuất kho" và "Debit (cước)" trong chuyến là chỉ-đọc** — phải tạo từ menu Kho/Kế toán rồi gắn chuyến.
5. **Hành trình không có nút chuyển mốc** — bấm vào **statusbar** trên form.
6. **Duyệt chuyến không chặn nút Chốt booking**, nhưng **duyệt Debit thì CHẶN post**.
7. **Rate limit**: login 30 lượt/phút/IP, `/driver/jobs` 120/phút — test tự động nhiều lần sẽ gặp `429`.
8. **Kết nối từ tablet**: máy này `adb reverse` không hoạt động (`nc 127.0.0.1 8069` từ tablet không trả gì),
   nên app phải trỏ **`http://192.168.1.12:8069`** (LAN), không dùng `127.0.0.1`/`10.0.2.2`.
   Máy ảo (`emulator-5554`) thì vẫn dùng `http://127.0.0.1:8069` + `adb reverse tcp:8069 tcp:8069`.

---

## 6. Cái gì CHƯA nối (đừng kỳ vọng khi demo)

| Gap | Hệ quả khi test | Hướng xử lý |
|---|---|---|
| Yêu cầu lấy hàng không có nút trạng thái trên web | mọi đổi trạng thái ③④⑤ phải qua app | thêm 5 nút `action_*` lên form (FE ~1h) |
| Hành trình không có nút `action_*` | chỉ đổi mốc bằng statusbar, chưa chặn lùi mốc | thêm 4 nút + luật "không lùi" (BE) |
| WH/OUT và Debit phải tạo tay ở menu khác | dễ quên gắn chuyến | thêm nút "Tạo phiếu xuất"/"Tạo Debit" trong chuyến (BE) |
| Duyệt chuyến không chặn Chốt booking | có thể chốt khi chưa duyệt | thêm kiểm tra `validated` vào `action_confirm_booking` |
| Chuyến `cancelled` chưa trả việc về trạng thái trước | việc nằm lại trong chuyến huỷ | xử lý khi huỷ chuyến |
| App không tự gia hạn phiên khi mở lại | mở lại app phải đăng nhập lại | lưu phiên / tự re-login |

---

## 7. Lệnh phụ trợ

```powershell
# 1. Dời dữ liệu demo về đúng HÔM NAY (để app tài xế có việc)
powershell -File infra\scripts\reset-ngay-demo.ps1

# 2. Đếm lại toàn bộ dữ liệu + KPI
powershell -File infra\scripts\verify-demo-lon.ps1

# 3. Test chặng ⑤ bằng script (login → capture → chống trùng → 4 câu SQL)
powershell -File infra\scripts\e2e-capture.ps1
```

Liên quan: `workflow/full-flow.md` (bản gốc 14 chặng, có ID thật theo ngày seed),
`workflow/demo-dataset.md` (bộ dữ liệu), `workflow/test-flow.md` (kịch bản kiểm thử).
