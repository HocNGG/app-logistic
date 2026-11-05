package vn.lab.logistics.core.model

/**
 * =====================================================================================
 * VAI TRÒ CỦA TÀI KHOẢN → MÀN HÌNH LÀM VIỆC CỦA APP.
 *
 * Hợp đồng với BE-01 (`POST /lab/api/v1/auth/login`): `data.roles` là mảng chuỗi, từ vựng
 * CHỐT SẴN:
 *
 * | mã              | nghĩa nghiệp vụ                        | app có màn hình riêng |
 * |-----------------|----------------------------------------|-----------------------|
 * | `admin`         | quản trị viên — CHUYỂN TÀI KHOẢN để test | CÓ — "Chuyển tài khoản" |
 * | `driver`        | tài xế đi lấy hàng tại shop            | CÓ — "Việc hôm nay"   |
 * | `driver_leader` | trưởng nhóm tài xế, NGƯỜI PHÂN CÔNG    | CÓ — "Phân công tài xế" |
 * | `cs`            | CS điều phối (tạo YC, gán tài xế, gom chuyến) | KHÔNG — dùng Odoo web (D1) |
 * | `kho`           | thủ kho nhận hàng, sinh phiếu nhập kho | CÓ — "Nhận hàng"      |
 * | `ketoan`        | kế toán (debit, đối soát)              | KHÔNG — dùng Odoo web |
 * | `manager`       | quản lý                                | CÓ — Dashboard của sếp |
 * | `sep`           | ban giám đốc                           | CÓ — Dashboard của sếp |
 *
 * QUY TẮC CHỌN MÀN (thuần, kiểm được bằng unit test — xem `RoleRoutingTest`):
 * ưu tiên **admin → driver → driver_leader → kho → dashboard (manager/sep)**; có nhiều vai
 * trò thì lấy vai trò ưu tiên cao nhất; không có vai trò nào thuộc các mã trên (kể cả danh sách
 * rỗng, hoặc CHỈ có `cs`) ⇒
 * [Workspace.Unsupported] — màn thông báo "Vai trò này làm việc trên Odoo web", KHÔNG phải màn
 * trắng và KHÔNG đoán bừa.
 *
 * ⚠️ QUYẾT ĐỊNH D1 (chốt 2026-11-02, đổi so với bản trước): **CS rời app, làm việc trên Odoo web**.
 * Lý do nghiệp vụ: việc của CS là nhập nhiều trường (tạo khách, tạo yêu cầu lấy hàng, sửa địa chỉ,
 * gom nhiều đơn vào một chuyến) và cần nhìn nhiều dòng — bàn phím + màn lớn, không phải điện thoại.
 * Màn "Phân công" trong app vẫn còn, nhưng dành cho **trưởng nhóm tài xế** (`driver_leader`) —
 * người điều chỉnh phân công ngay trong ngày, ngoài hiện trường.
 * Mã vai trò `cs` VẪN nằm trong từ vựng hợp đồng với BE-01 (server vẫn trả), chỉ là app không
 * mở màn làm việc cho nó nữa — app hiện đúng câu "vai trò này làm việc trên web".
 *
 * Vì sao `manager`/`sep` có màn riêng: sếp cần nhìn KPI + xu hướng + việc gần nhất bằng app
 * (ảnh mẫu + spec `ui-dashboard-app-screens.md` §6), không phải mở Odoo web rồi tự cộng số.
 * `ketoan` vẫn làm việc trên Odoo web (debit/đối soát là việc của kế toán, không phải dashboard).
 *
 * Vì sao phải là hàm THUẦN ở `core/model` (không nằm trong `App.kt`): đây là quy tắc nghiệp
 * vụ dễ lệch nhất giữa app và backend (BE đổi thứ tự/mã vai trò là app sai màn), nên nó phải
 * có test JVM chạy offline, không cần GUI, không cần Odoo.
 *
 * So sánh **không phân biệt hoa/thường và bỏ khoảng trắng đầu/cuối** để app không vỡ nếu
 * backend trả `"CS"` hay `" cs"` — từ vựng vẫn là từ vựng của hợp đồng.
 * =====================================================================================
 */

/** Từ vựng vai trò CHỐT với BE-01. Không tự thêm mã mới ở app. */
object LabRole {
    /**
     * Quản trị viên — người được phép CHUYỂN TÀI KHOẢN để test nhanh 8 vai trò.
     *
     * BE-01 trả mã này ở ĐẦU `data.roles` khi tài khoản là admin, và chỉ tài khoản có mã này mới
     * gọi được `/lab/api/v1/admin/accounts` + `/admin/switch` (403 với mọi tài khoản khác).
     */
    const val ADMIN: String = "admin"
    const val DRIVER: String = "driver"

