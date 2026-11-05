package vn.lab.logistics.feature.admin

import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.model.LabRole
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.core.model.roleLabelOf

/**
 * =====================================================================================
 * CÂU CHỮ CỦA MÀN **CHUYỂN TÀI KHOẢN** (hàm THUẦN, không Compose — test được offline).
 *
 * Định dạng dòng chọn đã chốt với Orchestrator: `"Trần Văn Hùng · taixe01 · driver"`
 * — **tên · tài khoản · mã vai trò**. Mã vai trò để nguyên (không dịch) vì đây là màn của
 * QUẢN TRỊ VIÊN: họ đang đối chiếu với nhóm quyền trong Odoo, nơi mã là `driver`/`cs`/`kho`.
 * Nhãn tiếng Việt của màn đích nằm ở dòng thứ hai ("Mở màn: …") để người xem video hiểu ngay.
 * =====================================================================================
 */

/**
 * Dòng chọn MỘT dòng: `"Trần Văn Hùng · taixe01 · driver"`.
 *
 * Ba phần đều có thể vắng từ phía server nhưng KHÔNG được để lại dấu "·" treo:
 *  - tên vắng ⇒ model đã fallback về `login` (`DemoAccount.name` không bao giờ rỗng);
 *  - vai trò vắng ⇒ `"chưa có vai trò"` (đúng nhánh hợp đồng, không bịa `driver`).
 *
 * Tên hàm là `accountLabel` (không phải `displayLabel`) để KHÔNG trùng tên với extension của
 * `CsDriver` ở `feature.cs`: hai loại dòng chọn khác nhau, đừng để người đọc nhầm.
 */
fun DemoAccount.accountLabel(): String = "$name · $login · ${rolesCode()}"

/** Mã vai trò đọc được: `"driver"` · `"sep, manager"` · `"chưa có vai trò"`. */
fun DemoAccount.rolesCode(): String =
    roles.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        .ifBlank { "chưa có vai trò" }

/** Dòng phụ: màn mà app sẽ mở nếu chuyển sang tài khoản này (`Workspace.label`). */
fun DemoAccount.targetScreenLabel(): String = "Mở màn: " + targetWorkspace.label

/** Tài khoản này có màn thao tác trên app không (khác nhánh "vai trò khác"). */
fun DemoAccount.opensAppScreen(): Boolean = targetWorkspace.hasAppScreen

/**
 * Tìm nhanh theo tên / tài khoản / mã vai trò. Không phân biệt hoa/thường và bỏ khoảng trắng
 * hai đầu; chuỗi rỗng ⇒ khớp tất cả (ô tìm trống = xem hết danh sách).
 *
 * Vì sao tìm cả mã vai trò: khi quay video 8 vai trò, gõ `cs` hay `kho` nhanh hơn nhiều so với
 * nhớ tên tài khoản demo.
 */
fun DemoAccount.matchesQuery(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val ten = name.lowercase()
    val taiKhoan = login.lowercase()
    val maVaiTro = roles.joinToString(" ").lowercase()
    return q in ten || q in taiKhoan || q in maVaiTro
}

/**
 * Lọc danh sách theo ô tìm nhanh. Hàm thuần để unit test kiểm được offline và để ViewModel chỉ
 * giữ state, không chứa logic chuỗi.
 */
fun filterAccounts(accounts: List<DemoAccount>, query: String): List<DemoAccount> =
    if (query.isBlank()) accounts else accounts.filter { it.matchesQuery(query) }

/**
 * Nhãn màn đích của một danh sách vai trò — dùng cho câu xác nhận sau khi chuyển
 * ("Đã chuyển sang tài khoản taixe01 — màn Tài xế — việc hôm nay").
 */
fun workspaceLabelOf(roles: List<String>): String = Workspace.fromRoles(roles).label

// =====================================================================================
// NÚT MỞ DROPDOWN CHỌN NHANH TÀI KHOẢN (yêu cầu của sếp: "làm nút button chuyển đổi
// dropdown xuống để dễ chọn")
//
// Các hằng/hàm dưới đây là TOÀN BỘ phần chữ của dropdown, tách khỏi Compose để unit test khoá
// được offline: nhãn dài quá 1 dòng là lỗi hiển thị thật (nút bị cắt, người dùng không biết đang
// chọn ai), nên nó phải được assert chứ không chỉ nhìn bằng mắt.
// =====================================================================================

/** Mũi tên "mở dropdown xuống" bằng ký tự — app chốt Material 2, không có icon library offline. */
const val PICKER_ARROW: String = "▾"

