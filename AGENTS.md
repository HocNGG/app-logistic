# AGENTS.md — HỢP ĐỒNG CHUNG CHO 3 AGENT

> **Đọc file này trước mọi file khác.** Nếu mâu thuẫn: `AGENTS.md` > `docs/` > `agents/*/AGENT.md` > skill.
> Dự án: **Hệ thống ERP gom hàng lẻ xuất khẩu trên Odoo 17 + app Kotlin Multiplatform**.

---

## 0. Bạn là ai

Bạn là một phần của đội **3 agent** cùng xây một sản phẩm thật, demo được, cho một bài đánh giá năng lực tuyển **Forward Deployed Engineer (FDE)**.

| Agent | Vai | File hướng dẫn |
|---|---|---|
| **BE-01** | Odoo 17 backend — model, security, data, report, test | `agents/be/AGENT.md` |
| **FE-01** | Kotlin Multiplatform desktop app + Odoo views/portal | `agents/fe/AGENT.md` |
| **RV-01** | Review & gatekeeper — chặn cái sai, đo cái đúng | `agents/review/AGENT.md` |

Đọc thêm: `agents/_shared/AGENT-BASE.md` (quy tắc chung), `agents/<role>/skills/` (bộ skill chuyên biệt).

---

## 1. Mục tiêu cuối (North Star)

> **Một sản phẩm gom hàng lẻ chạy được trên Odoo 17 CE, khoác lên một app KMP desktop đẹp và thông minh, kèm một video demo 3 phút khiến người xem hiểu ngay "đây là người hiểu nghiệp vụ + biết dùng AI + không viết lại thứ có sẵn".**

Ba trụ:
1. **Đúng nghiệp vụ** — 19 bước, 6 ngoại lệ, 6 phòng ban (docs/00).
2. **Đúng Odoo 17** — native-first, code theo convention, không viết lại (docs/01).
3. **Đúng kỳ vọng FDE** — AI-native, demo-driven, critical thinking (docs/05).

---

## 2. MƯỜI LUẬT BẤT BIẾN (Invariants)

Vi phạm bất kỳ luật nào → **sai, không cần bàn thêm**. RV-01 có quyền reject vô điều kiện.

### Luật 1 — NATIVE TRƯỚC, LUÔN LUÔN
Trước khi viết bất kỳ dòng code nào, trả lời 3 câu (trích nguyên văn vào PR):
1. Odoo 17 đã có model nào mang đúng ngữ nghĩa này chưa? (grep `addons/` trước khi grep Google)
2. Có thể biểu diễn bằng *field + view + config* trên model có sẵn không?
3. Nếu cần model mới, nó nằm trong 3 module được cấp phép không? ADR đâu?

Thang 5 bậc: **L0 native → L1 config → L2 inherit → L3 module mới → L4 fork (CẤM)**. Xem `docs/01` §1.
**Cấm tuyệt đối:** tạo model `debit`, `cuoc`, `kien`, `lo_hang`, `khoi_luong`, `tai_xe`, `khieu_nai`.

### Luật 2 — ODOO 17 IDIOMS, KHÔNG PHẢI ODOO 16 (và càng không phải 18/19)
Đã kiểm chứng với source `odoo/odoo@17.0`. **Những thứ sau là SAI ở 17.0:**

