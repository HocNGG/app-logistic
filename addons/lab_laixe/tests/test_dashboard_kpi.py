# -*- coding: utf-8 -*-
"""TransactionCase cho ``lab.yeu.cau.lay.hang.lab_dashboard_kpi``.

Đây là cửa vào RPC mà **giao diện OWL** (backend) gọi để lấy số liệu dashboard
(``orm.call("lab.yeu.cau.lay.hang", "lab_dashboard_kpi", ["7d"])``) — không đi
qua HTTP token. Endpoint ``GET /lab/api/v1/sep/kpi`` đã có HttpCase riêng
(``lab_api/tests/test_sep_kpi_api.py``); ở đây dùng TransactionCase vì cần:

* kiểm **chính method** + payload (khoá cứng, để FE không bị đổi sau lưng);
* đổi user bằng ``with_user`` để chứng minh ACL/``ir.rule`` quyết định dữ liệu
  (không sudo), thứ mà HttpCase chỉ kiểm được qua thêm một tầng HTTP.

Khẳng định (Luật 7):
* trả đủ ``{range, kpi, trend, byState, recent}``, ``kpi`` đủ 5 khoá,
  ``byState`` đủ 7 state đúng thứ tự, ``recent`` chỉ có khoá an toàn;
* ``range=30d`` -> đúng 30 phần tử trend (trục ngày liên tục, ngày cuối = hôm nay);
* ``range`` sai -> ``UserError`` tiếng Việt (KHÔNG tự mặc định — chart sai mà im
  lặng còn tệ hơn báo lỗi);
* tài xế (``group_driver``) gọi được nhưng **chỉ thấy việc của mình** — ir.rule
  lọc, không sudo, và ``avgLoadPct`` = 0 vì tài xế không có ACL ``lab.chuyen``;
* user **không có ACL** đọc model -> Odoo tự raise ``AccessError``.

User test đặt ``tz = "UTC"`` để test tự tính biên ngày bằng
``datetime.combine(..., time.min)`` (đổi múi giờ có test riêng ở HttpCase của
endpoint, chỉ kiểm hình dạng nên không cần lặp ở đây).
"""

from datetime import datetime, timedelta

from odoo import fields
from odoo.exceptions import AccessError, UserError
from odoo.tests import TransactionCase, new_test_user, tagged

#: Khoá theo hợp đồng với app KMP — test khoá cứng từng bộ.
KHOA_DATA = {"range", "kpi", "trend", "byState", "recent"}
KHOA_KPI = {
    "pendingAssign",
    "enRoute",
    "pickedUpToday",
    "confirmedToday",
    "avgLoadPct",
    # 2026-11-03: payload có thêm khối CHUYẾN (dải KPI màn "Chuyến")
    "tripsTotal",
    "tripsOpen",
    "tripsBooked",
    "tripsDeparted",
}
KHOA_TREND = {"date", "created", "picked", "confirmed"}
KHOA_BY_STATE = {"state", "label", "count"}
KHOA_RECENT = {
    "id",
    "ref",
    "shop",
    "stateLabel",
    "driverName",
    "windowFrom",
    "updatedAt",
}

#: Thứ tự state machine — ``byState`` phải trả đúng thứ tự này.
THU_TU_STATE = [
    "new",
    "assigned",
    "en_route",
    "picked_up",
    "confirmed",
    "cancelled",
    "failed",
]


