# ADR-005 — Cầu nối `helpdesk_mgmt` ↔ `lab_hanhtrinh`

**Trạng thái:** ACCEPTED · **Đề xuất:** BE-01

## Bối cảnh

Nhánh ngoại lệ ⑥ (khiếu nại · trả hàng) dùng `helpdesk_mgmt` (OCA/helpdesk). Nhưng `lab_hanhtrinh` không nên depends `helpdesk_mgmt`: giữ module nghiệp vụ độc lập, tránh kéo OCA vào core, và không được sửa code OCA.

## Phương án

- **A.** `lab_hanhtrinh` depends `helpdesk_mgmt` → ràng buộc cứng, thứ tự cài phức tạp, module nghiệp vụ không cài được nếu thiếu OCA.
- **B.** Thêm module `lab_helpdesk_bridge` → thêm 1 module nữa (vi phạm tinh thần "ít module").
- **C.** Không phụ thuộc: dùng `mail.activity` type "Khiếu nại" + `lab.hanh.trinh.stage='exception'`, và nếu `helpdesk_mgmt` có cài thì lưu **số ticket** vào field Char.

## Quyết định

**C.** `lab_hanhtrinh` giữ `stage = 'exception'` + mở `mail.activity` type "Khiếu nại"; field `lab_ticket_ref` (Char) để ghi số ticket nếu OCA có cài.

## Hệ quả

- ✅ `lab_hanhtrinh` cài được mà **không cần** OCA.
- ✅ Không module nào phải depends OCA (trừ nơi thật sự dùng: `base_tier_validation`).
- ⚠️ Mất liên kết **mạnh** (không có m2o tới `helpdesk.ticket`) → không bấm từ ticket sang hành trình được. Chấp nhận cho bài demo; ghi backlog.
- **Nếu Orchestrator muốn liên kết mạnh** → làm module `lab_helpdesk_bridge` (~20 dòng: `_inherit = 'helpdesk.ticket'` thêm `lab_hanhtrinh_id`) và **supersede ADR này**. Điều kiện: OCA đã vendor xong ở P0.
- **Cách demo:** mở ticket từ menu Helpdesk, ghi số ticket vào hành trình, đóng ticket → hành trình chuyển `closed`. Vẫn kể được câu chuyện "có quy trình khiếu nại", không cần liên kết cứng.
