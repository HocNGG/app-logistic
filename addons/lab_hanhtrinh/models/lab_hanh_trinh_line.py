# -*- coding: utf-8 -*-
from odoo import fields, models


class LabHanhTrinhLine(models.Model):
    """Một mốc trên dòng thời gian hành trình (timeline mà khách nhìn thấy).

    Nguồn: docs/02-kien-truc.md §2.4. Bảng REA (docs/02 §11) không coi đây là
    sự kiện kinh tế — nó là *nhật ký* của hành trình, nên không sinh bút toán.

    Ví dụ ``moc``: "Đã lên máy bay", "Đã hạ cánh Tân Sơn Nhất", "Đại lý đã nhận".
    """

    _name = "lab.hanh.trinh.line"
    _description = "Mốc hành trình"
    _order = "id"

    hanhtrinh_id = fields.Many2one(
        comodel_name="lab.hanh.trinh",
        string="Hành trình",
        required=True,
        ondelete="cascade",
        index=True,
    )
    moc = fields.Char(
        string="Mốc hành trình",
        required=True,
        help="Nhãn hiển thị cho khách: 'Đã lên máy bay', 'Đại lý đã nhận', ...",
    )
    thoi_diem = fields.Datetime(string="Thời điểm")
    dia_diem = fields.Char(string="Địa điểm")
    nguoi_cap_nhat_id = fields.Many2one(
        comodel_name="res.users",
        string="Người cập nhật",
        default=lambda self: self.env.user,
        readonly=True,
    )
