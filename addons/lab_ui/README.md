# lab_ui — giao diện "Modern Industrial" cho Odoo 17 CE

> Module **chỉ trình bày**: không model, không controller, không security, không data nghiệp vụ.
> Quyết định: `docs/adr/ADR-007-theme-lab-ui.md`. Token (nguồn DUY NHẤT):
> `agents/_shared/skills/ui-modern-industrial.md`. Bố cục màn hình làm việc:
> `agents/_shared/skills/ui-dashboard-app-screens.md` (dựng từ 4 ảnh mẫu của sếp trong
> `docs/review/refs/`). Luật chống "giao diện AI đại trà": `agents/_shared/skills/design-taste-anti-slop.md`.

## 1. Module có gì

| Tầng | File | Bundle | Nội dung |
|---|---|---|---|
| Biến SCSS | `static/src/scss/variables.scss` | `web._assets_primary_variables` | ghi đè `$o-community-color` (tím `#71639e` → forest `#1F4A3D`), `$o-brand-primary`, `$o-brand-odoo`, `$o-action`, `$o-main-text-color`, `$o-main-color-muted`, `$o-body-bg`, `$o-view-background-color`, `$o-border-radius*`, **4 màu semantic** (`$o-success/$o-info/$o-warning/$o-danger`) + các giá trị **phái sinh** mà `primary_variables.scss` đã tính sẵn bằng màu tím |
| CSS backend | `static/src/scss/backend.scss` | `web.assets_backend` | `--lab-*` trên `:root` (kể cả `--lab-chart-1..4`, `--lab-card-shadow`), navbar + menu (item active/hover: nền forest 10% + **vạch trái 3px**), control panel (search, breadcrumb, nút chuyển chế độ xem), **bảng** (header paper-2, hàng 44px, chỉ kẻ ngang, sọc paper-2 nhạt, số tabular, **chip 7/5 nhóm trạng thái**), **form sheet trắng + `--lab-card-shadow`** + statusbar, **kanban card**, lớp nổi (modal/dropdown) |
| CSS trang login | `static/src/scss/backend.scss` (cùng file) | `web.assets_frontend` | khối `body.o_login`: hai cột, cột trái nền paper-2 + logo vẽ bằng CSS, focus nhìn thấy |
| QWeb login | `views/login.xml` | — | inherit `web.login_layout` (thêm cột thương hiệu + chân trang), inherit `web.login` (thêm 1 câu nhãn phụ) |
| **Bảng điều khiển (mới)** | `static/src/dashboard/dashboard.js` · `dashboard.xml` · `dashboard.scss` | `web.assets_backend` | client action OWL 2: toolbar + 4 KPI + area chart SVG + donut SVG + bảng 10 việc |
| Menu + action (mới) | `views/menu.xml` | — | `ir.actions.client` tag `lab_ui.dashboard` + mục menu **Gom hàng lẻ → Bảng điều khiển** |
| Logo công ty | `data/company.xml` + `static/img/lab_logo.png` | — | đặt `res.company.logo` bằng cơ chế dữ liệu chuẩn (L0) |

**Vì sao `backend.scss` có mặt ở cả `web.assets_frontend`:** `/web/login` **không** nạp
`web.assets_backend`; nó đi qua `web.login_layout` → `web.frontend_layout` →
`t-call-assets "web.assets_frontend"`. Thiếu mục đó thì biến màu vẫn ăn nhưng layout hai
cột + logo CSS của trang login sẽ không có CSS. Cùng một file, selector backend không khớp
ở frontend (không có `.o_web_client`), nên không ảnh hưởng trang công khai.
Dashboard **không** có mặt ở frontend: nó chỉ chạy trong backend (OWL + `orm` service).

## 2. Cài / nâng cấp

```powershell
# (DB đang chạy trong docker compose — xem AGENTS.md §9)
docker compose -f infra/docker-compose.yml exec odoo \
  odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_ui --stop-after-init

# rồi khởi động lại nếu container đã tắt theo lệnh trên
docker compose -f infra/docker-compose.yml up -d
```

Cài lần đầu = thêm `-i lab_ui`. Chỉ đổi SCSS/XML/JS cũng phải `-u lab_ui` (Odoo sinh lại
bundle asset).

