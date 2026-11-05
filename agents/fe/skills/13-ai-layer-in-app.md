## Dùng khi nào

- P9: thêm **VieNeu TTS** đọc timeline hành trình cho tài xế, hoặc **command palette AI** cho CS.
- Cần quyết định "tính năng này có được lên demo không" — Luật 9: **cấm giả lập AI**.
- Review PR có `api.vieneu.io`, `openai`, `LLM` — kiểm tra key không nằm trong code và demo không gọi API sống.

## 1. Nguyên tắc (Luật 9 — AI phải thật)

| Được | Bị chặn |
|---|---|
| Gọi API thật, dữ liệu thật, **pre-generate** trước demo | Gọi API sống trong lúc demo (mạng chậm = mất điểm) |
| Rule-based parser cho tập intent **khai báo cố định** | Để LLM tự bịa lệnh không tồn tại |
| Nói rõ "trợ lý chưa cấu hình" khi thiếu key | Giả vờ có AI, hardcode câu trả lời |
| Cache audio theo hash văn bản | Gọi lại API cho cùng một câu |
| Ghi prompt/cách dùng vào `docs/ai-log.md` | "Nghe hợp lý nên tin" |

## 2. VieNeu AI TTS — sự thật đã kiểm chứng

| Hạng mục | Giá trị |
|---|---|
| Base URL | `https://api.vieneu.io/api/v1` |
| Auth | `Authorization: Bearer vn_sk_...` (hoặc `X-API-Key`) |
| Engine | hiện **chỉ** `v4` |
| Voice | **resolve runtime**: `GET /v1/voices?engine=v4` → chọn theo **tên tiếng Việt** (vd "Ngọc Lan"); **không** hardcode voice id |
| Tổng hợp | `POST /v1/audio/speech` — OpenAI-compatible, sync hoặc stream |
| Async (tuỳ chọn) | `POST /v1/tts` → `GET /v1/tts/{jobId}` — chỉ cần khi audio dài; demo **không** dùng |
| Tính tiền | theo **ký tự**, **tối thiểu 50 ký tự**/lần gọi |

> ⚠️ Base đã gồm `/api/v1` → URL đầy đủ là `https://api.vieneu.io/api/v1/v1/voices?engine=v4`. Nghe "lạ" nhưng đúng theo tài liệu đã kiểm chứng — **kiểm tra lại docs/curl của VieNeu** trước khi viết script mới. Tên field trong body speech cũng phải đối chiếu tài liệu: giữ chúng trong **một** DTO duy nhất (§3) để sửa một chỗ.

## 3. Ktor client

```kotlin
// commonMain/core/ai/VieNeuTts.kt
@Serializable data class Voice(val id: String, val name: String)
@Serializable data class SpeechRequest(
    val engine: String = "v4", val voice: String, val input: String,
    val response_format: String = "wav",     // WAV để phát được bằng Java Sound (không cần lib MP3)
)

class VieNeuTts(private val http: HttpClient, private val apiKey: String, private val base: String = "https://api.vieneu.io/api/v1") {
    suspend fun resolveVoice(name: String): Voice {
        val voices = http.get("$base/v1/voices?engine=v4") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }.body<List<Voice>>()                                        // ⚠️ xác nhận shape: có thể là {data:[...]}
        return voices.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalStateException("Không tìm thấy giọng đọc '$name'")
    }

    suspend fun speak(text: String, voice: Voice): ByteArray {
        require(text.trim().length >= 50) { "VieNeu tính tiền tối thiểu 50 ký tự — câu quá ngắn, gộp lại hoặc đệm ngữ cảnh." }
        val resp = http.post("$base/v1/audio/speech") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(SpeechRequest(voice = voice.id, input = text))
        }
        if (!resp.status.isSuccess()) throw IOException("VieNeu ${resp.status.value}: ${resp.bodyAsText().take(200)}")
        return resp.bodyAsBytes()
    }
}
```

