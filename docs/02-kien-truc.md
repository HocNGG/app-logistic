# 02 — KIẾN TRÚC & MODEL MAP

> Đọc sau `docs/00-nghiep-vu.md` và `docs/01-tai-su-dung-odoo17.md`.
> Đây là bản thiết kế kỹ thuật **có thể code trực tiếp**. Nếu mâu thuẫn với `docs/01`, `docs/01` thắng (native-first thắng).

---

## 1. Sơ đồ module

```
                          l10n_vn   (VN accounting)
                             │
       ┌─────────────────────┼──────────────────────┐
       │                     │                      │
   ┌───┴────┐          ┌─────┴─────┐          ┌─────┴─────┐
   │  sale  │          │   stock   │          │  account  │
   │  _management   _delivery │   _payment │   │  portal   │
   └───┬────┘          └─────┬─────┘          └─────┬─────┘
       │                     │                      │
       └──────────┬──────────┴──────────┬───────────┘
                  │                     │
            ┌─────┴─────┐         ┌─────┴─────┐   fleet · hr
            │ _tier_    │         │   mail    │   helpdesk_mgmt (OCA)
            │ validation│         │  (chatter)│
            │  (OCA)    │         └─────┬─────┘
            └─────┬─────┘               │
                  │                     │
            ┌─────┴─────────────────────┴──────┐
            │            lab_core              │  ← module keo (ADR-001)
            │  groups · mixin · sequences ·    │
            │  field mở rộng · chargeable_wt   │
            └──┬───────────┬────────────┬──────┘
               │           │            │
       ┌───────┴──┐  ┌─────┴────┐  ┌────┴──────┐
       │lab_chuyen│  │lab_laixe │  │lab_hanhtrinh│
       │ ⑤⑥⑦⑬⑭  │  │   ②      │  │  ⑨⑩⑪     │
       └────┬─────┘  └────┬─────┘  └────┬──────┘
            │             │             │
            └─────────────┼─────────────┘
                          │
                    ┌─────┴─────┐
                    │  lab_api  │  ← endpoints cho app KMP (ADR-004)
                    └───────────┘
```

**Quy tắc phụ thuộc (CHỐT — không còn mơ hồ):**

1. `lab_core` **không** depends module `lab_*` nào.
2. Ba module nghiệp vụ depends `lab_core`.
3. **Ngoại lệ duy nhất, đã chốt:** `lab_hanhtrinh` **depends `lab_chuyen`** — vì `lab.hanh.trinh.chuyen_id` là Many2one tới `lab.chuyen`. Đây là phụ thuộc **một chiều**, không phải vòng.
4. `lab_laixe` **không** depends `lab_chuyen` (yêu cầu lấy hàng tồn tại trước khi có chuyến). Liên kết yếu qua `sale_order_id`.
5. `lab_api` depends cả `lab_laixe` + `lab_hanhtrinh`.
6. Mọi giao tiếp chéo còn lại: field m2o khai báo trong module **bị trỏ tới**, hoặc qua `lab_core`.

Ví dụ: `lab.hanh.trinh` cần trỏ tới `lab.chuyen`? → **Sai hướng.** `lab_chuyen` thêm `lab_hanhtrinh_id` trên `lab.chuyen`? Cũng sai. **Đúng:** `lab_hanhtrinh` depends `lab_chuyen` (hành trình thuộc về chuyến) **hoặc** đặt field quan hệ trong `lab_core` dưới dạng `lab.chuyen` reference bằng `_inherits`? → Cách sạch nhất: `lab_hanhtrinh` khai báo `chuyen_id = Many2one('lab.chuyen')`, và **`lab_hanhtrinh` depends `lab_chuyen`**. Vậy thứ tự cài: `lab_core` → `lab_chuyen` → `lab_laixe` → `lab_hanhtrinh` → `lab_api`. Ghi vào manifest.

---

## 2. Model inventory

### 2.1 `lab_core` — không có model nghiệp vụ, chỉ có glue