> ⚠️ **`depends` đã đổi thành `["web", "lab_core"]`.** Không phải trang trí: `views/menu.xml`
> trỏ tới `lab_core.menu_lab_root`, `lab_core.group_manager`, `lab_core.group_director`
> (xmlid thật, không tự chế nhóm). Thiếu phụ thuộc này thì `-i lab_ui` trên DB **chưa** có
> lab_core sẽ chết với "External ID not found". DB demo hiện tại đã có `lab_core=installed`
> (log `docs/review/upgrade-waveB.log:110`) nên `-u lab_ui` chạy bình thường.

## 3. Mở Bảng điều khiển (đường vào + quyền)

1. Đăng nhập backend bằng user thuộc **Trưởng phòng (duyệt)** hoặc **Ban giám đốc (chỉ đọc)**
   (`lab_core.group_manager` / `lab_core.group_director` — xem `data/demo_users_*.xml`).
2. Menu: **Gom hàng lẻ → Bảng điều khiển** (`lab_ui.menu_lab_dashboard`, sequence 5 ⇒ mục
   đầu tiên của menu gốc).
3. Action: `lab_ui.action_lab_dashboard` (`ir.actions.client`), tag `lab_ui.dashboard`.

User **không** thuộc 2 nhóm trên không thấy menu (groups_id trên menu — Luật 3, không dùng `if`).
Nhớ: client action **không có ACL riêng**; chốt chặn dữ liệu là ACL + `ir.rule` của
`lab.yeu.cau.lay.hang` và việc BE-01 kiểm nhóm trong chính method `lab_dashboard_kpi`.

## 4. Bảng điều khiển — từng khối và nguồn số

Dữ liệu: **một** RPC `orm.call("lab.yeu.cau.lay.hang", "lab_dashboard_kpi", [range])`,
hợp đồng trả **y hệt** `data` của `GET /lab/api/v1/sep/kpi`
(`addons/lab_api/controllers/sep.py`):
`{range, kpi:{pendingAssign,enRoute,pickedUpToday,confirmedToday,avgLoadPct},
trend:[{date,created,picked,confirmed}], byState:[{state,label,count}],
recent:[{id,ref,shop,stateLabel,driverName,windowFrom,updatedAt}]}`.

> ⚠️ **chờ BE-01:** grep `lab_dashboard_kpi` trong `addons/` ngày 2026-10-29 = **0 kết quả**
> ⇒ method chưa tồn tại. FE code đúng hợp đồng; khi RPC lỗi, **mỗi khối** hiện khối lỗi +
> nút "Thử lại" và câu "Không tải được số liệu cho bảng điều khiển". Không hiện số 0 giả.

| Khối | Nội dung | Nguồn | Thiếu dữ liệu thì |
|---|---|---|---|
| Toolbar | tiêu đề + 3 chip kỳ **Hôm nay / 7 ngày / 30 ngày** (`today`/`7d`/`30d`) + nút **Xuất CSV (disabled)** | `range` | — |
| 4 KPI | Việc chờ gán · Đang đi lấy hàng · Đã về kho hôm nay · Tải chuyến trung bình | `kpi.*` | "—" + "Chưa có dữ liệu" |
| Chip Δ | **chỉ** ở thẻ "Đã về kho hôm nay": `trend[-1].confirmed` vs `trend[-2].confirmed` | `trend` | chỉ hiện khi `trend.length ≥ 2`; hôm qua = 0 thì so sánh tuyệt đối ("+N"), không bịa % |
| Chip ngưỡng | tải TB > 100% ⇒ "Vượt ngưỡng 100% — cần giảm tải"; > 90% ⇒ "Sát ngưỡng 90%" | `kpi.avgLoadPct` | ẩn |
| Area chart | 2 series: **Tạo mới** (`trend.created`, vùng + đường) và **Đã về kho** (`trend.confirmed`, đường). SVG tự vẽ, không thư viện ngoài | `trend` | mọi điểm = 0 ⇒ "Chưa có dữ liệu" (không vẽ đường phẳng 0) |
| Donut | 4 nhóm: Đã về kho · Đã lấy được hàng · Đang đi lấy (assigned+en_route) · Chờ gán. Chú giải có số + %; **"Lấy hàng thất bại" và "Đã huỷ" hiện thành chip chữ NGOÀI vòng** | `byState` | tổng **mọi** trạng thái = 0 ⇒ "Chưa có dữ liệu"; nếu chỉ có thất bại/đã huỷ thì vòng rỗng nhưng 2 chip ngoài vòng vẫn hiện số |
| Bảng | 10 việc cập nhật gần nhất: mã việc · shop · chip trạng thái · tài xế · khung giờ · cập nhật (dd/MM/yyyy HH:mm) | `recent` | empty state + nút "Xem 30 ngày" |

