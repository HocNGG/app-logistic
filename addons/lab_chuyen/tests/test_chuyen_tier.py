# -*- coding: utf-8 -*-
"""Test tier validation — bước ⑥ (duyệt vào chuyến) và ⑭ (duyệt khoá Debit).

Nguồn: docs/02 §3.1 + agents/be/skills/08-tier-validation.md §6.

Ba nguyên tắc của file này (rút ra sau khi chạy thật 2026-10-28):
1. **Xoá cache rồi đọc lại từ DB** sau mỗi bước (`rec.invalidate_recordset()`) —
   `review_ids` là One2many có inverse là `Many2oneReference`
   (`tier.review.res_id`), cache o2m này không tự xoá khi `tier.review` được
   tạo/ghi bằng một env khác (`with_user`), nên đọc lại là bắt buộc để test
   không "nhìn thấy dữ liệu cũ".
2. **Kiểm bằng SEARCH trên `tier.review`** thay vì `record.review_ids` — cùng lý do.
3. **Kiểm bằng hành vi** (`action_depart` được/không được) chứ không chỉ field:
   ý định nghiệp vụ là "chưa duyệt đủ 2 cấp thì KHÔNG được khởi hành".
"""

from odoo.exceptions import ValidationError
from odoo.tests import TransactionCase, new_test_user, tagged


