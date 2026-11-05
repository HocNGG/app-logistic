# -*- coding: utf-8 -*-
# lab_laixe — bước ② (docs/00 §3, docs/02 §2.3).
# Tái sử dụng: tài xế = hr.employee, xe = fleet.vehicle (KHÔNG tạo model tai_xe).
{
    "name": "Lab Logistics — Điều tài xế đi lấy hàng",
    "version": "17.0.1.10.0",
    "category": "Logistics/Operations",
    "summary": "Yêu cầu lấy hàng tận nơi: gán tài xế, khung giờ, ảnh, chữ ký, "
               "xác nhận và sinh phiếu nhập kho",
    "author": "AppLogictics (BE-01)",
    "license": "LGPL-3",
    "depends": [
        "lab_core",  # groups + mixin + native deps (sale_stock, stock_delivery, account, portal, mail)
        "hr",        # tài xế là hr.employee — không tạo model tai_xe
        "fleet",     # xe là fleet.vehicle — không tạo model xe
        # _inherit sale.order -> phải khai depends TRỰC TIẾP (không dựa bắc cầu)
        "sale",
    ],
    "data": [
        "security/lab_laixe_rules.xml",   # ir.rule phải load sau khi ir.model có model
        "security/ir.model.access.csv",
        "data/ir_sequence_data.xml",
        # seed data demo: 3 tài xế (user + hr.employee) và 5 yêu cầu lấy hàng — Luật 8
        "data/demo_data.xml",
        # VÁ EMAIL tài xế demo (noupdate="0"): demo_data.xml là noupdate="1" nên
        # upgrade trên DB cũ không áp email -> message_post raise -> mất giao dịch
        "data/demo_users_email.xml",
        # GỠ NHÓM KỸ THUẬT khỏi 3 tài xế demo (noupdate="0" + <function>) — cùng
        # lý do với lab_core/data/demo_users_technical.xml
        "data/demo_users_technical.xml",
        # view + action (FE-01 viết), rồi menu con CUỐI CÙNG
        # TEM NHẬN KIỆN (100x70) — in ngay sau khi kho bấm "Nhận hàng & in tem"
        "report/tem_nhan.xml",
        # WIZARD gan tai xe (D1/D2: CS gan tren web) — action phai co truoc nut
        "views/gan_tai_xe_views.xml",
        "views/lab_yeu_cau_lay_hang_views.xml",
        "views/menuitems.xml",
    ],
    "installable": True,
    "application": False,
    "auto_install": False,
}
