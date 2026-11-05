=============================================================
lab_hanhtrinh — Hành trình quốc tế (bước ⑨ ⑩ ⑪)
=============================================================

Theo dõi lô hàng **sau khi đã rời kho** công ty: lên máy bay/tàu, quá cảnh, đến
kho đối tác, đại lý giao khách (POD). Kèm chỗ lưu AWB/HBL/manifest và token để
khách tự tra cứu.

Nguồn: ``docs/02-kien-truc.md`` §2.4, §3.3, §6.5 · ``docs/00-nghiep-vu.md`` §3

Vì sao là module mới (L3) — Reuse Proof
=======================================

1. **Grep model:** ``stock`` chỉ quản kho **của chính mình**. Khi hàng đã lên
   máy bay / nằm ở kho đối tác nước ngoài thì ``stock.picking`` đã ``done`` —
   không còn gì để theo dõi.
2. **Thêm state vào ``stock.picking``?** Không được: đó là "bẫy" số 1 trong
   ``docs/01`` §4 — làm bẩn ngữ nghĩa chứng từ kho và picking đã done.
3. **Tái sử dụng:** đại lý = ``res.partner``, ảnh POD = ``ir.attachment``,
   báo cáo AWB/HBL = QWeb ``ir.actions.report`` (không model nào thêm).
4. **Kết luận:** L3 — 2 model, thuộc module được cấp phép ``lab_hanhtrinh``.

Nội dung P0
===========

=====================================  ==========================================================
File                                    Nội dung
=====================================  ==========================================================
``models/lab_hanh_trinh.py``            ``lab.hanh.trinh`` — field + 11 stage
``models/lab_hanh_trinh_line.py``       ``lab.hanh.trinh.line`` — timeline cho khách
``models/ir_attachment.py``             ``ir.attachment.lab_hanhtrinh_id`` (inverse cho POD)
``security/ir.model.access.csv``        16 dòng ACL (8 nhóm × 2 model)
``data/ir_sequence_data.xml``           sequence ``lab.hanh.trinh`` → ``HT/2026/0001``
``tests/test_hanh_trinh.py``            4 test: sequence, stage mặc định, liên kết chuyến, timeline
=====================================  ==========================================================

Stage (docs/02 §3.3)
====================

``draft`` → ``booked`` → ``departed`` → ``in_transit`` → ``arrived`` →
``handed_to_agent`` → ``delivered`` → ``closed``
nhánh ngoại lệ ⑥: ``exception`` → ``returned`` / ``compensated`` / ``closed``

Những quyết định cần biết (⚠️ đọc trước khi review)
==================================================

1. **``depends: lab_chuyen``** — chiều phụ thuộc đúng ``docs/02`` §1: hành trình
   **thuộc về** chuyến, nên ``lab_hanhtrinh`` depends ``lab_chuyen``, không phải
   ngược lại. Vì vậy ``lab.hanh.trinh.chuyen_id`` khai ở đây, không khai ở
   ``lab_chuyen``.
2. **``cang_di`` / ``cang_den`` là Char**, ``docs/02`` §2.4 ghi "m2o res.country /
   char" (chưa chốt). Chọn Char vì tên sân bay/cảng không phải quốc gia và người
   dùng cần gõ tự do ("Tân Sơn Nhất", "Cat Lai"). ⚠️ ASSUMPTION — đổi sang m2o
   ``res.country`` chỉ là sửa kiểu field, không đổi tên.
3. **``access_token`` ở P0 là Char thường**, chưa phải ``portal.mixin``. Khi làm
   portal (P4-03) phải chuyển sang ``_inherit = ['portal.mixin']`` +
   ``_compute_access_url`` để dùng ``_document_check_access`` — nếu khai cả hai
   nơi sẽ trùng field.
4. **``anh``/POD là o2m qua ``ir.attachment.lab_hanhtrinh_id``** (cùng lý do như
   ``lab_laixe``): one2many bắt buộc có field nghịch dạng m2o.
5. **ACL không cấp ``perm_unlink``** cho nhóm nào — ``docs/02`` §4.2 chỉ ghi
   "RW". Hành trình là chứng từ của khách: sai thì sửa/đóng, không xoá.

Chưa làm ở P0 (đúng phạm vi)
============================

* Transition stage ⑨⑩⑪ + tự sinh ``lab.hanh.trinh.line`` — P4-01.
* Report QWeb AWB/HBL/manifest (**font tiếng Việt**) — P4-02.
* Portal tra cứu: ``portal.mixin`` + ``/lab/api/v1/track/<token>`` + QWeb
  whitelist field — P4-03/P4-04.
* Nhánh ⑥ khiếu nại: ``helpdesk_mgmt`` + ``stage = exception`` — P6-05 (ADR-005,
  không thêm module cầu nối).
* View XML — **FE-01**.

Cài & kiểm tra
==============

.. code-block:: powershell

   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab `
     -i lab_core,lab_chuyen,lab_hanhtrinh --stop-after-init

   docker compose -f infra/docker-compose.yml run --rm odoo `
     odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_hanhtrinh `
     --test-tags=/lab_hanhtrinh --stop-after-init

   powershell -File infra/scripts/check.ps1   # kiểm tra tĩnh offline
