# ADR-009 — Palette "Dark Premium + Lime" (thay ADR-007 forest/giấy ấm)

**Trạng thái:** ACCEPTED — chỉ đạo trực tiếp của chủ dự án (2026-11-01), kèm 3 ảnh tham chiếu.
**Cập nhật 2026-10-30 (phiên redesign toàn phần):** khách gửi **4 ảnh tham chiếu mới** + bảng hex cụ thể
⇒ chốt **§6 là bảng token CUỐI CÙNG**; §2 giữ làm lịch sử (giá trị cũ hơn, cùng họ màu).
**Thay thế:** ADR-007 (Modern Industrial: giấy ấm + forest). ADR-007 **không xoá** — giữ làm lịch sử.

## 1. Bối cảnh

Chủ dự án gửi 3 ảnh dashboard tham chiếu (fintech/ERP SaaS nền gần đen, card bo lớn, pill nav,
một card "hero" tô đặc màu, avatar stack) và yêu cầu: đổi accent sang **lime**, đồng bộ phong cách
xuyên suốt **web Odoo → app KMP → desktop app**. Mọi token forest/giấy ấm trở nên sai hướng.

## 2. Quyết định (bản đầu — giữ làm lịch sử)

Một bảng token duy nhất cho cả 3 bề mặt, mọi nơi khác chỉ tham chiếu token:

| Vai | Hex | Ghi chú |
|---|---|---|
| `bg` (ground) | `#0B0D10` | nền gần đen, hơi xanh |
| `surface` | `#14171C` | card |
| `surface-2` | `#1B1F26` | header bảng, track segmented, card lồng |
| `surface-3` | `#232832` | item đang chọn, chip trung tính |
| `line` | `#262B33` | hairline 1px DUY NHẤT |
| `ink` | `#F2F5F7` | chữ chính (17.1:1 trên bg) |
| `ink-muted` | `#8A93A0` | nhãn phụ (6.4:1 trên bg) |
| **`accent`** | **`#C6F24E`** (lime) | hành động chính, nav active, series chính của chart |
| `accent-ink` | `#0B0D10` | chữ **trên** nền lime (13:1) — lime là màu sáng, chữ phải là mực tối |
| `success` | `#5FD68A` | đã về kho |
| `warning` | `#E8A33D` | mới / chờ gán |
| `danger` | `#FF6B5E` | lấy hàng thất bại |
| `hero-from`/`hero-to` | `#D9FA6B` → `#A8E02E` | gradient rất nhẹ CHỈ trên card hero (theo ảnh 1–2) |

**Hình học:** card 16px · control 10px · tag/pill 999px · rail 72px icon · nav = pill segmented
trong header · nhịp 4px · card padding 16 · gap 12.

## 3. Phương án bị loại

| Phương án | Vì sao loại |
|---|---|
| Giữ forest/giấy ấm, chỉ đổi nút sang lime | Chỉ đạo là đổi *phong cách*, không phải đổi màu nút; lime trên giấy ấm là cặp chói |
| Dùng lime làm nền lớn (sidebar lime) | Ảnh tham chiếu dùng lime **rất tiết chế** (1 card hero, 1 tag active); nền lớn sẽ thành "neon" |
| Đổi cả success sang lime | Mất phân biệt trạng thái; success giữ xanh lá `#5FD68A` |
| Fork/sửa `odoo/addons/web` | L4 cấm (AGENTS.md Luật 1) |

## 4. Hệ quả

- **Phải sửa**: `addons/lab_ui/static/src/scss/variables.scss` + `backend.scss` (light→dark là thay đổi lớn:
  bảng, form, chatter, login), `apps/kmp/.../design/Tokens.kt` + `LabPalette.kt`, `design-system/MASTER.md`.
- **Rủi ro chính**: Odoo backend vốn dựa trên Bootstrap sáng; đổi sang dark phải đè nhiều biến
  (`$o-body-bg`, `$o-view-background-color`, `$o-main-text-color`, `$o-border-color`) và kiểm **từng màn**
  bằng ảnh chụp 1440/768/390 — không được tuyên bố xong khi chưa chụp.
- **Chống hồi quy**: giữ nguyên model/controller/API/quyền; chỉ chạm lớp trình bày (stay trong biên giới FE-01).
- **Tương phản**: đo bằng `selfCheck` (app) và bằng ảnh chụp thật (web), không đoán.

## 5. Nghiệm thu

1. App `selfCheck` in bảng contrast: mọi cặp chữ/nền ≥ 4.5:1, đồ hoạ ≥ 3:1 — không FAIL.
2. Web: login + list + dashboard chụp 1440/768/390, không còn pixel giấy ấm/forest, không còn viền sáng trên nền tối.
3. Không màn nào có > 1 vùng lime đặc (trừ card hero dashboard).

---

## 6. TOKEN CUỐI CÙNG (chốt 2026-10-30 với 4 ảnh tham chiếu mới)

> Đây là bảng **duy nhất** được chép vào code. §2 ở trên chỉ còn giá trị lịch sử (cùng họ màu, khác hex).

