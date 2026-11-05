# ADR-003 — BẰNG CHỨNG CẦN THU THẬP (chưa phải quyết định)

> **Trạng thái:** `EVIDENCE-NEEDED` — tài liệu này **không** chốt phương án, **không** thay `ADR-003-chuyen-vs-batch.md`.
> **Người lập:** BE-01 · **Người thẩm định:** RV-01 · **Ngày:** 2026-10-28
> **Chặn bởi:** chưa đọc được code OCA `tms` + chưa chạy được Odoo để soi `stock_picking_batch` thật.

---

## 0. Vì sao chưa chốt được (và vì sao đây không phải "làm biếng")

| Việc cần làm để chốt ADR | Trạng thái trong phiên này | Lý do |
|---|---|---|
| Đọc `addons/stock_picking_batch/models/*.py` (Odoo 17 CE) | ❌ **không làm được** | Repo **không có** source Odoo. `infra/odoo/addons` (đường dẫn ở `AGENTS.md` §9) **không tồn tại** trong workspace → không có gì để grep. |
| Đọc `addons/oca/.../tms/models/*.py` (OCA 17.0) | ❌ **không làm được** | `addons/oca` rỗng (chưa vendor) **và** shell của agent không có mạng ra github.com. |
| Bật Odoo để `env['stock.picking.batch']._fields` | ❌ **không làm được** | Docker Desktop chưa bật → không có container, không có `odoo shell`, không có DB. |

Vì vậy tài liệu này là **danh sách kiểm tra chính xác** + **lệnh copy-paste được** để người dùng chạy. Mọi kết luận phải dựa trên output thật, không dựa trên trí nhớ (Luật 2: "không code theo trí nhớ"; ADR-003 §"Ghi chú cho người review": chọn C mà không có mục đã đọc A và B → RV-01 BLOCKED).

**Hai điều đã biết chắc từ tài liệu FROZEN (bằng chứng hạng M, không phải H):**

1. `stock_picking_batch` là module **CE 17.0** — gom nhiều `stock.picking` **của cùng một công ty** để xử lý một lượt (skill `01-native-first-recon.md` §5.2).
2. `tms` (repo `OCA/stock-logistics-transport`, nhánh 17.0): version **17.0.1.1.1**, license **AGPL-3**, `development_status: **Alpha**`; model `tms.order/route/stage/team/crew/driver/insurance`; kéo theo `tms_sale`, `tms_purchase`, `tms_account`, `tms_expense`, `tms_product`, `shipment_advice`, `stock_depot`, `stock_dock` (`docs/01` §3.2, `docs/09` bảng "đã kiểm chứng", skill `01` §5.2).
   ⚠️ Đây là số liệu **do tài liệu dự án ghi lại**, chưa phải output của phiên này → vẫn phải kiểm lại ở §3.

---

## 1. Điều kiện tiên quyết (chạy đúng thứ tự)

```powershell
# (1) Bật môi trường — Docker Desktop phải đang chạy
docker compose -f infra/docker-compose.yml up -d

# (2) Vendor OCA + clone thêm bộ tms CHỈ ĐỂ ĐỌC (không copy tms ra addons/oca)
powershell -ExecutionPolicy Bypass -File infra/scripts/vendor-oca.ps1 -IncludeTms

# (3) Xác nhận đã có source tms trên đĩa
Get-ChildItem addons\oca\_repos\OCA-stock-logistics-transport\tms\models
```

> `vendor-oca.ps1` **không** cài `tms`. Nó chỉ clone `OCA/stock-logistics-transport` vào `addons/oca/_repos/` để đọc code. Muốn cài thật thì phải là quyết định riêng của ADR-003.

---

## 2. Cần kiểm `stock_picking_batch` (Odoo 17 CE) — 9 mục

**Đường dẫn gốc trong container:** `/usr/lib/python3/dist-packages/odoo/addons/stock_picking_batch/`
**File dự kiến:** `models/stock_picking_batch.py`, `models/stock_picking.py`, `__manifest__.py`, `security/ir.model.access.csv`, `views/*.xml`

