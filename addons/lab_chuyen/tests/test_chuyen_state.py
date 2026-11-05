# -*- coding: utf-8 -*-
from unittest.mock import patch

from odoo.addons.mail.models.mail_thread import MailThread
from odoo.exceptions import UserError
from odoo.tests import TransactionCase, new_test_user, tagged

#: Câu lỗi ĐÃ ĐO trong E2E thật (user demo không có email người gửi).
LOI_GUI_MAIL = (
    "Unable to send message, please configure the sender's email address."
)


@tagged("post_install", "-at_install")
class TestChuyenState(TransactionCase):
    """P0 — smoke test state machine + tải trọng của lab.chuyen (docs/02 §3.1).

    Chưa kiểm tier validation (⑥) và giảm tải (nhánh ②) — hai phần đó là P2 và
    chưa được cài ở P0. Ở đây chỉ khoá lại hành vi ĐÃ làm: transition hợp lệ,
    transition sai bị chặn, và compute tải trọng chỉ tính dòng đã vào chuyến.

    Wave B: đã bật tier validation cho cặp `booking_confirmed -> departed`, nên
    test nào cần state `departed` phải đi qua 2 cấp duyệt (xem test_chuyen_tier.py).
    """

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.warehouse = cls.env["stock.warehouse"].search([], limit=1)
        cls.partner = cls.env["res.partner"].create({"name": "Shop Kiểm Thử"})
        cls.sale_admin = new_test_user(
            cls.env,
            login="sa_state",
            password="sa_state",
            groups="base.group_user,lab_core.group_sale_admin",
        )
        cls.manager = new_test_user(
            cls.env,
            login="mgr_state",
            password="mgr_state",
            groups="base.group_user,lab_core.group_manager",
        )
        cls.chuyen = cls.env["lab.chuyen"].create({
            "loai_van_chuyen": "air",
            "kho_xuat_id": cls.warehouse.id,
            "tai_trong_max_kg": 1000.0,
        })
        cls.sale_order = cls.env["sale.order"].create({"partner_id": cls.partner.id})

    def test_so_chuyen_dung_sequence_air(self):
        self.assertTrue(self.chuyen.name.startswith("CH/AIR/"), self.chuyen.name)

    def test_so_chuyen_dung_sequence_sea(self):
        chuyen_bien = self.env["lab.chuyen"].create({"loai_van_chuyen": "sea"})
        self.assertTrue(chuyen_bien.name.startswith("CH/SEA/"), chuyen_bien.name)

    def test_draft_mo_chuyen(self):
        self.chuyen.action_open()
        self.assertEqual(self.chuyen.state, "open")

    def test_khong_cho_khoi_hanh_tu_nhap(self):
        with self.assertRaises(UserError):
            self.chuyen.action_depart()

    def test_huy_chuyen_tu_nhap_duoc(self):
        self.chuyen.action_cancel()
        self.assertEqual(self.chuyen.state, "cancelled")

    def test_khong_huy_duoc_chuyen_da_khoi_hanh(self):
        self.chuyen.action_open()
        self.chuyen.action_confirm_booking()
        # ⑥: khởi hành phải qua duyệt 2 cấp (tier validation, Wave B).
        # Đọc lại từ DB sau mỗi bước duyệt: cache o2m review_ids không tự xoá
        # (inverse là Many2oneReference) — xem ghi chú đầu test_chuyen_tier.py.
        self.chuyen.request_validation()
        self.chuyen.invalidate_recordset()
        self.env["lab.chuyen"].with_user(self.sale_admin).browse(
            self.chuyen.id
        ).validate_tier()
        self.chuyen.invalidate_recordset()
        self.env["lab.chuyen"].with_user(self.manager).browse(
            self.chuyen.id
        ).validate_tier()
        self.chuyen.invalidate_recordset()
        chuyen = self.env["lab.chuyen"].browse(self.chuyen.id)
        self.assertEqual(
            chuyen.validation_status, "validated", "phải duyệt đủ 2 cấp trước khi đi"
        )

        chuyen.action_depart()
        chuyen = self.env["lab.chuyen"].browse(self.chuyen.id)
        self.assertEqual(chuyen.state, "departed")
        with self.assertRaises(UserError):
            chuyen.action_cancel()

    def test_tai_trong_chi_tinh_dong_da_vao_chuyen(self):
        dong = self.env["lab.chuyen.line"].create({
            "chuyen_id": self.chuyen.id,
            "sale_order_id": self.sale_order.id,
            "khoi_luong_kg": 250.0,
            "the_tich_cbm": 1.5,
        })
        self.assertEqual(self.chuyen.tai_trong_da_dung_kg, 250.0)
        self.assertEqual(self.chuyen.the_tich_da_dung_cbm, 1.5)

        # Nhánh ②: dòng bị gỡ sang waitlist -> không còn chiếm tải trọng
        dong.write({"state": "waitlist", "ly_do_go": "Chuyến quá tải"})
        self.assertEqual(self.chuyen.tai_trong_da_dung_kg, 0.0)

    # --------------------------------------------- hồi quy: chatter lỗi (E2E)
    def test_loi_gui_chatter_khong_lam_mat_giao_dich(self):
        """Override ``message_post`` của lab.chuyen: mail lỗi -> state vẫn đổi.

        Chốt chặn nằm ở ``LabChuyen.message_post`` (models/lab_chuyen.py), nên
        test patch ``mail.thread.MailThread.message_post`` — bản GỐC mà override
        gọi vào; patch chính override sẽ tự vô hiệu hoá thứ cần kiểm.
        Đường này che cả 7 ``action_*`` lẫn các ``message_post`` do OCA
        ``base_tier_validation`` gọi khi duyệt cấp (bước ⑥).
        """
        chuyen = self.env["lab.chuyen"].create(
            {"loai_van_chuyen": "air", "kho_xuat_id": self.warehouse.id}
        )
        with patch.object(
            MailThread, "message_post", side_effect=UserError(LOI_GUI_MAIL)
        ) as gui_mail:
            with self.assertLogs(
                "odoo.addons.lab_chuyen.models.lab_chuyen", level="WARNING"
            ) as nhat_ky:
                chuyen.action_open()  # KHÔNG được raise

        self.assertTrue(
            gui_mail.called, "message_post gốc PHẢI được gọi rồi mới bị chặn"
        )
        self.assertEqual(chuyen.state, "open", "chatter lỗi không được rollback state")
        self.assertTrue(
            any("Không ghi được chatter" in dong for dong in nhat_ky.output),
            nhat_ky.output,
        )
        # Lỗi NGHIỆP VỤ vẫn raise như cũ (không bị nuốt).
        with self.assertRaises(UserError):
            chuyen.action_open()
