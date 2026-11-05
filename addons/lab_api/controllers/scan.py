# -*- coding: utf-8 -*-
"""Controller QUÉT TEM KIỆN — `POST /lab/api/v1/scan` (chốt 2026-11-02).

MỘT endpoint cho cả 2 mốc:

* ``moc = "nhan"`` — kho quét tem NHẬN sau khi dán lên kiện (kiện vào kho);
* ``moc = "xuat"`` — kho quét tem XUẤT khi đưa kiện ra khỏi kho.

Quy tắc (đã chốt với khách):
  * Tài xế KHÔNG quét tem (tem chỉ sinh tại kho) — endpoint này chỉ dành cho
    Kho / CS / Trưởng phòng / Sale Admin.
  * Mỗi lần quét ghi **1 dòng** `lab.scan.event` (append-only) để có bảng tổng kết
    và truy vết ai quét lúc nào bằng thiết bị gì.
  * **Chống trùng**: cùng `clientUuid` (mạng chập, bấm 2 lần) → trả lại kết quả cũ
    với cờ ``trung=true``, KHÔNG tạo dòng mới.
  * Quét **sai mốc** (ví dụ quét tem xuất khi kiện chưa được in tem xuất) → 409
    ``sai_moc`` kèm câu tiếng Việt; quét **lặp ở đúng mốc** → cờ ``trung=true``
    nhưng vẫn 200 để app hiện "đã quét trước đó".

Envelope trả về giống mọi endpoint khác: ``{ok, data, error}`` (tools.ok/loi).
"""

import re
from urllib.parse import unquote

from odoo import _, http
from odoo.http import request

from ..helpers import kiem_mot_trong_nhom, xac_thuc_token
from ..tools import check_rate_limit, loi, ok, tham_so, tra_loi

#: Regex trích mã kiện (`KIEN/…`) hoặc mã tem xuất (`XUAT/…`) từ nội dung QR / URL tra cứu.
RE_MA_TEM = re.compile(r"((?:KIEN|XUAT)/[A-Za-z0-9/_-]+)", re.IGNORECASE)

#: Nhóm được quét tem: kho làm chính, CS/Trưởng phòng/Sale Admin để đối soát.
NHOM_DUOC_QUET = (
    "lab_core.group_warehouse",
    "lab_core.group_cs",
    "lab_core.group_manager",
    "lab_core.group_sale_admin",
)

#: Hai mốc được phép gọi từ app.
MOC_HOP_LE = ("nhan", "xuat")


def _tra_loi_loi(code, message, du_lieu, status):
    """Envelope LỖI cho app (`ok: false`) nhưng vẫn kèm `data` để app đọc `ketQua`.

    VÌ SAO (RV-01 review 2026-11-03): trước đây 404/409 trả `ok: true` (chỉ đổi HTTP
    status) nên app dễ hiểu sai là thành công. HTTP status vẫn giữ 404/409 như cũ —
    chỉ envelope đổi cho ĐÚNG nghĩa.
    """
    return tra_loi(
        {"ok": False, "data": du_lieu, "error": {"code": code, "message": message}},
        status=status,
    )

