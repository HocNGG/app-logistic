# Skill — Dashboard & màn hình làm việc (app screen) theo bộ ảnh tham chiếu của sếp

> **Nguồn tham chiếu (ảnh sếp gửi, đã lưu trong repo — coi là *bằng chứng*, không phải template để copy):**
> `docs/review/refs/ref-01-olive-overview.png` (olive + kem, card xếp lớp, area/line/bar/donut)
> `docs/review/refs/ref-02-dark-orange.png` (nền than + cam, sidebar 2 tầng, KPI + sparkline, step chart)
> `docs/review/refs/ref-03-forest-inventory.png` (xanh rừng + kem + mù tạt, bảng tồn kho + chip trạng thái)
> `docs/review/refs/ref-04-warehouse-racks.jpg` (trắng + panel đen, số lớn 01/02/03, gauge tròn)
>
> **Quan hệ với các skill khác:** landing/trang công khai theo `design-taste-anti-slop.md` (cấm shadow/card/chip).
> **Màn hình làm việc (dashboard, bảng, form) thì KHÁC** — skill đó cũng nói rõ: "quiet borders, small labels and repeated
> panels are normal there". File này là luật cho *app screen*, và token vẫn lấy từ `ui-modern-industrial.md`.
> Cấm: gradient, glow, icon trong ô tinted, chart cầu vồng, số liệu bịa.

---

## 1. Cái học được từ 4 ảnh (và cái KHÔNG copy)

| Học | Từ ảnh | Áp vào dự án |
|---|---|---|
| Card xếp lớp trên nền tông ấm, viền rất nhẹ + shadow mờ | 01, 03 | `--lab-card` trên `--lab-paper`, shadow `0 1px 2px` + `0 8px 24px rgb(22 21 15/.06)` |
| Một màu nhấn duy nhất, mọi chart dùng cùng họ màu đó | 01 (olive), 02 (cam), 03 (rừng + mù tạt) | nhấn = forest `#1F4A3D`; chart dùng **thang 4 bậc của forest** + amber chỉ cho "cần chú ý" |
| KPI card: nhãn nhỏ + số lớn + chip Δ + sparkline | 01, 02 | đúng cấu trúc này (xem §3) |
| Bảng có chip trạng thái chữ + màu nền nhạt | 03 | chip `Low Stock`/`Reorder` → của ta: `Chờ gán`/`Đang lấy`/`Đã lấy`/`Đã về kho` |
| Sidebar 2 tầng: wordmark + nhóm mục + item active có nền | 02, 03 | nav dọc: nhóm "VẬN HÀNH / ĐỐI TÁC / HỆ THỐNG", item active nền forest 10% + vạch trái 3px |
| Số lớn làm điểm nhấn thị giác (01/02/03 Rack) | 04 | dùng cho "số việc hôm nay" / số kiện; **không** dùng số La Mã/01-02-03 làm trang trí |
| **KHÔNG copy**: nền tối + cam nê-ông (02), neon xanh (04), gradient, gauge có icon cảm xúc | 02, 04 | giữ hệ giấy ấm hoặc dark `#0F1512` của app; gauge chỉ khi có ngưỡng nghiệp vụ thật |

## 2. Token bổ sung cho app screen (thêm vào `ui-modern-industrial.md` §1–2)

```
--lab-chart-1: #1F4A3D   /* forest đậm — dữ liệu chính */
--lab-chart-2: #4E7A63   /* forest vừa */
--lab-chart-3: #8FB9A2   /* forest nhạt */
--lab-chart-4: #C9B18A   /* cát — dữ liệu nền/so sánh */
--lab-chart-warn: #B8761F /* hổ phách — CHỈ cho ngưỡng vượt/cần chú ý */
--lab-card-shadow: 0 1px 2px rgb(22 21 15 / .06), 0 8px 24px rgb(22 21 15 / .06)
--lab-radius-card: 10px     --lab-radius-chip: 4px
```
Dark mode: chart-1 → `#8FB9A2`, chart-2 → `#6E9C86`, chart-3 → `#C9B18A`, warn → `#D9973F`.

