package vn.lab.logistics

import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.model.LabRole
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.parseAdminAccountsPayload
import vn.lab.logistics.core.net.parseSwitchSessionPayload
import vn.lab.logistics.core.net.switchAccountRequestBody
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.core.settings.InMemorySettingsStorage
import vn.lab.logistics.feature.admin.accountLabel
import vn.lab.logistics.feature.admin.accountMenuMaxHeightDp
import vn.lab.logistics.feature.admin.accountMenuWidthDp
import vn.lab.logistics.feature.admin.adminToolbarSubtitle
import vn.lab.logistics.feature.admin.dongDangDungLabel
import vn.lab.logistics.feature.admin.filterAccounts
import vn.lab.logistics.feature.admin.hangVaiTroMenu
import vn.lab.logistics.feature.admin.opensAppScreen
import vn.lab.logistics.feature.admin.pickerAccounts
import vn.lab.logistics.feature.admin.pickerLabel
import vn.lab.logistics.feature.admin.pickerMenuLabel
import vn.lab.logistics.feature.admin.rolesCode
import vn.lab.logistics.feature.admin.sapXepMenuTaiKhoan
import vn.lab.logistics.feature.admin.shouldShowRoleSwitcher
import vn.lab.logistics.feature.admin.switcherLabel
import vn.lab.logistics.feature.admin.targetScreenLabel
import vn.lab.logistics.feature.admin.workspaceLabelOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * ADMIN — CHUYỂN TÀI KHOẢN: test JVM chạy offline (`:composeApp:desktopTest`).
 *
 * Tính năng này là CÔNG CỤ TEST 8 vai trò, nên lỗi ở đây không làm hỏng nghiệp vụ — nhưng nó
 * làm hỏng ĐÚNG THỨ đang được đánh giá: khả năng demo. Bảy thứ dưới đây phải khoá bằng assert:
 *   1. parse `admin/accounts` chịu được payload thiếu `name`/`roles`/`login` (seed dở vẫn chạy);
 *   2. parse `admin/switch` đọc đúng `login` + `roles` + token (shape login của BE-01);
 *   3. body `switch` đúng MỘT khoá `login` (hợp đồng);
 *   4. **ghi phiên mới** vào `AppSettings`: token · uid · roles · login · driverName · jobsToday
 *      · mốc hạn — và phiên đó sống qua lần đọc lại từ nơi lưu bền (mở app lần sau vào đúng màn);
 *   5. **nhãn của nút mở dropdown chọn nhanh** (`pickerLabel`) và dòng menu (`pickerMenuLabel`):
 *      nút luôn 1 dòng "… ▾", menu luôn liệt kê TOÀN BỘ tài khoản chứ không theo ô tìm;
 *   6. **phụ đề toolbar ở màn hẹp** (`adminToolbarSubtitle`): đủ ngắn để không bị `LabToolbar` cắt
 *      giữa từ ở 360×640, và vẫn nói đủ 3 thông tin (ai được dùng · đang là ai · nguồn dữ liệu);
 *   7. **`canSwitch`** (quyền chuyển vai trò): parse có/không có/sai kiểu, `AppSettings` ghi-đọc-
 *      xoá, điều kiện hiện nút, nhãn nút theo vai trò, và kích thước menu bị chặn theo màn hình;
 *   8. **thứ tự dòng trong menu chuyển vai trò** (`sapXepMenuTaiKhoan`): driver trước sep, cùng
 *      vai trò theo `login`, mã lạ/rỗng xuống cuối, không mất/không nhân bản phần tử — và nhãn
 *      dòng "đang đăng nhập" (`dongDangDungLabel`) khi đang là `admin`.
 * =====================================================================================
 */
class AdminAccountsTest {

    // ==================================================================================
    // 1. GET /lab/api/v1/admin/accounts
    // ==================================================================================

