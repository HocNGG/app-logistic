# 14 — Performance Odoo 17 (list chậm, form chậm, API chậm)

## Dùng khi nào

Khi list view > 1s, form mở chậm, endpoint quá 500ms, hoặc trước khi thêm field compute thứ 5 vào một model.

---

## 1. Computed: `store=True` hay không — quyết định bằng "ai đọc field này"

| Field được dùng để | `store` | Vì sao |
|---|---|---|
| filter / sort / group_by / pivot / `ir.rule` | **True** | không store = không search được (lỗi `Non-stored field cannot be searched`) |
| hiển thị trên form (tỉ lệ %, badge) | False | tránh ghi DB + tránh recompute khi `-u` |
| hiển thị trên **list** nhiều dòng | True (hoặc bỏ khỏi list) | mỗi dòng gọi compute = N lần Python |

```python
# ĐÚNG: tổng hợp phải store
tai_trong_da_dung_kg = fields.Float(compute="_compute_tai_trong", store=True)

# ĐÚNG: tỉ lệ % chỉ hiển thị
ti_le_tai = fields.Float(compute="_compute_tai_trong")
```

Stored compute = ghi khi dependency đổi; nếu `@api.depends` **thiếu** một nhánh, giá trị cũ nằm lại DB (bug im lặng, không phải bug hiệu năng — nhưng càng khó tìm hơn).

## 2. `_read_group` thay `read_group` (17.0)

```python
# 17.0: _read_group(domain, groupby, aggregates) — trả list các tuple, KHÔNG trả dict cũ
groups = self.env["lab.chuyen.line"]._read_group(
    domain=[("chuyen_id", "in", chuyens.ids), ("state", "=", "booked")],
    groupby=["chuyen_id"],
    aggregates=["khoi_luong_kg:sum", "so_kien:sum", "__count"],
)
for chuyen, kg, kien, count in groups:
    chuyen.the_tich_da_dung_cbm = ...      # dùng dữ liệu đã gom, không vòng lặp record
```

- `read_group(...)` là API 16 — dùng trong 17 sẽ đi qua shim deprecated rồi bị bỏ; `rg -n "read_group\(" addons/` phải rỗng.
- `aggregates` nhận `'field:sum'`, `'field:avg'`, `'field:max'`, `'__count'`.
- Dùng `_read_group` cho **mọi** chỗ cần con số tổng: KPI dashboard, manifest, kiểm tải chuyến.

## 3. Prefetch và N+1 — 2 lỗi làm API chậm nhất

```python
# ❌ N+1 trong search_read: mỗi vòng lặp có thể là 1 query
for job in jobs:
    data.append({"shop": job.partner_id.display_name, "driver": job.driver_id.user_id.login})

# ✅ 1 lần đọc, đủ field cần
jobs = self.env["lab.yeu.cau.lay.hang"].search_read(
    [("driver_id.user_id", "=", self.env.user.id)],
    fields=["name", "partner_id", "dia_chi_lay", "state", "so_kien_thuc_te"],
    limit=50, order="khung_gio_tu asc",
)
```

- Prefetch: truy cập `record.partner_id` cho **cả recordset** là 1 query, nhưng `for rec in records: rec.partner_id` xen kẽ `search()` bên trong vòng lặp sẽ phá prefetch. Quy tắc: **không `search()` trong vòng lặp** (`rg -n "for .*:\n.*search\(" ` — đọc bằng mắt trong review).
- Cần bỏ prefetch có chủ đích (bảng lớn): `.with_context(prefetch_fields=False)`.
- `mapped()`/`filtered()` trên recordset dùng cache, nhanh hơn list-comprehension gọi field từng record.

## 4. Index và `_order`

```python
lab_chuyen_id = fields.Many2one("lab.chuyen", index=True)      # filter theo chuyến: rất nhiều
state = fields.Selection([...], index=True)                    # filter theo trạng thái
driver_id = fields.Many2one("hr.employee", index=True)
khung_gio_tu = fields.Datetime(index=True)                     # sort theo giờ hẹn
_order = "khung_gio_tu asc, id asc"
```

Sự thật: **`_order` không tạo index.** `_order` chỉ định `ORDER BY`; nếu sort trên bảng lớn mà field không index → sort in-memory. Thêm `id` vào `_order` để phân trang ổn định (tránh trùng/thiếu dòng khi `limit`). Đừng index mọi field: mỗi index làm chậm `create`/`write`.

