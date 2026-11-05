# Tài khoản demo — 8 user cho 6 phòng ban

> 1 user ↔ 1 nhóm quyền. Dùng đúng user khi demo để chứng minh **phân quyền chạy thật**.
> Mật khẩu demo: `lab@2026` (chỉ dùng ở máy local — **không** dùng cho bất kỳ môi trường thật nào).

| # | Login | Tên hiển thị | Nhóm chính | Dùng để demo |
|---|---|---|---|---|
| 1 | `admin` | Quản trị hệ thống | `base.group_system` | Cấu hình, không demo nghiệp vụ |
| 2 | `cs01` | Nguyễn Thu CS | `lab_core.group_cs` | ① Nhận yêu cầu, báo giá, tạo yêu cầu lấy hàng |
| 3 | `taixe01` | Trần Văn Hùng (tài xế) | `lab_core.group_driver` | ② Đi lấy hàng trên app KMP: ảnh · ký · xác nhận |
| 4 | `taixe02` | Lê Minh Tâm (tài xế) | `lab_core.group_driver` | Chứng minh **không** thấy job của `taixe01` (test cách ly) |
| 5 | `kho01` | Phạm Kho | `lab_core.group_warehouse` | ③ Nhận hàng, cân–đo–dán mã kiện, ④ đóng gói, ⑦ xuất kho |
| 6 | `chungtu01` | Hoàng Chứng Từ | `lab_core.group_docs` | ⑤ booking, ⑧ AWB/HBL/manifest, ⑨⑩⑪ hành trình |
| 7 | `ketoan01` | Đỗ Kế Toán | `lab_core.group_account` | ⑫ Tính cước, ⑬ lập Debit, ⑮ hoá đơn + thu tiền |
| 8 | `manager01` | Vũ Trưởng Nhóm | `lab_core.group_manager` | ⑥ Duyệt vào chuyến, ⑭ duyệt & mở khoá Debit, duyệt giảm tải |
| 9 | `sep` | Giám đốc | `lab_core.group_director` | Dashboard read-only |

## Dữ liệu nền (seed)

| Loại | Dữ liệu |
|---|---|
| Shop (khách) | Sen Đá Shop · Mây Trắng Store · Bình Minh Home |
| Đại lý nước ngoài | Sakura Logistics (JP) |
| Kho | `KHO CHÍNH` (nhận/đóng gói) · `KHO BAY` · `KHO TÀU` · location `KHO CHỜ` |
| Chuyến | `CH/AIR/26/0001` (Hà Nội → Tokyo, cut-off 14:00) · `CH/SEA/26/0001` |
| Sản phẩm cước | `Cước vận chuyển (kg)` UoM kg · `Phí lấy hàng tận nơi` |
| Pricelist | Bậc thang: 0–50kg · 50–100kg · 100kg+ (giá air ≠ sea) |

## Ghi chú thực hiện

- User + group khai báo trong `addons/lab_core/data/demo_users.xml` (`noupdate="1"`).
- **Không** tạo user bằng tay trên UI — phải bằng XML để `reset-demo.ps1` dựng lại được (Luật 8).
- Tài xế (`group_driver`) kế thừa `base.group_portal` → **không** vào được backend web. Chỉ dùng app KMP hoặc portal.
- API key cho app: tạo qua Preferences → Account Security → New API Key (dùng **thay password** khi `authenticate`).
