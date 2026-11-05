## Dùng khi nào

- Viết/sửa **bất kỳ** file `addons/**/views/*.xml` (quyền của FE-01 theo Luật 10).
- Màn hình Odoo web (backend) cho `lab.chuyen`, `lab.yeu.cau.lay.hang`, `lab.hanh.trinh`… và inherit view core (`sale.order`, `stock.picking`).
- Review PR có `attrs=`, `states=`, `<list>`, `quantity_done` — thấy là chặn ngay.

## 1. Bảng sai/đúng ở Odoo 17 (đã kiểm chứng source 17.0)

| ❌ SAI | ✅ ĐÚNG ở 17.0 |
|---|---|
| `attrs="{'invisible': [['state','=','draft']]}"` | `invisible="state == 'draft'"` — `attrs` bị **hard-remove**, `_check_xml` raise `ValidationError("Since 17.0, the attrs and states attributes are no longer used")` |
| `states="draft,open"` | `invisible="state not in ('draft','open')"` (trạng thái readonly đặt bằng `readonly=\"state != 'draft'\"`) |
| `<list>` | **`<tree>`** (17.0 chưa có `list` trong `ir.ui.view.type`) |
| `groups="group_lab_manager"` (thiếu module) | `groups="lab_core.group_lab_manager"` — **luôn** có tiền tố module |

Quy ước file: `views/lab_chuyen_views.xml`, `views/sale_order_views.xml`; mỗi record có `id` tiền tố module (`lab_chuyen.view_chuyen_form`), `name` dạng `lab.chuyen.form`. Không sửa `addons/oca/**`.

⚠️ Tên field trong view **phải khớp** `models/*.py` của BE-01. Lệch thì FE sửa **view** (view là của bạn), **không** sửa model — báo BE qua `workflow/task-board.md`.

## 2. `<tree>` cho `lab.chuyen`

```xml
<record id="view_chuyen_tree" model="ir.ui.view">
    <field name="name">lab.chuyen.tree</field>
    <field name="model">lab.chuyen</field>
    <field name="arch" type="xml">
        <tree string="Chuyến" decoration-danger="ti_le_tai &gt; 100"
              decoration-warning="ti_le_tai &gt; 90 and ti_le_tai &lt;= 100">
            <field name="name"/>
            <field name="loai_van_chuyen"/>
            <field name="cut_off_datetime"/>
            <field name="tai_trong_max_kg" sum="Tổng tải trọng"/><field name="tai_trong_da_dung_kg" sum="Đã dùng"/>
            <field name="ti_le_tai" widget="progressbar"/>
            <field name="state" widget="badge"
                   decoration-success="state == 'open'" decoration-info="state == 'draft'"/>
            <field name="user_id" widget="many2one_avatar_user" optional="show"/>
        </tree>
    </field>
</record>
```

`optional="show|hide"` cho cột — bật/tắt được trong UI, rất hợp màn CS.

## 3. Form view đầy đủ cho `lab.chuyen`

```xml
<record id="view_chuyen_form" model="ir.ui.view">
    <field name="name">lab.chuyen.form</field>
    <field name="model">lab.chuyen</field>
    <field name="arch" type="xml">
        <form string="Chuyến">
            <header>
                <button name="action_open" type="object" string="Mở chuyến" class="oe_highlight"
                        invisible="state != 'draft'"/>
                <button name="action_lock" type="object" string="Chốt chuyến" class="oe_highlight"
                        invisible="state != 'open'" confirm="Chốt chuyến thì không sửa được đơn. Tiếp tục?"/>
                <button name="action_reopen" type="object" string="Mở lại"
                        groups="lab_core.group_lab_manager" invisible="state != 'locked'"/>
                <field name="state" widget="statusbar" statusbar_visible="draft,open,locked"/>
            </header>
            <sheet>
                <div class="oe_button_box" name="button_box">
                    <button name="action_view_lines" type="object" class="oe_stat_button" icon="fa-cubes">
                        <field name="line_count" widget="statinfo" string="Đơn"/>
                    </button>
                </div>
                <group>
                    <group string="Thông tin chuyến">
                        <field name="loai_van_chuyen"/><field name="cut_off_datetime"/>
                        <field name="user_id" string="Phụ trách"/>
                    </group>
                    <group string="Tải trọng">
                        <field name="tai_trong_max_kg"/><field name="tai_trong_da_dung_kg"/>
                        <field name="ti_le_tai" widget="progressbar"/>
                    </group>
                </group>
                <notebook>
                    <page string="Đơn hàng" name="lines">
                        <field name="line_ids" readonly="state == 'locked'">
                            <tree editable="bottom">
                                <field name="so_id"/><field name="partner_id"/>
                                <field name="khoi_luong_kg" sum="Tổng kg"/><field name="so_kien" sum="Tổng kiện"/>
                                <field name="state" widget="badge"/>
                            </tree>
                        </field>
                    </page>
                    <page string="Ghi chú" name="notes">
                        <field name="note" placeholder="Ghi chú nội bộ cho chuyến…"/>
                    </page>
                </notebook>
            </sheet>
            <div class="oe_chatter">
                <field name="message_follower_ids"/><field name="activity_ids"/><field name="message_ids"/>
            </div>
        </form>
    </field>
</record>
```

