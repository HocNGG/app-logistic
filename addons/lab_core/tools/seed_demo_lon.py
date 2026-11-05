# -*- coding: utf-8 -*-
"""Bộ DỮ LIỆU MẪU LỚN — 6 tháng lịch sử, phủ đủ 7 trạng thái việc lấy hàng, 8 trạng
thái chuyến, 11 mốc hành trình, tiền (hoá đơn + Debit) và 6 nhánh ngoại lệ của docs/07.

VÌ SAO có file này (không phải data XML): 300 việc + 24 chuyến + 120 đơn + 140 bút toán
viết tay bằng XML là không bảo trì được, và cần BACKDATE ``create_date`` (XML không làm
được). Script chạy một lần trong ``odoo shell``, tất định, idempotent.

CÁCH CHẠY (trong container, không cần server đang chạy):

    docker compose -f infra/docker-compose.yml run --rm --no-deps -T odoo \
        odoo shell -d odoo17_lab --no-http < addons/lab_core/tools/seed_demo_lon.py

Hoặc 1 lệnh PowerShell (tự upgrade module + restart + kiểm chứng):

    powershell -File infra/scripts/seed-demo-lon.ps1

XOÁ VÀ TẠO LẠI:  đặt env ``LAB_SEED_RESET=1`` (PowerShell: ``-Reset``).
Đã có dữ liệu và KHÔNG reset ⇒ script chỉ in bảng đếm rồi thoát (không tạo trùng).

DẤU NHẬN DẠNG (để xoá lại được, đừng đổi):
  * ``lab.yeu.cau.lay.hang.lab_client_uuid`` = ``demo-lon-*``
  * ``res.partner.ref``                          = ``DEMO-LON-*``
  * ``product.template.default_code``            = ``DL-*``
  * ``lab.chuyen.so_booking``                    = ``DEMO-LON-*``
  * ``lab.hanh.trinh.so_manifest``               = ``DEMO-LON-*``
  * ``sale.order.client_order_ref``              = ``DEMO-LON-*``
  * ``stock.picking.origin``                     = ``DEMO-LON-*``
  * ``account.move.ref``                         = ``DEMO-LON-*``
  * ``res.users.login``                          = ``taixe04``, ``taixe05``
"""

import base64
import datetime as dt
import os
import random
import struct
import zlib

#: Seed cố định ⇒ chạy lại ra CÙNG dữ liệu (test/demo ổn định).
SEED = 2026
CONFIG_KEY = "lab.demo_lon"
SO_DONG = 180  # so ngay lich su


