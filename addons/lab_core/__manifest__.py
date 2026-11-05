# -*- coding: utf-8 -*-
# lab_core — module "keo" (glue) của dự án AppLogictics.
# Vì sao có module này: 3 module nghiệp vụ cùng cần nhóm quyền + mixin ngoại lệ +
# field mở rộng trên model native; nếu không có chỗ khai báo chung thì chúng phải
# depends lẫn nhau -> phụ thuộc vòng.
# Xem docs/adr/ADR-001-lab-core.md và docs/02-kien-truc.md §1.
{
    "name": "Lab Logistics — Lõi nền (nhóm quyền + mixin ngoại lệ)",
    "version": "17.0.2.3.0",
    "category": "Logistics/Operations",
    "summary": "9 nhóm quyền 6 phòng ban (thêm Trưởng nhóm tài xế), mixin 6 field "
               "ngoại lệ và field mở rộng trên sale.order / stock.picking dùng "
               "chung cho 3 module nghiệp vụ",
    "author": "AppLogictics (BE-01)",
    "license": "LGPL-3",
    # Phụ thuộc native — lab_core KHÔNG depends module lab_* nào
    # (docs/02 §1: quy tắc phụ thuộc một chiều, tránh vòng).
    "depends": [
        "sale_stock",      # sale.order <-> stock.picking (bước ① ③ ⑦)
        "stock_delivery",  # delivery.carrier + stock.move.weight (bước ⑤ ⑫)
        "account",         # Debit = account.move (bước ⑬ ⑭ ⑮ ⑯)
        "portal",          # khách tra cứu, tài xế portal-level (bước ⑨ ⑩ ⑪)
        "mail",            # chatter + mail.activity (pain P1)
    ],
    "data": [
        # 1) nhóm quyền (res.groups) phải load TRƯỚC csv
        "security/lab_core_groups.xml",
        # 2) ACL sau nhóm. Hiện file chỉ có header — CỐ Ý và ĐÚNG: lab_core chỉ
        #    khai field trên model native (không có model lưu DB riêng), viết dòng
        #    ACL cho lab.flow.mixin sẽ làm cài đặt lỗi "External ID not found".
        "security/ir.model.access.csv",
        # 2b) ĐỌC `sale.order` cho CS/quản lý: bước ⑦ (gom đơn vào chuyến) cần CHỌN
        #     "Đơn hàng của shop"; đo 2026-11-02 cs01/manager01 đọc được 0 đơn ⇒ ô chọn
        #     rỗng. ACL ở csv + 2 ir.rule ở đây (rule riêng vì rule "Personal Orders"
        #     của Odoo chỉ cho thấy đơn của chính mình).
        "security/sale_order_cs_read_rule.xml",
        # 3a) SẢN PHẨM ĐẠI DIỆN "KIỆN GOM LẺ" (KIEN-GOM) — dòng hàng cho WH/IN & WH/OUT.
        #     Vì sao: phiếu nhập trước đây tạo ra mà KHÔNG có stock.move ⇒ tồn kho không
        #     tăng (đo 2026-11-02: 3 phiếu WH/IN, 0 dòng hàng). Không phải demo data:
        #     đây là dữ liệu cấu hình BẮT BUỘC để nghiệp vụ kho chạy đúng.
        "data/product_kien.xml",
        # 3) SEED DATA DEMO (Luật 8): 3 shop, 1 đại lý JP, sản phẩm cước, 5 đơn
        "data/demo_data.xml",
        # 3b) VÁ EMAIL user demo (noupdate="0"): demo_data.xml là noupdate="1" nên
        #     upgrade trên DB cũ không áp email -> message_post raise -> mất giao dịch
        "data/demo_users_email.xml",
        # 3c) NGÔN NGỮ vi_VN cho tài khoản demo (Luật 6): list web hiện dd/MM/yyyy HH:mm
        "data/demo_users_lang.xml",
        # P0-1 RV vòng 2: gỡ nhóm Kho khỏi tài khoản chỉ thuộc nhóm Chứng từ
        "data/demo_users_docs_group.xml",
        # 3e) GỠ NHÓM KỸ THUẬT — PHẢI LÀ BƯỚC CUỐI của nhóm file vá user: mọi write trên
        #     res.users (đổi lang, đổi nhóm…) đều materialize lại nhóm implied
        #     base.group_no_one ⇒ nếu chạy trước thì "Technical Features" quay lại và test
        #     test_tai_khoan_demo_khong_co_nhom_ky_thuat đỏ (đã xảy ra 2026-11-03).
        "data/demo_users_technical.xml",
        # 3d) KHÁCH HÀNG (Shop) — bước ① cần CHỖ TẠO KHÁCH MỚI: list/form/search +
        #     action + menu "Khách hàng (Shop)". ACL res.partner cho group_cs nằm ở
        #     security/ir.model.access.csv (đo thật 2026-10-29: cs01 không tạo được khách).
        "views/res_partner_views.xml",
        # 3e) ĐƠN BÁN — cột "Tạo lúc" + sắp CŨ NHẤT LÊN ĐẦU (quyết định N1/N2, 2026-11-02).
        #     Chỉ inherit view chuẩn `sale.view_order_tree`; KHÔNG đổi `_order` của model.
        "views/sale_order_list_views.xml",
        # 4) menu gốc — khai CUỐI (menu trỏ tới action nên phải sau view/action)
        "views/menuitems.xml",
    ],
    "installable": True,
    "application": False,   # keo kỹ thuật, không phải app có menu gốc riêng
    "auto_install": False,
}
