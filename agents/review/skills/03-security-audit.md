## Dùng khi nào

Làn 3 — chạy trong **mọi** review, bắt buộc khi handoff chạm `security/**`, controller `lab_api`, model có `ir.rule`, hoặc bất kỳ endpoint/portal/token nào.
Đầu ra: **bằng chứng đăng nhập thật** (log test hoặc ảnh màn hình 2 user khác phòng ban) + issue P0 cho mọi lỗ hổng.
Lỗ hổng quyền = **P0 BLOCKED** (`agents/review/AGENT.md` §4). Không có "demo nội bộ nên bỏ qua".

---

## 1. Bốn lớp quyền — kiểm đủ, không thay thế nhau

| Lớp | Cơ chế 17.0 | File | Câu hỏi kiểm | Lỗ hay gặp |
|---|---|---|---|---|
| Record | `ir.rule` (có `groups`) | `security/*_security.xml` | "User A có thấy record của B không?" | Chỉ có ACL, thiếu rule → thấy hết |
| Field | `groups="…"` trong view | `views/*.xml` | "Field giá vốn/lãi có bị che không?" | Rule ẩn field nhưng RPC vẫn đọc được → phải dùng `groups` trên **field model** nếu cần chặn thật |
| Menu | `groups` trên `<menuitem>` | `views/*_menus.xml` | "Menu của phòng ban khác có hiện không?" | Menu ẩn nhưng action/URL vẫn mở được — chấp nhận được **chỉ khi** layer record+ACL đúng |
| ACL | `ir.model.access.csv` | `security/ir.model.access.csv` | "Đủ 4 dòng/nhóm chưa?" | `perm_write=1` cho nhóm chỉ đọc (CS sửa Debit) |

**Cấm tuyệt đối:** `if not self.env.user.has_group(...): raise UserError` trong business logic (Luật 3). `rg -n "has_group" addons/lab_*/models` phải **rỗng**; có hit → P0.

## 2. Tám nhóm quyền (`docs/02` §4.1) — kiểm theo phạm vi thấy

| Group | Kế thừa | Được thấy | Không được thấy |
|---|---|---|---|
| `lab_core.group_cs` | `sales_team.group_sale_salesman` | `sale.order` của mình, `lab.chuyen` (R) | giá vốn, `account.move` của phòng khác |
| `lab_core.group_driver` | `base.group_portal` | **chỉ** `lab.yeu.cau.lay.hang` có `driver_id.user_id = user.id` | backend, chuyến, giá, dữ liệu tài xế khác |
| `lab_core.group_warehouse` | `stock.group_stock_user` | `stock.picking`, `stock.quant.package`, `lab.chuyen` (RW) | `account.move` (ghi) |
| `lab_core.group_docs` | `lab_core.group_warehouse` | `lab.hanh.trinh` (RW), manifest | tiền, cước |
| `lab_core.group_account` | `account.group_account_invoice` | `account.move` (RW), Debit | `lab.yeu.cau.lay.hang` (ghi) |
| `lab_core.group_sale_admin` | `lab_core.group_cs` | duyệt vào chuyến, duyệt giảm tải | mở khoá Debit |
| `lab_core.group_manager` | `group_sale_admin` + `account.group_account_manager` | duyệt tier ⑥/⑭/②, mở khoá Debit | — |
| `lab_core.group_director` | `base.group_user` | dashboard, read-only | mọi ghi nghiệp vụ |

## 3. Năm phép thử bắt buộc (không có bằng chứng = FAIL)

