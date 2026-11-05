## Dùng khi nào

- Dựng `core/net` (P3): đăng nhập Odoo, gọi `call_kw`, gọi endpoint custom `/lab/api/v1/*`.
- Cần sửa cách app xử lý lỗi/timeout — **mọi** lỗi hiển thị cho người dùng đi qua skill này.
- Review PR có Ktor để chắc không phạm "Bearer của Odoo 19" hay đọc `session_id` từ JSON.

## 1. Ba sự thật Odoo 17 (không được quên)

1. `POST /web/session/authenticate` trả `uid` + `user_context`; **`session_id` CHỈ có trong header `Set-Cookie`**, không đọc từ body (docs/02 §6.3). `uid=false/0` → sai db/login/api key.
2. API key (`res.users.apikeys`) dùng **thay password**. App **không** lưu password người dùng.
3. Odoo 17 **không** có `Authorization: Bearer` (đó là 19.0/`/json/2`). Bearer của `/lab/api/v1` do controller **của mình** (BE-01) kiểm tra.

## 2. `HttpClient` + cookie + timeout

```kotlin
fun HttpClientConfig<*>.configureOdoo() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(HttpCookies) { storage = AcceptAllCookiesStorage() }   // session_id sống trong RAM
    install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 15_000 }
    expectSuccess = false                                          // tự map lỗi, không để Ktor ném trước
}
// desktopMain: HttpClient(CIO) { configureOdoo() } · test: HttpClient(MockEngine) { configureOdoo() }
```

Session sống qua lần mở app sau: parse `Set-Cookie` → lưu `session_id` vào `Settings`, lần sau gắn `Cookie: session_id=<sid>`. Nếu tự viết `CookiesStorage` persistent: **kiểm tra lại API của Ktor 3.6.0** (`addCookie/get`) trước khi implement.

## 3. Đăng nhập + `uid=false`

```kotlin
class OdooClient(private val baseUrl: String, private val http: HttpClient, private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    var session: Session? = null; private set
    suspend fun authenticate(db: String, login: String, apiKey: String): Session {
        val resp = http.post("$baseUrl/web/session/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(JsonRpcRequest(id = nextId(), method = "call", params = buildJsonObject {
                put("db", db); put("login", login); put("password", apiKey)      // apiKey thay password
            }))
        }
        val result = json.parseToJsonElement(resp.bodyAsText()).jsonObject["result"]?.jsonObject
        val uid = result?.get("uid")?.jsonPrimitive?.intOrNull
        if (uid == null || uid == 0) throw OdooException.LoginFailed("Sai máy chủ, cơ sở dữ liệu, tài khoản hoặc API key.")
        val sid = resp.headers.getAll(HttpHeaders.SetCookie)
            ?.firstNotNullOfOrNull { Regex("session_id=([^;]+)").find(it)?.groupValues?.get(1) }
            ?: throw OdooException.SessionExpired
        settings.putString(KEY_SESSION, sid)
        return Session(token = sid, uid = uid, name = result["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
            .also { session = it }
    }
}
```

`baseUrl`/`db` chỉ đến từ màn Login + `Settings` (mặc định env `LAB_ODOO_BASE_URL`) — không viết cứng.

## 4. `call_kw` — JSON-RPC envelope

```kotlin
@Serializable data class JsonRpcRequest(val jsonrpc: String = "2.0", val method: String, val id: Int, val params: JsonObject)
suspend fun <T> callKw(model: String, method: String, args: JsonArray = JsonArray(emptyList()),
                       kwargs: JsonObject = buildJsonObject { }, decode: (JsonElement) -> T): T {
    val params = buildJsonObject {
        put("model", model); put("method", method); put("args", args); put("kwargs", kwargs)
    }
    val resp = http.post("$baseUrl/web/dataset/call_kw") {
        contentType(ContentType.Application.Json)
        session?.let { header(HttpHeaders.Cookie, "session_id=${it.token}") }
        setBody(JsonRpcRequest(id = nextId(), method = "call", params = params))
    }
    val envelope = json.parseToJsonElement(resp.bodyAsText()).jsonObject
    envelope["error"]?.let { throw mapOdooError(it.jsonObject) }               // lỗi nghiệp vụ Odoo
    val result = envelope["result"]
    if (result == null || result is JsonNull) throw OdooException.SessionExpired
    return decode(result)
}
suspend fun todayJobs(uid: Int): List<DriverJob> = callKw(
    model = "lab.yeu.cau.lay.hang", method = "web_search_read",
    args = jsonArrayOf(jsonArrayOf(jsonArrayOf("driver_id.user_id", "=", uid))),
    kwargs = buildJsonObject {
        put("fields", jsonArrayOf("name", "partner_id", "street", "state", "so_kien")); put("limit", 50)
    },
) { el -> el.jsonArray.map { it.jsonObject.toDriverJob() } }
```

## 5. Map lỗi → tiếng Việt (không lộ stacktrace ra UI)

Odoo trả `{"error":{"data":{"name":"odoo.exceptions.AccessError","message":"…","debug":"…"}}}`:

