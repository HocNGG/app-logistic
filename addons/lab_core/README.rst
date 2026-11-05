=============================================================
lab_core — Lõi nền (nhóm quyền + mixin ngoại lệ)
=============================================================

Module "keo" của hệ thống gom hàng lẻ xuất khẩu. Không có nghiệp vụ riêng:
nó chỉ giữ **thứ mà 3 module nghiệp vụ cùng cần**, để chúng không phải depends
lẫn nhau (phụ thuộc vòng ⇒ cài sai thứ tự, không upgrade được).

Nguồn thiết kế: ``docs/02-kien-truc.md`` §1, §2.1, §4.1 · ``docs/adr/ADR-001-lab-core.md``

Nội dung P0
===========

===========================================  ==========================================================
Thành phần                                    Nội dung
===========================================  ==========================================================
``models/lab_flow_mixin.py``                  AbstractModel ``lab.flow.mixin`` — 6 field ngoại lệ
``security/lab_core_groups.xml``              9 nhóm quyền (``res.groups``) + 1 ``ir.module.category``
``security/ir.model.access.csv``              Header-only (xem ghi chú bên dưới)
``tests/test_flow_mixin.py``                  Kiểm cấu trúc mixin + đủ 9 nhóm quyền
===========================================  ==========================================================

6 field ngoại lệ (chép đúng ``docs/02`` §2.1, không đổi tên)
------------------------------------------------------------

``lab_is_exception`` · ``lab_exception_type`` · ``lab_exception_note`` ·
``lab_return_step`` · ``lab_exception_user_id`` · ``lab_exception_date``

9 nhóm quyền (xmlid cố định — ``docs/02`` §4.1)
-----------------------------------------------

``lab_core.group_cs`` · ``group_driver`` · ``group_driver_leader`` ·
``group_warehouse`` · ``group_docs`` · ``group_account`` · ``group_sale_admin`` ·
``group_manager`` · ``group_director``

``group_driver_leader`` ("Trưởng nhóm tài xế") là nhóm **thêm sau**, phục vụ
việc phân công tài xế: Internal User (``implied_ids`` = ``base.group_user``),
KHÔNG phải tài xế (không implied ``group_driver``). Quyền: đọc + ghi
``lab.yeu.cau.lay.hang`` (không tạo/xoá — xem
``addons/lab_laixe/security/ir.model.access.csv``) và gọi được 2 endpoint
``/lab/api/v1/cs/jobs`` · ``/lab/api/v1/cs/jobs/<id>/assign`` như CS.

.. note::
   **Vì sao ``ir.model.access.csv`` chỉ có dòng header ở P0?**
   ``lab.flow.mixin`` là ``AbstractModel`` — không có bảng, không có bản ghi
   ``ir.model``, nên **không thể** và **không cần** dòng ACL. Nếu viết
   ``model_lab_flow_mixin`` vào CSV, Odoo báo
   ``External ID not found: model_lab_flow_mixin`` và **cài đặt thất bại**.
   Dòng ACL đầu tiên của module này sẽ xuất hiện khi có model lưu DB thật
   (wizard nhánh ngoại lệ ① — P6). File được giữ sẵn để ``infra/scripts/check.ps1``
   và RV-01 có chỗ kiểm, và để thứ tự nạp (nhóm → ACL) không phải sửa manifest.

Chưa làm ở P0 (đúng phạm vi — không viết trước)
-----------------------------------------------

* Chưa ``_inherit`` ``res.partner`` / ``sale.order`` / ``stock.picking`` /
  ``stock.move.line`` / ``product.template`` / ``res.company`` để thêm field
  L2 (P2-01). **Cảnh báo (⚠️ phát hiện của BE-01):** ``docs/02`` §2.1 đặt
  ``stock.picking.lab_chuyen_id`` và ``sale.order.lab_chuyen_id`` trong
  ``lab_core``, nhưng điều đó **không làm được** — ``Many2one('lab.chuyen')``
  chỉ resolve được khi module đó depends ``lab_chuyen``, mà ``lab_core``
  depends ``lab_*`` là phụ thuộc vòng (chính ``docs/02`` §1 cấm). Vì vậy các
  field trỏ tới ``lab.chuyen`` được khai báo trong ``lab_chuyen``
  (xem ``addons/lab_chuyen/models/stock_picking.py``).
* Chưa có ``lab.config.settings`` (transient) — chờ chốt danh sách tham số cấu hình (P1).
* Chưa có ``i18n/vi.po`` có nội dung: nhãn field đã là tiếng Việt ngay trong
  source; PO chỉ cần khi chốt bộ chuỗi dịch (P1/P2).

Cài đặt / kiểm tra
==================

.. code-block:: powershell

   # Trong container (docker compose đang chạy)
   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab -i lab_core --stop-after-init

   # Kiểm tra tĩnh offline (không cần Docker/Odoo/DB)
   powershell -File infra/scripts/check.ps1
