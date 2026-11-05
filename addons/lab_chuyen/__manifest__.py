# -*- coding: utf-8 -*-
# lab_chuyen — bước ⑤⑥⑦⑬⑭ (docs/00 §3, docs/02 §2.2).
# Phụ thuộc native (sale_stock, stock_delivery, account, portal, mail) được kéo
# vào qua lab_core; ở đây chỉ khai thêm thứ dùng TRỰC TIẾP trong module này.
{
    "name": "Lab Logistics — Chuyến gom hàng lẻ",
    "version": "17.0.1.6.0",
    "category": "Logistics/Operations",
    "summary": "Chuyến bay/tàu gom nhiều đơn của nhiều khách: tải trọng, cut-off, "
               "duyệt đơn vào chuyến (⑥), duyệt khoá Debit (⑭)",
    "author": "AppLogictics (BE-01)",
    "license": "LGPL-3",
    "depends": [
        "lab_core",              # groups + mixin + native deps + field sale.order/stock.picking
        "base_tier_validation",  # OCA/server-ux nhánh 17.0 — duyệt 2 cấp cho ⑥ và ⑭
        # _inherit account.move + stock.picking -> phải khai depends TRỰC TIẾP,
        # không dựa vào bắc cầu qua lab_core.
        "account",
        "stock",
    ],
    "data": [
        "security/ir.model.access.csv",
        "data/ir_sequence_data.xml",
        # định nghĩa người duyệt (tier.definition) cho ⑥ và ⑭
        "data/tier_definition_data.xml",
        # backfill access_token cho chuyến cũ (khoá tra cứu công khai ngẫu nhiên)
        "data/backfill_token.xml",
        # seed data demo: 2 chuyến (1 chuyến 120% tải) — Luật 8
        "data/demo_data.xml",
        # view + action (FE-01 viết), rồi menu con CUỐI CÙNG
        # TEM XUẤT KIỆN (100x70) — mỗi kiện một tem, in khi kho xuất chuyến
        "report/tem_xuat.xml",
        "views/lab_chuyen_views.xml",
        "views/stock_picking_views.xml",
        "views/sale_order_views.xml",
        "views/menuitems.xml",
    ],
    "installable": True,
    "application": False,
    "auto_install": False,
}
