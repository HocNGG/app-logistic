# -*- coding: utf-8 -*-
"""Tiện ích trên ``res.users`` dùng chung: vai trò app + gỡ nhóm kỹ thuật.

Vì sao đặt ở ``lab_core`` (không phải ``lab_api``): hai thứ dưới đây đều nói về
**nhóm quyền**, mà nhóm quyền được khai ở ``lab_core/security/lab_core_groups.xml``
— cùng module thì XML id luôn resolve được, và ``lab_api``/``lab_laixe`` chỉ việc
gọi lại.
"""

import logging

from odoo import SUPERUSER_ID, Command, api, models

_logger = logging.getLogger(__name__)

#: Từ vựng vai trò **CỐ ĐỊNH** của app KMP (docs/02 §6.2, ADR-004).
#:
#: ⚠️ Đây là **DỮ LIỆU để app chọn màn hình**, KHÔNG phải kiểm quyền: không
#: endpoint nào được dùng giá trị này để cho phép/từ chối. Quyền bản ghi vẫn do
#: ``ir.rule`` + ACL quyết định (AGENTS.md Luật 3).
#:
#: Thứ tự trong tuple = thứ tự trả về (ổn định để app không phải sort).
#: Nhóm nào không có trong bảng này (vd ``lab_core.group_docs``) thì không sinh
#: vai trò riêng — người thuộc nhóm đó vẫn nhận vai trò của nhóm mà nó kế thừa
#: (``group_docs`` imply ``group_warehouse`` -> "kho").
#:
#: Thứ tự ưu tiên (chốt với FE-01, workflow/test-flow.md §0): **driver ·
#: driver_leader · cs · kho · ketoan · manager · sep**. App mở màn đầu tiên theo
#: vai trò ĐẦU TIÊN trong danh sách, nên "tài xế trước trưởng nhóm" là có chủ đích:
#: ai vừa lái vừa phân công thì mở màn tài xế trước.
#:
#: ⚠️ Vai trò ``admin`` KHÔNG nằm trong bảng này: nó không suy từ nhóm ``lab_core.*``
#: mà từ **quyền quản trị** — xem ``NHOM_QUAN_TRI`` / ``VAI_TRO_QUAN_TRI`` bên dưới.
VAI_TRO_THEO_NHOM = (
    ("driver", "lab_core.group_driver"),
    ("driver_leader", "lab_core.group_driver_leader"),
    ("cs", "lab_core.group_cs"),
    ("kho", "lab_core.group_warehouse"),
    ("ketoan", "lab_core.group_account"),
    ("manager", "lab_core.group_manager"),
    ("sep", "lab_core.group_director"),
)

#: Nhóm của Odoo đánh dấu QUẢN TRỊ VIÊN ("Settings"). Đây là nhóm duy nhất **không
#: phải của dự án** nhưng vẫn sinh vai trò app — vì nó quyết định "tài khoản này
#: được chuyển sang tài khoản khác" (``/lab/api/v1/admin/*``).
NHOM_QUAN_TRI = "base.group_system"

#: Vai trò app của quản trị viên. Đứng **ĐẦU** ``roles`` (app mở màn hình theo
#: ``roles[0]``): admin đăng nhập phải vào màn "Chuyển tài khoản", không phải màn
#: nghiệp vụ (admin không thuộc nhóm ``lab_*`` nào nên trước đây ``roles`` rỗng).
#: ⚠️ Đây là DỮ LIỆU cho app, KHÔNG phải kiểm quyền: quyền của admin vẫn đến từ
#: ``base.group_system`` + superuser; endpoint ``/admin/*`` tự chặn ở controller.
VAI_TRO_QUAN_TRI = "admin"

#: Hai nhóm "kỹ thuật" mà **tài khoản demo** không được có (DoD: login user đúng
#: phòng ban thấy đúng dữ liệu). ``base.group_no_one`` = "Technical Features",
#: ``base.group_system`` = "Settings".
NHOM_KY_THUAT = ("base.group_no_one", "base.group_system")


