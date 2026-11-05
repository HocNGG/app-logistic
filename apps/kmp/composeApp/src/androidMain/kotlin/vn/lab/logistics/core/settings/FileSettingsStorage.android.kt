package vn.lab.logistics.core.settings

import android.content.Context
import android.content.SharedPreferences
import vn.lab.logistics.core.net.APP_LOG_TAG
import vn.lab.logistics.core.net.logTechnicalError

/**
 * =====================================================================================
 * LƯU CẤU HÌNH TRÊN ANDROID — SharedPreferences.
 *
 * Bản desktop (`FileSettingsStorage.desktop.kt`) ghi một tệp `session.properties` trong
 * `~/.applogictics/`. Trên Android, chỗ đúng để lưu vài khoá cấu hình là **SharedPreferences**
 * của app (thư mục dữ liệu riêng của app, bị gỡ khi Uninstall) — không ghi ra bộ nhớ ngoài,
 * không cần quyền lưu trữ.
 *
 * GIỐNG BẢN DESKTOP Ở BA ĐIỂM:
 *  1. Cùng interface [SettingsStorage] (`read()` / `write()`), nên `AppSettings` không biết
 *     mình đang chạy trên nền tảng nào.
 *  2. **Nội dung do `AppSettings.persist()` quyết định** — whitelist khoá cố định, KHÔNG có mật khẩu
 *     / API key. Lớp này không tự thêm khoá nào.
 *  3. Lỗi đọc/ghi **không** được làm chết app: mất cấu hình thì người dùng nhập lại ở màn
 *     Đăng nhập.
 *
 * KHÁC BIỆT CÓ CHỦ ĐÍCH: [write] **xoá sạch trước khi ghi**. Bản desktop ghi lại cả tệp nên
 * khoá rỗng biến mất tự nhiên; SharedPreferences thì `putString` cộng dồn, nên nếu chỉ put
 * thì `token` cũ sẽ sống lại sau khi "Đăng xuất" (`clearSession()` ghi token rỗng) — đó là lỗi
 * bảo mật, không phải chi tiết nhỏ.
 * =====================================================================================
 */
class FileSettingsStorage(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) : SettingsStorage {

    // applicationContext: không giữ reference tới Activity (tránh memory leak khi xoay máy).
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun read(): Map<String, String> = try {
        prefs.all
            .mapNotNull { (key, value) ->
                (value as? String)?.takeIf { it.isNotBlank() }?.let { key to it }
            }
            .toMap()
    } catch (e: Exception) {
        emptyMap() // prefs hỏng/quyền bị chặn → coi như chưa có cấu hình
    }

    override fun write(newValues: Map<String, String>) {
        try {
            val editor = prefs.edit()
            editor.clear() // xem KDoc: tránh khoá cũ sống lại sau khi đăng xuất
            newValues.forEach { (key, value) ->
                if (value.isNotEmpty()) editor.putString(key, value)
            }
            editor.apply()
        } catch (e: Exception) {
            // Không ghi được thì app vẫn chạy, nhưng PHẢI thấy trong logcat: mất token/roles ở đây
            // chính là nguyên nhân "mở lại app vẫn ở màn Đăng nhập" mà không ai biết vì sao.
            logTechnicalError(APP_LOG_TAG, "Ghi SharedPreferences thất bại (mất cấu hình + phiên)", e)
        }
    }

    companion object {
        /** Tên tệp prefs riêng của app; đổi tên = người dùng mất cấu hình đã lưu. */
        const val DEFAULT_PREFS_NAME: String = "applogictics_session"
    }
}
