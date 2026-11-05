# LUỒNG TEST ĐẦY ĐỦ — TỪ "KHÁCH MUỐN GỬI HÀNG" TỚI "HÀNG ĐÃ GỬI XONG"

> Cập nhật **2026-11-03** theo hệ thống ĐANG CHẠY (sau khi có tem + quét + nhận tại quầy).
> Mỗi bước ghi: **ai · thiết bị nào · bấm ở đâu · kết quả phải thấy · kiểm bằng gì**.
> Tài khoản mật khẩu chung `lab@2026` (riêng `admin`/`admin`).

---

## 0. Ba bề mặt & phân vùng (chốt, không đổi khi test)

| Bề mặt | Thiết bị | Dùng cho |
|---|---|---|
| **APP** | Tablet Android (cài `composeApp-debug.apk`, Máy chủ Odoo = `http://192.168.1.20:8069`) | **Tài xế · Trưởng nhóm tài xế · Kho** |
| **WEB** | `http://localhost:8069` trên máy chạy Odoo | **CS · Manager · Chứng từ · Kế toán · Sếp** (+ Kho đọc) |
| **TRACK (công khai)** | Điện thoại bất kỳ quét QR | Khách/shop tra cứu kiện |

**Vai trò trên app:** `taixe01…05` (Tài xế) · `nhomtruong01` (Phân công) · `kho01` (Kho) · `sep` (Tổng quan).
**Vai trò trên web:** `cs01` · `manager01` · `chungtu01` · `ketoan01` · `sep` · `admin`.
⚠️ `cs01` mở app sẽ thấy **"Vai trò này làm việc trên Odoo web"** — đó là đúng (quyết định D1).

---

## A. LUỒNG CHÍNH — tài xế tới shop lấy hàng (16 bước)

### Bước 1 — Tạo khách (chỉ khi khách mới) · `cs01` · **WEB**
Gom hàng lẻ → **Khách hàng (Shop)** → **Tạo** → tên shop, SĐT, địa chỉ lấy, ghi chú → **Lưu**.
*Kiểm:* danh sách khách có bản ghi mới. (Đường nhanh: tạo ngay trong ô **Shop** của YC — "Tạo và chỉnh sửa".)

### Bước 2 — Tạo Yêu cầu lấy hàng · `cs01` · **WEB**
Gom hàng lẻ → **Yêu cầu lấy hàng** → **Tạo**:
- **Shop**, địa chỉ lấy, người liên hệ + SĐT, **khung giờ HÔM NAY**, số kiện dự kiến, kg.
- `Kênh nhận` để **Tài xế tới shop lấy** (mặc định).
- **Lưu** → trạng thái **Mới**.
*Kiểm:* `select name,state,kenh_nhan from lab_yeu_cau_lay_hang order by id desc limit 1;` → `new | tai_shop`.

### Bước 3 — Gán tài xế · `cs01` (web) **hoặc** `nhomtruong01` (app) · **WEB/APP**
- **Web:** mở YC → nút **Gán tài xế** → chọn tài xế (chỉ nhóm Tài xế) → **Gán**.
- **App (trưởng nhóm):** tab **Phân công** → mở việc → chọn tài xế → **Gán tài xế** (nút khoá thành "Đã gán").
*Kết quả:* `new → assigned`. *Kiểm:* `select state,driver_id from lab_yeu_cau_lay_hang where id=<id>;`

### Bước 4 — Tài xế bắt đầu đi · `taixe01` · **APP**
Tab **Tài xế · Việc hôm nay** → mở việc → **Bắt đầu đi** → `Đang đến lấy`.
⚠️ App **chỉ hiện việc có khung giờ hôm nay**. Không thấy → chạy `infra\scripts\reset-ngay-demo.ps1`.

### Bước 5 — Lấy hàng tại shop · `taixe01` · **APP**
Mở việc → **Chụp ảnh & ký biên nhận** → Ảnh → Ký → **số kiện thực tế + kg** → **Gửi & xác nhận** → `Đã lấy được hàng`.
*Kiểm:* `select state,so_kien_thuc_te,(chu_ky is not null) as co_ky from lab_yeu_cau_lay_hang where id=<id>;`

