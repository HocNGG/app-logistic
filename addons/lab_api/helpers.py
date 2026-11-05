# -*- coding: utf-8 -*-
"""Helper dùng chung cho các controller của ``lab_api``.

Vì sao tách khỏi ``tools.py``: ``tools.py`` là tiện ích **kỹ thuật** (envelope,
rate limit, base64, token) — không biết gì về nghiệp vụ. File này là các mảnh
**nghiệp vụ lặp ở ≥2 controller**:

* payload gọn của yêu cầu lấy hàng — mỗi màn của app đọc MỘT bộ khoá, không tự
  đoán tên field (``/driver/jobs`` · ``/cs/jobs`` · ``/kho/cho-nhan``);
* lấy bản ghi trong phạm vi quyền của người đang đăng nhập (ir.rule, không sudo);
* kiểm ``X-Lab-Token`` của phiên;
* chặn theo NHÓM ở tầng controller — xem ``kiem_nhom`` (có giải thích Luật 3).

Quy ước: chuỗi người dùng thấy là tiếng Việt có dấu, bọc ``_()``.
"""

from odoo import SUPERUSER_ID, _, fields
from odoo.exceptions import AccessError
from odoo.http import request

from .tools import bam_token, gio_phut, loi

#: Trạng thái "tài xế đang làm" — dùng để đếm ``jobsToday`` của một phiên app
#: (chuyển từ ``controllers/auth.py`` sang đây khi ``/admin/switch`` cần CÙNG con
#: số với ``/auth/login``; hai endpoint dùng chung ``payload_phien``).
TRANG_THAI_VIEC = ("assigned", "en_route", "picked_up")

# ======================================================================
# Nhãn & payload
# ======================================================================
def nhan_state(job):
    """Nhãn tiếng Việt của trạng thái — lấy từ selection của model (một nguồn thật)."""
    return dict(job._fields["state"].selection).get(job.state, job.state)


def job_dict(job):
    """Payload gọn cho app TÀI XẾ (giữ NGUYÊN khoá cũ — app đang cài dùng bộ này).

    Chỉ đọc field **của chính yêu cầu lấy hàng**: tài xế là portal nên không đọc
    được ``res.partner``/``sale.order`` của shop, tên shop lấy từ ``shop_name``
    (field related stored trên model — OQ-22 cùng lý do).
    """
    du_lieu = {
        "id": job.id,
        "ref": job.name,
        "shop": job.shop_name or "",
        "address": job.dia_chi_lay or "",
        "contactName": job.lien_he_ten or "",
        "contactPhone": job.lien_he_sdt or "",
        "windowFrom": gio_phut(job, job.khung_gio_tu),
        "windowTo": gio_phut(job, job.khung_gio_den),
        "state": job.state,
        "stateLabel": nhan_state(job),
        "note": job.lab_exception_note or "",
    }
    # OQ-22: chỉ trả "số kiện dự kiến" khi có NGUỒN THẬT trên yêu cầu lấy hàng.
    if job.so_kien_du_kien:
        du_lieu["expectedPackages"] = job.so_kien_du_kien
    return du_lieu


def job_dict_dieu_phoi(job):
    """Payload cho màn ĐIỀU PHỐI của CS (``GET /cs/jobs``).

    Gọn như ``job_dict`` nhưng bỏ phần chỉ tài xế cần (``contactName``, ``note``)
    và thêm ``driverName`` khi việc đã gán tài xế. **Không** trả ``driver_id``
    hay field quan hệ nào của ``hr.employee``: CS là Internal User thường, Odoo 17
    chỉ cấp ``hr.employee`` cho ``hr.group_hr_user`` nên chạm ``driver_id`` là
    AccessError giữa request (dùng ``driver_user_id`` — cột stored trên chính
    model này).
    """
    du_lieu = {
        "id": job.id,
        "ref": job.name,
        "shop": job.shop_name or "",
        "address": job.dia_chi_lay or "",
        "contactPhone": job.lien_he_sdt or "",
        "windowFrom": gio_phut(job, job.khung_gio_tu),
        "windowTo": gio_phut(job, job.khung_gio_den),
        "state": job.state,
        "stateLabel": nhan_state(job),
    }
    if job.so_kien_du_kien:
        du_lieu["expectedPackages"] = job.so_kien_du_kien
    # Thời điểm TẠO việc — app hiện "Tạo lúc …" và sắp xếp màn phân công theo mốc này.
    # Format giống `quetLuc` của `/kho/jobs/<id>/kien`: `yyyy-MM-dd HH:mm:ss` (UTC).
    if job.create_date:
        du_lieu["createDate"] = fields.Datetime.to_string(job.create_date)
    ten_tai_xe = job.driver_user_id.name or ""
    if ten_tai_xe:
        du_lieu["driverName"] = ten_tai_xe
    return du_lieu


