package vn.lab.logistics.core.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * OdooClient — MỘT `HttpClient` dùng chung cho cả app (Wave B: gọi Odoo THẬT).
 *
 * Ba sự thật Odoo 17 đã kiểm chứng, code này tuân thủ đúng (docs/02 §6.3, ADR-004):
 *
 * 1. `POST /web/session/authenticate` trả `uid`; **`session_id` CHỈ có trong `Set-Cookie`**,
 *    không có trong body ⇒ phải giữ cookie jar. Vì vậy `install(HttpCookies)` — client tự
 *    lưu/gửi `session_id` cho các request sau (jar sống trong RAM, không ghi ra đĩa).
 * 2. Odoo 17 **KHÔNG** có `Authorization: Bearer` (đó là 19.0/`/json/2`). Bearer/token của
 *    `/lab/api/v1` do controller CỦA MÌNH kiểm tra qua header `X-Lab-Token` (xem `LabApi`).
 * 3. `uid = false` (hoặc 0) nghĩa là **sai DB/tài khoản/mật khẩu** — trả `null`, KHÔNG ném lỗi
 *    mạng, để repo tự map sang câu tiếng Việt.
 *
 * Địa chỉ máy chủ (`baseUrl`) **luôn** lấy từ [AppSettings] (người dùng nhập ở màn Đăng nhập
 * hoặc sửa ở thanh demo) — KHÔNG có chuỗi URL nào viết cứng trong lớp này.
 * =====================================================================================
 */

/** Kết quả thô của một lời gọi HTTP: đủ để phân biệt 401 / HTML / JSON. */
data class HttpResult(
    val status: Int,
    val body: String,
    val setCookies: List<String> = emptyList(),
)

/**
 * Cấu hình client — khai ở `commonMain`, engine (CIO) do `desktopMain` cắm vào qua
 * [newOdooHttpClient].
 */
internal fun HttpClientConfig<*>.installOdooPlugins() {
    // Không để Ktor ném exception trước khi ta kịp đọc body: 401 và "200 + HTML login"
    // là hai trạng thái NGHIỆP VỤ (phiên hết hạn), phải do app map (skill 04 §1).
    expectSuccess = false

    // Cookie jar: `session_id` của Odoo 17 chỉ đi qua `Set-Cookie` (docs/02 §6.3).
    install(HttpCookies) {
        // Mặc định = AcceptAllCookiesStorage: giữ cookie trong RAM, không ghi ra tệp.
    }

    // Sẵn cho các DTO `@Serializable`; phần đọc envelope vẫn làm thủ công để phát hiện HTML.
    install(ContentNegotiation) {
        json(AppJson)
    }

    // Timeout 15s theo skill 04 §2 — mạng 3G ngoài hiện trường vẫn kịp; quá lâu thì tài xế
    // tưởng app treo. Kết nối TCP chặt hơn (5s) để báo lỗi nhanh khi Odoo chưa bật.
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        socketTimeoutMillis = REQUEST_TIMEOUT_MS
    }
}

private const val REQUEST_TIMEOUT_MS = 15_000L
private const val CONNECT_TIMEOUT_MS = 5_000L

/**
 * `expect`/`actual`: engine HTTP chỉ tồn tại ở từng nền tảng (`desktopMain` dùng CIO).
 * Nhờ vậy `App.kt` (commonMain) tạo được client mà không biết gì về CIO.
 */
expect fun newOdooHttpClient(): HttpClient

