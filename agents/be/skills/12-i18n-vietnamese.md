# 12 — i18n tiếng Việt (Luật 6)

## Dùng khi nào

Mỗi khi thêm chuỗi người dùng thấy: label, thông báo lỗi, email, report, menu — và trước demo để kiểm không lộ tiếng Anh.

---

## 1. Quy tắc bọc chuỗi

```python
from odoo import _                                # BẮT BUỘC import từ odoo

raise UserError(_("Chuyến %s chưa có đơn nào để lập Debit.") % chuyen.name)
chuyen.message_post(body=_("Đã chốt booking %(bk)s.", bk=so_booking))
```

| ✅ Đúng | ❌ Sai | Vì sao |
|---|---|---|
| `_("Chuỗi tiếng Việt")` | `"Chuỗi tiếng Việt"` không bọc | không vào `vi.po`, không dịch được sang EN nếu cần |
| `_("Đơn %s", name)` / `_("Đơn %(n)s", n=name)` | `_("Đơn ") + name` | người dịch không thấy ngữ cảnh, ngôn ngữ khác đảo trật tự từ |
| placeholder có tên khi ≥ 2 biến | `%(1)s %(2)s` ba bốn biến | dễ đảo, dễ sai |
| `_()` trong Python, `t-out="_(...)"` trong QWeb | `_()` trong XML data | XML data load trước khi có `_` |
| Tên field `string="Kg tính cước"` | hardcode label trong view | label nằm ở model, dịch 1 chỗ |

Không dịch: `Debit`, `AWB`, `HBL`, `CBM`, `POD`, `booking`, `manifest` — giữ nguyên thuật ngữ nghiệp vụ (glossary `docs/00` §10).

## 2. `i18n/vi.po` — workflow 4 bước

```powershell
# 1) Xuất chuỗi từ module ra PO (lần đầu; sau đó export lại để cập nhật)
python odoo-bin -c infra/odoo.conf -d odoo17_lab --i18n-export=addons/lab_chuyen/i18n/vi.po --modules=lab_chuyen --language=vi_VN

# 2) Dịch trong file .po (msgstr) — hoặc import bản dịch có sẵn
# 3) Nạp lại bản dịch vào DB + nâng cấp module để áp dụng
python odoo-bin -c infra/odoo.conf -d odoo17_lab --i18n-import=addons/lab_chuyen/i18n/vi.po --language=vi_VN --i18n-overwrite --stop-after-init
python odoo-bin -c infra/odoo.conf -d odoo17_lab -u lab_chuyen --stop-after-init

# 4) Kiểm: đổi ngôn ngữ user sang Tiếng Việt rồi mở đúng màn hình demo
```

Cấu trúc 1 entry đúng:

```po
#. module: lab_chuyen
#: addons/lab_chuyen/models/lab_chuyen.py:0
#: addons/lab_chuyen/wizard/lab_lap_debit_wizard.py:0
#, python-format
msgid "Chuyến %s chưa có đơn nào để lập Debit."
msgstr ""
```

- `msgstr` để **rỗng** khi nguồn đã là tiếng Việt (nguồn = tiếng Việt là hợp lệ với dự án này); chỉ điền khi cần bản dịch EN/khác.
- **Không sửa tay** `msgid` (phải khớp 100% chuỗi trong code, kể cả dấu chấm cuối).
- Footer mỗi file: `"Plural-Forms: nplurals=1; plural=0;\n"` (tiếng Việt không chia số nhiều) — sai dòng này là lỗi `ValueError` khi nạp.
- Header `"Language: vi\n"`, `"Content-Type: text/plain; charset=UTF-8\n"`, file **UTF-8 no BOM**.

## 3. Glossary bắt buộc (dán lên tường)

| Thuật ngữ | Dùng đúng | Đừng dùng |
|---|---|---|
| Chuyến | `lab.chuyen` — "Chuyến CH/AIR/26/0001" | "trip", "lô", "container" |
| Kiện | `stock.quant.package` — "Kiện PACK00012" | "kien_hang", "thùng" (khi là đơn vị đóng gói) |
| Cước | `sale.order.line` — "Cước bay 83,33 kg" | "phí", "giá vận chuyển" (khi đã có ngữ cảnh cước) |
| Debit | `account.move` — "Debit tháng 3" | "phiếu nợ", "công nợ" (= phải thu nói chung) |
| Kho chờ | `stock.location` KHO CHỜ | "kho tạm", "kho ảo" |
| Giảm tải | gỡ đơn khỏi chuyến (nhánh ②) | "hủy chuyến", "downgrade" |
| Tải trọng | `tai_trong_max_kg` | "trọng tải xe" |
| Manifest | bảng kê hàng của chuyến | "danh sách", "bảng kê" (khi là tên chứng từ) |
| POD | proof of delivery | "biên nhận" (= phiếu lấy hàng) |
| CBM | m³ | "khối" (trong cột bảng) |

