## Dùng khi nào

Làn 2 — khi handoff có file `.py` hoặc `.xml` thuộc `addons/lab_*/` (BE-01 handoff, FE-01 sửa view).
Chạy **sau** `01-native-first-audit.md` (đã qua cổng Reuse Proof).
Đầu ra: issue P0/P1 kèm `file:dòng` + câu nói cụ thể + đề xuất sửa. RV-01 **không** sửa code.

---

## 1. Thứ tự đọc một module (7 bước, đừng đọc model trước)

`__manifest__.py` → `security/ir.model.access.csv` → `models/*.py` → `views/*.xml` → `data/*.xml` → `report/*.xml` → `tests/` → `i18n/vi.po`.
Lý do: manifest sai thì mọi thứ sau vô nghĩa; i18n đọc cuối nhưng **bắt buộc** đọc (Luật 6).

## 2. Manifest — checklist

| Kiểm | Đúng ở 17.0 | Cách phát hiện | Mức |
|---|---|---|---|
| `version` | `'17.0.x.y.z'` (khớp thứ tự cài `lab_core` → `lab_chuyen` → `lab_laixe` → `lab_hanhtrinh` → `lab_api`) | `rg "version" addons/lab_*/__manifest__.py` | P1 |
| `license` | `'LGPL-3'` | manifest thiếu key | P2 |
| `depends` | đúng, không depends module `lab_*` khác chiều (xem `docs/02` §1) | đọc `depends` vs sơ đồ module | P0 |
| `data` | liệt kê đủ file, **thứ tự security → data → views → report** | file XML có mà không khai | P1 |
| `installable` / `application` | `True` / `False` (trừ `lab_core`) | — | P2 |
| EE API | không có `studio`, `documents`, `sign`, `helpdesk` bản EE | `rg "helpdesk\b" addons/lab_*/__manifest__.py` | P0 |

## 3. Model: convention

- `_description` có ở **mọi** model — thiếu → **P1** (nói: "Odoo hiển thị tên model ở error message, thiếu `_description` là lỗi convention").
- `_order` có ở model có list view — thiếu → thứ tự list ngẫu nhiên → **P1**.
- 17.0 **vẫn dùng** `_sql_constraints`; thấy `models.Constraint` → **P0** (API 18.0).
- `_name` snake_case có prefix `lab_`; 1 class / 1 file; `from odoo import models, fields, api, _`.
- Field: không viết tắt (`lab_kl` → `lab_weight_kg`); `compute` phải `store=True` khi dùng để `search`/`group_by`/hiện trên list.

## 4. `@api.depends` và `store=True`

| Lỗi | Cách phát hiện | Nói gì | Sửa gì |
|---|---|---|---|
| Thiếu depends | `rg -n "def _compute_" -A3 addons/lab_*/models`: thấy field dùng trong thân hàm mà không có trong `@api.depends` | "Compute sẽ không cập nhật khi field nguồn đổi" | Thêm đủ đường dẫn (`line_ids.khoi_luong_kg`, `line_ids.state`) |
| `store=True` bừa | compute chỉ dùng để hiển thị, không search/group | "Tốn cột DB + trigger, không lợi gì" | Bỏ `store`, hoặc giữ nếu list view có `group_by` |
| `store=True` thiếu depends | `store=True` + `@api.depends` rỗng/thiếu | "Cột DB đóng băng, sai số liệu" | **P1** — sai số liệu nghiệp vụ (bảng REA) |
| `search()` trong `for` | `rg -n "for .* in .*:" -A5 addons/lab_*/models` có `search(` | "N+1, 1.000 đơn là treo list" | Gộp 1 `search` ngoài loop / `search_read` / `mapped` |
| `_compute` gán vào `self` nhiều record | gán `record.x` trong `for record in self` | OK về cú pháp, nhưng phải có `for record in self` | — |

## 5. State machine (Luật 5 + `docs/02` §3)

| Kiểm | Fail nếu | Mức |
|---|---|---|
| Không nhảy cóc | `action_*` không `raise UserError` khi state sai | P0 |
| Mọi transition có `message_post` | chuyển state mà chatter im lặng | P1 |
| Nút transition trong `<header>` `type="object"` + `statusbar` | nút nằm ngoài header / dùng `type="action"` cho method | P2 |
| Không có state dead-end | state không có đường ra (trừ `cancelled`/`closed` là trạng thái cuối) | P0 |
| Không `unlink` mất vết nghiệp vụ | `unlink()` line/picking để "gỡ" → dùng `state='waitlist'`/`removed` (`docs/07` ②) | P0 |
| Guard là `UserError` có tiếng Việt | `raise Exception` / tiếng Anh | P1 |

## 6. Transaction safety

`rg -n "cr\.commit\(\)\|cr\.rollback\(\)" addons/lab_*` → **rỗng** (P0). Odoo quản lý transaction theo request; commit thủ công làm hỏng rollback và test.
`self.env.cr.execute` chỉ được dùng khi ORM không làm được, **luôn** có tham số: `self.env.cr.execute("... WHERE id = %s", (x,))` — f-string/`%` nối chuỗi → **P0** (SQL injection).
Không gọi `flush()` lung tung; không `_cr` trực tiếp.

