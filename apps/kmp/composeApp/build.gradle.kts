import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Wave C — APK test trên máy ảo Android (AGP 9.4.1, xem gradle/libs.versions.toml).
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Wave B: cần serializer cho DTO `@Serializable` của lab_api (Ktor + kotlinx.serialization).
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Toolchain khoá ở 21: máy này có cả JDK 25 (mặc định trong PATH),
    // mà Gradle/Kotlin dễ lỗi với bản JDK quá mới. Xem docs/10 §9.
    jvmToolchain(21)

    // Desktop target — tên khác "jvm()" nên phải wire ở compose.desktop bên dưới
    jvm("desktop")

    // =====================================================================================
    // WAVE C — TARGET ANDROID
    //
    // Chỉ thêm `androidTarget()`; toàn bộ code dùng chung (UI, repo, DTO, expect/actual) KHÔNG
    // phải sửa gì thêm ngoài các file `actual` trong `src/androidMain/`.
    //
    // `compilerOptions { jvmTarget = 17 }`: AGP biên dịch phần Java của module ở mức 17
    // (`compileOptions` bên dưới). KGP phải khai CÙNG mức, nếu lệch sẽ đỏ ngay ở
    // "Inconsistent JVM-target compatibility detected for tasks 'compileDebugJavaWithJavac' …".
    // Target desktop vẫn giữ 21 như cũ (lấy từ toolchain) — không đổi hành vi bản desktop.
    // =====================================================================================
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting
        // Dùng đúng kiểu `by getting` như desktopMain ở trên (không dựa vào accessor sinh tự động).
        val androidMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            // Material 2 = bản STABLE. Material 3 vẫn là alpha trong CMP 1.12.2 → ADR-006
            implementation(compose.material)
            implementation(compose.ui)

            // Wave B — gọi Odoo THẬT (docs/02 §6, ADR-004).
            // `ktor-client-core` đã chứa `HttpCookies` (giữ cookie `session_id` của Odoo 17).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.negotiation)
            // `ktor-serialization-kotlinx-json` tự kéo `kotlinx-serialization-json`
            // → KHÔNG khai version riêng cho kotlinx-serialization (tránh lệch bản).
            implementation(libs.ktor.json)
        }

        // commonTest: chỉ dùng kotlin-test (KHÔNG kéo framework nặng).
        // Chạy: .\gradlew.bat --offline :composeApp:desktopTest
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            // Engine CIO chỉ có ở desktop — `newOdooHttpClient()` (actual) dựng client bằng engine này.
            implementation(libs.ktor.client.cio)
        }

        // ------------------------------------------------------------------ Wave C: Android
        androidMain.dependencies {
            // Engine OkHttp: bản `actual` của `newOdooHttpClient()` (OdooHttp.android.kt).
            // Vì sao OkHttp chứ không CIO trên Android: OkHttp là engine được khuyến nghị cho
            // Android — có sẵn trong hệ sinh thái, dùng được HTTP/2 + TLS của hệ điều hành.
            implementation(libs.ktor.client.okhttp)
            // `ComponentActivity.setContent`, `enableEdgeToEdge`, `registerForActivityResult`
            // (bộ chọn ảnh SAF — không cần quyền đọc bộ nhớ).
            implementation(libs.androidx.activity.compose)
        }
    }
}

