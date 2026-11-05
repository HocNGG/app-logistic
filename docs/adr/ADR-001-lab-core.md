# ADR-001 — Module keo `lab_core`

**Trạng thái:** ACCEPTED · **Ngày:** 2026-10 · **Đề xuất:** BE-01 · **Thẩm định:** RV-01

## Bối cảnh

Đề bài yêu cầu **3 module**: `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh`. Nhưng cả 3 cùng cần: 8 nhóm quyền, sequence, mixin ngoại lệ, field mở rộng trên `res.partner`/`stock.picking`/`sale.order`, field `lab_chargeable_weight`, cấu hình công ty.

## Phương án

- **A.** Mỗi module tự khai báo groups/field riêng → **phụ thuộc vòng**, cài sai thứ tự, không upgrade được.
- **B.** Nhét tất cả vào `lab_chuyen` và để 2 module kia depends nó → sai ngữ nghĩa (module chuyến không phải "base").
- **C. Module keo `lab_core`** — không có model nghiệp vụ, chỉ groups + mixin + field mở rộng + sequence + settings.

## Quyết định

**C.** `lab_core` là module thứ 4 được cấp phép. Đây là **thực hành chuẩn Odoo**: `sale_stock`, `stock_delivery`, `account_payment` đều là module keo — tồn tại chính vì lý do này.

## Hệ quả

- ✅ Không phụ thuộc vòng; thứ tự cài: `lab_core` → `lab_chuyen` → `lab_laixe` → `lab_hanhtrinh` → `lab_api`.
- ✅ 8 nhóm quyền, sequence, mixin khai báo **một lần**.
- ⚠️ Con số "3 module" của đề bài thành 4–5 → **phải giải thích trong demo**, không giấu.
- **Quyết định bổ sung:** nếu cần giữ đúng "3 module" cho con số đẹp, **gộp `lab_api` vào `lab_core`** (controller không cần module riêng). Khi đó: 3 module nghiệp vụ + 1 module keo = 4.
- **Cách nói trong demo:** *"Ba module nghiệp vụ theo đề bài, cộng một module keo theo đúng convention của Odoo — vì nếu không có nó, ba module kia phải depends lẫn nhau."*
