# -*- coding: utf-8 -*-
"""HttpCase cho màn "Kho · Xuất kho" (chốt 2026-11-02):

* ``GET  /lab/api/v1/kho/cho-xuat``            — danh sách chuyến còn kiện chưa xuất
* ``POST /lab/api/v1/kho/chuyen/<id>/xuat``    — xác nhận xuất kho

Ba case bắt buộc của endpoint xác nhận:
1. Còn kiện chưa quét + KHÔNG có lý do  → **409** ``CON_KIEN_CHUA_QUET`` kèm `{conLai, danhSach}`.
2. Còn kiện chưa quét + CÓ lý do        → phiếu **done**, kiện thiếu `cancelled` (quyết định M9).
3. Đã quét đủ                          → phiếu **done**, `soKienThieu = 0`.

Cộng thêm: gửi lại cùng `clientUuid` không validate lần hai; user không thuộc nhóm Kho → 403.

Quyết định kỹ thuật: token seed sẵn trong `setUpClass` (như `test_cs_kho_api`) để không đụng
rate limit 30 lượt/phút của `/auth/login`; mỗi test tạo chuyến + phiếu riêng.
"""

import hashlib
import json
from datetime import timedelta

from odoo import fields
from odoo.tests import HttpCase, new_test_user, tagged

MAT_KHAU = "Lab@XuatKho2026"


