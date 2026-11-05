# LUỒNG ĐẦY ĐỦ — TỪ SHOP → KHO → MÁY BAY → GIAO KHÁCH → XÁC NHẬN

> Viết từ **code đang chạy** + **đo trên DB `odoo17_lab` ngày 2026-10-29**, không chép lại docs.
> Mỗi chặng có: **ai bấm · bấm ở đâu · bấm gì · trạng thái trước → sau · sinh chứng từ gì · kiểm bằng gì**.
> Bộ dữ liệu lớn đã có sẵn (`workflow/demo-dataset.md`) nên test được ngay, không phải tạo tay.

---

## 0. Ba đường thao tác

| Đường | Dùng cho | Ghi chú |
|---|---|---|
| **App KMP** (máy ảo `emulator-5554`) | tài xế, CS, kho, trưởng nhóm tài xế, admin | phải `adb reverse tcp:8069 tcp:8069`; đăng nhập `http://127.0.0.1:8069` |
| **Odoo web** `http://localhost:8069` | chuyến, hành trình, kho (phiếu), kế toán, dashboard | menu **Gom hàng lẻ** |
| **Script** | kiểm chứng không cần bấm | `infra/scripts/*.ps1` |

Mật khẩu chung `lab@2026`; riêng `admin` = `admin`. Tài khoản: `cs01` · `nhomtruong01` · `taixe01…05` · `kho01` · `chungtu01` · `ketoan01` · `manager01` · `sep` · `admin`.

---

## 1. Sơ đồ 14 chặng

```
 SHOP (điểm lấy hàng)                                                        KHÁCH NƯỚC NGOÀI
   │                                                                                  ▲
   │ ① CS tạo YC lấy hàng (web/app)          state: new                              │ ⑪ Đã giao khách
   ▼                                                                                  │    (delivered + POD)
 ② Gán tài xế (app: trưởng nhóm / CS) ──► assigned                                   │
   ▼                                                                                  │
 ③ Tài xế "Bắt đầu đi" (app) ──────────► en_route                                    │
   ▼                                                                                  │
 ④ Tài xế CHỤP ẢNH + KÝ + đếm kiện (app) ► picked_up  (+ảnh, +chữ ký)                 │
   ▼                                                                                  │
 ⑤ Kho "Nhận hàng" (app) ───────────── ► confirmed  ⇢ **tự sinh WH/IN** (REA)        │
   ▼                                                                                  │
 ⑥ CS gom việc vào CHUYẾN (web) ───────► chuyến: draft → open   (dòng: booked)        │
   ▼                                                                                  │
 ⑦ "Chốt booking" (web) ───────────────► booking_confirmed   + phiếu DUYỆT 2 cấp (⑥) ┤
   ▼                                                                                  │
 ⑧ Xuất kho WH/OUT (web/Kho) ──────────► hàng rời kho                                │
   ▼                                                                                  │
 ⑨ "Khởi hành" (web) ──────────────────► departed      ┐                             │
   │   Hành trình AWB/HBL: booked → departed → in_transit ┘  ✈ MÁY BAY               │
   ▼                                                                                  │
 ⑩ "Đã đến nơi" (web) ─────────────────► arrived   · hành trình: arrived             │
   │                                                                                  │
   ├──► ⑪ "Đã giao khách" (web) ───────► delivered · hành trình: handed_to_agent → delivered
   │                                                                                  │
   ├──► ⑫ "Khoá chuyến" (web) ─────────► closed    · hành trình: closed               │
   │                                                                                  │
   └──► ⑬ TIỀN: hoá đơn khách (từ đơn bán) + phiếu ghi nợ Debit + DUYỆT KHOÁ DEBIT ───┘
                                    ▼
                        ⑭ Đối soát: Bảng điều khiển (sep) + Debit đã khoá tháng
```

---

## 2. 14 chặng — chi tiết bấm được ngay

