package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.model.LabRole
import vn.lab.logistics.core.net.LoginSession
import vn.lab.logistics.core.repo.AdminAccountsRepo

/**
 * =====================================================================================
 * ⚠️ IMPL GIẢ LẬP — dữ liệu MẪU cho màn "Chuyển tài khoản", không gọi mạng (Luật 8: demo chạy
 * được cả khi Odoo chưa bật hoặc DB chưa seed tài khoản demo).
 *
 * Danh sách dưới đây phủ ĐỦ 8 vai trò để quay video chuyển vai trò liên tục: `admin`,
 * `driver`, `driver_leader`, `cs`, `kho`, `ketoan`, `manager`, `sep`.
 *
 * `switch` trả [LoginSession] theo ĐÚNG hợp đồng của BE (token + roles của tài khoản đích) để
 * màn hình và `App.kt` chạy y hệt đường thật — chỉ khác token là chuỗi giả do [mockToken] sinh.
 *
 * ⚠️ Token giả KHÔNG phải literal trong mã (self-check khối 8/9 chặn token hardcode): nó được
 * sinh từ `login` nên không có chuỗi bí mật nào nằm trong file này.
 * =====================================================================================
 */
class MockAdminAccountsRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
) : AdminAccountsRepo {

    override suspend fun accounts(): Result<List<DemoAccount>> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)

        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không tải được danh sách tài khoản demo: máy chủ Odoo không phản hồi. " +
                        "Kiểm tra Odoo đã chạy rồi bấm Tải lại.",
                ),
            )
        }
        if (scenario == DemoScenario.Empty) return Result.success(emptyList())
        return Result.success(ACCOUNTS)
    }

    override suspend fun switch(login: String): Result<LoginSession> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)

        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không chuyển được tài khoản: máy chủ Odoo không phản hồi. Phiên hiện tại " +
                        "vẫn giữ nguyên.",
                ),
            )
        }

        val account = ACCOUNTS.firstOrNull { it.login == login.trim() }
            ?: return Result.failure(
                IllegalStateException("Không tìm thấy tài khoản \"${login.trim()}\" trong danh sách demo."),
            )
        return Result.success(
            LoginSession(
                token = mockToken(account.login),
                tokenType = "LabToken",
                expiresAt = MOCK_EXPIRES_AT,
                uid = MOCK_UID,
                userName = account.name,
                driverUserId = null,
                // Đúng như BE: `driver_name` vắng ở phản hồi switch, app fallback về tên tài khoản.
                driverName = account.name,
                jobsToday = MOCK_JOBS_TODAY,
                roles = account.roles,
                login = account.login,
                // Đúng như BE-01: SAU khi admin chuyển, phiên VẪN được phép chuyển vai trò
                // (`canSwitch = true`) ⇒ nút dropdown ở thanh trên còn hiện để đổi tiếp.
                canSwitch = true,
            ),
        )
    }

    private companion object {
        const val NORMAL_DELAY_MS = 500L
        const val SLOW_DELAY_MS = 2_000L

        /** Token giả của chế độ mẫu — SINH từ login, không phải bí mật nằm trong mã. */
        fun mockToken(login: String): String = "mock-" + login

        /** Mốc hạn hiển thị của phiên mẫu (chỉ để UI có chuỗi, không dùng để tính hạn). */
        const val MOCK_EXPIRES_AT = "2099-12-31 23:59:59"
        const val MOCK_UID = 1
        const val MOCK_JOBS_TODAY = 2

        /**
         * 8 tài khoản demo — ĐÚNG các vai trò app có màn hình. Tên đọc được như `hr.employee`
         * của Odoo (kèm hậu tố đơn vị) để dòng chọn giống dữ liệu thật.
         */
        val ACCOUNTS: List<DemoAccount> = listOf(
            DemoAccount(login = "admin", name = "Quản trị viên", roles = listOf(LabRole.ADMIN)),
            DemoAccount(login = "taixe01", name = "Trần Văn Hùng (tài xế)", roles = listOf(LabRole.DRIVER)),
            DemoAccount(login = "taixe02", name = "Nguyễn Thị Mai (tài xế)", roles = listOf(LabRole.DRIVER)),
            DemoAccount(
                login = "nhomtruong01",
                name = "Phạm Minh Nhóm (trưởng nhóm tài xế)",
                roles = listOf(LabRole.DRIVER_LEADER),
            ),
            DemoAccount(login = "cs01", name = "Lê Thu Hà (CS)", roles = listOf(LabRole.CS)),
            DemoAccount(login = "kho01", name = "Võ Anh Khoa (thủ kho)", roles = listOf(LabRole.KHO)),
            DemoAccount(login = "ketoan01", name = "Đỗ Thị Kim (kế toán)", roles = listOf(LabRole.KETOAN)),
            DemoAccount(
                login = "sep01",
                name = "Nguyễn Văn Sếp (ban giám đốc)",
                roles = listOf(LabRole.SEP, LabRole.MANAGER),
            ),
        )
    }
}
