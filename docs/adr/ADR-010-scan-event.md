# ADR-010 — Nhật ký quét tem (`lab.scan.event`)

**Ngày**: 2026-11-02 · **Trạng thái**: Đã duyệt (khách chốt "bắt đầu")
**Người đề xuất**: BE-01 · **Module**: `lab_chuyen` (không tạo module mới)

## Bối cảnh

Nghiệp vụ chốt 2026-11-02: tem **chỉ sinh tại kho**. Kho in tem nhận khi hàng về, quét tem
nhận; khi xuất chuyến kho in tem xuất (mỗi kiện một tem) và quét tem xuất lúc hàng rời kho.

Cần trả lời được: **ai quét, lúc nào, ở mốc nào, bằng thiết bị gì, kết quả ra sao** — để
có bảng tổng kết (tỉ lệ quét đúng lần đầu, số kiện lệch, cycle time 3 mốc) và để chống
trùng khi mạng chập (app gửi lại cùng một lần quét).

## Quyết định

1. Thêm **một** model mới `lab.scan.event` trong module `lab_chuyen` (append-only).
2. Model này **không thay thế chứng từ nào**: sự kiện kinh tế vẫn do `stock.picking`
   (`WH/IN`, `WH/OUT`) làm chứng từ theo AGENTS.md Luật 4. `lab.scan.event` chỉ là **nhật ký
   xác nhận** (audit + số liệu), đúng loại thông tin không có chỗ trong chứng từ kho.
3. Không cấp quyền `unlink` cho bất kỳ nhóm nghiệp vụ nào (audit không xoá được).
4. Chống trùng bằng `client_uuid` **unique**; gửi lại cùng UUID ⇒ trả kết quả cũ,
   `trung=true`, không tạo dòng mới.
5. Kiện vẫn là **`stock.quant.package`** (native). Nhật ký trỏ tới kiện, phiếu kho, người quét.

## Hệ quả

- Có dữ liệu để dựng bảng tổng kết tem/kiện mà không phải đọc `mail_message`.
- Thêm 1 bảng nhỏ (ước tính < 10 dòng/kiện) — chấp nhận được cho quy mô demo.
- Nếu sau này bỏ: dữ liệu audit mất, chứng từ kho KHÔNG ảnh hưởng (đã tách tuyệt đối).
