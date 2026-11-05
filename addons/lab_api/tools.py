# -*- coding: utf-8 -*-
"""Tiện ích dùng chung của ``lab_api`` — envelope, rate limit, token, kiểm ảnh.

Nguồn: docs/02-kien-truc.md §6.1–6.3 + ADR-004. Tách ra khỏi controller để:
* **một** envelope ``{ok, data, error}`` cho mọi endpoint (FE chỉ viết 1 bộ parser);
* rate limit và kiểm ảnh chỉ có **một** chỗ để review;
* khỏi lặp lại 4 dòng try/except base64 ở 3 controller.

Quy ước: mọi thông điệp trả về cho người dùng là tiếng Việt có dấu, bọc ``_()``.
"""

import base64
import binascii
import hashlib
import json
import secrets
import time
from datetime import timedelta

from odoo import _, fields
from odoo.exceptions import UserError
from odoo.http import Response, request

# Token sống 8 giờ (docs/02 §6.3) — hết hạn thì app phải đăng nhập lại.
TOKEN_TTL_GIO = 8
# Ảnh/chữ ký gửi lên: tối đa 5 MB mỗi file (docs/02 §6.3, review skill §6).
ANH_TOI_DA_BYTES = 5 * 1024 * 1024

#: Bộ đếm rate limit nằm trong RAM của worker.
#: Demo chạy **1 worker** nên đủ; production phải chuyển sang Redis/DB
#: (docs/02 §6.3 — "rate limit theo IP + token, đếm trong cache").
_RATE_CACHE = {}


# ======================================================================
# Envelope {ok, data, error}
# ======================================================================
def ok(data=None):
    """Envelope thành công."""
    return {"ok": True, "data": data, "error": None}


def loi(code, message):
    """Envelope lỗi. ``code`` là mã máy đọc được, ``message`` là tiếng Việt."""
    return {"ok": False, "data": None, "error": {"code": code, "message": message}}


def tra_loi(payload, status=200):
    """Trả JSON thuần (không bọc JSON-RPC) để app KMP/Ktor đọc thẳng."""
    try:
        return request.make_json_response(payload, status=status)
    except AttributeError:  # pragma: no cover - bản build không có helper này
        return Response(
            json.dumps(payload),
            status=status,
            content_type="application/json; charset=utf-8",
        )


# ======================================================================
# Đọc tham số
# ======================================================================
def tham_so():
    """Đọc body JSON của request.

    Chấp nhận **cả hai** kiểu body để app không phải đoán:
    * phẳng  ``{"login": "...", "password": "..."}``
    * JSON-RPC ``{"jsonrpc": "2.0", "method": "call", "params": {...}}``
      (nếu sau này FE dùng lại client JSON-RPC của Odoo).
    """
    raw = request.httprequest.get_data(as_text=True) or ""
    if not raw.strip():
        return {}
    try:
        du_lieu = json.loads(raw)
    except ValueError:
        return {}
    if not isinstance(du_lieu, dict):
        return {}
    params = du_lieu.get("params")
    if isinstance(params, dict):
        gop = {k: v for k, v in du_lieu.items() if k != "params"}
        gop.update(params)
        return gop
    return du_lieu


def ip_client():
    """IP của client để đếm rate limit (deploy trực tiếp, không qua proxy lạ)."""
    return request.httprequest.remote_addr or "unknown"


# ======================================================================
# Rate limit (đếm trong RAM — demo 1 worker)
# ======================================================================
def check_rate_limit(key, limit, window=60):
    """Đếm số lần gọi ``key`` trong ``window`` giây.

    Trả envelope lỗi (để controller trả 429) khi vượt ``limit``; trả ``None``
    khi còn trong hạn mức (đồng thời ghi nhận lượt gọi này).
    """
    now = time.time()
    hits = [t for t in _RATE_CACHE.get(key, []) if now - t < window]
    if len(hits) >= limit:
        _RATE_CACHE[key] = hits
        return loi(
            "RATE_LIMITED",
            _("Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút."),
        )
    hits.append(now)
    _RATE_CACHE[key] = hits
    if len(_RATE_CACHE) > 5000:  # chống phình RAM khi bị quét token
        _RATE_CACHE.clear()
        _RATE_CACHE[key] = [now]
    return None


