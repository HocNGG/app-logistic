# -*- coding: utf-8 -*-
"""HttpCase cho `GET /lab/api/v1/sep/kpi` — dashboard của Sếp/Trưởng phòng.

Cách chạy (orchestrator):
    odoo -c /etc/odoo/odoo.conf -d odoo17_lab \\
         -u lab_core,lab_laixe,lab_api \\
         --test-tags=/lab_core,/lab_laixe,/lab_api --stop-after-init

Khẳng định (Luật 7):
* `sep` (group_director) và `manager` (group_manager) -> 200, đủ 5 khoá, `trend`
  dài **đúng** số ngày của range (`today`=1, `7d`=7, `30d`=30), ngày cuối = hôm nay;
* `range` sai -> 400 `VALIDATION` (cố ý KHÔNG tự mặc định — chart sai mà im lặng
  còn tệ hơn báo lỗi);
* số liệu khớp `search_count` (tính trên dữ liệu của CHÍNH test, không dựa vào
  seed demo) — gồm `byState` đủ 7 trạng thái kể cả 0;
* ngày KHÔNG có dữ liệu vẫn phải có phần tử trong `trend` (trục liên tục cho chart);
* tài xế / CS -> 403 tiếng Việt;
* `recent` chỉ trả field an toàn và sắp xếp `write_date` giảm dần.

Vì sao user test có `tz = "UTC"`: ngày "hôm nay" của API cắt theo `res.users.tz`;
đặt UTC để test tự tính được biên ngày bằng `datetime.combine(..., time.min)` mà
không phải lặp lại phép đổi múi giờ của controller (đổi múi giờ có test riêng ý
nghĩa ở `test_..._theo_mui_gio` — chỉ kiểm hình dạng, không kiểm số).

Token `X-Lab-Token` được seed sẵn trong `setUpClass` (đúng 2 field endpoint login
ghi) để không đụng rate limit 30 lượt/phút của `/auth/login`.
"""

import hashlib
import json
from datetime import datetime, time as gio_ngay, timedelta

from odoo import fields
from odoo.tests import HttpCase, new_test_user, tagged

MAT_KHAU = "Lab@KpiDashboard2026"

