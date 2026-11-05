package vn.lab.logistics.core.net

/**
 * =====================================================================================
 * LỖI MẠNG/Odoo — MỌI lỗi hiển thị cho người dùng phải đi qua file này.
 *
 * Luật (AGENTS.md Luật 6 + skill 04 §5): người dùng **không bao giờ** thấy stacktrace,
 * tiếng Anh mặc định của Odoo, hay `{"code":"TOKEN_REQUIRED"}` thô. Mỗi nhánh dưới đây
 * mang sẵn một câu tiếng Việt có dấu, đúng thuật ngữ nghiệp vụ.
 *
 * Ngữ nghĩa ĐÃ ĐO trên Odoo 17 thật (2026-10-28, FE-01 gọi `http://localhost:8069`,
 * DB `odoo17_lab`):
 *
 * | Server trả về                              | Map thành                        |
 * |--------------------------------------------|----------------------------------|
 * | 401 + `{ok:false,error:{code:TOKEN_*}}`    | [ApiException.SessionExpired]     |
 * | 200 + **trang HTML login Odoo** (không JSON)| [ApiException.SessionExpired]     |
 * | `uid=false` ở `/web/session/authenticate`  | `null` ⇒ [ApiException.LoginFailed]|
 * | không kết nối được (Connection refused…)   | [ApiException.Network]            |
 * | 409 `INVALID_STATE`, 400 `VALIDATION`…     | [ApiException.Server] (giữ msg BE)|
 * =====================================================================================
 */

/** Câu chuẩn khi phiên hết hạn (báo cáo FE-01 gọi là `PhienHetHan`). */
const val MSG_PHIEN_HET_HAN: String = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."

/** Câu chuẩn khi không bắt được tín hiệu từ máy chủ (mạng đứt, Odoo chưa bật). */
const val MSG_KHONG_KET_NOI: String = "Không kết nối được máy chủ Odoo. Kiểm tra mạng rồi thử lại."

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * `PhienHetHan` — 401, token hết hạn, hoặc Odoo trả trang HTML đăng nhập.
     * Repo luôn xoá token trong `AppSettings` trước khi trả lỗi này để buộc đăng nhập lại.
     */
    class SessionExpired(
        message: String = MSG_PHIEN_HET_HAN,
        cause: Throwable? = null,
    ) : ApiException(message, cause)

    /** Sai máy chủ/DB/tài khoản/mật khẩu (`uid=false`). */
    class LoginFailed(
        message: String = "Sai máy chủ, cơ sở dữ liệu, tên đăng nhập hoặc mật khẩu/API key.",
    ) : ApiException(message)

    /** Không mở được kết nối TCP/HTTP tới Odoo. Luôn nêu ĐỊA CHỈ để người dùng biết kiểm tra gì. */
    class Network(
        val baseUrl: String,
        cause: Throwable? = null,
    ) : ApiException(
        "Không kết nối được Odoo ở $baseUrl. Kiểm tra máy chủ đã chạy chưa " +
            "(docker compose … up -d) và app có mạng không, rồi thử lại.",
        cause,
    )

    /** 403 — có phiên nhưng không đủ quyền (ir.rule chặn). */
    class Forbidden(
        message: String = "Bạn không có quyền thực hiện thao tác này.",
    ) : ApiException(message)

    /** Lỗi nghiệp vụ/định dạng do máy chủ trả — giữ nguyên câu tiếng Việt của BE nếu có. */
    class Server(message: String, cause: Throwable? = null) : ApiException(message, cause)
}

/** `throwable` có phải "phiên hết hạn" (phải bắt người dùng đăng nhập lại) hay không. */
fun Throwable.isPhienHetHan(): Boolean = this is ApiException.SessionExpired

/**
 * Đổi mọi `Throwable` thành câu tiếng Việt cho UI. Đây là **cửa duy nhất** đi ra màn hình,
 * nhờ vậy không lộ stacktrace/tiếng Anh ở bất kỳ màn nào.
 */
fun Throwable.toVietnameseMessage(): String = when (this) {
    is ApiException.SessionExpired -> message ?: MSG_PHIEN_HET_HAN
    is ApiException.LoginFailed -> message ?: "Đăng nhập không thành công."
    is ApiException.Network -> message ?: MSG_KHONG_KET_NOI
    is ApiException.Forbidden -> message ?: "Bạn không có quyền thực hiện thao tác này."
    is ApiException.Server -> message ?: "Máy chủ Odoo trả lỗi không xác định."
    else -> "Có lỗi xảy ra khi kết nối Odoo. Vui lòng thử lại."
}
