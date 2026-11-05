# 15 — OCA reuse: vendor `addons/oca`, license, và ADR-003

## Dùng khi nào

Trước khi viết module mới, và mỗi lần cần một năng lực không có trong CE (duyệt nhiều cấp, khiếu nại, tms). AGENTS.md Luật 10: `addons/oca/**` **không ai được sửa**, kể cả BE-01.

---

## 1. Vendor bằng `git clone --depth 1` (khuyến nghị) hoặc subtree

```powershell
# Mỗi repo OCA = 1 thư mục con trong addons/oca (không lồng repo vào nhau)
New-Item -ItemType Directory -Force addons/oca | Out-Null
git clone --branch 17.0 --depth 1 https://github.com/OCA/server-ux.git addons/oca/server-ux
git clone --branch 17.0 --depth 1 https://github.com/OCA/helpdesk.git addons/oca/helpdesk
git clone --branch 17.0 --depth 1 https://github.com/OCA/stock-logistics-workflow.git addons/oca/stock-logistics-workflow
git clone --branch 17.0 --depth 1 https://github.com/OCA/stock-logistics-transport.git addons/oca/stock-logistics-transport

# Ghi lại đúng commit đã vendor (để tái lập được môi trường)
git -C addons/oca/server-ux rev-parse HEAD
```

Subtree (nếu muốn lịch sử nằm trong repo chính) — nặng hơn, chỉ chọn 1 cách:

```powershell
git subtree add --prefix addons/oca/server-ux https://github.com/OCA/server-ux.git 17.0 --squash
```

Quy ước: **1 repo = 1 thư mục**, ghi commit SHA + ngày vendor vào `README` của repo và ADR tương ứng. Không `pip install` module Odoo, không copy tay từng file OCA vào `addons/` (mất dấu bản quyền + không upgrade được).

## 2. `addons-path` — thứ tự và chỗ đặt

```ini
# infra/odoo.conf
addons_path = infra/odoo/addons,addons,addons/oca/server-ux,addons/oca/helpdesk,addons/oca/stock-logistics-workflow,addons/oca/stock-logistics-transport
```

- Odoo quét **lần lượt**, module trùng tên thì bản đầu tiên thắng → `addons` (của ta) đặt **trước** OCA để không bị OCA "che" module cùng tên.
- Đường dẫn tuyệt đối cũng hợp lệ; trên Windows dùng `/` hoặc `\\` thống nhất trong file conf.
- Thiếu 1 thư mục OCA trong `addons_path` = `Module not found` khi cài module depends nó.

## 3. Giấy phép — đọc trước khi thêm dòng `depends`

| License | Nghĩa thực dụng | Trong dự án |
|---|---|---|
| **LGPL-3** | dùng/sửa/phân phối, phải giữ license + công bố sửa đổi của module đó | license của **module ta viết** |
| **AGPL-3** | nếu chạy qua mạng cho người khác dùng → phải cung cấp source (kể cả bản sửa) | `base_tier_validation`, `stock_quant_package_dimension`, `tms` đều **AGPL-3** |
| OPL-1 / OEEL-1 | Enterprise — không dùng | — |

Quy tắc của dự án:
- Module của ta giữ `license: 'LGPL-3'`; **không copy code AGPL** vào file của ta (copy là nhiễm AGPL cho file đó). Muốn đổi hành vi → `_inherit` ở module ta (không sửa, không copy).
- Với đồ án/demo nội bộ: dùng AGPL bình thường. Nếu sản phẩm được phân phối hoặc cho khách dùng qua mạng → phải công bố source của các module AGPL đã vendor (nguyên bản, không sửa) — ghi vào `README` mục "Third-party licenses".
- Không trộn: đừng `depends` vào module AGPL rồi tuyên bố cả gói là LGPL thuần; ghi rõ trong README/ADR.

## 4. Không bao giờ sửa code vendor — dùng `_inherit`

```python
# addons/lab_chuyen/models/tier_definition.py  (module CỦA TA)
from odoo import models


class TierDefinition(models.Model):
    _inherit = "tier.definition"

    def _get_tier_validation_model_names(self):
        res = super()._get_tier_validation_model_names()
        return res + ["lab.chuyen", "lab.yeu.cau.lay.hang"]
```

- Sửa file trong `addons/oca/**` = mất khi `git pull` hoặc người sau `clone --depth 1` → dự án không tái lập được, RV-01 reject ngay (Luật 10).
- Cần field/behavior mới → `_inherit` trong module ta; cần patch JS/OWL → `patch()` từ module ta.
- Cần đổi `store=True` của một computed của OCA (ví dụ `volume` của `stock_quant_package_dimension` là **non-stored**, không filter/group được): khai lại field trong `_inherit` của ta (`volume = fields.Float(compute="_compute_volume", store=True)`) và ghi ADR — vẫn không sửa vendor.

## 5. Chiến lược upgrade

1. Pin theo **commit**, không pin theo nhánh: `git -C addons/oca/server-ux checkout <sha>` và ghi sha vào ADR/README.
2. Nâng cấp có kế hoạch: đọc `HISTORY.rst`/`readme/` của module → chạy `-u base_tier_validation` trên DB **snapshot** → chạy test của ta (`--test-tags=/lab_chuyen`) → mới commit.
3. Không nâng cấp OCA trong tuần demo. Không trộn nhánh (17.0 với 16.0 cùng `addons_path`).
4. OCA có `development_status: Alpha/Beta` (ví dụ `tms` = **Alpha**) → không đặt phụ thuộc sống còn vào nó cho demo.