## 4. Kanban · Search · Calendar

```xml
<record id="view_chuyen_kanban" model="ir.ui.view">
    <field name="name">lab.chuyen.kanban</field><field name="model">lab.chuyen</field>
    <field name="arch" type="xml">
        <kanban default_group_by="state" quick_create="false">
            <field name="name"/><field name="ti_le_tai"/>
            <templates><t t-name="kanban-box">
                <div class="oe_kanban_global_click">
                    <strong><field name="name"/></strong>
                    <div class="text-muted"><field name="loai_van_chuyen"/></div>
                    <span t-if="record.ti_le_tai.raw_value &gt; 100" class="badge badge-danger">Quá tải</span>
                </div>
            </t></templates>
        </kanban>
    </field>
</record>
<record id="view_chuyen_search" model="ir.ui.view">
    <field name="name">lab.chuyen.search</field><field name="model">lab.chuyen</field>
    <field name="arch" type="xml">
        <search string="Tìm chuyến">
            <field name="name" filter_domain="['|', ('name','ilike',self), ('partner_id','ilike',self)]"/>
            <filter name="f_open" string="Đang mở" domain="[('state','=','open')]"/>
            <filter name="g_state" string="Theo trạng thái" context="{'group_by':'state'}"/>
            <filter name="g_cutoff" string="Theo ngày chốt" context="{'group_by':'cut_off_datetime:day'}"/>
        </search>
    </field>
</record>
<record id="view_chuyen_calendar" model="ir.ui.view">
    <field name="name">lab.chuyen.calendar</field><field name="model">lab.chuyen</field>
    <field name="arch" type="xml">
        <calendar string="Lịch chuyến" date_start="cut_off_datetime" color="state"/>
    </field>
</record>
```

## 5. Inherit view core bằng xpath (`sale.order`)

```xml
<record id="view_order_form_lab_chuyen" model="ir.ui.view">
    <field name="name">sale.order.form.lab.chuyen</field>
    <field name="model">sale.order</field>
    <field name="inherit_id" ref="sale.view_order_form"/>
    <field name="arch" type="xml">
        <xpath expr="//header" position="inside">
            <button name="action_create_chuyen" type="object" string="Tạo chuyến" class="oe_highlight"
                    groups="lab_core.group_lab_cs" invisible="state not in ('sale','done')"/>
        </xpath>
        <xpath expr="//div[@name='button_box']" position="inside">
            <button name="action_open_chuyen" type="object" class="oe_stat_button" icon="fa-plane" groups="lab_core.group_lab_cs">
                <field name="lab_chuyen_id" widget="statinfo" string="Chuyến"/>
            </button>
        </xpath>
        <xpath expr="//field[@name='partner_id']" position="after">
            <field name="lab_pickup_mode" groups="lab_core.group_lab_cs"/>
        </xpath>
    </field>
</record>
```

Quy tắc xpath: ưu tiên `//div[@name='button_box']`, `//header`, `//field[@name='x']` — **không** dùng đường dẫn số kiểu `//form/sheet/group[2]`. Nếu `ref` không tồn tại → `_check_xml` raise `ValidationError` ngay khi upgrade, nêu rõ id sai.

## Checklist nhanh

- [ ] Không có `attrs=` / `states=` trong bất kỳ view nào (`grep -rn "attrs=" addons/*/views`).
- [ ] Dùng `<tree>`, không dùng `<list>`.
- [ ] Mọi `groups=` có tiền tố module (`lab_core.group_…`), không có group "trần".
- [ ] Form có `<header>` + `statusbar` + `button_box` (smart button) + `oe_chatter`.
- [ ] `invisible`/`readonly` đều là **biểu thức** dạng mới, không phải list domain.
- [ ] Inherit dùng `ref` core có thật trong 17.0 (`sale.view_order_form` …) và xpath theo `@name`.