| ❌ Sai (16, 18 hoặc 19) | ✅ Đúng ở 17.0 |
|---|---|
| `attrs="{'invisible': [...]}"` / `states="..."` trong view | `invisible="state == 'draft'"` — **`attrs` và `states` bị hard-remove**, `_check_xml` raise `ValidationError("Since 17.0, the attrs and states attributes are no longer used")` |
| `quantity_done`, `qty_done`, `reserved_uom_qty` | `quantity` (trên `stock.move` = Σ move line) + cờ `picked` |
| `states={...}` kwarg trên `fields.*` | Đã bỏ — readonly-theo-state chuyển vào **view** |
| `read_group(...)` | `_read_group(domain, groupby, aggregates)` |
| `SavepointCase` | `TransactionCase` / `SingleTransactionCase` / `HttpCase` |
| `stock.package` (tên của 18.0) | **`stock.quant.package`** (type vẫn là `stock.package.type`) |
| `<list>` (tên của 18.0) | **`<tree>`** — 17.0 chưa có `list` trong `ir.ui.view.type` |
| `hard_lock_date` (tên của 18.0) | `fiscalyear_lock_date` (+ `period_lock_date`, `tax_lock_date`) |
| `is_storable` (18.0) | `product.template.type == 'product'` |
| OWL 1 `this.state`, `props.value` | OWL 2: `useState`, `props.record.data[props.name]`, `patch` từ `@web/core/utils/patch` |
| `Authorization: Bearer` (19.0) | 17.0 **không có** — API key thay password, `session_id` chỉ trả qua **cookie** |
| `models.Constraint` (18.0) | 17.0 vẫn dùng **`_sql_constraints`** |

**Bắt buộc:** mọi `models/*.py` dùng Python 3.10+, `from odoo import models, fields, api, _`, `from odoo.exceptions import UserError, ValidationError`. Không dùng API Enterprise (`studio`, `documents`, `sign`, `helpdesk` bản EE).
**Nếu nghi ngờ giữa 16→17:** mở source `odoo/addons/<module>/models/*.py` bản **17.0** rồi mới dùng. Không code theo trí nhớ.

### Luật 3 — PHÂN QUYỀN BẰNG `ir.rule`, KHÔNG BẰNG `if`
- Record-level: `ir.rule` (XML, có `groups`).
- Field-level: `groups="..."` trong view.
- Menu: `groups` trên `menuitem`.
- **Cấm:** `if not self.env.user.has_group(...): raise` trong business logic.
- Mọi model mới **phải** có 4 dòng ACL trong `security/ir.model.access.csv` cho mỗi nhóm user.
- `sudo()` chỉ được dùng khi có comment giải thích + RV duyệt.

### Luật 4 — MỌI SỰ KIỆN KINH TẾ PHẢI CÓ BÚT TOÁN (REA)
Bảng REA ở `docs/02` §11. Nếu một event (nhận hàng, xuất kho, giao đại lý, mất hàng) xảy ra mà không sinh chứng từ tương ứng → **lỗi thiết kế**, không phải "tính năng thiếu".
Debit = `account.move`. Cấu trúc sinh ra tiền **không bao giờ** là model riêng.

### Luật 5 — SÁU NHÁNH NGOẠI LỆ CÓ ĐIỂM QUAY VỀ
Mỗi nhánh (docs/07) phải có: đường vào ≤ 2 click, đường ra quay lại luồng chính, và **một test chứng minh điểm quay về**. Ngoại lệ không được tạo luồng song song vĩnh viễn.
Nếu không vẽ được "điểm quay về" → thiết kế sai, dừng lại.

### Luật 6 — TIẾNG VIỆT LÀ NGÔN NGỮ SẢN PHẨM
- Nhãn UI, thông báo lỗi, email, report: **tiếng Việt có dấu**, dùng đúng thuật ngữ nghiệp vụ (chuyến, kiện, cước, debit, kho chờ, giảm tải — xem glossary `docs/00` §10).
- Không lộ chuỗi tiếng Anh mặc định của Odoo ở màn hình demo.
- Chuỗi trong Python phải bọc `_()`; bản dịch trong `i18n/vi.po`.
- Comment code: tiếng Việt hoặc tiếng Anh đều được — nhưng tài liệu/PR mô tả: **tiếng Việt**.

### Luật 7 — KHÔNG CÓ CODE KHÔNG TEST
- Model mới → tối thiểu 1 `TransactionCase` cho mỗi transition state + 1 case lỗi.
- Endpoint mới → 1 `HttpCase`.
- UI mới (KMP) → ít nhất 1 compose UI test hoặc kịch bản smoke có ghi lại.
- Test phải chạy được bằng lệnh trong `README` — không "test trên máy tôi chạy được".

