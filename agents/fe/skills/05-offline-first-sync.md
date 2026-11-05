## Dùng khi nào

- Làm phân hệ **DRIVER** (P3): tài xế mất mạng vẫn chụp ảnh/ký/xác nhận, có mạng thì đồng bộ.
- Cần sửa hành vi "3 việc chờ đồng bộ", retry, hoặc nghiệp vụ chặn confirm khi thiếu ảnh/chữ ký.
- Review PR có `Outbox`, `retry`, `client_uuid` (skill này là chuẩn để chấm).

## 1. Nguyên tắc (đúng `docs/04` §8)

| Luật | Hệ quả code |
|---|---|
| Offline first | Thao tác xong → ghi **đĩa** (JSON + file), UI báo "đã lưu, chờ đồng bộ", **không** chặn người dùng |
| Worker **tuần tự** | 1 coroutine + `Mutex`, không song song (tránh đua `state` trên Odoo) |
| Idempotent | `client_uuid` (UUID v4) sinh **một lần** lúc capture; BE chống trùng bằng nó |
| 4xx = cần sửa · 5xx/IO = retry | 4xx → `NEEDS_FIX` (người dùng sửa, không retry mù); 5xx/IO → giữ trong outbox, backoff khi có mạng |
| Không confirm thiếu ảnh + chữ ký | Validate **trước** khi ghi outbox, cả khi offline |
| Không tự đổi state cục bộ | Chỉ optimistic + rollback khi BE trả lỗi |

## 2. Data class Outbox

```kotlin
@Serializable
data class OutboxItem(
    val clientUuid: String,                       // UUID.randomUUID().toString() — sinh 1 lần
    val jobId: Int,
    val kind: Kind,                               // CAPTURE | FAIL | ACCEPT
    val createdAt: String,                        // Instant ISO-8601 (kotlinx-datetime)
    val payload: CapturePayloadDto,               // packages/weightKg/note/photos(base64 hoặc tên file)
    val photos: List<String>,                     // tên file trong outboxDir/files/
    val signaturePng: String,                     // tên file PNG
    val attempts: Int = 0,
    val lastError: String? = null,
    val status: Status = Status.PENDING,
) {
    enum class Kind { CAPTURE, FAIL, ACCEPT }
    enum class Status { PENDING, SENDING, NEEDS_FIX, DONE }
}
@Serializable data class CapturePayloadDto(val packages: Int, val weightKg: Double, val note: String?)
```

Lưu trên đĩa (không dùng SQLite/DB cho demo):

```
%LOCALAPPDATA%/AppLogictics/outbox/
├── <clientUuid>.json          # OutboxItem (nguồn sự thật)
└── files/<clientUuid>-0.jpg, -1.jpg, <clientUuid>-sig.png
```

```kotlin
class OutboxStore(private val dir: Path) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    fun save(item: OutboxItem, photos: List<ByteArray>, signature: ByteArray) = withContext(Dispatchers.IO) {
        filesDir.mkdirs()
        photos.forEachIndexed { i, bytes -> File(filesDir, "${item.clientUuid}-$i.jpg").writeBytes(bytes) }
        File(filesDir, "${item.clientUuid}-sig.png").writeBytes(signature)
        File(dir, "${item.clientUuid}.json").writeText(json.encodeToString(item))
    }
    fun pending(): List<OutboxItem> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { json.decodeFromString<OutboxItem>(it.readText()) }.getOrNull() }
        ?.filter { it.status != OutboxItem.Status.DONE }
        ?.sortedBy { it.createdAt } ?: emptyList()
}
```

## 3. Worker tuần tự (pseudo-code)

```kotlin
class OutboxWorker(
    private val store: OutboxStore,
    private val api: DriverApi,                  // POST /lab/api/v1/driver/jobs/<id>/capture
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    fun start() = scope.launch(Dispatchers.IO) { connectivity.online.collect { if (it) flush() } }
    suspend fun flush() = mutex.withLock {                                  // KHÔNG song song
        for (item in store.pending()) {
            if (!connectivity.isOnline()) return@withLock
            store.mark(item.copy(status = OutboxItem.Status.SENDING))
            when (val r = runCatching { api.capture(item) }) {
                is Result.Success -> store.delete(item)                     // xoá JSON + files
                is Result.Failure -> when (val e = r.error) {
                    is ApiError.Client4xx -> store.mark(item.copy(
                        status = OutboxItem.Status.NEEDS_FIX, lastError = e.message))   // người sửa
                    else -> { store.mark(item.copy(attempts = item.attempts + 1,
                                lastError = e.message)); delay(backoff(item.attempts)) } // để lại, thử sau
                }
            }
        }
    }
    private fun backoff(attempts: Int) = (500L shl attempts.coerceAtMost(5)).coerceAtMost(60_000L)
}
```