### Bước 6 — Kho nhận hàng + IN TEM NHẬN · `kho01` · **APP** ⭐ (mới)
Tablet → tab **Kho** → mở việc **Đã lấy được hàng** → bấm **Nhận hàng & in tem**:
- Hệ thống sinh **phiếu nhập `WH/IN`** (có dòng hàng thật) + **N kiện `KIEN/26/000123`** + **N tem nhận** (mỗi kiện 1 tem, QR tra cứu).
- Việc → `Đã về kho`; **tồn kho tăng đúng N kiện**.
*Kiểm:*
```sql
select sp.name, sp.state, sp.lab_package_count from stock_picking sp
 join lab_yeu_cau_lay_hang j on j.stock_picking_id=sp.id where j.id=<id>;   -- done | N
select p.name, p.lab_trang_thai from stock_quant_package p
 where p.id in (select result_package_id from stock_move_line where picking_id=<picking>);
```
*In lại tem:* nút **In lại tem nhận** (mã kiện không đổi).

### Bước 7 — Dán tem nhận rồi QUÉT xác nhận · `kho01` · **APP**
Dán tem lên từng kiện → khối **"Kiện của việc"** hiện danh sách `KIEN/…` + chip *Chưa quét* → **quét/nhập mã kiện** từng cái:
- Đúng → chip xanh **Đã xác nhận**, tiến độ `x/N` tăng.
- Quét lại → vàng **Đã quét trước đó** (không cộng tiến độ).
- Sai mốc / mã lạ → đỏ kèm câu giải thích của server.
Đủ N → nút **Hoàn tất nhận** bật.
*Kiểm:* `select count(*) from lab_scan_event where loai='nhan' and ket_qua='ok' and kien_id in (<các kiện>);` = N.

### Bước 8 — Gom đơn vào chuyến · `cs01` · **WEB**
Gom hàng lẻ → **Chuyến** → mở chuyến (`Nháp`/`Đang mở`) → tab **Đơn hàng trong chuyến** → **Thêm dòng**:
- Cột **Đơn hàng của shop** = **mã đơn bán** (`S0149…` — gõ mã, KHÔNG gõ tên shop), Khách hàng tự điền, Số kiện/kg/CBM.
- Chuyến `Nháp` → bấm **Mở chuyến**.
*Kiểm:* `select count(*) from lab_chuyen_line where chuyen_id=<cid>;` ⚠️ Nếu bấm Chốt booking lúc còn dòng thiếu đơn → lỗi **"Invalid fields: Đơn hàng trong chuyến"** (xem §C).

### Bước 9 — Chốt booking (sinh phiếu xuất + tem xuất) · `manager01` · **WEB** ⭐
Mở chuyến → **Chốt booking** → `Đang mở → Đã chốt booking`. Đồng thời hệ thống:
- Sinh **1 phiếu `WH/OUT`** cho cả chuyến (trạng thái `assigned`), dòng hàng = tổng kiện.
- Sinh **mỗi kiện 1 mã tem xuất `XUAT/26/000123`**.
*Kiểm:*
```sql
select name,state,lab_package_count from stock_picking where lab_chuyen_id=<cid> and picking_type_id in
 (select id from stock_picking_type where code='outgoing');            -- 1 phiếu | assigned
select name,lab_ma_xuat,lab_trang_thai from stock_quant_package
 where lab_chuyen_id=<cid>;                                            -- packed + XUAT/…
```

### Bước 10 — In tem xuất · `kho01`/`manager01` · **WEB** ⭐
Chuyến → nút **In tem xuất** → PDF khổ 100×70, mỗi kiện 1 trang: mã xuất, mã kiện, chuyến, điểm đến, AWB/HBL, QR.

### Bước 11 — Xuất kho: dán tem xuất + QUÉT từng kiện · `kho01` · **APP** ⭐
Tablet → tab **Kho** → toolbar **Xuất kho** → mở chuyến:
- Danh sách kiện + chip trạng thái; quét/nhập **mã xuất** từng kiện → tiến độ `x/N`.
- Đủ N → **Xác nhận xuất** → phiếu `WH/OUT` → **done**, tồn kho **giảm** N kiện.
- Thiếu kiện → hộp thoại **bắt buộc nhập lý do** → vẫn xuất được; kiện thiếu → `Không xuất`, ghi vào `lab.scan_event`.
*Kiểm:*
```sql
select name,state from stock_picking where lab_chuyen_id=<cid> and picking_type_id in
 (select id from stock_picking_type where code='outgoing');            -- done
select lab_trang_thai,count(*) from stock_quant_package where lab_chuyen_id=<cid> group by 1;
```
*Nhánh 409 "còn kiện chưa quét"*: app hiện danh sách kiện còn lại + mở hộp thoại lý do — đây là **đường quay về**, không phải lỗi.

