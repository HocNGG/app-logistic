=============================================================
lab_api — Cửa vào cho app KMP (chưa có endpoint ở P0)
=============================================================

Module keo thứ hai: nó **không** thêm nghiệp vụ, chỉ đặt controller HTTP cho app
Kotlin Multiplatform. Ở P0 module chỉ được dựng khung để chuỗi ``depends``
(``lab_api`` → ``lab_laixe`` / ``lab_hanhtrinh`` → ``lab_chuyen`` → ``lab_core``)
cài được và để FE-01 biết chính xác tên module khi viết client.

Nguồn hợp đồng API: ``docs/02-kien-truc.md`` §6 · ``docs/adr/ADR-004-lab-api.md``

Nội dung P0
===========

=====================================  ==========================================================
File                                    Nội dung
=====================================  ==========================================================
``__manifest__.py``                     depends ``lab_laixe`` + ``lab_hanhtrinh``, ``data: []``
``__init__.py``                         ``from . import controllers``
``controllers/__init__.py``             **Rỗng có chủ đích** — kèm checklist hợp đồng endpoint
=====================================  ==========================================================

Vì sao chưa viết endpoint (và không phải "bỏ dở")
=================================================

1. **Luật 7:** endpoint mới phải có ít nhất 1 ``HttpCase``. Chưa có DB/docker để
   chạy ``HttpCase`` ⇒ viết endpoint bây giờ là code không test.
2. **Hợp đồng API chưa đóng băng:** task P3-07 (S2 gate) mới là lúc chốt; FE-01
   còn 3 yêu cầu chéo mở trong ``workflow/task-board.md`` §2 (trả thêm ``so_kien``,
   ``window_from/to``, ``loadPercent``).
3. **Native làm được 80%:** app CS dùng JSON-RPC ``/web/dataset/call_kw`` (không
   cần controller). Chỉ 4 việc cần controller (docs/02 §6.1) — và cả 4 đều phụ
   thuộc quyết định token của OQ-14.

Khi làm (P3-03) — thứ tự bắt buộc
=================================

1. Token tài xế: 8h, lưu **hash**, không log (docs/02 §6.3).
2. ``/driver/jobs`` trước (FE-01 đang chờ), rồi ``accept``, ``capture``, ``fail``.
3. ``tests/test_driver_api.py`` (``HttpCase``) cho từng route — Luật 7.
4. Thêm dòng vào ``workflow/runbook.md`` khi có cổng/route mới (DoD D8).

⚠️ Bảo mật tối thiểu (docs/02 §6.3) — không được quên
=====================================================

* Upload ảnh ≤ 5MB, validate **magic bytes**, không tin ``mimetype`` client gửi.
* Không trả ``access_token`` của người khác; endpoint track chỉ trả field whitelist.
* Rate limit theo IP + token (đếm trong cache).
* Odoo 17 **không** hỗ trợ ``Authorization: Bearer`` (19.0 mới có) — token custom
  do controller của ta cài đặt, không giả vờ là native.