Chi tiết đáng biết:

- **Nút Xuất CSV cố ý `disabled`** + `title` giải thích: chưa có backend xuất CSV, bật nút
  gọi endpoint không tồn tại là hứa tính năng không có (Luật 8/9).
- **Bấm 1 dòng** trong bảng → mở form yêu cầu lấy hàng (`ir.actions.act_window`, `res_id`)
  = đường quay về luồng chính (Luật 5). Bàn phím: `Enter`/`Space` trên dòng.
- **Chip Δ và sparkline**: hợp đồng chỉ có chuỗi thời gian cho `created/picked/confirmed`
  ⇒ chỉ thẻ "Đã về kho hôm nay" có Δ; **không** vẽ sparkline cho 3 thẻ còn lại vì không có
  chuỗi ngày thật (skill §3: "không có chuỗi thời gian → bỏ sparkline, giữ card 2 dòng").
- **`recent` không trả mã `state`**, chỉ `stateLabel` ⇒ chip tra ngược nhãn → mã
  (`MA_THEO_NHAN` trong `dashboard.js`). Nếu BE-01 thêm `state` vào `recent`, nhánh
  `viec.state` tự thắng — không phải sửa FE. **Đề nghị BE-01 thêm `state`** (xem §7).
- Chart vẽ **SVG trong template**; tooltip là `<title>` gốc của browser (không JS, không
  thư viện). `prefers-reduced-motion`: chỉ animation entrance của thẻ KPI, tắt hoàn toàn khi
  người dùng yêu cầu giảm chuyển động.

## 5. Kiểm màu (2 phép kiểm, đừng đoán)

**(a) Bằng grep trong bundle đã sinh** — màu **không** nằm trong HTML, HTML chỉ trỏ tới CSS:

```powershell
$html = (Invoke-WebRequest -UseBasicParsing http://localhost:8069/web/login).Content
$css  = [regex]::Matches($html, '/web/assets/[^"?]+\.css') | ForEach-Object { $_.Value } | Sort-Object -Unique
foreach ($u in $css) {
  $b = (Invoke-WebRequest -UseBasicParsing ("http://localhost:8069" + $u)).Content
  "{0}`n  forest(#1f4a3d)={1}  purple(#71639e)={2}" -f $u, ($b -match '(?i)#1f4a3d'), ($b -match '(?i)#71639e')
}
```

Kỳ vọng: `forest=True`. `purple=True` là **bình thường** ở bundle backend — Odoo vẫn còn
khai báo mặc định trong CSS của nó; điều cần kiểm là **không còn phần tử nào dùng** màu đó
(kiểm bằng mắt/DevTools). Sass có thể chuẩn hoá thành `#1f4a3d` hoặc `rgb(31, 74, 61)` —
grep cả hai. Backend cần phiên đăng nhập nên lấy URL bundle từ DevTools → Network.

**(b) Bằng mắt, 4 chỗ dễ thấy nhất sau `-u lab_ui`:**

1. navbar: nền paper-2, chữ ink, mục menu đang trỏ có **vạch trái 3px forest**;
2. danh sách **Yêu cầu lấy hàng**: header `#EAE3D4`, hàng cao 44, chỉ kẻ ngang `#D6CEBC`,
   chip trạng thái chữ 13px bo 4px (không còn viên thuốc bo tròn);
3. form chi tiết: sheet **trắng `#FBF8F1`** nổi trên nền giấy, có shadow mờ 2 lớp;
4. **Bảng điều khiển**: nền giấy, 4 card trắng có shadow mờ, chart chỉ xanh rừng,
   chip trạng thái 7 màu semantic.

**Chưa chụp được ảnh** (không có phiên đăng nhập trong môi trường này): danh sách, form,
dashboard ở 1440 / 768 / 390 px. Cách rẻ nhất: dùng profile Chrome đã đăng nhập sẵn với
`infra/scripts/shot.ps1` (xem ADR-007 §6) hoặc chụp tay trước khi demo.

Nếu `-u lab_ui` xong mà **không** thấy đổi: bundle CSS được cache trong `ir_attachment`
(`url like '/web/assets/%'`). Cách xử lý: xoá các attachment đó + restart, hoặc chạy Odoo
với `--dev=assets` khi đang chỉnh theme.

