# 10 — KMP: app chạy thế nào, xem ở đâu

> Viết cho người **chưa từng dùng Gradle/JVM**. Đọc xong bạn phải trả lời được: *"app ở đâu, bấm gì để thấy nó, lệnh đó làm gì"*.

---

## 1. KMP là gì (và vì sao đề bài chọn nó)

**Kotlin Multiplatform (KMP)** = viết **một lần** bằng ngôn ngữ Kotlin, **build ra nhiều nền tảng**: Android, iOS, **desktop (Windows/macOS/Linux)**, server, web.

Không có KMP thì phải viết 4 lần:

| Nền tảng | Ngôn ngữ thường dùng | Với KMP |
|---|---|---|
| Android | Kotlin | Kotlin |
| iOS | Swift | Kotlin |
| Desktop | Java / C# / Electron | **Kotlin** ← đề bài cần cái này |
| Web | JavaScript | Kotlin/JS |

**"Desktop target" trong đề bài nghĩa là gì:** build ra một **chương trình chạy trực tiếp trên laptop** — có icon, bấm vào mở **một cửa sổ**, giống Zalo PC hay Notepad. Không phải trang web, không cần trình duyệt.

**Vì sao đề bài bắt chạy desktop để demo:** trong buổi phỏng vấn, mở laptop bấm chạy là xong. Nếu là app điện thoại thì phải cài APK, phải có máy Android, phải chiếu màn hình — rủi ro cao.

---

## 2. "Xem app" khác "xem web" thế nào — chỗ dễ hiểu nhầm nhất

Hai thứ này **khác nhau về bản chất**:

| | Odoo (web) | App KMP (desktop) |
|---|---|---|
| Mở bằng | Trình duyệt (Chrome/Edge) | **Cửa sổ ứng dụng** trên Windows |
| Địa chỉ | `http://localhost:8069` | **không có** địa chỉ |
| Có server không | ✅ Có — server Odoo chạy nền | ❌ Không — app là **client** |
| Có cổng (port) không | ✅ 8069 | ❌ Không |
| Đóng app thì | server vẫn chạy | cửa sổ đóng, hết |
| Ví dụ tương tự | Facebook trên trình duyệt | **Zalo PC** |

**Ẩn dụ cho dễ nhớ:**
- **Odoo** = cái **nhà hàng** (có địa chỉ, có bếp, có kho).
- **App KMP** = **thực khách** (đi tới nhà hàng, gọi món).
- Khách không cần "địa chỉ của khách" — chỉ nhà hàng có địa chỉ.

Vì vậy: **không có chuyện "mở app bằng localhost:xxxx"**. Muốn thấy app → **nhìn cửa sổ nó mở ra**.

> ⚠️ Ngoại lệ: nếu FE-01 tạo thêm **mock server** (server giả để làm UI trước khi BE xong), thì cái đó **có** cổng (mặc định `8080`). Nhưng nó là *đồ nghề tạm*, phải tắt trước demo và phải ghi vào `workflow/runbook.md`.

---

## 3. Ba thứ phải chạy, đúng thứ tự

```
① PostgreSQL  (nơi lưu dữ liệu)       cổng 5432
        ↓  phải có trước
② Odoo 17     (bộ não nghiệp vụ)      cổng 8069   ← mở bằng trình duyệt
        ↓  phải có trước
③ App KMP     (cửa sổ cho tài xế/CS)  không cổng   ← nhìn cửa sổ
```

Bật ①② bằng 1 lệnh:
```powershell
docker compose -f infra/docker-compose.yml up -d
```
Kiểm tra ② đã sống: mở `http://localhost:8069` thấy trang đăng nhập Odoo.

Rồi mới chạy ③.

---

## 4. Lệnh chạy app — và giải thích từng mảnh

```powershell
cd apps/kmp
.\gradlew.bat :composeApp:run
```