| Loại | Tên | Mô tả |
|---|---|---|
| Abstract mixin | `lab.flow.mixin` | 6 field chung cho mọi chứng từ có ngoại lệ: `lab_exception_type`, `lab_exception_note`, `lab_return_step`, `lab_is_exception`, `lab_exception_user_id`, `lab_exception_date` |
| Inherit | `res.partner` | `lab_role` (shop/agent/driver/customer), `lab_shop_code`, `lab_credit_terms`, `lab_is_agent`, `lab_country_code` |
| Inherit | `sale.order` | `lab_pickup_mode` (pickup/drop_off), `lab_shop_ref`, `lab_chargeable_weight`, `lab_volumetric_weight`, `lab_cbm`, `lab_exception_*` (mixin). **KHONG co `lab_chuyen_id`/`lab_hanhtrinh_id`** — xem dinh chinh (*) |
| Inherit | `stock.picking` | `lab_pickup_mode`, `lab_weight_kg`, `lab_package_count`, `lab_is_cho_khach` (hàng chờ khách), `lab_exception_*`. **KHONG co `lab_chuyen_id`** — xem dinh chinh (*) |
| Inherit | `stock.move.line` | `lab_weight_kg`, `lab_volume_m3`, `lab_dim_l/w/h`, `lab_chargeable_weight` (compute) |
| Inherit | `product.template` | `lab_is_freight` (bool), `lab_volumetric_divisor` (int, default 6000) |
| Inherit | `res.company` | `lab_debit_journal_id`, `lab_kho_cho_location_id`, `lab_auto_lock_debit` (bool) |
| Transient | `lab.config.settings` | Kế thừa `res.config.settings` để lộ cấu hình |


> ★ **ĐÍNH CHÍNH (đã chốt, bắt buộc):** KHÔNG khai báo `lab_chuyen_id` / `lab_hanhtrinh_id` trong `lab_core`.
> Lý do: m2o trỏ tới `lab.chuyen` / `lab.hanh.trinh` buộc phải `depends` module đó ⇒ **phụ thuộc vòng**, trái quy tắc §1.
> **Đúng — field nằm ở module sở hữu đích:**
> - `lab_chuyen` khai `_inherit = ['sale.order', 'stock.picking', 'account.move']` và thêm `lab_chuyen_id` (m2o) + `picking_ids` / `debit_ids` (o2m inverse).
> - `lab_hanhtrinh` khai `_inherit = ['sale.order']` và thêm `lab_hanhtrinh_id`.
> - `lab_core` chỉ giữ field **không trỏ** sang module nghiệp vụ (mixin ngoại lệ, `lab_pickup_mode`, `lab_weight_kg`, …).
> BE-01 đã làm đúng cách này; §2.1 ở trên đã sửa theo.

### 2.2 `lab_chuyen` — ⑤ ⑥ ⑦ ⑬ ⑭

| Model | Field | Kiểu | Ghi chú |
|---|---|---|---|
| **`lab.chuyen`** | `name` | char, readonly | sequence `lab.chuyen` → `CHUYEN/2026/0001` |
| | `loai_van_chuyen` | selection | `air` (bay) / `sea` (tàu) — dùng cho UoM và report |
| | `carrier_id` | m2o `delivery.carrier` | **tái sử dụng** `stock_delivery` |
| | `hang_van_chuyen` | char | tên hãng (VN Airlines, Maersk…) |
| | `so_booking` | char | số booking hãng cấp |
| | `kho_xuat_id` | m2o `stock.warehouse` | KHO BAY / KHO TÀU |
| | `ngay_di`, `ngay_den_du_kien` | date | |
| | `cut_off_datetime` | datetime | hạn chốt hàng |
| | `tai_trong_max_kg`, `the_tich_max_cbm` | float | sức chứa |
| | `tai_trong_da_dung_kg`, `the_tich_da_dung_cbm` | float, compute store | |
| | `ti_le_tai` | float, compute | % tải, dùng cho cảnh báo quá tải |
| | `state` | selection | xem state machine §3 |
| | `line_ids` | o2m `lab.chuyen.line` | |
| | `tier_validation` | từ mixin OCA | ⑥ ⑭ |
| | `debit_ids` | o2m `account.move` | ⑬ |
| | `picking_ids` | o2m `stock.picking` | ⑦ |
| **`lab.chuyen.line`** | `chuyen_id` | m2o, required, ondelete cascade | |
| | `sale_order_id` | m2o `sale.order`, required | đơn của shop |
| | `stock_picking_id` | m2o `stock.picking` | phiếu xuất tương ứng |
| | `partner_id` | related `sale_order_id.partner_id`, store | để lọc nhanh |
| | `khoi_luong_kg`, `the_tich_cbm` | float | snapshot tại thời điểm gán |
| | `state` | selection | `booked` / `waitlist` / `removed` / `cancelled` |
| | `ly_do_go` | char | bắt buộc khi `removed` (nhánh ②) |
| | `so_kien` | int | |

