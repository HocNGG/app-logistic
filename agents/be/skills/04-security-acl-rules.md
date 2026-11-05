# 04 — Security: ACL, `ir.rule`, groups (8 nhóm của dự án)

## Dùng khi nào

Mỗi lần thêm model/field mới, mỗi lần thêm endpoint, và mỗi lần ai đó định viết `if user.has_group(...)`.

---

## 1. Ba tầng quyền — không trộn

| Tầng | Công cụ | File | Ghi nhớ |
|---|---|---|---|
| Model (CRUD) | `ir.model.access.csv` | `security/ir.model.access.csv` | thiếu dòng = `AccessError` ngay |
| Record (dòng nào) | `ir.rule` + `domain_force` | `security/*_rules.xml` | đây là nơi viết luật "tài xế chỉ thấy job của mình" |
| Field / menu | `groups="..."` trong view, `groups` trên `menuitem` | `views/*.xml` (FE-01) | chỉ ẩn UI, **không** phải bảo mật dữ liệu |

**Cấm:** `if not self.env.user.has_group(...): raise` trong business logic (AGENTS.md Luật 3). `ir.rule` làm đúng việc đó, kể cả khi record bị đọc qua JSON-RPC.

## 2. 8 nhóm quyền — khai báo ở `lab_core` (không phải CSV)

`docs/02` §4.1 chốt đúng 8 nhóm. Nhóm là **XML** (`res.groups`), CSV chỉ dùng cho ACL:

```xml
<!-- addons/lab_core/security/lab_core_groups.xml -->
<odoo>
  <record id="group_cs" model="res.groups">
    <field name="name">Lab · CS/Sale</field><field name="implied_ids" eval="[(4, ref('sales_team.group_sale_salesman'))]"/>
    <field name="category_id" ref="base.module_category_sales_management"/></record>
  <record id="group_driver" model="res.groups">
    <field name="name">Lab · Tài xế (portal)</field><field name="implied_ids" eval="[(4, ref('base.group_portal'))]"/></record>
  <record id="group_warehouse" model="res.groups">
    <field name="name">Lab · Kho</field><field name="implied_ids" eval="[(4, ref('stock.group_stock_user'))]"/></record>
  <record id="group_docs" model="res.groups">
    <field name="name">Lab · Chứng từ</field><field name="implied_ids" eval="[(4, ref('group_warehouse'))]"/></record>
  <record id="group_account" model="res.groups">
    <field name="name">Lab · Kế toán</field><field name="implied_ids" eval="[(4, ref('account.group_account_invoice'))]"/></record>
  <record id="group_sale_admin" model="res.groups">
    <field name="name">Lab · Sale Admin</field><field name="implied_ids" eval="[(4, ref('group_cs'))]"/></record>
  <record id="group_manager" model="res.groups">
    <field name="name">Lab · Trưởng phòng (duyệt)</field>
    <field name="implied_ids" eval="[(4, ref('group_sale_admin')), (4, ref('account.group_account_manager'))]"/></record>
  <record id="group_director" model="res.groups">
    <field name="name">Lab · Ban giám đốc (chỉ đọc)</field><field name="implied_ids" eval="[(4, ref('base.group_user'))]"/></record>
</odoo>
```

8 dòng ACL tương ứng **mỗi model mới** (một dòng = một cặp model × nhóm). Mẫu `lab.chuyen`:

```csv
id,name,model_id:id,group_id:id,perm_read,perm_write,perm_create,perm_unlink
access_lab_chuyen_cs,lab.chuyen cs,model_lab_chuyen,lab_core.group_cs,1,0,0,0
access_lab_chuyen_driver,lab.chuyen driver,model_lab_chuyen,lab_core.group_driver,0,0,0,0
access_lab_chuyen_warehouse,lab.chuyen warehouse,model_lab_chuyen,lab_core.group_warehouse,1,1,1,1
access_lab_chuyen_docs,lab.chuyen docs,model_lab_chuyen,lab_core.group_docs,1,1,0,0
access_lab_chuyen_account,lab.chuyen account,model_lab_chuyen,lab_core.group_account,1,0,0,0
access_lab_chuyen_sale_admin,lab.chuyen sale admin,model_lab_chuyen,lab_core.group_sale_admin,1,1,1,1
access_lab_chuyen_manager,lab.chuyen manager,model_lab_chuyen,lab_core.group_manager,1,1,1,0
access_lab_chuyen_director,lab.chuyen director,model_lab_chuyen,lab_core.group_director,1,0,0,0
```

