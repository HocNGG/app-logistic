## Dùng khi nào

Làn 4 — chạy trong **mọi** review có model/endpoint/UI mới; bắt buộc trước handoff và trước sync point S1/S2/S3.
Điều kiện vào cổng: đã qua làn 1 + làn 2 (không review test của code vi phạm native-first).
Đầu ra: log chạy test dán vào `docs/review/P<x>-<verdict>.md`, kèm danh sách case thiếu.

---

## 1. "Test gate" ở dự án này nghĩa là gì

| # | Điều kiện | Đo bằng |
|---|---|---|
| G1 | Mọi transition state có test (đi được **và** bị chặn khi sai state) | đối chiếu state machine `docs/02` §3 với file test |
| G2 | Mỗi nhánh trong **6 ngoại lệ** có test **điểm quay về** (`docs/07` §0) | 6 test, tên nêu rõ "quay về" |
| G3 | Mỗi endpoint `lab_api` có `HttpCase` | 8 endpoint `docs/02` §6.2 → ≥ 8 case |
| G4 | **Đường lỗi** được test, không chỉ happy path | mỗi file có ≥ 1 `assertRaises` |
| G5 | Test độc lập thứ tự chạy, không phụ thuộc dữ liệu còn sót | chạy 2 lần, chạy riêng từng file, kết quả như nhau |
| G6 | Chạy được trên **DB sạch** bằng lệnh trong README | lệnh §2, không sửa tay DB trước |

Thiếu bất kỳ điều kiện nào → **CHANGES-REQUESTED tối thiểu**, làn 4 đỏ.

## 2. Lệnh chính xác (không tự phát minh)

```powershell
# 1) Chạy toàn bộ test lab_* trên DB dev
python odoo-bin -c infra/odoo.conf -d odoo17_lab --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh --stop-after-init

# 2) Gate "DB sạch": tạo DB mới, cài module kèm test
python odoo-bin -c infra/odoo.conf -d odoo17_gate -i lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh --stop-after-init

# 3) Debug một class (dùng lại odoo17_gate vừa tạo; drop sau khi xong)
python odoo-bin -c infra/odoo.conf -d odoo17_gate --test-tags=/lab_chuyen:TestGiamTai --stop-after-init
dropdb -U odoo odoo17_gate   # dọn rác, giữ DB sạch cho lần gate sau

# 4) FE (Compose UI test)
cd apps/kmp; ./gradlew :composeApp:desktopTest
```

`--test-tags=/<module>` lọc theo module; thêm `:<Class>` để lọc class. Test phải `@tagged('post_install', '-at_install')` nếu cần dữ liệu cấu hình đã cài.
Vừa sửa code thì phải thêm `-u <module>` cùng `--test-tags` (cú pháp đầy đủ: `agents/be/skills/11-testing-odoo.md` §4); thiếu `-u` là Odoo có thể vẫn dùng code cũ trong DB — "test xanh" giả.

## 3. Cách đọc output Odoo 17 — dòng nào là phán quyết

| Dòng trong log | Nghĩa |
|---|---|
| `odoo.tests.result: 0 failed, 0 error(s) of 12 tests when loading database 'odoo17_gate'` | **DÒNG PHÁN QUYẾT**. Chỉ dòng này quyết định PASS |
| `ERROR: test_x (odoo.addons.lab_chuyen.tests.test_chuyen_state.TestChuyenState.test_x)` | case lỗi exception; đọc traceback ngay dưới |
| `FAIL: test_x (...)` | assert sai; đọc `AssertionError` |
| `odoo.tests.stats: lab_chuyen: 12 tests ...` | số test mỗi module — 0 test = cổng đỏ, không phải "không có gì để test" |
| `skipped=1` | test bị bỏ qua → **FAIL** nếu lý do là "môi trường"/"chưa viết" |
| exit code khác 0 | không được coi là "chạy xong" |

Bằng chứng nộp: copy nguyên dòng `odoo.tests.result` + đuôi log; **không** nộp ảnh chụp giữa log.

## 4. Tám file test bắt buộc

(`docs/02` §10 có thêm `test_chuyen_capacity.py` — 8 file dưới đây là **gate tối thiểu**.)