### Luật 8 — DEMO LÀ MỘT TÍNH NĂNG
Mỗi phase phải tạo ra **một cảnh quay được**. Cấm phase thuần hạ tầng không nhìn thấy được.
Seed data phải bật lại được bằng 1 lệnh (`infra/scripts/reset-demo.ps1`). Trước demo phải snapshot DB.

### Luật 9 — AI-NATIVE, NHƯNG PHẢI THẬT
- Dùng AI (đọc code, sinh code, sinh test, sinh data, viết script demo, review chéo) ở mọi khâu thủ công.
- **Nhưng:** mọi output của AI phải được kiểm chứng bằng chạy thật, không "nghe hợp lý nên tin".
- **Cấm giả lập AI trong demo.** Nếu AI trả lời không có dữ liệu thật → cắt khỏi demo, không diễn.
- Ghi lại prompt/ cách dùng AI vào `docs/ai-log.md` (bằng chứng năng lực FDE).

### Luật 10 — BIÊN GIỚI FILE = BIÊN GIỚI TRANH CHẤP
| Đuôi / thư mục | Chủ sở hữu |
|---|---|
| `addons/**/models/*.py`, `security/*.csv`, `data/*.xml`, `wizard/*.py`, `tests/**`, `i18n/**`, `report/*.xml` | **BE-01** |
| `addons/**/views/*.xml`, `addons/**/static/**`, `apps/kmp/**` | **FE-01** |
| `docs/**`, `workflow/**` | **RV-01** (trừ `docs/00`–`docs/07` đã FROZEN — chỉ Orchestrator sửa) |
| `addons/oca/**` | **Không ai sửa** — vendor read-only |

Muốn sửa file của người khác → mở **yêu cầu** trong `workflow/task-board.md`, không tự sửa.

---

## 3. Mô hình vận hành FDE

> Nguyên văn từ chat của sếp: **"Start with the outcome, do what it takes to succeed, then follow with scale."**

Dịch thành hướng dẫn thực thi:

| Câu | Nghĩa trong dự án |
|---|---|
| **Start with the outcome** | Mỗi việc bắt đầu bằng "cảnh này trong video demo là gì / test này chứng minh gì". Không bắt đầu bằng "tôi sẽ refactor module". |
| **Do what it takes** | Không có "không làm được vì thiếu lib". Có phương án B, C. Tự cài, tự generate data, tự viết script. Nhưng **không** vượt Luật 1–5. |
| **Then follow with scale** | Trước khi chạy 4 module song song, chứng minh 1 luồng xuyên suốt. Đừng "scale" một thiết kế sai. |

**Nghĩa là:** được quyền **thử**, **dựng nhiều phương án**, **đo**, nhưng mỗi bước đi phải có **outcome kiểm chứng được**. Không có "spike vô tận".

---

## 4. "Thỏa sức sáng tạo, không giới hạn điều kiện" — sáng tạo có kỷ luật

Sếp cho phép tự do. Nhưng **tự do trong khuôn khổ nghiệp vụ**:

**Được khuyến khích (và nên làm):**
- Sáng tạo trong **UX** (duo UI, animation, keyboard-first, empty state đẹp).
- Sáng tạo trong **AI layer** (copilot, voice, OCR, gợi ý giảm tải).
- Sáng tạo trong **cách demo** (video, kể chuyện, so sánh trước/sau).
- Thêm tính năng **ai cũng thấy đúng** nhưng đề bài không nói (vd: in phiếu giao hàng cho tài xế).

**Bị chặn (dù "sáng tạo"):**
- Thêm model trùng Odoo.
- Thêm microservice/broker/queue cho bài toán 19 bước.
- Đổi công nghệ (không Odoo 17, không KMP).
- Tự chế định dạng dữ liệu thay cho `sale.order`/`stock.picking`.

**Thử thách dành cho sự sáng tạo:** *bạn có thể làm bài này hay hơn với **ít code hơn** không?* Đó mới là sáng tạo được đánh giá cao.

---

## 5. Quy trình làm việc (bắt buộc)

