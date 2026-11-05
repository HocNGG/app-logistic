# -*- coding: utf-8 -*-
"""Controller cho màn ĐIỀU PHỐI của CS (docs/02-kien-truc.md §6.2, ADR-004).

2 endpoint:

* ``GET  /lab/api/v1/cs/jobs``                — việc cần điều phối + danh sách tài xế
* ``POST /lab/api/v1/cs/jobs/<id>/assign``    — gán tài xế (gọi ``action_assign``)

Quyền: ``auth='user'`` + ``X-Lab-Token`` + nhóm ``lab_core.group_cs`` **HOẶC**
``lab_core.group_driver_leader`` ("Trưởng nhóm tài xế" — người phân công; 403 nếu
không thuộc cả hai — ``helpers.kiem_mot_trong_nhom``). Phạm vi BẢN GHI vẫn do
**ir.rule + ACL** quyết định (Luật 3): endpoint không lọc theo ``uid`` như
``/driver/jobs`` — người điều phối nhìn toàn bộ yêu cầu đang chờ, không phải chỉ
việc của mình.

⚠️ Vì sao vẫn cho CS gọi khi đã có Trưởng nhóm tài xế: giữ tương thích (app KMP
bản cũ + web dùng `cs01`), và CS vẫn là người tạo yêu cầu. Kiểm nhóm ở đây chỉ là
chặn **cửa vào namespace**, không phải nghiệp vụ.
"""

from odoo import _, http
from odoo.exceptions import UserError
from odoo.http import request

from ..helpers import (
    danh_sach_tai_xe,
    job_dict_dieu_phoi,
    job_theo_quyen,
    kiem_mot_trong_nhom,
    nhan_state,
    xac_thuc_token,
)
from ..tools import (
    check_rate_limit,
    loi,
    ok,
    so_nguyen,
    tham_so,
    tra_loi,
)

#: Việc còn nằm trong tay CS: chưa gán tài xế, hoặc đã gán nhưng tài xế chưa đi.
TRANG_THAI_CAN_DIEU_PHOI = ("new", "assigned")

#: Hai nhóm được PHÂN CÔNG tài xế: CS (giữ tương thích) + Trưởng nhóm tài xế.
#: Tách hằng số để test và RV thấy ngay danh sách quyền, không phải đọc thân hàm.
NHOM_DIEU_PHOI = ("lab_core.group_cs", "lab_core.group_driver_leader")

#: Trần số việc trả về một lần — client lỗi không kéo cả nghìn bản ghi.
SO_VIEC_TOI_DA = 100


class LabCsApi(http.Controller):
    @staticmethod
    def _chan_quyen():
        """403 nếu không thuộc CS **hoặc** Trưởng nhóm tài xế (``kiem_mot_trong_nhom``)."""
        return kiem_mot_trong_nhom(
            NHOM_DIEU_PHOI,
            _(
                "Chức năng điều phối chỉ dành cho bộ phận CS hoặc Trưởng nhóm tài xế."
            ),
        )

    # ==================================================================
    # GET /lab/api/v1/cs/jobs
    # ==================================================================
    @http.route(
        "/lab/api/v1/cs/jobs",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def jobs(self, **kwargs):
        """Việc cần điều phối (``state`` = new/assigned) + tài xế để gán."""
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("cs-jobs:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        jobs = request.env["lab.yeu.cau.lay.hang"].search(
            [("state", "in", TRANG_THAI_CAN_DIEU_PHOI)],
            # CHỐT VỚI KHÁCH (2026-11-03): màn "Phân công tài xế" phải đưa **đơn TẠO
            # MỚI NHẤT lên đầu** để trưởng nhóm thấy việc vừa phát sinh trước.
            # KHÁC `/driver/jobs`: tài xế vẫn sắp theo khung giờ hẹn (đúng nghiệp vụ chạy
            # tuyến trong ngày) — KHÔNG đổi endpoint đó.
            order="create_date desc, id desc",
            limit=SO_VIEC_TOI_DA,
        )
        return tra_loi(
            ok(
                {
                    "items": [job_dict_dieu_phoi(job) for job in jobs],
                    "drivers": danh_sach_tai_xe(),
                }
            )
        )

    # ==================================================================
    # POST /lab/api/v1/cs/jobs/<id>/assign
    # ==================================================================
    @http.route(
        "/lab/api/v1/cs/jobs/<int:job_id>/assign",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def assign(self, job_id, **kwargs):
        """Gán tài xế cho việc — payload ``{"driverId": <hr.employee.id>}``.

        Gọi đúng ``action_assign()`` hiện có để **state machine** vẫn là một chỗ
        duy nhất: sai trạng thái -> ``UserError`` -> 409 ``INVALID_STATE`` kèm câu
        tiếng Việt của model.
        """
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("cs-assign:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        job = job_theo_quyen(job_id, can_write=True)
        if not job:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy yêu cầu lấy hàng này.")), status=404
            )

        params = tham_so()
        try:
            driver_id = so_nguyen(params.get("driverId"), _("Tài xế"))
        except UserError as exc:
            return tra_loi(loi("VALIDATION", str(exc)), status=400)
        if not driver_id:
            return tra_loi(
                loi(
                    "VALIDATION",
                    _("Vui lòng chọn tài xế cần gán cho yêu cầu %s.", job.name),
                ),
                status=400,
            )
        # Kiểm tài xế tồn tại bằng bảng public (người phân công — CS/Trưởng nhóm
        # tài xế — không có ACL hr.employee, chỉ có hr.employee.public).
        if not request.env["hr.employee.public"].browse(driver_id).exists():
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy tài xế có mã %s.", driver_id)),
                status=404,
            )

        # savepoint: ghi driver_id + action_assign là MỘT đơn vị. Nếu action_assign
        # raise (state sai) thì driver_id vừa ghi cũng phải quay lại — không để
        # "đã đổi tài xế mà vẫn báo 409". Cursor.savepoint tự flush trước và
        # invalidate cache sau khi rollback (odoo/sql_db.py).
        try:
            with request.env.cr.savepoint():
                job.write({"driver_id": driver_id})
                job.action_assign()
        except UserError as exc:
            return tra_loi(loi("INVALID_STATE", str(exc)), status=409)

        return tra_loi(
            ok(
                {
                    "state": job.state,
                    "stateLabel": nhan_state(job),
                    "driverName": job.driver_user_id.name or "",
                }
            )
        )
