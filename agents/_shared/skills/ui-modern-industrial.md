# Skill — Design system "Modern Industrial" (CHUẨN CỦA DỰ ÁN)

> **Đây là nguồn màu/typo DUY NHẤT của dự án.** Mọi màn hình (Odoo backend, landing, app KMP) lấy token từ đây.
> Sai lệch phải có ADR. Sinh ra để chữa bệnh "giao diện AI đại trà" (nền tối + gradient cyan/indigo + card bo tròn + pill badge).
>
> Nền tảng: hướng "Modern Industrial" trong bản nghiên cứu UI của sếp, **đã sửa chỗ xung đột** với dữ liệu của
> `design-taste-anti-slop.md`: bảng màu "kem + terracotta/rust" bị liệt vào *tell* của giao diện AI, nên dự án dùng
> **giấy ấm + xanh rừng đậm + hổ phách công nghiệp** thay vì kem + đất nung.

---

## 1. Token — LIGHT (Odoo backend, portal, landing, tài liệu)

| Vai | Token | Hex | Dùng ở |
|---|---|---|---|
| Nền | `--lab-paper` | `#F4F0E6` | ground toàn trang |
| Nền bước 1 | `--lab-paper-2` | `#EAE3D4` | panel, hàng lẻ của bảng, vùng chọn |
| Bề mặt nổi | `--lab-card` | `#FBF8F1` | card/modal (chỉ modal mới có shadow) |
| Mực | `--lab-ink` | `#16150F` | chữ chính, tiêu đề |
| Mực phụ | `--lab-ink-2` | `#5A5647` | nhãn, ghi chú (không dùng cho nội dung chính) |
| Đường | `--lab-line` | `#D6CEBC` | viền mảnh DUY NHẤT cho control/bảng |
| Nhấn (chính) | `--lab-forest` | `#1F4A3D` | hành động chính, tiêu đề nhấn, nav active |
| Nhấn (hover) | `--lab-forest-2` | `#17392F` | hover/pressed của nhấn chính |
| Cảnh báo | `--lab-amber` | `#B8761F` | trạng thái cần chú ý, badge "quá tải" (KHÔNG dùng làm màu hành động) |
| Nguy hiểm | `--lab-rust` | `#8C3A22` | lỗi, huỷ, thất bại |
| Thành công | `--lab-moss` | `#3F6B4A` | thành công, đã nhận |

## 2. Token — DARK (app KMP, màn tài xế ngoài đường)

| Vai | Hex |
|---|---|
| Nền | `#0F1512` |
| Bề mặt | `#161E19` |
| Bề mặt 2 | `#1E2822` |
| Mực | `#F1EDE1` |
| Mực phụ | `#A7A395` |
| Đường | `#2A352E` |
| Nhấn | `#8FB9A2` (forest sáng cho nền tối) |
| Cảnh báo | `#D9973F` |
| Nguy hiểm | `#D07255` |

**Không** dùng: `#071`, `#38BDF8`, indigo, violet, và mọi gradient. Nếu file còn `--o-brand-primary == #71639e` (tím mặc định Odoo) thì theme chưa ăn.

## 3. Typography

- **Một họ chữ** cho UI: `"Segoe UI Variable Display", "Segoe UI", Inter, system-ui, sans-serif`.
  Số liệu: `font-variant-numeric: tabular-nums`. (Font display có bản quyền: ghi ADR trước khi thêm, và phải nhúng local để demo offline.)
- Thang 5 bậc (không thêm): `14 / 16 / 20 / 28 / 44` px (mobile dùng `13 / 15 / 18 / 24 / 34`).
- Display: `letter-spacing: -0.03em`, `line-height: 0.95 – 1.05`, **tối đa 2 dòng**.
- Body: `line-height: 1.55`, đo 45–75 ký tự/dòng.
- Nhãn: 13px, chữ thường (sentence case), **không** in hoa + giãn cách, **không** monospace.

## 4. Khoảng cách, bo góc, độ nổi

- Thang 4px: `4 · 8 · 12 · 16 · 24 · 32 · 48 · 64` — không có 13px, 2.3rem.
- Bo góc: `6px` cho control (button/input/badge), `10px` cho panel/card, `999px` **chỉ** cho avatar. Không "bo 12–16px mọi thứ".
- Độ nổi: mặc định **phẳng** — phân tách bằng `--lab-line` hoặc bước tông `paper → paper-2`. Shadow chỉ cho modal/popover/dropdown và **một** lớp: `0 8px 24px rgb(22 21 15 / .10)`.
- Không glow, không glass, không backdrop-blur.

## 5. Thành phần (spec cứng)