| # | Câu hỏi quyết định | Kiểm ở đâu / bằng gì | A **đủ** nếu | Ghi bằng chứng (file:dòng) |
|---|---|---|---|---|
| B1 | `stock.picking.batch` có **field gì**? | `env['stock.picking.batch']._fields` (lệnh §4.2) | danh sách field ngắn, không có gì đụng tải trọng | |
| B2 | Có **sức chứa** (kg / CBM) không? | `rg "weight\|volume\|cbm\|kg" models/` | **không có** → phải thêm ≥2 field | |
| B3 | Có **hạn chốt hàng (cut-off)** không? | `rg -i "cut_?off\|deadline\|must_be_done" models/` | **không có** → thêm 1 field datetime | |
| B4 | Có phân biệt **đường bay / đường biển** không? | `rg "fields\.Selection" models/` + đọc `state`/loại | **không có** → thêm 1 selection | |
| B5 | **State machine** gồm những state nào? Có chỗ cho "chờ chuyến sau" không? | `env['stock.picking.batch']._fields['state'].selection` | chỉ `draft/done/cancel` và **không** cần waitlist | |
| B6 | Có biểu diễn **nhiều khách trên một chuyến** không? (`partner_id`? `sale_ids`?) | `rg "partner_id\|sale_id\|sale_order" models/` | không có `partner_id` **nhưng** `picking_ids` gián tiếp đủ để lọc theo khách | |
| B7 | **Ràng buộc với picking**: batch có **ghi đè** hành vi validate của `stock.picking` không (`button_validate`, `_action_done`, `action_assign`)? | `rg "def (button_validate\|_action_done\|action_assign\|_check_company)" models/` | **không** ghi đè → an toàn khi kế thừa | |
| B8 | Có hook **tier validation** sẵn không? | `rg -i "tier\|_inherit" models/ __manifest__.py` | không có → tự thêm (P2-05) | |
| B9 | **Chi phí kế thừa**: nếu thêm 5 field + 1 state vào batch, có phải sửa view của `stock` không? | đọc `views/stock_picking_batch_views.xml` + đếm XPath cần chèn | ≤ 4 field + 1 state, không phải sửa view của module khác | |

**Ngưỡng đọc kết quả (theo gợi ý của RV-01 trong ADR-003 gốc, không phải quyết định của BE-01):**
- Nếu **B2, B3, B4** đều "không có" và **B9** ≤ 4 field + 1 state → phương án **A** (batch + inherit) đủ.
- Nếu cần **waitlist**, **state riêng của chuyến**, **manifest theo chuyến**, hoặc phải sửa hành vi `stock.picking` → phương án **C** (`lab_chuyen`), vì "chuyến" có đời sống riêng khác chứng từ kho.

---

## 3. Cần kiểm OCA `tms` — 6 mục

