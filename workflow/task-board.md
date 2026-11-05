# TASK BOARD — bảng việc của 3 agent

> Giao tiếp chéo giữa agent = ghi vào file này. Không chat, không "tự sửa file người khác".
> Trạng thái: `TODO` · `DOING` · `REVIEW` · `DONE` · `BLOCKED` · `BACKLOG`

---

## 1. Bảng việc theo phase

### P0 — Móng & chốt quyết định
| ID | Việc | Ai | Trạng thái | Ghi chú |
|---|---|---|---|---|
| P0-01 | Dựng layout repo (`addons/`, `apps/`, `infra/`, `demo/`) | BE-01 | TODO | |
| P0-02 | `docker-compose.yml` + `odoo.conf` + DB `odoo17_lab` | BE-01 | TODO | Postgres 15+ |
| P0-03 | Vendor OCA 17.0: `base_tier_validation` (**server-ux**), `helpdesk_mgmt` (**helpdesk**) | BE-01 | TODO | `addons/oca/` read-only |
| P0-04 | Skeleton 3 module (`__manifest__.py` + `__init__.py`) cài được | BE-01 | TODO | |
| P0-05 | Skeleton app KMP (`jvm("desktop")`) hello world | FE-01 | **REVIEW** | Khung đã tạo + `bootstrap.ps1`; **chờ người chạy xác nhận compile** (shell agent không có mạng) |
| P0-06 | CI: `ruff check addons/`, test Odoo, build KMP | BE-01 + FE-01 | TODO | |
| P0-07 | **Chốt ADR-003** (`lab_chuyen` vs `stock_picking_batch` vs OCA `tms`) | BE-01 → RV-01 duyệt | **BLOCKED** | Đọc code `stock_picking_batch` + OCA `tms` trước |
| P0-08 | Chốt ADR-006 (Material 2 vs 3) | FE-01 | DONE | Đã chốt: M2 stable |
| P0-09 | Chốt ADR-002 (dashboard) | RV-01 | DONE | Đã kiểm chứng: `spreadsheet_dashboard` là CE |

### P1 — Native baseline (0 dòng code)
| ID | Việc | Ai | Trạng thái |
|---|---|---|---|
| P1-01 | 3 warehouse + location `KHO CHỜ` | BE-01 | TODO |
| P1-02 | UoM kg/m³, product cước, pricelist bậc thang | BE-01 | TODO |
| P1-03 | 8 nhóm quyền + user demo (CS, tài xế, kho, chứng từ, kế toán, manager, sếp) | BE-01 | TODO |
| P1-04 | Journal Debit + `l10n_vn` + hoá đơn VN | BE-01 | TODO |
| P1-05 | Đi hết 1 vòng: báo giá → đơn → receipt → delivery → invoice → payment | BE-01 | TODO |
| P1-06 | Seed data XML (3 shop, 1 đại lý, 3 tài xế, 5 đơn, 2 chuyến) | BE-01 | TODO |
| P1-07 | **Video 60s chứng minh native baseline (S1 gate)** | BE-01 + RV-01 | TODO |
| P1-08 | **Vendor source Odoo 17** vào `infra/odoo/addons` để Luật 1 (grep source) thi hành được offline | BE-01 | TODO | Cần mạng → **người** chạy `git clone --depth 1 --branch 17.0 https://github.com/odoo/odoo infra/odoo` |
| P1-09 | Định dạng số chứng từ năm 2/4 chữ số (OQ-19) | BE-01 | TODO | Kiểm bằng 1 lần tạo chuyến thật |

### P2 — `lab_core` + `lab_chuyen`
| ID | Việc | Ai | Trạng thái |
|---|---|---|---|
| P2-01 | `lab.flow.mixin` + 10 field mở rộng | BE-01 | TODO |
| P2-02 | `lab.chuyen` + `lab.chuyen.line` (model + state machine) | BE-01 | TODO |
| P2-03 | 5 view: tree/form/kanban/calendar/graph | FE-01 | TODO |
| P2-04 | Tải trọng + cảnh báo > 100% | BE-01 | TODO |
| P2-05 | Tier validation 2 cấp (⑥) | BE-01 | TODO |
| P2-06 | Wizard giảm tải (nhánh ②) | BE-01 | TODO |
| P2-07 | `ir.rule` + test security | BE-01 | TODO |
| P2-08 | 6 test (state, capacity, giảm tải, security) | BE-01 | TODO |

