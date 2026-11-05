# 09 — QWeb reports: AWB, HBL, manifest, phiếu lấy hàng

## Dùng khi nào

Khi cần in PDF (AWB/HBL/manifest/POD/phiếu lấy hàng/debit note). Không dùng Python + ReportLab, không tự sinh HTML.

---

## 1. `paperformat` → `ir.actions.report` → template QWeb

```xml
<!-- addons/lab_hanhtrinh/report/paperformat_data.xml -->
<odoo>
  <record id="paperformat_awb" model="report.paperformat">
    <field name="name">AWB — A4 dọc</field>
    <field name="format">A4</field>
    <field name="orientation">Portrait</field>
    <field name="margin_top">28</field>
  </record>
  <record id="paperformat_manifest" model="report.paperformat">
    <field name="name">Manifest — A4 ngang</field>
    <field name="format">A4</field>
    <field name="orientation">Landscape</field>
  </record>
</odoo>
```

```xml
<!-- addons/lab_hanhtrinh/report/lab_hanhtrinh_report.xml -->
<odoo>
  <record id="action_report_awb" model="ir.actions.report">
    <field name="name">AWB — Vận đơn hàng không</field>
    <field name="model">lab.hanh.trinh</field>
    <field name="report_type">qweb-pdf</field>
    <field name="report_name">lab_hanhtrinh.report_awb_document</field>
    <field name="paperformat_id" ref="paperformat_awb"/>
    <field name="print_report_name">(object.so_awb or object.name)</field>
    <field name="binding_model_id" ref="model_lab_hanh_trinh"/>
    <field name="binding_type">report</field>
  </record>
</odoo>
```

`report_name` luôn có **tiền tố module**; `binding_model_id` để nút Print hiện trên form/list; `print_report_name` là **biểu thức Python** đặt tên file PDF.

## 2. Template AWB hoàn chỉnh (copy được)

```xml
<template id="report_awb_document">
  <t t-call="web.html_container">
    <t t-foreach="docs" t-as="o">
      <t t-call="web.external_layout">
        <div class="page">
          <style>
            @font-face { font-family: "DejaVuVN"; src: url("/lab_hanhtrinh/static/src/fonts/DejaVuSans.ttf") format("truetype"); }
            .awb, .awb * { font-family: "DejaVuVN", sans-serif !important; }
            .awb table { width: 100%; border-collapse: collapse; } .awb td, .awb th { border: 1px solid #333; padding: 4px 6px; }
            .awb .title { font-size: 18px; font-weight: bold; text-align: center; }
          </style>
          <div class="awb">
            <div class="row mb-2">
              <div class="col-8">
                <div class="title">VẬN ĐƠN HÀNG KHÔNG — AIR WAYBILL</div>
                <div class="text-center">Số AWB: <strong t-out="o.so_awb or '—'"/></div>
              </div>
              <div class="col-4 text-end">
                <img t-attf-src="/report/barcode/?barcode_type=Code128&amp;value=#{o.so_awb or o.name}&amp;width=600&amp;height=100"
                     style="height:52px;"/>
                <div class="small" t-out="o.so_awb or o.name"/>
              </div>
            </div>
            <table>
              <tr><th style="width:50%">Người gửi (Shipper)</th><th style="width:50%">Người nhận (Consignee)</th></tr>
              <tr style="height:80px;">
                <td t-out="o.chuyen_id.line_ids[:1].sale_order_id.partner_id.display_name or '—'"/>
                <td t-out="o.sale_order_id.partner_shipping_id.display_name or o.sale_order_id.partner_id.display_name or '—'"/>
              </tr>
              <tr><th>Sân bay đi</th><th>Sân bay đến</th></tr>
              <tr><td t-out="o.cang_di or '—'"/><td t-out="o.cang_den or '—'"/></tr>
            </table>
            <table class="mt-2">
              <tr><th>Chuyến</th><th>Ngày bay</th><th>Số kiện</th><th>Kg tính cước</th><th>CBM</th></tr>
              <tr>
                <td t-out="o.chuyen_id.name"/>
                <td t-out="o.ngay_bay and o.ngay_bay.strftime('%d/%m/%Y') or '—'"/>
                <td t-out="sum(o.chuyen_id.line_ids.filtered(lambda l: l.state == 'booked').mapped('so_kien'))"/>
                <td t-out="round(o.chuyen_id.tai_trong_da_dung_kg, 2)"/>
                <td t-out="round(o.chuyen_id.the_tich_da_dung_cbm, 3)"/>
              </tr>
            </table>
            <table class="mt-2">
              <tr><th>STT</th><th>Mã đơn</th><th>Khách</th><th>Kiện</th><th>Kg</th></tr>
              <tr t-foreach="o.chuyen_id.line_ids.filtered(lambda l: l.state == 'booked')" t-as="line">
                <td t-out="line_index + 1"/><td t-out="line.sale_order_id.name"/>
                <td t-out="line.partner_id.display_name"/><td t-out="line.so_kien"/>
                <td t-out="round(line.khoi_luong_kg, 2)"/>
              </tr>
            </table>
            <div class="row mt-3">
              <div class="col-6">Ngày lập: <span t-field="o.create_date" t-options="{'format': 'dd/MM/yyyy'}"/></div>
              <div class="col-6 text-end">Đại lý: <span t-out="o.dai_ly_giao_id.display_name or '—'"/></div>
            </div>
          </div>
        </div>
      </t>
    </t>
  </t>
</template>
```

