# -*- coding: utf-8 -*-
from odoo.exceptions import AccessError
from odoo.tests import TransactionCase, new_test_user, tagged


@tagged("post_install", "-at_install")
class TestLayHang(TransactionCase):
    """P0 — smoke test lab.yeu.cau.lay.hang + ir.rule của tài xế.

    Kiểm đúng 2 thứ đã làm ở P0:
    1. tạo được yêu cầu và có số chứng từ theo sequence;
    2. tài xế A KHÔNG thấy yêu cầu của tài xế B (ir.rule, không phải has_group).
    """

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Lấy Hàng"})

        cls.user_a = new_test_user(
            cls.env,
            login="tx_a",
            password="tx_a",
            groups="base.group_portal,lab_core.group_driver",
        )
        cls.user_b = new_test_user(
            cls.env,
            login="tx_b",
            password="tx_b",
            groups="base.group_portal,lab_core.group_driver",
        )
        cls.driver_a = cls.env["hr.employee"].create(
            {"name": "Tài xế A", "user_id": cls.user_a.id}
        )
        cls.driver_b = cls.env["hr.employee"].create(
            {"name": "Tài xế B", "user_id": cls.user_b.id}
        )
        cls.yeu_cau_a = cls.env["lab.yeu.cau.lay.hang"].create(
            {"partner_id": cls.shop.id, "driver_id": cls.driver_a.id}
        )
        cls.yeu_cau_b = cls.env["lab.yeu.cau.lay.hang"].create(
            {"partner_id": cls.shop.id, "driver_id": cls.driver_b.id}
        )

    def test_so_yeu_cau_theo_sequence(self):
        self.assertTrue(self.yeu_cau_a.name.startswith("LH/"), self.yeu_cau_a.name)

    def test_mac_dinh_la_moi(self):
        self.assertEqual(self.yeu_cau_a.state, "new")

    def test_tai_xe_chi_thay_yeu_cau_cua_minh(self):
        YeuCau = self.env["lab.yeu.cau.lay.hang"]
        self.assertEqual(YeuCau.with_user(self.user_a).search([]).ids, [self.yeu_cau_a.id])
        self.assertEqual(YeuCau.with_user(self.user_b).search([]).ids, [self.yeu_cau_b.id])

    def test_tai_xe_khong_doc_duoc_yeu_cau_cua_nguoi_khac(self):
        """Case BỊ CHẶN: search đã lọc, nhưng read trực tiếp phải raise AccessError."""
        with self.assertRaises(AccessError):
            self.env["lab.yeu.cau.lay.hang"].with_user(self.user_a).browse(
                self.yeu_cau_b.id
            ).read(["name"])

    def test_anh_dinh_kem_duoc_vao_yeu_cau(self):
        dinh_kem = self.env["ir.attachment"].create({
            "name": "anh-kien-1.png",
            "datas": "aGVsbG8=",  # "hello"
            "lab_lay_hang_id": self.yeu_cau_a.id,
        })
        self.assertIn(dinh_kem, self.yeu_cau_a.anh_ids)