### P3 — `lab_laixe` + app driver
| ID | Việc | Ai | Trạng thái |
|---|---|---|---|
| P3-01 | `lab.yeu.cau.lay.hang` + state machine | BE-01 | TODO |
| P3-02 | Sinh `stock.picking` khi confirmed | BE-01 | TODO |
| P3-03 | 4 endpoint `/lab/api/v1/driver/*` + token | BE-01 | TODO |
| P3-04 | App: login → jobs → detail | FE-01 | TODO |
| P3-05 | App: capture ảnh + chữ ký + confirm | FE-01 | TODO |
| P3-06 | Offline outbox + retry | FE-01 | TODO |
| P3-07 | **S2 gate: đóng băng API contract** | BE-01 + FE-01 | TODO |

### P4–P9
| ID | Việc | Ai | Trạng thái |
|---|---|---|---|
| P4-01 | `lab.hanh.trinh` + timeline + 7 mốc | BE-01 | TODO |
| P4-02 | QWeb AWB/HBL/Manifest (font tiếng Việt!) | BE-01 | TODO |
| P4-03 | Portal track công khai (`portal.mixin` + route) | BE-01 | TODO |
| P4-04 | Template QWeb portal + trang track | FE-01 | TODO |
| P5-01 | `chargeable_weight` + 4 test air/sea | BE-01 | TODO |
| P5-02 | Wizard tạo Debit từ chuyến | BE-01 | TODO |
| P5-03 | Tier validation khoá Debit + luồng mở khoá | BE-01 | TODO |
| P5-04 | Hoá đơn + payment + đối soát + lock date | BE-01 | TODO |
| P6-01 | Nhánh ① hàng chờ (KHO CHỜ + wizard gán khách) | BE-01 | TODO |
| P6-02 | Nhánh ③ thiếu thông tin (`mail.activity`) | BE-01 | TODO |
| P6-03 | Nhánh ④ xuất thiếu kiện (backorder) | BE-01 | TODO |
| P6-04 | Nhánh ⑤ chuyển kho bay ↔ tàu | BE-01 | TODO |
| P6-05 | Nhánh ⑥ khiếu nại (`helpdesk_mgmt`) | BE-01 | TODO |
| P6-06 | **S3 gate: 6/6 nhánh có test điểm quay về** | RV-01 | TODO |
| P7-01 | `DuoScaffold` 3 ngưỡng + displacement | FE-01 | TODO |
| P7-02 | CS console: board + drag&drop đơn vào chuyến | FE-01 | TODO |
| P7-03 | Phím tắt + help overlay | FE-01 | TODO |
| P7-04 | Dark mode + i18n vi/en | FE-01 | TODO |
| P7-05 | A11y: Tab-only walkthrough | FE-01 | TODO |
| P8-01 | Pivot doanh thu/sản lượng + dashboard sếp | BE-01 | TODO |
| P8-02 | KPI screen trong app | FE-01 | TODO |
| P9-01 | AI: command palette / voice VieNeu (pre-generate) | FE-01 | TODO |
| P9-02 | `ai-log.md` + red-team + scoring | RV-01 | TODO |
| P9-03 | Video demo 3 phút | RV-01 + FE-01 | TODO |
| P9-04 | Rehearsal ×3 + snapshot DB | RV-01 | TODO |

---

## 2. Yêu cầu chéo (cross-requests)

