# -*- coding: utf-8 -*-
# lab_ui — giao diện "Dark Premium + Lime" cho Odoo 17 CE: theme + Bảng điều khiển OWL.
# Không model, không controller, không security, không business logic
# (ADR-009 docs/adr/ADR-009-dark-premium-lime.md §6 = bảng token DUY NHẤT;
#  bố cục màn hình làm việc: agents/_shared/skills/ui-dashboard-app-screens.md).
{
    "name": "Lab — Dark Premium UI",
    "version": "17.0.3.2.0",
    "category": "Hidden",  # theme kỹ thuật: không hiện trong danh sách Apps
    "summary": "Giao diện dark navy + neon lime (ADR-009 §6): biến SCSS Odoo, "
               "bảng/form/nav, trang đăng nhập hai panel tối và Bảng điều khiển "
               "(client action OWL 2)",
    "author": "AppLogictics (FE-01)",
    "license": "LGPL-3",
    # ⚠️ "lab_core" là phụ thuộc THẬT, không phải trang trí:
    #    views/menu.xml đặt menu "Bảng điều khiển" dưới menu gốc `lab_core.menu_lab_root` và gán
    #    nhóm `lab_core.group_manager` + `lab_core.group_director` (xmlid thật, không tự chế nhóm).
    #    Thiếu phụ thuộc này thì `-i lab_ui` trên DB chưa có lab_core sẽ chết với
    #    "External ID not found" — đúng loại lỗi im lặng chỉ nổ ở lần cài đầu.
    # "auth_signup" là phụ thuộc THẬT từ 2026-11-01: hai link "Chưa có tài khoản?" /
    # "Quên mật khẩu" trên trang đăng nhập do auth_signup chèn vào `.oe_login_buttons`.
    # Không khai phụ thuộc thì thứ tự áp view không xác định và views/login.xml có thể chết
    # với "Element ... cannot be located" (đã xảy ra thật). Khai báo ⇒ view của ta LUÔN
    # được áp SAU view của auth_signup.
    "depends": ["web", "lab_core", "auth_signup"],
    "data": [
        "views/login.xml",   # inherit web.login_layout (+ nhãn phụ ở web.login)
        "data/company.xml",  # logo công ty cho trang login (cơ chế dữ liệu chuẩn, L0)
        "views/menu.xml",    # action client "Bảng điều khiển" + menuitem (ĐẶT SAU CÙNG)
    ],
    "assets": {
        # TẦNG BIẾN: bundle thật của web 17.0 (web/__manifest__.py:327).
        # Trong bundle này, asset của module phụ thuộc nằm SAU asset của web ⇒ file dưới
        # nạp sau primary_variables.scss (nơi có $o-community-color mặc định = tím Odoo) ⇒ biến ta thắng.
        "web._assets_primary_variables": [
            "lab_ui/static/src/scss/variables.scss",
        ],
        # TẦNG CSS BACKEND + BẢNG ĐIỀU KHIỂN: body của backend có class `o_web_client` ⇒ mọi
        # selector trong 2 file dưới nằm dưới `.o_web_client` (trừ khối login dưới `body.o_login`).
        #
        # Thứ tự khai báo CÓ Ý NGHĨA: backend.scss (token trên `:root` + theme chung) đứng TRƯỚC
        # dashboard.scss (chỉ dùng token), để nếu sau này ai thêm khai báo trùng thì tầng theme
        # vẫn là nơi định nghĩa token. JS/XML của dashboard đặt sau SCSS cũng không ảnh hưởng
        # (Odoo gom theo loại file), nhưng giữ nhóm "dashboard" liền nhau cho dễ đọc.
        "web.assets_backend": [
            # ĐO THẬT 2026-10-28: nếu CHỈ dựa vào include lồng nhau
            # (`web.assets_backend` → `_assets_helpers` → `_assets_primary_variables`),
            # navbar backend vẫn ra TÍM NHẠT (ảnh `odoo-dashboard-after.png` bản đầu:
            # dải menu phía trên tím, dù card/chip đã đúng). Vì vậy khai tường minh
            # variables.scss ĐỨNG TRƯỚC backend.scss trong chính bundle này.
            "lab_ui/static/src/scss/variables.scss",
            "lab_ui/static/src/scss/backend.scss",
                "lab_ui/static/src/scss/theme-v5.scss",
            "lab_ui/static/src/dashboard/dashboard.scss",
            "lab_ui/static/src/js/nav_active.js",
                "lab_ui/static/src/js/kpi_strip.js",
                "lab_ui/static/src/dashboard/dashboard.js",
            "lab_ui/static/src/dashboard/dashboard.xml",
        ],
        # ⚠️ VÌ SAO CÓ 2 FILE Ở ĐÂY (đã đo bằng ảnh chụp + đọc source 17.0):
        # /web/login KHÔNG nạp web.assets_backend; nó đi web.login_layout →
        # web.frontend_layout → t-call-assets "web.assets_frontend".
        # - `web.assets_frontend` VỐN ĐÃ include `web._assets_primary_variables` (qua
        #   `web._assets_helpers`), nên biến SCSS của ta vào frontend từ đầu — khai tường
        #   minh `variables.scss` ở đây chỉ để bundle không phụ thuộc chuỗi include lồng nhau
        #   (nếu Odoo đổi cấu trúc include, theme không chết im lặng).
        # - RIÊNG nút "Log in": màu mặc định là `$o-enterprise-color` của Odoo, KHÔNG đi qua
        #   `$primary`/`$o-brand-primary`/`$o-btns-bs-override` ⇒ không biến SCSS nào chạm tới.
        #   Vì vậy phải có khối CSS tường minh `body.o_login .btn-primary` trong backend.scss
        #   (đo pixel trên ảnh chụp: trước khi có rule, nút vẫn mang màu enterprise mặc định;
        #   sau khi có rule = 0 pixel màu đó). Từ 17.0.2.0.0 khai thêm
        #   `$o-enterprise-color` ở variables.scss — hai lớp bảo hiểm, rẻ hơn debug lại.
        # ⚠️ Dashboard KHÔNG có mặt ở frontend: nó chỉ chạy trong backend (OWL + orm service).
        "web.assets_frontend": [
            "lab_ui/static/src/scss/variables.scss",
            "lab_ui/static/src/scss/backend.scss",
                "lab_ui/static/src/scss/theme-v5.scss",
        ],
    },
    "installable": True,
    "application": False,
    "auto_install": False,
}
