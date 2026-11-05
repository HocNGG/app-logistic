# P0 BASELINE — Cổng kiểm soát Wave A

> **Ngày:** 2026-10-28 · **Reviewer:** RV-01 · **Phạm vi:** Wave A (dựng cổng, **không** review code đang viết)
> **MỐC CHỐT SỐ LIỆU: 11:00** — mọi con số dưới đây đúng tại mốc này; workspace đã thay đổi **trong lúc** dựng cổng (xem §12). Chỗ nào quá hạn, phải đo lại chứ không được tin file này.
> **Môi trường đo:** shell agent **không có mạng**, Docker **chưa bật** (`docker info` exit 1), **không có git** (`Test-Path .git` = False), **không có `rg`** (audit/check chạy bằng `Select-String` + .NET).
> **Người đọc:** Orchestrator (chốt), BE-01 (1 P0 + 3 P1), FE-01 (P0-04), RV-01 (review tiếp).

---

## 0. TL;DR — 60 giây

| Hạng mục | Kết quả (mốc 11:00) |
|---|---|
| **Cổng Wave A (deliverable RV)** | ✅ `docs/review/P0-baseline.md` (file này) · `red-team.md` 15 → **20 kịch bản** · `audit-native-first.log` (3 lần chạy) · `docs/09` sửa 3 chỗ · `task-board` 4 cross-request |
| **Audit native-first** | ✅ **0 P0 · 0 P1** — RV chạy trực tiếp **3 lần** (10:51:38, 10:54:42 rỗng, 10:58:51 đã có 53 file) + 4 lần do `check.ps1` gọi (10:59:37 → 11:00:18): tất cả exit 0 |
| **`check.ps1` (mới, 10:59)** | ✅ **PASS** — 6 PASS · 1 WARN · 0 FAIL · exit 0 (33 file `.py` biên dịch, 5 XML, 40 dòng ACL, 5 manifest) |
| **Code BE-01** | ⏳ 5 module + 53 file (10:55–10:59) — **không review** (đang viết) |
| **Code FE-01** | ⏳ ~20 file Kotlin (10:54–10:58: `DuoScaffold`, tokens, login, driver jobs, SelfCheck) — **không review** |
| **Verdict Wave A** | **CHANGES-REQUESTED** — cổng PASS, P0 exit **chưa đạt** (4 ✅ / 3 ⏳ / 4 ❌) |
| **+ 1 P0 cục bộ → BLOCKED** | `lab_chuyen`/`lab_laixe` bị chặn cho tới khi **ADR-003** được điền (code `lab_chuyen` đã có từ 10:55:30 trong khi ADR-003 vẫn `PROPOSED`) |
| **Còn lại** | 4 P1 · 17 P2 · 9 P3 — **0 P0 khác** |

**Nếu Orchestrator chỉ đọc 1 dòng:** *cổng đã chạy và đã bắt được lỗi thật (BOM `.ps1` đã được sửa ngay trong wave); thứ duy nhất chặn đường là **ADR-003 chưa được điền** — điền xong là hạng mục `lab_chuyen` được mở, và Docker bật lên là đo được 5 module.*

---

## 1. Phạm vi P0 (Wave A)

| Thành phần | Ai | Deliverable P0 | Trạng thái ghi nhận (RV **không** review code) |
|---|---|---|---|
| **BE-01** | BE-01 | Skeleton 5 module + manifest + script tĩnh (`infra/`) | ⏳ **đã có**: 5 `__manifest__.py` (10:55:10 → 10:57:46), 53 file, 4 file test, `check.ps1` (10:59:32), `vendor-oca.ps1` (10:59:01), `odoo.conf` viết lại 10:58:07. Chưa cài/chưa test được (Docker tắt) |
| **FE-01** | FE-01 | Design system + `DuoScaffold` + login + màn demo + bootstrap Gradle | ⏳ **đã có**: `DuoScaffold.kt` (10:54:31), `Tokens/AppTheme` (10:55:48–10:57:33), `LoginScreen` (10:56:15), `DriverJobsScreen` (10:57:17), `SelfCheck.kt` (10:57:48); wrapper + jar desktop 10:47 |
| **RV-01** | RV-01 | Cổng: `P0-baseline.md`, `red-team.md`, audit log, drift log, verdict | ✅ xong trong wave này |
| **Orchestrator** | Orchestrator | Chốt ADR-003, bật Docker/Odoo, quyết định VCS, chạy `vendor-oca.ps1` | ❌ 4/4 còn mở (§5) |

**Không thuộc P0** (đừng chấm, đừng đòi): model nghiệp vụ đầy đủ, view, `ir.rule`, test Odoo, seed data, wizard, portal. Đó là P2–P6.

---

## 2. Điều kiện PASS P0 — checklist kiểm được

> Luật của cổng: **chỉ đóng P0 khi 11/11 dòng dưới đây ✅**. `⏳` = chưa kiểm được trong phiên này (không phải lỗi của ai); `❌` = **điều kiện chưa đạt**.

| # | Điều kiện PASS | Lệnh kiểm (copy-paste) | Bằng chứng RV đang có (11:00) | TT | Ai chốt |
|---|---|---|---|---|---|
| **P0-01** | 5 module cài được trên Odoo 17 CE, DB `odoo17_lab`, 0 `ERROR/CRITICAL` | `docker compose -f infra/docker-compose.yml up -d` → `docker compose -f infra/docker-compose.yml exec odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab -i lab_core,lab_chuyen,lab_laixe,lab_hanhtrinh,lab_api --stop-after-init` | Không có: Docker tắt; 5 manifest **đã tồn tại** nhưng chưa cài lần nào | ⏳ | BE-01 + người bật Docker |
| **P0-02** | `check.ps1` tồn tại và xanh (exit 0) | `powershell -ExecutionPolicy Bypass -File infra/scripts/check.ps1` | ✅ 10:59:48 — **6 PASS · 1 WARN · 0 FAIL · exit 0**; WARN: `lab_core/security/ir.model.access.csv` chỉ có header (hợp lý nếu `lab_core` chưa có model lưu DB, nhưng phải xác nhận khi cài) | ✅ | BE-01 |
| **P0-03** | `audit-native-first.ps1` → **0 P0** | `powershell -ExecutionPolicy Bypass -File infra/scripts/audit-native-first.ps1` | ✅ 2 lần: 10:54:42 (rỗng, `addons/` chưa có file) và **10:58:51** (đã có 21 dòng "model" — xem §3 về nhiễu) — 0 P0 · 0 P1 · exit 0 | ✅ | RV-01 |
| **P0-04** | FE **compile xanh offline** trên máy demo | `cd apps/kmp; .\gradlew.bat --offline :composeApp:compileKotlinDesktop` → `BUILD SUCCESSFUL`; `.\gradlew.bat --version` in `JVM: 21.x`; `.\gradlew.bat :composeApp:run` mở cửa sổ | Gián tiếp: `composeApp-desktop.jar` 22.538 B (10:47:49) + `build/classes/...`; **chưa có log nào** trong handoff; RV không tự chạy để tránh khoá Gradle với FE-01 | ⏳ | FE-01 + người (bấm `run`) |
| **P0-05** | Mọi `.ps1` là UTF-8 **có BOM**; tiếng Việt đọc đúng trên PowerShell 5.1 | đọc 3 byte đầu phải là `239 187 191`; `powershell -Command "Get-Content <file> \| Select-Object -First 30"` | ✅ **6/6** `.ps1` có BOM lúc 11:00 (đã sửa `reset-demo.ps1` lúc **10:59:32** — xem §12) | ✅ | RV-01 xác nhận lại khi handoff |
| **P0-06** | **Không file nào ngoài biên giới bị sửa** (Luật 10) | `git status --porcelain` (rỗng) **hoặc** so `Get-FileHash` với snapshot P0 | ❌ không kiểm được: **không có git**; 2 agent ghi song song vào cùng cây | ⏳ → **P1-05** | Orchestrator |
| **P0-07** | ADR-003 đã chốt (A/B/C) **kèm bằng chứng đọc code** | đọc `docs/adr/ADR-003-chuyen-vs-batch.md` — bảng chọn phải có ☑ + dòng lý do | ❌ vẫn `PROPOSED` (mtime 09:44:38), bảng chọn trống; **trong khi** `addons/lab_chuyen/models/lab_chuyen.py` đã có (10:55:30) | ❌ → **P0-01** | BE-01 → Orchestrator |
| **P0-08** | OCA 17.0 vendor đúng nhánh **và Odoo nhìn thấy** | `addons/oca/base_tier_validation/__manifest__.py` có `version '17.0.*'`; sau cài: `env['ir.module.module'].search([('name','=','base_tier_validation')]).state == 'installed'` | ❌ `addons/oca/` chỉ có `.gitignore` + `.gitkeep`; `vendor-oca.ps1` đã có (10:59:01) nhưng **cần người chạy** (có mạng). Kèm rủi ro P1-01 (giả định "Odoo tự quét 1 cấp con") | ❌ | Người chạy + BE-01 |
| **P0-09** | Odoo sống ở `:8069`, login được, DB `odoo17_lab` | `Invoke-WebRequest http://localhost:8069/web/login` → 200; login admin | ⏳ Docker tắt (`failed to connect to ... dockerDesktopLinuxEngine`) | ⏳ | người dùng |
| **P0-10** | Lệnh trong `README`/`AGENTS.md` §9 **copy-paste được** | chạy đúng 3 lệnh ở `AGENTS.md` §9 | ❌ `odoo.conf` **đã** ghi cách override `--db_host=127.0.0.1` (dòng 64–66, 10:58:07) nhưng `AGENTS.md:216` vẫn trỏ `infra/odoo/addons` **không tồn tại**, và `README.md:83,87` vẫn là lệnh host không override | ❌ → **P1-03** | BE-01 |
| **P0-11** | Cổng RV có bằng chứng đầy đủ | `Get-ChildItem docs/review` | ✅ `P0-baseline.md`, `red-team.md` (20 dòng), `audit-native-first.log`, `README.md` | ✅ | RV-01 |

