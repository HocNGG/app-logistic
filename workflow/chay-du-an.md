# CHẠY DỰ ÁN — WEB, DESKTOP KOTLIN, APP ANDROID, VÀ CÀI APP QUA CÁP USB-C

> Viết 2026-11-02 trên chính máy này (Windows 11, Docker Desktop, JDK 21, Android SDK).
> Mọi lệnh dưới đây **đã chạy thật**, kèm giải thích từng tham số để người mới đọc là hiểu.
> Quy ước: mọi lệnh chạy tại **thư mục gốc dự án** `C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics`
> trừ khi có ghi `cd ...`.

---

## 0. Ba thứ phải có (kiểm tra 30 giây)

| Thứ | Kiểm bằng lệnh | Kết quả đúng |
|---|---|---|
| Docker Desktop **đang chạy** | `docker ps` | in ra bảng container, không báo lỗi `permission denied` |
| JDK 21 | `$env:JAVA_HOME` | `C:\Program Files\Java\jdk-21.0.10` |
| Android SDK | `Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"` | `True` |

**`adb` KHÔNG có trên PATH của máy này** — nên trong tài liệu luôn gọi bằng đường dẫn đầy đủ:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
```

Muốn gõ `adb` cho ngắn (làm 1 lần, mở PowerShell mới):

```powershell
[Environment]::SetEnvironmentVariable("Path",
  $env:Path + ";$env:LOCALAPPDATA\Android\Sdk\platform-tools",
  "User")
```

> Giải thích: `adb` = **Android Debug Bridge**, "cầu nối" giữa máy tính và điện thoại/máy ảo qua cáp
> USB hoặc mạng. Mọi việc cài app, chụp màn hình, gửi lệnh vào điện thoại đều đi qua `adb`.

---

## 1. WEB (Odoo 17) — chạy bằng Docker

### 1.1 Bật web

```powershell
docker compose -f infra/docker-compose.yml up -d
```

| Mảnh lệnh | Nghĩa |
|---|---|
| `docker compose` | công cụ chạy nhiều container (máy ảo nhẹ) theo một tệp cấu hình |
| `-f infra/docker-compose.yml` | chỉ định tệp cấu hình (project có 2 service: `odoo` + `db`) |
| `up` | tạo và chạy các container đã khai |
| `-d` | *detached*: chạy nền rồi trả lại dấu nhắc, không treo cửa sổ |

Mở **http://localhost:8069** → đăng nhập `admin` / `admin`. Database: `odoo17_lab`.
Container: `lab_odoo` (cổng 8069) và `lab_pg` (Postgres, cổng 5432).

### 1.2 Xem nó có sống không

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8069/web/login -TimeoutSec 5 | Select-Object StatusCode
docker compose -f infra/docker-compose.yml ps
docker compose -f infra/docker-compose.yml logs --tail 50 odoo
```

`StatusCode : 200` là sống. `ps` xem container nào đang chạy; `logs --tail 50 odoo` xem 50 dòng log cuối
(đọc log này khi web trắng trang / báo lỗi).

### 1.3 Dừng / bật lại

```powershell
docker compose -f infra/docker-compose.yml stop          # dừng, GIỮ dữ liệu
docker compose -f infra/docker-compose.yml start         # chạy lại
docker compose -f infra/docker-compose.yml restart odoo  # chỉ khởi động lại Odoo
docker compose -f infra/docker-compose.yml down          # dừng + xoá container (dữ liệu vẫn còn trong volume)
```

### 1.4 Cài / nâng cấp module sau khi sửa code Python, XML, SCSS

```powershell
# 1) nạp lại module (chạy container RIÊNG, xong là thoát)
docker compose -f infra/docker-compose.yml run --rm --no-deps odoo `
  odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_core,lab_chuyen,lab_ui --stop-after-init

# 2) xoá cache tài nguyên (CSS/JS) để Odoo dựng lại bundle mới
docker compose -f infra/docker-compose.yml exec -T db psql -U odoo -d odoo17_lab `
  -c "delete from ir_attachment where url like '/web/assets/%';"

# 3) BẮT BUỘC: khởi động lại server đang chạy
docker compose -f infra/docker-compose.yml restart odoo
```