```
1. Đọc AGENTS.md + docs/00 + docs/01 + agents/<role>/AGENT.md
2. Đọc skill liên quan trong agents/<role>/skills/
3. Lập Reuse Proof (Luật 1) → ghi vào PR/commit message
4. Làm 1 vertical slice nhỏ nhất chạy được
5. Chạy test/lint
6. Tự review bằng checklist của role
7. Handoff → workflow/handoff.md
8. RV-01 review → PASS / CHANGES-REQUESTED
```

**Không được:** nhảy vào code khi chưa đọc Luật 1. **Không được:** handoff khi test đỏ.

---

## 6. Definition of Done (tóm tắt — chi tiết `workflow/definition-of-done.md`)

- [ ] Chạy được bằng UI (không dùng shell để demo).
- [ ] Test của module xanh, có case lỗi.
- [ ] Có Reuse Proof.
- [ ] Tiếng Việt đúng nghiệp vụ ở mọi nhãn người dùng thấy.
- [ ] Đúng quyền: login user đúng phòng ban thấy đúng dữ liệu.
- [ ] Có ít nhất 1 cảnh quay được cho video demo.
- [ ] Không có file ngoài biên giới bị sửa.
- [ ] Đã cập nhật `workflow/task-board.md`.

---

## 7. Khi bí (escalation) — không tự bịa

| Tình huống | Làm gì |
|---|---|
| Không biết Odoo có sẵn không | Grep `addons/`. Không chắc → ghi vào `docs/09-open-questions.md` với **giả định tạm** + đánh dấu `⚠️ ASSUMPTION` trong code comment, tiếp tục phần không bị chặn |
| Cần quyết định kiến trúc | Viết ADR ngắn (5 dòng) trong `docs/adr/`, đề xuất RV + Orchestrator |
| Module OCA không có bản 17 | Phương án B: native thay thế. Ghi ADR. Không fork OCA |
| Yêu cầu mâu thuẫn | Ưu tiên: `AGENTS.md` > sơ đồ nghiệp vụ > ảnh của sếp > giả định riêng |
| Hết thời gian phase | Cắt phần **phụ** (dashboard), **không** cắt ngoại lệ, **không** cắt test, **không** cắt tiếng Việt |

---

## 8. Từ vựng bị cấm trong code

| Cấm | Vì sao | Dùng |
|---|---|---|
| `debit` (model) | Trùng `account.move` | `account.move` journal Debit |
| `cuoc`, `phi_van_chuyen` (model) | Trùng `sale.order.line` | product + pricelist |
| `kien_hang` (model) | Trùng `stock.quant.package` | `stock.quant.package` |
| `lo_hang` (model) | Không có trong Odoo, `stock.picking` + `lab.chuyen.line` |
| `tai_xe` (model) | Trùng `hr.employee` + `fleet.vehicle` | `hr.employee` + `fleet.vehicle` |
| `khieu_nai` (model) | Trùng `helpdesk.ticket` | `helpdesk_mgmt` |
| `attrs={...}` | Bỏ ở Odoo 17 | `invisible="..."` |
| `quantity_done` | Đổi tên ở 17 | `quantity` / `picked` |
| `if user.has_group()` trong logic | Không phải Odoo way | `ir.rule` |
| `TODO` không người | Mồ côi | `TODO(BE-01): ...` + issue |

---

## 9. Lệnh tham chiếu (dùng đúng, đừng phát minh)

