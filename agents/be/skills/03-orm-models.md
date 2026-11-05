# 03 — ORM models Odoo 17 (field, compute, state, skeleton `lab.chuyen`)

## Dùng khi nào

Khi viết hoặc sửa bất kỳ `models/*.py`, `wizard/*.py` — đọc mục "KHÔNG làm" (§7) trước khi commit.

## 1. Cheat sheet field type (17.0)

| Cần | Field | Ghi chú |
|---|---|---|
| Mã chứng từ | `Char(copy=False, readonly=True, default=lambda self: _('Mới'))` | + `ir.sequence` trong `create` |
| Trạng thái | `Selection([...], default='draft', tracking=True, copy=False)` | `tracking=True` để chatter tự ghi |
| Kg / CBM | `Float(digits='Stock Weight')` / `Float(digits=(8, 4))` | đừng tự làm tròn trong model |
| Ngày, giờ chốt | `Date()`, `Datetime()` | cut-off là `Datetime` |
| Người / xe | `Many2one('hr.employee')`, `Many2one('fleet.vehicle')` | tái sử dụng `hr` + `fleet` |
| Dòng chi tiết | `One2many('lab.chuyen.line', 'chuyen_id')` | `inverse_name` bắt buộc |

Kế thừa: `_inherit = "stock.picking"` để **thêm** field (L2, không đổi semantics); `_inherit = ["stock.picking", "portal.mixin"]` khi cần nhiều mixin (`docs/02` §6.5). `_inherits` (delegation 1-1) **không dùng** trong dự án — cần "là một" cái khác thì gần như chắc chắn là `Many2one`.

## 2. Computed: `store` / `depends` / `precompute`

```python
tai_trong_da_dung_kg = fields.Float("Đã dùng (kg)", compute="_compute_tai_trong",
                                    store=True, digits="Stock Weight")   # store: filter/sort được
ti_le_tai = fields.Float("Tỉ lệ tải (%)", compute="_compute_tai_trong")   # không store: chỉ hiển thị

@api.depends("line_ids.khoi_luong_kg", "line_ids.state", "tai_trong_max_kg")
def _compute_tai_trong(self):
    for chuyen in self:
        booked = chuyen.line_ids.filtered(lambda l: l.state == "booked")
        chuyen.tai_trong_da_dung_kg = sum(booked.mapped("khoi_luong_kg"))
        chuyen.ti_le_tai = (chuyen.tai_trong_da_dung_kg / chuyen.tai_trong_max_kg * 100.0
                            if chuyen.tai_trong_max_kg else 0.0)
```

`depends` phải ghi **đường dẫn đầy đủ** (`line_ids.state`) — thiếu 1 nhánh là bug im lặng. `precompute=True` chỉ khi cần giá trị có ngay trước khi insert (verify `odoo/fields.py` 17.0; dự án này không cần).

## 3. `constrains` vs `_sql_constraints`

```python
_sql_constraints = [
    ("chuyen_booking_uniq", "unique(so_booking, kho_xuat_id, loai_van_chuyen)",
     "Số booking đã tồn tại trong kho này!"),
    ("chuyen_tai_trong_positive", "CHECK(tai_trong_max_kg >= 0)", "Tải trọng phải >= 0"),
]

@api.constrains("ngay_di", "ngay_den_du_kien")
def _check_ngay(self):
    for chuyen in self:
        if chuyen.ngay_di and chuyen.ngay_den_du_kien and chuyen.ngay_di > chuyen.ngay_den_du_kien:
            raise ValidationError(_("Ngày đi không được sau ngày đến dự kiến (%s).") % chuyen.name)
```

`_sql_constraints` cho unique/check SQL thuần; `@api.constrains` cho luật nhiều field + cần `_()`. **17.0 vẫn dùng `_sql_constraints`** — `models.Constraint` là 18.0. Không lặp cùng một luật ở cả hai nơi.

## 4. `related`, `ondelete`, `_order`, `_description`

```python
partner_id = fields.Many2one("res.partner", compute="_compute_partner",
                             store=True, readonly=True, index=True)      # lọc nhiều -> index
chuyen_id = fields.Many2one("lab.chuyen", required=True, ondelete="cascade")       # dòng theo chuyến
sale_order_id = fields.Many2one("sale.order", required=True, ondelete="restrict")  # SO còn line
```

`ondelete`: `cascade` cho dòng chi tiết, `restrict` cho chứng từ đã sinh bút toán. `_description` bắt buộc (thiếu → ACL/menu hiện tên kỹ thuật). `_order = "ngay_di desc, id desc"` — luôn có `id` phụ để phân trang ổn định.

## 5. State machine — 4 luật, không thương lượng

1. Mỗi transition = 1 method `action_*`, kiểm state hiện tại trước khi đổi.
2. State sai → `UserError` **tiếng Việt** nêu rõ state hiện tại (`docs/02` §3).
3. Mỗi lần đổi state → `message_post` (hoặc `tracking=True` trên field `state`).
4. Không "nhảy cóc" state; không `write({'state': ...})` từ ngoài — chỉ gọi `action_*`. Ví dụ chuẩn: `action_depart` ở §6.

## 6. Skeleton `lab.chuyen` (rút gọn — phần đủ để chạy)