Quy tắc: nhóm được `implied_ids` từ nhóm khác **không** tự có ACL — phải có dòng riêng. Tài xế có dòng `0,0,0,0` để ghi nhận "đã xét" (ACL rỗng + `ir.rule` là combo đúng, tránh RV-01 hỏi "sao không có dòng cho tài xế").

## 3. `ir.rule` — 4 luật bắt buộc của dự án

```xml
<!-- addons/lab_laixe/security/lab_laixe_rules.xml -->
<odoo>
  <!-- (1) Tài xế chỉ thấy yêu cầu lấy hàng của mình (docs/02 §4.2) -->
  <record id="rule_lay_hang_driver_own" model="ir.rule">
    <field name="name">Tài xế chỉ thấy yêu cầu của mình</field>
    <field name="model_id" ref="model_lab_yeu_cau_lay_hang"/>
    <field name="domain_force">[('driver_id.user_id', '=', user.id)]</field>
    <field name="groups" eval="[(4, ref('lab_core.group_driver'))]"/>
    <field name="perm_read" eval="True"/><field name="perm_write" eval="True"/>
    <field name="perm_create" eval="False"/><field name="perm_unlink" eval="False"/>
  </record>

  <!-- (2) CS chỉ thấy chuyến có đơn của mình phụ trách -->
  <record id="rule_chuyen_cs_own" model="ir.rule">
    <field name="name">CS chỉ thấy chuyến có đơn của mình</field>
    <field name="model_id" ref="model_lab_chuyen"/>
    <field name="domain_force">[('line_ids.sale_order_id.user_id', '=', user.id)]</field>
    <field name="groups" eval="[(4, ref('lab_core.group_cs'))]"/>
  </record>

  <!-- (3) Kế toán: chỉ thấy Debit; các nhóm vận hành khác cũng vậy -->
  <record id="rule_move_only_debit" model="ir.rule">
    <field name="name">Chỉ thấy bút toán Debit của chuyến</field>
    <field name="model_id" ref="model_account_move"/>
    <field name="domain_force">[('journal_id.code', '=', 'DEBIT')]</field>
    <field name="groups" eval="[(4, ref('lab_core.group_cs')), (4, ref('lab_core.group_warehouse')), (4, ref('lab_core.group_docs'))]"/>
    <field name="perm_read" eval="True"/><field name="perm_write" eval="False"/>
    <field name="perm_create" eval="False"/><field name="perm_unlink" eval="False"/>
  </record>

  <!-- (4) Global rule: tài xế không bao giờ thấy Debit, kể cả sau này thêm nhóm -->
  <record id="rule_move_not_driver" model="ir.rule">
    <field name="name">Tài xế không thấy bút toán</field>
    <field name="model_id" ref="model_account_move"/>
    <field name="domain_force">[('id', '=', False)]</field>
    <field name="groups" eval="[(4, ref('lab_core.group_driver'))]"/>
  </record>
</odoo>
```

`domain_force` dùng được: `user` (res.users của request), `company_id`/`company_ids` (được inject), và toán tử domain chuẩn. Không gọi hàm Python trong `domain_force`.

## 4. Pitfall record rules (RV-01 kiểm đúng 6 dòng này)