## 5. Vì sao **không** đặt logic trong `onchange`

- `onchange` chỉ chạy trên **web client**; `create()`/`write()` từ API, import, hay test **không** chạy nó → dữ liệu sai lệch giữa UI và API.
- `onchange` chạy lại toàn bộ mỗi lần field liên quan đổi → form nhiều field = giật, và có thể gọi RPC mỗi keystroke (`onchange` với `@api.onchange` + `warning`).
- Đúng: ràng buộc dữ liệu ở `@api.constrains`/`_sql_constraints`; tính toán ở `compute` (store nếu cần); hành vi người dùng ở `action_*`. `onchange` chỉ để **gợi ý** giá trị mặc định.

## 6. Attachment & ảnh (app tài xế chụp 5MB/kiện)

- Lưu `ir.attachment` (field `datas` binary), **không** lưu base64 trong `Char`/`Text` → nếu không, mỗi lần đọc list kéo theo MB.
- Hiển thị thumbnail: URL `/web/image/<attachment_id>?height=120&width=120` (Odoo tự resize + cache), không render ảnh gốc trong list.
- Ảnh chữ ký: PNG nhỏ; ảnh hàng hoá: resize server-side nếu > 2MB trước khi lưu vào attachment phụ, giữ bản gốc nếu cần bằng chứng.
- Đừng đặt `ir.attachment` inline trong QWeb report với ảnh lớn → PDF 40MB, wkhtmltopdf timeout.

## 7. Năm triệu chứng cụ thể của dự án + cách sửa

| # | Triệu chứng | Nguyên nhân thật | Sửa |
|---|---|---|---|
| 1 | List "Chuyến" 2.000 dòng mất 8–15s | `ti_le_tai` non-stored + compute duyệt `line_ids` cho từng dòng | `tai_trong_da_dung_kg`/`the_tich_da_dung_cbm` `store=True` + `depends` đủ; `ti_le_tai` bỏ khỏi list hoặc store |
| 2 | Mở form chuyến 500 đơn: 6–10s | o2m `line_ids` load hết + prefetch field nặng | view o2m đặt `limit="40"` (FE-01) + hiển thị tổng bằng `_read_group`; không đưa attachment vào form |
| 3 | `/lab/api/v1/driver/jobs` > 3s | N+1 (`job.partner_id`, `job.driver_id.user_id` trong vòng lặp) | `search_read` + `limit=50` + field related `store=True` (`partner_id`) |
| 4 | Report manifest 20s | Python `for chuyen in records: sum(...)` lặp record | 1 `_read_group` theo `chuyen_id` rồi render |
| 5 | Ghi job mới chậm 2s (app tài xế) | 5 ảnh 5MB lưu base64 trong field + `message_post` từng ảnh | `ir.attachment` + resize + 1 lần `message_post` với `attachment_ids` |

## 8. Cách đo (không đoán)

```python
# Đếm query trong test/dev — bật rồi xem số query của 1 hành động
self.env.cr.execute("SELECT 1")   # mốc
# Trong dev: thêm --log-level=debug_sql vào lệnh chạy, đếm số dòng "QUERY" quanh thao tác
```

Hoặc `odoo.tools.profiler`/`--dev=all` + trang Performance của Odoo (nếu bật). Quy tắc: **sửa sau khi đo** — mỗi tối ưu phải kèm 1 con số trước/sau vào PR.

## Checklist nhanh

- [ ] Mọi field dùng filter/sort/group đều `store=True` (hoặc không dùng để search).
- [ ] `@api.depends` đủ nhánh cho mọi stored compute.
- [ ] Không `read_group(` còn trong `addons/`; dùng `_read_group`.
- [ ] Không `search()`/`browse()` trong `for` trên recordset.
- [ ] API trả bằng `search_read` có `fields` + `limit` + `order`.
- [ ] Field filter/sort chính có `index=True`; `_order` có `id` phụ.
- [ ] Ảnh lưu `ir.attachment`, hiển thị bằng `/web/image?...&height=`, không base64 trong Char.
- [ ] PR có số đo trước/sau cho thay đổi hiệu năng (không "cảm thấy nhanh hơn").