## 6. Bảng tương phản (WCAG 2.1, tính bằng công thức — không đoán)

| Cặp | Tỉ lệ | Dùng ở |
|---|---|---|
| ink `#16150F` / paper `#F4F0E6` | 16.1:1 | chữ chính |
| ink-2 `#5A5647` / card `#FBF8F1` | 6.9:1 | nhãn, phụ đề |
| card `#FBF8F1` / forest `#1F4A3D` | 9.4:1 | nút/chip nền forest |
| moss `#3F6B4A` / card | 5.4:1 | chip "Đã về kho", Δ dương |
| rust `#8C3A22` / card | 6.7:1 | chip "thất bại", Δ âm |
| **amber-ink `#7F4E12`** / card | 6.6:1 | chip/chữ hổ phách |
| ~~amber `#B8761F` / paper~~ | **3.3:1 ⇒ CẤM làm chữ** | chỉ dùng cho nét chart / nền tint |

Quy ước: mọi chip đều có **nhãn chữ** (màu không bao giờ là tín hiệu duy nhất), thang chart
chỉ có 4 bậc forest + hổ phách cho ngưỡng, không gradient/glass/glow.

## 7. Rủi ro đã biết

1. **`lab_dashboard_kpi` chưa có (BE-01).** Dashboard sẽ hiện khối lỗi ở cả 4 vùng cho tới
   khi method được thêm. Hợp đồng đã chốt trong docstring `dashboard.js`; nếu BE trả khác
   hợp đồng thì `_chuanHoa()` chấp nhận cả dạng bọc `{"data": …}` nhưng **không** đoán được
   tên khoá khác. Đề nghị BE-01: (a) thêm `state` vào từng phần tử `recent`; (b) kiểm nhóm
   ngay trong method (client action không có ACL riêng).
2. **Bám vào class của Odoo 17** — nếu nâng cấp Odoo, các chỗ sau có thể chết **im lặng**
   (SCSS sai selector không báo lỗi): `.o_main_navbar`, `.o_nav_entry`, `.o_menu_brand`,
   `.o-navbar_apps_menu`, `.o_menu_sections`, `.o-dropdown--menu`, `.o_control_panel`,
   `.o_breadcrumb`, `.o_cp_switch_buttons`, `.o_list_view .o_list_renderer`,
   `.o_list_table`, `tr.o_data_row`, `tr.o_data_row_selected`, `.o_badge_cell .badge`,
   `.o_form_view .o_form_sheet`, `.o_form_statusbar`, `.o_statusbar_status .o_arrow_button`,
   `.o_kanban_renderer .o_kanban_record`, `--ListRenderer-thead-*`, `--table-striped-bg`,
   `--table-hover-bg`. Mọi tên ở trên đã đối chiếu source 17.0 (ghi trong comment từng khối)
   — nâng cấp Odoo = chạy lại checklist §5.
3. **Chip trạng thái trong list chỉ phân biệt được 5 nhóm, không phải 7.** View tree của
   `lab_laixe` gộp `new + assigned` vào cùng `decoration-info` và `en_route + picked_up` vào
   cùng `decoration-primary`; module này **không được sửa file ngoài `addons/lab_ui/**`** nên
   CSS chỉ tô được 5 nhóm. Bảng điều khiển (do lab_ui tự render) đã có đủ 7 màu. Muốn đủ 7 ở
   list: tách thành `decoration-info`/`decoration-warning`… trong
   `addons/lab_laixe/views/lab_yeu_cau_lay_hang_views.xml` (việc của FE-01 ở nhiệm vụ khác).