| Mảnh lệnh | Nghĩa |
|---|---|
| `run` | tạo container **tạm** để chạy 1 lệnh, không dùng container đang phục vụ |
| `--rm` | chạy xong thì xoá container tạm |
| `--no-deps` | không bật kèm `db` (db đã chạy rồi) — tránh treo |
| `odoo -c /etc/odoo/odoo.conf` | chạy tiến trình Odoo với tệp cấu hình trong container |
| `-d odoo17_lab` | chọn database |
| `-u lab_core,lab_chuyen` | **update** các module này: đọc lại model/view/rules, ghi vào DB |
| `--stop-after-init` | làm xong thì thoát (không mở server) |

> ⚠️ **Bài học đã trả giá (2026-11-02):** `-u` chỉ ghi vào **database**. Tiến trình Odoo **đang chạy**
> vẫn giữ "bộ nhớ đệm cấu hình" (registry) cũ trong RAM ⇒ quyền mới / view mới **chưa có tác dụng**.
> Phải `restart odoo` (bước 3). Triệu chứng đã gặp: đã cấp quyền đọc đơn bán mà ô chọn vẫn rỗng.

### 1.5 Dữ liệu demo

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\reset-ngay-demo.ps1      # dời mốc ngày về HÔM NAY
powershell -ExecutionPolicy Bypass -File infra\scripts\seed-demo-lon.ps1        # nạp bộ dữ liệu lớn
powershell -ExecutionPolicy Bypass -File infra\scripts\verify-demo-lon.ps1      # đếm lại + KPI
powershell -ExecutionPolicy Bypass -File infra\scripts\reset-demo.ps1           # trả DB về mặc định
powershell -ExecutionPolicy Bypass -File infra\scripts\e2e-capture.ps1          # test luồng tài xế không cần bấm
```

Vì sao cần `reset-ngay-demo`: bộ seed neo cứng `2026-10-29`, còn app tài xế **chỉ trả việc có khung giờ
hôm nay** ⇒ quá vài ngày sẽ thấy "0 việc".

---

## 2. DESKTOP KOTLIN (Compose Multiplatform)

### 2.1 Chạy cửa sổ app

```powershell
cd apps\kmp
.\gradlew.bat :composeApp:run
```

| Mảnh lệnh | Nghĩa |
|---|---|
| `cd apps\kmp` | vào thư mục dự án Gradle (có `gradlew.bat`, `settings.gradle.kts`) |
| `gradlew.bat` | "Gradle wrapper" — tự tải đúng phiên bản Gradle (9.8.2) rồi chạy, không cần cài Gradle |
| `:composeApp:run` | task **chạy** ứng dụng desktop (class chính `vn.lab.logistics.MainKt`) |

> App desktop **không mở cổng nào**. Nó là *client*: mặc định gọi Odoo ở `http://localhost:8069`.
> Muốn xem kết quả thì nhìn **cửa sổ app**, không nhìn trình duyệt.

### 2.2 Đóng gói bản cài (.msi cho Windows)

```powershell
cd apps\kmp
.\gradlew.bat :composeApp:packageDistributionForCurrentOS      # bản cài
.\gradlew.bat :composeApp:createDistributable                  # bản chạy trực tiếp, không cần cài
```

