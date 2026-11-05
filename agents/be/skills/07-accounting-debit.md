# 07 — Accounting & Debit (bước ⑬⑭⑮⑯)

## Dùng khi nào

Khi làm Debit, kiểm–khoá Debit, hoá đơn/thu tiền, đối soát, hoặc khi ai đó định tạo model `debit`.

---

## 1. Debit = `account.move`, không có model riêng

| Khái niệm nghiệp vụ | Đối tượng Odoo 17 |
|---|---|
| Debit (phiếu ghi nợ gửi khách) | `account.move` với `move_type = 'out_invoice'`, `journal_id` = journal **Debit** |
| Dòng cước | `account.move.line` (native) |
| Hoá đơn | cùng `account.move`, đổi `journal_id` |
| Thu tiền | `account.payment` (+ wizard `account.payment.register`) |
| Bồi thường / trả hàng | credit note = `account.move` (`move_type='out_refund'`) qua wizard `account.move.reversal` |

Field kế thừa trên `account.move` (L2, khai trong `lab_chuyen` theo `docs/02` §2.2): `lab_chuyen_id` (m2o `lab.chuyen`), `lab_khoi_luong` (float, kg tính cước).

## 2. Journal Debit (L1 — data XML, đừng hardcode id)

```xml
<record id="journal_debit" model="account.journal">
  <field name="name">Debit</field>
  <field name="code">DEBIT</field>          <!-- tối đa 5 ký tự -->
  <field name="type">sale</field>
  <field name="company_id" ref="base.main_company"/>
  <field name="restrict_mode_hash_table" eval="True"/>  <!-- chống sửa bút toán đã post -->
</record>
```

Mọi chỗ dùng: `self.env.company.lab_debit_journal_id` (field của `lab_core`) hoặc `self.env.ref('lab_chuyen.journal_debit')`. **Không** `search([('code','=','DEBIT')])` rải rác.

## 3. Wizard lập Debit: gom theo khách, nhưng để native sinh hoá đơn

Nguyên tắc native-first: **không tự dựng `account.move.line` bằng tay** khi đã có `sale.order`. Dùng `sale.order._create_invoices()` rồi chuyển sang journal Debit.

```python
# addons/lab_chuyen/wizard/lab_lap_debit_wizard.py
from odoo import _, fields, models
from odoo.exceptions import UserError


class LabLapDebitWizard(models.TransientModel):
    _name = "lab.lap.debit.wizard"
    _description = "Lập Debit theo chuyến"

    chuyen_id = fields.Many2one("lab.chuyen", required=True, ondelete="cascade")
    chi_lay_da_giao = fields.Boolean("Chỉ lấy đơn đã giao", default=True)

    def action_lap_debit(self):
        self.ensure_one()
        chuyen = self.chuyen_id
        lines = chuyen.line_ids.filtered(lambda l: l.state == "booked")
        if not lines:
            raise UserError(_("Chuyến %s chưa có đơn nào để lập Debit.") % chuyen.name)
        debit_journal = chuyen.company_id.lab_debit_journal_id
        if not debit_journal:
            raise UserError(_("Chưa cấu hình journal Debit cho công ty."))

        moves = self.env["account.move"]
        for partner, partner_lines in lines.grouped("partner_id").items():
            orders = partner_lines.sale_order_id.filtered(lambda so: so.state == "sale")
            if not orders:
                raise UserError(_("Khách %s chưa có đơn đã xác nhận.") % partner.display_name)
            invoices = orders._create_invoices()          # NATIVE: sinh hoá đơn nháp
            invoices.write({
                "journal_id": debit_journal.id,           # Debit là 1 journal riêng
                "lab_chuyen_id": chuyen.id,
                "invoice_date": fields.Date.context_today(self),
            })
            moves |= invoices
        return {
            "name": _("Debit vừa lập"),
            "type": "ir.actions.act_window",
            "res_model": "account.move",
            "view_mode": "tree,form",
            "domain": [("id", "in", moves.ids)],
        }
```

Quy tắc: **1 Debit / 1 khách / 1 chuyến** (yêu cầu nghiệp vụ). `_create_invoices()` tôn trọng `invoice_policy` của sản phẩm; nếu chính sách là *delivery* thì phải có picking `done` trước — đó là lý do bước ⑦ đứng trước ⑬ trong luồng.

## 4. Khoá Debit (⑭): 3 lớp, dùng đúng tên field 17.0

| Lớp | Công cụ | Hiệu lực |
|---|---|---|
| Quy trình | tier validation trên `account.move` (xem `08-tier-validation.md`) | phải được duyệt mới post |
| Bút toán | `account.journal.restrict_mode_hash_table = True` | chặn sửa nội dung đã post (hash chain) |
| Kỳ kế toán | `res.company.period_lock_date`, `fiscalyear_lock_date`, `tax_lock_date` | chặn sửa bút toán trong kỳ đã khoá |

