# 01 — TÁI SỬ DỤNG ODOO 17 (Native-First Ledger)

> **Đây là file quan trọng nhất của dự án.** Mọi quyết định "có viết code hay không" đều phải trích dẫn file này.
> Nguyên tắc: **Native → Cấu hình → Kế thừa (inherit) → Module mới → Fork.** Đi từ trên xuống, dừng ở bước đầu tiên giải quyết được.

---

## 1. Thang quyết định 5 bậc (Decision Ladder)

| Bậc | Tên | Làm gì | Ai duyệt | Dấu hiệu đúng |
|---|---|---|---|---|
| **L0** | **Dùng native** | Cài module có sẵn, dùng model/field/view/report sẵn có | Không cần | 0 dòng code |
| **L1** | **Cấu hình** | `ir.config_parameter`, `res.config.settings`, data XML, server action, automated action, tier validation config, pricelist, route, warehouse | Review | Chỉ XML/CSV/YAML config |
| **L2** | **Kế thừa nhẹ** | `_inherit` để thêm field/method; XPath để thêm nút/view | Review | Không có model mới, không có luồng mới |
| **L3** | **Module mới** | Model mới, view mới, security riêng, report riêng | Review + ADR | Chỉ khi L0–L2 thất bại **và** có ADR |
| **L4** | **Fork** | Copy module core/OCA rồi sửa | **Cấm** | — |

**Ba câu hỏi bắt buộc trước khi viết dòng code đầu tiên:**

1. *Odoo 17 đã có model nào mang đúng ngữ nghĩa này chưa?* (tra `addons/*/models/*.py` **trước**, không tra Google trước)
2. *Nếu chưa có model, có thể biểu diễn bằng field + view + config trên model có sẵn không?*
3. *Nếu phải tạo model, nó thuộc module nào trong 3 module đã được cấp phép?* (Ngoài 3 module → cần ADR.)

**Quy tắc 90 giây:** nếu không trả lời được 3 câu trên trong 90 giây, dừng lại và ghi câu hỏi vào `docs/09-open-questions.md`. Không đoán.

---

## 2. Ledger — 19 bước, từng bước một

Ký hiệu: 🟩 L0 native · 🟨 L1 config · 🟦 L2 inherit · 🟥 L3 module mới · ❌ không làm

