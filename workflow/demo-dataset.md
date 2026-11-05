# BỘ DỮ LIỆU MẪU LỚN — 6 tháng lịch sử, phủ đủ trạng thái + 6 nhánh ngoại lệ

> Sinh bởi `addons/lab_core/tools/seed_demo_lon.py` · áp vào DB bằng `infra/scripts/seed-demo-lon.ps1`.
> **Mọi con số dưới đây là đo thật trên DB `odoo17_lab` ngày 2026-10-29** bằng
> `infra/scripts/verify-demo-lon.ps1` (JSON-RPC, không phải ước lượng).

---

## 1. Một lệnh — áp vào DB

```powershell
cd "C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics"
powershell -File infra\scripts\seed-demo-lon.ps1            # lần đầu / áp lại (idempotent)
powershell -File infra\scripts\seed-demo-lon.ps1 -Reset     # XOÁ phần đã sinh rồi tạo lại
powershell -File infra\scripts\verify-demo-lon.ps1          # chỉ đếm, không ghi
```

Script tự: `-u` 4 module (`lab_core,lab_laixe,lab_chuyen,lab_hanhtrinh`) → chạy seed trong `odoo shell`
→ restart Odoo → in bảng kiểm. Thời gian thật: **~40 s** cho 300 việc (đã đo).

Đặc điểm kỹ thuật: **tất định** (`random.Random(2026)`), **idempotent** (`ir.config_parameter`
`lab.demo_lon`), **xoá lại được** (mọi bản ghi mang dấu `DEMO-LON*` / `demo-lon-*` / `DL-*`),
**backdate `create_date`** để biểu đồ theo ngày có dữ liệu thật.

---

## 2. Quy mô (số đo thật)

| Model | Tổng | Trong đó do seed |
|---|---|---|
| `lab.yeu.cau.lay.hang` | **305** | 300 |
| `lab.chuyen` | **26** | 24 |
| `lab.chuyen.line` | 119 | ~110 |
| `lab.hanh.trinh` | **13** | 12 |
| `lab.hanh.trinh.line` | 28 | 24 |
| `sale.order` | **149** | 120 |
| `stock.picking` | **80** | 52 (48 nhập/xuất + 4 chuyển kho) |
| `account.move` | **164** | 140 (110 hoá đơn + 30 Debit) |
| `res.partner` | **127** | 54 (40 shop + 4 đại lý + 9 khách nước ngoài + 1 khách chưa rõ) |
| `product.template` | 42 | 8 dịch vụ cước |
| `hr.employee` / `res.users` | 25 / 15 | +2 tài xế `taixe04`, `taixe05` |

### Việc lấy hàng theo trạng thái (đo bằng API)
`new` 12 · `assigned` 26 · `en_route` 10 · `picked_up` 17 · `confirmed` 191 · `failed` 29 · `cancelled` 20 → **305**

### Chuyến theo trạng thái
`draft` 2 · `open` 4 · `booking_confirmed` 6 · `departed` 4 · `arrived` 3 · `delivered` 3 · `closed` 3 · `cancelled` 1 → **26**

### Hành trình — phủ **cả 11 mốc**
`draft` 2 · `booked` · `departed` · `in_transit` 2 · `arrived` · `handed_to_agent` · `delivered` · `closed` · **`exception`** · **`returned`** · **`compensated`**

---

## 3. Sáu nhánh ngoại lệ (docs/07) — có bản ghi THẬT

| # | Nhánh | Dữ liệu trong DB | Kiểm bằng |
|---|---|---|---|
| ① | Hàng chưa rõ khách | **5** việc trạng thái `new` gắn partner *"Khách chưa xác định (chờ CS xử lý)"*, địa chỉ "Chưa xác định — hàng tập kết tại …" | `verify-demo-lon.ps1` §5 |
| ② | Rớt chuyến / giảm tải | **3** chuyến `ti_le_tai > 100%` + **1** dòng chuyến `state = removed` có lý do | §5 `chuyen qua tai >100%` |
| ③ | Đơn thiếu thông tin | **4** việc không có `dia_chi_lay`, **6** việc không có `khung_gio_tu` | SQL `thieu_dia_chi · thieu_khung_gio` |
| ④ | Xuất thiếu kiện | **10** việc có `so_kien_thuc_te < so_kien_du_kien` (kèm ghi chú tài xế) | §5 `job thieu kien` |
| ⑤ | Chuyển kho bay ↔ tàu | **4** `stock.picking` internal giữa 2 vị trí nội bộ | §5 `phieu chuyen kho noi bo` |
| ⑥ | Khiếu nại / trả hàng | 1 hành trình `exception` · 1 `returned` · 1 `compensated` (+8 ticket helpdesk có sẵn) | §4 |

