# -*- coding: utf-8 -*-
from odoo import fields, models


class IrAttachment(models.Model):
    """Cho phép ``lab.yeu.cau.lay.hang.anh_ids`` là One2many thật.

    Vì sao cần file này: One2many BẮT BUỘC có field nghịch (m2o) ở model đích.
    ``ir.attachment`` là model "generic" (dùng cặp ``res_model``/``res_id``, kiểu
    Integer) nên **không** dùng làm inverse cho o2m được — khai
    ``One2many('ir.attachment', 'res_id')`` sẽ làm cài đặt lỗi. Cách đúng của
    Odoo là thêm một m2o thật trỏ ngược về chứng từ, đúng như file này.

    Đây là L2: chỉ thêm field vào model native, không đổi semantics.
    """

    _inherit = "ir.attachment"

    lab_lay_hang_id = fields.Many2one(
        comodel_name="lab.yeu.cau.lay.hang",
        string="Yêu cầu lấy hàng",
        index=True,
        ondelete="cascade",
        help="Ảnh hàng hoá tài xế chụp trong app KMP.",
    )
