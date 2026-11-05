# -*- coding: utf-8 -*-
"""Controller cho app tài xế (docs/02-kien-truc.md §6.2, ADR-004).

4 endpoint: ``jobs`` · ``accept`` · ``capture`` · ``fail`` (+ login ở controller auth).

Quyền: ``auth='user'`` (tài xế phải có phiên Odoo, docs/02 §6.3) + nhóm
``lab_core.group_driver`` (403 nếu không thuộc namespace này — xem
``helpers.kiem_nhom``). Phạm vi dữ liệu do **ir.rule** quyết định (Luật 3),
controller KHÔNG sudo để đọc job. Ngoài phiên, mỗi request phải mang
``X-Lab-Token`` (cột "Auth = token" của docs §6.2): token 8 giờ, hash trong DB.
"""

import base64
from datetime import timedelta

from odoo import _, fields, http
from odoo.exceptions import UserError
from odoo.http import request

from ..helpers import (
    job_dict,
    job_theo_quyen,
    kiem_nhom,
    nhan_state,
    xac_thuc_token,
)
from ..tools import (
    check_rate_limit,
    giai_ma_anh,
    loi,
    ok,
    so_nguyen,
    so_thuc,
    tham_so,
    tra_loi,
)

TRANG_THAI_VIEC = ("assigned", "en_route", "picked_up")