| Thành phần | Spec |
|---|---|
| Button chính | cao 40 (mobile 48), padding `0 16`, nền `--lab-forest`, chữ `--lab-card`, radius 6, **không shadow**, hover `--lab-forest-2` |
| Button phụ | nền trong suốt, viền 1px `--lab-line`, chữ `--lab-ink`; hover nền `--lab-paper-2` |
| Input | cao 40, viền 1px `--lab-line`, nền `--lab-card`, focus viền `--lab-forest` + `outline: 2px solid` cùng màu (focus nhìn thấy được), **không** đổ bóng |
| Bảng | header nền `--lab-paper-2`, chữ 13px sentence-case, hàng cao 44, kẻ **chỉ** đường ngang 1px `--lab-line`, số căn phải + tabular |
| Badge trạng thái | chữ 13px, padding `2px 8px`, radius 4, nền tint 12% của màu trạng thái + chữ màu đậm — **luôn có nhãn chữ**, không chỉ màu |
| Nav dọc | nền `--lab-paper-2`, item active: nền `--lab-forest` 10% + vạch trái 3px `--lab-forest` |
| Empty/error/loading | luôn có: icon vẽ bằng CSS (không emoji) + 1 câu giải thích + 1 hành động |

## 6. Chuyển động

- Thời lượng `160–220ms`, easing `cubic-bezier(.2,.6,.2,1)`; hover chỉ đổi màu/nền (không scale quá 1.02).
- Landing/trang công khai: **≥ 8** phần tử chuyển động thật (entrance stagger 60–90ms, dời 16–32px), một signature move, một ảnh động.
- Odoo backend/app làm việc: chuyển động **chỉ** ở login, dashboard, thẻ thống kê, feedback control. Bảng/form: không.
- Luôn có `@media (prefers-reduced-motion: reduce)` giữ nội dung, bỏ đường đi.

## 7. Ánh xạ vào từng nơi

**Odoo 17 backend** — module `lab_ui` (ADR-007):
- SCSS khai trong bundle **`web._assets_primary_variables`** để ghi đè `$o-community-color`, `$o-brand-primary`, `$o-action`, `$o-main-text-color`, `$o-main-color-muted`, `$o-body-bg`, `$o-view-background-color` + CSS custom props cho login.
- **Không** dùng selector toàn cục kiểu `.o_list_view td { }` nếu không giới hạn trong `.o_web_client`; không sửa `odoo/addons/web`.
- Login page: inherit view `web.login` (QWeb XML) — chỉ đổi khối trình bày, **không** chạm controller/CSRF.

**Landing / website** (`demo/landing/index.html`, và bản port sang `website` sau này):
- Ảnh thật của app (chụp trên máy ảo/desktop) đặt trong `assets/`; hero ≥ 50% màn hình đầu.
- Một "thứ sống": bảng tính cước thu nhỏ hoặc bảng chuyến tự cập nhật từ dữ liệu JSON tĩnh — có fallback không JS.

**App KMP** (`apps/kmp/.../design/Tokens.kt`):
- Thay `Brand.night/sky/cyan/indigo` bằng token mục 2; logo vẽ lại theo forest/amber; `LogoMark.kt` là nguồn duy nhất.
- Material 2 (ADR-006) — chọn `colors = lightColors(primary = forest, …)` / `darkColors(...)`, không tự vẽ lại control chuẩn.

## 8. Câu tự vấn (bắt buộc trước khi báo xong UI)

1. Ảnh chụp ở 1440 / 768 / 390px đã **mở ra xem** chưa (không phải "code đúng")?
2. Còn gradient / glass / glow / icon card / pill badge / 01-02-03 / monospace nhãn / emoji nào không?
3. Bảng màu có đúng **một nhấn** không? Có chỗ nào dùng màu làm tín hiệu duy nhất không?
4. Chữ trên nền đạt 4.5:1 chưa (đo bằng công cụ, không đoán)?
5. Bàn phím đi hết được chưa? Focus có nhìn thấy không? Có nhánh `prefers-reduced-motion` chưa?
6. Build/khởi động lại **vẫn** ra giao diện mới chưa? (Odoo: `-u lab_ui` + xoá assets cache nếu cần.)

---

# PHỤ LỤC v2 — DARK NAVY + NEON LIME (ADR-009 §6, HIỆU LỰC 2026-10-30)

> **§1 và §2 ở trên là BẢN CŨ (giấy ấm + forest) — đã bị thay thế.** Mọi màn hình mới/sửa phải dùng
> bảng dưới đây. Nguồn đầy đủ + lý do + phương án bị loại: `docs/adr/ADR-009-dark-premium-lime.md`.
> Tên file giữ nguyên (đã được AGENTS.md §11 và 3 agent trỏ tới) — chỉ nội dung token đổi.