| # | Phép thử | Kỳ vọng | Bằng chứng nộp kèm |
|---|---|---|---|
| S1 | Login tài xế A → `search` `lab.yeu.cau.lay.hang` | Chỉ thấy job của A; `read` job của B → `AccessError` | log `test_security.py` |
| S2 | Login CS → tìm `account.move` của shop phòng khác | 0 record, `read` → `AccessError` | ảnh/log |
| S3 | `GET /lab/api/v1/track/<token>` bằng `curl` không đăng nhập | Chỉ trả field whitelist; **không** có giá, `partner_id` nội bộ, tên tài xế, `access_token` của record khác | dán JSON thật |
| S4 | Login `group_director` → sửa `lab.chuyen` | `AccessError` (read-only thật, không chỉ ẩn nút) | log |
| S5 | Login tài xế → mở URL backend `/web#model=lab.chuyen` | Bị chặn/không có quyền; không thấy dữ liệu | ảnh |

Thử bằng UI (đúng DoD D4: "đăng nhập user phòng ban khác → không thấy dữ liệu chéo") **và** bằng test.

## 4. Outline `test_security.py` (RV viết vào issue, BE-01 áp dụng)

```python
from odoo.exceptions import AccessError
from odoo.tests import TransactionCase, tagged, new_test_user

@tagged('post_install', '-at_install')
class TestSecurity(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.shop_khac = cls.env['res.partner'].create({'name': 'Shop phòng khác'})
        cls.taixe_a = new_test_user(cls.env, login='tx_a', name='Tài xế A',
                                    groups='base.group_portal,lab_core.group_driver')
        cls.taixe_b = new_test_user(cls.env, login='tx_b', name='Tài xế B',
                                    groups='base.group_portal,lab_core.group_driver')
        cls.cs = new_test_user(cls.env, login='cs1', name='CS 1',
                               groups='base.group_user,lab_core.group_cs')
        emp = cls.env['hr.employee']
        cls.job_a = cls.env['lab.yeu.cau.lay.hang'].create({
            'partner_id': cls.env['res.partner'].create({'name': 'Shop Sen Đá'}).id,
            'driver_id': emp.create({'name': 'Tài xế A', 'user_id': cls.taixe_a.id}).id,
        })
        cls.job_b = cls.job_a.copy({'driver_id': emp.create({'name': 'Tài xế B', 'user_id': cls.taixe_b.id}).id})

    def test_driver_a_khong_thay_job_cua_b(self):
        Job = self.env['lab.yeu.cau.lay.hang'].with_user(self.taixe_a)
        self.assertEqual(Job.search([('id', '=', self.job_b.id)]).ids, [])
        with self.assertRaises(AccessError):
            Job.browse(self.job_b.id).read(['name'])

    def test_cs_khong_thay_account_move_phong_khac(self):
        Move = self.env['account.move'].with_user(self.cs)
        self.assertFalse(Move.search([('partner_id', '=', self.shop_khac.id)]))
```

Bắt buộc có: 1 case **được thấy** + 1 case **bị chặn** cho mỗi cặp (role × model) trong ma trận `docs/02` §4.2. Chỉ test "được thấy" → P1 (không chứng minh được cách ly).

## 5. Review `sudo()`

Mỗi `sudo()` phải trả lời được 4 câu: (1) user nào chạy nhánh này, (2) vì sao `ir.rule` của chính user đó không đủ, (3) có bị lộ dữ liệu khác không, (4) ai duyệt.
Chấp nhận được: sinh sequence, sinh token portal (`_portal_ensure_token`), đọc whitelist cho `/track`, ghi attachment đã validate.
**Không** chấp nhận (P0): `sudo()` để list view thấy dữ liệu phòng khác · `sudo()` trong controller để "đỡ phải viết `ir.rule`" · `sudo()` bọc quanh `search` rồi trả cả recordset ra API.

## 6. Upload endpoint (`/lab/api/v1/driver/jobs/<id>/capture`)

