# -*- coding: utf-8 -*-
"""Hai endpoint "CHUYỂN TÀI KHOẢN" (login-as) cho DEMO — chỉ quản trị viên.

* ``GET  /lab/api/v1/admin/accounts`` — tài khoản demo được phép chuyển sang + roles
* ``POST /lab/api/v1/admin/switch``   — đổi PHIÊN hiện tại sang tài khoản đó

Vì sao có: kịch bản demo/video phải đi qua cả chục vai trò (tài xế → trưởng nhóm
tài xế → CS → kho → chứng từ → kế toán → quản lý → BGĐ). Logout/login từng tài
khoản vừa mất thời gian vừa không quay được liền mạch; quản trị viên bấm một nút là
thành tài khoản khác, và app lưu token y như vừa đăng nhập
(``helpers.payload_phien`` — cùng shape với ``/auth/login``).

⚠️ ĐÂY LÀ "LOGIN-AS" NỘI BỘ, KHÔNG PHẢI TÍNH NĂNG CHO NGƯỜI DÙNG THẬT:

* chỉ phiên **được phép chuyển** (``helpers.duoc_phep_chuyen``): quản trị viên
  (superuser uid 1 **hoặc** nhóm ``base.group_system`` = "Settings"), **hoặc**
  phiên do admin chuyển sang; mọi trường hợp khác nhận **403** kèm câu tiếng Việt;
* danh sách bị giới hạn ở **tài khoản demo** (có ít nhất một nhóm
  ``lab_core.group_*``) và loại các login hệ thống (``helpers.LOGIN_HE_THONG``);
* KHÔNG đổi mật khẩu, KHÔNG tạo user mới, KHÔNG tạo session rời: chỉ ghi 4 khoá
  định danh của phiên hiện tại (``uid`` · ``login`` · ``context`` ·
  ``session_token`` — đúng những gì ``Session.authenticate`` ghi) rồi xoay ``sid``
  để chống session fixation; phiên cũ của admin tự mất vì cookie ``session_id`` đã
  mang uid mới;
* **chuyển được LIÊN TỤC** (yêu cầu UX của chủ dự án): ``admin`` → ``taixe01`` →
  ``cs01`` … mỗi bước 1 click trên dropdown. Cơ chế: khi chuyển thành công, ghi
  ``session[helpers.PHIEN_DO_ADMIN_CHUYEN] = <uid admin gốc>``; hai endpoint
  ``/admin/*`` cho qua khi phiên có dấu đó (``helpers.duoc_phep_chuyen``). Dấu này
  **chỉ** do endpoint này ghi và bị ``/auth/login`` xoá — đăng nhập trực tiếp bằng
  tài khoản thường thì dropdown biến mất (``payload.canSwitch = False``);
* **cố ý không cho chuyển SANG ``admin``** (admin không nằm trong whitelist): muốn
  quay lại admin thì đăng xuất rồi đăng nhập — endpoint này không được phép trở
  thành đường tắt "thành admin" mà không có mật khẩu.

⚠️ Vì sao kiểm quyền bằng ``helpers.kiem_admin`` (chứ không bằng ACL/ir.rule): cả
hai endpoint nói chuyện với ``res.users`` của NGƯỜI KHÁC, mà ACL ``res.users`` ở
Odoo 17 cấp quyền đọc khá rộng cho Internal User — ACL không phân biệt "admin hay
không". Đây là tầng HTTP nên chặn ở cửa vào là đúng chỗ, cùng khuôn với ``/cs`` và
``/kho`` (xem ``helpers.kiem_nhom``). KHÔNG dùng ``has_group``: luật tĩnh A4 của
``infra/scripts/audit-native-first.ps1`` cấm chuỗi đó trong ``addons/`` để Luật 3
không bị lách.
"""

from odoo import _, http
from odoo.http import request

from ..helpers import (
    PHIEN_DO_ADMIN_CHUYEN,
    danh_sach_tai_khoan_demo,
    kiem_admin,
    nguon_chuyen_phien,
    payload_phien,
    tai_khoan_demo_theo_login,
    tim_nguoi_dung_theo_login,
    xac_thuc_token,
)
from ..tools import (
    bam_token,
    check_rate_limit,
    loi,
    ok,
    tao_token,
    tham_so,
    thoi_diem_het_han,
    tra_loi,
)


