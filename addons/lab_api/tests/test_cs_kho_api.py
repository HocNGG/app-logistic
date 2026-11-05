# -*- coding: utf-8 -*-
"""HttpCase cho 2 màn mới của app: **CS điều phối** (`/cs/*`) và **Kho nhận hàng**
(`/kho/*`), cộng `roles` trong login — Luật 7 của AGENTS.md.

Cách chạy (orchestrator):
    odoo -c /etc/odoo/odoo.conf -d odoo17_lab \\
         -u lab_core,lab_laixe,lab_api \\
         --test-tags=/lab_core,/lab_laixe,/lab_api --stop-after-init

Bốn nhóm khẳng định (theo yêu cầu BE-01 đợt này):

a. `cs01` gọi `/cs/jobs` -> 200 và CHỈ thấy việc `new`/`assigned`; tài xế gọi
   `/cs/jobs` -> 403 (không phải 404) kèm câu tiếng Việt.
b. CS gán tài xế -> job `assigned`, `driver_user_id` đúng; gán lại khi state sai
   -> 409 `INVALID_STATE` và KHÔNG đổi tài xế.
c. Kho gọi `/kho/cho-nhan` -> chỉ thấy việc `picked_up`; xác nhận nhận hàng ->
   `confirmed` **và có `stock.picking`** đúng job/đơn hàng (AGENTS.md Luật 4).
d. `roles` trong login: tài xế có "driver", **trưởng nhóm tài xế có
   "driver_leader"**, CS có "cs", Kho có "kho", Kế toán có "ketoan", sếp có "sep",
   người không thuộc nhóm nào -> mảng rỗng.
   ⚠️ Hồi quy từ lần chạy thật: tài khoản **Kế toán** (dòng ACL
   `lab.yeu.cau.lay.hang` của nhóm này là `0,0,0,0`) và tài khoản **Internal User
   không nhóm nào** (không có dòng ACL) từng làm `/auth/login` trả **403 HTML**
   vì controller search job vô điều kiện -> AccessError. Giờ login phải 200.

Sửa đợt "Trưởng nhóm tài xế" (role phân công):

e. `GET /cs/jobs` -> `drivers` chỉ gồm nhân viên **có nhóm tài xế**, mỗi phần tử
   `{id, name, login}` — chứng minh bằng một nhân viên CÓ tài khoản nhưng KHÔNG
   thuộc nhóm tài xế (hồi quy vụ `Mitchell Admin`/`Marc Demo` lẫn vào danh sách).
f. `nhomtruong01`-tương đương (user test) gọi được **cả** `/cs/jobs` và
   `/cs/jobs/<id>/assign` như CS; tài xế thường vẫn 403.

Cộng thêm: tài khoản demo KHÔNG có nhóm kỹ thuật (`base.group_no_one` /
`base.group_system`) — hồi quy cho việc gỡ "Technical Features" ở
`lab_core/models/res_users.py::_lab_bo_nhom_ky_thuat`.

Hai quyết định kỹ thuật của file test:

* User tạo bằng `new_test_user` (có mật khẩu) -> chạy được trên **DB sạch**,
  không phụ thuộc tài khoản seed.
* Token `X-Lab-Token` được **seed sẵn trong `setUpClass`** (đúng cặp field mà
  endpoint login ghi) thay vì gọi `/auth/login` ở mọi test: endpoint login có
  rate limit 30 lượt/phút theo IP (`tools.check_rate_limit`), và bộ test đã có
  ~16 lượt gọi login từ trước — thêm ~17 lượt nữa là vượt hạn mức và làm đỏ cả
  bộ test một cách OAN. Endpoint login vẫn được kiểm đầy đủ ở
  `test_auth_api.py` và ở test `roles` bên dưới (8 lượt, trong hạn mức).
"""

import hashlib
import json
from datetime import timedelta

from odoo import fields
from odoo.tests import HttpCase, new_test_user, tagged

#: Mật khẩu chung của user tạo trong test (không phải secret — fixture).
MAT_KHAU = "Lab@CsKho2026"