# ======================================================================
# Tiện ích nhỏ
# ======================================================================
def _png(rong=8, cao=8, rgb=(31, 74, 61)):
    """PNG 8x8 hợp lệ → base64. Dùng làm ảnh bằng chứng + chữ ký cho có thật."""

    def chunk(loai, du_lieu):
        c = loai + du_lieu
        return struct.pack(">I", len(du_lieu)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    raw = b""
    for _i in range(cao):
        raw += b"\x00" + bytes(rgb) * rong
    data = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", rong, cao, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )
    return base64.b64encode(data)


ANH = _png(8, 8, (31, 74, 61))
CHU_KY = _png(12, 6, (22, 21, 15))

SHOP = [
    "Shop Hoa Tươi 24h", "Nội Thất Gỗ Việt", "Cà Phê Rang Xay Nguyên Chất",
    "Thời Trang Con Gấu", "Mỹ Phẩm Hàn Quốc Bibi", "Đồ Chơi Trẻ Em Kidsland",
    "Thủy Sản Đông Lạnh Biển Xanh", "Bánh Trung Thu Hương Sen", "Điện Gia Dụng Minh Long",
    "Giày Dép Thể Thao SportX", "Túi Xách Da Handmade", "Máy Tính Linh Kiện TechZone",
    "Trà Sữa Trân Châu Milky", "Sách Cũ Sài Gòn", "Đạt Phân Bón Nông Nghiệp",
    "Bình Gốm Sứ Bát Tràng", "Chăn Ga Gối Đệm Ấm", "Kính Mắt Thời Trang Oscar",
    "Phụ Tùng Xe Máy Tân Phát", "Thực Phẩm Chức Năng Nutri", "Đồ Câu Cá Biển",
    "Vợt Cầu Lông ProSport", "Linh Kiện Ô Tô AutoPart", "Bao Bì Nhựa Tiến Đạt",
    "In Ấn Quảng Cáo Sáng Tạo", "Chậu Cây Cảnh Mini", "Đồ Điện Tử Cửa Hàng Số",
    "Bếp Từ Tân Tiến", "Máy May Công Nghiệp Vina", "Vải Cuộn Dệt May Thành Công",
    "Nhang Trầm Đức Hạnh", "Trứng Gà Sạch Farm VN", "Nước Mắm Truyền Thống Phú Quốc",
    "Cà Rốt Đà Lạt Nông Sản", "Đồ Sơ Sinh Bibi Mart", "Mũ Bảo Hiểm Bình Minh",
    "Sofa Da Luxury", "Đá Quý Trang Sức Kim Long", "Nhựa Kỹ Thuật Sài Gòn",
    "Thiết Bị Y Tế Medico",
]

SHOP_ADDRESS = [
    ("128 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM", "Anh Tuấn", "0903112233"),
    ("45 Trần Hưng Đạo, Quận Hoàn Kiếm, Hà Nội", "Chị Lan", "0912445566"),
    ("77 Lê Lợi, Hải Châu, Đà Nẵng", "Anh Hòa", "0908123456"),
    ("12 Phạm Ngũ Lão, Quận Ninh Kiều, Cần Thơ", "Chị Huệ", "0939887766"),
    ("301 Quốc lộ 1A, Thủ Đức, TP.HCM", "Anh Dũng", "02873001234"),
    ("88 Nguyễn Văn Linh, Quận 7, TP.HCM", "Chị Mai", "0907888999"),
    ("25 Điện Biên Phủ, Quận 3, TP.HCM", "Anh Phúc", "0366777888"),
    ("56 Lê Duẩn, Hải Phòng", "Chị Trang", "0313334444"),
    ("19 Hùng Vương, Bình Dương", "Anh Kiên", "0274333777"),
    ("174 Lạch Tray, Ngô Quyền, Hải Phòng", "Chị Nga", "0225333666"),
    ("9 Trường Chinh, Thanh Xuân, Hà Nội", "Anh Việt", "0966333222"),
    ("63 Cách Mạng Tháng 8, Quận 10, TP.HCM", "Chị Thu", "0905333444"),
]

CONSIGNEE = [
    ("Sakura Foods Co., Ltd", "JP", "Tokyo"),
    ("Kanko Trading K.K.", "JP", "Osaka"),
    ("Nhat Ban Logistics Ltd", "JP", "Nagoya"),
    ("KS Global Express", "KR", "Seoul"),
    ("Ever Top Trading", "TW", "Taipei"),
    ("Pacific Sun LLC", "US", "Los Angeles"),
    ("Hansa Import GmbH", "DE", "Hamburg"),
    ("Siam Fresh Trading", "TH", "Bangkok"),
    ("Lion City Forwarding", "SG", "Singapore"),
]

AGENT_VN = [
    "Kho Ngoại Quan Tân Cảng",
    "Đại Lý Forwarder Miền Nam",
    "Kho Thuê Thế Giới Đông Dương",
    "Cảng Cát Lái Logistics Hub",
]

PRODUCT = [
    ("Cước vận chuyển hàng không (kg)", 185000.0),
    ("Cước vận chuyển đường biển (CBM)", 2450000.0),
    ("Phí lấy hàng nội địa (chuyến)", 850000.0),
    ("Phí kho bãi (ngày/tấn)", 120000.0),
    ("Phí chứng từ AWB / HBL", 650000.0),
    ("Phí thủ tục hải quan (bộ hồ sơ)", 1450000.0),
    ("Phụ phí nhiên liệu (kg)", 42000.0),
    ("Phí bảo hiểm hàng hoá", 320000.0),
]

TUYEN_BAY = [
    ("SGN", "NRT", "Vietnam Airlines", "HN"),
    ("SGN", "HND", "Japan Airlines", "JL"),
    ("SGN", "KIX", "Vietjet Air", "VJ"),
    ("SGN", "ICN", "Korean Air", "KE"),
    ("HAN", "TPE", "Eva Air", "BR"),
]
TUYEN_BIEN = [
    ("CAT LAI", "LOS ANGELES", "Maersk Line"),
    ("CAT LAI", "HAMBURG", "Hapag-Lloyd"),
    ("CAT LAI", "SINGAPORE", "ONE"),
    ("CAT LAI", "BANGKOK", "OOCL"),
    ("CAT LAI", "BUSAN", "Yang Ming"),
]

LY_DO_THAT_BAI = [
    "Shop đóng cửa, không có người",
    "Hàng chưa sẵn sàng, hẹn lại ngày mai",
    "Sai địa chỉ, không tìm được điểm lấy",
    "Hàng quá khổ so với thông tin khai báo",
    "Shop từ chối giao vì chưa thanh toán",
    "Điện thoại liên hệ không liên lạc được",
    "Khung giờ hẹn đã hết, shop đóng cửa",
]

GHI_CHU = [
    "Hàng dễ vỡ, yêu cầu chèn xốp",
    "Gửi bảo vệ toà nhà, số lượng đủ",
    "Hàng thiếu 1 kiện so với khai báo",
    "Shop giao muộn 20 phút",
    "",
    "",
    "Kiện nặng, cần mang cân về kho",
    "Đã chụp ảnh niêm phong trước khi di chuyển",
]


# ======================================================================
# Helpers DB
# ======================================================================
def _co(env, k, model, domain, vals, khoang=None):
    """Tìm (theo domain) hoặc tạo. Trả record (không bắn lỗi nếu không tạo được)."""
    rec = env[model].sudo().search(domain, limit=1)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    if rec:
        return rec
    return env[model].sudo().create(vals)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)


def _delete_muc(env):
    """Xoá dữ liệu do script này tạo (thứ tự phụ thuộc). In số lượng đã xoá."""
    ket_qua = []

    # ------------------------------------------------------------------
    # DỌN BẰNG SQL TRƯỚC: phiếu kho đã "Done" thì ORM KHÔNG cho cancel/xoá
    # ("You cannot cancel a stock move that has been set to 'Done'") và product
    # bị FK chặn xoá. Đã trúng thật 2026-10-29. Dữ liệu demo nên xoá thẳng theo
    # đúng thứ tự con → cha là an toàn và nhanh hơn đi qua workflow.
    # ------------------------------------------------------------------
    for sql in (
        "DELETE FROM stock_move_line WHERE move_id IN (SELECT m.id FROM stock_move m"
        " JOIN stock_picking p ON p.id = m.picking_id WHERE p.origin LIKE 'DEMO-LON%')",
        "DELETE FROM stock_move WHERE picking_id IN"
        " (SELECT id FROM stock_picking WHERE origin LIKE 'DEMO-LON%')",
        "DELETE FROM stock_picking WHERE origin LIKE 'DEMO-LON%'",
        "DELETE FROM mail_message WHERE model = 'lab.yeu.cau.lay.hang' AND res_id IN"
        " (SELECT id FROM lab_yeu_cau_lay_hang WHERE lab_client_uuid LIKE 'demo-lon-%')",
        "DELETE FROM ir_attachment WHERE res_model = 'lab.yeu.cau.lay.hang' AND res_id IN"
        " (SELECT id FROM lab_yeu_cau_lay_hang WHERE lab_client_uuid LIKE 'demo-lon-%')",
        # stock_quant giu FK toi product_product => khong xoa thi khong xoa duoc san pham
        "DELETE FROM stock_quant WHERE product_id IN (SELECT pp.id FROM product_product pp"
        " JOIN product_template pt ON pt.id = pp.product_tmpl_id WHERE pt.default_code LIKE 'DL-%')",
        "DELETE FROM stock_valuation_layer WHERE product_id IN (SELECT pp.id FROM product_product pp"
        " JOIN product_template pt ON pt.id = pp.product_tmpl_id WHERE pt.default_code LIKE 'DL-%')",
    ):
        try:
            env.cr.execute(sql)
        except Exception as exc:  # noqa: BLE001 - dọn dữ liệu, không được chặn seed
            print("   [canh bao] don SQL: " + str(exc)[:90])
            env.cr.rollback()

    def xoa(model, domain, nhan):
        try:
            recs = env[model].sudo().search(domain)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
            n = len(recs)
            if not n:
                return
            for r in recs:
                # Đưa chứng từ về trạng thái xoá được (Odoo chặn xoá bút toán đã post,
                # đơn đã xác nhận, phiếu kho đã done).
                if model == "account.move" and r.state != "draft":
                    try:
                        r.button_draft()
                    except Exception:
                        pass
                if model == "sale.order" and r.state not in ("draft", "cancel"):
                    try:
                        r._action_cancel()
                    except Exception:
                        pass
                if model == "stock.picking" and r.state not in ("draft", "cancel"):
                    try:
                        r.action_cancel()
                    except Exception:
                        pass
            recs.unlink()
            ket_qua.append((nhan, n))
        except Exception as exc:  # noqa: BLE001 - seed data, cần chạy tiếp
            ket_qua.append((nhan + " (loi: " + str(exc)[:60] + ")", 0))

    # ⚠️ THỨ TỰ quan trọng: con trước cha. `lab.chuyen.line.sale_order_id` là NOT NULL
    # nên xoá sale.order trước chuyến sẽ bị chặn (đã trúng lỗi 2026-10-29).
    xoa("account.move", [("ref", "like", "DEMO-LON")], "acc.move")
    xoa("stock.picking", [("origin", "like", "DEMO-LON")], "stock.picking")
    xoa("lab.hanh.trinh", [("so_manifest", "like", "DEMO-LON")], "hanh.trinh")
    xoa("lab.chuyen", [("so_booking", "like", "DEMO-LON")], "chuyen")
    xoa("lab.yeu.cau.lay.hang", ["|", ("lab_client_uuid", "like", "demo-lon-"),
         ("partner_id.ref", "like", "DEMO-LON")], "lay_hang")
    xoa("sale.order", [("client_order_ref", "like", "DEMO-LON")], "sale.order")
    xoa("product.template", [("default_code", "like", "DL-")], "product")
    xoa("res.partner", [("ref", "like", "DEMO-LON")], "partner")
    for k, v in ket_qua:
        print("   xoa %-28s %s" % (k, v))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)


