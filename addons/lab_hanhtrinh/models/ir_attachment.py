# -*- coding: utf-8 -*-
from odoo import fields, models


class IrAttachment(models.Model):
    """Cho phép ``lab.hanh.trinh.pod_attachment_ids`` là One2many thật.

    One2many bắt buộc có field nghịch dạng Many2one ở model đích. ``ir.attachment``
    là model generic (``res_model``/``res_id`` kiểu Integer) nên không làm inverse
    được — xem giải thích đầy đủ ở ``addons/lab_laixe/models/ir_attachment.py``.

    Đây là L2: chỉ thêm field, không đổi semantics của ir.attachment.
    """

    _inherit = "ir.attachment"

    lab_hanhtrinh_id = fields.Many2one(
        comodel_name="lab.hanh.trinh",
        string="Hành trình",
        index=True,
        ondelete="cascade",
        help="Bằng chứng giao hàng (POD) do đại lý tải lên.",
    )
