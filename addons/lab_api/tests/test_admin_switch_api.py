# -*- coding: utf-8 -*-
"""HttpCase cho **"chuyển tài khoản"** (``/admin/accounts`` + ``/admin/switch``) — Luật 7.

Cách chạy (orchestrator):
    odoo -c /etc/odoo/odoo.conf -d odoo17_lab \\
         -u lab_api --test-tags=/lab_api --stop-after-init

Bảy nhóm khẳng định:

a. ``/admin/accounts``: chỉ phiên **được phép chuyển** gọi được (người khác 403 +
   câu tiếng Việt); danh sách chỉ gồm **tài khoản demo** (có nhóm ``lab_core.*``),
   loại admin và user không nhóm lab, sắp theo ``login``, mỗi phần tử đúng
   ``{login,name,roles}``.
b. ``/admin/switch``: đổi được **phiên thật** sang tài khoản đích — trả cùng shape
   với ``/auth/login`` (thêm ``login``, ``canSwitch=True``), cấp **token mới** dùng
   được ngay; token admin cũ -> 401.
c. **Chuyển LIÊN TỤC** (yêu cầu UX của chủ dự án): admin -> taixe01 -> cs01, bước
   thứ hai dùng đúng token + phiên của taixe01 (đã được admin chuyển) -> 200; và
   ``/admin/accounts`` khi đang là taixe01 cũng 200. Dấu phải SỐNG qua ``rotate``.
d. **Đăng nhập trực tiếp KHÔNG có quyền chuyển**: login bằng mật khẩu của taixe01
   -> ``canSwitch=false`` và cả hai route ``/admin/*`` trả 403. Gọi ``/auth/login``
   trên phiên vừa được chuyển cũng **xoá dấu** (dropdown mất).
e. Lỗi đầu vào: thiếu ``login`` -> 400 ``VALIDATION``; login không tồn tại -> 404
   ``NOT_FOUND``; login có thật nhưng không thuộc bộ demo -> 403 ``FORBIDDEN``.
f. ``roles`` trong ``/auth/login``: quản trị viên có ``"admin"`` ở **ĐẦU** danh sách
   và ``canSwitch=true``.
g. Người không phải admin gọi ``/admin/switch`` -> 403 và **không** đổi được phiên.

Quyết định của file test (giống ``test_cs_kho_api.py``): tài khoản tạo bằng
``new_test_user`` (chạy được trên DB sạch, không phụ thuộc tài khoản seed), token
``X-Lab-Token`` **seed sẵn** trong ``setUpClass`` để không đụng rate limit 30
lượt/phút của ``/auth/login`` (file này gọi login thật 3 lượt: nhóm d và f).
"""

import hashlib
import json

from datetime import timedelta

from odoo import fields
from odoo.tests import HttpCase, new_test_user, tagged

#: Mật khẩu chung của user tạo trong test (không phải secret — fixture).
MAT_KHAU = "Lab@Admin2026"

#: Thông điệp 403 mà MỌI endpoint ``/admin/*`` phải trả (hợp đồng với FE-01).
THONG_DIEP_403 = "Chức năng chuyển tài khoản chỉ dành cho quản trị viên."