**Tally 11:00:** ✅ **4** (P0-02, 03, 05, 11) · ⏳ **3** (P0-01, 04, 09) · ❌ **4** (P0-06, 07, 08, 10) → **P0 CHƯA PASS.**

> So với đầu wave (2 ✅), cổng đã có tác dụng ngay: **P0-05 từ ❌ → ✅** và **P0-02 từ ❌ → ✅** trong 5 phút, không cần RV sửa một dòng code nào.

---

## 3. Audit + check tĩnh — kết quả (bằng chứng)

### 3.1 `audit-native-first.ps1` — 2 lần chạy

```
> powershell -ExecutionPolicy Bypass -File infra/scripts/audit-native-first.ps1     # lần 1, 10:54
Audit native-first: 0 P0 · 0 P1  ->  PASS native-first
Log: ...\docs\review\audit-native-first.log
EXITCODE=0
# log: (chưa có addons/ — bình thường ở P0)

# lần 2, 10:58:51 — addons/ đã có 53 file, 5 module
KET LUAN: 2026-10-28 10:58:51 — 0 P0, 0 P1 — PASS native-first
```

**Nhật ký log (10 entry, tất cả 0 P0 · 0 P1):** 09:38:08 · 09:43:27 · 09:44:44 (3 entry của phiên trước) · **10:51:38** (RV, rỗng) · **10:54:42** (RV, rỗng) · **10:58:51** (RV, có code — 21 "model" trong đó 6 thật) · 10:59:37 · 10:59:49 · 11:00:11 · 11:00:18 (gọi từ `check.ps1` — BE-01 và RV-01 đã thực sự dùng gate này).

**Cách đọc — đừng đọc quá xa:**
1. **Lần 1 là PASS rỗng**: `addons/` chưa có file nào. Giá trị thật của audit chỉ bắt đầu từ lần 2.
2. **Lần 2 vẫn PASS** trên code đang viết, nhưng danh sách "models (lab.*) found" trong log **bị nhiễu**: script dùng pattern `_name\s*=` nên **khớp cả `comodel_name=` và `inverse_name=`**. Log liệt kê 21 tên, thực tế model mới chỉ 6 (`lab.chuyen`, `lab.chuyen.line`, `lab.flow.mixin`, `lab.hanh.trinh`, `lab.hanh.trinh.line`, `lab.yeu.cau.lay.hang`); còn lại là `account.move`, `chuyen_id`, `delivery.carrier`, `hr.employee`, `lab_chuyen_id`, `inverse_name`… → **P3-08**. Verdict A1 hiện **không sai** (các tên nhiễu không dạng `lab.*` có dấu chấm), nhưng nếu ai đọc log để "đếm model" sẽ đếm sai.
3. Script chạy bằng `Select-String` (không có `rg`) và **A12 chỉ quét `*.py`** → không quét `.xml/.js/.kt`. Phải nói rõ khi dùng log làm bằng chứng.

### 3.2 `check.ps1` (BE-01 thêm lúc 10:59) — cổng tĩnh offline

```
==> [1/7] Cú pháp Python            [PASS] 33 file .py biên dịch OK
==> [2/7] XML well-formed           [PASS] 5 file .xml OK
==> [3/7] ir.model.access.csv       [WARN] 4 file, 40 dòng ACL; lab_core chỉ có header
==> [4/7] audit-native-first.ps1    [PASS] Exit 0 — không có P0/P1
==> [5/7] __manifest__.py & __init__ [PASS] 5 module lab_*
==> [6/7] Encoding                  [PASS] 8 file OK (4 file .ps1 + CSV)
==> [7/7] YAML (bonus)              [PASS] 1 file .yml không có TAB
BẢNG TỔNG HỢP — 6 PASS · 1 WARN · 0 FAIL
KẾT LUẬN: PASS (cảnh báo)     CHECK EXIT=0
```

**Đánh giá:** đây là **script đúng kiểu** — tự khai báo cái nó **không** kiểm được (module cài, test Odoo, `ir.rule`) thay vì để người đọc tưởng "PASS = xong". Giữ nguyên tinh thần này khi thêm bước sau.
**Hai điều phải theo dõi:** (a) WARN `lab_core` ACL — nếu `lab_core` sinh model lưu DB mà ACL vẫn header-only thì thành **P0 lỗi quyền** khi cài thật; (b) mục [6] chỉ quét 4 `.ps1` trong `infra/scripts`, **bỏ** `apps/kmp/bootstrap.ps1` và `demo/voice/generate-voice.ps1` → **P3-09** (cả 2 hiện đều có BOM, nên chưa thiệt hại).

---

## 4. Sync point S1 (cuối P1) — điều kiện và cách chứng minh

> Gate này là **bằng chứng native-first của cả dự án** (`workflow/handoff.md` S1, `docs/03` P1). Trượt S1 → **cấm code** `lab_*`; sửa cấu hình, không sửa luật.

| # | Điều kiện S1 | Cách chứng minh (không chấp nhận lời khai) | TT |
|---|---|---|---|
| **S1-1** | **Video 60s** đi hết luồng native ①→③→④→⑦→⑫→⑬→⑮→⑯ **chỉ bằng UI Odoo** (không shell, không SQL) | `demo/out/S1-native-60s.mp4` (60 ± 5s) + `demo/script.vi.srt`; RV xem, đếm đủ 8 mốc: báo giá → đơn → receipt → package → delivery → cước kg → Debit → payment | ⏳ |
| **S1-2** | **0 dòng code `lab_*`** trong luồng quay phim | `docker compose ... exec db psql -U odoo -d odoo17_lab -c "select name,state from ir_module_module where name like 'lab_%'"` → **không** dòng `installed`; kèm audit `0 P0` | ⏳ |
| **S1-3** | DB dựng lại được 2 lần liên tiếp (idempotent) | `powershell -File infra/scripts/reset-demo.ps1` chạy **2 lần**, cả 2 exit 0 và < 60s | ⏳ |
| **S1-4** | 8 nhóm quyền + user demo đăng nhập đúng dữ liệu | Ảnh chụp 4 user (CS / tài xế / kế toán / manager) mở cùng 1 bản ghi: CS thấy, tài xế không thấy — kèm `infra/scripts/seed-users.md` cập nhật | ⏳ |
| **S1-5** | Bút toán thật: ⑬ Debit = `account.move` `out_invoice`; ⑮ payment reconciled | Ảnh chụp `account.move` + `account.payment` cùng số tiền; số kg trên Debit = kg trên picking (một nguồn — pain P2 `docs/00` §1) | ⏳ |
| **S1-6** | Tiếng Việt trên mọi màn trong video | RV soi khung hình: **0** chuỗi `New / Save / Confirm / Validate / Draft`; thuật ngữ đúng glossary (`docs/00` §10) | ⏳ |
| **S1-7** | RV đã ghi verdict S1 | `docs/review/S1-pass.md` (hoặc `S1-changes-requested.md`) tồn tại | ⏳ |

