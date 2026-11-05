# 05 — GIẢI MÃ YÊU CẦU CỦA SẾP → INSTRUCTION CHUYÊN BIỆT

> Nguồn: 3 ảnh chat (Nhan Pham, 11:54–11:58) + ảnh đề bài.
> Mục đích: biến **câu nói miệng** thành **instruction thi hành được, kiểm chứng được**.
> Mỗi yêu cầu → 1 instruction có mã (`I-xx`) → được nhúng vào `AGENTS.md` / `agents/*/AGENT.md` / `skills/*`.
> Người đọc: cả 3 agent + Orchestrator.

---

## 1. Nguyên văn & bóc tách

### Ảnh 1 (11:58)
> "Nếu cần thì dùng **opus 5.5** làm **product demo video**, **VieNeu AI**, .. **"wow moment"**. Hoàn thành việc bằng **AI**: deepseek, claude, codex, ..., **hạn chế thủ công**. Vị trí: **Forward Deployed AI Engineer (FDE)**"
> — "What FDE means at OpenAI | OpenAI https://share.google/…"
> — "One core value: **"Start with the outcome, do what it takes to succeed, then follow with scale"**."

### Ảnh 2 (11:54)
> "**Thỏa sức sáng tạo và hỏi AI** nha các e. **Ko giới hạn điều kiện gì**"
> — "A muốn xem **năng lực hỏi và làm việc với AI** của các bạn"
> — "**REA**, **responsive UI**, **iphone 18 duo UI**, ... **critical thinking**"

### Ảnh 3 — đề bài
> Odoo 17 + app di động **Kotlin Multiplatform**, phải chạy được **desktop target** để demo.

### Bảng bóc tách (câu nói → nghĩa thực → instruction)

| # | Câu nói | Nghĩa thực sự | Instruction | Bằng chứng phải có |
|---|---|---|---|---|
| 1 | "hạn chế thủ công", "hoàn thành việc bằng AI" | Không được ngồi gõ tay những gì máy làm được. Quy trình phải **AI-first**, có dấu vết | **I-01 AI-Native Delivery** | `docs/ai-log.md` + script sinh data/test/video |
| 2 | "dùng opus 5.5 làm product demo video" | Video demo là **sản phẩm**, không phải quay màn hình thô. Dùng frontier model để viết kịch bản + dựng | **I-02 Demo Video Pipeline** | `demo/` + file MP4 + prompt log |
| 3 | "VieNeu AI" | Giọng nói tiếng Việt cho demo — chạm cảm xúc, khác biệt | **I-03 Vietnamese Voice** | File audio + script sinh bằng API |
| 4 | `"wow moment"` | Phải có **một khoảnh khắc** khiến người xem nhớ. Không phải 19 bước đều hay | **I-04 Wow Moment** | 1 cảnh được đặt tên trong `docs/06` |
| 5 | "Forward Deployed AI Engineer (FDE)" | Không chấm "biết code", chấm **own outcome, làm tại môi trường khách, ship thật** | **I-05 FDE Posture** | Câu chuyện demo kể theo vai FDE |
| 6 | "Start with the outcome…" | Bắt đầu từ kết quả, không từ công nghệ. Làm đủ để thắng rồi mới scale | **I-06 Outcome-First** | Mỗi phase có "cảnh demo", vertical slice |
| 7 | "Thỏa sức sáng tạo", "ko giới hạn điều kiện" | Được phá khuôn — **trừ** khuôn nghiệp vụ và native-first | **I-07 Creative Allowlist** | Danh sách sáng tạo + danh sách bị chặn |
| 8 | "A muốn xem năng lực **hỏi** và làm việc với AI" | Chấm **chất lượng câu hỏi** và **cách kiểm chứng** đầu ra AI, không phải số lần dùng AI | **I-08 Ask-Well Protocol** | `docs/ai-log.md` có câu hỏi hay + câu AI trả lời sai và cách phát hiện |
| 9 | **REA** | Mô hình Resources–Events–Agents: mỗi sự kiện kinh tế phải có tài nguyên, sự kiện, tác nhân → **không sót bút toán** | **I-09 REA Integrity** | Bảng REA ↔ chứng từ Odoo, test |
| 10 | **responsive UI** | Không phải "co giãn được" mà **giữ được ngữ cảnh** qua mọi kích thước | **I-10 Responsive Contract** | 3 ngưỡng + test kéo cửa sổ |
| 11 | **"iphone 18 duo UI"** | Apple **iPhone Duo** (gập, 2 màn hình, split/overlay arrangement, reserved regions) → **duo layout** thích ứng | **I-11 Duo Layout** | Layout 3 ngưỡng, giữ state, fold-aware |
| 12 | **critical thinking** | Phản biện chính mình: giả định, rủi ro, phương án B, cái gì sẽ nổ | **I-12 Critical Thinking** | `docs/09-open-questions.md` + ADR + red-team |

