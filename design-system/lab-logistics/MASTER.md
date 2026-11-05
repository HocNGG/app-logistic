# MASTER — Design System "Lab Logistics" (Web Odoo + App KMP)

> **Đây là file quy chuẩn thiết kế của toàn sản phẩm.** Mọi màn hình mới đọc file này trước khi viết CSS/Compose.

---

## ⚠️ V2 HIỆU LỰC TỪ 2026-10-30 — DARK NAVY + NEON LIME

Toàn bộ token màu trong file này (§0–§2, bảng "giấy ấm + forest") **đã bị thay thế** bởi
**`docs/adr/ADR-009-dark-premium-lime.md` §6**. Bảng mới (chép nguyên, không tự nghĩ màu):

`bg #080D17` · `s1 #101725` · `s2 #192231` · `card #202A3A` · `card-2 #263245` ·
`line rgba(255,255,255,.08)` · `line-2 rgba(255,255,255,.14)` ·
`ink #F5F7FA` · `ink-2 #929CAB` · `ink-3 #687486` ·
**`accent #A6F000`** · `accent-2 #B8FF28` · `on-accent #080D17` ·
`ok #5FD68A` · `warn #E8A33D` · `bad #FF6B5E` · `info #6EA8FF` ·
`c1 #A6F000` / `c2 #45D4C4` / `c3 #6EA8FF` / `grid rgba(255,255,255,.06)`

**Vẫn giữ từ file này:** cấu trúc thông tin, thứ tự ưu tiên khi mâu thuẫn, các luật về mật độ khoảng cách,
quy tắc "một nền — một mực — một nhấn", danh sách anti-pattern. **Thay đổi:** mọi mã màu + radius
(8/10/16/20/24) + dark là mặc định. Trạng thái từng phase: xem `REDESIGN_PROGRESS.md` ở gốc repo.

---

> Nguồn gốc: skill `ui-ux-pro-max` (`.agents/skills/ui-ux-pro-max/`) chạy `--design-system` ngày 2026-11-01,
> **hợp nhất** với token bắt buộc của repo là `agents/_shared/skills/ui-modern-industrial.md` (ADR-007).
>
> Thứ tự ưu tiên khi mâu thuẫn: **AGENTS.md > docs/ > agents/&lt;role&gt;/AGENT.md > ui-modern-industrial.md > MASTER.md này > skill ngoài**.
> Nghĩa là: MASTER.md **không được** đổi bảng màu, chỉ được **cụ thể hoá cách dùng**.

---

## 0. Vì sao bảng màu của `ui-ux-pro-max` KHÔNG được dùng

Skill trả về palette `#2563EB` (xanh dương) + `#EA580C` (cam) cho "logistics ERP". Palette đó bị loại vì:

| Lý do | Bằng chứng |
|---|---|
| Xanh dương + cam là cặp của **mọi** SaaS doanh nghiệp → không nhận ra sản phẩm | `design-taste-anti-slop.md` §3.9 "một nền, một mực, một nhấn"; §3.12 cấm bảng màu "trợ lý AI" |
| `ui-modern-industrial.md` §1 đã chốt giấy ấm + forest + hổ phách; đổi màu phải có ADR | ADR-007 §2 |
| Palette mới sẽ làm **lệch** app KMP (đang chạy dark forest) và ảnh landing đã chụp | `docs/review/refs/ref-03-forest-inventory.png` |

**Nhận từ skill:** tên style (Minimalism & Swiss Style), mật độ 8 (dense/dashboard), typography Inter + hệ thống,
thang motion "Stagger List" (rút về 60–120ms vì backend không được animate), và danh sách anti-pattern.

---

## 1. Hướng thị giác (một câu)

> **Giấy ấm và mực đậm, một nhấn xanh rừng, mật độ cao, đường kẻ mảnh thay cho bóng đổ.**
> Bề mặt phân tách bằng **một bước tông**, không bằng viền + bóng cùng lúc.

Ba ảnh tham chiếu (đã có trong repo): `docs/review/refs/ref-01-olive-overview.png` (bố cục dashboard),
`ref-02-dark-orange.png` (sidebar 2 tầng + KPI + sparkline), `ref-03-forest-inventory.png` (bảng + chip trạng thái).

---

## 2. Token — chép nguyên, không tự nghĩ màu mới

### 2.1 Light (Odoo backend, portal, landing, tài liệu)

