# -*- coding: utf-8 -*-
from odoo import fields, models


class SaleOrder(models.Model):
    """Field mở rộng của đơn hàng — L2, không đổi semantics của ``sale.order``.

    Nguồn: docs/02-kien-truc.md §2.1 (bảng "Inherit | sale.order").
    Vì sao nằm ở ``lab_core``: đây là field **không trỏ sang module nghiệp vụ nào**
    nên không tạo phụ thuộc vòng (docs/02 §1, đính chính ★ ở §2.1).

    Phạm vi Wave B (Orchestrator chốt): CHỈ 2 field dưới đây. Các field
    ``lab_chargeable_weight`` / ``lab_volumetric_weight`` / ``lab_cbm`` để P5
    (tính cước) — chưa khai ở đây để khỏi "khai field chết".
    """

    _inherit = "sale.order"

    lab_pickup_mode = fields.Selection(
        selection=[
            ("pickup", "Lấy tận nơi"),
            ("drop_off", "Khách tự mang tới kho"),
        ],
        string="Hình thức vào kho",
        default="pickup",
        required=True,
        tracking=True,
        help="Nhánh 'khách tự mang tới kho' của docs/07: KHÔNG tạo "
             "lab.yeu.cau.lay.hang, hàng vào thẳng bước ③.",
    )
    lab_shop_ref = fields.Char(
        string="Mã đơn của shop",
        copy=False,
        help="Số đơn do shop tự đánh (khác số đơn nội bộ của Odoo).",
    )
