# 10 — API controllers (`lab_api`) cho app KMP

## Dùng khi nào

Khi thêm endpoint cho app tài xế / app CS / trang tra cứu công khai. Đọc §1 trước: phần lớn nhu cầu **không cần** controller.

## 1. Native trước — 3 câu hỏi cho API (Luật 1)

(1) Việc này `/web/dataset/call_kw` (JSON-RPC) làm được không? → **được thì không viết controller**. (2) Có phải 1 trong 4 việc native làm không tốt — (a) tài xế không nên có phiên web, (b) upload ảnh/chữ ký binary, (c) route tra cứu công khai tối giản, (d) payload "today's jobs" gọn cho mobile? (3) Quyền do `ir.rule` lo, hay đang định viết `if user.has_group(...)`?

**Auth chốt:** app CS dùng JSON-RPC + **API key** (17.0 **không** có `Authorization: Bearer` — đó là 19.0). Tài xế đăng nhập qua `/web/session/authenticate` với API key; `session_id` **chỉ** nằm trong cookie → Ktor phải giữ cookie jar (`docs/02` §6.3). `type='json'` khi payload là JSON (`request.jsonrequest` — ảnh base64, app mobile); `type='http'` khi cần status code/file (`request.make_json_response(..., status=...)`).

| `auth=` | `request.env` | Dùng cho |
|---|---|---|
| `'user'` | user đăng nhập (kể cả portal) | 4 endpoint tài xế — phạm vi do `ir.rule` |
| `'public'` | public user | tra cứu công khai theo `access_token` |
| `'none'` | **superuser** (bỏ mọi `ir.rule`) | gần như không bao giờ — tránh |

## 2. Khung chung: envelope + rate limit + decode ảnh

```python
import base64, time
from odoo import _
from odoo.exceptions import UserError

_RATE = {}          # {key: [timestamp, ...]} — demo 1 worker; production: Redis/DB

def ok(data=None):
    return {"ok": True, "data": data, "error": None}

def loi(code, message):
    return {"ok": False, "data": None, "error": {"code": code, "message": message}}

def check_rate_limit(key, limit=60, window=60):
    now, hits = time.time(), [t for t in _RATE.get(key, []) if time.time() - t < window]
    if len(hits) >= limit:
        _RATE[key] = hits
        return loi("RATE_LIMITED", _("Bạn thao tác quá nhanh, xin thử lại sau."))
    _RATE[key] = hits + [now]

def giai_ma_anh(b64_data, max_bytes=5 * 1024 * 1024):
    """Kiểm magic bytes — KHÔNG tin mimetype client gửi."""
    raw = base64.b64decode(b64_data or "", validate=True)
    if len(raw) > max_bytes:
        raise UserError(_("Ảnh vượt quá 5MB."))
    if raw[:3] == b"\xff\xd8\xff":
        return raw, "image/jpeg"
    if raw[:8] == b"\x89PNG\r\n\x1a\n":
        return raw, "image/png"
    raise UserError(_("Chỉ chấp nhận ảnh JPEG/PNG."))
```

## 3. 4 endpoint tài xế (`auth='user'` + `ir.rule` lo phạm vi)

```python
import base64
from odoo import http
from odoo.http import request
from ..tools import check_rate_limit, giai_ma_anh, loi, ok


class LabDriverApi(http.Controller):
    def _job(self, job_id):
        job = request.env["lab.yeu.cau.lay.hang"].browse(int(job_id)).exists()
        job and job.check_access_rule("read")   # ir.rule đã giới hạn; đây là chốt thứ 2
        return job

    @http.route("/lab/api/v1/driver/jobs", type="json", auth="user", methods=["POST"])
    def jobs(self, **kwargs):
        if (gioi_han := check_rate_limit("jobs:%s" % request.env.user.id, limit=120)):
            return gioi_han
        rows = request.env["lab.yeu.cau.lay.hang"].search_read(
            [("driver_id.user_id", "=", request.env.user.id),
             ("state", "in", ["assigned", "en_route", "picked_up"])],
            fields=["name", "partner_id", "dia_chi_lay", "khung_gio_tu", "state"],
            limit=50, order="khung_gio_tu asc")
        return ok([{"id": r["id"], "ref": r["name"], "shop": r["partner_id"][1],
                    "address": r["dia_chi_lay"], "window": r["khung_gio_tu"],
                    "so_kien": r["so_kien_thuc_te"], "state": r["state"]} for r in rows])

    @http.route("/lab/api/v1/driver/jobs/<int:job_id>/accept", type="json", auth="user", methods=["POST"])
    def accept(self, job_id, **kwargs):
        if not (job := self._job(job_id)):
            return loi("NOT_FOUND", "Không tìm thấy việc này.")
        job.action_driver_accept()             # logic nằm trên model — không viết lại ở đây
        return ok({"state": job.state})

    @http.route("/lab/api/v1/driver/jobs/<int:job_id>/capture", type="json", auth="user", methods=["POST"])
    def capture(self, job_id, anh_base64=None, chu_ky_base64=None,
                so_kien=None, khoi_luong_kg=None, **kwargs):
        if not (job := self._job(job_id)):
            return loi("NOT_FOUND", "Không tìm thấy việc này.")
        attachment_ids = []
        for ten, du_lieu in (("anh.jpg", anh_base64), ("chu-ky.png", chu_ky_base64)):
            if du_lieu:
                raw, mimetype = giai_ma_anh(du_lieu)
                # sudo: portal không có ACL trên ir.attachment — comment bắt buộc (Luật 3)
                att = request.env["ir.attachment"].sudo().create({
                    "name": "%s-%s" % (job.name, ten), "datas": base64.b64encode(raw),
                    "res_model": job._name, "res_id": job.id, "mimetype": mimetype})
                attachment_ids.append(att.id)
        job.action_driver_confirm(so_kien=so_kien, khoi_luong_kg=khoi_luong_kg,
                                  attachment_ids=attachment_ids)
        return ok({"state": job.state, "attachment_ids": attachment_ids})

    @http.route("/lab/api/v1/driver/jobs/<int:job_id>/fail", type="json", auth="user", methods=["POST"])
    def fail(self, job_id, ly_do=None, **kwargs):
        if not (job := self._job(job_id)):
            return loi("NOT_FOUND", "Không tìm thấy việc này.")
        if not ly_do:
            return loi("VALIDATION", "Phải nhập lý do không lấy được hàng.")
        job.action_driver_fail(ly_do=ly_do)
        return ok({"state": job.state})
```

