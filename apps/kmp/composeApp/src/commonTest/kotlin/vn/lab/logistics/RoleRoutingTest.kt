package vn.lab.logistics

import vn.lab.logistics.core.model.LabRole
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.core.model.roleLabelOf
import vn.lab.logistics.core.model.rolesLabelOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * QUY TẮC CHỌN MÀN THEO VAI TRÒ — test JVM, chạy offline (`:composeApp:desktopTest`).
 *
 * Vì sao đây là test QUAN TRỌNG NHẤT của tính năng "route theo vai trò": nếu quy tắc sai, tài xế
 * đăng nhập xong bị đưa vào sai màn (hoặc kế toán bị đưa vào màn tài xế rỗng) — lỗi hiện ra ngay
 * trong video demo, không phải sau khi bấm gì.
 *
 * Hợp đồng đã chốt với BE-01 (docs/02 §6 + yêu cầu của sếp):
 *  - từ vựng `admin` · `driver` · `driver_leader` · `cs` · `kho` · `ketoan` · `manager` · `sep`;
 *  - ưu tiên **admin → driver → driver_leader → kho → dashboard (manager/sep)**;
 *  - vai trò khác HOẶC danh sách rỗng ⇒ màn thông báo "Vai trò này làm việc trên Odoo web".
 *
 * ⚠️ QUYẾT ĐỊNH D1 (2026-11-02) — CS RỜI APP, LÀM TRÊN ODOO WEB:
 *  - `cs` KHÔNG còn màn "CS · Điều phối"; tài khoản chỉ có `cs` rơi xuống [Workspace.Unsupported].
 *  - Màn phân công vẫn còn nhưng thuộc về `driver_leader` (trưởng nhóm tài xế) — dùng chung
 *    `CsDispatchScreen`, cùng API `cs/jobs` + `cs/jobs/<id>/assign`.
 *  - Mã `cs` VẪN là từ vựng hợp đồng (server vẫn trả) ⇒ test khoá cả hai mặt: "vẫn hiểu mã" và
 *    "không mở màn làm việc".
 *
 * `admin` đứng ĐẦU vì màn "Chuyển tài khoản" là CÔNG CỤ TEST: tài khoản admin mà còn vai trò tài
 * xế vẫn phải mở bàn chuyển tài khoản trước (muốn vào màn tài xế thì chuyển sang đúng tài khoản
 * tài xế — đó chính là mục đích của công cụ này).
 *
 * Từ vựng và thứ tự ưu tiên là 2 thứ BE có thể đổi mà app vẫn build xanh ⇒ phải khoá bằng assert.
 * =====================================================================================
 */
class RoleRoutingTest {

    // ------------------------------------------------------- ưu tiên admin → driver → leader → kho