# ======================================================================
# Script chính
# ======================================================================
def chay(env):
    rnd = random.Random(SEED)
    hom_nay = dt.date(2026, 10, 29)

    print("=" * 78)
    print("BO DU LIEU MAU LON - seed %d, %d ngay lich su, hom nay = %s" % (SEED, SO_DONG, hom_nay))
    print("=" * 78)

    cong_ty = env["res.company"].sudo().search([], limit=1)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    current = env.user
    da_co = env["ir.config_parameter"].sudo().get_param(CONFIG_KEY)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    reset = bool(os.environ.get("LAB_SEED_RESET"))

    if da_co and not reset:
        print("  [--] Da co du lieu lon (%s = %s). Muon lam lai: LAB_SEED_RESET=1" % (CONFIG_KEY, da_co))
        _in_bang(env)
        return
    if da_co and reset:
        print("  [--] LAB_SEED_RESET=1 => xoa du lieu cu do script nay tao")
        _delete_muc(env)

    # ------------------------------------------------------------------ 1. danh muc
    print("\n[1/7] Danh muc: khach/shop, dai ly, san pham, tai xe, journal Debit")
    Partner = env["res.partner"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    quoc_gia = {}
    for ma in ("JP", "KR", "TW", "US", "DE", "TH", "SG", "VN"):
        qg = env["res.country"].sudo().search([("code", "=", ma)], limit=1)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        if qg:
            quoc_gia[ma] = qg

    shops = []
    for i, ten in enumerate(SHOP):
        dia_chi, lien_he, sdt = SHOP_ADDRESS[i % len(SHOP_ADDRESS)]
        p = Partner.create({
            "name": ten,
            "ref": "DEMO-LON-SHOP-%03d" % (i + 1),
            "is_company": True,
            "email": "shop%02d@demo.local" % (i + 1),
            "phone": sdt,
            "street": dia_chi,
            "country_id": quoc_gia.get("VN").id if quoc_gia.get("VN") else False,
            "comment": "Shop demo - sinh boi seed_demo_lon.py",
        })
        shops.append(p)

    # Khách CHƯA RÕ (nhánh ngoại lệ 1) — partner_id là bắt buộc nên phải có 1 partner riêng.
    khach_chua_ro = Partner.create({
        "name": "Khách chưa xác định (chờ CS xử lý)",
        "ref": "DEMO-LON-UNKNOWN",
        "is_company": True,
        "comment": "Ngoại lệ 1 (docs/07): hàng về chưa rõ chủ. CS phải xác minh.",
    })

    dai_ly = []
    for i, ten in enumerate(AGENT_VN):
        dai_ly.append(Partner.create({
            "name": ten,
            "ref": "DEMO-LON-AGENT-%03d" % (i + 1),
            "is_company": True,
            "email": "agent%02d@demo.local" % (i + 1),
            "country_id": quoc_gia.get("VN").id if quoc_gia.get("VN") else False,
        }))

    nuoc_ngoai = []
    for i, (ten, ma, thanh_pho) in enumerate(CONSIGNEE):
        nuoc_ngoai.append(Partner.create({
            "name": ten,
            "ref": "DEMO-LON-CONS-%03d" % (i + 1),
            "is_company": True,
            "email": "buyer%02d@demo.local" % (i + 1),
            "city": thanh_pho,
            "country_id": quoc_gia.get(ma).id if quoc_gia.get(ma) else False,
            "lang": "en_US",
        }))

    products = []
    for i, (ten, gia) in enumerate(PRODUCT):
        products.append(env["product.template"].sudo().create({  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
            "name": ten,
            "default_code": "DL-%03d" % (i + 1),
            "type": "service",
            "list_price": gia,
            "sale_ok": True,
            "purchase_ok": False,
        }))

    kho_list = env["stock.warehouse"].sudo().search([])  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    kho_bay = kho_list[0] if kho_list else False
    kho_tau = kho_list[1] if len(kho_list) > 1 else kho_bay

    # Tài xế: 2 tài khoản mới (portal + nhóm tài xế), có email để chatter không lỗi.
    nhom_tai_xe = env.ref("lab_core.group_driver")
    nhan_vien = env["hr.employee"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    tai_xe_1 = nhan_vien.search([("user_id.login", "=", "taixe01")], limit=1)
    phong_ban = tai_xe_1.department_id if tai_xe_1 else False
    # Mỗi user 1 nhân viên: DB có thể đã sinh trùng (lần chạy lỗi trước tạo thêm
    # employee cho cùng user) => gom theo user_id, giữ bản ghi id nhỏ nhất.
    _theo_user = {}
    for _e in nhan_vien.search([("user_id.login", "like", "taixe0")], order="id"):
        _theo_user.setdefault(_e.user_id.id, _e)
    tai_xe = list(_theo_user.values())
    for i, (login, ten) in enumerate((("taixe04", "Đặng Quốc Bảo"), ("taixe05", "Lý Thanh Hà"))):
        u = env["res.users"].sudo().search([("login", "=", login)], limit=1)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        if not u:
            # ⚠️ KHÔNG đặt notification_type="inbox": tài xế là portal, còn ràng buộc
            # `res_users_notification_type` của Odoo chặn inbox cho user không nội bộ
            # (đã gặp thật 2026-10-29: CheckViolation). Để mặc định (email).
            u = env["res.users"].sudo().with_context(no_reset_password=True).create({  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
                "name": ten,
                "login": login,
                "password": "lab@2026",
                "email": login + "@demo.local",
                "groups_id": [(6, 0, [nhom_tai_xe.id])],
                "tz": "Asia/Ho_Chi_Minh",
            })
        e = nhan_vien.search([("user_id", "=", u.id)], limit=1)
        if not e:
            e = nhan_vien.create({
                "name": ten,
                "user_id": u.id,
                "department_id": phong_ban.id if phong_ban else False,
            })
        tai_xe.append(e)
    tai_xe = [e for e in tai_xe if e]

    # Tài khoản kế toán trước (để biết CÔNG TY NÀO có chart), rồi mới tạo journal Debit
    # trong ĐÚNG công ty đó. ⚠️ Đã trúng lỗi thật 2026-10-29: tạo journal DEBIT ở công ty
    # "My Company (San Francisco)" nhưng tài khoản lấy được thuộc công ty khác ⇒
    # "Incompatible companies on records" ⇒ 0/30 phiếu ghi nợ.
    acc_phai_thu = env["account.account"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("account_type", "=", "asset_receivable")], limit=1)
    acc_doanh_thu = env["account.account"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("account_type", "=", "income")], limit=1)
    cong_ty_kt = (acc_phai_thu.company_id if acc_phai_thu else False) or cong_ty

    # Journal Debit (docs/00 §7) — DB demo CHUA co, thiếu nó thì debit_ids rỗng + tier không chạy.
    # Ưu tiên journal CÙNG công ty với tài khoản; nếu đã có DEBIT ở công ty khác thì
    # DÙNG LẠI (đừng tạo cái thứ hai — đã trúng: 2 journal "Phiếu ghi nợ (Debit)").
    j_debit = env["account.journal"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("code", "=", "DEBIT"), ("company_id", "=", cong_ty_kt.id)], limit=1)
    if not j_debit:
        j_debit = env["account.journal"].sudo().search([("code", "=", "DEBIT")], limit=1)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    if not j_debit:
        j_debit = env["account.journal"].sudo().create({  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
            "name": "Phiếu ghi nợ (Debit)",
            "code": "DEBIT",
            "type": "general",
            "company_id": cong_ty_kt.id,
        })
    # Journal bán hàng cho HOÁ ĐƠN: giữ như bản đã chạy được (110/110) — không đổi công ty
    # của nó, vì sản phẩm/khách của seed tạo ở công ty mặc định.
    j_ban = env["account.journal"].sudo().search([("type", "=", "sale")], limit=1)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    print("  shop=%d · dai_ly=%d · nuoc_ngoai=%d · san_pham=%d · tai_xe=%d · DEBIT=%s (%s) · j_ban=%s · phai_thu=%s · doanh_thu=%s"
          % (len(shops), len(dai_ly), len(nuoc_ngoai), len(products), len(tai_xe), j_debit.code,
             cong_ty_kt.name, j_ban.code if j_ban else "-",
             acc_phai_thu.code or "-", acc_doanh_thu.code or "-"))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)

    # ------------------------------------------------------------------ 2. đơn hàng
    # ⚠️ PHẢI tạo TRƯỚC chuyến: `lab.chuyen.line.sale_order_id` là NOT NULL (đã trúng
    # lỗi thật 2026-10-29: "null value in column sale_order_id").
    print("\n[2/7] Don hang (120): draft/sent/sale/done, 1-3 dong cuoc")
    SO = env["sale.order"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    so_list = []
    for i in range(120):
        shop = shops[i % len(shops)]
        ngay = hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG))
        trang_thai = "draft" if i < 10 else ("sent" if i < 25 else ("sale" if i < 105 else "done"))
        lines = []
        for _k in range(rnd.randint(1, 3)):
            sp = products[rnd.randrange(len(products))]
            lines.append((0, 0, {
                "product_id": sp.product_variant_id.id,
                "product_uom_qty": rnd.randint(1, 30),
                "price_unit": sp.list_price,
                "name": sp.name,
            }))
        try:
            so = SO.create({
                "partner_id": shop.id,
                "date_order": dt.datetime.combine(ngay, dt.time(10, 0)),
                "client_order_ref": "DEMO-LON-SO-%03d" % (i + 1),
                "order_line": lines,
            })
            if trang_thai in ("sale", "done"):
                so.action_confirm()
                # KHÔNG write state="done": Odoo chặn ghi trực tiếp (đã trúng lỗi
                # "Wrong value for sale.order.state: done" 2026-10-29). Đơn đã xác
                # nhận vẫn đủ để demo; muốn "done" thì phải đi qua giao hàng thật.
            elif trang_thai == "sent":
                try:
                    so.action_quotation_sent()
                except Exception:
                    pass
            so_list.append(so)
        except Exception as exc:  # noqa: BLE001
            print("   [loi] don hang %d: %s" % (i + 1, str(exc)[:110]))
    print("  tao %d don hang" % len(so_list))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)

    # ------------------------------------------------------------------ 3. việc lấy hàng
    print("\n[3/7] Viec lay hang (300) + bang chung (anh/chu ky)")
    Job = env["lab.yeu.cau.lay.hang"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    phan_bo = (
        ["new"] * 12 + ["assigned"] * 24 + ["en_route"] * 10 + ["picked_up"] * 16
        + ["confirmed"] * 190 + ["failed"] * 28 + ["cancelled"] * 20
    )
    rnd.shuffle(phan_bo)
    jobs = []
    thong_ke = {}
    # Đếm RIÊNG cho 2 nhánh ngoại lệ: danh sách đã shuffle nên không thể dựa vào chỉ số
    # `i` (đã trúng: 0 việc "chưa rõ khách" và 0 việc "thiếu kiện" vì i%23 rơi vào state khác).
    dem_chua_ro = 0
    dem_thieu_kien = 0
    dem_thieu_tt = 0  # nhánh ngoại lệ ③: đơn thiếu thông tin (không địa chỉ / không khung giờ)
    for i, trang_thai in enumerate(phan_bo):
        # Hôm nay có >= 8 việc mới để dashboard "Hôm nay" không rỗng.
        # ⚠️ Việc ĐANG LÀM (assigned/en_route/picked_up) phải có khung giờ HÔM NAY:
        # `/driver/jobs` chỉ trả việc hôm nay (hoặc không đặt giờ). Đo 2026-10-29: seed
        # rải 180 ngày ⇒ taixe04 mở app thấy "0 việc" dù DB có 18 việc đang làm.
        if trang_thai in ("assigned", "en_route", "picked_up"):
            ngay = hom_nay
        elif i < 24 or trang_thai == "new":
            # 24 việc đầu rơi vào hôm nay / 1-2 ngày trước để dashboard "Hôm nay"
            # và "7 ngày" có số thật (đo 2026-10-29: chỉ 1 việc today là quá ít).
            ngay = hom_nay - dt.timedelta(days=rnd.choice([0, 0, 0, 0, 1, 1, 2]))
        else:
            ngay = hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG))
        gio = rnd.randint(8, 17)
        shop = shops[rnd.randrange(len(shops))]
        dia_chi, lien_he, sdt = SHOP_ADDRESS[rnd.randrange(len(SHOP_ADDRESS))]
        so_kien = rnd.randint(1, 12)
        kg = round(rnd.uniform(5, 480), 1)
        vals = {
            "partner_id": shop.id,
            "dia_chi_lay": dia_chi,
            "lien_he_ten": lien_he,
            "lien_he_sdt": sdt,
            "khung_gio_tu": dt.datetime.combine(ngay, dt.time(gio, 0)),
            "khung_gio_den": dt.datetime.combine(ngay, dt.time(min(gio + 3, 20), 0)),
            "so_kien_du_kien": so_kien,
            "khoi_luong_uoc_kg": kg,
            "state": trang_thai,
            "lab_client_uuid": "demo-lon-%04d" % (i + 1),
            # 2/3 số việc gắn đơn hàng (bước ② trong docs/00: có đơn rồi mới lấy hàng).
            "sale_order_id": so_list[i % len(so_list)].id if (so_list and i % 3 != 2) else False,
        }
        # Ngoại lệ ③: đơn THIẾU THÔNG TIN — không có địa chỉ lấy và không có khung giờ
        # (CS phải gọi lại shop để bổ sung trước khi điều phối).
        if trang_thai in ("new", "assigned") and dem_thieu_tt < 6:
            dem_thieu_tt += 1
            vals.pop("dia_chi_lay", None)
            vals.pop("khung_gio_tu", None)
            vals.pop("khung_gio_den", None)
        # Ngoại lệ 1: hàng chưa rõ khách (đúng 5 việc trạng thái new).
        if trang_thai == "new" and dem_chua_ro < 5:
            dem_chua_ro += 1
            vals.update({
                "partner_id": khach_chua_ro.id,
                "dia_chi_lay": "Chưa xác định — hàng tập kết tại " + dia_chi,
            })

        co_tai_xe = trang_thai in ("assigned", "en_route", "picked_up", "confirmed") or (
            trang_thai == "failed" and rnd.random() < 0.7)
        if co_tai_xe and tai_xe:
            vals["driver_id"] = tai_xe[rnd.randrange(len(tai_xe))].id

        if trang_thai in ("picked_up", "confirmed", "failed"):
            vals["ngay_giao"] = dt.datetime.combine(ngay, dt.time(gio + 1, rnd.randint(0, 59)))
            thuc_te = so_kien
            if trang_thai == "confirmed" and dem_thieu_kien < 8:  # ngoại lệ 4: xuất thiếu kiện
                dem_thieu_kien += 1
                thuc_te = max(1, so_kien - rnd.randint(1, 2))
            vals["so_kien_thuc_te"] = thuc_te
            vals["chu_ky"] = CHU_KY
            ghi_chu = GHI_CHU[rnd.randrange(len(GHI_CHU))]
            if thuc_te < so_kien:
                ghi_chu = ("Thiếu %d kiện so với khai báo, đã báo CS." % (so_kien - thuc_te))
            vals["ghi_chu_tai_xe"] = ghi_chu
            vals["anh_ids"] = [(0, 0, {
                "name": "anh-%04d.png" % (i + 1),
                "datas": ANH,
                "mimetype": "image/png",
            })]

        if trang_thai == "confirmed":
            vals["ngay_xac_nhan"] = dt.datetime.combine(
                ngay + dt.timedelta(days=1), dt.time(rnd.randint(8, 17), 0))

        if trang_thai == "failed":
            vals["ly_do_that_bai"] = LY_DO_THAT_BAI[rnd.randrange(len(LY_DO_THAT_BAI))]

        try:
            job = Job.create(vals)
            jobs.append(job)
            thong_ke[trang_thai] = thong_ke.get(trang_thai, 0) + 1
        except Exception as exc:  # noqa: BLE001
            print("   [loi] job %d (%s): %s" % (i + 1, trang_thai, str(exc)[:110]))
    print("  tao %d/%d viec: %s" % (len(jobs), len(phan_bo), thong_ke))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)

    # ------------------------------------------------------------------ 4. chuyến
    print("\n[4/7] Chuyen (24) + dong hang, co 2 chuyen qua tai va 1 chuyen giam tai")
    Chuyen = env["lab.chuyen"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    trang_thai_chuyen = (
        ["draft"] * 2 + ["open"] * 4 + ["booking_confirmed"] * 5 + ["departed"] * 4
        + ["arrived"] * 3 + ["delivered"] * 3 + ["closed"] * 2 + ["cancelled"] * 1
    )
    rnd.shuffle(trang_thai_chuyen)
    jobs_nguon = [j for j in jobs if j.state in ("confirmed", "picked_up")] or jobs
    chuyen_list = []
    for i, trang_thai in enumerate(trang_thai_chuyen):
        bay = i % 3 != 0
        # TUYEN_BAY co 4 phan tu (san bay di/den/hang/prefix IATA), TUYEN_BIEN chi co 3
        # => luon lay 3 phan tu dau de khong vo khi doi danh sach.
        ma_di, ma_den, hang = (TUYEN_BAY if bay else TUYEN_BIEN)[i % 5][:3]
        ngay_di = hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG))
        vals = {
            "loai_van_chuyen": "air" if bay else "sea",
            "state": trang_thai,
            "hang_van_chuyen": hang,
            "so_booking": "DEMO-LON-BK-%03d" % (i + 1),
            "ngay_di": ngay_di,
            "ngay_den_du_kien": ngay_di + dt.timedelta(days=rnd.randint(2, 12) if bay else rnd.randint(18, 40)),
            "cut_off_datetime": dt.datetime.combine(ngay_di - dt.timedelta(days=2), dt.time(17, 0)),
            "kho_xuat_id": (kho_bay if bay else kho_tau).id if (kho_bay or kho_tau) else False,
            "tai_trong_max_kg": round(rnd.uniform(1200, 3000), 0) if bay else 0.0,
            "the_tich_max_cbm": 0.0 if bay else round(rnd.uniform(15, 28), 2),
        }
        try:
            chuyen = Chuyen.create(vals)
        except Exception as exc:  # noqa: BLE001
            print("   [loi] chuyen %d: %s" % (i + 1, str(exc)[:110]))
            continue
        # Dòng hàng: 3–7 việc. `sale_order_id` là NOT NULL trên lab.chuyen.line.
        so_dong = rnd.randint(3, 7)
        qua_tai = i in (0, 1)  # 2 chuyến quá tải (ngoại lệ 2)
        giam_tai = i == 2        # 1 chuyến có dòng bị rút
        if not so_list:
            print("   [--] khong co don hang => bo qua dong chuyen")
            chuyen_list.append(chuyen)
            continue
        for _k in range(so_dong):
            job = jobs_nguon[rnd.randrange(len(jobs_nguon))]
            so_dong_hang = so_list[rnd.randrange(len(so_list))]
            kg_dong = job.khoi_luong_uoc_kg or round(rnd.uniform(20, 180), 1)
            if qua_tai:
                kg_dong = kg_dong * 4  # đẩy tỉ lệ tải > 100%
            line_vals = {
                "chuyen_id": chuyen.id,
                "sale_order_id": so_dong_hang.id,
                "partner_id": job.partner_id.id,
                "so_kien": job.so_kien_du_kien,
                "khoi_luong_kg": round(kg_dong, 2),
                "the_tich_cbm": round(kg_dong / 167.0, 4) if not bay else 0.0,
            }
            if giam_tai and _k == 0:
                line_vals["state"] = "removed"
                line_vals["ly_do_go"] = "Giảm tải: chuyến vượt tải trọng, chuyển sang chuyến sau"
            try:
                env["lab.chuyen.line"].sudo().create(line_vals)  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
            except Exception as exc:  # noqa: BLE001
                print("   [loi] dong chuyen %d: %s" % (i + 1, str(exc)[:90]))
        chuyen_list.append(chuyen)
    print("  tao %d chuyen" % len(chuyen_list))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)

    # ------------------------------------------------------------------ 5. hành trình
    print("\n[5/7] Hanh trinh (12) phu 11 moc + chang duong")
    HanhTrinh = env["lab.hanh.trinh"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    moc = ["draft", "booked", "departed", "in_transit", "arrived", "handed_to_agent",
           "delivered", "closed", "exception", "returned", "compensated"]
    ht_list = []
    for i in range(12):
        stage = moc[i % len(moc)]
        bay = i % 3 != 0
        ma_di, ma_den, _hang = (TUYEN_BAY if bay else TUYEN_BIEN)[i % 5][:3]
        chuyen = chuyen_list[i % len(chuyen_list)] if chuyen_list else False
        ngay = hom_nay - dt.timedelta(days=rnd.randint(1, SO_DONG))
        vals = {
            "name": ("AWB" if bay else "HBL") + "-DEMO-%03d" % (i + 1),
            "loai_chung_tu": "awb" if bay else "hbl",
            "stage": stage,
            "chuyen_id": chuyen.id if chuyen else False,
            "cang_di": ma_di,
            "cang_den": ma_den,
            "ngay_bay": dt.datetime.combine(ngay, dt.time(rnd.randint(6, 20), 0)),
            "ngay_den": dt.datetime.combine(ngay + dt.timedelta(days=rnd.randint(1, 9)), dt.time(12, 0)),
            "dai_ly_giao_id": dai_ly[i % len(dai_ly)].id if dai_ly else False,
            "so_manifest": "DEMO-LON-MF-%03d" % (i + 1),
            "line_ids": [
                (0, 0, {"moc": "Đã nhận hàng tại kho", "thoi_diem": dt.datetime.combine(ngay, dt.time(9, 0)),
                        "dia_diem": "Kho " + ("bay" if bay else "tau")}),
                (0, 0, {"moc": "Khởi hành", "thoi_diem": dt.datetime.combine(ngay, dt.time(21, 0)),
                        "dia_diem": ma_di}),
            ],
        }
        if stage in ("arrived", "handed_to_agent", "delivered", "closed"):
            vals["pod_date"] = dt.datetime.combine(ngay + dt.timedelta(days=3), dt.time(15, 0))
        try:
            ht_list.append(HanhTrinh.create(vals))
        except Exception as exc:  # noqa: BLE001
            print("   [loi] hanh trinh %d: %s" % (i + 1, str(exc)[:110]))
    print("  tao %d hanh trinh" % len(ht_list))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)

    # ------------------------------------------------------------------ 6. kho & tien
    print("\n[6/7] Phieu kho · hoa don khach (110) · phieu ghi no Debit (30)")

    # Phiếu kho: 40 WH/IN (việc đã về kho) + 8 WH/OUT (chuyến đã đi) + 4 chuyển kho nội bộ.
    # ⚠️ Lấy VỊ TRÍ theo ĐÚNG CÔNG TY của loại phiếu: DB demo có nhiều công ty, trộn
    # location công ty A với picking type công ty B ⇒ "Incompatible companies on records"
    # (đã trúng lỗi thật 2026-10-29: tạo 0/48 phiếu kho).
    Picking = env["stock.picking"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    loai_picking = {}
    # ⚠️ PHẢI active_test=False: DB demo để ARCHIVE loại "Internal Transfers"/"Pack"
    # nên search thường chỉ thấy incoming/outgoing (đã trúng 2026-10-29: loai_keys
    # chỉ có 2 mã ⇒ bỏ qua 4 phiếu chuyển kho nội bộ của ngoại lệ ⑤).
    for _pt in env["stock.picking.type"].sudo().with_context(active_test=False).search([]):  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        loai_picking.setdefault(_pt.code, _pt)
    pt_vao = loai_picking.get("incoming")
    kho_ty = pt_vao.company_id if pt_vao else cong_ty
    kho_hang = env["stock.location"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("usage", "=", "internal"), ("company_id", "=", kho_ty.id)], limit=1)
    kho_khach = env["stock.location"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("usage", "=", "customer"), ("company_id", "in", [kho_ty.id, False])], limit=1)
    kho_nha_cung_cap = env["stock.location"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("usage", "=", "supplier"), ("company_id", "in", [kho_ty.id, False])], limit=1)
    print("  kho_ty=%s · kho_hang=%s · kho_khach=%s · ncc=%s"
          % (kho_ty.name, kho_hang.complete_name, kho_khach.complete_name, kho_nha_cung_cap.complete_name))
    picking_tao = 0
    job_confirmed = [j for j in jobs if j.state == "confirmed"]

    def tao_picking(loai, job, khong_nguyen_lieu):
        pt = loai_picking.get(loai)
        if not pt or not kho_hang:
            return None
        vals = {
            "picking_type_id": pt.id,
            "origin": "DEMO-LON-%s-%s" % (loai.upper(), job.lab_client_uuid),
            "partner_id": job.partner_id.id,
            "location_id": (kho_nha_cung_cap or kho_hang).id if loai == "incoming" else kho_hang.id,
            "location_dest_id": kho_hang.id if loai == "incoming" else (kho_khach or kho_hang).id,
            "move_ids": [(0, 0, {
                "name": "Kien hang " + job.lab_client_uuid,
                "product_id": products[0].product_variant_id.id,
                "product_uom_qty": job.so_kien_thuc_te or job.so_kien_du_kien or 1,
                "product_uom": products[0].uom_id.id,
                "location_id": (kho_nha_cung_cap or kho_hang).id if loai == "incoming" else kho_hang.id,
                "location_dest_id": kho_hang.id if loai == "incoming" else (kho_khach or kho_hang).id,
            })],
        }
        try:
            pk = Picking.create(vals)
            pk.action_confirm()
            # KHÔNG button_validate(): phiếu "Done" sinh stock_quant và Odoo chặn
            # cancel/xoá về sau (đã trúng thật 2026-10-29). Để "Đã xác nhận" là đủ cho
            # demo kho + liên kết job.stock_picking_id.
            return pk
        except Exception as exc:  # noqa: BLE001
            print("   [loi] picking %s: %s" % (loai, str(exc)[:110]))
            return None

    for job in job_confirmed[:40]:
        pk = tao_picking("incoming", job, None)
        if pk:
            picking_tao += 1
            try:
                job.write({"stock_picking_id": pk.id})
            except Exception:
                pass
    for job in job_confirmed[40:48]:
        pk = tao_picking("outgoing", job, None)
        if pk:
            picking_tao += 1
    # Ngoại lệ 5: chuyển kho bay <-> tàu (internal).
    # ⚠️ `stock.warehouse.lot_stock_id` có thể rỗng (DB demo tạo kho bằng tay) ⇒ dùng
    # 2 VỊ TRÍ NỘI BỘ bất kỳ của đúng công ty, không phụ thuộc warehouse (đã trúng lỗi
    # "0 phiếu chuyển kho nội bộ" 2026-10-29).
    vi_tri_noi_bo = env["stock.location"].sudo().search(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        [("usage", "=", "internal"), ("company_id", "=", kho_ty.id)], limit=2)
    pt_internal = loai_picking.get("internal") or env["stock.picking.type"].sudo().with_context(  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
        active_test=False).search([("code", "=", "internal"), ("company_id", "=", kho_ty.id)], limit=1)
    if len(vi_tri_noi_bo) >= 2 and pt_internal:
        nguon, dich = vi_tri_noi_bo[0], vi_tri_noi_bo[1]
        for i in range(4):
            try:
                pk = Picking.create({
                    "picking_type_id": pt_internal.id,
                    "origin": "DEMO-LON-INTERNAL-%02d" % (i + 1),
                    "location_id": nguon.id,
                    "location_dest_id": dich.id,
                    "move_ids": [(0, 0, {
                        "name": "Chuyen kho bay -> tau %02d" % (i + 1),
                        "product_id": products[0].product_variant_id.id,
                        "product_uom_qty": rnd.randint(1, 10),
                        "product_uom": products[0].uom_id.id,
                        "location_id": nguon.id,
                        "location_dest_id": dich.id,
                    })],
                })
                pk.action_confirm()
                picking_tao += 1
            except Exception as exc:  # noqa: BLE001
                print("   [loi] picking internal: %s" % (str(exc)[:110]))
    else:
        print("   [--] chi co %d vi tri noi bo => bo qua chuyen kho noi bo" % len(vi_tri_noi_bo))
    print("  tao %d phieu kho" % picking_tao)

    # Hoá đơn khách (REA: mỗi việc đã về kho ⇒ 1 bút toán doanh thu).
    Move = env["account.move"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    hoa_don = 0
    if so_list and j_ban:
        for i in range(110):
            so = so_list[i % len(so_list)]
            ngay = hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG))
            lines = []
            for line in so.order_line[:2]:
                lines.append((0, 0, {
                    "product_id": line.product_id.id,
                    "name": line.name,
                    "quantity": line.product_uom_qty,
                    "price_unit": line.price_unit,
                }))
            try:
                mv = Move.create({
                    "move_type": "out_invoice",
                    "partner_id": so.partner_id.id,
                    "journal_id": j_ban.id,
                    "invoice_date": ngay,
                    "date": ngay,
                    "ref": "DEMO-LON-INV-%03d" % (i + 1),
                    "invoice_line_ids": lines,
                })
                if i < 105:
                    mv.action_post()
                hoa_don += 1
            except Exception as exc:  # noqa: BLE001
                print("   [loi] hoa don %d: %s" % (i + 1, str(exc)[:110]))
    print("  tao %d hoa don" % hoa_don)

    # Phiếu ghi nợ (Debit) — account.move trên journal DEBIT, gắn chuyến (bước 13).
    debit = 0
    debit_da_post = 0
    if chuyen_list and acc_phai_thu and acc_doanh_thu:
        for i in range(30):
            chuyen = chuyen_list[i % len(chuyen_list)]
            ngay = hom_nay - dt.timedelta(days=rnd.randint(1, SO_DONG))
            so_tien = round(rnd.uniform(2000000, 85000000), 0)
            try:
                mv = Move.create({
                    "move_type": "entry",
                    "journal_id": j_debit.id,
                    "date": ngay,
                    "ref": "DEMO-LON-DEBIT-%03d" % (i + 1),
                    "lab_chuyen_id": chuyen.id,
                    "line_ids": [
                        (0, 0, {"name": "Phải thu cước chuyến " + chuyen.so_booking,
                                "account_id": acc_phai_thu.id, "debit": so_tien, "credit": 0.0}),
                        (0, 0, {"name": "Doanh thu cước chuyến " + chuyen.so_booking,
                                "account_id": acc_doanh_thu.id, "debit": 0.0, "credit": so_tien}),
                    ],
                })
                debit += 1
                # Debit trên journal DEBIT bị TIER VALIDATION chặn post (đúng nghiệp vụ:
                # phải qua "Duyệt khoá Debit" ⑭). Post được thì post, không thì để nháp
                # => màn duyệt có việc thật để bấm.
                try:
                    mv.action_post()
                    debit_da_post += 1
                except Exception:
                    pass
            except Exception as exc:  # noqa: BLE001
                print("   [loi] debit %d: %s" % (i + 1, str(exc)[:110]))
    print("  tao %d phieu ghi no (post duoc %d, con lai cho duyet)" % (debit, debit_da_post))
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)

    # ------------------------------------------------------------------ 6. backdate
    print("\n[7/7] Backdate create_date (dashboard can truc thoi gian that)")
    from psycopg2.extras import execute_values

    def backdate(bang, cap):
        if not cap:
            return 0
        try:
            execute_values(
                env.cr._obj,  # psycopg2 cursor tho (env.cr la wrapper odoo.sql_db.Cursor)
                "UPDATE " + bang + " SET create_date = v.d, write_date = v.d "
                "FROM (VALUES %s) AS v(id, d) WHERE " + bang + ".id = v.id",
                [(rid, ngay) for rid, ngay in cap],
            )
            return len(cap)
        except Exception as exc:  # noqa: BLE001
            print("   [loi] backdate " + bang + ": " + str(exc)[:110])
            return 0

    cap_job = [(j.id, dt.datetime.combine(hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG)),
                                          dt.time(rnd.randint(8, 17), 0)))
               for j in jobs]
    n1 = backdate("lab_yeu_cau_lay_hang", cap_job)
    n2 = backdate("lab_chuyen", [(c.id, dt.datetime.combine(
        hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG)), dt.time(9, 0))) for c in chuyen_list])
    n3 = backdate("lab_hanh_trinh", [(h.id, dt.datetime.combine(
        hom_nay - dt.timedelta(days=rnd.randint(0, SO_DONG)), dt.time(9, 0))) for h in ht_list])
    print("  backdate: job=%d chuyen=%d hanh_trinh=%d" % (n1, n2, n3))

    # Đánh dấu đã chạy.
    env["ir.config_parameter"].sudo().set_param(CONFIG_KEY, dt.datetime.now().isoformat(timespec="seconds"))  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    env.cr.commit()  # audit-ok: seed theo lo, giu transaction nho (script tool, khong phai code nghiep vu)
    env.invalidate_all()

    print("")
    _in_bang(env)
    print("\n=== DA COMMIT — bo du lieu mau lon san sang ===")


def _in_bang(env):
    """Bảng đếm nhanh (đọc bằng sudo để không phụ thuộc quyền của shell user)."""
    Job = env["lab.yeu.cau.lay.hang"].sudo()  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)
    tong = 0
    print("  %-12s %s" % ("job", "-" * 40))
    for st in ("new", "assigned", "en_route", "picked_up", "confirmed", "cancelled", "failed"):
        n = Job.search_count([("state", "=", st)])
        tong += n
        print("    %-12s %d" % (st, n))
    print("    %-12s %d" % ("TONG", tong))
    for model, nhan in (("lab.chuyen", "chuyen"), ("lab.hanh.trinh", "hanh.trinh"),
                        ("sale.order", "sale.order"), ("stock.picking", "stock.picking"),
                        ("account.move", "account.move"), ("res.partner", "res.partner"),
                        ("product.template", "product")):
        print("  %-12s %d" % (nhan, env[model].sudo().search_count([])))  # sudo-ok: script seed dữ liệu demo (chạy bằng admin, không phải code nghiệp vụ phục vụ request)


if __name__ == "__main__" or "env" in globals():
    chay(env)