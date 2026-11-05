# -*- coding: utf-8 -*-
from odoo import api, fields, models


class LabChuyenLine(models.Model):
    """Một đơn hàng (sale.order) được gom vào một chuyến.

    Nguồn: docs/02-kien-truc.md §2.2. Đây là "dòng chuyến" — chứng từ của sự
    kiện "Gom vào chuyến" trong bảng REA (docs/02 §11).

    Nhánh ngoại lệ ② (rớt chuyến / giảm tải) tác động lên `state` của DÒNG, không
    phải state của chuyến: booked -> waitlist -> (gán chuyến khác) -> booked.
    Điểm quay về: dòng ở `waitlist` được gán `chuyen_id` mới.
    """

    _name = "lab.chuyen.line"
    _description = "Đơn hàng trong chuyến"
    _order = "id"

    chuyen_id = fields.Many2one(
        comodel_name="lab.chuyen",
        string="Chuyến",
        required=True,
        ondelete="cascade",
        index=True,
    )
    sale_order_id = fields.Many2one(
        comodel_name="sale.order",
        string="Đơn hàng của shop",
        required=True,
        ondelete="restrict",
        index=True,
    )
    stock_picking_id = fields.Many2one(
        comodel_name="stock.picking",
        string="Phiếu xuất kho",
        help="Phiếu xuất tương ứng (bước ⑦); để trống khi đơn chưa xuất kho.",
    )
    partner_id = fields.Many2one(
        comodel_name="res.partner",
        string="Khách hàng",
        related="sale_order_id.partner_id",
        store=True,
        readonly=True,
        index=True,
        help="Lấy từ đơn hàng, store để lọc/nhóm nhanh theo shop.",
    )

    # Snapshot tại thời điểm gán vào chuyến (không compute lại — số liệu đã chốt
    # với khách không được tự đổi khi cân lại ở kho).
    khoi_luong_kg = fields.Float(string="Khối lượng (kg)", digits=(16, 2))
    the_tich_cbm = fields.Float(string="Thể tích (CBM)", digits=(16, 4))
    so_kien = fields.Integer(string="Số kiện")

    state = fields.Selection(
        selection=[
            ("booked", "Đã vào chuyến"),
            ("waitlist", "Chờ chuyến sau"),
            ("removed", "Đã gỡ khỏi chuyến"),
            ("cancelled", "Đã huỷ"),
        ],
        string="Trạng thái dòng",
        required=True,
        default="booked",
        index=True,
    )
    ly_do_go = fields.Char(
        string="Lý do gỡ khỏi chuyến",
        help="Bắt buộc nhập khi chuyển dòng sang Đã gỡ (nhánh ②). Không xoá dòng "
             "để giữ audit trail (OQ-05).",
    )

    @api.model
    def _so_lieu_tu_yeu_cau_lay_hang(self, sale_order_id):
        """Lấy ``(số kiện, khối lượng kg)`` từ yêu cầu lấy hàng đã về kho của đơn bán."""
        if not sale_order_id or "lab.yeu.cau.lay.hang" not in self.env:
            return 0, 0.0
        YeuCau = self.env["lab.yeu.cau.lay.hang"].sudo()  # sudo-ok: đọc số kiện/khối lượng thực nhận tại kho của yêu cầu lấy hàng gắn với đơn bán để tự điền vào dòng chuyến
        viec = YeuCau.search(
            [("sale_order_id", "=", sale_order_id), ("state", "=", "confirmed")]
        )
        if not viec:
            return 0, 0.0
        so_kien = sum(len(v._lay_kien()) or int(v.so_kien_thuc_te or 0) for v in viec)
        khoi_luong = sum(float(v.khoi_luong_uoc_kg or 0.0) for v in viec)
        return so_kien, khoi_luong

    @api.onchange("sale_order_id")
    def _onchange_sale_order_id_dien_kien_kg(self):
        """Khi CS chọn 'Đơn hàng của shop' trong chuyến → tự điền số kiện & kg đã về kho."""
        for rec in self:
            if not rec.sale_order_id:
                continue
            so_kien, khoi_luong = self._so_lieu_tu_yeu_cau_lay_hang(rec.sale_order_id.id)
            if so_kien and not rec.so_kien:
                rec.so_kien = so_kien
            if khoi_luong and not rec.khoi_luong_kg:
                rec.khoi_luong_kg = khoi_luong

    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            don_id = vals.get("sale_order_id")
            if don_id and (not vals.get("so_kien") or not vals.get("khoi_luong_kg")):
                so_kien, khoi_luong = self._so_lieu_tu_yeu_cau_lay_hang(don_id)
                if so_kien and not vals.get("so_kien"):
                    vals["so_kien"] = so_kien
                if khoi_luong and not vals.get("khoi_luong_kg"):
                    vals["khoi_luong_kg"] = khoi_luong
        return super().create(vals_list)
