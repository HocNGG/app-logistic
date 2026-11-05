# AI-LOG — bằng chứng "năng lực hỏi và làm việc với AI"

> Yêu cầu của sếp: *"Anh muốn xem năng lực **hỏi** và làm việc với AI của các bạn."*
> File này **không phải** danh sách "tôi đã dùng AI". Nó là **nhật ký kiểm chứng**: câu hỏi hay → AI trả lời → tôi kiểm tra → sửa bao nhiêu.
> Mẫu điền: một dòng mỗi lần. Ghi **cả khi AI đúng** (vì đó là bằng chứng quy trình).

---

## 1. Nhật ký

| # | Việc | Công cụ | Câu hỏi (rút gọn) | Kết quả AI | Đã sửa | Kiểm chứng bằng |
|---|---|---|---|---|---|---|
| 1 | Xác định tên model kiện hàng Odoo 17 | AI assistant | "Model package của stock trong Odoo 17 tên gì?" | `stock.package` ❌ | 100% | Mở `addons/stock/models/stock_picking.py` bản 17.0 → `stock.quant.package`. **`stock.package` là tên của 18.0.** |
| 2 | Tìm repo OCA của `base_tier_validation` bản 17.0 | AI assistant | "OCA repo nào chứa base_tier_validation?" | `OCA/server-tools` ❌ | 100% | Kiểm branch thật → **`OCA/server-ux`**, manifest `17.0.4.1.0`. |
| 3 | `spreadsheet_dashboard` có trong CE 17 hay EE? | AI assistant | "Odoo 17 spreadsheet_dashboard Community hay Enterprise?" | cần kiểm chứng (không chắc) | — | Đọc `addons/spreadsheet_dashboard/__manifest__.py` + `addons/board/__manifest__.py` → **CE, LGPL-3** |
| 4 | Odoo 17 còn dùng `attrs` không? | AI assistant | "attrs trong view Odoo 17 còn không?" | đã bỏ | 0% (AI đúng) | `ir_ui_view._check_xml` raise `ValidationError("Since 17.0…")` |
| 5 | Sinh bảng 19 bước từ sơ đồ PDF | LLM + pdfplumber | "Đọc text 4 trang PDF, liệt kê các bước và model tương ứng" | 16 bước có số + 3 bước không số | 20% | Đối chiếu `page-2.png` bằng mắt; phát hiện 19 = 16+3 |
| 6 | Sinh 12 instruction từ 3 ảnh chat của sếp | claude-opus-5-5 | "Bóc tách yêu cầu từ 3 screenshot, mỗi yêu cầu → instruction thi hành được" | 12 instruction | 35% | Rà lại: bỏ 2 cái chung chung, gộp 1 trùng, thêm I-08 Ask-Well |
| 7 | Capture API trả 409 `INVALID_STATE` — vì sao? | AI agent (harness) | "Đọc controller + log, nêu nguyên nhân capture lỗi" | đoán: sai state machine | 100% | **Sai hẳn.** Đọc đúng `message` trong response mới thấy: `"Unable to send message, please configure the sender's email address."` → `message_post` trong `action_pick` nổ vì **cả 12 user demo không có email** (`select login, email from res_users join res_partner` → toàn `(TRONG)`). Đã gán email + chạy lại: `picked_up`. |
| 8 | Vì sao `/lab/api/v1/auth/login` trả 401 dù mật khẩu đúng? | AI agent | "So sánh với `/web/session/authenticate` (200, cùng mật khẩu)" | nghi rate-limit / khoá tài khoản | 100% | **Sai.** Đo trong container: `Session.authenticate(dbname, login=None, password=None)` là **chữ ký cũ**; code thử dạng `credential dict` trước → bản cũ nhận `password=None` → raise `AccessDenied` (**không phải `TypeError`**) nên khối `except` nuốt mất, không bao giờ fallback. Sửa: đọc `inspect.signature` rồi gọi đúng dạng. |
| 9 | Script E2E báo `Unexpected token 'Job'` sau khi sửa file | AI agent | "Sửa lỗi cú pháp PowerShell" | nghi thiếu `}` | 100% | **Sai.** `[System.IO.File]::ReadAllBytes` cho thấy **BOM đã mất**; PS 5.1 đọc file bằng ANSI → byte `0x94` của dấu `—` thành `”`, mà PowerShell coi `”` (U+201D) là **dấu nháy đóng** → chuỗi bị cắt giữa. Sửa: file thuần ASCII + tự kiểm bằng `[Parser]::ParseFile` trước khi chạy. |
| 10 | 16/54 test HTTP đỏ 401 sau khi thêm test mới | AI agent | "Test login trả 401 dù chạy tay 200 — sai ở đâu?" | nghi code auth của Orchestrator sai | 100% | **Sai.** Log test có `OSError: [Errno 98] Address already in use`: tiến trình test không bind được 8069 (server chính giữ) nên `url_open()` gọi sang **server thật**, nơi không có user do test tạo. Sửa: chạy test bằng `docker compose run --rm --no-deps` (container riêng) → **0 failed/54**. Đây là lỗi hạ tầng test, đã ghi vào `AGENTS.md` §9 + `setup.ps1`. |
| 11 | Build APK Android đỏ 2 lần | AI agent (FE) + Orchestrator | "Sửa lỗi build AGP 9 / Kotlin" | AGP sai phiên bản; nghi thiếu dependency | 100% | (a) AGP 9 chặn `androidTarget()` + `com.android.application` → `android.newDsl=false`; (b) `MainActivity.kt` "Unclosed comment": **block comment Kotlin lồng nhau**, KDoc chứa `image/*` → mở lớp thứ 2; lỗi báo ở dòng `class` nên đọc sai chỗ. Kết quả: APK 12,5 MB cài lên Pixel_7 (API 37) chạy thật — ảnh `docs/review/android-01-welcome.png`. |
| 12 | Bóc bộ skill UI từ 1 chat share của sếp để "hết basic AI" | ChatGPT deep-research (của sếp) + AI agent (kiểm chứng) | "Truy cập link share, lọc skill làm UI web đẹp/trend, đẩy vào dự án" | Chat đề xuất 6 thứ: `interface-design`, `taste-skill`, `frontend-ui-engineering`, `web-design-guidelines`, `owl`, `mart337i/odoo-skills` | 60% phải sửa | **Kiểm từng cái bằng GitHub API, không tin lời chat**: 4 repo có thật (★ 5.9k / 103.9k / 111k / 60); `mart337i/odoo-skills` **không có LICENSE** → không được vendor; phần "lệnh cài `npx skills …`" trong chat bị **cắt cụt** (không dùng được). Đã đọc toàn bộ SKILL.md của `taste-skill` + `frontend-ui-engineering` (MIT) rồi **rút gọn có ghi nguồn** thành `design-taste-anti-slop.md`; tự viết design system dự án `ui-modern-industrial.md`; **sửa xung đột**: chat khuyên "kem + terracotta" nhưng chính dữ liệu của skill xếp cặp đó vào *tell* → đổi sang giấy ấm + xanh rừng + hổ phách. |
| 13 | Theme Odoo: cài xong vẫn tím | AI agent (FE) + Orchestrator (chẩn đoán) | "Vì sao biến SCSS không ăn trên trang login?" | Orchestrator: nghi `web._assets_primary_variables` không vào frontend | **50% sai** | **Đo pixel ảnh "before" mới biết nút là `#714B67` = `$o-enterprise-color` (không phải `#71639e` = `$o-community-color`), và biến này KHÔNG đi qua `$primary`/`$o-brand-primary`** ⇒ không biến SCSS nào chữa được. Thêm nữa agent chỉ ra `web.assets_frontend` **vốn đã** include `_assets_primary_variables` (qua `_assets_helpers`), nên chẩn đoán "biến thiếu ở frontend" của tôi **sai**; thứ cứu được nút là khối CSS tường minh `body.o_login .btn-primary`. Đã sửa lại comment trong manifest + ADR-007. Kết quả đo sau fix: 2 827 pixel `#1F4A3D`, **0** pixel `#714B67`. |
| 14 | Mở Dashboard OWL thì cả action chết | AI agent (FE) + Orchestrator | "OWL báo Invalid loop expression: undefined is not iterable" | FE: nghi payload thiếu khoá | 100% | Bắt `pageerror` + `console` bằng Playwright mới thấy lỗi thật; đọc template: `t-foreach="cacKy"` nhưng class JS chỉ có getter **`nhanKy`** ⇒ thêm `get cacKy()` trả `CAC_KY`. Sau fix, ảnh `odoo-dashboard-after.png` render đủ KPI/area/donut/bảng với số liệu thật. |
| 15 | Navbar backend vẫn tím dù CSS đã đúng | Orchestrator | "Đã đặt background cho `.o_main_navbar` mà vẫn #71639E" | nghi thiếu đặc hiệu / cache asset | 100% | **Sai cả hai**: probe `elementFromPoint(700,12)` cho thấy phần tử tím là **`a.o_nav_entry`** (từng mục menu được Odoo tô nền brand), không phải navbar; navbar đã là paper-2. Sửa: set `background-color: transparent` cho `.o_nav_entry`/dropdown-toggle. Đo lại: cả 3 điểm ảnh = `#EAE3D4`. Bài học: đo **phần tử tại toạ độ**, đừng suy từ tên class. |
| 16 | Admin "chuyển tài khoản" (login-as) — công cụ test 8 vai trò | 2 AI agent song song (BE-01 + FE-01) + Orchestrator | "Cho admin chuyển sang MỌI tài khoản trong DB để test nhanh; làm nút button dropdown xuống cho dễ chọn; chức năng này CHỈ trên admin" | BE: `GET /admin/accounts` + `POST /admin/switch` (login-as, xoay sid) + `helpers.payload_phien` dùng chung. FE: `SwitchAccountScreen` + `DropdownMenu "Chọn tài khoản ▾"` + `Workspace.Admin` ưu tiên cao nhất + 18 test mới (90 → 108) | 95% của agent — Orchestrator sửa 1 lỗi layout dùng chung (dòng 17); BE phải **bỏ** hướng dẫn `Session.finalize()` của Orchestrator vì 17.0 dùng nó cho MFA (sẽ 500) | `infra/scripts/smoke-admin-switch.ps1` → **26/26 `[OK]`**; ảnh `app-admin-switch.png` · `app-admin-dropdown.png` · `app-admin-selected.png` · `app-switched-to-driver.png`; logcat `← 200 POST /lab/api/v1/admin/switch` rồi `← 200 GET /driver/jobs` |
| 17 | Màn "Chuyển tài khoản" **mất thanh nút dưới cùng** trên điện thoại | Orchestrator (đo + chẩn đoán) | "Nút 'Chọn tài khoản ▾' và 'Chuyển' không nhìn thấy trên AVD 720×1280 (360×640 dp), dù danh sách vẫn cuộn được — vì sao?" | 2 giả thuyết đều **sai**: (a) toolbar/search quá cao; (b) `BoxWithConstraints` mất ràng buộc chiều cao | 100% | **Đo thật thay vì đoán**: chèn tạm `println` + `onSizeChanged` → logcat `LABDBG availH=486.0` và `LABDBG bar=273x624` ⇒ thanh có thật, cao ~160dp, nhưng bị đẩy ra ngoài màn. Nguyên nhân gốc: nhánh `UiState.Content` của `StateHost` gọi `content(...)` **trực tiếp, bỏ qua `modifier`** ⇒ `Modifier.weight(1f)` của 5 màn không có hiệu lực ⇒ LazyColumn chiếm cả chiều cao còn lại. Sửa: bọc `Box(modifier)`. Bằng chứng trước/sau: `app-admin-switch-TRUOC-fix.png` → `app-admin-switch.png` |
| 18 | Dropdown chuyển vai trò **luôn hiện** + chuyển **liên tiếp** (admin → taixe01 → cs01) | 2 AI agent song song (BE-01 + FE-01) + Orchestrator | "Đăng nhập admin thì có button dropdown ở phía trên cùng; đang là tài xế01 vẫn chuyển được sang cs01 chỉ 1 click; dropdown chỉ mất khi đăng nhập không phải admin" | BE: dấu phiên `session["lab_switch_nguon"]` (giữ uid admin GỐC) + `helpers.duoc_phep_chuyen()` (một chỗ duy nhất) + khoá `canSwitch` trong payload. FE: `RoleSwitcher` gắn thanh trên cùng + chân sidebar, `AppSettings.canSwitch` (11 khoá, **không** suy từ `roles`), +5 test (109 → 114) | ~98% | Smoke `infra/scripts/smoke-admin-switch.ps1` → **20/20 `[OK]`** (admin→taixe01→cs01→sep đều 200; login trực tiếp taixe01 ⇒ `canSwitch=false` + **403** cả 2 route). App AVD: `app-switch-demo.ps1` in `admin=Quản trị viên ▾ · taixe01=Tài xế ▾ (vẫn còn) · cs01=CS ▾ · login trực tiếp=rỗng` |
| 19 | **Tín hiệu đỏ của chính mình cũng phải kiểm** (lần chụp đầu báo "sau khi chuyển KHÔNG còn dropdown") | Orchestrator | "Script bảo mất nút sau khi chuyển — sửa app hay sửa script?" | 2 giả thuyết về code đều **sai** | 100% | Đọc lại ảnh: `app-switch-3-driver.png` chụp đúng lúc menu admin **còn đang mở** ⇒ lệnh bấm `taixe01` chưa xảy ra (dòng nằm ngoài vùng thấy, phải cuộn) và `TapPattern ... \| Out-Null` đã **nuốt `Write-Output`** nên log trống. Sửa script (`Write-Host` + `TapScroll`) → 4/4 yêu cầu ĐẠT. Bài học: ảnh chụp là bằng chứng, log bị nuốt là lỗi công cụ, không phải lỗi sản phẩm |
| 20 | **Bộ dữ liệu mẫu LỚN** 6 tháng lịch sử, phủ 7 trạng thái việc + 8 trạng thái chuyến + 11 mốc hành trình + 6 nhánh ngoại lệ | Orchestrator tự viết (BE-01 treo 2,5 giờ không ra file → `interrupt_agent` rồi tự làm để có vòng lặp chạy thật) | "Tạo bộ dữ liệu mẫu lớn, bao quát đủ trường hợp, vừa để test vừa để hình dung pj sau này; áp vào DB và chạy lại" | `addons/lab_core/tools/seed_demo_lon.py` (tất định `Random(2026)`, idempotent qua `ir.config_parameter`, backdate `create_date`, xoá lại được theo dấu `DEMO-LON`/`demo-lon-`/`DL-`) + 2 script PowerShell (seed/verify) | Script do tôi viết sau khi agent treo, **9 vòng sửa lỗi thật** (xem dòng 21) | `verify-demo-lon.ps1`: **305 việc** (12/26/10/17/191/29/20) · 26 chuyến · 13 hành trình đủ 11 mốc · 149 đơn · **80 phiếu kho** · **164 bút toán** (110 hoá đơn + 30 Debit) · 127 partner · KPI `pendingAssign 12 / enRoute 36 / pickedUpToday 12 / confirmedToday 12`; ảnh `odoo-data-jobs-1440.png`, `odoo-data-dashboard-1440.png`, `app-data-driver.png` (taixe04: 14 việc) |
| 21 | 9 lỗi **chỉ lộ khi chạy thật** trong seed (không lỗi nào phát hiện được bằng đọc code) | Orchestrator | "Chạy `odoo shell < seed.py` rồi sửa từng lỗi" | Lần lượt: (1) `notification_type="inbox"` → `CheckViolation` vì tài xế là portal; (2) `account.account.company_ids` → field của 18.0, 17.0 là `company_id`; (3) `lab.chuyen.line.sale_order_id` **NOT NULL** ⇒ phải tạo ĐƠN trước CHUYẾN; (4) `env.cr.cursor()` không tồn tại ⇒ `env.cr._obj`; (5) journal Debit ở công ty khác tài khoản ⇒ `Incompatible companies`; (6) phiếu kho "Done" không xoá được ⇒ reset bằng SQL + không validate nữa; (7) `stock_quant` giữ FK sản phẩm chặn xoá; (8) `stock.picking.type` loại internal bị **archive** ⇒ `search` thường chỉ thấy 2 mã ⇒ `active_test=False`; (9) `/driver/jobs` chỉ trả việc **hôm nay** ⇒ rải 180 ngày thì tài xế thấy "0 việc" | 100% | Mỗi lỗi có log/traceback trong `.tmp/seed-*.log` và đều sửa **trong script** (không sửa model) — kèm comment "đã trúng lỗi thật 2026-10-29" để lần sau không lặp. Bằng chứng cuối: `taixe01..05` đều thấy 4–14 việc qua đúng API app |
| 22 | Bước ① không có **chỗ tạo khách mới** cho CS | Orchestrator (đo rồi tự sửa) | "Ở bước đầu, khi muốn tạo khách mới thì chưa có chỗ tạo khách mới" | 2 giả thuyết: (a) view đặt `no_create` — **sai** (`options="{'no_create': False}"` đã có); (b) thiếu menu — **đúng một nửa** | 100% | **Đo thật**: `res.partner` ACL chỉ cấp `perm_create` cho `base.group_partner_manager`; nhóm CS kế thừa `group_sale_salesman` (chỉ đọc) ⇒ `cs01` bị chặn tạo. Sửa: +1 dòng ACL `access_res_partner_cs` (1,1,1,0) + `views/res_partner_views.xml` (list/form/search + action + menu **Khách hàng (Shop)**), bump `lab_core 17.0.1.5.0`. Lại trúng luật Odoo 17: `view_mode='list'` bị `ValueError` (17.0 dùng **`tree`**, `list` là 18.0). Kiểm: `cs01` tạo partner qua RPC ⇒ `result: 1222` (đã xoá); ảnh `docs/review/odoo-data-khachhang-1440.png` (45 khách + nút New) |