## 3. KPI card — cấu trúc bắt buộc (không tự thêm)

```
┌ nhãn 13px, chữ thường, --lab-ink-2 ────────────────┐
│ SỐ LỚN 32–44px, tabular-nums, --lab-ink            │  ← một con số, không 3 số
│ chip Δ (+12,8% tuần này)  nền tint 12% + chữ đậm  │  ← phải có MỐC so sánh thật
│ sparkline 40px (line, 1 màu forest)                 │  ← chỉ khi có chuỗi thời gian thật
└─────────────────────────────────────────────────────┘
```
- Δ chỉ khi tính được từ dữ liệu thật; **cấm** "↑10.5%" tự bịa (đây là *tell* của dashboard giả).
- Nếu không có chuỗi thời gian → bỏ sparkline, giữ card 2 dòng.

## 4. Chart — chỉ 4 loại, một bảng màu

1. **Area/line** cho xu hướng theo ngày/tuần (1–2 series).
2. **Bar dọc** cho so sánh theo nhóm (shop, kho, tài xế).
3. **Donut** cho cơ cấu ≤ 5 phần (tỷ lệ trạng thái việc) — chỉ 1 vòng, không 3D, không nổ.
4. **Step/accumulate** cho tải chuyến theo thời gian.
Cấm: 3D pie, chart cầu vồng (>5 màu), radar không nghiệp vụ, gauge không ngưỡng, animation vô hạn.
Luôn có: trục có nhãn đơn vị, tooltip/legend chữ, và **giá trị rỗng hiển thị "chưa có dữ liệu"** thay vì chart 0.

## 5. Bảng & chip

- Hàng cao 44–48px; chỉ kẻ **đường ngang 1px** `--lab-line`; header `--lab-paper-2` chữ 13px chữ thường.
- Số căn phải + `tabular-nums`; ngày theo `dd/MM/yyyy`; đơn vị trong header, không lặp trong ô.
- Chip: chữ 13px, `padding 2px 8px`, radius 4, nền tint 12%, **luôn có chữ** (không chỉ màu).
- Trạng thái nghiệp vụ dùng đúng nhãn tiếng Việt của model (Mới/Đã gán/Đang đến lấy/Đã lấy được hàng/Đã về kho/Lấy hàng thất bại/Đã huỷ).

## 6. Bố cục dashboard chuẩn (dùng cho màn Sếp/Manager)

```
[toolbar]  tiêu đề + khoảng thời gian (chips: Hôm nay · 7 ngày · 30 ngày) + nút xuất
[4 KPI]    Việc chờ gán · Đang lấy hàng · Đã về kho hôm nay · Tải chuyến trung bình
[2 chart]  trái: xu hướng việc theo ngày (area, 7–30 điểm) · phải: cơ cấu trạng thái (donut)
[bảng]     10 việc gần nhất: mã việc · shop · trạng thái (chip) · tài xế · khung giờ · cập nhật
```
Responsive: ≤ 600dp → 1 cột, KPI 2×2, chart cao 160px, bảng chỉ 3 cột (mã việc · shop · chip).
Bàn phím: mọi chip lọc và hàng bảng focus được; `↑/↓` di chuyển hàng, `Enter` mở.

## 7. Checklist nhanh

- [ ] Mọi số trên màn hình lấy từ dữ liệu thật, có mốc so sánh hoặc ghi rõ "chưa có dữ liệu".
- [ ] Chỉ một họ màu chart (forest) + hổ phách cho ngưỡng.
- [ ] Không gradient/glow/neon; shadow chỉ trên card/modal.
- [ ] Chip có chữ, đúng nhãn nghiệp vụ tiếng Việt.
- [ ] 390px: 1 cột, không tràn ngang, không cắt chữ.
- [ ] Trạng thái loading/empty/error cho từng khối (KPI, chart, bảng).
- [ ] Ảnh chụp 1440 + 390 đã mở ra xem.
