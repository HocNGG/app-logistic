# apps/kmp — App desktop (Kotlin Multiplatform)

Khung KMP cho **CS Console** và **Driver app**. Chạy trên **desktop target** để demo (đề bài yêu cầu).

> Giải thích KMP từ đầu + lệnh chạy chi tiết: **`docs/10-kmp-chay-the-nao.md`**

---

## Chạy lần đầu (3 bước)

```powershell
# 0. Một lần duy nhất: tạo gradle wrapper (repo không commit file nhị phân)
powershell -ExecutionPolicy Bypass -File apps\kmp\bootstrap.ps1

# 1. Vào thư mục
cd apps\kmp

# 2. Kiểm tra JDK đang dùng — PHẢI thấy "JVM: 21.x"
.\gradlew.bat --version

# 3. Mở app → CỬA SỔ hiện ra
.\gradlew.bat :composeApp:run
```

**Bắt buộc có Odoo chạy trước** (để login): `docker compose -f infra/docker-compose.yml up -d`

---

## Lệnh hay dùng

| Lệnh | Làm gì |
|---|---|
| `.\gradlew.bat :composeApp:run` | Chạy dev (dùng JDK trên máy) |
| `.\gradlew.bat --offline :composeApp:selfCheck` | **Tự kiểm tra không mở GUI**: WCAG AA (12 cặp màu), 3 ngưỡng duo UI, luật lineHeight tiếng Việt. Mã thoát 0 = đạt |
| `.\gradlew.bat --offline :composeApp:compileKotlinDesktop` | Chỉ biên dịch (nhanh nhất, không mở cửa sổ) |
| `.\gradlew.bat :composeApp:runDistributable` | Chạy **bản đóng gói** — phải test trước demo |
| `.\gradlew.bat :composeApp:createDistributable` | Đóng gói ra thư mục chạy được, không cần JDK ngoài |
| `.\gradlew.bat :composeApp:packageDistributionForCurrentOS` | Ra `.msi` (Windows cần **WiX Toolset 3.x**) |
| `.\gradlew.bat :composeApp:assembleDebug` | **Build APK Android** (Wave C) — cần mạng lần đầu, xem §Android bên dưới |
| `.\gradlew.bat :composeApp:desktopTest` | Chạy 35 unit test JVM (commonTest) |
| `.\gradlew.bat tasks` | Liệt kê mọi task |

> **Vì sao có `selfCheck` mà chưa có `desktopTest`:** Wave A build offline, cache Gradle chưa có
> artifact `kotlin-test`. Khi có mạng, chạy `gradlew :composeApp:build` một lần để tải, rồi
> chuyển các assert trong `SelfCheck.kt` thành `desktopTest` (logic đã là hàm thuần ở `design/`).

---

## Android (Wave C — APK test trên máy ảo)

```powershell
cd apps\kmp

# 1. BẮT BUỘC có mạng LẦN ĐẦU (AGP 9.4.1 + androidx.activity-compose + ktor-client-okhttp
#    chưa có trong Gradle cache). KHÔNG thêm --offline.
.\gradlew.bat :composeApp:assembleDebug --stacktrace

# 2. APK nằm ở:
#    composeApp\build\outputs\apk\debug\composeApp-debug.apk

# 3. Cài + mở trên AVD Pixel_7 (đã boot sẵn emulator-5554)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\composeApp\build\outputs\apk\debug\composeApp-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.applogictics/vn.lab.logistics.MainActivity
```

**Cấu hình đã khoá:** AGP 9.4.1 · compileSdk 37 (`Sdk/platforms/android-37.0`) · build-tools 36.0.0 ·
targetSdk 37 · minSdk 26 · `namespace`/`applicationId` = `com.applogictics`.

**Đường dẫn SDK:** `local.properties` → `sdk.dir` (máy này KHÔNG đặt `ANDROID_HOME`). Tệp này
không commit; trên máy khác hãy sửa đúng 1 dòng đó.

