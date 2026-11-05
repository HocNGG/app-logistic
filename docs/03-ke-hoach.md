# 03 — KẾ HOẠCH XÂY DỰNG (Build Plan)

> Kế hoạch này được thiết kế để **3 agent (BE · FE · Review)** chạy song song có kiểm soát, không giẫm chân nhau.
> Mỗi phase có: **mục tiêu · đầu vào · đầu ra · người làm · tiêu chí nghiệm thu (DoD) · rủi ro**.

---

## 1. Nguyên tắc điều hành

| # | Nguyên tắc | Hệ quả thực tế |
|---|---|---|
| 1 | **Native-first, tuyệt đối** | Không có dòng code nào cho một bước 🟩/🟨 trong `docs/01`. Review chặn ngay. |
| 2 | **Demo-driven** | Mỗi phase phải tạo ra **một thứ nhìn thấy được trong video demo**. Không có phase nào là "hạ tầng vô hình". |
| 3 | **Vertical slice trước, ngang sau** | Làm 1 luồng xuyên suốt (①→③→⑤→⑥→⑦→⑬→⑮) chạy được **trước**, rồi mới mở rộng 6 nhánh ngoại lệ. |
| 4 | **Ngoại lệ không phải phase cuối** | 6 nhánh ngoại lệ là **điều kiện nghiệm thu của phase tương ứng**, không gom vào "phase 8 làm sau". |
| 5 | **Contract-first giữa BE và FE** | FE không chờ BE. Chốt API contract (`docs/02` §6) ở P1, FE mock theo contract. |
| 6 | **AI làm phần thủ công** | Sinh code, sinh data demo, sinh test, sinh script video — nhưng **review bằng mắt người/agent Review**. |
| 7 | **Một nguồn sự thật** | `docs/00`–`docs/07` + `AGENTS.md`. Không có quyết định nằm ngoài tài liệu. |

---

## 2. Đội hình 3 agent

```
                    ┌──────────────────────────────┐
                    │   ORCHESTRATOR (bạn/người)   │
                    │  chốt scope · duyệt ADR ·    │
                    │  quyết định ưu tiên          │
                    └───────────┬──────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
   ┌────┴─────┐          ┌──────┴──────┐        ┌───────┴──────┐
   │  BE-01   │          │   FE-01     │        │  RV-01       │
   │ Odoo 17  │◄──contract──►  KMP +   │        │  Gatekeeper  │
   │ backend  │          │  Odoo UI    │        │  + FDE demo  │
   └────┬─────┘          └──────┬──────┘        └───────┬──────┘
        │                       │                       │
        └──────────►  artifacts  ◄───────────────────────┘
                   (workspace)
```

| Agent | File | Sở hữu (write) | Được đọc | Không được chạm |
|---|---|---|---|---|
| **BE-01** | `agents/be/AGENT.md` | `addons/**/*.py`, `security/*.csv`, `data/*.xml`, `i18n/*.po`, `tests/**`, `report/*.xml` | tất cả | `apps/**`, `docs/**` (chỉ đọc) |
| **FE-01** | `agents/fe/AGENT.md` | `apps/kmp/**`, `addons/**/views/*.xml`, `addons/**/static/**` | tất cả | `addons/**/models/*.py`, `security/*.csv` |
| **RV-01** | `agents/review/AGENT.md` | `docs/review/**`, `docs/adr/**` (đề xuất), `workflow/**` | tất cả | **không sửa code** — chỉ mở issue/patch đề xuất |

**Quy tắc vàng chống giẫm chân:** biên giới là **đuôi file**. `.py` = BE. `.kt/.xml view/.scss` = FE. Review chỉ viết markdown.

---

## 3. Lộ trình 10 phase

### Tổng quan

