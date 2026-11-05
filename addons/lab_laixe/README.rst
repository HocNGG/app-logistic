=============================================================
lab_laixe — Điều tài xế đi lấy hàng (bước ②)
=============================================================

Yêu cầu lấy hàng tận nơi: gán tài xế, khung giờ, ảnh + chữ ký + số kiện thực tế
từ app KMP, rồi sinh phiếu nhập kho khi hàng về.

Nguồn: ``docs/02-kien-truc.md`` §2.3, §3.2, §4.2 · ``docs/00-nghiep-vu.md``
§4 (hai đường vào kho) · ``docs/07`` nhánh "khách tự mang tới kho"

Vì sao là module mới (L3) — Reuse Proof
=======================================

1. **Grep model:** Odoo 17 CE không có model "điều tài xế đi lấy hàng cho một
   đơn". ``fleet`` quản lý **xe của công ty** (bảo dưỡng, chi phí), không điều
   phối chuyến đi lấy hàng; ``hr.employee`` chỉ là hồ sơ nhân sự.
2. **Field + config trên model có sẵn?** Không: không có chỗ để lưu "khung giờ
   lấy hàng + ảnh + chữ ký + xác nhận của tài xế cho đơn này".
3. **Tái sử dụng tối đa (Luật 1):** tài xế = ``hr.employee``, xe =
   ``fleet.vehicle``, ảnh = ``ir.attachment``, chữ ký = ``fields.Binary``.
   **Không** tạo model ``tai_xe`` / model ảnh / dùng module ``sign`` (EE).
4. **Kết luận:** L3 — 1 model, thuộc module được cấp phép ``lab_laixe``.

Nội dung P0
===========

=====================================  ==========================================================
File                                    Nội dung
=====================================  ==========================================================
``models/lab_yeu_cau_lay_hang.py``      ``lab.yeu.cau.lay.hang`` — field + state (chưa có transition)
``models/ir_attachment.py``             ``ir.attachment.lab_lay_hang_id`` (inverse cho ``anh_ids``)
``security/lab_laixe_rules.xml``        ``ir.rule``: tài xế chỉ thấy yêu cầu của mình
``security/ir.model.access.csv``        8 dòng ACL (8 nhóm × 1 model)
``data/ir_sequence_data.xml``           sequence ``lab.yeu.cau.lay.hang`` → ``LH/2026/0001``
``tests/test_lay_hang.py``              4 test: sequence, state mặc định, ir.rule, ảnh đính kèm
=====================================  ==========================================================

Trạng thái (docs/02 §3.2)
=========================

``new`` → ``assigned`` → ``en_route`` → ``picked_up`` → ``confirmed``
(``nhanh thất bại`` → ``failed``; huỷ trước khi lấy → ``cancelled``)

Khách **tự mang tới kho** thì **không tạo record** — đi thẳng vào ``stock.picking``
(docs/00 §4). Không được coi "tự mang" là lỗi.

Những quyết định cần biết (⚠️ đọc trước khi review)
==================================================

1. **``anh_ids`` cần file ``models/ir_attachment.py``.** One2many bắt buộc có
   field nghịch dạng Many2one ở model đích; ``ir.attachment`` dùng cặp
   ``res_model``/``res_id`` (Integer) nên không làm inverse được. Thêm
   ``lab_lay_hang_id`` là cách Odoo chuẩn, không phải "sửa model native".
2. **``chu_ky`` là Binary(attachment=True)**, không dùng ``sign`` (EE — docs/01
   §3.3). App KMP vẽ chữ ký rồi gửi base64 qua endpoint ``capture`` (P3-03).
3. **Chưa có method transition ở P0.** Cố ý: state machine + sinh
   ``stock.picking`` là P3-01/P3-02, phải đi kèm test. Khai state trước để FE-01
   dựng được view/statusbar.
4. **ACL không cấp ``perm_unlink``** cho nhóm nào — ``docs/02`` §4.2 chỉ ghi
   "RW". Yêu cầu lấy hàng đã phát sinh phải còn vết; bỏ thì dùng
   ``state = cancelled`` / ``failed``.

Chưa làm ở P0 (đúng phạm vi)
============================

* Transition ``new → assigned → en_route → picked_up → confirmed`` và sinh
  ``stock.picking`` khi ``confirmed`` — P3-01/P3-02.
* 4 endpoint ``/lab/api/v1/driver/*`` + token 8h — P3-03 (``lab_api``).
* ``ir.rule`` cho CS/Kho/Chứng từ trên model này — P2-07.
* View XML — **FE-01**.

Cài & kiểm tra
==============

.. code-block:: powershell

   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab -i lab_core,lab_laixe --stop-after-init

   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_laixe `
     --test-tags=/lab_laixe --stop-after-init

   powershell -File infra/scripts/check.ps1   # kiểm tra tĩnh offline
