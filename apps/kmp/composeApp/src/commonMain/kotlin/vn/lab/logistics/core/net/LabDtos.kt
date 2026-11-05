package vn.lab.logistics.core.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsDriver
import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.core.model.CsJobsPayload
import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.model.KhoKien
import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.KhoXuatKien
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.core.model.ScanResult

/**
 * =====================================================================================
 * DTO của nhóm endpoint `/lab/api/v1` — CHÉP ĐÚNG payload đã gọi thật (2026-10-28, DB `odoo17_lab`).
 *
 * Nguồn sự thật (đọc cả 2 phía, không đoán):
 *  - `addons/lab_api/tools.py`  : envelope `{ok, data, error}`, `tra_loi()` trả JSON thuần
 *    (`type="http"`, **không** bọc JSON-RPC), `gio_phut()` trả `"HH:mm"` **hoặc null**.
 *  - `addons/lab_api/controllers/driver.py` : `expectedPackages` **CHỈ có** khi
 *    `job.so_kien_du_kien` có giá trị ⇒ trường này **VẮNG** trong phần lớn payload
 *    (OQ-22) ⇒ ở DTO phải là `Int?` + có default, không được là `Int` trơn.
 *
 * Vì sao parse thủ công phần envelope thay vì `@Serializable` cả envelope:
 *  - phải phát hiện "200 + trang HTML login Odoo" (mất phiên) → coi là `PhienHetHan`;
 *  - phải chấp nhận cả body JSON-RPC bọc `result` (nếu BE đổi controller sang `type="json"`),
 *    nhờ vậy app không vỡ khi backend đổi kiểu route.
 * =====================================================================================
 */

/**
 * `Json` dùng CHUNG cho mọi phía (client + test + self-check).
 * - `ignoreUnknownKeys = true`: BE thêm field mới thì app cũ vẫn chạy.
 * - `explicitNulls = false`: field null không cần gửi lên.
 * - `isLenient = true`: chịu được chuỗi/số hơi lệch chuẩn (Odoo đôi khi trả `1` thay `true`).
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

private const val MSG_DU_LIEU_LA =
    "Máy chủ Odoo trả dữ liệu không đúng định dạng mà app mong đợi. Vui lòng thử lại."

// =====================================================================================
// 1. Đăng nhập — POST /lab/api/v1/auth/login (bước 2 của luồng 2 bước)
// =====================================================================================

/**
 * `data` của endpoint login. Ví dụ THẬT (đã đo):
 * ```json
 * {"token":"jAj9…","tokenType":"LabToken","expiresAt":"2026-10-28 14:27:56","uid":12,
 *  "name":"…","driver_id":false,"driver_user_id":12,
 *  "driver_name":"Trần Văn Hùng (tài xế)","jobsToday":2}
 * ```
 * ⚠️ `driver_id` LUÔN `false` — portal Odoo 17 không đọc được `hr.employee` (ADR-004) ⇒
 * app **không** dùng nó; định danh tài xế lấy từ `driver_user_id` + `driver_name`.
 */
@Serializable
data class LoginDataDto(
    val token: String = "",
    val tokenType: String? = null,
    val expiresAt: String? = null,
    val uid: Int? = null,
    val name: String? = null,
    /** Luôn `false` (Boolean) — để `JsonElement` cho khỏi vỡ nếu BE đổi sang id/None. */
    @SerialName("driver_id") val driverId: JsonElement? = null,
    @SerialName("driver_user_id") val driverUserId: Int? = null,
    @SerialName("driver_name") val driverName: String? = null,
    val jobsToday: Int? = null,
    /**
     * Vai trò của tài khoản — hợp đồng MỚI với BE-01 (mã: `admin` · `driver` · `driver_leader` ·
     * `cs` · `kho` · `ketoan` · `manager` · `sep`). Vắng/rỗng ⇒ app coi là "vai trò khác" (xem
     * `core/model/Workspace.kt`), KHÔNG đoán bừa là tài xế.
     */
    val roles: List<String> = emptyList(),
    /**
     * Tên đăng nhập của phiên. Có trong phản hồi của `POST /admin/switch` (để app ghi đúng
     * `login` vào Settings); `/auth/login` có thể vắng ⇒ `null`, khi đó app giữ `login` đang lưu.
     */
    val login: String? = null,
    /**
     * Phiên này có được CHUYỂN VAI TRÒ không — hợp đồng MỚI với BE-01, có ở CẢ `/auth/login` và
     * `/admin/switch`:
     *  - login bằng tài khoản `admin` ⇒ `true`;
     *  - login bằng tài khoản thường ⇒ `false`;
     *  - sau `/admin/switch` ⇒ **`true`** (phiên do admin chuyển vẫn gọi được 2 endpoint admin,
     *    nên nút chuyển vai trò phải còn — đúng yêu cầu của sếp).
     *
     * Để `JsonElement` (không phải `Boolean`) vì BE có thể trả `1`/`"true"` khi cấu hình lệch, và
     * app KHÔNG được vỡ vì một cờ phụ: sai kiểu ⇒ coi như `false` (xem [parseSessionPayload]).
     */
    val canSwitch: JsonElement? = null,
)

/** Bản đã chuẩn hoá để UI/settings dùng (không còn trường "có thể vắng"). */
data class LoginSession(
    val token: String,
    val tokenType: String,
    val expiresAt: String,
    val uid: Int?,
    val userName: String,
    val driverUserId: Int?,
    val driverName: String,
    val jobsToday: Int?,
    /** Vai trò của tài khoản — quyết định màn hình mở ra sau khi đăng nhập. */
    val roles: List<String> = emptyList(),
    /**
     * Tên đăng nhập của phiên (không phải bí mật). Vắng (`""`) ⇒ `AppSettings` giữ nguyên
     * `login` đang lưu (đăng nhập thường đã set qua `updateLogin`).
     */
    val login: String = "",
    /**
     * Được phép CHUYỂN VAI TRÒ trong phiên này (nút dropdown ở thanh trên). `false` ⇒ không hiện
     * nút — đó là trường hợp duy nhất làm nút mất: đăng nhập thẳng bằng tài khoản không phải admin.
     * Thiếu khoá ở payload ⇒ `false`.
     */
    val canSwitch: Boolean = false,
)