---

## 4. Dashboard Sếp nhận được gì với bộ dữ liệu này (đo thật, range 30 ngày)

```
kpi: { pendingAssign: 12, enRoute: 36, pickedUpToday: 12, confirmedToday: 12, avgLoadPct: 70..77 }
trend: mỗi ngày đều có số (tạo mới / đã lấy / đã về kho) — không có ngày trống
byState: Đã về kho 191 (75%) · Đã lấy được hàng 17 (7%) · Đang đi lấy hàng 36 (14%) · Chờ gán 12 (5%)
ngoài vùng cung ứng: Lấy hàng thất bại 29 · Đã huỷ 20
```
Mốc thời gian: việc có `ngay_giao` **hôm nay 11** · **7 ngày 26** · **30 ngày 58**.

### Mỗi tài khoản tài xế đều có việc hôm nay (đo qua đúng API `/driver/jobs`)
| Tài khoản | `jobsToday` | `/driver/jobs` trả về |
|---|---|---|
| `taixe01` | 10 | 9 việc |
| `taixe02` | 4 | 4 việc |
| `taixe03` | 13 | 13 việc |
| `taixe04` | 14 | 14 việc |
| `taixe05` | 12 | 12 việc |

> Vì sao phải cố ý làm điều này: `/driver/jobs` **chỉ trả việc có khung giờ HÔM NAY** (hoặc không
> đặt giờ). Bản seed đầu rải việc đang làm khắp 180 ngày ⇒ `taixe04` mở app thấy **"0 việc"** dù DB
> có 18 việc đang làm. Đã sửa: mọi việc `assigned/en_route/picked_up` đều có khung giờ hôm nay.

---

## 5. Xem ở đâu (đường dẫn thật, đã chụp ảnh)

| Màn | Đăng nhập | URL |
|---|---|---|
| Yêu cầu lấy hàng (305 dòng, lọc theo trạng thái) | `cs01` / `manager01` | `/web#menu_id=334&action=489` |
| Chuyến (26 dòng) | `manager01` | `/web#menu_id=332&action=487` |
| Hành trình (13 dòng, đủ 11 mốc) | `manager01` | `/web#menu_id=336&action=491` |
| Bảng điều khiển OWL | `sep` | `/web#menu_id=338&action=493` |
| App tài xế "Việc hôm nay" (14 việc) | `taixe04` | app → tab **Tài xế** |
| App CS "Điều phối" | `cs01` | app → tab **Điều phối** |

Ảnh: `docs/review/odoo-data-jobs-1440.png` · `odoo-data-dashboard-1440.png` ·
`odoo-data-chuyen-1440.png` · `odoo-data-hanhtrinh-1440.png` · `app-data-driver.png` · `app-data-cs.png`

---

## 6. Giới hạn đã biết (đừng tưởng là lỗi)

1. **30 phiếu ghi nợ Debit đang ở Nháp**: journal `DEBIT` giờ đã tồn tại nên `base_tier_validation`
   chặn `action_post` ("Reviews pending: Duyệt khoá Debit ⑭"). Đây là **đúng nghiệp vụ** — vào
   menu Chứng từ/Debit sẽ thấy việc chờ duyệt. Muốn post thì phải bấm duyệt trên UI.
2. **Phiếu kho ở trạng thái "Đã xác nhận", không "Done"**: phiếu Done sinh `stock_quant` và Odoo
   chặn xoá về sau (đã trúng khi reset). Seed cố ý dừng ở "Đã xác nhận".
3. **Đơn bán `done` không tạo được trực tiếp** — Odoo chặn `write state='done'`; seed để `sale`.
4. **Hai công ty trong DB**: journal Debit + tài khoản 101300/400000 thuộc công ty khác công ty mặc
   định; hoá đơn dùng journal `INV` của công ty mặc định. Đừng trộn khi demo kế toán.
5. **`taixe04/taixe05` là tài khoản mới** (mật khẩu `lab@2026`) — xuất hiện trong dropdown chuyển
   vai trò của `admin` và trong danh sách tài xế của màn Phân công.