// =======================================================================================
// CẤU HÌNH ANDROID (Wave C) — chỉ ảnh hưởng target Android, desktop không đổi.
//
// compileSdk 37 / targetSdk 37 — máy này CHỈ cài `Sdk/platforms/android-37.0`
//   (Android 17, `ro.build.version.sdk=37`) và `Sdk/build-tools/36.0.0`.
//   AGP 9.4.1 tự chọn build-tools 36.0.0 (SdkConstants.CURRENT_BUILD_TOOLS_VERSION) nhưng vẫn
//   khai tường minh để build KHÔNG cố tải bản khác khi máy offline.
//   ⚠️ Nếu AGP báo "Failed to find target with hash string 'android-37…'" thì đây là chỗ sửa
//   (đổi `compileSdk`/thêm `compileSdkMinor = 0`), không phải sửa code.
//
// minSdk 26: Camera/SAF + java.time đều có từ 26; máy ảo Pixel_7 (API 37) thoả.
// namespace = applicationId = `com.applogictics` (theo yêu cầu). Package Kotlin của app vẫn là
//   `vn.lab.logistics` — manifest khai ĐẦY ĐỦ tên class (`vn.lab.logistics.MainActivity`) chứ
//   không dùng `.MainActivity`, vì tiền tố `.` sẽ được hiểu là `com.applogictics.*`.
// =======================================================================================
android {
    namespace = "com.applogictics"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.applogictics"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        // Không khai testInstrumentationRunner: `assembleDebug` không chạy test Android nào,
        // và thêm runner là thêm dependency tải từ mạng mà build này không cần.
    }

    compileOptions {
        // PHẢI khớp `androidTarget { compilerOptions { jvmTarget = 17 } }` ở trên.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("debug") {
            // APK test: không minify để log/stacktrace đọc được khi demo trên máy ảo.
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            // Chưa có signingConfig: `assembleRelease` sẽ đỏ, `assembleDebug` thì không cần.
        }
    }

    packaging {
        resources {
            // Tránh "More than one file was found with OS independent path 'META-INF/AL2.0'"
            // khi trộn Compose + Ktor (cả hai đều đóng gói giấy phép này).
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    // KHÔNG khai `buildFeatures.compose = true`: từ Kotlin 2.x compose compiler là PLUGIN của
    // KGP (`alias(libs.plugins.composeCompiler)`), không phải `composeOptions` của AGP.
}

compose.desktop {
    application {
        // Bắt buộc khi target tên là "desktop" (không phải "jvm")
        from(kotlin.targets["desktop"])

        mainClass = "vn.lab.logistics.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "AppLogictics"
            packageVersion = "1.0.0"
            description = "Gom hàng lẻ — CS Console & Driver App"
            vendor = "AppLogictics"
        }
    }
}

// ---------------------------------------------------------------------------------------
// Tự kiểm tra KHÔNG mở GUI — "kịch bản smoke" theo Luật 7 của AGENTS.md.
//
//   cd apps/kmp
//   .\gradlew.bat --offline :composeApp:selfCheck
//
// Kiểm 9 khối (xem src/desktopMain/.../SelfCheck.kt):
//   1. Tương phản WCAG AA (>= 4.5:1) của 12 cặp màu nghiệp vụ, cả light + dark
//   2. Ba ngưỡng duo UI đúng biên 1280 / 900 dp
//   3. Luật typography tiếng Việt: lineHeight >= 1.4 × fontSize
//   4. Ánh xạ 7 mã state của lab.yeu.cau.lay_hang
//   5. Brand palette + 6. Hình học logo
//   7. Parse payload THẬT của `GET /lab/api/v1/driver/jobs` (windowFrom "01:30",
//      expectedPackages VẮNG ⇒ null không nổ, window "-" ⇒ "Chưa hẹn giờ", stateLabel server)
//   8. Không hardcode URL/token trong mã nguồn (grep chính thư mục `src/`)
//   9. Bất biến an toàn CAPTURE: thiếu ảnh/chữ ký/số kiện/kg ⇒ chặn gửi; ảnh nén ≤ 5 MB
//      (nén THẬT một ảnh nhiễu lớn); clientUuid là UUID v4 và GIỮ NGUYÊN khi "Thử lại"
//      (chống trùng — `driver.py::capture` chỉ chặn trùng khi uuid y hệt).
//
// commonTest dùng kotlin-test (đã khai báo ở trên). Lần đầu chạy cần mạng để tải
// artifact `kotlin-test`. Khi có mạng, chạy `gradlew :composeApp:build` một lần để tải,
// rồi chuyển các assert trong SelfCheck thành desktopTest (logic đã là hàm thuần ở design/).
// ---------------------------------------------------------------------------------------
val desktopMainCompilation = kotlin.targets.getByName("desktop").compilations.getByName("main")

// Classpath để chạy selfCheck: output của desktopMain + toàn bộ dependency runtime (jars trong cache).
val selfCheckClasspath = files(
    desktopMainCompilation.output.allOutputs,
    desktopMainCompilation.runtimeDependencyFiles,
)

tasks.register<JavaExec>("selfCheck") {
    group = "verification"
    description = "Chạy tự kiểm tra design tokens + duo UI, in kết quả ra console (không mở cửa sổ)."
    dependsOn(desktopMainCompilation.compileTaskProvider)
    mainClass.set("vn.lab.logistics.SelfCheckKt")
    classpath = selfCheckClasspath
    // Bắt buộc trên Windows: `System.out` mặc định dùng code page của console → chuỗi tiếng
    // Việt bị mất dấu (mojibake). Ép UTF-8 cho cả file.encoding + stdout/stderr.
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