| Kiểm | Đúng | Fail |
|---|---|---|
| Kích thước | ≤ **5MB**/file, chặn trước khi đọc hết body | không giới hạn → P0 |
| Loại file | Validate **magic bytes** (`FFD8FF` JPEG, `89504E47` PNG); **không tin** `mimetype` client gửi | tin `Content-Type` → P0 |
| Lưu | `ir.attachment` (`res_model`/`res_id`), ảnh nén sẵn từ app | base64 trong field Char/Text → P1 |
| Tên file | Không dùng tên client làm path; không cho `.exe/.html/.svg` | path traversal → P0 |
| Quyền | Tài xế chỉ upload được cho job của mình (đối chiếu `driver_id.user_id`) | upload chéo job → P0 |
| Trùng lặp | `client_uuid` idempotent (`docs/04` §8) — gửi 2 lần không tạo 2 bộ ảnh | thiếu → P1 |

## 7. Token (driver + track)

Hết hạn **8h**, lưu **hash** trong DB, **không log** token, rate limit theo IP + token (cache), rotate khi login lại, không trả `access_token` của người khác (`docs/02` §6.3).
Kiểm: `rg -n "access_token\|session_token\|token" addons/lab_api` → mọi chỗ log/print là P0. Token trong URL query mà không hết hạn → P1.

## 8. Injection & eval

`rg -n "cr\.execute\(|safe_eval|eval\(|exec\(" addons/lab_api addons/lab_*` → mỗi hit đọc tay.
SQL phải tham số hoá: `cr.execute("SELECT id FROM x WHERE a = %s", (val,))`. Nối chuỗi/f-string → **P0**.
`safe_eval` chỉ với domain tĩnh của hệ thống, **không bao giờ** với input người dùng → P0.

## 9. Secrets & auth flow

`rg -n "vn_sk_|api[_-]?key\s*=|password\s*=\s*['\"]|BEGIN (RSA|PRIVATE)|postgres://" addons/ apps/kmp infra/` → phải rỗng (trừ `.env` mẫu + `infra/odoo.conf` có placeholder).
Nhắc lại sự thật 17.0 (được khẳng định): **API key thay password** (`res.users.apikeys`), **`session_id` chỉ trả qua cookie**, **17.0 không có `Authorization: Bearer` cho API native** — nếu `lab_api` dùng Bearer thì đó là controller tự dựng, phải ghi ADR-004 và phải tự làm hết (expiry, hash, rate limit).
App **không** lưu password người dùng; không `sudo` khi gọi `execute_kw` (quyền lấy từ user).

## 10. Phân mức + mẫu issue

**P0:** thấy dữ liệu chéo · thiếu `ir.rule` cho model có ACL · `has_group` trong logic · upload không validate · SQL nối chuỗi · secret trong code · token không hết hạn/không hash · portal trả field nội bộ.
**P1:** thiếu test security · thiếu 4 dòng ACL cho 1 nhóm · rate limit thiếu.
**P2:** tên rule không rõ, comment thiếu. **P3:** hardening thêm.
Mẫu issue: 7 ô theo `agents/review/AGENT.md` §5 — nêu rõ **phép thử S1–S5 nào đang đỏ** + log làm bằng chứng + phạm vi chặn (vd: "chặn toàn bộ P4 portal cho tới khi có test security").

## Checklist nhanh

- [ ] ACL đủ dòng cho 8 nhóm; không `perm_write` cho nhóm read-only
- [ ] Mỗi model có `ir.rule`; `rg "has_group" addons/lab_*/models` rỗng
- [ ] S1–S5 có log/ảnh làm bằng chứng, không chỉ nói miệng
- [ ] Field nhạy cảm (giá vốn, lãi, đối tác nội bộ) có `groups`
- [ ] Upload: ≤5MB, magic bytes, lưu `ir.attachment`, chống trùng `client_uuid`
- [ ] Token: 8h, hash, không log, rate limit; `/track` chỉ trả whitelist
- [ ] Không secret trong `addons/` `apps/kmp` `infra/`; app dùng API key, giữ cookie `session_id`
- [ ] Mọi lỗ hổng đã thành issue P0 kèm phạm vi chặn + người áp dụng (RV không sửa code)
