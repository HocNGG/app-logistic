# 06 — Sale & cước theo kg (bước ①②⑫ + 1 compute duy nhất của dự án)

## Dùng khi nào

Khi làm báo giá/đơn (`sale.order`), bảng giá bậc thang theo kg, hoặc khi cần "khối lượng tính cước" (chargeable weight).

---

## 1. Sự thật native — 90% bài toán cước đã có sẵn

| Cần | Native | Ghi chú |
|---|---|---|
| Đơn vị kg | `uom.uom` xmlid **`uom.product_uom_kgm`** | danh mục `uom.product_uom_categ_kgm` |
| Bảng giá bậc thang | `product.pricelist.item` (`min_quantity`) | L1 — cấu hình, không code |
| Dòng cước | `sale.order.line` | product cước + `product_uom` = kg |
| Tổng kg của đơn | `sale.order.shipping_weight` (từ `sale_stock`, verify trong `addons/sale_stock/models/sale_order.py` 17.0) | kg thực tế của picking |
| Thể tích kiện | OCA `stock_quant_package_dimension` (`volume`, `width`, `height`, `pack_length`) | xem `15-oca-reuse.md` |
| **Khối lượng tính cước** | **không có** | **đây là phần duy nhất phải tự viết** |

## 2. Sản phẩm cước + UoM kg (L1, data XML)

```xml
<!-- addons/lab_core/data/lab_product_data.xml -->
<odoo>
  <record id="product_cuoc_bay" model="product.product">
    <field name="name">Cước vận chuyển hàng không (kg)</field>
    <field name="type">service</field>
    <field name="uom_id" ref="uom.product_uom_kgm"/>
    <field name="uom_po_id" ref="uom.product_uom_kgm"/>
    <field name="list_price">0.0</field>
    <field name="lab_is_freight" eval="True"/>
    <field name="lab_volumetric_divisor">6000</field>
  </record>
  <record id="product_cuoc_bien" model="product.product">
    <field name="name">Cước vận chuyển đường biển (kg)</field>
    <field name="type">service</field>
    <field name="uom_id" ref="uom.product_uom_kgm"/>
    <field name="uom_po_id" ref="uom.product_uom_kgm"/>
    <field name="lab_is_freight" eval="True"/>
    <field name="lab_volumetric_divisor">1000</field>
  </record>
</odoo>
```

`product.template.type = 'product'` (hàng tồn kho) / `'service'` (cước). `is_storable` là 18.0 — **không** dùng.

## 3. Bảng giá bậc thang theo kg (L1 — không code)

```xml
<record id="pricelist_cuoc_bay" model="product.pricelist">
  <field name="name">Bảng giá cước bay (theo kg)</field>
  <field name="currency_id" ref="base.USD"/>
</record>
<record id="pl_item_bay_0_45" model="product.pricelist.item">
  <field name="pricelist_id" ref="pricelist_cuoc_bay"/>
  <field name="applied_on">1_product</field>
  <field name="product_tmpl_id" ref="product_cuoc_bay_product_template"/>
  <field name="min_quantity">0</field>
  <field name="compute_price">fixed</field>
  <field name="fixed_price">6.5</field>
</record>
<record id="pl_item_bay_45_100" model="product.pricelist.item">
  <field name="pricelist_id" ref="pricelist_cuoc_bay"/>
  <field name="applied_on">1_product</field>
  <field name="product_tmpl_id" ref="product_cuoc_bay_product_template"/>
  <field name="min_quantity">45</field>
  <field name="compute_price">fixed</field>
  <field name="fixed_price">5.9</field>
</record>
```

Tính tiền = `số kg tính cước × giá bậc`. Đừng tạo model `cuoc`/`phi_van_chuyen` (AGENTS.md §8). Nếu khách muốn giá theo **tuyến**, nhân bản bảng giá theo tuyến (`product.pricelist` nhiều bản) — vẫn L1.

## 4. Phần duy nhất phải viết: chargeable weight

**Nơi đặt hệ số chia:** `product.template.lab_volumetric_divisor` (int, default 6000). Mỗi loại cước có 1 product riêng: hàng không 6000, đường biển 1000 (đã khai ở §2). Như vậy hệ số **đi theo sản phẩm cước**, không hardcode trong Python, và kế toán tự sửa được ở màn hình sản phẩm.

```python
# addons/lab_core/models/product_template.py
from odoo import fields, models


class ProductTemplate(models.Model):
    _inherit = "product.template"

    lab_is_freight = fields.Boolean(string="Là sản phẩm cước", default=False)
    lab_volumetric_divisor = fields.Integer(
        string="Hệ số thể tích (cm³/kg)", default=6000,
        help="6000 = hàng không, 1000 = đường biển (1 CBM = 1000 kg).")
```