#: 5 khoá KPI + 7 khoá mỗi dòng `recent` theo hợp đồng — test khoá cứng để FE
#: không bị đổi payload sau lưng.
# 2026-11-03: payload KPI có thêm khối CHUYẾN (dùng cho dải KPI màn "Chuyến") —
# thay đổi TƯƠNG THÍCH NGƯỢC, app/OWL cũ chỉ đọc khoá nó biết. Test cập nhật theo.
KHOA_KPI = {
    "pendingAssign",
    "enRoute",
    "pickedUpToday",
    "confirmedToday",
    "avgLoadPct",
    "tripsTotal",
    "tripsOpen",
    "tripsBooked",
    "tripsDeparted",
}
KHOA_RECENT = {
    "id",
    "ref",
    "shop",
    "stateLabel",
    "driverName",
    "windowFrom",
    "updatedAt",
}
#: Thứ tự state machine — `byState` phải trả đúng thứ tự này.
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
class TestSepKpiApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Dashboard Kiểm Thử"})

        cls.manager = new_test_user(
            cls.env,
            login="api_kpi_manager",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_manager",
        )
        cls.sep = new_test_user(
            cls.env,
            login="api_kpi_sep",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_director",
        )
        cls.cs = new_test_user(
            cls.env,
            login="api_kpi_cs",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_cs",
        )
        cls.tai_xe = new_test_user(
            cls.env,
            login="api_kpi_taixe",
            password=MAT_KHAU,
            groups="base.group_portal,lab_core.group_driver",
        )
        # Ngày cắt theo tz người dùng -> đặt UTC để test tự tính được biên ngày.
        (cls.manager | cls.sep).write({"tz": "UTC"})

        cls.token_theo_login = {}
        for nguoi_dung in (cls.manager, cls.sep, cls.cs, cls.tai_xe):
            token = "token-kpi-%s" % nguoi_dung.login
            nguoi_dung.write(
                {
                    "lab_api_token_hash": hashlib.sha256(token.encode()).hexdigest(),
                    "lab_api_token_expiry": fields.Datetime.now() + timedelta(hours=8),
                }
            )
            cls.token_theo_login[nguoi_dung.login] = token

        # ---- Dữ liệu mốc thời gian có kiểm soát (múi giờ UTC) ----
        cls.hom_nay = fields.Datetime.now().date()
        cls.ngay_cu = cls.hom_nay - timedelta(days=3)
        cls.gio_hom_nay = fields.Datetime.now()
        cls.gio_ngay_cu = datetime.combine(cls.ngay_cu, gio_ngay(hour=9))

        cls.job_new_1 = cls._tao_job("new")
        cls.job_new_2 = cls._tao_job("new")
        cls.job_dang_di = cls._tao_job("assigned")
        cls.job_da_lay_hom_nay = cls._tao_job("picked_up", ngay_giao=cls.gio_hom_nay)
        cls.job_da_lay_hom_qua = cls._tao_job("picked_up", ngay_giao=cls.gio_ngay_cu)
        cls.job_da_ve_kho = cls._tao_job(
            "confirmed",
            ngay_giao=cls.gio_ngay_cu,
            ngay_xac_nhan=cls.gio_hom_nay,
        )

    @classmethod
    def _tao_job(cls, state, ngay_giao=False, ngay_xac_nhan=False):
        return cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "partner_id": cls.shop.id,
                "dia_chi_lay": "18 Nguyễn Trãi, Quận 1",
                "lien_he_sdt": "0903 111 222",
                "so_kien_thuc_te": 3,
                "khoi_luong_uoc_kg": 12.0,
                "state": state,
                "ngay_giao": ngay_giao,
                "ngay_xac_nhan": ngay_xac_nhan,
            }
        )

    # ------------------------------------------------------------------ helpers
    def _goi(self, duong_dan, token=None, get=True, du_lieu=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["X-Lab-Token"] = token
        if get:
            return self.url_open(duong_dan, headers=headers)
        return self.url_open(duong_dan, data=json.dumps(du_lieu or {}), headers=headers)

    def _vao(self, nguoi_dung):
        self.authenticate(nguoi_dung.login, MAT_KHAU)
        return self.token_theo_login[nguoi_dung.login]

    def _kpi(self, nguoi_dung, chuoi_range=None):
        token = self._vao(nguoi_dung)
        duong_dan = "/lab/api/v1/sep/kpi"
        if chuoi_range:
            duong_dan += "?range=%s" % chuoi_range
        res = self._goi(duong_dan, token=token)
        self.assertEqual(
            res.status_code,
            200,
            "%s (GET, user=%s) -> %s | body: %s"
            % (duong_dan, nguoi_dung.login, res.status_code, (res.text or "")[:400]),
        )
        return res.json()["data"]

    def _dem(self, **domain):
        Job = self.env["lab.yeu.cau.lay.hang"]
        return Job.search_count([(k, "=", v) for k, v in domain.items()])

    # ================================================================ happy path
    def test_sep_kpi_du_khoa_va_trend_7_ngay(self):
        du_lieu = self._kpi(self.sep)

        self.assertEqual(du_lieu["range"], "7d", "mặc định phải là 7d")
        self.assertEqual(set(du_lieu), {"range", "kpi", "trend", "byState", "recent"})
        self.assertEqual(set(du_lieu["kpi"]), KHOA_KPI)

        # trend: đúng 7 phần tử, trục ngày liên tục, kết thúc ở HÔM NAY
        trend = du_lieu["trend"]
        self.assertEqual(len(trend), 7)
        ngay = [datetime.strptime(muc["date"], "%Y-%m-%d").date() for muc in trend]
        self.assertEqual(ngay, sorted(ngay), "trục ngày phải tăng dần")
        self.assertEqual(ngay[0], self.hom_nay - timedelta(days=6))
        self.assertEqual(ngay[-1], self.hom_nay, "ngày cuối phải là hôm nay")
        for muc in trend:
            self.assertEqual(set(muc), {"date", "created", "picked", "confirmed"})
            for khoa in ("created", "picked", "confirmed"):
                self.assertIsInstance(muc[khoa], int)
                self.assertGreaterEqual(muc[khoa], 0)

    def test_manager_kpi_va_range_today_1_phan_tu(self):
        du_lieu = self._kpi(self.manager, "today")
        self.assertEqual(du_lieu["range"], "today")
        self.assertEqual(len(du_lieu["trend"]), 1, "range=today phải có đúng 1 phần tử")
        self.assertEqual(du_lieu["trend"][0]["date"], self.hom_nay.isoformat())

    def test_range_30d_co_30_phan_tu(self):
        du_lieu = self._kpi(self.sep, "30d")
        self.assertEqual(du_lieu["range"], "30d")
        self.assertEqual(len(du_lieu["trend"]), 30)

    def test_range_sai_tra_400_validation(self):
        token = self._vao(self.sep)
        res = self._goi("/lab/api/v1/sep/kpi?range=90d", token=token)
        self.assertEqual(res.status_code, 400, res.text)
        body = res.json()
        self.assertFalse(body["ok"])
        self.assertEqual(body["error"]["code"], "VALIDATION")
        self.assertIn("90d", body["error"]["message"])

    # ====================================================== số liệu đúng & đủ ngày
    def test_kpi_khop_search_count_va_trend_du_7_phan_tu(self):
        du_lieu = self._kpi(self.sep, "7d")
        kpi = du_lieu["kpi"]

        # --- 3 số đếm theo trạng thái: khớp search_count ---
        self.assertEqual(kpi["pendingAssign"], self._dem(state="new"))
        self.assertGreaterEqual(kpi["pendingAssign"], 2, "phải đếm 2 việc new của test")
        self.assertEqual(
            kpi["enRoute"],
            sum(self._dem(state=ma) for ma in ("assigned", "en_route")),
        )

        # --- byState: đủ 7 trạng thái, đúng thứ tự state machine, khớp search_count ---
        by_state = du_lieu["byState"]
        self.assertEqual([muc["state"] for muc in by_state], THU_TU_STATE)
        for muc in by_state:
            self.assertTrue(muc["label"], "label tiếng Việt không được rỗng")
            self.assertEqual(muc["count"], self._dem(state=muc["state"]))
        self.assertEqual(
            sum(muc["count"] for muc in by_state),
            self.env["lab.yeu.cau.lay.hang"].search_count([]),
        )

        # --- 2 số "hôm nay": bằng phần tử cuối của trend, và đếm đúng việc của test ---
        hom_nay = du_lieu["trend"][-1]
        self.assertEqual(kpi["pickedUpToday"], hom_nay["picked"])
        self.assertEqual(kpi["confirmedToday"], hom_nay["confirmed"])
        self.assertGreaterEqual(kpi["pickedUpToday"], 1, "phải đếm việc lấy hôm nay")
        self.assertGreaterEqual(kpi["confirmedToday"], 1, "phải đếm việc về kho hôm nay")
        # ngay_giao của việc "3 ngày trước" phải rơi đúng vào ô của ngày đó
        o_ngay_cu = next(
            muc for muc in du_lieu["trend"] if muc["date"] == self.ngay_cu.isoformat()
        )
        self.assertGreaterEqual(o_ngay_cu["picked"], 1)
        # ngày KHÔNG có dữ liệu vẫn phải có phần tử (chart cần trục liên tục)
        o_ngay_rong = next(
            muc
            for muc in du_lieu["trend"]
            if muc["date"] == (self.hom_nay - timedelta(days=2)).isoformat()
        )
        # Ngày cách đây 2 ngày VẪN PHẢI có phần tử trong trend (chart cần trục liên tục
        # để không bị gãy). KHÔNG hard-code "phải bằng 0": trên DB demo (305 việc trải
        # 45 ngày) ngày đó có thể có dữ liệu thật — sửa 2026-11-03 vì test đỏ oan.
        self.assertIsInstance(o_ngay_rong["picked"], int)
        self.assertIsInstance(o_ngay_rong["confirmed"], int)
        self.assertIsInstance(o_ngay_rong["created"], int)

        # --- avgLoadPct: số nguyên không âm (giá trị thật do model chuyến tính) ---
        self.assertIsInstance(kpi["avgLoadPct"], int)
        self.assertGreaterEqual(kpi["avgLoadPct"], 0)

    # ================================================================== phân quyền
    def test_tai_xe_va_cs_goi_kpi_tra_403(self):
        for nguoi_dung in (self.tai_xe, self.cs):
            token = self._vao(nguoi_dung)
            res = self._goi("/lab/api/v1/sep/kpi", token=token)
            self.assertEqual(
                res.status_code,
                403,
                "user=%s -> %s | %s" % (nguoi_dung.login, res.status_code, res.text),
            )
            body = res.json()
            self.assertEqual(body["error"]["code"], "FORBIDDEN")
            self.assertIn("Trưởng phòng", body["error"]["message"])

    # ==================================================================== recent
    def test_recent_chi_tra_field_an_toan_va_moi_nhat(self):
        du_lieu = self._kpi(self.sep)
        recent = du_lieu["recent"]
        self.assertLessEqual(len(recent), 10)
        self.assertGreaterEqual(len(recent), 1)

        for muc in recent:
            self.assertEqual(set(muc), KHOA_RECENT, "payload recent có khoá lạ")
            # không lộ dữ liệu nội bộ / giá
            for khoa_cam in (
                "partner_id",
                "sale_order_id",
                "driver_id",
                "khoi_luong_uoc_kg",
                "price",
                "amount_total",
            ):
                self.assertNotIn(khoa_cam, muc)
            self.assertRegex(muc["updatedAt"], r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$")

        # mới nhất trước: chuỗi "YYYY-MM-DD HH:mm" so sánh được trực tiếp
        cac_moc = [muc["updatedAt"] for muc in recent]
        self.assertEqual(cac_moc, sorted(cac_moc, reverse=True))
        self.assertEqual(len({muc["id"] for muc in recent}), len(recent), "không trùng id")

    def test_recent_thay_viec_vua_doi(self):
        """Việc vừa được ghi phải nằm trong `recent` (write_date desc)."""
        self.job_new_1.write({"lien_he_sdt": "0909 999 999"})
        # flush: request HTTP chạy trên CÙNG cursor nhưng env khác, nên phải đẩy
        # write xuống SQL trước khi search(order="write_date desc") đọc lại.
        self.env.flush_all()
        du_lieu = self._kpi(self.sep)
        self.assertIn(
            self.job_new_1.id,
            [muc["id"] for muc in du_lieu["recent"]],
            "việc vừa cập nhật phải lên đầu bảng hoạt động gần đây",
        )

    def test_kpi_ngay_chac_chan_rong_van_dem_ra_0(self):
        """Test THẬT cho ca "ngày không có dữ liệu" (RV-01 yêu cầu, 2026-11-03).

        Vì sao test ở tầng helper: trend của endpoint chỉ có 7/30 ngày gần nhất, không
        thể chọn một ngày "chắc chắn rỗng" trong đó (bộ dữ liệu demo trải ~45 ngày nên
        ngày nào cũng có thể có việc). Bộ đếm theo ngày mới là chỗ quyết định "ngày rỗng
        vẫn trả 0 để chart có phần tử" — nên kiểm trực tiếp nó với một ngày CHẮC CHẮN
        rỗng (`hom_nay - 400 ngày`, ngoài phạm vi mọi seed) rồi khẳng định cả 3 bộ đếm
        đều 0 và mốc UTC vẫn hợp lệ (dau < cuoi).
        """
        from odoo.addons.lab_laixe.models.lab_yeu_cau_lay_hang import (
            _cac_ngay,
            _dem_trong_ngay,
            _moc_ngay_utc,
            _mui_gio,
        )

        mui_gio = _mui_gio(self.env)
        ngay_rong = self.hom_nay - timedelta(days=400)
        dau, cuoi = _moc_ngay_utc(ngay_rong, mui_gio)
        self.assertLess(dau, cuoi, "Mốc ngày phải là khoảng nửa mở hợp lệ.")

        Job = self.env["lab.yeu.cau.lay.hang"]
        self.assertEqual(_dem_trong_ngay(Job, "create_date", dau, cuoi), 0)
        self.assertEqual(_dem_trong_ngay(Job, "ngay_giao", dau, cuoi), 0)
        self.assertEqual(_dem_trong_ngay(Job, "ngay_xac_nhan", dau, cuoi), 0)

        # Trục ngày vẫn SINH RA phần tử cho ngày rỗng (đó là điều chart cần).
        self.assertEqual(_cac_ngay(1, ngay_rong), [ngay_rong])