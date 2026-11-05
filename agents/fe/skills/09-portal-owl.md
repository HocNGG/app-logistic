## Dùng khi nào

- Làm trang **TRACK công khai** (`/lab/track/<token>`) và các trang portal `/my/...` (P4).
- Viết **OWL 2** cho `addons/*/static/src/**` (component, field widget, patch view).
- Review PR portal/OWL: cần biết cái gì **không** có trong Odoo 17 để không code theo trí nhớ Odoo 16/18.

## 1. Sự thật portal Odoo 17 (đã kiểm chứng, docs/02 §6.5)

| Tin đồn / Odoo khác | Sự thật 17.0 |
|---|---|
| "Có route tổng quát `/my/<model>/<id>`" | **Không có**. Đường tổng quát là `/mail/view?model=…&res_id=…&access_token=…`; route `/my/...` là **theo từng model** (`/my/orders/<int:order_id>` với `auth="public"`) |
| "`access_token` là field khai báo tay" | `portal.mixin` thêm `access_token` (Char, `copy=False`, không index), điền **lười** bởi `_portal_ensure_token()` (uuid4, sudo) |
| "Kiểm quyền portal bằng `ir.rule`" | Không. Controller gọi **`_document_check_access(model, id, access_token)`**: sudo-browse → `check_access_rule('read')` → nếu `AccessError` thì so `consteq(record.access_token, access_token)` |
| "`stock.picking` có portal sẵn" | `stock.picking` **không** phải `portal.mixin`; cần `_inherit = ['stock.picking', 'portal.mixin']` + override `_compute_access_url()` (BE-01 làm) |
| "OWL 1 `this.state = {...}`, `props.value`" | OWL 2: `useState`, `props.record.data[props.name]`, `patch` từ `@web/core/utils/patch` |

## 2. Việc của BE-01 (bạn **không** sửa `.py` — chỉ đọc và yêu cầu)

1. `_inherit = ['stock.picking', 'portal.mixin']`, override `_compute_access_url()`.
2. Route `@http.route('/lab/track/<string:token>', auth='public', website=True)` gọi `_document_check_access` (hoặc endpoint `/lab/api/v1/track/<access_token>` — docs/02 §6.2).
3. Template QWeb whitelist — **template là của bạn**, route là của BE. Thiếu route → ghi `workflow/task-board.md`, không tự viết model.

## 3. Hợp đồng dữ liệu cho trang track (chỉ field whitelist)

| Được hiển thị | **Không** được |
|---|---|
| Mã vận đơn (`name`), chặng (`stage`), thời điểm, địa danh công khai, ghi chú công khai | Giá/cước, `partner_id` nội bộ, tên tài xế, `employee`, `account.move`, id kỹ thuật |

```python
# BE-01 (tham chiếu — KHÔNG phải file bạn sửa)
events = [{"stage": e.stage, "stage_label": e.stage_label, "at": e.at, "place": e.place, "note": e.note}
          for e in record.event_ids]     # whitelist tường minh, không dùng .read() toàn bộ
```

## 4. QWeb template công khai (đặt ở `views/portal_track_templates.xml`)

```xml
<template id="lab_track_page" name="Lab Track">
    <t t-call="portal.portal_layout">
        <t t-set="no_breadcrumbs" t-value="True"/>
        <div class="container py-4" id="lab-track">
            <h1 class="h4 mb-1">Hành trình vận đơn</h1>
            <p class="text-muted mb-4">Mã: <span t-esc="tracking_ref"/></p>
            <ol class="list-unstyled">
                <t t-foreach="events" t-as="e">
                    <li class="d-flex mb-3">
                        <span class="badge badge-primary mr-3" t-esc="e['stage_label']"/>
                        <div>
                            <div><t t-esc="e['at']"/></div>
                            <div class="text-muted" t-if="e.get('place')"><t t-esc="e['place']"/></div>
                            <div t-if="e.get('note')"><t t-esc="e['note']"/></div>
                        </div>
                    </li>
                </t>
            </ol>
            <p class="text-muted" t-if="not events">Chưa có mốc hành trình nào.</p>
        </div>
    </t>
</template>
```

- `t-esc` (không `t-raw`) — mọi giá trị từ DB đều escape.
- Không bao giờ render `record` thô; controller đã whitelist, template chỉ đọc key đã có.
- Trang công khai **không** dùng `portal.portal_layout` nếu không muốn lộ menu portal? Với demo: dùng layout để có header/footer đồng bộ, chấp nhận hiện nút "My account" — hoặc `t-call="web.layout"` nếu muốn tối giản. Ghi quyết định vào PR.

