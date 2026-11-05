package vn.lab.logistics.core.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.core.net.LoginSession
import vn.lab.logistics.nowEpochMillis
import vn.lab.logistics.platformDefaults

/**
 * =====================================================================================
 * AppSettings — NGUỒN DUY NHẤT của "kết nối tới Odoo nào", "đang dùng dữ liệu mẫu hay thật"
 * và "phiên đăng nhập còn hạn không".
 *
 * BẢO MẬT (yêu cầu cứng của FE-01 + docs/02 §6.3):
 *  - **KHÔNG** lưu mật khẩu / API key ra tệp, không log, không đưa vào state lưu bền.
 *    Chúng chỉ sống trong `LoginViewModel` (RAM) và biến mất khi đăng xuất/đóng app.
 *  - ĐƯỢC lưu (11 khoá, xem [persist]): `baseUrl`, `db`, `login`, `token`, `uid`, `driverName`,
 *    `jobsToday`, **`realDataSource`** (lựa chọn Mẫu/Odoo thật), **`tokenSavedAt`** (mốc nhận
 *    token để biết còn hạn hay không), **`roles`** (vai trò `driver`/`cs`/`kho`… — để mở app
 *    lần sau vào đúng màn) và **`canSwitch`** (quyền chuyển vai trò của phiên — quyết định nút
 *    dropdown ở thanh trên cùng còn hiện hay không). Không khoá nào là bí mật.
 *  - `session_id` của Odoo (cookie) và mật khẩu **không** được ghi ra đĩa: cookie nằm trong
 *    cookie jar (`HttpCookies`) của RAM; mật khẩu không được đưa vào đây từ đầu.
 *
 * GHI NHỚ LỰA CHỌN + PHIÊN (thay đổi có chủ đích, Wave C):
 *  - **`useRealApi` nay LƯU BỀN**, mặc định theo nền tảng (`platformDefaults().useRealApiByDefault`:
 *    desktop = Mẫu, Android = Odoo thật). Trước đây cố tình không lưu để demo luôn an toàn;
 *    nay app tài xế trên điện thoại là app thật nên "mở ra là thấy việc" mới là hành vi đúng.
 *    Luật 8 vẫn được giữ: khi gọi Odoo lỗi, màn danh sách có sẵn nút **"Về dữ liệu mẫu"**.
 *  - **`baseUrl` được ghi nhớ NGAY khi người dùng sửa** ([rememberBaseUrl]), không đợi đăng nhập
 *    thành công — nếu không, người dùng sửa ô "Máy chủ Odoo" mà thông báo lỗi vẫn nêu địa chỉ
 *    cũ và tưởng app bỏ qua ô nhập.
 *  - **Có token còn hạn ⇒ mở app vào thẳng màn của VAI TRÒ** ([hasValidSession] + [workspace]):
 *    tài xế mở ra là thấy việc, CS mở ra là thấy việc cần gán, kho mở ra là thấy hàng chờ nhận;
 *    không bắt đăng nhập lại. Hết hạn/401 thì luồng cũ tự đưa về Đăng nhập.
 * =====================================================================================
 */

/** Nơi lưu bền cấu hình (desktopMain = tệp; androidMain = SharedPreferences; test = RAM). */
interface SettingsStorage {
    fun read(): Map<String, String>
    fun write(newValues: Map<String, String>)
}

/** Bản trong RAM: dùng cho preview/test/self-check (không ghi đĩa). */
class InMemorySettingsStorage(initial: Map<String, String> = emptyMap()) : SettingsStorage {
    private var values: Map<String, String> = initial.toMap()

    override fun read(): Map<String, String> = values

    override fun write(newValues: Map<String, String>) {
        values = newValues.toMap()
    }
}

class AppSettings(private val storage: SettingsStorage? = null) {

    private val saved: Map<String, String> = storage?.read().orEmpty()

    /**
     * Địa chỉ máy chủ Odoo của lần chạy này. Người dùng nhập, UI hiển thị được.
     * Mặc định KHÁC THEO NỀN TẢNG (Wave C): desktop `http://localhost:8069`,
     * Android `http://10.0.2.2:8069` — xem [platformDefaults].
     */
    var baseUrl: String by mutableStateOf(
        saved[KEY_BASE_URL]?.takeIf { it.isNotBlank() } ?: platformDefaults().baseUrl,
    )
        private set

    /** Tên cơ sở dữ liệu, mặc định `odoo17_lab` của lab. */
    var db: String by mutableStateOf(saved[KEY_DB]?.takeIf { it.isNotBlank() } ?: DEFAULT_DB)
        private set

    /** Tên đăng nhập của lần trước (KHÔNG phải bí mật — chỉ để đỡ phải gõ lại). */
    var login: String by mutableStateOf(saved[KEY_LOGIN].orEmpty())
        private set

