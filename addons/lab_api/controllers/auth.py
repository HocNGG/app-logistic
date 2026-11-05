# -*- coding: utf-8 -*-
"""Đăng nhập tài xế — `POST /lab/api/v1/auth/login` (docs/02 §6.2–6.3, ADR-004).

Ba đường vào, cùng trả **một** envelope ``{ok, data, error}``:

1. ``{login, password}``  — mật khẩu hoặc **API key** (`res.users.apikeys` dùng
   thay mật khẩu, docs/02 §6.3). Odoo tự kiểm, ta không lưu mật khẩu.
2. (đã có phiên) app gọi sau khi đăng nhập native `/web/session/authenticate`
   → chỉ cấp token 8 giờ. Đây là đường FE-01 dùng khi Ktor đã giữ cookie.
3. không có gì → 401 tiếng Việt.

Token trả về: **hash** lưu ở `res.users.lab_api_token_hash` + hạn 8 giờ, token
gốc chỉ trả 1 lần cho client, **không log** (docs/02 §6.3).

Ngoài token, response còn có `roles` — mảng vai trò app ("admin" ở đầu nếu là
quản trị viên, rồi "driver"/"driver_leader"/"cs"/"kho"/"ketoan"/"manager"/"sep",
theo đúng thứ tự ưu tiên đó) suy từ group của user, để app KMP biết mở màn hình
nào. ⚠️ `roles` **không phải** kiểm quyền: quyền vẫn do `ir.rule` + ACL, và mỗi
namespace API (`/driver/*`, `/cs/*`, `/kho/*`, `/admin/*`) tự chặn ở tầng
controller (xem `helpers.kiem_nhom` · `helpers.kiem_admin`).

`data` của phiên do `helpers.payload_phien` dựng — **dùng chung** với
`POST /admin/switch` (chuyển tài khoản cho demo) để app chỉ cần MỘT bộ parser.
"""

import inspect
import logging

from odoo import _, http
from odoo.http import request

from ..helpers import PHIEN_DO_ADMIN_CHUYEN, payload_phien
from ..tools import (
    bam_token,
    check_rate_limit,
    ip_client,
    loi,
    ok,
    tao_token,
    tham_so,
    thoi_diem_het_han,
    tra_loi,
)

_logger = logging.getLogger(__name__)


