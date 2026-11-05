# -*- coding: utf-8 -*-
"""Controller XUẤT KHO theo chuyến — chặng cuối của chuỗi tem (chốt 2026-11-02).

Hai endpoint:

* ``GET  /lab/api/v1/kho/cho-xuat``            — danh sách chuyến có kiện ĐÃ IN TEM XUẤT
  nhưng CHƯA xuất kho (màn "Kho · Xuất kho" của app). Thêm ``?chuyenId=`` để lấy chi tiết.
* ``POST /lab/api/v1/kho/chuyen/<id>/xuat``    — **Xác nhận xuất**: validate phiếu
  ``WH/OUT`` sau khi kho quét tem (hoặc chấp nhận xuất thiếu kèm lý do — quyết định M9).

Quyền: ``auth='user'`` + ``X-Lab-Token`` + nhóm Kho — dùng lại ``kiem_nhom`` như
``kho.py``. Toàn bộ đọc/ghi bản ghi vẫn do ACL + ir.rule quyết định (Luật 3).

REA (Luật 4): validate phiếu xuất là **sự kiện kinh tế** "hàng rời kho" ⇒ chứng từ là
``stock.picking`` native. ``lab.scan.event`` chỉ ghi nhật ký quét/ngoại lệ, không thay
chứng từ.

Luồng quyết định (đã chốt):
    * quét đủ kiện  → validate, tất cả kiện `shipped`;
    * thiếu kiện + KHÔNG có lý do → 409 ``CON_KIEN_CHUA_QUET`` (chặn, trả danh sách);
    * thiếu kiện + CÓ lý do → kiện thiếu `cancelled`, ghi scan.event kèm lý do, vẫn
      validate (xuất thiếu), trả ``soKienThieu``.
"""

from odoo import _, fields, http
from odoo.exceptions import UserError
from odoo.http import request

from ..helpers import kiem_nhom, xac_thuc_token
from ..tools import check_rate_limit, loi, ok, tham_so, tra_loi

#: Trạng thái chuyến được phép xuất kho (đã chốt booking trở đi).
TRANG_THAI_CHO_XUAT = ("booking_confirmed", "departed", "arrived", "delivered")

#: Mã lỗi máy đọc được.
LOI_KHONG_CO_PHIEU = "KHONG_CO_PHIEU"
LOI_CON_KIEN_CHUA_QUET = "CON_KIEN_CHUA_QUET"


def _tra_loi_kem_du_lieu(code, message, du_lieu, status=409):
    """Envelope lỗi NHƯNG vẫn kèm ``data`` (app cần danh sách kiện còn thiếu để hiện)."""
    return tra_loi(
        {"ok": False, "data": du_lieu, "error": {"code": code, "message": message}},
        status=status,
    )