    /** Token `lab_api` (8 giờ). Lưu được; mất cookie phiên thì vẫn phải đăng nhập lại. */
    var token: String by mutableStateOf(saved[KEY_TOKEN].orEmpty())
        private set

    /** `uid` Odoo của tài xế (từ `/web/session/authenticate`). */
    var uid: Int? by mutableStateOf(saved[KEY_UID]?.toIntOrNull())
        private set

    /** Tên tài xế do server trả (`driver_name`). */
    var driverName: String by mutableStateOf(saved[KEY_DRIVER_NAME].orEmpty())
        private set

    /** Số việc server báo lúc đăng nhập (`jobsToday`) — dùng cho câu chào trên UI. */
    var jobsToday: Int? by mutableStateOf(saved[KEY_JOBS_TODAY]?.toIntOrNull())
        private set

    /**
     * Vai trò của tài khoản do server trả lúc đăng nhập (`data.roles` — mã `admin`/
     * `driver`/`driver_leader`/`cs`/`kho`/`ketoan`, xem `core/model/Workspace.kt`;
     * `manager`/`sep` mở dashboard).
     *
     * LƯU BỀN (khoá `roles`, dạng `driver,cs`) **không phải bí mật**: nhờ vậy mở lại app khi
     * token còn hạn thì vào ĐÚNG màn của vai trò đó, không bắt đăng nhập lại.
     * Danh sách rỗng ⇒ [workspace] = `Unsupported` ⇒ màn thông báo "Vai trò này làm việc trên
     * Odoo web" (KHÔNG đoán bừa là tài xế).
     */
    var roles: List<String> by mutableStateOf(
        saved[KEY_ROLES]?.split(SEP_ROLES)?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList(),
    )
        private set

    /**
     * Màn hình làm việc suy ra từ [roles]: ưu tiên driver → driver_leader → cs → kho → dashboard
     * (hàm thuần, có test).
     */
    val workspace: Workspace get() = Workspace.fromRoles(roles)

    /**
     * Phiên hiện tại có được **CHUYỂN VAI TRÒ** không (nút dropdown ở thanh trên cùng).
     *
     * LƯU BỀN (khoá `canSwitch`, `"true"`/`"false"`) vì server là nguồn quyết định và app phải
     * giữ đúng câu trả lời đó qua lần mở sau — KHÔNG suy ra từ [roles]: một phiên do admin chuyển
     * sang tài xế có `roles = ["driver"]` nhưng **vẫn** được chuyển vai trò (server vẫn trả 200
     * cho 2 endpoint admin), nên nếu suy từ `roles` thì nút sẽ biến mất ngay sau lần chuyển đầu —
     * đúng lỗi mà yêu cầu của sếp muốn tránh.
     *
     * `false` ⇒ không hiện nút. Đó là trường hợp DUY NHẤT nút mất: đăng nhập thẳng bằng tài khoản
     * không phải admin (`/auth/login` trả `canSwitch: false`).
     */
    var canSwitch: Boolean by mutableStateOf(saved[KEY_CAN_SWITCH]?.toBooleanStrictOrNull() ?: false)
        private set

    /**
     * Điều kiện DUY NHẤT để hiện nút chuyển vai trò: server cho phép + đang có phiên còn hạn.
     *
     * Cố ý KHÔNG dựa vào [roles]: xem KDoc của [canSwitch]. `hasValidSession` chặn trường hợp đã
     * đăng xuất (`clearSession` xoá token) mà cờ còn sót lại trong bộ nhớ.
     */
    val hienNutChuyenVaiTro: Boolean get() = canSwitch && hasValidSession

    /**
     * Nguồn dữ liệu: `true` = Odoo thật, `false` = dữ liệu Mẫu.
     *
     * LƯU BỀN (khoá `realDataSource`). Mặc định đọc từ [platformDefaults] khi chưa có lựa chọn
     * nào được lưu: desktop = Mẫu, Android = Odoo thật.
     */
    var useRealApi: Boolean by mutableStateOf(
        saved[KEY_REAL_DATA_SOURCE]?.toBooleanStrictOrNull()
            ?: platformDefaults().useRealApiByDefault,
    )
        private set

    /**
     * Mốc (epoch ms) lúc NHẬN token thành công — cơ sở duy nhất để biết token còn hạn.
     *
     * Không dùng chuỗi `expiresAt` của server: nó có dạng `2026-10-28 14:27:56` **không kèm
     * múi giờ**, so sánh với giờ máy sẽ sai khi lệch múi giờ. Token `lab_api` có hạn cố định
     * 8 giờ (docs/02 §6.3) nên dùng mốc nhận token + [TOKEN_TTL_MILLIS] là đủ và đúng.
     */
    private var tokenSavedAtMillis: Long? = saved[KEY_TOKEN_SAVED_AT]?.toLongOrNull()