---

## 2. Mười hai instruction chuyên biệt (nội dung thi hành)

### I-01 · AI-NATIVE DELIVERY
**Luật:** Mọi việc lặp lại ≥ 2 lần phải được tự động hoá hoặc sinh bằng AI. Cấm dùng tay cho: seed data, test data, script demo, dịch chuỗi, sinh report mẫu.

**Phải làm:**
- Sinh **seed data** bằng script (XML/CSV/JSON) — 1 lệnh dựng lại toàn bộ dữ liệu demo.
- Sinh **test case** từ 19 bước + 6 nhánh (bảng trong `docs/00`) — mỗi bước ≥ 1 test.
- Sinh **kịch bản video** bằng frontier model, rồi **đọc lại và sửa**.
- Ghi `docs/ai-log.md`: việc gì · công cụ gì · prompt tóm tắt · **đã sửa bao nhiêu %** · **đã kiểm chứng bằng gì**.

**Cấm:** commit output AI chưa chạy thử. **Cấm:** giả lập AI trong sản phẩm.

**Đo:** nếu xoá hết file do AI sinh, dự án có mất > 50% khối lượng? Nếu không → chưa AI-native đủ.

---

### I-02 · DEMO VIDEO PIPELINE (Opus 5.5 + công cụ dựng)
**Luật:** Video 3 phút là **deliverable số 2** (sau phần mềm chạy được). Có pipeline, không quay tay.

**Stack đã kiểm chứng (2026-10):**
| Vai | Công cụ | Ghi chú |
|---|---|---|
| Viết kịch bản + storyboard | **`claude-opus-5-5`** ($4/$20 per MTok, 1M ctx, adaptive thinking **không tắt được**) | Đưa toàn bộ `docs/00`, `docs/01`, `docs/06` vào context ở lần 1 |
| Ghi hình UI thật | **Playwright** `recordVideo` (mp4, 1920×1080) | ⚠️ file video chỉ được ghi khi `browserContext.close()` |
| Dựng/ chèn tiêu đề/overlay | **Remotion** (`npx remotion render <entry> <id> out/video.mp4`) hoặc ffmpeg | Remotion khi cần kinetic typography |
| Giọng đọc tiếng Việt | **VieNeu AI** (xem I-03) | pre-generate, không gọi lúc demo |
| Cắt/ghép/nhạc | `ffmpeg` | |

**Quy trình 6 bước:** kịch bản (LLM) → storyboard 8 cảnh → quay từng cảnh (Playwright) → voice-over (VieNeu) → dựng (Remotion/ffmpeg) → **xem lại 3 lần và cắt**.

**Đo:** video ≤ 3:00, có phụ đề tiếng Việt, cảnh đầu 8 giây đã nêu vấn đề.

---

### I-03 · VIETNAMESE VOICE (VieNeu AI)
**Sự thật đã kiểm chứng:** `api.vieneu.io/api/v1` · auth `Authorization: Bearer vn_sk_...` · engine hiện tại **chỉ `v4`** · `POST /v1/audio/speech` (OpenAI-compatible) · `GET /v1/voices?engine=v4` (voice id là tên tiếng Việt, ví dụ "Ngọc Lan") · tính tiền theo ký tự, min 50 ký tự · trial 7 ngày, 50 call/ngày, key `vn_test_` giới hạn 100 từ.

**Luật:**
- Voice-over **pre-generate** vào `demo/voice/*.wav` trước demo ≥ 1 ngày. Không gọi API lúc demo.
- Chọn voice người lớn, tốc độ ~0.95× để nghe rõ thuật ngữ Việt.
- Mọi thuật ngữ nghiệp vụ (debit, AWB, HBL, manifest, tier validation) **đánh vần theo cách người Việt nói**, không đọc như tiếng Anh.
- Có **backup**: nếu API lỗi → dùng TTS offline hoặc phụ đề + nhạc.