| Vai | Token | Hex |
|---|---|---|
| Nền | `--lab-paper` | `#F4F0E6` |
| Nền bước 1 | `--lab-paper-2` | `#EAE3D4` |
| Bề mặt nổi | `--lab-card` | `#FBF8F1` |
| Mực | `--lab-ink` | `#16150F` |
| Mực phụ | `--lab-ink-2` | `#5A5647` |
| Đường | `--lab-line` | `#D6CEBC` |
| Nhấn | `--lab-forest` | `#1F4A3D` |
| Nhấn hover | `--lab-forest-2` | `#17392F` |
| Cảnh báo (chữ) | `--lab-amber-ink` | `#7F4E12` |
| Cảnh báo (đồ hoạ) | `--lab-amber` | `#B8761F` |
| Nguy hiểm | `--lab-rust` | `#8C3A22` |
| Thành công | `--lab-moss` | `#3F6B4A` |

### 2.2 Dark (app KMP) — `apps/kmp/.../design/Tokens.kt` (`Brand`)

`ground #0F1512` · `surface #161E19` · `surface2 #1E2822` · `ink #F1EDE1` · `inkMuted #A7A395` ·
`line #2A352E` · `forest #8FB9A2` · `forestHover #6E9C86` · `amber #D9973F` · `danger #D07255`

### 2.3 Bề mặt bổ sung (MỚI — dùng cho cả hai phía)

Thêm **hai** bước tông để hết phải dùng viền:

| Vai | Light | Dark | Dùng cho |
|---|---|---|---|
| `--lab-elev-1` | `#EFEADF` | `#131A16` | vùng chìm: nền của panel lọc, khối "trống" |
| `--lab-elev-2` | `#FFFDF8` | `#1B241F` | khối nổi trong card (dòng hover, ô KPI phụ) |

> Thêm `--lab-elev-*` **không** phải màu nhấn mới: cả hai đều là bước tông của `paper`/`ground`, chênh ≤ 5% độ sáng.

---

## 3. Kiểu chữ

- **Một họ chữ**: `"Segoe UI Variable Display", "Segoe UI", Inter, system-ui, sans-serif`. App KMP dùng mặc định hệ thống.
- **Số luôn `tabular-nums`** (bảng, KPI, tiền, thời gian). Đây là luật, không phải tuỳ chọn.
- Thang: `12 / 13 / 16 / 20 / 28 / 44` (desktop) · `12 / 13 / 15 / 18 / 24 / 34` (mobile).
- **Không** dùng `text-transform: uppercase` cho nhãn tiếng Việt (dấu mũ bị dày, xấu) — `ui-modern-industrial.md` §3.
- Tiêu đề: `letter-spacing: -.02em`, `line-height: 1.05`, tối đa 2 dòng.
- Nhãn bảng/ô nhập: 13px, `--lab-ink-2`, weight 500. Giá trị: 13–14px, `--lab-ink`, weight 400.

---

## 4. Khoảng cách · bo góc · độ nổi

- Thang 4px: `4 · 8 · 12 · 16 · 24 · 32 · 48 · 64`.
- Bo góc: `6` control · `10` panel/card · `4` chip nhỏ · `999` **chỉ** avatar.
- **Độ nổi: mặc định phẳng.** Một lớp bóng duy nhất `0 1px 2px rgb(22 21 15/.06), 0 8px 24px rgb(22 21 15/.06)` —
  **chỉ** cho card dashboard và sheet form. Modal/dropdown dùng `0 8px 24px rgb(22 21 15/.10)`.
- **Không** dùng viền **và** bóng trên cùng một khối (đây là lỗi thị giác phổ biến nhất của bản cũ).
- Không glow, không glass, không backdrop-blur, không gradient.

---

## 5. Quy tắc riêng cho **bảng dữ liệu** (web Odoo list view)

Đây là màn hình được nhìn nhiều nhất ⇒ chi tiết quan trọng hơn trang chủ.

1. **Bỏ zebra dày**: không tô cả hàng chẵn bằng `paper-2 @55%`. Dùng **đường kẻ ngang 1px** `--lab-line` giữa các hàng +
   nền `--lab-card` đồng nhất. Hover hàng: `forest @5%`. Hàng đang chọn: `forest @10%` + vạch trái 2px forest.
2. Header bảng: nền `--lab-paper-2`, chữ 13px weight 600 `--lab-ink-2`, **dính** (`position: sticky; top: 0`).
3. Hàng cao `44px` (mật độ), padding ngang 12px. Số + ngày **căn phải**, `tabular-nums`.
4. **Không tô đỏ cả ô chỉ vì trạng thái lỗi.** Trạng thái chỉ nằm ở **chip**; hàng lỗi nhận thêm vạch trái 2px `--lab-rust`
   (màu làm tín hiệu **phụ**, chữ trong chip mới là tín hiệu **chính** — WCAG 1.4.1).
