# SKILL — REA Model (Resources · Events · Agents)

## Dùng khi nào
Khi thiết kế/sửa bất kỳ luồng nào **có sinh ra tiền hoặc đổi quyền sở hữu hàng** (bước ③⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯ và nhánh ④⑥).
Sếp liệt kê **REA** là một tiêu chí chấm — skill này biến nó thành việc kiểm tra được.

---

## 1. REA là gì (và không phải gì)

**REA = Resources – Events – Agents.** Một mô hình kế toán/ERP nói rằng: mỗi **sự kiện kinh tế** (economic event) phải ghi lại:
- **Resource** — tài nguyên bị biến đổi (hàng, tiền, sức chứa)
- **Event** — sự kiện (nhận hàng, xuất kho, ghi nợ, thu tiền)
- **Agent** — người/nhóm gây ra hoặc chịu ảnh hưởng (kho, kế toán, khách, đại lý)

Hệ quả cho dự án: **không có "sự kiện ngoài sổ"**. Nếu nghiệp vụ xảy ra mà Odoo không sinh chứng từ → **lỗi thiết kế**, không phải "tính năng thiếu".

**REA KHÔNG phải:**
- Không phải yêu cầu tạo model `resource`/`event`/`agent` (đó là viết lại Odoo — Luật 1).
- Không phải bắt chước REA trong database; Odoo **đã** là một hệ REA ngầm (chứng từ + partner + user).
- Không phải lý do để bỏ qua `account`.

---

## 2. Bảng REA của dự án (nguồn chuẩn: `docs/02` §11)

| Sự kiện (Event) | Tài nguyên (Resource) | Tác nhân (Agent) | Chứng từ Odoo bắt buộc | Bước |
|---|---|---|---|---|
| Nhận hàng để gửi | Hàng của shop (tồn kho) | Kho, shop | `stock.picking` (Receipt, done) | ③ |
| Nhận hàng chưa rõ khách | Hàng trong KHO CHỜ | Kho | `stock.picking` (`lab_is_cho_khach=True`) | N① |
| Đóng gói | Kiện hàng | Kho | `stock.quant.package` | ④ |
| Gom vào chuyến | Sức chứa chuyến | Kho/chứng từ | `lab.chuyen.line` | ⑤⑥ |
| Gỡ đơn khỏi chuyến | Sức chứa chuyến (trả lại) | Manager duyệt | `lab.chuyen.line.state='waitlist'` + lý do | N② |
| Xuất kho | Hàng đã gửi | Kho | `stock.picking` (Delivery, done) | ⑦ |
| Chuyển kho bay ↔ tàu | Hàng (đổi vị trí) | Manager duyệt | `stock.picking` (Internal) | N⑤ |
| Thiếu kiện khi xuất | Hàng còn lại | Kho | `stock.picking` backorder | N④ |
| Hàng đến đại lý / giao khách | Quyền sở hữu hàng | Đại lý | `lab.hanh.trinh` (stage + POD) | ⑩⑪ |
| Cước phát sinh | Dịch vụ vận chuyển | Kế toán, shop | `sale.order.line` | ⑫ |
| **Ghi nợ khách** | **Khoản phải thu** | Kế toán | **`account.move` (Debit)** | ⑬ |
| Khoá Debit | Quyền sửa chứng từ | Manager | tier validation + `inalterable_hash` | ⑭ |
| Thu tiền | Tiền mặt/ngân hàng | Kế toán, ngân hàng | `account.payment` | ⑮ |
| Đối soát · khoá tháng | Kỳ kế toán | Kế toán trưởng | `res.company.period_lock_date` | ⑯ |
| Bồi thường / trả hàng | Hàng + khoản phải thu | Kế toán | `account.move` (credit note) + return picking | N⑥ |

---

## 3. Ba câu hỏi kiểm tra (chạy mỗi khi thêm/sửa một state)