---

## 2. Hai lần AI sai — và cách bắt (bắt buộc phải có)

### Lần 1 — Tên model package (sai vì "phiên bản lớn hơn")
- **AI nói:** model kiện hàng là `stock.package`.
- **Thực tế 17.0:** `stock.quant.package`. `stock.package` là **tên của Odoo 18.0**.
- **Cách bắt:** không tin tên model trong câu trả lời; mở source 17.0 và grep `_name = 'stock.quant.package'`.
- **Tác động nếu không bắt:** toàn bộ tài liệu, model map và skill pack sẽ dạy sai tên model → code không chạy, mất điểm native-first.

### Lần 2 — Repo OCA (sai vì "đã từng đúng")
- **AI nói:** `base_tier_validation` nằm trong `OCA/server-tools`.
- **Thực tế 17.0:** đã **chuyển sang `OCA/server-ux`** (bản cũ ở server-tools là 13.0/14.0).
- **Cách bắt:** kiểm branch thật (danh sách repo + `__manifest__.py` của module), không dựa vào "kiến thức cũ".
- **Tác động nếu không bắt:** vendor nhầm repo → không cài được → tính năng duyệt nhiều cấp (bước ⑥⑭) mất.

### Bài học chung
> **AI sai theo hai kiểu: (a) nhầm phiên bản, (b) đúng ở quá khứ.**
> Cả hai đều bị chặn bằng **một hành động duy nhất**: mở source/branch thật của đúng phiên bản đang dùng.
> Không có cách nào khác. Không tin "nghe rất hợp lý".