@tagged("post_install", "-at_install")
class TestKhoXuatApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.kho = new_test_user(
            cls.env,
            login="api_xuat_kho",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_warehouse,stock.group_stock_user",
        )
        cls.cs = new_test_user(
            cls.env,
            login="api_xuat_cs",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_cs",
        )
        cls.token_theo_login = {}
        for nguoi_dung in (cls.kho, cls.cs):
            token = "token-kiem-thu-xuat-%s" % nguoi_dung.login
            nguoi_dung.write(
                {
                    "lab_api_token_hash": hashlib.sha256(token.encode()).hexdigest(),
                    "lab_api_token_expiry": fields.Datetime.now() + timedelta(hours=8),
                }
            )
            cls.token_theo_login[nguoi_dung.login] = token

        cls.san_pham = cls.env.ref("lab_core.product_kien_gom_le")
        cls.loai_xuat = cls.env.ref("stock.picking_type_out")
        cls.kho_hang = cls.env.ref("stock.stock_location_stock")
        cls.khach = cls.env.ref("stock.stock_location_customers")

    # ------------------------------------------------------------------ helpers
    def _vao(self, nguoi_dung=None):
        self.authenticate((nguoi_dung or self.kho).login, MAT_KHAU)
        return self.token_theo_login[(nguoi_dung or self.kho).login]

    def _goi(self, duong_dan, token, du_lieu=None):
        headers = {"Content-Type": "application/json", "X-Lab-Token": token}
        if du_lieu is None:
            return self.url_open(duong_dan, headers=headers)
        return self.url_open(duong_dan, data=json.dumps(du_lieu).encode(), headers=headers)

    def _tao_chuyen_co_phieu(self, so_kien=3, so_da_xuat=0):
        """Chuyến `booking_confirmed` + phiếu WH/OUT + N kiện (một số đã 'shipped')."""
        chuyen = self.env["lab.chuyen"].create(
            {
                "name": "CH/AIR/XUATTEST/%04d" % (len(self.env["lab.chuyen"].search([])) + 1),
                "loai_van_chuyen": "air",
                "state": "booking_confirmed",
            }
        )
        picking = self.env["stock.picking"].create(
            {
                "picking_type_id": self.loai_xuat.id,
                "location_id": self.kho_hang.id,
                "location_dest_id": self.khach.id,
                "origin": chuyen.name,
                "lab_chuyen_id": chuyen.id,
                "lab_package_count": so_kien,
            }
        )
        move = self.env["stock.move"].create(
            {
                "name": self.san_pham.display_name,
                "product_id": self.san_pham.id,
                "product_uom_qty": so_kien,
                "product_uom": self.san_pham.uom_id.id,
                "picking_id": picking.id,
                "location_id": self.kho_hang.id,
                "location_dest_id": self.khach.id,
            }
        )
        kien_list = self.env["stock.quant.package"]
        for i in range(so_kien):
            da_xuat = i < so_da_xuat
            kien = self.env["stock.quant.package"].create(
                {
                    "name": "KIEN/XUATTEST/%02d%02d" % (chuyen.id, i),
                    "lab_ma_xuat": "XUAT/XUATTEST/%02d%02d" % (chuyen.id, i),
                    "lab_chuyen_id": chuyen.id,
                    "lab_trang_thai": "shipped" if da_xuat else "packed",
                }
            )
            self.env["stock.move.line"].create(
                {
                    "move_id": move.id,
                    "picking_id": picking.id,
                    "product_id": self.san_pham.id,
                    "product_uom_id": self.san_pham.uom_id.id,
                    "quantity": 1,
                    "location_id": self.kho_hang.id,
                    "location_dest_id": self.khach.id,
                    "package_id": kien.id,
                }
            )
            kien_list |= kien
        picking.action_confirm()
        return chuyen, picking, kien_list

    # ------------------------------------------------------------------ tests
    def test_01_danh_sach_cho_xuat(self):
        chuyen, _picking, kien = self._tao_chuyen_co_phieu(so_kien=2, so_da_xuat=1)
        token = self._vao()
        res = self._goi("/lab/api/v1/kho/cho-xuat", token)
        self.assertEqual(res.status_code, 200, res.text)
        items = res.json()["data"]["items"]
        muc = next((x for x in items if x["chuyenId"] == chuyen.id), None)
        self.assertTrue(muc, "Chuyến còn kiện chưa xuất phải có trong danh sách.")
        self.assertEqual(muc["soKien"], 2)
        self.assertEqual(muc["daQuet"], 1)
        self.assertEqual({k["ma"] for k in muc["kien"]}, {k.name for k in kien})

        # ?chuyenId= lấy chi tiết đúng chuyến
        res_chi_tiet = self._goi(
            "/lab/api/v1/kho/cho-xuat?chuyenId=%d" % chuyen.id, token
        )
        self.assertEqual(res_chi_tiet.status_code, 200)
        self.assertEqual(res_chi_tiet.json()["data"]["chuyenId"], chuyen.id)

    def test_02_thieu_kien_khong_ly_do_tra_409(self):
        chuyen, _picking, _kien = self._tao_chuyen_co_phieu(so_kien=3, so_da_xuat=1)
        token = self._vao()
        res = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id,
            token,
            {"clientUuid": "uuid-xuat-thieu-khong-ly-do"},
        )
        self.assertEqual(res.status_code, 409, res.text)
        du_lieu = res.json()
        self.assertEqual(du_lieu["error"]["code"], "CON_KIEN_CHUA_QUET")
        self.assertEqual(du_lieu["data"]["conLai"], 2)
        self.assertEqual(len(du_lieu["data"]["danhSach"]), 2)

    def test_03_thieu_kien_co_ly_do_van_xuat_duoc(self):
        chuyen, picking, kien = self._tao_chuyen_co_phieu(so_kien=3, so_da_xuat=1)
        token = self._vao()
        res = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id,
            token,
            {
                "clientUuid": "uuid-xuat-thieu-co-ly-do",
                "lyDoThieu": "Khách bỏ 2 kiện, hẹn chuyến sau",
            },
        )
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        self.assertEqual(du_lieu["state"], "done", "Vẫn phải validate phiếu (xuất thiếu — M9).")
        self.assertEqual(du_lieu["soKienXuat"], 1)
        self.assertEqual(du_lieu["soKienThieu"], 2)
        self.assertEqual(picking.state, "done")
        thieu = kien.filtered(lambda k: k.lab_ma_xuat and k.lab_trang_thai == "cancelled")
        self.assertEqual(len(thieu), 2, "Kiện không xuất phải chuyển 'cancelled'.")
        # Có nhật ký quét cho từng kiện thiếu (audit bảng tổng kết)
        so_event = self.env["lab.scan.event"].search_count(
            [("kien_id", "in", thieu.ids), ("loai", "=", "xuat")]
        )
        self.assertEqual(so_event, 2)

    def test_04_du_kien_thi_done(self):
        chuyen, picking, _kien = self._tao_chuyen_co_phieu(so_kien=2, so_da_xuat=2)
        token = self._vao()
        res = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id,
            token,
            {"clientUuid": "uuid-xuat-du"},
        )
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        self.assertEqual(du_lieu["state"], "done")
        self.assertEqual(du_lieu["soKienXuat"], 2)
        self.assertEqual(du_lieu["soKienThieu"], 0)
        self.assertEqual(picking.state, "done")

        # Gửi lại CÙNG clientUuid -> không validate lần hai, trả cờ trung
        res2 = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id,
            token,
            {"clientUuid": "uuid-xuat-du"},
        )
        self.assertEqual(res2.status_code, 200)
        self.assertTrue(res2.json()["data"]["trung"])

    def test_05_cs_goi_xuat_tra_403(self):
        chuyen, _picking, _kien = self._tao_chuyen_co_phieu(so_kien=1, so_da_xuat=1)
        token = self._vao(self.cs)
        res = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id, token, {"clientUuid": "uuid-cs-xuat"}
        )
        self.assertEqual(res.status_code, 403, res.text)

    def test_06_phieu_da_done_van_tra_trung(self):
        """Phiếu đã xuất xong: gọi lại (UUID MỚI) phải 200 trung=true, KHÔNG 409.

        Hồi quy rủi ro BE-01 tự nêu + RV-01 yêu cầu: kiện `cancelled` sau khi xuất thiếu
        từng bị tính là "chưa quét" ⇒ lần gọi sau trả 409 sai nghĩa.
        """
        chuyen, picking, _kien = self._tao_chuyen_co_phieu(so_kien=2, so_da_xuat=2)
        token = self._vao()
        res = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id, token, {"clientUuid": "uuid-done-01"}
        )
        self.assertEqual(res.status_code, 200, res.text)
        self.assertEqual(picking.state, "done")

        res2 = self._goi(
            "/lab/api/v1/kho/chuyen/%d/xuat" % chuyen.id, token, {"clientUuid": "uuid-done-02"}
        )
        self.assertEqual(res2.status_code, 200, res2.text)
        du_lieu = res2.json()["data"]
        self.assertIs(du_lieu["trung"], True)
        self.assertEqual(du_lieu["state"], "done")
        self.assertEqual(du_lieu["soKienXuat"], 2)