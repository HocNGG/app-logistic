# -*- coding: utf-8 -*-
from odoo import fields, models


class StockPicking(models.Model):
    """Field mở rộng của phiếu kho — L2, không đổi semantics của ``stock.picking``.

    Nguồn: docs/02-kien-truc.md §2.1 (bảng "Inherit | stock.picking").
    Dùng cho bước ③④⑦: cân–đo tại kho, đếm kiện, và đánh dấu "hàng chờ khách".
    """

    _inherit = "stock.picking"

    lab_pickup_mode = fields.Selection(
        related="sale_id.lab_pickup_mode",
        string="Hình thức vào kho",
        store=True,
        readonly=True,
        help="Lấy từ đơn hàng để lọc phiếu kho theo cách hàng vào kho.",
    )
    lab_weight_kg = fields.Float(
        string="Kg thực tế",
        digits=(12, 3),
        help="Khối lượng cân thật tại kho (bước ③). Khác ``weight`` của "
             "stock.move.line là số do hãng/đơn vị vận chuyển khai.",
    )
    lab_package_count = fields.Integer(
        string="Số kiện",
        help="Số kiện đếm được khi nhận/đóng gói (kiện là stock.quant.package — "
             "không tạo model kiện riêng).",
    )
    lab_is_cho_khach = fields.Boolean(
        string="Hàng chờ khách",
        tracking=True,
        help="Bật khi hàng đã về kho nhưng khách chưa gửi/ chưa đủ chứng từ — "
             "vào nhánh ngoại lệ ① (docs/07), điểm quay về là bước ③.",
    )
