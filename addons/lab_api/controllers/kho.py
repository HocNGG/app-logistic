# -*- coding: utf-8 -*-
"""Controller cho màn NHẬN HÀNG của Kho (docs/02-kien-truc.md §6.2, ADR-004).

2 endpoint:

* ``GET  /lab/api/v1/kho/cho-nhan``            — việc ``picked_up`` đang chờ kho nhận
* ``POST /lab/api/v1/kho/jobs/<id>/nhan``      — xác nhận hàng về kho (``action_confirm``)

Quyền: ``auth='user'`` + ``X-Lab-Token`` + nhóm ``lab_core.group_warehouse`` (403
nếu không thuộc — ``helpers.kiem_nhom``). Phạm vi bản ghi do ir.rule + ACL
(Luật 3).

REA (AGENTS.md Luật 4): ``nhan`` không chỉ đổi state — ``action_confirm`` của
model **sinh ``stock.picking`` nhập kho** (phiếu native, không phải model mới),
và chọn kho mặc định của công ty hiện tại (xem ``_tao_phieu_nhap_kho``).
"""

from odoo import _, fields, http
from odoo.exceptions import UserError
from odoo.http import request

from ..helpers import (
    job_dict_kho,
    job_theo_quyen,
    kiem_nhom,
    nhan_state,
    xac_thuc_token,
)
from ..tools import check_rate_limit, loi, ok, tra_loi

#: Chỉ việc tài xế ĐÃ lấy được hàng mới chờ kho nhận.
TRANG_THAI_CHO_NHAN = "picked_up"
TRANG_THAI_DA_VE_KHO = "confirmed"

#: Trần số việc trả về một lần.
SO_VIEC_TOI_DA = 100
SO_VIEC_DA_NHAN_GAN_DAY = 30


