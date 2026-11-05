package vn.lab.logistics.core.settings

import java.io.File

/**
 * Lưu cấu hình vào MỘT tệp văn bản nhỏ trong thư mục người dùng
 * (`%USERPROFILE%\.applogictics\session.properties` trên Windows).
 *
 * VÌ SAO tệp phẳng, không dùng thư viện:
 *  - `multiplatform-settings` là dependency MỚI; Wave B chỉ được thêm Ktor (yêu cầu của task).
 *  - Chỉ vài khoá `key=value` không bí mật → đọc/ghi bằng `kotlin.io` là đủ, không rủi ro API lạ.
 *
 * BẢO MẬT: nội dung tệp do [AppSettings.persist] quyết định (whitelist khoá cố định, **không** có
 * mật khẩu/API key). Lỗi đọc/ghi **không** được làm chết app: cấu hình chỉ là tiện ích, mất
 * cấu hình thì người dùng nhập lại ở màn Đăng nhập.
 */
class FileSettingsStorage(
    private val file: File = defaultFile(),
) : SettingsStorage {

    override fun read(): Map<String, String> = try {
        if (!file.exists()) {
            emptyMap()
        } else {
            file.readLines(Charsets.UTF_8)
                .mapNotNull { line ->
                    val at = line.indexOf('=')
                    if (at <= 0) null else line.substring(0, at).trim() to line.substring(at + 1).trim()
                }
                .toMap()
        }
    } catch (e: Exception) {
        emptyMap() // tệp hỏng/quyền bị chặn → coi như chưa có cấu hình
    }

    override fun write(newValues: Map<String, String>) {
        try {
            file.parentFile?.mkdirs()
            // Ghi bằng tay thay vì `file.writeText` để: (1) bỏ giá trị rỗng, (2) thứ tự khoá
            // cố định (dễ đọc khi RV-01 mở tệp kiểm), (3) không bao giờ ghi kèm bí mật.
            val text = newValues.entries
                .filter { it.value.isNotEmpty() }
                .joinToString(separator = "\n") { (key, value) -> "$key=$value" }
            file.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            // Không ghi được (đĩa đầy, quyền) → app vẫn phải chạy.
        }
    }

    companion object {
        /** `~/.applogictics/session.properties` — KHÔNG nằm trong repo, không bị commit. */
        fun defaultFile(): File {
            val home = System.getProperty("user.home") ?: "."
            return File(File(home, ".applogictics"), "session.properties")
        }
    }
}
