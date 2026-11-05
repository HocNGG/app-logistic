package vn.lab.logistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.LabRole
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.core.model.roleLabelOf
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.LoginSession
import vn.lab.logistics.core.net.OdooClient
import vn.lab.logistics.core.net.newOdooHttpClient
import vn.lab.logistics.core.repo.AdminAccountsRepo
import vn.lab.logistics.core.repo.CsJobsRepo
import vn.lab.logistics.core.repo.DriverJobsRepo
import vn.lab.logistics.core.repo.DriverWorkRepo
import vn.lab.logistics.core.repo.KhoRepo
import vn.lab.logistics.core.repo.LoginRepo
import vn.lab.logistics.core.repo.SepDashboardRepo
import vn.lab.logistics.core.repo.mock.DemoScenario
import vn.lab.logistics.core.repo.mock.MockAdminAccountsRepo
import vn.lab.logistics.core.repo.mock.MockCsRepo
import vn.lab.logistics.core.repo.mock.MockDriverJobsRepo
import vn.lab.logistics.core.repo.mock.MockDriverWorkRepo
import vn.lab.logistics.core.repo.mock.MockDriverWorkSession
import vn.lab.logistics.core.repo.mock.MockKhoRepo
import vn.lab.logistics.core.repo.mock.MockLoginRepo
import vn.lab.logistics.core.repo.mock.MockOpsSession
import vn.lab.logistics.core.repo.mock.MockSepDashboardRepo
import vn.lab.logistics.core.repo.real.RealAdminAccountsRepo
import vn.lab.logistics.core.repo.real.RealCsRepo
import vn.lab.logistics.core.repo.real.RealDriverJobsRepo
import vn.lab.logistics.core.repo.real.RealDriverWorkRepo
import vn.lab.logistics.core.repo.real.RealKhoRepo
import vn.lab.logistics.core.repo.real.RealLoginRepo
import vn.lab.logistics.core.repo.real.RealSepDashboardRepo
import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.feature.admin.RoleSwitcher
import vn.lab.logistics.feature.admin.SwitchAccountScreen
import vn.lab.logistics.feature.admin.SwitchAccountViewModel
import vn.lab.logistics.feature.admin.accountMenuMaxHeightDp
import vn.lab.logistics.feature.admin.accountMenuWidthDp
import vn.lab.logistics.feature.admin.shouldShowRoleSwitcher
import vn.lab.logistics.feature.capture.DriverCaptureScreen
import vn.lab.logistics.feature.capture.DriverCaptureViewModel
import vn.lab.logistics.feature.capture.toSnackbarMessage
import vn.lab.logistics.feature.cs.CsDispatchScreen
import vn.lab.logistics.feature.cs.CsDispatchViewModel
import vn.lab.logistics.feature.dashboard.SepDashboardScreen
import vn.lab.logistics.feature.dashboard.SepDashboardViewModel
import vn.lab.logistics.feature.driver.DriverJobsScreen
import vn.lab.logistics.feature.driver.DriverJobsViewModel
import vn.lab.logistics.feature.kho.KhoReceiveScreen
import vn.lab.logistics.feature.kho.KhoXuatViewModel
import vn.lab.logistics.feature.kho.KhoXuatScreen
import vn.lab.logistics.feature.kho.KhoReceiveViewModel
import vn.lab.logistics.feature.login.LoginScreen
import vn.lab.logistics.feature.login.LoginViewModel
import vn.lab.logistics.feature.role.UnsupportedRoleScreen
import vn.lab.logistics.feature.welcome.WelcomeScreen
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.demo.DemoSettingsDialog
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabEnter
import vn.lab.logistics.ui.kit.LabGearIcon
import vn.lab.logistics.ui.kit.LabNavGroup
import vn.lab.logistics.ui.kit.LabNavItem
import vn.lab.logistics.ui.kit.LabSidebar
import vn.lab.logistics.ui.kit.LabTopNav
import vn.lab.logistics.ui.kit.LocalReducedMotion

