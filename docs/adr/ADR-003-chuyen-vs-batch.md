# ADR-003 — `lab_chuyen` thay vì `stock_picking_batch` / OCA `tms`

**Trạng thái:** ✅ **ACCEPTED** (2026-10, sau bằng chứng đọc source) · **Đề xuất:** BE-01 · **Chốt:** Orchestrator · **Thẩm định:** RV-01
**Gỡ chặn:** issue `[P0]` của RV-01 (P0-baseline) — ADR này là Reuse Proof cho `lab_chuyen`.

## Bối cảnh

Sơ đồ nghiệp vụ khẳng định: *"Odoo không có khái niệm gom nhiều đơn của nhiều khách vào một chuyến bay/tàu"* → cần `lab_chuyen` (bước ⑤⑥).

Hai phương án tái sử dụng phải bị loại **bằng bằng chứng**, không bằng cảm giác:

- **A.** CE `stock_picking_batch` (`stock.picking.batch`) + field kế thừa
- **B.** OCA `tms` trong `OCA/stock-logistics-transport` nhánh 17.0

## Bằng chứng — phương án A: đọc source 17.0

Nguồn: `odoo/odoo@17.0` → `addons/stock_picking_batch/models/stock_picking_batch.py` (14.976 bytes) · `__manifest__.py` (CE, LGPL-3) · `models/stock_picking.py`, `models/stock_move.py`, `models/stock_move_line.py`.

**Field thực tế của `stock.picking.batch`:**
`name` · `user_id` · `company_id` · `picking_ids` · `allowed_picking_ids` · `move_ids` · `move_line_ids` · `state` · `picking_type_id` · `picking_type_code` (related) · `scheduled_date` · `is_wave` · `show_check_availability` · `show_allocation` · `show_set_qty_button` · `show_clear_qty_button` · `show_lots_text`

### 5 thứ của "chuyến" mà batch KHÔNG có

| Cần cho "chuyến" | `stock.picking.batch` | Hệ quả nếu dùng A |
|---|---|---|
| **Sức chứa** (kg / CBM) | ❌ không có field nào | Không làm được bước ⑥ "duyệt theo tải trọng", không có thanh tải 120% |
| **Cut-off datetime** | ❌ chỉ có `scheduled_date` (ngày dự kiến) | Không cảnh báo "còn 4h chốt chuyến" |
| **Loại vận chuyển air/sea** | ❌ không có | Không chọn được hệ số quy đổi thể tích 6000/1000 (bước ⑫) |
| **Số booking / hãng vận chuyển** | ❌ không có | Chứng từ ⑧ (booking, AWB/HBL) không có chỗ neo |
| **Trạng thái từng đơn trong chuyến** (`booked`/`waitlist`/`removed`) | ❌ chỉ có m2o `picking_ids` | **Nhánh ② (giảm tải) không làm được**: gỡ đơn = xoá khỏi m2o, mất dấu |

### 3 ràng buộc kỹ thuật làm A càng không phù hợp

1. **`state` là computed + store**, tự suy ra từ picking:
   `_compute_state()` chỉ sinh `draft` / `in_progress` / `done` / `cancel`. Không thể đặt `departed` / `arrived` / `delivered` theo nghiệp vụ (bước ⑨⑩⑪) vì mỗi lần compute sẽ bị ghi đè.
2. **`allowed_picking_ids` lọc theo `picking_type_id` bằng nhau**:
   ```python
   domain = [('company_id','=',...), ('state','in',domain_states)]
   if batch.picking_type_id:
       domain += [('picking_type_id','=',batch.picking_type_id.id)]
   ```
   ⇒ Một batch chỉ chứa picking **cùng operation type**. Gom hàng lẻ từ nhiều shop/loại hàng rất dễ khác operation type → phải nới domain (sửa core) hoặc ép dữ liệu (bẩn).
