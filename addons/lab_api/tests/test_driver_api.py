# -*- coding: utf-8 -*-
"""HttpCase cho 4 endpoint tài xế + login (docs/02 §6.2, ADR-004) — Luật 7.

Cách chạy (orchestrator):
    odoo -c /etc/odoo/odoo.conf -d odoo17_lab \\
         -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api \\
         --test-tags=/lab_api,/lab_laixe,/lab_chuyen --stop-after-init

Điểm quan trọng của test:
* tài xế phải có **phiên Odoo** (cookie ``session_id``) — 17.0 không có Bearer;
* mỗi request phải kèm ``X-Lab-Token`` (token 8 giờ, hash trong DB);
* tài xế A **không** thấy/ không nhận được việc của tài xế B (ir.rule, không sudo);
* upload sai magic bytes -> 400; token hết hạn -> 401.
"""

import base64
import hashlib
import json
from datetime import timedelta

from odoo import fields
from odoo.tests import HttpCase, new_test_user, tagged

# PNG 1x1 hợp lệ (magic bytes 89 50 4E 47 ...) — dùng làm ảnh + chữ ký trong test.
ANH_PNG_B64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGA"
    "hKmMIQAAAABJRU5ErkJggg=="
)


@tagged("post_install", "-at_install")
class TestDriverApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop API Kiểm Thử"})

        cls.user_a = new_test_user(
            cls.env,
            login="api_taixe_a",
            password="api_taixe_a",
            groups="base.group_portal,lab_core.group_driver",
        )
        cls.user_b = new_test_user(
            cls.env,
            login="api_taixe_b",
            password="api_taixe_b",
            groups="base.group_portal,lab_core.group_driver",
        )
        cls.emp_a = cls.env["hr.employee"].create(
            {"name": "Tài xế API A", "user_id": cls.user_a.id}
        )
        cls.emp_b = cls.env["hr.employee"].create(
            {"name": "Tài xế API B", "user_id": cls.user_b.id}
        )

        cls._so_job = 0
        # Mỗi test một job riêng: test không phụ thuộc thứ tự chạy (skills/11 §7).
        cls.job_list = cls._tao_job("assigned", cls.emp_a)
        cls.job_accept = cls._tao_job("assigned", cls.emp_a)
        cls.job_capture = cls._tao_job("en_route", cls.emp_a)
        cls.job_capture_loi = cls._tao_job("en_route", cls.emp_a)
        cls.job_capture_trung = cls._tao_job("en_route", cls.emp_a)
        # App KMP giữ 3 khung ảnh: 3 ảnh phải lưu đủ 3 attachment, 4 ảnh bị chặn.
        cls.job_capture_3anh = cls._tao_job("en_route", cls.emp_a)
        cls.job_capture_3anh_trung = cls._tao_job("en_route", cls.emp_a)
        cls.job_capture_4anh = cls._tao_job("en_route", cls.emp_a)
        cls.job_fail = cls._tao_job("en_route", cls.emp_a)
        cls.job_token_het_han = cls._tao_job("assigned", cls.emp_a)
        cls.job_cua_b = cls._tao_job("assigned", cls.emp_b)

    @classmethod
    def _tao_job(cls, state, driver):
        cls._so_job += 1
        so = cls._so_job
        return cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "partner_id": cls.shop.id,
                "driver_id": driver.id,
                "dia_chi_lay": "18 Nguyễn Trãi, Quận 1",
                "lien_he_ten": "Chị Hạnh",
                "lien_he_sdt": "0903 111 222",
                "khung_gio_tu": fields.Datetime.now().replace(
                    hour=1, minute=30, second=0, microsecond=0
                ),
                "khung_gio_den": fields.Datetime.now().replace(
                    hour=3, minute=0, second=0, microsecond=0
                ),
                "so_kien_du_kien": 10 + so,
                "state": state,
            }
        )

    # ------------------------------------------------------------------ helpers
    def _goi(self, duong_dan, token=None, du_lieu=None, get=False):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["X-Lab-Token"] = token
        if get:
            return self.url_open(duong_dan, headers=headers)
        return self.url_open(duong_dan, data=json.dumps(du_lieu or {}), headers=headers)

    def _dang_nhap(self, login="api_taixe_a", mat_khau="api_taixe_a"):
        """Đăng nhập native (phiên) rồi đổi lấy token 8 giờ của lab_api."""
        self.authenticate(login, mat_khau)
        res = self._goi("/lab/api/v1/auth/login")
        self.assertEqual(res.status_code, 200, res.text)
        return res.json()["data"]

    def _token(self, **kwargs):
        return self._dang_nhap(**kwargs)["token"]

    # ================================================================== login
    def test_login_tra_token_8h_va_thong_tin_tai_xe(self):
        du_lieu = self._dang_nhap()
        self.assertTrue(du_lieu["token"])
        self.assertGreaterEqual(len(du_lieu["token"]), 32)
        self.assertEqual(du_lieu["uid"], self.user_a.id)
        # Tài xế portal KHÔNG đọc được hr.employee (Odoo 17 chặn) -> driver_id = False
        # có chủ đích; định danh tài xế đi qua driver_user_id + driver_name.
        self.assertFalse(du_lieu["driver_id"])
        self.assertEqual(du_lieu["driver_user_id"], self.user_a.id)
        self.assertEqual(du_lieu["driver_name"], self.user_a.name)
        self.assertTrue(du_lieu["expiresAt"])
        # DB chỉ giữ HASH, không giữ token gốc (docs/02 §6.3)
        self.env.invalidate_all()
        self.assertEqual(
            self.user_a.lab_api_token_hash,
            hashlib.sha256(du_lieu["token"].encode()).hexdigest(),
        )

    # ================================================================== jobs
    def test_jobs_tra_viec_cua_minh_va_khong_lo_du_lieu_thua(self):
        token = self._token()
        res = self._goi("/lab/api/v1/driver/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        body = res.json()
        self.assertTrue(body["ok"])
        ids = [job["id"] for job in body["data"]]
        self.assertIn(self.job_list.id, ids)
        self.assertNotIn(self.job_cua_b.id, ids, "ir.rule: không thấy việc tài xế khác")

        job = next(j for j in body["data"] if j["id"] == self.job_list.id)
        self.assertEqual(job["ref"], self.job_list.name)
        self.assertEqual(job["state"], "assigned")
        self.assertEqual(job["shop"], "Shop API Kiểm Thử")
        self.assertEqual(job["contactName"], "Chị Hạnh")
        # OQ-23: giờ hẹn là chuỗi HH:mm, KHÔNG phải ISO
        self.assertRegex(job["windowFrom"], r"^\d{2}:\d{2}$")
        self.assertNotIn("T", job["windowFrom"])
        # OQ-22: có nguồn thật thì mới trả số kiện dự kiến
        self.assertEqual(job["expectedPackages"], self.job_list.so_kien_du_kien)
        # Không lộ field nội bộ
        for khoa in ("partner_id", "sale_order_id", "driver_id", "khung_gio_tu"):
            self.assertNotIn(khoa, job)

    def test_jobs_thieu_token_tra_401(self):
        self._token()
        res = self._goi("/lab/api/v1/driver/jobs", get=True)
        self.assertEqual(res.status_code, 401)
        self.assertEqual(res.json()["error"]["code"], "TOKEN_REQUIRED")

    def test_jobs_token_sai_tra_401(self):
        self._token()
        res = self._goi("/lab/api/v1/driver/jobs", token="sai-token-123456", get=True)
        self.assertEqual(res.status_code, 401)
        self.assertEqual(res.json()["error"]["code"], "TOKEN_INVALID")

    def test_jobs_token_het_han_tra_401(self):
        token = "token-het-han-" + "a" * 32
        self.user_a.write(
            {
                "lab_api_token_hash": hashlib.sha256(token.encode()).hexdigest(),
                "lab_api_token_expiry": fields.Datetime.now() - timedelta(hours=1),
            }
        )
        self.authenticate("api_taixe_a", "api_taixe_a")
        res = self._goi("/lab/api/v1/driver/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 401)
        self.assertEqual(res.json()["error"]["code"], "TOKEN_EXPIRED")

    # ================================================================== accept
    def test_accept_chuyen_sang_en_route(self):
        token = self._token()
        res = self._goi(
            "/lab/api/v1/driver/jobs/%d/accept" % self.job_accept.id, token=token
        )
        self.assertEqual(res.status_code, 200, res.text)
        self.assertEqual(res.json()["data"]["state"], "en_route")
        self.env.invalidate_all()
        self.assertEqual(self.job_accept.state, "en_route")

    def test_accept_viec_cua_tai_xe_khac_tra_404(self):
        token = self._token()
        res = self._goi(
            "/lab/api/v1/driver/jobs/%d/accept" % self.job_cua_b.id, token=token
        )
        self.assertEqual(res.status_code, 404)
        self.assertEqual(res.json()["error"]["code"], "NOT_FOUND")

    # ================================================================== capture
    def test_capture_luu_anh_chu_ky_so_kien_kg(self):
        token = self._token()
        res = self._goi(
            "/lab/api/v1/driver/jobs/%d/capture" % self.job_capture.id,
            token=token,
            du_lieu={
                "photoBase64": ANH_PNG_B64,
                "signatureBase64": ANH_PNG_B64,
                "packages": 7,
                "weightKg": 42.5,
                "clientUuid": "uuid-capture-1",
            },
        )
        self.assertEqual(res.status_code, 200, res.text)
        self.assertEqual(res.json()["data"]["state"], "picked_up")
        self.assertEqual(len(res.json()["data"]["attachment_ids"]), 1)

        self.env.invalidate_all()
        self.assertEqual(self.job_capture.state, "picked_up")
        self.assertEqual(self.job_capture.so_kien_thuc_te, 7)
        self.assertEqual(self.job_capture.khoi_luong_uoc_kg, 42.5)
        self.assertTrue(self.job_capture.chu_ky, "chữ ký lưu ở field binary chu_ky")
        self.assertEqual(len(self.job_capture.anh_ids), 1, "ảnh lưu ở ir.attachment")
        self.assertEqual(self.job_capture.anh_ids.res_model, "lab.yeu.cau.lay.hang")
        self.assertEqual(self.job_capture.anh_ids.res_id, self.job_capture.id)

    def test_capture_anh_sai_dinh_dang_tra_400(self):
        token = self._token()
        res = self._goi(
            "/lab/api/v1/driver/jobs/%d/capture" % self.job_capture_loi.id,
            token=token,
            du_lieu={
                "photoBase64": base64.b64encode(b"day khong phai anh").decode(),
                "signatureBase64": ANH_PNG_B64,
                "packages": 3,
                "weightKg": 5,
            },
        )
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.json()["error"]["code"], "VALIDATION")
        self.env.invalidate_all()
        self.assertEqual(self.job_capture_loi.state, "en_route", "state không được đổi")

    def test_capture_gui_lai_khong_tao_anh_thu_hai(self):
        token = self._token()
        du_lieu = {
            "photoBase64": ANH_PNG_B64,
            "signatureBase64": ANH_PNG_B64,
            "packages": 2,
            "weightKg": 3,
            "clientUuid": "uuid-trung",
        }
        duong_dan = "/lab/api/v1/driver/jobs/%d/capture" % self.job_capture_trung.id
        lan_1 = self._goi(duong_dan, token=token, du_lieu=du_lieu)
        self.assertEqual(lan_1.status_code, 200, lan_1.text)
        lan_2 = self._goi(duong_dan, token=token, du_lieu=du_lieu)
        self.assertEqual(lan_2.status_code, 200, lan_2.text)
        self.assertTrue(lan_2.json()["data"]["duplicate"])
        self.env.invalidate_all()
        self.assertEqual(len(self.job_capture_trung.anh_ids), 1)

    def test_capture_3_anh_tao_3_attachment_va_luu_ghi_chu(self):
        """App giữ 3 khung ảnh -> API phải nhận & trả ĐỦ 3 (trước đây chỉ nhận 1)."""
        token = self._token()
        ghi_chu = "3 kiện, 1 kiện móp góc — đã chụp 3 ảnh."
        res = self._goi(
            "/lab/api/v1/driver/jobs/%d/capture" % self.job_capture_3anh.id,
            token=token,
            du_lieu={
                "photos": [ANH_PNG_B64, ANH_PNG_B64, ANH_PNG_B64],
                "signatureBase64": ANH_PNG_B64,
                "packages": 3,
                "weightKg": 12.0,
                "note": ghi_chu,
                "clientUuid": "uuid-3anh",
            },
        )
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        self.assertEqual(du_lieu["state"], "picked_up")
        self.assertEqual(
            len(du_lieu["attachment_ids"]), 3, "API phải trả id của CẢ 3 ảnh"
        )

        self.env.invalidate_all()
        self.assertEqual(len(self.job_capture_3anh.anh_ids), 3)
        self.assertEqual(self.job_capture_3anh.so_kien_thuc_te, 3)
        self.assertEqual(self.job_capture_3anh.ghi_chu_tai_xe, ghi_chu)

    def test_capture_4_anh_tra_400_va_khong_doi_trang_thai(self):
        token = self._token()
        duong_dan = "/lab/api/v1/driver/jobs/%d/capture" % self.job_capture_4anh.id
        res = self._goi(
            duong_dan,
            token=token,
            du_lieu={
                "photos": [ANH_PNG_B64] * 4,
                "signatureBase64": ANH_PNG_B64,
                "packages": 3,
                "weightKg": 5,
            },
        )
        self.assertEqual(res.status_code, 400, res.text)
        self.assertEqual(res.json()["error"]["code"], "VALIDATION")
        self.env.invalidate_all()
        self.assertEqual(self.job_capture_4anh.state, "en_route", "state không đổi")
        self.assertFalse(self.job_capture_4anh.anh_ids, "không được để lại rác")

        # Ghi chú quá 500 ký tự cũng là lỗi validate (400), không phải 500.
        res_note = self._goi(
            duong_dan,
            token=token,
            du_lieu={
                "photos": [ANH_PNG_B64],
                "signatureBase64": ANH_PNG_B64,
                "packages": 3,
                "weightKg": 5,
                "note": "x" * 501,
            },
        )
        self.assertEqual(res_note.status_code, 400, res_note.text)
        self.assertEqual(res_note.json()["error"]["code"], "VALIDATION")

    def test_capture_3_anh_gui_lai_cung_uuid_khong_tao_them(self):
        token = self._token()
        du_lieu = {
            "photos": [ANH_PNG_B64, ANH_PNG_B64, ANH_PNG_B64],
            "signatureBase64": ANH_PNG_B64,
            "packages": 2,
            "weightKg": 4,
            "clientUuid": "uuid-3anh-trung",
        }
        duong_dan = (
            "/lab/api/v1/driver/jobs/%d/capture" % self.job_capture_3anh_trung.id
        )
        lan_1 = self._goi(duong_dan, token=token, du_lieu=du_lieu)
        self.assertEqual(lan_1.status_code, 200, lan_1.text)
        self.assertEqual(len(lan_1.json()["data"]["attachment_ids"]), 3)

        lan_2 = self._goi(duong_dan, token=token, du_lieu=du_lieu)
        self.assertEqual(lan_2.status_code, 200, lan_2.text)
        self.assertTrue(lan_2.json()["data"]["duplicate"])
        # Vẫn trả đủ id của bộ ảnh ĐÃ có để app đối chiếu.
        self.assertEqual(len(lan_2.json()["data"]["attachment_ids"]), 3)
        self.env.invalidate_all()
        self.assertEqual(
            len(self.job_capture_3anh_trung.anh_ids),
            3,
            "gửi lại cùng clientUuid KHÔNG được tạo bộ ảnh thứ hai",
        )

    # ================================================================== fail
    def test_fail_bat_buoc_ly_do_va_doi_trang_thai(self):
        token = self._token()
        duong_dan = "/lab/api/v1/driver/jobs/%d/fail" % self.job_fail.id

        thieu_ly_do = self._goi(duong_dan, token=token, du_lieu={})
        self.assertEqual(thieu_ly_do.status_code, 400)
        self.assertEqual(thieu_ly_do.json()["error"]["code"], "VALIDATION")

        res = self._goi(duong_dan, token=token, du_lieu={"reason": "Shop đóng cửa"})
        self.assertEqual(res.status_code, 200, res.text)
        self.assertEqual(res.json()["data"]["state"], "failed")
        self.env.invalidate_all()
        self.assertEqual(self.job_fail.ly_do_that_bai, "Shop đóng cửa")