    @Test
    fun `parse accounts du truong`() {
        val accounts = parseAdminAccountsPayload(ACCOUNTS_PAYLOAD)

        assertEquals(2, accounts.size)
        assertEquals("taixe01", accounts[0].login)
        assertEquals("Trần Văn Hùng (tài xế)", accounts[0].name)
        assertEquals(listOf(LabRole.DRIVER), accounts[0].roles)
        assertEquals(Workspace.Driver, accounts[0].targetWorkspace)
        // Định dạng dòng chọn đã chốt: "tên · tài khoản · mã vai trò".
        assertEquals("Trần Văn Hùng (tài xế) · taixe01 · driver", accounts[0].accountLabel())
        assertEquals("Mở màn: Tài xế — việc hôm nay", accounts[0].targetScreenLabel())
        assertTrue(accounts[0].opensAppScreen())

        assertEquals("nhomtruong01", accounts[1].login)
        assertEquals(Workspace.DriverLeader, accounts[1].targetWorkspace)
    }

    @Test
    fun `accounts thieu name va roles khong vo`() {
        val accounts = parseAdminAccountsPayload(
            """{"ok":true,"data":{"items":[
               {"login":"cs01"},
               {"login":"kho01","name":"","roles":[]},
               {"login":"  ","name":"Không có tài khoản","roles":["cs"]},
               {"name":"Thiếu hẳn login","roles":["cs"]}
            ]}}""",
        )
        // Dòng thiếu `login` (hoặc login toàn khoảng trắng) bị BỎ: không gửi được switch.
        assertEquals(2, accounts.size, "chỉ giữ dòng có login dùng được")

        // Thiếu `name` ⇒ rơi về `login`; thiếu `roles` ⇒ "chưa có vai trò", KHÔNG bịa `driver`.
        assertEquals("cs01", accounts[0].name)
        assertEquals("cs01 · cs01 · chưa có vai trò", accounts[0].accountLabel())
        assertEquals(Workspace.Unsupported, accounts[0].targetWorkspace)
        assertFalse(accounts[0].opensAppScreen(), "vai trò rỗng ⇒ màn 'vai trò khác', không phải app")

        assertEquals("kho01", accounts[1].name, "tên rỗng ⇒ dùng login")
    }

    @Test
    fun `accounts data null hoac sai dang`() {
        assertTrue(parseAdminAccountsPayload("""{"ok":true,"data":null}""").isEmpty())
        assertTrue(parseAdminAccountsPayload("""{"ok":true,"data":{"items":[]}}""").isEmpty())

        val error = assertFailsWith<ApiException.Server> {
            parseAdminAccountsPayload("""{"ok":true,"data":"hỏng"}""")
        }
        assertTrue((error.message ?: "").contains("tài khoản"), "câu lỗi phải nói rõ là tài khoản")
    }

    @Test
    fun `403 khong phai admin thi bao khong du quyen`() {
        val error = assertFailsWith<ApiException.Forbidden> {
            parseAdminAccountsPayload(
                """{"ok":false,"error":{"code":"ACCESS_DENIED","message":"Access Denied"}}""",
                status = 403,
            )
        }
        // Luật 6: người dùng thấy câu tiếng Việt, KHÔNG thấy "Access Denied" của Odoo.
        assertTrue(
            (error.toVietnameseMessage()).contains("quyền"),
            "câu hiển thị phải là tiếng Việt: ${error.toVietnameseMessage()}",
        )
    }

    // ==================================================================================
    // 2. POST /lab/api/v1/admin/switch
    // ==================================================================================

    @Test
    fun `body switch dung mot khoa login`() {
        assertEquals("""{"login":"taixe01"}""", switchAccountRequestBody("taixe01"))
        assertEquals("""{"login":"cs01"}""", switchAccountRequestBody("  cs01  "), "cắt khoảng trắng")
    }

