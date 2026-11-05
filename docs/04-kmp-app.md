# 04 — ĐẶC TẢ APP KOTLIN MULTIPLATFORM (desktop)

> Đề bài: *"Làm thêm một ứng dụng di động cho bài toán trên, viết bằng Kotlin Multiplatform (KMP). Ứng dụng phải chạy được trên desktop target để demo."*
> Yêu cầu của sếp: **responsive UI**, **"iPhone 18 duo UI"**, REA, critical thinking.
> Người đọc: FE-01. BE-01 đọc §6 để biết cần cung cấp API gì.

---

## 1. Định vị sản phẩm

App **không** phải bản sao Odoo trên desktop (đó là viết lại Odoo — vi phạm Luật 1).

App là **lớp làm việc tại hiện trường + bảng điều khiển** cho hai nhóm người dùng mà giao diện Odoo-web không phục vụ tốt:

| Phân hệ | Người dùng | Vấn đề Odoo web không giải quyết | App giải quyết |
|---|---|---|---|
| **DRIVER** | Tài xế đi lấy hàng | Odoo web nặng, không có luồng "ảnh · ký · xác nhận", không offline | App 3 bước, một tay, offline-first |
| **CS CONSOLE** | CS/Sale, Sale admin | Bảng kanban Odoo không cho kéo đơn vào chuyến, không thấy tải chuyến cùng lúc | Duo UI: danh sách + chi tiết cạnh nhau, kéo thả |
| **TRACK** | Khách / đại lý | Portal Odoo chỉ xem, không "sống" | Timeline hàng đi, không cần đăng nhập (access_token) |

**Nguyên tắc:** app **gọi** Odoo, không **chứa** logic nghiệp vụ. Không tính cước, không tính tồn kho, không tạo bút toán trong app. Mọi con số lấy từ Odoo.

---

## 2. Stack — versions đã kiểm chứng (2026-10)

| Thành phần | Version | Ghi chú |
|---|---|---|
| Kotlin | **2.4.21** | stable |
| Compose Multiplatform | **1.12.2** | stable |
| Compose compiler | `org.jetbrains.kotlin.plugin.compose` — **theo version Kotlin** | không còn artifact riêng |
| Gradle plugin | `org.jetbrains.kotlin.multiplatform` = Kotlin · `org.jetbrains.compose` = CMP | |
| **Thư viện UI** | **Material 2** (`org.jetbrains.compose.material:material:1.12.2`) **stable** | ⚠️ `material3` trong CMP 1.12.2 vẫn là `1.12.0-alpha03` — **không dùng cho demo** |
| Adaptive layout | `org.jetbrains.compose.material3.adaptive:adaptive:1.3.0` | **stable** — dùng được cùng Material 2 |
| Window size class | `androidx.window.core.layout.WindowSizeClass` + `currentWindowAdaptiveInfoV2()` | multiplatform |
| Navigation | `org.jetbrains.androidx.navigation:navigation-compose:2.10.0` | port chính thức của JetBrains |
| DI | **Koin 4.2.2** (+ Koin compiler plugin 1.2.1) | đơn giản, đủ cho demo |
| HTTP | **Ktor 3.6.0** (client CIO/OkHttp trên JVM) | |
| JSON | `kotlinx.serialization` | |
| Local | `multiplatform-settings` (KV nhỏ) + **SQLDelight** hoặc Room 3 cho outbox | chỉ cần KV cho demo |
| Coroutines | `kotlinx-coroutines-core` | |
| Đóng gói | `packageDistributionForCurrentOS` (jpackage + jlink) | xem §9 |

> ⚠️ **Quyết định P0 (ADR-006):** dùng **Material 2 stable** thay vì Material 3 alpha. Lý do: demo không được phép vỡ vì alpha API. Nếu Orchestrator muốn M3, phải chấp nhận rủi ro và khoá version tuyệt đối.
>
> *Ghi chú:* nếu chọn M3, `ListDetailPaneScaffold` (trong material3-adaptive) cho duo UI sẽ có sẵn; với M2 ta tự viết scaffold 2 pane (khoảng 60 dòng, đã có trong skill `03-duo-adaptive-layout`).

---

## 3. Kiến trúc app