**Trạng thái S1 hôm nay:** P1 chưa bắt đầu (P1-01…P1-07 `TODO`) → S1 **chưa thể chấm**. Câu chốt cho Orchestrator: *"Nếu phải chọn giữa viết `lab_chuyen` và quay video 60s native — quay video trước."*

---

## 5. Những gì **CHƯA kiểm được** trong phiên này — và ai kiểm

| Hạng mục | Vì sao chưa kiểm được | Ai kiểm sau | Bằng chứng phải nộp |
|---|---|---|---|
| Cài/upgrade 5 module, test Odoo, `ir.rule` chạy thật | **Docker daemon tắt**: `docker info` → `failed to connect ... dockerDesktopLinuxEngine` (exit 1) | Người dùng bật Docker (pull `odoo:17.0`, `postgres:15`) → BE-01 chạy | Log `-i ... --stop-after-init` sạch + `--test-tags=/lab_*` 0 failed |
| Vendor OCA 17.0 (`base_tier_validation`, `helpdesk_mgmt`) | `addons/oca/` chỉ có placeholder; `vendor-oca.ps1` cần **mạng** (shell agent không có) | **Người dùng** chạy `powershell -File infra/scripts/vendor-oca.ps1` → BE-01 xác nhận | 2 `__manifest__.py` (version/license) + log cài `-i` |
| Odoo có nhìn thấy `addons/oca/<module>` hay không | Cần Odoo thật để kiểm; hiện chỉ có **giả định** trong comment (`odoo.conf:25-28`, `vendor-oca.ps1` docstring) rằng "Odoo tự quét thêm 1 cấp con" | BE-01 + RV-01, ngay sau khi vendor | 1 trong 2 lệnh: `docker compose ... exec odoo odoo shell -d odoo17_lab` → `env['ir.module.module'].search([('name','=','base_tier_validation')])`; hoặc `-i base_tier_validation` ra `installed` |
| **Tái lập bảng "đã kiểm chứng" của `docs/09`** (attrs, quantity, package, Bearer, tier hook…) | Source Odoo **không có trong workspace**; các dòng `H`/`M` là di sản phiên trước, **không tái lập được offline** | BE-01 + RV-01 khi có image | `docker run --rm odoo:17.0 grep -rn "Since 17.0, the attrs and states" /usr/lib/python3/dist-packages/odoo/addons/base/models/ir_ui_view.py` + tương tự cho `models.py` → dán vào `docs/09` |
| FE compile/run thật | RV không chạy build để tránh khoá Gradle với FE-01 đang làm; shell không có mạng để tải dependency | FE-01 + người (terminal thật) | `BUILD SUCCESSFUL` + `JVM: 21.x` + ảnh cửa sổ app trong `workflow/handoff.md` |
| Chốt ADR-003 | Cần đọc code `stock_picking_batch` + OCA `tms` (cần addon/source) | BE-01 đề xuất, Orchestrator duyệt | ADR-003 có ☑ + `file:dòng` dẫn chứng |
| "Không file nào ngoài biên giới bị sửa" | Không có git | Orchestrator | `git init` + commit, hoặc `docs/review/P0-hash-manifest.txt` |
| Font tiếng Việt PDF QWeb / portal token | Chưa có report/portal (P4) | RV-01 tại P4 | PDF in thật + ảnh |
| Điểm rubric 0–100 | Chấm bây giờ là **chấm cảm giác** (vi phạm `skills/08` §5) | RV-01 tại S1/P3 | `docs/review/S1-pass.md` có bảng 8 tiêu chí + bằng chứng |

---

## 6. Giả định đang mở (ảnh hưởng trực tiếp P0/P1)

| ID | Giả định tạm (rút gọn) | Nếu sai thì vỡ cái gì | Phải chốt trước |
|---|---|---|---|
| **OQ-01** (`docs/09:9`) | Cước = `max(kg thực, kg thể tích)`, divisor **6000** (air) / **1000** (sea) | Toàn bộ ⑫⑬⑮, Debit, test P5, KPI; field `product.template.lab_volumetric_divisor` nằm trong `lab_core` — phải có **từ P2** | P5-01, nhưng field phải tạo ở **P2** |
| **OQ-10** (`docs/09:18`) | Không dùng OCA `tms`; `lab_chuyen` là L3 | Viết thừa module (mất điểm Luật 1) hoặc thiếu ngữ nghĩa "chuyến" (mất nghiệp vụ) | **P0 exit** — đang là **P0-01** |
| **OQ-16** (`docs/09:24`) | "REA" = Resources–Events–Agents | Bảng REA + `test_rea_completeness.py` kiểm sai thứ → mất Luật 4 | Trước P5/P6 (test REA) |
| **OQ-02** (`docs/09:10`) | 2 warehouse (BAY/TÀU) + KHO CHÍNH; `KHO CHỜ` là **location** | Nhánh ① + báo cáo tồn kho + P1-01 | P1 |
| **OQ-04** (`docs/09:12`) | Chỉ `group_manager` mở khoá Debit + bắt buộc lý do | ⑭, kiểm soát gian lận, security audit | P5 |
| **OQ-11** (`docs/09:19`) | Chưa rõ OCA `stock_picking_portal` có trang token | P4-03 (tự viết controller vs dùng OCA) | P4 |
| **OQ-13** (`docs/09:21`) | `< 900dp` = overlay 1 pane, hẹp nhất 700dp | `DuoScaffold` (FE **đã viết** 10:54) + video resize + tiêu chí rubric 4 | **ngay** (đã code) |

---

## 7. Drift log — rà tính nhất quán tài liệu

> **Luật chơi:** `docs/00`–`docs/07` **FROZEN** → chỉ **báo cáo**, không sửa. `docs/review/**`, `docs/09`, `workflow/**` → RV được sửa (đã sửa 3 chỗ, ghi rõ bên dưới).
> **Công cụ:** không có `rg` → `Select-String` / đọc trực tiếp. Mọi kết luận đều kèm `file:dòng`.

### 7.1 Tổng hợp

| Mục kiểm (theo yêu cầu Wave A) | Kết quả | Số phát hiện |
|---|---|---|
| **1.** Model/field trong `agents/**/skills/**` lệch `docs/02`? | Drift `phuong_thuc` **= 0 hit** (đã hết — tin tốt); `tai_trong_kg` chỉ còn trong **ví dụ migration**; phát hiện **10 chỗ** tên field/khái niệm lệch giữa docs/00, docs/01, docs/02, docs/07, skills, whitelist, task-board | 10 (P2/P3) |
| **2.** API sai phiên bản (`attrs=`, `quantity_done`, `stock.package`, `<list>`, `hard_lock_date`, `read_group`, `SavepointCase`, `Bearer`) | **0 chỗ dùng như đúng** — 100% là cảnh báo/cấm; nhưng 1 chỗ **mâu thuẫn câu chữ** (`read_group`) + 1 chỗ **severity lệch** (`sudo`) | 2 (P2) + 3 (P3) |
| **3.** `AGENTS.md` Luật 2 bảng idioms đúng không? | **12/12 dòng đúng hướng**, khớp docs/01, docs/09, skills, audit script; 1 dòng cần sửa câu chữ; thiếu 1 idiom (`t-esc`→`t-out`); **toàn bảng chưa tái lập được offline** | 1 (P2) + 2 (P3) |
| **4.** Mọi skill có `## Dùng khi nào` + `## Checklist nhanh`? | **40/40 có đủ 2 section** ✅; **16/40 thiếu H1**; **4 shared skill chưa đăng ký** | 2 (P2) |
| **5.** `docs/09` còn câu đã trả lời mà chưa cập nhật? | **2 câu** (OQ-09, OQ-14) đã chốt bằng ADR nhưng bảng còn "Mở" → **đã sửa**; **1 dòng bảng bị gộp** → **đã sửa**; OQ-06 có bằng chứng cấu hình nhưng chưa chạy thật → đề nghị | 2 (đã sửa) + 1 (P3) |

### 7.2 Mục 1 — tên model/field lệch chuẩn `docs/02`