### 2.3 `lab_laixe` — ②

| Model | Field | Kiểu | Ghi chú |
|---|---|---|---|
| **`lab.yeu.cau.lay.hang`** | `name` | char readonly | sequence → `LAYHANG/2026/0001` |
| | `partner_id` | m2o `res.partner` (shop), required | |
| | `sale_order_id` | m2o `sale.order` | có thể tạo trước khi có SO |
| | `dia_chi_lay` | text | |
| | `lien_he_ten`, `lien_he_sdt` | char | |
| | `khung_gio_tu`, `khung_gio_den` | datetime | cửa sổ lấy hàng |
| | `driver_id` | m2o `hr.employee` + `res.partner` | **tái sử dụng** `hr` |
| | `vehicle_id` | m2o `fleet.vehicle` | **tái sử dụng** `fleet` |
| | `state` | selection | xem §3 |
| | `ngay_giao` | datetime | thời điểm tài xế xác nhận |
| | `chu_ky` | binary (image) | chữ ký vẽ trong app KMP |
| | `anh_ids` | o2m `ir.attachment` | ảnh hàng hoá (app chụp) |
| | `so_kien_thuc_te`, `khoi_luong_uoc_kg` | int, float | tài xế nhập |
| | `stock_picking_id` | m2o `stock.picking` | sinh picking khi về kho |
| | `access_token` | char | cho app tài xế (không cần user backend) |
| | `ly_do_that_bai` | text | nếu tài xế không lấy được |

### 2.4 `lab_hanhtrinh` — ⑨ ⑩ ⑪

| Model | Field | Kiểu | Ghi chú |
|---|---|---|---|
| **`lab.hanh.trinh`** | `name` | char readonly | sequence → `HT/2026/0001` |
| | `chuyen_id` | m2o `lab.chuyen`, required | |
| | `sale_order_id` | m2o `sale.order` | hành trình theo từng lô |
| | `stage` | selection | xem §3 |
| | `loai_chung_tu` | selection | `awb` / `hbl` |
| | `so_awb`, `so_hbl` | char | ⑧ |
| | `so_manifest` | char | |
| | `ngay_bay`, `ngay_den` | datetime | |
| | `cang_di`, `cang_den` | m2o `res.country` / char | sân bay/cảng |
| | `dai_ly_giao_id` | m2o `res.partner` | đại lý nước ngoài |
| | `pod_date` | datetime | proof of delivery |
| | `pod_attachment_ids` | o2m `ir.attachment` | |
| | `access_token` | char | link tra cứu cho khách |
| | `line_ids` | o2m `lab.hanh.trinh.line` | dòng thời gian |
| **`lab.hanh.trinh.line`** | `hanhtrinh_id` | m2o | |
| | `moc` | char | "Đã lên máy bay", "Đã hạ cánh Tân Sơn Nhất", "Đại lý đã nhận" |
| | `thoi_diem` | datetime | |
| | `dia_diem` | char | |
| | `nguoi_cap_nhat_id` | m2o `res.users` | |

### 2.5 `lab_api` — cửa cho app KMP

Xem §6.

---