---

## 3. Ba câu hỏi tốt nhất của dự án (bằng chứng "năng lực hỏi")

### Câu 1 — Hỏi về khả năng tái sử dụng (không hỏi "làm sao")
```
Trong Odoo 17.0 Community, tôi cần mô hình "chuyến gom nhiều đơn của nhiều khách,
có cut-off và tải trọng". Ràng buộc:
- Không sửa core, không fork.
- Chỉ được thêm module mới nếu thật sự không có cách khác.
Hãy làm 3 việc:
(1) Liệt kê những gì addons/stock, addons/stock_picking_batch, addons/delivery
    đã cung cấp có thể tái dùng cho mô hình này.
(2) Nêu rõ cái gì THIẾU (không thể biểu diễn bằng field/config).
(3) Với mỗi cái thiếu, nói rõ nó có đủ lớn để biện minh cho một model mới không.
Nếu không chắc một API nào, ghi UNVERIFIED thay vì đoán.
```
→ Kết quả: `stock_picking_batch` tái dùng được phần "gom", thiếu capacity/cut-off/state riêng → ADR-003 có căn cứ.

### Câu 2 — Hỏi về tiêu chí chấm (không hỏi "làm sao gây ấn tượng")
```
Sếp tôi nói các tiêu chí: REA, responsive UI, "iPhone 18 duo UI", critical thinking.
Với từng tiêu chí:
(1) Nói tiêu chí này đo CÁI GÌ trong một sản phẩm ERP.
(2) Nói nó KHÔNG đo cái gì (để tránh hiểu sai).
(3) Cho 3 ví dụ về artifact cụ thể (file, tính năng, video) chứng minh được tiêu chí đó.
Nếu "iPhone 18 duo" là tên sản phẩm cụ thể, hãy kiểm chứng và mô tả đúng hướng dẫn UI của nó.
```
→ Kết quả: phát hiện iPhone Duo là máy gập thật (09/2026) với arrangement split/overlay + reserved regions → sinh ra I-11.