| Tên | Xuất hiện ở | Chuẩn `docs/02` | Kết luận | Mức |
|---|---|---|---|---|
| `lab_volume` | `docs/00-nghiep-vu.md:204` (glossary CBM) | `lab_volume_m3` (`docs/02:62`, `docs/01:38`) | **2 tên cho 1 field**; skills dùng `lab_volume_m3` (đúng) → Orchestrator sửa docs/00 | **P2-01** |
| `lab_dims` | `docs/01:38` | `lab_dim_l` / `lab_dim_w` / `lab_dim_h` (`docs/02:62`) | `lab_dims` gộp 3 chiều ≠ 3 field; `lab_dim_w`/`lab_dim_h` **không xuất hiện ở file nào khác** | **P2-02** |
| `lab_khoi_luong` (trên `account.move`) | `docs/01:48`, `agents/be/skills/07-accounting-debit.md:21` | Không có trong `docs/02` §2.1 (bảng inherit **thiếu `account.move`**) dù `docs/02:85` có `debit_ids` o2m | Thiếu spec + tên **không có đơn vị** (`_kg`) trái quy ước `docs/02:79-80,91` | **P2-03** |
| nguồn dẫn sai | `agents/be/skills/07-accounting-debit.md:21` ghi *"theo `docs/02` §2.2"* | `docs/02` §2.2 là bảng `lab.chuyen`, **không** có field `account.move` | Skill dẫn nguồn không tồn tại | **P2-04** |
| `lab.pickup.line`, `lab.lap.debit.wizard` | whitelist `infra/scripts/audit-native-first.ps1:84,91`; `docs/00:133` | `docs/02` **không** mô tả 2 thứ này | Whitelist rộng hơn spec → BE đoán field, RV không có chuẩn | **P2-05** |
| `lab.config.settings` | `docs/02:65`, whitelist `:88` | Convention: `_inherit = 'res.config.settings'` — **không tạo model mới** | Tên trong bảng đọc như model mới → dễ sinh model thừa (Luật 1) | **P2-06** |
| `lab.chuyen.tier_review_ids` | `docs/07:63` | `base_tier_validation` cấp `review_ids`/`reviewer_ids`; `docs/02` §2.2 chỉ ghi "từ mixin OCA" | Tên field **không tồn tại** → nếu copy sẽ `Invalid field` | **P2-07** |
| `has_group` trong prose | `docs/07:70` (tự phủ định ở cuối câu) | Luật 3 cấm trong business logic | Agent đọc nhanh copy nửa đầu → **P0 khi code** | **P2-07** |
| `helpdesk_ticket_id` | `docs/07:188` | `lab_ticket_ref` — `docs/adr/ADR-005:17` | 2 spec cho 1 việc; ADR thắng nhưng docs/07 FROZEN vẫn ghi tên khác | **P2-08** |
| `tai_trong_kg` | `agents/be/skills/13-migration-upgrade.md:45,48` | `tai_trong_max_kg` (`docs/02:79`, 13 chỗ khác) | Chỉ là **ví dụ migration**, nhưng gợi ý schema cũ chưa từng tồn tại | **P3-01** |
| `lab_hanhtrinh_stage` | `workflow/task-board.md:94` (yêu cầu FE→BE) | `lab.hanh.trinh.stage` (`docs/02:124`) | Field **thứ 2** cho cùng dữ liệu → trái D1/"một nguồn số liệu" | **P2-13** |

**Tin tốt phải ghi lại (đừng sửa nhầm):** `phuong_thuc` **0 hit** toàn repo; `tai_trong_max_kg` / `tai_trong_da_dung_kg` / `khoi_luong_kg` / `the_tich_cbm` / `ly_do_go` / `loai_van_chuyen` / `cut_off_datetime` **khớp 100%** giữa `docs/02` và skills. Drift cũ đã hết.

### 7.3 Mục 2 — API sai phiên bản (scan toàn repo, `.md/.py/.xml/.kt/.ps1`)

| API | Số hit | Nơi | Phân loại | Mức |
|---|---|---|---|---|
| `attrs=` | 19 | `AGENTS.md:51,205`, `agents/fe/skills/08:11,165`, `agents/be/skills/01:59`, `agents/review/skills/01:30`, `agents/review/skills/02:86`, `workflow/definition-of-done.md:25`, `workflow/skill-import.md:118`, `docs/review/README.md:32`… | **100% cảnh báo/cấm** — không chỗ nào dạy dùng | ✅ |
| `states=` (view) | 11 | như trên + `agents/fe/skills/08:12` | 100% cảnh báo | ✅ |
| `states={...}` (field kwarg) | 3 | `AGENTS.md:53`, `agents/review/skills/02:88`, audit `A2b` | Cảnh báo | ✅ |
| `quantity_done` / `qty_done` / `reserved_uom_qty` | 20 | `agents/be/skills/05:22,31,148,157`, `AGENTS.md:52,206`, `agents/_shared/skills/ai-native-delivery.md:68` (AI trả lời sai — **đúng mục đích**) | 100% cảnh báo | ✅ |
| `stock.package` (không `.quant`) | 14 | `agents/be/skills/05:20,149`, `agents/_shared/skills/odoo-native-doctrine.md:39`, `docs/09:50` | 100% cảnh báo | ✅ |
| `<list>` | 15 | phần lớn cảnh báo; **2 false positive** do Kotlin generics (`agents/fe/skills/06:118`, `13-ai-layer:45`) | Cảnh báo đúng; audit A7 chỉ quét `addons/*.xml` nên không nhiễu | ✅ + P3-03 |
| `hard_lock_date` | 7 | `agents/be/skills/07:93,166`, `AGENTS.md:58`, audit `A9` | 100% cảnh báo | ✅ |
| `read_group(` | 8 | `AGENTS.md:54`, `agents/be/skills/03:155`, **`agents/be/skills/14:40`** (*"API 16 — dùng trong 17 sẽ đi qua shim deprecated rồi bị bỏ"*), `agents/review/skills/02:89`, audit `A8` (P1) | Không dạy sai, nhưng **mâu thuẫn câu chữ**: hợp đồng nói "SAI", skill nói "deprecated shim" | **P2-12** |
| `SavepointCase` | 8 | `agents/be/skills/11:17`, `AGENTS.md:55` | 100% cảnh báo (bị xoá từ 16.0) | ✅ |
| `models.Constraint` | 9 | `AGENTS.md:62`, `agents/review/skills/02:29,97` | 100% cảnh báo | ✅ |
| `is_storable` | 6 | `agents/be/skills/06:45`, `AGENTS.md:59` | 100% cảnh báo | ✅ |
| `Bearer` | 25 | **Hợp lệ**: `agents/fe/skills/04:140-147` (→ `/lab/api/v1`), `13-ai-layer:44,53` + `docs/05:80` (VieNeu), `demo/voice/generate-voice.ps1:41` (VieNeu). Còn lại cảnh báo "17.0 không có Bearer" | ✅ đúng luật (chỉ cho endpoint tự dựng, **không** cho `/web/*`) | ✅ |
| `props.value` / `this.state =` | 5 + 2 | `AGENTS.md:60`, `agents/fe/skills/09:15,131,158`; `this.state = useState(...)` ở `09:77` là **OWL 2 đúng** | ✅ | ✅ |
| `move.weight` / `picking.weight` | 12 | `agents/be/skills/05:105-112` nói rõ **chỉ có** khi cài `stock_delivery` | Chính xác | ✅ |

**Kết luận mục 2:** không có chỗ nào dạy API sai như đúng. Vấn đề còn lại là **câu chữ + severity**, không phải kiến thức.
**Ghi chú công cụ:** pattern A1 của audit còn khớp `comodel_name=`/`inverse_name=` → log liệt kê 21 "model" trong khi thật có 6 (**P3-08**).

### 7.4 Mục 3 — `AGENTS.md` Luật 2 (bảng idioms) đúng không?

