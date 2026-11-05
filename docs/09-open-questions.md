# 09 — CÂU HỎI MỞ & GIẢ ĐỊNH (Open Questions)

> **Đây là bằng chứng của "critical thinking" (I-12).** Mỗi câu hỏi phải có: giả định tạm, mức chắc chắn, ai chịu trách nhiệm, và **cách kiểm chứng**.
> Quy tắc: có câu hỏi mở **không** được dừng việc — dùng giả định tạm, đánh dấu `⚠️ ASSUMPTION` trong code, tiếp tục phần không bị chặn.
> Mức chắc chắn: **H** = đã chạy thật · **M** = đọc source/docs chính thức · **L** = suy luận.

| ID | Câu hỏi | Giả định tạm | Mức | Ai | Kiểm chứng bằng | Trạng thái |
|---|---|---|---|---|---|---|
| OQ-01 | Cước tính theo `max(kg thực, kg thể tích)` hay chỉ kg thực? Hệ số chia bao nhiêu? | `max(actual, volumetric)`; hệ số **6000** cho air, **1000** cho sea | L | BE-01 | Cấu hình được trên `product.template.lab_volumetric_divisor`; test 4 case air/sea × nhẹ/nặng | Mở |
| OQ-02 | Có bao nhiêu kho thật? "KHO CHỜ" là warehouse hay location? | 2 warehouse (KHO BAY, KHO TÀU) + 1 (KHO CHÍNH); KHO CHỜ là **location** trong KHO CHÍNH | L | BE-01 | Báo cáo tồn kho gộp đúng khi KHO CHỜ là location | Mở |
| OQ-03 | Debit dùng `move_type` gì? `out_invoice` hay `out_refund`/entry? | `out_invoice` trên journal riêng tên "Debit" | M | BE-01 | Hoá đơn in ra đúng mẫu, đối soát được với payment | Mở |
| OQ-04 | Ai được mở khoá Debit? Có cần 2 cấp không? | Chỉ `lab_core.group_manager` + **bắt buộc nhập lý do** + ghi chatter | L | BE-01 + RV | Test: user kế toán không mở khoá được | Mở |
| OQ-05 | Đơn bị gỡ khỏi chuyến nên `unlink` hay `state='waitlist'`? | **`waitlist`** — không bao giờ unlink (mất audit trail) | H | BE-01 | `test_giam_tai.py` khẳng định line vẫn tồn tại | **Đã chốt** |
| OQ-06 | Phiên bản Odoo có phải **Community** (không EE)? | Community 17.0; không dùng `sign`, `studio`, `documents`, `helpdesk` EE | M | Orchestrator | Cài từ odoo/odoo@17.0 + addons/oca, không có module EE | Mở |
| OQ-07 | Có cần multi-company không? | **Không** — 1 công ty, nhiều địa chỉ (đại lý nước ngoài là `res.partner`) | L | Orchestrator | Không có field `company_id` thủ công trong model custom | Mở |
| OQ-08 | Hoá đơn điện tử VN (MISA/VNPT/Viettel) có nằm trong phạm vi? | **Ngoài phạm vi**. `account.move` đủ dữ liệu để tích hợp sau | M | Orchestrator | Ghi trong backlog `docs/03` §7 | **Đã chốt: backlog** |
| OQ-09 | Khiếu nại dùng `helpdesk_mgmt` (OCA) hay `project.task`? | `helpdesk_mgmt` (OCA/helpdesk, nhánh 17.0 có thật). `lab_hanhtrinh` **không** depends nó | M | BE-01 | Ticket mở được từ hành trình; nếu không cài được → ADR-005 dùng activity | **Đã chốt (ADR-005 — activity + `lab_ticket_ref`)** |
| OQ-10 | Có nên dùng OCA `tms` (OCA/stock-logistics-transport) thay vì `lab_chuyen`/`lab_laixe`? | **Chưa** — `tms` là trucking theo tuyến, không phải consolidation theo chuyến bay/tàu. Phải đọc code trước khi chốt | M | BE-01 | ADR-003 có kết luận rõ ràng dù chọn hay không | **Bắt buộc chốt ở P0** |
| OQ-11 | `stock_picking_portal` (OCA) có sẵn trang tracking bằng token không? | **Chưa rõ** — phải đọc code; nếu có thì dùng, nếu không thì tự thêm `portal.mixin` + controller | L | BE-01 | Đọc `addons/oca/stock_picking_portal/**` | Mở |
| OQ-12 | App KMP dùng Material 2 (stable) hay Material 3 (alpha)? | **Material 2 stable** + `material3.adaptive` cho adaptive info | H | FE-01 | ADR-006: build không vỡ khi update | **Đã chốt** |
| OQ-13 | Cửa sổ hẹp nhất phải hỗ trợ là bao nhiêu dp? | **< 900dp = overlay 1 pane**; hẹp nhất 700dp vẫn dùng được | L | FE-01 | Video resize 1600→700→1600 không mất state | Mở |
| OQ-14 | Tài xế đăng nhập bằng gì? Tài khoản Odoo hay token riêng? | Tài khoản `res.users` thuộc `lab_core.group_driver` **+ API key**; endpoint custom trả token 8h | M | BE-01 + FE-01 | Test: tài xế không vào được backend web | **Đã chốt (ADR-004 — API key + token custom 8h)** |
| OQ-15 | Có cần quay video trước ngày demo không? | **Bắt buộc** — video xuất trước ≥ 24h, là lớp dự phòng cuối | H | RV-01 | Checklist T-24h trong `docs/06` | **Đã chốt** |
| OQ-16 | "REA" sếp nói là mô hình Resources-Events-Agents hay viết tắt khác? | **REA accounting ontology** (Resources–Events–Agents) — đúng ngữ cảnh ERP/kế toán | L | Orchestrator | Ánh xạ REA → chứng từ Odoo (`docs/02` §11) + `test_rea_completeness.py` | Mở (xác nhận với sếp) |
| OQ-17 | "iPhone 18 duo UI" = layout 2 pane thích ứng? | **Đúng** — iPhone Duo (09/2026) có 2 màn hình, arrangement split/overlay, reserved regions | M | FE-01 | 3 ngưỡng + test displacement (`docs/04` §4) | **Đã chốt** |
| OQ-18 | Ngôn ngữ thứ 2 của app có cần tiếng Anh? | Có (i18n vi/en) nhưng **tiếng Việt là mặc định** | L | FE-01 | Đổi ngôn ngữ không tràn layout | Mở |

