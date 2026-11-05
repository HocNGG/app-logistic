# -*- coding: utf-8 -*-
"""Test hành vi 6 method chuyển trạng thái của yêu cầu lấy hàng (docs/02 §3.2).

Luật 7: mỗi transition có case đúng + case sai; case `action_confirm` phải chứng
minh **có sinh chứng từ** (stock.picking) — không chỉ đổi state (AGENTS.md Luật 4).

Ba test cuối file là test HỒI QUY cho lỗi E2E thật (2026-10-29): user không có
email làm chatter raise và cuốn theo cả giao dịch nghiệp vụ. Chốt chặn nằm ở
``LabYeuCauLayHang.message_post`` (override), nên test patch
``mail.thread.MailThread.message_post`` — tức bản GỐC mà override gọi vào — chứ
không patch chính override (patch chính nó là tự vô hiệu hoá thứ đang cần kiểm).

``test_tracking_precommit_khong_lam_mat_giao_dich`` KHÔNG kiểm chốt chặn đó: nó
khoá sự thật đo được ở 17.0 là tracking mặc định (không ``_track_subtype``) đi
``_message_log`` chứ không đi ``message_post`` — xem docstring của test.
"""

from unittest.mock import patch

from odoo.addons.mail.models.mail_thread import MailThread
from odoo.exceptions import UserError
from odoo.tests import TransactionCase, new_test_user, tagged

#: Câu lỗi ĐÃ ĐO trong E2E thật — dùng lại nguyên văn để test không "dễ hơn thực tế".
LOI_GUI_MAIL = (
    "Unable to send message, please configure the sender's email address."
)
LOGGER_MODEL = "odoo.addons.lab_laixe.models.lab_yeu_cau_lay_hang"