| Phase | Tên | Ai | Đầu ra demo được | Ước lượng |
|---|---|---|---|---|
| **P0** | Móng & chốt quyết định | Orchestrator + BE | Repo chạy được, DB `odoo17_lab` trắng | 0.5 ngày |
| **P1** | Native baseline (0 dòng code) | BE | Báo giá → đơn → picking → Debit → thanh toán chạy bằng cấu hình | 1 ngày |
| **P2** | `lab_core` + `lab_chuyen` | BE | Mở chuyến, gán đơn, duyệt 2 cấp, cảnh báo quá tải | 2 ngày |
| **P3** | `lab_laixe` + app KMP (driver) | BE + FE | Tài xế nhận việc trên desktop app, chụp ảnh, ký, xác nhận | 2.5 ngày |
| **P4** | `lab_hanhtrinh` + AWB/HBL/manifest | BE | Timeline hàng đi → đến → đại lý giao, in AWB | 2 ngày |
| **P5** | Cước kg → Debit → khoá → thu tiền | BE | Debit khoá được, mở khoá có lý do, hoá đơn, thu tiền, khoá tháng | 1.5 ngày |
| **P6** | 6 nhánh ngoại lệ | BE + FE | Mỗi nhánh bấm được trên UI, quay về luồng chính | 2 ngày |
| **P7** | App KMP: CS console + duo UI | FE | 2 pane, kéo thả, keyboard shortcut, responsive | 2.5 ngày |
| **P8** | Báo cáo & dashboard | BE + FE | Pivot doanh thu/sản lượng + màn KPI | 1 ngày |
| **P9** | AI layer + video demo + hardening | Cả 3 | Video 3 phút + demo live không crash | 2 ngày |

**Tổng: ~17 ngày-người** (≈ 6–8 ngày lịch nếu 3 agent chạy song song đúng nhịp).

---

### P0 — Móng & chốt quyết định (0.5 ngày)

**Mục tiêu:** không viết code nghiệp vụ nào. Chỉ dựng đúng nền.

| Việc | Ai | DoD |
|---|---|---|
| Dựng repo theo layout §4 | BE | `addons/`, `apps/`, `infra/` tồn tại |
| Chạy Odoo 17 CE + PostgreSQL, DB `odoo17_lab` | BE | `http://localhost:8069` login được admin |
| Vendor OCA 17.0: `base_tier_validation`, `helpdesk_mgmt` | BE | `--addons-path` include `addons/oca/` |
| Chốt 3 ADR: `lab_core` (ADR-001), dashboard (ADR-002), chuyến vs batch (ADR-003) | RV đề xuất, Orchestrator duyệt | 3 file trong `docs/adr/` |
| Skeleton 3 module `lab_*` (manifest, rỗng) | BE | Cài được, không lỗi |
| Skeleton app KMP (`jvm("desktop")`), hello world | FE | `./gradlew run` mở cửa sổ |
| Bật CI: lint Python (`ruff`/`pylint-odoo`), KMP build, test Odoo | BE + FE | CI xanh với skeleton |

**Rủi ro:** OCA 17.0 thiếu module → ghi vào ADR và chọn phương án B trước khi sang P1.

---

### P1 — Native baseline, 0 dòng code (1 ngày)

> **Phase này chứng minh toàn bộ giá trị của `docs/01`.** Nếu ai đó muốn viết code ở đây, họ đang sai.

| Việc | Công cụ | DoD |
|---|---|---|
| Tạo 3 warehouse: `KHO CHÍNH`, `KHO BAY`, `KHO TÀU` + location `KHO CHỜ` | `stock.warehouse` | Chọn được trong picking |
| Bật Packages, UoM kg/m³, product `Cước vận chuyển (kg)` + `Phí lấy hàng` | `stock`, `uom`, `product` | Đơn vị kg chọn được |
| Pricelist bậc thang: 0–50kg, 50–100kg, 100kg+ (air/sea khác giá) | `product.pricelist` | Đổi số kg → giá đổi |
| Tạo `res.partner`: 3 shop, 1 đại lý nước ngoài, 3 tài xế | `res.partner` | — |
| Luồng: báo giá → xác nhận đơn → receipt → delivery → invoice → payment | `sale_stock`, `account` | Đi hết 1 vòng không lỗi |
| Journal `Debit` + hoá đơn VN + `l10n_vn` | `account` | Đã có bút toán VND |
| 8 nhóm quyền + gán user demo | `res.groups` | Login từng user thấy đúng |
| Tier validation cấu hình sẵn trên `sale.order` (để test cơ chế) | `base_tier_validation` | Duyệt 2 cấp bấm được |
| **Seed data demo** bằng XML/CSV | `data/` | `--init` một lệnh có dữ liệu |

