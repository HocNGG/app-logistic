## Dùng khi nào

Chấm điểm cuối mỗi sync point (S1–S4) và lần cuối ở P9. Dùng làm **đầu ra chuẩn** của `docs/review/P<x>-pass.md`.
Đầu vào: log audit (làn 1), log test (làn 4), bằng chứng video/PDF, 4 artifact critical thinking, `docs/ai-log.md`.
Nguyên tắc: **không có bằng chứng = 0 điểm**. Điểm phải kèm đường dẫn file hoặc lệnh đã chạy.

---

## 1. Tám tiêu chí (theo `docs/05` + `agents/review/AGENT.md` §8) — mô tả 0 / 3 / 5 cho **dự án này**

| # | Tiêu chí | Trọng số | 0 điểm | 3 điểm | 5 điểm |
|---|---|---|---|---|---|
| 1 | **Odoo native** (Luật 1) | 20 | Có ≥ 1 model `debit`/`cuoc`/`kien`/`tai_xe`/`khieu_nai`, **hoặc** viết lại ≥ 3 thứ native, **hoặc** không có Reuse Proof | 0 model trùng, nhưng 1–2 chỗ code cho việc config được (tự viết duyệt, tự chế token); Reuse Proof sơ sài | Chỉ 5 module whitelist; nêu **đích danh** model native đã dùng (`stock.quant.package`, `account.move`, `base_tier_validation`, `ir.rule`, `spreadsheet.dashboard`); Reuse Proof có lệnh grep + kết quả; audit làn 1 sạch |
| 2 | **Nghiệp vụ đủ** (19 bước + 6 nhánh) | 20 | Thiếu ≥ 2 nhánh ngoại lệ, hoặc 19 bước thiếu > 30% | Đủ 19 bước; 4–5/6 nhánh bấm được nhưng 1–2 nhánh thiếu **test điểm quay về** | 19/19; 6/6 nhánh có đường vào ≤ 2 click, đường ra, và test điểm quay về (`docs/07` §0); không dead-end; `unlink` không xoá mất vết |
| 3 | **REA / bút toán** (Luật 4) | 15 | Không có bút toán cho sự kiện, hoặc Debit là model riêng | `test_rea_completeness.py` có nhưng phủ 4–5/7 dòng REA; chưa kiểm kg picking = kg Debit | 7/7 dòng REA (`docs/02` §11) có chứng từ + test khẳng định; Debit = `account.move`; khoá/mở khoá có vết (chatter + người duyệt + lý do); kg 1 nguồn được test |
| 4 | **Responsive + duo UI** (I-10/I-11) | 10 | 1 layout cứng; resize vỡ layout hoặc mất text đang gõ | 3 ngưỡng đúng nhưng mất scroll/selection khi resize, hoặc < 900 vẫn cố split | 3 arrangement đúng; displacement test 6 bước pass; keyboard-only đi hết luồng; contrast ≥ 4.5:1 cả 2 chế độ; 4 trạng thái mọi màn |
| 5 | **Critical thinking** (I-12) | 15 | Không có artifact nào | Đủ 4 artifact nhưng câu trả lời chung; ADR thiếu phương án bị loại | 12+ câu có giả định + mức chắc chắn; 5+ ADR có phương án loại; 10+ red-team có trạng thái; fallback R1–R10; trả lời "1.000 đơn/ngày" bằng số cụ thể |
| 6 | **AI-native** (Luật 9) | 5 | Không dùng AI, **hoặc** AI giả trong demo | Có dùng AI trong quy trình nhưng `docs/ai-log.md` thiếu số liệu; AI trong sản phẩm gọi dữ liệu thật nhưng không trả lời được câu ngoài kịch bản | `ai-log.md` có ≥ 3 câu hỏi tốt + ≥ 2 lần AI sai và cách phát hiện; AI sản phẩm dùng dữ liệu thật và đã kiểm chứng; seed/test/video sinh bằng AI có script |
| 7 | **Video / demo** (Luật 8) | 10 | Không có video, hoặc live demo crash | Video ≤ 3:00 nhưng quay thô, không có wow moment có tên, narration chưa chỉnh | Kịch bản chạy sạch 3 lần, ≤ 3:00; wow moment có tên trong `docs/06` dùng dữ liệu thật; narration tiếng Việt đã nghe lại + phụ đề; 5 PDF in đúng font |
| 8 | **Tiếng Việt** (Luật 6) | 5 | Lẫn nhiều chuỗi Anh mặc định Odoo; thuật ngữ sai | Hầu hết tiếng Việt, còn 2–5 chuỗi Anh ở màn phụ | 100% nhãn Việt đúng glossary (`docs/00` §10); không lộ "New/Save/Confirm"; `i18n/vi.po` có msgstr thật; thông báo lỗi tiếng Việt |

## 2. Cách tính tổng + ánh xạ verdict

`Điểm tổng = Σ (điểm_tiêu_chí / 5 × trọng_số)` → thang 100. Trọng số: 20 + 20 + 15 + 10 + 15 + 5 + 10 + 5 = **100**.

| Tổng | Verdict | Hành động |
|---|---|---|
| **85–100** | **PASS** | ghi `docs/review/P<x>-pass.md`, báo Orchestrator, được sang phase sau |
| **70–84** | **PASS có điều kiện** | PASS nhưng kèm danh sách P1 phải xong trước sync point kế tiếp |
| **55–69** | **CHANGES-REQUESTED** | sửa rồi review lại trong cùng phase |
| **40–54** | **REWORK** | thiết kế lại phần đỏ; mất 1 nhịp, báo Orchestrator |
| **< 40** | **BLOCKED** | dừng hạng mục, không đưa vào demo |