```
apps/kmp/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml         # khoá version tuyệt đối, không dùng +
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/vn/lab/logistics/
│       │   ├── App.kt                 # root composable + theme + nav
│       │   ├── di/                    # Koin modules
│       │   ├── core/
│       │   │   ├── net/               # OdooClient, JsonRpc, session, error mapping
│       │   │   ├── model/             # data class: Job, Chuyen, HanhTrinh, SaleOrder...
│       │   │   ├── repo/              # interface + impl (mock/real)
│       │   │   └── sync/              # Outbox, RetryPolicy, Connectivity
│       │   ├── design/                # tokens, Typography, Theme, Icons
│       │   ├── ui/components/         # DuoScaffold, StatusPill, LoadState, EmptyState...
│       │   └── feature/
│       │       ├── login/
│       │       ├── driver/            # JobList, JobDetail, Capture, Sign, Confirm
│       │       ├── cs/                # Board, ChuyenPanel, DragDrop
│       │       └── track/             # Timeline
│       ├── desktopMain/kotlin/        # main() window, file picker, packaging config
│       └── commonTest/kotlin/         # repo tests, state tests, compose UI tests
```

**Quy tắc kiến trúc:**
- `feature/*` chỉ phụ thuộc `core/repo` **interface**, không phụ thuộc `net` trực tiếp.
- `core/net` là nơi duy nhất biết Odoo.
- UI không có `if (demo)`; chọn impl trong Koin module.
- Mọi state màn hình: `sealed interface UiState { Loading, Empty, Error(msg, retry), Content(data) }`.

---

## 4. "iPhone 18 duo UI" — nghĩa chính xác và cách làm

**Sự thật (đã kiểm chứng):** *iPhone Duo* là sản phẩm thật của Apple (ra mắt 09/2026, $1.999): máy gập kiểu book, **hai màn hình** (màn ngoài + màn trong gập), **mỗi màn hình có size class riêng**. Apple công bố "reserved regions" (bản lề, camera) và khái niệm **arrangement: split / overlay**, cùng "displacement patterns" khi mở/gập.

**Dịch sang yêu cầu kỹ thuật cho app desktop:**

| Khái niệm Duo | Yêu cầu trong app | Cách làm trong Compose |
|---|---|---|
| Hai màn hình, hai size class | Layout thay đổi theo **vùng hiển thị**, không theo "thiết bị" | `currentWindowAdaptiveInfoV2().windowSizeClass` → 3 ngưỡng |
| **Arrangement = split** | Hai pane song song, mỗi pane tự scroll | `Row` với 2 `LazyColumn` độc lập |
| **Arrangement = overlay** | 1 pane chính + 1 pane phủ (drawer/sheet) khi hẹp | `Box` + `AnimatedVisibility` / `ModalDrawer` |
| **Reserved regions** (bản lề, camera) | Không đặt nội dung/nút quan trọng ở vùng bị che | `WindowInsets` + padding an toàn; tránh nút ở mép giữa khi cửa sổ rất rộng |
| **Displacement patterns** | Khi đổi kích thước, **giữ nguyên ngữ cảnh**: item đang chọn, vị trí scroll, text đang gõ | State nằm ở ViewModel, không ở composable cục bộ |
| Chuyển màn hình mượt | Không "nhảy" layout khi kéo cửa sổ | `animateContentSize` / `Crossfade` theo ngưỡng, tránh re-create |

### Ngưỡng bắt buộc (tiêu chí nghiệm thu)

| Bề rộng cửa sổ | Arrangement | Hành vi |
|---|---|---|
| **≥ 1280 dp** | Split (rộng) | Danh sách (360–420dp) + chi tiết; có thể thêm cột thứ 3 (thông tin chuyến) |
| **900–1280 dp** | Split (hẹp) | Danh sách 300dp + chi tiết; ẩn cột phụ |
| **< 900 dp** | Overlay | 1 pane; chọn item → chi tiết phủ lên, có nút Back; giữ scroll danh sách |

**Định nghĩa "responsive" của dự án:** *kéo cửa sổ từ 1600dp xuống 700dp rồi lên lại, không mất dữ liệu đang gõ, không mất item đang chọn, không vỡ layout, và luồng chính vẫn hoàn thành được.*

---

## 5. Màn hình (IA)

