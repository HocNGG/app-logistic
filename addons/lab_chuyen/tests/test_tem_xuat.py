# -*- coding: utf-8 -*-
"""Test cho TEM XUẤT + mã tem xuất theo kiện (chốt 2026-11-02: mỗi kiện một tem).

Bốn khẳng định:

1. Chốt booking sinh phiếu `WH/OUT` cho chuyến và **gán mã `XUAT/…` cho TỪNG kiện**.
2. `so_kien_xuat` và `kien_xuat_ids` đọc đúng từ package (nguồn: chứng từ, không tự khai).
3. Nút "In tem xuất" trả về đúng report action (không phải None / không phải action khác).
4. `client_uuid` của `lab.scan.event` là DUY NHẤT — chống ghi trùng khi mạng chập.
"""

from odoo import fields
from odoo.tests import TransactionCase, tagged


@tagged("post_install", "-at_install")
class TestTemXuat(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Tem Xuat Kiem Thu"})
        cls.don_ban = cls.env["sale.order"].create({"partner_id": cls.shop.id})

        # Chuyến đang MỞ (đủ field bắt buộc: name, loai_van_chuyen).
        cls.chuyen = cls.env["lab.chuyen"].create(
            {
                "name": "CH/AIR/TEST/0001",
                "loai_van_chuyen": "air",
                "state": "open",
            }
        )
        cls.env["lab.chuyen.line"].create(
            {
                "chuyen_id": cls.chuyen.id,
                "sale_order_id": cls.don_ban.id,
                "so_kien": 2,
                "khoi_luong_kg": 10.0,
            }
        )

        # Việc đã về kho + phiếu nhập giả (chỉ để làm gốc liên kết cho kiện).
        cls.phieu_nhap = cls.env["stock.picking"].create(
            {
                "picking_type_id": cls.env.ref("stock.picking_type_in").id,
                "location_id": cls.env.ref("stock.stock_location_suppliers").id,
                "location_dest_id": cls.env.ref("stock.stock_location_stock").id,
                "origin": "LH/TEST/0001",
            }
        )
        cls.viec = cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "name": "LH/TEST/0001",
                "partner_id": cls.shop.id,
                "sale_order_id": cls.don_ban.id,
                "stock_picking_id": cls.phieu_nhap.id,
                "state": "confirmed",
                "so_kien_thuc_te": 2,
            }
        )
        # 2 kiện (native package) — gắn vào phiếu nhập qua DÒNG HÀNG, đúng như luồng
        # thật: `job.kien_ids` đọc từ `stock.move.line.result_package_id` của phiếu nhập.
        cls.kien_1 = cls.env["stock.quant.package"].create({"name": "KIEN/TEST/0001"})
        cls.kien_2 = cls.env["stock.quant.package"].create({"name": "KIEN/TEST/0002"})
        san_pham = cls.env.ref("lab_core.product_kien_gom_le")
        cls.move_nhap = cls.env["stock.move"].create(
            {
                "name": san_pham.display_name,
                "product_id": san_pham.id,
                "product_uom_qty": 2,
                "product_uom": san_pham.uom_id.id,
                "picking_id": cls.phieu_nhap.id,
                "location_id": cls.phieu_nhap.location_id.id,
                "location_dest_id": cls.phieu_nhap.location_dest_id.id,
            }
        )
        for kien in (cls.kien_1, cls.kien_2):
            cls.env["stock.move.line"].create(
                {
                    "move_id": cls.move_nhap.id,
                    "picking_id": cls.phieu_nhap.id,
                    "product_id": san_pham.id,
                    "product_uom_id": san_pham.uom_id.id,
                    "quantity": 1,
                    "location_id": cls.phieu_nhap.location_id.id,
                    "location_dest_id": cls.phieu_nhap.location_dest_id.id,
                    "result_package_id": kien.id,
                }
            )

    def test_01_chot_booking_sinh_ma_tem_xuat_cho_tung_kien(self):
        """Mỗi kiện phải có MỘT mã xuất riêng, cùng một chuyến."""
        self.chuyen.action_confirm_booking()

        self.assertEqual(self.chuyen.state, "booking_confirmed")
        phieu_xuat = self.chuyen.picking_ids.filtered(
            lambda p: p.picking_type_id.code == "outgoing"
        )
        self.assertTrue(phieu_xuat, "Chốt booking phải sinh phiếu xuất WH/OUT.")
        self.assertEqual(
            len(phieu_xuat.move_line_ids), 2, "Phiếu xuất phải có 1 dòng/kiện."
        )

        hai_kien = self.env["stock.quant.package"].search(
            [("lab_chuyen_id", "=", self.chuyen.id)]
        )
        self.assertEqual(len(hai_kien), 2, "Phải gán chuyến cho đúng 2 kiện.")
        self.assertTrue(all(p.lab_ma_xuat for p in hai_kien), "Kiện nào cũng phải có mã xuất.")
        self.assertEqual(
            len(set(hai_kien.mapped("lab_ma_xuat"))), 2, "Mỗi kiện một mã xuất RIÊNG."
        )
        self.assertEqual(
            set(hai_kien.mapped("lab_trang_thai")), {"packed"}, "Kiện phải ở trạng thái packed."
        )

    def test_02_so_kien_va_nut_in_tem(self):
        """`so_kien_xuat` đếm đúng + nút in trả về đúng report action."""
        self.chuyen.action_confirm_booking()
        self.assertEqual(self.chuyen.so_kien_xuat, 2)

        action = self.chuyen.action_in_tem_xuat()
        self.assertIsInstance(action, dict)
        self.assertEqual(action.get("report_name"), "lab_chuyen.report_tem_xuat_doc")
        self.assertEqual(action.get("type"), "ir.actions.report")

    def test_03_qr_tra_cuu_dung_dang_path(self):
        """QR tem xuất: URL path, khoá = mã chuyến đổi '/' thành '-', không '?' / '&'."""
        self.chuyen.action_confirm_booking()
        kien = self.chuyen.kien_xuat_ids[:1]
        url = self.chuyen.track_url_kien(kien)
        # Khoá tra cứu là TOKEN ngẫu nhiên, KHÔNG phải mã chuyến (RV-01 vòng 2):
        # mã chuyến đoán được nên không được xuất hiện trong URL.
        self.assertIn("/lab/track/%s/" % self.chuyen.access_token, url)
        self.assertNotIn("CH-AIR-TEST-0001", url, "Không được dùng mã chuyến làm khoá.")
        self.assertNotIn("?", url)
        self.assertNotIn("&", url)
        self.assertTrue(url.endswith(kien.name))

    def test_04_client_uuid_chong_trung(self):
        """Ghi 2 lần cùng client_uuid phải bị DB chặn (unique)."""
        kien = self.env["stock.quant.package"].create({"name": "KIEN/TEST/9999"})
        gia_tri = {
            "kien_id": kien.id,
            "loai": "nhan",
            "ket_qua": "ok",
            "thiet_bi": "app",
            "client_uuid": "uuid-kiem-thu-trung",
            "thoi_diem": fields.Datetime.now(),
        }
        self.env["lab.scan.event"].create(dict(gia_tri))
        # Vi phạm unique của `client_uuid`: Odoo có thể ném ValidationError (khi dịch
        # _sql_constraints) hoặc UniqueViolation thô từ psycopg2 — bắt Exception trong
        # SAVEPOINT để transaction không bị hỏng và test vẫn kiểm được đúng hành vi.
        with self.assertRaises(Exception):
            with self.env.cr.savepoint():
                self.env["lab.scan.event"].create(dict(gia_tri))
