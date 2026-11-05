# -*- coding: utf-8 -*-
"""Token tra cứu công khai của CHUYẾN — chống đoán (RV-01 vòng 2, 2026-11-03).

Ba khẳng định:

a. Token mới **không** chứa mã chuyến, đủ dài, và khác nhau giữa hai chuyến.
b. Khoá kiểu cũ `CH-AIR-26-0257` (mã chuyến đổi '/'→'-') trên URL tra cứu → **404**.
c. Token thật + mã kiện thuộc chuyến → **200**, và trang hiện đúng mã kiện.
"""

import re

from odoo.tests import HttpCase, tagged


@tagged("post_install", "-at_install")
class TestTrackToken(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.chuyen_1 = cls.env["lab.chuyen"].create(
            {"name": "CH/AIR/TOKEN/0001", "loai_van_chuyen": "air"}
        )
        cls.chuyen_2 = cls.env["lab.chuyen"].create(
            {"name": "CH/AIR/TOKEN/0002", "loai_van_chuyen": "air"}
        )
        cls.kien = cls.env["stock.quant.package"].create(
            {
                "name": "KIEN/TOKEN/0001",
                "lab_ma_xuat": "XUAT/TOKEN/0001",
                "lab_chuyen_id": cls.chuyen_1.id,
            }
        )

    def test_a_token_khong_doan_duoc(self):
        token = self.chuyen_1.access_token
        self.assertTrue(token, "Tạo chuyến phải sinh access_token ngay.")
        self.assertGreaterEqual(len(token), 24)
        # Không chứa mã chuyến (đã bỏ toàn bộ dấu '/' và chữ số đặc trưng của tên chuyến).
        for manh in ("CH", "AIR", "TOKEN", "0001"):
            self.assertNotIn(manh, token)
        self.assertFalse(re.fullmatch(r"[A-Za-z0-9\-_/]+", token) and "TOKEN" in token)
        self.assertNotEqual(
            self.chuyen_1.access_token,
            self.chuyen_2.access_token,
            "Hai chuyến phải có token khác nhau.",
        )

    def test_b_khoa_kieu_cu_tra_404(self):
        # Đúng giá trị mà bản cũ dùng làm khoá
        khoa_cu = self.chuyen_1.name.replace("/", "-")
        self.assertEqual(khoa_cu, "CH-AIR-TOKEN-0001")
        res = self.url_open("/lab/track/%s/%s" % (khoa_cu, self.kien.name))
        self.assertEqual(
            res.status_code, 404, "Mã chuyến KHÔNG còn là khoá tra cứu — phải 404."
        )

    def test_c_token_that_tra_200(self):
        res = self.url_open(
            "/lab/track/%s/%s" % (self.chuyen_1.access_token, self.kien.name)
        )
        self.assertEqual(res.status_code, 200, res.text[:300])
        self.assertIn(self.kien.name, res.text)

    def test_d_backfill_sinh_token_cho_chuyen_cu(self):
        """Chuyến cũ chưa có token (dữ liệu trước 2026-11-03) phải được backfill."""
        self.chuyen_1.write({"access_token": False})
        self.assertFalse(self.chuyen_1.access_token)
        self.env["lab.chuyen"]._lab_backfill_access_token()
        self.chuyen_1.invalidate_recordset(["access_token"])
        self.assertTrue(self.chuyen_1.access_token, "Backfill phải sinh token cho chuyến cũ.")
        # Idempotent: chạy lại không đổi token đã có.
        token_truoc = self.chuyen_1.access_token
        self.env["lab.chuyen"]._lab_backfill_access_token()
        self.chuyen_1.invalidate_recordset(["access_token"])
        self.assertEqual(self.chuyen_1.access_token, token_truoc)