class LabAuthApi(http.Controller):
    # ==================================================================
    # Đăng nhập
    # ==================================================================
    @http.route(
        "/lab/api/v1/auth/login",
        type="http",
        auth="public",
        methods=["POST"],
        csrf=False,
        save_session=True,
    )
    def login(self, **kwargs):
        gioi_han = check_rate_limit("login:%s" % ip_client(), limit=30, window=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        params = tham_so()
        login = (params.get("login") or "").strip()
        mat_khau = params.get("password") or params.get("api_key") or ""

        if login and mat_khau:
            if not self._dang_nhap_bang_mat_khau(login, mat_khau):
                return tra_loi(
                    loi(
                        "AUTH_FAILED",
                        _(
                            "Sai tên đăng nhập, mật khẩu/API key, hoặc tài khoản "
                            "đã bị khoá."
                        ),
                    ),
                    status=401,
                )
        elif not request.session.uid:
            return tra_loi(
                loi(
                    "AUTH_REQUIRED",
                    _("Vui lòng nhập tên đăng nhập và mật khẩu (hoặc API key)."),
                ),
                status=401,
            )

        uid = request.session.uid
        if not uid:
            return tra_loi(
                loi("AUTH_FAILED", _("Không xác định được tài khoản đăng nhập.")),
                status=401,
            )
        # Phiên đã xác thực -> đồng bộ env của request theo user vừa đăng nhập.
        request.update_env(user=uid)
        nguoi_dung = request.env.user

        token = tao_token()
        het_han = thoi_diem_het_han()
        # Ghi hash token vào CHÍNH tài khoản đang đăng nhập. Tài xế là portal nên
        # không có ACL trên res.users (2 field token có groups=base.group_system)
        # -> cần sudo; chỉ ghi 2 field, không trả recordset ra ngoài.
        user_sudo = request.env["res.users"].sudo()  # sudo-ok: ghi hash token 8 giờ vào chính user vừa đăng nhập (portal không có ACL res.users)
        user_sudo.browse(uid).write(
            {
                "lab_api_token_hash": bam_token(token),
                "lab_api_token_expiry": het_han,
            }
        )

        # Đăng nhập TRỰC TIẾP -> xoá dấu "phiên do admin chuyển" (nếu có): từ đây
        # quyền chuyển tài khoản chỉ còn khi CHÍNH tài khoản này là admin, nên
        # dropdown "chuyển vai trò" biến mất đúng yêu cầu UX của chủ dự án.
        request.session.pop(PHIEN_DO_ADMIN_CHUYEN, None)

        # `data` của phiên dựng ở helpers.payload_phien — DÙNG CHUNG với
        # POST /admin/switch để app chỉ cần MỘT bộ parser (xem docstring helper).
        # `co_the_chuyen=None` -> helper tự suy: chỉ admin thật mới có `canSwitch`.
        return tra_loi(ok(payload_phien(nguoi_dung, token, het_han)))

    # ==================================================================
    # Xác thực mật khẩu/API key -> tạo phiên Odoo 17
    # ==================================================================
    @staticmethod
    def _dang_nhap_bang_mat_khau(login, mat_khau):
        """Tạo phiên Odoo cho ``login``/``mat_khau``. Trả True nếu thành công.

        ⚠️ ĐÃ ĐO TRÊN BẢN BUILD NÀY (17.0-20260908), không đoán: hai biến thể
        ``Session.authenticate`` cùng tồn tại trong hệ sinh thái 17.0:

        * ``(dbname, credential_dict)`` — bản refactor phục vụ MFA;
        * ``(dbname, login, password)`` — bản cũ, và **image đang chạy dùng bản
          này** (``grep -n 'def authenticate' odoo/http.py`` -> ``login=None,
          password=None``; ``res.users.authenticate(cls, db, login, password,
          user_agent_env)``).

        Vì vậy **không** thử lần lượt: gọi dạng dict trên bản cũ sẽ vào
        ``Users._login(db, {dict}, None, ...)`` -> ``password=None`` ->
        ``AccessDenied``, mà ``AccessDenied`` **không phải** ``TypeError`` nên
        bị ``except Exception`` nuốt mất và đăng nhập bằng mật khẩu không bao
        giờ chạy (bug thật: API trả 401 "Sai tên đăng nhập" dù mật khẩu đúng,
        trong khi ``/web/session/authenticate`` vẫn 200). Đọc **tên tham số**
        của hàm rồi gọi đúng dạng — quyết định dựa trên source, không dựa vào
        thứ tự thử.
        """
        dbname = request.env.cr.dbname
        tham_so = inspect.signature(http.Session.authenticate).parameters
        try:
            if "credential" in tham_so:
                request.session.authenticate(
                    dbname,
                    {"login": login, "password": mat_khau, "type": "password"},
                )
            else:
                request.session.authenticate(dbname, login, mat_khau)
            return True
        except TypeError:
            # Chữ ký khác dự đoán (bản build lạ) -> thử dạng còn lại.
            try:
                if "credential" in tham_so:
                    request.session.authenticate(dbname, login, mat_khau)
                else:
                    request.session.authenticate(
                        dbname,
                        {"login": login, "password": mat_khau, "type": "password"},
                    )
                return True
            except Exception:  # noqa: BLE001 - AccessDenied: coi như sai thông tin
                _logger.warning(
                    "Đăng nhập thất bại ở dạng chữ ký dự phòng (login=%s)", login,
                    exc_info=True,
                )
                return False
        except Exception:  # noqa: BLE001 - AccessDenied/UserError: sai thông tin
            # Không log mật khẩu; chỉ log login + traceback để biết ĐÂY LÀ lỗi
            # hệ thống hay sai thông tin (bug 401 im lặng trước đây nằm ở chỗ này).
            _logger.warning("Đăng nhập thất bại (login=%s)", login, exc_info=True)
            return False