### Câu 3 — Hỏi về rủi ro (không hỏi "làm sao làm")
```
Đây là kiến trúc của tôi: [3 module lab_*, app KMP desktop, tier validation,
Debit = account.move]. Hãy đóng vai người phản biện khó tính:
(1) Liệt kê 10 chỗ thiết kế này SẼ NỔ khi gặp dữ liệu thật (quá tải chuyến,
    debit đã khoá cần sửa, mất kiện sau khi xuất, khách không nhận hàng...).
(2) Với mỗi chỗ, nói cách phát hiện sớm nhất.
Không đưa lời khen. Không nói "nên tham khảo thêm".
```
→ Kết quả: sinh ra red-team list + 6 nhánh ngoại lệ có test điểm quay về.

---

## 4. AI trong quy trình (không phải trong sản phẩm)

| Khâu | AI làm gì | Người kiểm chứng thế nào |
|---|---|---|
| Đọc PDF sơ đồ | `pdfplumber` trích text 4 trang | Đối chiếu ảnh render `page-2/3/4.png` |
| Sinh bảng 19 bước | LLM tổ chức dữ liệu | Đếm lại: 16 số + 3 không số = 19 |
| Sinh 12 instruction | LLM bóc tách từ chat | Rà từng cái: có thi hành được không? |
| Sinh skill pack (36 file) | LLM viết theo spec | Đọc lại, kiểm tra API Odoo 17 + version CMP |
| Viết kịch bản video | `claude-opus-5-5` | Đọc thành tiếng, cắt 30-50% |
| Voice-over | VieNeu AI | Nghe lại, sửa chỗ đọc sai thuật ngữ |

**Số liệu cuối dự án:** cần điền `% khối lượng do AI sinh` và `% đã sửa`. Nếu "đã sửa" = 0% ở mọi dòng → **chưa kiểm chứng**, chỉ copy.

---

## 5. Quy tắc ghi log

- Ghi **ngay khi làm**, không viết lại từ trí nhớ cuối dự án.
- Câu hỏi ghi **rút gọn nhưng đủ 5 phần** (ngữ cảnh · ràng buộc · kết quả · tiêu chí · yêu cầu tự kiểm).
- Kết quả ghi **đúng những gì AI nói**, kể cả khi sai — đó mới là bằng chứng.
- Cột "Kiểm chứng bằng" phải là **hành động cụ thể** (mở file nào, chạy lệnh gì), không phải "tôi đã kiểm tra".
