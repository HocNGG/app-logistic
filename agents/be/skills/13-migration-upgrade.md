# 13 — Migration & upgrade (đổi schema thì phải có script)

## Dùng khi nào

Khi đổi tên/xoá field, đổi kiểu field, đổi ngữ nghĩa dữ liệu, sửa record `noupdate="1"` — hoặc khi `-u` chạy nhưng dữ liệu cũ sai/thiếu.

---

## 1. Tên file có **gạch nối**, thư mục là **version**

```
addons/lab_chuyen/
├── __manifest__.py                 # "version": "17.0.2.0"
└── migrations/
    └── 17.0.2.0/                   # = version mới trong manifest
        ├── pre-migrate.py          # ĐÚNG (gạch nối)
        ├── post-migrate.py         # ĐÚNG
        └── end-migrate.py          # tuỳ chọn
```

- **`pre_migrate.py` (gạch dưới) bị BỎ QUA âm thầm** — không có log, không có lỗi, dữ liệu không được migrate. Đây là lỗi số 1 của kỹ thuật này.
- Tên thư mục = version đã bump. Bump lên `17.0.2.0` thì thư mục là `17.0.2.0`; nếu manifest để `17.0.2.0.0` thì thư mục `17.0.2.0.0` (Odoo chuẩn hoá version khi so sánh, nhưng đừng bắt người sau phải nhớ).
- Script chạy khi version cài đặt < version thư mục ≤ version mới. Không bump version → **không có migration nào chạy**.
- Thứ tự: `pre-migrate` (trước `_auto_init` của module) → `_auto_init` (thêm cột/constraint) → `post-migrate` (dữ liệu, ORM dùng được) → `end-migrate` (sau khi tất cả module xong).

## 2. Chữ ký hàm — chính xác là `migrate(cr, version)`

```python
# addons/lab_chuyen/migrations/17.0.2.0/pre-migrate.py
import logging

from odoo import SUPERUSER_ID, api

_logger = logging.getLogger(__name__)


def migrate(cr, version):
    """version = version đang cài TRƯỚC khi upgrade (có thể None ở DB rất cũ)."""
    if not version:
        return
    _logger.info("lab_chuyen: migrate từ %s lên 17.0.2.0", version)
    # Ở pre-migrate: schema CŨ vẫn còn, ORM mới có thể CHƯA khớp → ưu tiên SQL thuần.
    cr.execute("""
        SELECT column_name FROM information_schema.columns
        WHERE table_name = 'lab_chuyen' AND column_name = 'tai_trong_kg'
    """)
    if cr.fetchone():
        cr.execute("ALTER TABLE lab_chuyen RENAME COLUMN tai_trong_kg TO tai_trong_max_kg")
```

```python
# addons/lab_chuyen/migrations/17.0.2.0/post-migrate.py
from odoo import SUPERUSER_ID, api


def migrate(cr, version):
    env = api.Environment(cr, SUPERUSER_ID, {})
    # Ở post-migrate: schema mới đã có, ORM dùng được → dùng env[...] cho dễ đọc.
    chuyens = env["lab.chuyen"].search([("tai_trong_da_dung_kg", "=", 0.0)])
    for chuyen in chuyens:
        booked = chuyen.line_ids.filtered(lambda l: l.state == "booked")
        chuyen.tai_trong_da_dung_kg = sum(booked.mapped("khoi_luong_kg"))
```

Quy tắc: `cr.execute` với **tên bảng/cột cứng** (SQL không biết đổi tên model); không commit trong script; không gọi `cr.execute` với f-string từ input người dùng.

## 3. Khi nào **phải** bump version (+ có migration) và khi nào không

| Thay đổi | Bump? | Migration? |
|---|---|---|
| Thêm field mới (không bắt buộc) | nên | không — `_auto_init` thêm cột |
| Thêm field `required`/`compute store` | có | thường không: Odoo tự recompute; nếu > 100k dòng → post-migrate theo lô |
| **Đổi tên** field | có | **có** (`ALTER TABLE ... RENAME COLUMN`) — Odoo không tự đổi tên |
| Đổi kiểu (Char→Selection, Float→Int) | có | có (pre hoặc post, kèm cast dữ liệu) |
| Đổi ngữ nghĩa dữ liệu (kg → gam) | có | có (post-migrate, theo lô) |
| Xoá field | có | không bắt buộc (Odoo để lại cột + warning); dọn ở post-migrate nếu muốn sạch |
| Sửa record **`noupdate="1"`** (sequence, activity type, tier definition) | có | **có** — `-u` **không** ghi đè chúng |
| Chỉ sửa logic Python / view | có (patch) | không |
| Sửa `data/*.xml` **không** `noupdate` | có | không — `-u` load lại và ghi đè |