`store.delete` chỉ xoá **sau** khi BE trả `ok` — mất file là mất chứng từ (Luật 4).

## 4. Phân loại lỗi

| HTTP | Ý nghĩa | Hành vi |
|---|---|---|
| 200 `{ok:true}` | Đã ghi trên Odoo | Xoá khỏi outbox, cập nhật cache job |
| 400/422 | Dữ liệu sai (số kiện ≤ 0, thiếu file) | `NEEDS_FIX` + hiện trong `DriverOutbox`, người dùng sửa rồi gửi lại |
| 401/403 | Token hết hạn / sai quyền | Dừng worker, quay về Login — **không** đánh `NEEDS_FIX` |
| 409 | `client_uuid` đã tồn tại (BE báo trùng) | **Thành công** — xoá khỏi outbox (đây là idempotency hoạt động đúng) |
| 429/5xx/IO | Máy chủ/mạng | Giữ `PENDING`, backoff, thử lại |

## 5. Connectivity

`commonMain` chỉ có interface; `desktopMain` cài đặt (không có API Android):

```kotlin
interface Connectivity { val online: StateFlow<Boolean>; fun isOnline(): Boolean }
// desktopMain: poll nhẹ 10s — vừa kiểm mạng vật lý vừa kiểm Odoo sống
class DesktopConnectivity(private val baseUrl: String, private val http: HttpClient, scope: CoroutineScope) : Connectivity {
    private val _online = MutableStateFlow(true)
    override val online = _online.asStateFlow(); override fun isOnline() = _online.value
    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val ok = runCatching {
                    http.get("$baseUrl/web/webclient/version_info") { timeout { requestTimeoutMillis = 3_000 } }
                }.getOrNull()?.status?.isSuccess() == true
                _online.value = ok
                delay(10_000)
            }
        }
    }
}
```

Không dựa vào `NetworkInterface` một mình (có wifi vẫn có thể không tới Odoo). Probe endpoint công khai, rẻ.

## 6. UI: badge + màn `DriverOutbox`

```kotlin
@Composable
fun OutboxBadge(count: Int, onClick: () -> Unit) {
    if (count == 0) return
    TextButton(onClick = onClick) {
        Badge(backgroundColor = MaterialTheme.colors.secondary) { Text("$count") }
        Text("  việc chờ đồng bộ", style = MaterialTheme.typography.caption)   // tiếng Việt, skill 11
    }
}
```

- Badge luôn thấy ở màn chính + `DriverJobs` (kể cả khi đang online nhưng còn item).
- `DriverOutbox`: mỗi dòng = giờ tạo · job · lỗi cuối · nút "Gửi lại" / "Sửa" (khi `NEEDS_FIX`) / "Xoá" (chỉ khi người dùng xác nhận, có dialog). **Đang gửi**: hiện `LinearProgressIndicator`, không cho xoá khi `SENDING`.

## 7. Chặn confirm thiếu chứng từ — kiểm tra 2 lớp

```kotlin
fun validateBeforeConfirm(vm: CaptureState): List<String> = buildList {
    if (vm.photos.size < 1) add("Cần ít nhất 1 ảnh kiện hàng")
    if (vm.signature == null || vm.signature.isEmpty) add("Cần chữ ký người giao")
    if (vm.packages <= 0) add("Số kiện phải lớn hơn 0")
}
```

- Lớp 1 (UI): nút "Hoàn tất" `enabled = errors.isEmpty()`, lỗi hiện dưới từng slot.
- Lớp 2 (ViewModel `confirm()`): gọi lại `validateBeforeConfirm` — **không** tin UI. Offline hay online đều chặn như nhau.
- Nếu BE từ chối vì thiếu attachment (4xx) → `NEEDS_FIX`, không tự retry.

## Checklist nhanh

- [ ] `client_uuid` sinh **một lần** lúc capture và không đổi khi retry.
- [ ] Worker là **một** coroutine + `Mutex`; không có `async` song song trên outbox.
- [ ] Xoá outbox chỉ sau `ok:true` (hoặc `409` trùng `client_uuid`).
- [ ] 4xx → `NEEDS_FIX` (người sửa); 5xx/IO → retry có backoff; 401 → về Login.
- [ ] Badge "N việc chờ đồng bộ" hiện ở màn chính, kể cả khi online còn tồn.
- [ ] Validate ảnh + chữ ký ở **cả** UI và ViewModel, chạy được khi offline.
- [ ] Có test: tắt mạng → capture → bật mạng → outbox rỗng, BE có đúng 1 bản ghi.