> Ví dụ dùng **đúng bản ghi có trong DB** (đo 2026-10-29). Cột "Kiểm" là lệnh chạy được.

### Chặng ⓪ — (khi khách lần đầu) Tạo khách mới
- **Ai**: `cs01` · **Ở đâu**: web → **Gom hàng lẻ › Khách hàng (Shop)** (`/web#menu_id=339&action=496`).
- **Bấm**: **New** → Tên shop/khách, điện thoại, email, MST, địa chỉ lấy hàng, ghi chú → **Lưu**.
  Khách tạo ở đây tự có `customer_rank = 1` nên hiện ngay trong danh sách (danh sách chỉ hiện khách đã
  phát sinh giao dịch, không lẫn 100+ partner demo).
- **Cách khác (nhanh hơn)**: ngay trong form Yêu cầu lấy hàng, ô **Shop** → gõ tên → **Tạo và chỉnh sửa**
  (tạo khách mới không cần rời màn).
- **Kiểm**: `select id,name,phone,city,customer_rank from res_partner where customer_rank>0 order by id desc limit 5;`
- **Đo thật 2026-10-29 (đã sửa)**: trước đây `cs01` **KHÔNG** tạo được khách — ACL `res.partner` chỉ cấp
  `perm_create` cho nhóm *Contact Creation* (`base.group_partner_manager`), nhóm CS kế thừa
  `sales_team.group_sale_salesman` (chỉ đọc) ⇒ "Tạo và chỉnh sửa" bị chặn, và **không có menu nào** trỏ
  tới `res.partner`. Đã thêm: 1 dòng ACL `access_res_partner_cs` (đọc/ghi/tạo, **không xoá**) + menu
  **Khách hàng (Shop)** (`views/res_partner_views.xml`, `lab_core 17.0.1.5.0`). Bằng chứng: `cs01` tạo
  partner qua JSON-RPC ⇒ `{"result": 1222}` (đã xoá bản ghi test), ảnh `docs/review/odoo-data-khachhang-1440.png`.

### Chặng ① — CS tạo yêu cầu lấy hàng
- **Ai**: `cs01`. **Ở đâu**: web → **Gom hàng lẻ › Yêu cầu lấy hàng** (`/web#menu_id=334&action=489`) hoặc app tab Chuyển… CS.
- **Bấm**: *Tạo* → chọn **Shop** (khách vừa tạo ở chặng ⓪; chưa có thì tạo ngay tại ô này), địa chỉ lấy, người liên hệ + SĐT, khung giờ, số kiện dự kiến, kg.
- **Trạng thái**: (mới) → `Mới`. **Chưa sinh chứng từ** (đơn bán để bước ⑥ tuỳ chọn).
- **Dữ liệu có sẵn để test**: `LH/26/4346` (Sách Cũ Sài Gòn, hôm nay 10:00, 7 kiện) · `LH/26/4349` · `LH/26/4369`.
- **Kiểm**: `select name,state from lab_yeu_cau_lay_hang where state='new' limit 5;`

### Chặng ② — Gán tài xế
- **Ai**: `nhomtruong01` (app **Phân công tài xế**) hoặc `cs01` (app **Điều phối** → mở việc → chọn tài xế → **Gán tài xế**).
- **API**: `POST /lab/api/v1/cs/jobs/<id>/assign` `{"driver_id": <hr.employee.id>}`.
- **Trạng thái**: `Mới` → **`Đã gán tài xế`** (`new → assigned`). Gui chatter "Gán … cho yêu cầu".
- **Kiểm**: `select name,state,driver_user_id from lab_yeu_cau_lay_hang where id=<id>;`
- **Bẫy**: danh sách tài xế chỉ gồm người có nhóm `driver` (5 người: `taixe01…05`), hiện `Tên · tài khoản`.