**Luật chấm (không thương lượng):**
- Vi phạm Luật 1 (model trùng native) → tổng **≤ 40**, verdict **BLOCKED**, bất kể các mục khác.
- Lỗ hổng quyền (S1–S5 đỏ ở `03-security-audit.md`) → tổng **≤ 50**, **BLOCKED**.
- Thiếu ≥ 2 nhánh ngoại lệ → tổng **≤ 55**.
- Không có video / live demo hỏng → tổng **≤ 60**.
- **Cấm bù điểm**: tiếng Việt tốt không bù native dở; UI đẹp không bù thiếu ngoại lệ.
- Mỗi điểm phải có bằng chứng (lệnh/kết quả/file: dòng). Không có → 0.

## 3. Điểm phụ: 8 tín hiệu FDE (`docs/05` I-05) — 0–5 mỗi tín hiệu

| # | Tín hiệu | 0 | 3 | 5 | Bằng chứng phải chỉ ra |
|---|---|---|---|---|---|
| F1 | **Outcome-first** | làm theo công nghệ, không nêu cảnh | có OUTCOME nhưng mơ hồ | mọi hạng mục có `OUTCOME: <cảnh demo> / <test>` trước khi làm | báo cáo trong `workflow/handoff.md` |
| F2 | **Thin vertical slice** | làm ngang, không luồng nào xong | 1 luồng xong nhưng thiếu bước | ①→③→⑤⑥→⑦→⑬→⑮ chạy xuyện trước khi mở rộng | video 60s của P1 |
| F3 | **Reuses and proves it** | viết lại | Reuse Proof có nhưng không kèm kết quả grep | Reuse Proof + audit làn 1 sạch + nêu đích danh model native | `docs/review/audit-native-first.log` |
| F4 | **Models domain incl. exceptions** | chỉ happy path | 6 nhánh có UI, thiếu test quay về | 6/6 nhánh + test quay về + giải thích được bằng lời không cần code | `docs/07` §0 + `test_*` |
| F5 | **Debugs live** | không dám bấm ngoài script | gỡ được lỗi nhưng im lặng | nói ra miệng được nguyên nhân + sửa + tại sao không tái diễn | ghi chú drill §9 của `07-demo-readiness.md` |
| F6 | **Quantifies impact** | mô tả chung "nhanh hơn" | có số nhưng không nguồn | số cụ thể có nguồn: "19 bước → 6 màn hình, 1 chuyến 20 đơn gom trong 4 phút thay vì 2 giờ Excel" | demo + `docs/06` |
| F7 | **Uses AI and verifies it** | không log, tin AI mù | có `ai-log.md` nhưng thiếu lần AI sai | ≥ 3 câu hỏi tốt + ≥ 2 lần AI sai + cách phát hiện + đã chạy lại kiểm chứng | `docs/ai-log.md` |
| F8 | **Writes the handover** | không ai khác chạy được | có README nhưng thiếu seed/test | runbook + seed 1 lệnh + test 1 lệnh + snapshot/rollback, người ngoài làm được trong 10 phút | `infra/scripts/`, README |

`FDE = Σ(8 tín hiệu) / 8` (thang 5). Báo cáo riêng, **không** cộng vào thang 100 — vì đây là tín hiệu tuyển dụng, không phải điểm sản phẩm.

## 4. Mẫu báo cáo điểm (copy vào `docs/review/P<x>-pass.md`)

```markdown
## Chấm điểm P<x> — <ngày> — verdict: <PASS|CHANGES-REQUESTED|BLOCKED>
| # | Tiêu chí | Điểm (0-5) | ×Trọng số | Bằng chứng (lệnh/file:dòng) |
|---|---|---|---|---|
| 1 | Odoo native | 5 | 20 | rg audit sạch; chỉ 5 module whitelist |
| ... |
Tổng: <n>/100 · Cap áp dụng: <không | Luật 1 | security | video>
FDE sub-score: <n>/5 (F1..F8: 5,5,4,5,3,4,5,4)
Issue P0: <n> · P1: <n> · P2: <n> · P3: <n>
Phải xong trước sync point kế: <3 gạch đầu dòng, mỗi dòng có file:dòng>
```

## 5. Ba lỗi chấm điểm phải tránh

1. **Chấm theo cảm giác** — "nhìn cũng ổn": không có log thì mục đó 0.
2. **Chấm bình quân để tránh xung đột** — 1 mục 0 (native) không được kéo lên bằng 7 mục 5.
3. **Chấm trước khi có bằng chứng** — điểm chỉ được ghi sau khi làn 1 và làn 4 đã chạy xong.

## Checklist nhanh

- [ ] 8 tiêu chí đã chấm 0–5, mỗi điểm có bằng chứng `file:dòng` hoặc lệnh
- [ ] Trọng số dùng đúng (20/20/15/10/15/5/10/5), tổng /100 đã tính
- [ ] Cap Luật 1 / security / ngoại lệ / video đã áp dụng đúng
- [ ] 8 tín hiệu FDE đã chấm riêng, có bằng chứng
- [ ] Không có mục nào "bù điểm" cho mục khác
- [ ] Verdict khớp bảng §2 và khớp `workflow/definition-of-done.md`
- [ ] Danh sách "phải xong trước sync point" có ≤ 5 dòng, mỗi dòng actionable
- [ ] File `docs/review/P<x>-pass.md` đã ghi, Orchestrator đã được báo