/** Nhãn nút khi CHƯA chọn tài khoản nào. */
const val PICKER_EMPTY_LABEL: String = "Chọn tài khoản $PICKER_ARROW"

/**
 * Nhãn NÚT mở dropdown, đúng 1 dòng:
 *  - chưa chọn (hoặc toàn khoảng trắng) ⇒ `"Chọn tài khoản ▾"`;
 *  - đã chọn ⇒ `"Đã chọn: <login> ▾"` — để người dùng biết đang trỏ vào tài khoản NÀO ngay trên
 *    nút, không phải cuộn xuống đọc lại dòng "Đang chọn:" ở dưới.
 *
 * Dùng `login` chứ không dùng `name`: tên hiển thị của Odoo hay kèm hậu tố ("(tài xế)") và có thể
 * dài; `login` là thứ ngắn, duy nhất và là thứ phải gõ khi đăng nhập.
 */
fun pickerLabel(selectedLogin: String?): String {
    val login = selectedLogin?.trim().orEmpty()
    return if (login.isEmpty()) PICKER_EMPTY_LABEL else "Đã chọn: $login $PICKER_ARROW"
}

/** Dấu đánh dấu dòng ĐANG CHỌN trong menu (Material 2 không có radio sẵn cho menu). */
const val PICKER_CHECK_MARK: String = "✓"

/**
 * Nhãn MỘT DÒNG trong menu dropdown: dấu `✓` ở đầu khi đang chọn, rồi [accountLabel]
 * ("Trần Văn Hùng (tài xế) · taixe01 · driver"). Dòng phụ (`targetScreenLabel`) do màn hình vẽ.
 */
fun DemoAccount.pickerMenuLabel(isSelected: Boolean): String =
    if (isSelected) "$PICKER_CHECK_MARK ${accountLabel()}" else accountLabel()

/**
 * Danh sách cho MENU chọn nhanh: **TOÀN BỘ** tài khoản đã tải, KHÔNG lọc theo ô tìm.
 *
 * Vì sao khác [filterAccounts]: ô tìm thu hẹp danh sách CHI TIẾT bên dưới, còn menu là lối chọn
 * nhanh "toàn bộ" — người dùng đang gõ tìm một người vẫn phải mở menu ra chọn được người khác mà
 * không phải xoá ô tìm trước. Tách hàm để luật này nằm ở một chỗ và test được.
 */
fun pickerAccounts(accounts: List<DemoAccount>): List<DemoAccount> = accounts

// =====================================================================================
// NGÂN SÁCH CHIỀU CAO Ở MÀN HẸP (360×640 dp = AVD 720×1280)
//
// Đo thật trên AVD: vùng nội dung còn 486dp. Bản đầu tốn toolbar ~129dp + ô tìm 80dp + thanh chọn
// 160dp + 1 đường kẻ = 370dp ⇒ danh sách chỉ còn ~116dp ⇒ **2 dòng** (dòng 67dp) — quá ít để chọn.
// Ba luật dưới đây (đều là hàm/cờ thuần, test được offline) giành lại chỗ cho danh sách:
//   1. phụ đề toolbar NGẮN ở compact ([adminToolbarSubtitle]) — câu đầy đủ bị cắt giữa từ;
//   2. ô tìm đệm dọc 12→4dp (80→64dp); thanh chọn BỎ dòng "Đang chọn:" (nút picker đã in
//      "Đã chọn: <login> ▾") và đệm 12→8dp ⇒ thanh còn 128dp (tổng -48dp);
//   3. dòng danh sách chỉ 1 dòng nhãn + đệm 8dp ⇒ **44dp** mỗi dòng thay vì 67dp.
// Kết quả kỳ vọng: 486 − 129 − 64 − 128 − 1 = **164dp** cho danh sách ⇒ **3,7 dòng 44dp**
// (thấy trọn 3 dòng + một phần dòng thứ 4) thay vì 2 dòng như trước.
// =====================================================================================

/**
 * Dòng phụ của toolbar màn Chuyển tài khoản.
 *
 * `compact` (màn hẹp, < 700dp như AVD 360×640): câu NGẮN `"Chỉ admin · đang là admin · Odoo thật"`.
 * Vì sao phải rút gọn: `LabToolbar` chỉ cho phụ đề 1 dòng và cắt bằng ellipsis — câu đầy đủ bị cắt
 * GIỮA TỪ ("… đang là a…"), vừa xấu vừa không đọc được. Màn rộng giữ câu đầy đủ giải thích màn này
 * là gì cho người mở lần đầu.
 *
 * `currentLogin` rỗng ⇒ bỏ hẳn vế "đang là …", không để dấu "·" treo.
 */