class OdooClient(
    private val http: HttpClient,
    private val settings: AppSettings,
) {

    /** Địa chỉ máy chủ đang dùng (bỏ `/` cuối để không sinh `//web/session/...`). */
    val baseUrl: String get() = settings.baseUrl.trim().trimEnd('/')

    /** Đã có cookie phiên (`session_id`) trong jar chưa — hiển thị được ở UI demo. */
    var hasSessionCookie: Boolean = false
        private set

    private var rpcId: Int = 0

    /**
     * BƯỚC 1 của luồng đăng nhập: mở phiên web Odoo 17.
     *
     * @return `uid` khi thành công, `null` khi `uid=false` (sai máy chủ/DB/tài khoản/mật khẩu).
     *         Ném [ApiException.Network] khi không kết nối được (message nêu rõ địa chỉ).
     */
    suspend fun authenticate(db: String, login: String, password: String): Int? {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "call")
            put("id", ++rpcId)
            put(
                "params",
                buildJsonObject {
                    put("db", db)
                    put("login", login)
                    // API key (`res.users.apikeys`) đi vào ĐÚNG chỗ này — Odoo 17 dùng thay mật khẩu.
                    put("password", password)
                },
            )
        }
        val result = postJson("/web/session/authenticate", payload.toString())
        rememberSessionCookie(result.setCookies)
        return parseAuthenticateUid(result)
    }

    /** POST JSON tới `path` (tương đối với `baseUrl`); không bao giờ ném lỗi HTTP thô. */
    suspend fun postJson(
        path: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResult = send(post = true, path = path, body = body, extraHeaders = extraHeaders)

    /** GET JSON tới `path`; dùng cho `GET /lab/api/v1/driver/jobs`. */
    suspend fun getJson(
        path: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResult = send(post = false, path = path, body = null, extraHeaders = extraHeaders)

    // ------------------------------------------------------------------ nội bộ

    private suspend fun send(
        post: Boolean,
        path: String,
        body: String?,
        extraHeaders: Map<String, String>,
    ): HttpResult {
        val url = url(path)
        val method = if (post) "POST" else "GET"
        // Mốc "ĐÃ cố gọi": thấy dòng này trong logcat nghĩa là app đã tới tầng mạng (không phải
        // kẹt ở UI/coroutine). Không log body, không log header ⇒ không lộ mật khẩu/API key/token.
        logTechnicalInfo(APP_LOG_TAG, "→ $method $url")
        try {
            val response: HttpResponse = if (post) {
                http.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body ?: EMPTY_JSON_BODY)
                    // ⚠️ đặt tên `extraHeaders`: `headers` bên trong lambda là property của
                    // HttpRequestBuilder, dùng lại tên sẽ bị che (shadow) → sai header.
                    extraHeaders.forEach { (key, value) -> if (value.isNotBlank()) header(key, value) }
                }
            } else {
                http.get(url) {
                    extraHeaders.forEach { (key, value) -> if (value.isNotBlank()) header(key, value) }
                }
            }
            logTechnicalInfo(APP_LOG_TAG, "← ${response.status.value} $method $url")
            return HttpResult(
                status = response.status.value,
                body = response.bodyAsText(),
                setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw e
        } catch (e: Throwable) {
            // ĐÂY là chỗ câu "Không kết nối được Odoo ở …" sinh ra. Câu tiếng Việt cho người dùng
            // KHÔNG đổi (Luật 6) — nhưng logcat phải trả lời được BA câu: exception thật là gì,
            // cleartext cho host này có bật không, và socket TCP thô từ chính app có mở được không.
            logNetworkFailure(url, e)
            throw ApiException.Network(baseUrl, e)
        }
    }

    private fun url(path: String): String {
        val base = baseUrl
        if (base.isBlank()) {
            throw ApiException.LoginFailed("Chưa cấu hình địa chỉ máy chủ Odoo. Nhập ở màn Đăng nhập.")
        }
        return if (path.startsWith("/")) base + path else "$base/$path"
    }

    /**
     * Ghi nhớ `session_id` từ `Set-Cookie`. Plugin `HttpCookies` đã tự giữ jar (đó là cơ chế
     * chính); biến này chỉ để UI demo biết "đã có phiên" và để log lỗi rõ ràng hơn.
     */
    private fun rememberSessionCookie(setCookies: List<String>) {
        val sessionId = setCookies.firstNotNullOfOrNull { cookie ->
            SESSION_COOKIE.find(cookie)?.groupValues?.getOrNull(1)
        }
        if (!sessionId.isNullOrBlank()) hasSessionCookie = true
    }

    /**
     * Đọc `result.uid` của `/web/session/authenticate`.
     * - `result` không có `uid` hoặc `uid=false/0` ⇒ `null` (sai thông tin đăng nhập).
     * - `error.data.name` chứa `AccessDenied/AccessError` ⇒ cũng là `null` (Odoo báo sai quyền
     *   đăng nhập bằng đúng exception này).
     */
    private fun parseAuthenticateUid(result: HttpResult): Int? {
        if (result.status == 401 || result.status == 403) return null
        if (result.status !in 200..299) {
            throw ApiException.LoginFailed(
                "Máy chủ Odoo từ chối yêu cầu đăng nhập (mã HTTP ${result.status}). " +
                    "Kiểm tra địa chỉ máy chủ \"$baseUrl\".",
            )
        }
        val text = result.body.trim()
        if (text.startsWith("<")) {
            throw ApiException.LoginFailed(
                "Máy chủ trả trang HTML thay vì JSON. Kiểm tra địa chỉ máy chủ \"$baseUrl\" " +
                    "và tên cơ sở dữ liệu.",
            )
        }
        val element = try {
            AppJson.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ApiException.LoginFailed(
                "Máy chủ không trả JSON khi đăng nhập. Kiểm tra địa chỉ \"$baseUrl\" và tên cơ sở dữ liệu.",
            )
        }
        val root = element as? JsonObject
            ?: throw ApiException.LoginFailed("Máy chủ trả dữ liệu đăng nhập không đúng định dạng.")

        (root["error"] as? JsonObject)?.let { error ->
            val data = error["data"] as? JsonObject
            val name = (data?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (name.contains("AccessDenied") || name.contains("AccessError")) return null
            throw ApiException.LoginFailed(
                "Máy chủ Odoo báo lỗi khi đăng nhập. Kiểm tra cơ sở dữ liệu và tài khoản, rồi thử lại.",
            )
        }

        val resultObject = root["result"] as? JsonObject ?: return null
        val uid = (resultObject["uid"] as? JsonPrimitive)?.intOrNull
        return if (uid == null || uid <= 0) null else uid
    }

    private companion object {
        const val EMPTY_JSON_BODY = "{}"
        val SESSION_COOKIE = Regex("session_id=([^;]+)")
    }
}
