# 11 — Testing Odoo 17 (TransactionCase, HttpCase, `--test-tags`)

## Dùng khi nào

Luôn. Không có test = không được handoff (AGENTS.md Luật 7, `agents/be/AGENT.md` §7).

---

## 1. Ba lớp test còn tồn tại ở 17.0

| Class | Hành vi | Dùng cho |
|---|---|---|
| `TransactionCase` | mỗi `test_*` chạy trong 1 transaction rollback sau test; `setUpClass` chạy 1 lần/class, dữ liệu tạo trong `setUpClass` **dùng chung** | mặc định — 90% test của dự án |
| `SingleTransactionCase` | **tất cả** `test_*` dùng chung 1 transaction, commit lúc kết thúc | chỉ khi cần state tích luỹ giữa các test |
| `HttpCase` | như `TransactionCase` + `url_open`, `authenticate`, `browser_js` | endpoint `lab_api`, portal |

**`SavepointCase` đã bị xoá** (gộp vào `TransactionCase` từ 16.0). Code còn `SavepointCase` → `ImportError` ngay khi chạy test.

## 2. Khung file test (bắt buộc đúng 3 thứ)

```python
# addons/lab_chuyen/tests/__init__.py     <-- BẮT BUỘC, thiếu là test không chạy và KHÔNG báo lỗi
from . import test_chuyen_state
from . import test_chargeable_weight
```

```python
# addons/lab_chuyen/tests/test_chuyen_state.py
from odoo.tests import Form, TransactionCase, tagged
from odoo.exceptions import UserError


@tagged("post_install", "-at_install")
class TestChuyenState(TransactionCase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()                       # BẮT BUỘC gọi super
        cls.env = cls.env(context=dict(cls.env.context, tracking_disable=True))
        cls.warehouse = cls.env["stock.warehouse"].search([], limit=1)
        cls.chuyen = cls.env["lab.chuyen"].create({
            "loai_van_chuyen": "air",
            "kho_xuat_id": cls.warehouse.id,
            "tai_trong_max_kg": 1000.0,
        })

    def test_draft_mo_chuyen(self):
        self.chuyen.action_open()
        self.assertEqual(self.chuyen.state, "open")

    def test_khong_the_khoi_hanh_tu_draft(self):
        with self.assertRaises(UserError):
            self.chuyen.action_depart()
```

Quy tắc: `setUpClass` dùng `cls.env` (không phải `self.env`); tạo dữ liệu ở `setUpClass` để mỗi test không phải tạo lại; `tracking_disable=True` để chatter không spam mail.

## 3. `Form` — test đúng đường UI đi (onchange/readonly)

```python
def test_form_tao_chuyen_bay(self):
    with Form(self.env["lab.chuyen"]) as form:
        form.loai_van_chuyen = "air"
        form.kho_xuat_id = self.warehouse
        form.tai_trong_max_kg = 500.0
    chuyen = form.save()
    self.assertTrue(chuyen.name.startswith("CH/AIR/"), chuyen.name)
```

`Form` chạy `onchange` + kiểm readonly giống web client → đây là cách duy nhất phát hiện "code chạy bằng `create()` nhưng UI không nhập được field". `from odoo.tests import Form` (17.0), **không** `odoo.tests.common.Form`.

## 4. `--test-tags` — cú pháp và cách chạy đúng

```powershell
# Chỉ test 1 module (kèm cài/nâng cấp để test được nạp)
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --test-tags=/lab_chuyen --stop-after-init

# Nhiều module
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh --stop-after-init

# Chỉ 1 class
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --test-tags=/lab_chuyen:TestChuyenState --stop-after-init

# Chỉ 1 method
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --test-tags=/lab_chuyen:TestChuyenState.test_draft_mo_chuyen --stop-after-init

# Bỏ test at_install, chỉ chạy post_install
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --test-tags=post_install,-at_install,/lab_chuyen --stop-after-init
```

- `-u <module>` là điều kiện để test được nạp; `-i` chỉ dùng lần đầu.
- Test **at_install** chạy khi cài module; test **post_install** chạy cuối, sau khi mọi module xong → đúng cho test cần `account`, `stock` đã sẵn sàng.
- Không có `--test-tags` mà có `-u` → Odoo vẫn chạy test at_install của module đó (dễ tưởng "không chạy test").
- Exit code ≠ 0 = có test đỏ. Log dòng cuối chứa `FAIL`/`ERROR` + tên test.

## 5. Test quyền bằng user thứ hai (bắt buộc)

