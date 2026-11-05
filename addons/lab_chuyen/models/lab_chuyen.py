# -*- coding: utf-8 -*-
import logging
import secrets

from odoo import _, api, fields, models
from odoo.exceptions import UserError

_logger = logging.getLogger(__name__)


class LabChuyen(models.Model):
    """Chuyến gom hàng lẻ (chuyến bay / chuyến tàu).

    Nguồn: docs/02-kien-truc.md §2.2 (field) + §3.1 (state machine) + docs/00 §3
    (bước ⑤⑥⑦⑬⑭).

    Phạm vi P0: chỉ KHAI field + state + state-guard tối thiểu. Chưa có:
    tier validation 2 cấp (⑥), wizard giảm tải (nhánh ②), sinh Debit (⑬),
    report manifest (⑧) — các phần đó thuộc P2/P5.
    """

    _name = "lab.chuyen"
    _description = "Chuyến gom hàng lẻ"
    _order = "id desc"
    _inherit = ["mail.thread", "mail.activity.mixin", "lab.flow.mixin", "tier.validation"]

    # ------------------------------------------------------------------
    # Tier validation — bước ⑥ (duyệt đơn vào chuyến trước khi khởi hành)
    #
    # Hợp đồng mixin (đã đọc addons/oca/base_tier_validation/models/tier_validation.py
    # bản 17.0.4.1.0): hook thật là `_check_state_conditions(vals)` và
    # `_validated_states()` — KHÔNG tồn tại `_check_tier_validation`.
    #   * `_state_from` -> `_state_to` là cặp state BỊ CHẶN khi chưa duyệt;
    #   * `_cancel_state` = huỷ thì xoá chuỗi duyệt (quay về luồng chính);
    #   * `_tier_validation_manual_config = False`: mixin tự chèn nút Duyệt/Từ chối
    #     vào form (FE-01 không phải viết tay).
    # Định nghĩa 2 cấp nằm ở data/tier_definition_data.xml.
    # ------------------------------------------------------------------
    _tier_validation_manual_config = False
    _state_field = "state"
    _state_from = ["booking_confirmed"]
    _state_to = ["departed"]
    _cancel_state = "cancelled"

    # ------------------------------------------------------------------
    # Định danh & vận chuyển
    # ------------------------------------------------------------------
    name = fields.Char(
        string="Số chuyến",
        required=True,
        readonly=True,
        copy=False,
        index=True,
        default=lambda self: _("Mới"),
    )

    access_token = fields.Char(
        string="Mã tra cứu công khai",
        copy=False,
        index=True,
        help="Token ngẫu nhiên cho QR tra cứu công khai (tem xuất, trang /lab/track). "
             "KHÔNG dùng mã chuyến làm khoá: mã chuyến đoán được (CH/AIR/26/0257) nên ai "
             "biết mã là mở được trang — RV-01 vòng 2 phát hiện 2026-11-03.",
    )
    loai_van_chuyen = fields.Selection(
        selection=[
            ("air", "Đường bay"),
            ("sea", "Đường biển"),
        ],
        string="Loại vận chuyển",
        required=True,
        default="air",
        tracking=True,
    )
    # Tái sử dụng delivery.carrier của stock_delivery, KHÔNG tự tạo model hãng.
    carrier_id = fields.Many2one(
        comodel_name="delivery.carrier",
        string="Hãng vận chuyển (carrier)",
    )
    hang_van_chuyen = fields.Char(
        string="Tên hãng",
        help="Tên thương mại của hãng: Vietnam Airlines, Maersk, ... "
             "(delivery.carrier dùng để tính cước, field này để in manifest).",
    )
    so_booking = fields.Char(string="Số booking", copy=False)
    kho_xuat_id = fields.Many2one(
        comodel_name="stock.warehouse",
        string="Kho xuất",
        help="KHO BAY hoặc KHO TÀU (docs/00 §9 mục 4).",
    )

    # ------------------------------------------------------------------
    # Lịch & sức chứa
    # ------------------------------------------------------------------
    ngay_di = fields.Date(string="Ngày đi")
    ngay_den_du_kien = fields.Date(string="Ngày đến dự kiến")
    cut_off_datetime = fields.Datetime(string="Hạn chốt hàng (cut-off)", tracking=True)
    tai_trong_max_kg = fields.Float(
        string="Tải trọng tối đa (kg)",
        digits=(16, 2),
        default=0.0,
    )
    the_tich_max_cbm = fields.Float(
        string="Thể tích tối đa (CBM)",
        digits=(16, 4),
        default=0.0,
    )
    tai_trong_da_dung_kg = fields.Float(
        string="Tải trọng đã dùng (kg)",
        compute="_compute_tai_trong",
        store=True,
        readonly=True,
        digits=(16, 2),
        help="Tổng kg của các dòng đang ở trạng thái Đã vào chuyến (booked).",
    )
    the_tich_da_dung_cbm = fields.Float(
        string="Thể tích đã dùng (CBM)",
        compute="_compute_tai_trong",
        store=True,
        readonly=True,
        digits=(16, 4),
        help="Tổng CBM của các dòng đang ở trạng thái Đã vào chuyến (booked).",
    )
    ti_le_tai = fields.Float(
        string="Tỉ lệ tải (%)",
        compute="_compute_ti_le_tai",
        # store=True BAT BUOC: search view loc theo ti_le_tai, tree decoration + sort cung dung.
        # Khong store -> "Unsearchable field 'ti_le_tai'" khi validate view (da gap 2026-10-28).
        store=True,
        readonly=True,
        help="Lấy mức căng hơn trong 2 chiều kg/CBM. > 100 nghĩa là quá tải "
             "(nhánh ngoại lệ ② — giảm tải).",
    )

    # ------------------------------------------------------------------
    # Trạng thái — docs/02 §3.1
    # ------------------------------------------------------------------
    state = fields.Selection(
        selection=[
            ("draft", "Nháp"),
            ("open", "Đang mở"),
            ("booking_confirmed", "Đã chốt booking"),
            ("departed", "Đã khởi hành"),
            ("arrived", "Đã đến nơi"),
            ("delivered", "Đã giao khách"),
            ("closed", "Đã khoá"),
            ("cancelled", "Đã huỷ"),
        ],
        string="Trạng thái",
        required=True,
        default="draft",
        copy=False,
        index=True,
        tracking=True,
    )

    # ------------------------------------------------------------------
    # Quan hệ
    # ------------------------------------------------------------------
    line_ids = fields.One2many(
        comodel_name="lab.chuyen.line",
        inverse_name="chuyen_id",
        string="Đơn hàng trong chuyến",
    )
    picking_ids = fields.One2many(
        comodel_name="stock.picking",
        inverse_name="lab_chuyen_id",
        string="Phiếu xuất kho",
        help="Các phiếu xuất gắn với chuyến (bước ⑦).",
    )
    debit_ids = fields.One2many(
        comodel_name="account.move",
        inverse_name="lab_chuyen_id",
        string="Phiếu ghi nợ (Debit)",
        help="Debit = account.move (journal Debit) — không có model debit riêng (bước ⑬).",
    )

    # ==================================================================
    # Tên chứng từ theo sequence: CH/AIR/26/0001 (docs/02 §5)
    # ==================================================================
    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            # Default được ORM điền SAU override này, nên ở đây name thường là
            # None; chỉ khi client gửi lại đúng chuỗi default thì mới ghi đè.
            if not vals.get("name") or vals.get("name") == _("Mới"):
                loai = vals.get("loai_van_chuyen") or "air"
                code = "lab.chuyen.sea" if loai == "sea" else "lab.chuyen.air"
                vals["name"] = self.env["ir.sequence"].next_by_code(code) or _("Mới")
            # Khoá tra cứu công khai = token ngẫu nhiên (RV-01 vòng 2).
            if not vals.get("access_token"):
                vals["access_token"] = secrets.token_urlsafe(24)
        return super().create(vals_list)

    # ==================================================================
    # Computed — tải trọng (docs/02 §2.2 + §9)
    # ==================================================================
    @api.depends("line_ids.khoi_luong_kg", "line_ids.the_tich_cbm", "line_ids.state")
    def _compute_tai_trong(self):
        for chuyen in self:
            dong_da_vao = chuyen.line_ids.filtered(lambda line: line.state == "booked")
            chuyen.tai_trong_da_dung_kg = sum(dong_da_vao.mapped("khoi_luong_kg"))
            chuyen.the_tich_da_dung_cbm = sum(dong_da_vao.mapped("the_tich_cbm"))

    @api.depends(
        "tai_trong_da_dung_kg",
        "tai_trong_max_kg",
        "the_tich_da_dung_cbm",
        "the_tich_max_cbm",
    )
    def _compute_ti_le_tai(self):
        for chuyen in self:
            ti_le = 0.0
            if chuyen.tai_trong_max_kg:
                ti_le = max(ti_le, 100.0 * chuyen.tai_trong_da_dung_kg / chuyen.tai_trong_max_kg)
            if chuyen.the_tich_max_cbm:
                ti_le = max(ti_le, 100.0 * chuyen.the_tich_da_dung_cbm / chuyen.the_tich_max_cbm)
            chuyen.ti_le_tai = ti_le

    # ==================================================================
    # Chatter an toàn — lỗi gửi thông báo KHÔNG được phá giao dịch nghiệp vụ
    #
    # Vì sao override ``message_post`` (chứ không bọc từng chỗ gọi): đó là cửa
    # ngõ DUY NHẤT sinh message + notification của ``mail.thread``. Ở lab.chuyen
    # có ít nhất 3 đường vào:
    #   * 7 ``action_*`` bên dưới (``self.message_post(body=...)``);
    #   * OCA ``base_tier_validation`` — ``_notify_accepted_reviews`` gọi
    #     ``message_post(subtype_xmlid=...)`` khi duyệt cấp (bước ⑥);
    #   * tracking: ``write()`` KHÔNG post ngay — ``MailThread.write`` chỉ gọi
    #     ``_track_prepare``, còn ``_track_finalize`` chạy ở **precommit**, và
    #     ``_message_track`` chỉ gọi ``record.message_post(...)`` khi
    #     ``_track_subtype()`` trả về subtype (ngược lại dùng ``_message_log``).
    #     Model này chưa khai ``_track_subtype``, nhưng nếu ai thêm (hoặc module
    #     khác override) thì lỗi sẽ nổ Ở PRECOMMIT — sau khi action_* đã xong,
    #     vẫn rollback cả transaction. Bọc tại đây chặn cả 3 đường.
    #
    # Lỗi thật đã đo (E2E 2026-10-29): user demo không có email người gửi ->
    # ``UserError("Unable to send message, please configure the sender's email
    # address.")`` ném ra SAU khi state đã ghi -> rollback -> mất giao dịch
    # (tài xế gửi ảnh/kiện xong mà yêu cầu vẫn ở trạng thái cũ).
    #
    # Giải thích đầy đủ + đánh đổi: xem
    # addons/lab_laixe/models/lab_yeu_cau_lay_hang.py::message_post (cùng mẫu).
    # ⛔ KHÔNG bọc ``write``/``_check_state``: lỗi nghiệp vụ vẫn phải raise.
    # ==================================================================
    def message_post(self, **kwargs):
        """Chatter không phá giao dịch: lỗi gửi mail/chatter chỉ log warning."""
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

    # ==================================================================
    # State machine — docs/02 §3.1
    # Quy tắc (docs/02 §3): không nhảy cóc trạng thái, chặn bằng UserError
    # tiếng Việt, mọi chuyển state đều message_post để có chatter.
    # ==================================================================
    def _check_state(self, allowed_states, action_label):
        """Chặn transition sai. Trả về True nếu hợp lệ, raise nếu không."""
        self.ensure_one()
        if self.state not in allowed_states:
            nhan_state = dict(self._fields["state"].selection).get(self.state, self.state)
            raise UserError(
                _(
                    "Không thể %s: chuyến %s đang ở trạng thái \"%s\".",
                    action_label,
                    self.name,
                    nhan_state,
                )
            )
        return True

    def action_open(self):
        """Nháp -> Đang mở."""
        self.ensure_one()
        self._check_state(("draft",), _("mở chuyến"))
        self.write({"state": "open"})
        self.message_post(body=_("Mở chuyến %s.", self.name))
        return True

    def action_confirm_booking(self):
        """Đang mở -> Đã chốt booking (+ sinh phiếu XUẤT + mã tem xuất từng kiện).

        CHỐT 2026-11-02 (N12 + M7):
          * Phiếu xuất `WH/OUT` sinh **tự động khi chốt booking** — không phải lúc thêm
            dòng (còn đổi/gỡ đơn) và không phải lúc khởi hành (kho không kịp soạn hàng).
          * **Mỗi kiện một mã xuất** `XUAT/26/000123` (không dùng chung 1 tem cho chuyến).
          * Chưa validate: kho còn phải quét từng kiện rồi bấm "Xác nhận xuất" trên app.
        """
        self.ensure_one()
        self._check_state(("open",), _("chốt booking"))
        self.write({"state": "booking_confirmed"})
        phieu = self._tao_phieu_xuat()
        kien = self._lay_kien_cua_chuyen()
        self.message_post(
            body=_(
                "Chốt booking cho chuyến %(ten)s. Sinh phiếu xuất %(phieu)s với %(kien)d kiện.",
                ten=self.name,
                phieu=phieu.name if phieu else _("(không có kiện)"),
                kien=len(kien),
            )
        )
        if kien:
            return self.action_in_tem_xuat()
        return True

    # ==================================================================
    # XUẤT KHO (chốt 2026-11-02) — phiếu WH/OUT + mã tem xuất cho từng kiện
    # ==================================================================
    def _lay_kien_cua_chuyen(self):
        """Kiện sẽ xuất = kiện của các việc ĐÃ VỀ KHO thuộc các đơn trong chuyến.

        VÌ SAO phải đi qua `lab.yeu.cau.lay.hang` dù lab_chuyen KHÔNG depends lab_laixe:
          kiện chỉ sinh ở bước kho nhận hàng (lab_laixe). Hai module luôn cài cùng nhau
          trong dự án; có guard ``in self.env`` để không nổ nếu chạy lab_chuyen một mình.
        """
        self.ensure_one()
        Package = self.env["stock.quant.package"]
        if "lab.yeu.cau.lay.hang" not in self.env:
            return Package
        don_ban = self.line_ids.filtered(lambda line: line.state == "booked").mapped(
            "sale_order_id"
        )
        if not don_ban:
            return Package
        viec = self.env["lab.yeu.cau.lay.hang"].search(
            [
                ("sale_order_id", "in", don_ban.ids),
                ("state", "=", "confirmed"),
            ]
        )
        kien = Package
        for v in viec:
            kien |= v.kien_ids
        return kien.filtered(
            lambda k: (k.lab_trang_thai in (False, "in_stock")) or k.lab_chuyen_id == self
        )

    def _loai_phieu_xuat(self):
        self.ensure_one()
        loai = self.env["stock.picking.type"].search(
            [
                ("code", "=", "outgoing"),
                ("warehouse_id.company_id", "=", self.env.company.id),
            ],
            order="sequence, id",
            limit=1,
        )
        if not loai:
            raise UserError(
                _(
                    "Chưa cấu hình loại phiếu xuất kho (Delivery Orders). "
                    "Vào Kho > Cấu hình > Operation Types để tạo loại phiếu có mã 'outgoing'."
                )
            )
        return loai

    def _tao_phieu_xuat(self):
        """Sinh 1 phiếu `WH/OUT` cho cả chuyến + gán mã tem xuất cho từng kiện.

        Idempotent: chuyến đã có phiếu xuất thì trả lại phiếu đó (không tạo trùng).
        """
        self.ensure_one()
        da_co = self.picking_ids.filtered(lambda p: p.picking_type_id.code == "outgoing")
        if da_co:
            return da_co[:1]
        kien = self._lay_kien_cua_chuyen()
        if not kien:
            _logger.info("Chuyến %s chốt booking nhưng không có kiện nào để xuất.", self.name)
            return self.env["stock.picking"]
        loai = self._loai_phieu_xuat()
        # Vị trí: loại phiếu "Delivery Orders" của Odoo thường KHÔNG khai default src/dest
        # (dùng mặc định của kho) — đo thật 2026-11-02: default_location_src_id = False.
        # Vì vậy phải fallback: Stock của kho → Customers.
        kho = loai.warehouse_id
        vi_tri_giao = loai.default_location_src_id or kho.lot_stock_id
        vi_tri_nhan = loai.default_location_dest_id or self.env.ref(
            "stock.stock_location_customers", raise_if_not_found=False
        )
        if not vi_tri_giao or not vi_tri_nhan:
            raise UserError(
                _(
                    "Loại phiếu %s chưa có vị trí nguồn/đích mặc định và kho cũng "
                    "chưa có vị trí tồn kho.",
                    loai.display_name,
                )
            )
        product = self.env.ref("lab_core.product_kien_gom_le", raise_if_not_found=False)
        if not product:
            raise UserError(
                _("Chưa có sản phẩm đại diện KIỆN GOM LẺ (KIEN-GOM). Nâng cấp lab_core.")
            )
        picking = self.env["stock.picking"].create(
            {
                "picking_type_id": loai.id,
                "origin": self.name,
                "location_id": vi_tri_giao.id,
                "location_dest_id": vi_tri_nhan.id,
                "scheduled_date": fields.Datetime.now(),
                "lab_chuyen_id": self.id,
                "lab_package_count": len(kien),
            }
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
                    # Hàng XUẤT: kiện nằm ở cột NGUỒN (package_id), không phải result
                    "package_id": pkg.id,
                    "company_id": self.env.company.id,
                }
                for pkg in kien
            ]
        )
        picking.action_confirm()
        try:
            picking.action_assign()
        except Exception:  # noqa: BLE001 - thiếu tồn thì để kho xử lý, không chặn chốt
            _logger.warning(
                "Không reserve được phiếu xuất %s cho chuyến %s.",
                picking.name,
                self.name,
                exc_info=True,
            )
        # MỖI KIỆN MỘT MÃ TEM XUẤT
        #
        # sudo() CÓ CHỦ ĐÍCH + ĐÃ GHI RÕ: người bấm "Chốt booking" là manager/CS (nhóm
        # lab_core.group_manager / group_sale_admin) — họ có quyền nghiệp vụ với chuyến
        # nhưng KHÔNG có quyền kho trên `stock.quant.package` (cần Inventory/User).
        # Ghi mã tem là hệ quả KỸ THUẬT của hành động đã được phép (không phải mở quyền
        # kho cho manager): chỉ 3 field lab_* do chính module này khai, không đụng field
        # native nào của package. Cách khác (cấp Inventory/User cho manager) là mở quá rộng.
        sequence = self.env["ir.sequence"]
        for pkg in kien:
            pkg.sudo().write(  # sudo-ok: ghi field lab_* do lab_chuyen khai (mã tem xuất) thay vì cấp quyền Inventory cho manager
                {
                    "lab_ma_xuat": sequence.next_by_code("lab.xuat") or _("XUAT/MOI"),
                    "lab_chuyen_id": self.id,
                    "lab_trang_thai": "packed",
                    "lab_in_tem_luc": fields.Datetime.now(),
                }
            )
        return picking

    # ==================================================================
    # TEM XUẤT (2026-11-02) — mỗi kiện một tem, in tại kho
    # ==================================================================
    @api.depends("state")
    def _compute_kien_xuat_ids(self):
        Package = self.env["stock.quant.package"]
        for rec in self:
            kien = Package.search([("lab_chuyen_id", "=", rec.id)], order="id")
            rec.kien_xuat_ids = kien
            rec.so_kien_xuat = len(kien)

    kien_xuat_ids = fields.Many2many(
        comodel_name="stock.quant.package",
        string="Kiện của chuyến",
        compute="_compute_kien_xuat_ids",
        help="Kiện đã được gán mã tem xuất cho chuyến này (package.lab_chuyen_id).",
    )
    so_kien_xuat = fields.Integer(
        string="Số kiện đã có tem xuất",
        compute="_compute_kien_xuat_ids",
        help="Dùng để ẩn/hiện nút 'In tem xuất' — chuyến chưa có kiện thì không in gì.",
    )

    def _public_base_url(self):
        """Địa chỉ công khai để nhét vào QR (ưu tiên tham số khi demo bằng IP LAN)."""
        get_param = self.env["ir.config_parameter"].sudo().get_param  # sudo-ok: ghi field lab_* do lab_chuyen khai (mã tem xuất) thay vì cấp quyền Inventory cho manager
        return (get_param("lab.public_base_url") or get_param("web.base.url") or "").rstrip("/")

    def _ensure_token(self):
        """Bảo đảm chuyến có token (dữ liệu cũ trước 2026-11-03). Trả token."""
        self.ensure_one()
        if not self.access_token:
            # write() cần quyền ghi chuyến; hàm được gọi từ data backfill (env superuser)
            # hoặc từ report của người có quyền xem chuyến.
            self.sudo().write({"access_token": secrets.token_urlsafe(24)})  # sudo-ok: sinh token hệ thống cho QR tra cứu công khai (không phải dữ liệu người dùng); người xem báo cáo có thể không có quyền ghi chuyến
        return self.access_token

    def khoa_tra_cuu(self):
        """Khoá tra cứu công khai = **token ngẫu nhiên**, KHÔNG phải mã chuyến.

        VÌ SAO ĐỔI (RV-01 vòng 2, 2026-11-03): trước đây khoá = mã chuyến đổi '/'→'-'
        (vd `CH-AIR-26-0257`) — ai đoán/biết mã chuyến là mở được trang tra cứu (lộ tên
        shop + trạng thái kiện). Nay dùng token 32 ký tự không đoán được.
        """
        self.ensure_one()
        return self._ensure_token() or ""

    @api.model
    def _lab_backfill_access_token(self):
        """Backfill token cho chuyến cũ (data function, idempotent).

        Gọi từ `data/backfill_token.xml` mỗi lần cài/nâng cấp; chỉ ghi chuyến CHƯA có
        token nên chạy lại vô hại.
        """
        thieu = self.sudo().search([("access_token", "=", False)])  # sudo-ok: backfill token hệ thống chạy từ data XML (env superuser), chỉ đọc bản ghi thiếu token
        for chuyen in thieu:
            chuyen.write({"access_token": secrets.token_urlsafe(24)})
        _logger.info("Backfill access_token cho %d chuyến.", len(thieu))
        return True

    def track_url_kien(self, kien):
        """URL tra cứu công khai của 1 kiện thuộc chuyến này (dạng path, QR sạch)."""
        self.ensure_one()
        return "%s/lab/track/%s/%s" % (
            self._public_base_url(),
            self.khoa_tra_cuu(),
            kien.name,
        )

    def thong_tin_diem_den(self):
        """Điểm đến + chứng từ vận tải của chuyến, lấy từ HÀNH TRÌNH (không bịa field mới).

        `lab.hanh.trinh` là nơi duy nhất có `cang_den`, `dai_ly_giao_id`, `so_awb`,
        `so_hbl`. lab_chuyen KHÔNG depends lab_hanhtrinh (hai module cùng depends
        lab_core), nên dùng guard ``in self.env`` để không nổ khi chạy lab_chuyen một mình.
        """
        self.ensure_one()
        thong_tin = {"diem_den": "", "chung_tu": ""}
        if "lab.hanh.trinh" not in self.env:
            return thong_tin
        hanh_trinh = self.env["lab.hanh.trinh"].search(
            [("chuyen_id", "=", self.id)], limit=1
        )
        if not hanh_trinh:
            return thong_tin
        thong_tin["diem_den"] = hanh_trinh.cang_den or (
            hanh_trinh.dai_ly_giao_id.name if hanh_trinh.dai_ly_giao_id else ""
        ) or ""
        thong_tin["chung_tu"] = hanh_trinh.so_awb or hanh_trinh.so_hbl or ""
        return thong_tin

    def action_in_tem_xuat(self):
        """Nút "In tem xuất": mở report PDF, mỗi kiện một tem 100×70."""
        self.ensure_one()
        report = self.env.ref("lab_chuyen.action_report_tem_xuat", raise_if_not_found=False)
        if not report:
            return True
        # config=False: admin in lần đầu sẽ KHÔNG bị đá sang wizard 'chọn layout báo cáo'
        # (Odoo trả act_window thay vì report action khi công ty chưa có layout — lỗi thật
        # phát hiện qua test 2026-11-02).
        return report.report_action(self, config=False)


    def action_depart(self):
        """Đã chốt booking -> Đã khởi hành."""
        self.ensure_one()
        self._check_state(("booking_confirmed",), _("cho chuyến khởi hành"))
        self.write({"state": "departed"})
        self.message_post(body=_("Chuyến %s đã khởi hành.", self.name))
        return True

    def action_arrive(self):
        """Đã khởi hành -> Đã đến nơi."""
        self.ensure_one()
        self._check_state(("departed",), _("đánh dấu chuyến đã đến"))
        self.write({"state": "arrived"})
        self.message_post(body=_("Chuyến %s đã đến nơi.", self.name))
        return True

    def action_deliver(self):
        """Đã đến nơi -> Đã giao khách."""
        self.ensure_one()
        self._check_state(("arrived",), _("đánh dấu đã giao khách"))
        self.write({"state": "delivered"})
        self.message_post(body=_("Chuyến %s đã giao cho đại lý/khách.", self.name))
        return True

    def action_close(self):
        """Đã giao khách -> Đã khoá (khoá sửa)."""
        self.ensure_one()
        self._check_state(("delivered",), _("khoá chuyến"))
        self.write({"state": "closed"})
        self.message_post(body=_("Khoá chuyến %s.", self.name))
        return True

    def action_cancel(self):
        """Nháp / Đang mở / Đã chốt booking -> Đã huỷ (docs/02 §3.1)."""
        self.ensure_one()
        self._check_state(("draft", "open", "booking_confirmed"), _("huỷ chuyến"))
        self.write({"state": "cancelled"})
        self.message_post(body=_("Huỷ chuyến %s.", self.name))
        return True