class LabKhoApi(http.Controller):
    @staticmethod
    def _chan_quyen():
        """403 nếu người đang đăng nhập không thuộc nhóm Kho (xem ``helpers.kiem_nhom``)."""
        return kiem_nhom(
            "lab_core.group_warehouse",
            _("Chức năng nhận hàng chỉ dành cho bộ phận Kho."),
        )

    # ==================================================================
    # GET /lab/api/v1/kho/cho-nhan
    # ==================================================================
    @http.route(
        "/lab/api/v1/kho/cho-nhan",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def cho_nhan(self, **kwargs):
        """Hàng chờ kho nhận + (tuỳ chọn ``includeConfirmed=1``) việc vừa về kho để quét tem.

        Sắp xếp ``id desc`` (mã ``LH/26/...`` mới nhất ở đầu danh sách) để nhân viên kho
        thấy ngay đơn vừa lấy về hoặc vừa nhận tại quầy mà không phải cuộn qua dữ liệu cũ.
        """
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("kho-cho-nhan:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        Job = request.env["lab.yeu.cau.lay.hang"]
        jobs = Job.search(
            [("state", "=", TRANG_THAI_CHO_NHAN)],
            order="id desc",
            limit=SO_VIEC_TOI_DA,
        )
        kem_da_nhan = (
            kwargs.get("includeConfirmed") or request.httprequest.args.get("includeConfirmed") or ""
        ).strip().lower() in ("1", "true", "yes")
        if kem_da_nhan:
            jobs_da_nhan = Job.search(
                [
                    ("state", "=", TRANG_THAI_DA_VE_KHO),
                    ("stock_picking_id", "!=", False),
                ],
                order="id desc",
                limit=SO_VIEC_DA_NHAN_GAN_DAY,
            )
            jobs = (jobs | jobs_da_nhan).sorted(key=lambda j: j.id, reverse=True)[
                :SO_VIEC_TOI_DA
            ]
        return tra_loi(ok({"items": [job_dict_kho(job) for job in jobs]}))

    # ==================================================================
    # POST /lab/api/v1/kho/jobs/<id>/nhan
    # ==================================================================
    @http.route(
        "/lab/api/v1/kho/jobs/<int:job_id>/nhan",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def nhan(self, job_id, **kwargs):
        """Xác nhận hàng đã về kho — ``action_confirm`` (đổi state + sinh phiếu nhập).

        Sai trạng thái (hoặc kho chưa cấu hình loại phiếu nhập) -> ``UserError``
        của model -> 409 ``INVALID_STATE`` kèm câu tiếng Việt của chính model.
        """
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("kho-nhan:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        job = job_theo_quyen(job_id, can_write=True)
        if not job:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy yêu cầu lấy hàng này.")), status=404
            )

        # savepoint: action_confirm vừa tạo stock.picking vừa đổi state + chatter.
        # Lỗi giữa đường thì KHÔNG được để lại phiếu nhập kho dở dang.
        try:
            with request.env.cr.savepoint():
                job.action_confirm()
        except UserError as exc:
            return tra_loi(loi("INVALID_STATE", str(exc)), status=409)

        ten_don_ban = job.sudo().sale_order_id.name or ""  # sudo-ok: chỉ đọc mã đơn bán S0xxxx gắn với yêu cầu lấy hàng sau khi kho xác nhận nhận hàng
        ket_qua = {
            "state": job.state,
            "stateLabel": nhan_state(job),
            "pickingName": job.stock_picking_id.name or "",
        }
        if ten_don_ban:
            ket_qua["saleOrderName"] = ten_don_ban
        return tra_loi(ok(ket_qua))

    # ==================================================================
    # GET /lab/api/v1/kho/jobs/<id>/kien
    # ==================================================================
    @http.route(
        "/lab/api/v1/kho/jobs/<int:job_id>/kien",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def kien_cua_job(self, job_id, **kwargs):
        """Danh sách KIỆN của một việc — để FE hiện ngay sau khi kho bấm "Nhận hàng".

        VÌ SAO CẦN (FE-01 báo thiếu, 2026-11-03): sau khi nhận hàng, kho phải DÁN TEM
        rồi QUÉT từng kiện. Màn app cần biết "việc này có những kiện nào, kiện nào đã
        quét" — nhưng chưa có endpoint nào trả về.

        NGUỒN SỰ THẬT: `job._lay_kien()` (đọc từ `stock.picking.move_line_ids.result_package_id`
        của phiếu nhập) — KHÔNG tự khai danh sách riêng, để danh sách luôn khớp chứng từ kho.

        ``daQuet`` = có `lab.scan.event` với ``loai='nhan'`` + ``ket_qua='ok'`` cho kiện đó;
        ``quetLuc`` = thời điểm quét MỚI NHẤT (UTC, ``yyyy-MM-dd HH:mm:ss``).
        """
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("kho-kien:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        job = job_theo_quyen(job_id)
        if not job:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy yêu cầu lấy hàng này.")), status=404
            )

        kien = job._lay_kien()
        Event = request.env["lab.scan.event"]
        quet = Event.search(
            [
                ("kien_id", "in", kien.ids),
                ("loai", "=", "nhan"),
                ("ket_qua", "=", "ok"),
            ],
            order="thoi_diem desc",
        )
        # Mỗi kiện lấy lần quét MỚI NHẤT (search đã order desc nên phần tử đầu là mới nhất).
        luc_theo_kien = {}
        for event in quet:
            luc_theo_kien.setdefault(event.kien_id.id, event.thoi_diem)

        danh_sach = []
        da_quet = 0
        for k in kien:
            luc = luc_theo_kien.get(k.id)
            if luc:
                da_quet += 1
            danh_sach.append(
                {
                    "ma": k.name,
                    "maXuat": k.lab_ma_xuat or "",
                    "trangThai": k.lab_trang_thai or "",
                    "trangThaiLabel": dict(
                        k._fields["lab_trang_thai"].selection
                    ).get(k.lab_trang_thai, ""),
                    "daQuet": bool(luc),
                    "quetLuc": fields.Datetime.to_string(luc) if luc else None,
                }
            )

        return tra_loi(
            ok(
                {
                    "job": job.name,
                    "soKien": len(danh_sach),
                    "daQuet": da_quet,
                    "kien": danh_sach,
                }
            )
        )