**Nghiệm thu P1:** một video 60s quay màn hình đi hết ①③④⑦⑫⑬⑮⑯ **chỉ bằng UI Odoo**, không có module `lab_*`. Đây là **bằng chứng native-first**.

---

### P2 — `lab_core` + `lab_chuyen` (2 ngày)

| Việc | DoD |
|---|---|
| `lab.flow.mixin` + 10 field mở rộng ở `docs/02` §2.1 | Migrate sạch, `-u lab_core` không lỗi |
| `lab.chuyen` + `lab.chuyen.line` với 5 view: list/form/kanban/calendar/graph | Form dùng `<header>` + statusbar đúng Odoo 17 |
| Sequence `CH/AIR/26/0001` | Tạo chuyến có mã |
| Tải trọng: `ti_le_tai` + warning khi > 100% | Đơn vượt tải → cảnh báo đỏ |
| Duyệt 2 cấp (⑥) trên `lab.chuyen` | Manager duyệt → nút "Duyệt" mới sáng |
| Wizard giảm tải (nhánh ②) | Chọn đơn → gỡ → `waitlist` → gán chuyến khác |
| `ir.rule`: CS thấy chuyến của mình, kho thấy tất cả trong kho mình | Test security pass |
| 6 test: state, capacity, giảm tải, security | `--test-tags=/lab_chuyen` xanh |

**Demo được:** mở chuyến HAN–SGN, kéo 5 đơn shop vào, thanh tải đầy 120% → đỏ → giảm tải → duyệt.

---

### P3 — `lab_laixe` + app KMP driver (2.5 ngày)

| Việc | Ai | DoD |
|---|---|---|
| Model `lab.yeu.cau.lay.hang` + state machine §3.2 | BE | 7 test transition |
| Sinh `stock.picking` khi `confirmed` | BE | Picking có `lab_pickup_mode='pickup'` |
| `access_token` + controller `/lab/api/v1/driver/*` | BE | 4 endpoint trả đúng shape |
| App KMP: login → danh sách việc → chi tiết | FE | Chạy trên desktop Windows |
| Chụp ảnh: chọn file + preview + gửi lên (multipart) | FE | Ảnh nằm trong `ir.attachment` |
| Vẽ chữ ký (Canvas) → lưu base64 → `chu_ky` | FE | Chữ ký hiện trong Odoo |
| Offline queue: mất mạng vẫn lưu, có mạng gửi lại | FE | Tắt wifi, thao tác, bật lại → đồng bộ |
| Demo: tài xế xe máy đi lấy hàng shop, ký, xác nhận | FE+BE | Video 45s |

**Rủi ro lớn nhất của dự án** (xem §6 R3): chữ ký/ảnh trên desktop. Phương án B: dùng ảnh chụp sẵn trong `data/` nếu demo camera không ổn định.

---

### P4 — `lab_hanhtrinh` + chứng từ (2 ngày)

| Việc | DoD |
|---|---|
| `lab.hanh.trinh` + line, stage 7 mốc | Timeline hiển thị đẹp (widget `mail_activity`-style hoặc custom kanban) |
| Field AWB/HBL/manifest + `mail.thread` | Lưu số, có chatter |
| QWeb report AWB + HBL + Manifest | In PDF ra đúng tiếng Việt, không lỗi font |
| Portal: `/my/hanhtrinh/<token>` | Khách mở link thấy timeline, không thấy giá/đối tác nội bộ |
| Nhánh ⑤ chuyển kho bay ↔ tàu | Internal transfer + cập nhật chuyến mới |
| Nhánh ⑥ khiếu nại → `helpdesk_mgmt` | Ticket mở từ hành trình |