**Nếu không dùng được VieNeu:** Viettel AI / FPT.AI / Vbee / ElevenLabs multilingual. Không chặn tiến độ.

---

### I-04 · WOW MOMENT (một khoảnh khắc, không phải 19 khoảnh khắc)
**Luật:** Phải chỉ định **đúng một** khoảnh khắc "wow" và nó phải **thật**.

**Ba ứng viên (chọn 1 chính, 1 phụ):**

| Ứng viên | Cảnh | Vì sao wow | Rủi ro |
|---|---|---|---|
| **A. Voice → hành động** | CS nói tiếng Việt: *"Tạo yêu cầu lấy hàng cho shop Sen Đá, 5 kiện, 120kg, lấy lúc 9 giờ"* → record xuất hiện trên màn duo UI + hiện trên app tài xế trong 2 giây | AI thật, dữ liệu thật, kết quả hiện ngay | Cần LLM + parse; nếu sai → mất uy tín |
| **B. Một luồng xuyên 4 màn hình** | Kéo 1 đơn vào chuyến ở app desktop → app tài xế rung lên có việc mới → tài xế ký → Odoo cập nhật → khách thấy timeline | Chứng minh "một hệ thống", không phải 4 demo rời | Cần app + Odoo + track cùng chạy |
| **C. Thanh tải 120% → giảm tải có duyệt** | Chuyến đỏ, kéo 1 đơn ra, phải có người duyệt, đơn về "chờ chuyến sau" | Thể hiện hiểu ngoại lệ + kiểm soát | Ít "wow" với người không làm logistics |

**Khuyến nghị:** chính = **B** (đáng tin, không phụ thuộc LLM), phụ = **A** nếu kịp. C = chèn như "chi tiết chuyên môn".

**Cấm:** dùng AI để *trông như* AI (gõ chữ giả, animation "đang suy nghĩ" không có thật).

---

### I-05 · FDE POSTURE
**Định nghĩa (kiểm chứng):** FDE = kỹ sư **nhúng tại khách hàng**, own **outcome** chứ không own ticket: xác định vấn đề → xây tích hợp → ship production → **bàn giao**. OpenAI công bố bài "What FDE means at OpenAI" (10/2026).

**Tám tín hiệu nhà tuyển dụng tìm (dùng làm checklist):**
1. Bắt đầu từ **outcome/cảnh**, không từ công nghệ.
2. Ship **vertical slice** mỏng chạy end-to-end trước khi mở rộng.
3. **Tái sử dụng** cái có sẵn và **chứng minh** được (native-first + Reuse Proof).
4. Mô hình hoá **nghiệp vụ đúng, kể cả ngoại lệ**, và gọi tên được **đường quay về**.
5. Debug **live, nói ra miệng**, trong môi trường khách.
6. **Định lượng** tác động (tiết kiệm bao nhiêu bước/thời gian) và **demo**, không mô tả.
7. Dùng AI **mạnh tay + kiểm chứng**; cho xem prompt log và **lỗi AI đã bị bắt**.
8. Viết **bàn giao**: runbook, test, script reset/seed.

**Luật thi hành:** mọi hạng mục phải trả lời được "outcome là gì" trước khi làm (khung 5 dòng trong `agents/_shared/AGENT-BASE.md` §1).

---

### I-06 · OUTCOME-FIRST (Start with the outcome…)
**Luật:** Không có phase nào không tạo ra thứ **nhìn thấy được**. Không refactor trước khi có 1 luồng xuyên suốt.

**Thực thi:**
- Mỗi hạng mục mở đầu bằng: `OUTCOME: <cảnh demo> | <test chứng minh>`.
- "Do what it takes" = có phương án B cho mọi việc rủi ro (R3 app desktop, R5 font PDF, R7 AI).
- "Then follow with scale" = chỉ mở rộng sau khi **sync point** qua.

---

### I-07 · CREATIVE ALLOWLIST (sáng tạo có kỷ luật)
**Được làm tự do:** UX/animation/empty-state, AI layer, cách kể demo, phím tắt, dashboard bố cục, icon, micro-copy tiếng Việt.