    val hasToken: Boolean get() = token.isNotBlank()

    /**
     * Token còn hạn ⇒ mở app vào THẲNG màn "Việc hôm nay" (không bắt đăng nhập lại).
     *
     * Ba nhánh, không có nhánh thứ tư:
     *  - chưa có token → `false` (đi Đăng nhập);
     *  - có token + có mốc nhận → còn hạn nếu chưa quá [TOKEN_TTL_MILLIS];
     *  - có token nhưng KHÔNG có mốc (cấu hình lưu từ bản cũ) → coi như còn hạn: nếu token
     *    thật sự hết thì request đầu tiên trả 401 → `ApiException.SessionExpired` → app xoá token
     *    và đưa về màn Đăng nhập (`onExitToLogin`), đúng đường lui.
     */
    val hasValidSession: Boolean
        get() {
            if (token.isBlank()) return false
            val savedAt = tokenSavedAtMillis ?: return true
            val elapsed = nowEpochMillis() - savedAt
            return elapsed in 0L until TOKEN_TTL_MILLIS
        }

    // ------------------------------------------------------------------ setters (đều ghi bền)

    /** Đặt địa chỉ máy chủ (dùng ở luồng đăng nhập) — rỗng thì quay về mặc định nền tảng. */
    fun updateBaseUrl(value: String) {
        baseUrl = normalizeBaseUrl(value).ifBlank { platformDefaults().baseUrl }
        persist()
    }

    /**
     * Ghi nhớ NGAY địa chỉ người dùng đang sửa ở ô "Máy chủ Odoo" (chưa cần đăng nhập thành công).
     *
     * Bỏ qua khi ô đang rỗng (người dùng xoá để gõ lại) để không ghi đè bằng giá trị mặc định.
     */
    fun rememberBaseUrl(value: String) {
        val normalized = normalizeBaseUrl(value)
        if (normalized.isBlank() || normalized == baseUrl) return
        baseUrl = normalized
        persist()
    }

    fun updateDb(value: String) {
        db = value.trim().ifBlank { DEFAULT_DB }
        persist()
    }

    fun updateLogin(value: String) {
        login = value.trim()
        persist()
    }

    /** Đổi nguồn dữ liệu — LƯU BỀN để lần mở sau giữ đúng lựa chọn của người dùng. */
    fun updateUseRealApi(value: Boolean) {
        useRealApi = value
        persist()
    }

    /**
     * Lưu kết quả đăng nhập THẬT (gọi sau bước 2 thành công) **và** kết quả CHUYỂN TÀI KHOẢN
     * (`POST /admin/switch` — phản hồi đúng shape login nên đi cùng một đường ghi).
     *
     * Ghi ĐÈ toàn bộ phiên: `token`, mốc nhận token (`tokenSavedAt = now`), `uid`, `driverName`,
     * `jobsToday`, `roles` và `login`. Nhờ vậy "đang là ai" chỉ có MỘT nguồn sự thật
     * ([roles]/[login]/[driverName]) — màn tài xế, màn phân công và nhãn ở chân sidebar đọc cùng
     * chỗ, không thể lệch nhau sau khi chuyển tài khoản.
     *
     * Không lưu mật khẩu/API key (chúng không có mặt trong `LoginSession`).
     */
    fun saveSession(session: LoginSession) {
        token = session.token
        tokenSavedAtMillis = nowEpochMillis()
        session.uid?.let { uid = it }
        if (session.driverName.isNotBlank()) driverName = session.driverName
        // `login` là khoá KHÔNG bí mật: giữ đúng tài khoản đang dùng để hiện "Tài khoản: …".
        // Vắng (đăng nhập thường không trả `login`) ⇒ giữ giá trị đang lưu.
        if (session.login.isNotBlank()) login = session.login
        session.jobsToday?.let { jobsToday = it }
        // Ghi ĐÈ danh sách vai trò bằng giá trị server vừa trả (rỗng ⇒ màn thông báo vai trò).
        roles = session.roles
        // Và ghi ĐÈ cả quyền chuyển vai trò (thiếu khoá ở payload ⇒ `LoginSession` đã là `false`):
        // đăng nhập thẳng bằng tài khoản thường phải XOÁ quyền còn sót từ phiên admin trước đó.
        canSwitch = session.canSwitch
        persist()
    }