## 4. Dịch nhãn mặc định của Odoo trên màn hình demo

Ba cách, theo thứ tự:

1. **Bật tiếng Việt cho user demo** (L0): `res.users.lang = 'vi_VN'`. Odoo CE có sẵn bản dịch `vi` cho `sale`, `stock`, `account` → phần lớn nhãn đã đúng. Kiểm từng màn hình demo, ghi lại chỗ còn tiếng Anh.
2. **Nhãn của ta thì ta sở hữu**: field mới luôn có `string=` tiếng Việt; view của FE-01 dùng `string=` khi cần đổi nhãn cho đúng nghiệp vụ.
3. **Đổi nhãn menu/action core** (chỉ khi demo chạm vào): cập nhật record bằng xmlid đầy đủ trong data XML của ta:

```xml
<odoo>
  <!-- Đổi nhãn 1 menu core đúng chỗ demo; ghi rõ lý do trong README module -->
  <record id="sale.menu_sale_order" model="ir.ui.menu">
    <field name="name">Đơn hàng</field>
  </record>
</odoo>
```

⚠️ Cách 3 là override record của module khác: chỉ dùng cho 1–2 nhãn thật sự lộ trên demo, ghi vào `docs/09-open-questions.md`, và **không** dùng để "Việt hoá toàn bộ Odoo" (đó là việc của bản dịch, không phải của ta).

## 5. QWeb: `t-out`/`t-esc` + `_()`

```xml
<!-- Nhãn tĩnh trong template: được trích vào từ điển của view, dịch qua vi.po của module -->
<div class="title">VẬN ĐƠN HÀNG KHÔNG</div>

<!-- Chuỗi động: dùng t-out (17.0) + _() -->
<td t-out="_(line.state == 'booked' and 'Đã vào chuyến' or 'Chờ chuyến')"/>

<!-- Field có metadata: t-field (tự format theo lang) -->
<span t-field="o.ngay_bay" t-options="{'format': 'dd/MM/yyyy'}"/>
```

- `t-esc` vẫn chạy ở 17.0 nhưng là tên cũ — dùng `t-out`.
- `t-out` **escape** HTML mặc định; muốn render HTML đã kiểm thì `t-out` với `Markup` từ Python, không dùng `t-raw`.
- `_` được inject vào context render QWeb (kiểm lại trong `addons/base/models/ir_qweb.py` bản 17.0 nếu template báo `NameError: _`).

## 6. Ngày & số kiểu Việt Nam

| Cần | Cách | Kết quả |
|---|---|---|
| Ngày | `%d/%m/%Y` (`t-options` hoặc `format_date(env, d)`) | 31/12/2026 |
| Giờ | 24h, `%H:%M` | 18:30 |
| Số thập phân | `formatLang(env, 1234.5, decimal_places=2)` | 1.234,50 |
| Tiền | `formatLang(env, amount, currency_obj=currency)` | 1.234.500 đ |
| Số nguyên kiện | `formatLang(env, n, decimal_places=0)` | 12 |

Ngôn ngữ `vi_VN` có `decimal_places`/`thousands_sep` riêng trong `res.lang` — **không** tự `str(amount).replace('.', ',')` ở bất kỳ đâu. Trong Python dùng `odoo.tools.format_date`/`formatLang`, trong QWeb dùng `t-field`/`t-options`.

## Checklist nhanh

- [ ] Mọi chuỗi user-facing trong `models/`, `wizard/`, `report/` đều bọc `_()`.
- [ ] Không nối chuỗi đã dịch; dùng placeholder có tên khi ≥ 2 biến.
- [ ] `i18n/vi.po` tồn tại, UTF-8 no BOM, `Plural-Forms: nplurals=1; plural=0;`.
- [ ] Đã export lại PO sau khi thêm chuỗi mới (`--i18n-export`), không để PO lệch code.
- [ ] User demo `lang = 'vi_VN'`; đã rà **từng màn hình demo** không còn "New"/"Confirm"/"Save".
- [ ] Thuật ngữ đúng glossary (chuyến, kiện, cước, Debit, kho chờ, giảm tải, manifest).
- [ ] Ngày/số dùng `format_date`/`formatLang` (hoặc `t-field`), không tự replace dấu.
- [ ] Mọi override nhãn core (nếu có) được ghi lý do + vào `docs/09-open-questions.md`.
