# -*- coding: utf-8 -*-
"""HttpCase cho endpoint tra cứu CÔNG KHAI `/lab/api/v1/track/<access_token>`.

Điều phải chứng minh (docs/02 §6.2 + agents/review/skills/03 §7):
* không cần đăng nhập, chỉ cần token;
* **chỉ** trả field whitelist: không giá, không đối tác nội bộ, không tài xế,
  không id bản ghi;
* giờ hẹn trả dạng ``HH:mm`` (OQ-23), không trả ``expectedPackages`` khi không
  có nguồn thật (OQ-22);
* token sai -> 404 (không tiết lộ tồn tại hay không).
"""

from odoo import fields
from odoo.tests import HttpCase, tagged


@tagged("post_install", "-at_install")
class TestTrackApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Track Kiểm Thử"})
        cls.dai_ly = cls.env["res.partner"].create({"name": "Sakura Logistics (JP)"})
        cls.sale_order = cls.env["sale.order"].create({"partner_id": cls.shop.id})
        cls.chuyen = cls.env["lab.chuyen"].create({"loai_van_chuyen": "air"})
        cls.yeu_cau = cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "partner_id": cls.shop.id,
                "sale_order_id": cls.sale_order.id,
                "khung_gio_tu": fields.Datetime.now().replace(
                    hour=1, minute=30, second=0, microsecond=0
                ),
                "khung_gio_den": fields.Datetime.now().replace(
                    hour=3, minute=0, second=0, microsecond=0
                ),
                "so_kien_du_kien": 12,
            }
        )
        cls.hanh_trinh = cls.env["lab.hanh.trinh"].create(
            {
                "chuyen_id": cls.chuyen.id,
                "sale_order_id": cls.sale_order.id,
                "stage": "in_transit",
                "loai_chung_tu": "awb",
                "so_awb": "VN-157-12345678",
                "cang_di": "SGN",
                "cang_den": "NRT",
                "dai_ly_giao_id": cls.dai_ly.id,
                "line_ids": [
                    (0, 0, {"moc": "Đã lấy hàng tại shop", "dia_diem": "Quận 1"}),
                    (0, 0, {"moc": "Đã lên máy bay", "dia_diem": "SGN"}),
                ],
            }
        )

    # ------------------------------------------------------------------ helpers
    def _track(self, token):
        return self.url_open("/lab/api/v1/track/%s" % token)

    # ------------------------------------------------------------------ tests
    def test_track_tra_whitelist_khong_lo_du_lieu_noi_bo(self):
        res = self._track(self.hanh_trinh.access_token)
        self.assertEqual(res.status_code, 200, res.text)
        body = res.json()
        self.assertTrue(body["ok"])
        du_lieu = body["data"]

        self.assertEqual(du_lieu["stage"], "in_transit")
        self.assertEqual(du_lieu["awb"], "VN-157-12345678")
        self.assertEqual(du_lieu["origin"], "SGN")
        self.assertEqual(len(du_lieu["events"]), 2)
        self.assertEqual(du_lieu["events"][0]["label"], "Đã lấy hàng tại shop")

        # OQ-23: giờ hẹn đã format sẵn HH:mm
        self.assertRegex(du_lieu["windowFrom"], r"^\d{2}:\d{2}$")
        self.assertRegex(du_lieu["windowTo"], r"^\d{2}:\d{2}$")
        # OQ-22: có nguồn thật (so_kien_du_kien) thì mới trả
        self.assertEqual(du_lieu["expectedPackages"], 12)

        # KHÔNG được lộ dữ liệu nội bộ
        for khoa in (
            "id",
            "partner_id",
            "sale_order_id",
            "chuyen_id",
            "dai_ly_giao_id",
            "access_token",
            "driver_id",
            "so_kien_thuc_te",
            "amount_total",
        ):
            self.assertNotIn(khoa, du_lieu)

    def test_track_token_sai_tra_404(self):
        res = self._track("0" * 32)
        self.assertEqual(res.status_code, 404)
        self.assertEqual(res.json()["error"]["code"], "NOT_FOUND")

    def test_track_token_qua_ngan_tra_404(self):
        res = self._track("abc")
        self.assertEqual(res.status_code, 404)
        self.assertEqual(res.json()["error"]["code"], "NOT_FOUND")
