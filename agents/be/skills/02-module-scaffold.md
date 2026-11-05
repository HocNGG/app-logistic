# 02 — Scaffold module Odoo 17 (lab_chuyen và anh em)

## Dùng khi nào

Khi bắt đầu một module mới được cấp phép (`lab_core`, `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh`, `lab_api`) hoặc khi module không cài/không upgrade được.

---

## 1. Cây module đầy đủ (chuẩn của dự án, đúng `docs/01` §5)

```
addons/lab_chuyen/
├── __init__.py                     # from . import models, wizard
├── __manifest__.py
├── models/
│   ├── __init__.py
│   ├── lab_chuyen.py
│   ├── lab_chuyen_line.py
│   └── sale_order.py               # inherit để nối lab_chuyen_id
├── wizard/
│   ├── __init__.py
│   ├── lab_giam_tai_wizard.py
│   └── lab_giam_tai_wizard_views.xml
├── views/                          # FE-01 sở hữu — BE-01 KHÔNG viết
│   ├── lab_chuyen_views.xml
│   └── lab_chuyen_menus.xml
├── security/
│   ├── lab_chuyen_security.xml     # ir.rule (+ groups nếu không nằm ở lab_core)
│   └── ir.model.access.csv
├── data/
│   ├── ir_sequence_data.xml
│   ├── mail_activity_type_data.xml
│   └── tier_definition_data.xml    # ⑥ giảm tải / duyệt vào chuyến
├── report/
│   ├── paperformat_data.xml
│   └── lab_chuyen_report.xml
├── static/description/icon.png
├── i18n/vi.po                      # BẮT BUỘC
├── tests/
│   ├── __init__.py                 # BẮT BUỘC, thiếu → test không chạy, không báo lỗi
│   ├── test_chuyen_state.py
│   └── test_chargeable_weight.py
└── README.md
```

## 2. `__manifest__.py` — template copy được

```python
# -*- coding: utf-8 -*-
{
    "name": "Lab Logistics — Chuyến gom hàng lẻ",
    "version": "17.0.1.0.0",          # LUÔN 17.0.x.y.z
    "category": "Logistics/Operations",
    "summary": "Chuyến bay/tàu gom nhiều đơn nhiều khách, duyệt vào chuyến, giảm tải",
    "author": "AppLogictics (BE-01)",
    "license": "LGPL-3",              # LGPL-3 | AGPL-3 | OEEL-1 (không dùng)
    "depends": [
        "lab_core",                   # groups + mixin + field mở rộng
        "sale_stock",                 # sale.order <-> stock.picking
        "stock_delivery",             # delivery.carrier + weight/shipping_weight
        "base_tier_validation",       # OCA/server-ux, nhánh 17.0
        "account",                    # debit = account.move
    ],
    "data": [
        # 1) nhóm quyền (nếu có) phải load TRƯỚC csv
        "security/lab_chuyen_security.xml",
        # 2) ACL sau nhóm
        "security/ir.model.access.csv",
        # 3) data: sequence, activity type, tier definition (noupdate="1")
        "data/ir_sequence_data.xml",
        "data/mail_activity_type_data.xml",
        "data/tier_definition_data.xml",
        # 4) report (paperformat trước report action)
        "report/paperformat_data.xml",
        "report/lab_chuyen_report.xml",
        # 5) wizard views, rồi view model, rồi menu CUỐI CÙNG
        "wizard/lab_giam_tai_wizard_views.xml",
        "views/lab_chuyen_views.xml",
        "views/lab_chuyen_menus.xml",
    ],
    "demo": ["demo/lab_chuyen_demo.xml"],   # chỉ load khi --demo
    "assets": {
        "web.assets_backend": [
            "lab_chuyen/static/src/**/*",
        ],
    },
    "installable": True,
    "application": False,             # app = chỉ module có menu gốc (lab_api: False)
    "auto_install": False,
}
```

**Thứ tự `data` là thứ tự thực thi.** Sai thứ tự là lỗi cài đặt thật, không phải "chuyện nhỏ":

| Sai | Triệu chứng |
|---|---|
| CSV trước XML nhóm | `External ID not found: lab_core.group_cs` |
| `paperformat` sau report action | report dùng format mặc định, PDF sai khổ |
| menu trước view/action | `External ID not found: action_lab_chuyen` |
| tier definition trước khi `ir.model` của model mới tồn tại | `model_id` không resolve |

## 3. `__init__.py` (bắt buộc, thiếu = model "biến mất" không lỗi)

```python
# addons/lab_chuyen/__init__.py
from . import models
from . import wizard
```

```python
# addons/lab_chuyen/models/__init__.py
from . import lab_chuyen
from . import lab_chuyen_line
from . import sale_order
```

```python
# addons/lab_chuyen/wizard/__init__.py
from . import lab_giam_tai_wizard
```