/**
 * =====================================================================================
 * GỐC GIAO DIỆN — KHUNG ỨNG DỤNG (app shell) + điều hướng theo VAI TRÒ.
 *
 * Route bằng STATE CỤC BỘ (`Route` enum), KHÔNG dùng navigation library: Wave A build offline,
 * `navigation-compose` không có trong cache → thêm vào là vỡ build. Khi có navigation thật,
 * `Route` này thay bằng NavHost; danh sách màn hình không đổi.
 *
 * ============================ CHỌN MÀN THEO VAI TRÒ (roles) ==========================
 * Sau khi đăng nhập (và khi mở app lúc token còn hạn), app chọn màn theo `roles` mà server trả:
 * **ưu tiên admin → driver → driver_leader → cs → kho → dashboard**, vai trò khác (`ketoan`)
 * hoặc danh sách rỗng ⇒ màn thông báo "Vai trò này làm việc trên Odoo web". Quy tắc nằm DUY
 * NHẤT ở `Workspace.fromRoles` (`core/model/Workspace.kt`) để unit test JVM kiểm được offline.
 *
 * `admin` (quản trị viên) mở màn **CHUYỂN TÀI KHOẢN** (`SwitchAccountScreen`): chọn một tài khoản
 * demo trong danh sách là app ghi phiên mới rồi mở đúng màn của vai trò đó — cùng một đường đi
 * như vừa đăng nhập, nên test 8 vai trò không phải đăng xuất/gõ lại mật khẩu 8 lần. Tài khoản
 * KHÔNG có mã `admin` không bao giờ thấy màn này (server cũng trả 403 ở hai endpoint admin).
 *
 * ================== NÚT CHUYỂN VAI TRÒ Ở THANH TRÊN CÙNG (yêu cầu của sếp) =================
 * Ngoài màn chi tiết, có **một nút dropdown** (`RoleSwitcher`) gắn ở thanh trên cùng — chân
 * sidebar trên màn rộng, `LabTopNav.trailing` trên điện thoại. Bấm MỘT dòng là đổi phiên ngay.
 *
 *  - Nó LUÔN hiện khi `AppSettings.canSwitch` = `true` và còn phiên: đăng nhập bằng `admin`, VÀ cả
 *    khi đang đứng ở màn của tài khoản do admin chuyển sang (`taixe01` vẫn thấy nút để sang `cs01`
 *    mà không phải quay lại màn Chuyển tài khoản). BE-01 trả `canSwitch = true` cho phiên do
 *    `/admin/switch` tạo và hai endpoint admin vẫn 200 với phiên đó, nên app chỉ việc gọi lại repo.
 *  - Nó MẤT trong ĐÚNG một trường hợp: đăng nhập thẳng bằng tài khoản thường — `/auth/login` trả
 *    `canSwitch = false` (server cũng 403), không có gì để chuyển. Vì vậy điều kiện hiện nút là
 *    `canSwitch` của SERVER, KHÔNG suy ra từ `roles` (suy từ `roles` thì nút biến mất ngay sau lần
 *    chuyển đầu, vì lúc đó `roles` chỉ còn `driver`).
 *  - Cả nút dropdown và màn chi tiết đi qua `MỘT` đường: `chuyenPhien(login)` →
 *    `adminVm.switchTo` → `apDungPhien` (ghi Settings + điều hướng + snackbar).
 *
 * `driver_leader` (trưởng nhóm tài xế — người PHÂN CÔNG) dùng LẠI `CsDispatchScreen`: cùng
 * endpoint `cs/jobs`, cùng thao tác `assign`, chỉ khác tiêu đề ("Phân công tài xế") và dòng phụ
 * ("<tên> · <tài khoản>"). Không có màn thứ hai cho cùng một nghiệp vụ.
 *
 * ============================ KHUNG NHÌN: 2 KIỂU NAV =================================
 *  - **Desktop (≥ 900dp)**: nav DỌC bên trái (`LabSidebar`) — wordmark + nhóm mục + item đang mở
 *    có nền nhấn 10% và vạch trái 3dp (spec `ui-dashboard-app-screens.md` §1). Nội dung nằm
 *    trong cột căn giữa, bề rộng tối đa [Lab.CONTENT_MAX_WIDTH_DP] nên không "kéo hết màn 27
 *    inch" — đây là thứ làm app trông như sản phẩm thay vì mockup.
 *  - **Điện thoại/tablet hẹp (< 900dp)**: nav NGANG 2 hàng (`LabTopNav`) — wordmark + hàng tab
 *    chia đều; điện thoại (< 600dp) không có nav dọc vì nó chiếm 60% màn 390dp.
 *
 * ====================== CÔNG TẮC DEMO: CHỈ CÒN SAU NÚT "CÀI ĐẶT DEMO" =================
 * Trước đây mọi công tắc (nguồn dữ liệu, vai trò demo, kịch bản, nền tối, giảm chuyển động, bề
 * rộng giả lập) nằm trên một thanh cao 2–3 hàng ở đỉnh cửa sổ — nhìn ra ngay "mockup". Nay:
 *  - màn chính chỉ còn nav + nội dung nghiệp vụ;
 *  - công tắc nằm trong [DemoSettingsDialog], mở bằng nút **Cài đặt demo** (icon bánh răng vẽ
 *    bằng Canvas) ở chân sidebar — CHỈ desktop;
 *  - điện thoại không có màn này: công tắc nguồn dữ liệu duy nhất cần thiết vẫn nằm trong màn
 *    Đăng nhập (`onToggleDataSource`), đúng như bản Android trước đây.
 *  - **không còn công tắc "Nền tối"**: app chỉ có MỘT chế độ màu (tối, ADR-009 §6) — `AppTheme`
 *    không nhận tham số chế độ nữa nên không thể có màn nào vẽ bằng bảng màu khác.
 * Khả năng demo offline (Luật 8) KHÔNG mất: đó là mục đích duy nhất của màn cài đặt này.
 *
 * ============================ NGUỒN DỮ LIỆU (Mẫu ⟷ Odoo thật) =========================
 * Công tắc `AppSettings.useRealApi` (màn Cài đặt demo trên desktop, màn Đăng nhập trên điện
 * thoại):
 *  - **Mẫu**: `MockLoginRepo` + `MockDriverJobsRepo` + `MockCsRepo` + `MockKhoRepo` +
 *    `MockSepDashboardRepo` + `MockAdminAccountsRepo` — không gọi mạng, demo an toàn khi Odoo
 *    chưa bật / DB chưa seed.
 *  - **Odoo thật**: 6 repo thật gọi địa chỉ trong Settings (người dùng sửa được ở màn Đăng nhập).
 * Mặc định theo nền tảng + lưu bền: desktop = **Mẫu** (giữ hành vi demo cũ), Android = **Odoo
 * thật** (app trên điện thoại là app thật).
 *
 * Quyết định chọn impl nằm DUY NHẤT ở đây — màn hình không có `if (demo)` nào
 * (`agents/fe/AGENT.md` §3.2: mock phải nằm sau interface `Repository`, bật bằng flag).
 * Khi API thật lỗi, mọi màn hiện khối lỗi + nút **"Về dữ liệu mẫu"** (Luật 8).
 *
 * @param settings cấu hình dùng chung. `main.kt` (desktop) truyền bản ghi ra tệp; nếu `null`
 *        (preview/test) thì dùng bản chỉ trong RAM — không ghi gì xuống đĩa.
 * Không còn tham số chọn chế độ màu: app chỉ có MỘT chế độ (tối, ADR-009 §6).
 * =====================================================================================
 */