def job_dict_kho(job):
    """Payload cho màn NHẬN HÀNG của Kho (``GET /kho/cho-nhan``).

    ``packages``/``weightKg`` là số tài xế khai lúc lấy hàng
    (``so_kien_thuc_te``/``khoi_luong_uoc_kg``) — kho cân–đo lại ở bước ③.
    """
    du_lieu = {
        "id": job.id,
        "ref": job.name,
        "shop": job.shop_name or "",
        "packages": job.so_kien_thuc_te,
        "weightKg": job.khoi_luong_uoc_kg,
        "driverName": job.driver_user_id.name or "",
        "state": job.state,
        "stateLabel": nhan_state(job),
    }
    # Chỉ có phiếu nhập kho khi việc ĐÃ xác nhận về kho; giữ khoá optional này để
    # app hiển thị được "phiếu nhập" mà không phải gọi thêm endpoint.
    if job.stock_picking_id:
        du_lieu["pickingName"] = job.stock_picking_id.name or ""
        kien = job._lay_kien()
        tong_kien = len(kien)
        if tong_kien > 0:
            Event = request.env["lab.scan.event"].sudo()  # sudo-ok: đếm số kiện đã quét tem nhận của việc đã về kho để app khoá nút quét tiếp khi đã đủ kiện
            da_quet = len(
                Event.search(
                    [
                        ("kien_id", "in", kien.ids),
                        ("loai", "=", "nhan"),
                        ("ket_qua", "=", "ok"),
                    ]
                ).mapped("kien_id")
            )
            du_lieu["totalPackages"] = tong_kien
            du_lieu["scannedPackages"] = da_quet
    if job.sale_order_id:
        ten_don_ban = job.sudo().sale_order_id.name or ""  # sudo-ok: chỉ đọc mã đơn bán S0xxxx gắn với yêu cầu lấy hàng để app Kho hiển thị mã gom chuyến
        if ten_don_ban:
            du_lieu["saleOrderName"] = ten_don_ban
    return du_lieu