## 3. State machines (bắt buộc vẽ đúng)

### 3.1 `lab.chuyen`

```
  draft ──mở chuyến──> open ──chốt booking──> booking_confirmed
                          │                        │
                          │                        ├──duyệt đơn vào chuyến (tier ⑥)──┐
                          │                        │                                   │
                          └──huỷ (cancel)──────────┴──> cancelled                     │
                                                   │                                   │
                                             departed <───────────────────────────────┘
                                                   │
                                             arrived (⑩)
                                                   │
                                             delivered (⑪)
                                                   │
                                              closed ──> (khoá sửa)
```
Nhánh ② (rớt chuyến) tác động **`lab.chuyen.line.state`** (`booked → waitlist → removed`), **không** đổi state chuyến (trừ khi hủy cả chuyến).

### 3.2 `lab.yeu.cau.lay.hang`

```
new ──assign──> assigned ──tài xế nhận──> en_route ──đến nơi──> picked_up
  │                │                          │                     │
  │                └──huỷ──> cancelled        └──thất bại──> failed └──về kho──> confirmed
  │                                                                              (sinh stock.picking)
  └──(khách tự mang)──> [không tạo record]  ← nhánh "tự mang tới kho"
```

### 3.3 `lab.hanh.trinh`

```
draft ──> booked ──> departed ──> in_transit ──> arrived ──> handed_to_agent ──> delivered ──> closed
                                                        │
                                                        └──exception (nhánh ⑥ khiếu nại) ──> returned / compensated / closed
```

**Quy tắc Odoo 17:**
- Mọi state machine dùng `fields.Selection` + `_order = 'id desc'`.
- Nút chuyển trạng thái nằm trong `<header>` với `button` + `type="object"`.
- Không được "nhảy cóc" state — mỗi method `action_*` phải `raise UserError` nếu state sai.
- Mọi chuyển state phải ghi `mail.message` (chatter) → tự động nếu gọi `message_post`.

---

## 4. Ma trận phân quyền

### 4.1 Groups

| Group ID | Kế thừa từ | Người dùng |
|---|---|---|
| `lab_core.group_cs` | `sales_team.group_sale_salesman` | CS/Sale |
| `lab_core.group_driver` | `base.group_portal` | Tài xế (portal-level) |
| `lab_core.group_warehouse` | `stock.group_stock_user` | Kho |
| `lab_core.group_docs` | `lab_core.group_warehouse` | Chứng từ |
| `lab_core.group_account` | `account.group_account_invoice` | Kế toán |
| `lab_core.group_sale_admin` | `lab_core.group_cs` | Sale admin |
| `lab_core.group_manager` | `lab_core.group_sale_admin` + `account.group_account_manager` | Trưởng phòng — duyệt tier |
| `lab_core.group_director` | `base.group_user` + read-only | BGĐ |

### 4.2 ACL (rút gọn — file đầy đủ do BE viết)

| Model | cs | driver | warehouse | docs | account | sale_admin | manager |
|---|---|---|---|---|---|---|---|
| `lab.chuyen` | R | – | RW | RW | R | RW | RW |
| `lab.chuyen.line` | R | – | RW | R | R | RW | RW |
| `lab.yeu.cau.lay.hang` | RW | RW* | R | R | – | RW | RW |
| `lab.hanh.trinh` | R | – | R | RW | R | R | RW |
| `account.move` | R | – | – | – | RW | R | RW |
| `sale.order` | RW | – | R | R | R | RW | RW |

`*` tài xế chỉ RW record của mình → **`ir.rule`**:

```xml
<record id="rule_lay_hang_driver_own" model="ir.rule">
  <field name="name">Tài xế chỉ thấy yêu cầu của mình</field>
  <field name="model_id" ref="model_lab_yeu_cau_lay_hang"/>
  <field name="domain_force">[('driver_id.user_id', '=', user.id)]</field>
  <field name="groups" eval="[(4, ref('lab_core.group_driver'))]"/>
</record>
```

