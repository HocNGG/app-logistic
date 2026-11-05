# AppLogictics — ERP gom hàng lẻ xuất khẩu (Odoo 17 + Kotlin Multiplatform)

> Bài tập đánh giá năng lực — vị trí **Forward Deployed Engineer (FDE)**, Đề số 4.
> Sản phẩm: **Odoo 17 Community** cho nghiệp vụ gom hàng lẻ (19 bước, 6 phòng ban, 6 nhánh ngoại lệ) + **app KMP desktop** cho tài xế & CS + **video demo 3 phút**.

---

## Bắt đầu từ đâu (thứ tự đọc)

| Bước | File | Vì sao |
|---|---|---|
| **0** | **`ONBOARDING.md`** | **Người mới / để trả lời sếp: đọc 10 phút là hiểu toàn bộ dự án** |
| 1 | **`AGENTS.md`** | 10 luật bất biến của dự án. Không đọc = làm sai. |
| 2 | `docs/00-nghiep-vu.md` | 19 bước, 6 phòng ban, 6 ngoại lệ, glossary |
| 3 | `docs/01-tai-su-dung-odoo17.md` | **Ledger native-first** — bước nào dùng Odoo sẵn, bước nào viết code |
| 4 | `docs/02-kien-truc.md` | Model, field, state machine, phân quyền, API contract |
| 5 | `docs/03-ke-hoach.md` | 10 phase, ai làm gì, sync point, rủi ro |
| 6 | `docs/04-kmp-app.md` | Đặc tả app KMP + duo UI |
| 7 | `docs/05-yeu-cau-cua-sep.md` | 12 instruction chuyên biệt từ yêu cầu của sếp |
| 8 | `docs/06-demo-video.md` | Kịch bản video 3 phút |
| 9 | `docs/07-exception-flows.md` | 6 nhánh ngoại lệ + điểm quay về |
| 10 | `docs/08-do-luong-kpi.md` | Đo cái gì, con số nào được nói trong demo |

---

## Bản đồ tài liệu

```
AGENTS.md                     hợp đồng chung — 10 luật bất biến
README.md                     file này
docs/
  00-nghiep-vu.md             phân tích nghiệp vụ (19 bước / 6 nhánh)
  01-tai-su-dung-odoo17.md    ledger native-first + module cần cài
  02-kien-truc.md             kiến trúc, model map, security, API
  03-ke-hoach.md              kế hoạch 10 phase + rủi ro
  04-kmp-app.md               đặc tả app KMP (duo UI, offline, packaging)
  05-yeu-cau-cua-sep.md       12 instruction chuyên biệt (I-01..I-12)
  06-demo-video.md            storyboard 8 cảnh + pipeline + checklist
  07-exception-flows.md       6 nhánh ngoại lệ chi tiết
  08-do-luong-kpi.md          đo lường & tác động (KPI, con số dùng trong demo)
  09-open-questions.md        18 câu hỏi mở + giả định (bằng chứng critical thinking)
  ai-log.md                   nhật ký làm việc với AI (≥3 câu hỏi tốt, ≥2 lần AI sai)
  adr/                        6 ADR (quyết định kiến trúc + phương án bị loại)
  review/                     verdict của RV-01 + red-team + audit log
agents/
  _shared/AGENT-BASE.md       cách làm việc chung (suy nghĩ, báo cáo, khi bí)
  _shared/skills/             4 skill nền: native doctrine, AI-native, REA, tiếng Việt
  be/AGENT.md + 15 skills     Odoo 17 backend
  fe/AGENT.md + 13 skills     KMP desktop + Odoo views/portal
  review/AGENT.md + 8 skills  gatekeeper + FDE demo
workflow/
  handoff.md                  mẫu bàn giao + sync point
  definition-of-done.md       D1–D8, thước đo PASS
  task-board.md               bảng việc + yêu cầu chéo
addons/                       Odoo 17 addons (lab_core, lab_chuyen, lab_laixe, lab_hanhtrinh, lab_api, oca/)
apps/kmp/                     Kotlin Multiplatform desktop app
infra/                         docker-compose, odoo.conf, scripts reset-demo
demo/                          kịch bản video, voice-over, assets
_intake/                       PDF gốc + text + ảnh trang (read-only)
```