| # | Bước | Nguồn native (L0) | Cấu hình (L1) | Kế thừa (L2) | Module mới (L3) | Quyết định |
|---|---|---|---|---|---|---|
| ① | Nhận yêu cầu từ shop | `sale.order`, `sale.order.line` | Pricelist theo kg; `sale_management` bật báo giá; portal cho shop tự đặt | field `lab_pickup_mode` (`pickup`/`drop_off`) + `lab_shop_ref` | — | 🟩 + 🟦 nhẹ |
| ② | Tạo yêu cầu lấy hàng | — | — | — | `lab.yeu.cau.lay.hang` trong `lab_laixe` | 🟥 |
| — | Tài xế đi lấy (ảnh·ký·xác nhận) | `ir.attachment` (ảnh), `mail.thread` | — | — | `lab_laixe` + app KMP | 🟥 |
| ③ | Nhận hàng tại kho | `stock.picking` (Receipt), `stock.move`, `stock.move.line` | Picking type, location `KHO CHỜ` | field `lab_shipper_ref` | — | 🟩 + 🟦 nhẹ |
| — | Cân · đo · dán mã kiện | `stock.move.line` (qty + `lot`/`package`), `stock.quant.package`, `product.template.weight` | Bật `Packages` trong settings | field `lab_weight_kg`, `lab_volume_m3`, `lab_dims` trên `stock.move.line`; compute `lab_chargeable_weight` | — | 🟩 + 🟦 |
| ④ | Đóng gói | `stock.quant.package`, `stock.package.type` | Tạo package type (Thùng, Kiện, Pallet) | field `lab_package_label` | — | 🟩 |
| ⑤ | Mở chuyến · booking | `delivery.carrier` chỉ là *phương thức* | — | — | `lab.chuyen` + `lab.chuyen.line` | 🟥 |
| ⑥ | Duyệt đơn vào chuyến | `base_tier_validation` (OCA) | Cấu hình 2 cấp duyệt trên `lab.chuyen` | override `_check_state_conditions` / `_validated_states` | — | 🟨 + 🟦 |
| ⑦ | Xuất kho | `stock.picking` (Delivery), `stock.move` | Route, chứng từ xuất | field `lab_chuyen_id` (m2o `lab.chuyen`) | — | 🟩 + 🟦 |
| ⑧ | AWB · HBL · manifest | `mail.thread` (lưu số), QWeb report engine (`ir.actions.report`) | — | report QWeb mới trong `lab_chuyen`/`lab_hanhtrinh` | template PDF | 🟩 (report là config-level của Odoo) |
| ⑨ | Hàng đi | — | — | — | `lab.hanh.trinh` | 🟥 |
| ⑩ | Đến nơi | — | — | — | `lab.hanh.trinh` (`stage`) | 🟥 |
| ⑪ | Đại lý giao khách | `res.partner` (đại lý) | — | — | `lab.hanh.trinh` (`pod_date`) | 🟥 |
| ⑫ | Tính cước theo kg | `product` (UoM = kg), `product.pricelist` / `product.pricelist.item`, `sale.order.line` | Bảng giá bậc thang theo kg, theo tuyến | `_get_delivery_price`-style compute | — | 🟩 + 🟨 |
| ⑬ | Lập Debit | `account.move`, `account.move.line` | Journal *Debit*, tài khoản doanh thu | field `lab_chuyen_id`, `lab_khoi_luong` trên `account.move` | — | 🟩 + 🟦 nhẹ |
| ⑭ | Kiểm & khoá Debit | `account.move` posted-lock, `base_tier_validation` | Tier validation trên `account.move`; `restrict_mode_hash_table` nếu cần | `_post` override để chặn sửa | — | 🟨 + 🟦 |
| ⑮ | Hoá đơn · thu tiền | `account.payment`, `account.payment.register` | Journal ngân hàng, đối soát | — | — | 🟩 |
| ⑯ | Đối soát · khoá tháng | `res.company.period_lock_date` / `fiscalyear_lock_date` | Đặt lock date theo tháng | — | — | 🟩 |
| — | Báo cáo · dashboard | `graph`/`pivot` view, `spreadsheet`/`spreadsheet_dashboard` | Dashboard cho sếp | — | — | 🟨 |
| N1 | Hàng chưa rõ khách | `stock.location` (KCHO), `stock.picking` | Location + picking type | field `lab_cho_khach` (bool) | — | 🟩 |
| N2 | Rớt chuyến / giảm tải | `base_tier_validation` | Tier config | wizard `lab.giam.tai.wizard` (transient model — OK) | — | 🟨 + 🟦 |
| N3 | Đơn thiếu thông tin | `mail.activity`, `mail.activity.type` | Activity type "Bổ sung thông tin" | — | — | 🟩 |
| N4 | Xuất thiếu kiện | `stock.picking` backorder, `stock.move` | — | — | — | 🟩 |
| N5 | Chuyển kho bay ↔ tàu | `stock.warehouse`, internal transfer `stock.picking` | 2 kho + route | — | — | 🟩 |
| N6 | Khiếu nại · trả hàng | `helpdesk_mgmt` (OCA), return picking | Ticket team + stage | — | — | 🟩 (OCA) |

**Tổng:** 3 module mới, 2 wizard transient, ~10 field kế thừa, 1 custom compute. **Đó là toàn bộ code của dự án.** Nếu nhiều hơn đáng kể → đang viết lại Odoo.

---

## 3. Danh sách module — cái gì cài, cái gì không

### 3.1 Community (CE) — cài từ Odoo 17 gốc

