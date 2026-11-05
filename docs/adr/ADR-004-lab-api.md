# ADR-004 — `lab_api` cho app KMP

**Trạng thái:** ACCEPTED · **Đề xuất:** BE-01 + FE-01

## Bối cảnh

App KMP cần 5 nhóm việc: đăng nhập, danh sách việc của tài xế, upload ảnh + chữ ký, timeline tra cứu công khai, KPI cho màn duo UI.

## Phương án

- **A.** Chỉ dùng JSON-RPC native (`/web/session/authenticate` + `/web/dataset/call_kw`) → đủ cho CS console, **không đủ** cho upload binary và không phù hợp để tài xế có phiên web.
- **B.** Viết REST/JSON đầy đủ cho mọi thao tác → **viết lại API của Odoo**, cấm (Luật 1).
- **C. Lai:** JSON-RPC native cho CS console; **4 endpoint custom** cho tài xế + 1 cho tracking công khai.

## Quyết định

**C.** Chỉ custom đúng những thứ native làm không tốt:

| Endpoint | Vì sao custom |
|---|---|
| `POST /lab/api/v1/auth/login` | tài xế không nên có phiên web backend |
| `GET /lab/api/v1/driver/jobs` | payload gọn cho mobile, gộp 1 lần gọi |
| `POST /lab/api/v1/driver/jobs/<id>/{accept,capture,fail}` | upload ảnh/chữ ký binary |
| `GET /lab/api/v1/track/<access_token>` | trang công khai tối giản, whitelist field |

## Hệ quả

- ✅ Bề mặt bảo mật nhỏ nhất; quyền vẫn do `ir.rule` + `_document_check_access` quyết định, **không** bypasse bằng sudo.
- ⚠️ **Odoo 17 không hỗ trợ `Authorization: Bearer`** (Bearer là 19.0 với `/json/2`) → token do **ta** cài trong controller: hash trong DB, hết hạn 8h, rate limit theo IP+token.
- ⚠️ `/web/session/authenticate` trả `session_id` **chỉ qua cookie** (không có trong JSON) → Ktor client phải giữ cookie jar. API key (`res.users.apikeys`) dùng **thay password**.
- ⚠️ Upload ảnh: base64 trong JSON **hoặc** multipart — chọn 1, giới hạn 5MB, validate magic bytes, không tin `mimetype` client.
- ⚠️ Timeline công khai chỉ trả field whitelist (không giá, không đối tác nội bộ, không tên tài xế).
- ⚠️ Nếu gộp `lab_api` vào `lab_core` (ADR-001) → giữ được con số "3 module nghiệp vụ + 1 keo".

## Chốt sau khi chạy thật (2026-10-28, BE-01)

- ✅ **Giữ `auth='user'` cho 4 endpoint tài xế** (portal đăng nhập native `/web/session/authenticate`, giữ cookie `session_id`) **+ header `X-Lab-Token`** (token 8h, hash trong DB); **không** chuyển sang `auth='public'` + `request.update_env(user=…)`. Lý do: chỉ `auth='user'` mới dựng `env` = user thật để **`ir.rule` tự áp dụng**; tự đổi env trong controller là tự chế cơ chế auth và dễ bỏ qua rule (Luật 3).
- ⚠️ **Portal không đọc được `hr.employee`** (Odoo 17 tự chuyển sang `hr.employee.public`, chỉ Internal User) → mọi endpoint chạm `driver_id` bị 403. Vì vậy `lab.yeu.cau.lay.hang` thêm cột stored `driver_user_id` (`related="driver_id.user_id"`) để lọc/so quyền và lấy tên tài xế; payload login trả `driver_id = False` + `driver_user_id` + `driver_name`. **Không** đổi `lab_core.group_driver` sang internal user (phá mục tiêu "tài xế không vào backend").