Quy ước: **1 class / 1 file**, tên file = tên model viết snake (`lab.chuyen` → `lab_chuyen.py`). File `__init__.py` của `tests/` cũng bắt buộc.

## 4. Cài / nâng cấp / test (đúng lệnh của `AGENTS.md` §9)

```powershell
# Cài mới (thứ tự phụ thuộc: lab_core trước)
python odoo-bin -c infra/odoo.conf -d odoo17_lab -i lab_core --stop-after-init
python odoo-bin -c infra/odoo.conf -d odoo17_lab -i lab_chuyen --stop-after-init

# Cài/nâng cấp cả chuỗi (khuyến nghị mỗi lần sửa)
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api --stop-after-init

# Nâng cấp + test của riêng module
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --test-tags=/lab_chuyen --stop-after-init

# Chạy dev server (Ctrl+C để dừng; log lỗi cài đặt nằm ngay trên console)
python odoo-bin -c infra/odoo.conf -d odoo17_lab --addons-path=addons,infra/odoo/addons
```

Quy tắc: **`-i` khi thêm model/file mới lần đầu, `-u` cho mọi lần sau.** Đổi `depends` hoặc thêm file vào `data` → phải `-u`. Đổi tên model/field → cần thêm `migrations/` (xem `13-migration-upgrade.md`). Trước mỗi lần reset demo: snapshot DB (`infra/scripts/reset-demo.ps1`).

## 5. Lỗi cài đặt thường gặp — và sửa đúng

| # | Thông báo | Nguyên nhân thật | Sửa |
|---|---|---|---|
| 1 | `Module lab_chuyen not found` | `--addons-path` thiếu `addons`, hoặc thư mục không tên `lab_chuyen` | Kiểm `-c infra/odoo.conf` + `--addons-path=addons,infra/odoo/addons`; không dùng đường dẫn tương đối sai |
| 2 | `ParseError: ... Invalid attribute attrs` / `states` | view 16.0 | `invisible="state == 'draft'"`; `attrs`/`states` bị hard-remove ở 17.0 (file view của FE-01 → mở yêu cầu, không tự sửa) |
| 3 | `lab_chuyen depends on lab_core` | cài sai thứ tự | `-i lab_core,lab_chuyen` (một lệnh, dấu phẩy) |
| 4 | Model tạo ra nhưng UI báo `Model not found: lab.chuyen` | thiếu `from . import lab_chuyen` trong `models/__init__.py` | Thêm import, `-u lab_chuyen` |
| 5 | `External ID not found: model_lab_chuyen` in CSV | `model_id:id` phải là xmlid `model_` + tên model thay `.` bằng `_` | `model_lab_chuyen`, `model_lab_chuyen_line` |
| 6 | `External ID not found: <nhóm>` | CSV load trước XML nhóm, hoặc nhóm nằm ở module khác chưa cài | Nhóm ở `lab_core`, `depends: ["lab_core"]`, đặt XML nhóm trước CSV |
| 7 | `ValueError: Invalid version number` | version không đúng dạng | `17.0.1.0.0` (5 thành phần, bắt đầu `17.0`) |
| 8 | `psycopg2.errors.DuplicateColumn / column already exists` | cài dở dang, DB còn bảng cột của lần trước | Chạy lại `-u lab_chuyen`; nếu vẫn lỗi → `infra/scripts/reset-demo.ps1` rồi cài lại DB sạch |
| 9 | `The tag <tree> ... ` không nhận | ai đó dùng `<list>` (18.0) | `<tree>` là tag đúng ở 17.0 |
| 10 | Cài xong nhưng menu trống | thiếu `groups` trên `menuitem` hoặc user không thuộc nhóm | Kiểm nhóm của user; nhóm phải `implied_ids` từ `base.group_user`/`portal` |

**Đọc log đúng cách:** lỗi cài đặt thật thường nằm ở dòng **gần cuối** stacktrace (sau `ParseError`/`KeyError`), không phải dòng đầu. `--stop-after-init` mà exit ≠ 0 là **cài thất bại**, kể cả khi không thấy `ERROR`.

## Checklist nhanh

- [ ] `version` = `17.0.x.y.z`, có `license`, `category`, `summary`, `depends`, `data`.
- [ ] `depends` đúng thứ tự phụ thuộc, **không** vòng (`lab_core` không depends `lab_*`).
- [ ] `models/__init__.py` + `wizard/__init__.py` + `tests/__init__.py` đều có, đủ import.
- [ ] `data` load theo thứ tự: nhóm → ACL → data → report → wizard views → views → menus.
- [ ] `-i lab_core,lab_chuyen ... --stop-after-init` exit code = 0.
- [ ] `i18n/vi.po` tồn tại (dù mới chỉ có vài chuỗi).
- [ ] Không commit `static/description/icon.png` giả (0 byte) — module list sẽ vỡ ảnh.
- [ ] Không sửa file `views/*.xml` của FE-01; nếu cần field mới, mở yêu cầu trong `workflow/task-board.md`.
