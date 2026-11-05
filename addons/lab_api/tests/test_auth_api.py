# -*- coding: utf-8 -*-
"""HttpCase cho đường ĐĂNG NHẬP BẰNG MẬT KHẨU: `POST /lab/api/v1/auth/login`.

Vì sao file này tồn tại — đây là lỗ hổng test đã để một bug thật lọt qua 46 test xanh:

    `tests/test_driver_api.py` luôn gọi `self.authenticate(login, password)` của
    `HttpCase` (tức `/web/session/authenticate` native) TRƯỚC, rồi mới gọi
    `/lab/api/v1/auth/login` **không kèm login/password**. Như vậy nó chỉ chạy
    nhánh "đã có phiên" của controller. Nhánh mà app KMP thật sự dùng — gửi
    `{login, password}` trong body để controller tự tạo phiên — **chưa từng được
    test**, nên chữ ký `Session.authenticate` của bản build 17.0 đang chạy
    (cũ: `(dbname, login, password)`; bản refactor dùng dict `credential`) lệch
    với code mà test vẫn xanh.

Ba khẳng định (AGENTS.md Luật 7 + docs/02 §6.3):

* đúng mật khẩu -> 200, `ok: true`, có `token`, `tokenType = "LabToken"`;
* sai mật khẩu  -> 401, `error.code = "AUTH_FAILED"`;
* không gửi gì  -> 401, `error.code = "AUTH_REQUIRED"`.

Tài khoản được tạo ngay trong test (portal + group_driver, có mật khẩu) nên test
chạy đúng trên **DB sạch**, không phụ thuộc `taixe01` của dữ liệu demo.
"""

import hashlib
import json

from odoo.tests import HttpCase, new_test_user, tagged


@tagged("post_install", "-at_install")
class TestDangNhapApi(HttpCase):
    MAT_KHAU = "Lab@Auth2026"

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.tai_xe = new_test_user(
            cls.env,
            login="api_auth_taixe",
            password=cls.MAT_KHAU,
            groups="base.group_portal,lab_core.group_driver",
        )

    # ------------------------------------------------------------------ helpers
    def _xoa_phien(self):
        """Xoá cookie phiên để request kế tiếp CHẮC CHẮN là ẩn danh.

        `HttpCase` dùng lại một ``requests.Session`` cho cả class: nếu test chạy
        trước đã đăng nhập thì cookie ``session_id`` còn đó và controller sẽ đi
        vào nhánh "đã có phiên" (200) thay vì nhánh 401 AUTH_REQUIRED — test sẽ
        xanh giả. Xoá cookie là cách rẻ nhất để khẳng định đúng trạng thái.
        """
        phien = getattr(self, "session", None)
        if phien is not None and hasattr(phien, "cookies"):
            phien.cookies.clear()

    def _goi_login(self, du_lieu):
        """POST JSON thẳng vào endpoint (route đã có ``csrf=False``)."""
        return self.url_open(
            "/lab/api/v1/auth/login",
            data=json.dumps(du_lieu or {}),
            headers={"Content-Type": "application/json"},
        )

    # ------------------------------------------------------------------ tests
    def test_dang_nhap_dung_mat_khau_tra_token_va_token_type_lab(self):
        self._xoa_phien()
        res = self._goi_login({"login": self.tai_xe.login, "password": self.MAT_KHAU})
        self.assertEqual(res.status_code, 200, res.text)

        body = res.json()
        self.assertTrue(body["ok"], body)
        du_lieu = body["data"]
        self.assertTrue(du_lieu["token"], "phải trả token gốc đúng 1 lần cho client")
        self.assertGreaterEqual(len(du_lieu["token"]), 32)
        self.assertEqual(du_lieu["tokenType"], "LabToken")
        self.assertEqual(du_lieu["uid"], self.tai_xe.id)
        self.assertTrue(du_lieu["expiresAt"])

        # DB chỉ giữ HASH của token, KHÔNG giữ token gốc (docs/02 §6.3).
        self.env.invalidate_all()
        self.assertEqual(
            self.tai_xe.lab_api_token_hash,
            hashlib.sha256(du_lieu["token"].encode()).hexdigest(),
        )

    def test_dang_nhap_sai_mat_khau_tra_401_auth_failed(self):
        self._xoa_phien()
        res = self._goi_login({"login": self.tai_xe.login, "password": "sai-mat-khau"})
        self.assertEqual(res.status_code, 401, res.text)
        body = res.json()
        self.assertFalse(body["ok"])
        self.assertEqual(body["error"]["code"], "AUTH_FAILED")

    def test_dang_nhap_khong_du_thong_tin_tra_401_auth_required(self):
        # "Không body" theo nghĩa: body rỗng, không có login/password.
        self._xoa_phien()
        res = self._goi_login({})
        self.assertEqual(res.status_code, 401, res.text)
        body = res.json()
        self.assertFalse(body["ok"])
        self.assertEqual(body["error"]["code"], "AUTH_REQUIRED")