@tagged("post_install", "-at_install")
class TestCsKhoApi(HttpCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.shop = cls.env["res.partner"].create({"name": "Shop CS Kho Kiểm Thử"})
        cls.don_hang = cls.env["sale.order"].create({"partner_id": cls.shop.id})

        # 7 vai trò + 1 người "không thuộc nhóm nghiệp vụ nào" (chỉ Internal User).
        # Vai trò thứ 7 là "Trưởng nhóm tài xế" (group_driver_leader) — người phân
        # công việc, thêm cùng đợt với 2 endpoint /cs/*.
        cls.cs = new_test_user(
            cls.env,
            login="api_cs_kho_cs",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_cs",
        )
        cls.kho = new_test_user(
            cls.env,
            login="api_cs_kho_kho",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_warehouse",
        )
        cls.tai_xe = new_test_user(
            cls.env,
            login="api_cs_kho_taixe",
            password=MAT_KHAU,
            groups="base.group_portal,lab_core.group_driver",
        )
        # "Trưởng nhóm tài xế" — Internal User PHÂN CÔNG việc (tương đương tài
        # khoản seed `nhomtruong01`). Đây là role MỚI của đợt này: cần vào được /cs.
        cls.truong_nhom = new_test_user(
            cls.env,
            login="api_cs_kho_nhomtruong",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_driver_leader",
        )
        cls.sep = new_test_user(
            cls.env,
            login="api_cs_kho_sep",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_director",
        )
        # Kế toán: dòng ACL của `lab.yeu.cau.lay.hang` cho nhóm này là 0,0,0,0
        # (lab_laixe/security/ir.model.access.csv) -> đăng nhập bằng tài khoản kế
        # toán từng nổ AccessError (Odoo trả trang 403 HTML). Giữ user này để khoá
        # hồi quy: login phải 200 và trả roles ["ketoan"].
        cls.ketoan = new_test_user(
            cls.env,
            login="api_cs_kho_ketoan",
            password=MAT_KHAU,
            groups="base.group_user,lab_core.group_account",
        )
        cls.khong_nhom = new_test_user(
            cls.env,
            login="api_cs_kho_khong_nhom",
            password=MAT_KHAU,
            groups="base.group_user",
        )

        # Token 8 giờ có sẵn cho từng user — đúng 2 field mà endpoint login ghi
        # (xem docstring đầu file: tránh đụng rate limit của /auth/login).
        cls.token_theo_login = {}
        for nguoi_dung in (
            cls.cs,
            cls.kho,
            cls.tai_xe,
            cls.sep,
            cls.ketoan,
            cls.khong_nhom,
            cls.truong_nhom,
        ):
            token = "token-kiem-thu-%s" % nguoi_dung.login
            nguoi_dung.write(
                {
                    "lab_api_token_hash": hashlib.sha256(token.encode()).hexdigest(),
                    "lab_api_token_expiry": fields.Datetime.now() + timedelta(hours=8),
                }
            )
            cls.token_theo_login[nguoi_dung.login] = token

        cls.emp = cls.env["hr.employee"].create(
            {"name": "Tài xế CS Kho", "user_id": cls.tai_xe.id}
        )
        # Tài xế thứ hai (chưa có tài khoản app) — dùng để chứng minh savepoint của
        # endpoint assign: gán khi state sai thì driver_id KHÔNG đổi.
        cls.emp_2 = cls.env["hr.employee"].create({"name": "Tài xế thứ hai"})
        # Nhân viên CÓ tài khoản nhưng KHÔNG thuộc nhóm tài xế (trưởng nhóm) — dùng
        # để khoá hồi quy "danh sách gán lẫn người không phải tài xế".
        cls.emp_khong_phai_tai_xe = cls.env["hr.employee"].create(
            {"name": "Trưởng nhóm tài xế", "user_id": cls.truong_nhom.id}
        )

        # Mỗi test một job riêng để không phụ thuộc thứ tự chạy (skills/11 §7).
        cls._dem = 0
        cls.job_moi = cls._tao_job("new", driver=None)
        cls.job_da_gan = cls._tao_job("assigned", driver=cls.emp)
        cls.job_cho_kho = cls._tao_job(
            "picked_up",
            driver=cls.emp,
            so_kien=7,
            khoi_luong=42.5,
            don_hang=cls.don_hang,
        )
        # Việc RIÊNG cho test "Trưởng nhóm tài xế phân công" — không dùng chung
        # `job_moi` để test đó không phụ thuộc thứ tự chạy của các test khác.
        cls.job_truong_nhom = cls._tao_job("new", driver=None)

    @classmethod
    def _tao_job(cls, state, driver=None, so_kien=0, khoi_luong=0.0, don_hang=None):
        cls._dem += 1
        return cls.env["lab.yeu.cau.lay.hang"].create(
            {
                "partner_id": cls.shop.id,
                "sale_order_id": don_hang.id if don_hang else False,
                "driver_id": driver.id if driver else False,
                "dia_chi_lay": "18 Nguyễn Trãi, Quận 1",
                "lien_he_ten": "Chị Hạnh",
                "lien_he_sdt": "0903 111 222",
                "khung_gio_tu": fields.Datetime.now().replace(
                    hour=1, minute=30, second=0, microsecond=0
                ),
                "khung_gio_den": fields.Datetime.now().replace(
                    hour=3, minute=0, second=0, microsecond=0
                ),
                "so_kien_du_kien": 5 + cls._dem,
                "so_kien_thuc_te": so_kien,
                "khoi_luong_uoc_kg": khoi_luong,
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

    def _vao(self, nguoi_dung):
        """Mở phiên Odoo cho ``nguoi_dung`` và trả token đã seed ở ``setUpClass``."""
        self.authenticate(nguoi_dung.login, MAT_KHAU)
        return self.token_theo_login[nguoi_dung.login]

    def _dang_nhap(self, nguoi_dung):
        """Đi ĐÚNG đường thật: POST /auth/login (dùng cho test `roles`).

        Thông điệp assert ghi rõ **URL + user + status** để đọc log là biết ngay
        lỗi thuộc về ai (bài học từ lần chạy thật: `403 != 200` mà không biết user
        nào bị chặn).
        """
        self.authenticate(nguoi_dung.login, MAT_KHAU)
        duong_dan = "/lab/api/v1/auth/login"
        res = self._goi(duong_dan)
        self.assertEqual(
            res.status_code,
            200,
            "%s (POST, user=%s) -> %s | body: %s"
            % (duong_dan, nguoi_dung.login, res.status_code, (res.text or "")[:400]),
        )
        return res.json()["data"]

    # ============================================================ (a) /cs/jobs
    def test_cs_jobs_tra_viec_cho_dieu_phoi_va_danh_sach_tai_xe(self):
        token = self._vao(self.cs)
        res = self._goi("/lab/api/v1/cs/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]

        ids = [muc["id"] for muc in du_lieu["items"]]
        self.assertIn(self.job_moi.id, ids, "phải thấy việc state=new")
        self.assertIn(self.job_da_gan.id, ids, "phải thấy việc state=assigned")
        self.assertNotIn(
            self.job_cho_kho.id, ids, "việc đã lấy hàng KHÔNG thuộc màn điều phối"
        )

        muc = next(x for x in du_lieu["items"] if x["id"] == self.job_moi.id)
        self.assertEqual(muc["state"], "new")
        self.assertTrue(muc["stateLabel"])
        self.assertEqual(muc["shop"], "Shop CS Kho Kiểm Thử")
        self.assertEqual(muc["contactPhone"], "0903 111 222")
        # OQ-23: giờ hẹn là chuỗi HH:mm, không phải ISO
        self.assertRegex(muc["windowFrom"], r"^\d{2}:\d{2}$")
        self.assertNotIn("T", muc["windowFrom"])
        # OQ-22: chỉ có số kiện dự kiến khi có nguồn thật
        self.assertEqual(muc["expectedPackages"], self.job_moi.so_kien_du_kien)
        # Chưa gán tài xế -> không có driverName
        self.assertNotIn("driverName", muc)
        # Không lộ field nội bộ
        for khoa in ("partner_id", "sale_order_id", "driver_id", "khung_gio_tu", "note"):
            self.assertNotIn(khoa, muc)

        # Danh sách tài xế để gán: id là hr.employee.id, CHỈ nhân viên có nhóm tài
        # xế (không phải "mọi nhân viên có tài khoản"), và có thêm `login` để app
        # hiện "Tên · tài khoản".
        drivers = du_lieu["drivers"]
        driver_ids = [d["id"] for d in drivers]
        self.assertIn(self.emp.id, driver_ids)
        self.assertNotIn(
            self.emp_2.id, driver_ids, "nhân viên chưa có tài khoản app không vào danh sách"
        )
        self.assertNotIn(
            self.emp_khong_phai_tai_xe.id,
            driver_ids,
            "nhân viên CÓ tài khoản nhưng không thuộc nhóm tài xế (vd Mitchell "
            "Admin/Marc Demo) không được vào danh sách gán",
        )
        for d in drivers:
            self.assertEqual(
                set(d),
                {"id", "name", "login"},
                "mỗi tài xế chỉ trả {id, name, login} — không lộ field hr.employee khác",
            )
        ten_tai_xe = next(d["name"] for d in drivers if d["id"] == self.emp.id)
        self.assertEqual(ten_tai_xe, "Tài xế CS Kho")
        login_tai_xe = next(d["login"] for d in drivers if d["id"] == self.emp.id)
        self.assertEqual(
            login_tai_xe,
            self.tai_xe.login,
            "login của nhân viên = tài khoản app (hr.employee.user_id.login)",
        )

    def test_tai_xe_goi_cs_jobs_tra_403_tieng_viet(self):
        token = self._vao(self.tai_xe)
        res = self._goi("/lab/api/v1/cs/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 403, res.text)
        body = res.json()
        self.assertFalse(body["ok"])
        self.assertEqual(body["error"]["code"], "FORBIDDEN")
        self.assertIn("CS", body["error"]["message"])
        self.assertIn(
            "Trưởng nhóm tài xế",
            body["error"]["message"],
            "403 phải nêu CẢ HAI vai trò được phép (CS + Trưởng nhóm tài xế)",
        )

    def test_kho_goi_cs_jobs_tra_403(self):
        token = self._vao(self.kho)
        res = self._goi("/lab/api/v1/cs/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 403, res.text)
        self.assertEqual(res.json()["error"]["code"], "FORBIDDEN")

    # ================================== (e/f) Trưởng nhóm tài xế phân công việc
    def test_truong_nhom_tai_xe_vao_cs_jobs_va_gan_duoc_tai_xe(self):
        """Role MỚI: ``lab_core.group_driver_leader`` dùng được ĐÚNG 2 endpoint phân
        công — đọc danh sách việc/tài xế và gán tài xế (ghi ``driver_id``).

        Đi đúng đường thật như app: ``GET /cs/jobs`` -> ``POST .../assign``. Không
        kiểm bằng ``has_group`` mà bằng kết quả HTTP + state của bản ghi.
        """
        token = self._vao(self.truong_nhom)
        job = self.job_truong_nhom

        res = self._goi("/lab/api/v1/cs/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        ids = [muc["id"] for muc in du_lieu["items"]]
        self.assertIn(job.id, ids, "trưởng nhóm phải thấy việc cần phân công")
        self.assertIn(
            self.emp.id,
            [d["id"] for d in du_lieu["drivers"]],
            "trưởng nhóm phải thấy danh sách tài xế để gán",
        )

        res = self._goi(
            "/lab/api/v1/cs/jobs/%d/assign" % job.id,
            token=token,
            du_lieu={"driverId": self.emp.id},
        )
        self.assertEqual(res.status_code, 200, res.text)
        ket_qua = res.json()["data"]
        self.assertEqual(ket_qua["state"], "assigned")
        self.assertEqual(ket_qua["driverName"], self.tai_xe.name)

        self.env.invalidate_all()
        self.assertEqual(job.state, "assigned")
        self.assertEqual(job.driver_id.id, self.emp.id)
        self.assertEqual(
            job.driver_user_id.id,
            self.tai_xe.id,
            "cột stored driver_user_id phải trỏ đúng tài khoản app",
        )

    def test_truong_nhom_tai_xe_khong_vao_duoc_namespace_khac(self):
        """Chỉ phân công: KHÔNG vào ``/kho/*`` hay ``/driver/*`` (không nhận việc)."""
        token = self._vao(self.truong_nhom)
        for duong_dan in ("/lab/api/v1/kho/cho-nhan", "/lab/api/v1/driver/jobs"):
            res = self._goi(duong_dan, token=token, get=True)
            self.assertEqual(res.status_code, 403, "%s: %s" % (duong_dan, res.text))
            self.assertEqual(res.json()["error"]["code"], "FORBIDDEN")

    # ========================================================= (b) /cs/.../assign
    def test_cs_gan_tai_xe_doi_state_va_driver_user(self):
        token = self._vao(self.cs)
        res = self._goi(
            "/lab/api/v1/cs/jobs/%d/assign" % self.job_moi.id,
            token=token,
            du_lieu={"driverId": self.emp.id},
        )
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        self.assertEqual(du_lieu["state"], "assigned")
        self.assertTrue(du_lieu["stateLabel"])
        self.assertEqual(du_lieu["driverName"], self.tai_xe.name)

        self.env.invalidate_all()
        self.assertEqual(self.job_moi.state, "assigned")
        self.assertEqual(self.job_moi.driver_id.id, self.emp.id)
        self.assertEqual(
            self.job_moi.driver_user_id.id,
            self.tai_xe.id,
            "driver_user_id (cột stored) phải trỏ đúng tài khoản app của tài xế",
        )

    def test_cs_gan_lai_khi_state_sai_tra_409_va_khong_doi_tai_xe(self):
        """Việc đã `assigned` -> action_assign raise -> 409, driver_id KHÔNG đổi."""
        token = self._vao(self.cs)
        res = self._goi(
            "/lab/api/v1/cs/jobs/%d/assign" % self.job_da_gan.id,
            token=token,
            du_lieu={"driverId": self.emp_2.id},
        )
        self.assertEqual(res.status_code, 409, res.text)
        body = res.json()
        self.assertFalse(body["ok"])
        self.assertEqual(body["error"]["code"], "INVALID_STATE")
        self.assertTrue(body["error"]["message"])

        self.env.invalidate_all()
        self.assertEqual(self.job_da_gan.state, "assigned")
        self.assertEqual(
            self.job_da_gan.driver_id.id,
            self.emp.id,
            "savepoint: driver_id ghi dở phải quay lại, KHÔNG được đổi sang tài xế mới",
        )

    def test_cs_gan_sai_du_lieu_tra_400_va_tai_xe_khong_ton_tai_tra_404(self):
        token = self._vao(self.cs)
        duong_dan = "/lab/api/v1/cs/jobs/%d/assign" % self.job_moi.id

        thieu = self._goi(duong_dan, token=token, du_lieu={})
        self.assertEqual(thieu.status_code, 400, thieu.text)
        self.assertEqual(thieu.json()["error"]["code"], "VALIDATION")

        rac = self._goi(duong_dan, token=token, du_lieu={"driverId": "khong-phai-so"})
        self.assertEqual(rac.status_code, 400, rac.text)
        self.assertEqual(rac.json()["error"]["code"], "VALIDATION")

        khong_co = self._goi(duong_dan, token=token, du_lieu={"driverId": 999999})
        self.assertEqual(khong_co.status_code, 404, khong_co.text)
        self.assertEqual(khong_co.json()["error"]["code"], "NOT_FOUND")

        self.env.invalidate_all()
        self.assertEqual(self.job_moi.state, "new", "các lần lỗi không được đổi state")

    # ====================================================== (c) /kho/cho-nhan
    def test_kho_cho_nhan_chi_tra_viec_da_lay_hang(self):
        token = self._vao(self.kho)
        res = self._goi("/lab/api/v1/kho/cho-nhan", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        items = res.json()["data"]["items"]
        ids = [x["id"] for x in items]
        self.assertIn(self.job_cho_kho.id, ids)
        self.assertNotIn(self.job_moi.id, ids, "việc chưa lấy hàng chưa vào kho")
        self.assertNotIn(self.job_da_gan.id, ids)

        Job = self.env["lab.yeu.cau.lay.hang"]
        for muc in items:
            self.assertEqual(
                Job.browse(muc["id"]).state,
                "picked_up",
                "màn hình kho CHỈ được thấy việc đã lấy hàng",
            )

        muc = next(x for x in items if x["id"] == self.job_cho_kho.id)
        self.assertEqual(muc["ref"], self.job_cho_kho.name)
        self.assertEqual(muc["shop"], "Shop CS Kho Kiểm Thử")
        self.assertEqual(muc["packages"], 7)
        self.assertEqual(muc["weightKg"], 42.5)
        self.assertEqual(muc["driverName"], self.tai_xe.name)
        self.assertNotIn("pickingName", muc, "chưa nhận thì chưa có phiếu nhập kho")

    def test_kho_xac_nhan_nhan_hang_sinh_phieu_nhap_kho(self):
        token = self._vao(self.kho)
        res = self._goi(
            "/lab/api/v1/kho/jobs/%d/nhan" % self.job_cho_kho.id, token=token
        )
        self.assertEqual(res.status_code, 200, res.text)
        du_lieu = res.json()["data"]
        self.assertEqual(du_lieu["state"], "confirmed")
        self.assertTrue(du_lieu["stateLabel"])
        self.assertTrue(du_lieu["pickingName"])

        self.env.invalidate_all()
        job = self.job_cho_kho
        self.assertEqual(job.state, "confirmed")
        picking = job.stock_picking_id
        self.assertTrue(picking, "Luật 4: nhận hàng phải SINH chứng từ kho")
        self.assertEqual(du_lieu["pickingName"], picking.name)
        self.assertEqual(picking.picking_type_id.code, "incoming")
        # Đúng yêu cầu lấy hàng: origin = số yêu cầu, cùng khách, cùng số kiện/kg
        self.assertEqual(picking.origin, job.name)
        self.assertEqual(picking.partner_id, job.partner_id)
        self.assertEqual(picking.lab_package_count, job.so_kien_thuc_te)
        self.assertEqual(picking.lab_weight_kg, job.khoi_luong_uoc_kg)
        # Đúng đơn hàng: phiếu nhập của cùng khách với sale.order của yêu cầu
        self.assertTrue(job.sale_order_id, "tiền đề: yêu cầu gắn với 1 đơn hàng")
        self.assertEqual(job.sale_order_id.partner_id, picking.partner_id)

    def test_kho_cho_nhan_xep_moi_nhat_len_dau_va_kem_da_ve_kho(self):
        """Đơn tạo mới hơn (id lớn hơn, mã LH/26/... mới hơn) phải đứng đầu;
        và khi có ``includeConfirmed=1`` thì trả kèm việc đã về kho để quét tem (Bước 7)."""
        Job = self.env["lab.yeu.cau.lay.hang"]
        job_moi_hon = Job.create(
            {
                "partner_id": self.shop.id,
                "sale_order_id": self.don_hang.id,
                "dia_chi_lay": "88 Nguyễn Huệ, Q.1",
                "state": "picked_up",
                "so_kien_thuc_te": 2,
                "khoi_luong_uoc_kg": 10.0,
            }
        )
        token = self._vao(self.kho)
        res = self._goi("/lab/api/v1/kho/cho-nhan", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        items = res.json()["data"]["items"]
        ids = [x["id"] for x in items]
        self.assertEqual(
            ids,
            sorted(ids, reverse=True),
            "danh sách nhận hàng của kho phải xếp đơn mới nhất (id giảm dần) ở đầu",
        )
        self.assertEqual(ids[0], job_moi_hon.id)

        # Nhận việc gốc -> chuyển sang confirmed (có stock_picking_id)
        res_nhan = self._goi(
            "/lab/api/v1/kho/jobs/%d/nhan" % self.job_cho_kho.id, token=token
        )
        self.assertEqual(res_nhan.status_code, 200, res_nhan.text)

        # Gọi kèm includeConfirmed=1 -> vẫn thấy cả việc đã về kho (để quét tem ở Bước 7)
        res_kem = self._goi(
            "/lab/api/v1/kho/cho-nhan?includeConfirmed=1", token=token, get=True
        )
        self.assertEqual(res_kem.status_code, 200, res_kem.text)
        items_kem = res_kem.json()["data"]["items"]
        ids_kem = [x["id"] for x in items_kem]
        self.assertEqual(ids_kem, sorted(ids_kem, reverse=True))
        self.assertIn(job_moi_hon.id, ids_kem)
        self.assertIn(self.job_cho_kho.id, ids_kem)
        muc_da_nhan = next(x for x in items_kem if x["id"] == self.job_cho_kho.id)
        self.assertEqual(muc_da_nhan["state"], "confirmed")
        self.assertEqual(muc_da_nhan["stateLabel"], "Đã về kho")
        self.assertTrue(muc_da_nhan.get("pickingName"))
        self.assertEqual(muc_da_nhan.get("saleOrderName"), self.don_hang.name)
        self.assertEqual(muc_da_nhan.get("totalPackages"), 7)
        self.assertEqual(muc_da_nhan.get("scannedPackages"), 0)

    def test_kho_nhan_viec_chua_lay_hang_tra_409(self):
        token = self._vao(self.kho)
        res = self._goi("/lab/api/v1/kho/jobs/%d/nhan" % self.job_moi.id, token=token)
        self.assertEqual(res.status_code, 409, res.text)
        self.assertEqual(res.json()["error"]["code"], "INVALID_STATE")
        self.env.invalidate_all()
        self.assertEqual(self.job_moi.state, "new")
        self.assertFalse(
            self.job_moi.stock_picking_id, "state sai thì không được tạo phiếu kho"
        )

    def test_cs_goi_kho_va_driver_tra_403(self):
        """Namespace chặn chéo: CS chỉ `/cs/*`, không vào `/kho/*` hay `/driver/*`."""
        token = self._vao(self.cs)
        res_kho = self._goi("/lab/api/v1/kho/cho-nhan", token=token, get=True)
        self.assertEqual(res_kho.status_code, 403, res_kho.text)
        self.assertEqual(res_kho.json()["error"]["code"], "FORBIDDEN")

        res_driver = self._goi("/lab/api/v1/driver/jobs", token=token, get=True)
        self.assertEqual(res_driver.status_code, 403, res_driver.text)
        self.assertEqual(res_driver.json()["error"]["code"], "FORBIDDEN")

    # ============================================================== (d) roles
    def test_roles_trong_login_theo_phong_ban(self):
        # Mỗi assert là MỘT lượt gọi /auth/login thật (rate limit 30/phút/IP, file
        # này chỉ dùng 8 lượt; phần lớn test khác dùng token seed sẵn).
        self.assertIn("driver", self._dang_nhap(self.tai_xe)["roles"])
        self.assertIn("cs", self._dang_nhap(self.cs)["roles"])
        self.assertIn("kho", self._dang_nhap(self.kho)["roles"])
        self.assertIn("ketoan", self._dang_nhap(self.ketoan)["roles"])
        self.assertIn("sep", self._dang_nhap(self.sep)["roles"])
        # Trưởng nhóm tài xế: ĐÚNG MỘT vai trò app là "driver_leader" (thứ tự ưu
        # tiên cố định driver · driver_leader · cs · kho · ketoan · manager · sep).
        vai_tro_truong_nhom = self._dang_nhap(self.truong_nhom)["roles"]
        self.assertEqual(
            vai_tro_truong_nhom,
            ["driver_leader"],
            "trưởng nhóm tài xế không phải tài xế (không có 'driver') và không phải CS",
        )
        # Người không thuộc nhóm nghiệp vụ nào -> mảng rỗng (không phải None),
        # và KHÔNG được làm sập đăng nhập dù không có ACL đọc yêu cầu lấy hàng.
        du_lieu = self._dang_nhap(self.khong_nhom)
        self.assertEqual(du_lieu["roles"], [])
        self.assertEqual(du_lieu["jobsToday"], 0)
        self.assertEqual(du_lieu["driver_user_id"], self.khong_nhom.id)

    def test_roles_la_du_lieu_khong_phai_kiem_quyen(self):
        """`roles` chỉ để app chọn màn hình: sếp có role "sep" nhưng KHÔNG vào /cs/jobs."""
        du_lieu = self._dang_nhap(self.sep)
        self.assertIn("sep", du_lieu["roles"])
        res = self._goi("/lab/api/v1/cs/jobs", token=du_lieu["token"], get=True)
        self.assertEqual(
            res.status_code, 403, "quyền do nhóm ở controller + ir.rule, không do roles"
        )

    # ============================================ nhóm kỹ thuật của tài khoản demo
    def test_tai_khoan_demo_khong_co_nhom_ky_thuat(self):
        """Technical Features/Settings phải bị gỡ khỏi tài khoản demo (data hook).

        Nguồn gốc lỗi (đọc source 17.0): ``odoo/addons/base/security/base_groups.xml``
        cho ``base.group_user`` imply ``base.group_no_one`` nên MỌI user nội bộ —
        kể cả tài khoản demo không hề khai nhóm đó — đều dính "Technical
        Features". ``lab_core/models/res_users.py::_lab_bo_nhom_ky_thuat`` gỡ lại,
        và ``data/demo_users_technical.xml`` gọi hàm đó ở mỗi lần load module.
        """
        nhom_ky_thuat = self.env.ref("base.group_no_one")
        nhom_he_thong = self.env.ref("base.group_system")
        # xmlid tài khoản demo -> nhóm NGHIỆP VỤ nó phải còn giữ
        mong_doi = {
            "lab_core.demo_user_cs": "lab_core.group_cs",
            "lab_core.demo_user_kho": "lab_core.group_warehouse",
            "lab_core.demo_user_chungtu": "lab_core.group_docs",
            "lab_core.demo_user_ketoan": "lab_core.group_account",
            "lab_core.demo_user_manager": "lab_core.group_manager",
            "lab_core.demo_user_sep": "lab_core.group_director",
            "lab_core.demo_user_nhomtruong": "lab_core.group_driver_leader",
            "lab_laixe.demo_user_taixe01": "lab_core.group_driver",
        }
        for xmlid, xmlid_nhom in mong_doi.items():
            nguoi_dung = self.env.ref(xmlid, raise_if_not_found=False)
            if not nguoi_dung:
                continue
            self.assertNotIn(
                nhom_ky_thuat,
                nguoi_dung.groups_id,
                "%s không được có 'Technical Features'" % xmlid,
            )
            self.assertNotIn(
                nhom_he_thong,
                nguoi_dung.groups_id,
                "%s không được có 'Settings'" % xmlid,
            )
            self.assertIn(
                self.env.ref(xmlid_nhom),
                nguoi_dung.groups_id,
                "%s vẫn phải giữ nhóm nghiệp vụ của phòng ban" % xmlid,
            )
        # Đối chứng: nhóm kỹ thuật tồn tại thật và admin thì có — nếu không, các
        # assertNotIn ở trên là vô nghĩa (gỡ nhầm nhóm không tồn tại).
        admin = self.env.ref("base.user_admin")
        self.assertIn(nhom_he_thong, admin.groups_id)
        self.assertIn(nhom_ky_thuat, admin.groups_id)


    def test_cs_jobs_sap_xep_moi_nhat_len_dau_va_co_createDate(self):
        """`/cs/jobs` phải đưa việc TẠO SAU lên đầu + trả `createDate` đúng format.

        Chốt với khách (2026-11-03): màn "Phân công tài xế" sắp theo `create_date desc`
        (khác `/driver/jobs` — tài xế vẫn sắp theo khung giờ hẹn).
        """
        token = self._vao(self.cs)
        Job = self.env["lab.yeu.cau.lay.hang"]

        # Hai việc tạo LIÊN TIẾP: `create_date` là magic field do ORM ghi, KHÔNG ghi đè
        # bằng write() được ⇒ dùng đúng thứ tự tạo thật (mới tạo sau = create_date lớn
        # hơn hoặc bằng; hoà thì `id desc` phá hoà — đó chính là hành vi cần kiểm).
        cu = self._tao_job("new", driver=None)
        moi = self._tao_job("new", driver=None)
        self.env.invalidate_all()
        self.assertGreaterEqual(moi.create_date, cu.create_date)

        res = self._goi("/lab/api/v1/cs/jobs", token=token, get=True)
        self.assertEqual(res.status_code, 200, res.text)
        items = res.json()["data"]["items"]
        ids = [muc["id"] for muc in items]
        self.assertIn(cu.id, ids)
        self.assertIn(moi.id, ids)
        self.assertLess(
            ids.index(moi.id),
            ids.index(cu.id),
            "Việc tạo SAU phải nằm TRƯỚC trong danh sách phân công.",
        )

        muc_moi = next(m for m in items if m["id"] == moi.id)
        self.assertEqual(
            muc_moi["createDate"],
            fields.Datetime.to_string(moi.create_date),
            "createDate phải đúng mốc tạo của việc (UTC, cùng format quetLuc).",
        )
        self.assertRegex(muc_moi["createDate"], r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$")

        # `/driver/jobs` KHÔNG đổi thứ tự theo create_date (vẫn theo khung giờ hẹn).
        res_tx = self._goi(
            "/lab/api/v1/driver/jobs",
            token=self._vao(self.tai_xe),
            get=True,
        )
        self.assertEqual(res_tx.status_code, 200, res_tx.text)