#: Tài khoản DEMO phải dùng tiếng Việt (Luật 6). Dùng LOGIN thay vì xmlid vì
#: `taixe04`/`taixe05` được tạo bởi `lab_core/tools/seed_demo_lon.py` (không có
#: xmlid), còn driver 01..03 có xmlid ở `lab_laixe` — lấy theo login phủ hết.
TAI_KHOAN_DEMO = (
    "admin",
    "taixe01",
    "taixe02",
    "taixe03",
    "taixe04",
    "taixe05",
    "nhomtruong01",
    "cs01",
    "kho01",
    "chungtu01",
    "ketoan01",
    "manager01",
    "sep",
)

class ResUsers(models.Model):
    _inherit = "res.users"

    # ==================================================================
    # Vai trò app (login trả về để app chọn màn hình)
    # ==================================================================
    def _lab_vai_tro(self):
        """Danh sách vai trò app của user, theo từ vựng cố định của ``VAI_TRO_THEO_NHOM``.

        Dùng cho ``POST /lab/api/v1/auth/login`` (``data.roles``) và
        ``POST /lab/api/v1/admin/switch``. **Không phải kiểm quyền** — xem
        docstring của ``VAI_TRO_THEO_NHOM``.

        Tên có ``_`` ở đầu là CỐ Ý: chỉ dùng nội bộ (controller gọi), không cho
        gọi qua XML-RPC/JSON-RPC (Odoo chặn method bắt đầu bằng ``_``) — tránh
        việc user portal, vốn có ACL *read* trên ``res.users``, hỏi vai trò của
        người khác.

        Vì sao đọc ``res.groups.users`` (qua sudo) thay vì ``user.groups_id``:

        * ``groups_id`` là m2m trên ``res.users``; tài khoản portal không có ACL
          trên ``res.groups`` nên đọc tên/xmlid nhóm có thể bị chặn giữa request
          đăng nhập của tài xế;
        * Odoo có sẵn ``has_group`` nhưng luật tĩnh A4 của
          ``infra/scripts/audit-native-first.ps1`` cấm chuỗi đó ở MỌI file .py
          trong ``addons/`` (để Luật 3 không bị lách) — nên ta kiểm bằng đúng
          bảng quan hệ mà ``has_group`` cũng đọc: ``res_groups_users_rel``.
        """
        self.ensure_one()
        Groups = self.env["res.groups"].sudo()  # sudo-ok: chỉ ĐỌC nhóm của chính user để suy vai trò app; portal không có ACL res.groups
        nhom_cua_user = Groups.search([("users", "in", self.ids)])
        xmlid_cua_nhom = set(nhom_cua_user.get_external_id().values())
        vai_tro = [
            vai_tro for vai_tro, xmlid in VAI_TRO_THEO_NHOM if xmlid in xmlid_cua_nhom
        ]
        # ``admin`` đứng ĐẦU: suy từ quyền QUẢN TRỊ (superuser uid 1 hoặc nhóm
        # "Settings"), không phải nhóm của dự án. Kiểm bằng chính tập xmlid vừa nạp
        # nên không phát sinh thêm query và không phụ thuộc ``has_group`` (luật A4).
        if self.id == SUPERUSER_ID or NHOM_QUAN_TRI in xmlid_cua_nhom:
            vai_tro.insert(0, VAI_TRO_QUAN_TRI)
        return vai_tro

    # ==================================================================
    # Gỡ nhóm kỹ thuật khỏi tài khoản DEMO
    # ==================================================================
    @api.model
    def _lab_bo_nhom_ky_thuat(self, user_ids):
        """Gỡ "Technical Features"/"Settings" khỏi các tài khoản **demo**.

        Tên có ``_`` ở đầu: chỉ dùng nội bộ (``<function>`` gọi lúc load module),
        không cho gọi qua RPC — đây là hàm GHI vào ``res.users``, không được để
        lộ thành API.

        Được gọi từ ``data/demo_users_technical.xml`` qua ``<function>``: hàm
        trong data XML chạy **mỗi lần** cài/nâng cấp module, còn
        ``<record noupdate="0">`` thì KHÔNG sửa được record đã tạo bởi file
        ``noupdate="1"`` (``odoo/tools/convert.py::_tag_record`` bỏ qua khi
        ``self.noupdate`` và ``_load_records`` kiểm lại cờ ``noupdate`` đã lưu
        trên ``ir.model.data``). Vì vậy ``<function>`` là đường duy nhất vá được
        DB **đã cài** bằng ``-u``.

        Vì sao phải gỡ (đo trên source 17.0, không đoán):
        ``odoo/addons/base/security/base_groups.xml`` cho ``base.group_user``
        **imply** ``base.group_no_one``, và ``GroupsImplied.write``
        (``odoo/addons/base/models/res_users.py``) materialize nhóm implied vào
        bảng ``res_groups_users_rel`` bằng một câu SQL đệ quy -> MỌI user nội bộ
        (cs01/kho01/chungtu01/ketoan01/manager01/sep) đều có "Technical
        Features" dù seed không hề khai. Tài khoản demo không phải kỹ thuật nên
        gỡ đi cho đúng DoD phân quyền.

        Gỡ **từ phía nhóm** (``group.write({'users': [...]})``) là bắt buộc:
        gỡ từ phía user (``user.write({'groups_id': [(3, ...)]})``) bị
        ``UsersImplied.write`` thêm lại ngay trong CÙNG lời gọi, vì
        ``base.group_user`` vẫn còn và vẫn imply ``base.group_no_one``.

        Ảnh hưởng ACL: **không có**. Đã grep
        ``odoo/addons/base/security/ir.model.access.csv`` -> 0 dòng ACL nào gán
        cho ``base.group_no_one``; nhóm này chỉ mở menu/field "Technical"
        (``groups="base.group_no_one"`` trong view/menu), không cấp quyền model.

        ⚠️ ASSUMPTION: Odoo sẽ thêm lại nhóm này nếu ai đó sửa ``groups_id`` của
        user trên UI (mọi write ``groups_id`` đều materialize lại nhóm implied).
        Hàm này chạy lại ở mỗi lần ``-u`` nên DB demo tự về đúng.
        """
        nhom = self.env["res.groups"]
        for xmlid in NHOM_KY_THUAT:
            # env.ref(..., raise_if_not_found=False) trả None khi thiếu -> phải
            # kiểm trước khi OR hợp, nếu không sẽ TypeError giữa lúc load module.
            nhom_can_go = self.env.ref(xmlid, raise_if_not_found=False)
            if nhom_can_go:
                nhom |= nhom_can_go
        nguoi_dung = self.browse([int(uid) for uid in (user_ids or []) if uid]).exists()
        if not nhom or not nguoi_dung:
            return True
        Nhom = nhom.sudo()  # sudo-ok: hàm chạy từ data XML (env superuser) và chỉ gỡ nhóm kỹ thuật khỏi tài khoản DEMO
        Nhom.write({"users": [Command.unlink(uid) for uid in nguoi_dung.ids]})
        _logger.info(
            "Đã gỡ nhóm kỹ thuật %s khỏi tài khoản demo: %s",
            ", ".join(nhom.mapped("name")),
            ", ".join(nguoi_dung.mapped("login")),
        )
        return True

    # ==================================================================
    # NGÔN NGỮ TIẾNG VIỆT cho tài khoản DEMO (Luật 6)
    # ==================================================================
    @api.model
    def _lab_go_nhom_kho_khoi_chung_tu(self):
        """Bỏ nhóm **Kho** khỏi các tài khoản chỉ thuộc nhóm **Chứng từ**.

        VÌ SAO CẦN (RV-01 review vòng 2, P0-1 — đo thật 2026-11-03):
          ``group_docs`` từng có ``implied_ids = group_warehouse`` ("chứng từ cũng
          phải xem được hàng"). Hệ quả: ``/auth/login`` của ``chungtu01`` trả
          ``roles = ["kho"]`` ⇒ **app mở nhầm màn Kho**, ``GET /kho/cho-xuat``
          trả 200, và tài khoản chứng từ có thể **validate nhập/xuất kho**.
        Đã bỏ ``implied_ids`` trong ``security/lab_core_groups.xml``, nhưng Odoo
        KHÔNG tự gỡ membership đã "vật chất hoá" trong ``res_groups_users_rel``
        ⇒ phải gỡ bằng ``<function>`` này (chạy mỗi lần cài/nâng cấp, idempotent).

        Chứng từ vẫn XEM được phiếu kho qua 3 dòng ACL chỉ-đọc
        (``access_stock_picking_docs_read`` …) — không cần kế thừa cả nhóm.
        """
        docs = self.env.ref("lab_core.group_docs", raise_if_not_found=False)
        kho = self.env.ref("lab_core.group_warehouse", raise_if_not_found=False)
        # Nhóm NATIVE mà "Kho" kế thừa: khi user được gán nhóm Kho, Odoo vật chất hoá
        # luôn nhóm này vào `res_groups_users_rel`. Gỡ mỗi `group_warehouse` là KHÔNG
        # đủ — đo 2026-11-03: chungtu01 vẫn `write` được stock.picking vì còn
        # `stock.group_stock_user`, tức vẫn "validate tồn kho" như một thủ kho.
        stock_user = self.env.ref("stock.group_stock_user", raise_if_not_found=False)
        if not docs:
            return True
        for user in docs.users:
            cmds = []
            if kho and kho in user.groups_id:
                cmds.append((3, kho.id))
            if stock_user and stock_user in user.groups_id:
                cmds.append((3, stock_user.id))
            if not cmds:
                continue
            user.write({"groups_id": cmds})
            _logger.info(
                "Đã gỡ nhóm Kho/Inventory-User khỏi %s (chỉ thuộc Chứng từ) — P0-1 RV vòng 2.",
                user.login,
            )
        return True

    @api.model
    def _lab_dat_ngon_ngu_vi(self, user_ids):
        """Đặt ``lang = vi_VN`` cho các tài khoản demo (idempotent).

        VÌ SAO CẦN (đo 2026-11-03): user demo được seed với ``lang = en_US``
        (mặc định của Odoo) ⇒ list web hiện ngày kiểu **mm/dd/yyyy HH:mm** và một
        số nhãn hệ thống (Year, Filters…) bằng tiếng Anh — sai Luật 6 "tiếng Việt
        là ngôn ngữ sản phẩm". Đổi ngôn ngữ của RIÊNG tài khoản demo, KHÔNG đổi
        ngôn ngữ DB (tránh ảnh hưởng dữ liệu dịch của bản ghi đã tạo).

        ⚠️ ``res.users`` KHÔNG có cột ``lang``: ngôn ngữ nằm trên
        ``res.users.partner_id.lang`` (đó cũng là lý do endpoint kiểm bằng
        ``select login, lang from res_users`` sẽ báo lỗi cột — phải join
        ``res_partner``).

        Kích hoạt ``vi_VN`` trước nếu chưa active: Odoo tạo sẵn mọi ``res.lang``
        nhưng phần lớn để ``active = NULL``; user đặt lang chưa active thì giao
        diện vẫn rơi về en_US (im lặng, rất khó phát hiện).

        Được gọi từ ``data/demo_users_lang.xml`` bằng ``<function>`` (chạy mỗi lần
        cài/nâng cấp) — cùng lý do với ``_lab_bo_nhom_ky_thuat``: ``<record
        noupdate="0">`` không sửa được record đã tạo bởi file ``noupdate="1"``.
        """
        Lang = self.env["res.lang"].with_context(active_test=False)
        tieng_viet = Lang.search([("code", "=", "vi_VN")], limit=1)
        if not tieng_viet:
            _logger.warning(
                "Không tìm thấy res.lang 'vi_VN' — bỏ qua việc đặt ngôn ngữ demo."
            )
            return True
        if not tieng_viet.active:
            tieng_viet.sudo().write({"active": True})  # sudo-ok: bật một ngôn ngữ hệ thống, chạy từ data XML
            # Xoá cache để mọi request sau thấy vi_VN đã active (nếu không, user
            # đặt lang vẫn có thể bị fallback về en_US trong cùng tiến trình).
            self.env.registry.clear_cache()
        chi_dinh = self.browse([int(uid) for uid in (user_ids or []) if uid]).exists()
        theo_login = self.with_context(active_test=False).search(
            [("login", "in", list(TAI_KHOAN_DEMO))]
        )
        nguoi_dung = chi_dinh | theo_login
        if not nguoi_dung:
            return True
        can_doi = nguoi_dung.filtered(lambda u: (u.partner_id.lang or "") != "vi_VN")
        if can_doi:
            can_doi.sudo().write({"lang": "vi_VN"})  # sudo-ok: hàm tiện ích chạy từ data XML; 'lang' là related về res_partner
            _logger.info(
                "Đã đặt ngôn ngữ vi_VN cho tài khoản demo: %s",
                ", ".join(can_doi.mapped("login")),
            )
        return True
