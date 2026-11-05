# -*- coding: utf-8 -*-
# lab_hanhtrinh — bước ⑨⑩⑪ (docs/00 §3, docs/02 §2.4).
# Depends lab_chuyen vì hành trình THUỘC VỀ chuyến (docs/02 §1: hướng phụ thuộc).
{
    "name": "Lab Logistics — Hành trình quốc tế",
    "version": "17.0.1.2.0",
    "category": "Logistics/Operations",
    "summary": "Mốc hành trình sau khi hàng rời kho: hàng đi, đến nơi, đại lý giao "
               "khách, AWB/HBL, POD và link tra cứu công khai cho khách",
    "author": "AppLogictics (BE-01)",
    "license": "LGPL-3",
    "depends": [
        "lab_core",    # groups + mixin + native deps (portal, mail, stock_delivery, account...)
        "lab_chuyen",  # hành trình thuộc về một chuyến (m2o lab.chuyen)
    ],
    "data": [
        "security/ir.model.access.csv",
        "data/ir_sequence_data.xml",
        # seed data demo: 1 hành trình đang bay + token tra cứu cho cảnh quay — Luật 8
        "data/demo_data.xml",
        # view + action (FE-01 viết), rồi menu con CUỐI CÙNG
        "views/lab_hanh_trinh_views.xml",
        "views/menuitems.xml",
    ],
    "installable": True,
    "application": False,
    "auto_install": False,
}