### Chặng ③ — Tài xế bắt đầu đi
- **Ai**: `taixe01`. **App** → tab **Tài xế · Việc hôm nay** → mở việc → **Bắt đầu đi**.
- **API**: `POST /driver/jobs/<id>/accept`.
- **Trạng thái**: `assigned → en_route` ("Đang đến lấy").
- **Kiểm**: `select state from lab_yeu_cau_lay_hang where id=<id>;` = `en_route`.
- **Bẫy số 1**: app **chỉ trả việc có khung giờ HÔM NAY** (hoặc không đặt giờ). Việc `assigned` của hôm qua sẽ không hiện.

### Chặng ④ — Tài xế lấy hàng: ảnh + chữ ký + số kiện
- **Ai**: `taixe01`. **App** → mở việc → **Chụp ảnh & ký biên nhận** (3 bước: Ảnh → Ký → Số kiện/kg) → **Gửi & xác nhận**.
- **API**: `POST /driver/jobs/<id>/capture` (kèm `clientUuid` chống trùng).
- **Trạng thái**: `en_route → picked_up` + ghi `so_kien_thuc_te`, `khoi_luong_uoc_kg`, `chu_ky` (PNG), 1..n `ir.attachment` ảnh, `ngay_giao`.
- **Kiểm**:
  ```
  select state, so_kien_thuc_te, khoi_luong_uoc_kg, (chu_ky is not null) as co_chu_ky, ngay_giao
  from lab_yeu_cau_lay_hang where id=<id>;
  select count(*) from ir_attachment where res_model='lab.yeu.cau.lay.hang' and res_id=<id>;
  ```
- **Kiểm nhanh không cần bấm**: `powershell -File infra\scripts\e2e-capture.ps1` (login → capture → gửi lại cùng uuid → SQL).
- **Bẫy**: nút capture chỉ bật khi việc ở `assigned`/`en_route`.

### Chặng ⑤ — Kho nhận hàng (REA: sinh phiếu nhập)
- **Ai**: `kho01`. **App** → tab **Kho · Nhận hàng** → chọn việc → **Hàng đã về kho**. **API**: `POST /kho/jobs/<id>/nhan`.
- **Trạng thái**: `picked_up → confirmed` + **tự sinh `stock.picking` WH/IN** và gắn vào `stock_picking_id`; ghi `ngay_xac_nhan` (mốc cho KPI "hôm nay nhận được mấy việc").
- **Bằng chứng có sẵn**: `LH/26/4239` → `WH/IN/00446` · `LH/26/4240` → `WH/IN/00447`.
- **Kiểm**:
  ```
  select j.name, j.state, sp.name as phieu_nhap, sp.state from lab_yeu_cau_lay_hang j
  join stock_picking sp on sp.id = j.stock_picking_id where j.id=<id>;
  ```
- **Bẫy**: gọi lại lần 2 ⇒ 409 `INVALID_STATE` ("đã có phiếu nhập — không tạo trùng").

### Chặng ⑥ — CS gom việc vào CHUYẾN
- **Ai**: `cs01`/`manager01`. **Web** → **Chuyến** (`/web#menu_id=332&action=487`) → mở chuyến → tab **Đơn hàng trong chuyến** → *Thêm dòng*: chọn Đơn bán (bắt buộc), khách, số kiện, kg, CBM. Nếu chuyến còn `Nháp` → bấm **Mở chuyến**.
- **Trạng thái**: chuyến `draft → open`; dòng hàng `booked`; `tải trọng đã dùng` + `tỉ lệ tải %` tự tính (stored).
- **Dữ liệu test**: chuyến `CH/AIR/26/0257` (id 364, đang `Nháp`, 21% tải) · `CH/AIR/26/0262` (id 371) · mở sẵn: `CH/AIR/26/0258` (id 365, 65%), `CH/AIR/26/0260` (368).
- **Kiểm**:
  ```
  select c.name, c.state, c.ti_le_tai, count(l.id) as so_dong
  from lab_chuyen c left join lab_chuyen_line l on l.chuyen_id=c.id group by c.id, c.name, c.state, c.ti_le_tai;
  ```