    /**
     * Đăng xuất / phiên hết hạn: xoá token + mốc hạn để mọi request sau buộc đăng nhập lại.
     * Xoá luôn [canSwitch] — ở màn Đăng nhập thì không còn nút chuyển vai trò nào để hiện.
     */
    fun clearSession() {
        token = ""
        tokenSavedAtMillis = null
        uid = null
        driverName = ""
        jobsToday = null
        roles = emptyList()
        canSwitch = false
        persist()
    }

    /**
     * Ghi ra nơi lưu bền — **CHỈ 11 khoá dưới đây**. Mật khẩu/API key không nằm trong danh sách
     * này nên không có đường nào ghi bí mật ra đĩa/tệp prefs (self-check khối 8 kiểm lại lần nữa).
     */
    private fun persist() {
        val target = storage ?: return
        target.write(
            linkedMapOf(
                KEY_BASE_URL to baseUrl,
                KEY_DB to db,
                KEY_LOGIN to login,
                KEY_TOKEN to token,
                KEY_UID to (uid?.toString() ?: ""),
                KEY_DRIVER_NAME to driverName,
                KEY_JOBS_TODAY to (jobsToday?.toString() ?: ""),
                KEY_REAL_DATA_SOURCE to useRealApi.toString(),
                KEY_TOKEN_SAVED_AT to (tokenSavedAtMillis?.toString() ?: ""),
                KEY_ROLES to roles.joinToString(SEP_ROLES),
                KEY_CAN_SWITCH to canSwitch.toString(),
            ),
        )
    }

    companion object {
        /**
         * Địa chỉ mặc định của lab TRÊN DESKTOP (Odoo chạy cùng máy).
         *
         * ⚠️ Đây là chỗ DUY NHẤT trong mã nguồn chứa literal này — self-check khối 8 grep toàn
         * bộ `src/` để chứng minh không file nào khác hardcode.
         *
         * Wave C: mặc định theo nền tảng nằm ở `platformDefaults()` (`Platform.kt`).
         *  - desktop: [DEFAULT_BASE_URL] (hằng này — hằng vẫn giữ để self-check + tài liệu).
         *  - Android: `http://10.0.2.2:8069` (loopback máy host từ máy ảo), khai trong
         *    `androidMain/.../Platform.android.kt`.
         * Cả hai đều chỉ là GIÁ TRỊ KHỞI TẠO: người dùng sửa được ở màn Đăng nhập và giá trị
         * vừa sửa được lưu bền ngay ([rememberBaseUrl]).
         */
        const val DEFAULT_BASE_URL: String = "http://localhost:8069"

        /** Database của lab (BE-01 đã cài đủ 7 module + 46 test xanh). */
        const val DEFAULT_DB: String = "odoo17_lab"

        /**
         * Hạn của token `lab_api` = **8 giờ** (docs/02 §6.3, server phát `expiresAt` tương ứng).
         * Dùng để quyết định "mở app có phải đăng nhập lại không" — xem [hasValidSession].
         */
        private const val TOKEN_TTL_MILLIS: Long = 8L * 60L * 60L * 1000L

        /**
         * Chuẩn hoá địa chỉ máy chủ người dùng gõ.
         *
         * Trên điện thoại rất dễ gõ `127.0.0.1:8069` (thiếu scheme); nếu giữ nguyên thì Ktor
         * không dựng được URL và người dùng chỉ thấy "không kết nối được" — lỗi trông như app
         * bỏ qua ô nhập. Ở đây mặc định **http** (đúng với Odoo dev), giữ nguyên `https://`.
         */
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return ""
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            return if (hasScheme) trimmed else "http://$trimmed"
        }

        private const val KEY_BASE_URL = "baseUrl"
        private const val KEY_DB = "db"
        private const val KEY_LOGIN = "login"
        private const val KEY_TOKEN = "token"
        private const val KEY_UID = "uid"
        private const val KEY_DRIVER_NAME = "driverName"
        private const val KEY_JOBS_TODAY = "jobsToday"

        /** Lựa chọn nguồn dữ liệu (Mẫu / Odoo thật) — khoá Wave C. */
        private const val KEY_REAL_DATA_SOURCE = "realDataSource"

        /** Quyền chuyển vai trò của phiên (`true`/`false`) — khoá mới, KHÔNG phải bí mật. */
        private const val KEY_CAN_SWITCH = "canSwitch"

        /** Mốc nhận token (epoch ms) — khoá Wave C, dùng cho [hasValidSession]. */
        private const val KEY_TOKEN_SAVED_AT = "tokenSavedAt"

        /** Vai trò của tài khoản (`driver,cs`) — khoá mới, KHÔNG phải bí mật. */
        private const val KEY_ROLES = "roles"

        /** Dấu phân cách danh sách vai trò trong một khoá văn bản (không dùng dấu phẩy trong mã vai trò). */
        private const val SEP_ROLES = ","
    }
}