    /**
     * Trưởng nhóm tài xế — người PHÂN CÔNG (nhóm Odoo `lab_core.group_driver_leader`).
     * BE-01 trả mã này trong `data.roles` cùng các mã khác; app mở màn "Phân công tài xế".
     */
    const val DRIVER_LEADER: String = "driver_leader"
    const val CS: String = "cs"
    const val KHO: String = "kho"
    const val KETOAN: String = "ketoan"
    const val MANAGER: String = "manager"
    const val SEP: String = "sep"

    /**
     * Các mã CÓ màn hình LÀM VIỆC trên app (theo đúng thứ tự ưu tiên nghiệp vụ) — dùng cho thanh
     * chọn vai trò của chế độ mẫu và cho test bất biến "mã vai trò khớp từ vựng".
     *
     * `admin` cố ý KHÔNG nằm trong danh sách này: màn của admin là **công cụ test** (chuyển tài
     * khoản), không phải màn làm việc hằng ngày; nó chỉ mở khi server trả đúng mã `admin`.
     * `cs` cũng KHÔNG còn (quyết định D1): CS làm việc trên Odoo web.
     */
    val APP_ROLES: List<String> = listOf(DRIVER, DRIVER_LEADER, KHO)
}

/**
 * Màn hình làm việc tương ứng với vai trò của tài khoản.
 *
 * @param role mã vai trò quyết định màn này (rỗng với [Unsupported]).
 * @param label nhãn tiếng Việt hiện trên UI (thanh trên, màn thông báo).
 * @param subtitle một dòng giải thích màn này làm gì — dùng ở màn thông báo vai trò.
 */
enum class Workspace(
    val role: String,
    val label: String,
    val subtitle: String,
) {
    Driver(
        role = LabRole.DRIVER,
        label = "Tài xế — việc hôm nay",
        subtitle = "Lấy hàng tại shop: nhận việc, chụp ảnh, ký biên nhận.",
    ),

    /**
     * `driver_leader` — trưởng nhóm tài xế: người PHÂN CÔNG việc cho tài xế trong nhóm.
     *
     * Màn hình là **cùng một màn với CS** (`CsDispatchScreen`, chỉ khác tiêu đề/phụ đề): cùng
     * dữ liệu `cs/jobs`, cùng thao tác `assign`, cùng luật "server là nguồn sự thật của trạng
     * thái". Nhân bản màn thứ hai chỉ tạo hai bản sao dễ lệch nhau của CÙNG một luồng nghiệp vụ.
     *
     * Đứng SAU [Driver] trong thứ tự ưu tiên: tài khoản vừa là tài xế vừa là trưởng nhóm phải
     * mở app ra thấy việc của chính mình trước, không phải bàn phân công.
     */
    DriverLeader(
        role = LabRole.DRIVER_LEADER,
        label = "Trưởng nhóm tài xế",
        subtitle = "Phân công tài xế cho từng việc trong nhóm và theo dõi khung giờ hẹn.",
    ),

    Kho(
        role = LabRole.KHO,
        label = "Kho — nhận hàng",
        subtitle = "Nhận hàng tài xế mang về và lấy số phiếu nhập kho.",
    ),

    /** `manager` / `sep` — dashboard tổng quan cho sếp (KPI, xu hướng, cơ cấu trạng thái). */
    Dashboard(
        role = LabRole.MANAGER,
        label = "Sếp — tổng quan",
        subtitle = "KPI trong kỳ, xu hướng việc theo ngày, cơ cấu trạng thái và 10 việc gần nhất.",
    ),

    /**
     * `admin` — quản trị viên: màn **CHUYỂN TÀI KHOẢN** để test nhanh các vai trò.
     *
     * Vì sao có màn này: phải demo/chụp 8 vai trò trong cùng một buổi; đăng xuất rồi gõ lại mật
     * khẩu 8 lần vừa chậm vừa dễ gõ sai trên máy chiếu. Admin chọn tài khoản trong danh sách là
     * app ghi phiên mới và mở ĐÚNG màn của vai trò đó — cùng đường đi như vừa đăng nhập.
     *
     * Đây là CÔNG CỤ TEST, không phải màn nghiệp vụ: chỉ tài khoản có mã `admin` mới thấy.
     */
    Admin(
        role = LabRole.ADMIN,
        label = "Chuyển tài khoản",
        subtitle = "Chỉ quản trị viên · chọn tài khoản để test nhanh",
    ),

    /** `ketoan` hoặc danh sách rỗng: app không có màn hình cho vai trò này. */
    Unsupported(
        role = "",
        label = "Vai trò khác",
        subtitle = "Vai trò này làm việc trên Odoo web.",
    ),
    ;

    /** Vai trò này có màn hình thao tác trên app không (khác [Unsupported]). */
    val hasAppScreen: Boolean get() = this != Unsupported

    companion object {
        /**
         * Quy tắc chọn màn DUY NHẤT của app: ưu tiên admin → driver → driver_leader → kho
         * → dashboard. Thứ tự là **quy tắc nghiệp vụ**, không phải thứ tự server trả:
         *  - `admin` đứng ĐẦU vì màn chuyển tài khoản là công cụ test: tài khoản admin mà còn
         *    vai trò tài xế vẫn phải mở bàn chuyển tài khoản trước (muốn vào màn tài xế thì
         *    chuyển sang đúng tài khoản tài xế — đó chính là mục đích của công cụ này);
         *  - `driver` đứng đầu nhóm nghiệp vụ vì đó là việc của chính người cầm máy;
         *  - `driver_leader` trước `kho` vì trưởng nhóm là người phân công;
         *  - `manager`/`sep` đứng cuối: người vẫn còn việc tác nghiệp hằng ngày thì làm việc
         *    trước, xem tổng quan sau.
         *
         * ⚠️ `cs` KHÔNG còn nhánh riêng (D1): tài khoản chỉ có `cs` rơi xuống [Unsupported] và
         * app hiện câu "Vai trò này làm việc trên Odoo web" — đúng nghiệp vụ, không giả vờ có màn.
         *
         * @param roles `data.roles` của lần đăng nhập (hoặc danh sách đã lưu trong Settings).
         */
        fun fromRoles(roles: List<String>): Workspace {
            val normalized = roles.mapNotNull { it.normalizedRole() }.toSet()
            return when {
                LabRole.ADMIN in normalized -> Admin
                LabRole.DRIVER in normalized -> Driver
                LabRole.DRIVER_LEADER in normalized -> DriverLeader
                LabRole.KHO in normalized -> Kho
                // Sếp: `manager` hoặc `sep` đều vào dashboard (hai mã, cùng một màn).
                LabRole.MANAGER in normalized || LabRole.SEP in normalized -> Dashboard
                else -> Unsupported
            }
        }
    }
}

