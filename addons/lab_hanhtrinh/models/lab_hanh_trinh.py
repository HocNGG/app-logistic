# -*- coding: utf-8 -*-
import secrets

from odoo import _, api, fields, models


class LabHanhTrinh(models.Model):
    """Hành trình quốc tế của một lô hàng (bước ⑨⑩⑪).

    Nguồn: docs/02-kien-truc.md §2.4 (field) + §3.3 (stage machine).

    Vì sao KHÔNG thêm state vào stock.picking (bẫy số 1 của docs/01 §4):
    hàng đã ra khỏi kho công ty thì ``stock.picking`` đã ``done`` — nó không còn
    gì để theo dõi. Hành trình là thực thể riêng, theo dõi phần "sau kho":
    lên máy bay/tàu, đến kho đối tác, đại lý giao khách (POD).

    Phạm vi P0: chỉ KHAI field + stage. Transition, timeline tự sinh, portal
    ``access_token`` và report AWB/HBL là P4.
    """

    _name = "lab.hanh.trinh"
    _description = "Hành trình quốc tế của lô hàng"
    _order = "id desc"
    _inherit = ["mail.thread", "mail.activity.mixin", "lab.flow.mixin"]

    # ------------------------------------------------------------------
    # Định danh & liên kết
    # ------------------------------------------------------------------
    name = fields.Char(
        string="Số hành trình",
        required=True,
        readonly=True,
        copy=False,
        index=True,
        default=lambda self: _("Mới"),
    )
    chuyen_id = fields.Many2one(
        comodel_name="lab.chuyen",
        string="Chuyến",
        required=True,
        ondelete="restrict",
        index=True,
    )
    sale_order_id = fields.Many2one(
        comodel_name="sale.order",
        string="Đơn hàng",
        help="Hành trình theo từng lô (một chuyến có thể có nhiều lô).",
    )

    # ------------------------------------------------------------------
    # Chứng từ vận tải (bước ⑧)
    # ------------------------------------------------------------------
    loai_chung_tu = fields.Selection(
        selection=[
            ("awb", "AWB — vận đơn hàng không"),
            ("hbl", "HBL — vận đơn đường biển"),
        ],
        string="Loại vận đơn",
    )
    so_awb = fields.Char(string="Số AWB", copy=False)
    so_hbl = fields.Char(string="Số HBL", copy=False)
    so_manifest = fields.Char(string="Số manifest", copy=False)

    # ------------------------------------------------------------------
    # Hành trình
    # ------------------------------------------------------------------
    stage = fields.Selection(
        selection=[
            ("draft", "Nháp"),
            ("booked", "Đã book"),
            ("departed", "Đã khởi hành"),
            ("in_transit", "Đang trên đường"),
            ("arrived", "Đã đến nơi"),
            ("handed_to_agent", "Đã giao đại lý"),
            ("delivered", "Đã giao khách"),
            ("closed", "Đã đóng"),
            ("exception", "Có sự cố (khiếu nại)"),
            ("returned", "Đã trả hàng"),
            ("compensated", "Đã bồi thường"),
        ],
        string="Mốc hành trình",
        required=True,
        default="draft",
        copy=False,
        index=True,
        tracking=True,
    )
    ngay_bay = fields.Datetime(string="Ngày bay / khởi hành")
    ngay_den = fields.Datetime(string="Ngày đến")
    cang_di = fields.Char(string="Sân bay / cảng đi")
    cang_den = fields.Char(string="Sân bay / cảng đến")
    dai_ly_giao_id = fields.Many2one(
        comodel_name="res.partner",
        string="Đại lý giao hàng",
        help="Đại lý nước ngoài (res.partner, type delivery) — không tạo model đại lý.",
    )

    # ------------------------------------------------------------------
    # POD + tra cứu cho khách
    # ------------------------------------------------------------------
    pod_date = fields.Datetime(string="Ngày đại lý giao khách (POD)")
    pod_attachment_ids = fields.One2many(
        comodel_name="ir.attachment",
        inverse_name="lab_hanhtrinh_id",
        string="Chứng từ giao hàng (POD)",
    )
    access_token = fields.Char(
        string="Token tra cứu của khách",
        copy=False,
        help="Dùng cho route công khai /lab/api/v1/track/<token>. Không index, "
             "không trả cho người khác (docs/02 §6.3).",
    )
    line_ids = fields.One2many(
        comodel_name="lab.hanh.trinh.line",
        inverse_name="hanhtrinh_id",
        string="Dòng thời gian hành trình",
    )

    # ==================================================================
    # Sequence: HT/26/0001 (docs/02 §5)
    # ==================================================================
    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if not vals.get("name") or vals.get("name") == _("Mới"):
                vals["name"] = (
                    self.env["ir.sequence"].next_by_code("lab.hanh.trinh") or _("Mới")
                )
            # Token tra cứu công khai (/lab/api/v1/track/<token>): sinh lười bằng
            # secrets (16 byte = 32 ký tự hex) — không đoán được, không trùng.
            # Cố ý KHÔNG dùng portal.mixin: stock.picking/lab.hanh.trinh không phải
            # portal document, và `_portal_ensure_token` chỉ chạy khi có portal page
            # (docs/02 §6.5). Sinh ở đây để record nào cũng có link tra cứu.
            if not vals.get("access_token"):
                vals["access_token"] = secrets.token_hex(16)
        return super().create(vals_list)