> Trên macOS/Linux (hoặc Git Bash) viết là `./gradlew :composeApp:run`.
> Trên Windows PowerShell **phải có `.\`** ở đầu — không có thì máy không tìm thấy file trong thư mục hiện tại.

### Bóc từng phần

| Mảnh lệnh | Nghĩa | Giải thích cho người mới |
|---|---|---|
| `cd apps/kmp` | đi vào thư mục app | `cd` = change directory |
| `gradlew.bat` | **Gradle Wrapper** | Một script nhỏ nằm **trong repo**. Nó tự tải đúng phiên bản Gradle về máy rồi chạy. **Bạn không cần cài Gradle.** Giống `npm` đọc `package.json` |
| `:composeApp` | tên **module con** | Một kho code có thể chia nhiều module. `:` ở đầu là cú pháp của Gradle chỉ "module cấp gốc" |
| `run` | tên **task** | `run` do plugin Compose tạo ra: biên dịch + khởi động app |

### Lệnh này thực sự làm gì (7 bước)

1. Tìm file `gradle/wrapper/gradle-wrapper.properties` → biết cần Gradle phiên bản nào.
2. Chưa có → **tải Gradle về** (lần đầu, ~100–200MB).
3. Đọc `settings.gradle.kts` → biết dự án có module `composeApp`.
4. Đọc `build.gradle.kts` → biết cần thư viện gì (Kotlin 2.4.21, Compose 1.12.2, Ktor…). Chưa có → **tải từ Maven Central** (lần đầu, vài phút).
5. Biên dịch Kotlin → **bytecode** chạy trên JVM (máy ảo Java).
6. Khởi động một **tiến trình JVM** chạy `main()`.
7. `main()` mở một `Window` → **cửa sổ hiện ra trên màn hình Windows**.

**Lần đầu chậm (3–10 phút vì tải). Các lần sau 5–20 giây.**

---

## 5. Code "mở cửa sổ" chỉ có thế này

`apps/kmp/composeApp/src/desktopMain/kotlin/vn/lab/logistics/main.kt`

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {          // khởi động vòng đời ứng dụng desktop
    Window(                         // ← đây là thứ tạo ra CỬA SỔ
        onCloseRequest = ::exitApplication,
        title = "Gom hàng lẻ — CS Console",
    ) {
        App()                       // toàn bộ giao diện nằm trong App()
    }
}
```

Đó là toàn bộ "phép thuật" của desktop app: `application { Window { … } }`.
Bên trong `App()` mới là các màn hình (login, danh sách việc, duo UI …).

---

## 6. Các lệnh khác bạn sẽ gặp

| Lệnh | Làm gì | Khi nào dùng |
|---|---|---|
| `.\gradlew.bat :composeApp:run` | Chạy dev | Hằng ngày, khi sửa code |
| `.\gradlew.bat :composeApp:desktopTest` | Chạy test UI/logic | Trước khi handoff |
| `.\gradlew.bat :composeApp:createDistributable` | Đóng gói thành thư mục chạy được (**không cần JDK ngoài**) | Kiểm tra bản phát hành |
| `.\gradlew.bat :composeApp:runDistributable` | Chạy **bản đã đóng gói** | **Bắt buộc** trước demo (khác `run`!) |
| `.\gradlew.bat :composeApp:packageDistributionForCurrentOS` | Ra file cài `.msi` | ⚠️ Windows cần **WiX Toolset 3.x**; không có thì lỗi |
| `.\gradlew.bat tasks` | Liệt kê mọi task có thể chạy | Khi không biết tên task |

**Vì sao phải thử `runDistributable`:** `run` chạy bằng JDK đang cài trên máy bạn; `runDistributable` chạy bằng **runtime đã cắt gọn** (jlink). Một số thư viện bị jlink cắt mất → `ClassNotFoundException` chỉ nổ ở bản đóng gói. Nếu demo bằng bản đóng gói mà không test → sập trước mặt người chấm.

---

## 7. Cấu trúc thư mục (sau khi P0 tạo)

```
apps/kmp/
├── settings.gradle.kts          ← khai báo có module composeApp
├── build.gradle.kts             ← cấu hình chung
├── gradle/libs.versions.toml    ← KHOÁ VERSION (Kotlin 2.4.21, CMP 1.12.2, Ktor 3.6.0…)
├── gradlew / gradlew.bat        ← wrapper (đã nói ở §4)
├── gradle/wrapper/              ← wrapper properties + jar
└── composeApp/
    ├── build.gradle.kts         ← cấu hình module + packaging
    └── src/
        ├── commonMain/          ← CODE DÙNG CHUNG (phần lớn nằm đây)
        ├── desktopMain/         ← code riêng desktop: main(), FileDialog
        └── commonTest/          ← test
```