| Module | Vì sao cần | Bước | Ghi chú |
|---|---|---|---|
| `sale_management` | Báo giá, đơn hàng, pricelist | ① ⑫ | Bắt buộc |
| `sale_stock` | Nối `sale.order` ↔ `stock.picking` | ① ③ ⑦ | Bắt buộc — **không tự viết cầu nối** |
| `stock` | Picking, move, move line, package, location, warehouse | ③ ④ ⑦ | Bắt buộc |
| `stock_delivery` | `delivery.carrier` (hãng bay/tàu), `stock.picking.weight` | ⑤ ⑫ | Bắt buộc — **không tự viết model hãng vận chuyển** |
| `purchase` (tuỳ chọn) | Mua cước từ hãng/đại lý → giá vốn chuyến | ⑤ ⑬ | Nên có để tính lãi chuyến |
| `account` | `account.move`, Debit, hoá đơn, payment | ⑬ ⑭ ⑮ ⑯ | Bắt buộc |
| `account_payment` (nếu tách) | Payment register | ⑮ | Kiểm tra bản 17 |
| `mail` | Chatter, activity, thông báo | toàn bộ | Bắt buộc |
| `portal` | Shop tự tra cứu, tài xế đăng nhập | ⑨ ⑩ ⑪ | Bắt buộc |
| `base_automation` | Tự động hoá (nhắc hạn, chặn) | ⑥ ⑭ | Cấu hình, không code |
| `l10n_vn` | Thuế VN, tài khoản VN, VND | ⑬ ⑮ | Bắt buộc |
| `uom` (trong `stock`/`product`) | Đơn vị kg, m³ | ⑫ | Native |
| `fleet` (tuỳ chọn) | `fleet.vehicle` — xe của công ty | ② | **Tái sử dụng!** `lab_laixe` depends `fleet` thay vì tự tạo model xe |
| `hr` (tuỳ chọn) | Tài xế là `hr.employee` | ② | Tái sử dụng thay vì model `tai_xe` |
| ~~`stock_picking_batch`~~ | Đã đánh giá và **loại** — thiếu 5/5 thuộc tính chuyến, `action_cancel` xoá liên kết | — | **Xem ADR-003 (ACCEPTED)** |
| `project` (tuỳ chọn) | Dự phòng cho khiếu nại nếu không cài được helpdesk | N6 | Phương án B |
| `spreadsheet` | Dashboard CE | Báo cáo | Xem §6 |

### 3.2 OCA — phải pin nhánh `17.0`

| Module | Repo | Dùng cho | Rủi ro |
|---|---|---|---|
| **`base_tier_validation`** 17.0.4.1.0 | ⚠️ **`OCA/server-ux`** — **KHÔNG** phải `server-tools` | ⑥ ⑭ N2 — duyệt nhiều cấp | Trung bình: hook thật là `_check_state_conditions` / `_validated_states` (**không** có `_check_tier_validation`) |
| `helpdesk_mgmt` | `OCA/helpdesk` | N6 — khiếu nại | Thấp. Có thêm `helpdesk_mgmt_timesheet`, `_sla`, `_project`, `_rating` |
| **`stock_quant_package_dimension`** | `OCA/stock-logistics-workflow` | Đo kiện dài/rộng/cao → thể tích → cước | **Nên dùng** — khỏi phải tự viết field dimension |
| `stock_picking_invoice_link` | `OCA/stock-logistics-workflow` | Nối picking ↔ hoá đơn (đối soát ⑯) | Thấp |
| `stock_picking_tier_validation` | `OCA/stock-logistics-workflow` | Nếu cần duyệt bước xuất kho ⑦ | Thấp |
| `stock_picking_portal` | `OCA/stock-logistics-workflow` | Tra cứu picking cho khách | **Phải đọc code** — chưa xác nhận có token page |
| `stock_depot` · `stock_dock` · `shipment_advice` | `OCA/stock-logistics-transport` | Kho bãi, dock, lệnh gửi hàng | Chỉ nếu cần |
| `partner_firstname` (tuỳ chọn) | `OCA/partner-contact` | Tên Việt: họ riêng, tên riêng | Thấp |
| `account_financial_report` (tuỳ chọn) | `OCA/account-financial-reporting` | Báo cáo kế toán chi tiết | Thấp |

> ### ✅ ADR-003 ĐÃ CHỐT: viết `lab_chuyen` (phương án C) — loại batch và `tms` bằng bằng chứng
> **Đã kiểm chứng:** `OCA/stock-logistics-transport` **tồn tại ở nhánh 17.0** và chứa bộ **TMS (Transport Management System)**: `tms` với model **`tms.order`, `tms.route`, `tms.stage`, `tms.team`, `tms.crew`, `tms.driver`, `tms.insurance`** + mở rộng `fleet.vehicle`/`res.partner`; kèm `tms_sale`, `tms_purchase`, `tms_account`, `tms_expense`, `tms_product`, `shipment_advice`, `stock_depot`, `stock_dock`.
> (Lưu ý: **không** có `stock.transport` / `transport_trip` — tên đó không tồn tại.)
>
> ⚠️ **`tms` 17.0.1.1.1 · AGPL-3 · `development_status: Alpha`** → rủi ro cao cho demo. Đây là dữ kiện quyết định trong ADR-003.
>
> **BE-01 phải đọc `tms` TRƯỚC khi tạo `lab.chuyen`.** Ba câu phải trả lời trong ADR-003:
> 1. `tms.route`/`tms.order` có mô tả được **chuyến gom nhiều đơn của nhiều khách** không? (TMS thường là **trucking theo tuyến**, không phải **consolidation theo chuyến bay/tàu** — thiên hướng khác.)
> 2. `tms.driver` + `fleet.vehicle` có đủ cho `lab_laixe` không? → nếu đủ, **hạ `lab_laixe` xuống L2**.
> 3. Chi phí phụ thuộc: `tms` kéo theo bao nhiêu module? Có làm nặng bản demo không?
>
> **Kết luận phải nằm trong ADR — dù chọn dùng hay không dùng.** "Không biết nó tồn tại" là lỗi native-first.

