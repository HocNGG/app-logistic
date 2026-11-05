# 01 — Native-first recon (khảo sát trước khi viết dòng code đầu tiên)

## Dùng khi nào

Trước **mọi** việc: thêm field, thêm model, viết wizard, viết report, cài module OCA — chưa làm recon thì chưa được mở file Python.

---

## 1. Thang quyết định — đi từ bậc thấp nhất giải quyết được

| Bậc | Tên | Dấu hiệu đúng | Ai duyệt |
|---|---|---|---|
| **L0** | Dùng native | 0 dòng code, chỉ cài module có sẵn | không cần |
| **L1** | Cấu hình | chỉ XML/CSV (pricelist, route, warehouse, tier, lock date) | Review |
| **L2** | Kế thừa nhẹ | `_inherit` thêm field/method, XPath thêm nút — **không model mới** | Review |
| **L3** | Module mới | model mới + view + security + report riêng, **phải có ADR** | Review + ADR |
| **L4** | Fork | copy module core/OCA rồi sửa — **CẤM** | — |

Nguồn chuẩn: `docs/01-tai-su-dung-odoo17.md` §1 và bảng ledger 19 bước (§2). Cột "Lớp" trong ledger ấy là **hợp đồng**: viết Python cho dòng 🟩/🟨 → RV-01 reject.

## 2. Ba câu hỏi bắt buộc (trích nguyên văn vào PR)

1. **Odoo 17 đã có model nào mang đúng ngữ nghĩa này chưa?** — tra `addons/*/models/*.py` **trước**, tra Google sau.
2. **Nếu chưa có model, có thể biểu diễn bằng field + view + config trên model có sẵn không?**
3. **Nếu phải tạo model, nó thuộc module nào trong số được cấp phép** (`lab_core`, `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh`, `lab_api`)? Ngoài danh sách → ADR + RV duyệt.

**Quy tắc 90 giây:** không trả lời được 3 câu trên trong 90 giây → dừng, ghi vào `docs/09-open-questions.md` với giả định tạm `⚠️ ASSUMPTION`, làm việc khác. Không đoán.

## 3. Bộ lệnh recon (PowerShell + ripgrep)

`rg` là công cụ chính (`ruff` chỉ để lint). Đứng ở gốc repo:

```powershell
Set-Location C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics
$ODOO = "infra/odoo/addons"   # source Odoo 17, khớp --addons-path ở AGENTS.md §9
rg --version                  # nếu chưa có: winget install BurntSushi.ripgrep.MSVC
```

### 3.1 Có model nào chưa?

```powershell
rg -n "^\s*_name = ['\"]" $ODOO/stock/models                                  # toàn bộ model của stock
rg -n "_name = ['\"].*(trip|chuyen|batch|consolidat|groupage)" $ODOO -g "*.py"
rg -n "_name = ['\"]stock\.package" $ODOO -g "*.py"                          # 17.0: KHÔNG có stock.package
```

### 3.2 Field đã tồn tại chưa?

```powershell
rg -n "chargeable|volumetric" $ODOO -g "*.py"                                # trọng lượng tính cước
rg -n "weight = fields\.|shipping_weight|weight_bulk" $ODOO/stock_delivery/models -g "*.py"
rg -n "package_type_id|packaging_id" $ODOO/stock/models/stock_quant.py       # tên field kiện ở 17.0
```

### 3.3 Kiểm idioms 17.0 (grep để bắt cái sai, không phải để học)

```powershell
rg -n "quantity_done|qty_done|reserved_uom_qty" addons/                      # phải rỗng
rg -n "attrs=|states=" addons/ --glob "*.xml"                                # phải rỗng (17.0 hard-remove)
rg -n "read_group\(" addons/                                                 # phải rỗng, dùng _read_group
rg -n "SavepointCase" addons/                                                # phải rỗng
```

### 3.4 Kiểm Luật 3 (phân quyền bằng `ir.rule`, không bằng `if`)

```powershell
rg -n "has_group\(" addons/lab_* -g "*.py"        # mỗi hit: ACL/ir.rule đã làm được chưa?
rg -n "\.sudo\(\)" addons/lab_* -g "*.py"         # mỗi hit phải có comment lý do + RV duyệt
rg -n "ir.rule" addons/lab_*/security/*.xml | Measure-Object -Line
Get-Content addons/lab_chuyen/security/ir.model.access.csv | Measure-Object -Line
```