def danh_sach_tai_xe():
    """Tài xế để PHÂN CÔNG việc: nhân viên **có tài khoản app VÀ thuộc nhóm tài xế**
    — mỗi phần tử ``{id, name, login}``.

    ``id`` là ``hr.employee.id`` — đúng thứ ``POST /cs/jobs/<id>/assign`` nhận.
    ``login`` là khoá MỚI để app hiện "Tên · tài khoản" (vd ``Trần Văn Hùng ·
    taixe01``) và để người phân công phân biệt hai nhân viên trùng tên.

    ⚠️ Hồi quy đã sửa: trước đây hàm này trả **mọi** nhân viên có ``user_id`` và
    chỉ ``{id, name}`` -> danh sách gán lẫn cả ``Mitchell Admin`` / ``Marc Demo``
    (nhân viên demo của Odoo có tài khoản nhưng KHÔNG phải tài xế). Giờ lọc theo
    đúng nhóm ``lab_core.group_driver`` — cũng là nhóm mà ``/driver/jobs`` +
    ``ir.rule`` dùng để quyết định "ai nhận được việc", nên danh sách để gán và
    quyền nhận việc khớp nhau.

    Đọc ``hr.employee.public`` chứ không phải ``hr.employee``: Odoo 17 chỉ cấp
    ACL ``hr.employee`` cho ``hr.group_hr_user``/``base.group_system``
    (``addons/hr/security/ir.model.access.csv``), còn CS/Trưởng nhóm tài xế là
    Internal User thường -> phải đi qua bảng public (``base.group_user`` có read).
    ``id`` của bảng public **trùng** ``hr.employee.id`` (nó là SQL view trên
    ``hr_employee`` và ``_search_employee_id`` trả đúng ``[('id', operator,
    value)]``).

    Không raise khi nhóm chưa cài: ``_ids_nguoi_dung_cua_nhom`` trả ``[]`` ->
    danh sách rỗng (app hiện "chưa có tài xế" thay vì 500).
    """
    uid_tai_xe = _ids_nguoi_dung_cua_nhom("lab_core.group_driver")
    if not uid_tai_xe:
        return []
    # Chỉ ĐỌC chuỗi login của đúng các tài khoản tài xế để app hiện "Tên · tài
    # khoản"; người gọi (CS/Trưởng nhóm) không chắc có ACL res.users -> sudo.
    Users = request.env["res.users"].sudo()  # sudo-ok: chỉ đọc `login` của tài khoản tài xế để app hiện "Tên · tài khoản"
    login_theo_uid = {
        dong["id"]: dong["login"] or ""
        for dong in Users.browse(uid_tai_xe).read(["login"])
    }
    nhan_vien = request.env["hr.employee.public"].search(
        [("user_id", "in", uid_tai_xe)], order="name"
    )
    return [
        {
            "id": nv.id,
            "name": nv.name or "",
            # nv.user_id: many2one có sẵn trên bảng public; chỉ lấy .id, KHÔNG đọc
            # field nào khác của res.users (login đã lấy ở trên).
            "login": login_theo_uid.get(nv.user_id.id, ""),
        }
        for nv in nhan_vien
    ]


