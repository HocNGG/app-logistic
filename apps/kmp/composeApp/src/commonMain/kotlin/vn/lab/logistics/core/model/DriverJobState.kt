package vn.lab.logistics.core.model

/**
 * Mã trạng thái của `lab.yeu.cau.lay.hang` — **đồng bộ với model thật của BE-01**.
 *
 * Nguồn: `addons/lab_laixe/models/lab_yeu_cau_lay_hang.py` (field `state`).
 * Đối chiếu ngày **2026-10-28** (Orchestrator xác nhận cùng ngày).
 *
 * ⚠️ ASSUMPTION: trạng thái đồng bộ với docs/02 §3.2 + model BE-01; verify lại khi có API
 * (Wave B: đọc từ `GET /lab/api/v1/driver/jobs`, không hardcode ở client nữa — object này
 * chỉ còn dùng để map nhãn và để self-check).
 *
 * Vì sao vẫn phải liệt kê ở client (dù app KHÔNG tự đổi state):
 *  - map mã → nhãn tiếng Việt **lấy đúng string của model** để app và Odoo nói cùng một ngôn ngữ;
 *  - quyết định UI theo state (nút "Bắt đầu đi" chỉ bật ở `new`/`assigned`);
 *  - self-check phát hiện sớm khi BE đổi/thêm state (thay vì hiện mã thô trên UI demo).
 */
object JobState {
    /** Mới tạo, chưa gán tài xế. */
    const val NEW = "new"

    /** Đã gán tài xế. */
    const val ASSIGNED = "assigned"

    /** Tài xế đang trên đường đến lấy. */
    const val EN_ROUTE = "en_route"

    /** Đã lấy được hàng tại shop. */
    const val PICKED_UP = "picked_up"

    /** Hàng đã về kho — kết thúc tốt. */
    const val CONFIRMED = "confirmed"

    /** Việc bị huỷ (không phải lỗi của tài xế). */
    const val CANCELLED = "cancelled"

    /** Lấy hàng thất bại. */
    const val FAILED = "failed"

    /** Đủ 7 state theo đúng thứ tự khai báo trong model. */
    val ALL: List<String> = listOf(NEW, ASSIGNED, EN_ROUTE, PICKED_UP, CONFIRMED, CANCELLED, FAILED)
}
