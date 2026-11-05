# -*- coding: utf-8 -*-
from odoo import fields, models


class AccountMove(models.Model):
    """Nối Debit (account.move) với chuyến — bước ⑬, và duyệt khoá Debit — bước ⑭.

    Debit KHÔNG phải model riêng: đó là account.move trên journal Debit
    (docs/00 §7). Field `lab_chuyen_id` chỉ để lọc/nhóm Debit theo chuyến.

    Tier validation ⑭: phải có người duyệt TRƯỚC khi post phiếu ghi nợ. Cặp state
    `draft -> posted` là cặp bị chặn; định nghĩa duyệt nằm ở
    data/tier_definition_data.xml và CHỈ áp cho journal có code 'DEBIT' —
    nếu để domain rỗng thì mọi hoá đơn bán hàng cũng phải duyệt, phá luồng ⑮
    native (skills/08 §5). Chưa có journal DEBIT thì định nghĩa không kích hoạt.
    """

    # BẮT BUỘC khi _inherit là LIST có model cụ thể + mixin: phải khai _name,
    # nếu không Odoo coi đây là model MỚI (cảnh báo "has no _description") và
    # field lab_chuyen_id KHÔNG vào registry -> One2many debit_ids ném
    # KeyError: 'lab_chuyen_id'. Đã gặp thật 2026-10-28 (docs/review/upgrade-waveB.log).
    _name = "account.move"
    _inherit = ["account.move", "tier.validation"]

    _tier_validation_manual_config = False
    _state_field = "state"       # account.move 17.0: draft / posted / cancel
    _state_from = ["draft"]
    _state_to = ["posted"]
    _cancel_state = "cancel"

    lab_chuyen_id = fields.Many2one(
        comodel_name="lab.chuyen",
        string="Chuyến",
        copy=False,
        index=True,
        help="Chuyến mà phiếu ghi nợ (Debit) này tính cước.",
    )