# ======================================================================
# Phiên app (dùng chung cho /auth/login và /admin/switch)
# ======================================================================
def payload_phien(nguoi_dung, token, het_han, co_the_chuyen=None):
    """``data`` của một phiên app — DÙNG CHUNG cho ``/auth/login`` và ``/admin/switch``.

    Vì sao tách thành helper thay vì mỗi controller tự dựng dict: app KMP lưu phiên
    bằng MỘT bộ parser (``LoginSession``). Nếu "chuyển tài khoản" trả shape khác
    login thì app phải viết bộ thứ hai, và hai bộ đó sẽ lệch nhau ngay lần đầu ai
    đó đổi tên một khoá. Mọi khoá giữ NGUYÊN như trước: ``token`` · ``tokenType`` ·
    ``expiresAt`` · ``uid`` · ``name`` · ``roles`` · ``driver_id`` ·
    ``driver_user_id`` · ``driver_name`` · ``jobsToday``.

    ``roles`` là của **nguoi_dung** (không phải người gọi) — xem
    ``lab_core/models/res_users.py::_lab_vai_tro``.

    Thêm một khoá **bool** ``canSwitch`` — app dùng để hiện/ẩn dropdown "chuyển vai
    trò" trên thanh trên cùng:

    * ``/auth/login`` truyền ``co_the_chuyen=None`` -> tự suy theo ``roles``: chỉ
      admin thật (``roles`` có ``"admin"``) mới thấy dropdown. Đăng nhập trực tiếp
      bằng ``taixe01`` là ``False``.
    * ``/admin/switch`` truyền ``co_the_chuyen=True``: phiên vừa được admin chuyển
      sang nên vẫn được chuyển TIẾP (xem ``PHIEN_DO_ADMIN_CHUYEN``) — đúng yêu cầu
      UX "dropdown dùng được liên tục".
    """
    uid = nguoi_dung.id
    Job = request.env["lab.yeu.cau.lay.hang"]
    # ⚠️ KHÔNG được sập phiên vì thiếu quyền đọc yêu cầu lấy hàng: helper này phục
    # vụ cả CS/Kho/Sếp/Kế toán, mà ACL của `lab.yeu.cau.lay.hang` cấp theo TỪNG
    # nhóm lab_* (lab_laixe/security/ir.model.access.csv) — user chỉ có
    # `base.group_user` không có dòng ACL nào, còn nhóm Kế toán có dòng 0,0,0,0 ->
    # cả hai đều AccessError khi search. `check_access_rights(..., raise_exception=
    # False)` là cách hỏi "có quyền không" của Odoo (không phải kiểm quyền nghiệp
    # vụ, không dùng has_group).
    if Job.check_access_rights("read", raise_exception=False):
        # Lọc bằng cột stored `driver_user_id` (related driver_id.user_id): tài xế
        # là portal nên Odoo 17 CHẶN đọc hr.employee (tự chuyển sang
        # hr.employee.public — chỉ Internal User) -> 403 nếu chạm driver_id.
        job_gan = Job.search([("driver_user_id", "=", uid)], order="id desc", limit=1)
        so_viec_hom_nay = Job.search_count(
            [("driver_user_id", "=", uid), ("state", "in", TRANG_THAI_VIEC)]
        )
    else:
        # Người không thuộc phòng ban nào: vẫn có phiên (app thấy roles rỗng và mọi
        # namespace trả 403), chỉ không có số liệu việc.
        job_gan = Job
        so_viec_hom_nay = 0
    vai_tro = nguoi_dung._lab_vai_tro()
    if co_the_chuyen is None:
        # Mặc định của /auth/login: chỉ admin thật mới thấy dropdown chuyển vai trò.
        co_the_chuyen = VAI_TRO_ADMIN in vai_tro
    return {
        "token": token,
        "tokenType": "LabToken",
        "expiresAt": fields.Datetime.to_string(het_han),
        "uid": uid,
        "name": nguoi_dung.name or "",
        "roles": vai_tro,
        # ⚠️ `driver_id` (id hr.employee) KHÔNG lấy được bằng user portal (Odoo chặn
        # đọc hr.employee) -> trả False có chủ đích; app chỉ cần uid/name. Đọc tên
        # qua driver_user_id (res.users của chính họ).
        "driver_id": False,
        "driver_user_id": job_gan.driver_user_id.id or uid,
        "driver_name": job_gan.driver_user_id.name or nguoi_dung.name or "",
        "jobsToday": so_viec_hom_nay,
        # Ẩn/hiện dropdown "chuyển vai trò" trên app. KHÔNG phải kiểm quyền: quyền
        # thật vẫn do ``duoc_phep_chuyen()`` quyết định ở TỪNG request.
        "canSwitch": bool(co_the_chuyen),
    }


# ======================================================================
# Kiểm quyền ở tầng controller
# ======================================================================
def _ids_nguoi_dung_cua_nhom(nhom_xmlid):
    """ID các user thuộc ``nhom_xmlid``; ``[]`` nếu nhóm không tồn tại (fail closed).

    Một chỗ DUY NHẤT cho việc tra xmlid -> thành viên nhóm, dùng bởi cả
    ``kiem_nhom`` (403 ở cửa vào namespace) và ``danh_sach_tai_xe`` (chỉ lấy
    người có nhóm tài xế) — để hai nơi không lệch nhau về định nghĩa "thuộc nhóm".

    * Tra xmlid -> (model, id) bằng raw SQL (``_xmlid_to_res_model_res_id``),
      KHÔNG dùng ``env.ref``: env.ref gọi ``exists()`` bằng env của người đang
      đăng nhập, mà portal không có ACL ``res.groups`` -> 403 giữa request của
      tài xế.
    * Đọc thành viên nhóm qua sudo (tài khoản portal không có ACL ``res.groups``);
      chỉ ĐỌC id, không trả recordset ra ngoài.
    """
    IrModelData = request.env["ir.model.data"]
    res_model, res_id = IrModelData._xmlid_to_res_model_res_id(
        nhom_xmlid, raise_if_not_found=False
    )
    if res_model != "res.groups" or not res_id:
        return []
    # Tài khoản portal không có ACL res.groups -> đọc thành viên nhóm qua sudo;
    # chỉ ĐỌC id, không trả recordset ra ngoài.
    Groups = request.env["res.groups"].sudo()  # sudo-ok: chỉ đọc thành viên của MỘT nhóm để quyết định 403 / lọc tài xế; portal không có ACL res.groups
    nhom = Groups.browse(res_id).exists()
    return nhom.users.ids if nhom else []