4. **Đổi `variables.scss` có thể ảnh hưởng màn khác**: `$o-info` nay là forest (nền "đang
   chọn", nút chế độ xem), `$o-warning` là amber-ink (chữ), `$o-success` là moss,
   `$o-danger` là rust. Đây là ý định (một bảng màu), nhưng nếu màn nào đó trông lạ thì tra
   đúng 4 biến này trước.
5. **Bẫy biến phái sinh** — `primary_variables.scss` tính sẵn một số biến/map từ màu tím
   (`$o-main-link-color`, `$o-btns-bs-override`, `$o-text-colors-custom`,
   `$o-list-group-active-bg`…). File `variables.scss` đã khai lại những cái đang dùng; nếu bản
   Odoo sau thêm map phái sinh mới, màu tím có thể quay lại **mà không có lỗi nào**.
   (`$o-list-group-active-bg` cố ý để nguyên: không xuất hiện ở màn nào của demo.)
6. **`!important` — 1 khai báo duy nhất** trong `backend.scss` (bo góc chip trong list), vì
   utility `.rounded-pill` của Bootstrap sinh kèm `!important`
   (`$enable-important-utilities: true`). Có comment lý do ngay tại chỗ; ngoài ra SCSS của
   module dùng **0** `!important`.
7. **Assets cache** — theme "không ăn" thường là cache, không phải CSS sai: xem §5.
8. **Logo hiện tại là bộ nhận diện cũ** (`assets/brand/appicon-256.png`: cyan/indigo +
   gradient) — trái với skill §2. Module chỉ *gắn* file có sẵn, không vẽ lại logo. Cần file
   logo mới theo token forest/amber: thay `static/img/lab_logo.png` là xong (ghi đè bằng
   `data/company.xml`, `-u lab_ui`).
9. **Chuỗi tiếng Anh mặc định trên form login** ("Email", "Password", "Log in", "Manage
   Databases", "Powered by Odoo") — là i18n của `web`, không phải phần trình bày của module
   này. Xử lý đúng là `i18n/vi.po` (BE-01) hoặc đặt ngôn ngữ người dùng.

## 8. Phạm vi selector (để review)

Toàn bộ selector nằm dưới **một trong ba gốc**: `.o_web_client` (backend + dashboard),
`body.o_login` (trang đăng nhập), `:root` (chỉ khai CSS custom property `--lab-*`).
Không có selector toàn cục kiểu `td { }`, `.card { }`, `body { }`.
Selector **mới** của vòng này (ngoài những cái đã có từ trước):

```
.o_web_client .o_main_navbar .o_nav_entry[:hover|:focus-visible|.active]
.o_web_client .o_main_navbar .o_menu_brand[:hover|:focus-visible]
.o_web_client .o_main_navbar .o_menu_systray .btn
.o_web_client .o_navbar_apps_menu .o-dropdown--menu .dropdown-item[:hover|:focus-visible|.active|.focus]
.o_web_client .o_menu_sections .o-dropdown--menu .dropdown-item[:hover|:focus-visible|.active|.focus]
.o_web_client .o-dropdown--menu .dropdown-header
.o_web_client .o_control_panel .o_breadcrumb | .breadcrumb | .o_last_breadcrumb_item.active
.o_web_client .o_control_panel .o_cp_switch_buttons .o_switch_view[.active]
.o_web_client .o_control_panel .o_cp_pager
.o_web_client .o_list_view .o_list_table{,:nth-of-type odd,:hover,.o_data_row_selected,.o_group_header} > …
.o_web_client .o_list_view .o_badge_cell .badge[.text-bg-{info,primary,success,warning,danger}]
.o_web_client .o_form_view .o_form_sheet_bg | .o_form_sheet | .oe_title h1 | .o_form_label
.o_web_client .o_form_view .o_statusbar_status .o_arrow_button[.o_arrow_button_current]
.o_web_client .o_kanban_renderer | .o_kanban_group | .o_kanban_record | .o_kanban_header
.o_web_client .o_lab_dash … (toàn bộ dashboard: dashboard.scss, đều dưới .o_lab_dash)
```

## 9. Gỡ

- Tạm tắt: Apps → gỡ cài đặt `lab_ui` (asset + menu + action biến mất, giao diện về mặc
  định Odoo). Lưu ý: logo đã ghi vào `base.main_company` **không** tự khôi phục.
- Gỡ hẳn: xoá thư mục `addons/lab_ui` + xoá `ir_attachment` bundle CSS + restart.
- Không cần rollback DB: module không tạo model/bảng nào (chỉ 1 `ir.actions.client` + 1
  `ir.ui.menu`, xoá cùng module).

## 10. Không làm (giữ đúng biên giới)

- Không sửa `odoo/addons/**`, không patch JS/OWL, không đổi cấu trúc DOM của form đăng nhập.
- Không gradient, không `backdrop-filter`, không glow, không animation vô hạn, không kéo
  thư viện chart ngoài (SVG tự vẽ trong QWeb).
- Không tạo model/method backend: `lab_dashboard_kpi` là việc của BE-01.
- Không sửa view của module khác (`lab_laixe`, `lab_chuyen`, …) dù biết chỗ cần sửa — mọi
  thứ ở vòng này nằm trong `addons/lab_ui/**` (xem §7 mục 3 để biết hệ quả).
