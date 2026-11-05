# 05 — Stock & logistics Odoo 17 (picking, move, kiện, kho)

## Dùng khi nào

Bước ③④⑦ (nhận, cân–đo–dán kiện, đóng gói, xuất kho), nhánh ngoại lệ ①④⑤ — trước khi định tạo field/model mới trong `stock`.

---

## 1. Bản đồ khái niệm — dừng lại ở đây trước khi code

| Nghiệp vụ | Đối tượng Odoo 17 | KHÔNG tạo |
|---|---|---|
| Phiếu nhận / xuất / chuyển kho | `stock.picking` (+ `stock.picking.type`) | model phiếu |
| Dòng hàng cần chuyển | `stock.move` (`product_uom_qty` = yêu cầu) | dòng đơn |
| Dòng hàng thực tế theo lô/kiện | `stock.move.line` (`quantity` = đã làm) | bảng cân |
| Kiện | **`stock.quant.package`** (loại kiện: `stock.package.type`) | `kien_hang` |
| Tồn kho | `stock.quant` | bảng tồn riêng |
| Kho / vị trí | `stock.warehouse` / `stock.location` | kho ảo |

**17.0:** model kiện là `stock.quant.package` (không phải `stock.package` của 18.0). Trường loại kiện: `stock.quant.package.package_type_id` → `stock.package.type`. `stock.package.type` có `packaging_length` (**không phải** `length`), `width`, `height`, `base_weight`, `max_weight`, `barcode`.

## 2. `quantity` + `picked` — không có `quantity_done`

```python
move = picking.move_ids_without_package[:1]
move.product_uom_qty          # số yêu cầu (theo product_uom)
move.quantity                 # ĐÃ LÀM — compute Σ move_line.quantity, có inverse
move.picked                   # True = "đã lấy đủ, chốt con số này khi validate"
```

- 16.0: `quantity_done`. **17.0: `quantity`** — viết `quantity_done` là `ValueError: Invalid field`.
- Validate chỉ ghi nhận `quantity` của move line có `picked=True`. Muốn chốt đúng số đã đếm: set `move.picked = True` (hoặc tạo move line với `picked=True`).
- Cách chuẩn (không phụ thuộc inverse): tạo/thao tác `stock.move.line` trực tiếp:

```python
self.env["stock.move.line"].create({
    "move_id": move.id,
    "picking_id": picking.id,
    "product_id": move.product_id.id,
    "product_uom_id": move.product_uom.id,
    "quantity": 5.0,
    "picked": True,
    "location_id": picking.location_id.id,
    "location_dest_id": picking.location_dest_id.id,
    "result_package_id": package.id,      # kiện đích (17.0)
})
```

## 3. Nhận hàng, đóng kiện, xuất kho — code copy được

```python
Picking = self.env["stock.picking"]
Package = self.env["stock.quant.package"]
PackageType = self.env["stock.package.type"]

# ① Kiện: tạo bằng tay là hợp lệ (name để trống -> Odoo sinh PACK00001)
kien = Package.create({"package_type_id": PackageType.search([], limit=1).id})

# ② Phiếu nhận (Receipt) KHÔNG có khách — nhánh ① (docs/07)
picking = Picking.create({
    "picking_type_id": self.env.ref("lab_core.picking_type_kho_cho").id,
    "location_id": self.env.ref("stock.stock_location_suppliers").id,
    "location_dest_id": self.env.ref("lab_core.location_kho_cho").id,
    "partner_id": False,                      # hợp lệ: stock.picking.partner_id không required
    "lab_is_cho_khach": True,                 # field của lab_core
    "move_ids_without_package": [(0, 0, {
        "name": "Hàng chờ khách",
        "product_id": product.id,
        "product_uom_qty": 3.0,
        "product_uom": product.uom_id.id,
        "location_id": self.env.ref("stock.stock_location_suppliers").id,
        "location_dest_id": self.env.ref("lab_core.location_kho_cho").id,
    })],
})
picking.action_confirm()      # tạo/confirm move
picking.action_assign()       # giữ chỗ (receipt thường không cần)
for move in picking.move_ids:
    move.quantity = move.product_uom_qty
    move.picked = True
res = picking.button_validate()   # -> có thể trả action wizard backorder
```

`button_validate()` trả về `dict` action khi thiếu số → phải xử lý wizard, **không** bỏ qua kết quả (xem §4).

## 4. Backorder (nhánh ④ — xuất thiếu kiện, tuyệt đối dùng native)

Khi `quantity < product_uom_qty` (và `picked=True`), 17.0 mở wizard `stock.backorder.confirmation`:

```python
res = picking.button_validate()
if isinstance(res, dict) and res.get("res_model") == "stock.backorder.confirmation":
    wizard = self.env["stock.backorder.confirmation"].with_context(
        res.get("context", {})).create({})
    wizard.process()                 # tạo backorder (picking mới, state assigned)
    # hoặc wizard.process_cancel_backorder()  -> không tạo backorder, đóng phần thiếu
```

Context native liên quan: `button_validate_picking_ids`, `skip_backorder=True`, `picking_ids_not_to_backorder` (đã kiểm trong `addons/stock/wizard/stock_backorder_confirmation.py` 17.0). Không tự `move.quantity = 0` cho phần thiếu — mất dấu vết.