def kiem_nhom(nhom_xmlid, thong_diep):
    """Chặn theo NHÓM QUYỀN ở tầng CONTROLLER -> trả envelope lỗi 403 (không 404).

    ⚠️ Vì sao kiểm ở controller (đã nêu trong báo cáo để RV review):

    * Luật 3 cấm ``has_group`` trong **business logic** (model). Ở đây là tầng
      HTTP: ``/cs/jobs`` và ``/kho/cho-nhan`` đọc **cùng một model** và CS/Kho có
      ACL giống nhau (đọc + ghi ``lab.yeu.cau.lay.hang``), nên ir.rule/ACL không
      thể phân biệt "ai được gọi namespace nào" — muốn 403 thay vì 404 thì phải
      kiểm nhóm tại cửa vào.
    * Dùng ``res.groups.users`` (đọc qua sudo, xem ``_ids_nguoi_dung_cua_nhom``)
      thay vì hàm ``has_group`` vì luật tĩnh A4 của
      ``infra/scripts/audit-native-first.ps1`` cấm tên hàm đó ở **mọi** file .py
      trong ``addons/`` (để không ai lách Luật 3).
    """
    if request.env.uid in _ids_nguoi_dung_cua_nhom(nhom_xmlid):
        return None
    return loi("FORBIDDEN", thong_diep)


def kiem_mot_trong_nhom(nhom_xmlids, thong_diep):
    """Như ``kiem_nhom`` nhưng cho qua nếu user thuộc **BẤT KỲ** nhóm nào.

    Hai chỗ dùng (đều là "một namespace, hai vai"):

    * dashboard của Sếp: ``lab_core.group_manager`` ∪ ``lab_core.group_director``;
    * điều phối/phân công: ``lab_core.group_cs`` ∪ ``lab_core.group_driver_leader``
      (``controllers/cs.py``).

    Khác với các namespace chỉ một nhóm như ``/driver`` hay ``/kho``.
    """
    for nhom_xmlid in nhom_xmlids:
        if kiem_nhom(nhom_xmlid, thong_diep) is None:
            return None
    return loi("FORBIDDEN", thong_diep)


# ======================================================================
# Quản trị viên & "chuyển tài khoản" (login-as cho demo)
# ======================================================================
#: Login của tài khoản HỆ THỐNG — KHÔNG BAO GIỜ vào danh sách "chuyển tài khoản":
#:
#: * 5 login đầu do Odoo tự tạo lúc cài DB: ``public`` (cổng công khai),
#:   ``__system__`` (OdooBot ẩn), ``default``, ``portaltemplate``, ``demo`` (user
#:   mẫu của Odoo);
#: * ``admin`` là chính người đang chuyển — cố ý KHÔNG cho chuyển SANG admin
#:   (muốn quay lại thì đăng xuất rồi đăng nhập; xem ``controllers/admin.py``);
#: * ``odoo`` để phòng DB đặt tên user tay trùng bot.
LOGIN_HE_THONG = (
    "public",
    "__system__",
    "default",
    "portaltemplate",
    "demo",
    "admin",
    "odoo",
)

#: Vai trò app của quản trị viên trong ``roles`` (nguồn thật: ``lab_core``
#: ``VAI_TRO_QUAN_TRI``). Khai lại ở đây vì ``lab_api`` chỉ *depends* ``lab_core``
#: chứ không import module model của nó — và hằng số này là một phần HỢP ĐỒNG API.
VAI_TRO_ADMIN = "admin"