class LabDriverApi(http.Controller):
    #: Số ảnh hàng hoá tối đa cho mỗi lần capture. Lý do chọn 3: app KMP có đúng
    #: 3 khung chụp (hàng hoá · nhãn/kiện · hiện trường). Trần cứng để client lỗi
    #: không đẩy được 100 ảnh 5 MB vào 1 request.
    SO_ANH_TOI_DA = 3
    #: Ghi chú của tài xế tối đa 500 ký tự (field `ghi_chu_tai_xe` là Text, nhưng
    #: API giới hạn để màn điều phối của CS không bị một dòng dài vô hạn).
    GHI_CHU_TOI_DA = 500

    # ==================================================================
    # Tiện ích nội bộ
    #
    # Từ khi có thêm namespace /cs/* và /kho/*, phần logic dùng chung đã chuyển
    # sang ``lab_api/helpers.py`` (một chỗ để review: payload, tra job theo quyền,
    # kiểm token, chặn theo nhóm). Các method dưới đây chỉ còn là lớp mỏng giữ
    # NGUYÊN tên cũ để 4 endpoint và 16 test hiện có không phải đổi.
    # ==================================================================
    def _xac_thuc_token(self):
        """Kiểm header ``X-Lab-Token`` của phiên hiện tại (logic ở ``helpers``)."""
        return xac_thuc_token()

    def _chan_quyen(self):
        """403 nếu user không thuộc nhóm tài xế (``/driver/*`` chỉ dành cho họ)."""
        return kiem_nhom(
            "lab_core.group_driver",
            _("Chức năng của tài xế chỉ dành cho tài khoản tài xế."),
        )

    def _job(self, job_id, can_write=False):
        """Lấy job trong phạm vi ir.rule của tài xế (không sudo) — xem ``helpers``.

        Trả ``None`` nếu không tồn tại **hoặc** không thuộc quyền — cố ý gộp 2
        trường hợp để không tiết lộ job của tài xế khác (trả 404 như nhau).
        """
        return job_theo_quyen(job_id, can_write=can_write)

    @staticmethod
    def _nhan_state(job):
        return nhan_state(job)

    def _job_dict(self, job):
        """Payload gọn cho mobile (khoá không đổi — app KMP đang dùng)."""
        return job_dict(job)

    # ==================================================================
    # GET /lab/api/v1/driver/jobs
    # ==================================================================
    @http.route(
        "/lab/api/v1/driver/jobs",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def jobs(self, **kwargs):
        """Việc hôm nay của tài xế đang đăng nhập (payload gọn cho mobile)."""
        loi_token = self._xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("driver-jobs:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        dau_ngay = fields.Datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        cuoi_ngay = dau_ngay + timedelta(days=1)
        domain = [
            # Lọc bằng cột stored `driver_user_id` (related driver_id.user_id) —
            # KHÔNG dùng `driver_id.user_id`: tài xế là portal, Odoo 17 chặn đọc
            # hr.employee (tự chuyển sang hr.employee.public — chỉ Internal User)
            # nên chạm driver_id là 403 ngay giữa request.
            ("driver_user_id", "=", request.env.uid),
            ("state", "in", TRANG_THAI_VIEC),
            # Job không đặt khung giờ vẫn phải hiện (CS chưa kịp nhập giờ hẹn);
            # job có khung giờ thì chỉ lấy trong hôm nay.
            "|",
            ("khung_gio_tu", "=", False),
            "&",
            ("khung_gio_tu", ">=", dau_ngay),
            ("khung_gio_tu", "<", cuoi_ngay),
        ]
        jobs = request.env["lab.yeu.cau.lay.hang"].search(
            domain, order="khung_gio_tu asc, id asc", limit=50
        )
        return tra_loi(ok([self._job_dict(job) for job in jobs]))

    # ==================================================================
    # POST /lab/api/v1/driver/jobs/<id>/accept
    # ==================================================================
    @http.route(
        "/lab/api/v1/driver/jobs/<int:job_id>/accept",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def accept(self, job_id, **kwargs):
        """Tài xế nhận việc: new -> assigned -> en_route (giữ đúng state machine)."""
        loi_token = self._xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("driver-accept:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)
        job = self._job(job_id, can_write=True)
        if not job:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy việc này của bạn.")), status=404
            )
        try:
            if job.state in ("en_route", "picked_up"):
                pass  # đã nhận việc trước đó -> trả lại state hiện tại (idempotent)
            elif job.state == "new":
                job.action_assign()
                job.action_start()
            else:
                job.action_start()  # assigned -> en_route
        except UserError as exc:
            return tra_loi(loi("INVALID_STATE", str(exc)), status=409)
        return tra_loi(
            ok({"state": job.state, "stateLabel": self._nhan_state(job)})
        )

    # ==================================================================
    # POST /lab/api/v1/driver/jobs/<id>/capture
    # ==================================================================
    def _doc_danh_sach_anh(self, params):
        """Đọc ảnh hàng hoá: ``photos`` (mảng base64, tối đa 3) hoặc ``photoBase64``.

        Vì sao nhận CẢ HAI: app KMP đã giữ 3 khung ảnh nhưng API cũ chỉ nhận 1 ->
        2 ảnh sau bị mất mà không báo lỗi. Giữ ``photoBase64`` để bản app đang cài
        không phải cập nhật cùng lúc (tương thích ngược); client gửi cả hai thì
        ``photos`` thắng.

        Magic bytes được kiểm cho **từng** ảnh qua ``giai_ma_anh``: không tin
        ``mimetype`` client gửi, không tin phần mở rộng tên file.
        """
        photos = params.get("photos")
        if isinstance(photos, list):
            if not photos:
                raise UserError(_("Ảnh hàng hoá: không có dữ liệu."))
            if len(photos) > self.SO_ANH_TOI_DA:
                raise UserError(
                    _(
                        "Chỉ được gửi tối đa %s ảnh hàng hoá mỗi lần (bạn gửi %s ảnh).",
                        self.SO_ANH_TOI_DA,
                        len(photos),
                    )
                )
            return [
                giai_ma_anh(anh, _("Ảnh hàng hoá thứ %s", thu_tu))
                for thu_tu, anh in enumerate(photos, start=1)
            ]
        if photos not in (None, False, ""):
            raise UserError(
                _(
                    "Trường \"photos\" phải là một MẢNG ảnh base64 "
                    "(ví dụ [\"...\", \"...\"]), tối đa %s ảnh.",
                    self.SO_ANH_TOI_DA,
                )
            )
        anh_base64 = params.get("photoBase64") or params.get("photo")
        return [giai_ma_anh(anh_base64, _("Ảnh hàng hoá"))]

    def _doc_ghi_chu(self, params):
        """Đọc ``note`` — ghi chú của tài xế, chuỗi ≤ 500 ký tự; rỗng thì bỏ qua."""
        ghi_chu = params.get("note")
        if ghi_chu in (None, False):
            return False
        if not isinstance(ghi_chu, str):
            raise UserError(_("Ghi chú của tài xế phải là chuỗi ký tự."))
        ghi_chu = ghi_chu.strip()
        if len(ghi_chu) > self.GHI_CHU_TOI_DA:
            raise UserError(
                _(
                    "Ghi chú của tài xế tối đa %s ký tự (bạn nhập %s ký tự).",
                    self.GHI_CHU_TOI_DA,
                    len(ghi_chu),
                )
            )
        return ghi_chu or False

    @staticmethod
    def _ten_anh(job, thu_tu, tong_so, mime):
        """Tên file ảnh. 1 ảnh giữ đúng tên cũ; nhiều ảnh thì đánh số thứ tự."""
        duoi = "jpg" if mime == "image/jpeg" else "png"
        if tong_so <= 1:
            return "%s-anh-hang.%s" % (job.name, duoi)
        return "%s-anh-hang-%s.%s" % (job.name, thu_tu, duoi)

    @http.route(
        "/lab/api/v1/driver/jobs/<int:job_id>/capture",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def capture(self, job_id, **kwargs):
        """Tài xế gửi ảnh + chữ ký + số kiện + kg khi đã lấy được hàng.

        Payload (JSON):

        * ``photos`` — **mảng** base64, tối đa 3 ảnh (app KMP giữ 3 khung: hàng
          hoá, nhãn/kiện, hiện trường).
        * ``photoBase64`` — ảnh đơn, đường cũ. VẪN nhận để bản app đang cài không
          phải cập nhật cùng lúc; nếu client gửi cả hai thì ``photos`` thắng.
        * ``note`` — ghi chú của tài xế, chuỗi ≤ 500 ký tự.
        * ``signatureBase64`` — chữ ký PNG (bắt buộc).
        * ``packages`` · ``weightKg`` · ``clientUuid`` (chống trùng).
        """
        loi_token = self._xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("driver-capture:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)
        job = self._job(job_id, can_write=True)
        if not job:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy việc này của bạn.")), status=404
            )

        params = tham_so()
        client_uuid = (params.get("clientUuid") or "").strip()

        # Tài xế là portal nên KHÔNG có ACL đọc ir.attachment (đọc `job.anh_ids`
        # trả 403). Vì vậy mọi thao tác attachment đi qua đúng MỘT env sudo này,
        # luôn giới hạn theo `lab_lay_hang_id` = job mà tài xế đã qua ir.rule.
        Attachment = request.env["ir.attachment"].sudo()  # sudo-ok: đọc/ghi attachment của CHÍNH job đã qua ir.rule (portal không có ACL ir.attachment); nội dung đã validate magic bytes + <=5MB

        # Idempotent (review skill §6): app gửi lại do mất mạng/ bấm 2 lần thì trả
        # kết quả cũ, KHÔNG tạo bộ ảnh thứ hai.
        if client_uuid and client_uuid == job.lab_client_uuid and job.state in (
            "picked_up",
            "confirmed",
        ):
            return tra_loi(
                ok(
                    {
                        "state": job.state,
                        "attachment_ids": Attachment.search(
                            [("lab_lay_hang_id", "=", job.id)]
                        ).ids,
                        "duplicate": True,
                    }
                )
            )

        # Kiểm state TRƯỚC khi tạo attachment: nếu state sai thì không để lại rác.
        if job.state not in ("assigned", "en_route"):
            return tra_loi(
                loi(
                    "INVALID_STATE",
                    _(
                        "Không thể gửi ảnh/chữ ký khi việc đang ở trạng thái \"%s\".",
                        self._nhan_state(job),
                    ),
                ),
                status=409,
            )

        try:
            so_kien = so_nguyen(params.get("packages"), _("Số kiện thực tế"))
            khoi_luong = so_thuc(params.get("weightKg"), _("Khối lượng (kg)"))
            danh_sach_anh = self._doc_danh_sach_anh(params)
            ky_base64 = params.get("signatureBase64") or params.get("signature")
            ky_raw, _ky_mime = giai_ma_anh(ky_base64, _("Chữ ký"))
            ghi_chu = self._doc_ghi_chu(params)
            if not so_kien:
                raise UserError(
                    _("Phải nhập số kiện thực tế (lớn hơn 0) — không được để trống.")
                )
        except UserError as exc:
            return tra_loi(loi("VALIDATION", str(exc)), status=400)

        # Ghi TỪNG ảnh thành ir.attachment (res_model/res_id + lab_lay_hang_id) —
        # dùng chung env sudo đã khai ở đầu hàm, không mở thêm đường quyền nào.
        # Một lời gọi create() với cả danh sách: 1 query, không tạo nửa vời.
        dinh_kem = Attachment.create(
            [
                {
                    "name": self._ten_anh(job, thu_tu, len(danh_sach_anh), anh_mime),
                    "datas": base64.b64encode(anh_raw).decode(),
                    "mimetype": anh_mime,
                    "res_model": job._name,
                    "res_id": job.id,
                    "lab_lay_hang_id": job.id,
                }
                for thu_tu, (anh_raw, anh_mime) in enumerate(danh_sach_anh, start=1)
            ]
        )

        gia_tri = {
            "chu_ky": base64.b64encode(ky_raw).decode(),
            "so_kien_thuc_te": so_kien,
            "khoi_luong_uoc_kg": khoi_luong,
            "lab_client_uuid": client_uuid or False,
        }
        if ghi_chu:
            gia_tri["ghi_chu_tai_xe"] = ghi_chu
        try:
            job.write(gia_tri)
            if job.state == "assigned":
                job.action_start()  # tôn trọng state machine: assigned -> en_route
            job.action_pick()  # en_route -> picked_up (+ message_post)
        except UserError as exc:
            return tra_loi(loi("INVALID_STATE", str(exc)), status=409)

        return tra_loi(
            ok(
                {
                    "state": job.state,
                    "stateLabel": self._nhan_state(job),
                    # Trả ĐỦ id của mọi ảnh vừa tạo để app không phải đoán/đếm.
                    "attachment_ids": dinh_kem.ids,
                }
            )
        )

    # ==================================================================
    # POST /lab/api/v1/driver/jobs/<id>/fail
    # ==================================================================
    @http.route(
        "/lab/api/v1/driver/jobs/<int:job_id>/fail",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def fail(self, job_id, **kwargs):
        """Tài xế báo không lấy được hàng — bắt buộc có lý do (docs/02 §3.2)."""
        loi_token = self._xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("driver-fail:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)
        job = self._job(job_id, can_write=True)
        if not job:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy việc này của bạn.")), status=404
            )
        params = tham_so()
        ly_do = (params.get("reason") or params.get("ly_do") or "").strip()
        if not ly_do:
            return tra_loi(
                loi("VALIDATION", _("Phải nhập lý do không lấy được hàng.")),
                status=400,
            )
        try:
            job.action_fail(ly_do=ly_do)
        except UserError as exc:
            return tra_loi(loi("INVALID_STATE", str(exc)), status=409)
        return tra_loi(
            ok({"state": job.state, "stateLabel": self._nhan_state(job)})
        )