/** `" CS "` → `"cs"`; chuỗi rỗng/toàn khoảng trắng → `null` (không phải vai trò). */
internal fun String.normalizedRole(): String? =
    trim().lowercase().takeIf { it.isNotEmpty() }

/**
 * Nhãn tiếng Việt của một mã vai trò — dùng cho màn thông báo vai trò và thanh chọn vai trò
 * của chế độ mẫu. Mã lạ (BE thêm sau) hiện **nguyên chuỗi**, không nuốt mất thông tin.
 */
fun roleLabelOf(role: String): String = when (role.normalizedRole()) {
    LabRole.ADMIN -> "Quản trị viên"
    LabRole.DRIVER -> "Tài xế"
    LabRole.DRIVER_LEADER -> "Trưởng nhóm tài xế"
    /**
     * `cs` — CS điều phối. **Không có màn trên app** (D1): công việc của CS là nhập nhiều trường và
     * nhìn nhiều dòng (tạo khách, tạo yêu cầu lấy hàng, gán tài xế, gom chuyến) nên làm trên Odoo
     * web. Nhãn vẫn ghi rõ "(trên web)" để người dùng không tưởng app thiếu màn.
     */
    LabRole.CS -> "CS — điều phối (trên web)"
    LabRole.KHO -> "Kho — nhận hàng"
    LabRole.KETOAN -> "Kế toán"
    LabRole.MANAGER -> "Quản lý"
    LabRole.SEP -> "Ban giám đốc"
    null -> "Chưa có vai trò"
    else -> role.trim()
}

/**
 * Một dòng liệt kê vai trò của tài khoản: `"Kế toán · Quản lý"`.
 * Danh sách rỗng ⇒ `"Chưa có vai trò"` (đúng nhánh rỗng của hợp đồng).
 */
fun rolesLabelOf(roles: List<String>): String {
    val labels = roles.map { it.trim() }.filter { it.isNotEmpty() }.map { roleLabelOf(it) }
    return if (labels.isEmpty()) "Chưa có vai trò" else labels.joinToString(" · ")
}