# ======================================================================
# Token của app tài xế (không phải Bearer của Odoo 19 — xem ADR-004)
# ======================================================================
def tao_token():
    """Sinh token ngẫu nhiên 43 ký tự (32 byte urlsafe)."""
    return secrets.token_urlsafe(32)


def bam_token(token):
    """Băm token để lưu DB — DB **không bao giờ** giữ token gốc (docs/02 §6.3)."""
    return hashlib.sha256((token or "").encode("utf-8")).hexdigest()


def thoi_diem_het_han():
    """Thời điểm hết hạn của token vừa sinh (8 giờ, giờ UTC như Odoo lưu DB)."""
    return fields.Datetime.now() + timedelta(hours=TOKEN_TTL_GIO)


# ======================================================================
# Định dạng thời gian cho app (OQ-23: API format sẵn, app không parse Datetime)
# ======================================================================
def gio_phut(record, gia_tri):
    """``datetime`` (UTC trong DB) -> ``"HH:mm"`` theo múi giờ của người dùng."""
    if not gia_tri:
        return None
    return fields.Datetime.context_timestamp(record, gia_tri).strftime("%H:%M")


def thoi_diem_dia_phuong(record, gia_tri):
    """``datetime`` -> chuỗi ISO 8601 **giờ địa phương** (cho timeline công khai)."""
    if not gia_tri:
        return None
    return fields.Datetime.context_timestamp(record, gia_tri).isoformat(timespec="minutes")


# ======================================================================
# Ảnh / chữ ký gửi từ app
# ======================================================================
def giai_ma_anh(du_lieu_base64, nhan="Ảnh"):
    """Giải base64 + kiểm magic bytes, KHÔNG tin ``mimetype`` client gửi.

    Trả ``(bytes, mimetype)``. Raise ``UserError`` tiếng Việt nếu sai.
    """
    if not du_lieu_base64:
        raise UserError(_("%s: không có dữ liệu.", nhan))
    try:
        raw = base64.b64decode(du_lieu_base64, validate=True)
    except (binascii.Error, TypeError, ValueError) as exc:
        raise UserError(_("%s: dữ liệu base64 không hợp lệ.", nhan)) from exc
    if len(raw) > ANH_TOI_DA_BYTES:
        raise UserError(_("%s: vượt quá 5 MB, vui lòng chụp lại ở chế độ thường.", nhan))
    if raw[:3] == b"\xff\xd8\xff":
        return raw, "image/jpeg"
    if raw[:8] == b"\x89PNG\r\n\x1a\n":
        return raw, "image/png"
    raise UserError(_("%s: chỉ chấp nhận ảnh JPEG hoặc PNG.", nhan))


def so_nguyen(gia_tri, nhan, toi_thieu=0):
    """Ép giá trị người dùng gửi thành số nguyên, lỗi thì báo tiếng Việt."""
    if gia_tri in (None, "", False):
        return 0
    try:
        so = int(float(gia_tri))
    except (TypeError, ValueError) as exc:
        raise UserError(_("%s không hợp lệ: %s", nhan, gia_tri)) from exc
    if so < toi_thieu:
        raise UserError(_("%s không được nhỏ hơn %s.", nhan, toi_thieu))
    return so


def so_thuc(gia_tri, nhan, toi_thieu=0.0):
    """Ép giá trị người dùng gửi thành số thực, lỗi thì báo tiếng Việt."""
    if gia_tri in (None, "", False):
        return 0.0
    try:
        so = float(gia_tri)
    except (TypeError, ValueError) as exc:
        raise UserError(_("%s không hợp lệ: %s", nhan, gia_tri)) from exc
    if so < toi_thieu:
        raise UserError(_("%s không được nhỏ hơn %s.", nhan, toi_thieu))
    return so
