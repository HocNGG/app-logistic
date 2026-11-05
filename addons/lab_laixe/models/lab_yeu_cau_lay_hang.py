# -*- coding: utf-8 -*-
import logging
from datetime import datetime, time as gio_ngay, timedelta

import pytz

from odoo import _, api, fields, models
from odoo.exceptions import UserError

_logger = logging.getLogger(__name__)

# ======================================================================
# Dashboard của Sếp — MỘT bản tính, HAI cửa vào
#
# Phép đếm/ngày/múi giờ nằm ở ĐÂY chứ không nằm ở controller: controller
# ``lab_api`` chỉ lo token + nhóm + rate limit rồi gọi
# ``lab_dashboard_kpi``. Nhờ vậy giao diện OWL (gọi RPC, không qua HTTP token)
# và ``GET /lab/api/v1/sep/kpi`` trả **y hệt** một payload — sửa cách tính ngày
# chỉ phải sửa một file, và không thể có chuyện 2 cửa vào lệch số.
# ======================================================================

#: Số ngày của từng ``range`` (giá trị mặc định: ``7d``).
RANGE_NGAY = {"today": 1, "7d": 7, "30d": 30}
RANGE_MAC_DINH = "7d"

#: Trạng thái "đã gán cho tài xế" (gồm cả đang trên đường tới shop).
TRANG_THAI_DANG_DI = ("assigned", "en_route")

#: ``lab.chuyen`` đang mở = còn gom hàng / đã chốt booking nhưng CHƯA khởi hành.
#: (``draft`` là nháp chưa gom, ``departed`` trở đi thì không còn nhận thêm hàng.)
TRANG_THAI_CHUYEN_MO = ("open", "booking_confirmed")

#: Số việc trong bảng "hoạt động gần đây".
SO_VIEC_GAN_DAY = 10


def _mui_gio(env):
    """Múi giờ để cắt ngày: tz của người dùng -> tz công ty -> UTC."""
    return env.user.tz or env.company.partner_id.tz or "UTC"


def _moc_ngay_utc(ngay, mui_gio):
    """Ngày **địa phương** -> ``(đầu ngày, đầu ngày kế tiếp)`` ở UTC (naive).

    Odoo lưu ``Datetime`` bằng UTC naive, nên so sánh trực tiếp với 2 mốc này
    là đúng múi giờ, không cần hàm đổi ngược của ``context_timestamp``.
    """
    gio = pytz.timezone(mui_gio)
    dau = gio.localize(datetime.combine(ngay, gio_ngay.min)).astimezone(pytz.UTC)
    cuoi = gio.localize(datetime.combine(ngay + timedelta(days=1), gio_ngay.min))
    return (
        dau.replace(tzinfo=None),
        cuoi.astimezone(pytz.UTC).replace(tzinfo=None),
    )


def _cac_ngay(so_ngay, hom_nay):
    """Các ngày **liên tục** kết thúc ở ``hom_nay`` (kể cả ngày không có dữ liệu).

    Tách khỏi method vì tham số của method tên ``range`` (nó che builtin
    ``range`` — dùng builtin trong method là TypeError thật).
    """
    return [hom_nay - timedelta(days=so_ngay - 1 - i) for i in range(so_ngay)]


def _dem_trong_ngay(model, ten_field, dau, cuoi):
    """Đếm bản ghi có ``ten_field`` nằm trong ``[dau, cuoi)`` (UTC).

    Dùng ``_read_group`` (Luật 2: 17.0 không dùng ``read_group``) với đúng MỘT
    aggregate ``__count``; không group theo ngày để tránh lệch múi giờ (xem
    docstring ``lab_dashboard_kpi``).
    """
    ket_qua = model._read_group(
        [(ten_field, ">=", dau), (ten_field, "<", cuoi)], [], ["__count"]
    )
    return ket_qua[0][0] if ket_qua else 0


def _thoi_diem_gan_nhat(gia_tri_utc, mui_gio):
    """``datetime`` UTC -> chuỗi ``"YYYY-MM-DD HH:mm"`` theo múi giờ người dùng."""
    if not gia_tri_utc:
        return None
    gio = pytz.timezone(mui_gio)
    return pytz.UTC.localize(gia_tri_utc).astimezone(gio).strftime("%Y-%m-%d %H:%M")


def _gio_phut(record, gia_tri):
    """``datetime`` UTC -> ``"HH:mm"`` theo múi giờ người dùng (cho ``recent``).

    Không import ``lab_api.tools.gio_phut``: ``lab_api`` depends ``lab_laixe`` nên
    import ngược là vòng lặp module. Đây chỉ là 3 dòng ĐỊNH DẠNG; toàn bộ phép
    *đếm* ngày/múi giờ (phần dễ sai làm lệch trục chart) chỉ có MỘT bản là
    ``_dem_trong_ngay`` ở trên.
    """
    if not gia_tri:
        return None
    return fields.Datetime.context_timestamp(record, gia_tri).strftime("%H:%M")