---

## Cách dùng file này

1. **BE-01/FE-01:** trước khi code một phần phụ thuộc câu hỏi mở → tra bảng, dùng giả định tạm, **đánh dấu `⚠️ ASSUMPTION(L): OQ-xx` trong code comment**.
2. **RV-01:** mỗi sprint kiểm tra: có câu hỏi mở nào **đã được giải quyết bằng code mà không cập nhật bảng** không? Nếu có → P1.
3. **Orchestrator:** câu hỏi nào cần hỏi khách hàng thật → gom vào 1 email, không hỏi lắt nhắt.

## Ba câu hỏi quan trọng nhất (nếu chỉ được hỏi 3)

1. **OQ-01** — công thức cước. Sai công thức = sai toàn bộ tiền của dự án.
2. **OQ-10** — có dùng OCA `tms` không. Ảnh hưởng trực tiếp việc có phải viết `lab_chuyen`, `lab_laixe`.
3. **OQ-04** — ai được mở khoá Debit. Đây là điểm kiểm soát gian lận.

---

## Những câu hỏi **đã được trả lời** bằng kiểm chứng (giữ lại làm bằng chứng)

| Câu hỏi cũ | Kết luận đã kiểm chứng | Nguồn |
|---|---|---|
| `spreadsheet_dashboard` có trong CE 17 không? | **Có** — LGPL-3, CE; `board` app depends nó | `addons/spreadsheet_dashboard/__manifest__.py` |
| `base_tier_validation` ở repo nào? | **`OCA/server-ux`**, không phải `server-tools` | OCA branch 17.0 |
| Model kiện hàng tên gì ở 17? | **`stock.quant.package`** (`stock.package` là tên 18.0) | `addons/stock/models/stock_picking.py` |
| `attrs` còn dùng được không? | **Không** — raise `ValidationError` từ 17.0 | `ir_ui_view._check_xml` |
| Done qty field tên gì? | **`quantity`** + cờ `picked` | `stock_move.py`, `stock_move_line.py` |
| Odoo 17 có Bearer auth? | **Không** — API key thay password; Bearer là 19.0 | `odoo/http.py` 17.0, `/json/2` ở 19.0 |
| `stock.move.weight` có sẵn? | Chỉ khi cài **`stock_delivery`** | `addons/stock_delivery/models/stock_move.py` |
| Có route `/my/<model>/<id>` tổng quát? | **Không** — dùng `/mail/view?model=&res_id=&access_token=` | `portal_mixin.py` |
| Có method `_check_tier_validation` để override không? | **KHÔNG** ở `base_tier_validation` 17.0.4.1.0. Hook thật: `_check_state_conditions(vals)`, `_validated_states()`, `evaluate_tier()`, `_get_under_validation_exceptions()` | Đọc `base_tier_validation/models/tier_validation.py` |
| OCA `tms` ở 17.0 chín đến đâu? | version **17.0.1.1.1**, license **AGPL-3**, `development_status` = **Alpha** → rủi ro cao cho demo | `__manifest__.py` của `tms` |
| `stock_quant_package_dimension` cho filter theo thể tích được không? | `volume` là **non-stored** → muốn filter/group phải **re-declare `store=True`** bằng `_inherit` trong module của mình, **không** sửa vendor | đọc model OCA |
| `helpdesk_mgmt` / `stock_picking_invoice_link` có thật ở 17.0? | **Có** (branch 17.0 tồn tại) — nhưng mới xác nhận *tồn tại*, **chưa** review độ phù hợp → vẫn là quyết định của ADR-005 | branch OCA 17.0 |
| OCA `stock-logistics-transport` có gì? | Bộ **`tms`**: `tms.order/route/stage/team/crew/driver/insurance` | repo 17.0 |