### Bước 12 — Khởi hành (+ AWB/HBL) · `manager01` / `chungtu01` · **WEB**
- Chuyến → **Khởi hành** → `Đã chốt booking → Đã khởi hành`.
- **Hành trình** → mở chuyến bay → nhập **số AWB** (bay) / **HBL** (biển), sân bay, ETD/ETA → bấm **statusbar** đổi mốc `booked → departed → in_transit`.

### Bước 13 — Đã đến nơi · `manager01` · **WEB**
Chuyến → **Đã đến nơi** (`departed → arrived`); Hành trình → `arrived`.

### Bước 14 — Giao khách + POD · `manager01` / `chungtu01` · **WEB**
Chuyến → **Đã giao khách** (`arrived → delivered`); Hành trình `handed_to_agent → delivered` + `pod_date` + đính kèm POD.

### Bước 15 — Khoá chuyến · `manager01` · **WEB**
Chuyến → **Khoá chuyến** (`delivered → closed`); Hành trình `closed`. **Đơn hoàn thiện về vận tải.**

### Bước 16 — Tiền · `ketoan01` → `manager01` · **WEB**
1. **Bán hàng → Đơn bán** → mở đơn → **Create Invoice** → **Post** → `INV/…` đã vào sổ.
2. **Kế toán → Misc** → bút toán journal **DEBIT** + gắn **Chuyến** (Nợ 131 / Có 511) → **Post** sẽ **BỊ CHẶN** ("validation process is still open") → `manager01` mở phiếu → **Validate** → `ketoan01` **Post** lại → `posted`.
   *(30 phiếu Debit đang `draft` trong data là ĐÚNG — đó là màn "chờ duyệt".)*

### Bước 17 — Đối soát · `sep` · **WEB + APP**
Web → **Bảng điều khiển** (KPI + 7 ngày + donut). App → `sep` → tab **Tổng quan**.

---

## B. LUỒNG PHỤ — KHÁCH TỰ MANG HÀNG TỚI KHO (nhận tại quầy) ⭐ mới

Khác luồng A từ bước 3 đến bước 7: **không có tài xế**, kho tạo và nhận tại quầy.

### B1 — Kho tạo yêu cầu · `kho01` · **WEB hoặc APP**
Web: **Yêu cầu lấy hàng → Tạo** → Shop, SĐT người giao, **Kênh nhận = Khách tự mang tới kho**, số kiện thực tế, kg → **Lưu**.
*Kiểm:* `select state,kenh_nhan from lab_yeu_cau_lay_hang order by id desc limit 1;` → `new | tai_kho`.

### B2 — Nhận tại quầy · `kho01` · **WEB**
Mở YC → nút **Nhận tại quầy** → hệ thống: `new → confirmed`, sinh **`WH/IN` done** + N kiện + ghi `gio_nhan_tai_kho`.
*Kiểm:* `select state,gio_nhan_tai_kho,stock_picking_id from lab_yeu_cau_lay_hang where id=<id>;`
*App:* mở việc **Đã về kho** → khối kiện + quét tem nhận như bước 7.

### B3 — Tiếp tục như A từ bước 8
CS gắn đơn bán + gom chuyến → manager chốt booking → kho xuất kho (bước 9–11) → …

---

## C. 6 nhánh ngoại lệ (mỗi nhánh có ĐIỂM QUAY VỀ)

| # | Tình huống | Vào ở đâu | Quay về bằng |
|---|---|---|---|
| ① | Hàng chưa rõ khách | YC có shop "Khách chưa xác định" (`LH/26/4303`, `LH/26/4341`) | CS sửa `Shop` → đi tiếp bước 3 |
| ② | Chuyến quá tải (>100%) | Menu **Chuyến quá tải**, vd `CH/AIR/26/0267` (164%) | Sửa dòng chuyến → **Đã gỡ khỏi chuyến** + **Lý do gỡ** → thêm đơn sang chuyến khác |
| ③ | YC thiếu địa chỉ/khung giờ | `select name from lab_yeu_cau_lay_hang where dia_chi_lay is null;` | CS bổ sung trên web → việc hiện lại trong app tài xế |
| ④ | Xuất thiếu kiện | Bước 11 bấm Xác nhận khi `x < N` | Nhập lý do → kiện thiếu `Không xuất` (có trong `lab.scan_event`) |
| ⑤ | Chuyển kho bay ↔ tàu | Phiếu nội bộ `origin LIKE 'DEMO-LON-INTERNAL%'` | Validate phiếu chuyển kho rồi vào chuyến tương ứng |
| ⑥ | Khiếu nại / trả hàng | Hành trình `exception` | `exception → returned → compensated` (không để treo) |