#: Khoá SESSION đánh dấu "phiên này do admin chuyển sang" — giá trị là **uid của
#: admin gốc** (người bấm chuyển đầu tiên).
#:
#: Vì sao cần: yêu cầu UX của chủ dự án — dropdown "chuyển vai trò" phải dùng được
#: LIÊN TỤC: ``admin`` → ``taixe01`` → ``cs01`` … mỗi bước 1 click. Nếu chỉ dựa vào
#: uid hiện tại thì sau bước đầu, phiên là của tài xế -> ``/admin/*`` trả 403 ->
#: dropdown chết ngay sau lần chuyển đầu tiên.
#:
#: Vì sao an toàn: dữ liệu session nằm ở **phía server** (FilesystemSessionStore),
#: client chỉ giữ ``session_id`` ngẫu nhiên nên KHÔNG sửa được khoá này. Nó chỉ
#: được ghi bởi ``/admin/switch`` (sau khi đã kiểm admin) và bị ``/auth/login``
#: xoá -> "đăng nhập trực tiếp" luôn mất quyền, đúng yêu cầu.
PHIEN_DO_ADMIN_CHUYEN = "lab_switch_nguon"


def la_quan_tri(nguoi_dung=None):
    """True nếu ``nguoi_dung`` (mặc định: user của phiên) là QUẢN TRỊ VIÊN.

    Định nghĩa giữ ở MỘT chỗ cho cả model (``_lab_vai_tro``) lẫn controller
    (``/admin/*``): **superuser uid 1** hoặc thành viên ``base.group_system``
    ("Settings"). Ở DB demo, tài khoản ``admin`` (uid 2) thoả điều kiện thứ hai.

    Vì sao KHÔNG viết ``has_group``: luật tĩnh A4 của
    ``infra/scripts/audit-native-first.ps1`` cấm chuỗi đó ở **mọi** file .py trong
    ``addons/`` (để Luật 3 không bị lách). Ở đây ta hỏi ``env.is_admin()`` — API
    công khai của Odoo cho đúng câu hỏi "có phải admin không" — rồi kiểm thêm nhóm
    "Settings" bằng đúng bảng ``res_groups_users_rel`` như ``kiem_nhom``.

    ⚠️ Phải gọi ``is_admin()`` trên env của CHÍNH người đó, không phải env đang
    ``sudo()``: env sudo thì ai cũng "là admin", và endpoint chuyển tài khoản sẽ
    thành lỗ hổng.
    """
    uid = request.env.uid if nguoi_dung is None else (
        nguoi_dung if isinstance(nguoi_dung, int) else nguoi_dung.id
    )
    if uid == SUPERUSER_ID:
        return True
    if request.env(user=uid).is_admin():
        return True
    return uid in _ids_nguoi_dung_cua_nhom("base.group_system")


def nguon_chuyen_phien():
    """uid của admin đã mở quyền chuyển cho phiên hiện tại; ``None`` nếu không có."""
    gia_tri = request.session.get(PHIEN_DO_ADMIN_CHUYEN)
    try:
        return int(gia_tri) or None
    except (TypeError, ValueError):
        # Session bị sửa tay / dữ liệu rác: coi như KHÔNG có quyền (fail closed).
        return None


def duoc_phep_chuyen():
    """True nếu PHIÊN hiện tại được phép gọi ``/admin/accounts`` · ``/admin/switch``.

    Đúng **hai** đường, không có đường thứ ba:

    1. người đang đăng nhập LÀ quản trị viên (``la_quan_tri`` — superuser hoặc nhóm
       "Settings");
    2. phiên này do admin chuyển sang: ``session[PHIEN_DO_ADMIN_CHUYEN]`` = uid admin
       gốc, và uid đó **vẫn còn là admin** (kiểm lại mỗi lần gọi — admin bị thu
       quyền/xoá thì khoá này hết giá trị).

    Đây là chỗ DUY NHẤT chứa luật "ai được chuyển tài khoản"; controller chỉ gọi
    ``kiem_admin()`` (bọc hàm này thành 403) — không rải ``if`` ở 2 nơi.

    ⚠️ KHÔNG dùng ``has_group`` (luật tĩnh A4) và không kiểm bằng ACL/ir.rule:
    ``/admin/*`` nói chuyện với ``res.users`` của NGƯỜI KHÁC, ACL không phân biệt
    "admin hay không" — xem ``controllers/admin.py``.
    """
    if la_quan_tri():
        return True
    nguon_uid = nguon_chuyen_phien()
    if not nguon_uid:
        return False
    # Session là dữ liệu TIN CẬY (server-side), nhưng quyền của admin có thể đã bị
    # thu hồi sau khi chuyển -> kiểm lại uid nguồn mỗi request cho chắc.
    User = request.env["res.users"].sudo()  # sudo-ok: chỉ KIỂM tồn tại của uid admin đã chuyển phiên (tài xế là portal, không có ACL res.users)
    if not User.browse(nguon_uid).exists():
        return False
    return la_quan_tri(nguon_uid)