    @Test
    fun `danh sach rong thi la vai tro khac`() {
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(emptyList()))
        assertFalse(Workspace.Unsupported.hasAppScreen, "vai trò khác không có màn thao tác")
    }

    @Test
    fun `chi co driver thi vao man tai xe`() {
        assertEquals(Workspace.Driver, Workspace.fromRoles(listOf(LabRole.DRIVER)))
    }

    /**
     * D1: CS không còn màn làm việc trên app. Đây là assert CHÍNH của quyết định này — nếu ai đó
     * vô tình thêm lại nhánh `LabRole.CS -> Cs`, test này đỏ ngay.
     */
    @Test
    fun `chi co cs thi vao man thong bao lam viec tren web`() {
        val workspace = Workspace.fromRoles(listOf(LabRole.CS))
        assertEquals(Workspace.Unsupported, workspace)
        assertFalse(workspace.hasAppScreen, "CS làm việc trên Odoo web, không có màn app")
        // Mã cs vẫn phải được HIỂU (không rơi vào nhánh "mã lạ"): nhãn nói rõ làm trên web.
        assertTrue(
            roleLabelOf(LabRole.CS).contains("web"),
            "nhãn CS phải nói rõ làm trên web, thực tế: ${roleLabelOf(LabRole.CS)}",
        )
    }

    @Test
    fun `chi co kho thi vao man nhan hang`() {
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf(LabRole.KHO)))
    }

    @Test
    fun `driver duoc uu tien truoc cs va kho`() {
        assertEquals(Workspace.Driver, Workspace.fromRoles(listOf(LabRole.CS, LabRole.KHO, LabRole.DRIVER)))
        assertEquals(Workspace.Driver, Workspace.fromRoles(listOf(LabRole.DRIVER, LabRole.CS)))
        assertEquals(Workspace.Driver, Workspace.fromRoles(listOf(LabRole.KHO, LabRole.DRIVER)))
    }

    @Test
    fun `cs khong con man nen kho thang khi chi co cs va kho`() {
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf(LabRole.KHO, LabRole.CS)))
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf(LabRole.CS, LabRole.KHO)))
    }

    // ------------------------------------ vai trò MỚI: quản trị viên (chuyển tài khoản)

    @Test
    fun `admin luon vao man chuyen tai khoan, ke ca khi co vai tro khac`() {
        assertEquals(Workspace.Admin, Workspace.fromRoles(listOf(LabRole.ADMIN)))
        assertTrue(Workspace.Admin.hasAppScreen, "admin có màn chuyển tài khoản trên app")
        // ADMIN THẮNG MỌI VAI TRÒ, hai chiều danh sách như nhau: màn chuyển tài khoản là công cụ
        // test, phải mở trước — muốn vào màn tài xế thì chuyển sang đúng tài khoản tài xế.
        assertEquals(Workspace.Admin, Workspace.fromRoles(listOf(LabRole.ADMIN, LabRole.DRIVER)))
        assertEquals(Workspace.Admin, Workspace.fromRoles(listOf(LabRole.DRIVER, LabRole.ADMIN)))
        assertEquals(Workspace.Admin, Workspace.fromRoles(listOf(LabRole.CS, LabRole.KHO, LabRole.ADMIN)))
        assertEquals(
            Workspace.Admin,
            Workspace.fromRoles(listOf(LabRole.MANAGER, LabRole.SEP, LabRole.ADMIN)),
        )
        // Chuẩn hoá hoa/thường vẫn áp dụng cho mã mới.
        assertEquals(Workspace.Admin, Workspace.fromRoles(listOf(" ADMIN ")))
    }

    // ------------------------------------------- vai trò MỚI: trưởng nhóm tài xế (phân công)

    @Test
    fun `chi co driver_leader thi vao man phan cong`() {
        assertEquals(Workspace.DriverLeader, Workspace.fromRoles(listOf(LabRole.DRIVER_LEADER)))
        assertTrue(Workspace.DriverLeader.hasAppScreen, "trưởng nhóm tài xế có màn trên app")
    }

    @Test
    fun `driver_leader dung sau driver nhung truoc kho`() {
        // Hai chiều của cùng một danh sách: thứ tự SERVER trả không quyết định màn hình.
        // (D1: `cs` không còn màn nên hai ca dưới chỉ còn so với `kho`/`driver`.)
        assertEquals(
            Workspace.DriverLeader,
            Workspace.fromRoles(listOf(LabRole.DRIVER_LEADER, LabRole.CS)),
        )
        assertEquals(
            Workspace.DriverLeader,
            Workspace.fromRoles(listOf(LabRole.CS, LabRole.DRIVER_LEADER)),
        )
        assertEquals(
            Workspace.DriverLeader,
            Workspace.fromRoles(listOf(LabRole.KHO, LabRole.DRIVER_LEADER)),
        )
        assertEquals(
            Workspace.DriverLeader,
            Workspace.fromRoles(listOf(LabRole.DRIVER_LEADER, LabRole.KHO)),
        )
        // ... nhưng vẫn sau `driver`: vừa chạy xe vừa làm trưởng nhóm ⇒ thấy việc của mình trước.
        assertEquals(
            Workspace.Driver,
            Workspace.fromRoles(listOf(LabRole.DRIVER_LEADER, LabRole.DRIVER)),
        )
        assertEquals(
            Workspace.DriverLeader,
            Workspace.fromRoles(listOf(LabRole.MANAGER, LabRole.SEP, LabRole.DRIVER_LEADER)),
        )
    }

    @Test
    fun `uu tien dung cho ca danh sach day du sau vai tro`() {
        // Tài khoản "làm gì cũng được" (quản trị lab hay gán nhiều nhóm) vẫn phải vào màn tài xế
        // nếu có `driver` — đây là hành vi đang demo, đổi là thấy khác ngay.
        val du = listOf(
            LabRole.KETOAN,
            LabRole.MANAGER,
            LabRole.SEP,
            LabRole.KHO,
            LabRole.CS,
            LabRole.DRIVER_LEADER,
            LabRole.DRIVER,
        )
        assertEquals(Workspace.Driver, Workspace.fromRoles(du))
    }

    // ------------------------------------------------------------------ vai trò khác ⇒ màn thông báo

    @Test
    fun `manager va sep vao dashboard, ketoan van o man thong bao`() {
        // Sếp có màn riêng (dashboard KPI) theo spec `ui-dashboard-app-screens.md` §6.
        assertEquals(Workspace.Dashboard, Workspace.fromRoles(listOf(LabRole.MANAGER)))
        assertEquals(Workspace.Dashboard, Workspace.fromRoles(listOf(LabRole.SEP)))
        assertEquals(Workspace.Dashboard, Workspace.fromRoles(listOf(LabRole.SEP, LabRole.MANAGER)))
        assertEquals(Workspace.Dashboard, Workspace.fromRoles(listOf(" SEP ")), "chuẩn hoá hoa/thường")
        // Kế toán làm việc trên Odoo web (debit/đối soát) ⇒ vẫn là màn thông báo.
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(listOf(LabRole.KETOAN)))
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(listOf(LabRole.KETOAN, "vai_tro_la")))
        // D1: CS cũng nằm trong nhóm "làm việc trên web".
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(listOf(LabRole.CS, LabRole.KETOAN)))
    }

    @Test
    fun `dashboard xep sau kho trong thu tu uu tien`() {
        // Tài khoản vừa kho vừa quản lý: màn làm việc hằng ngày (kho) thắng dashboard.
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf(LabRole.MANAGER, LabRole.KHO)))
        // D1: `cs` không còn màn ⇒ tài khoản sep+cs vào dashboard.
        assertEquals(Workspace.Dashboard, Workspace.fromRoles(listOf(LabRole.SEP, LabRole.CS)))
        assertEquals(Workspace.Driver, Workspace.fromRoles(listOf(LabRole.MANAGER, LabRole.DRIVER)))
    }

    @Test
    fun `vai tro la khong lam vo app`() {
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(listOf("vai_tro_moi_cua_be")))
        // Trộn vai trò lạ với vai trò hợp lệ thì vai trò hợp lệ vẫn thắng.
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf("vai_tro_moi_cua_be", LabRole.KHO)))
    }

    // ------------------------------------------------------------------ chuẩn hoá chuỗi

    @Test
    fun `ma vai tro khong phan biet hoa thuong va khoang trang`() {
        // D1: " CS " vẫn được CHUẨN HOÁ đúng thành mã `cs` — chỉ là mã đó không có màn app.
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(listOf(" CS ")))
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf("Kho")))
        assertEquals(Workspace.Driver, Workspace.fromRoles(listOf("DRIVER")))
    }

    @Test
    fun `phan tu rong trong danh sach bi bo qua`() {
        assertEquals(Workspace.Unsupported, Workspace.fromRoles(listOf("", "   ")))
        assertEquals(Workspace.Kho, Workspace.fromRoles(listOf("", LabRole.KHO)))
    }

    // ------------------------------------------------------------------ nhãn tiếng Việt

    @Test
    fun `nhan vai tro la tieng Viet dung nghiep vu`() {
        assertEquals("Quản trị viên", roleLabelOf(LabRole.ADMIN))
        assertEquals("Tài xế", roleLabelOf(LabRole.DRIVER))
        assertEquals("Trưởng nhóm tài xế", roleLabelOf(LabRole.DRIVER_LEADER))
        // D1: nhãn phải nói rõ CS làm trên web.
        assertEquals("CS — điều phối (trên web)", roleLabelOf(LabRole.CS))
        assertEquals("Kho — nhận hàng", roleLabelOf(LabRole.KHO))
        assertEquals("Kế toán", roleLabelOf(LabRole.KETOAN))
        assertEquals("Quản lý", roleLabelOf(LabRole.MANAGER))
        assertEquals("Ban giám đốc", roleLabelOf(LabRole.SEP))
        // Chưa gán vai trò: câu này hiện trên màn thông báo, không được là chuỗi rỗng.
        assertEquals("Chưa có vai trò", roleLabelOf(""))
        // Mã lạ: hiện nguyên chuỗi để không nuốt mất thông tin của BE.
        assertEquals("vai_tro_moi_cua_be", roleLabelOf("vai_tro_moi_cua_be"))
    }

    @Test
    fun `rolesLabelOf ghep nhieu vai tro va xu ly rong`() {
        assertEquals("Chưa có vai trò", rolesLabelOf(emptyList()))
        assertEquals("Chưa có vai trò", rolesLabelOf(listOf("", " ")))
        assertEquals("Kế toán · Quản lý", rolesLabelOf(listOf(LabRole.KETOAN, LabRole.MANAGER)))
    }

    // ------------------------------------------------------------------ bất biến của enum

    @Test
    fun `chi vai tro lam viec co man hinh tren app`() {
        assertTrue(Workspace.Driver.hasAppScreen)
        assertTrue(Workspace.DriverLeader.hasAppScreen, "trưởng nhóm tài xế có màn phân công")
        assertTrue(Workspace.Kho.hasAppScreen)
        assertTrue(Workspace.Dashboard.hasAppScreen, "sếp có dashboard riêng trên app")
        assertTrue(Workspace.Admin.hasAppScreen, "admin có màn chuyển tài khoản")
        assertFalse(Workspace.Unsupported.hasAppScreen)
        // D1: enum KHÔNG còn màn riêng cho CS ⇒ đếm tổng số màn làm việc = 5.
        val soManLamViec = Workspace.entries.count { it.hasAppScreen }
        assertEquals(5, soManLamViec, "chỉ còn 5 màn: tài xế, trưởng nhóm, kho, dashboard, admin")
    }

    @Test
    fun `moi man lam viec co nhan va mo ta khong rong`() {
        Workspace.entries.forEach { workspace ->
            assertTrue(workspace.label.isNotBlank(), "${workspace.name}: nhãn không được rỗng")
            assertTrue(workspace.subtitle.isNotBlank(), "${workspace.name}: mô tả không được rỗng")
        }
        // Mã vai trò của app phải khớp từ vựng đã chốt với BE (không tự đặt mã khác).
        // D1: `cs` đã ra khỏi danh sách màn làm việc.
        assertEquals(
            listOf(LabRole.DRIVER, LabRole.DRIVER_LEADER, LabRole.KHO),
            listOf(
                Workspace.Driver.role,
                Workspace.DriverLeader.role,
                Workspace.Kho.role,
            ),
        )
        assertEquals(
            LabRole.APP_ROLES,
            listOf(
                Workspace.Driver.role,
                Workspace.DriverLeader.role,
                Workspace.Kho.role,
            ),
        )
        // Mã `cs` VẪN thuộc từ vựng hợp đồng với BE, chỉ là không có màn app.
        assertFalse(LabRole.CS in LabRole.APP_ROLES, "cs không còn là màn làm việc trên app")
        // `admin` CỐ Ý không nằm trong APP_ROLES: đó là màn của công cụ test, không phải màn làm
        // việc — nhưng vẫn phải có mã đúng từ vựng để BE trả về.
        assertEquals(LabRole.ADMIN, Workspace.Admin.role)
        assertFalse(LabRole.ADMIN in LabRole.APP_ROLES, "admin không phải màn làm việc hằng ngày")
    }
}