| Pitfall | Sự thật 17.0 | Cách tránh |
|---|---|---|
| Rule **global** (không `groups`) | AND với mọi rule nhóm → cực dễ khoá sạch dữ liệu | Chỉ dùng global khi thật sự muốn "không ai được"; luôn test bằng user thật |
| Nhiều rule **nhóm** | Cùng 1 operation: rule nhóm **OR** với nhau (union), global **AND** | Đừng viết 2 rule nhóm tưởng là AND |
| User không thuộc nhóm của rule | Rule **không áp dụng** (không phải "deny") | Quyền phải đến từ ACL; rule chỉ thu hẹp |
| `sudo()` | Bỏ qua **toàn bộ** `ir.rule` | Cấm `sudo()` không comment; test security phải chạy `with_user()` |
| Ẩn field bằng `groups` trong view | Chỉ ẩn UI; API `search_read` vẫn trả | Whitelist field ở `lab_api`; đừng đưa field nhạy cảm vào payload |
| domain theo quan hệ (`line_ids.sale_order_id.user_id`) | Đúng nhưng chậm trên bảng lớn | Thêm `index=True`; nếu > 50k dòng, xin RV duyệt thêm field `nhan_vien_phu_trach_id` trên `lab.chuyen` |

## 5. Portal-level (tài xế) và menu

- `lab_core.group_driver` implied `base.group_portal` → tài xế **không** vào backend, chỉ dùng app KMP qua `lab_api`.
- Portal không có `ir.model.access` mặc định cho model của ta → phải có dòng ACL cho `group_driver` (như §2) **cộng** `ir.rule`. Thiếu rule là tài xế đọc được mọi record cùng nhóm.
- Menu: `<menuitem id="menu_lab_chuyen" name="Chuyến" parent="..." action="..." groups="lab_core.group_warehouse,lab_core.group_sale_admin"/>` — nhiều nhóm thì ngăn bằng dấu phẩy (OR).
- Debit/journal là dữ liệu nhạy cảm: rule (4) là kiểu "đóng bằng domain rỗng" — dễ đọc, dễ audit hơn `perm_read=False` rải rác.

## 6. Test bằng user thứ hai (bắt buộc cho mọi model mới)

```python
from odoo.tests import TransactionCase, new_test_user
from odoo.exceptions import AccessError


class TestSecurityLabChuyen(TransactionCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.user_a = new_test_user(cls.env, login="drv_a", password="drv_a",
                                   groups="base.group_portal,lab_core.group_driver")
        cls.user_b = new_test_user(cls.env, login="drv_b", password="drv_b",
                                   groups="base.group_portal,lab_core.group_driver")
        cls.driver_a = cls.env["hr.employee"].create({"name": "Tài xế A", "user_id": cls.user_a.id})
        cls.job_a = cls.env["lab.yeu.cau.lay.hang"].create({
            "partner_id": cls.env["res.partner"].create({"name": "Shop A"}).id,
            "driver_id": cls.driver_a.id})

    def test_driver_chi_thay_job_cua_minh(self):
        Job = self.env["lab.yeu.cau.lay.hang"]
        self.assertEqual(Job.with_user(self.user_a).search([]).ids, [self.job_a.id])
        self.assertFalse(Job.with_user(self.user_b).search([]))

    def test_driver_khong_doc_duoc_account_move(self):
        with self.assertRaises(AccessError):
            self.env["account.move"].with_user(self.user_a).search([])
```

`with_user()` mới là test quyền; `self.env` (admin) luôn pass và **không** chứng minh gì.

## Checklist nhanh

- [ ] Mọi model mới có dòng ACL cho **từng** nhóm liên quan (kể cả `0,0,0,0`).
- [ ] Có `ir.rule` cho: tài xế (own job), CS (own chuyến), Debit (chỉ Debit / không cho tài xế).
- [ ] Không còn `has_group(` trong `models/`/`wizard/` (`rg -n "has_group\(" addons/` → rỗng).
- [ ] Mọi `sudo()` có comment lý do + RV duyệt.
- [ ] Có test `with_user()` cho ≥ 2 user khác nhóm, có case `assertRaises(AccessError)`.
- [ ] Field nhạy cảm không lộ qua `lab_api` (whitelist trong controller).
- [ ] Menu/field dùng `groups=`, không dùng `if` để ẩn.
