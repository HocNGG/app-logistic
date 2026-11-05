# workflow/ — Quy trình phối hợp 3 agent

Thư mục này là **bộ nhớ chung** của đội. Giao tiếp giữa agent = file, không phải chat.

| File | Ai ghi | Dùng để |
|---|---|---|
| `handoff.md` | BE-01, FE-01 | Báo xong / xin review |
| `definition-of-done.md` | RV-01 | Thước đo PASS |
| `task-board.md` | Cả 3 | Trạng thái việc + yêu cầu chéo |

---

# HANDOFF PROTOCOL

## Khi nào handoff

Chỉ handoff khi: **code chạy được + test xanh + có chứng cứ**. Không handoff "để xin ý kiến giữa đường" — câu hỏi giữa đường ghi vào `docs/09-open-questions.md` hoặc `task-board.md`.

## Mẫu handoff (copy nguyên khối, thêm vào cuối file)

```markdown
---
## HO-<số> · <ROLE> → RV-01 · <ngày>

**Hạng mục:** <tên>
**Phase:** P<x>
**Bước nghiệp vụ liên quan:** ①②… (theo docs/00)
**TDD/PR:** <nhánh hoặc danh sách file>

### Đã làm
- <bullet, có đường dẫn file>

### Chứng cứ chạy thật
```
<lệnh>
<output rút gọn: test xanh / module cài được / app chạy>
```
Mức chắc chắn: HIGH

### Reuse Proof
- Đã grep: ...
- Đã đọc source 17: ...
- Kết luận: L2 (thêm field) — không tạo model.

### Cần agent khác
- BE→FE: cần view cho field `lab_chargeable_weight` trên form picking.
- FE→BE: cần endpoint `GET /lab/api/v1/driver/jobs` trả thêm `so_kien`.

### Cảnh demo
- Cảnh 3 trong `docs/06`: kéo đơn vào chuyến, thấy thanh tải 120% đỏ.

### Rủi ro / giả định
- ⚠️ ASSUMPTION(LOW): divisor thể tích 6000/1000 — chưa có xác nhận từ khách. Q: OQ-04.

### Chưa làm
- Report manifest chưa có bản dịch tiếng Anh (P2).
---
```

## RV-01 phản hồi ở đâu

`docs/review/P<x>-<verdict>.md`. Verdict: `PASS` / `CHANGES-REQUESTED` / `BLOCKED` / `REJECT-SCOPE`.

## Vòng lặp sửa

```
RV mở issue → agent sửa → ghi "HO-<số>-FIX" vào handoff.md (không mở HO mới)
→ RV review lại chỉ phần đã sửa (không review lại toàn bộ)
→ tối đa 2 vòng; vòng 3 phải có Orchestrator tham gia
```

---

# SYNC POINTS (không được vượt)

| Gate | Điều kiện qua | Nếu không qua |
|---|---|---|
| **S1** (cuối P1) | Video 60s đi hết luồng native, 0 dòng code `lab_*` | Dừng; sửa cấu hình; **không** code |
| **S2** (cuối P3) | API contract KMP đóng băng, app driver chạy được | Đóng băng contract cũ; đổi sau phải ADR |
| **S3** (cuối P6) | 6/6 nhánh ngoại lệ có test điểm quay về | Cắt P8 (báo cáo), **không** cắt ngoại lệ |
| **S4** (P9) | Rehearsal 3/3 lần không hỏng + video xuất được | Hoãn live demo, chỉ chiếu video |

---

# CHECKLIST HANDOFF NHANH (dán trước khi bấm gửi)

- [ ] Test xanh, có output dán kèm
- [ ] Có Reuse Proof
- [ ] Có mục "Cảnh demo"
- [ ] Không sửa file ngoài scope (kiểm bằng `git status`)
- [ ] Đã cập nhật `task-board.md`
- [ ] Chuỗi tiếng Việt đúng thuật ngữ
- [ ] Không còn `TODO` mồ côi
