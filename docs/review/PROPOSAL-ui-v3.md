# ĐỀ XUẤT UI v3 — "Dark shell + Ivory data panel + Floating detail card"

> **Trạng thái: CHỜ DUYỆT.** Chưa sửa một dòng code sản phẩm nào. Đây là bản dựng tĩnh để anh
> xem và chỉnh trước khi tôi áp vào Odoo / app KMP / desktop.
> Dựng từ: ảnh anh gửi (Salesforce/San Francisco "Invoices" shot) + link Dribbble
> `dribbble.com/shots/24527373` (Dribbble trả 202, không cho tải nội dung ⇒ dùng ảnh làm chuẩn).
>
> Prototype (mở trực tiếp bằng trình duyệt): `demo/prototype/web.html` · `desktop.html` · `app.html`
> Ảnh render: `docs/review/proto-v3/proto-web-1440.png` · `proto-desktop-final.png` · `proto-app-final.png`

---

## 1. Đã "học" gì từ ảnh (và áp vào cả 3 nền tảng)

| # | Chi tiết trong ảnh | Áp vào sản phẩm |
|---|---|---|
| 1 | Nền gần đen + **một panel dữ liệu màu ngà (ivory)** | Danh sách = panel ivory; phần còn lại navy. Đảo "trọng lượng thị giác": dữ liệu sáng, khung tối |
| 2 | **Card chi tiết NỔI** đè lên panel, có bóng mềm | Chi tiết chuyến/việc = card nổi, đổ bóng — **ngoại lệ duy nhất** của luật "không bóng" |
| 3 | **Segmented tab** trong pill tối, tab đang chọn = **lime, chữ tối** | Top nav: mục active = pill lime (đã có trên Odoo, port sang app/desktop) |
| 4 | 4 **thẻ KPI**: nhãn 11px uppercase, số 30px, **1 thẻ tô lime đặc** | Dải KPI đầu màn: thẻ số 4 = lime (ngân sách lime: 1 thẻ + 1 CTA + 1 nhấn trong list) |
| 5 | Avatar stack + thanh tiến độ mảnh dưới KPI | "Chuyến: 6 chuyến · VJ JL KE NH" + bar lime |
| 6 | **Hàng filter pill** (dropdown 999px) + ô tìm kiếm pill | Filter bar của list (Odoo: search facets + filter pills) |
| 7 | Bảng: avatar + mã + phụ đề 2 dòng + chip trạng thái + số tiền | Row list: avatar chữ cái, mã chuyến, "hãng · tuyến", chip trạng thái, % tải |
| 8 | Bo góc lớn: shell 28 · panel 20 · card 16 · pill 999 | Token radius (đã có 8/10/16/20/24 → thêm 28 cho shell) |
| 9 | Typography: 1 họ, số `tabular-nums`, tiêu đề 30–34px, nhãn 11px | Giữ Inter, thêm quy tắc "nhãn uppercase 11px + letter-spacing .05em" |

## 2. Token — 5 giá trị MỚI cần duyệt (bổ sung ADR-009 §6)

| Vai | Token | Hex | Ghi chú |
|---|---|---|---|
| Panel dữ liệu | `--lab-ivory` | `#F6F4EC` | nền panel danh sách |
| Panel hover/zebra | `--lab-ivory-2` | `#ECEAE0` | hover row, header nhóm |
| Đường kẻ trên ivory | `--lab-ivory-line` | `rgba(20,24,31,.10)` | hairline |
| Mực trên ivory | `--lab-ink-ivory` | `#14181F` | chữ chính (đo 15,4:1 trên ivory) |
| Mực phụ trên ivory | `--lab-ink2-ivory` | `#5B6270` | phụ đề (đo 6,2:1) |

**Không đổi**: bg `#080D17` · s1 `#101725` · s2 `#192231` · card `#202A3A` · accent `#A6F000` ·
on-accent `#080D17` · ok/warn/bad/info. **Luật giữ nguyên**: lime không làm nền diện rộng; chữ trên
lime luôn `#080D17`; trạng thái luôn kèm chữ.

## 3. Bố cục từng nền tảng (ba ảnh trong `docs/review/proto-v3/`)

### WEB — Odoo (1440) · `proto-web-1440.png`
`top tabs pill` → `tiêu đề + 2 nút` → `4 thẻ KPI (thẻ 4 lime)` → `filter pills + search` →
**panel ivory** "Chuyến đang xử lý" (segmented Tất cả/Nháp/Đã chốt, 5 dòng) + **card nổi** chi tiết
chuyến (3 ô ngày/cut-off/booking, 2 ô kho/khách, 3 số sức chứa/đã dùng/tỉ lệ tải, 4 nút trạng thái).

