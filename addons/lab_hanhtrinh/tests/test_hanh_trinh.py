# -*- coding: utf-8 -*-
from odoo.tests import TransactionCase, tagged


@tagged("post_install", "-at_install")
class TestHanhTrinh(TransactionCase):
    """P0 — smoke test lab.hanh.trinh + lab.hanh.trinh.line.

    Kiểm đúng những gì ĐÃ làm ở P0: tạo hành trình theo chuyến, có số chứng từ,
    stage mặc định là nháp, và timeline (line) gắn đúng vào hành trình.
    Transition của hành trình (⑨⑩⑪) là P4 — chưa kiểm ở đây.
    """

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.chuyen = cls.env["lab.chuyen"].create({"loai_van_chuyen": "air"})
        cls.hanh_trinh = cls.env["lab.hanh.trinh"].create({
            "chuyen_id": cls.chuyen.id,
            "loai_chung_tu": "awb",
        })

    def test_so_hanh_trinh_theo_sequence(self):
        self.assertTrue(self.hanh_trinh.name.startswith("HT/"), self.hanh_trinh.name)

    def test_stage_mac_dinh_la_nhap(self):
        self.assertEqual(self.hanh_trinh.stage, "draft")

    def test_gan_dung_chuyen(self):
        self.assertEqual(self.hanh_trinh.chuyen_id, self.chuyen)

    def test_them_moc_hanh_trinh(self):
        moc = self.env["lab.hanh.trinh.line"].create({
            "hanhtrinh_id": self.hanh_trinh.id,
            "moc": "Đã lên máy bay",
            "dia_diem": "Sân bay Tân Sơn Nhất",
        })
        self.assertIn(moc, self.hanh_trinh.line_ids)
        self.assertEqual(moc.nguoi_cap_nhat_id, self.env.user)