**Lưu ý font:** PDF tiếng Việt cần font hỗ trợ Unicode (DejaVu/Roboto) — đưa vào `lab_core/static/fonts` và khai báo `@font-face` trong CSS report. Lỗi font là lỗi demo hay gặp nhất.

---

### P5 — Cước kg → Debit → khoá → thu tiền (1.5 ngày)

| Việc | DoD |
|---|---|
| `lab_chargeable_weight = max(actual, volumetric)` với divisor 6000/1000 theo loại chuyến | Test 4 case: air/sea × nhẹ/nặng |
| Debit từ `lab.chuyen`: wizard tạo `account.move` gom theo `lab.chuyen.line` | 1 chuyến → N Debit (1/shop) |
| Tier validation trên `account.move` (⑭) | Chưa duyệt → không post được |
| Khoá Debit: sau khi duyệt → chặn sửa, có nút "Yêu cầu mở khoá" | Thử sửa → `UserError` có thông báo tiếng Việt |
| Mở khoá: chỉ `group_manager` + bắt buộc nhập lý do + ghi chatter | Có audit trail |
| Hoá đơn + thu tiền + đối soát + `period_lock_date` | Đi hết vòng kế toán |
| Test: `test_debit_lock.py` | Xanh |

---

### P6 — 6 nhánh ngoại lệ (2 ngày)

> Mỗi nhánh là **một chùm task nhỏ**, và **điều kiện nghiệm thu chung**: *bấm từ luồng chính → vào ngoại lệ → xử lý → quay về luồng chính → dữ liệu vẫn nhất quán*.

| Nhánh | Task chính | Test bắt buộc |
|---|---|---|
| ① Hàng chưa rõ khách | Nhận vào `KHO CHỜ` không cần partner → màn "đối chiếu" → gán khách → sinh picking thật | Không lọt hàng vào kho chính khi chưa gán |
| ② Rớt chuyến / giảm tải | Wizard giảm tải + tier duyệt + `waitlist` | Gỡ đơn không làm mất đơn; gán lại được |
| ③ Đơn thiếu thông tin | `mail.activity` type "Bổ sung thông tin" + chặn submit | Đơn không vào được ⑥ khi còn activity mở |
| ④ Xuất thiếu kiện | Backorder + tìm/bổ sung + bồi thường | Backorder tự sinh, cân nặng cộng dồn đúng |
| ⑤ Chuyển kho bay ↔ tàu | Internal transfer 2 chiều + cập nhật chuyến | Tồn kho 2 kho đúng, lịch sử có |
| ⑥ Khiếu nại · trả hàng | Ticket + return picking + credit note | Return picking trỏ đúng picking gốc |

**Chỉ số chất lượng (Review đo):** mỗi nhánh có **điểm quay về** được chứng minh bằng test. 6/6 = PASS.

---

### P7 — App KMP: CS console + duo UI (2.5 ngày)

| Việc | DoD |
|---|---|
| Layout 2 pane thích ứng (duo UI) — xem `docs/04` | Kéo cửa sổ hẹp → chuyển 1 pane; rộng → 2 pane |
| Panel trái: danh sách yêu cầu/chuyến, panel phải: chi tiết + action | Không cần chuyển màn hình để xử lý |
| Bàn phím: `Cmd/Ctrl+K` search, `J/K` di chuyển, `Enter` mở, `R` refresh | Ghi trong help overlay |
| Kéo thả đơn vào chuyến | Kéo 1 đơn → thả vào chuyến → cập nhật |
| Design tokens + dark mode | Đổi theme không vỡ layout |
| i18n vi/en | Đổi ngôn ngữ không tràn chữ |
| A11y: focus ring, contrast ≥ 4.5:1, thao tác được chỉ bằng bàn phím | Đi hết luồng bằng Tab |