### DESKTOP — app KMP màn rộng (1280) · `proto-desktop-final.png`
`rail icon 72px` (mục active = ô lime) + `4 thẻ KPI` + **panel ivory** "10 việc cập nhật" +
card dark "Cơ cấu trạng thái" (donut lime/teal/xanh + legend). Không dùng bóng, không gradient.

### APP — mobile 390 · `proto-app-final.png`
`header` → tiêu đề → **2 thẻ KPI (thẻ 1 lime)** → filter pill + search → **danh sách NỀN TỐI**
(avatar, mã, phụ đề, chip) → **sheet chi tiết** dưới (2 ô giờ/kiện, địa chỉ, liên hệ) + 2 nút
"Không lấy được" / **"Bắt đầu đi" (lime)**.

## 4. Vì sao mobile KHÔNG dùng panel ivory toàn màn hình (đề xuất)

Tài xế dùng app ngoài đường, nhiều lúc trời tối: một panel ngà chiếm 80% màn hình sẽ chói và phá
"ban đêm" của app. Đề xuất: **ivory chỉ ở web + desktop** (nơi ảnh tham chiếu sống), mobile giữ
danh sách tối + sheet tối. Nếu anh muốn "y chang" cả mobile, tôi đổi 1 dòng CSS.

## 5. Thứ tự thi công (sau khi duyệt)

| Bước | Việc | File | Ước lượng |
|---|---|---|---|
| 1 | Token ivory + radius 28 + luật "bóng chỉ cho card nổi" | `backend.scss`, `variables.scss`, `Tokens.kt`, `LabPalette.kt` | 1 h |
| 2 | **Web**: dải KPI + panel ivory cho list + card nổi cho form | `backend.scss`, `views/*.xml` (thêm khối KPI cần OWL) | 3–4 h |
| 3 | **Desktop app**: rail + dải KPI + panel ivory | `App.kt`, `ui/kit/*`, `feature/**` | 3 h |
| 4 | **Mobile app**: KPI 2 thẻ + list tối + sheet | `feature/**` | 2–3 h |
| 5 | Ảnh nghiệm thu 1440/1280/390 + regression (305 việc · 7 nút chuyến · app login) | — | 1,5 h |

**Rủi ro đã biết**: (a) Odoo list không có "dải KPI" sẵn ⇒ phải viết OWL như dashboard đã làm;
(b) panel ivory đè lên Bootstrap dark phải khai riêng cho từng cell/header; (c) bóng trên card nổi
là ngoại lệ — phải ghi vào ADR để RV không bắt lỗi.

## 6. Cần anh chốt (5 điểm)

1. **Panel ivory**: dùng cho web+desktop (đề xuất) / dùng cả mobile / bỏ, giữ toàn tối?
2. **Độ sáng lime**: giữ `#A6F000` (đang có, đã đo tương phản) hay theo ảnh sáng hơn `#C6FF3D`?
3. **Dải KPI trên mỗi màn danh sách** (cần OWL custom, +3–4 h) hay chỉ đặt KPI ở Dashboard?
4. **Card nổi**: đúng như ảnh (đè lên panel, có bóng) hay đặt cạnh panel (không bóng)?
5. **Phạm vi đợt này**: làm đủ 3 nền tảng (≈10–12 h) hay làm WEB trước để duyệt rồi mới port?

---

## 7. BIÊN BẢN DUYỆT (khách chốt 2026-10-30)

| Điểm | Quyết định của khách | Hệ quả kỹ thuật |
|---|---|---|
| Panel ivory | **Cả 3 nền tảng, kể cả mobile** | Mobile: panel danh sách = ivory, sheet chi tiết = tối; phải khai --lab-ivory* trong Compose Tokens.kt + LabPalette.kt |
| Sắc lime | **#C6FF3D** (sáng hơn ảnh) | Đổi --lab-accent; --lab-accent-2 (hover) = #D6FF6B. **Đã đo lại**: lime/nền 16,45:1 · chữ #080D17 trên lime 16,45:1 · lime trên ivory **1,07:1 ⇒ CẤM làm chữ/icon trên ivory, chỉ làm nền chip** |
| Dải KPI | **Có, ở MỌI màn danh sách** | Cần OWL component lab_kpi_strip dùng lại payload /sep/kpi + lab_dashboard_kpi (không thêm API mới) |
| Phạm vi | **Đủ 3 nền tảng** | Web (Odoo) → Desktop (KMP wide) → Mobile (KMP compact), theo thứ tự §5 |

**Chưa chốt (tôi sẽ hỏi khi tới bước đó, không tự quyết)**: card nổi có bóng đúng ảnh hay đặt cạnh
panel; có giữ nút "Xuất CSV" ở header không (ảnh không có).

**Tương phản ivory (đo bằng công thức WCAG, không đoán)**: chữ #14181F/ivory = **16,16:1** ·
phụ #5B6270/ivory = **5,57:1** · lime/ivory = **1,07:1** (chỉ nền).