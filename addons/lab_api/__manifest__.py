# -*- coding: utf-8 -*-
# lab_api — cửa vào cho app KMP (ADR-004, docs/02 §6).
# Endpoint: login (token 8h + roles) · driver (jobs/accept/capture/fail) ·
#           cs (jobs/assign — CS hoặc Trưởng nhóm tài xế) · kho (cho-nhan/nhan) ·
#           sep (kpi) · admin (accounts/switch — "chuyển tài khoản" cho demo:
#           admin HOẶC phiên do admin chuyển sang, nên đổi vai trò liên tiếp được)
#           · track công khai.
# Quyền: auth='user' + ir.rule/ACL cho dữ liệu, và chặn theo NHÓM ở tầng controller
# (403) cho từng namespace — xem helpers.kiem_nhom / helpers.kiem_mot_trong_nhom /
# helpers.kiem_admin. Track: auth='public' + access_token.
{
    "name": "Lab Logistics — API cho app KMP",
    "version": "17.0.1.9.0",
    "category": "Logistics/Operations",
    "summary": "Controller /lab/api/v1/* cho app tài xế, phân công (CS/Trưởng nhóm "
               "tài xế), Kho nhận hàng, chuyển vai trò liên tục cho admin (dropdown "
               "trên app) và tra cứu công khai: đăng nhập token 8 giờ + roles + "
               "canSwitch, việc theo vai trò, gán tài xế, nhận hàng (sinh phiếu "
               "kho), timeline",
    "author": "AppLogictics (BE-01)",
    "license": "LGPL-3",
    "depends": [
        "lab_laixe",       # /driver/* — yêu cầu lấy hàng
        "lab_hanhtrinh",   # /track/*  — timeline cho khách
        # lab_hanhtrinh depends lab_chuyen depends lab_core -> cả chuỗi có sẵn
    ],
    # Không có model mới (chỉ _inherit res.users) nên không cần ACL/ir.rule riêng.
    # View duy nhất: trang tra cứu công khai của kiện (mở bằng QR in trên tem).
    "data": [
        "views/track_templates.xml",
    ],
    "installable": True,
    "application": False,
    "auto_install": False,
}