---

### P8 — Báo cáo & dashboard (1 ngày)

| Việc | DoD |
|---|---|
| Pivot: doanh thu theo tháng/shop/loại chuyến | Trả lời được "Doanh thu tháng này bao nhiêu, shop nào nhiều nhất" |
| Graph: sản lượng kg theo tuần | Có biểu đồ |
| Dashboard sếp: 4 KPI + 1 pivot + 1 graph (spreadsheet hoặc menu action) | Mở 1 trang thấy hết |
| Màn KPI trong app KMP (duo UI) | Số liệu khớp với Odoo (không lệch) |
| Export Excel/PDF từ pivot | Native, không code |

---

### P9 — AI layer + video demo + hardening (2 ngày)

| Việc | DoD |
|---|---|
| AI Copilot trong Odoo (nếu đủ thời gian) hoặc trong app KMP | Trả lời được "Đơn nào sắp trễ chuyến?" bằng dữ liệu thật |
| AI tạo yêu cầu lấy hàng từ câu lệnh tiếng Việt | "Tạo yêu cầu lấy hàng cho shop A, 5 kiện, 120kg" → record tạo đúng |
| OCR AWB (nếu đủ thời gian) | Chụp AWB → tự điền số + kg |
| Voice-over tiếng Việt (**VieNeu AI**) cho video | File audio khớp timeline |
| Video demo 3 phút (**opus 5.5** hỗ trợ script + dựng) | Xuất MP4 1080p, xem liền mạch |
| Rehearsal 3 lần: demo live không crash, có phương án B | Checklist `docs/06` pass |
| Hardening: seed data reset 1 lệnh, snapshot DB trước demo | `reset-demo.ps1` chạy < 60s |

Chi tiết: `docs/06-demo-video.md`.

---

## 4. Layout repo

```
AppLogictics/
├── AGENTS.md                       # hợp đồng chung (đọc đầu tiên)
├── README.md
├── docs/                           # 00→07 + adr/ + review/
├── agents/                         # 3 agent + skill packs
│   ├── _shared/{AGENT-BASE.md,skills/}
│   ├── be/{AGENT.md,skills/}
│   ├── fe/{AGENT.md,skills/}
│   └── review/{AGENT.md,skills/}
├── workflow/                       # handoff, DoD, task-board
├── addons/                         # Odoo 17 addons path
│   ├── lab_core/
│   ├── lab_chuyen/
│   ├── lab_laixe/
│   ├── lab_hanhtrinh/
│   ├── lab_api/
│   └── oca/                        # vendor OCA 17.0 (read-only với agent)
├── apps/
│   └── kmp/                        # Kotlin Multiplatform desktop
├── infra/
│   ├── docker-compose.yml          # postgres + odoo 17
│   ├── odoo.conf
│   └── scripts/                    # reset-demo.ps1, run-tests.ps1, seed.ps1
├── demo/                           # script video, assets, voice-over
└── _intake/                        # PDF gốc, text, ảnh trang (read-only)
```

---

## 5. Nhịp làm việc (ưu tiên cho 3 agent)

```
Ngày 1  P0 + P1            BE làm chính, FE dựng skeleton, RV viết 3 ADR + DoD
Ngày 2  P2                 BE làm chính, FE đọc contract + mock API, RV review P1
Ngày 3  P3 (BE+FE song song) BE: model + API | FE: app driver | RV: security review
Ngày 4  P4 + P5            BE làm chính, FE polish driver app, RV review P3
Ngày 5  P6                 BE + FE chia nhau 6 nhánh, RV đo "điểm quay về"
Ngày 6  P7                 FE làm chính (duo UI), BE fix bug, RV UX review
Ngày 7  P8 + P9            cả 3: báo cáo, AI, quay video, rehearsal
```