**Điều cấm:** `if not self.env.user.has_group(...)` trong business method. Quyền record = `ir.rule`; quyền field = `groups="..."` trong view; quyền menu = `groups` trên `menuitem`.

---

## 5. Sequences & naming

| Sequence | Code | Định dạng |
|---|---|---|
| Chuyến | `lab.chuyen` | `CH/{loai}/{yy}/{seq:04d}` → `CH/AIR/26/0001` |
| Yêu cầu lấy hàng | `lab.yeu.cau.lay.hang` | `LH/26/0001` |
| Hành trình | `lab.hanh.trinh` | `HT/26/0001` |
| Debit | `account.move` (native) | theo journal |
| Picking | native | `WH/IN/00001` |

Sequence khai báo trong `data/ir_sequence_data.xml` với `noupdate="1"` (không ghi đè khi upgrade).

---

## 6. Hợp đồng API cho app KMP (`lab_api`)

### 6.1 Nguyên tắc

- **Ưu tiên JSON-RPC native của Odoo** (`/jsonrpc` → `/web/dataset/call_kw`) — không viết lại.
- Chỉ viết controller cho **4 thứ native không làm tốt**: đăng nhập tài xế bằng token, upload ảnh/chữ ký từ app, timeline tra cứu công khai, endpoint "today's jobs" tối ưu cho mobile.
- Mọi endpoint custom: prefix `/lab/api/v1/`, `type='json'` hoặc `http`, có `auth='public'` + token, **http method POST**, trả `{ok, data, error}`.

### 6.2 Bảng endpoint

| Method | Route | Auth | Mục đích | Trả về |
|---|---|---|---|---|
| POST | `/lab/api/v1/auth/login` | public | Đăng nhập tài xế (username/password hoặc token) → trả `session_token` | `{token, driver_id, name}` |
| GET | `/lab/api/v1/driver/jobs` | token | Danh sách việc hôm nay của tài xế | `[{id, ref, shop, address, window, so_kien, state}]` |
| POST | `/lab/api/v1/driver/jobs/<id>/accept` | token | Tài xế nhận việc | `{state}` |
| POST | `/lab/api/v1/driver/jobs/<id>/capture` | token | Gửi ảnh + chữ ký + số kiện + kg | `{state, attachment_ids}` |
| POST | `/lab/api/v1/driver/jobs/<id>/fail` | token | Báo không lấy được + lý do | `{state}` |
| GET | `/lab/api/v1/track/<access_token>` | public | Timeline tra cứu cho khách (không lộ dữ liệu nội bộ) | `{stage, events[], eta}` |
| GET | `/lab/api/v1/cs/board` | user session | Bảng CS: yêu cầu mới + chuyến sắp chốt | `{requests[], chuyens[]}` |
| POST | `/lab/api/v1/cs/request` | user session | Tạo yêu cầu lấy hàng từ app CS | `{id, ref}` |
| GET | `/lab/api/v1/dashboard/kpi` | user session | KPI cho màn hình duo | `{revenue, kg, chuyen_count, exception_count}` |

### 6.3 Auth flow cho app (đã kiểm chứng 17.0)

```
App ──POST /web/session/authenticate {db, login, password=<API KEY>}
   ←── Set-Cookie: session_id=<sid>     ← sid CHỈ nằm trong cookie, KHÔNG có trong body
   ←── result: {uid, username, user_context, ...}   (uid=false → sai thông tin)

App ──POST /web/dataset/call_kw   (Cookie: session_id=<sid>)
```

**Ba sự thật phải nhớ về Odoo 17:**
1. **API key thay thế password** (`res.users.apikeys`): dùng key ở chỗ password khi authenticate/`execute_kw`. **Không** lưu password người dùng trong app.
2. **Odoo 17 KHÔNG hỗ trợ `Authorization: Bearer`.** Bearer key là tính năng Odoo 19 (`/json/2`). Nếu app cần Bearer cho endpoint custom của `lab_api` → **ta tự cài đặt** trong controller của mình (không phải Odoo native).
3. `session_id` **chỉ trả về qua cookie** → Ktor client phải giữ cookie jar (`HttpCookies`/`cookiesStorage`), không đọc từ JSON.