@tagged("post_install", "-at_install")
class TestAdminSwitchApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop Admin Switch Kiểm Thử"})

        # 1) "Quản trị viên" theo ĐÚNG định nghĩa của dự án: thành viên
        #    `base.group_system` ("Settings"). Không dùng tài khoản `admin` của DB
        #    để test chạy được cả trên DB không có demo data.
        cls.quan_tri = new_test_user(
            cls.env,
            login="api_admin_switch_admin",
            password=MAT_KHAU,
            groups="base.group_user,base.group_system",
        )
        # 2) Tài khoản đích: tài xế demo (portal + nhóm tài xế) — có 1 việc đang làm
        #    để chứng minh `jobsToday` của phiên mới là của CHÍNH tài xế.
        cls.tai_xe = new_test_user(
            cls.env,
            login="api_admin_switch_taixe",
            password=MAT_KHAU,
            groups="base.group_portal,lab_core.group_driver",
        )
        # 3) User nội bộ KHÔNG có nhóm lab nào: "có thật nhưng không thuộc bộ demo".
        cls.khong_nhom = new_test_user(
            cls.env,
            login="api_admin_switch_khong_nhom",
            password=MAT_KHAU,
            groups="base.group_user",
        )

        # 4) Tài khoản thứ hai của bộ demo: CS — đích của bước chuyển LIÊN TIẾP
        #    (admin -> taixe01 -> cs01) để chứng minh dropdown dùng được nhiều lần.
        cls.cs = new_test_user(
            cls.env,
            login="api_admin_switch_cs",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_cs",
        )

        # Token 8 giờ có sẵn (đúng 2 field mà /auth/login và /admin/switch ghi).
        cls.token_theo_login = {}
        for nguoi_dung in (cls.quan_tri, cls.tai_xe, cls.khong_nhom):
            token = "token-admin-kiem-thu-%s" % nguoi_dung.login
            nguoi_dung.write(
                {
                    "lab_api_token_hash": hashlib.sha256(token.encode()).hexdigest(),
                    "lab_api_token_expiry": fields.Datetime.now() + timedelta(hours=8),
                }
            )
            cls.token_theo_login[nguoi_dung.login] = token

        cls.emp = cls.env["hr.employee"].create(
            {"name": "Tài xế Admin Switch", "user_id": cls.tai_xe.id}
        )
        # Việc đang ở trạng thái `assigned` -> nằm trong `jobsToday` của tài xế.
        cls.job = cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "partner_id": cls.shop.id,
                "driver_id": cls.emp.id,
                "dia_chi_lay": "12 Nguyễn Huệ, Quận 1",
                "state": "assigned",
            }
        )

    # ------------------------------------------------------------------ helpers
    def _vao(self, nguoi_dung):
        """Mở phiên Odoo cho ``nguoi_dung`` và trả token đã seed ở ``setUpClass``."""
        self.authenticate(nguoi_dung.login, MAT_KHAU)
        return self.token_theo_login[nguoi_dung.login]

    def _goi(self, duong_dan, token=None, du_lieu=None, get=False):
        """Gọi endpoint và ĐỒNG BỘ session_id mới sau mỗi lần `/admin/switch`.

        VÌ SAO (RV-01 2026-11-03): `/admin/switch` cố ý XOAY `sid` để chống session
        fixation (odoo/http.py: `should_rotate`). Trình mở HTTP của HttpCase giữ
        cookie cũ, nên request kế tiếp mang `session_id` đã bị vô hiệu ⇒ Odoo tạo
        phiên RỖNG ⇒ `/auth/login` trả 401 `AUTH_REQUIRED` (3 test đỏ oan).
        Đây là sửa TEST theo đúng hành vi sản phẩm (dùng cookie mới), KHÔNG hạ
        chuẩn bảo mật của endpoint.
        """
        headers = {"Content-Type": "application/json"}
        if token:
            headers["X-Lab-Token"] = token
        if get:
            res = self.url_open(duong_dan, headers=headers)
        else:
            res = self.url_open(duong_dan, data=json.dumps(du_lieu or {}), headers=headers)
        sid_moi = res.cookies.get("session_id")
        if sid_moi:
            # Phải XOÁ HẾT cookie session_id cũ rồi set lại y như `HttpCase.authenticate`
            # (`self.opener.cookies['session_id'] = sid`). Lý do: sau khi server xoay sid,
            # trình mở có THÊM một cookie session_id mới (domain localhost) bên cạnh cookie
            # cũ (domain rỗng) — requests gửi cả hai và server dùng cookie "cụ thể" hơn ⇒
            # vẫn vào phiên cũ đã bị xoá ⇒ "Odoo Session Expired"/401. Xem ghi chú cùng
            # vấn đề trong `odoo/tests/common.py::HttpCase.authenticate`.
            for cookie in list(self.opener.cookies):
                if cookie.name == "session_id":
                    self.opener.cookies.clear(cookie.domain, cookie.path, cookie.name)
            self.opener.cookies["session_id"] = sid_moi
        return res

    # ============================================== (a) GET /admin/accounts
    def test_chi_admin_va_chi_tai_khoan_demo_trong_danh_sach(self):
        token = self._vao(self.quan_tri)
        res = self._goi("/lab/api/v1/admin/accounts", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        body = res.json()
        self.assertTrue(body["ok"])
        self.assertIsNone(body["error"])

        muc = body["data"]["items"]
        self.assertTrue(muc, "danh sách phải có ít nhất tài khoản tài xế của test")
        for item in muc:
            self.assertEqual(
                set(item),
                {"login", "name", "roles"},
                "mỗi tài khoản chỉ trả {login, name, roles}",
            )
            # Không có tài khoản hệ thống nào lọt vào danh sách chuyển.
            self.assertNotIn(item["login"], ("admin", "public", "__system__", "demo"))

        logins = [item["login"] for item in muc]
        self.assertEqual(logins, sorted(logins), "sắp theo login")
        self.assertIn(self.tai_xe.login, logins)
        self.assertNotIn(self.quan_tri.login, logins, "không cho chuyển SANG admin")
        self.assertNotIn(
            self.khong_nhom.login, logins, "user không có nhóm lab nào không thuộc bộ demo"
        )

        tai_xe = next(x for x in muc if x["login"] == self.tai_xe.login)
        self.assertEqual(tai_xe["roles"], ["driver"])
        self.assertEqual(tai_xe["name"], self.tai_xe.name)

    def test_nguoi_khong_phai_admin_goi_accounts_tra_403(self):
        token = self._vao(self.tai_xe)
        res = self._goi("/lab/api/v1/admin/accounts", token=token, get=True)
        self.assertEqual(res.status_code, 403, res.text)
        body = res.json()
        self.assertFalse(body["ok"])
        self.assertEqual(body["error"]["code"], "FORBIDDEN")
        self.assertEqual(body["error"]["message"], THONG_DIEP_403)

    # ================================================ (b) POST /admin/switch
    def test_switch_doi_phien_va_cap_token_moi_cho_tai_khoan_dich(self):
        token_admin = self._vao(self.quan_tri)
        res = self._goi(
            "/lab/api/v1/admin/switch",
            token=token_admin,
            du_lieu={"login": self.tai_xe.login},
        )
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]

        # Cùng shape với /auth/login (+ `login` của tài khoản đích + `canSwitch`).
        for khoa in (
            "token",
            "tokenType",
            "expiresAt",
            "uid",
            "name",
            "roles",
            "driver_id",
            "driver_user_id",
            "driver_name",
            "jobsToday",
            "login",
            "canSwitch",
        ):
            self.assertIn(khoa, du_lieu, "thiếu khoá %s của phiên" % khoa)
        self.assertEqual(du_lieu["tokenType"], "LabToken")
        self.assertEqual(du_lieu["uid"], self.tai_xe.id)
        self.assertEqual(du_lieu["login"], self.tai_xe.login)
        self.assertEqual(du_lieu["name"], self.tai_xe.name)
        self.assertEqual(du_lieu["roles"], ["driver"])
        self.assertIs(
            du_lieu["canSwitch"],
            True,
            "phiên do admin chuyển sang vẫn được chuyển tiếp -> dropdown phải hiện",
        )
        self.assertNotEqual(
            du_lieu["token"], token_admin, "phải cấp token MỚI cho tài khoản đích"
        )
        self.assertEqual(
            du_lieu["jobsToday"], 1, "jobsToday đếm việc của CHÍNH tài khoản đích"
        )

        # Token mới dùng được ngay trên namespace của tài khoản đích...
        res_tai_xe = self._goi(
            "/lab/api/v1/driver/jobs", token=du_lieu["token"], get=True
        )
        self.assertEqual(res_tai_xe.status_code, 200, res_tai_xe.text)
        # /driver/jobs trả `data` là MẢNG job (không phải {items}) — xem driver.py.
        self.assertEqual(
            [x["id"] for x in res_tai_xe.json()["data"]],
            [self.job.id],
            "tài xế chỉ thấy việc của mình (ir.rule)",
        )
        # ...còn token CŨ của admin không còn dùng được với phiên đã đổi.
        res_token_cu = self._goi(
            "/lab/api/v1/admin/accounts", token=token_admin, get=True
        )
        self.assertEqual(res_token_cu.status_code, 401, res_token_cu.text)
        self.assertEqual(res_token_cu.json()["error"]["code"], "TOKEN_INVALID")

        # Đang là tài xế nhưng phiên DO ADMIN CHUYỂN -> vẫn gọi được /admin/* (đúng
        # yêu cầu UX: dropdown còn trên thanh trên cùng để chuyển tiếp).
        res_sau_khi_chuyen = self._goi(
            "/lab/api/v1/admin/accounts", token=du_lieu["token"], get=True
        )
        self.assertEqual(res_sau_khi_chuyen.status_code, 200, res_sau_khi_chuyen.text)
        self.assertIn(
            self.cs.login,
            [item["login"] for item in res_sau_khi_chuyen.json()["data"]["items"]],
            "phiên đã được chuyển vẫn xem được danh sách để chuyển TIẾP",
        )

    def test_chuyen_lien_tiep_admin_taixe01_roi_cs01(self):
        """Yêu cầu UX chủ dự án: admin -> taixe01 -> cs01, mỗi bước 1 click.

        Bước 2 dùng ĐÚNG token + phiên của taixe01 (không quay lại admin, không nhập
        lại mật khẩu) -> chứng minh dấu ``session[lab_switch_nguon]`` sống qua
        ``rotate()`` và ``xac_thuc_token`` vẫn pass với token của tài khoản đích.
        """
        token_admin = self._vao(self.quan_tri)
        buoc1 = self._goi(
            "/lab/api/v1/admin/switch",
            token=token_admin,
            du_lieu={"login": self.tai_xe.login},
        )
        self.assertEqual(buoc1.status_code, 200, buoc1.text)
        phien_tai_xe = buoc1.json()["data"]
        self.assertEqual(phien_tai_xe["uid"], self.tai_xe.id)
        self.assertIs(phien_tai_xe["canSwitch"], True)

        # Bước 2: token của TÀI XẾ + phiên hiện tại (đã rotate sang tài xế).
        buoc2 = self._goi(
            "/lab/api/v1/admin/switch",
            token=phien_tai_xe["token"],
            du_lieu={"login": self.cs.login},
        )
        self.assertEqual(buoc2.status_code, 200, buoc2.text)
        phien_cs = buoc2.json()["data"]
        self.assertEqual(phien_cs["uid"], self.cs.id)
        self.assertEqual(phien_cs["login"], self.cs.login)
        self.assertEqual(phien_cs["roles"], ["cs"])
        self.assertEqual(phien_cs["name"], self.cs.name)
        self.assertIs(phien_cs["canSwitch"], True)
        self.assertNotEqual(
            phien_cs["token"], phien_tai_xe["token"], "mỗi bước cấp token riêng"
        )

        # Vẫn chuyển tiếp được lần nữa từ phiên CS (chuỗi không bị đứt).
        res_accounts = self._goi(
            "/lab/api/v1/admin/accounts", token=phien_cs["token"], get=True
        )
        self.assertEqual(res_accounts.status_code, 200, res_accounts.text)
        logins = [item["login"] for item in res_accounts.json()["data"]["items"]]
        self.assertIn(self.tai_xe.login, logins)
        self.assertIn(self.cs.login, logins)

        # Token của bước 1 đã bị thay (mỗi lần switch ghi hash mới cho tài khoản
        # đích) -> không dùng lại được.
        res_token_cu = self._goi(
            "/lab/api/v1/admin/accounts", token=phien_tai_xe["token"], get=True
        )
        self.assertEqual(res_token_cu.status_code, 401, res_token_cu.text)

    def test_dang_nhap_truc_tiep_khong_co_quyen_chuyen(self):
        """Đăng nhập trực tiếp bằng mật khẩu của chính tài xế -> KHÔNG có dropdown."""
        res_login = self._goi(
            "/lab/api/v1/auth/login",
            du_lieu={"login": self.tai_xe.login, "password": MAT_KHAU},
        )
        self.assertEqual(res_login.status_code, 200, res_login.text)
        du_lieu = res_login.json()["data"]
        self.assertEqual(du_lieu["uid"], self.tai_xe.id)
        self.assertEqual(du_lieu["roles"], ["driver"])
        self.assertIs(
            du_lieu["canSwitch"],
            False,
            "đăng nhập trực tiếp bằng tài xế -> app phải ẨN dropdown chuyển vai trò",
        )

        token = du_lieu["token"]
        res_accounts = self._goi("/lab/api/v1/admin/accounts", token=token, get=True)
        self.assertEqual(res_accounts.status_code, 403, res_accounts.text)
        self.assertEqual(res_accounts.json()["error"]["message"], THONG_DIEP_403)

        res_switch = self._goi(
            "/lab/api/v1/admin/switch",
            token=token,
            du_lieu={"login": self.cs.login},
        )
        self.assertEqual(res_switch.status_code, 403, res_switch.text)
        self.assertEqual(res_switch.json()["error"]["code"], "FORBIDDEN")

    def test_auth_login_xoa_dau_phien_do_admin_chuyen(self):
        """``/auth/login`` phải XOÁ dấu chuyển phiên (yêu cầu hợp đồng mục 3)."""
        token_admin = self._vao(self.quan_tri)
        buoc1 = self._goi(
            "/lab/api/v1/admin/switch",
            token=token_admin,
            du_lieu={"login": self.tai_xe.login},
        )
        self.assertEqual(buoc1.status_code, 200, buoc1.text)
        self.assertIs(buoc1.json()["data"]["canSwitch"], True)

        # Gọi /auth/login trên CHÍNH phiên đó (đường "đã có phiên, cấp token mới").
        res_login = self._goi("/lab/api/v1/auth/login")
        self.assertEqual(res_login.status_code, 200, res_login.text)
        du_lieu = res_login.json()["data"]
        self.assertEqual(du_lieu["uid"], self.tai_xe.id)
        self.assertIs(
            du_lieu["canSwitch"],
            False,
            "đăng nhập lại -> dấu chuyển phiên bị xoá, dropdown phải mất",
        )

        res_accounts = self._goi(
            "/lab/api/v1/admin/accounts", token=du_lieu["token"], get=True
        )
        self.assertEqual(res_accounts.status_code, 403, res_accounts.text)
        self.assertEqual(res_accounts.json()["error"]["message"], THONG_DIEP_403)

    def test_switch_thieu_login_tra_400(self):
        token = self._vao(self.quan_tri)
        duong_dan = "/lab/api/v1/admin/switch"
        for body in ({}, {"login": "   "}):
            res = self._goi(duong_dan, token=token, du_lieu=body)
            self.assertEqual(res.status_code, 400, res.text)
            self.assertEqual(res.json()["error"]["code"], "VALIDATION")

    def test_switch_login_khong_ton_tai_tra_404(self):
        token = self._vao(self.quan_tri)
        res = self._goi(
            "/lab/api/v1/admin/switch",
            token=token,
            du_lieu={"login": "khong_he_co_tai_khoan_nay"},
        )
        self.assertEqual(res.status_code, 404, res.text)
        self.assertEqual(res.json()["error"]["code"], "NOT_FOUND")

    def test_switch_login_co_that_nhung_khong_phai_demo_tra_403(self):
        token = self._vao(self.quan_tri)
        res = self._goi(
            "/lab/api/v1/admin/switch",
            token=token,
            du_lieu={"login": self.khong_nhom.login},
        )
        self.assertEqual(res.status_code, 403, res.text)
        body = res.json()
        self.assertEqual(body["error"]["code"], "FORBIDDEN")
        self.assertIn("tài khoản demo", body["error"]["message"])

    def test_nguoi_khong_phai_admin_khong_chuyen_duoc_phien(self):
        token = self._vao(self.khong_nhom)
        res = self._goi(
            "/lab/api/v1/admin/switch",
            token=token,
            du_lieu={"login": self.tai_xe.login},
        )
        self.assertEqual(res.status_code, 403, res.text)
        self.assertEqual(res.json()["error"]["code"], "FORBIDDEN")
        self.assertEqual(res.json()["error"]["message"], THONG_DIEP_403)
        # Phiên vẫn là của user cũ: gọi /admin/accounts vẫn 403 (không thành tài xế).
        res_lai = self._goi("/lab/api/v1/admin/accounts", token=token, get=True)
        self.assertEqual(res_lai.status_code, 403, res_lai.text)

    # ============================================ (f) roles có "admin" ở đầu
    def test_roles_trong_login_co_admin_o_dau(self):
        self.authenticate(self.quan_tri.login, MAT_KHAU)
        res = self._goi("/lab/api/v1/auth/login")
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        self.assertEqual(
            du_lieu["roles"],
            ["admin"],
            "quản trị viên: 'admin' đứng ĐẦU và là vai trò duy nhất của tài khoản này",
        )
        self.assertIs(
            du_lieu["canSwitch"],
            True,
            "admin đăng nhập -> app phải hiện dropdown chuyển vai trò",
        )
        self.assertNotIn(
            "login", du_lieu, "/auth/login giữ shape cũ (không có khoá `login`)"
        )