### 3.5 Khi nghi ngờ 16 → 17: đọc source, đừng code theo trí nhớ

```powershell
Get-Content $ODOO/stock/models/stock_move.py | Select-String -Pattern "picked|quantity =" -Context 1,1
Get-Content $ODOO/stock_delivery/models/stock_picking.py | Select-String "weight"
```

## 4. Recon worksheet — dán vào PR (mẫu copy được)

```markdown
### Recon worksheet — <việc cụ thể>
- Câu hỏi nghiệp vụ (1 dòng):
- Bước trong ledger: (①–⑯ / N1–N6)
- Đã grep (lệnh + kết quả): `rg "..." $ODOO` → 0 hit / hit ở file:line
- Đã đọc source 17.0 (file:line):
- Nguồn tái dùng: model / field / view / config nào
- Không tái dùng được vì:
- Lớp quyết định: L0 / L1 / L2 / L3 (+ ADR: docs/adr/ADR-xxx)
- Module sẽ sửa: lab_core / lab_chuyen / lab_laixe / lab_hanhtrinh / lab_api
- Test chứng minh:
- Cảnh demo dùng nó:
```

## 5. Ba ví dụ đã làm xong trên chính dự án này

### 5.1 "Có sẵn chargeable weight chưa?" → L2, tự viết 2 field

```powershell
rg -n "chargeable|volumetric" $ODOO -g "*.py"                       # 0 hit
rg -n "weight" $ODOO/stock_delivery/models/stock_move.py            # weight = product_qty * product.weight
```

Kết luận: `stock.move.weight` (do **`stock_delivery`** thêm) chỉ là kg thực tế = `product_qty * product.weight`, không có thể tích, không có hệ số chia. `stock.picking.weight`/`shipping_weight` cũng chỉ tổng kg thực tế.
→ **L2:** thêm `product.template.lab_volumetric_divisor` + compute `lab_chargeable_weight` trên `stock.move.line`. Không tạo model trọng lượng. Chi tiết: `06-sale-pricing-kg.md`.

### 5.2 "Có model chuyến (trip) chưa?" → L3, nhưng chỉ 2 model

```powershell
rg -n "_name = ['\"].*(trip|chuyen|consolidat)" $ODOO -g "*.py"     # 0 hit
rg -n "_name = \"stock.picking.batch\"|picking_ids" $ODOO/stock_picking_batch/models -g "*.py"
```

`stock.picking.batch` = gom nhiều **picking của cùng một công ty** để xử lý một lượt; không có hãng vận chuyển, không có cut-off, không có tải trọng, không có nhiều khách, không có `waitlist`. OCA `tms` (repo `OCA/stock-logistics-transport`, 17.0.1.1.1, AGPL-3, depends `base,uom,fleet,base_geolocalize`, `development_status: Alpha`) là **trucking theo tuyến** (`tms.order`/`tms.route`/`tms.driver`), không có consolidation theo chuyến bay/tàu.
→ **L3:** `lab.chuyen` + `lab.chuyen.line`, ADR bắt buộc (`docs/01` §3.2 ADR-003). Chi tiết đánh giá: `15-oca-reuse.md`.

### 5.3 "Có cần `lab_chuyen` cho manifest/booking không?" → không

Manifest = **báo cáo QWeb** trên `lab.chuyen.line` (L0 report engine). Booking chỉ là 3 field char/date trên `lab.chuyen`. Hãng vận chuyển = `delivery.carrier` (`stock_delivery`). Số AWB/HBL = char trên `lab.hanh.trinh`.
→ Không thêm model, không thêm module. Ba artifact "trông như model" này đều là **field + report**.

## Checklist nhanh

- [ ] Đã chạy `rg` trên `infra/odoo/addons` **trước** khi tra web.
- [ ] Đã ghi 3 câu trả lời + lệnh grep + kết quả vào recon worksheet/PR.
- [ ] Đã đọc source 17.0 của module sẽ inherit (không code theo trí nhớ).
- [ ] Đã kiểm 4 grep idioms: `quantity_done`, `attrs=`, `read_group(`, `SavepointCase` → rỗng.
- [ ] Không có `has_group(`/`sudo()` mới mà chưa có ACL/`ir.rule`/comment lý do.
- [ ] Nếu chọn L3: có ADR trong `docs/adr/` và đúng 1 trong 5 module được cấp phép.
- [ ] Worksheet đã dán vào PR; RV-01 đọc được mà không cần hỏi lại.