QWeb: `docs` là recordset, `o` là biến; `line_index` tự sinh trong `t-foreach`; `t-out` (17.0) thay `t-esc`; `t-field` cho field có metadata/format. Không `attrs=` trong template.

## 3. Font tiếng Việt — nguyên nhân PDF mất dấu

`wkhtmltopdf` không luôn có font tiếng Việt. Cách đúng (khuyến nghị): đặt `DejaVuSans.ttf`/`Roboto-Regular.ttf` vào `addons/lab_hanhtrinh/static/src/fonts/` và khai `@font-face` như §2 → font đi theo module, demo máy nào cũng đúng. Cách nhanh nhưng rủi ro: khai `font-family: "Times New Roman"` + cài font trên máy chạy Odoo (fail khi demo máy khác). Muốn CSS dùng chung cho mọi report: `"assets": {"web.report_assets_common": ["lab_hanhtrinh/static/src/scss/report_font.scss"]}` — kiểm tên bundle trong `addons/web/__manifest__.py` 17.0 trước khi dùng.

Kiểm chứng: mở PDF, zoom 400%, đọc "Đại lý"/"Nguyễn". Ra ô vuông/`?` = thiếu font, không phải lỗi encoding — đừng "sửa" bằng cách bỏ dấu dữ liệu.

## 4. Manifest (A4 ngang) — cùng khung §2, khác nội dung bảng

```xml
<!-- Nằm trong <div class="page"> của khung §2, paperformat_manifest -->
<h3 class="text-center">MANIFEST — <span t-out="o.name"/></h3>
<table class="table table-sm table-bordered">
  <thead><tr><th>STT</th><th>Mã đơn</th><th>Khách</th><th>Kiện</th><th>Kg</th><th>CBM</th><th>Trạng thái</th></tr></thead>
  <tbody>
    <tr t-foreach="o.line_ids" t-as="line">
      <td t-out="line_index + 1"/><td t-out="line.sale_order_id.name"/><td t-out="line.partner_id.display_name"/>
      <td t-out="line.so_kien"/><td t-out="round(line.khoi_luong_kg, 2)"/><td t-out="round(line.the_tich_cbm, 3)"/>
      <td t-out="dict(line._fields['state'].selection).get(line.state)"/>
    </tr>
  </tbody>
  <tfoot><tr class="fw-bold"><td colspan="3">Tổng</td><td t-out="sum(o.line_ids.mapped('so_kien'))"/>
    <td t-out="round(o.tai_trong_da_dung_kg, 2)"/><td t-out="round(o.the_tich_da_dung_cbm, 3)"/><td/></tr></tfoot>
</table>
```

Wrapper (`web.html_container` → `web.external_layout` → `<div class="page">`) giống §2 cho **mọi** report; chỉ phần thân khác nhau.

## 5. Report của dự án (`docs/02` §8)

| Report | Model | Format | Ghi chú |
|---|---|---|---|
| AWB | `lab.hanh.trinh` | A4 dọc | §2 |
| HBL | `lab.hanh.trinh` | A4 dọc | như AWB + container/seal |
| Manifest | `lab.chuyen` | A4 **ngang** | §4 |
| POD / phiếu giao | `lab.hanh.trinh` | A4 | đại lý ký nhận |
| Yêu cầu lấy hàng | `lab.yeu.cau.lay.hang` | A5 | tài xế in mang theo |
| Debit note | `account.move` | A4 | **tái dùng** `account.report_invoice_document`, không viết mới |

## 6. Debug nhanh

| Triệu chứng | Nguyên nhân | Sửa |
|---|---|---|
| PDF trắng | `report_name` sai xmlid / sai biến `t-foreach` | log `QWebException`; đúng dạng `module.template_id` |
| Mất dấu tiếng Việt | thiếu font (§3) | nhúng font vào `static/src/fonts/` |
| Bảng tràn trang | thiếu `<div class="page">` cho mỗi record | mỗi `o` một `.page` |
| Barcode không hiện | escape `&` sai trong `t-att-src` | dùng `t-attf-src` với `&amp;` |
| Thiếu header/logo | không `t-call="web.external_layout"` | luôn call external layout, không tự vẽ header |

## Checklist nhanh

- [ ] Mỗi report có `paperformat` riêng khi khác khổ/orientation.
- [ ] `report_name` có tiền tố module; `binding_model_id` để in từ form.
- [ ] Template `t-call="web.external_layout"` + `<div class="page">` cho mỗi record.
- [ ] Font DejaVu/Roboto trong `static/src/fonts/` + `@font-face` (không phụ thuộc font máy).
- [ ] Đã xuất PDF thật bằng tiếng Việt có dấu, không chỉ xem HTML.
- [ ] Debit note tái dùng report native của `account`, không viết lại.
- [ ] Không `attrs=`, không `<list>` trong report XML.
- [ ] `_render_qweb_pdf('lab_hanhtrinh.report_awb_document', [rec.id])` chạy không raise.