| # | Dòng Luật 2 | Đánh giá | Đối chiếu nội bộ | Mức |
|---|---|---|---|---|
| 1 | `attrs=`/`states=` (view) → `invisible="…"` | ✅ đúng | `docs/01:186`, `agents/fe/skills/08:11`, audit `A2` (P0), `docs/09:51` | — |
| 2 | `quantity_done/qty_done/reserved_uom_qty` → `quantity` + `picked` | ✅ đúng | `agents/be/skills/05:22-31,148`, audit `A3` (P0) | — |
| 3 | `states={...}` trên `fields.*` đã bỏ | ✅ đúng | audit `A2b` (P0) | — |
| 4 | `read_group(...)` → `_read_group(...)` | ✅ hướng đúng, ⚠️ **câu chữ** "SAI ở 17.0" xung đột `agents/be/skills/14:40` ("shim deprecated"); audit xếp **P1** | `docs/09` không có dòng cho `read_group` | **P2-12** |
| 5 | `SavepointCase` → `TransactionCase`/`SingleTransactionCase`/`HttpCase` | ✅ đúng | `agents/be/skills/11:17` | — |
| 6 | `stock.package` → `stock.quant.package` (type vẫn `stock.package.type`) | ✅ đúng | `agents/be/skills/05:20`, `docs/09:50` | — |
| 7 | `<list>` → `<tree>` | ✅ đúng | `agents/fe/skills/08:13`, audit `A7` (P0) | — |
| 8 | `hard_lock_date` → `fiscalyear_lock_date` (+`period_`,`tax_`) | ✅ đúng | `agents/be/skills/07:93`, audit `A9` (P0) | — |
| 9 | `is_storable` → `product.template.type == 'product'` | ✅ đúng | `agents/be/skills/06:45` | — |
| 10 | OWL 1 `this.state`/`props.value` → OWL 2 | ✅ đúng | `agents/fe/skills/09` | — |
| 11 | `Authorization: Bearer` (19.0) — 17.0 không có | ✅ đúng | `docs/02:283`, `ADR-004:29`, `agents/fe/skills/04:11` | — |
| 12 | `models.Constraint` (18.0) → `_sql_constraints` | ✅ đúng | `agents/be/skills/03:54`, `agents/review/skills/02:29` | — |

**Hai thiếu sót của bảng (không phải sai):**
- **Thiếu `t-esc` → `t-out`** (17.0): `agents/be/skills/09-qweb-reports.md:111` đã dạy nhưng Luật 2 không có (**P3-04**).
- **Toàn bộ bảng không tái lập được trong workspace**: nguồn `odoo/odoo@17.0` không có ở đây; các dòng `H`/`M` ở `docs/09` là di sản phiên trước. Đây là **lỗ hổng chuỗi bằng chứng** — cách vá ở §5 (grep trong container, dán output).

### 7.5 Mục 4 — chất lượng & đăng ký skill

| Kiểm | Kết quả | Bằng chứng |
|---|---|---|
| Có `## Dùng khi nào` | **40/40** ✅ | đọc heading toàn bộ `agents/**/skills/*.md` |
| Có `## Checklist nhanh` | **40/40** ✅ | như trên |
| Có `# Tiêu đề` (H1) — `workflow/skill-import.md` §2 bắt buộc | **24/40** — **16 file thiếu H1** | `agents/fe/skills/{01,02,03,04,05,06,07,08,10,11}`, `agents/review/skills/{02,03,05,06,07,08}` → **P2-09** |
| Đăng ký trong `agents/<role>/AGENT.md` §5 | **36/36** skill của 3 role ✅ | đối chiếu tên file với bảng §5 |
| Shared skill được đăng ký ở đâu | **0/4** ❌ — `ai-native-delivery`, `rea-model` chỉ nhắc trong `docs/05`; `vietnamese-business-language` chỉ trong 2 skill RV; **`odoo-native-doctrine` = 0 tham chiếu toàn repo** | **P2-10** — theo `AGENTS.md` §11.2 là "file chết" |

### 7.6 Mục 5 — `docs/09`: câu đã có câu trả lời mà bảng chưa cập nhật

| Phát hiện | Bằng chứng | Xử lý |
|---|---|---|
| **OQ-09** đã được **ADR-005 ACCEPTED** quyết (activity + `lab_ticket_ref`, không depends OCA) nhưng bảng vẫn "Mở" | `docs/09:17` vs `docs/adr/ADR-005:1,17` | ✅ **đã sửa** → "Đã chốt (ADR-005)" |
| **OQ-14** đã được **ADR-004 ACCEPTED** quyết (API key + token custom 8h) | `docs/09:22` vs `docs/adr/ADR-004:1,20-31` | ✅ **đã sửa** → "Đã chốt (ADR-004)" |
| **Dòng bảng bị gộp** bằng `\|\|` → render hỏng, mất 1 câu trả lời | `docs/09:59` | ✅ **đã sửa** → tách 2 dòng |
| **OQ-06** (Community 17.0?) — bằng chứng **cấu hình** có (`infra/docker-compose.yml:19` image `odoo:17.0` = CE) nhưng chưa chạy thật | `docs/09:14` | ⏳ **đề nghị** Orchestrator đổi sang "Đã chốt (cấu hình)" **sau** lần login đầu tiên (RV không tự đổi để khỏi khai khống mức `H`) |
| **OQ-10** (`tms`) vẫn mở và **đúng** như vậy | `docs/09:18`, `docs/adr/ADR-003:3,35-41` | Không sửa; chặn P0 exit (**P0-01**) |
| Đếm câu hỏi mở | 18 câu, 5 câu "Đã chốt" → **13 câu mở**, vẫn ≥ 12 (`docs/05` I-12) | ✅ đạt |

---

## 8. Trạng thái BE-01 / FE-01 (ghi nhận — **không** review code)

> 2 agent đang viết song song → RV **không chấm logic code**, chỉ ghi trạng thái quan sát được + bằng chứng.

| Agent | Quan sát (mốc 11:00) | Suy ra | Cần gì tiếp |
|---|---|---|---|
| **BE-01** | 5 `__manifest__.py` (10:55:10 → 10:57:46); 53 file; `addons/oca/` = `.gitignore` + `.gitkeep`; `check.ps1` 10:59:32 (PASS); `vendor-oca.ps1` 10:59:01; `odoo.conf` viết lại 10:58:07; 4 file test (`lab_chuyen`, `lab_core`, `lab_hanhtrinh`, `lab_laixe`); `lab_api` chưa có test | Khung P0 gần đủ; **ADR-003 chưa chốt nhưng `lab_chuyen` đã có code** → **P0-01**; OCA chưa vendor; chưa cài được (Docker) | Điền ADR-003 **trước** khi mở rộng `lab_chuyen`; dán log `-i` + `--test-tags` khi có Docker; sửa P1-01/P1-03 |
| **FE-01** | ~20 file trong 10:54–10:58 (`DuoScaffold.kt`, `Tokens.kt`, `AppTheme.kt`, `LoginScreen.kt`, `DriverJobsScreen.kt`, `SelfCheck.kt`…); wrapper 10:43; jar desktop 10:47:49 | Đã dựng design system + scaffold + login + màn demo + self-check — **đúng phạm vi P0**; **không có log build** trong handoff | Dán `BUILD SUCCESSFUL` + `JVM: 21.x` + ảnh cửa sổ app + kết quả `SelfCheck` vào `workflow/handoff.md` |

**Ghi chú tiến trình:** `workflow/task-board.md` vẫn lệch (P0-01/02/04 = `TODO` dù file đã có; P0-05 = "chờ xác nhận compile" dù jar đã build) → **P2-14**. Đề nghị 2 agent tự cập nhật board khi handoff; RV không sửa trạng thái việc của người khác.

---

## 9. Issue list (mẫu `agents/review/AGENT.md` §5 — 7 ô)

### 9.1 P0 — 1 issue, **chặn cục bộ**

