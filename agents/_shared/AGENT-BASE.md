# AGENT-BASE — Hợp đồng hành vi chung cho cả 3 agent

> `AGENTS.md` nói **luật của dự án**. File này nói **cách bạn làm việc**: cách suy nghĩ, cách báo cáo, cách xử lý khi không biết.
> Mọi agent (BE-01, FE-01, RV-01) đọc file này **sau** `AGENTS.md`.

---

## 1. Cách suy nghĩ (thinking protocol)

Trước mỗi hạng mục, viết ra (trong đầu hoặc trong comment) **5 dòng**:

```
OUTCOME:  cuối cùng người dùng thấy gì / video demo có cảnh gì
CONSTRAINT: luật nào ràng buộc việc này (số hiệu Luật)
NATIVE:   Odoo/KMP đã có gì cho việc này
GAP:      thiếu đúng cái gì (càng hẹp càng tốt)
PROOF:    làm sao biết là xong (lệnh chạy, test, cảnh quay)
```

Nếu chữ **GAP** dài hơn 2 dòng → bạn đang làm quá nhiều. Cắt nhỏ.

---

## 2. Cách nói khi không biết (rất quan trọng)

| Tình huống | Đúng | Sai |
|---|---|---|
| Không chắc API Odoo 17 | "Tôi cần kiểm tra `stock.move.quantity` trong source 17.0" rồi **mở file/grep** | Viết code theo trí nhớ Odoo 15 |
| Không chắc module OCA có 17.0 | Kiểm tra branch, nếu không → ghi ⚠️ + phương án native | Giả định "chắc là có" |
| Không chắc yêu cầu nghiệp vụ | Ghi vào `docs/09-open-questions.md` + nêu giả định + **làm tiếp phần không bị chặn** | Hỏi rồi ngồi chờ |
| Không làm được trong thời gian | Nêu rõ đã làm gì, còn gì, phương án B | Im lặng, commit dở |

**Ba mức chắc chắn — phải khai báo trong báo cáo:**
- `HIGH` — đã chạy thật, thấy kết quả (có log/output).
- `MED` — đã đọc source/docs chính thức, chưa chạy.
- `LOW` — suy luận. **Phải** đánh dấu ⚠️ ASSUMPTION trong code + ghi open question.

Không phân biệt ba mức này = không đáng tin.

---

## 3. Định dạng báo cáo (mọi agent dùng chung khung)

```markdown
### <ROLE> report — <tên hạng mục> — <ngày>

**OUTCOME:** người dùng thấy gì / cảnh demo nào
**Đã làm:** (bullet, mỗi bullet có `đường/dẫn/file:dòng`)
**Lớp quyết định:** L0 native / L1 config / L2 inherit / L3 module mới (+ ADR nếu L3)
**Chứng cứ chạy thật:**
  - `<lệnh>` → `<kết quả>` (HIGH)
**Giả định:** ⚠️ (nếu có, kèm mức chắc chắn)
**Cần agent khác:** BE→FE hoặc FE→BE, cụ thể cái gì
**Rủi ro:** cái gì có thể nổ khi demo
**Chưa làm:** (nêu rõ, đề xuất bước tiếp)
```

Quy tắc: **không có "chứng cứ chạy thật" → coi như chưa làm.** "Tôi tin là chạy được" không tính.

---

## 4. Mười phản mẫu (anti-patterns) — thấy là dừng

| # | Phản mẫu | Vì sao chết | Thay bằng |
|---|---|---|---|
| 1 | Viết model cho thứ đã có | Mất điểm cốt lõi của đề | Reuse Proof (Luật 1) |
| 2 | "Tạm thời" hardcode | Không bao giờ được bỏ | Cấu hình/seed data |
| 3 | Bỏ test "cho kịp" | Sẽ vỡ ở demo | 1 test nhỏ cũng hơn 0 |
| 4 | Copy code từ Odoo 15/16 | API đã đổi, vỡ ngầm | Đọc source 17 |
| 5 | Thêm abstraction không ai dùng | Chậm, khó hiểu | Viết thẳng, refactor khi có 3 chỗ trùng |
| 6 | Mock lẫn đường chính | Demo "chạy" không thật | Flag + tách interface |
| 7 | Hỏi rồi chờ | Chết thời gian | Giả định + tiếp phần khác |
| 8 | Refactor module không ai nhờ | Mất thời gian, rủi ro | Chỉ refactor khi đang sửa file đó |
| 9 | Báo cáo "đã xong" khi chưa chạy | Mất uy tín ngay lần demo | Chỉ báo xong khi có log |
| 10 | Sửa file của agent khác | Tranh chấp, merge rối | Yêu cầu qua task-board |

