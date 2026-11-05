package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.net.APP_LOG_TAG
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.OdooClient
import vn.lab.logistics.core.net.logTechnicalError
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.LoginRepo
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * ĐĂNG NHẬP ODOO THẬT — 2 BƯỚC, cả hai đều bắt buộc (đã gọi và đo 2026-10-28).
 *
 * ```
 * BƯỚC 1  POST /web/session/authenticate        {"db","login","password"}
 *         ← result.uid = 12                     (uid=false ⇒ sai thông tin, trả null)
 *         ← Set-Cookie: session_id=…            ⚠️ session_id KHÔNG có trong body
 *                                               ⇒ client phải giữ cookie jar (HttpCookies)
 *
 * BƯỚC 2  POST /lab/api/v1/auth/login           (kèm cookie ở trên, body JSON-RPC rỗng)
 *         ← {"ok":true,"data":{token, tokenType, expiresAt, uid, name,
 *            driver_id:false, driver_user_id, driver_name, jobsToday}}
 * ```
 *
 * Vì sao PHẢI 2 bước: bước 1 do Odoo tự kiểm mật khẩu/API key và dựng `env` = user thật
 * (⇒ `ir.rule` áp dụng, Luật 3); bước 2 đổi phiên đó thành token 8 giờ cho `/lab/api/v1`.
 * App **không** tự gọi `Session.authenticate` phía server, không tự phát token.
 *
 * `driver_id` LUÔN `false` (portal Odoo 17 không đọc được `hr.employee`, ADR-004) ⇒ định danh
 * tài xế lấy từ `driver_user_id` + `driver_name`, không dùng `driver_id`.
 *
 * BẢO MẬT: mật khẩu/API key chỉ đi vào tham số của hàm này rồi hết — không ghi tệp, không log,
 * không vào `AppSettings` (chỉ token 8 giờ được lưu).
 * =====================================================================================
 */
class RealLoginRepo(
    private val client: OdooClient,
    private val api: LabApi,
    private val settings: AppSettings,
) : LoginRepo {

    override suspend fun login(
        server: String,
        db: String,
        login: String,
        apiKey: String,
    ): Result<Unit> {
        val base = server.trim().trimEnd('/')
        if (base.isBlank()) {
            return Result.failure(
                ApiException.LoginFailed("Vui lòng nhập địa chỉ máy chủ Odoo (vd: https://erp.congty.vn:8069)."),
            )
        }
        if (db.isBlank()) {
            return Result.failure(ApiException.LoginFailed("Vui lòng nhập tên cơ sở dữ liệu."))
        }
        if (login.isBlank() || apiKey.isBlank()) {
            return Result.failure(
                ApiException.LoginFailed("Vui lòng nhập tên đăng nhập và mật khẩu (hoặc API key)."),
            )
        }

        // Ghi nhớ phần KHÔNG bí mật trước khi gọi mạng: nếu sai mật khẩu, lần sau vẫn còn
        // sẵn máy chủ/DB/tài khoản để thử lại nhanh.
        settings.updateBaseUrl(base)
        settings.updateDb(db)
        settings.updateLogin(login)

        return try {
            // ---- BƯỚC 1: phiên web Odoo 17 (cookie session_id CHỈ có trong Set-Cookie) ----
            val uid = client.authenticate(db = db.trim(), login = login.trim(), password = apiKey)
                ?: throw ApiException.LoginFailed(
                    "Sai máy chủ, cơ sở dữ liệu, tên đăng nhập hoặc mật khẩu/API key. " +
                        "Kiểm tra lại tài khoản \"${login.trim()}\" trên cơ sở dữ liệu \"${db.trim()}\".",
                )

            // ---- BƯỚC 2: đổi phiên thành token 8 giờ của lab_api ----
            val session = api.authLogin()
            // Bước 2 hiện luôn trả `uid`; nếu server đổi mà bỏ field này thì vẫn dùng uid bước 1
            // (đã xác thực thật) thay vì để UI hiện "chưa rõ tài khoản".
            settings.saveSession(if (session.uid == null) session.copy(uid = uid) else session)
            Result.success(Unit)
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: Throwable) {
            // Lưới an toàn: mọi thứ chưa map được vẫn ra câu tiếng Việt, không lộ stacktrace.
            // Nhưng PHẢI có log kỹ thuật (logcat tag AppLogictics) — nếu không, một lỗi lạ ở
            // bước 1/2 sẽ chỉ hiện "Có lỗi xảy ra khi kết nối Odoo" và không ai biết vì sao.
            // Không log `apiKey`: chỉ máy chủ + tài khoản (không phải bí mật).
            logTechnicalError(
                APP_LOG_TAG,
                "Đăng nhập thất bại ở bước không xác định · máy chủ=${settings.baseUrl}" +
                    " · db=${db.trim()} · tài khoản=${login.trim()}",
                e,
            )
            Result.failure(ApiException.Server(e.toVietnameseMessage(), e))
        }
    }
}