Nghiệp vụ không có ngoại lệ **huỷ việc**: CS bấm **Huỷ việc** trên form YC (chỉ khi chưa `confirmed`).

---

## D. Bảng tra nhanh: việc nào làm ở đâu

| Việc | App | Web |
|---|---|---|
| Tạo khách, tạo YC, sửa YC | ✗ | ✓ (`cs01`) |
| **Gán tài xế** | ✓ (`nhomtruong01`) | ✓ (`cs01` — wizard) |
| Bắt đầu đi · ảnh + ký + đếm kiện | **✓ duy nhất** | ✗ |
| **Nhận hàng + in tem nhận + quét tem** | **✓ duy nhất** (`kho01`) | In lại tem (đọc) |
| **Nhận tại quầy (khách tự mang tới)** | (xem kiện + quét) | ✓ (`kho01` bấm nút) |
| Gom chuyến · Mở chuyến | ✗ | ✓ (`cs01`) |
| Chốt booking · Khởi hành · Đến · Giao · Khoá | ✗ | ✓ (`manager01`) |
| In tem xuất | ✗ | ✓ (`kho01`/`manager01`) |
| **Xuất kho + quét từng kiện** | **✓ duy nhất** (`kho01`) | ✗ |
| AWB/HBL · POD · sự cố | ✗ | ✓ (`chungtu01`) |
| Hoá đơn · Debit · duyệt khoá | ✗ | ✓ (`ketoan01` → `manager01`) |
| Đối soát | ✓ (`sep`) | ✓ (`sep`) |
| Tra cứu QR của kiện | Điện thoại nào cũng được (không cần đăng nhập) | cùng URL |

---

## E. Kiểm tổng thể sau khi chạy hết (copy-paste)

```sql
-- 1. Không còn việc "đã về kho" mà thiếu phiếu nhập (Luật 4)
select count(*) from lab_yeu_cau_lay_hang j left join stock_picking sp on sp.id=j.stock_picking_id
 where j.state='confirmed' and (sp.id is null or sp.state<>'done');
-- kỳ vọng: 0

-- 2. Kiện & tem
select lab_trang_thai, count(*) from stock_quant_package group by 1;
-- kỳ vọng: in_stock / packed / shipped / cancelled (không có NULL)

-- 3. Nhật ký quét (chống trùng: mỗi client_uuid 1 dòng)
select count(*) as tong, count(distinct client_uuid) as uuid from lab_scan_event;

-- 4. Token tra cứu phải là token, không phải mã chuyến
select name, access_token from lab_chuyen where state<>'cancelled' limit 5;

-- 5. Tồn kho kiện (bút toán kép)
select sl.complete_name, sum(q.quantity) from stock_quant q
 join product_product p on p.id=q.product_id join stock_location sl on sl.id=q.location_id
 where p.default_code='KIEN-GOM' group by 1;
```

```powershell
# Gate & test trước khi demo
powershell -ExecutionPolicy Bypass -File infra\scripts\check.ps1
docker compose -f infra/docker-compose.yml run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf `
  -d odoo17_lab --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh,/lab_api --stop-after-init
cd apps\kmp; .\gradlew.bat :composeApp:selfCheck :composeApp:desktopTest
```

---

## F. Bẫy phải biết trước khi test (đã gặp thật)

1. App tài xế **chỉ trả việc có khung giờ HÔM NAY** → chạy `reset-ngay-demo.ps1` nếu dữ liệu cũ ngày.
2. Tablet **không dùng `adb reverse`** → app phải trỏ `http://192.168.1.20:8069` (cùng Wi-Fi).
3. Cột **"Đơn hàng của shop"** chỉ nhận **mã đơn bán** (`S0149…`), gõ tên shop **không ra**.
4. Bấm **Chốt booking** khi còn dòng thiếu đơn → **"Invalid fields: Đơn hàng trong chuyến"**; xoá dòng trống hoặc chọn đơn.
5. Các nút trạng thái chuyến (Chốt/Khởi hành/Đến/Giao/Khoá) **chỉ manager/sale_admin** thấy.
6. **Tem in trước 2026-11-03** (QR kiểu mã chuyến) giờ **404** → in lại tem xuất.
7. `lyDoThieu` của lần xuất thiếu lưu ở `lab_scan_event.ghi_chu` (từng kiện), chưa có field trên phiếu.
8. Kg trên tem là **ước tính** (tổng kg chuyến ÷ số kiện).