```
                    ┌─ LOGIN ──────────────┐
                    │ server URL · db ·    │
                    │ login · api key      │
                    └──────────┬───────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
   ┌────┴─────┐          ┌─────┴──────┐        ┌─────┴─────┐
   │  DRIVER  │          │ CS CONSOLE │        │   TRACK   │
   │ (tài xế) │          │  (duo UI)  │        │ (công khai)│
   └────┬─────┘          └─────┬──────┘        └─────┬─────┘
        │                      │                      │
   1. Việc hôm nay       1. Board yêu cầu       1. Nhập mã vận đơn
   2. Chi tiết việc         + chuyến sắp chốt    2. Timeline
   3. Chụp ảnh           2. Kéo đơn vào chuyến    3. Chi tiết mốc
   4. Ký biên nhận       3. Duyệt / giảm tải
   5. Xác nhận (kg/kiện) 4. Tạo yêu cầu lấy hàng
   6. Hàng chờ đồng bộ   5. KPI
```

### 5.1 DRIVER — "ảnh · ký · xác nhận"
| Màn | Nội dung | Trạng thái |
|---|---|---|
| `DriverJobs` | Danh sách việc hôm nay: giờ hẹn, shop, địa chỉ, số kiện dự kiến, khoảng cách | loading/empty/error/content |
| `DriverJobDetail` | Nút gọi, mở bản đồ (external), ghi chú, nút "Bắt đầu đi" | |
| `DriverCapture` | 3 slot ảnh (hàng, kiện, biên nhận giấy) + camera/kéo ảnh | thiếu ảnh → cảnh báo |
| `DriverSign` | Pad vẽ chữ ký + xoá + xác nhận | cảm ứng + chuột |
| `DriverConfirm` | Nhập số kiện thực tế + kg ước tính + ghi chú → "Hoàn tất" | validate số > 0 |
| `DriverOutbox` | Hàng chờ đồng bộ (số item, retry, lỗi) | bắt buộc khi offline |

**Ràng buộc UX:** một tay, găng tay → nút ≥ 56dp, không cần chữ nhỏ, mọi bước có "quay lại" không mất dữ liệu.

### 5.2 CS CONSOLE — duo UI
| Vùng | Nội dung |
|---|---|
| Pane trái (danh sách) | Tab: **Yêu cầu mới** / **Chuyến sắp chốt** / **Ngoại lệ**. Mỗi dòng: mã, shop, kg, trạng thái, badge ngoại lệ |
| Pane phải (chi tiết) | Theo loại đang chọn: yêu cầu lấy hàng (gán tài xế), hoặc chuyến (danh sách đơn, thanh tải, nút duyệt/giảm tải) |
| Toolbar | Search (Ctrl/Cmd+K), refresh, tạo mới, chuyển ngôn ngữ |
| Trạng thái | Thanh tải chuyến: xanh < 90%, vàng 90–100%, **đỏ > 100%** + nhãn "Quá tải" |

**Kéo thả:** kéo 1 yêu cầu từ pane trái thả vào danh sách đơn của chuyến (pane phải) → gọi API gán → optimistic update + rollback nếu lỗi.

### 5.3 TRACK — timeline
Nhập mã (hoặc mở từ deep link) → timeline dọc: ⑨ hàng đi → ⑩ đến nơi → ⑪ đại lý giao. Không hiển thị giá, không hiển thị đối tác nội bộ, không hiển thị tên tài xế.

---

## 6. Hợp đồng dữ liệu với backend (BE-01 phải cung cấp)

Nguồn chuẩn: `docs/02-kien-truc.md` §6. Tóm tắt model app cần:

```kotlin
// core/model
data class Session(val token: String, val uid: Int, val name: String)

data class DriverJob(
    val id: Int, val ref: String, val shop: String,
    val address: String, val contactName: String?, val contactPhone: String?,
    val windowFrom: String?, val windowTo: String?,
    val expectedPackages: Int, val state: String,
    val note: String?
)

data class CapturePayload(
    val packages: Int, val weightKg: Double, val note: String?,
    val photos: List<ByteArray>,      // jpeg nén ≤ 5MB
    val signaturePng: ByteArray
)

data class Chuyen(
    val id: Int, val code: String, val mode: String,       // AIR | SEA
    val cutoff: String?, val capacityKg: Double, val usedKg: Double,
    val loadPercent: Double, val state: String,
    val orders: List<ChuyenOrder>
)

data class ChuyenOrder(val soId: Int, val so: String, val shop: String,
                       val kg: Double, val packages: Int, val state: String)

data class TrackEvent(val stage: String, val at: String, val place: String?, val note: String?)
```

**Quy tắc:** app không tự tính `loadPercent`, không tự tính cước, không đổi state cục bộ (chỉ optimistic với rollback).

---

## 7. Client Odoo (Ktor) — đúng chuẩn Odoo 17

