# -*- coding: utf-8 -*-
from odoo import api, fields, models


class SaleOrder(models.Model):
    """Chiều ngược của ``lab.yeu.cau.lay.hang.sale_order_id`` — L2.

    Vì sao cần: (1) màn CS thấy ngay "đơn này đã có yêu cầu lấy hàng chưa";
    (2) endpoint công khai ``/track/<token>`` đọc được khung giờ lấy hàng của đơn
    **qua quan hệ** thay vì phải thêm một lần ``sudo`` nữa (docs/02 §6.2);
    (3) khi gom đơn vào Chuyến (``lab.chuyen.line.sale_order_id``), CS có thể gõ
    **mã đơn bán** (``S0xxxx``), **mã yêu cầu lấy hàng** (``LH/26/...``) hoặc
    **tên shop** đều tìm thấy ngay.

    Không có field mới nào trỏ sang module khác ngoài ``lab_yeu_cau_lay_hang``
    (module này sở hữu) nên không tạo phụ thuộc vòng (docs/02 §1).
    """

    _inherit = "sale.order"
    _rec_names_search = [
        "name",
        "client_order_ref",
        "origin",
        "lab_shop_ref",
        "partner_id.name",
        "lab_lay_hang_ids.name",
    ]

    lab_lay_hang_ids = fields.One2many(
        comodel_name="lab.yeu.cau.lay.hang",
        inverse_name="sale_order_id",
        string="Yêu cầu lấy hàng",
        help="Các yêu cầu tài xế đi lấy hàng cho đơn này (bước ②).",
    )

    @api.depends("name", "origin", "lab_shop_ref", "partner_id.name")
    def _compute_display_name(self):
        super()._compute_display_name()
        for order in self:
            ma_yc = (order.lab_shop_ref or order.origin or "").strip()
            if ma_yc.startswith("LH/"):
                ten_shop = (order.partner_id.name or "").strip()
                phu = "%s · %s" % (ma_yc, ten_shop) if ten_shop else ma_yc
                order.display_name = "%s (%s)" % (order.name or "", phu)
