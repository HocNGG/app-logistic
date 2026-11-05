# 08 — Tier validation (duyệt 2 cấp cho ⑥, ⑭, giảm tải)

## Dùng khi nào

Khi cần "phải có người duyệt mới được làm" — duyệt đơn vào chuyến (⑥), giảm tải (nhánh ②), khoá Debit (⑭). Không dùng khi chỉ cần ghi log.

---

## 1. Cài đặt — repo đúng là `OCA/server-ux`, KHÔNG phải `server-tools`

```powershell
git clone --branch 17.0 --depth 1 https://github.com/OCA/server-ux.git addons/oca/server-ux
# addons-path trong infra/odoo.conf phải có addons/oca/server-ux
python odoo-bin -c infra/odoo.conf -d odoo17_lab -i base_tier_validation --stop-after-init
```

Đã kiểm chứng nhánh 17.0: `base_tier_validation` **17.0.4.1.0**, license **AGPL-3**, `depends: ["mail"]`, mixin abstract `tier.validation`.

## 2. Hợp đồng của mixin (đọc trước khi cấu hình)

| Thành phần | Giá trị / ý nghĩa |
|---|---|
| `_inherit` | `["tier.validation"]` (abstract model `tier.validation`) |
| `_tier_validation_manual_config` | `True` (default): **tự viết** nút/khối duyệt trong view. `False`: mixin tự chèn vào form qua `get_view` |
| `_state_field` / `_state_from` / `_state_to` | vd `state`, `["booking_confirmed"]`, `["departed"]` — duyệt kích hoạt khi **write** `state` đi từ `_state_from` sang `_state_to` |
| `_cancel_state` | `"cancelled"` — huỷ thì reset chuỗi duyệt |
| `review_ids` | o2m `tier.review` — các bước duyệt |
| `validation_status` | `no` / `waiting` / `pending` / `rejected` / `validated` |
| `can_review`, `reviewer_ids`, `need_validation` | để ẩn/hiện nút "Duyệt"/"Từ chối" |
| method | `request_validation()`, `validate_tier()`, `reject_tier()`, `restart_validation()` |
| hook override | `_validated_states()`, `_check_state_conditions(vals)`, `evaluate_tier(tier)`, `_get_under_validation_exceptions()` |

⚠️ **Đính chính so với `docs/01`:** không tồn tại method `_check_tier_validation` trong 17.0.4.1.0 (đã đọc `base_tier_validation/models/tier_validation.py`). Muốn chèn logic riêng thì override `_check_state_conditions`/`_validated_states`. Ghi vào `docs/09-open-questions.md` để RV cập nhật `docs/01`.

## 3. Duyệt đơn vào chuyến (⑥) — 2 cấp

```python
# addons/lab_chuyen/models/lab_chuyen.py
_tier_validation_manual_config = False   # mixin tự chèn nút vào form
_state_field = "state"
_state_from = ["booking_confirmed"]
_state_to = ["departed"]
_cancel_state = "cancelled"
```

```xml
<!-- addons/lab_chuyen/data/tier_definition_data.xml (noupdate="1") -->
<odoo noupdate="1">
  <record id="tier_chuyen_cap1" model="tier.definition">
    <field name="name">Duyệt vào chuyến — cấp 1: Trưởng nhóm CS</field>
    <field name="model_id" ref="model_lab_chuyen"/>
    <field name="review_type">group</field>
    <field name="reviewer_group_id" ref="lab_core.group_sale_admin"/>
    <field name="definition_domain">[]</field>
    <field name="sequence">10</field>
    <field name="approve_sequence" eval="True"/>
    <field name="notify_on_pending" eval="True"/>
  </record>
  <record id="tier_chuyen_cap2" model="tier.definition">
    <field name="name">Duyệt vào chuyến — cấp 2: Trưởng phòng</field>
    <field name="model_id" ref="model_lab_chuyen"/>
    <field name="review_type">group</field>
    <field name="reviewer_group_id" ref="lab_core.group_manager"/>
    <field name="definition_domain">[('loai_van_chuyen', '=', 'air')]</field>
    <field name="sequence">20</field>
    <field name="approve_sequence" eval="True"/>
    <field name="notify_on_pending" eval="True"/>
  </record>
</odoo>
```

`approve_sequence=True` = duyệt **tuần tự** (cấp 1 xong mới tới cấp 2). `definition_domain` là domain Python dạng chuỗi (OCA `literal_eval`): chỉ chuyến bay, hoặc `[('ti_le_tai', '&gt;', 100)]` cho chuyến quá tải (trong XML escape `>`). Thứ tự cấp: OCA sinh `tier.review` theo `tier.definition` xếp `sequence DESC` — **đừng đoán**, mở 1 chuyến, bấm "Yêu cầu duyệt", xem thứ tự 2 dòng duyệt rồi chỉnh.

## 4. Giảm tải (nhánh ②) — duyệt trên wizard, state chuyến không đổi