**Đường dẫn gốc (sau khi chạy §1):** `addons\oca\_repos\OCA-stock-logistics-transport\`

| # | Câu hỏi quyết định | Kiểm ở đâu / bằng gì | Kết luận nếu… |
|---|---|---|---|
| T1 | `__manifest__.py` nói gì về **version / license / tình trạng phát triển / depends**? | `Get-Content ...\tms\__manifest__.py` | (`development_status: Alpha` + AGPL-3 ⇒ rủi ro cao cho demo, **không** tự động loại) |
| T2 | `tms.route` / `tms.order` có biểu diễn được **nhiều `sale.order` của nhiều khách trên MỘT chuyến bay/tàu** không? Hay chỉ **1 xe · 1 tuyến · 1 ngày**? | `rg "sale_id\|partner_id\|picking_id\|route_id\|truck" ...\tms\models\tms_order.py` + đọc class `_name` | nếu chỉ trucking theo tuyến ⇒ không dùng được cho ⑤ |
| T3 | `tms.driver` + `fleet.vehicle` có đủ cho `lab_laixe`? Cần: **ảnh, chữ ký, xác nhận offline, token app, danh sách job trong ngày** | `rg "attachment\|image\|signature\|sign\|token\|offline" ...\tms\models\tms_driver.py` | nếu **đủ phần điều phối** ⇒ hạ `lab_laixe` xuống **L2** (chỉ thêm field ảnh/chữ ký) |
| T4 | `tms` kéo theo **bao nhiêu module** (trực tiếp + gián tiếp)? Có làm nặng bản demo (thời gian cài, menu rối, seed data) không? | `rg "^\s*\"depends\"" -A 20 ...\tms\__manifest__.py` + đọc `tms_sale/tms_account` manifest | cài > 8 module phụ ⇒ nặng cho demo 3 phút |
| T5 | Có **test** không? Bao nhiêu? | `Get-ChildItem ...\tms\tests -Recurse` | 0 test ⇒ rủi ro Alpha càng lớn |
| T6 | `stock_depot` / `stock_dock` / `shipment_advice` có giúp được **nhánh ⑤ (chuyển kho bay ↔ tàu)** không? | đọc `__manifest__.py` + `models/` của 3 module đó | nếu có → có thể tái dùng cho ⑤ (L0/L1) |

**Ghi chú pháp lý cần ghi vào ADR (không phải quyết định kỹ thuật):** AGPL-3 là copyleft **theo mạng**; nếu sau này sản phẩm phân phối/dịch vụ hoá phần mềm có `tms` thì nghĩa vụ công bố source khác với LGPL-3 hiện tại của các module `lab_*`. Đây là lý do **thứ hai** (sau Alpha) để nghiêng về C — nhưng RV-01/Orchestrator phải xác nhận.

---

## 4. Lệnh chạy (copy-paste được, dùng `docker compose exec`)

### 4.1 Liệt kê file trong hai gói — kiểm nhanh tên model/file

```powershell
# stock_picking_batch (Odoo core 17.0 trong image)
docker compose -f infra/docker-compose.yml exec odoo bash -lc "ls -R /usr/lib/python3/dist-packages/odoo/addons/stock_picking_batch | head -60"

# tms (OCA, đã clone ở bước §1) — đọc trên HOST là nhanh nhất
Get-ChildItem addons\oca\_repos\OCA-stock-logistics-transport\tms -Recurse -File -Filter *.py |
  Select-Object -ExpandProperty FullName
```

### 4.2 Bằng chứng mạnh nhất: hỏi thẳng ORM (không đoán theo tên file)

```powershell
docker compose -f infra/docker-compose.yml exec odoo `
  odoo shell -c /etc/odoo/odoo.conf -d odoo17_lab --no-http
```

Dán nguyên khối sau vào shell (in ra toàn bộ bằng chứng cho §2):

```python
# --- B1: field inventory ---
b = env['stock.picking.batch']
print(sorted(b._fields.keys()))

# --- B5: state machine ---
print(b._fields['state'].selection)

# --- B2/B3/B4: capacity / cut-off / air-sea ---
for k in sorted(b._fields):
    if any(t in k for t in ('weight', 'volume', 'cbm', 'kg', 'date', 'time', 'type', 'carrier', 'partner')):
        f = b._fields[k]
        print(k, '|', f.type, '|', getattr(f, 'string', ''), '|', getattr(f, 'selection', ''))

# --- B6: quan hệ tới khách / đơn hàng ---
print('partner_id' in b._fields, 'sale_id' in b._fields, 'picking_ids' in b._fields)

# --- B7: ghi đè hành vi picking? ---
from odoo.addons.stock_picking_batch.models import stock_picking_batch as m
import inspect
print([n for n, _ in inspect.getmembers(m, inspect.isclass)])
print(inspect.getsource(b.__class__)[:4000])

# --- kiểm chéo: picking có batch_id và có bị đổi hành vi validate không ---
p = env['stock.picking']
print('batch_id' in p._fields, p._fields['batch_id'].comodel_name if 'batch_id' in p._fields else None)
print(p._fields['state'].selection)
```

Thoát shell: `exit()` rồi Enter.

### 4.3 Đọc `tms` trên host (không cần Docker)

```powershell
$tms = "addons\oca\_repos\OCA-stock-logistics-transport\tms"
Get-Content "$tms\__manifest__.py"                                  # T1 + T4
Select-String -Path "$tms\models\*.py" -Pattern "_name\s*="        # danh sách model
Select-String -Path "$tms\models\*.py" -Pattern "sale_id|partner_id|picking_id|route_id"   # T2
Select-String -Path "$tms\models\*.py" -Pattern "signature|attachment|image|token"          # T3
Select-String -Path "$tms\models\*.py" -Pattern "fields\.(Float|Integer|Selection)"         # sức chứa/state
Get-ChildItem "$tms\tests" -Recurse -File -Filter *.py                                      # T5
```