class LabKhoXuatApi(http.Controller):
    # ==================================================================
    # Helpers dùng chung
    # ==================================================================
    @staticmethod
    def _chan_quyen():
        return kiem_nhom(
            "lab_core.group_warehouse",
            _("Chức năng xuất kho chỉ dành cho bộ phận Kho."),
        )

    @staticmethod
    def _phieu_xuat_cua_chuyen(chuyen):
        """Phiếu ``WH/OUT`` của chuyến (1 chuyến = 1 phiếu — quyết định D/N12)."""
        return chuyen.picking_ids.filtered(
            lambda p: p.picking_type_id.code == "outgoing"
        )[:1]

    @staticmethod
    def _kien_cua_chuyen(chuyen):
        return request.env["stock.quant.package"].search(
            [("lab_chuyen_id", "=", chuyen.id)], order="id"
        )

    @classmethod
    def _du_lieu_chuyen(cls, chuyen):
        """Một dòng cho màn 'Kho · Xuất kho'."""
        kien = cls._kien_cua_chuyen(chuyen)
        da_quet = len(kien.filtered(lambda k: k.lab_trang_thai == "shipped"))
        diem_den = ""
        if "lab.hanh.trinh" in request.env:
            diem_den = chuyen.thong_tin_diem_den().get("diem_den") or ""
        return {
            "chuyenId": chuyen.id,
            "chuyen": chuyen.name,
            "state": chuyen.state,
            "stateLabel": dict(chuyen._fields["state"].selection).get(chuyen.state, ""),
            "diemDen": diem_den,
            "soKien": len(kien),
            "daQuet": da_quet,
            "kien": [
                {
                    "ma": k.name,
                    "maXuat": k.lab_ma_xuat or "",
                    "trangThai": k.lab_trang_thai or "",
                    "trangThaiLabel": dict(
                        k._fields["lab_trang_thai"].selection
                    ).get(k.lab_trang_thai, ""),
                }
                for k in kien
            ],
        }

    # ==================================================================
    # GET /lab/api/v1/kho/cho-xuat
    # ==================================================================
    @http.route(
        "/lab/api/v1/kho/cho-xuat",
        type="http",
        auth="user",
        methods=["GET"],
        csrf=False,
    )
    def cho_xuat(self, **kwargs):
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("kho-cho-xuat:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        Chuyen = request.env["lab.chuyen"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + nhóm Kho; nghiệp vụ xuất kho cần đọc/ghi kiện + phiếu
        chuyen_id = (kwargs.get("chuyenId") or "").strip()
        if chuyen_id:
            chuyen = Chuyen.browse(int(chuyen_id)).exists() if chuyen_id.isdigit() else Chuyen
            if not chuyen or chuyen.state not in TRANG_THAI_CHO_XUAT:
                return tra_loi(
                    loi(
                        LOI_KHONG_CO_PHIEU,
                        _("Không tìm thấy chuyến đang chờ xuất với id %s.", chuyen_id),
                    ),
                    status=404,
                )
            return tra_loi(ok(self._du_lieu_chuyen(chuyen)))

        danh_sach = Chuyen.search([("state", "in", TRANG_THAI_CHO_XUAT)], order="id desc")
        items = []
        for chuyen in danh_sach:
            du_lieu = self._du_lieu_chuyen(chuyen)
            # Chỉ hiện chuyến còn kiện CHƯA xuất (đã in tem xuất), đúng mục đích màn hình.
            if du_lieu["soKien"] and du_lieu["daQuet"] < du_lieu["soKien"]:
                items.append(du_lieu)
        return tra_loi(ok({"items": items}))

    # ==================================================================
    # POST /lab/api/v1/kho/chuyen/<id>/xuat
    # ==================================================================
    @http.route(
        "/lab/api/v1/kho/chuyen/<int:chuyen_id>/xuat",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def xuat(self, chuyen_id, **kwargs):
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("kho-xuat:%s" % request.env.uid, limit=60)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        params = tham_so()
        client_uuid = (params.get("clientUuid") or "").strip()
        ly_do_thieu = (params.get("lyDoThieu") or "").strip()
        if not client_uuid:
            return tra_loi(
                loi(
                    "VALIDATION",
                    _("Thiếu clientUuid — app phải sinh mã chống trùng cho mỗi lần xác nhận."),
                ),
                status=400,
            )

        Chuyen = request.env["lab.chuyen"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + nhóm Kho; nghiệp vụ xuất kho cần đọc/ghi kiện + phiếu
        chuyen = Chuyen.browse(chuyen_id).exists()
        if not chuyen or chuyen.state not in TRANG_THAI_CHO_XUAT:
            return tra_loi(
                loi(
                    LOI_KHONG_CO_PHIEU,
                    _("Chuyến id %s không ở trạng thái chờ xuất kho.", chuyen_id),
                ),
                status=404,
            )

        picking = self._phieu_xuat_cua_chuyen(chuyen)
        if not picking:
            return tra_loi(
                loi(
                    LOI_KHONG_CO_PHIEU,
                    _("Chuyến %s chưa có phiếu xuất kho (WH/OUT).", chuyen.name),
                ),
                status=404,
            )

        # --- PHIẾU ĐÃ XUẤT XONG ⇒ trả kết quả, KHÔNG rơi vào nhánh "còn kiện chưa quét".
        # VÌ SAO (RV-01 → rủi ro #2 của BE-01): sau khi xuất thiếu, kiện bị đánh
        # `cancelled`; nếu app gọi lại với clientUuid MỚI thì logic cũ coi chúng là "chưa
        # quét" ⇒ 409 sai nghĩa. Phiếu `done` là trạng thái cuối nên mọi lần gọi lại đều
        # là "đã xuất rồi" (trung=true).
        if picking.state == "done":
            kien_cuoi = self._kien_cua_chuyen(chuyen)
            return tra_loi(
                ok(
                    {
                        "phieu": picking.name,
                        "soKienXuat": len(
                            kien_cuoi.filtered(lambda k: k.lab_trang_thai == "shipped")
                        ),
                        "soKienThieu": len(
                            kien_cuoi.filtered(lambda k: k.lab_trang_thai == "cancelled")
                        ),
                        "state": picking.state,
                        "trung": True,
                    }
                )
            )
        # --- CHỐNG TRÙNG: cùng clientUuid ⇒ trả kết quả cũ, không validate lại ---
        if picking.lab_xuat_client_uuid == client_uuid:
            kien_da_xuat = self._kien_cua_chuyen(chuyen).filtered(
                lambda k: k.lab_trang_thai == "shipped"
            )
            return tra_loi(
                ok(
                    {
                        "phieu": picking.name,
                        "soKienXuat": len(kien_da_xuat),
                        "soKienThieu": len(
                            self._kien_cua_chuyen(chuyen).filtered(
                                lambda k: k.lab_trang_thai == "cancelled"
                            )
                        ),
                        "state": picking.state,
                        "trung": True,
                    }
                )
            )

        kien_tat_ca = self._kien_cua_chuyen(chuyen)
        kien_chua_quet = kien_tat_ca.filtered(lambda k: k.lab_trang_thai != "shipped")

        # --- THIẾU KIỆN MÀ KHÔNG CÓ LÝ DO ⇒ CHẶN (409) ---
        if kien_chua_quet and not ly_do_thieu:
            return _tra_loi_kem_du_lieu(
                LOI_CON_KIEN_CHUA_QUET,
                _(
                    "Còn %(con_lai)d kiện chưa quét tem xuất. Quét đủ, hoặc nhập lý do "
                    "xuất thiếu để tiếp tục.",
                    con_lai=len(kien_chua_quet),
                ),
                {
                    "conLai": len(kien_chua_quet),
                    "danhSach": [k.name for k in kien_chua_quet],
                },
            )

        # --- THIẾU KIỆN + CÓ LÝ DO ⇒ đánh dấu cancelled + ghi nhật ký (M9) ---
        Event = request.env["lab.scan.event"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + nhóm Kho; nghiệp vụ xuất kho cần đọc/ghi kiện + phiếu
        if kien_chua_quet:
            for kien in kien_chua_quet:
                kien.sudo().write({"lab_trang_thai": "cancelled"})  # sudo-ok: endpoint đã qua X-Lab-Token + nhóm Kho; nghiệp vụ xuất kho cần đọc/ghi kiện + phiếu
                Event.create(
                    {
                        "kien_id": kien.id,
                        "loai": "xuat",
                        "ket_qua": "ok",
                        "thiet_bi": "app",
                        # UUID riêng cho từng kiện (client_uuid là unique toàn bảng).
                        "client_uuid": "%s-%s" % (client_uuid, kien.id),
                        "ghi_chu": _("Xuất thiếu: %s", ly_do_thieu),
                    }
                )
            # Bỏ dòng hàng của kiện không xuất để phiếu không sinh backorder.
            dong_bo = picking.move_line_ids.filtered(
                lambda ml: ml.package_id in kien_chua_quet
            )
            if dong_bo:
                dong_bo.unlink()
            picking.move_ids.write({"product_uom_qty": len(kien_tat_ca) - len(kien_chua_quet)})

        kien_da_xuat = kien_tat_ca.filtered(lambda k: k.lab_trang_thai == "shipped")

        # --- VALIDATE PHIẾU ---
        try:
            picking.move_ids.write({"picked": True})
            ket_qua = picking.button_validate()
        except UserError as exc:
            return tra_loi(loi("VALIDATION", str(exc)), status=409)
        if isinstance(ket_qua, dict):
            return tra_loi(
                loi(
                    "CHUA_HOAN_TAT",
                    _(
                        "Phiếu %s cần xác nhận tay trong kho (backorder/chuyển ngay).",
                        picking.name,
                    ),
                ),
                status=409,
            )

        luc = fields.Datetime.now()
        for kien in kien_da_xuat:
            kien.sudo().write(  # sudo-ok: endpoint đã qua X-Lab-Token + nhóm Kho; nghiệp vụ xuất kho cần đọc/ghi kiện + phiếu
                {"lab_trang_thai": "shipped", "lab_xuat_luc": kien.lab_xuat_luc or luc}
            )
        picking.write(
            {"lab_xuat_client_uuid": client_uuid, "lab_xuat_confirm_luc": luc}
        )

        return tra_loi(
            ok(
                {
                    "phieu": picking.name,
                    "soKienXuat": len(kien_da_xuat),
                    "soKienThieu": len(kien_chua_quet),
                    "state": picking.state,
                    "trung": False,
                }
            )
        )