| Từ | Đến | Nội dung | Cần trước | Trạng thái |
|---|---|---|---|---|
| FE-01 | BE-01 | Endpoint `GET /lab/api/v1/driver/jobs` trả thêm `so_kien`, `window_from`, `window_to` | P3-03 | TODO |
| FE-01 | BE-01 | Endpoint `GET /lab/api/v1/cs/board` trả `loadPercent` đã tính (app không tự tính) | P2-04 | TODO |
| FE-01 | BE-01 | `lab.chuyen` cần field `lab_hanhtrinh_stage` để hiển thị timeline trên card | P4-01 | TODO |
| BE-01 | FE-01 | View `lab.chuyen` form cần khối "Tải trọng" (progress bar đỏ khi > 100%) | P2-02 | TODO |
| BE-01 | FE-01 | Portal template `lab_track` chỉ hiện field whitelist | P4-03 | TODO |
| RV-01 | BE-01 | Mọi `sudo()` phải có comment lý do | P2-01 | TODO |
| RV-01 | FE-01 | Mọi màn phải có 4 trạng thái (loading/empty/error/content) | P3-04 | TODO |
| Orchestrator | RV-01 | Chốt ADR-003 trước khi BE viết `lab_chuyen` | P0-07 | **BLOCKED** |
| RV-01 | BE-01 | `infra/odoo.conf` + `vendor-oca.ps1` đang giả định "Odoo tự quét 1 cấp con" → nếu sai thì `addons/oca/<module>` vô hình. Cách rẻ: thêm `/mnt/extra-addons/oca` vào `addons_path` rồi chứng minh bằng `ir.module.module.search` — xem `docs/review/P0-baseline.md` P1-01 | P0-08 | TODO |
| RV-01 | BE-01 | `infra/scripts/reset-demo.ps1` phải lưu **UTF-8 có BOM** (PowerShell 5.1 đang đọc `LỖI` thành `Lá»–I`) — P1-02 | P0-05 | **DONE 10:59** (RV xác nhận 6/6 `.ps1` có BOM lúc 11:00) |
| RV-01 | Orchestrator | `docs/02` §1 mâu thuẫn về `depends` (`:46` vs `:48`) — BE-01 đã tạo `lab_hanhtrinh → lab_chuyen`, cần chốt bằng ADR/sửa docs — P1-06 | P0-04 | TODO |
| RV-01 | Orchestrator | `git init` + commit baseline (hoặc `docs/review/P0-hash-manifest.txt`) để kiểm biên giới file — hiện **không có VCS** — P1-05 | P0-06 | TODO |
| RV-01 | BE-01 | **`lab_chuyen` đã có code (10:55:30) trong khi ADR-003 vẫn PROPOSED** → RV chặn cục bộ `lab_chuyen`/`lab_laixe`. Điền ADR-003 (bảng chọn + dẫn chứng `stock_picking_batch`/`tms`) là đủ để mở lại — P0-01 | HO-01 | **BLOCKED** |

---

## 3. Việc bị chặn (BLOCKED)

| ID | Việc | Chặn bởi | Ai gỡ |
|---|---|---|---|
| P0-07 | Chốt ADR-003 | Chưa đọc code `stock_picking_batch` + OCA `tms` | BE-01 |
| P0-01 (RV) | Code `lab_chuyen` + `lab_laixe` (⑤⑥⑦⑬⑭) | ADR-003 vẫn `PROPOSED` trong khi code đã có (10:55:30) — RV chặn cục bộ | BE-01 điền ADR-003 → Orchestrator duyệt |
| P0-08 | Cài `base_tier_validation`/`helpdesk_mgmt` | Chưa vendor OCA + chưa chứng minh Odoo nhìn thấy `addons/oca/<module>` (P1-01) | Người chạy `vendor-oca.ps1` + BE-01 |
| OQ-01 | Công thức cước | Chưa xác nhận với khách | Orchestrator (dùng giả định, đánh dấu ⚠️) |
| OQ-09 | Cài `helpdesk_mgmt` | Chưa vendor OCA | BE-01 (P0-03) |

---

## 4. Nhật ký (append-only)