**Phải xin (ADR):** thêm module ngoài 5 module, đổi thư viện UI, thêm dependency nặng, thêm field vào model native ngoài danh sách `docs/02`.

**Bị chặn tuyệt đối:** viết lại Odoo; đổi công nghệ (Odoo 17, KMP); thêm microservice/broker; bỏ ngoại lệ; bỏ tiếng Việt; giả lập AI.

**Thử thách sáng tạo:** *làm hay hơn với ít code hơn.*

---

### I-08 · ASK-WELL PROTOCOL ("năng lực hỏi")
Đây là thứ được chấm trực tiếp. Sếp muốn thấy **cách bạn hỏi AI**, không phải bạn dùng AI nhiều.

**Chuẩn một câu hỏi tốt (5 phần):**
1. **Ngữ cảnh** — file/phiên bản/module cụ thể ("Odoo 17.0, `addons/stock/models/stock_move.py`").
2. **Ràng buộc** — "không được tạo model mới", "phải dùng native".
3. **Kết quả mong muốn** — định dạng đầu ra cụ thể.
4. **Tiêu chí đúng** — làm sao biết câu trả lời đúng/sai.
5. **Yêu cầu tự kiểm** — "nếu không chắc, nói UNVERIFIED thay vì đoán".

**Phản mẫu:** "Làm sao để làm chuyến trong Odoo?" (quá rộng → AI bịa).

**Bắt buộc ghi vào `docs/ai-log.md`:** ít nhất **3 câu hỏi tốt** + **2 lần AI trả lời sai** + cách bạn phát hiện. Đây là bằng chứng đắt giá nhất cho tiêu chí "năng lực hỏi".

---

### I-09 · REA INTEGRITY
**REA = Resources – Events – Agents.** Mỗi sự kiện kinh tế phải có: tài nguyên bị biến đổi, tác nhân, và **chứng từ ghi nhận**.

**Luật:**
- Bảng REA (`docs/02` §11) là **danh sách kiểm tra bắt buộc**. Mỗi dòng phải ánh xạ tới một chứng từ Odoo cụ thể.
- Event xảy ra mà không sinh chứng từ → **P0** (RV chặn).
- Không có "ghi nợ ngoài sổ": mọi khoản phải vào `account.move`.
- Đối chiếu: kg trên `stock.picking` = kg trên `lab.chuyen.line` = kg trên `account.move.line` (cùng kỳ).

**Test bắt buộc:** `test_rea_completeness.py` — duyệt từng event trong bảng, khẳng định có record chứng từ.

---

### I-10 · RESPONSIVE CONTRACT
**Định nghĩa của dự án:** *kéo cửa sổ từ 1600 → 700 → 1600 dp mà **không mất** dữ liệu đang gõ, item đang chọn, vị trí scroll; layout không vỡ; luồng chính vẫn hoàn thành được.*

**Ba ngưỡng:** ≥1280 split rộng · 900–1280 split hẹp · <900 overlay 1 pane.

**Cấm:** "responsive" bằng `Modifier.weight` mù; ẩn chức năng ở màn hẹp (phải **thay đổi cách truy cập**, không bỏ chức năng).

---

### I-11 · DUO LAYOUT ("iphone 18 duo UI")
**Sự thật (đã kiểm chứng):** *iPhone Duo* là máy gập thật của Apple (ra mắt 09/09/2026, $1.999): **hai màn hình** (ngoài + trong gập), mỗi màn hình có **size class riêng**. Apple định nghĩa **reserved regions** (bản lề, camera — dạng division/occlusion) và **arrangement: split / overlay**, cùng **displacement patterns** khi mở/gập.

**Dịch thành 4 luật thi hành:**
1. **Hai arrangement**: `split` (2 pane song song) và `overlay` (1 pane + pane phủ). Không có kiểu thứ ba.
2. **Reserved regions**: không đặt nút/nội dung quan trọng ở vùng có thể bị che (mép giữa khi resize, safe inset). Dùng `WindowInsets`.
3. **Displacement**: khi đổi arrangement, nội dung **không được "nhảy"** — giữ selection, scroll, text đang nhập. State ở ViewModel, không ở composable.
4. **Mỗi pane tự scroll độc lập**, có thể thao tác trên pane trái trong khi pane phải đang mở.