**Vì sao vẫn cần `lab_api` custom:** 4 việc native làm không tốt — (a) tài xế không nên có phiên web, (b) upload ảnh/chữ ký binary, (c) route tra cứu công khai tối giản, (d) payload "today's jobs" gọn cho mobile.

**Bảo mật tối thiểu:**
- Token custom: hết hạn 8h, lưu **hash** trong DB, không log.
- Rate limit theo IP + token (đếm trong cache).
- Upload ảnh: ≤ 5MB, validate **magic bytes**, không tin `mimetype` client gửi.
- Không trả `access_token` của người khác; endpoint track công khai chỉ trả **field whitelist**.

### 6.4 JSON-RPC cho app CS (không cần controller)

```
POST /jsonrpc
{"jsonrpc":"2.0","method":"call","params":{
  "service":"object","method":"execute_kw",
  "args":["odoo17_lab", 2, "<api_key>",
    "sale.order","search_read",
    [[["state","=","sale"]]],
    {"fields":["name","partner_id","lab_pickup_mode","amount_total"],"limit":50}
  ]}}
```

**Bắt buộc:** app không bao giờ gọi `execute_kw` với `sudo`; quyền lấy từ user → app phản chiếu đúng quyền backend.

### 6.5 Portal & track công khai (sửa lại theo sự thật 17.0)

⚠️ **Không có route tổng quát `/my/<model>/<id>` trong Odoo 17.** Cơ chế thật:
- `portal.mixin` thêm `access_token` (Char, `copy=False`, **không index**), điền lười bởi `_portal_ensure_token()` (uuid4 qua sudo).
- Kiểm quyền **không phải `ir.rule`**: controller gọi `_document_check_access(model, id, access_token)` → sudo-browse → `check_access_rule('read')` → nếu AccessError thì `consteq(record.access_token, access_token)`.
- Đường dẫn tổng quát: `/mail/view?model=…&res_id=…&access_token=…`; route `/my/...` là **theo từng model** (`/my/orders/<int:order_id>` với `auth="public"`).
- **`stock.picking` KHÔNG phải `portal.mixin`** và stock không có controller tracking.

**Vậy BE-01 phải làm (3 việc, không hơn):**
1. `_inherit = ['stock.picking', 'portal.mixin']` + override `_compute_access_url()`.
2. Một `@http.route('/lab/track/<string:token>', auth='public', website=True)` gọi `_document_check_access` (hoặc controller `lab_api` §6.2 `/lab/api/v1/track/<access_token>`).
3. QWeb template whitelist field.

Đánh giá thêm: OCA `stock_picking_portal` (17.0) — **phải đọc code** trước khi tự viết; nếu nó đã có token page thì dùng (ghi vào ADR-003).

---

## 7. Tự động hoá (native, không code)

| Tên | Trigger | Hành động | Công cụ |
|---|---|---|---|
| Nhắc chốt chuyến | `lab.chuyen` `cut_off_datetime` - 4h | Tạo `mail.activity` cho người phụ trách | `base_automation` |
| Cảnh báo quá tải | `lab.chuyen.ti_le_tai > 100` | Post chatter + activity cho manager | `base_automation` |
| Nhắc bổ sung thông tin | `lab.is_exception = True` type `missing_info` | Activity cho CS | `base_automation` |
| Nhắc thu tiền | `account.move` quá hạn 7 ngày | Activity cho kế toán | `base_automation` |
| Gán tài xế tự động | `lab.yeu.cau.lay.hang` mới, cùng quận | Gợi ý driver (chỉ gợi ý, không tự gán) | server action |
| Khoá tháng | Ngày 5 hàng tháng | Nhắc đặt `period_lock_date` | `ir.cron` (native) |

---

## 8. Reports (QWeb, native engine)