```python
# addons/lab_core/models/stock_move_line.py
from odoo import api, fields, models


class StockMoveLine(models.Model):
    _inherit = "stock.move.line"

    lab_weight_kg = fields.Float("Kg cân thực tế", digits="Stock Weight")
    lab_volume_m3 = fields.Float("Thể tích (m³)", digits=(8, 4))
    lab_volumetric_weight = fields.Float(
        "Kg thể tích", compute="_compute_lab_chargeable_weight",
        store=True, digits="Stock Weight")
    lab_chargeable_weight = fields.Float(
        "Kg tính cước", compute="_compute_lab_chargeable_weight",
        store=True, digits="Stock Weight",
        help="max(kg thực tế, kg thể tích).")

    @api.depends("lab_weight_kg", "lab_volume_m3", "move_id.product_id.lab_volumetric_divisor")
    def _compute_lab_chargeable_weight(self):
        for line in self:
            divisor = line.move_id.product_id.lab_volumetric_divisor or 6000
            # 1 m³ = 1_000_000 cm³  →  kg thể tích = cm³ / divisor
            line.lab_volumetric_weight = line.lab_volume_m3 * 1000000.0 / divisor
            line.lab_chargeable_weight = max(line.lab_weight_kg, line.lab_volumetric_weight)
```

Kiểm số học: air, kiện 0,5 m³ → 500 000 cm³ / 6000 = **83,33 kg** (thực tế 60 kg → tính cước 83,33). Sea, 0,5 m³ → 500 000 / 1000 = **500 kg** (thực tế 60 kg → 500 kg). Đúng nghiệp vụ.

Tổng theo đơn: dùng field có sẵn của `lab_core` (`sale.order.lab_chargeable_weight`) — **compute từ các move line của picking thuộc đơn**, không lưu tay:

```python
class SaleOrder(models.Model):
    _inherit = "sale.order"

    lab_chargeable_weight = fields.Float(
        "Kg tính cước", compute="_compute_lab_chargeable_weight",
        store=True, digits="Stock Weight")

    @api.depends("picking_ids.move_line_ids.lab_chargeable_weight", "order_line.product_uom_qty")
    def _compute_lab_chargeable_weight(self):
        for order in self:
            lines = order.picking_ids.move_line_ids.filtered("lab_chargeable_weight")
            order.lab_chargeable_weight = sum(lines.mapped("lab_chargeable_weight"))
```

Nếu picking chưa có (đơn mới), dùng `order_line.product_uom_qty` của dòng hàng làm kg tạm — ghi rõ trong help text, đừng để 0 im lặng (nhánh ③ "thiếu cân nặng" phải chặn được).

## 5. Chọn hệ số theo chuyến (air ↔ sea, nhánh ⑤)

Hệ số đi theo **product cước** (`lab_volumetric_divisor`), `lab.chuyen.loai_van_chuyen` chỉ quyết định dùng product cước nào — không sửa tay hệ số trên chuyến, không hardcode bảng `{"air": 6000, "sea": 1000}` trong compute. Khi đổi `loai_van_chuyen` (nhánh ⑤) thì cước phải tính lại → cảnh báo kế toán nếu Debit đã lập (`docs/07` ⑤); không "tự tính lại tiền" trong `write` của chuyến.

## 6. Test case bắt buộc (`tests/test_chargeable_weight.py`)

| # | Case | Cho | Kỳ vọng |
|---|---|---|---|
| 1 | Thực tế > thể tích | 60 kg, 0,2 m³, divisor 6000 | `lab_chargeable_weight = 60,0` (thể tích 33,33) |
| 2 | Thể tích > thực tế | 60 kg, 1,0 m³, divisor 6000 | `166,67` |
| 3 | Đường biển | 60 kg, 0,5 m³, divisor 1000 | `500,0` |
| 4 | Không có thể tích | 60 kg, 0 m³ | `60,0` (không chia 0) |
| 5 | Thiếu kg & không thể tích | 0 kg, 0 m³ | `0,0` + UI/constraint chặn tính cước |
| 6 | Tổng theo đơn | 2 line 60 + 100 kg | `lab_chargeable_weight = 160,0` |

```python
def test_the_tich_lon_hon_thuc_te(self):
    line = self._make_move_line(weight_kg=60.0, volume_m3=1.0, divisor=6000)
    self.assertAlmostEqual(line.lab_volumetric_weight, 166.67, places=2)
    self.assertAlmostEqual(line.lab_chargeable_weight, 166.67, places=2)
```

## Checklist nhanh

- [ ] UoM lấy bằng `ref="uom.product_uom_kgm"`, không tạo UoM mới.
- [ ] Bảng giá bậc thang bằng `product.pricelist.item.min_quantity` (L1), không code.
- [ ] Không có model `cuoc` / `phi_van_chuyen` / `khoi_luong` trong `addons/`.
- [ ] Hệ số 6000/1000 nằm ở `product.template.lab_volumetric_divisor`, không hardcode.
- [ ] `lab_volumetric_weight` và `lab_chargeable_weight` đều `store=True` (cần filter/pivot).
- [ ] Có đủ 6 test case ở §6, chạy xanh bằng `--test-tags=/lab_core`.
- [ ] Thiếu kg → chặn tính cước (nhánh ③), không âm thầm tính 0.