**Quy tắc vàng của KMP:** muốn viết code dùng chung → `commonMain`. Cái gì chỉ desktop có (mở file, vẽ cửa sổ) → `desktopMain`. **Không** dùng API Android trong `commonMain`.

---

## 8. Lỗi hay gặp và cách sửa

| Thông báo / hiện tượng | Nguyên nhân | Sửa |
|---|---|---|
| `'gradlew.bat' is not recognized` | Thiếu `.\` ở đầu, hoặc đang sai thư mục | `cd apps/kmp` rồi `.\gradlew.bat` |
| `JAVA_HOME is not set` / Gradle dùng sai Java | Máy có nhiều JDK, PATH trỏ lung tung | Đặt `JAVA_HOME` về JDK 21 (§9) |
| `Unsupported class file major version` | Gradle chạy trên Java quá mới | Dùng JDK 21, đừng dùng 25 |
| Tải dependency rất lâu / treo | Lần đầu, hoặc mạng chậm | Chờ, hoặc cấu hình mirror |
| `Connection refused` khi đăng nhập | Odoo chưa chạy | Bật `docker compose … up -d` |
| Cửa sổ mở nhưng trắng | Compose không lỗi nhưng API trả rỗng | Xem log, kiểm tra `base URL` trỏ `localhost:8069` |
| `ClassNotFoundException` khi chạy bản đóng gói | jlink cắt thiếu module | Thêm `modules(...)` hoặc `includeAllModules = true` |
| Build `.msi` lỗi | Thiếu WiX | Cài WiX 3.x **hoặc** dùng `createDistributable` (không cần installer) |

---

## 9. Trạng thái máy của bạn (đã kiểm tra)

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| **JDK 21** | ✅ `C:\Program Files\Java\jdk-21.0.10` | **Dùng cái này cho Gradle** |
| JDK 25 | ✅ nhưng đang là mặc định trong PATH | Gradle/Kotlin dễ lỗi với bản quá mới → đừng dùng |
| `javac` | ✅ có (JDK đầy đủ, không phải JRE) | |
| `JAVA_HOME` | ❌ chưa đặt | Cần đặt, xem dưới |
| Gradle | ❌ chưa cài | **Không sao** — dùng wrapper |
| Docker | ✅ 29.4.1 | Chạy Odoo/Postgres được |
| `apps/kmp` | ❌ chưa có | **P0 sẽ tạo** |

### Đặt JAVA_HOME về JDK 21 (1 lần)

```powershell
# đặt cho phiên hiện tại
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"

# đặt vĩnh viễn cho user (mở terminal mới mới có hiệu lực)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-21.0.10", "User")
```

Kiểm tra:
```powershell
java -version      # sẽ vẫn hiện 25 nếu chưa mở terminal mới
$env:JAVA_HOME     # phải là jdk-21.0.10
.\gradlew.bat --version   # Gradle sẽ báo "JVM: 21.0.10"
```

**Quy tắc:** con số ở dòng `JVM:` do Gradle in ra là **thứ thật sự được dùng**. Đó là dòng phải kiểm tra, không phải `java -version`.

---

## 10. Lần đầu chạy — checklist

```
□ ① Bật Docker:  docker compose -f infra/docker-compose.yml up -d
□ ② Mở http://localhost:8069 thấy trang login Odoo
□ ③ Đặt JAVA_HOME = jdk-21.0.10
□ ④ powershell -ExecutionPolicy Bypass -File apps\kmp\bootstrap.ps1   (1 lan: tao gradlew)
□ ⑤ cd apps/kmp
□ ⑥ .\gradlew.bat --version      → thấy "JVM: 21.x"
□ ⑦ .\gradlew.bat :composeApp:run → CỬA SỔ APP hiện ra
□ ⑧ Đăng nhập (server http://localhost:8069, db odoo17_lab, API key)
□ ⑨ .\gradlew.bat :composeApp:runDistributable  → chạy lại bằng bản đóng gói
```

Nếu bước ⑥ ra cửa sổ → **bạn đã "xem app" xong.** Không có URL nào cả, và điều đó là bình thường.

---

## 11. Một câu để nhớ

> Odoo là **nhà hàng có địa chỉ** (`localhost:8069`).
> App KMP là **thực khách** — nó mở **cửa sổ** trên màn hình bạn và **đi tới** nhà hàng.
> Vì vậy: muốn xem app thì **chạy lệnh mở cửa sổ**, không phải mở trình duyệt.