private enum class Route(val label: String, val shortLabel: String) {
    Welcome("Giới thiệu", "Giới thiệu"),
    Login("Đăng nhập", "Đăng nhập"),
    // `shortLabel` dùng cho nav NGANG của điện thoại (mỗi tab chỉ rộng ~1/3 màn hình).
    Driver("Tài xế · Việc hôm nay", "Tài xế"),
    // Trưởng nhóm tài xế = NGƯỜI PHÂN CÔNG: dùng LẠI màn của CS, chỉ khác tiêu đề/phụ đề
    // (xem `CsDispatchScreen` — không nhân bản màn thứ hai cho cùng một thao tác).
    DriverLeader("Trưởng nhóm tài xế · Phân công", "Phân công"),
    // ⚠️ KHÔNG còn route cho CS (quyết định D1, 2026-11-02): CS làm việc trên Odoo web
    // (tạo khách, tạo yêu cầu lấy hàng, gán tài xế, gom chuyến). Tài khoản `cs01` mở app sẽ
    // thấy màn "Vai trò khác" giải thích — xem `Workspace.fromRoles`.
    Kho("Kho · Nhận hàng", "Kho"),
    // MÀN CON của Kho: hai mốc của cùng chuỗi tem (nhận / xuất). Không phải tab thứ 4 của nav —
    // mở từ nút ở thanh trên của màn Kho, xem onOpenXuat / onOpenNhan bên dưới.
    KhoXuat("Kho · Xuất kho", "Xuất kho"),
    Dashboard("Sếp · Tổng quan", "Tổng quan"),
    // CHỈ QUẢN TRỊ VIÊN: công cụ test nhanh 8 vai trò (chuyển tài khoản, không cần đăng xuất).
    Admin("Chuyển tài khoản", "Chuyển TK"),
    Unsupported("Vai trò khác", "Vai trò"),
}

/**
 * Màn hình của một vai trò. Quy tắc chọn màn nằm DUY NHẤT ở `Workspace.fromRoles` (hàm thuần,
 * có unit test `RoleRoutingTest`) — `App.kt` chỉ đổi kết quả đó thành route, không tự so chuỗi.
 */
private fun routeOf(workspace: Workspace): Route = when (workspace) {
    Workspace.Driver -> Route.Driver
    Workspace.DriverLeader -> Route.DriverLeader
    Workspace.Kho -> Route.Kho
    Workspace.Dashboard -> Route.Dashboard
    Workspace.Admin -> Route.Admin
    Workspace.Unsupported -> Route.Unsupported
}

/**
 * Các route CÓ chỗ hiện "Snackbar một lần" (`flashMessage` + `onFlashShown`).
 *
 * Dùng để biết có nên đặt câu xác nhận sau khi chuyển tài khoản hay không: đặt vào màn không có
 * chỗ hiện thì câu đó nằm chờ trong state và sẽ bật ra ở một màn khác sau này — sai thời điểm.
 */
private val FLASH_ROUTES: Set<Route> = setOf(
    Route.Driver,
    Route.DriverLeader,
    Route.Kho,
    // Màn con của Kho cũng có snackbar (câu "Đã xuất N kiện · phiếu WH/OUT/…").
    Route.KhoXuat,
)

/**
 * 3 mục nav: **Giới thiệu · Đăng nhập · màn của vai trò**.
 *
 * Vì sao không liệt kê cả 6 route: mỗi mục phải chừa ≥ 44dp vùng chạm, 6 mục trên màn 360dp là
 * 60dp/mục — chữ "Đăng nhập" sẽ bị cắt cụt. Ba mục đúng với mô hình sản phẩm: một tài khoản chỉ
 * làm việc ở MỘT màn (quyết định bởi vai trò), còn hai mục kia là cửa vào/đăng xuất.
 */
private fun navRoutes(workspaceRoute: Route): List<Route> =
    listOf(Route.Welcome, Route.Login, workspaceRoute)

/** Nhóm mục của nav dọc. Một nhóm có tên — nhìn như sản phẩm, không phải danh sách phẳng. */
private fun navGroups(routes: List<Route>): List<LabNavGroup> = listOf(
    LabNavGroup(
        title = "Màn hình",
        items = routes.map { LabNavItem(label = it.label, shortLabel = it.shortLabel) },
    ),
)