```kotlin
// 1) Đăng nhập — lấy session_id (cookie) + uid
POST /web/session/authenticate
{"jsonrpc":"2.0","method":"call","id":1,
 "params":{"db":"odoo17_lab","login":"driver1","password":"<api_key_or_password>"}}
// → result: {uid, session_id, username, user_context, ...}; uid=false = sai thông tin

// 2) Gọi model — kèm cookie session_id
POST /web/dataset/call_kw
Cookie: session_id=<sid>
{"jsonrpc":"2.0","method":"call","id":2,
 "params":{"model":"lab.yeu.cau.lay.hang","method":"search_read",
           "args":[[["state","!=","confirmed"]]],
           "kwargs":{"fields":["name","partner_id","state"],"limit":50}}}
```

**Bắt buộc:**
- Lưu session trong `multiplatform-settings`, **không** hardcode.
- Odoo 17 hỗ trợ **API key** (`res.users.apikeys`) dùng thay password khi authenticate → dùng API key cho app, **không** lưu password người dùng.
- Endpoint custom (`/lab/api/v1/*`) dùng `Authorization: Bearer <token>` (BE-01 dựng) cho tài xế và track công khai.
- Map lỗi Odoo → thông báo tiếng Việt: `odoo.exceptions.AccessError` → "Bạn không có quyền…", `UserError` → hiển thị message gốc (đã tiếng Việt từ BE).
- Timeout 15s, retry 2 lần với backoff cho GET; **không** retry POST ghi dữ liệu (tránh trùng).

---

## 8. Offline-first (chỉ cho DRIVER)

```
Hành động offline → ghi Outbox (JSON + file đính kèm trong thư mục app)
   ↓ có mạng
Worker đồng bộ tuần tự (không song song) → POST capture
   ↓ thành công → xoá khỏi outbox, cập nhật cache
   ↓ thất bại 4xx → đánh dấu "cần sửa", hiện trong DriverOutbox
```

- Idempotency: sinh `client_uuid` mỗi lần capture; BE dùng để chống trùng.
- Hiển thị: badge "3 việc chờ đồng bộ" luôn thấy ở màn chính.
- **Không** cho phép confirm khi chưa có ảnh + chữ ký, kể cả offline.

---

## 9. Đóng gói & demo (bẫy thật)

| Bẫy | Sự thật | Cách tránh |
|---|---|---|
| Cross-compile | jpackage **không** cross-compile: `.msi` phải build trên Windows | Build trên **máy demo**, sáng ngày demo |
| MSI cần WiX | jpackage backend MSI cần **WiX Toolset 3.x** trong PATH | Hoặc dùng `createDistributable` (image, không cần installer) |
| jlink cắt module | `ClassNotFoundException` lúc **chạy**, không phải lúc build | `suggestModules`, `modules("java.sql", …)`, hoặc `includeAllModules=true`; **smoke test `runDistributable`** |
| SmartScreen | Installer không ký → "Windows protected your PC" | Ký (OV/EV cần HSM token) hoặc **không dùng MSI**, chạy `run`/`runDistributable` |
| Máy demo thiếu JDK | jpackage cần JDK ≥ 17 | Bundle JRE trong image; hoặc chạy từ repo |

**Kịch bản demo an toàn (bắt buộc có 3 lớp):**
1. `./gradlew :composeApp:run` từ repo (lớp chính, luôn hoạt động nếu có JDK).
2. `./gradlew :composeApp:runDistributable` (image đã đóng gói, kiểm tra giống bản phát hành).
3. **Video quay sẵn** (lớp cuối, `docs/06`).

---

## 10. Design tokens (bắt buộc, không tự phát minh màu)

```kotlin
object Tokens {
    // spacing scale 4dp
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp
    val s5 = 24.dp; val s6 = 32.dp; val s7 = 48.dp
    // radius
    val rSm = 6.dp; val rMd = 10.dp; val rLg = 16.dp
    // type (Material2 Typography mở rộng, ưu tiên tiếng Việt dễ đọc)
    // body 14–15sp, lineHeight 1.5; title 18sp/600; label 12sp/500
    // màu nghiệp vụ (không dùng color mặc định)
    val okBg   = Color(0xFFE7F6EC); val okFg   = Color(0xFF17663A)
    val warnBg = Color(0xFFFFF4E5); val warnFg = Color(0xFF8A4B00)
    val badBg  = Color(0xFFFDECEC); val badFg  = Color(0xFF9B1C1C)
    val infoBg = Color(0xFFEAF2FE); val infoFg = Color(0xFF1D4ED8)
}
```

