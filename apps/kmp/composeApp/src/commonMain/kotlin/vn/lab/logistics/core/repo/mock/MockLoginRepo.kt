package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.repo.LoginRepo

/**
 * ⚠️ IMPL GIẢ LẬP — KHÔNG gọi mạng. Chỉ dùng ở Wave A (chưa có Ktor/offline build).
 *
 * Quy ước demo (ghi rõ trên màn hình để không ai tưởng là AI/backend thật):
 *  - API key >= 8 ký tự  → đăng nhập THÀNH CÔNG
 *  - API key chứa "sai"  → đăng nhập LỖI (mô phỏng Odoo trả `uid = false`)
 *  - Kịch bản "Lỗi máy chủ" → lỗi kết nối (mô phỏng `Connection refused` khi Odoo chưa bật)
 *
 * KHÔNG lưu gì xuống đĩa: API key chỉ sống trong bộ nhớ của màn hình. Không log, không ghi file
 * (yêu cầu bảo mật của Wave A + docs/02 §6.3).
 */
class MockLoginRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
) : LoginRepo {

    override suspend fun login(
        server: String,
        db: String,
        login: String,
        apiKey: String,
    ): Result<Unit> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)

        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không kết nối được tới máy chủ Odoo tại \"$server\". " +
                        "Kiểm tra máy chủ đã chạy chưa (docker compose … up -d).",
                ),
            )
        }

        val key = apiKey.trim()
        if (key.lowercase().contains(ERROR_KEYWORD)) {
            return Result.failure(
                IllegalStateException(
                    "Đăng nhập thất bại: sai tên đăng nhập hoặc API key. " +
                        "Kiểm tra lại tài khoản \"$login\" trên cơ sở dữ liệu \"$db\".",
                ),
            )
        }
        if (key.length < MIN_API_KEY_LENGTH) {
            return Result.failure(
                IllegalStateException(
                    "API key quá ngắn — Odoo 17 cần API key tạo từ người dùng (res.users.apikeys), " +
                        "không dùng mật khẩu đăng nhập web.",
                ),
            )
        }
        return Result.success(Unit)
    }

    private companion object {
        const val NORMAL_DELAY_MS = 1_200L
        const val SLOW_DELAY_MS = 2_500L
        const val MIN_API_KEY_LENGTH = 8
        const val ERROR_KEYWORD = "sai"
    }
}