#### [P0-01] `lab_chuyen` đã có code trong khi **ADR-003 vẫn `PROPOSED`** — vi phạm trình tự bắt buộc của Luật 1
- **File:** `addons/lab_chuyen/__manifest__.py` (10:55:30) + `addons/lab_chuyen/models/lab_chuyen.py:17` (`_name = "lab.chuyen"`) · `docs/adr/ADR-003-chuyen-vs-batch.md:3` (vẫn `PROPOSED`, mtime 09:44:38, bảng chọn trống ở dòng 37–41) · `workflow/task-board.md:19,99,111` (P0-07 `BLOCKED`, *"Chốt ADR-003 trước khi BE viết `lab_chuyen`"*)
- **Vi phạm:** **Luật 1** (L3 chỉ hợp lệ khi đã chứng minh L0/L1/L2 không đủ — ADR + Reuse Proof) + `docs/01` §3.2 (*"BE-01 phải đọc `tms` TRƯỚC khi tạo `lab.chuyen`"*) + `agents/review/skills/01` §1 (thiếu Reuse Proof → BLOCKED)
- **Vì sao sai:** `lab.chuyen` là model mới **duy nhất** của ⑤ và quyết luôn `lab_laixe` ở L2 hay L3. Nếu ADR-003 được điền **sau** khi code xong, nó thành **biện hộ hậu nghiệm** — đúng thứ `docs/00` §11 gọi là "sẽ trượt". Ngược lại nếu `stock_picking_batch` đủ (phương án A), 53 file vừa viết là code thừa, mất điểm native-first.
- **Đề xuất:** điền ADR-003 **ngay** (bảng chọn ☑ + 4 câu trả lời kèm `file:dòng` của `stock_picking_batch` và OCA `tms`), Orchestrator duyệt. **Điều kiện hạ mức:** nếu ADR-003 được điền đúng bằng chứng **trong cùng handoff HO-01** → hạ xuống P1 (lỗi trình tự, không phải lỗi thiết kế) và mở lại ⑤⑥⑦⑬⑭.
- **Bằng chứng cần có:** ADR-003 có ☑ + dẫn chứng file; hoặc Reuse Proof trong handoff chỉ rõ đã đọc `stock_picking_batch` và `tms`.
- **Chặn:** **mọi việc `lab_chuyen` + `lab_laixe` + ⑤⑥⑦⑬⑭** cho tới khi ADR-003 xong. **Không** chặn: P1 native baseline (⑫⑬⑮⑯ bằng cấu hình), ①③④, FE, app KMP.

### 9.2 P1 — sửa trước khi đóng P0

#### [P1-01] Giả định "Odoo tự quét 1 cấp con" trong `odoo.conf` + `vendor-oca.ps1` — nếu sai thì `addons/oca/<module>` vô hình
- **File:** `infra/odoo.conf:25-30` (comment: *"Odoo tự quét thêm MỘT cấp con -> addons/oca/<module> cũng được nhận"*) · `infra/scripts/vendor-oca.ps1` (docstring: *"Odoo chỉ quét addons_path thêm MỘT cấp thư mục con … addons/oca/<module>/ là bố cục bắt buộc"*)
- **Vi phạm:** `docs/03` §P0 DoD (*"`--addons-path` include `addons/oca/`"*) + **Luật 1** (duyệt 2 cấp phải là `base_tier_validation` L1; mất nó → áp lực tự viết duyệt) + **Luật 3** (kiểm soát bằng tier/`ir.rule`, không `has_group`) + **Luật 9** ("Odoo có/không có" phải kiểm, không đoán)
- **Vì sao sai:** `addons_path` là danh sách thư mục **chứa module trực tiếp** — theo cách Odoo nạp module, `addons/oca/<module>` chỉ được thấy nếu `addons/oca` **có mặt trong `addons_path`**. Comment hiện tại nói ngược lại, và nó nằm ở **2 file cấu hình** → agent sau sẽ tin. Hệ quả: `-i base_tier_validation` báo không tìm thấy module, ⑥⑭ mất công cụ duyệt.
- **Đề xuất (rẻ, đúng cả khi giả định đúng):** thêm `/mnt/extra-addons/oca` vào `addons_path` (hoặc thêm tham số `--addons-path` trong lệnh compose). Chi phí 0, xoá bỏ rủi ro. Sửa lại comment theo **kết quả kiểm chứng**, không theo suy đoán.
- **Bằng chứng cần có:** sau `vendor-oca.ps1`, một trong hai: `docker compose ... exec odoo odoo shell -d odoo17_lab` → `env['ir.module.module'].search([('name','=','base_tier_validation')])` trả record; hoặc log `-i base_tier_validation` → `installed`. Kèm ảnh/log `docker compose ... exec odoo ls /mnt/extra-addons/oca`.
- **Chặn:** P0-08 + ⑥ (duyệt vào chuyến) + ⑭ (khoá Debit) + nhánh ②.

#### [P1-03] Lệnh trong `README`/`AGENTS.md` §9 vẫn không chạy được (host ↔ container) — dù `odoo.conf` đã ghi cách override
- **File:** `README.md:83,87` · `AGENTS.md:216,219,222` · `infra/odoo.conf:64-66` (đã thêm ghi chú override `--db_host=127.0.0.1`)
- **Vi phạm:** **Luật 7** (*"Test phải chạy được bằng lệnh trong README"*) + DoD D8 (runbook) + `agents/review/AGENT.md` §9b (runbook sai → P1)
- **Vì sao sai:** `odoo.conf:16` vẫn `db_host = db` (đúng cho container) và `addons_path` là đường dẫn container; `README.md:83/87` + `AGENTS.md:219/222` vẫn đưa lệnh host **không** override. Riêng `AGENTS.md:216` còn trỏ `infra/odoo/addons` — thư mục **không tồn tại**. Người mới làm theo tài liệu sẽ fail ở bước 2, đúng lúc gấp nhất.
- **Đề xuất:** chọn **một** cách chuẩn trong `workflow/runbook.md` (khuyến nghị: **container**), sửa README + AGENTS §9 theo cách đó; nếu giữ cách host thì thêm `--db_host=127.0.0.1 --addons-path=addons,addons/oca`.
- **Bằng chứng cần có:** dán output chạy đúng lệnh trong README trên máy sạch; runbook chỉ còn 1 cách.
- **Chặn:** P0-10 + S1 (không dựng được DB thì không có video native).

#### [P1-05] Không có VCS → không chứng minh được "không file ngoài biên giới bị sửa" (Luật 10), không rollback
- **File:** toàn repo (`Test-Path .git` = **False**; `git status` → `fatal: not a git repository`)
- **Vi phạm:** **Luật 10** (biên giới file) + điều kiện P0-06 + `workflow/handoff.md` (checklist *"kiểm bằng `git status`"*)
- **Vì sao sai:** 2 agent ghi song song vào **cùng** cây; không có diff → không phát hiện ghi đè (đặc biệt `__manifest__.py` — BE sở hữu nhưng phải chứa view của FE), không khôi phục khi hỏng. Bằng chứng: chỉ trong 5 phút, `odoo.conf` bị viết lại, `.ps1` được thêm/sửa 3 file — RV **không có cách nào** biết phần nào là của ai.
- **Đề xuất:** `git init` + commit "P0 baseline" **ngay**; nếu chưa dùng git được thì tạo `docs/review/P0-hash-manifest.txt` (`Get-FileHash -Algorithm SHA256`) và so lại trước mỗi handoff.
- **Bằng chứng cần có:** `git log --oneline` ≥ 1 commit; hoặc manifest hash + lần so sánh thứ 2.
- **Chặn:** P0-06 + mọi handoff từ đây.

#### [P1-06] `docs/02` §1 tự mâu thuẫn về `depends` giữa 3 module nghiệp vụ
- **File:** `docs/02-kien-truc.md:46` vs `:48`
- **Vi phạm:** **Luật 2** (manifest `depends` đúng) + `docs/00` §6 (lý do tồn tại `lab_core`: tránh phụ thuộc vòng)
- **Vì sao sai:** `:46` nói *"Ba module nghiệp vụ … **không** depends lẫn nhau"*; `:48` lại chốt *"`lab_hanhtrinh` depends `lab_chuyen`"* + thứ tự cài. BE đã tạo `addons/lab_hanhtrinh/models/lab_hanh_trinh.py:36` với `comodel_name="lab.chuyen"` → **đã phải chọn**; nếu chọn sai thì cài lẻ module lỗi, hoặc vi phạm quy tắc chống vòng.
- **Đề xuất:** ADR ngắn (hoặc sửa `:46` thành *"không depends **vòng**"*), giữ `lab_hanhtrinh depends lab_chuyen` vì `chuyen_id` required. Cập nhật `docs/02` (Orchestrator, FROZEN).
- **Bằng chứng cần có:** 5 `__manifest__.py` + log cài **từng module riêng lẻ** trên DB trắng.
- **Chặn:** P0-01/P0-04 + P2.

### 9.3 P2 — 17 issue (sửa khi có thời gian, nhưng trước khi sang phase tương ứng)