---

## Ba con số phải nhớ

| | |
|---|---|
| **19 bước** | Luồng nghiệp vụ chuẩn (10 pha, 6 phòng ban) |
| **10 + 3 + 6** | 10 bước Odoo đã có · 3 bước chỉ cấu hình · **6 bước phải viết code** |
| **3 module** | `lab_chuyen` (chuyến gom lô) · `lab_laixe` (điều tài xế) · `lab_hanhtrinh` (hành trình quốc tế) — cộng `lab_core` (module keo, ADR-001) |

> **Câu thần chú:** *Native trước — cấu hình trước — chỉ viết code khi Odoo 17 thật sự không có.*

---

## Quick start (sau khi P0 hoàn tất)

```powershell
# 1. Odoo 17 + PostgreSQL
docker compose -f infra/docker-compose.yml up -d

# 2. Cài module (qua docker — addons đã mount vào /mnt/extra-addons)
docker compose -f infra/docker-compose.yml exec odoo odoo -c /etc/odoo/odoo.conf `
  -d odoo17_lab -u lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api --stop-after-init

# 3. Chạy test
docker compose -f infra/docker-compose.yml exec odoo odoo -c /etc/odoo/odoo.conf `
  -d odoo17_lab --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh --stop-after-init

# 4. App KMP
cd apps/kmp; ./gradlew :composeApp:run

# 5. Reset dữ liệu demo
powershell -File infra/scripts/reset-demo.ps1
```

Odoo: `http://localhost:8069` · DB `odoo17_lab` · tài khoản demo: xem `infra/scripts/seed-users.md`.

---

## Dùng 3 agent này thế nào

Mỗi `agents/<role>/AGENT.md` có sẵn một khối **SYSTEM PROMPT** — dán trực tiếp vào `subagent` để khởi tạo agent đó:

```
1. BE-01  ← agents/be/AGENT.md §11   → viết Odoo backend
2. FE-01  ← agents/fe/AGENT.md §10   → viết app KMP + view
3. RV-01  ← agents/review/AGENT.md §10 → review, chặn, chấm điểm
```

Quy trình: **BE/FE làm → handoff (`workflow/handoff.md`) → RV review → PASS/CHANGES/BLOCKED**.

Biên giới chống giẫm chân: `.py` = BE · `.kt/.xml view/.scss` = FE · `docs/` = RV. Chi tiết `AGENTS.md` Luật 10.

---

## Trạng thái hiện tại

| Hạng mục | Trạng thái |
|---|---|
| Phân tích nghiệp vụ | ✅ xong (docs/00, 07) |
| Ledger native-first | ✅ xong (docs/01) — đã kiểm chứng với source Odoo 17.0 |
| Kiến trúc & model map | ✅ xong (docs/02) |
| Kế hoạch 10 phase | ✅ xong (docs/03) |
| Đặc tả app KMP | ✅ xong (docs/04) |
| Instruction từ yêu cầu sếp | ✅ xong (docs/05) |
| Kịch bản video demo | ✅ xong (docs/06) |
| 3 agent + skill pack | ✅ xong |
| **Code (`addons/`, `apps/`)** | ⏳ **chưa bắt đầu** — P0/P1 là việc tiếp theo |
| Video demo | ⏳ chưa có — sau P7 |

> Bộ tài liệu này là **bản thiết kế thi hành được**, không phải slide. Bước tiếp theo: chạy **P0** (`docs/03` §3) — dựng Odoo 17 + skeleton 3 module + skeleton app KMP.

---

## Nguồn & kiểm chứng

Mọi khẳng định kỹ thuật quan trọng trong repo đã được kiểm chứng với **source `odoo/odoo@17.0`** hoặc tài liệu chính thức (xem bảng cuối `docs/09-open-questions.md`). Những điểm chưa chắc chắn được đánh dấu **UNVERIFIED** thay vì đoán.

Sơ đồ nghiệp vụ gốc: `_intake/so-do-nghiep-vu.pdf` (4 trang) — bản text: `_intake/pdf-text.txt`.
