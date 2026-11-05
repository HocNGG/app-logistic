# -*- coding: utf-8 -*-
from odoo import fields, models


class StockPicking(models.Model):
    """Nối phiếu kho (native) với chuyến — bước ⑦.

    Vì sao field này nằm ở đây mà không nằm ở lab_core: một Many2one trỏ tới
    lab.chuyen chỉ resolve được khi module khai báo nó depends lab_chuyen.
    lab_core KHÔNG được depends lab_* (docs/02 §1), nên field trỏ về chuyến phải
    do lab_chuyen khai báo (module sở hữu quan hệ). Xem README.rst lab_core.

    Đây là L2 (kế thừa nhẹ): chỉ thêm field, không đổi semantics của stock.picking.
    """

    _inherit = "stock.picking"

    lab_chuyen_id = fields.Many2one(
        comodel_name="lab.chuyen",
        string="Chuyến",
        copy=False,
        index=True,
        help="Chuyến chứa phiếu xuất này (bước ⑦ xuất kho lên chuyến).",
    )
    lab_xuat_client_uuid = fields.Char(
        string="Mã chống trùng lần xác nhận xuất",
        copy=False,
        index=True,
        help="App kho gửi `clientUuid` khi bấm 'Xác nhận xuất'. Gửi lại cùng UUID "
             "(mạng chập, bấm 2 lần) ⇒ trả kết quả cũ, KHÔNG validate lại phiếu.",
    )
    lab_xuat_confirm_luc = fields.Datetime(
        string="Xác nhận xuất lúc",
        copy=False,
        readonly=True,
        help="Thời điểm kho bấm 'Xác nhận xuất' (phiếu được validate).",
    )