Kết quả nằm trong `apps\kmp\composeApp\build\compose\binaries\main\` (`.msi` / thư mục app).

### 2.3 Tự kiểm tra (self-check) không cần mở UI

```powershell
cd apps\kmp
.\gradlew.bat :composeApp:selfCheck
```

Task này chạy `vn.lab.logistics.SelfCheckKt` — kiểm token màu/độ tương phản và luồng dữ liệu, in từng
dòng `[ĐẠT ]` hoặc `[HỎNG]`, kết thúc bằng `=== KẾT QUẢ: … ===`.

> ⚠️ **Trạng thái thật ngày 2026-11-02: HỎNG — 36 mục** (ví dụ `nhấn (lime) / nền 1.21:1`,
> `ok / card 3.40:1`, `chip cảnh báo 2.51:1`). Nguyên nhân: bảng màu app đã đổi sang **nền sáng** (v5)
> nhưng các cặp "lime làm CHỮ" vẫn giữ từ bản nền tối — lime trên nền sáng chỉ ~1,2:1.
> Việc phải làm: đổi mọi chỗ dùng lime/mint làm **chữ** sang mực đậm (giống cách đã sửa ở web:
> `--lab-accent-ink`, `--lab-ok-ink`…), rồi chạy lại. **Chưa sửa xong** — đừng coi task này là gate xanh.

---

## 3. APP ANDROID — build và chạy trên máy ảo

### 3.1 Build ra tệp APK

```powershell
cd apps\kmp
.\gradlew.bat :composeApp:assembleDebug
```

Ra tệp: `apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk` (≈ 13 MB).
`debug` = bản gỡ lỗi, ký bằng khóa debug, cài trực tiếp được (không phải bản phát hành Play Store).

### 3.2 Bật máy ảo

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\emulator\emulator.exe" -list-avds                        # xem máy ảo có sẵn
& "$sdk\emulator\emulator.exe" -avd Medium_Phone_API_37.0 `
    -gpu swiftshader_indirect -no-audio -no-boot-anim
```

| Tham số | Nghĩa |
|---|---|
| `-avd <tên>` | chọn máy ảo (máy này có `Medium_Phone_API_37.0`, `Small_Phone`) |
| `-gpu swiftshader_indirect` | vẽ bằng phần mềm — **bắt buộc** trên máy này: `-gpu auto` từng gây `Fatal signal 6 (SIGABRT)` |
| `-no-audio` | tắt âm thanh (tránh lỗi driver âm thanh) |
| `-no-boot-anim` | bỏ hiệu ứng khởi động cho nhanh |

Kiểm máy ảo đã lên: `& $adb devices` phải thấy `emulator-5554  device`.

### 3.3 Cài + chạy app trên máy ảo

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = "apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk"

& $adb reverse tcp:8069 tcp:8069          # máy ảo thấy Odoo của máy thật ở 127.0.0.1:8069
& $adb install -r $apk
& $adb shell am start -n com.applogictics/vn.lab.logistics.MainActivity
```

Trong app: tab **Đăng nhập** → **Máy chủ Odoo** = `http://127.0.0.1:8069` · DB `odoo17_lab`.

> Vì sao cần `adb reverse`: máy ảo là một máy tính "ảo" có IP riêng; `localhost` bên trong máy ảo là
> chính nó. `adb reverse tcp:8069 tcp:8069` tạo một đường hầm: cổng 8069 **của máy ảo** → cổng 8069
> **của máy thật**. (Máy ảo này còn chặn cả `10.0.2.2` với tiến trình app — xem `workflow/runbook.md` §1d.)

---

## 4. CÀI APP LÊN MÁY THẬT QUA CÁP USB-C

Máy đang dùng: **Samsung Galaxy Tab A11 (SM-X133)**, số serial `R8YL407E9XB`.

### 4.1 Chuẩn bị trên máy tính bảng (làm 1 lần)

1. **Cài đặt → Giới thiệu điện thoại → Thông tin phần mềm** → bấm **Số bản dựng** 7 lần → có *Tuỳ chọn
   nhà phát triển*.
2. **Cài đặt → Tuỳ chọn nhà phát triển** → bật **Gỡ lỗi USB** (USB debugging).
3. Cắm cáp USB-C, chọn chế độ **Truyền tệp (MTP)** nếu máy hỏi.
4. Trên màn hình máy tính bảng hiện hộp thoại *"Cho phép gỡ lỗi USB?"* → bấm **Cho phép**
   (nên tích *Luôn cho phép từ máy tính này*).

### 4.2 Kiểm máy tính đã thấy máy chưa

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```

Ba trạng thái có thể gặp:

| Kết quả | Nghĩa | Xử lý |
|---|---|---|
| `R8YL407E9XB  device` | **đã sẵn sàng** | làm tiếp 4.3 |
| `R8YL407E9XB  unauthorized` | máy chưa tin máy tính này | bấm **Cho phép** trên máy tính bảng (mở khoá màn hình cho hộp thoại hiện) |
| không có dòng nào / `offline` | cáp lỗi, chế độ USB sai, hoặc driver | đổi cáp/ cổng, chọn lại chế độ MTP, `adb kill-server` rồi `adb devices` |

### 4.3 Build và cài (lệnh chính)

```powershell
cd apps\kmp
.\gradlew.bat :composeApp:assembleDebug
cd ..\..

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = "apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk"