### 4.4 Kiểm `stock_quant_package_dimension` (dùng cho cước thể tích — P5)

```powershell
# docs/09 đã ghi: field `volume` là NON-STORED -> muốn filter/group phải re-declare store=True
powershell -File infra/scripts/vendor-oca.ps1    # nếu chưa vendor stock-logistics-workflow thì clone tay:
git clone --depth 1 --branch 17.0 https://github.com/OCA/stock-logistics-workflow.git addons\oca\_repos\OCA-stock-logistics-workflow
Select-String -Path "addons\oca\_repos\OCA-stock-logistics-workflow\stock_quant_package_dimension\models\*.py" -Pattern "volume|store="
```

---

## 5. Bảng điền kết quả (dán output vào cột cuối)

| Mục | Kết quả thực tế | Nguồn (file:dòng hoặc output) | A đủ? | B đủ? |
|---|---|---|---|---|
| B1 field inventory | | | | |
| B2 sức chứa kg/CBM | | | | |
| B3 cut-off | | | | |
| B4 air/sea | | | | |
| B5 state machine | | | | |
| B6 nhiều khách | | | | |
| B7 ghi đè picking | | | | |
| B8 tier hook | | | | |
| B9 chi phí kế thừa | | | | |
| T1 manifest tms | | | | |
| T2 consolidation vs trucking | | | | |
| T3 tài xế (ảnh/ký/offline) | | | | |
| T4 trọng lượng phụ thuộc | | | | |
| T5 test của tms | | | | |
| T6 depot/dock cho nhánh ⑤ | | | | |

---

## 6. Sau khi có bằng chứng — cập nhật đúng chỗ (không sửa lung tung)

| Việc | File | Ai được sửa |
|---|---|---|
| Điền mục **Quyết định** + bảng A/B/C + Reuse Proof | `docs/adr/ADR-003-chuyen-vs-batch.md` | BE-01 (đề xuất) → **RV-01 duyệt** |
| Đổi trạng thái OQ-10 từ "Bắt buộc chốt ở P0" → kết luận | `docs/09-open-questions.md` | RV-01 / Orchestrator (BE-01 **không** sửa `docs/**`) |
| Nếu chọn **A** (bỏ `lab_chuyen`): sửa model inventory + ledger bước ⑤⑥ | `docs/02-kien-truc.md` §2.2, `docs/01` §2 | **Orchestrator** (docs/00–07 đã FROZEN) |
| Nếu chọn **C**: giữ nguyên, ghi lý do "đã đọc A và B" | `docs/adr/ADR-003-*.md` | BE-01 |
| Cập nhật trạng thái P0-07 (BLOCKED → DONE) + nhật ký | `workflow/task-board.md` | RV-01 (workflow/** của RV-01) |

**Không được:** sửa `docs/adr/ADR-003-chuyen-vs-batch.md` mà không có bằng chứng dán vào bảng §5 — đó đúng là lỗi "chọn C mà không đọc A/B" mà ADR-003 gốc đã cảnh báo.

---

## 7. Trạng thái code hiện tại liên quan tới ADR này

P0 đã dựng `lab_chuyen` (đi theo **phương án C**, tức là *giả định* C sẽ được chốt) nhưng:

* **chưa** inherit mixin `tier.validation` (chờ OCA + ADR),
* **chưa** viết logic giảm tải / duyệt vào chuyến,
* toàn bộ Reuse Proof của `lab_chuyen` nằm ở `addons/lab_chuyen/README.rst` §"Vì sao phải là module mới (L3)".

Nếu ADR-003 chốt **A**, phần bỏ đi là: 2 file model + 16 dòng ACL + 2 sequence (~300 dòng), và thay bằng ~5 field `_inherit` trên `stock.picking.batch`. Chi phí đổi hướng **thấp** ở thời điểm này — đó là lý do phải chốt ADR **trước P2**, không phải sau.
