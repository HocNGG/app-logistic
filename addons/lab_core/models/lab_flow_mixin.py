# -*- coding: utf-8 -*-
from odoo import fields, models


class LabFlowMixin(models.AbstractModel):
    """6 field ngoại lệ dùng chung cho mọi chứng từ có nhánh ngoại lệ.

    Nguồn: docs/02-kien-truc.md §2.1 (danh sách field) + docs/07-exception-flows.md
    (6 nhánh ①→⑥ và "điểm quay về" của từng nhánh).

    Vì sao là AbstractModel: mixin không có bảng riêng, chỉ được "trộn" vào
    lab.chuyen / lab.yeu.cau.lay.hang / lab.hanh.trinh bằng _inherit. Nhờ vậy
    lab_core không cần biết 3 module nghiệp vụ kia (không phụ thuộc vòng).

    Không có method nào ở đây — P0 chỉ khai field. Logic đánh dấu ngoại lệ và
    kiểm tra "điểm quay về" thuộc về từng module nghiệp vụ (P2/P3/P4) để mỗi
    mixin không biến thành "god object".
    """

    _name = "lab.flow.mixin"
    _description = "Mixin ngoại lệ dùng chung (6 nhánh ngoại lệ)"

    # ---------------------------------------------------------------------
    # 6 field ngoại lệ — tên field chép đúng docs/02 §2.1, không tự đặt lại.
    # ---------------------------------------------------------------------
    lab_is_exception = fields.Boolean(
        string="Đang ở nhánh ngoại lệ",
        default=False,
        copy=False,
        help="Bật khi chứng từ rời luồng chuẩn để đi một nhánh ngoại lệ "
             "(docs/07). Phải tắt lại khi quay về luồng chính.",
    )
    lab_exception_type = fields.Selection(
        selection=[
            # mã ngoại lệ -> nhãn tiếng Việt (ánh xạ ①→⑥ của docs/07)
            ("unknown_customer", "Hàng chưa rõ khách (nhánh ①)"),
            ("overload", "Rớt chuyến / giảm tải (nhánh ②)"),
            ("missing_info", "Đơn thiếu thông tin (nhánh ③)"),
            ("short_package", "Xuất thiếu kiện (nhánh ④)"),
            ("warehouse_switch", "Chuyển kho bay ↔ tàu (nhánh ⑤)"),
            ("complaint", "Khiếu nại / trả hàng (nhánh ⑥)"),
        ],
        string="Loại ngoại lệ",
        copy=False,
    )
    lab_exception_note = fields.Text(
        string="Ghi chú ngoại lệ",
        copy=False,
        help="Diễn giải ngắn gọn lý do vào nhánh ngoại lệ — dùng cho chatter/report.",
    )
    lab_return_step = fields.Char(
        string="Bước quay về",
        copy=False,
        help="Điểm quay về luồng chính (bắt buộc ghi khi đóng ngoại lệ). "
             "Ví dụ: 'gán khách → ③/④', 'duyệt chuyến → ⑥', "
             "'backorder done → ⑦'.",
    )
    lab_exception_user_id = fields.Many2one(
        comodel_name="res.users",
        string="Người đánh dấu ngoại lệ",
        default=lambda self: self.env.user,
        readonly=True,
        copy=False,
    )
    lab_exception_date = fields.Datetime(
        string="Thời điểm đánh dấu ngoại lệ",
        readonly=True,
        copy=False,
    )

    # Ghi chú: AbstractModel không có bảng và không search được nên cố ý KHÔNG
    # khai _order ở đây (khai _order cho mixin là metadata chết). Model thật
    # (lab.chuyen, lab.yeu.cau.lay.hang, lab.hanh.trinh) đều có _order riêng.