    @Test
    fun `parse switch tra session cua tai khoan dich`() {
        val session = parseSwitchSessionPayload(SWITCH_PAYLOAD)

        assertEquals("tok-test-doi-phien", session.token)
        assertEquals("LabToken", session.tokenType)
        assertEquals(12, session.uid)
        assertEquals("taixe01", session.login)
        assertEquals(listOf(LabRole.DRIVER), session.roles)
        assertEquals(3, session.jobsToday)
        // BE không trả `driver_name` ở phản hồi switch ⇒ app fallback về `name`.
        assertEquals("Trần Văn Hùng (tài xế)", session.driverName)
        assertEquals(Workspace.Driver, Workspace.fromRoles(session.roles))
    }

    @Test
    fun `switch thieu token thi coi la phien het han`() {
        assertFailsWith<ApiException.SessionExpired> {
            parseSwitchSessionPayload("""{"ok":true,"data":{"login":"taixe01","roles":["driver"]}}""")
        }
    }

    // ==================================================================================
    // 3. Ghi phiên mới — nguồn sự thật duy nhất của "đang là ai"
    // ==================================================================================

    @Test
    fun `chuyen tai khoan ghi du khoa phien`() {
        val storage = InMemorySettingsStorage()
        val settings = AppSettings(storage)
        assertFalse(settings.hasValidSession, "chưa đăng nhập thì chưa có phiên")

        val session = parseSwitchSessionPayload(SWITCH_PAYLOAD)
        settings.saveSession(session)

        assertEquals("tok-test-doi-phien", settings.token)
        assertEquals(12, settings.uid)
        assertEquals("taixe01", settings.login)
        assertEquals(listOf(LabRole.DRIVER), settings.roles)
        assertEquals("Trần Văn Hùng (tài xế)", settings.driverName)
        assertEquals(3, settings.jobsToday)
        assertEquals(Workspace.Driver, settings.workspace)
        assertTrue(settings.hasValidSession, "mốc nhận token được ghi NGAY khi chuyển")
    }

    @Test
    fun `phien sau khi chuyen song qua lan doc lai tu noi luu ben`() {
        val storage = InMemorySettingsStorage()
        AppSettings(storage).saveSession(parseSwitchSessionPayload(SWITCH_PAYLOAD))

        // "Mở app lần sau": AppSettings dựng lại từ đúng kho dữ liệu đã ghi.
        val docLai = AppSettings(storage)
        assertEquals("taixe01", docLai.login)
        assertEquals(listOf(LabRole.DRIVER), docLai.roles)
        assertEquals(Workspace.Driver, docLai.workspace, "mở lại phải vào đúng màn của vai trò mới")
        assertTrue(docLai.hasValidSession)
    }