**Địa chỉ Odoo trên Android:** mặc định `http://10.0.2.2:8069` (loopback của MÁY HOST nhìn từ
máy ảo — `localhost` trong máy ảo là chính máy ảo). Đổi được ở tab **Đăng nhập** (ô "Máy chủ
Odoo"), giá trị lưu vào SharedPreferences.

**Cleartext (HTTP thường) — mở bằng HAI lớp:** `res/xml/network_security_config.xml`
(`base-config` + `domain-config` cho `10.0.2.2` / `localhost` / `127.0.0.1`) và cờ cũ
`android:usesCleartextTraffic="true"`. Chỉ dùng cho máy chủ thử nghiệm — bản production phải bỏ.

**Đọc log khi app không gọi được Odoo** (mọi lời gọi HTTP đều đi qua `OdooClient.send`, có log):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s AppLogictics
```

| Dòng log | Nghĩa |
|---|---|
| `chẩn đoán mạng · host=… · cleartextChoHost=… · proxyHeThong=…` | in MỘT lần khi tạo client: cleartext có bị chặn cho host đó không, có proxy hệ thống không |
| `HttpClient đã tạo · engine=…` | chứng minh engine thật đang dùng (phải là `OkHttpEngine`) |
| `→ POST http://…/web/session/authenticate` | app ĐÃ gửi request (nếu chỉ có dòng này mà Odoo không thấy ⇒ kẹt ở tầng mạng) |
| `← 200 POST …` | nhận được phản hồi |
| `✗ POST … thất bại: <exception>` + stacktrace | exception THẬT đằng sau câu tiếng Việt "Không kết nối được Odoo ở …" |

Log **không bao giờ** chứa mật khẩu / API key / token / body (chỉ method + URL + mã HTTP).

**Máy ảo Android: vì sao dùng `adb reverse` thay vì `10.0.2.2`**

Trên image emulator của máy này, `10.0.2.2` (loopback máy host) **bị chặn theo UID của tiến
trình app**, không phải lỗi code — bốn phép đo lấy từ chính máy ảo:

| Phép đo | Kết quả |
|---|---|
| `adb shell run-as com.applogictics ping -c 1 10.0.2.2` | **100% packet loss** |
| `adb shell run-as com.applogictics toybox nc -w 5 10.0.2.2 8069` | **Timeout** |
| `adb shell run-as com.applogictics toybox nc -w 5 127.0.0.1 8069` (qua `adb reverse`) | **kết nối được** |
| `adb shell toybox nc … 10.0.2.2 8069` (uid shell 2000) | `HTTP/1.0 200 OK` |

⇒ Tiến trình chạy dưới UID app không ra được `10.0.2.2`, còn shell thì ra được; tắt Wi-Fi cũng
không đổi kết quả (máy ảo có `wlan0 10.0.2.16` + `eth0 10.0.2.15`). Cách chạy được — đã kiểm ở
mức UID — là **đẩy cổng xuống máy ảo** rồi dùng loopback của chính máy ảo:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb reverse tcp:8069 tcp:8069          # máy ảo 127.0.0.1:8069 → máy thật :8069
# Trong app: tab Đăng nhập → "Máy chủ Odoo" = http://127.0.0.1:8069
```

`adb reverse` mất khi rút/khởi động lại máy ảo — chạy lại lệnh là xong. `network_security_config`
đã cho phép cleartext với `127.0.0.1`, và địa chỉ vừa nhập được **lưu bền ngay** (không cần đăng
nhập thành công mới lưu).

> Mẹo khi chạy tự động (không gõ được `:` và `/` bằng `adb shell input text`): dừng app rồi ghi
> thẳng SharedPreferences — app đã `force-stop` nên không có cache trong RAM:
>
> ```powershell
> $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
> & $adb shell am force-stop com.applogictics
> $xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=`"baseUrl`">http://127.0.0.1:8069</string><boolean name=`"realDataSource`" value=`"true`" /></map>"
> $xml | & $adb shell run-as com.applogictics sh -c "cat > shared_prefs/applogictics_session.xml"
> ```
>
> Khoá lưu bền (9 khoá, không có bí mật): `baseUrl` · `db` · `login` · `token` · `uid` ·
> `driverName` · `jobsToday` · `realDataSource` · `tokenSavedAt`. Mật khẩu/API key **không** bao
> giờ được ghi. Token còn hạn (8 giờ kể từ `tokenSavedAt`) ⇒ mở app vào thẳng "Việc hôm nay".

**Responsive — ngưỡng `< 600dp` = điện thoại (`Tokens.BREAKPOINT_COMPACT_DP`):**

| Bề rộng khả dụng | Thanh trên | Nội dung | Nguồn dữ liệu |
|---|---|---|---|
| `< 600dp` (điện thoại) | `CompactTopBar`: logo + tên app + 3 tab chia đều | tràn đầy màn hình, không "khung giả lập" | công tắc nằm **trong màn Đăng nhập** |
| `>= 600dp` (desktop) | `DemoBar` đầy đủ (bề rộng giả lập, kịch bản, công tắc…) | khung giả lập `demoWidthDp` | công tắc trên `DemoBar` |

Bề rộng lấy từ `BoxWithConstraints` (`maxWidth`) — không hard-code tên thiết bị, không đọc
`LocalWindowInfo`. Ngưỡng 600dp là **breakpoint compact của Material**; ba ngưỡng nghiệp vụ của
duo UI (1280/900) vẫn do `DuoArrangement.fromWidth` quyết định và không đổi.

**Ba actual khác desktop:** `platformInfo()` · `newOdooHttpClient()` (engine **OkHttp** thay cho
CIO) · `FileSettingsStorage` (**SharedPreferences** thay cho tệp `~/.applogictics`). Ngoài ra
`core/media/ImageCodec`, `ui/components/SignaturePad`, `ui/platform/FilePicker` cũng có bản
`android.graphics` tương ứng — `commonMain` không đổi chữ ký `expect` nào.

---

## Vì sao có `bootstrap.ps1` mà không có sẵn `gradlew`

Repo **không commit** `gradle-wrapper.jar` vì đây là file nhị phân (không review được, dễ bị nghi ngờ).
`bootstrap.ps1` tải Gradle chính thức từ `services.gradle.org` rồi để Gradle **tự sinh** wrapper — đó là cách sạch nhất.

Sau khi chạy một lần, các file sau xuất hiện và **được commit**:

```
gradlew · gradlew.bat · gradle/wrapper/gradle-wrapper.jar
```

---

## Version đã khoá (đừng tự nâng)

| Thành phần | Version | Ghi chú |
|---|---|---|
| Kotlin | **2.4.21** | `gradle/libs.versions.toml` |
| Compose Multiplatform | **1.12.2** | |
| Gradle | **9.8.2** | `gradle/wrapper/gradle-wrapper.properties` |
| JDK toolchain | **21** | `jvmToolchain(21)` trong `composeApp/build.gradle.kts` |
| UI | **Material 2** | Material 3 trong CMP 1.12.2 vẫn **alpha** → ADR-006 |

---

## Lỗi hay gặp

| Hiện tượng | Sửa |
|---|---|
| `'gradlew.bat' is not recognized` | Thiếu `.\`, hoặc chưa chạy `bootstrap.ps1` |
| `JAVA_HOME is not set` / build chọn JDK 25 | Chạy `bootstrap.ps1` lần nữa (nó đặt `JAVA_HOME` về JDK 21) |
| `Unsupported class file major version` | Gradle đang chạy trên JDK 25 → đặt `JAVA_HOME` về 21 |
| `Connection refused` khi login | Odoo chưa chạy (`docker compose … up -d`) |
| `ClassNotFoundException` ở bản đóng gói | jlink cắt thiếu module → thêm `modules(...)` hoặc `includeAllModules = true` |
| Build `.msi` lỗi | Thiếu WiX → dùng `createDistributable` thay thế |

---

## Cấu trúc

```
apps/kmp/
├── bootstrap.ps1                  ← chạy 1 lần
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml      ← version khoá tuyệt đối
├── gradle/wrapper/…               ← sinh bởi bootstrap
└── composeApp/
    ├── build.gradle.kts
    └── src/
        ├── commonMain/kotlin/vn/lab/logistics/
        │   ├── App.kt              ← gốc giao diện: theme + route (Login ↔ Driver) + thanh demo
        │   ├── Platform.kt         ← `expect`
        │   ├── design/             ← TOKENS + AppTheme (M2) + contrastRatio/WCAG — nơi DUY NHẤT có màu
        │   ├── core/model/         ← DriverJob (hợp đồng docs/04 §6, không tự thêm field)
        │   ├── core/repo/          ← LoginRepo, DriverJobsRepo (interface) + mock/ (Wave A)
        │   ├── ui/components/      ← DuoScaffold, StateHost (4 trạng thái), StatusPill, KeyboardHelpDialog
        │   └── feature/
        │       ├── login/          ← LoginScreen + LoginViewModel (4 trạng thái)
        │       └── driver/         ← DriverJobsScreen (demo duo UI) + ViewModel + presentation
        └── desktopMain/kotlin/vn/lab/logistics/
            ├── main.kt             ← application { Window { App() } }
            ├── SelfCheck.kt        ← tự kiểm tra WCAG + 3 ngưỡng + lineHeight (không cần GUI)
            └── Platform.desktop.kt ← `actual`
```

**Quy tắc:** code dùng chung → `commonMain`. Cái chỉ desktop có (cửa sổ, file dialog) → `desktopMain`. Không dùng API Android trong `commonMain`.

---

## Trạng thái Wave A (đã xong)

1. **Design system** — `design/Tokens.kt` (spacing 4dp, radius, touch 56/48dp, 3 ngưỡng, 4 cặp màu light+dark) + `design/AppTheme.kt` (Material 2 + CompositionLocal + typography tiếng Việt + `contrastRatio()`).
2. **DuoScaffold** — `ui/components/DuoScaffold.kt`, 3 ngưỡng 1280/900, overlay có nút Quay lại, state do caller giữ.
3. **Login** — 4 trạng thái, API key (không lưu file), cảnh báo `http://`.
4. **Demo duo UI** — `feature/driver/DriverJobsScreen.kt` với 6 việc mẫu (phủ 6 trạng thái thật của `lab.yeu.cau.lay_hang`: assigned · cancelled · en_route · picked_up · failed · confirmed) + thanh trượt giả lập bề rộng ở `App.kt`.
5. **Kiểm chứng** — `:composeApp:selfCheck` (12 cặp tương phản + 6 mốc ngưỡng + 9 style chữ + 9 mục trạng thái: **ĐẠT 0 lỗi**).
6. **Đồng bộ trạng thái với BE-01** — 7 mã `state` đọc trực tiếp từ `addons/lab_laixe/models/lab_yeu_cau_lay_hang.py` (đối chiếu 2026-10-28), nhãn chép đúng cột 2 của `fields.Selection`.

## Trạng thái Wave B (đã xong — FE-01)

1. **Brand palette** — `design/Tokens.kt` thêm `object Brand`: `night #070B14 · sky #38BDF8 ·
   cyan #22D3EE · indigo #6366F1 · ink #F8FAFC` (đúng `docs/11-ui-3d-web.md` §3). Màu semantic
   `ok/warn/bad/info` **giữ nguyên**. Dark mode chuyển nền sang `Brand.night`.
2. **Logo vẽ bằng Compose Canvas** — `ui/components/LogoMark.kt`. Toạ độ chép từ
   `assets/brand/logo-mark.svg` (viewBox 64). Không asset nhị phân ⇒ sắc nét ở 30dp và 132dp.
   Dùng ở **góc trên-trái** thanh demo (`BrandLogo`) và ở hero (`LogoMark(glow = true)`).
3. **Màn Welcome/hero** — `feature/welcome/WelcomeScreen.kt`: nền gradient + lưới mờ (Canvas),
   logo nghiêng theo con trỏ, 3 chip chặng nghiệp vụ (⑤ chuyến · ② lấy hàng · ⑩ hành trình),
   nút **"Vào app"**. Route mặc định khi mở app là `Route.Welcome`.
4. **Giả-3D** — `ui/components/Tilt3D.kt`: `graphicsLayer { rotationX/rotationY;
   cameraDistance = 12f * density }`, làm mượt bằng `animateFloatAsState`. **Chỉ** dùng ở hero/card,
   không dùng cho danh sách dài.
5. **Đường lui bắt buộc** — công tắc **"Giảm chuyển động"** trên thanh demo
   (`LocalReducedMotion`): tắt tilt + nhấp nháy, nội dung vẫn đọc được đủ (`docs/11` §5 tiêu chí 3).
6. **Kiểm chứng** — `:composeApp:selfCheck` in **ĐẠT — 0 lỗi**: 18 cặp tương phản (12 semantic +
   6 brand), 3 cặp đồ hoạ (ngưỡng 3:1), 3 ngưỡng duo, 11 style chữ, 9 mục trạng thái,
   **5 hex brand phải khớp docs/11** và **4 bất biến hình học logo**.

## Bước tiếp theo

1. Thay `MockLoginRepo` bằng `OdooLoginRepo` (Ktor + `POST /web/session/authenticate`, API key, giữ cookie `session_id`) — UI không phải sửa.
2. Thay `MockDriverJobsRepo` bằng impl gọi `GET /lab/api/v1/driver/jobs` (cần BE-01 dựng `lab_api`).
3. Offline-first: outbox + retry (docs/04 §8); màn Capture ảnh + chữ ký (skill 06).
4. CS console duo UI (board + chuyến + kéo thả), Track timeline.
5. Chuyển `SelfCheck` thành `desktopTest` (cần tải `kotlin-test` khi có mạng).

Đặc tả đầy đủ: `docs/04-kmp-app.md`. Skill: `agents/fe/skills/`.
