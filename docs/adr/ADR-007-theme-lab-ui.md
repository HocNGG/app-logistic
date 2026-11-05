# ADR-007 — Module `lab_ui`: theme "Modern Industrial" cho Odoo 17

**Trạng thái:** ACCEPTED (theo yêu cầu trực tiếp của chủ dự án: "làm ui web đẹp hơn, màu sắc trend hơn, không basic AI")
**Ngày:** 2026-10-28 · **Người đề xuất:** Orchestrator · **Liên quan:** `agents/_shared/skills/ui-modern-industrial.md`, `design-taste-anti-slop.md`

## 1. Bối cảnh (đo được, không cảm tính)

- Odoo 17 CE mặc định: `$o-community-color: #71639e` (**tím**) — `odoo/addons/web/static/src/scss/primary_variables.scss:69`;
  `$o-brand-primary` (dòng 74) và `$o-action` (dòng 79) kế thừa từ nó ⇒ toàn bộ backend mang màu tím mặc định.
- Giao diện hiện tại của dự án bị chủ dự án đánh giá là **"basic AI"**: nền tối + gradient cyan/indigo, card bo tròn đều,
  pill badge — trùng đúng danh sách "tell" trong nghiên cứu 247 site của skill `design-taste-frontend`
  (xem `design-taste-anti-slop.md` §2).
- Nghiên cứu UI (chat của sếp) chốt hướng **Modern Industrial**: bề mặt trung tính ấm, xanh rừng/navy đậm, một màu nhấn,
  typography biên tập, chiều sâu 3D tiết chế. Nhưng cặp "kem + terracotta" bị chính dữ liệu đó xếp vào *tell* ⇒ dự án
  thay bằng **giấy ấm + xanh rừng + hổ phách công nghiệp** (token trong skill design system).

## 2. Quyết định

Tạo **module mới `addons/lab_ui`** — chỉ trình bày, **không** model, **không** business logic, **không** controller:

```
addons/lab_ui/
  __manifest__.py        depends=["web"]; data=["views/login.xml"]; assets:
  views/login.xml        inherit "web.login_layout" (+ nhãn phụ ở "web.login")
  static/src/scss/variables.scss   -> bundle "web._assets_primary_variables"
  static/src/scss/backend.scss     -> bundle "web.assets_backend"
```

- **Ghi đè biến SCSS** (không `!default`) trong `web._assets_primary_variables`: `$o-community-color`,
  `$o-brand-primary`, `$o-action`, `$o-main-text-color`, `$o-main-color-muted`, `$o-body-bg`,
  `$o-view-background-color`, các `$o-border-radius*`.
  *Cơ chế:* module phụ thuộc `web` ⇒ file của ta được nối **sau** `primary_variables.scss` trong cùng bundle; SCSS lấy
  khai báo sau ⇒ biến của ta thắng. (Đã xác nhận bundle tồn tại: `web/__manifest__.py:327`.)
- **CSS chỉ trong phạm vi** `.o_web_client`, `.o_login`, `body` của trang login. Cấm selector toàn cục kiểu `td { }`.
- **Login**: chỉ inherit QWeb `web.login_layout` (id tại `web/views/webclient_templates.xml:112`) để thêm khối thương hiệu;
  tuyệt đối không đổi `name=`, `action=`, token CSRF, hay controller (`web.Home.login`).
- Kèm **landing page** `demo/landing/index.html` dựng lại theo cùng token (file tĩnh, offline, không CDN).

## 3. Phương án bị loại

| Phương án | Vì sao loại |
|---|---|
| Fork/sửa `odoo/addons/web` | **L4 — cấm** (AGENTS.md Luật 1) |
| Mua theme (SDLC/Pantalytics/DXReady) | Ngân sách "free"; bản công bố hỗ trợ Odoo 18/19, không chắc 17 CE; license độc quyền |
| Nhồi SCSS vào `lab_core` | Trộn mối quan tâm; `lab_core` là module nghiệp vụ, theme là presentation |
| React/Tailwind cho backend | Cấm trong dự án (AGENTS.md §4); Odoo 17 dùng OWL + SCSS |
| Chỉ đổi màu bằng `ir.config_parameter` | Odoo 17 không có theme engine CE; chỉ đổi được vài accent, không đổi được nền/typography |