fun adminToolbarSubtitle(currentLogin: String, dataSourceLabel: String, compact: Boolean): String {
    val login = currentLogin.trim()
    return if (compact) {
        buildString {
            append("Chỉ admin")
            if (login.isNotEmpty()) append(" · đang là $login")
            append(" · $dataSourceLabel")
        }
    } else {
        buildString {
            append("Chỉ quản trị viên · chọn tài khoản để test nhanh")
            if (login.isNotEmpty()) append(" · đang là $login")
            append(" · Nguồn: $dataSourceLabel")
        }
    }
}

// =====================================================================================
// NÚT CHUYỂN VAI TRÒ Ở THANH TRÊN CÙNG (yêu cầu của sếp: LUÔN hiện khi phiên được phép
// chuyển vai trò, kể cả khi đang đứng ở màn của tài khoản vừa được admin chuyển sang)
//
// Vì sao nút còn hiện sau khi admin chuyển sang `taixe01`: BE-01 trả `canSwitch = true` cho phiên
// do `/admin/switch` tạo, và 2 endpoint admin vẫn 200 với phiên đó. Nếu app suy ra quyền từ `roles`
// (chỉ còn `driver`) thì nút sẽ biến mất ngay sau lần chuyển đầu — đúng cái sếp không muốn.
// Vì sao nút MẤT khi đăng nhập thẳng bằng tài khoản thường: `/auth/login` trả `canSwitch = false`,
// server trả 403 cho 2 endpoint admin — không có gì để chuyển nên không hiện nút.
// =====================================================================================

/**
 * Điều kiện DUY NHẤT để hiện nút chuyển vai trò: server cho phép **và** đang có phiên còn hạn.
 *
 * Hàm thuần để test khoá hành vi: không dựa vào `roles`, không dựa vào màn đang mở.
 */
fun shouldShowRoleSwitcher(canSwitch: Boolean, hasValidSession: Boolean): Boolean =
    canSwitch && hasValidSession

/**
 * Nhãn NGẮN của một vai trò cho nút ở thanh trên (chỗ hẹp, 1 dòng):
 * `"Tài xế"` · `"Trưởng nhóm"` · `"Kho"` · `"Tổng quan"` · `"Quản trị viên"`.
 *
 * Vai trò khác hiện đúng nhãn tiếng Việt của mã đó — `cs` ⇒ `"CS — điều phối (trên web)"`,
 * `ketoan` ⇒ `"Kế toán"`; chưa có vai trò ⇒ `"Chuyển vai trò"`. Luôn có chữ để bấm.
 *
 * ⚠️ `cs` KHÔNG còn màn riêng (D1, 2026-11-02) nên nó rơi vào nhánh `Unsupported` — nhãn vì vậy
 * phải nói rõ "(trên web)" để người demo hiểu app không thiếu màn, chỉ là CS làm ở web.
 */
fun shortRoleLabel(roles: List<String>): String = when (Workspace.fromRoles(roles)) {
    Workspace.Driver -> "Tài xế"
    Workspace.DriverLeader -> "Trưởng nhóm"
    Workspace.Kho -> "Kho"
    Workspace.Dashboard -> "Tổng quan"
    Workspace.Admin -> "Quản trị viên"
    Workspace.Unsupported -> {
        val ma = roles.map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val nhan = ma?.let { roleLabelOf(it) }.orEmpty()
        if (nhan.isBlank() || nhan == "Chưa có vai trò") "Chuyển vai trò" else nhan
    }
}

/** Nhãn trên nút ở thanh trên cùng: `"Tài xế ▾"`. Màn hình không tự ghép chuỗi này. */
fun switcherLabel(roles: List<String>): String = shortRoleLabel(roles) + " " + PICKER_ARROW

// =====================================================================================
// THỨ TỰ DÒNG TRONG MENU CHUYỂN VAI TRÒ
//
// Vấn đề đo được trên AVD (ảnh `app-switch-2-menu.png`): `/admin/accounts` trả theo `login` nên
// menu mở ra là `chungtu01 · cs01 · ketoan01 · kho01 · manager01 · nhomtruong01 · sep…` — BA tài
// khoản tài xế nằm cuối, phải cuộn mới thấy. Trong khi luồng demo (và luồng điều hướng của app)
// là **admin → tài xế → CS**: "bấm 1 lần" mà phải cuộn thì mất đúng ý nghĩa của nút.
//
// Nên menu tự sắp theo THỨ TỰ VAI TRÒ ĐIỀU HƯỚNG, cùng vai trò thì theo `login`:
//   driver → driver_leader → cs → kho → ketoan → manager → sep → admin → còn lại
// CHỈ sắp ở UI của menu này: KHÔNG đổi `AdminAccountsRepo`, không đổi danh sách của màn chi tiết
// (màn chi tiết giữ thứ tự server trả để còn đối chiếu với Odoo).
// =====================================================================================