5. Chip: nền tint 12% của màu trạng thái, chữ màu đậm cùng họ, radius 4px, padding `2px 8px`, **luôn có chữ**.
6. Checkbox chọn hàng: 16px, viền `--lab-line`; khi chọn nền `--lab-forest`.
7. Thanh công cụ (search + pager + view switcher) nằm **một hàng**, cao 56px, nền `--lab-paper`, kẻ dưới 1px.
   Ô search là **pill** bo 6px, icon kính lúp bên trái, rộng tối đa 420px.

---

## 6. Quy tắc riêng cho **form / sheet**

- Sheet form nền `--lab-card`, radius 10, **một** lớp bóng card, **không** viền ngoài.
- Nhóm trường: tối đa 2 cột; nhãn trên, giá trị dưới (label 13px `ink-2`); khoảng cách giữa nhóm 24px.
- `statusbar` (thanh trạng thái): nền `--lab-paper-2`, các bước là **chip chữ**, bước hiện tại nền forest + chữ card.
- Chatter: nền `--lab-card`, phân tách bằng đường kẻ, không bóng.
- Focus: viền forest 1px **+** `outline: 2px solid forest` offset 1px — đo được, không chỉ đổi màu nhẹ.

---

## 7. Trang đăng nhập (màn duy nhất được phép "đẹp")

- Bố cục **2 cột**: trái = panel thương hiệu nền **forest đậm** (dùng token dark), phải = form trên giấy.
- Panel trái chứa: logo vẽ bằng CSS (2 kiện + pallet), wordmark, **một** câu tuyên bố ≤ 2 dòng, và **3** dòng bằng chứng
  (19 bước · 6 phòng ban · bút toán debit tự động) — đây là "argument", không phải trang trí.
- Form: nhãn rõ, không placeholder-only; nút chính forest cao 44px; link phụ `--lab-forest`.
- **Chuyển động chỉ dùng `transform`**, không animate `opacity` từ 0 (bản cũ làm panel vô hình trong ảnh chụp/print).
- `prefers-reduced-motion: reduce` → bỏ chuyển động, giữ nguyên bố cục.

---

## 8. App KMP (Compose)

- Nền tối `ground`, card `surface` — **không viền**; phân tách bằng bước tông `surface → surface2` và khoảng trắng.
- Nav: desktop ≥900dp = **rail dọc** có nhóm; <700dp = thanh trên 2 hàng với item đang mở nền forest 12% (không gạch chân).
- KPI card: nhãn 13 · số 32/40 tabular · chip Δ chỉ khi có dữ liệu thật · sparkline 40dp chỉ khi có chuỗi thời gian.
- Bảng/list app: hàng 64dp (ngón tay), không kẻ dọc, đường kẻ ngang `line`.
- Chạm: nút chính 56dp, phụ 48dp; vòng focus 2dp `forest`.

---

## 9. Anti-pattern — **tự kiểm trước khi báo xong**

- [ ] Không gradient / glass / glow / vignette / neon.
- [ ] Không `uppercase` + `letter-spacing` cho nhãn.
- [ ] Không emoji làm icon; icon là hình học/CSS hoặc SVG.
- [ ] Không zebra dày; không viền + bóng cùng lúc.
- [ ] Không màu nào làm **tín hiệu duy nhất** (luôn kèm chữ hoặc hình).
- [ ] Mọi số đều từ dữ liệu thật, có đơn vị; không "số liệu trang trí".
- [ ] Contrast ≥ 4.5:1 cho chữ thường, ≥ 3:1 cho đồ hoạ — **đo**, không đoán (`selfCheck` in bảng).
- [ ] Có `prefers-reduced-motion`.
- [ ] Ảnh chụp 1440 / 768 / 390 đã **mở ra xem**.

---

## 10. Ánh xạ file

| Bề mặt | File |
|---|---|
| Odoo — biến SCSS | `addons/lab_ui/static/src/scss/variables.scss` |
| Odoo — theme backend + login | `addons/lab_ui/static/src/scss/backend.scss` |
| Odoo — trang đăng nhập | `addons/lab_ui/views/login.xml` |
| Odoo — Bảng điều khiển OWL | `addons/lab_ui/static/src/dashboard/{dashboard.js,dashboard.xml,dashboard.scss}` |
| App — token | `apps/kmp/composeApp/src/commonMain/kotlin/vn/lab/logistics/design/{Tokens.kt,LabPalette.kt,AppTheme.kt}` |
| App — bộ kit | `apps/kmp/composeApp/src/commonMain/kotlin/vn/lab/logistics/ui/kit/*.kt` |
| Landing | `demo/landing/index.html` |