    @Test
    fun `chuyen sang tai khoan khong co vai tro thi mo man vai tro khac`() {
        val storage = InMemorySettingsStorage()
        val settings = AppSettings(storage)
        settings.saveSession(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-test-ketoan","login":"ketoan01",
                   "name":"Đỗ Thị Kim (kế toán)","roles":["ketoan"]}}""",
            ),
        )
        assertEquals("ketoan01", settings.login)
        assertEquals(Workspace.Unsupported, settings.workspace)
    }

    @Test
    fun `switch lai lan nua ghi de phien cu`() {
        val settings = AppSettings(InMemorySettingsStorage())
        settings.saveSession(parseSwitchSessionPayload(SWITCH_PAYLOAD))
        settings.saveSession(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-test-cs","login":"cs01",
                   "name":"Lê Thu Hà (CS)","roles":["cs"],"jobsToday":7}}""",
            ),
        )
        assertEquals("tok-test-cs", settings.token)
        assertEquals("cs01", settings.login)
        // D1 (2026-11-02): CS làm việc trên Odoo web ⇒ phiên `cs01` rơi vào màn vai trò khác.
        assertEquals(Workspace.Unsupported, settings.workspace)
        assertEquals(7, settings.jobsToday)
    }

    @Test
    fun `ghi phien khong lam mat login khi server khong tra login`() {
        val settings = AppSettings(InMemorySettingsStorage())
        settings.updateLogin("nhomtruong01")
        // Đăng nhập thường TRƯỚC ĐÂY không trả `login` trong payload ⇒ không được xoá giá trị cũ.
        settings.saveSession(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-test-giu-login","roles":["driver_leader"]}}""",
            ),
        )
        assertEquals("nhomtruong01", settings.login)
        assertEquals(Workspace.DriverLeader, settings.workspace)
    }

    // ==================================================================================
    // 4. Tìm nhanh + nhãn màn đích (hàm thuần dùng ở màn hình)
    // ==================================================================================

    @Test
    fun `tim nhanh theo ten tai khoan hoac ma vai tro`() {
        val accounts = parseAdminAccountsPayload(ACCOUNTS_PAYLOAD)

        assertEquals(2, filterAccounts(accounts, "").size, "ô tìm trống ⇒ xem hết")
        assertEquals(1, filterAccounts(accounts, "Hùng").size, "khớp TÊN, không phân biệt hoa/thường")
        assertEquals(1, filterAccounts(accounts, "nhomtruong").size, "khớp TÀI KHOẢN")
        assertEquals(
            1,
            filterAccounts(accounts, "driver_leader").size,
            "khớp MÃ VAI TRÒ (quay video gõ 'cs'/'kho' nhanh hơn nhớ tên tài khoản)",
        )
        assertTrue(filterAccounts(accounts, "khong_co_ai").isEmpty())
    }

    @Test
    fun `nhan vai tro va man dich cua tai khoan`() {
        val accounts = parseAdminAccountsPayload(ACCOUNTS_PAYLOAD)
        assertEquals("driver", accounts[0].rolesCode())
        assertEquals("driver_leader", accounts[1].rolesCode())
        assertEquals("Tài xế — việc hôm nay", workspaceLabelOf(accounts[0].roles))
        assertEquals("Vai trò khác", workspaceLabelOf(emptyList()))
        assertEquals("chưa có vai trò", DemoAccount(login = "x", name = "x").rolesCode())
    }

    @Test
    fun `hai route admin nam trong LabApi`() {
        // Khoá đường dẫn: BE-01 đổi route mà app vẫn build xanh là lỗi chỉ lộ khi bấm thật.
        assertEquals("/lab/api/v1/admin/accounts", LabApi.PATH_ADMIN_ACCOUNTS)
        assertEquals("/lab/api/v1/admin/switch", LabApi.PATH_ADMIN_SWITCH)
        assertEquals("X-Lab-Token", LabApi.HEADER_LAB_TOKEN)
    }

    // ==================================================================================
    // 5. Nút bấm mở dropdown chọn nhanh (yêu cầu của sếp) — phần chữ thuần, KHÔNG chạm Compose UI
    // ==================================================================================

    @Test
    fun `nhan nut mo dropdown theo lua chon`() {
        // Chưa chọn ⇒ câu mời chọn; đã chọn ⇒ hiện ĐÚNG `login` đang trỏ tới (ngắn, duy nhất)
        // để người dùng không phải cuộn xuống đọc dòng "Đang chọn:".
        assertEquals("Chọn tài khoản ▾", pickerLabel(null))
        assertEquals("Chọn tài khoản ▾", pickerLabel(""))
        assertEquals("Chọn tài khoản ▾", pickerLabel("   "))
        assertEquals("Đã chọn: taixe01 ▾", pickerLabel("taixe01"))
        assertEquals("Đã chọn: nhomtruong01 ▾", pickerLabel("  nhomtruong01  "), "cắt khoảng trắng")

        // Nhãn phải giữ đúng khuôn "<câu> <login> ▾" kể cả với login dài (nút 1 dòng, ellipsis).
        val dai = pickerLabel("nguyenvanmot_ten_dang_nhap_rat_dai")
        assertTrue(dai.startsWith("Đã chọn: ") && dai.endsWith(" ▾"), "nhãn nút: \"$dai\"")
    }

    @Test
    fun `menu chon nhanh liet ke toan bo va danh dau dang chon`() {
        val accounts = parseAdminAccountsPayload(ACCOUNTS_PAYLOAD)

        // Ô tìm đang lọc danh sách CHI TIẾT còn 1 dòng...
        assertEquals(1, filterAccounts(accounts, "Hùng").size)
        // ...nhưng MENU dropdown luôn liệt kê TOÀN BỘ tài khoản đã tải (lối chọn nhanh).
        val menu = pickerAccounts(accounts)
        assertEquals(2, menu.size)

        // Dòng đang chọn có `✓` ở đầu; dòng khác thì không.
        assertEquals(
            "Trần Văn Hùng (tài xế) · taixe01 · driver",
            menu[0].pickerMenuLabel(isSelected = false),
        )
        assertEquals(
            "✓ Phạm Minh Nhóm (trưởng nhóm tài xế) · nhomtruong01 · driver_leader",
            menu[1].pickerMenuLabel(isSelected = true),
        )
        // Dòng phụ trong menu cho biết chọn xong sẽ mở màn nào.
        assertEquals("Mở màn: Trưởng nhóm tài xế", menu[1].targetScreenLabel())
    }

    // ==================================================================================
    // 6. Phụ đề toolbar ở màn hẹp (360×640 = AVD 720×1280)
    // ==================================================================================

    /**
     * Vì sao khoá bằng test: `LabToolbar` chỉ cho phụ đề **1 dòng** và cắt bằng ellipsis. Ở 360dp,
     * câu đầy đủ ("Chỉ quản trị viên · chọn tài khoản để test nhanh · đang là a…") bị cắt GIỮA TỪ
     * — đúng lỗi đã thấy trên AVD. Nên luật "compact ⇒ câu ngắn" phải nằm ở một hàm thuần và được
     * assert, không thể chỉ nhìn ảnh chụp.
     */
    @Test
    fun `phu de toolbar o man hep phai ngan va du thong tin`() {
        // Màn hẹp: giữ đúng 3 thông tin cần thiết (ai được dùng · đang là ai · nguồn dữ liệu).
        val hep = adminToolbarSubtitle(
            currentLogin = "admin",
            dataSourceLabel = "Odoo thật",
            compact = true,
        )
        assertEquals("Chỉ admin · đang là admin · Odoo thật", hep)
        assertTrue(hep.length <= 48, "phụ đề compact phải ngắn để không bị cắt: \"$hep\"")

        // Login dài nhất trong danh sách demo cũng không được làm câu tràn 1 dòng.
        val hepDai = adminToolbarSubtitle(
            currentLogin = "nhomtruong01",
            dataSourceLabel = "Odoo thật",
            compact = true,
        )
        assertTrue(hepDai.length <= 48, "vẫn ngắn với login dài: \"$hepDai\"")
        assertFalse(hepDai.contains("  "), "không để khoảng trắng đôi: \"$hepDai\"")

        // Chưa biết tài khoản ⇒ bỏ hẳn vế "đang là …", không để dấu "·" treo.
        assertEquals(
            "Chỉ admin · Mẫu",
            adminToolbarSubtitle(currentLogin = "", dataSourceLabel = "Mẫu", compact = true),
        )

        // Màn rộng: giữ câu đầy đủ giải thích màn này là gì.
        assertEquals(
            "Chỉ quản trị viên · chọn tài khoản để test nhanh · đang là admin · Nguồn: Odoo thật",
            adminToolbarSubtitle(currentLogin = "admin", dataSourceLabel = "Odoo thật", compact = false),
        )
    }

    // ==================================================================================
    // 7. canSwitch — quyền chuyển vai trò (nút dropdown ở thanh trên cùng)
    // ==================================================================================

    /**
     * Vì sao khoá bằng test: `canSwitch` quyết định nút dropdown còn hay mất. Sai một chiều là
     * hoặc mất nút giữa buổi demo (sếp đã nêu đích danh), hoặc hiện nút cho tài khoản thường rồi
     * bấm vào ăn 403. Payload cũ của BE (chưa có khoá) phải là `false` chứ không được nổ.
     */
    @Test
    fun `parse canSwitch co khong co va sai kieu`() {
        // Sau /admin/switch: `true` ⇒ nút còn hiện khi đang là taixe01.
        assertTrue(parseSwitchSessionPayload(SWITCH_PAYLOAD).canSwitch)

        // Login admin: `true`.
        assertTrue(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-a","canSwitch":true,"roles":["admin"]}}""",
            ).canSwitch,
        )
        // Login tài khoản thường: `false` ⇒ nút MẤT (trường hợp duy nhất).
        assertFalse(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-b","canSwitch":false,"roles":["driver"]}}""",
            ).canSwitch,
        )
        // Payload CŨ không có khoá ⇒ `false`, không nổ.
        assertFalse(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-c","roles":["admin"]}}""",
            ).canSwitch,
        )
        // Sai kiểu (số / chuỗi lạ / object / null) ⇒ `false`, KHÔNG làm hỏng lần đăng nhập.
        listOf(
            """{"ok":true,"data":{"token":"tok-d","canSwitch":1}}""",
            """{"ok":true,"data":{"token":"tok-e","canSwitch":"y"}}""",
            """{"ok":true,"data":{"token":"tok-f","canSwitch":{"x":1}}}""",
            """{"ok":true,"data":{"token":"tok-g","canSwitch":null}}""",
        ).forEach { body ->
            assertFalse(parseSwitchSessionPayload(body).canSwitch, "sai kiểu ⇒ false: $body")
        }
    }

    @Test
    fun `AppSettings ghi doc va xoa canSwitch`() {
        val storage = InMemorySettingsStorage()
        val settings = AppSettings(storage)
        assertFalse(settings.canSwitch, "mặc định: chưa có quyền chuyển vai trò")
        assertFalse(settings.hienNutChuyenVaiTro, "chưa có phiên ⇒ không hiện nút")

        // Sau /admin/switch ⇒ phiên mới có canSwitch = true.
        settings.saveSession(parseSwitchSessionPayload(SWITCH_PAYLOAD))
        assertTrue(settings.canSwitch)
        assertTrue(settings.hienNutChuyenVaiTro, "có phiên + có quyền ⇒ hiện nút chuyển vai trò")

        // LƯU BỀN: mở lại app (AppSettings dựng từ đúng kho dữ liệu) vẫn còn cờ.
        assertTrue(AppSettings(storage).canSwitch, "canSwitch phải sống qua lần đọc lại")

        // Đăng xuất ⇒ xoá cờ (ở màn Đăng nhập không còn nút nào để hiện).
        settings.clearSession()
        assertFalse(settings.canSwitch)
        assertFalse(AppSettings(storage).canSwitch, "logout phải xoá cả trong kho lưu bền")

        // Đăng nhập MỚI không có khoá ⇒ GHI ĐÈ về false (không giữ quyền của phiên admin cũ).
        settings.saveSession(
            parseSwitchSessionPayload(
                """{"ok":true,"data":{"token":"tok-login-thuong","roles":["driver"],"login":"taixe02"}}""",
            ),
        )
        assertFalse(settings.canSwitch, "login thường phải xoá quyền còn sót")
        assertFalse(settings.hienNutChuyenVaiTro)
    }

    @Test
    fun `dieu kien hien nut chuyen vai tro`() {
        // Chỉ hiện khi SERVER cho phép VÀ còn phiên — không dựa vào `roles`.
        assertTrue(shouldShowRoleSwitcher(canSwitch = true, hasValidSession = true))
        assertFalse(shouldShowRoleSwitcher(canSwitch = false, hasValidSession = true))
        assertFalse(shouldShowRoleSwitcher(canSwitch = true, hasValidSession = false))
        assertFalse(shouldShowRoleSwitcher(canSwitch = false, hasValidSession = false))
    }

    @Test
    fun `nhan nut chuyen vai tro theo vai tro dang dung`() {
        // Đang ở tài khoản vừa được admin chuyển sang ⇒ nhãn đổi theo vai trò đó.
        assertEquals("Tài xế ▾", switcherLabel(listOf(LabRole.DRIVER)))
        // D1: nhãn ngắn của `cs` phải nói rõ làm trên web (CS không còn màn app).
        assertEquals("CS — điều phối (trên web) ▾", switcherLabel(listOf(LabRole.CS)))
        assertEquals("Kho ▾", switcherLabel(listOf(LabRole.KHO)))
        assertEquals("Trưởng nhóm ▾", switcherLabel(listOf(LabRole.DRIVER_LEADER)))
        assertEquals("Tổng quan ▾", switcherLabel(listOf(LabRole.SEP, LabRole.MANAGER)))
        assertEquals("Quản trị viên ▾", switcherLabel(listOf(LabRole.ADMIN)))
        // Vai trò không có màn hình: hiện đúng nhãn tiếng Việt của mã đó.
        assertEquals("Kế toán ▾", switcherLabel(listOf(LabRole.KETOAN)))
        // Chưa có vai trò: vẫn có chữ để bấm, không phải nút rỗng.
        assertEquals("Chuyển vai trò ▾", switcherLabel(emptyList()))
    }

    @Test
    fun `kich thuoc menu chuyen vai tro bi chan theo man hinh`() {
        // 360dp (AVD): 360 − 48 = 312dp ⇒ vừa trong màn; 1440dp ⇒ kẹp trần 420dp.
        assertEquals(312, accountMenuWidthDp(360))
        assertEquals(420, accountMenuWidthDp(1440))
        assertEquals(200, accountMenuWidthDp(200), "sàn 200dp để nhãn không bị cắt vô nghĩa")

        // Chiều cao: 55% vùng nội dung, kẹp 160–420dp.
        assertEquals(267, accountMenuMaxHeightDp(486f), "486 × 0.55 = 267dp")
        assertEquals(160, accountMenuMaxHeightDp(100f))
        assertEquals(420, accountMenuMaxHeightDp(2000f))
    }

    // ==================================================================================
    // 8. THỨ TỰ MENU CHUYỂN VAI TRÒ (đo trên AVD: tài xế nằm cuối vì server trả theo login)
    // ==================================================================================

    @Test
    fun `menu xep theo thu tu dieu huong roi theo login`() {
        // Server trả theo `login` (chungtu01 · cs01 · ketoan01 · …) — đúng thứ tự đã thấy trên
        // AVD, và nó đặt 3 tài khoản tài xế ở CUỐI. Menu phải tự sắp lại:
        // driver → driver_leader → cs → kho → ketoan → manager → sep → mã lạ.
        val accounts = listOf(
            DemoAccount(login = "sep01", name = "Sếp", roles = listOf(LabRole.SEP)),
            DemoAccount(login = "ketoan01", name = "Kế toán", roles = listOf(LabRole.KETOAN)),
            DemoAccount(login = "taixe02", name = "Tài xế 2", roles = listOf(LabRole.DRIVER)),
            DemoAccount(login = "cs01", name = "CS", roles = listOf(LabRole.CS)),
            DemoAccount(login = "taixe01", name = "Tài xế 1", roles = listOf(LabRole.DRIVER)),
            DemoAccount(login = "kho01", name = "Kho", roles = listOf(LabRole.KHO)),
            DemoAccount(login = "nhomtruong01", name = "Nhóm", roles = listOf(LabRole.DRIVER_LEADER)),
            DemoAccount(login = "manager01", name = "Quản lý", roles = listOf(LabRole.MANAGER)),
        )

        assertEquals(
            listOf(
                "taixe01", "taixe02", // driver — hai dòng cùng vai trò xếp theo login
                "nhomtruong01", // driver_leader
                "cs01", // cs
                "kho01", // kho
                "ketoan01", // ketoan
                "manager01", // manager
                "sep01", // sep
            ),
            sapXepMenuTaiKhoan(accounts).map { it.login },
        )
        // Tài khoản tài xế KHÔNG còn nằm cuối: đó chính là lý do phải sắp (admin → tài xế → CS).
        assertEquals("driver", sapXepMenuTaiKhoan(accounts).first().roles.first())
    }

    @Test
    fun `menu sap xep khong mat va khong nhan ban phan tu`() {
        val accounts = listOf(
            DemoAccount(login = "sep01", name = "Sếp", roles = listOf(LabRole.SEP)),
            DemoAccount(login = "taixe01", name = "Tài xế", roles = listOf(LabRole.DRIVER)),
            DemoAccount(login = "chungtu01", name = "Chứng từ", roles = listOf("chungtu")),
            DemoAccount(login = "khongvaitro", name = "Chưa vai trò", roles = emptyList()),
            DemoAccount(login = "cs01", name = "CS", roles = listOf(LabRole.CS)),
        )
        val sorted = sapXepMenuTaiKhoan(accounts)

        assertEquals(accounts.size, sorted.size, "không được mất dòng nào")
        assertEquals(accounts.toSet(), sorted.toSet(), "không được nhân bản/đổi nội dung")
        // Mã lạ + rỗng vai trò nằm CUỐI (vẫn còn trong menu, chỉ ở dưới cùng), theo login.
        assertEquals(listOf("chungtu01", "khongvaitro"), sorted.takeLast(2).map { it.login })
        // Danh sách gốc KHÔNG bị sửa tại chỗ (hàm thuần ⇒ màn chi tiết giữ thứ tự server trả).
        assertEquals("sep01", accounts.first().login)
        assertEquals(Int.MAX_VALUE, hangVaiTroMenu(emptyList()))
        assertEquals(Int.MAX_VALUE, hangVaiTroMenu(listOf("chungtu")))
    }

    @Test
    fun `dong dang dang nhap o dau menu khi la admin`() {
        // `admin` không nằm trong whitelist của `/admin/accounts` ⇒ menu sẽ không có dòng nào ✓.
        // Dòng này nói rõ đang đứng ở đâu, và KHÔNG bấm được (chỉ để đọc).
        assertEquals(
            "✓ Quản trị viên (admin) — đang đăng nhập",
            dongDangDungLabel(listOf(LabRole.ADMIN), "admin"),
        )
        // Vai trò khác (dùng chung hàm): vẫn đúng khuôn "<nhãn vai trò> (<login>) — đang đăng nhập".
        assertEquals(
            "✓ Tài xế (taixe01) — đang đăng nhập",
            dongDangDungLabel(listOf(LabRole.DRIVER), "  taixe01  "),
        )
        // Không có login (dữ liệu thiếu) ⇒ vẫn có chữ, không ra ngoặc rỗng.
        assertEquals(
            "✓ Quản trị viên (?) — đang đăng nhập",
            dongDangDungLabel(listOf(LabRole.ADMIN), "   "),
        )
    }

    private companion object {
        /** `admin/accounts`: 1 tài xế + 1 trưởng nhóm (đúng hợp đồng BE-01). */
        const val ACCOUNTS_PAYLOAD = """
{"ok":true,"data":{"items":[
{"login":"taixe01","name":"Trần Văn Hùng (tài xế)","roles":["driver"]},
{"login":"nhomtruong01","name":"Phạm Minh Nhóm (trưởng nhóm tài xế)","roles":["driver_leader"]}
]}}
"""

        /** `admin/switch`: ĐÚNG shape login, KHÔNG có `driver_name`, CÓ `login` + `canSwitch`. */
        const val SWITCH_PAYLOAD = """
{"ok":true,"data":{"token":"tok-test-doi-phien","tokenType":"LabToken",
 "expiresAt":"2026-10-29 09:00:00","uid":12,"name":"Trần Văn Hùng (tài xế)",
 "login":"taixe01","roles":["driver"],"jobsToday":3,"canSwitch":true}}
"""
    }
}