| ID | File:dòng | Vi phạm | Vì sao sai | Đề xuất | Bằng chứng cần có | Chặn |
|---|---|---|---|---|---|---|
| **P2-01** | `docs/00:204` | Luật 2 (tên field) + AGENTS §5 | glossary `lab_volume` vs `lab_volume_m3` (`docs/02:62`) | Sửa docs/00 (Orchestrator) | chỉ còn 1 tên | P2 |
| **P2-02** | `docs/01:38` | như P2-01 | `lab_dims` (1 field) ≠ `lab_dim_l/w/h` (3 field) | Chốt 3 field rời, sửa docs/01 | BE dùng đúng 3 field | P2 |
| **P2-03** | `docs/01:48` + `docs/02` §2.1 | Luật 2 (tên có đơn vị) | `lab_khoi_luong` thiếu `_kg`; `docs/02` §2.1 **không liệt kê** `account.move` dù §2.2:85 có `debit_ids` | Bổ sung `account.move` vào §2.1 với `lab_chuyen_id` + `lab_khoi_luong_kg` | `account.move` có 2 field khớp docs | P5 |
| **P2-04** | `agents/be/skills/07-accounting-debit.md:21` | AGENTS §11 (dẫn nguồn) | dẫn *"`docs/02` §2.2"* cho field `account.move` — §2.2 không có | Sửa dẫn nguồn sau P2-03 | 2 tài liệu cùng nói 1 chỗ | P5 |
| **P2-05** | `infra/scripts/audit-native-first.ps1:84,91` | Luật 2 (whitelist = spec) | `lab.pickup.line`, `lab.lap.debit.wizard` whitelisted nhưng không có spec trong `docs/02` | Thêm spec hoặc bỏ khỏi whitelist | whitelist == `docs/02` §2 | P2/P3 |
| **P2-06** | `docs/02:65` + audit `:88` | Luật 1 (model thừa) | `lab.config.settings` đọc như model mới; convention là `_inherit = 'res.config.settings'` | Đổi tên dòng thành `res.config.settings (inherit)` | không có model `lab.config.settings` trong DB | P2 |
| **P2-07** | `docs/07:63,70` | **Luật 3** (nếu copy) + Luật 2 | `tier_review_ids` không tồn tại trong OCA mixin; prose còn `has_group(...)` | Thay `review_ids`; xoá nửa câu `has_group` (Orchestrator, FROZEN) | audit A4 = 0 hit | ⑥⑭ + nhánh ② |
| **P2-08** | `docs/07:188` vs `ADR-005:17` | AGENTS §5 | `helpdesk_ticket_id` vs `lab_ticket_ref` — 2 tên | Giữ `lab_ticket_ref`, sửa docs/07 | chỉ 1 tên trong repo | nhánh ⑥ |
| **P2-09** | 16 file (§7.5) | `workflow/skill-import.md` §2 | thiếu H1 → khó trích dẫn/khó kiểm file chết | Thêm `# <Tên skill>` | mỗi file 1 H1 | P1 |
| **P2-10** | `agents/_shared/skills/` | **AGENTS.md §11.2** | `odoo-native-doctrine.md` **0 tham chiếu**; 3 file còn lại không nằm trong bảng §5 nào | Thêm bảng "Skill nền" vào `AGENT-BASE.md` + trỏ từ 3 `AGENT.md` | mỗi file ≥ 1 tham chiếu | P1/P2 |
| **P2-11** | audit `:65` vs `agents/review/skills/01:33` | Luật 3 + DoD D4 | cùng lỗi `sudo()`: script xếp **P1**, skill xếp **P0** | Thống nhất 1 mức ở cả 2 nơi | sửa 1 chỗ, 2 nơi cùng mức | mọi review |
| **P2-12** | `AGENTS.md:54` vs `agents/be/skills/14:40` | Luật 2 (câu chữ) | `read_group` bị gọi là "SAI" ở hợp đồng, "deprecated shim" ở skill; audit xếp P1 | Sửa Luật 2: "deprecated since 17 — **cấm dùng trong repo này**", giữ P1 | 2 tài liệu cùng câu | mọi review |
| **P2-13** | `workflow/task-board.md:94` | Luật 4/D1 (một nguồn số liệu) | `lab_hanhtrinh_stage` = bản sao `lab.hanh.trinh.stage` | Từ chối field; card đọc stage qua endpoint | 1 nguồn stage + test | P7 |
| **P2-14** | `workflow/task-board.md:13-18` | AGENTS §12/§5 | P0-01/02/04 = `TODO` dù file đã có; P0-05 "chờ compile" dù jar đã build | 2 agent tự cập nhật khi handoff | board == thực tế lúc handoff | P0 |
| **P2-15** | `README.md:129` | AGENTS §5 | "Code (`addons/`, `apps/`) chưa bắt đầu" — sai với 53 file + 20 file Kotlin | Sửa trạng thái thật | README khớp workspace | P0 |
| **P2-16** | `docs/09:59` | — (lỗi format) | 2 dòng bảng bị gộp `\|\|` → render hỏng | **✅ đã sửa** (tách 2 dòng) | bảng render đúng 14 dòng | — |
| **P2-17** | `docs/09:17,22` | `skills/06` §2.3 | OQ-09/OQ-14 đã được ADR trả lời nhưng vẫn "Mở" | **✅ đã sửa** | trạng thái khớp ADR | — |

### 9.4 P3 — 9 đề xuất

| ID | File:dòng | Nội dung | Đề xuất |
|---|---|---|---|
| **P3-01** | `agents/be/skills/13-migration-upgrade.md:45,48` | Ví dụ migration đổi `tai_trong_kg` (field cũ chưa từng tồn tại) | Đổi ví dụ sang `tai_trong_uoc_kg` hoặc ghi "ví dụ giả định" |
| **P3-02** | `agents/review/skills/06-critical-thinking.md:15` | "hiện đã có 15" kịch bản red-team — nay là **20** | Cập nhật số (hoặc bỏ số, giữ "≥ 10") |
| **P3-03** | audit `:61,68,117` | A2 chỉ bắt `states="…"`; A8 bỏ sót `read_group(` đầu dòng; A12 chỉ `.py` + chuỗi ≥ 8 ký tự | Mở rộng pattern + ghi giới hạn trong header script |
| **P3-04** | `AGENTS.md` Luật 2 | Thiếu idiom `t-esc` → `t-out` (đã dạy ở `agents/be/skills/09:111`) | Thêm 1 dòng |
| **P3-05** | toàn repo `.md` | 20/76 file có BOM, 56 không (không sai luật) | Thống nhất: `.md` không BOM, `.ps1` có BOM; ghi vào `workflow/runbook.md` §6 |
| **P3-06** | `agents/review/skills/02:80` | "Sai → P1: `this.state`, `props.value`, `useState` cũ…" — `useState` là API đúng OWL 2 | Sửa câu cho rõ (chỉ sai khi dùng kiểu OWL 1) |
| **P3-07** | `docs/09` | Drift `phuong_thuc` đã hết nhưng không có dòng "đã kiểm 0 hit" | Thêm 1 dòng vào bảng "đã trả lời" để lần sau khỏi tìm lại |
| **P3-08** | `infra/scripts/audit-native-first.ps1:107` | Pattern A1 khớp cả `comodel_name=`/`inverse_name=` → log liệt kê 21 "model", thật có 6 | Neo pattern `(^\|\s)_name\s*=` / loại `comodel|inverse`; hoặc `rg "^\s*_name\s*="` |
| **P3-09** | `infra/scripts/check.ps1` mục [6] | Chỉ quét `.ps1` trong `infra/scripts` (4 file), **bỏ** `apps/kmp/bootstrap.ps1` + `demo/voice/generate-voice.ps1` → tên mục gây hiểu "mọi .ps1 đã BOM" | Quét toàn repo (trừ `build/`, `.gradle/`) và in rõ số file đã quét |

---

## 10. Verdict Wave A

### **CHANGES-REQUESTED** — cổng đạt, P0 exit chưa đạt · **+ BLOCKED cục bộ** trên `lab_chuyen`/`lab_laixe` (P0-01)

| Thành phần verdict | Kết quả |
|---|---|
| **Cổng kiểm soát (deliverable RV-01)** | **PASS** — 4 artifact + bằng chứng lệnh/output; audit + `check.ps1` chạy lại được |
| **P0 exit gate (11 điều kiện §2)** | **CHƯA PASS** — 4 ✅ / 3 ⏳ / 4 ❌ |
| **Vi phạm luật bất biến?** | **Có 1, phạm vi hẹp:** P0-01 (Luật 1 — code `lab_chuyen` trước ADR-003) → **BLOCKED cho `lab_chuyen`/`lab_laixe`**, không BLOCKED toàn repo |
| **P0 còn lại** | 0 · **P1** 4 · **P2** 17 · **P3** 9 |
| **Có scope creep / rework?** | Không — mọi việc nằm trong 19 bước |