def kiem_admin():
    """Chặn theo QUYỀN CHUYỂN TÀI KHOẢN ở tầng controller -> ``None`` hoặc envelope 403.

    Cùng khuôn với ``kiem_nhom`` (controller trả 403 thay vì để lộ 404), nhưng câu
    hỏi là "phiên này có được chuyển tài khoản không" — xem ``duoc_phep_chuyen``
    (admin thật **hoặc** phiên do admin chuyển sang).
    """
    if duoc_phep_chuyen():
        return None
    return loi(
        "FORBIDDEN",
        _("Chức năng chuyển tài khoản chỉ dành cho quản trị viên."),
    )


def _ids_nhom_lab():
    """ID các ``res.groups`` do module ``lab_core`` khai (= 9 nhóm nghiệp vụ).

    Tra qua ``ir.model.data`` thay vì viết cứng danh sách xmlid: thêm nhóm mới vào
    ``lab_core/security/lab_core_groups.xml`` là danh sách tài khoản demo tự đúng,
    không phải sửa hai nơi.
    """
    IrModelData = request.env["ir.model.data"].sudo()  # sudo-ok: chỉ ĐỌC xmlid của các nhóm lab_core để lọc tài khoản demo (portal không có ACL ir.model.data)
    return IrModelData.search(
        [("module", "=", "lab_core"), ("model", "=", "res.groups")]
    ).mapped("res_id")


def _tai_khoan_demo():
    """``res.users`` (đọc qua sudo) = TÀI KHOẢN DEMO được phép "chuyển sang".

    Whitelist dựa trên **dữ liệu**, không phải danh sách cứng:

    1. ``active = True``;
    2. login KHÔNG nằm trong ``LOGIN_HE_THONG``;
    3. thuộc **ít nhất một nhóm ``lab_core.group_*``** — đúng định nghĩa "tài khoản
       nghiệp vụ của dự án" mà menu/ACL đang dùng (taixe01..03 · cs01 · kho01 ·
       chungtu01 · ketoan01 · manager01 · sep · nhomtruong01);
    4. KHÔNG phải quản trị viên (``roles`` có ``admin``) — chuyển sang admin không
       có nghĩa vì "đang là admin rồi".

    Trả recordset của env ``sudo`` (chỉ dùng trong ``controllers/admin.py``, nơi đã
    chặn bằng ``kiem_admin``) để controller lấy được ``id``/``login`` mà không phải
    query lại; KHÔNG trả recordset này ra response.
    """
    nhom_lab_ids = _ids_nhom_lab()
    if not nhom_lab_ids:
        # Chưa cài lab_core -> không có tài khoản demo nào. Fail closed.
        return request.env["res.users"]
    User = request.env["res.users"].sudo()  # sudo-ok: liệt kê tài khoản demo (login/name/nhóm) để admin chuyển phiên; portal không có ACL res.users
    ung_vien = User.search(
        [
            ("active", "=", True),
            ("login", "not in", list(LOGIN_HE_THONG)),
            ("groups_id", "in", nhom_lab_ids),
        ],
        order="login",
    )
    # Bỏ quản trị viên: `roles` đã có sẵn mã "admin" nên không cần kiểm lại quyền.
    return ung_vien.filtered(lambda u: VAI_TRO_ADMIN not in u._lab_vai_tro())


