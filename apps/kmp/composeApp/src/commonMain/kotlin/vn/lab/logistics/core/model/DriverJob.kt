package vn.lab.logistics.core.model

/**
 * Một việc lấy hàng trong ngày của tài xế.
 *
 * HÌNH DẠNG ĐÚNG THEO payload THẬT của `GET /lab/api/v1/driver/jobs`
 * (đo 2026-10-28 trên Odoo 17, DB `odoo17_lab`; xem `addons/lab_api/controllers/driver.py`
 * `_job_dict()` và `docs/02-kien-truc.md` §6.2). **Không tự thêm/bớt field** — đây là hợp
 * đồng với BE-01; muốn đổi thì mở yêu cầu cho BE, không sửa lén ở app.
 *
 * Quy tắc: app KHÔNG tính cước, KHÔNG tính tồn kho, KHÔNG đổi `state` cục bộ
 * (chỉ optimistic khi thật sự gọi API — và có rollback).
 */
data class DriverJob(
    /** id bản ghi `lab.yeu.cau.lay.hang` trong Odoo. */
    val id: Int,
    /** Mã việc, vd `LH/26/0005` (field `name` của model). */
    val ref: String,
    /** Tên shop / khách gửi (`shop_name`). */
    val shop: String,
    /** Địa chỉ lấy hàng (một dòng, có thể rất dài → UI phải ellipsis). */
    val address: String,
    val contactName: String?,
    val contactPhone: String?,
    /**
     * Khung giờ hẹn, dạng chuỗi **`HH:mm`** do backend định dạng sẵn
     * (`tools.gio_phut()`; app KHÔNG parse `Datetime` — OQ-23).
     *
     * ⚠️ Có thể là **`"-"`** khi shop chưa hẹn giờ ⇒ UI phải hiện "Chưa hẹn giờ",
     * không hiện dấu gạch trần (xem `windowLabel()`).
     */
    val windowFrom: String?,
    val windowTo: String?,
    /**
     * Số kiện DỰ KIẾN — **CÓ THỂ VẮNG** trong payload (OQ-22: controller chỉ trả
     * `expectedPackages` khi `so_kien_du_kien` có giá trị).
     *
     * ⇒ Kiểu `Int?` là BẮT BUỘC: `Int` trơn sẽ làm parse hỏng/null-sai và UI bịa "0 kiện".
     * UI hiện "Chưa rõ số kiện" khi `null` (xem `packagesLabel()`).
     */
    val expectedPackages: Int?,
    /**
     * Mã trạng thái do backend trả — mã thuộc [JobState] (7 mã của `lab.yeu.cau.lay_hang`).
     * App chỉ hiển thị: KHÔNG tự đổi state.
     */
    val state: String,
    /**
     * Nhãn tiếng Việt **của server** (`stateLabel` trong payload, lấy từ
     * `fields.Selection` của model). `null` khi server không trả (hoặc dữ liệu mẫu) ⇒
     * app fallback về `stateLabelOf(state)`.
     *
     * Nguyên tắc: ưu tiên nhãn của server — BE thêm state mới thì app cũ vẫn hiện đúng
     * tiếng Việt mà không phải phát hành bản mới.
     */
    val stateLabel: String? = null,
    val note: String? = null,
)