### Chặng ⑦ — Chốt booking
- **Ai**: `manager01`. **Web** → chuyến → nút **Chốt booking** (`action_confirm_booking`).
- **Trạng thái**: `open → booking_confirmed`. Điền `số booking`, `hãng`, `cut-off`, `ngày đi`.
- **Duyệt ⑥ (2 cấp)**: trên form chuyến bấm **Request Validation** → cấp 1 `sale_admin` (nhóm `Sale Admin`) → cấp 2 `manager` **chỉ với chuyến bay** (`loai_van_chuyen = air`). **Đo thật**: nút chuyến **không** bị chặn khi chưa duyệt (phiếu duyệt là bước độc lập, `review_ids = []`) — ghi rõ để không hiểu sai.
- **Dữ liệu test**: `CH/AIR/26/0263` (id 373, 74% tải) · `CH/SEA/26/0107` (363) · **quá tải**: `CH/AIR/26/0267` (379, **164%** — ngoại lệ ②).
- **Kiểm**: `select name,state,so_booking from lab_chuyen where id=373;`

### Chặng ⑧ — Xuất kho (WH/OUT)
- **Ai**: `kho01`. **Web** → **Kho › Delivery Orders** (menu chuẩn Odoo) → tạo phiếu xuất từ kho bay/tàu, **ghi `Chuyến` = chuyến vừa chốt** → Validate.
- **Vì sao làm tay**: tab **Phiếu xuất kho** trong chuyến là `readonly="1"` (chỉ để xem) — đây là **chỗ chưa nối**, xem §8.
- **Kiểm**: `select name, state, origin from stock_picking where lab_chuyen_id=373;`

### Chặng ⑨ — Khởi hành (hàng lên máy bay)
- **Ai**: `manager01`. **Web** → chuyến → **Khởi hành** (`action_depart`).
- **Trạng thái**: `booking_confirmed → departed`.
- **Hành trình**: **Web → Hành trình** (`/web#menu_id=336&action=491`) → mở chuyến bay → nhập **số AWB** (air) / **số HBL** (sea), sân bay đi/đến, ETD/ETA → đổi **Mốc hành trình** trên statusbar: `booked → departed → in_transit`.
- **Dữ liệu test**: `AWB-DEMO-002` (id 161, `booked`) · `AWB-DEMO-003` (162, `departed`) · `HBL-DEMO-004` (163, `in_transit`).
- **Kiểm**: `select name,stage,so_awb,so_hbl from lab_hanh_trinh where id in (161,162,163);`

### Chặng ⑩ — Đã đến nơi
- **Ai**: `manager01`. **Web** → **Đã đến nơi** (`action_arrive`). Trạng thái `departed → arrived`. Hành trình: `arrived`.
- **Kiểm**: `select state from lab_chuyen where id=373;` và `select stage from lab_hanh_trinh where id=164;`

### Chặng ⑪ — Giao khách + xác nhận (POD)
- **Ai**: `manager01` (hoặc `chungtu01`). **Web** → **Đã giao khách** (`action_deliver`) → `arrived → delivered`.
- **Hành trình**: statusbar `handed_to_agent → delivered`, nhập `pod_date` + đính kèm POD (tab đính kèm).
- **Kiểm**: `select stage, pod_date from lab_hanh_trinh where id=166;` (mẫu `HBL-DEMO-007` đang `delivered`).

### Chặng ⑫ — Khoá chuyến
- **Ai**: `manager01`. **Web** → **Khoá chuyến** (`action_close`) → `delivered → closed` · hành trình `closed`.
- **Kiểm**: `select name,state from lab_chuyen where state='closed';`