## 6. Decision record — 5 module dự án đã cân nhắc

| Module (repo) | Version đã kiểm | License | Quyết định | Lý do / điều kiện |
|---|---|---|---|---|
| **`base_tier_validation`** (`OCA/server-ux`) | **17.0.4.1.0**, Mature | AGPL-3 | **DÙNG** (L1+L2) | Đúng nhu cầu ⑥⑭ giảm tải; `depends: ["mail"]` nhẹ. Lưu ý: repo là `server-ux`, **không phải** `server-tools` |
| **`helpdesk_mgmt`** (`OCA/helpdesk`) | nhánh 17.0 | AGPL-3 (verify `__manifest__.py`) | **DÙNG có điều kiện** (nhánh ⑥) | Chỉ cần khi làm khiếu nại; nếu cài lỗi/không kịp → phương án B: `mail.activity` + `lab.hanh.trinh.stage = 'exception'` (ADR-005, `docs/07` ⑥) |
| **`stock_quant_package_dimension`** (`OCA/stock-logistics-workflow`) | nhánh 17.0 | AGPL-3 | **DÙNG** (L1) | Có sẵn `pack_length/width/height/pack_weight/volume/estimated_pack_weight_kg` → khỏi tự viết field đo kiện (docs/01 §3.2). `volume` là **non-stored** → cần filter thì `_inherit` store=True + ADR |
| **`stock_picking_invoice_link`** (`OCA/stock-logistics-workflow`) | nhánh 17.0 (verify) | AGPL-3 | **CÂN NHẮC** | Hữu ích cho đối soát ⑯ (picking ↔ hoá đơn). Chỉ thêm nếu cần truy vết; Odoo native đã có `sale.order.invoice_ids` + `picking.sale_id` |
| **`tms`** (`OCA/stock-logistics-transport`) | **17.0.1.1.1**, Alpha | AGPL-3 | **ĐÁNH GIÁ BẮT BUỘC, thiên về KHÔNG dùng cho ⑤** | `depends: ["base","uom","fleet","base_geolocalize"]` (nhẹ, tốt), nhưng mô hình là **trucking theo tuyến** (`tms.order`/`tms.route`/`tms.stage`/`tms.team`/`tms.crew`/`tms.driver`/`tms.insurance` + mở rộng `fleet.vehicle`), không có khái niệm **consolidation theo chuyến bay/tàu** (sức chứa, cut-off, nhiều khách/chuyến) |

### ADR-003 — 3 câu phải trả lời trước khi tạo `lab.chuyen`/`lab_laixe`

1. `tms.route`/`tms.order` có mô tả được **chuyến gom nhiều đơn của nhiều khách** không? → Đọc `addons/oca/stock-logistics-transport/tms/models/tms_order.py` + `tms_route.py`; dự kiến **không** (order thuộc 1 route, không có tải trọng/cut-off/chờ chuyến). Ghi file:line vào ADR.
2. `tms.driver` + `fleet.vehicle` có đủ cho `lab_laixe` (②: gán tài xế, ảnh, chữ ký, xác nhận, sinh picking)? → Nếu `tms.driver` đã gắn `hr.employee`/`fleet.vehicle` và có lịch trình thì **hạ `lab_laixe` xuống L2** (chỉ thêm field ảnh/chữ ký + wizard); nếu chỉ là danh mục tài xế thì vẫn cần `lab.yeu.cau.lay.hang` (L3).
3. Chi phí: `tms` kéo thêm `fleet`, `base_geolocalize`, menu mới, 6 module con (`tms_sale`, `tms_account`, …) nếu dùng sâu → có làm nặng demo không? `development_status: Alpha` → rủi ro.

Kết luận phải nằm trong `docs/adr/ADR-003-chuyen-vs-batch.md` **dù chọn hay không chọn**. "Không biết nó tồn tại" = lỗi native-first.

## 7. Cách kiểm nhanh 1 module OCA trước khi `depends`

```powershell
Get-Content addons/oca/server-ux/base_tier_validation/__manifest__.py   # version, license, depends
Get-ChildItem addons/oca/stock-logistics-workflow/stock_quant_package_dimension/models
rg -n "_inherit|_name" addons/oca/helpdesk/helpdesk_mgmt/models -g "*.py" | Select-Object -First 20
```

Kiểm 3 thứ: (1) `version` bắt đầu `17.0`, (2) `license` + `depends` có kéo EE không, (3) có migration/`readme` cho 17.0 không.

## Checklist nhanh

- [ ] Module OCA clone đúng nhánh `17.0`, có SHA được ghi lại (README/ADR).
- [ ] Không có file nào trong `addons/oca/**` bị sửa (`git -C addons/oca/<repo> status` phải sạch).
- [ ] `addons_path` có đường dẫn OCA, `addons` của ta đứng trước.
- [ ] Đã đọc `__manifest__.py` của module: version/license/depends, không kéo EE.
- [ ] Nếu dùng AGPL: đã ghi chú license trong README (third-party) và không copy code sang module LGPL của ta.
- [ ] Có ADR-003 trả lời 3 câu về `tms` **trước** khi tạo `lab.chuyen`/`lab_laixe`.
- [ ] `tms` (Alpha) không phải phụ thuộc sống còn của demo; có phương án B.
- [ ] Upgrade OCA chỉ trên DB snapshot + chạy lại `--test-tags` của module ta.