@tagged("post_install", "-at_install")
class TestLayHangActions(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Kiểm Thử Action"})
        cls.driver = cls.env["hr.employee"].create({"name": "Tài xế Kiểm Thử"})
        cls.kho = new_test_user(
            cls.env,
            login="kho_action",
            password="kho_action",
            groups="base.group_user,lab_core.group_warehouse",
        )

    def _tao_yeu_cau(self, **vals):
        du_lieu = {"partner_id": self.shop.id, "driver_id": self.driver.id}
        du_lieu.update(vals)
        return self.env["lab.yeu.cau.lay.hang"].create(du_lieu)

    # ---------------------------------------------------------------- happy path
    def test_luong_day_du_va_sinh_phieu_nhap_kho(self):
        job = self._tao_yeu_cau()
        self.assertEqual(job.state, "new")

        job.action_assign()
        self.assertEqual(job.state, "assigned")

        job.action_start()
        self.assertEqual(job.state, "en_route")

        job.write({"so_kien_thuc_te": 5, "khoi_luong_uoc_kg": 12.5})
        job.action_pick()
        self.assertEqual(job.state, "picked_up")
        self.assertTrue(job.ngay_giao, "phải ghi thời điểm tài xế xác nhận")

        job.action_confirm()
        self.assertEqual(job.state, "confirmed")
        self.assertTrue(job.stock_picking_id, "phải SINH phiếu nhập kho (Luật 4)")
        # Mốc thời điểm confirm: dashboard Sếp đếm "hôm nay nhận được mấy việc" bằng
        # field này (controllers/sep.py) -> confirm mà không ghi là KPI sai im lặng.
        self.assertTrue(job.ngay_xac_nhan, "confirm phải ghi ngay_xac_nhan")
        self.assertEqual(job.stock_picking_id.origin, job.name)
        self.assertEqual(job.stock_picking_id.lab_package_count, 5)
        self.assertTrue(job.message_ids, "mọi lần chuyển state phải có chatter")

    def test_kho_bam_xac_nhan_tao_duoc_phieu_nhap_kho(self):
        """ACL: Kho (group_warehouse) phải bấm được 'Hàng đã về kho'."""
        job = self._tao_yeu_cau()
        job.action_assign()
        job.action_start()
        job.action_pick()

        job.with_user(self.kho).action_confirm()

        self.assertEqual(job.state, "confirmed")
        self.assertTrue(job.stock_picking_id)

    # ---------------------------------------------------------------- case lỗi
    def test_khong_gan_duoc_khi_thieu_tai_xe(self):
        job = self._tao_yeu_cau(driver_id=False)
        with self.assertRaises(UserError):
            job.action_assign()
        self.assertEqual(job.state, "new")

    def test_khong_duoc_nhay_coc_trang_thai(self):
        job = self._tao_yeu_cau()
        with self.assertRaises(UserError):
            job.action_start()  # new -> en_route
        with self.assertRaises(UserError):
            job.action_pick()  # new -> picked_up
        with self.assertRaises(UserError):
            job.action_confirm()  # new -> confirmed
        self.assertEqual(job.state, "new")

    def test_that_bai_bat_buoc_nhap_ly_do(self):
        job = self._tao_yeu_cau()
        job.action_assign()
        job.action_start()
        with self.assertRaises(UserError):
            job.action_fail()
        self.assertEqual(job.state, "en_route")

        job.action_fail(ly_do="Shop đóng cửa")
        self.assertEqual(job.state, "failed")
        self.assertEqual(job.ly_do_that_bai, "Shop đóng cửa")

    def test_huy_chi_tu_moi_hoac_da_gan(self):
        job = self._tao_yeu_cau()
        job.action_cancel()
        self.assertEqual(job.state, "cancelled")

        job2 = self._tao_yeu_cau()
        job2.action_assign()
        job2.action_start()
        with self.assertRaises(UserError):
            job2.action_cancel()
        self.assertEqual(job2.state, "en_route")

    def test_khong_tao_trung_phieu_nhap_kho(self):
        job = self._tao_yeu_cau()
        job.action_assign()
        job.action_start()
        job.action_pick()
        job.action_confirm()
        with self.assertRaises(UserError):
            job.action_confirm()

    # ------------------------------------------------- hồi quy: chatter lỗi (A)
    def _tao_tai_xe_khong_email(self, login="taixe_khong_email"):
        """User portal + group_driver KHÔNG có email (đúng tình huống E2E)."""
        tai_xe = new_test_user(
            self.env,
            login=login,
            password=login,
            groups="base.group_portal,lab_core.group_driver",
        )
        tai_xe.partner_id.email = False
        self.assertFalse(tai_xe.partner_id.email, "tiền đề: user KHÔNG có email")
        nhan_vien = self.env["hr.employee"].create(
            {"name": "Tài xế không email %s" % login, "user_id": tai_xe.id}
        )
        return tai_xe, nhan_vien

    def test_tai_xe_khong_co_email_van_chuyen_duoc_trang_thai(self):
        """Đường THẬT: tài xế portal không email bấm xác nhận -> state vẫn đổi.

        Cảnh báo về giới hạn của test này: môi trường test hiện KHÔNG tái hiện
        được lỗi gửi mail (không có người nhận/không có mail server), nên test
        này xanh **kể cả khi chưa có override**. Nó chỉ khoá hành vi nghiệp vụ
        (không raise, state đúng); phần chứng minh chốt chặn thật nằm ở
        ``test_loi_gui_chatter_khong_lam_mat_giao_dich...`` và
        ``test_tracking_precommit_...`` (patch bản gốc của ``message_post``).
        """
        tai_xe, nhan_vien = self._tao_tai_xe_khong_email()
        job = self._tao_yeu_cau(driver_id=nhan_vien.id)
        job.action_assign()
        job.action_start()
        job.write({"so_kien_thuc_te": 4, "khoi_luong_uoc_kg": 9.5})

        job.with_user(tai_xe).action_pick()  # KHÔNG được raise

        self.assertEqual(
            job.state, "picked_up", "state phải chuyển dù chatter không ghi được"
        )
        self.assertTrue(job.ngay_giao, "phải ghi thời điểm tài xế xác nhận")

    def test_loi_gui_chatter_khong_lam_mat_giao_dich_va_khong_nuot_loi_nghiep_vu(self):
        """Chốt chặn nằm ở ``message_post``: mail lỗi -> state vẫn đổi.

        Patch ``MailThread.message_post`` (class gốc) bằng ĐÚNG câu lỗi đã đo
        trong E2E. Override của model gọi ``super().message_post`` nên nếu không
        có try/except thì ``action_pick`` sẽ raise và mất giao dịch.

        Khẳng định: (1) bản gốc THẬT SỰ bị gọi (không phải test rỗng),
        (2) không raise, (3) state ``picked_up``, (4) có log warning,
        (5) lỗi NGHIỆP VỤ (sai state) vẫn raise y như cũ.
        """
        job = self._tao_yeu_cau()
        job.action_assign()
        job.action_start()
        job.write({"so_kien_thuc_te": 2, "khoi_luong_uoc_kg": 3.0})

        with patch.object(
            MailThread, "message_post", side_effect=UserError(LOI_GUI_MAIL)
        ) as gui_mail:
            with self.assertLogs(LOGGER_MODEL, level="WARNING") as nhat_ky:
                job.action_pick()  # KHÔNG được raise

        self.assertTrue(
            gui_mail.called, "message_post gốc PHẢI được gọi rồi mới bị chặn"
        )
        self.assertEqual(job.state, "picked_up")
        self.assertTrue(
            any("Không ghi được chatter" in dong for dong in nhat_ky.output),
            nhat_ky.output,
        )

        # Không nuốt lỗi nghiệp vụ: state sai vẫn raise UserError như trước.
        with self.assertRaises(UserError):
            job.action_pick()

    def test_tracking_precommit_khong_lam_mat_giao_dich(self):
        """Khoá SỰ THẬT đã đo: tracking 17.0 KHÔNG đi ``message_post``.

        Ba dòng source 17.0 (addons/mail/models/mail_thread.py + odoo/sql_db.py),
        trích để lần sau không ai phải suy đoán lại:

            # MailThread.write()
            if not self._context.get('mail_notrack'):
                self._track_prepare(self._fields)

            # MailThread._track_prepare()
            self.env.cr.precommit.add(self._track_finalize)

            # MailThread._message_track()
            if subtype:
                record.message_post(body=body, author_id=author_id,
                                    subtype_id=subtype.id,
                                    tracking_value_ids=tracking_value_ids)
            elif tracking_value_ids:
                record._message_log(body=body, author_id=author_id,
                                    tracking_value_ids=tracking_value_ids)

        Nghĩa là: tracking KHÔNG chạy trong ``write()`` mà ở **precommit**
        (``Cursor.commit()`` → ``flush()`` → ``precommit.run()``), và nó chỉ gọi
        ``message_post`` khi ``_track_subtype()`` trả về subtype. Hai model của dự
        án KHÔNG khai ``_track_subtype`` ⇒ đi nhánh ``_message_log`` — nhánh này
        tạo ``mail.message`` trực tiếp và **không gửi mail**, nên không thể ném
        "Unable to send message, please configure the sender's email address.".

        Vì vậy test này khẳng định đúng điều đó (và là TRIPWIRE): nếu một ngày ai
        thêm ``_track_subtype``, ``message_post`` sẽ được gọi ở precommit và assert
        ``assertFalse(gui_mail.called)`` sẽ đỏ — lúc đó phải kiểm tra lại là
        override ``message_post`` (xem ``LabYeuCauLayHang.message_post``) vẫn bọc
        đúng, chứ không được bỏ qua.

        KHÔNG dùng ``_track_subtype``/``_mail_track`` giả để ép nhánh
        ``message_post``: làm vậy là khoá một hành vi sản phẩm không tồn tại.
        """
        tai_xe, nhan_vien = self._tao_tai_xe_khong_email("taixe_khong_email_track")
        job = self._tao_yeu_cau(driver_id=nhan_vien.id)

        # Tiền đề của nhánh ``_message_log``: model KHÔNG khai subtype tracking.
        self.assertFalse(
            job._track_subtype({"state": "new"}),
            "model không khai _track_subtype -> _message_track dùng _message_log",
        )

        with patch.object(
            MailThread, "message_post", side_effect=UserError(LOI_GUI_MAIL)
        ) as gui_mail:
            # Ghi field tracking=True bằng CHÍNH user không có email.
            job.with_user(tai_xe).with_context(tracking_disable=False).write(
                {"state": "assigned"}
            )
            # 17.0 hoãn tracking sang precommit -> chạy hook thật (không có
            # exception nào được thoát ra, đó là một phần của khẳng định).
            self.env.cr.precommit.run()

        self.assertFalse(
            gui_mail.called,
            "nhánh tracking mặc định (không subtype) phải dùng _message_log, "
            "KHÔNG được gọi message_post. Nếu assert này đỏ: ai đó đã thêm "
            "_track_subtype — kiểm tra lại override message_post của model.",
        )
        job.invalidate_recordset()
        self.assertEqual(
            job.state, "assigned", "tracking/precommit KHÔNG được rollback state"
        )