3. ⚠️ **`action_cancel()` xoá sạch liên kết**: `self.picking_ids = False`.
   Đây đúng là phản mẫu mà `AGENTS.md` Luật 5 cấm ("gỡ đơn phải giữ vết"). Dùng batch làm chuyến thì thao tác huỷ sẽ **làm mất toàn bộ lịch sử gom**.

### Điểm duy nhất A làm được: giới hạn theo **số lượng**, không theo khối lượng

`stock.picking.type` có `batch_max_lines` + `batch_max_pickings`, dùng trong `_is_picking_auto_mergeable()`. Đây là **đếm số dòng/số picking**, không phải kg/CBM → không dùng cho tải trọng hàng không/đường biển.

## Bằng chứng — phương án B: OCA `tms`

Đọc `__manifest__.py` nhánh 17.0 của `OCA/stock-logistics-transport`:

| Thuộc tính | Giá trị | Đánh giá |
|---|---|---|
| version | `17.0.1.1.1` | ok |
| license | **AGPL-3** | ảnh hưởng phân phối |
| `development_status` | **Alpha** | ⚠️ API còn đổi, chưa đủ test — rủi ro cao cho demo |
| mô hình | `tms.order` / `tms.route` / `tms.stage` / `tms.crew` / `tms.driver` | **trucking theo tuyến**, không phải **consolidation theo chuyến bay/tàu** |

⇒ B vừa Alpha vừa lệch mô hình ngành → loại.

## Quyết định

**Phương án C — viết `lab_chuyen`** (module thứ nhất trong 3 module được cấp phép).

Không phải vì "thích viết code", mà vì:
- A thiếu **5/5** thuộc tính cốt lõi và có 3 ràng buộc kỹ thuật chặn (một trong đó gây mất dữ liệu)
- B là **Alpha** + sai mô hình ngành

Đây là L3 hợp lệ theo `docs/01` §1: đã loại L0 (native), L1 (config), L2 (inherit) bằng bằng chứng ở trên.

## Hệ quả

- ✅ `lab_chuyen` sở hữu: `tai_trong_max_kg`, `the_tich_max_cbm`, `cut_off_datetime`, `loai_van_chuyen`, `so_booking`, `state` 7 mốc, và **`lab.chuyen.line`** (đơn trong chuyến) có `state` riêng `booked`/`waitlist`/`removed` + `ly_do_go` ⇒ nhánh ② làm được đúng.
- ✅ `stock_picking_batch` **không cài** (tránh trùng khái niệm và tránh menu gây nhầm cho người dùng).
- ⚠️ **Không** dùng `action_cancel` kiểu batch; huỷ chuyến phải chuyển từng line sang `cancelled` và giữ vết.
- ⚠️ `lab_laixe` vẫn là L3 (kế hoạch không đổi): `tms.driver` không dùng được vì B bị loại; `hr.employee` + `fleet.vehicle` là native tái sử dụng.
- 📌 **Việc còn lại (không chặn P0):** khi người dùng vendor OCA xong, chạy lại lệnh kiểm trong `docs/adr/ADR-003-evidence-needed.md` để đóng dấu "đã đối chiếu trên code local" — nhưng **quyết định không đổi** vì field list ở trên đã đủ.

## Bằng chứng tái lập

```bash
# field thật của stock.picking.batch (không cần cài gì)
docker run --rm odoo:17.0 cat /usr/lib/python3/dist-packages/odoo/addons/stock_picking_batch/models/stock_picking_batch.py | grep -E "^\s+\w+ = fields"
# hoặc
curl -s https://raw.githubusercontent.com/odoo/odoo/17.0/addons/stock_picking_batch/models/stock_picking_batch.py | grep -c "fields\."
```

Dòng quan trọng cần thấy (để tự kiểm chứng kết luận trên):
```python
def action_cancel(self):
    self.state = 'cancel'
    self.picking_ids = False          # ← xoá liên kết, đây là lý do loại A
```