1. **State này có làm biến đổi tài nguyên hoặc tiền không?** → nếu có, **chứng từ nào** ghi nhận nó?
2. **Ai là tác nhân?** → có ghi được `user_id`/`partner_id`/`received_by` không?
3. **Nếu chỉ có state mà không có chứng từ**, kế toán có đối soát được vào cuối tháng không?

Nếu (3) = "không" → thiếu chứng từ → **P0 bug**.

---

## 4. Cách kiểm chứng bằng code

`tests/test_rea_completeness.py`:

```python
from odoo.tests import TransactionCase, tagged

@tagged('post_install', '-at_install', 'rea')
class TestREACompleteness(TransactionCase):
    """Mỗi event trong bảng REA phải sinh chứng từ tương ứng."""

    def test_receipt_creates_done_picking(self):
        picking = self._make_receipt()
        picking.button_validate()
        self.assertEqual(picking.state, 'done')
        self.assertTrue(picking.move_ids, "Nhận hàng phải có stock.move")

    def test_chuyen_line_creates_capacity_change(self):
        chuyen = self._make_chuyen(capacity=100.0)
        before = chuyen.tai_trong_da_dung_kg
        self._book_order(chuyen, kg=40.0)
        self.assertEqual(chuyen.tai_trong_da_dung_kg, before + 40.0)

    def test_debit_is_account_move(self):
        debit = self._create_debit_from_chuyen()
        self.assertEqual(debit._name, 'account.move')
        self.assertEqual(debit.move_type, 'out_invoice')
        self.assertEqual(debit.journal_id.type, 'sale')

    def test_locked_debit_cannot_be_modified(self):
        debit = self._posted_debit(locked=True)
        with self.assertRaises(UserError):
            debit.write({'invoice_date': fields.Date.today()})

    # ... 1 test cho mỗi dòng của bảng §2
```

**Nguyên tắc:** test không kiểm tra "code chạy", mà kiểm tra **"không có event nào ngoài sổ"**.

---

## 5. Vi phạm REA trông như thế nào (nhận diện nhanh)

| Triệu chứng | Vi phạm |
|---|---|
| Có bảng `lab_debit`, `lab_cuoc`, `lab_doanhthu` | Viết lại tầng kế toán |
| `lab.hanh.trinh.stage = 'delivered'` mà không có POD/attachment | Event không có bằng chứng |
| Gỡ đơn khỏi chuyến bằng `unlink()` | Event không có chứng từ → mất vết |
| Sửa `stock.quant` trực tiếp để "chuyển kho" | Không có chứng từ → tồn kho vô căn cứ |
| Cước tính bằng field trên `lab.chuyen` thay vì `sale.order.line` | Không vào được `account.move` → không có bút toán |
| Khoá Debit bằng `readonly` trong view | Không phải kiểm soát ở tầng dữ liệu (mở qua API/RPC vẫn sửa được) |

---

## 6. Liên hệ với "chuỗi ba số" của kế toán

Mỗi chuyến phải trả lời được 3 câu **cùng một con số**:

```
kg trên stock.move.line   ==   kg trên lab.chuyen.line   ==   kg trên account.move.line
      (cân thực tế)                 (cước tính)                  (Debit gửi khách)
```

Nếu lệch → có 3 khả năng: (a) tính lại cước sai, (b) gỡ/gán đơn không cập nhật Debit, (c) làm tròn khác nhau. Cả 3 đều **P1** trở lên.

**Test bổ sung:** `test_weight_consistency_across_documents`.

## Checklist nhanh
- [ ] Mỗi state mới đã trả lời 3 câu hỏi §3
- [ ] Event nào cũng có chứng từ Odoo (không model tiền riêng)
- [ ] `test_rea_completeness.py` có ≥ 1 test/dòng bảng §2
- [ ] Không có `unlink()` làm mất vết nghiệp vụ
- [ ] Khoá Debit ở tầng dữ liệu (không chỉ view)
- [ ] Chuỗi kg của 3 chứng từ khớp nhau cùng kỳ