## 7. Report — QWeb, không Python PDF

Đúng: `ir.actions.report` (`report_type='qweb-pdf'`) + template QWeb trong `report/*.xml`, tái dùng `web.external_layout`, `t-foreach` trên recordset.
Sai → **P0**: ReportLab/fpdf/`base64` PDF trong Python, `wkhtmltopdf` gọi tay, HTML tự sinh trong controller.
Font tiếng Việt: nhúng font Unicode (DejaVu/Roboto) trong `lab_core/static/fonts` + `@font-face` trong CSS report — thiếu → **P1** ("PDF sẽ ra ô vuông khi in AWB").

## 8. i18n (Luật 6 — không cắt để tiết kiệm thời gian)

- Mọi chuỗi user-facing trong Python bọc `_("…")`; `rg -n "raise UserError\('" addons/lab_*` hoặc `'…'` không `_()` → P1.
- `i18n/vi.po` tồn tại và **có msgstr** thật (không để trống): `rg -c 'msgstr ""' addons/lab_*/i18n/vi.po`.
- Nhãn trong XML view: 17.0 tự trích xuất được, nhưng label phải tiếng Việt đúng glossary (`docs/00` §10).

## 9. Migration

Đổi schema/field trên model đã có dữ liệu → **bắt buộc** `migrations/17.0.x.y/` (folder = version mới trong manifest), file kiểu `pre-<mô-tả>.py` / `post-<mô-tả>.py` (tên có gạch nối, không dấu, không space), mỗi file có `def migrate(cr, version):`.
Không có migration mà đổi kiểu field → **P1** ("upgrade DB demo sẽ lỗi hoặc mất dữ liệu"). Chỉ khi demo DB trắng thì hạ P2, phải ghi rõ.

## 10. OWL 2 (17.0), không phải OWL 1

Đúng: `useState`, `props.record.data[props.name]`, `patch` từ `@web/core/utils/patch`, `registry.category("fields").add(...)`.
Sai → P1: `this.state`, `props.value`, `useState` cũ, `Component.env`… Thấy `-A` import từ `web/owl` mà dùng `this.state` → P0 (component sẽ không re-render).

## 11. Bảng API 17.0-only (đã kiểm chứng — được phép khẳng định)

| ❌ Sai ở 17.0 | ✅ 17.0 |
|---|---|
| `attrs=`, `states=` trong view | `invisible="state == 'draft'"` (bị hard-remove, `_check_xml` raise) |
| `quantity_done` / `qty_done` | `quantity` (trên `stock.move`) + `picked` |
| `states={...}` kwarg trên `fields.*` | chuyển `readonly` vào view |
| `read_group(...)` | `_read_group(domain, groupby, aggregates)` |
| `SavepointCase` | `TransactionCase` / `SingleTransactionCase` / `HttpCase` |
| `stock.package` | `stock.quant.package` (type vẫn `stock.package.type`) |
| `<list>` | `<tree>` |
| `hard_lock_date` | `period_lock_date` / `fiscalyear_lock_date` |
| `is_storable` | `product.template.type == 'product'` |
| OWL 1 `this.state` | OWL 2 `useState` + `patch` |
| `Authorization: Bearer` (native) | API key thay password; `session_id` chỉ qua cookie |
| `models.Constraint` | `_sql_constraints` |

## 12. Phân mức P0 / P1 cho làn này

**P0 (chặn release/demo, không thương lượng):** viết lại Odoo · `attrs`/`states` · `quantity_done` · API 18/19 (`models.Constraint`, `is_storable`, `hard_lock_date`) · `has_group` trong logic · `cr.commit()` · SQL nối chuỗi · state nhảy cóc hoặc dead-end · report tự sinh PDF · depends EE.
**P1 (phải sửa trước handoff):** thiếu test cho transition · thiếu `_description`/`_order` · thiếu `message_post` · thiếu `_()` · thiếu migration khi đổi schema · font PDF · N+1 rõ ràng.
**P2:** đặt tên, format, `store=True` dư. **P3:** ý tưởng tối ưu.

## Checklist nhanh

- [ ] Manifest: `version='17.0.x.y.z'`, `license`, `depends` đúng chiều, `data` đủ thứ tự
- [ ] Mọi model có `_description`; list view có `_order`
- [ ] `@api.depends` phủ hết field dùng trong compute; `store=True` có lý do
- [ ] Mọi `action_*` có guard `UserError` + `message_post`; không dead-end
- [ ] `rg "cr\.commit\(\)"` rỗng; không SQL nối chuỗi; không `eval`
- [ ] Report là QWeb + `ir.actions.report`, font Unicode đã nhúng
- [ ] `i18n/vi.po` có msgstr thật; không chuỗi user-facing thiếu `_()`
- [ ] Không có API 16/18/19 trong bảng §11 (đã grep, dán log vào review file)