### Chặng ⑬ — TIỀN (REA bắt buộc)
1. **Hoá đơn khách**: web → **Bán hàng › Đơn bán** → mở đơn `sale` → **Create Invoice** (native) → **Post**. Có sẵn 110 hoá đơn đã post: `INV/2026/00005` … (`ref = DEMO-LON-INV-001`).
2. **Phiếu ghi nợ (Debit)**: web → **Kế toán › Misc** → tạo bút toán trên **journal `DEBIT`** *(Phiếu ghi nợ (Debit))*, ghi **Chuyến** (`lab_chuyen_id`), 2 dòng: Nợ 131 / Có 511. Có sẵn 30 phiếu `DEBIT/2026/…` gắn chuyến.
3. **DUYỆT KHOÁ DEBIT (⑭)**: post sẽ **BỊ CHẶN** khi chưa duyệt — đo thật:
   `ValidationError: A validation process is still open for at least one record.`
   → `manager01` mở phiếu → **Validate** (duyệt 1 cấp, chỉ journal `DEBIT`) → post lại.
- **Kiểm**: `select name,state,ref from account_move where journal_id=(select id from account_journal where code='DEBIT') limit 5;`
- **Bẫy**: 30 phiếu Debit trong data đang `draft` **đúng vì luật này** — đó là màn demo "chờ duyệt", không phải lỗi.

### Chặng ⑭ — Đối soát
- **Ai**: `sep`. **Web** → **Bảng điều khiển** (`/web#menu_id=338&action=493`). **App**: `sep` → tab **Tổng quan**.
- **Số hiện tại** (range 30 ngày): `pendingAssign 12 · enRoute 36 · pickedUpToday 12 · confirmedToday 12 · avgLoadPct 70–77%`, biểu đồ 7 ngày không ngày trống, donut 256 việc.

---

## 3. Kịch bản test 40 phút (dùng ID thật)

| # | Việc | Tài khoản · nơi | Thao tác | Kết quả phải thấy |
|---|---|---|---|---|
| 1 | Chọn việc | `cs01` · web | mở `LH/26/4346` (Sách Cũ Sài Gòn) | state **Mới**, 7 kiện, khung 10:00 hôm nay |
| 2 | Gán tài xế | `nhomtruong01` · app | Phân công → việc đó → chọn `Trần Văn Hùng · taixe01` → **Gán tài xế** | chip **Đã gán tài xế**; web F5 thấy cột Tài xế |
| 3 | Bắt đầu đi | `taixe01` · app | Tài xế → mở việc → **Bắt đầu đi** | **Đang đến lấy** |
| 4 | Lấy hàng | `taixe01` · app | **Chụp ảnh & ký** → Ảnh → Ký → 4 kiện/12,5 kg → **Gửi & xác nhận** | **Đã lấy được hàng**; DB có `chu_ky` + 1 ảnh |
| 5 | Về kho | `kho01` · app | Kho → Nhận hàng → **Hàng đã về kho** | **Đã về kho** + số phiếu **WH/IN/…** |
| 6 | Vào chuyến | `manager01` · web | Chuyến → `CH/AIR/26/0257` → tab Đơn hàng trong chuyến → thêm dòng (đơn bán + khách + kg) → **Mở chuyến** | state **Đang mở**, `tỉ lệ tải` tăng |
| 7 | Chốt booking | `manager01` · web | **Chốt booking** + nhập số booking | **Đã chốt booking** |
| 8 | AWB | `manager01` · web | Hành trình → `AWB-DEMO-002` → nhập số AWB → stage **đã book** | stage `booked`, có `so_awb` |
| 9 | Khởi hành | `manager01` · web | chuyến → **Khởi hành**; hành trình → `departed` → `in_transit` | **Đã khởi hành** |
| 10 | Đến nơi | `manager01` · web | **Đã đến nơi**; hành trình → `arrived` | **Đã đến nơi** |
| 11 | Giao khách | `manager01` · web | **Đã giao khách**; hành trình `handed_to_agent` → `delivered` + POD | **Đã giao khách** |
| 12 | Khoá | `manager01` · web | **Khoá chuyến**; hành trình `closed` | **Đã khoá** |
| 13 | Hoá đơn | `ketoan01` · web | Bán hàng → đơn `sale` → **Create Invoice** → **Post** | `INV/…` state **Đã vào sổ** |
| 14 | Debit + duyệt | `ketoan01` → `manager01` · web | Kế toán → bút toán journal **DEBIT** + chọn Chuyến → Post (**bị chặn**) → `manager01` **Validate** → Post lại | trước: `draft` + "Reviews pending"; sau: **Đã vào sổ** |
| 15 | Đối soát | `sep` · web/app | Bảng điều khiển / tab Tổng quan | KPI tăng so với trước khi test |