```python
# addons/lab_chuyen/models/lab_chuyen.py
from odoo import api, fields, models, _
from odoo.exceptions import UserError, ValidationError


class LabChuyen(models.Model):
    _name = "lab.chuyen"
    _description = "Chuyến gom hàng lẻ"
    _order = "ngay_di desc, id desc"
    _inherit = ["mail.thread", "mail.activity.mixin", "tier.validation"]

    # tier validation (⑥): 17.0.4.1.0 đọc _state_from/_state_to — xem 08-tier-validation.md
    _tier_validation_manual_config = False
    _state_field = "state"
    _state_from = ["booking_confirmed"]
    _state_to = ["departed"]

    name = fields.Char("Mã chuyến", required=True, copy=False, readonly=True,
                       default=lambda self: _("Mới"), index=True)
    loai_van_chuyen = fields.Selection(
        [("air", "Đường bay"), ("sea", "Đường biển")],
        string="Loại vận chuyển", required=True, default="air", tracking=True)
    carrier_id = fields.Many2one("delivery.carrier", string="Phương thức VC", tracking=True)
    so_booking = fields.Char("Số booking", copy=False)
    kho_xuat_id = fields.Many2one("stock.warehouse", string="Kho xuất", required=True,
                                  check_company=True)
    ngay_di = fields.Date("Ngày đi")
    cut_off_datetime = fields.Datetime("Hạn chốt hàng", tracking=True)
    tai_trong_max_kg = fields.Float("Tải trọng tối đa (kg)", digits="Stock Weight")
    tai_trong_da_dung_kg = fields.Float("Đã dùng (kg)", compute="_compute_tai_trong",
                                       store=True, digits="Stock Weight")
    ti_le_tai = fields.Float("Tỉ lệ tải (%)", compute="_compute_tai_trong")
    state = fields.Selection(
        [("draft", "Nháp"), ("open", "Đang mở"), ("booking_confirmed", "Đã chốt booking"),
         ("departed", "Đã khởi hành"), ("arrived", "Đã đến"), ("delivered", "Đã giao"),
         ("closed", "Đã đóng"), ("cancelled", "Đã huỷ")],
        string="Trạng thái", default="draft", required=True, tracking=True, copy=False, index=True)
    line_ids = fields.One2many("lab.chuyen.line", "chuyen_id", string="Đơn trong chuyến")
    picking_ids = fields.One2many("stock.picking", "lab_chuyen_id", string="Phiếu xuất")

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get("name", _("Mới")) == _("Mới"):
                loai = vals.get("loai_van_chuyen") or "air"
                vals["name"] = (self.env["ir.sequence"].next_by_code("lab.chuyen.%s" % loai)
                                or _("Mới"))
        return super().create(vals_list)

    @api.depends("line_ids.khoi_luong_kg", "line_ids.state", "tai_trong_max_kg")
    def _compute_tai_trong(self):
        for chuyen in self:
            booked = chuyen.line_ids.filtered(lambda l: l.state == "booked")
            chuyen.tai_trong_da_dung_kg = sum(booked.mapped("khoi_luong_kg"))
            chuyen.ti_le_tai = (chuyen.tai_trong_da_dung_kg / chuyen.tai_trong_max_kg * 100.0
                                if chuyen.tai_trong_max_kg else 0.0)

    def action_depart(self):
        for chuyen in self:
            if chuyen.state != "booking_confirmed":
                raise UserError(_("Chỉ chuyến đã chốt booking mới được khởi hành."))
            chuyen.state = "departed"      # tier validation chặn ở đây nếu chưa duyệt
            chuyen.message_post(body=_("Chuyến đã khởi hành."))
        return True

    def action_cancel(self):
        for chuyen in self:
            if chuyen.state in ("delivered", "closed"):
                raise UserError(_("Không thể huỷ chuyến đã giao/đã đóng."))
            chuyen.line_ids.filtered(lambda l: l.state == "booked").write({"state": "cancelled"})
            chuyen.state = "cancelled"
            chuyen.message_post(body=_("Chuyến đã huỷ."))
        return True
```

## 7. KHÔNG làm

- ❌ `attrs=`/`states=` trong view, `states={...}` trên field — bỏ ở 17.0.
- ❌ `read_group(...)` → `_read_group(domain, groupby, aggregates)`; `models.Constraint` (18.0) → `_sql_constraints`.
- ❌ Business rule trong `onchange` (client-only) → `constrains`/`write`.
- ❌ `if not self.env.user.has_group(...): raise` → `ir.rule` + ACL (`04-security-acl-rules.md`).
- ❌ `sudo()` không comment; base64 ảnh trong `Char` (dùng `ir.attachment`).
- ❌ Model trùng native: `debit`, `cuoc`, `kien_hang`, `lo_hang`, `tai_xe`, `khieu_nai` (AGENTS.md §8).

## Checklist nhanh

- [ ] `_name`, `_description`, `_order` (có `id` phụ) ở mọi model mới.
- [ ] Field có `string=` tiếng Việt; mọi chuỗi user-facing bọc `_()`.
- [ ] Computed `store=True` khi cần filter/sort; `@api.depends` đủ đường dẫn.
- [ ] `_sql_constraints` cho unique/check; `@api.constrains` cho luật nhiều field.
- [ ] Mọi `action_*` chặn state sai bằng `UserError` tiếng Việt + `message_post`.
- [ ] `ondelete` đúng: dòng chi tiết `cascade`, chứng từ `restrict`.
- [ ] Không `_inherits`, không model trùng native, không `has_group` trong logic.
- [ ] Có ≥ 1 test cho mỗi transition + 1 case lỗi (`11-testing-odoo.md`).