```powershell
# CHẠY QUA DOCKER (khuyến nghị — không cần Python/Odoo trên máy)
docker compose -f infra/docker-compose.yml up -d

# Cài / nâng cấp module (addons đã mount vào /mnt/extra-addons)
docker compose -f infra/docker-compose.yml exec odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api --stop-after-init

# Chạy test
# ⚠️ `run --rm --no-deps` = container RIÊNG. KHÔNG dùng `exec` cho lệnh test:
# HttpCase tự mở HTTP server ở cổng 8069; nếu chạy `exec` trong container đang phục vụ thì cổng
# đã bị server chính giữ -> "OSError: [Errno 98] Address already in use", request của test bay
# sang SERVER THẬT. Đã gặp thật 2026-10-28: 16 test đỏ 401 oan vì dữ liệu tạo trong transaction
# của test không tồn tại ở server thật. Container riêng có network namespace riêng -> cổng trống.
docker compose -f infra/docker-compose.yml run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh,/lab_api --stop-after-init

# ---- Nếu chạy odoo-bin trên HOST (không docker) thì phải override db_host ----
python odoo-bin -c infra/odoo.conf -d odoo17_lab --db_host=localhost --db_port=5432

# App KMP desktop
cd apps/kmp; ./gradlew :composeApp:run
cd apps/kmp; ./gradlew :composeApp:packageDistributionForCurrentOS

# Reset demo (1 lệnh)
powershell -File infra/scripts/reset-demo.ps1
```

Lint: `ruff check addons/`, `ruff format addons/`. Không tự thêm tool mới mà chưa hỏi.

---

## 10. Câu hỏi tự vấn trước khi handoff

1. Nếu người review là **khách hàng** (chủ doanh nghiệp gom hàng), họ có hiểu màn hình này không?
2. Nếu **bỏ hết code tôi viết**, Odoo 17 có làm được 80% việc này không? Nếu có → tôi đã viết thừa.
3. Nếu demo **mất mạng / DB trống / hết 30 giây**, nó có còn chạy không?
4. Cái tôi vừa làm có **một cảnh trong video** không? Nếu không → tại sao làm?
5. Tôi đã **dùng AI** để làm nhanh hơn ở đâu, và tôi đã **kiểm chứng** nó thế nào?

---

## 11. Skill & nguồn ngoài

Muốn thêm skill từ GitHub/marketplace → đọc `workflow/skill-import.md` trước.
Ba luật cứng:

1. **Thứ tự ưu tiên:** `AGENTS.md` > `docs/` > `agents/<role>/AGENT.md` > skill của repo > skill nhập ngoài. Skill ngoài **không bao giờ** đè hợp đồng.
2. **Phải đăng ký:** file skill không được liệt kê trong `agents/<role>/AGENT.md` §5 là **file chết** — agent không biết nó tồn tại.
3. **Phải có header nguồn:** URL + commit + license + lý do nhập. Không viết được "lý do nhập" → đừng nhập.

**Skill nền dùng chung (bắt buộc đọc theo việc):** `agents/_shared/skills/odoo-native-doctrine.md` · `ai-native-delivery.md` · `rea-model.md` · `vietnamese-business-language.md` · **`ui-modern-industrial.md`** (design system của dự án — nguồn token màu/typo DUY NHẤT, ADR-007) · **`design-taste-anti-slop.md`** (luật cho *trang công khai*: không gradient/glass/glow/icon-card/pill-badge, tiêu đề ≤ 2 dòng, một nền-một mực-một nhấn — vendor từ `Leonxlnx/taste-skill` + `addyosmani/agent-skills`, MIT) · **`ui-dashboard-app-screens.md`** (luật cho *màn hình làm việc*: KPI card, 4 loại chart, chip trạng thái, sidebar — dựng từ 4 ảnh mẫu của sếp trong `docs/review/refs/`).

Nguồn đã kiểm chứng (Odoo/KMP/mẫu skill): xem bảng trong `workflow/skill-import.md` §3.

---

## 12. Runbook — nơi trả lời "chạy ở cổng nào"

Mọi dịch vụ đã mở (Odoo, Postgres, app KMP, mock server, Remotion…) **phải** có dòng trong `workflow/runbook.md`: cổng · lệnh đầy đủ · cách kiểm tra sống · ai ghi.

Lý do: câu trả lời trong chat sẽ mất; dòng trong runbook thì không. Đây cũng là tín hiệu FDE "writes the handover".

**Đặc biệt:** app KMP desktop **không mở cổng** — nó là client gọi ra Odoo `:8069`. Muốn xem kết quả thì nhìn cửa sổ app, không nhìn trình duyệt.

---

*Bắt đầu từ `agents/<role>/AGENT.md`.*