**17.0 không có `hard_lock_date`** (đó là 18.0). Không tồn tại `move.lock_date` để "khoá 1 chứng từ" → muốn khoá 1 Debit thì dùng 3 lớp trên + tier.

```python
def action_lock_debit(self):
    for move in self:
        if move.journal_id != move.company_id.lab_debit_journal_id:
            raise UserError(_("Chỉ áp dụng cho bút toán journal Debit."))
        move.action_post()                     # native: ghi sổ
        move.message_post(body=_("Đã khoá Debit (đã ghi sổ)."))
    return True
```

## 5. Mở khoá: phải có lý do + chatter (không có nút "unlock" thần kỳ)

```python
def action_unlock_debit(self, ly_do):
    self.ensure_one()
    if not ly_do:
        raise UserError(_("Mở khoá Debit phải nhập lý do."))
    if self.company_id.fiscalyear_lock_date and self.date <= self.company_id.fiscalyear_lock_date:
        raise UserError(_("Kỳ kế toán đã khoá — không thể mở khoá. Liên hệ kế toán trưởng."))
    self.button_draft()                        # native; sẽ raise nếu journal bật hash
    self.message_post(body=_("MỞ KHOÁ Debit. Lý do: %s") % ly_do)
    self.restart_validation()                  # reset chuỗi duyệt (OCA) → phải duyệt lại
    return True
```

Ba sự thật: (a) `button_draft()` raise `UserError` nếu bút toán đã hash hoặc kỳ đã khoá — **đúng như mong muốn**, đừng lách; (b) lý do nằm ở chatter, không cần field mới; (c) sau khi mở khoá, phải duyệt lại trước khi post lại. Chỉ `lab_core.group_manager` có nút này (menu/nút `groups=`, và ACL không cho write `account.move`).

## 6. Credit note (bồi thường — nhánh ④⑥)

```python
def action_credit_note_boi_thuong(self, ly_do):
    self.ensure_one()
    reversal = self.env["account.move.reversal"].with_context(
        active_model="account.move", active_ids=self.ids).create({
            "move_ids": [(6, 0, self.ids)],
            "reason": ly_do,
            "journal_id": self.journal_id.id,
            "refund_method": "refund",
        })
    res = reversal.reverse_moves()      # verify trong addons/account/wizard/account_move_reversal.py 17.0
    return res
```

Không tự tạo `account.move` đối ứng bằng tay, không sửa số tiền của Debit đã gửi khách.

## 7. Thu tiền & đối soát (⑮⑯ — 100% native, 0 dòng code)

```python
# Từ UI: Invoice -> Register Payment. Từ code (dùng lại wizard native):
register = self.env["account.payment.register"].with_context(
    active_model="account.move", active_ids=moves.ids
).create({"payment_date": fields.Date.context_today(self),
          "journal_id": bank_journal.id, "amount": moves.amount_residual})
payments = register._create_payments()   # verify trong addons/account/wizard/account_payment_register.py 17.0
```

- Khoá tháng: đặt `period_lock_date` = cuối tháng trước (giao diện Settings → Accounting → Lock Dates). Nhắc việc tự động: `ir.cron` native (`docs/02` §7), không viết job riêng.
- Đối soát ngân hàng: dùng `account.bank.statement.line` + `account.reconcile.model` (L1).

## 8. `l10n_vn` — những gì có và không có

- Có: chart tài khoản VN, VND, thuế GTGT 8%/10%, tên tài khoản tiếng Việt. Cài `l10n_vn` và chọn chart ở lúc tạo DB (**không** đổi chart sau khi có bút toán).
- Không có: kết nối nhà cung cấp **hoá đơn điện tử VN** (ngoài phạm vi — `docs/00` §9.3). Dữ liệu trên `account.move` đã đủ để tích hợp sau.
- Số tiền: giữ VND cho nội địa, USD cho cước quốc tế → bật multi-currency; **không** tự quy đổi/rounding trong code, để `account` làm (`currency_id._convert`).
- Nhãn "Debit" là thuật ngữ nghiệp vụ Việt giữ nguyên tiếng Anh (glossary `docs/00` §10) — không dịch thành "Nợ".

## Checklist nhanh

- [ ] Không có model `debit`; mọi Debit là `account.move` + journal `DEBIT`.
- [ ] Journal lấy từ `company.lab_debit_journal_id`/`ref`, không hardcode id.
- [ ] Hoá đơn sinh bằng `sale.order._create_invoices()`, không dựng `account.move.line` tay.
- [ ] Không dùng `hard_lock_date`; dùng `period_lock_date`/`fiscalyear_lock_date`/`tax_lock_date`.
- [ ] Mở khoá Debit có: kiểm kỳ khoá → `button_draft()` → `message_post(lý do)` → `restart_validation()`.
- [ ] Credit note qua `account.move.reversal`, không sửa tay bút toán đã post.
- [ ] Có test `test_debit_lock.py`: đã khoá thì không sửa; mở khoá thiếu lý do → `UserError`.
- [ ] Mọi chuỗi lỗi bọc `_()` + có bản dịch trong `i18n/vi.po`.