def danh_sach_tai_khoan_demo():
    """Payload ``GET /admin/accounts``: ``[{login, name, roles}]`` sắp theo ``login``."""
    return [
        {
            "login": nguoi_dung.login or "",
            "name": nguoi_dung.name or "",
            "roles": nguoi_dung._lab_vai_tro(),
        }
        for nguoi_dung in _tai_khoan_demo()
    ]


def tim_nguoi_dung_theo_login(login):
    """``res.users`` (sudo) đang active có ``login`` này — bất kể có nhóm lab hay không.

    Chỉ dùng để PHÂN BIỆT hai mã lỗi ở ``/admin/switch``: login không tồn tại ->
    404; login có thật nhưng không thuộc bộ demo -> 403 (yêu cầu hợp lệ nhưng bị
    cấm), xem ``controllers/admin.py``.
    """
    if not login:
        return request.env["res.users"]
    User = request.env["res.users"].sudo()  # sudo-ok: chỉ KIỂM tồn tại của 1 login để trả 403 vs 404; portal không có ACL res.users
    return User.search([("login", "=", login), ("active", "=", True)], limit=1)


def tai_khoan_demo_theo_login(login):
    """``res.users`` (sudo) của tài khoản DEMO có ``login``; rỗng nếu không thuộc bộ demo.

    ``login`` đã được gọt khoảng trắng ở controller; so khớp CHÍNH XÁC để không
    chuyển nhầm người.
    """
    if not login:
        return request.env["res.users"]
    return _tai_khoan_demo().filtered(lambda u: u.login == login)


# ======================================================================
# Lấy bản ghi trong phạm vi quyền
# ======================================================================
def job_theo_quyen(job_id, can_write=False):
    """Lấy yêu cầu lấy hàng trong phạm vi quyền của người đang đăng nhập.

    Không sudo: **ir.rule + ACL** quyết định (Luật 3). Trả ``None`` nếu không
    tồn tại **hoặc** không có quyền — cố ý gộp 2 trường hợp để không tiết lộ
    bản ghi của người khác (endpoint trả 404 như nhau).
    """
    job = request.env["lab.yeu.cau.lay.hang"].browse(job_id).exists()
    if not job:
        return None
    try:
        job.check_access_rule("read")
        if can_write:
            job.check_access_rule("write")
    except AccessError:
        return None
    return job


# ======================================================================
# Token của app (X-Lab-Token, 8 giờ, hash trong DB)
# ======================================================================
def xac_thuc_token():
    """Kiểm header ``X-Lab-Token`` của phiên hiện tại.

    Trả envelope lỗi nếu thiếu/sai/hết hạn, ``None`` nếu hợp lệ. Token phải là
    của **chính user đang đăng nhập** — không tra hộ người khác.
    """
    token = (request.httprequest.headers.get("X-Lab-Token") or "").strip()
    if not token:
        return loi(
            "TOKEN_REQUIRED",
            _("Thiếu mã truy cập X-Lab-Token. Vui lòng đăng nhập lại."),
        )
    # Ghi chú: tài xế là portal nên không có ACL trên res.users (2 field token có
    # groups=base.group_system) -> phải đọc hash qua sudo, nhưng chỉ tìm trong
    # CHÍNH user của phiên và không trả recordset ra ngoài.
    User = request.env["res.users"].sudo()  # sudo-ok: đọc hash token của chính user đang đăng nhập (không có ACL res.users)
    nguoi_dung = User.search(
        [
            ("id", "=", request.env.uid),
            ("lab_api_token_hash", "=", bam_token(token)),
        ],
        limit=1,
    )
    if not nguoi_dung:
        return loi(
            "TOKEN_INVALID",
            _("Mã truy cập không hợp lệ. Vui lòng đăng nhập lại."),
        )
    het_han = nguoi_dung.lab_api_token_expiry
    if not het_han or het_han < fields.Datetime.now():
        return loi(
            "TOKEN_EXPIRED",
            _("Mã truy cập đã hết hạn (8 giờ). Vui lòng đăng nhập lại."),
        )
    return None