API key **chỉ** đến từ `System.getenv("VIENEU_API_KEY")` (hoặc keychain); không nằm trong repo, không trong `libs.versions.toml`.

## 4. Pre-generate audio → `demo/voice/` (không gọi API lúc demo)

```powershell
# demo/voice/generate-voice.ps1 — chạy TRƯỚC demo, commit file wav vào demo/voice/
# (lời bình nằm trong demo/voice/voice-lines.json — một nguồn duy nhất, khớp docs/06 §4)
$ErrorActionPreference = "Stop"
$key = $env:VIENEU_API_KEY; if (-not $key) { throw "Thiếu VIENEU_API_KEY (không hardcode key vào repo)." }
$base = "https://api.vieneu.io/api/v1"
$voices = Invoke-RestMethod -Uri "$base/v1/voices?engine=v4" -Headers @{ Authorization = "Bearer $key" }
$voice = ($voices | Where-Object { $_.name -eq "Ngọc Lan" } | Select-Object -First 1)
if (-not $voice) { throw "Không thấy giọng 'Ngọc Lan' — liệt kê tên thật: $($voices.name -join ', ')" }

New-Item -ItemType Directory -Force demo/voice | Out-Null
$lines = Get-Content demo/voice/voice-lines.json -Raw | ConvertFrom-Json    # [{id,text}]
foreach ($l in $lines) {
    if ($l.text.Trim().Length -lt 50) { Write-Warning "Bỏ qua '$($l.id)': dưới 50 ký tự (bị tính min 50)"; continue }
    $out = "demo/voice/$($l.id).wav"
    if (Test-Path $out) { Write-Host "có rồi: $out"; continue }               # cache theo id
    $body = @{ engine = "v4"; voice = $voice.id; input = $l.text; response_format = "wav" } | ConvertTo-Json -Compress
    Invoke-WebRequest -Uri "$base/v1/audio/speech" -Method Post -Headers @{ Authorization = "Bearer $key" } `
        -ContentType "application/json" -Body $body -OutFile $out
    Write-Host "OK $out ($((Get-Item $out).Length) bytes)"
}
```

Quy tắc: file audio là **artifact demo** (commit được, nhẹ); mỗi câu ≤ 2 câu nói; text nằm trong `voice-lines.json` để tái sinh được; **không** sinh 100 file (tiền theo ký tự).

## 5. Phát audio trong app (`desktopMain`, WAV)

```kotlin
// desktopMain/core/ai/AudioPlayer.desktop.kt
class DesktopAudioPlayer : AudioPlayer {
    private var clip: Clip? = null
    override fun play(wavFile: File) {
        stop()
        clip = AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(wavFile)); start() }
    }
    override fun stop() { clip?.let { if (it.isRunning) it.stop(); it.close() }; clip = null }
}
```

- Chỉ WAV (`javax.sound.sampled`); MP3 **không** phát được bằng Java Sound → nếu VieNeu chỉ trả MP3, pre-convert sang WAV (ffmpeg) hoặc thêm lib — kiểm tra trước demo.
- `AudioPlayer` là interface ở `commonMain`; UI chỉ gọi `player.play(File("demo/voice/${stage}.wav"))`. Không có file = hiện nút disabled + tooltip "Chưa có bản đọc", **không** giả vờ đang phát.
- Nếu chạy từ repo (`./gradlew run`) thì đường dẫn tương đối `demo/voice/` cần resolve theo working dir — đặt đường dẫn qua `Settings`/env, không hardcode `C:\`.

## 6. Command palette AI — tập intent **khai báo cố định**

```kotlin
enum class AiIntent(val samples: List<String>) {
    AssignOrder(listOf("gán đơn SO123 cho chuyến CH/AIR/26/0001", "gán SO456 vào CH/SEA/26/0002")),
    OpenChuyen(listOf("mở chuyến CH/AIR/26/0001", "xem chuyến CH/AIR/26/0001")),
    ShowOverload(listOf("chuyến nào quá tải", "danh sách quá tải")),
    RefreshBoard(listOf("tải lại bảng", "làm mới")),
    SpeakTimeline(listOf("đọc hành trình đơn SO123")),
}

