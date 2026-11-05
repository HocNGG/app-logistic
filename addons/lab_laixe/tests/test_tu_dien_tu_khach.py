# -*- coding: utf-8 -*-
"""Tự điền điểm lấy hàng từ khách khi tạo yêu cầu lấy hàng (yêu cầu 2026-11-04).

Luật 7: mỗi nhánh có case đúng + case sai. Điểm cần chứng minh:
* chọn khách có sẵn thông tin → địa chỉ / người liên hệ / SĐT được điền;
* người dùng nhập tay lúc tạo → GIỮ nguyên, không bị khách đè;
* sửa tay sau khi tạo → giữ (compute chỉ chạy lại khi ĐỔI khách);
* đổi khách khi còn "Mới" → điền lại theo khách mới; khi đã gán tài xế → KHÔNG đổi;
* khách thiếu thông tin → để trống, không bịa.
"""

from odoo.tests import Form, TransactionCase, tagged


@tagged("post_install", "-at_install")
class TestTuDienTuKhach(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        Partner = cls.env["res.partner"]
        cls.tinh = cls.env["res.country.state"].search([], limit=1)
        cls.shop_day_du = Partner.create(
            {
                "name": "Shop Đầy Đủ",
                "is_company": True,
                "street": "18 Nguyễn Trãi",
                "street2": "Phường Bến Thành",
                "city": "Quận 1",
                "state_id": cls.tinh.id,
                "phone": "028 3800 0000",
            }
        )
        cls.lien_he = Partner.create(
            {
                "name": "Chị Hạnh",
                "type": "contact",
                "parent_id": cls.shop_day_du.id,
                "mobile": "0903 111 222",
            }
        )
        cls.shop_khac = Partner.create(
            {
                "name": "Shop Khác",
                "is_company": True,
                "street": "77 Lê Lợi",
                "city": "Hải Châu",
            }
        )
        cls.shop_trong = Partner.create({"name": "Shop Chưa Có Thông Tin", "is_company": True})
        cls.ca_nhan = Partner.create(
            {"name": "Anh Tuấn", "street": "5 Tràng Tiền", "phone": "0912 555 666"}
        )
        cls.YeuCau = cls.env["lab.yeu.cau.lay.hang"]

    def test_chon_khach_co_san_thong_tin_thi_tu_dien(self):
        yc = self.YeuCau.create({"partner_id": self.shop_day_du.id})
        dia_chi = yc.dia_chi_lay
        self.assertTrue(dia_chi.startswith("18 Nguyễn Trãi, Phường Bến Thành, Quận 1"), dia_chi)
        self.assertIn(self.tinh.name, dia_chi)
        self.assertEqual(yc.lien_he_ten, "Chị Hạnh")
        # Di động của người liên hệ ưu tiên hơn số bàn của công ty.
        self.assertEqual(yc.lien_he_sdt, "0903 111 222")

    def test_khach_ca_nhan_thi_chinh_ho_la_nguoi_lien_he(self):
        yc = self.YeuCau.create({"partner_id": self.ca_nhan.id})
        self.assertEqual(yc.lien_he_ten, "Anh Tuấn")
        self.assertEqual(yc.lien_he_sdt, "0912 555 666")
        self.assertEqual(yc.dia_chi_lay, "5 Tràng Tiền")

    def test_khach_thieu_thong_tin_thi_de_trong_khong_bia(self):
        yc = self.YeuCau.create({"partner_id": self.shop_trong.id})
        self.assertFalse(yc.dia_chi_lay)
        self.assertFalse(yc.lien_he_ten)  # công ty chưa có liên hệ con → không lấy tên công ty
        self.assertFalse(yc.lien_he_sdt)

    def test_nhap_tay_luc_tao_thi_giu_nguyen(self):
        yc = self.YeuCau.create(
            {
                "partner_id": self.shop_day_du.id,
                "dia_chi_lay": "Kho Sen Đỏ - 45 Cách Mạng Tháng 8",
                "lien_he_ten": "Anh Dũng",
            }
        )
        self.assertEqual(yc.dia_chi_lay, "Kho Sen Đỏ - 45 Cách Mạng Tháng 8")
        self.assertEqual(yc.lien_he_ten, "Anh Dũng")
        # Trường không nhập tay vẫn được tự điền từ khách.
        self.assertEqual(yc.lien_he_sdt, "0903 111 222")

    def test_form_web_chon_khach_tu_dien_va_sua_duoc(self):
        """Đúng kịch bản màn web của CS: chọn khách → tự điền → sửa tay → lưu."""
        with Form(self.YeuCau) as form:
            form.partner_id = self.shop_day_du
            self.assertEqual(form.lien_he_ten, "Chị Hạnh")
            self.assertTrue(form.dia_chi_lay.startswith("18 Nguyễn Trãi"))
            form.dia_chi_lay = "Cổng sau, 18 Nguyễn Trãi"
            form.lien_he_sdt = "0909 000 111"
        yc = form.save()
        self.assertEqual(yc.dia_chi_lay, "Cổng sau, 18 Nguyễn Trãi")
        self.assertEqual(yc.lien_he_sdt, "0909 000 111")
        self.assertEqual(yc.lien_he_ten, "Chị Hạnh")

    def test_sua_tay_sau_khi_tao_thi_giu(self):
        yc = self.YeuCau.create({"partner_id": self.shop_day_du.id})
        yc.write({"dia_chi_lay": "Lấy ở kho phụ", "lien_he_sdt": "0900 000 000"})
        self.assertEqual(yc.dia_chi_lay, "Lấy ở kho phụ")
        self.assertEqual(yc.lien_he_sdt, "0900 000 000")

    def test_doi_khach_khi_con_moi_thi_dien_lai_theo_khach_moi(self):
        yc = self.YeuCau.create({"partner_id": self.shop_day_du.id})
        yc.write({"partner_id": self.shop_khac.id})
        self.assertEqual(yc.dia_chi_lay, "77 Lê Lợi, Hải Châu")
        # Khách mới không có thông tin liên hệ → để trống, KHÔNG sót lại của khách cũ.
        self.assertFalse(yc.lien_he_ten)
        self.assertFalse(yc.lien_he_sdt)

    def test_da_giao_tai_xe_thi_doi_khach_khong_doi_diem_lay_hang(self):
        yc = self.YeuCau.create({"partner_id": self.shop_day_du.id})
        # Ngoài đời việc tạo và việc gán là hai request riêng nên compute đã chạy; trong test
        # cùng một transaction thì phải ép chạy trước khi đổi trạng thái.
        self.env.flush_all()
        dia_chi_cu = yc.dia_chi_lay
        yc.state = "assigned"
        yc.write({"partner_id": self.shop_khac.id})
        self.assertEqual(yc.dia_chi_lay, dia_chi_cu)
        self.assertEqual(yc.lien_he_ten, "Chị Hạnh")

    def test_sua_thong_tin_khach_sau_do_khong_doi_yeu_cau_da_tao(self):
        """Là ảnh chụp lúc chọn khách: res.partner đổi sau không kéo theo yêu cầu cũ."""
        yc = self.YeuCau.create({"partner_id": self.shop_khac.id})
        # Compute stored chạy lười (lúc đọc/flush): chốt giá trị TRƯỚC khi sửa khách.
        self.assertEqual(yc.dia_chi_lay, "77 Lê Lợi, Hải Châu")
        self.shop_khac.street = "Địa chỉ mới của shop"
        self.assertEqual(yc.dia_chi_lay, "77 Lê Lợi, Hải Châu")