```python
class LabGiamTaiWizard(models.TransientModel):
    _name = "lab.giam.tai.wizard"
    _description = "Giảm tải chuyến"
    _inherit = ["tier.validation"]
    _tier_validation_manual_config = False
    _state_field = "state"
    _state_from = ["draft"]
    _state_to = ["to_approve"]
    _cancel_state = "cancel"

    chuyen_id = fields.Many2one("lab.chuyen", required=True, ondelete="cascade")
    line_ids = fields.Many2many("lab.chuyen.line", string="Đơn sẽ gỡ")
    ly_do = fields.Selection([("overload", "Quá tải"), ("carrier_change", "Hãng đổi lịch"),
                              ("customer_request", "Khách yêu cầu")], required=True)
    state = fields.Selection([("draft", "Nháp"), ("to_approve", "Chờ duyệt"),
                              ("done", "Đã gỡ"), ("cancel", "Đã huỷ")], default="draft")

    def action_gui_duyet(self):
        self.write({"state": "to_approve"})        # tier chặn ở đây nếu chưa có duyệt

    def action_apply(self):
        if self.validation_status != "validated":
            raise UserError(_("Cần được duyệt trước khi gỡ đơn khỏi chuyến."))
        self.line_ids.write({"state": "waitlist"})
        self.chuyen_id.message_post(body=_("Giảm tải %(n)s đơn. Lý do: %(ly_do)s. Người duyệt: %(who)s") % {
            "n": len(self.line_ids), "ly_do": dict(self._fields["ly_do"].selection)[self.ly_do],
            "who": ", ".join(self.review_ids.filtered(lambda r: r.status == "approved").mapped("done_by.name"))})
        self.write({"state": "done"})
```

⚠️ `tier.review` trỏ vào record tạm → bị autovacuum xoá. Vì vậy **bắt buộc** ghi tên người duyệt vào chatter của chuyến (như trên). Nếu RV yêu cầu audit vĩnh viễn: chuyển việc duyệt lên `lab.chuyen` bằng một field state phụ + tier riêng và ghi ADR — **không** tạo model mới ngoài ledger.

## 5. Khoá Debit (⑭) — tier trên `account.move`, phải giới hạn bằng domain

```python
class AccountMove(models.Model):
    _inherit = ["account.move", "tier.validation"]
    _tier_validation_manual_config = False
    _state_field = "state"          # account.move: draft / posted / cancel
    _state_from = ["draft"]
    _state_to = ["posted"]
```

```xml
<record id="tier_debit_duyet_khoa" model="tier.definition">
  <field name="name">Duyệt khoá Debit</field>
  <field name="model_id" ref="model_account_move"/>
  <field name="review_type">group</field>
  <field name="reviewer_group_id" ref="lab_core.group_manager"/>
  <field name="definition_domain">[('journal_id.code', '=', 'DEBIT')]</field>
  <field name="approve_sequence" eval="False"/>
</record>
```

`definition_domain` là **bắt buộc** ở đây: để `[]` thì mọi hoá đơn (kể cả journal bán hàng) đều phải duyệt → phá luồng ⑮ native. Chứng minh bằng test: hoá đơn journal khác post được mà không cần duyệt.

## 6. Test duyệt (Luật 7)

```python
class TestTierChuyen(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.manager = new_test_user(cls.env, login="mgr", groups="base.group_user,lab_core.group_manager")
        cls.sale_admin = new_test_user(cls.env, login="sa", groups="base.group_user,lab_core.group_sale_admin")
        cls.chuyen = cls._make_chuyen(state="booking_confirmed")

    def test_khong_duyet_thi_khong_khoi_hanh_duoc(self):
        with self.assertRaises(ValidationError):
            self.chuyen.write({"state": "departed"})
        self.assertNotEqual(self.chuyen.state, "departed")

    def test_duyet_du_2_cap_thi_di_duoc(self):
        self.chuyen.with_user(self.sale_admin).validate_tier()      # cấp 1
        self.assertNotEqual(self.chuyen.validation_status, "validated")
        self.chuyen.with_user(self.manager).validate_tier()         # cấp 2
        self.assertEqual(self.chuyen.validation_status, "validated")
        self.chuyen.action_depart()
        self.assertEqual(self.chuyen.state, "departed")
```

Mixin raise `ValidationError` (không phải `UserError`); người duyệt phải **thuộc nhóm reviewer** → dùng `with_user()`, `self.env` (admin) luôn pass và test vô nghĩa.

## Checklist nhanh

- [ ] Clone từ `OCA/server-ux` nhánh 17.0 (không phải `server-tools`).
- [ ] Model `_inherit` có `"tier.validation"`; `_state_from`/`_state_to` đúng cặp state nghiệp vụ.
- [ ] `tier.definition` có `noupdate="1"`, đủ 2 cấp với `approve_sequence=True`.
- [ ] Mọi definition đều có `definition_domain`; `[]` chỉ khi thật sự muốn áp cho tất cả.
- [ ] Đã kiểm thứ tự cấp thật bằng UI (không đoán theo `sequence`).
- [ ] Giảm tải: `validation_status == 'validated'` mới cho `action_apply`; tên người duyệt vào chatter.
- [ ] Có ≥ 3 test: chưa duyệt bị chặn / duyệt đủ 2 cấp đi được / từ chối bị chặn.
- [ ] Không có `_check_tier_validation` trong code (không tồn tại ở 17.0.4.1.0).