@tagged("post_install", "-at_install")
class TestDashboardKpi(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Dashboard RPC"})

        cls.quan_ly = new_test_user(
            cls.env,
            login="kpi_rpc_manager",
            password="kpi_rpc_manager",
            groups="base.group_user,lab_core.group_manager",
        )
        cls.tai_xe_user = new_test_user(
            cls.env,
            login="kpi_rpc_taixe",
            password="kpi_rpc_taixe",
            groups="base.group_portal,lab_core.group_driver",
        )
        # Internal User KHÔNG thuộc nhóm lab nào -> không có dòng ACL đọc model.
        cls.khong_quyen = new_test_user(
            cls.env,
            login="kpi_rpc_khong_quyen",
            password="kpi_rpc_khong_quyen",
            groups="base.group_user",
        )
        # Ngày cắt theo tz người dùng -> đặt UTC cho cả 3 user để test tự tính
        # được biên ngày (và không phụ thuộc tz công ty).
        (cls.quan_ly | cls.tai_xe_user | cls.khong_quyen).write({"tz": "UTC"})

        cls.tai_xe = cls.env["hr.employee"].create(
            {"name": "Tài xế gọi RPC dashboard", "user_id": cls.tai_xe_user.id}
        )

        cls.hom_nay = fields.Datetime.now()
        cls.hom_nay_ngay = cls.hom_nay.date()
        cls.job_new = cls._tao_job("new")
        cls.job_new_2 = cls._tao_job("new")
        cls.job_dang_di = cls._tao_job("assigned")
        cls.job_da_lay = cls._tao_job("picked_up", ngay_giao=cls.hom_nay)
        cls.job_ve_kho = cls._tao_job("confirmed", ngay_xac_nhan=cls.hom_nay)
        cls.job_cua_tai_xe = cls._tao_job("assigned", driver_id=cls.tai_xe.id)

    @classmethod
    def _tao_job(cls, state, ngay_giao=False, ngay_xac_nhan=False, driver_id=False):
        return cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "partner_id": cls.shop.id,
                "dia_chi_lay": "18 Nguyễn Trãi, Quận 1",
                "so_kien_thuc_te": 2,
                "khoi_luong_uoc_kg": 8.0,
                "state": state,
                "ngay_giao": ngay_giao,
                "ngay_xac_nhan": ngay_xac_nhan,
                "driver_id": driver_id,
            }
        )

    # ------------------------------------------------------------------ helpers
    def _job(self, nguoi_dung=None):
        Job = self.env["lab.yeu.cau.lay.hang"]
        return Job.with_user(nguoi_dung) if nguoi_dung else Job

    def _kpi(self, nguoi_dung=None, chuoi_range=None):
        return self._job(nguoi_dung).lab_dashboard_kpi(chuoi_range)

    def _dem(self, **domain):
        return self.env["lab.yeu.cau.lay.hang"].search_count(
            [(khoa, "=", gia_tri) for khoa, gia_tri in domain.items()]
        )

    # ================================================================ happy path
    def test_tra_du_khoa_va_gia_tri_khop(self):
        du_lieu = self._kpi(self.quan_ly)

        # --- đúng hợp đồng payload (test khoá cứng) ---
        self.assertEqual(set(du_lieu), KHOA_DATA)
        self.assertEqual(du_lieu["range"], "7d", "mặc định phải là 7d")
        self.assertEqual(set(du_lieu["kpi"]), KHOA_KPI)

        # --- trend: 7 phần tử, ngày liên tục, kết thúc ở HÔM NAY ---
        trend = du_lieu["trend"]
        self.assertEqual(len(trend), 7)
        ngay = [datetime.strptime(muc["date"], "%Y-%m-%d").date() for muc in trend]
        self.assertEqual(ngay, sorted(ngay), "trục ngày phải tăng dần")
        self.assertEqual(ngay[0], self.hom_nay_ngay - timedelta(days=6))
        self.assertEqual(ngay[-1], self.hom_nay_ngay, "ngày cuối phải là hôm nay")
        for muc in trend:
            self.assertEqual(set(muc), KHOA_TREND)
            for khoa in ("created", "picked", "confirmed"):
                self.assertIsInstance(muc[khoa], int)
                self.assertGreaterEqual(muc[khoa], 0)

        # --- byState: đủ 7 trạng thái, đúng thứ tự, khớp search_count ---
        by_state = du_lieu["byState"]
        self.assertEqual([muc["state"] for muc in by_state], THU_TU_STATE)
        for muc in by_state:
            self.assertEqual(set(muc), KHOA_BY_STATE)
            self.assertTrue(muc["label"], "nhãn tiếng Việt không được rỗng")
            self.assertEqual(muc["count"], self._dem(state=muc["state"]))
        self.assertEqual(
            sum(muc["count"] for muc in by_state),
            self.env["lab.yeu.cau.lay.hang"].search_count([]),
        )

        # --- 5 KPI khớp dữ liệu + 2 số "hôm nay" lấy từ chính trend ---
        kpi = du_lieu["kpi"]
        self.assertEqual(kpi["pendingAssign"], self._dem(state="new"))
        self.assertGreaterEqual(kpi["pendingAssign"], 2)
        self.assertEqual(
            kpi["enRoute"],
            self._dem(state="assigned") + self._dem(state="en_route"),
        )
        self.assertEqual(kpi["pickedUpToday"], trend[-1]["picked"])
        self.assertEqual(kpi["confirmedToday"], trend[-1]["confirmed"])
        self.assertGreaterEqual(kpi["pickedUpToday"], 1)
        self.assertGreaterEqual(kpi["confirmedToday"], 1)
        self.assertIsInstance(kpi["avgLoadPct"], int)
        self.assertGreaterEqual(kpi["avgLoadPct"], 0)

        # --- recent: chỉ khoá an toàn, không trùng id, tối đa 10 dòng ---
        recent = du_lieu["recent"]
        self.assertLessEqual(len(recent), 10)
        self.assertGreaterEqual(len(recent), 1)
        for muc in recent:
            self.assertEqual(set(muc), KHOA_RECENT, "payload recent có khoá lạ")
        self.assertEqual(len({muc["id"] for muc in recent}), len(recent))

    # ============================================================== tham số range
    def test_range_30d_co_30_phan_tu_trend(self):
        du_lieu = self._kpi(self.quan_ly, "30d")
        self.assertEqual(du_lieu["range"], "30d")
        trend = du_lieu["trend"]
        self.assertEqual(len(trend), 30, "range=30d phải có đúng 30 phần tử")
        ngay = [datetime.strptime(muc["date"], "%Y-%m-%d").date() for muc in trend]
        self.assertEqual(ngay, sorted(ngay))
        self.assertEqual(ngay[-1], self.hom_nay_ngay)
        self.assertEqual(len(self._kpi(self.quan_ly, "today")["trend"]), 1)

    def test_range_sai_raise_usererror_tieng_viet(self):
        with self.assertRaises(UserError) as bat:
            self._kpi(self.quan_ly, "90d")

        thong_diep = str(bat.exception)
        # Chứng minh đúng lỗi THAM SỐ (không phải AccessError — lớp con của
        # UserError), và thông điệp là tiếng Việt có dấu.
        self.assertIn("90d", thong_diep)
        self.assertIn("không hợp lệ", thong_diep)
        self.assertIn("today, 7d, 30d", thong_diep)

    # ================================================================== phân quyền
    def test_tai_xe_bi_ir_rule_gioi_han_khong_phai_sudo(self):
        """``group_driver`` CÓ ACL đọc -> method chạy, nhưng ``ir.rule`` chỉ cho
        thấy việc của chính tài xế (nếu ai đó thêm ``sudo()``, test này đỏ ngay).
        """
        Job = self._job(self.tai_xe_user)
        self.assertEqual(
            Job.search_count([]), 1, "ir.rule phải lọc còn đúng việc của tài xế"
        )
        self.assertEqual(Job.search([]).ids, [self.job_cua_tai_xe.id])

        du_lieu = Job.lab_dashboard_kpi()
        self.assertEqual(
            sum(muc["count"] for muc in du_lieu["byState"]),
            1,
            "tổng byState phải bằng số việc ir.rule cho thấy (1), không phải toàn DB",
        )
        self.assertEqual(
            [muc["id"] for muc in du_lieu["recent"]], [self.job_cua_tai_xe.id]
        )
        self.assertEqual(du_lieu["kpi"]["pendingAssign"], 0)
        self.assertEqual(du_lieu["kpi"]["enRoute"], 1)
        self.assertEqual(
            du_lieu["kpi"]["avgLoadPct"],
            0,
            "tài xế không có ACL lab.chuyen -> 0, KHÔNG được 500",
        )

    def test_user_khong_co_acl_doc_raise_access_error(self):
        """Không có quyền đọc model -> Odoo tự raise ``AccessError`` (method
        không sudo, không bắt lỗi quyền để tự xử lý).
        """
        Job = self._job(self.khong_quyen)
        self.assertFalse(
            Job.check_access_rights("read", raise_exception=False),
            "user test phải thật sự không có ACL đọc lab.yeu.cau.lay.hang",
        )
        with self.assertRaises(AccessError):
            Job.lab_dashboard_kpi()