// =====================================================================================
// 2. Việc hôm nay — GET /lab/api/v1/driver/jobs
// =====================================================================================

/**
 * Một phần tử của `data` (mảng). Payload THẬT:
 * ```json
 * {"id":..,"ref":"LH/26/0005","shop":"Sen Đá Shop","address":"…","contactName":"…",
 *  "contactPhone":"…","windowFrom":"01:30","windowTo":"03:00","state":"assigned",
 *  "stateLabel":"Đã gán tài xế","note":"…","expectedPackages":?}
 * ```
 * Ghi chú đo được:
 *  - `windowFrom`/`windowTo` là **String `HH:mm`**; khi job không đặt giờ, BE trả **`"-"`**
 *    (hoặc `null` nếu đổi `gio_phut`) ⇒ UI hiển thị "Chưa hẹn giờ".
 *  - `contactName`/`contactPhone`/`note`/`address` trả **chuỗi rỗng** khi không có dữ liệu
 *    (controller dùng `or ""`) ⇒ phải map `""` → `null`.
 *  - `stateLabel` là nhãn tiếng Việt của server ⇒ **ưu tiên** hơn bảng nhãn cứng ở app.
 */
@Serializable
data class DriverJobDto(
    val id: Int = 0,
    val ref: String? = null,
    val shop: String? = null,
    val address: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val windowFrom: String? = null,
    val windowTo: String? = null,
    val state: String? = null,
    val stateLabel: String? = null,
    val note: String? = null,
    val expectedPackages: Int? = null,
)

// =====================================================================================
// 3. accept / capture / fail — `data` là `{state, stateLabel, attachment_ids?}`
// =====================================================================================

@Serializable
data class SubmitResultDto(
    val state: String? = null,
    val stateLabel: String? = null,
    @SerialName("attachment_ids") val attachmentIds: List<Int> = emptyList(),
    val duplicate: Boolean = false,
)

data class SubmitResult(
    val state: String?,
    val stateLabel: String?,
    val attachmentIds: List<Int>,
    val duplicate: Boolean,
)

/**
 * Body của `POST /lab/api/v1/driver/jobs/<id>/capture` — CHÉP ĐÚNG 5 khoá controller đọc
 * (`photoBase64`, `signatureBase64`, `packages`, `weightKg`, `clientUuid`).
 * `clientUuid` để server chống trùng khi app gửi lại do mất mạng (controller idempotent).
 */
data class CaptureRequest(
    val photoBase64: String,
    val signatureBase64: String,
    val packages: Int,
    val weightKg: Double,
    val clientUuid: String,
)

/** Dựng body capture. Tách hàm thuần để test được tên khoá mà không cần mạng. */
fun captureRequestBody(request: CaptureRequest): String = buildJsonObject {
    put("photoBase64", request.photoBase64)
    put("signatureBase64", request.signatureBase64)
    put("packages", request.packages)
    put("weightKg", request.weightKg)
    put("clientUuid", request.clientUuid)
}.toString()

/** Dựng body `fail`. Controller đọc `reason` (hoặc `ly_do`), bắt buộc không rỗng. */
fun failRequestBody(reason: String): String = buildJsonObject {
    put("reason", reason)
}.toString()

/**
 * Dựng body `POST /lab/api/v1/scan` (chốt 2026-11-02):
 * `{"ma", "moc", "clientUuid", "ghiChu"?, "thietBi"}`.
 *
 * `clientUuid` BẮT BUỘC (BE trả 400 nếu thiếu) — dùng để chống trùng khi mạng chập: app sinh một
 * UUID cho mỗi lần quét và giữ nguyên nếu người dùng bấm lại đúng lần đó.
 * `thietBi = "app"` để bảng tổng kết phân biệt quét từ app với quét từ web (máy quét USB).
 */
fun scanRequestBody(
    ma: String,
    moc: String,
    clientUuid: String,
    ghiChu: String? = null,
    thietBi: String = "app",
): String = buildJsonObject {
    put("ma", ma)
    put("moc", moc)
    put("clientUuid", clientUuid)
    put("thietBi", thietBi)
    ghiChu?.takeIf { it.isNotBlank() }?.let { put("ghiChu", it) }
}.toString()

// =====================================================================================
// 4. Đọc envelope `{ok, data, error}` + phát hiện trang HTML login
// =====================================================================================

object LabPayload {

    /** Mã lỗi của BE nghĩa là "phiên/mã truy cập có vấn đề" ⇒ buộc đăng nhập lại. */
    private val MA_PHIEN = setOf(
        "TOKEN_REQUIRED",
        "TOKEN_INVALID",
        "TOKEN_EXPIRED",
        "AUTH_REQUIRED",
        "AUTH_FAILED",
        "SESSION_EXPIRED",
    )