**Điểm đồng bộ (sync point)** — không tự ý vượt:
- **S1 (cuối P1):** chứng minh native baseline. Nếu fail → dừng, sửa cấu hình, không code.
- **S2 (cuối P3):** API contract KMP đóng băng. Sau S2 đổi contract phải có ADR.
- **S3 (cuối P6):** 6/6 nhánh ngoại lệ pass. Nếu thiếu → cắt P8 (báo cáo) chứ không cắt ngoại lệ.
- **S4 (P9):** demo rehearsal + video.

---

## 6. Sổ rủi ro

| # | Rủi ro | Xác suất | Tác động | Phương án |
|---|---|---|---|---|
| **R1** | Viết lại Odoo (model `debit`, `cuoc`…) | Cao | Chí mạng | `docs/01` + 3 câu hỏi của Review agent + Reuse Proof bắt buộc |
| **R2** | 6 nhánh ngoại lệ bị bỏ cuối, không kịp | Cao | Chí mạng | Ngoại lệ là DoD của phase, không phải phase riêng (P6 cố định) |
| **R3** | App KMP desktop không kịp demo (signature/camera/offline) | Trung bình | Cao | Phương án B: chỉ demo 3 màn (login, jobs, capture) với ảnh có sẵn; video quay trước |
| **R4** | OCA 17.0 thiếu `base_tier_validation`/`helpdesk_mgmt` | Thấp | Trung bình | Dùng `base_automation` + `mail.activity` tạm; ghi ADR |
| **R5** | Font tiếng Việt trong PDF QWeb lỗi | Trung bình | Trung bình | Nhúng font vào module + test in PDF ở P4 |
| **R6** | Cấu hình ban đầu quá phức tạp, không demo được | Trung bình | Cao | Seed data bằng XML, `reset-demo.ps1`, snapshot DB |
| **R7** | AI layer "wow" nhưng không thật (giả lập) | Trung bình | Cao | AI phải gọi dữ liệu thật; nếu không kịp → cắt AI khỏi demo, không giả |
| **R8** | Video demo 3 phút ôm đồm 19 bước | Cao | Trung bình | Chỉ kể 1 luồng + 2 ngoại lệ; 19 bước để trong sơ đồ |
| **R9** | Scope creep từ ảnh của sếp ("không giới hạn điều kiện") | Cao | Trung bình | Mọi thứ ngoài 19 bước → backlog, không vào sprint |
| **R10** | Tranh chấp file giữa BE và FE | Thấp | Trung bình | Biên giới đuôi file (§2) |

---

## 7. Backlog (cố tình KHÔNG làm)

| Ý tưởng | Vì sao hoãn |
|---|---|
| Tích hợp Zalo OA thật | Cần tài khoản doanh nghiệp; `mail.thread` đủ chứng minh |
| Hoá đơn điện tử VN (MISA/VNPT) | Cần hợp đồng nhà cung cấp |
| Thanh toán online (VNPay/Stripe) | Ngoài 19 bước |
| Mobile target Android/iOS | Đề bài chỉ yêu cầu desktop target |
| Multi-company | Đã chốt 1 công ty |
| AI dự báo tải chuyến | Wow nhưng không cần cho demo |
| SSO/OAuth | Không cần |
| Kế toán chi tiết giá vốn chuyến (controlling) | EE-level (analytic account) — dùng `analytic` nếu còn thời gian |

---

## 8. Cách chứng minh "đã xong" (tóm tắt DoD)

Một phase chỉ PASS khi:

1. **Chức năng:** luồng chính bấm được bằng UI, không dùng shell.
2. **Test:** test của module liên quan xanh; có test cho nhánh lỗi.
3. **Native-first:** có mục Reuse Proof trong PR/commit message.
4. **Tiếng Việt:** mọi nhãn UI tiếng Việt đúng nghiệp vụ, không "New", "Save".
5. **Quyền:** login bằng user của đúng phòng ban, thấy đúng dữ liệu.
6. **Demo:** có ít nhất 1 cảnh quay được cho video.

Chi tiết: `workflow/definition-of-done.md`.

---

*Tiếp theo: `docs/04-kmp-app.md` — đặc tả app KMP & duo UI.*