| Ngày | Ai | Việc |
|---|---|---|
| — | Orchestrator | Chốt bộ tài liệu v1.0 (docs 00–09, AGENTS, 3 agent, ADR, DoD) |
| 2026-10 | Orchestrator | **Wave A đóng**: BE-01 (5 module + check.ps1 + vendor-oca.ps1) · FE-01 (design system + DuoScaffold + login 4 trạng thái + selfCheck) · RV-01 (P0-baseline + red-team 20 + drift log). Verdict RV: CHANGES-REQUESTED; P0 blocker (ADR-003) **đã gỡ** bởi Orchestrator bằng bằng chứng đọc source `odoo@17.0`. |
| 2026-10 | Orchestrator | **Hotfix sau Wave A**: FE-01 dùng state `done` (không tồn tại) cho `lab.yeu.cau.lay_hang`; đã sửa `DriverJobPresentation.kt` + `MockDriverJobsRepo.kt` sang đúng 7 state của model BE-01 (`new/assigned/en_route/picked_up/confirmed/cancelled/failed`) + nhãn chép từ Selection. Sau đó FE-01 đọc lại, tạo `DriverJobState.kt` (một nguồn duy nhất), thêm khối 4 vào `selfCheck` (8 assert) và **BUILD SUCCESSFUL + ĐẠT — 0 lỗi** → đã xác nhận. || — | RV-01 | Ghi nhận: đã kiểm chứng 9 sự thật Odoo 17 (xem cuối `docs/09`) — 3 sự thật làm thay đổi kế hoạch (`stock.quant.package`, tier validation ở `server-ux`, `spreadsheet_dashboard` là CE) |
| 2026-10-28 | RV-01 | **Wave A**: dựng cổng — `docs/review/P0-baseline.md` (11 điều kiện P0 + S1 + drift log), red-team mở rộng 15→20 kịch bản, audit native-first chạy thật (0 P0 · 0 P1). Verdict: **CHANGES-REQUESTED** cho P0 exit (0 P0 · 6 P1 · 18 P2 · 7 P3). **Không** review code BE/FE đang viết. |
| 2026-10 | Orchestrator | **Bai hoc quy trinh:** toi hotfix `feature/driver/**` trong luc FE-01 dang chay -> FE phai doc lai va hoan tat cho khop (FE gap 5 lan loi ghi file EIO). Lan sau: cho agent xong, HOAC send_message truoc khi sua. Quy uoc moi: **1 thoi diem chi 1 nguoi giu `feature/<x>/**`**. |
| 2026-10 | Orchestrator | **BUG that (lam D fail):** `[string]$Repo = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))` trong `param()` default -> `$PSScriptRoot` RONG tren Windows PowerShell 5.1 -> script crash `Cannot bind argument to parameter Path` TRUOC khi tai. Da sua ca 2 script (tinh Repo trong than script). Them `vendor-oca-zip.ps1` (tai ZIP, khong can git) lam duong lui. |
| 2026-10 | Orchestrator | **RV-01 đúng, BE-01 sai:** `addons_path` thieu `/mnt/extra-addons/oca` -> OCA vo hinh -> `lab_chuyen` khong cai duoc. BE lap luan "copy ra addons/oca/<module> la du" la SAI (Odoo chi quet 1 cap con cua MOI entry). Da sua odoo.conf. Bai hoc: **P1 cua RV phai duoc thi hanh truoc khi coi la xong**, khong duoc "ghi nhan roi bo qua". |
| 2026-10 | Orchestrator | **Môi trường:** shell agent mặc định không có mạng/Docker. Cách xin: `sandbox_permissions: danger-full-access` (prompt duyệt) → có mạng + Docker cho lệnh đó. Nhờ vậy Orchestrator tự chạy được `setup.ps1` thay vì bắt người dùng chạy tay. |
| 2026-10 | Orchestrator | **GATE LUẬT 7 PASS (lần đầu tiên).** Shell được cấp `danger-full-access` → tự chạy được Docker. Kết quả: 7/7 module `installed` (OCA + 5 dự án); test suite `0 failed, 0 error(s) of 19 tests`; có test `test_tai_xe_chi_thay_yeu_cau_cua_minh` (ir.rule) PASS. Bug bắt được khi chạy: test OCA import `odoo_test_helper` (không có trong image `odoo:17.0`) → CRITICAL. Đã sửa bằng `infra/Dockerfile.odoo` + image `applogictics/odoo:17.0-testhelper`, verify lại: build exit 0, test exit 0, **không pip tay**. |
| 2026-10 | Orchestrator | **2 lỗi script của tôi:** (a) `Dc up -d \| ForEach-Object` treo vì container đã healthy (no-op nhưng pipeline không thoát) → thay bằng `run --rm`; (b) bộ lọc output nuốt dòng `Pulling/Downloading` nên trông như treo → đã thêm thông báo thời gian. |
| 2026-10 | Orchestrator | **Bài học:** chạy `check.ps1`/audit TRONG LÚC agent đang ghi file → FAIL giả (manifest `__init__` chưa kịp import, log cũ lẫn log mới). Quy ước: chỉ chạy gate khi agent đã báo xong, hoặc đọc **khối AUDIT cuối cùng** trong log chứ không quét `^FAIL` toàn file. |
| 2026-10 | Orchestrator | **WAVE B PASS — 46/46 test xanh.** Upgrade exit 0; 12 view + 4 seed nạp; DB `menu=1 · views=12 · chuyen=2 (1 chuyến 120% tải) · layhang=5 · hanhtrinh=1`. Vòng sửa: 14 đỏ → 5+1 → 1 → **0**. 6 lỗi thật bắt được khi chạy: (1) `account_move.py` `_inherit` list thiếu `_name`; (2) `ti_le_tai` compute không `store` → "Unsearchable field"; (3) manifest thiếu depends trực tiếp `account/stock/sale`; (4) portal user đọc `hr.employee` → 403 (BE sửa bằng cột stored `driver_user_id`); (5) portal đọc `ir.attachment` → 403 (dùng `sudo()` + marker, giới hạn theo job đã qua ir.rule); (6) test Debit thiếu `request_validation()`. BE tự sửa đúng hơn chẩn đoán của Orchestrator ở (4)(5) và tìm ra cache o2m `Many2oneReference`. |
