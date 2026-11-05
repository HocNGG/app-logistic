# Bộ ảnh DUYỆT redesign — 2026-11-01

> Đây là **đề xuất thiết kế** (mockup), không phải ảnh chụp code hiện tại.
> Nguồn: `demo/redesign-proposal/index.html` (HTML tĩnh, đúng token của `design-system/lab-logistics/MASTER.md`).
> Duyệt xong mới sửa code thật (app KMP + `addons/lab_ui`).

## Ảnh đề xuất (xem theo thứ tự)

| File | Màn | Nội dung thay đổi so với hiện tại |
|---|---|---|
| `proposal-A-web-dashboard.png` | Web · Bảng điều khiển | Thêm **rail dọc** có nhóm mục; KPI phẳng có chip Δ + 1 sparkline; chart 2 cột; bảng mật độ cao, chip tint |
| `proposal-B-app-desktop.png` | App · Tài xế (desktop, nền tối) | Bỏ viền card (chỉ còn bước tông + 1 lớp bóng); danh sách việc có số thứ tự + chip; CTA rõ |
| `proposal-C-app-mobile.png` | App · Điện thoại 390×844 | Tab dạng **segmented pill** (bỏ gạch chân); card việc gọn; CTA cố định đáy |
| `proposal-D-web-login.png` | Web · Đăng nhập | Hai cột: panel thương hiệu forest + form trên giấy; 3 con số bằng chứng |

## Ngôn ngữ thị giác (một câu)

Giấy ấm + một nhấn xanh rừng + mật độ cao; **phân tách bằng bước tông**, không viền+bóng cùng lúc;
số tabular; chip nền tint 12% + chữ đậm; trạng thái lỗi có vạch trái gỉ đồng làm tín hiệu phụ.

## Đã làm thật (đã nằm trong code, chưa phải toàn bộ đề xuất)

- `addons/lab_ui/views/login.xml` + `backend.scss`: trang đăng nhập 2 cột đã dựng xong (ảnh `web-01..03`).
- `backend.scss` §12: bỏ zebra, header dính, chip tint, segmented view-switcher, tiêu đề 20px.
- Logo/brand assets: đổi từ gradient tím–cyan sang forest/hổ phách phẳng.
- App: `LabCard` (bỏ viền+bóng cùng lúc), `LabNav` (pill), `LabEmptyState` (nút thật), nhãn KPI 2 dòng.

## Còn lại (chờ duyệt)

1. Web: rail dọc + dashboard theo `proposal-A` (hiện dashboard là bản cũ, rail chưa có).
2. App: màn Welcome/Driver/Capture theo `proposal-B/C`.
3. Ảnh `web-04..06`, `app-01` trong thư mục này = **hiện trạng sau khi sửa nhẹ**, dùng làm mốc so sánh.
