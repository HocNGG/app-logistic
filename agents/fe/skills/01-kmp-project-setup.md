## Dùng khi nào

- Ngày đầu dựng `apps/kmp` (P3) — chưa có file Gradle nào, cần app chạy được trên Windows.
- Cần thêm/bớt dependency mà **không** phá version khoá (§2).
- Build đỏ vì `jlink`/`jpackage` (§8) hoặc IDE không nhận target desktop.

## 1. Bản đồ thư mục

Theo đúng `docs/04` §3: `composeApp/src/{commonMain,desktopMain,desktopTest}/kotlin/vn/lab/logistics/` — `commonMain` chứa `App.kt`, `di/`, `core/`, `design/`, `ui/`, `feature/`; `desktopMain` chỉ chứa thứ phụ thuộc AWT/JVM (`main.kt`, file dialog, `ImageIO`, audio); `desktopTest` chứa compose UI test + repo test.

## 2. Version khoá (đã kiểm chứng 2026-10)

| Thành phần | Version | Ghi chú |
|---|---|---|
| Kotlin | **2.4.21** | `org.jetbrains.kotlin.plugin.compose` **theo version Kotlin** |
| Compose Multiplatform | **1.12.2** | |
| **UI kit** | **Material 2** `org.jetbrains.compose.material:material:1.12.2` | stable |
| Adaptive info | `org.jetbrains.compose.material3.adaptive:adaptive:1.3.0` | **chỉ** để lấy `currentWindowAdaptiveInfoV2()` + `WindowSizeClass` |
| Navigation | `org.jetbrains.androidx.navigation:navigation-compose:2.10.0` | |
| Koin | `io.insert-koin:koin-core` / `koin-compose` **4.2.2** | dùng DSL module, **không** KSP annotation |
| Ktor | **3.6.0** | `CIO` trên desktop, `MockEngine` trong test |

> ⚠️ **Không** thêm `org.jetbrains.compose.material3:material3` — trong CMP 1.12.2 nó vẫn là `1.12.0-alpha03` (ADR-006). Chỉ `material3.adaptive` được dùng và **chỉ** để đọc size class.
> ⚠️ Nếu bật Koin annotations/KSP: phải khớp Kotlin 2.4.21 — **kiểm tra lại API + compatibility của Koin 4.2.2 ↔ KSP ↔ Kotlin 2.4.21** trước khi dùng. Mặc định skill này dùng DSL, không cần KSP.

## 3. `settings.gradle.kts` và `gradle/libs.versions.toml`

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "applogictics"
include(":composeApp")
```

```toml
[versions]
kotlin = "2.4.21"
cmp = "1.12.2"

[libraries]
compose-material2 = { module = "org.jetbrains.compose.material:material", version.ref = "cmp" }
m3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version = "1.3.0" }
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version = "2.10.0" }
koin-core = { module = "io.insert-koin:koin-core", version = "4.2.2" }
koin-compose = { module = "io.insert-koin:koin-compose", version = "4.2.2" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version = "3.6.0" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version = "3.6.0" }

ktor-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version = "3.6.0" }
ktor-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version = "3.6.0" }
kotlinx-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.8.0" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version = "1.10.2" }
multiplatform-settings = { module = "com.russhwolf:multiplatform-settings", version = "1.2.0" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.2" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "cmp" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

## 4. `composeApp/build.gradle.kts`

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform); alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler); alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")                       // source set: commonMain / desktopMain / desktopTest
    sourceSets {
        val desktopMain by getting
        commonMain.dependencies {
            implementation(compose.runtime); implementation(compose.foundation); implementation(compose.ui)
            implementation(libs.compose.material2)      // Material 2 stable — KHÔNG material3
            implementation(libs.m3.adaptive)            // chỉ WindowSizeClass / adaptive info
            implementation(libs.navigation.compose)
            implementation(libs.koin.core); implementation(libs.koin.compose)
            implementation(libs.ktor.client.core); implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.json); implementation(libs.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core); implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)     // skiko + AWT window
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing) // Dispatchers.Main trên desktop
        }
        // desktopTest.dependencies { uiTestJUnit4 + kotlin("test-junit") } — xem skill 12
    }
}

compose.desktop {
    application {
        from(kotlin.targets["desktop"])            // bắt buộc khi target tên "desktop"
        mainClass = "vn.lab.logistics.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)        // MSI cần WiX 3.x trong PATH
            packageName = "AppLogictics"; packageVersion = "1.0.0"
            modules("java.sql", "java.naming", "jdk.crypto.ec")   // jlink cắt thiếu → CNFE lúc chạy
            windows { menuGroup = "AppLogictics"; shortcut = true; upgradeUuid = "<uuid-cố-định>" }
        }
    }
}

tasks.withType<Test> { systemProperty("java.awt.headless", "false") }   // compose UI test cần display
```


## 5. `desktopMain/kotlin/vn/lab/logistics/main.kt`

```kotlin
package vn.lab.logistics

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.di.initKoin
import vn.lab.logistics.ui.platform.*   // LocalComposeWindow, LocalWindowWidthDp

fun main() = application {
    initKoin(baseUrl = System.getenv("LAB_ODOO_BASE_URL").orEmpty())   // KHÔNG hardcode URL/db
    val state = rememberWindowState(width = 1440.dp, height = 900.dp)
    Window(onCloseRequest = ::exitApplication, state = state, title = "AppLogictics — Gom hàng lẻ") {
        val widthDp = with(LocalDensity.current) { state.size.width.toDp() }
        CompositionLocalProvider(LocalComposeWindow provides window, LocalWindowWidthDp provides widthDp) {
            AppTheme { App() }
        }
    }
}
```

`window` là `ComposeWindow` (AWT Frame) có sẵn trong `FrameWindowScope` của `Window { }` — cần cho `FileDialog` ở skill 06. `LocalWindowWidthDp` khai báo ở `commonMain`, cấp tại đây để duo UI (skill 03) không phụ thuộc API platform.

## 6. Lệnh chạy / đóng gói (Windows là máy demo)

```powershell
cd apps/kmp
./gradlew :composeApp:run                              # lớp demo 1

./gradlew :composeApp:runDistributable                 # lớp demo 2 — smoke thật
./gradlew :composeApp:packageDistributionForCurrentOS  # .exe (WiX nếu .msi)
./gradlew :composeApp:desktopTest                      # test + compose UI test
```

## 7. Bẫy nhanh

| Bẫy | Triệu chứng | Cách tránh |
|---|---|---|
| jlink cắt module | `ClassNotFoundException` **lúc chạy** | thêm `modules(...)`, luôn `runDistributable` |
| Cross-compile / thiếu WiX | Không build `.msi` được | build trên máy demo, hoặc chỉ dùng `createDistributable` |
| Thiếu `coroutines-swing` | `Main dispatcher had failed to initialize` | khai báo ở `desktopMain` |

## Checklist nhanh

- [ ] `./gradlew :composeApp:run` mở cửa sổ 1440×900 trên Windows.
- [ ] `libs.versions.toml` không có `+`/`latest.release`; đủ 6 version của `docs/04` §2.
- [ ] `./gradlew :composeApp:dependencies` không kéo `material3` (chỉ `material3.adaptive`).
- [ ] `main.kt` không chứa URL/db/token — chỉ đọc env.
- [ ] `runDistributable` chạy được, không chỉ `run`.
- [ ] `:composeApp:desktopTest` cấu hình xong, chạy không lỗi.
- [ ] Không có `import android.*` trong `commonMain`.
