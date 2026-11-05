# DEFINITION OF DONE (DoD) — thước đo chính thức

> Một hạng mục chỉ **DONE** khi qua **cả 8 nhóm** dưới đây. Không có "done 90%".
> RV-01 dùng file này để ra verdict. BE-01/FE-01 dùng file này để tự review trước handoff.

---

## D1 — Chức năng (chạy thật, bằng UI)

- [ ] Luồng chính bấm được **hoàn toàn bằng UI**, không dùng shell/SQL để demo.
- [ ] Có trạng thái lỗi được xử lý: nhập sai, thiếu field, sai quyền, mất mạng.
- [ ] Không có màn hình "chết" (bấm vào không có phản hồi).
- [ ] Dữ liệu vào → ra nhất quán: cùng một số kg ở picking, chuyến, Debit.

## D2 — Native-first (Luật 1)

- [ ] Có mục **Reuse Proof** trong handoff/commit.
- [ ] Không có model mới ngoài `lab_core`, `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh`, `lab_api`.
- [ ] Không có model trùng ngữ nghĩa native (grep: `debit|cuoc|kien|lo_hang|tai_xe|khieu_nai` trong `_name`).
- [ ] Việc cấu hình được thì **đã** cấu hình (không viết code).
- [ ] Không sửa `addons/oca/**`.

## D3 — Odoo 17 idioms (Luật 2)

- [ ] `rg "attrs=" addons/lab_*` → rỗng.
- [ ] `rg "quantity_done" addons/lab_*` → rỗng.
- [ ] Manifest: `version='17.0.x.y.z'`, `license`, `depends` đúng.
- [ ] Mọi model có `_description`; `_order` có khi cần.
- [ ] Không dùng API Enterprise.

## D4 — Security (Luật 3)

- [ ] `ir.model.access.csv` đủ dòng cho mọi group liên quan.
- [ ] `ir.rule` cho record-level.
- [ ] `rg "has_group" addons/lab_*/models` → rỗng (hoặc có lý do).
- [ ] `sudo()` có comment giải thích.
- [ ] **Bằng chứng:** đăng nhập user phòng ban khác → không thấy dữ liệu chéo (ảnh/log).

## D5 — Bút toán / REA (Luật 4)

- [ ] Mỗi event trong bảng REA (`docs/02` §11) sinh chứng từ tương ứng.
- [ ] Debit là `account.move` (không phải model riêng).
- [ ] Cước tính từ `sale.order.line` + product kg + pricelist.
- [ ] Khoá/mở khoá Debit có vết (chatter + người duyệt + lý do).

## D6 — Ngoại lệ (Luật 5)

- [ ] Mỗi nhánh liên quan có: đường vào ≤ 2 click, đường ra, **test điểm quay về**.
- [ ] Không có trạng thái dead-end.
- [ ] Không `unlink` mất vết nghiệp vụ (dùng state `waitlist`/`cancelled`).

## D7 — Test, tiếng Việt, UX

- [ ] Test xanh: `--test-tags=/lab_*` (BE) / compose UI test (FE).
- [ ] Có test case lỗi (không chỉ happy path).
- [ ] Mọi màn hình có 4 trạng thái (loading/empty/error/content).
- [ ] Nhãn UI **tiếng Việt có dấu**, đúng thuật ngữ (chuyến, kiện, cước, debit, kho chờ, giảm tải).
- [ ] Không lộ chuỗi mặc định Odoo ("New", "Save", "Confirm") trên màn demo.
- [ ] Chuỗi dài không tràn layout; contrast ≥ 4.5:1.
- [ ] Đi hết luồng chính chỉ bằng bàn phím.

## D8 — Demo (Luật 8)

- [ ] Có ít nhất **một cảnh quay được**, ghi rõ cảnh nào trong `docs/06`.
- [ ] Seed data đủ cho cảnh đó; `reset-demo.ps1` vẫn chạy.
- [ ] Demo được **offline/DB trắng** hoặc có phương án B.
- [ ] **Runbook:** mọi dịch vụ/cổng đã mở có dòng trong `workflow/runbook.md` (lệnh + cách kiểm tra sống).
- [ ] Không có dữ liệu giả trông như thật mà không có thật.

---

## Phân loại verdict

| Verdict | Điều kiện |
|---|---|
| **PASS** | D1–D8 xanh |
| **CHANGES-REQUESTED** | Có mục đỏ nhưng sửa được, không vi phạm Luật 1–5 |
| **BLOCKED** | Vi phạm Luật 1–5, không có Reuse Proof, hoặc lỗ hổng quyền |
| **REJECT-SCOPE** | Không thuộc 19 bước và không có cảnh demo → backlog |

---

## "Done" ở cấp phase

Một phase DONE khi:
1. Mọi hạng mục trong phase PASS.
2. Sync point của phase đã qua (`workflow/handoff.md`).
3. `docs/review/P<x>-pass.md` tồn tại.
4. Video/cảnh quay của phase đã có (dù là clip nháp).