### 3.3 Enterprise (EE) — **không dùng**

Đề bài là **Odoo 17 Lab** = Community. Không được phụ thuộc module EE (`stock_landed_costs` là CE, nhưng `helpdesk` bản EE, `studio`, `documents`, `sign` là EE → không dùng).

- Chữ ký trong app tài xế → **vẽ chữ ký trong app KMP**, không dùng `sign` (EE).
- Dashboard → **`spreadsheet_dashboard` (đã xác nhận là CE)**, không cần module EE. Chi tiết §6.
- Khiếu nại → **`helpdesk_mgmt` (OCA)**, không cài `helpdesk` EE.

---

## 4. Những "cái bẫy" tái sử dụng — hay bị viết lại nhất

| Bẫy | Người mới hay làm | Đúng phải là |
|---|---|---|
| Cước | Model `cuoc` với field `khoi_luong`, `don_gia` | `sale.order.line` với `product` UoM kg + `pricelist` |
| Debit | Model `debit` | `account.move` journal Debit |
| Kiện hàng | Model `kien_hang` | `stock.quant.package` (+ `stock.package.type`) |
| Trọng lượng | Model `can_hang` | field trên `stock.move.line` / `stock.picking.weight` |
| Trạng thái hành trình | Thêm state vào `stock.picking` | `lab.hanh.trinh` (vì picking đã `done` khi hàng rời kho) |
| Duyệt | field `duyet_1`, `duyet_2` | `base_tier_validation` |
| Phân quyền | `if user.has_group(...)` | `ir.rule` + `res.groups` |
| Nhắc việc | Cron tự viết | `mail.activity` + `base_automation` |
| Báo cáo | Python xuất Excel | pivot view + `spreadsheet` export |
| Nhãn PDF | `wkhtmltopdf` tự gọi | `ir.actions.report` + QWeb |
| Cổng khách hàng | Controller tự viết | `portal.mixin` + `access_token` |
| Thông báo Zalo | Controller gửi Zalo | `mail.thread` trước; tích hợp Zalo là **backlog**, không phải core |
| Login app mobile | Tự viết JWT | `/web/session/authenticate` + API key của Odoo |

---

## 5. Quy ước "custom theo hướng Odoo 17" (bắt buộc)

Nếu phải viết module mới, nó phải **không thể phân biệt** với module chuẩn của Odoo:

```
lab_chuyen/
├── __init__.py                  # from . import models
├── __manifest__.py              # version '17.0.1.0.0', license 'LGPL-3', category, summary
├── models/
│   ├── __init__.py
│   ├── lab_chuyen.py            # 1 class / 1 file
│   └── sale_order.py            # inherit để nối
├── views/
│   ├── lab_chuyen_views.xml     # list / form / kanban / search / calendar
│   ├── lab_chuyen_menus.xml
│   └── sale_order_views.xml
├── security/
│   ├── lab_chuyen_security.xml  # groups + ir.rule
│   └── ir.model.access.csv
├── data/
│   ├── lab_chuyen_data.xml      # sequence, activity type
│   └── mail_template_data.xml   # email mẫu
├── report/
│   └── lab_chuyen_report.xml    # QWeb AWB/HBL/manifest
├── wizard/
│   └── lab_giam_tai_wizard.py
├── static/description/icon.png
├── i18n/vi.po                   # BẮT BUỘC có tiếng Việt
├── tests/
│   ├── __init__.py
│   └── test_lab_chuyen.py
└── README.rst                   # hoặc README.md
```

**Checklist convention Odoo 17:**