---

## RV-01 · cập nhật ở P0 baseline (2026-10-28)

> Việc này nằm trong quyền ghi của RV-01 (`AGENTS.md` Luật 10). Full verdict + bằng chứng: `docs/review/P0-baseline.md`.

**Đã sửa hôm nay:**

1. `OQ-09` → **Đã chốt (ADR-005)**: `lab_hanhtrinh` không depends `helpdesk_mgmt`; dùng `mail.activity` type "Khiếu nại" + `lab_ticket_ref` (Char) + `lab.hanh.trinh.stage = 'exception'`.
2. `OQ-14` → **Đã chốt (ADR-004)**: tài xế là `res.users` thuộc `lab_core.group_driver` + **API key**; endpoint `/lab/api/v1/*` trả token custom 8h (hash trong DB).
3. Tách **1 dòng bảng bị gộp** ở mục "Những câu hỏi đã được trả lời" (dòng cũ có `||` → mất 1 câu trả lời khi render).

**Còn để Orchestrator quyết (RV không tự đổi để khỏi khai khống mức chắc chắn):**

- `OQ-06` (Community hay Enterprise): bằng chứng **cấu hình** đã có — `infra/docker-compose.yml:19` dùng image `odoo:17.0` (image official = CE) — nhưng **chưa chạy thật** (Docker tắt). Đề nghị: sau lần login đầu tiên, đổi trạng thái sang "Đã chốt (cấu hình)" và giữ mức `M`.
- `OQ-10` (`tms`): **vẫn mở** và **đang chặn P0** — ADR-003 chưa chốt (xem `docs/review/P0-baseline.md` P1-04). Giữ nguyên trạng thái "Bắt buộc chốt ở P0" là đúng.

**Giới hạn chuỗi bằng chứng (phải nói rõ, không giấu):**

Cột "Nguồn" ở bảng trên dẫn tới **source Odoo 17** (`addons/stock/models/...`, `odoo/http.py`), nhưng **workspace này không chứa source Odoo** (`addons/` rỗng, không có `odoo/`). Hệ quả: những kết luận mức **`H`/`M`** ở trên là **di sản của phiên trước**, hiện **không tái lập được offline**. Cách vá (một lần, ~5 phút, ghi vào file này):

```powershell
# khi đã có image (Docker Desktop bật)
docker run --rm odoo:17.0 sh -c "grep -rn 'Since 17.0, the attrs and states' /usr/lib/python3/dist-packages/odoo/addons/base/models/ir_ui_view.py"
docker run --rm odoo:17.0 sh -c "grep -n 'def read_group\|def _read_group' /usr/lib/python3/dist-packages/odoo/models.py"
docker run --rm odoo:17.0 sh -c "ls /usr/lib/python3/dist-packages/odoo/addons | grep -E 'spreadsheet|stock_quant_package'"
```

Dán output vào bảng trên (cột "Nguồn") rồi mới được dùng lại các dòng đó làm bằng chứng trong verdict.
| OQ-19 | Định dạng số chứng từ: năm 2 hay 4 chữ số? | `%(y)s` của `ir.sequence` — **giả định 4 chữ số** (`CH/AIR/2026/0001`); nếu ra 2 chữ số thì đổi prefix, không đổi nghiệp vụ | L | BE-01 | Chạy 1 lần tạo chuyến ở P1 rồi xem số | Mở |
| OQ-20 | `lab.pickup.line` có tồn tại không? | **Có trong docs/00 §6 + whitelist nhưng docs/02 §2.3 không định nghĩa field** → **P0 không tạo**, chốt ở P3 khi làm app tài xế nhiều điểm lấy | L | BE-01 + FE-01 | Nếu app chỉ lấy 1 điểm/đơn thì bỏ model này và gỡ khỏi whitelist | Mở |
| OQ-21 | Có vendor source Odoo 17 vào repo để thi hành Luật 1 ("grep addons/ trước")? | **Nên có ở P1**: clone `odoo/odoo@17.0` (hoặc chỉ `addons/`) vào `infra/odoo/addons` để recon chạy offline | M | Orchestrator | `Test-Path infra/odoo/addons` = true + grep chạy được | Mở |
| OQ-22 | App can **so kien du kien** nhung `lab.yeu.cau.lay.hang` chi co `so_kien_thuc_te` (Integer) | Uu tien lay tu `sale.order`/`stock.picking` qua API; neu khong co thi **BE bo sung** `so_kien_du_kien` o P3. App **khong tu bia so** | L | BE-01 + FE-01 | `DriverJob.expectedPackages` co nguon that + test API | Mo |
| OQ-23 | `khung_gio_tu`/`khung_gio_den` la `Datetime` nhung app can `HH:mm` | **API format san** `windowFrom`/`windowTo` dang String `HH:mm`; app khong parse Datetime | H | BE-01 | Payload `/lab/api/v1/driver/jobs` tra `08:30`, khong phai ISO | Mo |
