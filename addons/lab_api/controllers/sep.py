# -*- coding: utf-8 -*-
"""Dashboard của Sếp/Trưởng phòng — ``GET /lab/api/v1/sep/kpi`` (docs/02 §6.2).

Payload KHÔNG tính ở đây: toàn bộ phép đếm ngày/múi giờ nằm ở
``lab.yeu.cau.lay.hang.lab_dashboard_kpi`` (lab_laixe). Controller này chỉ:

1. kiểm ``X-Lab-Token`` (401) và nhóm Trưởng phòng/BGĐ (403) + rate limit (429);
2. gọi đúng hàm đó (không sudo — ir.rule + ACL quyết định dữ liệu);
3. đổi ``UserError`` của tham số ``range`` thành envelope 400 ``VALIDATION``.

Vì hàm dùng chung cũng là cái giao diện OWL gọi qua RPC (backend, không cần
HTTP token), hai cửa vào trả **y hệt** một payload.

Cấu trúc ``data`` (giữ nguyên như trước khi tách hàm — app KMP đang parse bộ
khoá này):

* ``kpi``     — 5 con số trên hàng thẻ KPI;
* ``trend``   — chuỗi ngày **liên tục** (mỗi ngày 1 phần tử, kể cả ngày 0) vì
  chart cần trục X đều, không được nhảy ngày;
* ``byState`` — cơ cấu 7 trạng thái theo đúng thứ tự state machine (kể cả 0);
* ``recent``  — 10 việc mới nhất, chỉ field an toàn để hiển thị.

Quyền dữ liệu vẫn do **ir.rule + ACL** quyết định: không sudo ở bất kỳ truy vấn
nào. Chặn theo nhóm chỉ ở tầng HTTP (``helpers.kiem_mot_trong_nhom``); riêng
``lab.chuyen`` thì hàm dùng chung tự hỏi
``check_access_rights(..., raise_exception=False)`` trước khi đọc.
"""

from odoo import _, http
from odoo.exceptions import AccessError, UserError
from odoo.http import request

from ..helpers import kiem_mot_trong_nhom, xac_thuc_token
from ..tools import check_rate_limit, loi, ok, tra_loi

#: Hai nhóm được xem dashboard (Trưởng phòng duyệt + Ban giám đốc).
NHOM_XEM_DASHBOARD = ("lab_core.group_manager", "lab_core.group_director")


class LabSepApi(http.Controller):
    @staticmethod
    def _chan_quyen():
        """403 nếu không thuộc nhóm Trưởng phòng hoặc Ban giám đốc."""
        return kiem_mot_trong_nhom(
            NHOM_XEM_DASHBOARD,
            _("Bảng điều khiển chỉ dành cho Trưởng phòng hoặc Ban giám đốc."),
        )

    # ==================================================================
    # GET /lab/api/v1/sep/kpi?range=today|7d|30d
    # ==================================================================
    @http.route(
        "/lab/api/v1/sep/kpi",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def kpi(self, **kwargs):
        """Số liệu tổng hợp cho màn dashboard của Sếp."""
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("sep-kpi:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        # `?range=` nằm trong QUERY STRING (route chỉ nhận GET) -> đọc thẳng.
        # Không tự mặc định ở đây: hàm dùng chung nhận None/"" = 7d và raise
        # UserError cho giá trị lạ, nên luật range chỉ có MỘT bản.
        Job = request.env["lab.yeu.cau.lay.hang"]
        try:
            du_lieu = Job.lab_dashboard_kpi(request.httprequest.args.get("range"))
        except AccessError:
            # Lỗi QUYỀN không phải lỗi tham số: để Odoo báo như cũ, không nuốt
            # thành 400 (AccessError là lớp con của UserError).
            raise
        except UserError as exc:
            return tra_loi(loi("VALIDATION", str(exc)), status=400)

        return tra_loi(ok(du_lieu))