Quy ước version của dự án: `17.0.<minor>.<feature>.<patch>`; bump `minor` mỗi sprint. Commit migration **cùng** commit đổi model — không để "migrate sau".

## 4. `noupdate="1"` — hành vi phải nhớ

```xml
<odoo noupdate="1">
  <record id="seq_lab_chuyen_air" model="ir.sequence">
    <field name="name">Chuyến bay</field>
    <field name="code">lab.chuyen.air</field>
    <field name="prefix">CH/AIR/%(year)s/</field>
    <field name="padding">4</field>
  </record>
</odoo>
```

- Lần cài đầu: record được tạo, `ir.model.data.noupdate = True`.
- Các lần `-u` sau: record **không bị ghi đè** (đúng ý: người dùng có thể sửa số bắt đầu, prefix trên UI mà không bị reset).
- Hệ quả: sửa XML rồi `-u` sẽ **không có tác dụng**. Ba cách xử lý:
  1. post-migrate ghi trực tiếp (`env.ref('lab_chuyen.seq_lab_chuyen_air').write({...})`) — cách chuẩn;
  2. tạo record mới với id mới và migrate dữ liệu;
  3. (chỉ trên DB dev/demo) xoá `ir_model_data` của record rồi `-u` — **không** làm trên DB có dữ liệu thật.
- Không bọc `noupdate="1"` cho `ir.rule`, `ir.model.access.csv`, `res.groups`: đó là **hợp đồng bảo mật**, phải được cập nhật mỗi lần `-u`.

## 5. Quy trình chạy & kiểm

```powershell
# 0) Snapshot DB trước khi migrate (Luật 8: trước demo phải snapshot; xem lệnh trong README/infra)
powershell -File infra/scripts/reset-demo.ps1

# 1) Bump version trong __manifest__.py, thêm migrations/17.0.2.0/pre-migrate.py
# 2) Chạy nâng cấp (KHÔNG dùng -i: -i bỏ qua migration của version cũ)
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --stop-after-init

# 3) Kiểm dữ liệu sau migrate + chạy lại test
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --test-tags=/lab_chuyen --stop-after-init
```

Kiểm bằng chứng migrate: log phải có `lab_chuyen: migrate từ 17.0.1.0.0 lên 17.0.2.0`; `ir_module_module.latest_version` = version mới; đếm dữ liệu trước/sau bằng 1 câu SQL.

## 6. Năm lỗi migration hay gặp

| Lỗi | Nguyên nhân | Sửa |
|---|---|---|
| Migration không chạy | `pre_migrate.py` (gạch dưới) hoặc version không bump | đổi tên file, bump `version`, chạy `-u` (không `-i`) |
| `UndefinedColumn` trong pre-migrate | dùng ORM đọc field mới khi cột chưa có | pre-migrate chỉ dùng SQL |
| `psycopg2.errors.NotNullViolation` | thêm field `required=True` cho bảng có dữ liệu | 3 bước: thêm field không required → post-migrate backfill → bump tiếp mới bật required |
| Sequence reset về 1 sau upgrade | record `noupdate` bị xoá/tạo lại | dùng post-migrate `write`, không xoá xmlid |
| `-i` thay vì `-u` | cài lại module → migration bị bỏ qua | luôn `-u` cho DB đã có module |

## Checklist nhanh

- [ ] Đã bump `version` trong `__manifest__.py` cùng commit với thay đổi model.
- [ ] Thư mục `migrations/<version>/` khớp version mới; file tên **có gạch nối** `pre-migrate.py`.
- [ ] Hàm đúng chữ ký `migrate(cr, version)`, có `if not version: return`.
- [ ] Pre-migrate chỉ SQL; post-migrate dùng `api.Environment` khi cần ORM.
- [ ] Không `cr.commit()` trong script; thao tác dữ liệu lớn chia lô.
- [ ] Record `noupdate="1"` muốn đổi → có bước migrate, không tin `-u`.
- [ ] Đã snapshot DB trước khi chạy, và chạy `-u` (không `-i`) + test lại.
