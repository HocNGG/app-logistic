# -*- coding: utf-8 -*-
"""Test cho batch phân vùng app/web (D1/D2, P0-1, P0-7, N13–N15) — chốt 2026-11-02.

Bốn khẳng định:

a. **CS gán tài xế qua wizard** `lab.yeu.cau.lay.hang.gan.tai.xe` → state `assigned`,
   `driver_id` đúng (CS đã rời app nên đường này là đường DUY NHẤT của CS).
b. **CS tạo được dòng chuyến trên web** (ACL `lab.chuyen.line` của group_cs đã nâng
   `1,0,0,0 → 1,1,1,0`).
c. **`action_nhan_tai_quay`** (N13/N15): chỉ chạy với `kenh_nhan='tai_kho'`, tạo `WH/IN`
   **done** + kiện + tồn kho tăng; kênh tài xế thì bị chặn.
d. **Kho tạo được yêu cầu lấy hàng nhưng KHÔNG tạo được `sale.order`** (giữ N16: tiền và
   giá vẫn là việc của CS/kế toán).
"""

from odoo.exceptions import AccessError, UserError
from odoo.tests import TransactionCase, new_test_user, tagged

MAT_KHAU = "Lab@Batch2026"


@tagged("post_install", "-at_install")
class TestGanTaiXeVaNhanQuay(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Batch Phan Vung"})

        # Tài khoản CS (dùng cho (a) và (b)) — tạo bằng new_test_user để không phụ thuộc seed.
        cls.cs = new_test_user(
            cls.env,
            login="batch_cs",
            password=MAT_KHAU,
            email="batch_cs@example.com",
            groups="base.group_user,lab_core.group_cs",
        )
        # Tài xế: user thuộc nhóm Tài xế + nhân viên tương ứng (dropdown wizard lọc theo nhóm).
        cls.tai_xe_user = new_test_user(
            cls.env,
            login="batch_taixe",
            password=MAT_KHAU,
            email="batch_taixe@example.com",
            # Tài xế là PORTAL (không phải internal user): nhóm lab_core.group_driver
            # imply base.group_portal — khai cả hai group_user sẽ lỗi
            # "The user cannot have more than one user types".
            groups="base.group_portal,lab_core.group_driver",
        )
        cls.tai_xe = cls.env["hr.employee"].create(
            {"name": "Tài Xế Batch", "user_id": cls.tai_xe_user.id}
        )
        # Kho: dùng cho (c) và (d).
        cls.kho = new_test_user(
            cls.env,
            login="batch_kho",
            password=MAT_KHAU,
            email="batch_kho@example.com",
            groups="base.group_user,lab_core.group_warehouse,stock.group_stock_user",
        )

    # ------------------------------------------------------------------ (a)
    def test_a_cs_gan_tai_xe_qua_wizard(self):
        job = self.env["lab.yeu.cau.lay.hang"].create(
            {"name": "LH/BATCH/0001", "partner_id": self.shop.id}
        )
        self.assertEqual(job.state, "new")

        wizard = (
            self.env["lab.yeu.cau.lay.hang.gan.tai.xe"]
            .with_user(self.cs)
            .create({"job_id": job.id, "driver_id": self.tai_xe.id, "ghi_chu": "Giao tuyến Q7"})
        )
        # Dropdown chỉ chứa tài xế có user thuộc nhóm Tài xế.
        self.assertIn(self.tai_xe, wizard.driver_kha_dung)

        action = wizard.action_gan()
        self.assertEqual(action.get("type"), "ir.actions.act_window_close")
        self.assertEqual(job.state, "assigned", "Gán qua wizard phải đưa việc sang 'Đã gán'.")
        self.assertEqual(job.driver_id, self.tai_xe)
        # Ghi chú phân công phải vào chatter (không mất dữ liệu người dùng nhập).
        self.assertIn("Giao tuyến Q7", " ".join(job.message_ids.mapped("body")))

    # ------------------------------------------------------------------ (b)
    def test_b_cs_tao_duoc_dong_chuyen(self):
        don_ban = self.env["sale.order"].create({"partner_id": self.shop.id})
        chuyen = self.env["lab.chuyen"].create(
            {"name": "CH/AIR/BATCH/0001", "loai_van_chuyen": "air", "state": "draft"}
        )
        dong = (
            self.env["lab.chuyen.line"]
            .with_user(self.cs)
            .create(
                {
                    "chuyen_id": chuyen.id,
                    "sale_order_id": don_ban.id,
                    "so_kien": 3,
                    "khoi_luong_kg": 12.5,
                }
            )
        )
        self.assertTrue(dong.exists(), "CS phải tạo được dòng chuyến trên web (D2).")
        self.assertEqual(chuyen.line_ids, dong)

        # CS cũng phải SỬA được chuyến (mở chuyến) — nhưng KHÔNG xoá (ACL unlink = 0).
        chuyen.with_user(self.cs).write({"so_booking": "CS-GOM-CHUYEN-WEB"})
        with self.assertRaises(AccessError):
            dong.with_user(self.cs).unlink()

    # ------------------------------------------------------------------ (c)
    def test_c_nhan_tai_quay_sinh_phieu_nhap_va_kien(self):
        job = self.env["lab.yeu.cau.lay.hang"].create(
            {
                "name": "LH/BATCH/0002",
                "partner_id": self.shop.id,
                "kenh_nhan": "tai_kho",
                "nguoi_giao_ten": "Anh Ba",
                "nguoi_giao_sdt": "0909111222",
                "so_kien_thuc_te": 2,
            }
        )
        job.with_user(self.kho).action_nhan_tai_quay()

        self.assertEqual(job.state, "confirmed")
        self.assertTrue(job.gio_nhan_tai_kho, "Phải ghi mốc nhận tại quầy.")
        picking = job.stock_picking_id
        self.assertTrue(picking, "Luật 4: nhận hàng phải sinh chứng từ kho.")
        self.assertEqual(picking.picking_type_id.code, "incoming")
        self.assertEqual(picking.state, "done", "Nhận tại quầy phải validate ⇒ tồn kho tăng.")
        self.assertEqual(picking.lab_package_count, 2)
        self.assertEqual(len(job.kien_ids), 2, "Phải sinh 2 kiện để kho dán tem + quét.")

        # Kênh tài xế thì KHÔNG được nhận tại quầy.
        job_tai_shop = self.env["lab.yeu.cau.lay.hang"].create(
            {
                "name": "LH/BATCH/0003",
                "partner_id": self.shop.id,
                "so_kien_thuc_te": 1,
            }
        )
        self.assertEqual(job_tai_shop.kenh_nhan, "tai_shop", "Mặc định phải là kênh tài xế.")
        with self.assertRaises(UserError):
            job_tai_shop.with_user(self.kho).action_nhan_tai_quay()

        # Thiếu số kiện thực tế cũng phải chặn.
        job_thieu = self.env["lab.yeu.cau.lay.hang"].create(
            {"name": "LH/BATCH/0004", "partner_id": self.shop.id, "kenh_nhan": "tai_kho"}
        )
        with self.assertRaises(UserError):
            job_thieu.with_user(self.kho).action_nhan_tai_quay()

    # ------------------------------------------------------------------ (d)
    def test_d_kho_tao_duoc_yeu_cau_nhung_khong_tao_duoc_don_ban(self):
        job = (
            self.env["lab.yeu.cau.lay.hang"]
            .with_user(self.kho)
            .create(
                {
                    "name": "LH/BATCH/0005",
                    "partner_id": self.shop.id,
                    "kenh_nhan": "tai_kho",
                    "so_kien_thuc_te": 1,
                }
            )
        )
        self.assertTrue(job.exists(), "Kho phải TẠO được yêu cầu lấy hàng tại quầy (N14).")

        with self.assertRaises(AccessError):
            self.env["sale.order"].with_user(self.kho).create(
                {"partner_id": self.shop.id}
            )

    # ------------------------------------------------------------------ (e)
    def test_e_ve_kho_tu_sinh_don_ban_va_tim_duoc_trong_chuyen(self):
        """Yêu cầu lấy hàng để trống `sale_order_id` khi về kho phải tự sinh đơn bán `sale`
        để CS tìm được ngay ở Bước 8 (màn Chuyến) bằng mã `LH/...` hoặc mã `S0...`."""
        job = self.env["lab.yeu.cau.lay.hang"].create(
            {
                "name": "LH/26/9302",
                "partner_id": self.shop.id,
                "driver_id": self.tai_xe.id,
                "state": "picked_up",
                "so_kien_thuc_te": 1,
                "khoi_luong_uoc_kg": 4.0,
            }
        )
        self.assertFalse(job.sale_order_id)

        job.with_user(self.kho).action_confirm()

        self.assertEqual(job.state, "confirmed")
        self.assertTrue(
            job.sale_order_id,
            "Khi hàng về kho mà chưa có đơn bán thì phải tự sinh sale.order.",
        )
        self.assertEqual(
            job.sale_order_id.state,
            "sale",
            "Đơn bán phải ở trạng thái 'sale' để khớp domain của dòng chuyến.",
        )
        self.assertEqual(job.sale_order_id.origin, "LH/26/9302")
        self.assertIn("LH/26/9302", job.sale_order_id.display_name)

        # CS tìm trong dropdown "Đơn hàng của shop" bằng mã yêu cầu lấy hàng LH/26/9302
        ket_qua_tim = (
            self.env["sale.order"]
            .with_user(self.cs)
            .name_search(
                "LH/26/9302",
                args=[("state", "in", ["sale", "sent"])],
            )
        )
        ids_tim = [cap[0] for cap in ket_qua_tim]
        self.assertIn(
            job.sale_order_id.id,
            ids_tim,
            "CS gõ mã LH/26/9302 trong Chuyến phải tìm thấy đơn bán tương ứng.",
        )

        # Thêm dòng vào chuyến mà không gõ lại số kiện/kg -> tự điền từ yêu cầu đã về kho
        chuyen = self.env["lab.chuyen"].create(
            {"name": "CH/AIR/BATCH/9302", "loai_van_chuyen": "air", "state": "open"}
        )
        dong = (
            self.env["lab.chuyen.line"]
            .with_user(self.cs)
            .create(
                {
                    "chuyen_id": chuyen.id,
                    "sale_order_id": job.sale_order_id.id,
                }
            )
        )
        self.assertEqual(dong.so_kien, 1)
        self.assertEqual(dong.khoi_luong_kg, 4.0)
