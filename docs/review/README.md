# docs/review/ — nơi RV-01 ghi verdict

## Quy ước

Mỗi lần review một phase/hạng mục, RV-01 tạo **một file**: `P<x>-<verdict>.md`
Ví dụ: `P2-pass.md`, `P3-changes-requested.md`, `P5-blocked.md`.

Verdict chỉ có 4 giá trị:

| Verdict | Nghĩa | Ai phải hành động |
|---|---|---|
| `PASS` | D1–D8 xanh, qua sync point | Orchestrator (đi tiếp) |
| `CHANGES-REQUESTED` | Có lỗi sửa được, không vi phạm luật bất biến | BE-01 / FE-01 (tối đa 2 vòng) |
| `BLOCKED` | Vi phạm Luật 1–5, thiếu Reuse Proof, hoặc lỗ hổng quyền | Dừng hạng mục, báo Orchestrator |
| `REJECT-SCOPE` | Không thuộc 19 bước, không có cảnh demo | Đưa vào backlog `docs/03` §7 |

---

## Mẫu file verdict (copy khối này)

```markdown
# REVIEW P<2> — <tên phase> — <verdict>

**Ngày:** yyyy-mm-dd · **Reviewer:** RV-01 · **Người nộp:** BE-01 / FE-01
**Handoff:** HO-<số> trong `workflow/handoff.md`

## 1. Kết quả 6 làn

| Làn | Kết quả | Bằng chứng (lệnh + output) |
|---|---|---|
| 1. Native-first audit | 🟢 / 🔴 | `rg "^\s*_name\s*=" addons/lab_*` → 3 model (đúng danh sách cấp phép) |
| 2. Odoo 17 code review | 🟢 / 🔴 | `rg "attrs=|quantity_done|<list>" addons/lab_*` → 0 |
| 3. Security audit | 🟢 / 🔴 | login driver2 → không thấy job của driver1 (ảnh/log) |
| 4. Test gate | 🟢 / 🔴 | `--test-tags=/lab_chuyen` → 12 tests, 0 failed |
| 5. UX / duo UI / tiếng Việt | 🟢 / 🔴 | không còn "Save/New"; resize 3 ngưỡng OK |
| 6. Critical thinking | 🟢 / 🔴 | 6/6 nhánh có test điểm quay về |

## 2. Vi phạm phát hiện

### [P0] <tiêu đề>
- **File:** addons/<module>/<file>.py:<dòng>
- **Vi phạm:** Luật <n> / docs/<file> §<x>
- **Vì sao sai:**
- **Đề xuất sửa:**
- **Bằng chứng cần có:**
- **Chặn:** <hạng mục nào>

### [P1] ...

## 3. Điều kiện để qua sync point
- [ ] ...
- [ ] ...

## 4. Ghi chú cho Orchestrator
- ...
```

---

## Nhật ký review

| File | Phase | Verdict | Ngày | Ghi chú |
|---|---|---|---|---|
| `P0-baseline.md` | P0 (Wave A) | **CHANGES-REQUESTED** (+ BLOCKED cục bộ `lab_chuyen`/`lab_laixe`) | 2026-10-28 | Dựng cổng + baseline (mốc 11:00): audit 2 lần PASS, `check.ps1` PASS, 1 P0 cục bộ (ADR-003), 4 P1 / 17 P2 / 9 P3. Không review code BE/FE đang viết. |
| — | — | — | — | `P0-pass.md` / `P0-changes-requested.md` khi có handoff `HO-01` |

> **Ngoại lệ tên file:** `P0-baseline.md` là **baseline của cổng** (đo trạng thái đầu P0), không phải verdict của một lần nộp bài — nên không theo mẫu `P<x>-<verdict>.md`. Verdict của nó vẫn nằm ở §10 trong file.