## v2.1 Token — DÙNG CHUNG cho web Odoo + app KMP (một bảng, không có bản riêng)

| Vai | Token | Hex / giá trị | Dùng ở |
|---|---|---|---|
| Nền trang | `--lab-bg` | `#080D17` | body, nền sau cùng |
| Bề mặt 1 | `--lab-s1` | `#101725` | sidebar, top nav |
| Bề mặt 2 | `--lab-s2` | `#192231` | khối nội dung, filter bar |
| Card | `--lab-card` | `#202A3A` | card KPI, bảng, panel |
| Card hover | `--lab-card-2` | `#263245` | hover dòng/card |
| Viền | `--lab-line` | `rgba(255,255,255,.08)` | mọi đường phân tách (hairline 1px) |
| Viền mạnh | `--lab-line-2` | `rgba(255,255,255,.14)` | input focus, khung chọn |
| Mực | `--lab-ink` | `#F5F7FA` | chữ chính |
| Mực phụ | `--lab-ink-2` | `#929CAB` | nhãn, mô tả |
| Mực mờ | `--lab-ink-3` | `#687486` | chú thích, disabled |
| **Nhấn** | `--lab-accent` | `#A6F000` | CTA, nav active, tab chọn, progress, series 1 |
| Nhấn hover | `--lab-accent-2` | `#B8FF28` | hover CTA |
| Mực trên nhấn | `--lab-on-accent` | `#080D17` | **chữ trên nền lime bắt buộc màu này** |
| Thành công | `--lab-ok` | `#5FD68A` | chip "Đã về kho" |
| Cảnh báo | `--lab-warn` | `#E8A33D` | chip "Chờ gán" |
| Nguy hiểm | `--lab-bad` | `#FF6B5E` | chip "Thất bại"/huỷ |
| Thông tin | `--lab-info` | `#6EA8FF` | chip "Đang đi lấy" |
| Chuỗi chart | `--lab-c1/c2/c3` | `#A6F000` / `#45D4C4` / `#6EA8FF` | series |
| Lưới chart | `--lab-grid` | `rgba(255,255,255,.06)` | trục, gridline |

**Hình học v2** (thay §4 bản cũ): radius `8 / 10 / 16 / 20 / 24px` (input · control · card · panel · hero) ·
bóng **chỉ** cho popover/menu/dialog `0 8px 24px rgba(0,0,0,.45)`; card phân tầng bằng **bề mặt**, không bóng ·
nhịp 4px · card padding 16 · gap 12 · H1 30–40px · số KPI 30px `tabular-nums`.

## v2.2 Bốn luật cứng của v2

1. **Dark là mặc định.** Không cần bật gì. Light v1 (`[data-lab-theme="light"]`) chỉ là tuỳ chọn, không phải màn demo.
2. **Lime là màu nhấn, không phải màu nền.** Tối đa 1 vùng lime đặc/màn (card hero dashboard). Không dùng lime cho chữ đoạn dài, không dùng làm nền sidebar/bảng.
3. **Chữ trên lime = `#080D17`.** Không bao giờ dùng chữ trắng trên lime (tương phản kém + chói).
4. **Trạng thái luôn kèm chữ** (chip có nhãn) — màu chỉ là phụ trợ, không phải tín hiệu duy nhất.

## v2.3 Nhận từ skill `ui-ux-pro-max` (chỉ phần cấu trúc, KHÔNG lấy palette nó đề xuất)

Pattern *Real-Time/Operations*; typography **Inter** (fallback `"Segoe UI", system-ui, sans-serif`);
hover 150–300ms; tôn trọng `prefers-reduced-motion`; không emoji làm icon (SVG); breakpoint
375 / 768 / 1024 / 1440; cấm gradient tím/hồng "AI", cấm "static tracking" (số liệu phải có mốc thời gian).

## v2.4 Kiểm trước khi báo xong (bổ sung cho §8)

7. Có **0 mã màu literal** ngoài khối token không? (`grep -c '#[0-9a-fA-F]\{3,8\}'` trên `backend.scss`/`dashboard.scss` phải về 0.)
8. Chụp **1440 / 768 / 390** (web) và **360dp + 1440dp** (app) rồi mở ảnh ra xem chưa?
9. Cặp `--lab-ink`/`--lab-bg`, `--lab-ink-2`/`--lab-bg`, `--lab-on-accent`/`--lab-accent` đã đo ≥ 4.5:1 chưa?