## 4. Hệ quả

**Tích cực:** một nơi duy nhất quyết định diện mạo; xoá module = trả về giao diện Odoo nguyên bản; không rủi ro cho
logic nghiệp vụ & phân quyền; demo nhìn "phần mềm SaaS chuyên nghiệp" thay vì "Odoo mặc định".

**Tiêu cực / phải làm:**
- Thêm 1 bước cài: `-u lab_ui` (đã thêm vào `setup.ps1` và runbook).
- Đổi màu phải **rebuild assets**: `-u lab_ui` là đủ; nếu không ăn, xoá `ir_attachment` của bundle + restart.
- Odoo có thể đổi tên class ở bản sau → selector phải bám class ổn định (`o_web_client`, `o_list_view`, `o_form_view`,
  `o_kanban_view`, `o_login`) và ghi chú version trong comment.
- Chữ trên nền phải đạt 4.5:1 — kiểm bằng ảnh chụp thật ở 1440/768/390, không bằng cảm nhận.

## 5. Nghiệm thu

1. `-u lab_ui` không lỗi; vào `/web/login` thấy nền giấy ấm, panel thương hiệu, logo công ty thật, nút hành động **xanh rừng `#1F4A3D`**.
   **Đo cụ thể (đã chạy):** ảnh "before" có **2 827 pixel `#714B67`** (màu nút mặc định của Odoo 17 = `$o-enterprise-color`) và
   **0 pixel** forest; ảnh "after" có **2 827 pixel `#1F4A3D`**, **0 pixel** `#714B67`. Lưu ý: `$o-enterprise-color`
   **không** đi qua `$o-brand-primary`/`$primary`/`$o-btns-bs-override`, nên nút login **chỉ** đổi được bằng CSS tường minh
   (`body.o_login .btn-primary` trong `backend.scss`), không bằng biến SCSS.
2. Chụp 3 bề rộng 1440/768/390 của: trang login, danh sách `Yêu cầu lấy hàng`, form chi tiết
   (login đã có; list/form **chưa** chụp được vì cần phiên đăng nhập — xem §6 việc còn lại).
3. Không còn gradient/glass/glow; contrast đạt; focus nhìn thấy.
4. Landing: 0 vi phạm checklist `design-taste-anti-slop.md` §6; có ≥ 8 phần tử chuyển động + `prefers-reduced-motion`.
5. 69 test Odoo vẫn xanh (theme không được chạm logic).

## 6. Việc còn lại (ghi để không mất)

- **Ảnh backend** (list/form/kanban) chưa chụp: cần một phiên đăng nhập trong trình duyệt headless. Cách rẻ nhất: dùng
  `infra/scripts/shot.ps1` với `--user-data-dir` đã đăng nhập sẵn (mở Chrome thật 1 lần, đăng nhập, rồi trỏ profile đó),
  hoặc chụp tay. Nếu nút primary trong backend ra màu tím ⇒ thêm `variables.scss` **trước** `backend.scss` trong
  `web.assets_backend` (biến vào backend hiện qua include lồng nhau `_assets_helpers` → `_assets_primary_variables`).
- Hai đường kẻ trong card login còn lạnh `#DEE2E6` do Bootstrap dùng `.border-*` với `!important`; muốn đổi phải `!important`
  có lý do hoặc thêm class qua xpath — đã ghi ở `addons/lab_ui/README.md` §6, **cố ý để nguyên**.
- `data/company.xml` ghi logo vào `base.main_company` mỗi lần `-u`; đa công ty thì phải thêm record cho công ty tương ứng.
