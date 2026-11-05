## Dùng khi nào

- Trước mỗi handoff (Luật 7: "UI mới → ít nhất 1 compose UI test hoặc smoke có ghi lại").
- Cần chứng minh 3 hành vi: chọn item ở split · resize vẫn giữ selection · capture thiếu ảnh bị chặn.
- Cần test repo parse **JSON thật** từ Odoo (không phải JSON tự nghĩ ra).

## 1. Dependency + lệnh

```kotlin
// composeApp/build.gradle.kts (skill 01 §4)
val desktopTest by getting
desktopTest.dependencies {
    implementation(compose.desktop.uiTestJUnit4)
    implementation(libs.ktor.client.mock)
    implementation(kotlin("test-junit"))
}
tasks.withType<Test> { systemProperty("java.awt.headless", "false") }   // UI test cần display
```

```powershell
cd apps/kmp
./gradlew :composeApp:desktopTest                                  # tất cả
./gradlew :composeApp:desktopTest --tests "vn.lab.logistics.duo.*" # một nhóm
./gradlew :composeApp:desktopTest --info                           # xem test nào chạy
```

## 2. Test 1 — chọn item ở chế độ split

```kotlin
@OptIn(ExperimentalTestApi::class)
class DuoSplitTest {
    @Test fun selecting_item_opens_detail_in_split_wide() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalWindowWidthDp provides 1440.dp) {
                AppTheme { CsBoardScreen(vm = FakeCsBoardVm.seeded()) }
            }
        }
        onNodeWithTag("request-row-1").performClick()
        onNodeWithTag("chuyen-detail").assertIsDisplayed()
        onNodeWithText("CH/AIR/26/0001").assertIsDisplayed()
        onNodeWithTag("request-row-1").assertIsSelected()          // cần semantics selected (skill 10)
    }
}
```

⚠️ **kiểm tra lại API của Compose Multiplatform 1.12.2**: `runComposeUiTest` (annotation `@OptIn(ExperimentalTestApi::class)`) nằm ở `androidx.compose.ui.test`. Nếu bản này yêu cầu JUnit4 rule (`createComposeRule`) thì dùng rule — nguyên tắc test không đổi.

## 3. Test 2 — resize từ split → overlay vẫn giữ selection (test quan trọng nhất)

```kotlin
@Test fun resizing_to_overlay_keeps_selection() = runComposeUiTest {
    val vm = FakeCsBoardVm.seeded()
    var width by mutableStateOf(1440.dp)
    setContent {
        CompositionLocalProvider(LocalWindowWidthDp provides width) { AppTheme { CsBoardScreen(vm) } }
    }
    onNodeWithTag("request-row-1").performClick()
    onNodeWithTag("chuyen-detail").assertIsDisplayed()

    width = 800.dp                                   // = kéo cửa sổ xuống dưới 900dp
    waitForIdle()

    onNodeWithTag("chuyen-detail").assertIsDisplayed()   // overlay vẫn mở
    assertEquals(1, vm.selectedId)                       // selection ở ViewModel, không bị reset
    onNodeWithTag("overlay-back").performClick()
    onNodeWithTag("request-row-1").assertIsDisplayed()   // danh sách còn nguyên (không dispose)

    width = 1600.dp; waitForIdle()
    assertEquals(1, vm.selectedId)                       // kéo lên vẫn giữ
}
```

Bản chất: test **cùng một composable** dưới 2 `LocalWindowWidthDp` — không cần tạo cửa sổ thật, nên chạy được trên CI. Đây là lý do skill 01 cấp `LocalWindowWidthDp` từ `main.kt`.

## 4. Test 3 — capture từ chối khi thiếu ảnh

```kotlin
@Test fun confirm_is_blocked_when_photo_missing() = runComposeUiTest {
    val vm = FakeCaptureVm(photos = emptyList(), signed = true)
    setContent { AppTheme { DriverCaptureScreen(vm = vm) } }

    onNodeWithTag("confirm-button").performClick()
    onNodeWithText("Cần ít nhất 1 ảnh kiện hàng").assertIsDisplayed()
    assertFalse(vm.sent)                                  // KHÔNG gọi API (cả online lẫn offline)
}

@Test fun confirm_is_blocked_when_signature_missing() = runComposeUiTest {
    val vm = FakeCaptureVm(photos = listOf(FakePhoto()), signed = false, signBytes = ByteArray(0))
    setContent { AppTheme { DriverCaptureScreen(vm = vm) } }
    onNodeWithTag("confirm-button").performClick()
    onNodeWithText("Cần chữ ký người giao").assertIsDisplayed()
    assertFalse(vm.sent)
}
```

## 5. Test 4 — repo parse fixture **thật** từ `call_kw`

Lấy fixture bằng tay (một lần, ghi vào repo) — URL/server lấy từ env, **không** hardcode:

```powershell
if (-not $env:LAB_ODOO_BASE_URL -or -not $env:ODOO_SID) { throw "Đặt LAB_ODOO_BASE_URL và ODOO_SID trong môi trường" }
curl.exe -s -X POST "$env:LAB_ODOO_BASE_URL/web/dataset/call_kw" `
  -H "Content-Type: application/json" -H "Cookie: session_id=$env:ODOO_SID" `
  -d '{"jsonrpc":"2.0","method":"call","id":1,"params":{"model":"lab.chuyen","method":"web_search_read","args":[[["state","=","open"]]],"kwargs":{"fields":["name","ti_le_tai","state"],"limit":5}}}' `
  > apps/kmp/composeApp/src/desktopTest/resources/fixtures/call_kw_chuyen_search_read.json
```

```kotlin
class ChuyenRepoParseTest {
    private val json = """{"result":{"records":[{"id":7,"name":"CH/AIR/26/0001","ti_le_tai":104.5,"state":"open"}],"length":1}}"""

    @Test fun parses_real_shape_of_web_search_read() = runTest {
        val http = HttpClient(MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/web/dataset/call_kw"))
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureOdoo() }
        val repo = ChuyenRepoImpl(clientWithSession(http))          // OdooClient đã có session giả
        val chuyens = repo.openChuyens()
        assertEquals("CH/AIR/26/0001", chuyens.single().code)
        assertEquals(104.5, chuyens.single().loadPercent, 0.001)     // app KHÔNG tự tính lại
    }

    @Test fun maps_access_error_to_vietnamese_message() = runTest {
        val body = """{"error":{"data":{"name":"odoo.exceptions.AccessError","message":"Access Denied"}}}"""
        val http = HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        val ex = assertFailsWith<OdooException.Forbidden> { ChuyenRepoImpl(clientWithSession(http)).openChuyens() }
        assertEquals("Bạn không có quyền thực hiện thao tác này.", ex.toVietnameseMessage())
    }
}
```

`clientWithSession(http)` = helper test: `OdooClient("http://test", http, MapSettings()).apply { session = Session("sid", 2, "CS") }` — không cần DB thật.

Nguyên tắc fixture: chép **nguyên văn** response (kể cả field lạ) → test bắt được `ignoreUnknownKeys` hết hiệu lực; thêm fixture lỗi (AccessError, UserError, `uid:false`) trong cùng thư mục.

## 6. Smoke script — mở app, đăng nhập, mở 1 job (ghi lại kết quả)

```powershell
# infra/scripts/smoke-app.ps1  (TẠO Ở P7 — khi apps/kmp đã có; chạy trên máy demo, KHÔNG dùng trong CI không có display)
$ErrorActionPreference = "Stop"
$root = "apps/kmp"; $marker = Join-Path $root "build/smoke-ok.txt"
Remove-Item $marker -ErrorAction SilentlyContinue
if (-not $env:LAB_ODOO_BASE_URL -or -not $env:LAB_DB -or -not $env:LAB_LOGIN -or -not $env:LAB_API_KEY) {
    throw "Thiếu LAB_ODOO_BASE_URL / LAB_DB / LAB_LOGIN / LAB_API_KEY trong môi trường (không hardcode)."
}
Push-Location $root
$p = Start-Process -FilePath "$PWD\gradlew.bat" -ArgumentList ":composeApp:run","-Dlab.smoke=1" -PassThru -NoNewWindow
try {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline -and -not (Test-Path $marker)) { Start-Sleep -Seconds 2 }
    if (-not (Test-Path $marker)) { throw "SMOKE FAIL: app không ghi marker trong 90s" }
    "SMOKE OK — " + (Get-Content $marker -Raw)      # marker ghi: uid, job ref, thời điểm
} finally { if (-not $p.HasExited) { $p | Stop-Process -Force }; Pop-Location }
```

Phía app: khi thấy `-Dlab.smoke=1` (hoặc `LAB_SMOKE=1`), `main.kt` đăng nhập bằng env, mở job đầu tiên, ghi `build/smoke-ok.txt` rồi `exitApplication()`. Không nhét nhánh này vào luồng UI thật — chỉ rẽ ở `main`.

## Checklist nhanh

- [ ] 3 test UI chạy xanh: split select · resize giữ selection · capture thiếu ảnh bị chặn.
- [ ] Test resize dùng `LocalWindowWidthDp` (không tạo cửa sổ thật) → chạy được trên CI.
- [ ] Có fixture JSON **thật** của `web_search_read` + fixture lỗi `AccessError` trong `desktopTest/resources/fixtures/`.
- [ ] Test khẳng định app **không** tự tính `loadPercent`/cước.
- [ ] `infra/scripts/smoke-app.ps1` (tạo ở P7) chạy trên máy demo, in `SMOKE OK` + marker có uid/job.
- [ ] Không test nào đọc URL/db/token hardcode; tất cả từ env/`MapSettings`.
- [ ] Lệnh chạy test nằm trong `README`/`workflow/task-board.md`, không "chỉ chạy được trên máy tôi".