**Đường tắt kiểm tự động (không bấm):**
```powershell
powershell -File infra\scripts\verify-demo-lon.ps1     # đếm toàn bộ + KPI
powershell -File infra\scripts\e2e-capture.ps1         # login → capture → chống trùng → 4 câu SQL
powershell -File infra\scripts\smoke-admin-switch.ps1  # chuyển vai trò (nếu test nhiều tài khoản)
```

---

## 4. Bảng kiểm từng chặng (tick khi test xong)

| ✓ | Chặng | Câu SQL kiểm nhanh |
|---|---|---|
| ☐ | ② gán | `select state,driver_user_id from lab_yeu_cau_lay_hang where id=:id;` → `assigned` |
| ☐ | ③ bắt đầu | … → `en_route` |
| ☐ | ④ capture | `select state,so_kien_thuc_te,(chu_ky is not null) from lab_yeu_cau_lay_hang where id=:id;` |
| ☐ | ⑤ về kho | `select stock_picking_id, ngay_xac_nhan from lab_yeu_cau_lay_hang where id=:id;` |
| ☐ | ⑥ vào chuyến | `select count(*) from lab_chuyen_line where chuyen_id=:cid;` |
| ☐ | ⑦ chốt | `select state,so_booking from lab_chuyen where id=:cid;` |
| ☐ | ⑧ WH/OUT | `select name,state from stock_picking where lab_chuyen_id=:cid;` |
| ☐ | ⑨ khởi hành | `select state from lab_chuyen where id=:cid;` + `select stage,so_awb from lab_hanh_trinh where id=:hid;` |
| ☐ | ⑩ đến | … → `arrived` |
| ☐ | ⑪ giao | `select stage,pod_date from lab_hanh_trinh where id=:hid;` |
| ☐ | ⑫ khoá | … → `closed` |
| ☐ | ⑬ hoá đơn | `select name,state from account_move where id=:mid;` |
| ☐ | ⑭ debit | post lần 1 **phải lỗi** "Reviews pending"; sau Validate → `posted` |

Chạy SQL: `docker compose -f infra/docker-compose.yml exec -T db psql -U odoo -d odoo17_lab -c "<câu SQL>"`

---

## 5. Sáu nhánh ngoại lệ — đường vào & **điểm quay về**