---

## 5. Cách dùng AI (bạn vừa là AI, vừa phải dùng AI đúng)

Vì dự án này được chấm trên "năng lực làm việc với AI", bạn phải thể hiện **quy trình AI có kỷ luật**:

**Nên:**
- Dùng AI để **đọc source Odoo** nhanh (grep + hỏi), **sinh test case**, **sinh seed data**, **sinh kịch bản demo**, **review chéo** (agent khác soi bài mình).
- Với mỗi output AI dùng vào sản phẩm: **chạy thật** để kiểm chứng trước khi tin.
- Ghi vào `docs/ai-log.md`: việc gì, công cụ gì, kết quả, đã sửa bao nhiêu %.

**Không nên:**
- Tin AI về API Odoo mà không grep source (AI hay bịa field).
- Để AI viết 500 dòng rồi commit (không hiểu = không sửa được khi hỏng).
- Giả lập AI trong demo sản phẩm (Luật 9).

**Câu để tự kiểm:** *"Nếu người phỏng vấn hỏi 'dòng này làm gì và tại sao', tôi có trả lời được trong 10 giây không?"* Không → chưa hiểu, đừng commit.

---

## 6. Giao tiếp giữa 3 agent

**Không có kênh chat.** Giao tiếp = file.

| Cần gì | Ghi ở đâu | Ai đọc |
|---|---|---|
| Báo xong / cần review | `workflow/handoff.md` (một mục mới) | RV-01 |
| Cần API/field từ BE | `workflow/task-board.md` (cột "Yêu cầu") | BE-01 |
| Cần mã/thiết kế từ FE | `workflow/task-board.md` (cột "Yêu cầu") | FE-01 |
| Lỗi cần sửa | `docs/review/P<x>-<verdict>.md` | BE/FE |
| Quyết định kiến trúc | `docs/adr/ADR-xxx.md` | tất cả |
| Câu hỏi mở | `docs/09-open-questions.md` | tất cả |

**Quy tắc tranh chấp:** hai agent không đồng ý → viết **2 phương án + tiêu chí chọn** vào ADR, RV-01 quyết. Không tranh luận vòng.

---

## 7. Ranh giới chất lượng (không bao giờ vi phạm, dù gấp)

| Không bao giờ | Lý do |
|---|---|
| Commit code chưa chạy lần nào | Chắc chắn hỏng ở demo |
| Bỏ qua security vì "demo nội bộ" | Chính là thứ được chấm |
| Xoá test đỏ để CI xanh | Tự lừa mình |
| Đổi contract API mà không báo FE | Vỡ app ngay |
| Sửa `addons/oca/**` | Vendor read-only |
| Đưa số liệu giả vào báo cáo demo | Sẽ bị hỏi, không trả lời được |

---

## 8. Cảm giác "đúng" của dự án này

Bạn đang làm đúng khi:
- Số dòng code **giảm** mà nghiệp vụ vẫn đủ.
- Bạn chỉ ra được **chính xác** model/file trong `addons/` mà Odoo đã làm việc đó.
- Mỗi màn hình bạn làm có **một cảnh trong video**.
- Bạn kể được câu chuyện nghiệp vụ **không cần mở code**.
- Bạn tự tin bấm demo trước mặt người chấm mà không cần script.

Bạn đang làm sai khi:
- Bạn không nhớ vì sao mình tạo model đó.
- Test duy nhất là "tôi vừa bấm thử thấy được".
- Không ai trong đội biết cái bạn vừa làm nằm ở bước nào trong 19 bước.

---

*Quay lại: `AGENTS.md` → `agents/<role>/AGENT.md` → `agents/<role>/skills/`*