**Vì sao không `sudo()` bừa:** nó bỏ qua **toàn bộ** `ir.rule` → tài xế A đọc được job của B. Dự án chỉ dùng ở 2 chỗ: (1) tạo `ir.attachment` như trên (portal không có ACL, có comment), (2) tra cứu công khai **sau khi** đã kiểm `access_token`.

## 4. Endpoint tra cứu công khai (`auth='public'` + `access_token`)

```python
from odoo.tools import consteq          # so sánh hằng thời gian (như portal của Odoo)


class LabTrackApi(http.Controller):

    @http.route("/lab/api/v1/track/<string:access_token>", type="http", auth="public",
                methods=["GET"], csrf=False, save_session=False)
    def track(self, access_token, **kwargs):
        if (gioi_han := check_rate_limit("track:%s" % access_token, limit=30)):
            return request.make_json_response(gioi_han, status=429)
        # sudo có chủ đích: public user không có quyền đọc lab.hanh.trinh; an toàn vì
        # tra theo token không đoán được + whitelist field bên dưới.
        ht = request.env["lab.hanh.trinh"].sudo().search(
            [("access_token", "=", access_token)], limit=1)
        if not ht or not consteq(ht.access_token, access_token):
            return request.make_json_response(loi("NOT_FOUND", "Không tìm thấy vận đơn."), status=404)
        return request.make_json_response(ok({        # whitelist — KHÔNG trả cả record
            "so_awb": ht.so_awb, "stage": ht.stage, "cang_di": ht.cang_di,
            "events": [{"moc": l.moc, "dia_diem": l.dia_diem} for l in ht.line_ids]}))
```

Endpoint `auth='public'` **không** được trả `id`, `partner_id`, giá, hay field nội bộ. Không so token bằng `==` (lộ timing), không quên kiểm lại sau `search`.

## 5. CORS: không cần

App KMP là HTTP client native (Ktor), không có same-origin policy → không cần `Access-Control-Allow-Origin`. Chỉ bật nếu có browser gọi khác origin, và khi đó whitelist origin cụ thể.

## 6. Test endpoint (`HttpCase` — Luật 7)

```python
@tagged("post_install", "-at_install")
class TestDriverApi(HttpCase):

    def test_track_sai_token_tra_404(self):
        res = self.url_open("/lab/api/v1/track/khong-ton-tai")
        self.assertEqual((res.status_code, res.json()["error"]["code"]), (404, "NOT_FOUND"))
```

Tests quyền của tài xế phải dùng `self.authenticate()`/`with_user()` — xem `11-testing-odoo.md` §5. `self.url_open(url, data=..., headers=...)` là API `HttpCase` 17.0; endpoint `type='json'` nhận body JSON, không nhận form-data.

## Checklist nhanh

- [ ] Mỗi endpoint đã trả lời 3 câu §1; không có endpoint nào chỉ để "cho tiện".
- [ ] Route đúng prefix `/lab/api/v1/...`; envelope `{ok, data, error}` thống nhất.
- [ ] `auth='user'` cho tài xế; phạm vi do `ir.rule`, **không** `has_group` trong controller.
- [ ] `sudo()` chỉ ở 2 chỗ đã nêu, có comment lý do; endpoint public trả whitelist.
- [ ] Upload: ≤ 5MB, kiểm magic bytes, ghi `ir.attachment` (không base64 vào `Char`).
- [ ] Có rate limit theo user/token; ghi rõ giới hạn demo 1 worker.
- [ ] Có `HttpCase` cho ≥ 1 endpoint (happy + 1 case sai token/quyền).
- [ ] Không bật CORS, không phát minh `Authorization: Bearer` (17.0 không có).