@tagged("post_install", "-at_install")
class TestChuyenTier(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.sale_admin = new_test_user(
            cls.env,
            login="sa_tier",
            password="sa_tier",
            groups="base.group_user,lab_core.group_sale_admin",
        )
        cls.manager = new_test_user(
            cls.env,
            login="mgr_tier",
            password="mgr_tier",
            groups="base.group_user,lab_core.group_manager",
        )

    # ------------------------------------------------------------------ helpers
    def _tao_chuyen_booking_confirmed(self):
        chuyen = self.env["lab.chuyen"].create(
            {"loai_van_chuyen": "air", "tai_trong_max_kg": 100.0}
        )
        chuyen.action_open()
        chuyen.action_confirm_booking()
        self.assertEqual(chuyen.state, "booking_confirmed")
        return chuyen

    def _reviews(self, model, res_id):
        """Đọc tier.review bằng search — không dùng o2m để khỏi dính cache."""
        return self.env["tier.review"].search(
            [("model", "=", model), ("res_id", "=", res_id)]
        )

    def _doc_lai(self, model, rec):
        """Xoá cache của record rồi đọc lại từ DB.

        Vì sao bắt buộc: `review_ids` là One2many có inverse là
        `Many2oneReference` (`tier.review.res_id`) — cache o2m không tự xoá khi
        `tier.review` được tạo/ghi bằng một env khác (`with_user`). OCA cũng phải
        làm đúng vậy trong test của họ:
        ``addons/oca/_repos/OCA-server-ux/base_tier_validation_formula/tests/test_tier_validation.py:93``
        """
        rec.invalidate_recordset()
        return self.env[model].browse(rec.id)

    def _duyet(self, model, rec, user):
        """Một người duyệt bấm Duyệt, rồi đọc lại record từ DB."""
        rec.invalidate_recordset()
        self.env[model].with_user(user).browse(rec.id).validate_tier()
        return self._doc_lai(model, rec)

    # ---------------------------------------------------------------- ⑥ chuyến
    def test_du_lieu_2_cap_duyet_ton_tai(self):
        """Thiếu tier.definition thì mọi test duyệt bên dưới là vô nghĩa."""
        dinh_nghia = self.env["tier.definition"].search([("model", "=", "lab.chuyen")])
        self.assertGreaterEqual(
            len(dinh_nghia), 2, "phải có 2 cấp duyệt cho lab.chuyen (data XML)"
        )
        self.assertTrue(
            any(d.approve_sequence for d in dinh_nghia), "phải duyệt tuần tự"
        )

    def test_chua_duyet_thi_khong_khoi_hanh_duoc(self):
        chuyen = self._tao_chuyen_booking_confirmed()
        chuyen.request_validation()
        chuyen = self._doc_lai("lab.chuyen", chuyen)
        self.assertTrue(
            self._reviews("lab.chuyen", chuyen.id), "phải sinh chuỗi duyệt 2 cấp"
        )

        with self.assertRaises(ValidationError):
            chuyen.action_depart()
        chuyen = self._doc_lai("lab.chuyen", chuyen)
        self.assertNotEqual(
            chuyen.state, "departed", "chưa duyệt thì KHÔNG được khởi hành"
        )

    def test_duyet_du_hai_cap_thi_khoi_hanh_duoc(self):
        chuyen = self._tao_chuyen_booking_confirmed()
        chuyen.request_validation()
        chuyen = self._doc_lai("lab.chuyen", chuyen)
        self.assertTrue(self._reviews("lab.chuyen", chuyen.id))

        chuyen = self._duyet("lab.chuyen", chuyen, self.sale_admin)  # cấp 1
        self.assertEqual(
            self._reviews("lab.chuyen", chuyen.id).sorted("sequence")[0].status,
            "approved",
            "cấp 1 (Trưởng nhóm CS) phải duyệt được — kiểm tra reviewer_group_id",
        )
        self.assertNotEqual(
            chuyen.validation_status, "validated", "duyệt cấp 1 là CHƯA đủ"
        )

        chuyen = self._duyet("lab.chuyen", chuyen, self.manager)  # cấp 2
        self.assertEqual(
            self._reviews("lab.chuyen", chuyen.id).mapped("status"),
            ["approved", "approved"],
            "cả 2 cấp phải ở trạng thái approved",
        )
        self.assertEqual(
            chuyen.validation_status, "validated", "duyệt đủ 2 cấp phải validated"
        )

        chuyen.action_depart()
        chuyen = self._doc_lai("lab.chuyen", chuyen)
        self.assertEqual(chuyen.state, "departed")

    def test_tu_choi_thi_khong_khoi_hanh_duoc(self):
        chuyen = self._tao_chuyen_booking_confirmed()
        chuyen.request_validation()
        chuyen.invalidate_recordset()
        self.env["lab.chuyen"].with_user(self.sale_admin).browse(chuyen.id).reject_tier()
        chuyen = self._doc_lai("lab.chuyen", chuyen)
        self.assertEqual(chuyen.validation_status, "rejected")

        with self.assertRaises(ValidationError):
            chuyen.action_depart()
        chuyen = self._doc_lai("lab.chuyen", chuyen)
        self.assertNotEqual(chuyen.state, "departed")

    # ---------------------------------------------------------------- ⑭ debit
    def _tao_but_toan(self, journal, account):
        return self.env["account.move"].create(
            {
                "move_type": "entry",
                "journal_id": journal.id,
                "line_ids": [
                    (
                        0,
                        0,
                        {
                            "name": "Kiểm thử duyệt Debit",
                            "account_id": account.id,
                            "debit": 100.0,
                            "credit": 0.0,
                        },
                    ),
                    (
                        0,
                        0,
                        {
                            "name": "Kiểm thử duyệt Debit",
                            "account_id": account.id,
                            "debit": 0.0,
                            "credit": 100.0,
                        },
                    ),
                ],
            }
        )

    def test_duyet_khoa_debit_chi_ap_cho_journal_code_debit(self):
        account = self.env["account.account"].search([], limit=1)
        if not account:
            self.skipTest("DB chưa có tài khoản kế toán (l10n chưa cài)")

        Journal = self.env["account.journal"]
        # Dùng lại nếu DB đã có (tránh lỗi unique code khi chạy lại nhiều lần);
        # code journal giới hạn 5 ký tự nên không thêm hậu tố ngẫu nhiên được.
        journal_debit = Journal.search([("code", "=", "DEBIT")], limit=1)
        if not journal_debit:
            journal_debit = Journal.create(
                {"name": "Debit kiểm thử", "code": "DEBIT", "type": "general"}
            )
        journal_thuong = Journal.search([("code", "=", "MISCT")], limit=1)
        if not journal_thuong:
            journal_thuong = Journal.create(
                {"name": "Sổ nhật ký kiểm thử", "code": "MISCT", "type": "general"}
            )

        phieu_debit = self._tao_but_toan(journal_debit, account)

        # ⑭: chặn ĐÚNG cặp state mà tier canh (draft -> posted) trên journal DEBIT.
        # Cố ý write trực tiếp thay vì action_post: test này kiểm CỔNG DUYỆT, còn
        # action_post còn ghi name/sequence — việc của account, không phải của tier.
        with self.assertRaises(ValidationError):
            phieu_debit.write({"state": "posted"})
        phieu_debit = self._doc_lai("account.move", phieu_debit)
        self.assertEqual(phieu_debit.state, "draft")

        # BẮT BUỘC: `write(state=posted)` bị tier chặn chỉ NÉM LỖI, KHÔNG tự tạo
        # tier.review. Review chỉ sinh khi gọi `request_validation()` (xem OCA
        # tier_validation.py::request_validation). Thiếu dòng này thì assert
        # `_reviews(...)` luôn rỗng — đã gặp thật 2026-10-28.
        phieu_debit.request_validation()
        phieu_debit = self._doc_lai("account.move", phieu_debit)

        self.assertTrue(
            self._reviews("account.move", phieu_debit.id),
            "phải sinh chuỗi duyệt cho Debit",
        )

        phieu_debit = self._duyet("account.move", phieu_debit, self.manager)
        self.assertEqual(phieu_debit.validation_status, "validated")
        # Cổng duyệt đã mở. Kiểm đúng việc của tier: write `state=posted` đi qua
        # (không gọi action_post vì nó còn ghi name/sequence — việc của account,
        # không phải việc của cổng duyệt cần khoá ở đây).
        phieu_debit.write({"state": "posted"})
        self.assertEqual(phieu_debit.state, "posted")

        # Không phá luồng ⑮: journal khác post được NGAY, không cần duyệt
        phieu_thuong = self._tao_but_toan(journal_thuong, account)
        phieu_thuong.action_post()
        self.assertEqual(phieu_thuong.state, "posted")
