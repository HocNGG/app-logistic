# SKILL — Odoo Native Doctrine (học thuyết "không viết lại Odoo")

## Dùng khi nào
**Trước mọi việc.** Đây là skill nền của cả 3 agent. `AGENTS.md` Luật 1 nói *phải* native-first; skill này nói *làm thế nào* để chứng minh điều đó.

---

## 1. Ba câu hỏi (không trả lời được thì chưa được code)

1. **Odoo 17 đã có model nào mang đúng ngữ nghĩa này chưa?**
   → *Grep `addons/` trước, Google sau.* Người mới grep Google; kỹ sư Odoo grep source.
2. **Nếu chưa có model, có biểu diễn được bằng field + view + config trên model có sẵn không?**
3. **Nếu phải tạo model, nó nằm trong 5 module được cấp phép không? ADR đâu?**

Thời gian trả lời mục tiêu: **90 giây**. Quá 90 giây → ghi `docs/09-open-questions.md` rồi làm việc khác.

---

## 2. Thang 5 bậc — luôn chọn bậc thấp nhất giải quyết được

| Bậc | Tên | Dấu hiệu | Ví dụ trong dự án này |
|---|---|---|---|
| **L0** | Dùng native | 0 dòng code | Bước ⑬ Debit = `account.move`; ⑮ thu tiền = `account.payment` |
| **L1** | Cấu hình | Chỉ XML/CSV/config | Tier validation 2 cấp (⑥⑭), pricelist theo kg (⑫), lock date (⑯) |
| **L2** | Kế thừa nhẹ | `_inherit` + field/method, không model mới | `sale.order.lab_pickup_mode`, `stock.move.line.lab_weight_kg` |
| **L3** | Module mới | Model mới + view + security | `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh` |
| **L4** | Fork | Sửa module core/OCA | ❌ **CẤM** |

**Lỗi phổ biến:** nhảy thẳng L3 vì "viết mới cho nhanh". Nhanh trong 2 giờ, chậm trong 2 tuần (mất hạch toán, mất portal, mất audit, mất báo cáo).

---

## 3. Bản đồ "nếu bạn định viết X, Odoo đã có Y"

| Định viết | Odoo 17 đã có | Ghi chú 17.0 |
|---|---|---|
| model `debit` | `account.move` | journal "Debit", `move_type='out_invoice'` |
| model `cuoc` / `phi_van_chuyen` | `sale.order.line` + `product` UoM kg + `product.pricelist` | bậc thang giá theo kg là native |
| model `kien_hang` | **`stock.quant.package`** | ⚠️ `stock.package` là tên của **18.0** |
| model `lo_hang` | `stock.picking` + `lab.chuyen.line` | không có "lô" độc lập |
| model `can_hang` (cân) | field trên `stock.move.line` + `stock.picking.weight` | `stock.move.weight` chỉ có khi cài `stock_delivery` |
| model `tai_xe` | `hr.employee` + `fleet.vehicle` (hoặc OCA `tms.driver`) | |
| model `khieu_nai` | `helpdesk_mgmt` (OCA/helpdesk) | `helpdesk` bản EE thì không dùng |
| model `duyet_1`, `duyet_2` | `base_tier_validation` (**OCA/server-ux**) | |
| cron Python tự viết | `base_automation` + `mail.activity` | |
| controller portal tự viết | `portal.mixin` + `access_token` | 17.0 **không có** route tổng quát `/my/<model>/<id>` |
| Python xuất Excel/PDF | `pivot` view + QWeb report | |
| HTML dashboard + Chart.js | `spreadsheet_dashboard` (**CE**) | |
| JWT tự phát minh | `res.users.apikeys` (thay password) | 17.0 **không** có Bearer |

---

## 4. Reuse Proof — bằng chứng bắt buộc

Không có mục này → RV-01 trả lại, **không đọc code**.

```markdown
## Reuse Proof — <module>/<việc>
1. Đã grep: `rg "chargeable|volumetric|groupage|consolidat" addons/` → không có kết quả.
2. Đã đọc source 17.0: addons/stock_delivery/models/stock_picking.py
   → `weight`/`shipping_weight` chỉ là tổng khối lượng move + package, KHÔNG có quy đổi thể tích.
3. Đã kiểm OCA: stock-logistics-workflow (stock_quant_package_dimension → chỉ có dimension, không có divisor),
   stock-logistics-transport (tms.* → trucking theo tuyến, không phải consolidation theo chuyến bay/tàu).
4. Kết luận: **L2** — thêm `lab_volumetric_divisor` (product.template) + `lab_chargeable_weight` (compute trên stock.move.line).
   Không tạo model trọng lượng. Không cần ADR.
```

---

## 5. Khi nào một model mới là **chính đáng**

Chỉ khi **cả 4 điều** đúng:

1. Không model native nào mang ngữ nghĩa đó (đã chứng minh bằng Reuse Proof).
2. Không thể biểu diễn bằng field trên native model mà **không làm bẩn** ngữ nghĩa (ví dụ nhét "chuyến" vào `stock.picking` là bẩn — picking là chứng từ kho).
3. Có **vòng đời riêng** (state machine riêng) hoặc **quan hệ riêng** (nhiều-nhiều giữa nhiều đối tượng).
4. Nằm trong module được cấp phép + có ADR.

Áp vào dự án: `lab.chuyen` (vòng đời riêng: draft→open→booked→departed→arrived→delivered→closed), `lab.yeu.cau.lay.hang` (vòng đời riêng, tài xế + chữ ký), `lab.hanh.trinh` (vòng đời riêng, sau khi picking đã `done`) — **đủ 4 điều**. Model "trọng lượng", "cước", "kiện" — **không đủ**.

---

## 6. Nguyên tắc vàng

> Nếu xoá hết code bạn viết, Odoo 17 vẫn làm được **80%** công việc → bạn đã viết thừa.

Hệ quả thực hành:
- Mỗi field mới phải biện minh được "tại sao không dùng field có sẵn".
- Mỗi method mới: hỏi "native đã có hook chưa?" (`_action_confirm`, `_compute_*`, `_prepare_*_values`).
- Mỗi view mới: hỏi "có bị Odoo đè khi upgrade không?" → dùng `xpath` inherit thay vì copy.

## Checklist nhanh
- [ ] Đã grep `addons/` (không phải Google trước)
- [ ] Đã đọc source 17.0 của model sẽ inherit
- [ ] Chọn bậc thấp nhất giải quyết được (L0→L3)
- [ ] Có Reuse Proof, có đường dẫn file / lệnh đã chạy
- [ ] Không có model trùng ngữ nghĩa native
- [ ] Không sửa `addons/oca/**`
- [ ] Nếu L3: có ADR + nằm trong module được cấp phép
