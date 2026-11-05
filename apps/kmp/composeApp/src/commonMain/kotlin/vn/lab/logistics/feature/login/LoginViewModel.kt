package vn.lab.logistics.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.LoginRepo
import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.ui.components.UiState

/**
 * =====================================================================================
 * ViewModel của màn Đăng nhập — BỐN TRẠNG THÁI, không có trạng thái thứ năm.
 *
 *   UiState.Empty   → form trắng (chưa đăng nhập)          ← docs/04 §3: Empty
 *   UiState.Loading → "Đang xác thực với Odoo…"
 *   UiState.Error   → thông báo tiếng Việt + nút "Thử lại"
 *   UiState.Content → đăng nhập xong, có đường vào app
 *
 * Wave B: form KHÔNG còn trống — máy chủ / DB / tài khoản được nạp từ [AppSettings] (mặc định
 * của lab: `http://localhost:8069`, `odoo17_lab`) để người dùng chỉ phải gõ mật khẩu. Đây là
 * "default trong Settings, UI hiển thị được", **không** phải URL hardcode trong màn hình.
 *
 * Vì sao ViewModel này KHÔNG giữ `CoroutineScope` riêng: trên desktop, `Dispatchers.Main`
 * cần thêm artifact coroutines-swing (không có trong build offline). Caller truyền scope của
 * Compose (`rememberCoroutineScope()`) → coroutine chạy trên đúng luồng UI, không thêm dependency.
 *
 * BẢO MẬT: mật khẩu/API key chỉ nằm trong bộ nhớ của object này. **Không** ghi file, **không**
 * log, **không** vào `AppSettings`. Đăng xuất thì xoá hẳn (`reset()`).
 * =====================================================================================
 */
class LoginViewModel(
    private val repo: LoginRepo,
    private val settings: AppSettings? = null,
) {

    // ---------------------------------------------------------------- Dữ liệu form (lifted)
    var server by mutableStateOf(settings?.baseUrl ?: "")
        private set
    var db by mutableStateOf(settings?.db ?: "")
        private set
    var login by mutableStateOf(settings?.login ?: "")
        private set

    /** Mật khẩu HOẶC API key của Odoo 17 (API key dùng thay mật khẩu). Không lưu ra tệp. */
    var apiKey by mutableStateOf("")
        private set

    /** Hiện/ẩn mật khẩu — mặc định ẨN (không phơi bí mật lên màn hình khi đang demo). */
    var apiKeyVisible by mutableStateOf(false)

    var state by mutableStateOf<UiState<Unit>>(UiState.Empty)
        private set

    val isLoading: Boolean get() = state is UiState.Loading
    val isSuccess: Boolean get() = state is UiState.Content

    /**
     * Cảnh báo bảo mật thật (không phải trang trí): Odoo dev thường chạy qua HTTP thường, nghĩa
     * là mật khẩu/API key đi qua kết nối KHÔNG mã hoá. Chỉ chấp nhận được với máy chủ thử nghiệm.
     *
     * Địa chỉ KHÔNG ghi scheme cũng bị coi là HTTP: `AppSettings.normalizeBaseUrl` tự thêm
     * `http://` khi người dùng gõ `127.0.0.1:8069` (rất hay gặp trên bàn phím điện thoại).
     */
    val usesPlainHttp: Boolean
        get() = server.isNotBlank() && !server.trim().startsWith("https://", ignoreCase = true)

    fun onServerChange(value: String) {
        server = value
        // GHI NHỚ NGAY (chưa cần đăng nhập thành công): nếu chỉ lưu lúc đăng nhập thì người dùng
        // sửa ô "Máy chủ Odoo" mà thông báo lỗi vẫn nêu địa chỉ cũ → tưởng app bỏ qua ô nhập.
        settings?.rememberBaseUrl(value)
        clearError()
    }

    fun onDbChange(value: String) {
        db = value
        clearError()
    }

    fun onLoginChange(value: String) {
        login = value
        clearError()
    }

    fun onApiKeyChange(value: String) {
        apiKey = value
        clearError()
    }

    /**
     * Gửi đăng nhập. Chặn gửi lặp (double-click, Enter hai lần) — bài học từ demo thật:
     * hai lần gọi authenticate song song làm session sau ghi đè session trước.
     *
     * Với nguồn Odoo thật, repo chạy đúng **2 bước**: `/web/session/authenticate` (giữ cookie
     * `session_id`) → `/lab/api/v1/auth/login` (đổi thành token 8 giờ).
     */
    fun submit(scope: CoroutineScope) {
        if (state is UiState.Loading) return

        val missing = buildList {
            if (server.isBlank()) add("máy chủ Odoo")
            if (db.isBlank()) add("cơ sở dữ liệu")
            if (login.isBlank()) add("tên đăng nhập")
            if (apiKey.isBlank()) add("mật khẩu hoặc API key")
        }
        if (missing.isNotEmpty()) {
            state = UiState.Error(
                message = "Vui lòng nhập đủ: ${missing.joinToString(", ")}.",
                retry = { submit(scope) },
            )
            return
        }

        state = UiState.Loading
        scope.launch {
            val result = repo.login(
                server = server.trim(),
                db = db.trim(),
                login = login.trim(),
                // KHÔNG trim: mật khẩu/API key có thể chứa ký tự có nghĩa ở cuối.
                apiKey = apiKey,
            )
            state = result.fold(
                onSuccess = { UiState.Content(Unit) },
                onFailure = { error ->
                    UiState.Error(
                        // Mọi lỗi đã được repo map sang tiếng Việt; `toVietnameseMessage()`
                        // là lưới an toàn cuối cùng (không lộ stacktrace ra UI).
                        message = error.message ?: error.toVietnameseMessage(),
                        retry = { submit(scope) },
                    )
                },
            )
        }
    }

    /** Đăng xuất: xoá mật khẩu khỏi bộ nhớ, xoá token, về form trắng. */
    fun reset() {
        apiKey = ""
        apiKeyVisible = false
        settings?.clearSession()
        state = UiState.Empty
    }

    /** Xoá lỗi khi người dùng bắt đầu sửa — không bắt họ bấm "Thử lại" chỉ để gõ tiếp. */
    private fun clearError() {
        if (state is UiState.Error) state = UiState.Empty
    }
}