## 5. OWL 2 component (không OWL 1)

```js
/** @odoo-module **/
import { Component, useState } from "@odoo/owl";
import { registry } from "@web/core/registry";

export class LabTrackTimeline extends Component {
    static template = "lab_core.LabTrackTimeline";
    static props = { events: { type: Array }, defaultOpen: { type: Boolean, optional: true } };
    setup() {
        this.state = useState({ openStage: this.props.defaultOpen ? 0 : null });
    }
    toggle(index) {
        this.state.openStage = this.state.openStage === index ? null : index;
    }
    get hasEvents() {
        return this.props.events.length > 0;
    }
}
registry.category("components").add("lab_track_timeline", LabTrackTimeline);
```

```xml
<!-- static/src/xml/lab_track_timeline.xml — template OWL đăng ký qua ir.ui.view + qweb -->
<templates>
    <t t-name="lab_core.LabTrackTimeline">
        <ol class="o_lab_timeline list-unstyled">
            <t t-foreach="props.events" t-as="e" t-key="e.stage">
                <li t-att-class="state.openStage === e_index ? 'active' : ''"
                    t-on-click="() => this.toggle(e_index)">
                    <strong t-esc="e.stage_label"/>
                    <span t-esc="e.at"/>
                </li>
            </t>
        </ol>
        <p t-if="!hasEvents" class="text-muted">Chưa có mốc hành trình nào.</p>
    </t>
</templates>
```

Điểm phải nhớ: `/** @odoo-module **/` ở dòng đầu; `static template` (string, không phải `template:` instance); state qua `useState`; props khai báo `static props` (OWL 2 bắt buộc validate).

## 6. Field widget đọc `props.record.data[props.name]`

```js
/** @odoo-module **/
import { Component } from "@odoo/owl";
import { registry } from "@web/core/registry";
import { standardFieldProps } from "@web/views/fields/standard_field_props";

export class LoadGaugeField extends Component {
    static template = "lab_core.LoadGaugeField";
    static props = { ...standardFieldProps };
    get percent() {
        return Number(this.props.record.data[this.props.name] || 0);
    }
    get colorClass() {
        const p = this.percent;
        return p > 100 ? "bg-danger" : p > 90 ? "bg-warning" : "bg-success";
    }
}
registry.category("fields").add("lab_load_gauge", { component: LoadGaugeField, supportedTypes: ["float"] });
```

**Không** có `props.value` trong OWL 2 (đó là OWL 1). Không `this.state.field` — đọc qua `props.record`.
Nếu cần ghi: `this.props.record.update({ [this.props.name]: value })`; nếu cần chặn theo readonly: `this.props.readonly` (do `standardFieldProps` cung cấp).

## 7. `patch` để mở rộng hành vi có sẵn

```js
/** @odoo-module **/
import { patch } from "@web/core/utils/patch";
import { KanbanRecord } from "@web/views/kanban/kanban_record";

patch(KanbanRecord.prototype, {
    onClick() {
        // ví dụ: click thẻ chuyến quá tải → mở wizard duyệt thay vì form
        if (this.props.record.data.ti_le_tai > 100) { this.env.services.action.doAction("lab_chuyen.action_overload_wizard"); return; }
        return super.onClick(...arguments);
    },
});
```

Luật patch: chỉ patch prototype của class **có thật trong 17.0** (grep `addons/web/static/src/**`), luôn gọi `super` khi cần hành vi gốc, đặt file trong `static/src/js/` và khai báo asset bundle qua `views/*.xml` (FE-owned).

## Checklist nhanh

- [ ] Không có route `/my/<model>/<id>` tự chế; dùng `/mail/view?model=&res_id=&access_token=` hoặc route theo model.
- [ ] Trang công khai chỉ render field trong whitelist; không có giá/cước/tài xế/partner nội bộ.
- [ ] Mọi giá trị render bằng `t-esc`, không `t-raw`.
- [ ] OWL: `/** @odoo-module **/`, `static template`, `useState`, `static props`.
- [ ] Field widget đọc `props.record.data[props.name]` — không `props.value`.
- [ ] `patch` chỉ trên class 17.0 có thật, có `super` khi cần.
- [ ] Có `HttpCase`/kịch bản smoke cho trang track với token sai (phải 403/404, không lộ dữ liệu).
