# -*- coding: utf-8 -*-
from odoo import fields, models


class StockQuantPackage(models.Model):
    """Mở rộng KIỆN (`stock.quant.package`) cho phần XUẤT chuyến — L2, không model mới.

    VÌ SAO KHAI Ở MODULE lab_chuyen (không phải lab_laixe):
      ``lab_laixe`` sinh kiện lúc kho nhận (mã `KIEN/26/…`), còn **mã xuất** chỉ có
      nghĩa khi hàng lên chuyến — đó là phạm vi `lab_chuyen`. Khai ở đây giữ đúng
      chiều phụ thuộc (lab_laixe KHÔNG phải biết về chuyến).

    VÌ SAO KHÔNG TẠO MODEL `kien`:
      Odoo đã có ``stock.quant.package`` mang đúng ngữ nghĩa "một kiện/thùng"
      (AGENTS.md Luật 1 + bảng từ vựng cấm). Ở đây chỉ thêm 3 field phục vụ tem xuất.

    CHỐT 2026-11-02: **mỗi kiện một tem xuất** (M7) ⇒ mỗi kiện có `lab_ma_xuat` riêng.
    """

    _inherit = "stock.quant.package"

    lab_ma_xuat = fields.Char(
        string="Mã tem xuất",
        copy=False,
        index=True,
        help="Mã in trên tem xuất của kiện này (`XUAT/26/000123`). Sinh khi chuyến "
             "được chốt booking. Không đổi khi in lại (in lại chỉ ghi log).",
    )
    lab_xuat_luc = fields.Datetime(
        string="Xuất kho lúc",
        copy=False,
        help="Thời điểm kiện thực sự rời kho (kho quét tem xuất / xác nhận xuất).",
    )
    lab_chuyen_id = fields.Many2one(
        comodel_name="lab.chuyen",
        string="Chuyến xuất",
        copy=False,
        index=True,
        ondelete="set null",
        help="Chuyến mà kiện này được xuất đi. Trỏ thẳng để bảng tổng kết đọc nhanh, "
             "không phải đi qua phiếu kho.",
    )
    lab_trang_thai = fields.Selection(
        selection=[
            ("in_stock", "Trong kho"),
            ("packed", "Đã in tem xuất"),
            ("shipped", "Đã xuất kho"),
            ("cancelled", "Không xuất"),
        ],
        string="Trạng thái kiện",
        default="in_stock",
        copy=False,
        index=True,
        help="Vòng đời kiện: nhận hàng → in tem xuất → quét xuất → (giao khách). "
             "Dùng cho màn 'Kho · Xuất kho' (tiến độ đã quét x/N).",
    )
    lab_in_tem_luc = fields.Datetime(
        string="In tem xuất lúc",
        copy=False,
        help="Thời điểm hệ thống sinh mã tem xuất cho kiện (lúc chuyến chốt booking). "
             "Dùng cho trang tra cứu công khai — mốc 2/4.",
    )