| Report | Model | Kích thước | Ghi chú |
|---|---|---|---|
| **AWB** | `lab.hanh.trinh` | A4 | Số AWB, người gửi/nhận, kg, số kiện, chuyến, sân bay đi/đến |
| **HBL** | `lab.hanh.trinh` | A4 | Như AWB + container, seal |
| **Manifest** | `lab.chuyen` | A4 landscape | Bảng kê theo `lab.chuyen.line`, tổng kg/CBM |
| **Phiếu giao hàng (POD)** | `lab.hanh.trinh` | A4 | Đại lý ký nhận |
| **Yêu cầu lấy hàng** | `lab.yeu.cau.lay.hang` | A5 | Tài xế in mang theo |
| **Debit note** | `account.move` | A4 | Dùng report `account.report_invoice` native + query param |

**Quy tắc:** không tự sinh PDF bằng Python/ReportLab. Dùng `ir.actions.report` + QWeb. Tái sử dụng `web.external_layout` của Odoo.

---

## 9. Hiệu năng & kỹ thuật

| Vấn đề | Cách xử lý Odoo 17 |
|---|---|
| `compute` gọi mỗi dòng list | `store=True` + `@api.depends` đúng; hoặc `precompute=True` nếu cần |
| Danh sách `lab.chuyen` nghìn dòng | `_order` có index, `search` có domain giới hạn, dùng `group_by` |
| `tai_trong_da_dung_kg` compute | `store=True`, depends `line_ids.khoi_luong_kg, line_ids.state` |
| Attachment ảnh lớn | `ir.attachment` + resized thumbnail; không lưu base64 trong field |
| N+1 khi map sang API | `search_read` 1 lần, không `for record in records: record.name` |
| Nhiều công ty | Đã chốt 1 công ty → không cần `company_id` thủ công, dùng `ir.rule` của Odoo |

---

## 10. Kiểm thử tối thiểu (BE phải viết)

```
tests/
├── test_chuyen_state.py         # mọi transition hợp lệ + từ chối transition sai
├── test_chuyen_capacity.py      # quá tải phát hiện đúng, ti_le_tai đúng
├── test_giam_tai.py             # nhánh ②: gỡ đơn → waitlist → gán lại chuyến khác
├── test_laixe_flow.py           # nhánh ②: new→assigned→picked_up→confirmed→sinh picking
├── test_hanhtrinh_flow.py       # ⑨⑩⑪ + timeline line
├── test_chargeable_weight.py    # max(actual, volumetric) với 6000/1000
├── test_security.py             # tài xế A không thấy job của tài xế B (ir.rule)
└── test_debit_lock.py           # ⑭: đã khoá thì không sửa được; mở khoá có lý do
```

Chạy: `odoo-bin -d odoo17_lab -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh --test-tags=/lab_chuyen,/lab_laixe,/lab_hanhtrinh --stop-after-init`

---

## 11. Bản đồ REA (dùng để kiểm chứng không bỏ sót bút toán)

| Sự kiện kinh tế (Event) | Tài nguyên (Resource) | Tác nhân (Agent) | Chứng từ Odoo |
|---|---|---|---|
| Nhận hàng để gửi | Hàng của shop (inventory) | Kho, shop | `stock.picking` (IN) |
| Gom vào chuyến | Sức chứa chuyến | Kho/chứng từ, hãng | `lab.chuyen.line` |
| Xuất kho | Hàng đã gửi | Kho | `stock.picking` (OUT) |
| Cước phát sinh | Dịch vụ vận chuyển | Kế toán, shop | `sale.order.line` |
| Ghi nợ khách | Khoản phải thu | Kế toán | `account.move` (Debit) |
| Thu tiền | Tiền mặt/ngân hàng | Kế toán, ngân hàng | `account.payment` |
| Bồi thường/mất hàng | Tổn thất | Kế toán | `account.move` (credit note) |

**Quy tắc REA:** bất kỳ *Event* nào ở bảng trên xảy ra mà không sinh chứng từ tương ứng → lỗi thiết kế. Review agent kiểm tra bảng này mỗi sprint.

---

*Tiếp theo: `docs/03-ke-hoach.md` — kế hoạch xây dựng.*