& $adb -s R8YL407E9XB install -r $apk
```

| Mảnh lệnh | Nghĩa |
|---|---|
| `-s R8YL407E9XB` | chọn **đúng thiết bị** (khi cắm nhiều máy thì bắt buộc; nếu chỉ 1 máy có thể bỏ) |
| `install` | cài gói APK lên máy |
| `-r` | *reinstall*: cài đè, **giữ dữ liệu** và cấu hình cũ của app |
| `$apk` | đường dẫn tệp APK vừa build |

Kết quả đúng: `Performing Streamed Install` → `Success`.
Nếu báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ⇒ bản cài trên máy khác chữ ký: gỡ trước rồi cài lại:
`& $adb -s R8YL407E9XB uninstall com.applogictics`.

### 4.4 Mở app

```powershell
& $adb -s R8YL407E9XB shell am start -n com.applogictics/vn.lab.logistics.MainActivity
```

| Mảnh | Nghĩa |
|---|---|
| `shell` | chạy lệnh **bên trong** máy tính bảng (không phải trên Windows) |
| `am start` | *Activity Manager*: mở một màn hình của app |
| `com.applogictics/...MainActivity` | `applicationId` **/** tên đầy đủ của Activity khởi động |
| `-n` | chỉ đích danh *component* cần mở (không qua màn hình chờ) |

### 4.5 Cấu hình địa chỉ máy chủ trong app (quan trọng)

Trong app → tab **Đăng nhập** → ô **Máy chủ Odoo**:

- **Máy tính bảng thật (qua Wi-Fi):** `http://192.168.1.20:8069` ← IP LAN của máy tính đang chạy Odoo.
  Xem lại IP bằng: `Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -like "192.168.*"`
- **Máy ảo:** `http://127.0.0.1:8069` (kèm `adb reverse`, xem §3.3)

> ⚠️ Trên **máy tính bảng này `adb reverse` không hoạt động** (đã đo: `nc 127.0.0.1 8069` từ tablet
> không trả gì, còn `nc 192.168.1.20 8069` thì thông) ⇒ app phải trỏ IP LAN. Nếu IP máy tính đổi,
> sửa lại ô này. Máy tính bảng và máy tính phải **cùng một Wi-Fi**.

Tài khoản demo (mật khẩu chung `lab@2026`): `taixe01`…`taixe05` · `nhomtruong01` · `cs01` · `kho01` ·
`chungtu01` · `ketoan01` · `manager01` · `sep` · riêng `admin`/`admin`.

### 4.6 Chụp màn hình app (để làm bằng chứng / tài liệu)

```powershell
& $adb -s R8YL407E9XB shell screencap -p /sdcard/s.png
& $adb -s R8YL407E9XB pull /sdcard/s.png docs\review\ten-anh.png
```

| Mảnh | Nghĩa |
|---|---|
| `screencap -p` | chụp ảnh màn hình, `-p` = PNG |
| `/sdcard/s.png` | lưu **trên máy tính bảng** |
| `pull` | kéo tệp từ máy tính bảng về máy tính |

> **Đừng** dùng `adb exec-out screencap -p > file.png` trong PowerShell: dấu `>` ghi theo kiểu văn bản
> ⇒ tệp PNG hỏng, mở không được. Luôn `screencap` rồi `pull`.

### 4.7 Gỡ app / xem log

```powershell
& $adb -s R8YL407E9XB uninstall com.applogictics                     # gỡ app
& $adb -s R8YL407E9XB logcat -s AppLogictics                         # xem log của app (Ctrl+C để dừng)
& $adb -s R8YL407E9XB shell am force-stop com.applogictics           # tắt hẳn app
```

### 4.8 (Nâng cao) Đặt sẵn cấu hình, khỏi gõ tay trên máy