class LabYeuCauLayHang(models.Model):
    """Yêu cầu lấy hàng tận nơi (bước ②).

    Nguồn: docs/02-kien-truc.md §2.3 (field) + §3.2 (state machine) + docs/07
    nhánh "khách tự mang tới kho" (không tạo record).

    Phạm vi P0: chỉ KHAI field + state. State machine (new -> assigned ->
    en_route -> picked_up -> confirmed, sinh stock.picking khi confirmed) và 4
    endpoint cho app tài xế là P3 (P3-01/P3-02/P3-03).

    Tái sử dụng native (Luật 1):
    * tài xế  = ``hr.employee`` (không tạo model tai_xe)
    * xe      = ``fleet.vehicle`` (không tạo model xe)
    * ảnh     = ``ir.attachment`` (không tạo model ảnh)
    * chữ ký  = ``fields.Binary(attachment=True)`` — vẽ trong app KMP, KHÔNG dùng
      module ``sign`` (Enterprise, docs/01 §3.3)
    """

    _name = "lab.yeu.cau.lay.hang"
    _description = "Yêu cầu lấy hàng tận nơi"
    _order = "id desc"
    _inherit = ["mail.thread", "mail.activity.mixin", "lab.flow.mixin"]

    # ------------------------------------------------------------------
    # Định danh & khách hàng
    # ------------------------------------------------------------------
    name = fields.Char(
        string="Số yêu cầu",
        required=True,
        readonly=True,
        copy=False,
        index=True,
        default=lambda self: _("Mới"),
    )
    partner_id = fields.Many2one(
        comodel_name="res.partner",
        string="Shop (khách hàng)",
        required=True,
        index=True,
    )
    shop_name = fields.Char(
        string="Tên shop",
        related="partner_id.name",
        store=True,
        readonly=True,
        help="Bản sao tên shop để app tài xế đọc được: tài xế là portal nên "
             "ir.rule của res.partner chỉ cho thấy partner của chính mình "
             "(API /driver/jobs cần tên shop — xem controllers/driver.py).",
    )
    sale_order_id = fields.Many2one(
        comodel_name="sale.order",
        string="Đơn hàng",
        help="Có thể để trống: yêu cầu lấy hàng được tạo TRƯỚC khi có đơn.",
    )

    # ------------------------------------------------------------------
    # KÊNH NHẬN HÀNG (N13, chốt 2026-11-02)
    # ------------------------------------------------------------------
    kenh_nhan = fields.Selection(
        selection=[
            ("tai_shop", "Tài xế tới shop lấy"),
            ("tai_kho", "Khách tự mang tới kho"),
        ],
        string="Kênh nhận hàng",
        required=True,
        default="tai_shop",
        tracking=True,
        help="tai_shop: tài xế tới shop lấy (luồng ③④⑤). tai_kho: khách tự mang tới "
             "quầy — kho bấm 'Nhận tại quầy', BỎ QUA 3 bước tài xế, sinh WH/IN ngay.",
    )
    nguoi_giao_ten = fields.Char(
        string="Người giao hàng",
        help="Chỉ dùng cho kênh 'Khách tự mang tới kho': tên người mang hàng tới quầy.",
    )
    nguoi_giao_sdt = fields.Char(
        string="SĐT người giao",
        help="Chỉ dùng cho kênh 'Khách tự mang tới kho'.",
    )
    gio_nhan_tai_kho = fields.Datetime(
        string="Nhận tại kho lúc",
        readonly=True,
        copy=False,
        help="Mốc kho bấm 'Nhận tại quầy' (kênh khách tự mang tới).",
    )

    # ------------------------------------------------------------------
    # Điểm lấy hàng
    # ------------------------------------------------------------------
    # ⚠ TỰ ĐIỀN TỪ KHÁCH, CHO PHÉP SỬA (yêu cầu 2026-11-04). Idiom Odoo 17 cho "mặc định
    # theo field khác nhưng người dùng vẫn sửa được": ``compute`` + ``store=True`` +
    # ``readonly=False`` (giống ``sale.order.partner_invoice_id``). Không dùng ``@api.onchange``
    # vì onchange chỉ chạy ở form web — còn compute chạy cả khi tạo bằng code/API.
    # Là ẢNH CHỤP lúc chọn khách: sửa res.partner sau đó KHÔNG đổi yêu cầu đã tạo.
    dia_chi_lay = fields.Text(
        string="Địa chỉ lấy hàng",
        compute="_compute_dia_chi_lay",
        store=True,
        readonly=False,
        help="Tự điền từ địa chỉ của khách khi chọn khách; sửa tự do nếu lấy ở nơi khác.",
    )
    lien_he_ten = fields.Char(
        string="Người liên hệ",
        compute="_compute_lien_he_ten",
        store=True,
        readonly=False,
        help="Tự điền từ liên hệ của khách khi chọn khách; sửa tự do.",
    )
    lien_he_sdt = fields.Char(
        string="Số điện thoại liên hệ",
        compute="_compute_lien_he_sdt",
        store=True,
        readonly=False,
        help="Tự điền từ số điện thoại của khách khi chọn khách; sửa tự do.",
    )
    khung_gio_tu = fields.Datetime(string="Khung giờ lấy — từ")
    khung_gio_den = fields.Datetime(string="Khung giờ lấy — đến")

    # ------------------------------------------------------------------
    # Điều phối
    # ------------------------------------------------------------------
    driver_id = fields.Many2one(
        comodel_name="hr.employee",
        string="Tài xế",
        index=True,
        ondelete="restrict",
        help="hr.employee của tài xế; quyền xem dữ liệu do ir.rule quyết định "
             "(tài xế chỉ thấy yêu cầu của mình).",
    )
    driver_user_id = fields.Many2one(
        comodel_name="res.users",
        string="Tài khoản app của tài xế",
        related="driver_id.user_id",
        store=True,
        index=True,
        readonly=True,
        help="Bản sao (stored) của driver_id.user_id để đường API lọc/so quyền mà "
             "KHÔNG phải đọc hr.employee. Lý do thật (gặp khi chạy test 2026-10-28): "
             "tài xế thuộc base.group_portal, Odoo 17 chặn portal đọc hr.employee "
             "(tự chuyển sang hr.employee.public — chỉ Internal User) nên mọi "
             "endpoint chạm driver_id bị 403. driver_id vẫn là nguồn thật; field "
             "này chỉ là cột để lọc/so quyền.",
    )
    vehicle_id = fields.Many2one(
        comodel_name="fleet.vehicle",
        string="Xe",
    )
    state = fields.Selection(
        selection=[
            ("new", "Mới"),
            ("assigned", "Đã gán tài xế"),
            ("en_route", "Đang đến lấy"),
            ("picked_up", "Đã lấy được hàng"),
            ("confirmed", "Đã về kho"),
            ("cancelled", "Đã huỷ"),
            ("failed", "Lấy hàng thất bại"),
        ],
        string="Trạng thái",
        required=True,
        default="new",
        copy=False,
        index=True,
        tracking=True,
    )

    # ------------------------------------------------------------------
    # Bằng chứng tài xế (app KMP) & kết quả
    # ------------------------------------------------------------------
    ngay_giao = fields.Datetime(string="Thời điểm tài xế xác nhận")
    ngay_xac_nhan = fields.Datetime(
        string="Thời điểm hàng về kho",
        readonly=True,
        copy=False,
        help="Ghi khi Kho bấm 'Hàng đã về kho' (action_confirm). Dùng cho KPI "
             "'confirmedToday' của dashboard Sếp (controllers/sep.py). Vì sao không "
             "dùng write_date: write_date đổi ở MỌI lần ghi (kể cả chatter/đổi "
             "tài xế) nên không phải 'ngày confirm'.",
    )
    chu_ky = fields.Binary(
        string="Chữ ký tài xế",
        attachment=True,
        help="PNG chữ ký vẽ trên app KMP (không dùng module sign của Enterprise).",
    )
    anh_ids = fields.One2many(
        comodel_name="ir.attachment",
        inverse_name="lab_lay_hang_id",
        string="Ảnh chụp khi lấy hàng",
    )
    so_kien_thuc_te = fields.Integer(string="Số kiện thực tế")
    khoi_luong_uoc_kg = fields.Float(string="Khối lượng ước tính (kg)", digits=(16, 2))
    stock_picking_id = fields.Many2one(
        comodel_name="stock.picking",
        string="Phiếu nhập kho",
        help="Phiếu nhận hàng sinh ra khi tài xế mang hàng về kho (P3-02).",
    )
    ly_do_that_bai = fields.Text(
        string="Lý do không lấy được",
        help="Bắt buộc khi state = Lấy hàng thất bại.",
    )
    ghi_chu_tai_xe = fields.Text(
        string="Ghi chú của tài xế",
        help="Tài xế nhập trên app KMP lúc giao hàng (hàng dễ vỡ, gửi bảo vệ, "
             "thiếu 1 kiện…). Tối đa 500 ký tự, xem controllers/driver.py::capture.",
    )
    access_token = fields.Char(
        string="Mã truy cập cho app tài xế",
        copy=False,
        help="Token cho app KMP; không log, không trả cho người khác (docs/02 §6.3).",
    )

    # ------------------------------------------------------------------
    # KIỆN & TEM (2026-11-02) — kiện là stock.quant.package, KHÔNG model riêng
    # ------------------------------------------------------------------
    kien_ids = fields.Many2many(
        comodel_name="stock.quant.package",
        string="Kiện của việc này",
        compute="_compute_kien_ids",
        help="Các kiện sinh ra khi Kho bấm 'Nhận hàng & in tem'. Lấy từ dòng hàng "
             "của phiếu nhập (result_package_id) — nguồn sự thật là chứng từ kho, "
             "không phải danh sách tự khai.",
    )
    so_kien_da_tao = fields.Integer(
        string="Số kiện đã tạo",
        compute="_compute_kien_ids",
        help="Đếm kiện đã sinh. Khác so_kien_thuc_te khi việc chưa được kho nhận.",
    )
    so_kien_du_kien = fields.Integer(
        string="Số kiện dự kiến",
        help="Số kiện shop khai lúc đặt đơn. Có field này thì app tài xế mới hiển "
             "thị được 'kiện dự kiến' từ NGUỒN THẬT thay vì tự bịa số (OQ-22). "
             "Để trống (0) khi shop chưa khai — API không trả field khi đó.",
    )
    lab_client_uuid = fields.Char(
        string="Mã chống trùng của app",
        copy=False,
        index=True,
        help="client_uuid do app KMP sinh cho mỗi lần gửi ảnh/chữ ký: gửi lại "
             "(mất mạng, bấm 2 lần) KHÔNG tạo thêm bộ ảnh thứ hai "
             "(agents/review/skills/03-security-audit.md §6).",
    )

    # ==================================================================
    # Tự điền điểm lấy hàng từ khách (sửa được)
    # ==================================================================
    @api.model
    def _thong_tin_lay_hang_cua_khach(self, partner):
        """Trả ``(địa chỉ, người liên hệ, số điện thoại)`` lấy từ ``res.partner``.

        * Địa chỉ  = street, street2, city, tỉnh/thành (bỏ quốc gia — hàng nội địa).
        * Liên hệ  = liên hệ con (``type='contact'``) đầu tiên của khách; khách là cá nhân
          thì chính họ. Khách là công ty mà chưa có liên hệ con → để trống tên (KHÔNG lấy
          tên công ty làm "người liên hệ": đó không phải thông tin mới cho tài xế).
        * SĐT      = di động ưu tiên hơn bàn của người liên hệ, rồi mới tới của khách.

        Thiếu mục nào trả ``False`` mục đó — không bịa giá trị.
        """
        dia_chi = ", ".join(
            phan
            for phan in (
                partner.street,
                partner.street2,
                partner.city,
                partner.state_id.name,
            )
            if phan
        )
        lien_he = partner.child_ids.filtered(lambda c: c.type == "contact").sorted("id")[:1]
        nguoi = lien_he or (partner if not partner.is_company else partner.browse())
        ten = nguoi.name
        sdt = nguoi.mobile or nguoi.phone or partner.mobile or partner.phone
        return dia_chi or False, ten or False, sdt or False

    @api.depends("partner_id")
    def _compute_dia_chi_lay(self):
        self._dien_tu_khach("dia_chi_lay", 0)

    @api.depends("partner_id")
    def _compute_lien_he_ten(self):
        self._dien_tu_khach("lien_he_ten", 1)

    @api.depends("partner_id")
    def _compute_lien_he_sdt(self):
        self._dien_tu_khach("lien_he_sdt", 2)

    def _dien_tu_khach(self, ten_field, vi_tri):
        """Chọn/đổi khách → điền ``ten_field`` từ khách mới.

        MỖI field có hàm compute RIÊNG (không dùng chung một compute cho 3 field): nếu dùng
        chung, người dùng nhập tay một ô lúc tạo thì Odoo coi cả nhóm đã xong và các ô còn
        lại KHÔNG được tự điền (đã gặp thật trong test 2026-11-04).

        Điền ĐÈ lên giá trị cũ khi ĐỔI khách (giá trị cũ thuộc khách cũ). Người dùng sửa tay
        sau đó thì giữ nguyên — compute chỉ chạy lại khi ``partner_id`` đổi.
        Không đụng tới việc đã rời trạng thái "Mới" (đã gán tài xế/đang đi lấy…): đổi khách
        ở giai đoạn đó không được âm thầm đổi điểm lấy hàng của tài xế. Chưa chọn khách →
        giữ nguyên, không xoá chữ người dùng đã gõ.
        """
        for rec in self:
            if not rec.partner_id or rec.state not in (False, "new"):
                # Compute stored BẮT BUỘC gán lại field: gán bằng chính giá trị hiện có.
                rec[ten_field] = rec[ten_field]
                continue
            rec[ten_field] = self._thong_tin_lay_hang_cua_khach(rec.partner_id)[vi_tri]

    # ==================================================================
    # Sequence: LH/26/0001 (docs/02 §5)
    # ==================================================================
    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if not vals.get("name") or vals.get("name") == _("Mới"):
                vals["name"] = (
                    self.env["ir.sequence"].next_by_code("lab.yeu.cau.lay.hang") or _("Mới")
                )
        return super().create(vals_list)

    # ==================================================================
    # State machine — docs/02-kien-truc.md §3.2
    #
    #   new ──assign──> assigned ──tài xế nhận──> en_route ──đến nơi──> picked_up
    #     │                │                          │                     │
    #     │                └──huỷ──> cancelled        └──thất bại──> failed └──về kho──> confirmed
    #     └──(khách tự mang)──> [không tạo record]
    #
    # Quy tắc (docs/02 §3 + agents/be/AGENT.md §7): mỗi action_* chặn state sai
    # bằng UserError tiếng Việt và ghi chatter. KHÔNG kiểm quyền trong các method
    # này — quyền là việc của ir.rule + ACL (Luật 3).
    # ==================================================================
    def message_post(self, **kwargs):
        """Ghi chatter an toàn: lỗi **gửi thông báo** không được phá giao dịch.

        Chỗ chặn là ĐÂY — override ``mail.thread.message_post`` trên model, không
        bọc từng chỗ gọi. Lý do (đọc source odoo/odoo 17.0, không đoán):

        1. ``message_post`` là **cửa ngõ duy nhất** sinh message + notification.
           Mọi đường vào đều đi qua nó: 6 ``action_*`` của model này, ``create()``
           (message "đã tạo"), OCA ``base_tier_validation``
           (``_notify_accepted_reviews`` → ``message_post(subtype_xmlid=...)``),
           và **tracking**.
        2. Tracking ở 17.0 **KHÔNG** chạy bên trong ``write()``:
           ``MailThread.write()`` (addons/mail/models/mail_thread.py) chỉ gọi
           ``_track_prepare(self._fields)`` rồi ``super().write(values)``;
           ``_track_prepare`` đăng ký ``_track_finalize`` vào ``env.cr.precommit``,
           và ``_track_finalize`` chạy lúc flush/precommit (``Cursor.commit()`` →
           ``Cursor.flush()`` → ``precommit.run()``, xem odoo/sql_db.py). Trong
           ``_message_track`` của cùng file: ``if subtype: record.message_post(...)``
           — **ngược lại** ``record._message_log(...)`` (không gửi mail). Model này
           chưa khai ``_track_subtype`` nên hiện đi nhánh ``_message_log``; nhưng
           nếu ai thêm ``_track_subtype`` (hoặc module khác override) thì lỗi sẽ nổ
           ở **precommit — sau khi action_* đã xong**, vẫn rollback cả transaction.
           Bọc tại ``message_post`` chặn được cả hai nhánh, không phụ thuộc việc
           model có subtype hay không.
        3. Lỗi thật đã đo (E2E ``infra/scripts/e2e-capture.ps1``, 2026-10-29):
           user demo **không có email** → ``UserError("Unable to send message,
           please configure the sender's email address.")`` ném ra SAU khi state
           đã ghi → rollback → tài xế đã chụp ảnh/ký/gửi số kiện mà yêu cầu vẫn
           ở ``en_route`` (API trả 409 INVALID_STATE). Điểm raise: bước tính
           author/``email_from`` **bên trong** ``message_post`` —
           ``mail.thread._message_compute_author`` (cùng file) có hẳn cờ
           ``raise_on_email``, và ``mail.message.default_get`` gọi bản
           ``raise_on_email=False`` để KHÔNG raise; tức chỉ đường post mới ném lỗi
           này. Vì vậy bọc ``message_post`` là đúng chỗ, không phải bọc ``write``.

        Chatter là **phụ**, chuyển trạng thái là **chính** → chỉ log warning kèm
        traceback rồi trả recordset rỗng, KHÔNG raise.

        ⛔ Không bọc gì khác: ``write``, ``_chan_state_sai``, ``action_*`` vẫn
        raise ``UserError`` như cũ — nuốt lỗi nghiệp vụ còn tệ hơn bug đang sửa.
        Đánh đổi đã biết: ``AccessError`` của chatter cũng bị nuốt; chấp nhận vì
        quyền ghi bản ghi đã được ACL + ir.rule kiểm TRƯỚC đó (endpoint
        ``_job(can_write=True)``), chatter không phải chốt chặn quyền.
        """
        try:
            return super().message_post(**kwargs)
        except Exception:  # noqa: BLE001 - lỗi gửi mail/chatter không phá giao dịch
            _logger.warning(
                "Không ghi được chatter cho %s (%s). Giao dịch nghiệp vụ vẫn giữ "
                "nguyên — kiểm tra email người gửi (res.users > partner.email).",
                self._name,
                self.name,
                exc_info=True,
            )
            return self.env["mail.message"]

    def _chan_state_sai(self, trang_thai_hop_le, hanh_dong):
        """Chặn 'nhảy cóc' trạng thái. Trả True nếu hợp lệ, raise UserError nếu sai."""
        self.ensure_one()
        if self.state not in trang_thai_hop_le:
            nhan = dict(self._fields["state"].selection).get(self.state, self.state)
            raise UserError(
                _(
                    "Không thể %s: yêu cầu %s đang ở trạng thái \"%s\".",
                    hanh_dong,
                    self.name,
                    nhan,
                )
            )
        return True

    def action_assign(self):
        """Mới -> Đã gán tài xế (CS điều phối)."""
        self.ensure_one()
        self._chan_state_sai(("new",), _("gán tài xế"))
        if not self.driver_id:
            raise UserError(_("Phải chọn tài xế trước khi gán yêu cầu %s.", self.name))
        self.write({"state": "assigned"})
        # KHÔNG đọc self.driver_id.name ở đây: nếu người bấm là tài xế (portal) thì
        # Odoo 17 chặn đọc hr.employee -> 403 giữa luồng. driver_user_id là cột
        # stored trên chính model này nên đọc được với mọi vai trò.
        ten_tai_xe = self.driver_user_id.name or _("tài xế (chưa có tài khoản app)")
        self.message_post(body=_("Gán %s cho yêu cầu lấy hàng.", ten_tai_xe))
        return True

    def action_start(self):
        """Đã gán -> Đang đến lấy (tài xế bấm 'Bắt đầu đi' trên app)."""
        self.ensure_one()
        self._chan_state_sai(("assigned",), _("cho tài xế bắt đầu đi lấy hàng"))
        self.write({"state": "en_route"})
        self.message_post(body=_("Tài xế đã bắt đầu đi lấy hàng."))
        return True

    def action_pick(self):
        """Đang đến lấy -> Đã lấy được hàng (tài xế xác nhận tại shop)."""
        self.ensure_one()
        self._chan_state_sai(("en_route",), _("xác nhận đã lấy được hàng"))
        self.write({"state": "picked_up", "ngay_giao": fields.Datetime.now()})
        self.message_post(
            body=_(
                "Tài xế đã lấy được hàng: %s kiện, %s kg.",
                self.so_kien_thuc_te,
                self.khoi_luong_uoc_kg,
            )
        )
        return True

    def action_confirm(self):
        """Đã lấy được hàng -> Đã về kho + SINH stock.picking (REA: Nhận hàng để gửi).

        Ai bấm: Kho/CS sau khi tài xế mang hàng về. Sự kiện kinh tế "nhận hàng để
        gửi" BẮT BUỘC sinh chứng từ (AGENTS.md Luật 4, docs/02 §11) — ở đây là
        phiếu nhập kho native, không phải model mới.
        """
        self.ensure_one()
        self._chan_state_sai(("picked_up",), _("xác nhận hàng đã về kho"))
        # Chống tạo TRÙNG phiếu nhập. Chỉ chặn khi phiếu đang gắn ĐÚNG là phiếu NHẬP:
        # đo 2026-11-02 trên DB demo có việc đang gắn phiếu XUẤT (WH/OUT) do bộ seed lớn
        # gán bừa ⇒ nếu chặn mọi trường hợp thì kho không nhận hàng được. Phiếu không phải
        # incoming được coi là liên kết cũ và sẽ bị thay bằng phiếu nhập thật.
        if self.stock_picking_id and self.stock_picking_id.picking_type_id.code == "incoming":
            raise UserError(
                _(
                    "Yêu cầu %s đã có phiếu nhập kho %s — không tạo trùng.",
                    self.name,
                    self.stock_picking_id.name,
                )
            )
        if self.stock_picking_id:
            _logger.info(
                "Việc %s đang gắn phiếu %s (không phải phiếu nhập) — thay bằng phiếu nhập mới.",
                self.name,
                self.stock_picking_id.name,
            )
        picking = self._tao_phieu_nhap_kho()
        don_ban = self._dam_bao_don_ban()
        self.write(
            {
                "state": "confirmed",
                "stock_picking_id": picking.id,
                "sale_order_id": don_ban.id,
                # Mốc thời gian THẬT của lần confirm — dashboard cần để đếm
                # "hôm nay nhận được mấy việc" theo múi giờ người dùng.
                "ngay_xac_nhan": fields.Datetime.now(),
            }
        )
        # Validate để TỒN KHO TĂNG (2026-11-02). Nếu lỗi ⇒ raise ⇒ rollback cả transaction,
        # không để lại cảnh "việc đã về kho mà phiếu còn draft".
        self._xac_nhan_phieu_nhap(picking)
        self.message_post(
            body=_(
                "Hàng đã về kho. Sinh phiếu nhập kho %s · Đơn bán %s.",
                picking.name,
                don_ban.name,
            )
        )
        return True

    def _dam_bao_don_ban(self):
        """Đảm bảo yêu cầu đã về kho luôn gắn với một ``sale.order`` ở trạng thái ``sale``.

        Vì sao cần (lỗi thật 2026-11-04 trên ``LH/26/5302`` - shop ``AloBro``):
        Ở Bước 2 CS tạo Yêu cầu lấy hàng khi chưa có đơn bán (``sale_order_id = False``),
        sau đó qua Bước 3–7 tài xế lấy hàng và kho xác nhận về kho + quét tem nhận.
        Đến Bước 8 (màn Chuyến → Đơn hàng trong chuyến), domain của ``sale_order_id`` chỉ
        lấy ``sale.order`` có ``state in ('sale', 'sent')``, và ``lab.chuyen._lay_kien_cua_chuyen``
        cũng truy kiện qua ``lab.yeu.cau.lay.hang.sale_order_id``. Nếu lúc hàng về kho mà
        ``sale_order_id`` vẫn trống (hoặc còn ``draft``) thì CS không thể tìm thấy đơn đó
        trong Chuyến bay.

        Dùng ``sudo()`` vì thủ kho (``group_warehouse``) là người bấm xác nhận hàng về kho
        nhưng không có ACL tạo/sửa ``sale.order`` trực tiếp (khoá bởi
        ``test_d_kho_tao_duoc_yeu_cau_nhung_khong_tao_duoc_don_ban``).
        """
        self.ensure_one()
        SaleOrder = self.env["sale.order"].sudo()  # sudo-ok: tự sinh/xác nhận đơn bán gắn với yêu cầu lấy hàng khi kho xác nhận hàng về kho (thủ kho không có ACL tạo sale.order trực tiếp)
        Product = self.env["product.product"].sudo()  # sudo-ok: đọc sản phẩm dịch vụ cước vận chuyển mặc định để tạo dòng đơn bán
        product = self.env.ref("lab_core.demo_product_cuoc_kg", raise_if_not_found=False)
        if product:
            product = product.sudo()  # sudo-ok: đọc giá niêm yết và tên sản phẩm cước khi chạy dưới quyền thủ kho
        else:
            product = Product.search(
                [("sale_ok", "=", True), ("type", "=", "service")], limit=1
            )
        kl_kg = float(self.khoi_luong_uoc_kg or 0.0) or 1.0

        if self.sale_order_id:
            don_ban = self.sale_order_id.sudo()  # sudo-ok: đồng bộ mã yêu cầu lấy hàng và xác nhận đơn bán sang trạng thái sale khi hàng về kho
            vals_cap_nhat = {}
            if not don_ban.origin:
                vals_cap_nhat["origin"] = self.name
            if "lab_shop_ref" in don_ban._fields and not don_ban.lab_shop_ref:
                vals_cap_nhat["lab_shop_ref"] = self.name
            if vals_cap_nhat:
                don_ban.write(vals_cap_nhat)
            if don_ban.state == "draft":
                if not don_ban.order_line and product:
                    don_ban.write(
                        {
                            "order_line": [
                                (
                                    0,
                                    0,
                                    {
                                        "product_id": product.id,
                                        "name": "%s (%s)" % (product.name, self.name),
                                        "product_uom_qty": kl_kg,
                                        "price_unit": product.list_price or 180000.0,
                                    },
                                )
                            ]
                        }
                    )
                don_ban.action_confirm()
            return self.sale_order_id

        vals_don = {
            "partner_id": self.partner_id.id,
            "origin": self.name,
        }
        if "lab_shop_ref" in SaleOrder._fields:
            vals_don["lab_shop_ref"] = self.name
        if "lab_pickup_mode" in SaleOrder._fields:
            vals_don["lab_pickup_mode"] = (
                "drop_off" if self.kenh_nhan == "tai_kho" else "pickup"
            )
        if product:
            vals_don["order_line"] = [
                (
                    0,
                    0,
                    {
                        "product_id": product.id,
                        "name": "%s (%s)" % (product.name, self.name),
                        "product_uom_qty": kl_kg,
                        "price_unit": product.list_price or 180000.0,
                    },
                )
            ]
        don_ban = SaleOrder.create(vals_don)
        don_ban.action_confirm()
        return self.env["sale.order"].browse(don_ban.id)

    def _tao_phieu_nhap_kho(self):
        """Sinh phiếu nhập kho (stock.picking native) cho hàng tài xế mang về.

        Chọn **kho mặc định của công ty hiện tại** (không phải "loại phiếu đầu
        tiên trong DB"): demo có 3 kho (KHO CHÍNH · KHO BAY · KHO TÀU) nên tìm
        không giới hạn công ty sẽ nhận hàng vào kho bất kỳ. Lọc công ty bằng
        **domain** ``warehouse_id.company_id`` (domain được phép đi qua many2one),
        rồi ưu tiên ``sequence`` của chính loại phiếu — loại phiếu nhập của kho
        chính (tạo đầu tiên khi cài ``stock``) có ``id`` nhỏ nhất nên thắng khi
        ``sequence`` bằng nhau, tức là vẫn về đúng kho chính.

        ⚠️ KHÔNG order theo ``warehouse_id.sequence``: Odoo 17 cấm order bằng
        đường dẫn many2one — ``ValueError: Order a property ('sequence') on a
        non-properties field ('warehouse_id')`` (lỗi thật khi chạy test suite).
        """
        self.ensure_one()
        loai_phieu = self.env["stock.picking.type"].search(
            [
                ("code", "=", "incoming"),
                ("warehouse_id.company_id", "=", self.env.company.id),
            ],
            order="sequence, id",
            limit=1,
        )
        if not loai_phieu:
            raise UserError(
                _(
                    "Chưa cấu hình loại phiếu nhập kho. Vào Kho > Cấu hình > "
                    "Operation Types để tạo loại phiếu có mã 'incoming'."
                )
            )
        vi_tri_giao = loai_phieu.default_location_src_id or self.env.ref(
            "stock.stock_location_suppliers", raise_if_not_found=False
        )
        vi_tri_nhan = loai_phieu.default_location_dest_id
        if not vi_tri_giao or not vi_tri_nhan:
            raise UserError(
                _(
                    "Loại phiếu %s chưa có vị trí nguồn/đích mặc định.",
                    loai_phieu.display_name,
                )
            )
        so_kien = self._so_kien_thuc_nhan()
        picking = self.env["stock.picking"].create(
            {
                "picking_type_id": loai_phieu.id,
                "partner_id": self.partner_id.id,
                "origin": self.name,
                "location_id": vi_tri_giao.id,
                "location_dest_id": vi_tri_nhan.id,
                "scheduled_date": fields.Datetime.now(),
                # Field mở rộng của lab_core (docs/02 §2.1): cân–đo tại kho.
                "lab_weight_kg": self.khoi_luong_uoc_kg,
                "lab_package_count": so_kien,
            }
        )
        kien = self._tao_kien(so_kien)
        self._tao_dong_hang(picking, vi_tri_giao, vi_tri_nhan, kien)
        return picking

    def _so_kien_thuc_nhan(self):
        """Số kiện sẽ tạo khi nhận: **số tài xế đếm là nguồn sự thật**.

        Chỉ khi tài xế CHƯA đếm (0/None) mới rơi về số shop dự kiến, cuối cùng là 1.
        Không dùng max(): số dự kiến thường lớn hơn số thực tế (thiếu kiện) và bảng
        tổng kết sẽ sai — hồi quy thật 2026-11-03 (test 8 != 7).
        """
        self.ensure_one()
        thuc_te = int(self.so_kien_thuc_te or 0)
        du_kien = int(self.so_kien_du_kien or 0)
        return thuc_te or du_kien or 1

    def _tao_kien(self, so_luong):
        """Sinh `so_luong` KIỆN = `stock.quant.package` (native), mã từ sequence `lab.kien`.

        Không tạo model `kien` (AGENTS.md Luật 1 + bảng từ vựng cấm): Odoo đã có
        ``stock.quant.package`` mang đúng ngữ nghĩa "một kiện/thùng".
        Mã kiện dùng cho **cả tem nhận và tem xuất** về sau (một kiện = một mã).
        """
        self.ensure_one()
        Package = self.env["stock.quant.package"]
        sequence = self.env["ir.sequence"]
        kien = Package
        for _i in range(so_luong):
            ma = sequence.next_by_code("lab.kien") or _("KIEN/MOI")
            kien |= Package.create(
                {
                    "name": ma,
                    "company_id": self.env.company.id,
                    "pack_date": fields.Date.context_today(self),
                }
            )
        return kien

    def _tao_dong_hang(self, picking, vi_tri_giao, vi_tri_nhan, kien):
        """Sinh ĐÚNG dòng hàng cho phiếu nhập: 1 move `KIỆN GOM LẺ` × N, N move line.

        VÌ SAO PHẢI CÓ (đo thật 2026-11-02): bản cũ chỉ tạo `stock.picking` mà không có
        `stock.move` ⇒ 3 phiếu WH/IN nằm ở `draft` với **0 dòng hàng** ⇒ **tồn kho không
        tăng**, phiếu mất giá trị kho và không đối soát được với phiếu xuất.
        Mỗi move line mang 1 kiện qua ``result_package_id`` ⇒ kiện đi vào tồn kho thật.
        """
        self.ensure_one()
        product = self.env.ref("lab_core.product_kien_gom_le", raise_if_not_found=False)
        if not product:
            raise UserError(
                _(
                    "Chưa có sản phẩm đại diện KIỆN GOM LẺ (mã KIEN-GOM). "
                    "Nâng cấp module lab_core rồi thử lại.",
                )
            )
        move = self.env["stock.move"].create(
            {
                "name": product.display_name,
                "product_id": product.id,
                "product_uom_qty": len(kien),
                "product_uom": product.uom_id.id,
                "picking_id": picking.id,
                "location_id": vi_tri_giao.id,
                "location_dest_id": vi_tri_nhan.id,
                "company_id": self.env.company.id,
            }
        )
        self.env["stock.move.line"].create(
            [
                {
                    "move_id": move.id,
                    "picking_id": picking.id,
                    "product_id": product.id,
                    "product_uom_id": product.uom_id.id,
                    "quantity": 1,
                    "location_id": vi_tri_giao.id,
                    "location_dest_id": vi_tri_nhan.id,
                    "result_package_id": pkg.id,
                    "company_id": self.env.company.id,
                }
                for pkg in kien
            ]
        )
        picking.action_confirm()
        try:
            picking.action_assign()
        except Exception:  # noqa: BLE001 - nhập từ nhà cung cấp không cần assign
            pass
        move.picked = True
        return move

    def _xac_nhan_phieu_nhap(self, picking):
        """Validate phiếu nhập để TỒN KHO TĂNG. Lỗi ⇒ UserError tiếng Việt, không nửa vời.

        Cả `action_confirm` nằm trong MỘT transaction: nếu validate thất bại thì raise để
        Odoo rollback — không để lại cảnh "việc đã Đã về kho mà phiếu vẫn draft".
        """
        self.ensure_one()
        if picking.state == "done":
            return True
        try:
            ket_qua = picking.button_validate()
        except UserError:
            raise
        except Exception as exc:  # noqa: BLE001 - đổi sang thông báo nghiệp vụ
            _logger.warning("Validate phiếu nhập %s lỗi: %s", picking.name, exc, exc_info=True)
            raise UserError(
                _(
                    "Không xác nhận được phiếu nhập %s: %s",
                    picking.name,
                    exc,
                )
            ) from exc
        if isinstance(ket_qua, dict):
            raise UserError(
                _(
                    "Phiếu nhập %s chưa hoàn tất — còn bước xác nhận tay trong kho "
                    "(ví dụ chuyển ngay/backorder). Mở phiếu và xác nhận rồi thử lại.",
                    picking.name,
                )
            )
        return True

    # ==================================================================
    # KIỆN & TEM NHẬN — chốt 2026-11-02
    # ==================================================================
    @api.depends("stock_picking_id", "stock_picking_id.move_line_ids.result_package_id")
    def _compute_kien_ids(self):
        for rec in self:
            kien = rec._lay_kien()
            rec.kien_ids = kien
            rec.so_kien_da_tao = len(kien)

    def _lay_kien(self):
        """Kiện của việc này = package trên dòng hàng của phiếu nhập (nguồn: chứng từ)."""
        self.ensure_one()
        if not self.stock_picking_id:
            return self.env["stock.quant.package"]
        lines = self.stock_picking_id.move_line_ids
        return (lines.mapped("result_package_id") | lines.mapped("package_id")).exists()

    def _public_base_url(self):
        """Địa chỉ công khai để nhét vào QR trên tem.

        Ưu tiên tham số hệ thống ``lab.public_base_url`` (đặt IP LAN khi demo trên
        máy tính bảng), rồi mới tới ``web.base.url`` của Odoo.
        """
        get_param = self.env["ir.config_parameter"].sudo().get_param  # sudo-ok: ghi/xoá field lab_* + sinh package khi kho nhận hàng (đã có ACL Kho) — xem ADR/kho nhận hàng 2026-11-02
        return (get_param("lab.public_base_url") or get_param("web.base.url") or "").rstrip("/")

    def track_url_kien(self, kien):
        """URL mở trang tra cứu của 1 kiện — dạng PATH (không '?' / '&') để QR sạch."""
        self.ensure_one()
        return "%s/lab/track/%s/%s" % (
            self._public_base_url(),
            self.access_token or "",
            kien.name,
        )

    def action_nhan_va_in_tem(self):
        """MỘT NÚT NHANH (chốt M8): nhận hàng → sinh phiếu + kiện → in tem nhận.

        Không tách 2 bước: kho đứng ở dock, một tay bấm, máy in ra tem ngay.
        """
        self.ensure_one()
        self.action_confirm()
        report = self.env.ref("lab_laixe.action_report_tem_nhan", raise_if_not_found=False)
        if not report:
            return True
        # config=False: xem giải thích ở lab_chuyen.action_in_tem_xuat (tránh wizard layout).
        return report.report_action(self, config=False)

    def action_nhan_tai_quay(self):
        """Kho nhận hàng TẠI QUẦY (kênh khách tự mang tới) — N13/N15, chốt 2026-11-02.

        KHÁC luồng tài xế ở chỗ BỎ 3 bước assigned → en_route → picked_up: khách đã mang
        hàng tới nơi nên nhận luôn `new → confirmed`.

        KHÔNG NHÂN BẢN LOGIC: dùng lại `_tao_phieu_nhap_kho()` (sinh WH/IN + dòng hàng +
        kiện) và `_xac_nhan_phieu_nhap()` (validate ⇒ tồn kho tăng) — đúng một đường với
        `action_confirm`, nên hai kênh không thể lệch chứng từ.
        """
        self.ensure_one()
        if self.kenh_nhan != "tai_kho":
            raise UserError(
                _(
                    "Chỉ nhận tại quầy với yêu cầu có kênh \"Khách tự mang tới kho\" "
                    "(yêu cầu %s đang là kênh tài xế tới shop lấy).",
                    self.name,
                )
            )
        self._chan_state_sai(("new",), _("nhận hàng tại quầy"))
        if int(self.so_kien_thuc_te or 0) <= 0:
            raise UserError(
                _(
                    "Phải nhập \"Số kiện thực tế\" (> 0) trước khi nhận tại quầy — "
                    "đó là số kiện sẽ sinh phiếu nhập và tem.",
                )
            )
        if self.stock_picking_id and self.stock_picking_id.picking_type_id.code == "incoming":
            raise UserError(
                _(
                    "Yêu cầu %s đã có phiếu nhập kho %s — không tạo trùng.",
                    self.name,
                    self.stock_picking_id.name,
                )
            )
        luc = fields.Datetime.now()
        picking = self._tao_phieu_nhap_kho()
        don_ban = self._dam_bao_don_ban()
        self.write(
            {
                "state": "confirmed",
                "stock_picking_id": picking.id,
                "sale_order_id": don_ban.id,
                "ngay_xac_nhan": luc,
                "gio_nhan_tai_kho": luc,
                "ngay_giao": self.ngay_giao or luc,
            }
        )
        self._xac_nhan_phieu_nhap(picking)
        self.message_post(
            body=_(
                "Khách tự mang hàng tới kho. Nhận %(kien)d kiện, sinh phiếu nhập %(phieu)s · Đơn bán %(don)s.",
                kien=int(self.so_kien_thuc_te or 0),
                phieu=picking.name,
                don=don_ban.name,
            )
        )
        report = self.env.ref("lab_laixe.action_report_tem_nhan", raise_if_not_found=False)
        if report:
            return report.report_action(self, config=False)
        return True

    def action_fail(self, ly_do=None):
        """Đã gán / đang đến / đã lấy -> Lấy hàng thất bại (bắt buộc có lý do)."""
        self.ensure_one()
        self._chan_state_sai(
            ("assigned", "en_route", "picked_up"), _("báo không lấy được hàng")
        )
        ly_do = (ly_do or self.ly_do_that_bai or "").strip()
        if not ly_do:
            raise UserError(
                _(
                    "Phải nhập lý do không lấy được hàng (vd: shop đóng cửa, "
                    "hàng chưa đóng gói, sai địa chỉ)."
                )
            )
        self.write({"state": "failed", "ly_do_that_bai": ly_do})
        self.message_post(body=_("Lấy hàng thất bại. Lý do: %s", ly_do))
        return True

    def action_cancel(self):
        """Mới / Đã gán -> Đã huỷ (việc bị huỷ, không phải lỗi tài xế)."""
        self.ensure_one()
        self._chan_state_sai(("new", "assigned"), _("huỷ yêu cầu lấy hàng"))
        self.write({"state": "cancelled"})
        self.message_post(body=_("Huỷ yêu cầu lấy hàng %s.", self.name))
        return True

    # ==================================================================
    # Dashboard của Sếp — payload dùng chung cho OWL RPC + HTTP /sep/kpi
    # ==================================================================
    @api.model
    def lab_dashboard_kpi(self, range="7d"):
        """Số liệu dashboard Sếp — CÙNG payload với ``GET /lab/api/v1/sep/kpi``.

        Giao diện OWL (backend) gọi thẳng qua RPC, không cần HTTP token::

            orm.call("lab.yeu.cau.lay.hang", "lab_dashboard_kpi", ["7d"])

        ``lab_api`` cũng gọi chính hàm này (controller chỉ còn token + nhóm +
        rate limit), vì thế hai cửa vào không thể lệch payload.

        Quyền: chạy bằng env của **người gọi** (KHÔNG sudo) -> ACL + ``ir.rule``
        quyết định; ai không có quyền đọc model này thì Odoo tự raise
        ``AccessError``. KHÔNG kiểm nhóm ở đây (Luật 3): ai được mở màn Sếp là
        việc của menu/tầng HTTP, còn dữ liệu thì ir.rule lọc (tài xế chỉ thấy
        việc của mình).

        Tham số ``range``: ``today`` | ``7d`` | ``30d`` (mặc định ``7d``); giá
        trị khác -> ``UserError`` tiếng Việt (endpoint HTTP đổi thành 400
        ``VALIDATION``).

        Cách tính ngày (quan trọng — chart sai trục là lỗi nhìn thấy được):
        * mọi mốc "hôm nay/7 ngày/30 ngày" tính theo **múi giờ người dùng**
          (``res.users.tz``), không có thì tz công ty, cuối cùng UTC;
        * với mỗi ngày địa phương, quy ra **khoảng UTC nửa mở** ``[đầu ngày,
          đầu ngày kế tiếp)`` bằng ``pytz`` rồi đếm trong khoảng đó — KHÔNG
          group theo ``create_date:day`` của ``_read_group`` vì việc truncate đó
          chạy theo UTC và sẽ dồn nhầm việc tạo lúc 00:00–07:00 giờ VN sang
          ngày hôm trước;
        * đếm bằng ``_read_group(domain, [], ["__count"])`` trên đúng khoảng UTC
          đó.

        Trả về đúng ``data`` của endpoint (hợp đồng với app KMP):
        ``{range, kpi, trend, byState, recent}``.
        """
        chuoi_range = str(range or RANGE_MAC_DINH).strip().lower()
        if chuoi_range not in RANGE_NGAY:
            # Cố ý KHÔNG tự mặc định: FE chỉ có 3 lựa chọn, giá trị lạ là lỗi
            # client — nuốt đi sẽ hiện chart SAI mà không ai biết.
            raise UserError(
                _(
                    "Tham số range không hợp lệ: \"%s\". Chỉ nhận: today, 7d, 30d.",
                    chuoi_range,
                )
            )

        mui_gio = _mui_gio(self.env)
        tzinfo = pytz.timezone(mui_gio)

        # --- Trục ngày của trend: kết thúc ở HÔM NAY (địa phương), liên tục ---
        hom_nay = datetime.now(pytz.UTC).astimezone(tzinfo).date()
        trend = []
        for ngay in _cac_ngay(RANGE_NGAY[chuoi_range], hom_nay):
            dau, cuoi = _moc_ngay_utc(ngay, mui_gio)
            trend.append(
                {
                    "date": ngay.isoformat(),
                    "created": _dem_trong_ngay(self, "create_date", dau, cuoi),
                    "picked": _dem_trong_ngay(self, "ngay_giao", dau, cuoi),
                    "confirmed": _dem_trong_ngay(self, "ngay_xac_nhan", dau, cuoi),
                }
            )

        # --- Cơ cấu theo trạng thái: 1 truy vấn, đủ 7 state kể cả 0 ---
        nhom_theo_state = {
            trang_thai: so_luong
            for trang_thai, so_luong in self._read_group([], ["state"], ["__count"])
        }
        nhan_state = dict(self._fields["state"].selection)
        by_state = [
            {"state": ma, "label": nhan, "count": nhom_theo_state.get(ma, 0)}
            for ma, nhan in self._fields["state"].selection
        ]

        # --- Tỉ lệ tải trung bình của các chuyến đang mở ---------------------
        # ``lab_chuyen`` KHÔNG phải depends của lab_laixe (yêu cầu lấy hàng ở
        # bước ②, chuyến ở bước ⑤) nên chỉ đọc khi model có mặt; cài đủ bộ
        # module của app thì luôn có. Không có quyền đọc -> 0 (endpoint cũ cũng
        # vậy: check_access_rights raise_exception=False).
        avg_load_pct = 0
        if "lab.chuyen" in self.env:
            Chuyen = self.env["lab.chuyen"]
            if Chuyen.check_access_rights("read", raise_exception=False):
                ket_qua = Chuyen._read_group(
                    [("state", "in", TRANG_THAI_CHUYEN_MO)], [], ["ti_le_tai:avg"]
                )
                gia_tri = ket_qua[0][0] if ket_qua else 0
                avg_load_pct = int(round(gia_tri or 0))

        # --- 10 việc mới nhất: chỉ field an toàn (không giá, không id đối tác) ---
        recent = [
            {
                "id": job.id,
                "ref": job.name,
                "shop": job.shop_name or "",
                "stateLabel": nhan_state.get(job.state, job.state),
                "driverName": job.driver_user_id.name or "",
                "windowFrom": _gio_phut(job, job.khung_gio_tu),
                "updatedAt": _thoi_diem_gan_nhat(job.write_date, mui_gio),
            }
            for job in self.search([], order="write_date desc", limit=SO_VIEC_GAN_DAY)
        ]

        # SỐ LIỆU CHUYẾN (thêm 2026-11-03 — khách phản hồi màn "Chuyến" thiếu "số chuyến").
        # Vì sao đặt ở đây: dải KPI trên web gọi CÙNG một method cho mọi màn danh sách; nếu
        # mỗi màn gọi một endpoint riêng thì hai nơi dễ lệch số. Thêm khoá là thay đổi
        # TƯƠNG THÍCH NGƯỢC (app/OWL chỉ đọc khoá nó biết).
        # ⚠️ PHẢI kiểm quyền đọc trước khi đếm: tài xế (portal) KHÔNG có ACL trên
        # `lab.chuyen` → gọi thẳng search_count sẽ ném AccessError và làm `/sep/kpi`
        # của tài xế 500 (đo 2026-11-03). Không có quyền ⇒ trả 0, đúng tinh thần
        # "số nào không được phép thấy thì là 0", KHÔNG dùng sudo (Luật 3).
        chuyen = chuyen_dang_mo = chuyen_da_chot = chuyen_da_khoi_hanh = 0
        Chuyen = self.env["lab.chuyen"] if "lab.chuyen" in self.env else None
        if Chuyen and Chuyen.check_access_rights("read", raise_exception=False):
            chuyen = Chuyen.search_count([])
            chuyen_dang_mo = Chuyen.search_count([("state", "=", "open")])
            chuyen_da_chot = Chuyen.search_count([("state", "=", "booking_confirmed")])
            chuyen_da_khoi_hanh = Chuyen.search_count(
                [("state", "in", ("departed", "arrived", "delivered", "closed"))]
            )

        return {
            "range": chuoi_range,
            "kpi": {
                "pendingAssign": nhom_theo_state.get("new", 0),
                "enRoute": sum(nhom_theo_state.get(ma, 0) for ma in TRANG_THAI_DANG_DI),
                # 2 số "hôm nay" lấy từ chính phần tử cuối của trend để màn hình
                # không bao giờ lệch với chart.
                "pickedUpToday": trend[-1]["picked"],
                "confirmedToday": trend[-1]["confirmed"],
                "avgLoadPct": avg_load_pct,
                # --- khối chuyến (dùng cho dải KPI màn "Chuyến") ---
                "tripsTotal": chuyen,
                "tripsOpen": chuyen_dang_mo,
                "tripsBooked": chuyen_da_chot,
                "tripsDeparted": chuyen_da_khoi_hanh,
            },
            "trend": trend,
            "byState": by_state,
            "recent": recent,
        }