| File | Phải khẳng định | Case lỗi bắt buộc | Fail nếu |
|---|---|---|---|
| `test_chuyen_state.py` | mọi transition hợp lệ của `lab.chuyen` (draft→open→booking_confirmed→departed→arrived→delivered→closed) | gọi `action_departed` từ `draft` → `UserError` | thiếu 1 transition, hoặc nhảy cóc không bị chặn |
| `test_giam_tai.py` | ② gỡ đơn → `waitlist` → gán chuyến khác → về `booked` | gỡ khi chưa duyệt tier → bị chặn; `unlink` không xảy ra | mất record, hoặc không có điểm quay về |
| `test_laixe_flow.py` | new→assigned→en_route→picked_up→confirmed **sinh `stock.picking`** | confirm khi thiếu ảnh/chữ ký → `UserError`; `failed` có `ly_do_that_bai` | picking không sinh, hoặc sinh sai `lab_pickup_mode` |
| `test_hanhtrinh_flow.py` | ⑨⑩⑪ stage + `lab.hanh.trinh.line` mỗi mốc 1 dòng | `stage` lùi/không hợp lệ → chặn | thiếu `message_post` khi chuyển stage |
| `test_chargeable_weight.py` | `max(actual, volumetric)` với divisor **6000 (air) / 1000 (sea)** | thiếu `lab_dim_*` → không ra số âm/None | 4 case air/sea × nhẹ/nặng không đủ |
| `test_security.py` | tài xế A không thấy job B; CS không thấy `account.move` phòng khác (**chi tiết `03-security-audit.md` §4**) | `assertRaises(AccessError)` khi `read` chéo | chỉ test chiều "được thấy" |
| `test_debit_lock.py` | ⑭ sau duyệt: sửa Debit → `UserError`; mở khoá phải có lý do + `group_manager` + `message_post` | user thường mở khoá → `AccessError`; mở khoá không lý do → chặn | khoá mà vẫn sửa được (P0) |
| `test_rea_completeness.py` | duyệt **từng dòng** bảng REA `docs/02` §11 → có chứng từ (`stock.picking`, `lab.chuyen.line`, `sale.order.line`, `account.move`, `account.payment`) | gỡ event → test phải đỏ (test tự chứng minh là có tác dụng) | thiếu 1 dòng REA, hoặc test chỉ assert `True` |

## 5. Test độc lập + DB sạch

- Không dùng id cứng (`partner_id = 1`); luôn `create` trong `setUpClass` với tiền tố riêng (`'TEST_' + uuid`).
- Không commit thủ công trong test; không `cr.execute("TRUNCATE …")`.
- Không phụ thuộc thứ tự: chạy `--test-tags=/lab_chuyen:TestA` một mình phải xanh.
- Không `time.sleep`; không gọi mạng thật (mock bằng `unittest.mock.patch`).
- Test phải chạy **2 lần liên tiếp** cùng kết quả (bằng chứng chống rác DB).

## 6. Compose UI test gate (FE — `apps/kmp`)

```powershell
cd apps/kmp; ./gradlew :composeApp:desktopTest
```

Ba test bắt buộc (`docs/04` §13): (1) chọn item ở duo **split**; (2) resize sang **overlay** vẫn giữ selection; (3) capture thiếu ảnh → báo lỗi tiếng Việt.
Cộng thêm smoke có ghi lại: `./gradlew :composeApp:run` → login → mở 1 job (video ngắn hoặc log).
Thiếu 1 trong 3 → làn 4 đỏ (Luật 7).

## 7. PASS / FAIL criteria

| Kết quả | Điều kiện |
|---|---|
| **PASS** | `odoo.tests.result: 0 failed, 0 error(s)` + 8/8 file §4 tồn tại + mỗi file ≥ 1 `assertRaises` + 3 compose test xanh + chạy lại lần 2 vẫn xanh |
| **CHANGES-REQUESTED** | Có test nhưng thiếu case lỗi, hoặc thiếu 1–2 file, hoặc skip có lý do môi trường |
| **BLOCKED** | Bất kỳ test đỏ; `--test-tags` không chạy được; test bị xoá/comment để CI xanh; `0 tests` ở module có model mới |

## 8. Mẫu issue

**Mẫu 1 dòng tiêu đề issue:** `[P1] Transition booking_confirmed → departed không có test`
**File:** `addons/lab_chuyen/models/lab_chuyen.py:120` · **Vi phạm:** Luật 7 + DoD D7 · **Vì sao sai:** sửa guard ở đây sẽ vỡ im lặng · **Đề xuất:** thêm `test_chuyen_state.py::test_departed_tu_booking_confirmed` + case `assertRaises` khi gọi từ `draft` · **Bằng chứng cần có:** dòng `odoo.tests.result` mới · **Chặn:** P2 handoff.
RV-01 **không** viết test vào repo của BE-01; đưa đoạn test đề xuất trong issue.

## Checklist nhanh

- [ ] 8/8 file test §4 tồn tại, tên đúng
- [ ] Mỗi file có ≥ 1 case lỗi (`assertRaises`), không chỉ happy path
- [ ] 6 nhánh ngoại lệ có test điểm quay về (`docs/07` §0)
- [ ] Mỗi endpoint `lab_api` có `HttpCase`
- [ ] `odoo.tests.result: 0 failed, 0 error(s)` đã dán vào review file
- [ ] Chạy được trên DB sạch (`-i` + `--test-tags`), chạy lần 2 vẫn xanh
- [ ] 3 compose UI test xanh + smoke `:composeApp:run`
- [ ] Không test nào bị skip vì môi trường, không test bị xoá để CI xanh