    /**
     * Trả về phần `data` của envelope; ném [ApiException] tiếng Việt với MỌI nhánh sai.
     *
     * @param status mã HTTP thật (401 ⇒ `PhienHetHan` ngay, không cần đọc body).
     * @param body   nội dung thô — có thể là HTML (Odoo redirect về `/web/login`).
     */
    fun data(status: Int, body: String): JsonElement {
        if (status == 401) throw ApiException.SessionExpired()

        val text = body.trim()
        if (text.isEmpty()) {
            throw ApiException.Server("Máy chủ Odoo trả nội dung rỗng (mã HTTP $status). Vui lòng thử lại.")
        }
        if (text.startsWith("<")) {
            // Odoo 17: không có phiên → 302 về /web/login → Ktor đi theo redirect → **200 + HTML**.
            // Đây vẫn là "phiên hết hạn", không phải lỗi lạ (đã đo 2026-10-28).
            throw ApiException.SessionExpired()
        }

        val element = try {
            AppJson.parseToJsonElement(text)
        } catch (e: Exception) {
            if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<html", ignoreCase = true)) {
                throw ApiException.SessionExpired(cause = e)
            }
            throw ApiException.Server(
                "Máy chủ Odoo trả dữ liệu không phải JSON (mã HTTP $status). Kiểm tra địa chỉ máy chủ.",
                e,
            )
        }

        val root = element as? JsonObject
            ?: throw ApiException.Server("$MSG_DU_LIEU_LA (mã HTTP $status)")

        // Nhận cả 2 dạng: `{ok, data, error}` (thật) và `{jsonrpc, result:{ok, …}}` (nếu BE đổi route).
        val envelope = if (root["ok"] == null) (root["result"] as? JsonObject) ?: root else root
        val ok = (envelope["ok"] as? JsonPrimitive)?.booleanOrNull

        if (ok == false) {
            val error = envelope["error"] as? JsonObject
            val code = error.stringOrNull("code")
            val message = error.stringOrNull("message")
            if (code != null && code in MA_PHIEN) throw ApiException.SessionExpired()
            if (status == 403) throw ApiException.Forbidden()
            throw ApiException.Server(
                message?.takeIf { it.isNotBlank() }
                    ?: "Máy chủ Odoo báo lỗi (mã ${code ?: status.toString()}). Vui lòng thử lại.",
            )
        }
        if (ok == null) throw ApiException.Server("$MSG_DU_LIEU_LA (thiếu trường \"ok\")")

        return envelope["data"] ?: JsonNull
    }
}

// =====================================================================================
// 5. Hàm parse DÙNG CHUNG cho app + self-check + test (một chỗ duy nhất)
// =====================================================================================

/**
 * Parse payload `GET /lab/api/v1/driver/jobs` → danh sách [DriverJob].
 * Dùng ở [LabApi]; self-check và `commonTest` gọi cùng hàm này với payload THẬT để chứng
 * minh parse đúng (không có bản sao thứ hai dễ lệch).
 */
fun parseDriverJobsPayload(body: String, status: Int = 200): List<DriverJob> {
    val data = LabPayload.data(status, body)
    if (data !is JsonArray) {
        throw ApiException.Server("Danh sách việc của tài xế không đúng định dạng mảng.")
    }
    // `decodeFromString(deserializer, text)` là hàm THÀNH VIÊN của `StringFormat`
    // (không phải extension reified) → không phụ thuộc import nào, khó vỡ khi nâng Ktor.
    val dtos = AppJson.decodeFromString(ListSerializer(DriverJobDto.serializer()), data.toString())
    return dtos.map { it.toDriverJob() }
}

/** Parse payload `POST /lab/api/v1/auth/login` → [LoginSession]. */
fun parseLoginPayload(body: String, status: Int = 200): LoginSession =
    parseSessionPayload(body, status, "Phản hồi đăng nhập không đúng định dạng.")

/**
 * Parse `data` của `POST /lab/api/v1/admin/switch` → [LoginSession].
 *
 * Hợp đồng với BE-01: phản hồi của `switch` có **ĐÚNG shape của login** (token, tokenType,
 * expiresAt, uid, name, login, roles, jobsToday) nên app dùng CHUNG một hàm map — không có bản
 * sao thứ hai để lệch khi BE đổi trường. Khác biệt duy nhất là câu lỗi khi payload sai định dạng.
 */
fun parseSwitchSessionPayload(body: String, status: Int = 200): LoginSession =
    parseSessionPayload(body, status, "Phản hồi chuyển tài khoản không đúng định dạng.")

private fun parseSessionPayload(body: String, status: Int, wrongShape: String): LoginSession {
    val data = LabPayload.data(status, body)
    if (data !is JsonObject) {
        throw ApiException.Server(wrongShape)
    }
    val dto = AppJson.decodeFromString(LoginDataDto.serializer(), data.toString())
    if (dto.token.isBlank()) {
        throw ApiException.SessionExpired("Máy chủ không trả mã truy cập (token). Vui lòng đăng nhập lại.")
    }
    return LoginSession(
        token = dto.token,
        tokenType = dto.tokenType.orEmpty(),
        expiresAt = dto.expiresAt.orEmpty(),
        uid = dto.uid,
        userName = dto.name.orEmpty(),
        driverUserId = dto.driverUserId,
        driverName = dto.driverName.orEmpty().ifBlank { dto.name.orEmpty() },
        jobsToday = dto.jobsToday,
        // Chuẩn hoá NGAY khi parse: bỏ khoảng trắng, bỏ phần tử rỗng, giữ nguyên thứ tự server
        // trả (thứ tự ưu tiên là quy tắc của app, xem `Workspace.fromRoles`).
        roles = dto.roles.mapNotNull { it.orNullIfBlank() },
        login = dto.login.orNullIfBlank().orEmpty(),
        // `canSwitch` là CỜ PHỤ: thiếu khoá, `null`, hay sai kiểu (`1`, `"yes"`, object…) đều là
        // `false` — không được để một cờ hiển thị làm hỏng cả lần đăng nhập.
        canSwitch = (dto.canSwitch as? JsonPrimitive)?.booleanOrNull ?: false,
    )
}