data class ParsedCommand(val intent: AiIntent, val args: Map<String, String>, val raw: String)

// Bước 1 (luôn chạy được, 0 đồng, 0 mạng): parser rule-based cho đúng tập trên
fun parseCommand(text: String): ParsedCommand? {
    val t = text.trim().lowercase()
    val so = Regex("""so\s*([a-z0-9/]+)""").find(t)?.groupValues?.get(1)?.uppercase()
    val chuyen = Regex("""ch\s*/\s*[a-z0-9]+(?:\s*/\s*[a-z0-9]+)*""").find(t)?.value?.uppercase()?.replace(" ", "")
    return when {
        t.startsWith("gán") && so != null && chuyen != null -> ParsedCommand(AiIntent.AssignOrder, mapOf("so" to so, "chuyen" to chuyen), text)
        t.startsWith("mở") && chuyen != null -> ParsedCommand(AiIntent.OpenChuyen, mapOf("chuyen" to chuyen), text)
        t.contains("quá tải") -> ParsedCommand(AiIntent.ShowOverload, emptyMap(), text)
        t.contains("tải lại") || t.contains("làm mới") -> ParsedCommand(AiIntent.RefreshBoard, emptyMap(), text)
        t.startsWith("đọc") && so != null -> ParsedCommand(AiIntent.SpeakTimeline, mapOf("so" to so), text)
        else -> null
    }
}
```

- Bước 2 (tuỳ chọn, có key): gọi LLM để **chuẩn hoá câu** thành 1 trong 5 intent trên; output **phải** parse lại được thành `AiIntent`, sai → bỏ, không thực thi.
- Mọi lệnh chạy qua API Odoo thật (`call_kw`) rồi refresh dữ liệu — AI **không** tự bịa kết quả, **không** tự ghi khi chưa xác nhận của người dùng (`AssignOrder` mở dialog xác nhận).
- Palette phải hiện gợi ý từ `samples` khi chưa có LLM → người xem thấy rõ đây là "3–5 lệnh mẫu", không phải "AI vạn năng".

## 7. Khi thiếu key/model — fallback khai báo, không giả

```kotlin
sealed interface AiStatus {
    data object Ready : AiStatus
    data object NoKey : AiStatus        // "Chưa cấu hình trợ lý AI — đang dùng bộ lệnh mẫu"
    data class Unavailable(val reason: String) : AiStatus   // "Máy chủ AI không phản hồi"
}

// trong ViewModel
val status: StateFlow<AiStatus> = if (apiKey.isNullOrBlank()) MutableStateFlow(AiStatus.NoKey) else ...
```

UI: banner nhỏ + tooltip ở palette; **tắt** nút LLM-only, vẫn giữ 5 lệnh rule-based và TTS file local. Trong video demo nói đúng: "phần này chạy bằng bộ lệnh khai báo, không phải LLM".

## Checklist nhanh

- [ ] Không có `vn_sk_...`/LLM key trong repo; chỉ đọc từ env/keychain.
- [ ] Demo **không** gọi mạng ra `api.vieneu.io`: audio đã pre-generate trong `demo/voice/`.
- [ ] Voice id resolve runtime từ `GET /v1/voices?engine=v4`; không hardcode id.
- [ ] Mỗi lần gọi speech có text ≥ 50 ký tự (tránh bị tính min) và được cache theo id/hash.
- [ ] `AiIntent` là tập đóng 5 lệnh; parser rule-based chạy được khi offline.
- [ ] Thiếu key → `AiStatus.NoKey` hiện banner, không giả lập câu trả lời.
- [ ] Đã ghi vào `docs/ai-log.md`: công cụ, prompt, kết quả, đã sửa gì sau khi kiểm chứng.
