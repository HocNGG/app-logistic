# -*- coding: utf-8 -*-
from odoo import fields, models


class ResUsers(models.Model):
    """Token 8 giờ của app tài xế — L2 (thêm field vào model native).

    Vì sao KHÔNG tạo model ``lab.api.token``: model mới phải nằm trong whitelist
    ``infra/scripts/audit-native-first.ps1`` (Luật 1) và một bảng token riêng chỉ
    để giữ hash + hạn là viết lại ``res.users``. Ở đây mỗi user có **một** token
    đang hoạt động — login lại là rotate (ghi đè), đúng yêu cầu "rotate khi login
    lại" của agents/review/skills/03-security-audit.md §7.

    Lưu **hash** (sha256), không lưu token gốc, không log token (docs/02 §6.3).
    Field có ``groups`` để người dùng nội bộ không đọc được hash qua UI/RPC;
    controller đọc/ghi bằng ``sudo`` có marker ``# sudo-ok:``.
    """

    _inherit = "res.users"

    lab_api_token_hash = fields.Char(
        string="Hash token app tài xế",
        copy=False,
        index=True,
        groups="base.group_system",
        help="SHA-256 của token 8 giờ cấp cho app KMP. Không lưu token gốc.",
    )
    lab_api_token_expiry = fields.Datetime(
        string="Hạn token app tài xế",
        copy=False,
        groups="base.group_system",
        help="Quá thời điểm này token bị từ chối, app phải đăng nhập lại.",
    )
