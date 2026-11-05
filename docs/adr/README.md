# ADR — Architecture Decision Records

Mỗi quyết định kiến trúc = 1 file. Cấu trúc: **Bối cảnh · Phương án · Quyết định · Hệ quả · Trạng thái**.
Trạng thái: `PROPOSED` → `ACCEPTED` → `SUPERSEDED` (bởi ADR khác).
Đề xuất: BE-01/FE-01 · Thẩm định: RV-01 · Duyệt: Orchestrator (người).

> **Vì sao phải ghi phương án bị loại:** sếp chấm **critical thinking**. Một ADR chỉ ghi "đã chọn X" là vô giá trị; ADR ghi "loại Y vì lý do Z, đã kiểm chứng bằng W" mới là bằng chứng.

## Danh sách

| ADR | Quyết định | Trạng thái | Ảnh hưởng |
|---|---|---|---|
| [ADR-001](ADR-001-lab-core.md) | Thêm module keo `lab_core` (3 module đề bài → 3 + 1 keo) | ✅ ACCEPTED | Toàn bộ cấu trúc module |
| [ADR-002](ADR-002-dashboard.md) | Dashboard = `spreadsheet_dashboard` (**đã kiểm chứng là CE**) | ✅ ACCEPTED | P8 |
| [ADR-003](ADR-003-chuyen-vs-batch.md) | `lab_chuyen` vs `stock_picking_batch` vs OCA `tms` | ✅ **ACCEPTED — chọn C (viết `lab_chuyen`)**, bằng chứng đọc source 17.0 | ⑤⑥ + `lab_laixe` |
| [ADR-004](ADR-004-lab-api.md) | `lab_api`: lai giữa JSON-RPC native và 4 endpoint custom | ✅ ACCEPTED | App KMP |
| [ADR-005](ADR-005-helpdesk-bridge.md) | Không phụ thuộc `helpdesk_mgmt`; dùng activity + `lab_ticket_ref` | ✅ ACCEPTED | Nhánh ⑥ |
| [ADR-006](ADR-006-material2-vs-3.md) | Material 2 stable thay vì Material 3 (alpha) trong CMP | ✅ ACCEPTED | Toàn bộ app KMP |

## ADR nào cần thêm (dự kiến)

| Chủ đề | Khi nào | Ai |
|---|---|---|
| Công thức cước: divisor 6000/1000, tính theo kg nào | P5 | BE-01 (gắn với OQ-01) |
| Offline sync: idempotency + xung đột | P3 | FE-01 |
| Cách ly dữ liệu tài xế: user Odoo vs token riêng | P3 | BE-01 (gắn OQ-14) |
| AI layer: intent set cố định hay LLM tự do | P9 | FE-01 |
| Portal: tự viết `portal.mixin` hay dùng OCA `stock_picking_portal` | P4 | BE-01 (gắn OQ-11) |

## Mẫu ADR ngắn

```markdown
# ADR-00x — <quyết định một dòng>

**Trạng thái:** PROPOSED · **Đề xuất:** <agent>

## Bối cảnh
<2-4 dòng: ràng buộc gì, vì sao phải quyết>

## Phương án
- **A.** ... (ưu / nhược)
- **B.** ... (ưu / nhược)

## Quyết định
**<A|B|C>** vì <lý do 1 dòng, có bằng chứng>.

## Hệ quả
- ✅ ...
- ⚠️ ...

## Bằng chứng đã kiểm chứng
- <đường dẫn file:dòng> / <branch OCA> / <lệnh đã chạy>
```