**Ràng buộc:** mọi màn dùng chung token; không hardcode `Color(0x...)` ngoài file `design/`. Dark mode định nghĩa bộ token thứ 2; contrast ≥ 4.5:1 cả hai chế độ.

---

## 11. Bàn phím (desktop phải dùng được không chuột)

| Phím | Hành động |
|---|---|
| `Ctrl/Cmd + K` | Mở search / command palette |
| `J` / `K` hoặc `↑`/`↓` | Di chuyển trong danh sách |
| `Enter` | Mở chi tiết item đang chọn |
| `Esc` | Đóng overlay / huỷ |
| `R` | Refresh dữ liệu pane hiện tại |
| `D` | (CS console) Mở dialog "Duyệt" cho item đang chọn |
| `?` | Mở bảng trợ giúp phím tắt |
| `Tab` / `Shift+Tab` | Di chuyển focus, có focus ring rõ |

Bắt buộc: **focus không bị "nuốt"** — mọi control có thể Tab tới, có `Modifier.focusable()` + focus ring 2dp.

---

## 12. AI layer trong app (P9, nếu kịp)

Chỉ làm nếu **dữ liệu thật** (Luật 9):

| Tính năng | Cách làm | Rủi ro |
|---|---|---|
| **Command palette AI** | Gõ "gán đơn SO123 cho chuyến CH/AIR/26/0001" → parse → gọi API Odoo | Cần LLM key; có thể chỉ demo 3 câu lệnh mẫu (khai báo rõ là "intent mẫu") |
| **Gợi ý giảm tải** | Danh sách đơn + lý do gợi ý do AI sinh từ dữ liệu chuyến | AI giải thích được vì sao → tốt cho demo |
| **Voice (VieNeu AI)** | Đọc timeline hành trình bằng tiếng Việt cho tài xế khi đang lái | Cần audio; nếu server chậm → pre-generate file |
| **OCR AWB** | Chụp AWB → điền số AWB + kg | Nếu không đủ tin cậy → bỏ, không diễn |

**Tích hợp VieNeu (đã kiểm chứng):**
- Base `https://api.vieneu.io/api/v1`, auth `Authorization: Bearer vn_sk_...` (hoặc `X-API-Key`).
- `POST /v1/audio/speech` — OpenAI-compatible, sync/stream; `GET /v1/voices?engine=v4` để lấy voice id (tên tiếng Việt, vd "Ngọc Lan"); engine hiện tại **chỉ `v4`**; tính tiền theo ký tự, tối thiểu 50 ký tự.
- Với demo: **pre-generate** file WAV/MP3 vào `demo/voice/`, app chỉ phát file → không phụ thuộc mạng lúc demo.

---

## 13. Test & chất lượng

| Loại | Yêu cầu |
|---|---|
| Unit | `Repo` parse JSON Odoo đúng (fixture thật từ `call_kw`), map lỗi đúng |
| State | `UiState` transition: loading→content, loading→error→retry→content |
| Compose UI | 3 test: chọn item ở duo split; resize → overlay vẫn giữ selection; capture thiếu ảnh → báo lỗi |
| Smoke | Script khởi động app + login + mở 1 job trên máy demo |
| A11y | Kiểm bằng Tab-only walkthrough, ghi lại video ngắn |

Lệnh: `./gradlew :composeApp:desktopTest` và `./gradlew :composeApp:run`.

---

## 14. Definition of Done riêng của app

- [ ] Chạy trên **Windows desktop** (máy demo), không cần IDE.
- [ ] Login bằng API key, không lưu password.
- [ ] 3 phân hệ hoạt động với **dữ liệu thật** từ Odoo.
- [ ] 4 trạng thái ở mọi màn.
- [ ] Duo UI đúng 3 ngưỡng; kéo cửa sổ không mất state.
- [ ] Phím tắt đủ, help overlay có.
- [ ] Driver: ảnh + chữ ký + số kiện/kg; offline queue hoạt động.
- [ ] Track: chỉ hiện field whitelist.
- [ ] Tiếng Việt toàn bộ, không lộ chuỗi tiếng Anh.
- [ ] Dark mode không vỡ tương phản.
- [ ] `runDistributable` chạy được (kiểm tra bản đóng gói, không chỉ `run`).
- [ ] Có ≥ 1 cảnh quay cho video demo.