@Composable
fun App(settings: AppSettings? = null) {
    val appSettings = remember(settings) { settings ?: AppSettings() }

    // Đường lui bắt buộc của mọi animation (docs/11 §5 tiêu chí 3, skill ui-3d-premium §1).
    // Bật trong "Cài đặt demo" → mọi hiệu ứng dừng, nội dung vẫn đủ.
    var reducedMotion by remember { mutableStateOf(false) }
    // Cài đặt demo: lớp phủ, chỉ mở được từ nav dọc của desktop.
    var demoSettingsVisible by remember { mutableStateOf(false) }
    // Bề rộng NỘI DUNG giả lập: 0 = theo cửa sổ (mặc định). Khác 0 = dùng để chứng minh 3 ngưỡng
    // duo UI mà không phải kéo cửa sổ (tính năng demo, chuyển từ thanh demo cũ sang).
    var simulatedWidthDp by remember { mutableStateOf(0) }

    // ---- NGUỒN DỮ LIỆU + VAI TRÒ (đọc SỚM: cả hai quyết định màn mở đầu) -----------------
    val usingRealApi = appSettings.useRealApi
    // VAI TRÒ CỦA CHẾ ĐỘ MẪU: dữ liệu mẫu không có `roles` từ server, nên màn Cài đặt demo có ô
    // chọn vai trò để xem được CẢ 4 màn và cả nhánh "vai trò khác" khi quay video.
    var demoRole by remember { mutableStateOf(LabRole.DRIVER) }
    val effectiveRoles: List<String> = if (usingRealApi) appSettings.roles else listOf(demoRole)
    val workspace = Workspace.fromRoles(effectiveRoles)
    val workspaceRoute = routeOf(workspace)

    // MÀN MỞ ĐẦU: có token còn hạn ⇒ vào THẲNG màn của vai trò. Chưa có/hết hạn ⇒ Welcome.
    var route by remember {
        mutableStateOf(
            if (appSettings.hasValidSession) {
                routeOf(
                    Workspace.fromRoles(
                        if (appSettings.useRealApi) appSettings.roles else listOf(LabRole.DRIVER),
                    ),
                )
            } else {
                Route.Welcome
            },
        )
    }
    // Thu gọn thanh nav cố định (Giới thiệu · Đăng nhập · Role) khi đang làm việc ở màn vai trò
    // để nhường diện tích cho danh sách việc; người dùng bấm "Menu ▾" / "Ẩn ▲" để mở/thu bất kỳ lúc nào.
    var topNavCollapsed by remember { mutableStateOf(appSettings.hasValidSession) }
    var sidebarCollapsed by remember { mutableStateOf(false) }
    var scenario by remember { mutableStateOf(DemoScenario.Normal) }

    // ---- ĐIỀU HƯỚNG MÀN CAPTURE (C3/C4) ------------------------------------------------
    // `captureJob != null` ⇒ đang ở màn capture của việc đó. Đặt ở đây (không trong
    // DriverJobsScreen) để ViewModel capture sống qua mọi recomposition / đổi bề rộng.
    var captureJob by remember { mutableStateOf<DriverJob?>(null) }
    var flashMessage by remember { mutableStateOf<String?>(null) }
    val appScope = rememberCoroutineScope()

    // MỘT HttpClient cho cả app (`HttpCookies` giữ `session_id` trong RAM giữa các request).
    val http = remember { newOdooHttpClient() }
    val odooClient = remember(http, appSettings) { OdooClient(http, appSettings) }
    val labApi = remember(odooClient, appSettings) { LabApi(odooClient, appSettings) }
    val realLoginRepo = remember(odooClient, labApi, appSettings) {
        RealLoginRepo(odooClient, labApi, appSettings)
    }
    val realJobsRepo = remember(labApi, appSettings) { RealDriverJobsRepo(labApi, appSettings) }
    val realWorkRepo = remember(labApi, appSettings) { RealDriverWorkRepo(labApi, appSettings) }
    val realCsRepo = remember(labApi, appSettings) { RealCsRepo(labApi, appSettings) }
    val realKhoRepo = remember(labApi, appSettings) { RealKhoRepo(labApi, appSettings) }
    val realSepRepo = remember(labApi, appSettings) { RealSepDashboardRepo(labApi, appSettings) }
    val realAdminRepo = remember(labApi, appSettings) { RealAdminAccountsRepo(labApi, appSettings) }

    // Bộ nhớ của chế độ Mẫu: danh sách + thao tác ghi dùng CHUNG một phiên, nhờ vậy demo
    // "gửi biên nhận xong state đổi" chạy được cả khi không có Odoo (Luật 8).
    val mockSession = remember(scenario) { MockDriverWorkSession() }
    val mockOpsSession = remember(scenario) { MockOpsSession() }

    // ---- LẮP RÁP: nguồn dữ liệu quyết định impl repo (màn hình không biết điều này) ----
    val loginRepo: LoginRepo = remember(usingRealApi, scenario, realLoginRepo) {
        if (usingRealApi) realLoginRepo else MockLoginRepo(scenario)
    }
    val jobsRepo: DriverJobsRepo =
        remember(usingRealApi, scenario, mockSession, realJobsRepo) {
            if (usingRealApi) realJobsRepo else MockDriverJobsRepo(scenario, mockSession)
        }
    val workRepo: DriverWorkRepo =
        remember(usingRealApi, scenario, mockSession, realWorkRepo) {
            if (usingRealApi) realWorkRepo else MockDriverWorkRepo(scenario, mockSession)
        }
    val csRepo: CsJobsRepo = remember(usingRealApi, scenario, mockOpsSession, realCsRepo) {
        if (usingRealApi) realCsRepo else MockCsRepo(scenario, mockOpsSession)
    }
    val khoRepo: KhoRepo = remember(usingRealApi, scenario, mockOpsSession, realKhoRepo) {
        if (usingRealApi) realKhoRepo else MockKhoRepo(scenario, mockOpsSession)
    }
    val sepRepo: SepDashboardRepo = remember(usingRealApi, scenario, realSepRepo) {
        if (usingRealApi) realSepRepo else MockSepDashboardRepo(scenario)
    }
    val adminRepo: AdminAccountsRepo = remember(usingRealApi, scenario, realAdminRepo) {
        if (usingRealApi) realAdminRepo else MockAdminAccountsRepo(scenario)
    }
    val loginVm = remember(loginRepo, appSettings) { LoginViewModel(loginRepo, appSettings) }
    // ĐỔI PHIÊN (đăng nhập / chuyển tài khoản) ⇒ token đổi ⇒ tạo LẠI các ViewModel dữ liệu.
    // Không làm vậy, màn của tài khoản mới sẽ hiện dữ liệu đã tải cho tài khoản cũ (rò rỉ dữ
    // liệu + demo sai) vì `loadIfNeeded` không tải lại.
    val sessionToken: String = appSettings.token
    val jobsVm = remember(jobsRepo, workRepo, sessionToken) {
        DriverJobsViewModel(jobsRepo, workRepo)
    }
    val csVm = remember(csRepo, sessionToken) { CsDispatchViewModel(csRepo) }
    val khoVm = remember(khoRepo, sessionToken) { KhoReceiveViewModel(khoRepo) }
    // Màn con "Kho · Xuất kho" dùng CÙNG repo kho (2 mốc của cùng chuỗi tem) nhưng state riêng:
    // gộp state sẽ làm danh sách kiện đã quét của màn này lẫn sang màn kia.
    val khoXuatVm = remember(khoRepo, sessionToken) { KhoXuatViewModel(khoRepo) }
    val sepVm = remember(sepRepo, sessionToken) { SepDashboardViewModel(sepRepo) }
    val adminVm = remember(adminRepo, sessionToken) { SwitchAccountViewModel(adminRepo) }
    // ViewModel capture chỉ tồn tại khi đang capture ⇒ mở lại là phiên MỚI (clientUuid mới),
    // còn "Thử lại" trong cùng màn thì giữ nguyên uuid (C5 — `feature/capture/CaptureSession.kt`).
    val captureVm = remember(captureJob?.id, workRepo, sessionToken) {
        captureJob?.let { job -> DriverCaptureViewModel(workRepo, job) }
    }

    // Đường lui của Luật 8: chỉ tồn tại khi đang dùng Odoo thật.
    val backToSample: () -> Unit = { appSettings.updateUseRealApi(false) }
    val fallbackToSample: (() -> Unit)? = if (usingRealApi) backToSample else null

    // ----------------------------------------------------------------------------------
    // TÀI KHOẢN ĐANG ĐĂNG NHẬP TRÊN MÁY NÀY — đọc từ Settings (đã lưu sau khi đăng nhập),
    // KHÔNG gọi thêm API. Hiện ở màn tài xế ("Xin chào …" + tài khoản) và ở màn phân công của
    // trưởng nhóm ("<tên> · <tài khoản>"). Máy dùng chung theo ca thì đây là thứ duy nhất nói
    // cho người cầm máy biết mình đang làm việc bằng tài khoản nào.
    // ----------------------------------------------------------------------------------
    val accountName: String = appSettings.driverName.trim()
    val accountLogin: String = appSettings.login.trim()
    // "Trần Văn Hùng · taixe01"; thiếu một vế thì chỉ hiện vế còn lại; cả hai rỗng ⇒ "".
    val accountLine: String = listOf(accountName, accountLogin)
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

    // Nút CHUYỂN VAI TRÒ ở thanh trên cùng chỉ hiện khi SERVER cho phép (`canSwitch`) và còn phiên.
    // Cố ý không dựa vào `roles`: phiên do admin chuyển sang `taixe01` vẫn được chuyển tiếp.
    val hienNutChuyenVaiTro: Boolean =
        shouldShowRoleSwitcher(appSettings.canSwitch, appSettings.hasValidSession)

    // ==================================================================================
    // ĐƯỜNG DUY NHẤT ĐỔI PHIÊN — dùng CHUNG cho màn "Chuyển tài khoản" và nút ở thanh trên
    // ==================================================================================
    // `chuyenPhien(login)` là chỗ DUY NHẤT gọi repo (qua `adminVm.switchTo`), và `apDungPhien` là
    // chỗ DUY NHẤT ghi phiên mới. Nhờ vậy hai lối chuyển (bấm "Chuyển" ở màn chi tiết, hay bấm
    // MỘT dòng trong dropdown) không thể lệch nhau về trạng thái "Đang chuyển…", lỗi hay màn đích.
    //
    // Phiên mới mang `canSwitch` do server trả (luôn `true` sau `/admin/switch`) nên nút dropdown
    // CÒN HIỆN ở màn mới, kể cả khi đang là `taixe01` — đúng yêu cầu của sếp.
    val apDungPhien: (LoginSession) -> Unit = { session ->
        appSettings.saveSession(session)
        // Chế độ Mẫu không đọc `appSettings.roles` (dữ liệu mẫu không có server trả vai trò) ⇒
        // đồng bộ vai trò demo để màn đi theo tài khoản vừa chọn.
        if (!usingRealApi) session.roles.firstOrNull()?.let { demoRole = it }
        val rolesMoi = if (usingRealApi) session.roles else listOf(demoRole)
        val routeMoi = routeOf(Workspace.fromRoles(rolesMoi))
        // Snackbar xác nhận chỉ đặt khi màn đích CÓ chỗ hiện (tránh câu cũ hiện lại muộn ở màn
        // khác — cùng cơ chế `flashMessage` của luồng capture).
        flashMessage = if (routeMoi in FLASH_ROUTES) {
            val ten = session.login.ifBlank { session.userName }.trim()
            val vaiTro = roleLabelOf(rolesMoi.firstOrNull().orEmpty()).lowercase()
            "Đã chuyển sang tài khoản $ten — vai trò $vaiTro."
        } else {
            null
        }
        route = routeMoi
        topNavCollapsed = true
    }
    // Đường 1 CLICK từ dropdown: nhận `login`, đi đúng một đường gọi repo như màn chi tiết.
    val chuyenPhien: (String) -> Unit = { login ->
        // `appScope` (không phải scope của màn) để lời gọi sống qua lần đổi route ngay sau đó.
        adminVm.switchTo(login, appScope, apDungPhien)
    }

    // ----------------------------------------------------------------------------------
    // NỘI DUNG THEO MỤC NAV — gom vào một biến `screen(widthDp)` để 2 kiểu nav dùng chung.
    // `widthDp` là bề rộng THẬT của vùng nội dung ⇒ mỗi màn tự chọn 1-pane / 2-pane theo ngưỡng
    // nghiệp vụ 1280/900 (`DuoArrangement.fromWidth`) — không đoán theo tên thiết bị.
    // ----------------------------------------------------------------------------------
    val screen: @Composable (Int) -> Unit = { widthDp ->
        when (route) {
            Route.Welcome -> WelcomeScreen(
                onEnter = { route = Route.Login },
            )

            Route.Login -> LoginScreen(
                vm = loginVm,
                usingRealApi = usingRealApi,
                // Đăng nhập xong KHÔNG vào màn tài xế cố định nữa: màn hình được chọn theo
                // `roles` server trả (ưu tiên driver → driver_leader → cs → kho → dashboard).
                onLoggedIn = {
                    route = routeOf(
                        Workspace.fromRoles(
                            if (usingRealApi) appSettings.roles else listOf(demoRole),
                        ),
                    )
                    topNavCollapsed = true
                },
                // Điện thoại KHÔNG có màn Cài đặt demo ⇒ công tắc nguồn dữ liệu phải nằm ngay
                // trong màn Đăng nhập, nếu không bản Android không có đường bật "Odoo thật".
                // Desktop truyền `null` → màn Đăng nhập giữ nguyên luồng như cũ.
                onToggleDataSource = if (widthDp < Tokens.BREAKPOINT_COMPACT_DP) {
                    { wantsReal ->
                        appSettings.updateUseRealApi(wantsReal)
                        if (wantsReal && !appSettings.hasToken) route = Route.Login
                    }
                } else {
                    null
                },
            )

            Route.Driver -> {
                val activeCapture = captureVm
                if (activeCapture != null) {
                    // MÀN CAPTURE (C3) — thay cho danh sách; "Quay lại" về danh sách.
                    DriverCaptureScreen(
                        vm = activeCapture,
                        widthDp = widthDp,
                        dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                        sampleMode = !usingRealApi,
                        onBack = { captureJob = null },
                        onSubmitted = { success ->
                            // C4: về danh sách + Snackbar + REFRESH.
                            captureJob = null
                            flashMessage = success.toSnackbarMessage()
                            jobsVm.open(success.jobId)
                            jobsVm.load(appScope)
                        },
                    )
                } else {
                    DriverJobsScreen(
                        vm = jobsVm,
                        widthDp = widthDp,
                        dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                        onFallbackToSample = fallbackToSample,
                        // "Xin chào <tên>" + dòng nhỏ "<tài khoản>" ở góc trên. Rỗng (chế độ mẫu)
                        // ⇒ màn tự giữ tiêu đề "Việc hôm nay", không bịa tên.
                        accountName = accountName,
                        accountLogin = accountLogin,
                        onOpenCapture = { job -> captureJob = job },
                        flashMessage = flashMessage,
                        onFlashShown = { flashMessage = null },
                        onExitToLogin = {
                            loginVm.reset() // xoá mật khẩu khỏi bộ nhớ + xoá token
                            route = Route.Login
                        },
                    )
                }
            }

            // MÀN PHÂN CÔNG — TRƯỞNG NHÓM TÀI XẾ: CÙNG màn với CS, khác tiêu đề/phụ đề.
            // Không có ViewModel riêng: cùng `cs/jobs`, cùng `assign`, cùng luật "server trả
            // trạng thái". Thêm bản sao là thêm một chỗ để lệch khi nghiệp vụ đổi.
            Route.DriverLeader -> CsDispatchScreen(
                vm = csVm,
                widthDp = widthDp,
                dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                onFallbackToSample = fallbackToSample,
                title = "Phân công tài xế",
                // "<tên> · <tài khoản>"; chưa có tài khoản (chế độ mẫu) ⇒ màn dùng dòng mặc định.
                subtitle = accountLine.takeIf { it.isNotBlank() },
                flashMessage = flashMessage,
                onFlashShown = { flashMessage = null },
                onExitToLogin = {
                    loginVm.reset()
                    route = Route.Login
                },
            )

            // ⚠️ KHÔNG còn nhánh `Route.Cs`: CS làm việc trên Odoo web (quyết định D1).
            // Màn `CsDispatchScreen` KHÔNG bị xoá — nó vẫn là màn của trưởng nhóm tài xế ở
            // nhánh `Route.DriverLeader` phía trên (cùng dữ liệu, cùng thao tác gán).

            // MÀN KHO — NHẬN HÀNG: nhận hàng tài xế mang về (`kho/cho-nhan` + `kho/jobs/<id>/nhan`).
            Route.Kho -> KhoReceiveScreen(
                vm = khoVm,
                widthDp = widthDp,
                dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                onFallbackToSample = fallbackToSample,
                flashMessage = flashMessage,
                onFlashShown = { flashMessage = null },
                // Màn con cùng vai trò: giữ nguyên nav 3 tab, chỉ đổi màn nội dung.
                onOpenXuat = { route = Route.KhoXuat },
                onExitToLogin = {
                    loginVm.reset()
                    route = Route.Login
                },
            )

            // MÀN KHO — XUẤT KHO: quét tem xuất + xác nhận hàng rời kho (`kho/cho-xuat`).
            Route.KhoXuat -> KhoXuatScreen(
                vm = khoXuatVm,
                widthDp = widthDp,
                dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                onFallbackToSample = fallbackToSample,
                flashMessage = flashMessage,
                onFlashShown = { flashMessage = null },
                onOpenNhan = { route = Route.Kho },
                onExitToLogin = {
                    loginVm.reset()
                    route = Route.Login
                },
            )

            // MÀN SẾP — TỔNG QUAN: KPI + xu hướng + cơ cấu trạng thái + 10 việc gần nhất.
            Route.Dashboard -> SepDashboardScreen(
                vm = sepVm,
                widthDp = widthDp,
                dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                onFallbackToSample = fallbackToSample,
                onExitToLogin = {
                    loginVm.reset()
                    route = Route.Login
                },
            )

            // QUẢN TRỊ VIÊN — CHUYỂN TÀI KHOẢN: bản CHI TIẾT (ô tìm + danh sách) của cùng một
            // thao tác với nút dropdown ở thanh trên. Cả hai gọi `chuyenPhien` — một đường duy
            // nhất: ghi phiên mới rồi đi theo `Workspace.fromRoles(roles)` như vừa đăng nhập.
            Route.Admin -> SwitchAccountScreen(
                vm = adminVm,
                widthDp = widthDp,
                dataSourceLabel = if (usingRealApi) "Odoo thật" else "Mẫu",
                onFallbackToSample = fallbackToSample,
                currentLogin = accountLogin,
                onSwitchAccount = chuyenPhien,
                onExitToLogin = {
                    loginVm.reset()
                    route = Route.Login
                },
            )

            // VAI TRÒ KHÁC (ketoan hoặc chưa gán vai trò): làm việc trên Odoo web.
            Route.Unsupported -> UnsupportedRoleScreen(
                roles = effectiveRoles,
                baseUrl = appSettings.baseUrl,
                accountName = appSettings.login,
                onLogout = {
                    loginVm.reset()
                    route = Route.Login
                },
            )
        }
    }

    val routes = navRoutes(workspaceRoute)
    val navItems = routes.map { LabNavItem(label = it.label, shortLabel = it.shortLabel) }
    // Màn CON (vd `KhoXuat`) không nằm trong danh sách nav ⇒ giữ highlight ở màn của vai trò,
    // thay vì rơi về index 0 ("Giới thiệu") trông như đang ở sai chỗ.
    val activeIndex = routes.indexOf(route).takeIf { it >= 0 }
        ?: routes.indexOf(workspaceRoute).coerceAtLeast(0)

    AppTheme {
        CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
            Surface(modifier = Modifier.fillMaxSize(), color = AppTheme.lab.ground) {
                // BoxWithConstraints = nguồn bề rộng DUY NHẤT của khung nhìn. Không đọc tên thiết
                // bị, không hard-code kích thước màn hình: chỉ so với ngưỡng của design system.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val availableWidthDp = maxWidth.value.toInt()
                    // Nav dọc chỉ có từ 900dp: dưới ngưỡng đó nó chiếm quá nửa màn hình.
                    val verticalNav = availableWidthDp >= Lab.SIDEBAR_MIN_WIDTH_DP
                    val navWidth = when {
                        !verticalNav -> 0
                        sidebarCollapsed -> 56
                        else -> Lab.NAV_WIDTH_DP
                    }
                    // ⚠️ SỬA LỖI ĐO THẬT 2026-10-28: trước đây chỗ này là
                    // `.coerceAtLeast(Tokens.BREAKPOINT_COMPACT_DP)` ⇒ bề rộng nội dung bị ÉP
                    // lên đúng bằng ngưỡng compact, nên `compact = widthDp < breakpoint` LUÔN
                    // SAI. Hệ quả trên điện thoại: thanh trên xếp hàng ngang, tiêu đề bị cắt
                    // ("Việc h…"), hiện cả "Phím tắt (?)" — xem `app-02-driver-list.png` bản cũ;
                    // nhãn nguồn dữ liệu cũng in "600 dp" (chính là ngưỡng, không phải bề rộng
                    // thật). Chỉ cần sàn nhỏ để tránh chia cho 0 khi cửa sổ bị thu cực nhỏ.
                    val contentAvailableDp = (availableWidthDp - navWidth).coerceAtLeast(240)
                    // Bề rộng nội dung: theo cửa sổ (mặc định) hoặc bề rộng giả lập của màn Cài
                    // đặt demo, và không bao giờ vượt bề rộng tối đa cho mắt đọc.
                    val simulatedDp = if (simulatedWidthDp <= 0) {
                        contentAvailableDp
                    } else {
                        simulatedWidthDp.coerceAtMost(contentAvailableDp)
                    }
                    val layoutWidthDp = simulatedDp.coerceAtMost(Lab.CONTENT_MAX_WIDTH_DP)

                    val content: @Composable () -> Unit = {
                        Box(
                            modifier = Modifier
                                // ⚠️ `widthIn(max = …).fillMaxWidth()` thay cho `width(x.dp)`:
                                // `width(dp)` ĐẶT CỨNG bề rộng, nên khi `layoutWidthDp` tính ra lớn
                                // hơn vùng chứa thật (DPI 125%, cửa sổ hẹp) thì nội dung tràn ra
                                // ngoài cửa sổ và bị cắt ở mép — đúng hiện tượng trong ảnh chụp
                                // `.tmp/shot/app-5.png` (hero + 3 thẻ chạy khỏi mép phải).
                                // `widthIn().fillMaxWidth()` giữ nguyên tính năng "bề rộng giả lập"
                                // (khi sim < available thì lấy sim) nhưng KHÔNG BAO GIỜ vượt quá bề
                                // rộng vùng chứa.
                                .widthIn(max = layoutWidthDp.dp)
                                .fillMaxWidth()
                                .fillMaxHeight(),
                        ) {
                            key(route) {
                                LabEnter(modifier = Modifier.fillMaxSize()) {
                                    screen(layoutWidthDp)
                                }
                            }
                        }
                    }

                    // Kích thước menu chuyển vai trò tính từ cửa sổ THẬT (không đoán theo thiết bị):
                    // cùng hàm chặn với menu của màn "Chuyển tài khoản" ⇒ hai lối chọn không lệch.
                    val switcherMenuWidthDp = accountMenuWidthDp(availableWidthDp)
                    val switcherMenuHeightDp = accountMenuMaxHeightDp(maxHeight.value)

                    if (verticalNav) {
                        Row(Modifier.fillMaxSize()) {
                            LabSidebar(
                                title = "AppLogictics",
                                subtitle = "Gom hàng lẻ · Odoo 17",
                                groups = navGroups(routes),
                                activeIndex = activeIndex,
                                onSelect = { index -> route = routes[index] },
                                collapsed = sidebarCollapsed,
                                onToggleCollapse = { sidebarCollapsed = !sidebarCollapsed },
                                footer = {
                                    // NÚT CHUYỂN VAI TRÒ nằm ở chân sidebar, NGAY TRÊN dòng vai
                                    // trò/nguồn dữ liệu: luôn thấy ở mọi màn (kể cả màn của tài
                                    // khoản vừa được admin chuyển sang), 1 click là đổi vai trò.
                                    if (hienNutChuyenVaiTro) {
                                        RoleSwitcher(
                                            vm = adminVm,
                                            roles = appSettings.roles,
                                            currentLogin = accountLogin,
                                            onSwitch = chuyenPhien,
                                            menuWidthDp = switcherMenuWidthDp,
                                            menuMaxHeightDp = switcherMenuHeightDp,
                                            fillWidth = true,
                                        )
                                        Spacer(Modifier.height(Tokens.s3))
                                    }
                                    SidebarFooter(
                                        sourceLabel = if (usingRealApi) "Odoo thật" else "Dữ liệu mẫu",
                                        roleLabel = if (usingRealApi) {
                                            workspace.label
                                        } else {
                                            roleLabelOf(demoRole)
                                        },
                                        onOpenDemoSettings = { demoSettingsVisible = true },
                                    )
                                },
                            )
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                content()
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            LabTopNav(
                                title = "AppLogictics",
                                subtitle = "Gom hàng lẻ · Odoo 17",
                                items = navItems,
                                activeIndex = activeIndex,
                                onSelect = { index ->
                                    val chosen = routes[index]
                                    route = chosen
                                    if (chosen != Route.Welcome && chosen != Route.Login) {
                                        topNavCollapsed = true
                                    }
                                },
                                collapsed = topNavCollapsed,
                                onToggleCollapse = { topNavCollapsed = !topNavCollapsed },
                                trailing = {
                                    if (hienNutChuyenVaiTro) {
                                        // ĐIỆN THOẠI: nút chuyển vai trò LUÔN nằm trên thanh
                                        // trên cùng (yêu cầu của sếp). Nó THAY chip nguồn dữ
                                        // liệu: nguồn vẫn hiện ở phụ đề của mọi màn ("Nguồn:
                                        // …"), còn chỗ trên thanh 360dp thì chỉ đủ một trong hai.
                                        RoleSwitcher(
                                            vm = adminVm,
                                            roles = appSettings.roles,
                                            currentLogin = accountLogin,
                                            onSwitch = chuyenPhien,
                                            menuWidthDp = switcherMenuWidthDp,
                                            menuMaxHeightDp = switcherMenuHeightDp,
                                            compact = true,
                                        )
                                    } else {
                                        LabChip(
                                            text = if (usingRealApi) "Odoo thật" else "Dữ liệu mẫu",
                                            tone = if (usingRealApi) Tone.Warn else Tone.Info,
                                        )
                                    }
                                    Spacer(Modifier.width(Tokens.s1))
                                    // ĐIỆN THOẠI KHÔNG CÓ SIDEBAR ⇒ phải có lối vào "Cài đặt demo"
                                    // ngay trên thanh trên, nếu không không thể đổi nguồn dữ liệu /
                                    // vai trò demo / nền tối để chụp trạng thái Empty/Error/Dark.
                                    Box(
                                        modifier = Modifier
                                            .clickable { demoSettingsVisible = true }
                                            .padding(Tokens.s1),
                                    ) {
                                        LabGearIcon()
                                    }
                                },
                            )
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                content()
                            }
                        }
                    }

                    if (demoSettingsVisible) {
                        DemoSettingsDialog(
                            onDismiss = { demoSettingsVisible = false },
                            useRealApi = usingRealApi,
                            onUseRealApi = { wantsReal ->
                                appSettings.updateUseRealApi(wantsReal)
                                // Chưa có mã truy cập mà bật Odoo thật → đưa thẳng tới màn Đăng
                                // nhập (không để bấm vào màn nghiệp vụ rồi thấy lỗi).
                                if (wantsReal && !appSettings.hasToken) route = Route.Login
                            },
                            demoRole = if (usingRealApi) null else demoRole,
                            onDemoRole = { role ->
                                demoRole = role
                                if (route != Route.Welcome && route != Route.Login) {
                                    route = routeOf(Workspace.fromRoles(listOf(role)))
                                }
                            },
                            scenario = scenario,
                            onScenario = { scenario = it },
                            reducedMotion = reducedMotion,
                            onReducedMotion = { reducedMotion = it },
                            simulatedWidthDp = simulatedWidthDp,
                            onSimulatedWidth = { simulatedWidthDp = it },
                            baseUrl = appSettings.baseUrl,
                            hasToken = appSettings.hasToken,
                            driverName = appSettings.driverName,
                            realRoleLabel = workspace.label,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chân sidebar: nhãn nguồn dữ liệu + vai trò đang đăng nhập + nút mở "Cài đặt demo".
 *
 * Đây là chỗ DUY NHẤT của các công tắc demo trên desktop — thay cho thanh demo cao 2–3 hàng ở
 * đỉnh cửa sổ (thứ làm app trông như mockup). Icon bánh răng vẽ bằng Canvas ([LabGearIcon]),
 * không phải emoji và không cần thư viện icon (build offline không có).
 */
@Composable
private fun SidebarFooter(
    sourceLabel: String,
    roleLabel: String,
    onOpenDemoSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LabChip(
            text = sourceLabel,
            tone = if (sourceLabel == "Odoo thật") Tone.Warn else Tone.Info,
        )
        Spacer(Modifier.height(Tokens.s2))
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Tokens.s2))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.touchSecondary)
                .background(AppTheme.lab.accent.copy(alpha = Lab.NAV_ACTIVE_TINT))
                .clickable(onClick = onOpenDemoSettings)
                .padding(horizontal = Tokens.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabGearIcon()
            Spacer(Modifier.width(Tokens.s2))
            Text(
                text = "Cài đặt demo",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