| # | Nhánh | Vào bằng | Điểm quay về (phải chứng minh) | Dữ liệu có sẵn |
|---|---|---|---|---|
| ① | Hàng chưa rõ khách | 5 việc `Mới` gắn partner *"Khách chưa xác định (chờ CS xử lý)"* | CS sửa `partner_id` thật rồi đi tiếp chặng ② | `LH/26/4266`, `LH/26/4270`, `LH/26/4273` |
| ② | Rớt chuyến / giảm tải | chuyến `tỉ lệ tải > 100` → dòng chuyến `state = removed` + `lý do gỡ` | dòng `removed` vẫn còn (audit), việc được đưa sang chuyến khác rồi đi tiếp ⑨ | `CH/AIR/26/0267` (**164%**) |
| ③ | Đơn thiếu thông tin | 4 việc không `địa chỉ lấy` / 6 việc không `khung giờ` | CS bổ sung địa chỉ + khung giờ → việc hiện lại trong app tài xế (vì app lọc theo hôm nay) | `select name from lab_yeu_cau_lay_hang where dia_chi_lay is null;` |
| ④ | Xuất thiếu kiện | 10 việc `số kiện thực tế < dự kiến` + ghi chú tài xế | phiếu nhập WH/IN vẫn sinh; lệch kiện được ghi ở `ghi_chu_tai_xe` | `select name,so_kien_du_kien,so_kien_thuc_te from lab_yeu_cau_lay_hang where so_kien_thuc_te < so_kien_du_kien;` |
| ⑤ | Chuyển kho bay ↔ tàu | 4 phiếu **chuyển kho nội bộ** (`origin LIKE 'DEMO-LON-INTERNAL%'`) | hàng sang kho kia rồi vào chuyến tương ứng | `select name,state from stock_picking where origin like 'DEMO-LON-INTERNAL%';` |
| ⑥ | Khiếu nại / trả hàng | hành trình `exception` → `returned` → `compensated` | đóng bằng `compensated`/`closed`, **không** để treo ở `exception` | `AWB-DEMO-009` (exception) · `HBL-DEMO-010` (returned) · `AWB-DEMO-011` (compensated) |

---

## 6. Tám bẫy phải biết trước khi test

1. **App tài xế chỉ trả việc HÔM NAY** (`/driver/jobs` lọc `khung_gio_tu ≥ đầu ngày`). Việc cũ sẽ không hiện dù đúng người.
2. **Form web của Yêu cầu lấy hàng KHÔNG có nút chuyển trạng thái** — bước ②③④⑤ **chỉ làm được trên app** (web chỉ xem).
3. **Capture chỉ bật khi `assigned`/`en_route`** — việc đã "Đã lấy được hàng" thì không có nút chụp.
4. **Tab "Phiếu xuất kho" và "Debit (cước)" trong chuyến là readonly** — phải tạo từ menu kho/kế toán rồi gắn chuyến.
5. **Hành trình không có nút chuyển mốc** — bấm vào **statusbar** trên form (`stage` là field, không phải `state`).
6. **Duyệt chuyến không chặn nút** — phiếu duyệt 2 cấp là bước độc lập (đã đo). Nhưng **duyệt Debit thì CHẶN post**.
7. **Rate limit**: login 30 lượt/phút/IP; `/driver/jobs` 120/phút — test tự động nhiều lần sẽ gặp 429.
8. **Máy ảo chặn `10.0.2.2`** với tiến trình app ⇒ phải `adb reverse tcp:8069 tcp:8069`; app dùng `http://127.0.0.1:8069`.

---

## 7. Cái gì CHƯA nối (gap thật — để anh quyết có làm hay không)

| Gap | Ảnh hưởng | Gợi ý |
|---|---|---|
| Yêu cầu lấy hàng **không có nút trên web** | muốn đổi trạng thái phải dùng app | thêm 5 nút gọi `action_*` lên form (FE, ~1 giờ) |
| **Hành trình không có `action_*`** | chỉ đổi mốc bằng statusbar, không có luật "không lùi mốc" | thêm 4 nút + chặn lùi (BE) |
| **WH/OUT và Debit phải tạo tay** trong menu khác | dễ quên gắn chuyến | thêm nút "Tạo phiếu xuất"/"Tạo Debit" ngay trong chuyến (BE) |
| **Duyệt chuyến không chặn** | bấm Chốt booking khi chưa duyệt vẫn được | thêm kiểm tra `validated` vào `action_confirm_booking` |
| **Chuyến `cancelled` chưa trả việc về** trạng thái trước | việc nằm trong chuyến huỷ | thêm xử lý khi huỷ chuyến |
| App **không gia hạn phiên** qua lần mở lại app (cookie phiên) | mở lại app phải đăng nhập/refresh | lưu phiên hoặc tự re-login |