**4 việc phải xong để Orchestrator được đóng P0** (mỗi việc 1 chủ):

1. **BE-01 + Orchestrator** — điền **ADR-003** kèm dẫn chứng đọc `stock_picking_batch` + `tms` → gỡ P0-01 (mở lại ⑤⑥⑦⑬⑭). **Đây là việc duy nhất đang chặn một hạng mục.**
2. **Người dùng** — bật Docker + chạy `vendor-oca.ps1`; **BE-01** thêm `/mnt/extra-addons/oca` vào `addons_path` (P1-01), rồi chứng minh Odoo nhìn thấy 2 module OCA.
3. **BE-01** — đồng bộ `README.md` + `AGENTS.md` §9 với cách chạy thật (P1-03), chốt `docs/02` §1 (P1-06 — đã phải chọn trong manifest).
4. **Orchestrator** — `git init` + commit baseline (hoặc hash manifest) để kiểm biên giới (P1-05).
5. **FE-01** — dán `BUILD SUCCESSFUL` + `JVM: 21.x` + ảnh cửa sổ app + kết quả `SelfCheck` (P0-04).

**Việc RV-01 làm tiếp (không chờ ai):**
- Chạy lại `audit-native-first.ps1` + `check.ps1` **ngay khi HO-01 xuất hiện** → verdict P0 chính thức (lúc đó code mới đứng yên).
- Review đủ 6 làn khi có HO-01; ghi `docs/review/P0-changes-requested.md` hoặc `P0-pass.md`.
- Chấm S1 theo §4 ngay khi P1 xong (video 60s là điều kiện cứng, không thương lượng).

---

## 11. Phụ lục — lệnh đã chạy & output

### A. Audit native-first (2 lần)
```
> powershell -ExecutionPolicy Bypass -File infra/scripts/audit-native-first.ps1
Audit native-first: 0 P0 · 0 P1  ->  PASS native-first
EXITCODE=0
# 10:54:42 → (chưa có addons/ — bình thường ở P0)
# 10:58:51 → 21 tên trong "models (lab.*) found" (6 thật + 15 nhiễu do comodel_name/inverse_name)
```

### B. `check.ps1` (BE-01 thêm 10:59)
```
> powershell -ExecutionPolicy Bypass -File infra/scripts/check.ps1
6 PASS · 1 WARN · 0 FAIL      CHECK EXIT=0        (10:59:48)
WARN: addons\lab_core\security\ir.model.access.csv chỉ có header, 0 dòng ACL
```

### C. BOM `.ps1` (P0-05) — trước và sau
```
# 10:54 — TRƯỚC
infra\scripts\reset-demo.ps1              BOM=False  bytes=3742    ← FAIL
# 11:00 — SAU (BE-01 sửa 10:59:32)
apps\kmp\bootstrap.ps1                    BOM=True
demo\voice\generate-voice.ps1             BOM=True
infra\scripts\audit-native-first.ps1      BOM=True
infra\scripts\check.ps1                   BOM=True
infra\scripts\reset-demo.ps1              BOM=True    ← đã sửa
infra\scripts\vendor-oca.ps1              BOM=True
# Bằng chứng mojibake (trước khi sửa), PS 5.1 đọc ANSI:
> (Get-Content 'infra/scripts/reset-demo.ps1')[25]
function Fail($msg) { Write-Host "Lá»–I: $msg" -ForegroundColor Red; exit 1 }
> [System.IO.File]::ReadAllLines(..., UTF8)[25]
function Fail($msg) { Write-Host "LỖI: $msg" -ForegroundColor Red; exit 1 }
```

### D. Môi trường (lý do "chưa kiểm được")
```
> docker info --format '{{.ServerVersion}}'
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; ...
exit=1                                        ← không có Odoo/Postgres
> Test-Path .git          → False              ← không có VCS
> Get-Command rg          → không có           ← audit/check dùng Select-String + .NET
> $PSVersionTable.PSVersion → 5.1.26100.9711   ← mọi .ps1 phải có BOM
```

### E. Quét drift (không có `rg`)
```powershell
# 1) tên field/model xuất hiện ở đâu
Get-ChildItem -Recurse -File -Include *.md -Path agents,docs,workflow |
  Select-String -Pattern '\blab_[A-Za-z0-9_]+'          # → 79 tên, đối chiếu docs/02 §2

# 2) API sai phiên bản
Select-String -Pattern '\battrs\s*=|\bstates\s*=|quantity_done|qty_done|stock\.package(?![.\w])|<list\b|hard_lock_date|read_group\(|SavepointCase|models\.Constraint|is_storable|Bearer|props\.value'

# 3) BOM + heading skill
Get-ChildItem -Recurse -File -Include *.ps1 | % { đọc 3 byte đầu }
Get-ChildItem agents -Recurse -Filter *.md | ? FullName -match '\\skills\\' | % { heading }

# 4) đăng ký skill vs AGENT.md §5
#    đối chiếu tên file với bảng §5 của agents/<role>/AGENT.md
```

### F. File RV-01 đã tạo/sửa trong Wave A

| File | Việc | Ghi chú |
|---|---|---|
| `docs/review/P0-baseline.md` | **tạo** | file này |
| `docs/review/red-team.md` | **thêm #16–#20** | 5 kịch bản P0/Wave A (BOM, JDK 25, cache lạnh, `depends`, 2 agent cùng file) |
| `docs/review/audit-native-first.log` | **append** | 10 entry audit (script tự ghi): 3 của phiên trước + 10:51:38, 10:54:42, 10:58:51 (RV) + 10:59:37→11:00:18 (gọi từ `check.ps1`) |
| `docs/review/README.md` | **cập nhật** | thêm dòng P0-baseline + ghi chú ngoại lệ tên file |
| `docs/09-open-questions.md` | **sửa 3 chỗ** | tách dòng bảng gộp + OQ-09/OQ-14 → "Đã chốt (ADR-00x)" + mục "RV-01 · cập nhật ở P0 baseline" |
| `workflow/task-board.md` | **thêm 4 cross-request + 1 dòng nhật ký** | P1-01 (OCA), P1-05 (VCS), P1-06 (docs/02), P1-03 (runbook) — kèm trạng thái |

---

## 12. Nhật ký thay đổi **trong lúc** dựng cổng (vì sao không review code)

> Bằng chứng cho quyết định "Wave A = dựng cổng, không review code đang viết": workspace thay đổi **liên tục** trong 6 phút.

| Giờ | Ai (suy ra từ nội dung file) | Thay đổi | Ảnh hưởng tới baseline |
|---|---|---|---|
| 10:54:31–10:58 | FE-01 | ~20 file Kotlin (DuoScaffold, tokens, login, driver jobs, SelfCheck) | P0-04 vẫn ⏳ (chưa có log build) |
| 10:54:42 | RV-01 | audit lần 1 → PASS rỗng | §3.1 |
| 10:55:10–10:57:46 | BE-01 | 5 `__manifest__.py` + 53 file | P0-07 → **P0-01** (code trước ADR) |
| 10:57:50 | BE-01 | `addons/oca/.gitignore` (vendor bắt đầu) | P0-08 vẫn ❌ (chưa có module) |
| 10:58:07 | BE-01 | `odoo.conf` viết lại (thêm ghi chú host override) | P1-03 (một nửa) |
| 10:58:51 | RV-01 | audit lần 2 (đã có code) → PASS | §3.1 |
| 10:59:01 | BE-01 | `vendor-oca.ps1` | P1-01 (giả định auto-scan) |
| 10:59:32 | BE-01 | `check.ps1` + `reset-demo.ps1` (có BOM) | P0-02 ❌→✅ · P0-05 ❌→✅ |
| 10:59:48 | RV-01 | chạy `check.ps1` → PASS | §3.2 |
| 10:59:37 → 11:00:18 | BE-01 (+ RV) | 4 lần `check.ps1` gọi audit; BE-01 đang sửa tiếp sau mốc này | cổng đang được dùng thật → baseline phải đo lại ở handoff |
| 11:00 | RV-01 | chốt baseline (file này) | — |

*Hết. File tiếp theo khi có handoff: `docs/review/P0-changes-requested.md` (hoặc `P0-pass.md`).*