- [ ] `__manifest__.py` có đủ: `name`, `version` = `17.0.x.y.z`, `category`, `summary`, `depends`, `data`, `license`, `installable`, `application`
- [ ] Tên model: `lab.chuyen`, `lab.chuyen.line` (snake_case, có prefix công ty `lab_`)
- [ ] Tên field: `snake_case`, không viết tắt tuỳ tiện
- [ ] `_description` có ở mọi model
- [ ] `_order` có ở mọi model (tránh thứ tự ngẫu nhiên)
- [ ] Dùng **view syntax Odoo 17**: `invisible="state == 'draft'"` — **KHÔNG** dùng `attrs="{...}"` (đã bỏ ở 17)
- [ ] State dùng `Selection` + `<header><button>` + `statusbar` widget
- [ ] Mọi `ir.model.access.csv` có đủ 4 dòng/người dùng × nhóm
- [ ] `ir.rule` cho record-level (tài xế chỉ thấy chuyến của mình)
- [ ] Chuỗi hiển thị **tiếng Việt** trong PO, không hardcode trong Python
- [ ] `tests/` có ít nhất 1 test cho mỗi state transition chính
- [ ] Không `sudo()` bừa; nếu dùng phải comment lý do
- [ ] Không `_sql_constraints` trùng logic với `@api.constrains` (chọn 1, ưu tiên SQL cho unique)

---

## 6. Dashboard & báo cáo — quyết định CE (ĐÃ KIỂM CHỨNG)

**Sự thật 17.0:** cả **`spreadsheet`** và **`spreadsheet_dashboard`** đều là **Community (LGPL-3)** — **không** phải Enterprise. `spreadsheet_dashboard` depends `spreadsheet`; app `board` (CE) depends `spreadsheet_dashboard` và mount menu.

| Cách | Có trong CE 17? | Khi nào dùng |
|---|---|---|
| `graph` view | ✅ core `web` | Biểu đồ nhanh từ model |
| `pivot` view | ✅ core `web` | Bảng phân tích doanh thu / sản lượng |
| `spreadsheet` (app Bảng tính) | ✅ **CE** | Bảng tính kéo thả |
| `spreadsheet_dashboard` | ✅ **CE** — `spreadsheet.dashboard` + `spreadsheet.dashboard.group` | Dashboard lưu thành menu cho sếp |
| `web_dashboard` | ❌ **không tồn tại** ở 14–17 | Đừng plan |
| Controller + Chart.js tự viết | — | ❌ Cấm (viết lại Odoo) |

**Quyết định (ADR-002 — ĐÃ CHỐT, không cần verify nữa):** "sếp mở 1 trang thấy hết" = **`spreadsheet.dashboard`** (CE) + pivot/graph cho phân tích nhanh. Không viết HTML dashboard.

**Chi tiết kỹ thuật 17.0:**
- **Không có `fields.Spreadsheet`.** Lưu trữ: `spreadsheet_binary_data` (Binary) + computed `spreadsheet_data` (Text). Muốn nhúng vào model riêng → **inherit `spreadsheet.mixin`**.
- Phân quyền dashboard: field `group_ids` trên `spreadsheet.dashboard` → dùng để giấu dashboard của sếp.
- Hành động "Insert in spreadsheet" từ pivot/list trong CE: **chưa xác nhận** (UNVERIFIED) — đừng thiết kế demo phụ thuộc vào nó.

---

## 7. Quy trình "chứng minh đã tái sử dụng" (Reuse Proof)

Mỗi PR phải có mục **Reuse Proof** — đây là thứ Review agent kiểm tra đầu tiên:

```markdown
## Reuse Proof
- Grep đã tìm: `rg "chargeable|volumetric" addons/` → không có → tự viết.
- Đã kiểm tra: `stock.picking.weight` tồn tại nhưng chỉ là tổng weight của move → không có volumetric.
- Đã kiểm tra: `OCA/stock-logistics-transport` nhánh 17.0 → có bộ `tms` (`tms.order`, `tms.route`, `tms.driver`…) nhưng hướng **trucking theo tuyến**, không có khái niệm **consolidation theo chuyến bay/tàu** → không dùng được cho ⑤.
- Kết luận: L3 cần thiết. ADR: docs/adr/ADR-003-chuyen-vs-batch.md
- Field kế thừa thay vì model mới: `sale.order.lab_pickup_mode` (2 lựa chọn) — không tạo model.
```

Không có mục này → **không merge**.

---

## 8. Ba câu hỏi của Review agent (in ra dán màn hình)

1. **"Cái này Odoo 17 đã có chưa?"** — nếu có, chỉ vào đường dẫn file trong `odoo/addons/`.
2. **"Nếu chưa có, có cách nào biểu diễn bằng field + view + config không?"**
3. **"Model mới này có nằm trong 3 module được cấp phép không? ADR đâu?"**

---

*Tiếp theo: `docs/02-kien-truc.md` — kiến trúc & model map.*