```kotlin
private fun mapOdooError(error: JsonObject): OdooException {
    val data = error["data"]?.jsonObject
    val name = data?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
    val msg  = data?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
    return when {
        name.endsWith("AccessError")     -> OdooException.Forbidden("Bạn không có quyền thực hiện thao tác này.")
        name.endsWith("UserError")       -> OdooException.Business(msg.ifBlank { "Không thể thực hiện thao tác này." })
        name.endsWith("ValidationError") -> OdooException.Business(msg.ifBlank { "Dữ liệu chưa hợp lệ." })
        name.endsWith("MissingError")    -> OdooException.Business("Bản ghi đã bị xoá hoặc không còn tồn tại.")
        name.contains("SessionExpired")  -> OdooException.SessionExpired
        else -> OdooException.Unknown(msg.ifBlank { "Máy chủ Odoo trả lỗi không xác định." })
    }
}
fun Throwable.toVietnameseMessage(): String = when (this) {
    is OdooException.Forbidden -> "Bạn không có quyền thực hiện thao tác này."
    is OdooException.Business -> message.orEmpty()
    is OdooException.SessionExpired -> "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại."
    is IOException -> "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại."
    else -> "Có lỗi xảy ra. Vui lòng thử lại."
}
```


## 6. Timeout & retry — luật cứng

| Loại | Retry? | Vì sao |
|---|---|---|
| `search_read`, `web_search_read`, `read`, `read_group`, `fields_get` | **Có**, 2 lần, 300ms → 1.2s | Đọc, idempotent |
| `create`, `write`, `unlink`, `action_*`, capture ảnh/chữ ký | **Không bao giờ** | Retry = tạo trùng chứng từ (Luật 4) |

`readMethods = setOf("search_read", "web_search_read", "read", "read_group", "fields_get")` — nếu `method !in readMethods` thì gọi **đúng 1 lần**.
Không bật `HttpRequestRetry` mặc định: nó retry **cả POST ghi**. `call_kw` là POST kể cả khi đọc → tự viết vòng retry như trên (hoặc `retryIf` chỉ khi `request.method == HttpMethod.Get` cho endpoint GET thuần).

## 7. Data class theo `docs/04` §6

```kotlin
@Serializable data class Session(val token: String, val uid: Int, val name: String)
data class DriverJob(
    val id: Int, val ref: String, val shop: String, val address: String,
    val contactName: String?, val contactPhone: String?, val windowFrom: String?,
    val windowTo: String?, val expectedPackages: Int, val state: String, val note: String?,
)
data class Chuyen(val id: Int, val code: String, val mode: String,               // AIR | SEA
                  val cutoff: String?, val capacityKg: Double, val usedKg: Double,
                  val loadPercent: Double, val state: String, val orders: List<ChuyenOrder>)
data class ChuyenOrder(val soId: Int, val so: String, val shop: String, val kg: Double, val packages: Int, val state: String)
data class TrackEvent(val stage: String, val at: String, val place: String?, val note: String?)
```

`CapturePayload` có `ByteArray` → **không** nhét thẳng vào JSON: dùng DTO base64 (`CaptureRequestDto(packages, weightKg, note, photos: List<String>, signaturePng: String, clientUuid)`) hoặc `multipart/form-data` tới `/lab/api/v1/driver/jobs/<id>/capture`. Many2one trả `[id, name]` → helper `JsonObject.m2o(field): Pair<Int, String>?`.

## 8. `/lab/api/v1` — Bearer do mình dựng, envelope `{ok, data, error}`

```kotlin
class LabApiClient(private val baseUrl: String, private val http: HttpClient, private val tokens: TokenStore) {
    suspend fun <T> post(path: String, body: JsonObject, decode: (JsonElement) -> T): T {
        val resp = http.post("$baseUrl/lab/api/v1/$path") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${tokens.token()}")     // token từ Settings, không hardcode
            setBody(body)
        }
        if (resp.status == HttpStatusCode.Unauthorized) throw OdooException.SessionExpired
        val env = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        if (env["ok"]?.jsonPrimitive?.booleanOrNull != true)
            throw OdooException.Business(env["error"]?.jsonPrimitive?.contentOrNull ?: "Máy chủ trả lỗi.")
        return decode(env.getValue("data"))
    }
}
```

Endpoint custom dùng `auth='public'` + token riêng (hết hạn 8h, lưu hash) — **không** phải `session_id`. 401 → app quay về Login, xoá token.

## Checklist nhanh

- [ ] `session_id` đọc từ `Set-Cookie`/cookie jar, **không** đọc từ JSON body.
- [ ] Không có `Authorization: Bearer` nào gọi vào `/web/dataset/call_kw`.
- [ ] `baseUrl`/`db`/API key đến từ Login + `Settings`/env; không có chuỗi hardcode.
- [ ] `uid == 0/false` → thông báo "Sai máy chủ, cơ sở dữ liệu, tài khoản hoặc API key".
- [ ] Retry **chỉ** cho method đọc; không có vòng retry nào bọc `create/write`.
- [ ] `AccessError` → "Bạn không có quyền…"; `UserError` → giữ nguyên message của BE.
- [ ] Có fixture JSON thật từ `call_kw` để test parse + map lỗi (skill 12).