**Bằng chứng:** video kéo cửa sổ qua 3 ngưỡng, chậm, có hiển thị đồng hồ/state để chứng minh không mất dữ liệu.

---

### I-12 · CRITICAL THINKING (bắt buộc có sản phẩm cụ thể)
"Critical thinking" không phải thái độ — phải có **artifact**:

| Artifact | Nội dung | Ai viết |
|---|---|---|
| `docs/09-open-questions.md` | ≥ 12 câu hỏi mở, mỗi câu có **giả định tạm** + mức chắc chắn | cả 3 |
| `docs/adr/ADR-00x.md` | ≥ 5 quyết định kiến trúc, mỗi cái có **phương án bị loại + lý do** | BE/FE đề xuất, RV duyệt |
| `docs/review/red-team.md` | ≥ 10 kịch bản "cái gì sẽ nổ" + cách chặn | RV |
| `docs/06` §fallback | Phương án B cho mọi thứ rủi ro | RV + FE |
| Bảng "giả định → test" | Mỗi giả định phải có test hoặc ghi rõ chưa kiểm | BE |

**Câu hỏi phản biện bắt buộc trả lời trong demo:** *"Nếu công ty này có 1.000 đơn/ngày thay vì 10, cái gì sẽ nổ trước?"* — có câu trả lời cụ thể, không chung chung.

---

## 3. Ma trận truy vết: instruction → nơi thi hành

| Instruction | Nhúng trong | Skill | RV kiểm bằng |
|---|---|---|---|
| I-01 AI-Native | `AGENTS.md` Luật 9 | `agents/_shared/skills/ai-native-delivery.md` | `ai-log.md` tồn tại + có số liệu |
| I-02 Demo Video | `AGENTS.md` Luật 8, `docs/06` | `agents/review/skills/07-demo-readiness.md` | MP4 + prompt log |
| I-03 Voice | `docs/06`, `docs/04` §12 | `agents/fe/skills/13-ai-layer-in-app.md` | file audio trong `demo/voice/` |
| I-04 Wow | `docs/06` §2 | `agents/review/skills/07-demo-readiness.md` | cảnh được đặt tên |
| I-05 FDE | `AGENTS.md` §3, `agents/_shared/AGENT-BASE.md` §1 | — | báo cáo có mục OUTCOME |
| I-06 Outcome-first | `docs/03` §1 | — | mỗi phase có cảnh demo |
| I-07 Creative | `AGENTS.md` §4 | — | ADR khi vượt allowlist |
| I-08 Ask-well | `agents/_shared/AGENT-BASE.md` §5 | `agents/_shared/skills/ai-native-delivery.md` | `ai-log.md` có câu hỏi tốt + AI sai |
| I-09 REA | `AGENTS.md` Luật 4 | `agents/_shared/skills/rea-model.md` | `test_rea_completeness.py` |
| I-10 Responsive | `docs/04` §4 | `agents/fe/skills/03-duo-adaptive-layout.md` | video resize 3 ngưỡng |
| I-11 Duo | `docs/04` §4 | `agents/fe/skills/03-duo-adaptive-layout.md` | layout 3 ngưỡng |
| I-12 Critical | `AGENTS.md` §7 | `agents/review/skills/06-critical-thinking.md` | 4 artifact |

---

## 4. Ba hiểu lầm dễ mắc (và cách tránh)

| Hiểu lầm | Thực tế | Tránh |
|---|---|---|
| "Không giới hạn điều kiện" = làm gì cũng được | Nghiệp vụ và native-first vẫn là ràng buộc cứng | I-07 allowlist |
| "Dùng AI" = chèn AI vào sản phẩm | Chấm **quy trình** làm việc với AI, không chỉ tính năng AI | I-01 + I-08 artifacts |
| "Responsive" = chạy được cả điện thoại | Chấm **duo arrangement + giữ ngữ cảnh** | I-10 + I-11 |

---

## 5. Nếu chỉ nhớ 5 điều

1. **Outcome trước** — mỗi việc có cảnh demo hoặc test.
2. **Native-first** — chứng minh bằng Reuse Proof, không bằng lời.
3. **Wow moment thật** — 1 khoảnh khắc, dữ liệu thật, không diễn.
4. **Duo UI + responsive có luật** — 3 ngưỡng, split/overlay, không mất state.
5. **Critical thinking có file** — open questions, ADR, red-team, fallback.
