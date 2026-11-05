# -*- coding: utf-8 -*-
"""HttpCase cho `POST /lab/api/v1/scan` + trang tra cứu công khai `/lab/track/...`.

Năm khẳng định (đúng yêu cầu nghiệm thu 2026-11-02):

1. Quét tem NHẬN lần đầu -> 200, `ketQua=ok`, ghi 1 dòng `lab.scan.event`.
2. Gửi lại CÙNG `clientUuid` -> `trung=true` và **không** sinh dòng thứ hai.
3. Quét lại đúng mốc nhưng UUID khác -> `ketQua=trung` (vẫn 200, app hiện "đã quét").
4. Quét tem XUẤT khi kiện chưa in tem xuất -> 409 `sai_moc`.
5. Trang tra cứu công khai trả 200 và có mã kiện; mã sai -> 404.

Quyết định kỹ thuật: user + token được seed trong `setUpClass` (đúng cặp field endpoint
login ghi) để KHÔNG đụng rate limit 30 lượt/phút của `/auth/login`.
"""

import hashlib
import json
from datetime import timedelta

from odoo import fields
from odoo.tests import HttpCase, new_test_user, tagged

MAT_KHAU = "Lab@Scan2026"


@tagged("post_install", "-at_install")
class TestScanApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.kho = new_test_user(
            cls.env,
            login="api_scan_kho",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_warehouse,stock.group_stock_user",
        )
        token = "token-kiem-thu-scan-kho"
        cls.kho.write(
            {
                "lab_api_token_hash": hashlib.sha256(token.encode()).hexdigest(),
                "lab_api_token_expiry": fields.Datetime.now() + timedelta(hours=8),
            }
        )
        cls.token = token

        # Kiện 1: trong kho, CHƯA in tem xuất -> dùng cho ca quét nhận + ca sai mốc.
        cls.kien = cls.env["stock.quant.package"].create({"name": "KIEN/SCAN/0001"})
        # Kiện 2: đã in tem xuất (packed) -> dùng cho ca quét xuất hợp lệ.
        cls.kien_xuat = cls.env["stock.quant.package"].create(
            {"name": "KIEN/SCAN/0002", "lab_ma_xuat": "XUAT/SCAN/0002", "lab_trang_thai": "packed"}
        )

    def _vao(self):
        """Mở PHIÊN Odoo cho user kho (route `auth='user'`) — token chỉ là lớp thứ hai."""
        self.authenticate(self.kho.login, MAT_KHAU)

    def _goi(self, path, payload):
        return self.url_open(
            path,
            data=json.dumps(payload).encode(),
            headers={
                "Content-Type": "application/json",
                "X-Lab-Token": self.token,
            },
        )

    def test_01_quet_nhan_lan_dau(self):
        self._vao()
        res = self._goi(
            "/lab/api/v1/scan",
            {"ma": self.kien.name, "moc": "nhan", "clientUuid": "uuid-api-nhan-01"},
        )
        self.assertEqual(res.status_code, 200)
        du_lieu = res.json()["data"]
        self.assertEqual(du_lieu["ketQua"], "ok")
        self.assertFalse(du_lieu["trung"])
        so_dong = self.env["lab.scan.event"].search_count(
            [("client_uuid", "=", "uuid-api-nhan-01")]
        )
        self.assertEqual(so_dong, 1, "Phải ghi đúng 1 dòng nhật ký quét.")

    def test_02_gui_lai_cung_uuid_khong_tao_dong_moi(self):
        self._vao()
        payload = {"ma": self.kien.name, "moc": "nhan", "clientUuid": "uuid-api-nhan-02"}
        self._goi("/lab/api/v1/scan", payload)
        res2 = self._goi("/lab/api/v1/scan", payload)
        self.assertEqual(res2.status_code, 200)
        self.assertTrue(res2.json()["data"]["trung"], "Lần 2 phải trả cờ trung=true.")
        so_dong = self.env["lab.scan.event"].search_count(
            [("client_uuid", "=", "uuid-api-nhan-02")]
        )
        self.assertEqual(so_dong, 1, "Gửi lại cùng UUID KHÔNG được tạo dòng thứ hai.")

    def test_03_quet_lai_dung_moc_uuid_khac(self):
        self._vao()
        self._goi(
            "/lab/api/v1/scan",
            {"ma": self.kien_xuat.name, "moc": "xuat", "clientUuid": "uuid-api-xuat-01"},
        )
        res = self._goi(
            "/lab/api/v1/scan",
            {"ma": self.kien_xuat.name, "moc": "xuat", "clientUuid": "uuid-api-xuat-02"},
        )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["data"]["ketQua"], "trung")

    def test_04_quet_xuat_khi_chua_in_tem(self):
        self._vao()
        res = self._goi(
            "/lab/api/v1/scan",
            {"ma": self.kien.name, "moc": "xuat", "clientUuid": "uuid-api-xuat-03"},
        )
        self.assertEqual(res.status_code, 409, "Sai mốc phải trả 409. Body=" + res.text[:400])
        self.assertIs(res.json()["ok"], False, "Phản hồi lỗi phải có ok:false")
        self.assertEqual(res.json()["data"]["ketQua"], "sai_moc")

    def test_05_trang_tra_cuu_cong_khai(self):
        # Chuyến giả + gán kiện để trang tra cứu có dữ liệu.
        chuyen = self.env["lab.chuyen"].create(
            {
                "name": "CH/AIR/SCAN/0001",
                "loai_van_chuyen": "air",
            }
        )
        self.kien.write({"lab_chuyen_id": chuyen.id, "lab_ma_xuat": "XUAT/SCAN/0001"})
        url = "/lab/track/%s/%s" % (chuyen.khoa_tra_cuu(), self.kien.name)
        res = self.url_open(url)
        self.assertEqual(res.status_code, 200)
        self.assertIn(self.kien.name, res.text)

        # Mã kiện không tồn tại -> 404 (không lộ gì).
        res_404 = self.url_open("/lab/track/%s/KIEN/SCAN/9999" % chuyen.khoa_tra_cuu())
        self.assertEqual(res_404.status_code, 404, "Kiện không tồn tại -> 404 (trang HTML, không phải JSON)")
        self.assertNotIn("XUAT/", res_404.text, "Trang 404 không được lộ dữ liệu")

    def test_06_quet_ma_khong_ton_tai_tra_404_ok_false(self):
        """Mã kiện lạ: HTTP 404 + `ok: false` + `ketQua=khong_tim_thay` (hợp đồng app)."""
        self._vao()
        res = self._goi(
            "/lab/api/v1/scan",
            {"ma": "KIEN/SCAN/9999", "moc": "nhan", "clientUuid": "uuid-khong-ton-tai"},
        )
        self.assertEqual(res.status_code, 404, res.text)
        du_lieu = res.json()
        self.assertIs(du_lieu["ok"], False, "Phản hồi lỗi phải có ok:false")
        self.assertEqual(du_lieu["data"]["ketQua"], "khong_tim_thay")
        self.assertEqual(du_lieu["error"]["code"], "NOT_FOUND")

    def test_07_danh_sach_kien_cua_viec(self):
        """`GET /kho/jobs/<id>/kien` — 3 case: chưa nhận (0 kiện), đã nhận (N kiện), đã quét 1 kiện.

        Vì sao cần endpoint này (FE-01 báo thiếu): màn "Kho · Nhận hàng" phải hiện danh
        sách kiện NGAY sau khi nhận để kho dán tem rồi quét từng kiện.
        """
        self._vao()
        Job = self.env["lab.yeu.cau.lay.hang"]

        # (a) Việc CHƯA nhận -> chưa có phiếu nhập -> 0 kiện
        shop = self.env["res.partner"].create({"name": "Shop Kien API"})
        job_chua_nhan = Job.create({"name": "LH/KIENAPI/0001", "partner_id": shop.id})
        res0 = self.url_open(
            "/lab/api/v1/kho/jobs/%d/kien" % job_chua_nhan.id,
            headers={"X-Lab-Token": self.token},
        )
        self.assertEqual(res0.status_code, 200, res0.text)
        du_lieu0 = res0.json()["data"]
        self.assertEqual(du_lieu0["soKien"], 0)
        self.assertEqual(du_lieu0["kien"], [])
        self.assertEqual(du_lieu0["daQuet"], 0)

        # (b) Việc ĐÃ nhận: phiếu nhập + N kiện (dựng đúng như _tao_dong_hang của model)
        phieu = self.env["stock.picking"].create(
            {
                "picking_type_id": self.env.ref("stock.picking_type_in").id,
                "location_id": self.env.ref("stock.stock_location_suppliers").id,
                "location_dest_id": self.env.ref("stock.stock_location_stock").id,
                "origin": "LH/KIENAPI/0002",
            }
        )
        san_pham = self.env.ref("lab_core.product_kien_gom_le")
        move = self.env["stock.move"].create(
            {
                "name": san_pham.display_name,
                "product_id": san_pham.id,
                "product_uom_qty": 2,
                "product_uom": san_pham.uom_id.id,
                "picking_id": phieu.id,
                "location_id": phieu.location_id.id,
                "location_dest_id": phieu.location_dest_id.id,
            }
        )
        kien = self.env["stock.quant.package"]
        for i in range(2):
            k = self.env["stock.quant.package"].create({"name": "KIEN/KIENAPI/%02d" % i})
            self.env["stock.move.line"].create(
                {
                    "move_id": move.id,
                    "picking_id": phieu.id,
                    "product_id": san_pham.id,
                    "product_uom_id": san_pham.uom_id.id,
                    "quantity": 1,
                    "location_id": phieu.location_id.id,
                    "location_dest_id": phieu.location_dest_id.id,
                    "result_package_id": k.id,
                }
            )
            kien |= k
        job_da_nhan = Job.create(
            {
                "name": "LH/KIENAPI/0002",
                "partner_id": shop.id,
                "stock_picking_id": phieu.id,
                "state": "confirmed",
                "so_kien_thuc_te": 2,
            }
        )
        res1 = self.url_open(
            "/lab/api/v1/kho/jobs/%d/kien" % job_da_nhan.id,
            headers={"X-Lab-Token": self.token},
        )
        self.assertEqual(res1.status_code, 200, res1.text)
        du_lieu1 = res1.json()["data"]
        self.assertEqual(du_lieu1["soKien"], 2)
        self.assertEqual({m["ma"] for m in du_lieu1["kien"]}, {k.name for k in kien})
        self.assertEqual(du_lieu1["daQuet"], 0)
        self.assertFalse(any(m["daQuet"] for m in du_lieu1["kien"]))
        self.assertIsNone(du_lieu1["kien"][0]["quetLuc"])

        # (c) Quét 1 kiện -> daQuet=1, đúng kiện đó daQuet=true + có quetLuc
        kien_quet = kien[0]
        res_quet = self._goi(
            "/lab/api/v1/scan",
            {"ma": kien_quet.name, "moc": "nhan", "clientUuid": "uuid-kien-api-01"},
        )
        self.assertEqual(res_quet.status_code, 200, res_quet.text)

        res2 = self.url_open(
            "/lab/api/v1/kho/jobs/%d/kien" % job_da_nhan.id,
            headers={"X-Lab-Token": self.token},
        )
        du_lieu2 = res2.json()["data"]
        self.assertEqual(du_lieu2["daQuet"], 1)
        muc = next(m for m in du_lieu2["kien"] if m["ma"] == kien_quet.name)
        self.assertIs(muc["daQuet"], True)
        self.assertRegex(muc["quetLuc"], r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$")

    def test_08_quet_ma_qr_dang_url_tra_cuu(self):
        """Quét mã QR in trên tem (`http://.../lab/track/<token>/KIEN/...`) phải tự bóc mã kiện."""
        self._vao()
        kien_qr = self.env["stock.quant.package"].create({"name": "KIEN/SCAN/QR01"})
        url_qr = "http://192.168.1.5:8069/lab/track/token-bat-ky/%s" % kien_qr.name
        res_nhan = self._goi(
            "/lab/api/v1/scan",
            {"ma": url_qr, "moc": "nhan", "clientUuid": "uuid-qr-url-nhan-01"},
        )
        self.assertEqual(res_nhan.status_code, 200, res_nhan.text)
        self.assertEqual(res_nhan.json()["data"]["kien"], "KIEN/SCAN/QR01")
        self.assertEqual(res_nhan.json()["data"]["ketQua"], "ok")

        kien_qr.write({"lab_ma_xuat": "XUAT/SCAN/QR01", "lab_trang_thai": "packed"})
        res_xuat = self._goi(
            "/lab/api/v1/scan",
            {"ma": url_qr, "moc": "xuat", "clientUuid": "uuid-qr-url-xuat-01"},
        )
        self.assertEqual(res_xuat.status_code, 200, res_xuat.text)
        self.assertEqual(res_xuat.json()["data"]["kien"], "KIEN/SCAN/QR01")
        self.assertEqual(res_xuat.json()["data"]["ketQua"], "ok")