class LabScanApi(http.Controller):
    @staticmethod
    def _chan_quyen():
        return kiem_mot_trong_nhom(
            NHOM_DUOC_QUET,
            _("Chức năng quét tem chỉ dành cho Kho (hoặc CS/Trưởng phòng đối soát)."),
        )

    @staticmethod
    def _ung_vien_ma(ma):
        """Chuẩn hoá chuỗi quét từ QR/máy quét thành danh sách ứng viên mã kiện / mã xuất.

        Hỗ trợ:
          1. Mã thuần: ``KIEN/26/001352`` hoặc ``XUAT/26/000123``;
          2. URL QR trên tem nhận/xuất: ``http://.../lab/track/<token>/KIEN/26/001352``;
          3. Chuỗi QR nhiều dòng hoặc JSON có chứa ``KIEN/...`` / ``XUAT/...``.
        """
        goc = (ma or "").strip()
        if not goc:
            return []
        da_giai_ma = unquote(goc).strip()
        ung_vien = []

        def _them(gia_tri):
            sach = (gia_tri or "").strip().strip("/\"'")
            if sach and sach not in ung_vien:
                ung_vien.append(sach)

        _them(goc)
        _them(da_giai_ma)
        for nguon in (da_giai_ma, goc):
            if "/lab/track/" in nguon:
                phan_sau = nguon.split("/lab/track/", 1)[1].split("?", 1)[0].split("#", 1)[0]
                if "/" in phan_sau:
                    _them(phan_sau.split("/", 1)[1])
            for khop in RE_MA_TEM.findall(nguon):
                _them(khop.upper())
                _them(khop)
        return ung_vien

    @staticmethod
    def _tim_kien(ma):
        """Tìm kiện theo MÃ KIỆN, MÃ TEM XUẤT hoặc URL QR tra cứu trên tem."""
        Package = request.env["stock.quant.package"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem
        for ung_vien in LabScanApi._ung_vien_ma(ma):
            kien = Package.search([("name", "=", ung_vien)], limit=1)
            if kien:
                return kien
            kien = Package.search([("lab_ma_xuat", "=", ung_vien)], limit=1)
            if kien:
                return kien
        return Package.browse()

    @staticmethod
    def _picking_nhap_cua_kien(kien):
        """Phiếu NHẬP mà kiện này đi vào (qua dòng hàng có result_package_id)."""
        dong = (
            request.env["stock.move.line"]
            .sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem
            .search([("result_package_id", "=", kien.id)], limit=1)
        )
        return dong.picking_id

    @staticmethod
    def _tien_do(kien, moc):
        """{daQuet, tong} để app hiện tiến độ `7/12`."""
        Event = request.env["lab.scan.event"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem
        if moc == "xuat":
            chuyen = kien.lab_chuyen_id
            if not chuyen:
                return {"daQuet": 0, "tong": 0}
            Package = request.env["stock.quant.package"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem
            da_quet = Package.search_count(
                [("lab_chuyen_id", "=", chuyen.id), ("lab_trang_thai", "=", "shipped")]
            )
            return {"daQuet": da_quet, "tong": chuyen.so_kien_xuat}
        picking = LabScanApi._picking_nhap_cua_kien(kien)
        if not picking:
            return {"daQuet": 0, "tong": 0}
        da_quet = len(
            Event.search(
                [
                    ("picking_id", "=", picking.id),
                    ("loai", "=", "nhan"),
                    ("ket_qua", "=", "ok"),
                ]
            ).mapped("kien_id")
        )
        return {"daQuet": da_quet, "tong": picking.lab_package_count or 0}

    @staticmethod
    def _du_lieu(kien, moc, ket_qua, canh_bao=False, trung=False):
        chuyen = kien.lab_chuyen_id
        picking = (
            chuyen.picking_ids.filtered(lambda p: p.picking_type_id.code == "outgoing")[:1]
            if moc == "xuat" and chuyen
            else LabScanApi._picking_nhap_cua_kien(kien)
        )
        return ok(
            {
                "kien": kien.name,
                "maXuat": kien.lab_ma_xuat or "",
                "job": picking.origin or "",
                "chuyen": chuyen.name or "",
                "trangThai": dict(
                    kien._fields["lab_trang_thai"].selection
                ).get(kien.lab_trang_thai, ""),
                "ketQua": ket_qua,
                "canhBao": canh_bao,
                "trung": trung,
                "tienDo": LabScanApi._tien_do(kien, moc),
            }
        )

    @http.route(
        "/lab/api/v1/scan",
        type="http",
        auth="user",
        methods=["POST"],
        csrf=False,
    )
    def scan(self, **kwargs):
        loi_token = xac_thuc_token()
        if loi_token:
            return tra_loi(loi_token, status=401)
        loi_quyen = self._chan_quyen()
        if loi_quyen:
            return tra_loi(loi_quyen, status=403)
        gioi_han = check_rate_limit("scan:%s" % request.env.uid, limit=120)
        if gioi_han:
            return tra_loi(gioi_han, status=429)

        params = tham_so()
        ma = (params.get("ma") or "").strip()
        moc = (params.get("moc") or "").strip()
        client_uuid = (params.get("clientUuid") or "").strip()
        ghi_chu = (params.get("ghiChu") or "").strip()
        thiet_bi = (params.get("thietBi") or "web").strip()
        if thiet_bi not in ("web", "app"):
            thiet_bi = "web"

        if not ma:
            return tra_loi(loi("VALIDATION", _("Thiếu mã kiện hoặc mã tem xuất.")), status=400)
        if moc not in MOC_HOP_LE:
            return tra_loi(
                loi("VALIDATION", _("Mốc quét phải là 'nhan' hoặc 'xuat'.")), status=400
            )
        if not client_uuid:
            return tra_loi(
                loi(
                    "VALIDATION",
                    _("Thiếu clientUuid — app phải sinh mã chống trùng cho mỗi lần quét."),
                ),
                status=400,
            )

        Event = request.env["lab.scan.event"].sudo()  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem

        # (1) ĐÃ QUÉT TRƯỚC ĐÓ với cùng clientUuid -> trả lại kết quả cũ, không tạo dòng mới
        da_co = Event.search([("client_uuid", "=", client_uuid)], limit=1)
        if da_co:
            return tra_loi(
                self._du_lieu(
                    da_co.kien_id,
                    da_co.loai,
                    da_co.ket_qua,
                    canh_bao=(da_co.ket_qua != "ok"),
                    trung=True,
                )
            )

        # (2) KHÔNG TÌM THẤY KIỆN -> 404 kèm ketQua=khong_tim_thay cho app hiện cảnh báo.
        #     Không ghi được `lab.scan.event` vì `kien_id` là bắt buộc (không bịa kiện).
        kien = self._tim_kien(ma)
        if not kien:
            return _tra_loi_loi(
                "NOT_FOUND",
                _("Không tìm thấy kiện với mã %s.", ma),
                {
                    "kien": ma,
                    "maXuat": "",
                    "job": "",
                    "chuyen": "",
                    "trangThai": "",
                    "ketQua": "khong_tim_thay",
                    "canhBao": _("Không tìm thấy kiện với mã %s.", ma),
                    "trung": False,
                    "tienDo": {"daQuet": 0, "tong": 0},
                },
                404,
            )

        # (3) XÁC ĐỊNH KẾT QUẢ THEO MỐC
        trang_thai = kien.lab_trang_thai or "in_stock"
        ket_qua = "ok"
        canh_bao = False
        if moc == "nhan":
            # Kiện đã in tem xuất / đã xuất thì KHÔNG quét tem nhận nữa.
            if trang_thai in ("packed", "shipped"):
                ket_qua = "sai_moc"
                canh_bao = _(
                    "Kiện đã in tem xuất (hoặc đã xuất kho) — không quét tem nhận được nữa."
                )
            elif (
                Event.search_count(
                    [
                        ("kien_id", "=", kien.id),
                        ("loai", "=", "nhan"),
                        ("ket_qua", "=", "ok"),
                    ]
                )
                > 0
            ):
                ket_qua = "trung"
                canh_bao = _("Kiện này đã quét tem nhận trước đó.")
        else:  # moc == "xuat"
            if not kien.lab_ma_xuat:
                ket_qua = "sai_moc"
                canh_bao = _("Kiện chưa được in tem xuất — chốt booking trước rồi in tem.")
            elif trang_thai == "shipped":
                ket_qua = "trung"
                canh_bao = _("Kiện này đã xuất kho trước đó.")
            elif trang_thai != "packed":
                ket_qua = "sai_moc"
                canh_bao = _(
                    "Kiện chưa ở trạng thái 'đã in tem xuất' — không quét xuất được."
                )

        # (4) GHI NHẬT KÝ (luôn ghi: kể cả sai mốc/trùng — đó là dữ liệu đối soát)
        event = Event.create(
            {
                "kien_id": kien.id,
                "loai": moc,
                "ket_qua": ket_qua,
                "thiet_bi": thiet_bi,
                "client_uuid": client_uuid,
                "ghi_chu": ghi_chu,
            }
        )
        if ket_qua == "ok":
            if moc == "nhan":
                kien.sudo().write({"lab_trang_thai": "in_stock"})  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem
            else:
                kien.sudo().write(  # sudo-ok: endpoint đã qua X-Lab-Token + kiem_nhom Kho/CS/manager; đọc/ghi whitelist kiện cho màn quét tem
                    {"lab_trang_thai": "shipped", "lab_xuat_luc": event.thoi_diem}
                )

        # (5) TRẢ KẾT QUẢ
        du_lieu = self._du_lieu(
            kien, moc, ket_qua, canh_bao=canh_bao, trung=(ket_qua == "trung")
        )["data"]
        if ket_qua == "sai_moc":
            return _tra_loi_loi("SAI_MOC", canh_bao, du_lieu, 409)
        # `trung` = 200 nhưng vẫn là "đã quét trước đó" -> giữ ok:true (không phải lỗi)
        return tra_loi(ok(du_lieu))
