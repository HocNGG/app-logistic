# -*- coding: utf-8 -*-
"""Tra cứu công khai — `GET /lab/api/v1/track/<access_token>` (docs/02 §6.2).

Quy tắc cứng:
* ``auth='public'``, chỉ đọc, **không** trả id/giá/đối tác nội bộ/tên tài xế;
* token phải so bằng ``consteq`` (hằng thời gian như portal của Odoo), không ``==``;
* rate limit theo token + IP (cache trong RAM, demo 1 worker).
"""

from odoo import _, fields, http
from odoo.exceptions import AccessError
from odoo.http import request
from odoo.tools import consteq

from ..tools import (
    check_rate_limit,
    gio_phut,
    ip_client,
    loi,
    ok,
    thoi_diem_dia_phuong,
    tra_loi,
)


class LabTrackApi(http.Controller):
    @staticmethod
    def _moui_gio_hien_thi():
        """Múi giờ để format giờ cho khách: tz của công ty, mặc định UTC.

        Vì sao có try/except: request là public user; nếu bản cài đặt đặt rule
        chặt trên res.partner thì đọc tz công ty có thể bị chặn — hiển thị giờ
        UTC vẫn đúng hơn là làm sập trang tra cứu.
        """
        try:
            return request.env.company.partner_id.tz or "UTC"
        except AccessError:
            return "UTC"

    @http.route(
        "/lab/api/v1/track/<string:access_token>",
        type="http",
        auth="public",
        methods=["GET"],
        csrf=False,
        save_session=False,
    )
    def track(self, access_token, **kwargs):
        token = (access_token or "").strip()
        gioi_han = check_rate_limit(
            "track:%s:%s" % (ip_client(), token[:12]), limit=60, window=60
        )
        if gioi_han:
            return tra_loi(gioi_han, status=429)
        # Token ngắn hơn 16 ký tự chắc chắn không phải token do hệ thống sinh.
        if len(token) < 16:
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy vận đơn với mã tra cứu này.")),
                status=404,
            )

        # Public user không có quyền đọc lab.hanh.trinh -> tra bằng sudo, nhưng:
        # (1) token không đoán được, (2) so sánh consteq, (3) CHỈ trả whitelist
        # bên dưới (không trả recordset). Đúng ngoại lệ "đọc whitelist cho /track"
        # trong agents/review/skills/03-security-audit.md §5.
        ht_sudo = request.env["lab.hanh.trinh"].sudo()  # sudo-ok: tra cứu công khai theo access_token không đoán được, chỉ trả field whitelist
        hanh_trinh = ht_sudo.search([("access_token", "=", token)], limit=1)
        if not hanh_trinh or not consteq(hanh_trinh.access_token or "", token):
            return tra_loi(
                loi("NOT_FOUND", _("Không tìm thấy vận đơn với mã tra cứu này.")),
                status=404,
            )

        # Giờ hiển thị theo múi giờ công ty (public user không có tz riêng).
        tz = self._moui_gio_hien_thi()
        ht = hanh_trinh.with_context(tz=tz)

        # Khung giờ lấy hàng: đọc qua quan hệ đã sudo ở trên (sale.order ->
        # lab_lay_hang_ids) nên không cần thêm lần sudo nào.
        yeu_cau = ht.sale_order_id.lab_lay_hang_ids.filtered("khung_gio_tu")[:1]

        du_lieu = {
            "ref": ht.name,
            "stage": ht.stage,
            "stageLabel": dict(ht._fields["stage"].selection).get(ht.stage, ht.stage),
            "documentType": ht.loai_chung_tu or "",
            "awb": ht.so_awb or "",
            "hbl": ht.so_hbl or "",
            "origin": ht.cang_di or "",
            "destination": ht.cang_den or "",
            "departureAt": thoi_diem_dia_phuong(ht, ht.ngay_bay),
            "eta": thoi_diem_dia_phuong(ht, ht.ngay_den),
            "podDate": thoi_diem_dia_phuong(ht, ht.pod_date),
            "windowFrom": gio_phut(ht, yeu_cau.khung_gio_tu),
            "windowTo": gio_phut(ht, yeu_cau.khung_gio_den),
            "timezone": tz,
            "events": [
                {
                    "at": thoi_diem_dia_phuong(ht, line.thoi_diem),
                    "label": line.moc,
                    "place": line.dia_diem or "",
                }
                for line in ht.line_ids
            ],
        }
        # OQ-22: số kiện dự kiến chỉ trả khi có nguồn thật trên yêu cầu lấy hàng.
        if yeu_cau.so_kien_du_kien:
            du_lieu["expectedPackages"] = yeu_cau.so_kien_du_kien
        return tra_loi(ok(du_lieu))

    # ==================================================================
    # TRANG TRA CỨU KIỆN (HTML, công khai) — 2026-11-02
    # GET /lab/track/<khoá>/<mã kiện>
    #   * khoá = access_token của YC lấy hàng (tem nhận), hoặc
    #   * khoá = mã chuyến đổi '/' -> '-' (tem xuất, chuyến không có token)
    # Mục đích: TEM IN RA CÓ QR, quét bằng camera điện thoại bất kỳ là mở trang này
    # — không cần app, không cần đăng nhập.
    # ==================================================================
    @http.route(
        "/lab/track/<string:khoa>/<path:kien_ma>",
        type="http",
        auth="public",
        methods=["GET"],
        csrf=False,
        save_session=False,
    )
    def track_kien(self, khoa, kien_ma, **kwargs):
        khoa = (khoa or "").strip()
        kien_ma = (kien_ma or "").strip()
        gioi_han = check_rate_limit(
            "track-kien:%s:%s" % (ip_client(), khoa[:12]), limit=60, window=60
        )
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        # sudo-ok: tra cứu công khai, khoá không đoán được, chỉ trả field whitelist.
        Package = request.env["stock.quant.package"].sudo()  # sudo-ok: trang tra cứu công khai theo token/QR; chỉ đọc field whitelist, không lộ giá/khách khác
        kien = Package.search([("name", "=", kien_ma)], limit=1)
        if not kien:
            return request.not_found()

        chuyen = kien.lab_chuyen_id
        # Khoá tra cứu = TOKEN ngẫu nhiên của chuyến (KHÔNG phải mã chuyến — mã chuyến
        # đoán được; RV-01 vòng 2). So sánh hằng thời gian, cùng độ dài.
        hop_le = bool(
            chuyen
            and chuyen.access_token
            and len(khoa) == len(chuyen.access_token)
            and consteq(chuyen.access_token, khoa)
        )
        job = None
        if not hop_le:
            Job = request.env["lab.yeu.cau.lay.hang"].sudo()  # sudo-ok: trang tra cứu công khai theo token/QR; chỉ đọc field whitelist, không lộ giá/khách khác
            job = Job.search([("access_token", "=", khoa)], limit=1)
            hop_le = bool(job) and consteq(job.access_token or "", khoa) and kien in job.kien_ids
        if not hop_le:
            return request.not_found()

        # Việc (YC lấy hàng) của kiện: lấy từ phiếu nhập chứa kiện.
        if job is None:
            # `stock.quant.package` KHÔNG có `move_line_ids` ở 17.0 (lỗi thật 500):
            # phải đi từ dòng hàng -> phiếu nhập -> yêu cầu lấy hàng.
            MoveLine = request.env["stock.move.line"].sudo()  # sudo-ok: trang tra cứu công khai theo token/QR; chỉ đọc field whitelist, không lộ giá/khách khác
            dong = MoveLine.search([("result_package_id", "=", kien.id)], limit=1)
            if not dong:
                dong = MoveLine.search([("package_id", "=", kien.id)], limit=1)
            Job = request.env["lab.yeu.cau.lay.hang"].sudo()  # sudo-ok: trang tra cứu công khai theo token/QR; chỉ đọc field whitelist, không lộ giá/khách khác
            job = Job.search([("stock_picking_id", "=", dong.picking_id.id)], limit=1) if dong else Job

        tz = self._moui_gio_hien_thi()
        Event = request.env["lab.scan.event"].sudo()  # sudo-ok: trang tra cứu công khai theo token/QR; chỉ đọc field whitelist, không lộ giá/khách khác
        quet = Event.search([("kien_id", "=", kien.id)], order="thoi_diem asc")

        def _luc(gia_tri):
            if not gia_tri:
                return ""
            # context_timestamp cần một RECORDSET có tz trong context -> dùng chính kiện
            # đã with_context(tz=...) để giờ hiển thị đúng múi giờ công ty.
            return fields.Datetime.context_timestamp(
                kien.with_context(tz=tz), gia_tri
            ).strftime("%d/%m/%Y %H:%M")

        moc = [
            {
                "nhan": _("Nhận hàng tại kho"),
                "luc": _luc(job.ngay_xac_nhan if job else False),
                "xong": bool(job and job.ngay_xac_nhan),
            },
            {
                "nhan": _("In tem xuất"),
                "luc": _luc(kien.lab_in_tem_luc),
                "xong": bool(kien.lab_ma_xuat),
            },
            {
                "nhan": _("Xuất kho lên chuyến"),
                "luc": _luc(kien.lab_xuat_luc),
                "xong": bool(kien.lab_xuat_luc),
            },
            {
                "nhan": _("Giao khách"),
                "luc": _luc(chuyen.write_date) if chuyen and chuyen.state == "delivered" else "",
                "xong": bool(chuyen and chuyen.state in ("delivered", "closed")),
            },
        ]
        gia_tri = {
            "kien_ma": kien.name,
            "ma_xuat": kien.lab_ma_xuat or "",
            "trang_thai": dict(kien._fields["lab_trang_thai"].selection).get(
                kien.lab_trang_thai, ""
            ),
            "shop": (job.partner_id.name if job and job.partner_id else ""),
            "so_kien": (
                job.so_kien_thuc_te or job.so_kien_du_kien or len(job.kien_ids)
                if job
                else len(chuyen.kien_xuat_ids)
            ),
            "chuyen": chuyen.name or "",
            "moc": moc,
            "so_lan_quet": len(quet),
            "timezone": tz,
        }
        return request.render("lab_api.track_kien_page", gia_tri)