# -*- coding: utf-8 -*-
from odoo import _, api, fields, models

#: Ba mốc quét được phép. `in_lai` KHÔNG phải mốc vận chuyển — nó ghi việc in lại tem.
LOAI_QUET = [
    ("nhan", "Quét tem nhận"),
    ("xuat", "Quét tem xuất"),
    ("in_lai", "In lại tem"),
]

#: Kết quả một lần quét. `trung` = kiện đã được quét ở đúng mốc đó trước đây.
KET_QUA_QUET = [
    ("ok", "Hợp lệ"),
    ("sai_moc", "Sai mốc"),
    ("trung", "Đã quét trước đó"),
    ("khong_tim_thay", "Không tìm thấy kiện"),
]

THIET_BI_QUET = [
    ("web", "Web (máy quét USB)"),
    ("app", "App kho (camera)"),
]


class LabScanEvent(models.Model):
    """NHẬT KÝ QUÉT TEM — mỗi lần quét một dòng (append-only, không sửa, không xoá).

    VÌ SAO CẦN MODEL NÀY (đã được duyệt, ADR-010):
      Bảng tổng kết cần trả lời "ai quét, lúc nào, bằng thiết bị gì, kết quả ra sao" và
      phải CHỐNG TRÙNG khi mạng chập chờn (app gửi lại cùng `client_uuid`). Ghi vào
      chatter của phiếu thì không truy vấn/đếm được; còn chứng từ KHO vẫn là
      `stock.picking` do Odoo quản — model này **không thay thế** chứng từ nào
      (AGENTS.md Luật 4 vẫn nguyên).

    APPEND-ONLY: ACL không cấp `unlink` cho bất kỳ nhóm nghiệp vụ nào.
    """

    _name = "lab.scan.event"
    _description = "Nhật ký quét tem kiện"
    _order = "id desc"
    _rec_name = "client_uuid"

    kien_id = fields.Many2one(
        comodel_name="stock.quant.package",
        string="Kiện",
        required=True,
        ondelete="cascade",
        index=True,
    )
    picking_id = fields.Many2one(
        comodel_name="stock.picking",
        string="Phiếu kho",
        ondelete="set null",
        index=True,
        help="Phiếu nhập (khi quét tem nhận) hoặc phiếu xuất (khi quét tem xuất).",
    )
    loai = fields.Selection(
        selection=LOAI_QUET,
        string="Mốc quét",
        required=True,
        default="nhan",
        index=True,
    )
    ket_qua = fields.Selection(
        selection=KET_QUA_QUET,
        string="Kết quả",
        required=True,
        default="ok",
        index=True,
    )
    user_id = fields.Many2one(
        comodel_name="res.users",
        string="Người quét",
        default=lambda self: self.env.user,
        readonly=True,
        index=True,
    )
    thiet_bi = fields.Selection(
        selection=THIET_BI_QUET,
        string="Thiết bị",
        default="web",
        required=True,
    )
    thoi_diem = fields.Datetime(
        string="Thời điểm quét",
        default=fields.Datetime.now,
        readonly=True,
        index=True,
    )
    client_uuid = fields.Char(
        string="Mã chống trùng",
        copy=False,
        index=True,
        help="UUID do app/web sinh cho MỖI lần quét. Gửi lại cùng UUID (mạng chập, bấm 2 "
             "lần) thì hệ thống trả 'đã quét' và KHÔNG tạo dòng mới.",
    )
    ghi_chu = fields.Text(string="Ghi chú")

    _sql_constraints = [
        (
            "client_uuid_uniq",
            "unique(client_uuid)",
            "Mã chống trùng của lần quét phải là duy nhất — lần quét này đã được ghi.",
        ),
    ]

    @api.depends("kien_id", "loai", "thoi_diem")
    def _compute_display_name(self):
        """Tên đọc được trong log/audit: `KIEN/26/000001 · Quét tem nhận · 03/11 10:22`."""
        nhan_loai = dict(LOAI_QUET)
        for rec in self:
            kien = rec.kien_id.name or "?"
            loai = nhan_loai.get(rec.loai, rec.loai or "")
            luc = rec.thoi_diem.strftime("%d/%m %H:%M") if rec.thoi_diem else ""
            rec.display_name = "%s · %s · %s" % (kien, loai, luc)
