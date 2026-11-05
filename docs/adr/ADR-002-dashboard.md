# ADR-002 — Dashboard cho sếp trên Odoo 17 Community

**Trạng thái:** ACCEPTED (đã kiểm chứng source) · **Đề xuất:** RV-01

## Bối cảnh

Cần "sếp mở 1 trang thấy hết: doanh thu, sản lượng, chuyến, ngoại lệ". Ràng buộc: **Odoo 17 Community**, không dùng EE.

## Phương án

- **A.** Controller + HTML + Chart.js tự viết → **viết lại Odoo, cấm** (Luật 1).
- **B.** Chỉ dùng `pivot`/`graph` view (core `web`) → nhanh, nhưng sếp phải bấm nhiều.
- **C.** `spreadsheet_dashboard` → dashboard lưu thành menu.
- **D.** OCA `web_*` dashboard → thêm phụ thuộc, không cần thiết.

## Quyết định

**C** (kèm **B** cho phân tích nhanh).

**Đã kiểm chứng:** `spreadsheet` **và** `spreadsheet_dashboard` đều là **Community (LGPL-3)**, không phải Enterprise. `spreadsheet_dashboard` depends `spreadsheet`; app `board` (CE) depends `spreadsheet_dashboard` và mount menu. Model: `spreadsheet.dashboard` + `spreadsheet.dashboard.group`.

## Hệ quả

- ✅ 0 dòng code dashboard. Dữ liệu lấy từ `sale.order`, `account.move`, `lab.chuyen`.
- ✅ Phân quyền native bằng field `group_ids` trên `spreadsheet.dashboard` → gán `lab_core.group_director`.
- ⚠️ Lưu trữ dùng `spreadsheet_binary_data` (Binary) + computed `spreadsheet_data` (Text) — **không có** `fields.Spreadsheet`; muốn nhúng vào model riêng thì inherit `spreadsheet.mixin`.
- ⚠️ Hành động "Insert in spreadsheet" từ pivot/list trong CE: **chưa xác nhận** → không thiết kế demo phụ thuộc vào nó.
- ⚠️ Phải kiểm tra hiển thị tiếng Việt + định dạng số `1.234,56` trong spreadsheet app.
- ❌ `web_dashboard` **không tồn tại** ở Odoo 14–17 → đừng plan.