| Vai | Token CSS | Hex / giá trị | Dùng ở đâu |
|---|---|---|---|
| Nền trang | `--lab-bg` | `#080D17` | body, nền sau cùng |
| Bề mặt 1 | `--lab-s1` | `#101725` | sidebar, top nav |
| Bề mặt 2 | `--lab-s2` | `#192231` | khối nội dung, filter bar |
| Card | `--lab-card` | `#202A3A` | card KPI, bảng, panel |
| Card hover | `--lab-card-2` | `#263245` | hover dòng/card |
| Viền | `--lab-line` | `rgba(255,255,255,.08)` | mọi đường phân tách |
| Viền mạnh | `--lab-line-2` | `rgba(255,255,255,.14)` | input focus, khung chọn |
| Mực | `--lab-ink` | `#F5F7FA` | chữ chính |
| Mực phụ | `--lab-ink-2` | `#929CAB` | nhãn, mô tả |
| Mực mờ | `--lab-ink-3` | `#687486` | chú thích, disabled |
| **Nhấn** | `--lab-accent` | `#A6F000` | CTA, nav active, tab chọn, progress, series 1 |
| Nhấn hover | `--lab-accent-2` | `#B8FF28` | hover CTA |
| Mực trên nhấn | `--lab-on-accent` | `#080D17` | **chữ trên nền lime bắt buộc dùng màu này** |
| Thành công | `--lab-ok` | `#5FD68A` | chip "Đã về kho" |
| Cảnh báo | `--lab-warn` | `#E8A33D` | chip "Chờ gán" |
| Nguy hiểm | `--lab-bad` | `#FF6B5E` | chip "Thất bại"/huỷ |
| Thông tin | `--lab-info` | `#6EA8FF` | chip "Đang đi lấy" |
| Chuỗi biểu đồ | `--lab-c1/c2/c3` | `#A6F000` / `#45D4C4` / `#6EA8FF` | series |
| Lưới biểu đồ | `--lab-grid` | `rgba(255,255,255,.06)` | trục, gridline |
| Bán kính | `--lab-r-1..5` | `8 / 10 / 16 / 20 / 24px` | input · control · card · panel · hero |
| Bóng | `--lab-shadow-1` | `0 8px 24px rgba(0,0,0,.45)` | popover/menu/dialog **duy nhất** |
| Font | `--lab-font` | `Inter, "Segoe UI", system-ui, sans-serif` | toàn bộ |
| Số liệu | — | `font-variant-numeric: tabular-nums` | KPI, bảng số |

**Ánh xạ hex cũ → mới (dùng khi migrate, thay hàng loạt):**

`#0B0D10 → #080D17` · `#14171C → #101725` · `#1B1F26 → #192231` · `#232832 → #202A3A` ·
`#262B33 → rgba(255,255,255,.08)` · `#F2F5F7 → #F5F7FA` · `#8A93A0 → #929CAB` ·
`#C6F24E → #A6F000` · accent-ink `#0B0D10 → #080D17`

## 7. Quy tắc tương phản (bắt buộc, đã tính WCAG AA)

- `#F5F7FA` trên `#080D17` ≈ 16:1 ✓ · `#929CAB` trên `#080D17` ≈ 7.3:1 ✓ · `#687486` ≈ 3.8:1 (chỉ cho chữ ≥ 14px đậm hoặc nhãn phụ).
- `#080D17` trên `#A6F000` ≈ 14:1 ✓ — **không** dùng chữ trắng trên lime.
- `#A6F000` **không** làm màu chữ đoạn dài trên nền tối (chói/mỏi mắt): chỉ CTA, icon, nhãn nhỏ, series chart.
- Trạng thái **không bao giờ** chỉ dựa vào màu: chip luôn có chữ (đã áp: "Đã về kho", "Thất bại"…).

## 8. Cơ chế kỹ thuật

**Web**: token khai ở `variables.scss` (SCSS var) + `backend.scss` (CSS custom property `--lab-*`).
Mặc định dark; nếu cần light (v1) thì chỉ bật bằng `[data-lab-theme="light"]` — **dark là mặc định**.
`backend.scss`/`dashboard.scss` sau migrate **không được** chứa mã màu literal ngoài khối token
(audit: đếm `#[0-9a-fA-F]{3,8}` = 0 ở ngoài khối).
**App**: `design/Tokens.kt` + `LabPalette.kt` + `AppTheme.kt` — giữ nguyên **tên field** để không phải
sửa 30 màn; chỉ đổi giá trị. Giữ nguyên `BREAKPOINT_COMPACT_DP`, `Lab.*`, spacing (đã test 360dp/1440dp).

---

## 9. Quyết định bổ sung — 2026-10-30 (khách chốt trong phiên redesign)

1. **Dark là chế độ DUY NHẤT.** Bỏ hẳn giao diện giấy ấm v1 — không còn nhánh `[data-lab-theme="light"]`,
   không còn `--lab-paper*`, không còn `#F4F0E6/#EAE3D4/#FBF8F1/#16150F/#1F4A3D` trong code web.
   App: bỏ mục "Nền tối" trong Cài đặt demo; luôn render palette dark v2.
2. **Landing page nằm TRONG phạm vi redesign** (`demo/landing/index.html`), với điều kiện cứng:
   **giữ nguyên hero background bằng VIDEO** (`assets/video/hero.mp4`, autoplay/muted/loop/playsinline,
   JS pause khi ra khỏi khung nhìn, `prefers-reduced-motion` ẩn video). Chỉ đổi "da": scrim navy,
   panel kính tối `rgba(16,23,37,.55)` + blur, chữ `#F5F7FA`, CTA lime/chữ navy.
3. Vì vậy nghiệm thu §5 bổ sung: **không còn pixel giấy ấm ở bất kỳ bề mặt nào** (web/backend, app, landing),
   và landing vẫn phát video khi mở bằng `file://`.