class LabAdminApi(http.Controller):
    # ==================================================================
    # GET /lab/api/v1/admin/accounts
    # ==================================================================
    @http.route(
        "/lab/api/v1/admin/accounts",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def accounts(self, **kwargs):
        """Tài khoản demo mà admin được phép "chuyển sang" (kèm ``roles``)."""
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = kiem_admin()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("admin-accounts:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)
        return tra_loi(ok({"items": danh_sach_tai_khoan_demo()}))

    # ==================================================================
    # POST /lab/api/v1/admin/switch
    # ==================================================================
    @http.route(
        "/lab/api/v1/admin/switch",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
        save_session=True,
    )
    def switch(self, **kwargs):
        """Đổi phiên hiện tại sang tài khoản demo khác — payload ``{"login": "taixe01"}``.

        Trả **cùng shape** với ``/auth/login`` (thêm khoá ``login`` của tài khoản
        đích) để app lưu phiên bằng đúng một bộ parser.
        """
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        # ⚠️ Kiểm quyền TRƯỚC khi đổi phiên: sau khi phiên mang uid tài khoản đích
        # thì "người gọi" không còn là admin nữa, kiểm lúc đó là kiểm nhầm người.
        loi_quyen = kiem_admin()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("admin-switch:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        params = tham_so()
        login = (params.get("login") or "").strip()
        if not login:
            return tra_loi(
                loi(
                    "VALIDATION",
                    _("Vui lòng nhập tên đăng nhập cần chuyển sang."),
                ),
                status=400,
            )

        target = tai_khoan_demo_theo_login(login)
        if not target:
            # Hai tình huống KHÁC NGHĨA nên khác mã lỗi:
            # * login có thật nhưng không phải tài khoản demo (admin · public ·
            #   user rác không nhóm lab) -> 403: yêu cầu hợp lệ nhưng bị CẤM;
            # * login không tồn tại -> 404.
            if tim_nguoi_dung_theo_login(login):
                return tra_loi(
                    loi(
                        "FORBIDDEN",
                        _(
                            "Chỉ chuyển được sang tài khoản demo của hệ thống "
                            "(tài khoản %s không nằm trong danh sách).",
                            login,
                        ),
                    ),
                    status=403,
                )
            return tra_loi(
                loi("NOT_FOUND", _("Không có tài khoản %s.", login)),
                status=404,
            )

        # ------------------------------------------------------------------
        # Đổi phiên (login-as) — làm ĐÚNG những gì cơ chế đăng nhập của Odoo ghi
        # vào phiên: 4 khoá `uid` · `login` · `context` · `session_token`, rồi xoay
        # `sid` (`should_rotate`) để chống session fixation.
        #
        # ⚠️ ĐÃ ĐỌC SOURCE 17.0 (`odoo/http.py`, branch 17.0) — KHÔNG dùng
        # `Session.finalize()`: ở 17.0 hàm đó là bước cuối của luồng **MFA**, nó
        # `pop('pre_login')` / `pop('pre_uid')` từ phiên -> với một phiên đã đăng
        # nhập bình thường (không có 2 `pre_*`) là KeyError -> 500. `authenticate()`
        # cũng không dùng được vì nó đòi MẬT KHẨU của tài khoản đích.
        #
        # `_compute_session_token(sid)` là chính hàm mà `Session.finalize` và
        # `FilesystemSessionStore.rotate` dùng để ký phiên (odoo/http.py 17.0);
        # đặt tường minh ở đây để phiên hợp lệ NGAY, không phụ thuộc việc trình
        # chủ phiên có xoay sid hay không.
        # ------------------------------------------------------------------
        phien = request.session
        # Ghi DẤU "phiên do admin chuyển" TRƯỚC khi xoay sid: nó là dữ liệu của
        # session nên `rotate()` lưu nguyên (đã kiểm bằng test đi liên tiếp
        # admin -> taixe01 -> cs01). Nếu phiên ĐÃ có dấu (đang chuyển tiếp từ tài
        # khoản trước) thì GIỮ uid admin GỐC, không ghi đè bằng uid hiện tại —
        # nhờ vậy chuỗi chuyển không cần mật khẩu admin ở giữa.
        phien[PHIEN_DO_ADMIN_CHUYEN] = nguon_chuyen_phien() or request.env.uid
        phien.update(
            {
                "uid": target.id,
                "login": target.login,
                "context": dict(request.env(user=target.id)["res.users"].context_get()),
                "session_token": target._compute_session_token(phien.sid),
            }
        )
        phien.should_rotate = True
        # Env của request này cũng phải là tài khoản đích — nhờ vậy mọi truy vấn
        # bên dưới (roles, jobsToday, ghi token) chạy ĐÚNG quyền của target chứ
        # không phải quyền admin. Cũng để bước lưu phiên (`rotate(sess, self.env)`)
        # ký lại `session_token` theo đúng tài khoản mới.
        request.update_env(user=target.id)
        nguoi_dung = request.env.user

        # Cấp token MỚI cho tài khoản đích, y như /auth/login (hash + hạn 8 giờ).
        token = tao_token()
        het_han = thoi_diem_het_han()
        User = request.env["res.users"].sudo()  # sudo-ok: ghi hash token 8 giờ cho tài khoản vừa chuyển sang (tài xế là portal, không có ACL res.users)
        User.browse(nguoi_dung.id).write(
            {
                "lab_api_token_hash": bam_token(token),
                "lab_api_token_expiry": het_han,
            }
        )

        # `co_the_chuyen=True`: phiên này vừa được phép chuyển -> app giữ dropdown
        # hiện trên thanh trên cùng để chuyển TIẾP (admin -> taixe01 -> cs01 ...).
        du_lieu = payload_phien(nguoi_dung, token, het_han, co_the_chuyen=True)
        # `login` chỉ có ở endpoint này: app hiện "đang là tài khoản nào" và tự điền
        # lại ô đăng nhập khi cần. (/auth/login giữ nguyên shape cũ.)
        du_lieu["login"] = nguoi_dung.login or ""
        return tra_loi(ok(du_lieu))