## 5. Weight: cái nào có sẵn, cái nào phải thêm

| Field | Có từ | Ý nghĩa |
|---|---|---|
| `product.template.weight`, `weight_uom_id` | `stock`/`product` | kg của 1 đơn vị SP |
| `stock.move.weight` | **`stock_delivery`** (stored compute = `product_qty * product.weight`) | kg thực tế của move |
| `stock.picking.weight` | **`stock_delivery`** (`_cal_weight` = Σ move.weight) | kg thực tế của phiếu |
| `stock.picking.shipping_weight`, `weight_bulk`, `package_ids` | **`stock_delivery`** | kg dùng để tính cước |
| `stock.quant.package.weight`, `shipping_weight` | **`stock_delivery`** | kg của kiện |
| `lab_weight_kg`, `lab_volume_m3`, `lab_dim_l/w/h` trên move line | **chưa có** — `lab_core` thêm (L2) | kg **cân thực tế** và m³ **đo được** (khác `weight` = kg lý thuyết theo SP) |
| `lab_chargeable_weight` | **chưa có** — compute trên move line | max(actual, volumetric) → `06-sale-pricing-kg.md` |

→ Không cài `stock_delivery` là **không có** `stock.picking.weight`, `stock.move.weight`, `package_ids`. Đây chính là lý do `lab_chuyen` depends `stock_delivery`.

## 6. Location vs warehouse — 3 kho của dự án

```
KHO CHÍNH (WH)          KHO BAY (BAY)          KHO TÀU (TAU)
 ├─ WH/Stock             ├─ BAY/Stock            ├─ TAU/Stock
 ├─ WH/Stock/KHO CHO     └─ BAY/Input            └─ TAU/Input
 │   (location, KHÔNG phải warehouse — docs/00 §9.4)
 └─ WH/Input
```

- **KHO CHỜ là location**, không phải warehouse thứ 4 (nếu tạo warehouse: báo cáo tồn tách rời, phải chuyển kho thủ công — `docs/07` ①).
- Chuyển kho bay ↔ tàu (nhánh ⑤) = **internal transfer** giữa 2 warehouse: tạo `stock.picking` với `picking_type_id` code `internal`, `location_id = BAY/Stock`, `location_dest_id = TAU/Stock`. Không sửa `stock.quant` trực tiếp.

```python
internal_type = self.env["stock.picking.type"].search(
    [("code", "=", "internal"), ("warehouse_id", "=", kho_bay.id)], limit=1)
chuyen_kho = Picking.create({
    "picking_type_id": internal_type.id,
    "location_id": kho_bay.lot_stock_id.id,
    "location_dest_id": kho_tau.lot_stock_id.id,
    "origin": chuyen.name,
    "move_ids_without_package": [(0, 0, {
        "name": "Chuyển kho bay → tàu",
        "product_id": product.id, "product_uom_qty": qty,
        "product_uom": product.uom_id.id,
        "location_id": kho_bay.lot_stock_id.id,
        "location_dest_id": kho_tau.lot_stock_id.id})],
})
```

Cấu hình (L1) làm 1 lần bằng XML/giao diện: 3 warehouse, `lot_stock_id`, location `KHO CHỜ` (usage `internal`), picking type `Receipt (KHO CHỜ)` + `Delivery` mỗi kho, 2 route resupply nếu muốn tự sinh phiếu chuyển kho. **Ghi ID bằng `ref=` trong data XML, không hardcode số.**

## 7. Những cái bẫy hay gặp

- ❌ `quantity_done`, `qty_done`, `reserved_uom_qty` → không tồn tại ở 17.0.
- ❌ `stock.package` → `stock.quant.package`; `stock.package.type` giữ nguyên tên (chỉ là *type*).
- ❌ Validate bằng `picking.write({'state': 'done'})` → không sinh `stock.move.line`, tồn kho sai. Luôn `button_validate()`.
- ❌ Tạo `stock.quant` bằng tay để "nhập kho" → không có chứng từ, vi phạm Luật 4 (REA).
- ❌ Gán `partner_id` cho picking đã `done` để "gán khách" (nhánh ①) → phải sinh **internal transfer** KHO CHỜ → WH/Stock.
- ❌ Dùng `lot_id` cho kiện: kiện là `package`, lô là `lot` — hai khái niệm khác nhau.

## Checklist nhanh

- [ ] Không còn `quantity_done|qty_done` trong `addons/` (`rg` kiểm).
- [ ] Kiện dùng `stock.quant.package` + `package_type_id`; không model `kien_hang`.
- [ ] Mọi thay đổi tồn kho đi qua `stock.picking` + `button_validate()`.
- [ ] Có xử lý kết quả `button_validate()` (wizard backorder) — có test thiếu kiện.
- [ ] `lab_chuyen.depends` có `stock_delivery` (vì `weight`/`shipping_weight` ở đó).
- [ ] KHO CHỜ là `stock.location`, không phải `stock.warehouse`.
- [ ] ID kho/location lấy bằng `ref=` trong data XML, không hardcode.
- [ ] Test: nhận không partner → quant ở KHO CHỜ; gán khách → internal transfer → quant ở WH/Stock.