App lưu cấu hình trong `SharedPreferences` tên `applogictics_session`. Có thể ghi thẳng khi app đã tắt:

```powershell
& $adb -s R8YL407E9XB shell am force-stop com.applogictics
# tệp .tmp\applogictics_session.xml chứa baseUrl/db/login như mẫu dưới
& $adb -s R8YL407E9XB push .tmp\applogictics_session.xml /data/local/tmp/al_prefs.xml
& $adb -s R8YL407E9XB shell run-as com.applogictics cp /data/local/tmp/al_prefs.xml shared_prefs/applogictics_session.xml
```

Mẫu nội dung `applogictics_session.xml`:

```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="realDataSource">true</string>
    <string name="canSwitch">false</string>
    <string name="baseUrl">http://192.168.1.20:8069</string>
    <string name="db">odoo17_lab</string>
    <string name="login">nhomtruong01</string>
</map>
```

`run-as` = chạy lệnh với quyền của **chính app** (chỉ app gỡ lỗi mới cho phép) — nhờ vậy ghi được vào
thư mục riêng của app mà không cần root.

---

## 5. Lỗi thường gặp → cách xử lý

| Hiện tượng | Nguyên nhân | Xử lý |
|---|---|---|
| `docker: permission denied ... npipe` | Docker Desktop chưa chạy / phiên shell không đủ quyền | mở Docker Desktop, đợi "Engine running" |
| Web 500 / trắng trang | module lỗi sau khi sửa code | `logs --tail 100 odoo`, sửa, chạy lại `-u` + `restart` |
| Đã sửa CSS/JS mà web không đổi | bundle cũ trong DB + trình duyệt | xoá `/web/assets/%` + `restart odoo` + **Ctrl+F5** |
| Vừa cấp quyền mà không có tác dụng | registry cũ trong RAM | `restart odoo` |
| `more than one device/emulator` | cắm cả máy ảo và máy thật | thêm `-s <serial>` vào mọi lệnh adb |
| `unauthorized` | chưa bấm Cho phép trên máy | mở khoá máy, bấm **Cho phép** |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | khác chữ ký (bản Play/ bản máy khác) | `adb uninstall com.applogictics` rồi cài lại |
| App báo "Không kết nối được Odoo" | sai địa chỉ máy chủ | máy ảo: `127.0.0.1` + `adb reverse`; máy thật: IP LAN |
| App tài xế "0 việc" | dữ liệu demo cũ ngày | `reset-ngay-demo.ps1` |
| Gradle `Access is denied ... gradle-9.8.2-bin.zip.lck` | phiên chạy bị sandbox chặn ghi `%USERPROFILE%\.gradle` | chạy lại khi có quyền ghi, hoặc kiểm tra ổ đĩa còn chỗ |
| `-gpu auto` làm máy ảo crash (`Fatal signal 6`) | lỗi đồ hoạ của AVD | dùng `-gpu swiftshader_indirect` |

---

## 6. Bảng tóm tắt — chỉ cần nhớ 6 lệnh

```powershell
# WEB
docker compose -f infra/docker-compose.yml up -d                 # bật Odoo → http://localhost:8069

# DESKTOP KOTLIN
cd apps\kmp; .\gradlew.bat :composeApp:run                        # mở app desktop

# APP ANDROID (build)
cd apps\kmp; .\gradlew.bat :composeApp:assembleDebug              # ra composeApp-debug.apk

# CÀI LÊN MÁY THẬT QUA CÁP USB-C
& $adb -s R8YL407E9XB install -r apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk
& $adb -s R8YL407E9XB shell am start -n com.applogictics/vn.lab.logistics.MainActivity

# CHỤP MÀN HÌNH TỪ MÁY
& $adb -s R8YL407E9XB shell screencap -p /sdcard/s.png; & $adb -s R8YL407E9XB pull /sdcard/s.png docs\review\anh.png
```

Liên quan: `workflow/runbook.md` (cổng, log, sự cố đã đo), `workflow/flow-app-vs-web.md` (bước nào làm
ở app, bước nào ở web), `workflow/demo-dataset.md` (dữ liệu demo).
