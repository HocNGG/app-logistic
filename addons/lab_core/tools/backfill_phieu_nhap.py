# -*- coding: utf-8 -*-
"""BACKFILL PHIẾU NHẬP CHO DỮ LIỆU DEMO — sửa vi phạm Luật 4 (REA).

VẤN ĐỀ (RV-01 đo 2026-11-03, review vòng 2 — P0-2):
    Trong 195 việc `state = confirmed` ("Đã về kho"):
      150 việc KHÔNG có phiếu kho nào            -> mở việc thấy ô "Phiếu nhập" trống
       40 việc trỏ phiếu loại XUẤT (WH/OUT/...)   -> sai bản chất ô "Phiếu nhập"
        3 việc trỏ phiếu IN nhưng `draft` + 0 dòng hàng (WH/IN/00487, 00486, 00045)
        2 việc có phiếu IN `done` (đúng — từ lúc sửa code 02/11)
    Nghĩa là: chứng từ nói "hàng đã về kho" nhưng KHÔNG có bút toán kho tương ứng
    (AGENTS.md Luật 4). Dữ liệu cũ làm demo nói dối dù code mới đã đúng.

CÁCH SỬA: với mỗi việc đã `confirmed` mà chưa có phiếu NHẬP `done` có dòng hàng:
    - gọi lại ĐÚNG đường nghiệp vụ hiện có (`_tao_phieu_nhap_kho` + `_xac_nhan_phieu_nhap`),
      không nhân bản logic;
    - trỏ `stock_picking_id` sang phiếu nhập mới (gỡ liên kết sai tới WH/OUT);
    - ghi `ngay_xac_nhan` nếu trống.
Sau đó xoá 3 phiếu IN `draft` rỗng không còn ai trỏ tới.

CHẠY (script tool, không phải code nghiệp vụ):
    docker compose -f infra/docker-compose.yml exec -T odoo \
        odoo shell -d odoo17_lab --no-http --log-level=warn < addons/lab_core/tools/backfill_phieu_nhap.py
"""

import logging

_logger = logging.getLogger(__name__)

CHUNK = 20  # commit theo lô để không giữ 1 transaction quá lớn


def _can_backfill(job):
    picking = job.stock_picking_id
    if not picking:
        return True
    if picking.picking_type_id.code != "incoming":
        return True  # đang trỏ nhầm phiếu XUẤT
    if picking.state != "done":
        return True  # phiếu IN nhưng chưa validate (draft/assigned)
    if not picking.move_line_ids:
        return True  # phiếu "rỗng" — không có dòng hàng
    return False


def backfill(env):
    Job = env["lab.yeu.cau.lay.hang"].sudo()  # sudo-ok: backfill tool chay bang odoo shell (superuser), can ghi moi viec da confirmed
    jobs = Job.search([("state", "=", "confirmed")], order="id")
    can = jobs.filtered(_can_backfill)
    print("Tong viec confirmed: %d | can backfill: %d" % (len(jobs), len(can)))
    if not can:
        return 0

    dem = 0
    loi = 0
    for job in can:
        try:
            with env.cr.savepoint():
                picking = job.stock_picking_id
                if picking and picking.picking_type_id.code == "incoming" and picking.state != "done":
                    # Đã có phiếu IN dở dang: cố validate; thiếu dòng hàng thì tạo lại.
                    if not picking.move_line_ids:
                        picking.unlink()
                        picking = job._tao_phieu_nhap_kho()
                    job._xac_nhan_phieu_nhap(picking)
                else:
                    picking = job._tao_phieu_nhap_kho()
                    job._xac_nhan_phieu_nhap(picking)
                job.write(
                    {
                        "stock_picking_id": picking.id,
                        "ngay_xac_nhan": job.ngay_xac_nhan or picking.date_done,
                    }
                )
            dem += 1
            if dem % CHUNK == 0:
                env.cr.commit()  # audit-ok: backfill tool — commit theo lo, khong phai code nghiep vu
                print("  ... da xu ly %d/%d" % (dem, len(can)))
        except Exception as exc:  # noqa: BLE001 - tiep tuc cac viec khac, ghi lai de xu ly tay
            loi += 1
            _logger.warning("Backfill loi o viec %s: %s", job.name, exc, exc_info=True)
            print("  [LOI] %s: %s" % (job.name, exc))
    env.cr.commit()  # audit-ok: backfill tool — commit cuoi
    print("Xong: %d viec duoc backfill, %d loi" % (dem, loi))

    # Dọn phiếu IN draft rỗng không còn ai trỏ tới
    Picking = env["stock.picking"].sudo()  # sudo-ok: backfill tool - don phieu rac, khong phai code nghiep vu
    rac = Picking.search(
        [
            ("picking_type_id.code", "=", "incoming"),
            ("state", "=", "draft"),
            ("move_ids", "=", False),
            ("origin", "like", "LH/"),
        ]
    )
    if rac:
        print("Don %d phieu IN draft rong: %s" % (len(rac), ", ".join(rac.mapped("name"))))
        rac.unlink()
        env.cr.commit()  # audit-ok: backfill tool — don phieu rac
    return dem


backfill(env)
