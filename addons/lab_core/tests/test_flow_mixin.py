# -*- coding: utf-8 -*-
from odoo.tests import TransactionCase, tagged


@tagged("post_install", "-at_install")
class TestFlowMixin(TransactionCase):
    """P0 — kiểm CẤU TRÚC của lõi nền, không kiểm nghiệp vụ.

    Mixin ở P0 cố ý không có method nào (chỉ 6 field). Test này chứng minh:
    1. mixin tồn tại và đủ 6 field ngoại lệ (docs/02 §2.1);
    2. mixin là AbstractModel nên không có bảng/không cần ACL;
    3. 9 nhóm quyền (docs/02 §4.1 + nhóm "Trưởng nhóm tài xế") nạp đủ từ XML.
    """

    FIELD_NGOAI_LE = (
        "lab_exception_type",
        "lab_exception_note",
        "lab_return_step",
        "lab_is_exception",
        "lab_exception_user_id",
        "lab_exception_date",
    )

    NHOM_QUYEN = (
        "group_cs",
        "group_driver",
        "group_driver_leader",
        "group_warehouse",
        "group_docs",
        "group_account",
        "group_sale_admin",
        "group_manager",
        "group_director",
    )

    def test_mixin_co_du_6_field_ngoai_le(self):
        fields_map = self.env["lab.flow.mixin"]._fields
        for name in self.FIELD_NGOAI_LE:
            self.assertIn(name, fields_map, "Thiếu field ngoại lệ: %s" % name)

    def test_mixin_la_abstract_khong_co_bang(self):
        mixin = self.env["lab.flow.mixin"]
        self.assertFalse(mixin._auto, "lab.flow.mixin phải là AbstractModel (_auto = False)")
        self.assertFalse(mixin._transient)

    def test_du_9_nhom_quyen(self):
        for xmlid in self.NHOM_QUYEN:
            group = self.env.ref("lab_core.%s" % xmlid)
            self.assertTrue(group.id, "Thiếu nhóm quyền lab_core.%s" % xmlid)

    def test_nhom_tai_xe_la_portal_level(self):
        """Tài xế KHÔNG vào backend: group_driver phải implied base.group_portal."""
        driver = self.env.ref("lab_core.group_driver")
        portal = self.env.ref("base.group_portal")
        self.assertIn(portal, driver.implied_ids)
        self.assertNotIn(self.env.ref("base.group_user"), driver.implied_ids)

    def test_nhom_truong_nhom_tai_xe_la_internal_level(self):
        """Trưởng nhóm tài xế là người PHÂN CÔNG: Internal User, KHÔNG phải tài xế.

        Hai khẳng định này khoá đúng thiết kế (xem ``security/lab_core_groups.xml``):
        * implied ``base.group_user`` -> đọc được ``hr.employee.public`` (danh sách
          tài xế) và dùng app/backoffice như CS;
        * KHÔNG implied ``base.group_driver`` -> không bị ``ir.rule`` "Tài xế chỉ
          thấy yêu cầu của mình" khoá vào bản ghi của chính mình (nếu bị khoá thì
          không còn việc nào để phân công).
        """
        truong_nhom = self.env.ref("lab_core.group_driver_leader")
        self.assertIn(self.env.ref("base.group_user"), truong_nhom.implied_ids)
        self.assertNotIn(self.env.ref("lab_core.group_driver"), truong_nhom.implied_ids)
        self.assertEqual(truong_nhom.category_id, self.env.ref("lab_core.module_category_lab"))