```python
from odoo.tests import TransactionCase, new_test_user
from odoo.exceptions import AccessError


@tagged("post_install", "-at_install")
class TestSecurity(TransactionCase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.cs = new_test_user(cls.env, login="cs1", password="cs1",
                               groups="base.group_user,lab_core.group_cs")
        cls.wh = new_test_user(cls.env, login="wh1", password="wh1",
                               groups="base.group_user,lab_core.group_warehouse")

    def test_cs_khong_tao_duoc_chuyen(self):
        with self.assertRaises(AccessError):
            self.env["lab.chuyen"].with_user(self.cs).create(
                {"loai_van_chuyen": "air", "kho_xuat_id": self.warehouse.id})
```

`new_test_user(env, login, password=..., groups="a,b")` — truyền `groups` dạng chuỗi xmlid ngăn bằng dấu phẩy. `with_user()` là thứ duy nhất chứng minh quyền; `self.env` (admin) **luôn** pass.

## 6. Tám test case bắt buộc của dự án (`docs/02` §10)

| # | File | Case tối thiểu | Điểm quay về / điều kiện đạt |
|---|---|---|---|
| 1 | `test_chuyen_state.py` | mỗi transition hợp lệ + 1 transition sai → `UserError` | state machine `docs/02` §3.1 |
| 2 | `test_chuyen_capacity.py` | quá tải: `ti_le_tai > 100`; `tai_trong_da_dung_kg` = Σ line `booked` | compute store đúng |
| 3 | `test_giam_tai.py` | không duyệt → không gỡ được; duyệt → line `waitlist`; gán chuyến khác → `booked` | nhánh ② — *điểm quay về* |
| 4 | `test_laixe_flow.py` | `new→assigned→en_route→picked_up→confirmed` và sinh `stock.picking` | nhánh ② tài xế |
| 5 | `test_hanhtrinh_flow.py` | ⑨⑩⑪: `departed→arrived→handed_to_agent`, có `lab.hanh.trinh.line` | bước ⑨⑩⑪ |
| 6 | `test_chargeable_weight.py` | 6 case ở `06-sale-pricing-kg.md` §6 (6000/1000, thiếu dữ liệu) | cước đúng |
| 7 | `test_security.py` | tài xế A không thấy job của B; CS không tạo được chuyến | `ir.rule` thật |
| 8 | `test_debit_lock.py` | đã khoá → không sửa; mở khoá thiếu lý do → `UserError`; có lý do → `message_post` | ⑭ + Luật 4 |

Cộng 1 file `lab_api/tests/test_driver_api.py` (`HttpCase`) cho endpoint — Luật 7.

## 7. Mười điều làm test vô nghĩa

- ❌ Chỉ dùng `self.env` (admin) → mọi `ir.rule` bị bỏ qua.
- ❌ `assertTrue(record.exists())` thay vì so sánh giá trị cụ thể.
- ❌ Test phụ thuộc thứ tự chạy (`test_a` tạo dữ liệu cho `test_b`).
- ❌ `self.env.cr.commit()` trong test → phá rollback, DB bẩn.
- ❌ Bỏ `super().setUpClass()`.
- ❌ Quên `tests/__init__.py` → "0 tests, 0 failures" mà tưởng xanh.
- ❌ Test chỉ assert `record.state` mà không assert bút toán/chứng từ đi kèm (Luật 4).
- ❌ Test dùng ngày `datetime.now()` không freeze → flaky.
- ❌ Test phụ thuộc ID cứng (`ref('stock.stock_location_stock')` là đúng, `id=5` là sai).
- ❌ Comment `# TODO: test sau` (Luật 7 + §8 AGENTS.md).

## Checklist nhanh

- [ ] `tests/__init__.py` tồn tại và import mọi file test.
- [ ] Mọi class gọi `super().setUpClass()`; dữ liệu ở `setUpClass` dùng `cls.env`.
- [ ] Không còn `SavepointCase` trong repo (`rg -n "SavepointCase" addons/` → rỗng).
- [ ] Có test quyền bằng `with_user()` + `assertRaises(AccessError)`.
- [ ] Có ít nhất 1 case lỗi cho mỗi model mới (không chỉ happy path).
- [ ] Đủ 8 file test ở §6 + 1 `HttpCase` cho `lab_api`.
- [ ] Chạy xanh bằng đúng lệnh `--test-tags` trong `README`, trên DB sạch (`reset-demo.ps1`).
- [ ] Không `cr.commit()`, không phụ thuộc thứ tự test, không assert mơ hồ.