/** Parse `data` của accept/capture/fail. */
fun parseSubmitResult(body: String, status: Int = 200): SubmitResult {
    val data = LabPayload.data(status, body)
    if (data !is JsonObject) {
        throw ApiException.Server("Phản hồi cập nhật việc không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(SubmitResultDto.serializer(), data.toString())
    return SubmitResult(
        state = dto.state?.takeIf { it.isNotBlank() },
        stateLabel = dto.stateLabel?.takeIf { it.isNotBlank() },
        attachmentIds = dto.attachmentIds,
        duplicate = dto.duplicate,
    )
}

/**
 * Map DTO → model của app.
 *
 * Quy ước (vì controller trả `""` chứ không `null` khi thiếu dữ liệu):
 *  - chuỗi rỗng → `null` ⇒ UI hiện đúng câu "Chưa có …" thay vì khoảng trắng;
 *  - `expectedPackages` vắng ⇒ `null` ⇒ UI hiện "Chưa rõ số kiện" (KHÔNG bịa 0 kiện);
 *  - `state` lạ/rỗng ⇒ `new` để UI không vỡ; nhãn vẫn lấy `stateLabel` của server nếu có.
 */
fun DriverJobDto.toDriverJob(): DriverJob = DriverJob(
    id = id,
    ref = ref.orNullIfBlank() ?: "#$id",
    shop = shop.orNullIfBlank() ?: "Shop chưa có tên",
    address = address.orNullIfBlank() ?: "Chưa có địa chỉ lấy hàng",
    contactName = contactName.orNullIfBlank(),
    contactPhone = contactPhone.orNullIfBlank(),
    windowFrom = windowFrom.orNullIfBlank(),
    windowTo = windowTo.orNullIfBlank(),
    expectedPackages = expectedPackages,
    state = state.orNullIfBlank() ?: JobState.NEW,
    stateLabel = stateLabel.orNullIfBlank(),
    note = note.orNullIfBlank(),
)

// =====================================================================================
// 7. CS — ĐIỀU PHỐI: GET /lab/api/v1/cs/jobs · POST /lab/api/v1/cs/jobs/<id>/assign
// =====================================================================================

/**
 * Một phần tử `items` của `cs/jobs`. Mọi trường "có thể vắng" đều nullable + có default ⇒
 * BE trả thiếu (hoặc trả `null`) thì app vẫn parse được, chỉ hiện câu "Chưa có …" ở UI.
 */
@Serializable
data class CsJobDto(
    val id: Int = 0,
    val ref: String? = null,
    val shop: String? = null,
    val address: String? = null,
    val contactPhone: String? = null,
    val windowFrom: String? = null,
    val windowTo: String? = null,
    val state: String? = null,
    val stateLabel: String? = null,
    val expectedPackages: Int? = null,
    val driverName: String? = null,
    /**
     * Mốc tạo việc, ISO `yyyy-MM-dd HH:mm:ss` UTC (BE ship 2026-11-03).
     * Có default `null` để payload của server CŨ vẫn parse được — thiếu khoá này không được làm
     * app vỡ, chỉ là danh sách rơi về xếp theo `id`.
     */
    val createDate: String? = null,
)

/**
 * Một phần tử `drivers` — nguồn của ô chọn tài xế trên màn CS/phân công.
 *
 * `login` là trường MỚI của hợp đồng với BE-01 (server cũ chưa trả) ⇒ phải `String?` + default,
 * nếu khai `String` trơn thì payload cũ làm app vỡ ngay khi parse.
 */
@Serializable
data class CsDriverDto(
    val id: Int = 0,
    val name: String? = null,
    val login: String? = null,
)

/** `data` của `GET /lab/api/v1/cs/jobs`. */
@Serializable
data class CsJobsPayloadDto(
    val items: List<CsJobDto> = emptyList(),
    val drivers: List<CsDriverDto> = emptyList(),
)

/** `data` của `POST /lab/api/v1/cs/jobs/<id>/assign`. */
@Serializable
data class AssignResultDto(
    val state: String? = null,
    val stateLabel: String? = null,
    val driverName: String? = null,
)

/**
 * Dựng body `assign` — CHỈ một khoá `driverId` (đúng hợp đồng với BE-01).
 * Hàm thuần để test được tên khoá mà không cần mạng.
 */
fun assignRequestBody(driverId: Int): String = buildJsonObject {
    put("driverId", driverId)
}.toString()

/** Parse `data` của `cs/jobs` → [CsJobsPayload] (items + drivers). */
fun parseCsJobsPayload(body: String, status: Int = 200): CsJobsPayload {
    val data = LabPayload.data(status, body)
    // `data: null` (envelope `ok:true` mà không có dữ liệu) ⇒ danh sách rỗng, KHÔNG phải lỗi:
    // màn hình sẽ hiện empty state "Không còn việc nào cần gán tài xế".
    if (data is JsonNull) return CsJobsPayload()
    if (data !is JsonObject) {
        throw ApiException.Server("Danh sách việc của CS không đúng định dạng (thiếu items/drivers).")
    }
    val dto = AppJson.decodeFromString(CsJobsPayloadDto.serializer(), data.toString())
    return CsJobsPayload(
        items = dto.items.map { it.toCsJob() },
        // Tài xế không có id hợp lệ thì BỎ (không thể gán cho id 0 hay id âm).
        drivers = dto.drivers.mapNotNull { it.toCsDriver() },
    )
}

/** Parse `data` của `assign`. */
fun parseAssignResult(body: String, status: Int = 200): AssignOutcome {
    val data = LabPayload.data(status, body)
    if (data is JsonNull) return AssignOutcome(state = null, stateLabel = null, driverName = null)
    if (data !is JsonObject) {
        throw ApiException.Server("Phản hồi gán tài xế không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(AssignResultDto.serializer(), data.toString())
    return AssignOutcome(
        state = dto.state.orNullIfBlank(),
        stateLabel = dto.stateLabel.orNullIfBlank(),
        driverName = dto.driverName.orNullIfBlank(),
    )
}

/**
 * Map item CS → model. `state` vắng ⇒ `new` (giống `driver/jobs`) để UI không vỡ; nhãn vẫn ưu
 * tiên `stateLabel` của server.
 */
fun CsJobDto.toCsJob(): CsJob = CsJob(
    id = id,
    ref = ref.orNullIfBlank() ?: "#$id",
    shop = shop.orNullIfBlank() ?: "Shop chưa có tên",
    address = address.orNullIfBlank() ?: "Chưa có địa chỉ lấy hàng",
    contactPhone = contactPhone.orNullIfBlank(),
    windowFrom = windowFrom.orNullIfBlank(),
    windowTo = windowTo.orNullIfBlank(),
    state = state.orNullIfBlank() ?: JobState.NEW,
    stateLabel = stateLabel.orNullIfBlank(),
    expectedPackages = expectedPackages,
    driverName = driverName.orNullIfBlank(),
    createDate = createDate.orNullIfBlank(),
)

/**
 * Map tài xế; `id` không hợp lệ (≤ 0) ⇒ `null` để caller bỏ qua.
 * `login` rỗng/vắng ⇒ `null` (UI chỉ hiện tên, không hiện dấu "·" treo).
 */
fun CsDriverDto.toCsDriver(): CsDriver? {
    if (id <= 0) return null
    return CsDriver(
        id = id,
        name = name.orNullIfBlank() ?: "Tài xế #$id",
        login = login.orNullIfBlank(),
    )
}

// =====================================================================================
// 8. KHO — NHẬN HÀNG: GET /lab/api/v1/kho/cho-nhan · POST /lab/api/v1/kho/jobs/<id>/nhan
// =====================================================================================

/** Một phần tử `items` của `kho/cho-nhan` (việc đã `picked_up`). */
@Serializable
data class KhoJobDto(
    val id: Int = 0,
    val ref: String? = null,
    val shop: String? = null,
    val packages: Int? = null,
    val weightKg: Double? = null,
    val driverName: String? = null,
    val pickingName: String? = null,
    val state: String? = null,
    val stateLabel: String? = null,
    val saleOrderName: String? = null,
    val scannedPackages: Int? = null,
    val totalPackages: Int? = null,
)

/** `data` của `GET /lab/api/v1/kho/cho-nhan`. */
@Serializable
data class KhoChoNhanPayloadDto(
    val items: List<KhoJobDto> = emptyList(),
)

/** `data` của `POST /lab/api/v1/kho/jobs/<id>/nhan`. */
@Serializable
data class KhoReceiveResultDto(
    val state: String? = null,
    val stateLabel: String? = null,
    val pickingName: String? = null,
    val saleOrderName: String? = null,
)

/** Parse `data` của `kho/cho-nhan` → danh sách [KhoJob]. */
fun parseKhoChoNhanPayload(body: String, status: Int = 200): List<KhoJob> {
    val data = LabPayload.data(status, body)
    // `data: null` ⇒ không có việc nào chờ nhận (empty state), không phải lỗi định dạng.
    if (data is JsonNull) return emptyList()
    if (data !is JsonObject) {
        throw ApiException.Server("Danh sách hàng chờ nhận không đúng định dạng (thiếu items).")
    }
    val dto = AppJson.decodeFromString(KhoChoNhanPayloadDto.serializer(), data.toString())
    return dto.items.map { it.toKhoJob() }
}

/** Parse `data` của `nhan` — `pickingName` là SỐ PHIẾU NHẬP KHO kho cần đọc lại cho tài xế. */
fun parseKhoReceiveResult(body: String, status: Int = 200): ReceiveOutcome {
    val data = LabPayload.data(status, body)
    if (data is JsonNull) return ReceiveOutcome(state = null, stateLabel = null, pickingName = null)
    if (data !is JsonObject) {
        throw ApiException.Server("Phản hồi nhận hàng không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(KhoReceiveResultDto.serializer(), data.toString())
    return ReceiveOutcome(
        state = dto.state.orNullIfBlank(),
        stateLabel = dto.stateLabel.orNullIfBlank(),
        pickingName = dto.pickingName.orNullIfBlank(),
        saleOrderName = dto.saleOrderName.orNullIfBlank(),
    )
}

/** Map item kho → model; số kiện/kg vắng ⇒ `null` (UI hiện "Chưa rõ …", không bịa 0). */
fun KhoJobDto.toKhoJob(): KhoJob {
    val cleanPicking = pickingName.orNullIfBlank()
    val cleanState = state.orNullIfBlank()
        ?: if (cleanPicking != null) JobState.CONFIRMED else JobState.PICKED_UP
    return KhoJob(
        id = id,
        ref = ref.orNullIfBlank() ?: "#$id",
        shop = shop.orNullIfBlank() ?: "Shop chưa có tên",
        packages = packages,
        weightKg = weightKg,
        driverName = driverName.orNullIfBlank(),
        pickingName = cleanPicking,
        state = cleanState,
        stateLabel = stateLabel.orNullIfBlank(),
        saleOrderName = saleOrderName.orNullIfBlank(),
        scannedPackages = (scannedPackages ?: 0).coerceAtLeast(0),
        totalPackages = (totalPackages ?: 0).coerceAtLeast(0),
    )
}

/** `""` / toàn khoảng trắng ⇒ `null` (controller Odoo trả `or ""` cho field trống). */
internal fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

// =====================================================================================
// 8b. QUÉT TEM — POST /lab/api/v1/scan (chốt 2026-11-02)
// =====================================================================================

/** `data.tienDo` — `{daQuet, tong}` để app hiện `7/12`. */
@Serializable
data class ScanTienDoDto(
    val daQuet: Int = 0,
    val tong: Int = 0,
)

/**
 * `data` của `POST /lab/api/v1/scan`.
 *
 * ⚠️ `canhBao` là **boolean HOẶC string** tuỳ nhánh: controller trả `False` khi không có cảnh báo
 * và trả CÂU TIẾNG VIỆT khi có (`sai_moc`, `trung`). Vì vậy phải khai [`JsonElement`], không được
 * khai `String?` (sẽ nổ `JsonDecodingException` ngay khi gặp `false`).
 */
@Serializable
data class ScanResultDto(
    val kien: String? = null,
    val maXuat: String? = null,
    val job: String? = null,
    val chuyen: String? = null,
    val trangThai: String? = null,
    val ketQua: String? = null,
    val canhBao: JsonElement? = null,
    val trung: Boolean = false,
    val tienDo: ScanTienDoDto? = null,
)

/** `canhBao` → câu tiếng Việt; `false`/vắng ⇒ `null`; `true` (BE trả boolean) ⇒ câu mặc định. */
private fun JsonElement?.canhBaoText(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    primitive.contentOrNull?.takeIf { it.isNotBlank() && it != "false" }?.let { return it }
    return if (primitive.booleanOrNull == true) "Máy chủ báo cảnh báo cho lần quét này." else null
}

/**
 * Parse `data` của `POST /lab/api/v1/scan` → [ScanResult].
 *
 * ⚠️ ĐO THẬT 2026-11-03 (gọi API với `moc=xuat` trên kiện chưa in tem xuất): server trả
 * **HTTP 409 + `ok:false` + `error.code=SAI_MOC`** nhưng `data` VẪN đủ trường và có `ketQua`
 * (nhánh `khong_tim_thay` tương tự, 404). Vì vậy KHÔNG dùng [LabPayload.data] — hàm đó ném lỗi khi
 * `ok == false` và app sẽ hiện "máy chủ báo lỗi" thay vì chip đỏ + câu cảnh báo nghiệp vụ.
 *
 * Chỉ ném [ApiException] khi payload hỏng hẳn, hoặc lỗi thuộc về phiên/quyền/quan hệ tham số.
 *
 * @param maQuet mã người dùng vừa quét (để UI hiện lại cả khi server trả mã kiện khác).
 */
fun parseScanResult(body: String, status: Int = 200, maQuet: String = ""): ScanResult {
    if (status == 401) throw ApiException.SessionExpired()
    val text = body.trim()
    if (text.isEmpty()) {
        throw ApiException.Server("Máy chủ Odoo trả nội dung rỗng (mã HTTP $status). Vui lòng thử lại.")
    }
    if (text.startsWith("<")) throw ApiException.SessionExpired()

    val root = try {
        AppJson.parseToJsonElement(text)
    } catch (e: Exception) {
        throw ApiException.Server(
            "Máy chủ Odoo trả dữ liệu không phải JSON (mã HTTP $status). Kiểm tra địa chỉ máy chủ.",
            e,
        )
    } as? JsonObject ?: throw ApiException.Server("$MSG_DU_LIEU_LA (mã HTTP $status)")

    val envelope = if (root["ok"] == null) (root["result"] as? JsonObject) ?: root else root
    val okValue = (envelope["ok"] as? JsonPrimitive)?.booleanOrNull
    val data = envelope["data"] ?: JsonNull
    val error = envelope["error"] as? JsonObject
    val messageTuError = error.stringOrNull("message")

    if (data !is JsonObject) {
        // Không có `data` ⇒ lỗi thật (thiếu mã/mốc, thiếu clientUuid, hết phiên, sai quyền).
        if (okValue == false) {
            val code = error.stringOrNull("code")
            if (code == "TOKEN_REQUIRED" || code == "TOKEN_INVALID" || code == "TOKEN_EXPIRED") {
                throw ApiException.SessionExpired()
            }
            if (status == 403) throw ApiException.Forbidden()
            throw ApiException.Server(messageTuError ?: "Máy chủ Odoo từ chối lần quét này.")
        }
        throw ApiException.Server("Phản hồi quét tem không đúng định dạng.")
    }

    val dto = AppJson.decodeFromString(ScanResultDto.serializer(), data.toString())
    val outcome = ScanOutcome.fromCode(dto.ketQua ?: if (dto.trung) "trung" else "ok")
    return ScanResult(
        maQuet = maQuet,
        kien = dto.kien.orNullIfBlank() ?: maQuet,
        maXuat = dto.maXuat.orNullIfBlank(),
        job = dto.job.orNullIfBlank(),
        chuyen = dto.chuyen.orNullIfBlank(),
        trangThai = dto.trangThai.orNullIfBlank(),
        outcome = outcome,
        // `data.canhBao` là nguồn chính; nhánh `ok:false` chỉ có câu ở `error.message`.
        canhBao = dto.canhBao.canhBaoText() ?: messageTuError,
        daQuet = dto.tienDo?.daQuet ?: 0,
        tong = dto.tienDo?.tong ?: 0,
    )
}

private fun JsonObject?.stringOrNull(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull

// =====================================================================================
// 9. ADMIN — CHUYỂN TÀI KHOẢN: GET /lab/api/v1/admin/accounts · POST /lab/api/v1/admin/switch
// =====================================================================================
// Cả hai route CHỈ tài khoản có mã `admin` gọi được (BE-01 trả 403 cho tài khoản khác — app hiện
// đúng câu tiếng Việt qua `ApiException.Forbidden`). App KHÔNG tự quyết định ai là admin: chỉ mở
// màn này khi `data.roles` của phiên hiện tại có `admin`.

/**
 * Một phần tử `items` của `admin/accounts`. Mọi trường đều nullable + default: BE trả thiếu
 * `name`/`roles` thì app vẫn parse được (xem [toDemoAccount] cho luật fallback).
 */
@Serializable
data class AdminAccountDto(
    val login: String? = null,
    val name: String? = null,
    val roles: List<String> = emptyList(),
)

/** `data` của `GET /lab/api/v1/admin/accounts` — chỉ cần `items`. */
@Serializable
data class AdminAccountsPayloadDto(
    val items: List<AdminAccountDto> = emptyList(),
)

/**
 * Dựng body `switch` — CHỈ một khoá `login` (đúng hợp đồng với BE-01).
 * Hàm thuần để test được tên khoá mà không cần mạng.
 */
fun switchAccountRequestBody(login: String): String = buildJsonObject {
    put("login", login.trim())
}.toString()

/**
 * Parse `data` của `admin/accounts` → danh sách [DemoAccount].
 *
 * `data: null` (envelope `ok:true` mà không có tài khoản) ⇒ danh sách rỗng, KHÔNG phải lỗi: màn
 * hình hiện empty state "Chưa có tài khoản demo".
 */
fun parseAdminAccountsPayload(body: String, status: Int = 200): List<DemoAccount> {
    val data = LabPayload.data(status, body)
    if (data is JsonNull) return emptyList()
    if (data !is JsonObject) {
        throw ApiException.Server("Danh sách tài khoản demo không đúng định dạng (thiếu items).")
    }
    val dto = AppJson.decodeFromString(AdminAccountsPayloadDto.serializer(), data.toString())
    // Dòng không có `login` thì BỎ: không có `login` thì không gửi được `switch`.
    return dto.items.mapNotNull { it.toDemoAccount() }
}

/**
 * Map DTO → [DemoAccount]. `name` vắng ⇒ dùng `login` (dòng chọn vẫn đọc được); `roles` vắng ⇒
 * danh sách rỗng ⇒ nếu chuyển sang tài khoản đó, app mở màn "vai trò khác" (đúng hợp đồng,
 * KHÔNG đoán bừa là tài xế).
 */
fun AdminAccountDto.toDemoAccount(): DemoAccount? {
    val tenDangNhap = login.orNullIfBlank() ?: return null
    return DemoAccount(
        login = tenDangNhap,
        name = name.orNullIfBlank() ?: tenDangNhap,
        roles = roles.mapNotNull { it.orNullIfBlank() },
    )
}

// =====================================================================================
// 8c. XUẤT KHO THEO CHUYẾN — GET /lab/api/v1/kho/cho-xuat · POST …/chuyen/<id>/xuat
// =====================================================================================

/** Một kiện trong `data.kien[]` của `cho-xuat`. */
@Serializable
data class KhoXuatKienDto(
    val ma: String? = null,
    val maXuat: String? = null,
    val trangThai: String? = null,
    val trangThaiLabel: String? = null,
)

/** Một chuyến chờ xuất (dùng cho CẢ danh sách và chi tiết — server trả cùng shape). */
@Serializable
data class KhoXuatChuyenDto(
    val chuyenId: Int = 0,
    val chuyen: String? = null,
    val state: String? = null,
    val stateLabel: String? = null,
    val diemDen: String? = null,
    val soKien: Int = 0,
    val daQuet: Int = 0,
    val kien: List<KhoXuatKienDto> = emptyList(),
)

/** `data` của `GET /lab/api/v1/kho/cho-xuat` (danh sách). */
@Serializable
data class KhoXuatDanhSachDto(
    val items: List<KhoXuatChuyenDto> = emptyList(),
)

/** `data` khi POST `…/xuat` thành công. */
@Serializable
data class KhoXuatKetQuaDto(
    val phieu: String? = null,
    val soKienXuat: Int = 0,
    val soKienThieu: Int = 0,
    val state: String? = null,
    val trung: Boolean = false,
)

/** `data` khi POST bị chặn 409 `CON_KIEN_CHUA_QUET` (envelope `ok:false` nhưng VẪN có `data`). */
@Serializable
data class KhoXuatChanDto(
    val conLai: Int = 0,
    val danhSach: List<String> = emptyList(),
)

private fun KhoXuatKienDto.toKien(): KhoXuatKien = KhoXuatKien(
    ma = ma.orNullIfBlank() ?: "(không có mã)",
    maXuat = maXuat.orNullIfBlank(),
    trangThai = trangThai.orNullIfBlank(),
    trangThaiLabel = trangThaiLabel.orNullIfBlank(),
)

private fun KhoXuatChuyenDto.toChuyen(): KhoXuatChuyen = KhoXuatChuyen(
    chuyenId = chuyenId,
    chuyen = chuyen.orNullIfBlank() ?: "#$chuyenId",
    state = state.orNullIfBlank(),
    stateLabel = stateLabel.orNullIfBlank(),
    diemDen = diemDen.orNullIfBlank(),
    soKien = soKien,
    daQuet = daQuet,
    kien = kien.map { it.toKien() },
)

/** Parse `GET /lab/api/v1/kho/cho-xuat` → danh sách chuyến chờ xuất. */
fun parseKhoChoXuatPayload(body: String, status: Int = 200): List<KhoXuatChuyen> {
    val data = LabPayload.data(status, body)
    if (data is JsonNull) return emptyList()
    if (data !is JsonObject) {
        throw ApiException.Server("Danh sách chuyến chờ xuất không đúng định dạng (thiếu items).")
    }
    val dto = AppJson.decodeFromString(KhoXuatDanhSachDto.serializer(), data.toString())
    return dto.items.map { it.toChuyen() }
}

/** Parse `GET /lab/api/v1/kho/cho-xuat?chuyenId=<id>` → chi tiết một chuyến. */
fun parseKhoChoXuatChiTiet(body: String, status: Int = 200): KhoXuatChuyen {
    val data = LabPayload.data(status, body)
    if (data !is JsonObject) {
        throw ApiException.Server("Chi tiết chuyến chờ xuất không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(KhoXuatChuyenDto.serializer(), data.toString())
    return dto.toChuyen()
}

/**
 * Parse `POST /lab/api/v1/kho/chuyen/<id>/xuat`.
 *
 * ⚠️ Endpoint này CỐ Ý trả `ok: false` cho nhánh "còn kiện chưa quét" (409) **kèm `data`**, nên
 * KHÔNG dùng được [LabPayload.data] (hàm đó ném lỗi khi `ok == false`). Vì vậy phải đọc envelope
 * thủ công ở đây — và trả [XuatKetQua.ConKienChuaQuet] để UI mời người dùng nhập lý do (M9).
 */
fun parseKhoXuatKetQua(body: String, status: Int = 200): XuatKetQua {
    if (status == 401) throw ApiException.SessionExpired()
    val text = body.trim()
    if (text.isEmpty()) {
        throw ApiException.Server("Máy chủ Odoo trả nội dung rỗng (mã HTTP $status). Vui lòng thử lại.")
    }
    if (text.startsWith("<")) throw ApiException.SessionExpired()

    val root = try {
        AppJson.parseToJsonElement(text)
    } catch (e: Exception) {
        throw ApiException.Server(
            "Máy chủ Odoo trả dữ liệu không phải JSON (mã HTTP $status). Kiểm tra địa chỉ máy chủ.",
            e,
        )
    } as? JsonObject ?: throw ApiException.Server("$MSG_DU_LIEU_LA (mã HTTP $status)")

    val envelope = if (root["ok"] == null) (root["result"] as? JsonObject) ?: root else root
    val okValue = (envelope["ok"] as? JsonPrimitive)?.booleanOrNull
    val data = envelope["data"]

    if (okValue == false) {
        val error = envelope["error"] as? JsonObject
        val code = error.stringOrNull("code")
        val message = error.stringOrNull("message")
            ?: "Máy chủ Odoo từ chối xác nhận xuất kho."
        // Nhánh NGHIỆP VỤ có đường quay về: còn kiện chưa quét ⇒ mời nhập lý do xuất thiếu.
        if (code == "CON_KIEN_CHUA_QUET") {
            val chan = data as? JsonObject ?: return XuatKetQua.ConKienChuaQuet(
                conLai = 0,
                danhSach = emptyList(),
                message = message,
            )
            val dto = AppJson.decodeFromString(KhoXuatChanDto.serializer(), chan.toString())
            return XuatKetQua.ConKienChuaQuet(
                conLai = dto.conLai,
                danhSach = dto.danhSach,
                message = message,
            )
        }
        if (status == 403) throw ApiException.Forbidden()
        throw ApiException.Server(message)
    }

    if (data !is JsonObject) {
        throw ApiException.Server("Phản hồi xác nhận xuất kho không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(KhoXuatKetQuaDto.serializer(), data.toString())
    return XuatKetQua.DaXuat(
        phieu = dto.phieu.orNullIfBlank() ?: "(máy chủ chưa trả số phiếu)",
        soKienXuat = dto.soKienXuat,
        soKienThieu = dto.soKienThieu,
        state = dto.state.orNullIfBlank(),
        trung = dto.trung,
    )
}

/** Dựng body `POST …/xuat`: `{clientUuid, lyDoThieu?}`. Lý do rỗng ⇒ KHÔNG gửi khoá. */
fun xuatRequestBody(clientUuid: String, lyDoThieu: String? = null): String = buildJsonObject {
    put("clientUuid", clientUuid)
    lyDoThieu?.takeIf { it.isNotBlank() }?.let { put("lyDoThieu", it) }
}.toString()
// =====================================================================================
// 8d. KIỆN CỦA MỘT VIỆC — GET /lab/api/v1/kho/jobs/<id>/kien (BE ship 2026-11-03)
// =====================================================================================

/** Một kiện trong `data.kien[]`. */
@Serializable
data class KhoKienDto(
    val ma: String? = null,
    val maXuat: String? = null,
    val trangThai: String? = null,
    val trangThaiLabel: String? = null,
    val daQuet: Boolean = false,
    val quetLuc: String? = null,
)

/** `data` của `GET …/kho/jobs/<id>/kien`. */
@Serializable
data class KienCuaViecDto(
    val job: String? = null,
    val soKien: Int = 0,
    val daQuet: Int = 0,
    val kien: List<KhoKienDto> = emptyList(),
)

/**
 * Parse `GET /lab/api/v1/kho/jobs/<id>/kien` → [KienCuaViec].
 * Việc chưa nhận ⇒ `{soKien:0, daQuet:0, kien:[]}` ⇒ trả object rỗng, KHÔNG ném lỗi.
 */
fun parseKienCuaViec(body: String, status: Int = 200): KienCuaViec {
    val data = LabPayload.data(status, body)
    if (data is JsonNull) return KienCuaViec(job = null, soKien = 0, daQuet = 0, kien = emptyList())
    if (data !is JsonObject) {
        throw ApiException.Server("Danh sách kiện của việc không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(KienCuaViecDto.serializer(), data.toString())
    return KienCuaViec(
        job = dto.job.orNullIfBlank(),
        soKien = dto.soKien,
        daQuet = dto.daQuet,
        kien = dto.kien.map { k ->
            KhoKien(
                ma = k.ma.orNullIfBlank() ?: "(không có mã)",
                maXuat = k.maXuat.orNullIfBlank(),
                trangThai = k.trangThai.orNullIfBlank(),
                trangThaiLabel = k.trangThaiLabel.orNullIfBlank(),
                daQuet = k.daQuet,
                quetLuc = k.quetLuc.orNullIfBlank(),
            )
        },
    )
}