/** Thứ tự vai trò trong menu — theo đúng thứ tự điều hướng của app và luồng demo.
 *
 * `cs` vẫn nằm trong danh sách: tài khoản CS VẪN là tài khoản demo cần bấm tới (để thấy màn
 * "Vai trò này làm việc trên Odoo web"), chỉ là app không mở màn làm việc cho nó (D1).
 */
private val THU_TU_VAI_TRO_MENU: List<String> = listOf(
    LabRole.DRIVER,
    LabRole.DRIVER_LEADER,
    LabRole.CS,
    LabRole.KHO,
    LabRole.KETOAN,
    LabRole.MANAGER,
    LabRole.SEP,
    LabRole.ADMIN,
)

/**
 * Hạng của một tài khoản trong menu, tính theo **mã vai trò ĐẦU TIÊN** của dòng đó (cùng quy ước
 * với `Workspace.fromRoles`: server trả vai trò theo thứ tự ưu tiên của nó).
 *
 * Mã lạ hoặc danh sách rỗng ⇒ hạng cuối (`Int.MAX_VALUE`) — không bị ẩn, chỉ nằm dưới cùng.
 */
fun hangVaiTroMenu(roles: List<String>): Int {
    val ma = roles.map { it.trim().lowercase() }.firstOrNull { it.isNotEmpty() }
    if (ma == null) return Int.MAX_VALUE
    val hang = THU_TU_VAI_TRO_MENU.indexOf(ma)
    return if (hang >= 0) hang else Int.MAX_VALUE
}

/**
 * Sắp lại danh sách cho MENU chuyển vai trò: theo [hangVaiTroMenu] rồi theo `login`.
 *
 * Hàm THUẦN (không phụ thuộc Compose/mạng) để test khoá được đúng thứ tự người dùng sẽ thấy —
 * thứ tự này là một phần của UX "1 click", không phải chi tiết trang trí.
 */
fun sapXepMenuTaiKhoan(accounts: List<DemoAccount>): List<DemoAccount> =
    accounts.sortedWith(
        compareBy({ hangVaiTroMenu(it.roles) }, { it.login.trim().lowercase() }),
    )

/**
 * Nhãn dòng "đang đăng nhập" ở ĐẦU menu, chỉ dùng khi tài khoản hiện tại KHÔNG có trong danh sách
 * chuyển được (thực tế: `admin` không nằm trong whitelist của `/admin/accounts`).
 *
 * Định dạng đã chốt: `✓ Quản trị viên (admin) — đang đăng nhập` — để người xem video biết đang
 * đứng ở đâu, thay vì thấy một menu không có dòng nào được đánh dấu.
 */
fun dongDangDungLabel(roles: List<String>, login: String): String {
    val vaiTro = shortRoleLabel(roles)
    val taiKhoan = login.trim().ifEmpty { "?" }
    return "$PICKER_CHECK_MARK $vaiTro ($taiKhoan) — đang đăng nhập"
}

// =====================================================================================
// CHẶN KÍCH THƯỚC MENU CHỌN TÀI KHOẢN — một chỗ DUY NHẤT cho cả menu của màn chi tiết và menu
// của nút chuyển vai trò ở thanh trên (hai nơi, cùng một kích thước ⇒ cùng một trải nghiệm).
//
// Vì sao phải chặn: menu liệt kê 8 tài khoản × 2 dòng (nhãn + "Mở màn: …") ⇒ cao hơn màn 360×640
// rất nhiều nếu không chặn. Material 2 không tự giới hạn chiều cao popup, nên app chặn theo kích
// thước KHẢ DỤNG rồi cho menu tự cuộn.
// =====================================================================================

/** Bề rộng menu: bằng vùng nội dung trừ 48dp (padding hai bên), kẹp 200–420dp. */
fun accountMenuWidthDp(availableWidthDp: Int): Int =
    (availableWidthDp - 48).coerceIn(200, 420)

/** Trần chiều cao menu: 55% chiều cao vùng nội dung, kẹp 160–420dp. */
fun accountMenuMaxHeightDp(availableHeightDp: Float): Int =
    (availableHeightDp * 0.55f).toInt().coerceIn(160, 420)

