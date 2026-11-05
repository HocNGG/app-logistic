# -*- coding: utf-8 -*-
from odoo import _, api, fields, models
from odoo.exceptions import UserError


class LabGanTaiXe(models.TransientModel):
    """WIZARD "Gán tài xế" cho form Yêu cầu lấy hàng — đường dành cho **CS trên web**.

    VÌ SAO CẦN (quyết định D1/D2, 2026-11-02):
      CS đã RỜI app (chỉ tài xế / trưởng nhóm / kho dùng app) ⇒ việc "gán tài xế" phải
      làm được trên **web**, nếu không CS mất chức năng. Trước đây gán tài xế chỉ có ở
      app qua `POST /lab/api/v1/cs/jobs/<id>/assign`.

    NGUYÊN TẮC: **không nhân bản logic**. Wizard chỉ ghi `driver_id` rồi gọi đúng
    `action_assign()` của model — nơi đã có kiểm trạng thái + chatter. Nhờ vậy web và app
    đi CÙNG một đường nghiệp vụ, không lệch điều kiện.

    Mở từ nút trên form (context `default_job_id` = bản ghi đang mở).
    """

    _name = "lab.yeu.cau.lay.hang.gan.tai.xe"
    _description = "Gán tài xế cho yêu cầu lấy hàng"

    job_id = fields.Many2one(
        comodel_name="lab.yeu.cau.lay.hang",
        string="Yêu cầu lấy hàng",
        required=True,
        ondelete="cascade",
    )
    driver_id = fields.Many2one(
        comodel_name="hr.employee",
        string="Tài xế",
        required=True,
        domain="[('id', 'in', driver_kha_dung)]",
        help="Chỉ hiện nhân viên có tài khoản thuộc nhóm Tài xế (lab_core.group_driver).",
    )
    driver_kha_dung = fields.Many2many(
        comodel_name="hr.employee",
        compute="_compute_driver_kha_dung",
        help="Field kỹ thuật để giới hạn dropdown — không phải danh sách người dùng tự khai.",
    )
    ghi_chu = fields.Char(
        string="Ghi chú phân công",
        help="Tuỳ chọn — ghi vào chatter để tài xế/CS khác đọc được lý do đổi tài xế.",
    )

    @api.depends("job_id")
    def _compute_driver_kha_dung(self):
        """Danh sách tài xế = nhân viên có user thuộc `lab_core.group_driver`.

        Dùng `res.groups.users` (dữ liệu nhóm) thay vì `has_group` — Luật 3: không kiểm
        quyền bằng `has_group` trong logic; đây chỉ là **phạm vi dropdown**.
        """
        nhom = self.env.ref("lab_core.group_driver", raise_if_not_found=False)
        nguoi_dung = nhom.sudo().users if nhom else self.env["res.users"]  # sudo-ok: đọc thành viên nhóm Tài xế để DỰNG dropdown/validate (ir.rule res.users chặn CS đọc thành viên nhóm)
        # sudo-ok: chỉ để DỰNG DANH SÁCH dropdown tài xế cho CS (ir.rule của hr.employee chặn CS đọc nhân viên khác); không đọc lương/hợp đồng, chỉ id+name
        tai_xe = self.env["hr.employee"].sudo().search(  # sudo-ok: chỉ id+name nhân viên để giới hạn dropdown tài xế (ir.rule hr.employee chặn CS đọc nhân viên khác)
            [("user_id", "in", nguoi_dung.ids)], order="name"
        )
        for wizard in self:
            wizard.driver_kha_dung = tai_xe

    def action_gan(self):
        """Gán tài xế rồi đóng wizard. Gọi lại `action_assign()` của model — không copy logic."""
        self.ensure_one()
        # Chặn chọn nhầm người không phải tài xế (domain ở view chỉ là gợi ý; đây là LUẬT
        # NGHIỆP VỤ, không phải kiểm quyền — quyền vẫn do ACL/ir.rule).
        nhom_tai_xe = self.env.ref("lab_core.group_driver", raise_if_not_found=False)
        if nhom_tai_xe and self.driver_id:
            la_tai_xe = self.driver_id.user_id in nhom_tai_xe.sudo().users  # sudo-ok: validate người được gán có thuộc nhóm Tài xế (đọc thành viên nhóm bị ir.rule chặn)
            if not la_tai_xe:
                raise UserError(
                    _(
                        "%s chưa có tài khoản thuộc nhóm Tài xế — không gán được.",
                        self.driver_id.name,
                    )
                )
        job = self.job_id
        # Ghi driver_id TRƯỚC: action_assign() kiểm `if not self.driver_id: raise`.
        # Với việc ĐÃ gán (đổi tài xế) thì chỉ write + chatter, KHÔNG gọi action_assign
        # (hàm đó chỉ hợp lệ từ state 'new').
        job.write({"driver_id": self.driver_id.id})
        if job.state == "new":
            job.action_assign()
        else:
            ten_tai_xe = self.driver_id.name or _("tài xế (chưa có tài khoản app)")
            job.message_post(body=_("Đổi tài xế phụ trách sang %s.", ten_tai_xe))
        if self.ghi_chu:
            job.message_post(body=_("Ghi chú phân công: %s", self.ghi_chu))
        return {"type": "ir.actions.act_window_close"}
