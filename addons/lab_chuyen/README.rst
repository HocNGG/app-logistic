=============================================================
lab_chuyen — Chuyến gom hàng lẻ (⑤ ⑥ ⑦ ⑬ ⑭)
=============================================================

Gom nhiều đơn (`sale.order`) của **nhiều khách** vào một **chuyến bay / chuyến
tàu**, có tải trọng, hạn chốt hàng (cut-off), state machine của chuyến và chỗ
để gắn phiếu xuất kho / Debit.

Nguồn: ``docs/02-kien-truc.md`` §2.2, §3.1, §5 · ``docs/00-nghiep-vu.md`` §3
(bước ⑤⑥⑦⑬⑭) · ``docs/07-exception-flows.md`` nhánh ②

Vì sao phải là module mới (L3) — Reuse Proof
============================================

1. **Grep model:** Odoo 17 CE **không có** model mang ngữ nghĩa "chuyến gom hàng
   lẻ". ``stock.picking.batch`` (module ``stock_picking_batch``) chỉ gom nhiều
   picking **của cùng một công ty** để xử lý một lượt: không có hãng vận chuyển,
   không có cut-off, không có tải trọng kg/CBM, không có nhiều khách, không có
   "chờ chuyến sau".
2. **Field + config trên model có sẵn?** Không: ``delivery.carrier`` là *phương
   thức* vận chuyển, không có sức chứa; ``stock.picking`` là chứng từ kho, đã
   ``done`` khi hàng rời kho nên không mô tả được chuyến.
3. **OCA:** bộ ``tms`` (``OCA/stock-logistics-transport`` 17.0) là *trucking theo
   tuyến* (``tms.order``/``tms.route``/``tms.driver``), không phải consolidation
   theo chuyến bay/tàu, lại ở ``development_status: Alpha``.
   → Đánh giá đầy đủ và bằng chứng cần thu thập: ``docs/adr/ADR-003-chuyen-vs-batch.md``
   và ``docs/adr/ADR-003-evidence-needed.md``.
4. **Kết luận:** L3 — 2 model, thuộc module được cấp phép ``lab_chuyen``.

Nội dung P0
===========

=====================================  ==========================================================
File                                    Nội dung
=====================================  ==========================================================
``models/lab_chuyen.py``                ``lab.chuyen`` — field, state machine, compute tải trọng
``models/lab_chuyen_line.py``           ``lab.chuyen.line`` — dòng đơn trong chuyến (nhánh ②)
``models/stock_picking.py``             ``stock.picking.lab_chuyen_id`` (L2, bước ⑦)
``models/account_move.py``              ``account.move.lab_chuyen_id`` (L2, bước ⑬)
``security/ir.model.access.csv``        16 dòng ACL (8 nhóm × 2 model)
``data/ir_sequence_data.xml``           2 sequence: ``lab.chuyen.air``, ``lab.chuyen.sea``
``tests/test_chuyen_state.py``          7 test: sequence, transition đúng/sai, tải trọng
=====================================  ==========================================================

Những quyết định cần biết (⚠️ đọc trước khi review)
==================================================

1. **``stock.picking.lab_chuyen_id`` và ``account.move.lab_chuyen_id`` nằm ở
   ``lab_chuyen``, không nằm ở ``lab_core``.** ``docs/02`` §2.1 đặt chúng ở
   ``lab_core``, nhưng ``Many2one('lab.chuyen')`` chỉ resolve được khi module đó
   depends ``lab_chuyen`` — mà ``lab_core`` depends ``lab_*`` là phụ thuộc vòng,
   đúng thứ ``docs/02`` §1 cấm. Vì vậy field do module **sở hữu quan hệ** khai
   báo. Hai file ``stock_picking.py``/``account_move.py`` chỉ có **field**, không
   có logic, nên không phá vỡ phạm vi "chỉ khai field" của P0.
2. **Hai sequence thay vì một.** ``docs/02`` §5 ghi một sequence ``lab.chuyen``
   với định dạng ``CH/{loai}/{yy}/{seq:04d}``; nhưng ``ir.sequence`` không nội
   suy được field nghiệp vụ. Tách 2 sequence theo loại để vẫn đúng định dạng.
3. **Năm trong số chứng từ.** ``%(y)s`` của ``ir.sequence`` 17.0 trả **năm 4 chữ
   số** → ``CH/AIR/2026/0001`` (docs ghi ``CH/AIR/26/0001``). Muốn 2 chữ số: sửa
   ``prefix`` trong ``data/ir_sequence_data.xml``. ⚠️ Chưa kiểm chứng bằng runtime.
4. **Chưa inherit mixin ``tier.validation``.** ``base_tier_validation`` mới có
   trong ``depends`` để sẵn sàng cho ⑥ (P2-05); mixin chỉ được ``_inherit`` khi
   đã vendor OCA và đọc được tên hook thật (``_check_state_conditions``,
   ``_validated_states`` — docs/09). Không ``_inherit`` theo trí nhớ.
5. **ACL không cấp ``perm_unlink`` cho bất kỳ nhóm nào** (kể cả Kho/Sale Admin).
   ``docs/02`` §4.2 chỉ ghi "RW" — không có nghĩa là được xoá. Chuyến/đơn đã vào
   luồng nghiệp vụ phải còn vết (Luật 4 + Luật 5); muốn "bỏ" thì dùng
   ``state = cancelled`` / lưu trữ, không xoá. Admin hệ thống vẫn xoá được qua
   ``sudo`` khi cần dọn dữ liệu rác.

Chưa làm ở P0 (đúng phạm vi)
============================

* ⑥ Duyệt đơn vào chuyến bằng tier validation 2 cấp — P2-05.
* Nhánh ② : wizard ``lab.giam_tai.wizard`` (``booked → waitlist``, bắt buộc
  ``ly_do_go``, duyệt mới cho gỡ) — P2-06. Ở P0 mới chỉ có field ``state`` +
  ``ly_do_go`` trên dòng.
* ⑬ Wizard lập Debit từ chuyến + ``account.move.lab_khoi_luong`` — P5-02.
* ⑧ Report QWeb manifest — P4-02. ``ir.rule`` cho CS/Debit — P2-07.
* View XML (tree/form/kanban/calendar/graph) — **FE-01**, không phải file của BE.

Cài & kiểm tra
==============

.. code-block:: powershell

   # 1) Vendor OCA trước (base_tier_validation ở OCA/server-ux, KHÔNG phải server-tools)
   powershell -File infra/scripts/vendor-oca.ps1

   # 2) Cài
   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab -i lab_core,lab_chuyen --stop-after-init

   # 3) Test
   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_chuyen `
     --test-tags=/lab_chuyen --stop-after-init

   # 4) Kiểm tra tĩnh offline
   powershell -File infra/scripts/check.ps1
