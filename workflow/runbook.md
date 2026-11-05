# RUNBOOK — cổng, lệnh, cách xem kết quả

> **Người mới bắt đầu ở đây:** `workflow/chay-du-an.md` — giải thích từng lệnh (web, desktop Kotlin,
> app Android, cài qua cáp USB-C) kèm ý nghĩa từng tham số và lỗi thường gặp. File này là bản tra cứu
> sự cố đã đo được.
>
> **Đây là câu trả lời cho "cổng nào để xem kết quả".** Không phải chat — chat sẽ mất.
> Mọi agent **bắt buộc** cập nhật file này khi mở server / chạy app (DoD D8, tín hiệu FDE F8 "writes the handover").

---

---

## 0. MỘT LỆNH — không cần làm thủ công

```powershell
cd "C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics"
powershell -ExecutionPolicy Bypass -File infra\scripts\setup.ps1
```

Script **idempotent** — chạy lại nhiều lần không hỏng, bước nào xong thì bỏ qua. Nó làm 8 bước:

| # | Bước | Tự xử lý |
|---|---|---|
| 1 | Kiểm môi trường | Docker chưa bật → dừng với thông báo rõ |
| 2 | Bật Postgres + Odoo | đợi `pg_isready` (tối đa 120s) |
| 3 | Vendor OCA | có rồi thì **SKIP**; thiếu thì `git clone`, fail thì **tải ZIP** |
| 4 | Nạp lại `addons_path` | `restart odoo` + đợi `/web/login` trả 200 |
| 5 | Cài module | OCA trước, rồi `lab_*`; đã cài thì SKIP (ép: `-Reinstall`) |
| 6 | Chạy test suite | đọc `odoo.tests.result`, in FAIL/ERROR nếu có |
| 7 | Kiểm tĩnh | `check.ps1` + `audit-native-first.ps1` |
| 8 | App KMP | `selfCheck` + `desktopTest` (offline) |

Cuối cùng in **bảng tổng hợp** và `exit 0/1` — dùng được cho CI.

Cờ hữu ích: `-SkipOdoo` · `-SkipApp` · `-OdooOnly` · `-Reinstall`.

## 1. Dịch vụ của dự án

| Dịch vụ | Cổng | Cách chạy | Kiểm tra sống | Ai ghi | Trạng thái |
|---|---|---|---|---|---|
| **Odoo 17** (web UI + API) | **8069** | `docker compose -f infra/docker-compose.yml up -d odoo` | `http://localhost:8069/web/login` trả 200 | BE-01 | ✅ đã khai |
| **PostgreSQL** | **5432** | `docker compose ... up -d db` | `pg_isready -U odoo` | BE-01 | ✅ đã khai |
| **App KMP (desktop)** | **không mở cổng** | `cd apps/kmp; .\gradlew.bat :composeApp:run` → màn **Giới thiệu (Welcome/hero)** → "Vào app" → Đăng nhập → Tài xế. Thanh demo: logo · 3 tab · kịch bản dữ liệu · **Giảm chuyển động** · **Nền tối** · thanh trượt bề rộng 1280/900 || ✅ đã xác nhận 10/2026 |
| **Landing / trang giới thiệu** | không cần server | mở `demo\landing\index.html` bằng double-click (chạy `file://`) | trang hiện hero 3D + logo, cuộn được | Orchestrator | ✅ |
| App KMP — chế độ **Odoo thật** | không mở cổng | app gọi `http://localhost:8069`: `/web/session/authenticate` (cookie) → `/lab/api/v1/auth/login` (token) → `/lab/api/v1/driver/jobs` | bật công tắc “Nguồn dữ liệu: Odoo thật”, đăng nhập `taixe01`/`lab@2026` → thấy `LH/26/0005`, `LH/26/0006` | FE-01 | ✅ |
| App KMP — bản đóng gói | không có cổng | `./gradlew :composeApp:runDistributable` | app chạy không cần JDK ngoài | FE-01 | ✅ |
| Remotion Studio (dựng video) | 3000 *(mặc định)* | `npx remotion studio demo/` | `http://localhost:3000` | RV-01 | chỉ khi làm video |
| Playwright (quay demo) | không có cổng | `node demo/capture/record-demo.mjs` | file mp4 trong `demo/capture/raw/` | RV-01 | không cần server |
| **Kiểm chứng capture E2E** (script) | không có cổng | `powershell -ExecutionPolicy Bypass -File infra\scripts\e2e-capture.ps1` · chạy lại: thêm `-Restore -JobId 5` trước | in `=== KET QUA: DAT ===`; kiểm cả `duplicate=true` và 4 câu SQL | Orchestrator | ✅ 10/2026 |
| **Theme Odoo** (`lab_ui`) | không có cổng | `docker compose -f infra/docker-compose.yml run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_ui --stop-after-init` → `restart odoo` | `/web/login` nền giấy ấm, nút **xanh rừng `#1F4A3D`** (KHÔNG còn tím `#71639e`); logo công ty hiện thật | FE-01 (ADR-007) | ✅ 10/2026 |
| **Chụp ảnh web** (nghiệm thu UI) | không có cổng | `powershell -File infra\scripts\shot.ps1 -Url <url> -Out <png> [-Width 1440 -Height 900]` · mobile thật: `-Width 390 -Height 844 -Scale 2` | in `[OK] … PNG header OK`; mở file bằng `read_image` | Orchestrator | ✅ 10/2026 |
| **Deploy + smoke "Chuyển tài khoản"** | không có cổng | `pwsh -File infra\scripts\deploy-admin-switch.ps1` (đã gồm `-u lab_core,lab_api` + restart + smoke) | in `=== XONG ===`; `GET /admin/accounts` = 200 (10 dòng), `POST /admin/switch` = 200 (`roles=driver`) | BE-01 + Orchestrator | ✅ 2026-10-29 |
| **Smoke API chỉ HTTP** (không docker) | không có cổng | `powershell -File infra\scripts\smoke-admin-switch.ps1` | in `=== KET QUA: TAT CA DAT ===` (26 `[OK]`); cần **cookie phiên** — route `lab_api` là `auth="user"` | Orchestrator | ✅ 2026-10-29 |
| **Bộ dữ liệu mẫu LỚN** (305 việc · 26 chuyến · 12 hành trình · 149 đơn · 164 bút toán) | không có cổng | `powershell -File infra\scripts\seed-demo-lon.ps1` (`-Reset` để tạo lại từ đầu) | in `=== DA COMMIT ===` + bảng đếm; kiểm bằng `infra\scripts\verify-demo-lon.ps1` (`TONG` xanh, `job thieu kien` 10, `DEBIT` 30) | Orchestrator | ✅ 2026-10-29 |
| **Kiểm dữ liệu lớn** (chỉ đọc, không docker) | không có cổng | `powershell -File infra\scripts\verify-demo-lon.ps1` | in bảng đếm + KPI `pendingAssign/enRoute/pickedUpToday/confirmedToday/avgLoadPct` | Orchestrator | ✅ 2026-10-29 |
| **Chụp chuỗi chuyển vai trò (dropdown)** | không có cổng | `powershell -File infra\scripts\app-switch-demo.ps1` | in `KET LUAN`: `admin` → `Quản trị viên ▾` · `taixe01` → `Tài xế ▾` (vẫn còn) · `cs01` → `CS ▾` · login trực tiếp `taixe01` → **rỗng**; ảnh `docs/review/app-switch-1..5-*.png` | Orchestrator | ✅ 2026-10-29 |

### 1b. Kiểm chứng **luồng thật** không qua UI (nhanh nhất, đáng tin nhất)

Ba script dưới đây trả lời "có chạy thật không" mà không cần bấm chuột. Chạy theo thứ tự:

```powershell
cd "C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics"
powershell -ExecutionPolicy Bypass -File infra\scripts\setup.ps1                       # 1. dựng + test + kiểm tĩnh
powershell -ExecutionPolicy Bypass -File infra\scripts\e2e-capture.ps1                 # 2. API capture từ đầu đến cuối
powershell -ExecutionPolicy Bypass -File infra\scripts\e2e-capture.ps1 -Restore -JobId 5   # 3. dọn để chạy lại
```

`e2e-capture.ps1` chứng minh 5 điều, mỗi điều một dòng `[OK]`: đăng nhập tài xế → token · job `picked_up` ·
**gửi lại cùng `clientUuid` trả `duplicate=true`** · job rời danh sách việc đang làm · DB khớp
(`picked_up|5|42.5|<uuid>|true`, đúng 1 ảnh + 1 chữ ký, chatter có tin nhắn).

### 1c. App Android (APK) trên máy ảo — đã chạy thật 10/2026

| Việc | Lệnh | Kiểm tra sống |
|---|---|---|
| Build APK | `cd apps\kmp; .\gradlew.bat :composeApp:assembleDebug` (cần mạng lần đầu: AGP 9.4.1) | `composeApp\build\outputs\apk\debug\composeApp-debug.apk` (~12,5 MB) |
| Bật máy ảo | `& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_7 -no-snapshot-load -gpu auto` | `adb devices` thấy `emulator-5554`; `adb shell getprop sys.boot_completed` = `1` |
| Cài + mở | `adb install -r <apk>` · `adb shell monkey -p com.applogictics -c android.intent.category.LAUNCHER 1` | `adb shell "dumpsys activity activities \| grep topResumedActivity"` → `com.applogictics/vn.lab.logistics.MainActivity` |
| Chụp màn hình | `adb shell screencap -p /sdcard/s.png` + `adb pull /sdcard/s.png docs/review/<ten>.png` | mở file PNG được |

**Ba cái bẫy đã trúng thật, đừng lặp lại:**
1. Máy ảo gọi Odoo qua **`http://10.0.2.2:8069`** (không phải `localhost`) → manifest phải có `usesCleartextTraffic="true"`.
2. **Không** dùng `adb exec-out screencap -p > file.png` trong PowerShell: `>` ghi text, PNG hỏng (`read_image` báo "malformed"). Dùng `screencap` trên máy rồi `adb pull`.
3. Ghi đè lên `gradle.properties`: phải giữ `android.useAndroidX=true`, `android.newDsl=false`, `android.builtInKotlin=false` — thiếu `newDsl=false` thì AGP 9 từ chối `androidTarget()`.

### 1d. Máy ảo này CHẶN `10.0.2.2` với tiến trình app — dùng `adb reverse`

Đo thật 10/2026 trên AVD `Pixel_7` (Android 17/API 37). **Cùng một máy ảo**, khác UID:

| Kiểm tra | Shell (uid 2000) | App (`run-as com.applogictics`) |
|---|---|---|
| `ping 10.0.2.2` | OK | **100% packet loss** |
| `nc 10.0.2.2 8069` | `HTTP/1.0 200 OK` | **Timeout** |
| `nc 127.0.0.1 8069` (qua `adb reverse`) | OK | **OK** |

Log app (`adb logcat -s AppLogictics`) khi đó: `ConnectTimeoutException … from /10.0.2.16 after 5000ms`,
trong khi `cleartextChoHost=true`, proxy `DIRECT`, quyền `INTERNET: granted=true` — nên **không phải lỗi cleartext/quyền/code**.
Máy ảo có 2 interface (`wlan0 10.0.2.16` của app, `eth0 10.0.2.15` của shell); tắt Wi-Fi cũng không đổi.

**Cách chạy app thật trên máy ảo (đã chứng minh):**
```powershell
adb reverse tcp:8069 tcp:8069        # máy ảo 127.0.0.1:8069 -> host :8069
# trong app: Đăng nhập -> Máy chủ Odoo = http://127.0.0.1:8069 (giữ DB odoo17_lab)
```
`adb reverse` đi qua kênh adb nên **không phụ thuộc** interface mạng của app — đây là đường demo ổn định.

### 1e. Máy tính bảng THẬT (Samsung SM-X133, cáp USB-C) — đã cài thật 2026-11-02

| Việc | Lệnh | Ghi chú đo được |
|---|---|---|
| Nhận máy | `adb devices -l` | `R8YL407E9XB  device  product:gta11wifidx model:SM_X133` — lần đầu hiện `unauthorized`, phải bấm **Cho phép gỡ lỗi USB** trên máy |
| Cài | `adb -s R8YL407E9XB install -r apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk` | `Success` |
| Mở | `adb -s R8YL407E9XB shell am start -n com.applogictics/vn.lab.logistics.MainActivity` | `mCurrentFocus=com.applogictics/…MainActivity` |
| Chụp | `adb -s <serial> shell screencap -p /sdcard/s.png` rồi `adb pull` | xem `docs/review/tablet-*.png` |

**Khác máy ảo ở một điểm quyết định: `adb reverse` KHÔNG chạy trên máy này.**
Đo thật: `adb reverse tcp:8069 tcp:8069` báo OK, nhưng từ tablet
`printf 'GET /web/login HTTP/1.0\r\n\r\n' | nc 127.0.0.1 8069` **không trả gì**, trong khi
`nc 192.168.1.20 8069` trả `HTTP/1.0 500` (tức là **thông** — 500 chỉ vì request tay thiếu header).
⇒ Trong app, màn **Đăng nhập → Máy chủ Odoo** phải là **`http://192.168.1.20:8069`** (LAN), DB `odoo17_lab`.
Nếu IP LAN đổi, xem lại bằng `Get-NetIPAddress -AddressFamily IPv4`.

### 1f. Dữ liệu demo "bị cũ ngày" — dời về hôm nay bằng 1 lệnh

Seed neo cứng `hom_nay = 2026-10-29`, còn app tài xế **chỉ trả việc có khung giờ hôm nay** ⇒ sau vài ngày
mở app thấy **0 việc**. Đo 2026-11-02: trước khi dời = `0` việc hôm nay; sau khi dời = **65**
(`5 Mới · 20 Đã gán · 10 Đang đến lấy · 16 Đã lấy được hàng`).

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\reset-ngay-demo.ps1 -DryRun   # xem trước
powershell -ExecutionPolicy Bypass -File infra\scripts\reset-ngay-demo.ps1           # áp dụng
```

Script dời `khung_gio_tu/khung_gio_den/ngay_giao/ngay_xac_nhan` (việc), `ngay_di/ngay_den_du_kien/cut_off_datetime`
(chuyến), `ngay_bay/ngay_den/pod_date` (hành trình) đi đúng `(hôm nay − 2026-10-29)` ngày.
**Bẫy SQL**: `timestamp + <n>` không hợp lệ — phải `+ interval '<n> days'` (chỉ `date` mới cộng số nguyên).

---

## 2. Vì sao app KMP **không** có cổng — đọc kỹ

Đây là chỗ hay hiểu nhầm:

- **App desktop là client**, không phải server. Nó **mở kết nối ra** tới Odoo (`http://localhost:8069`), chứ không mở cổng nào để bạn vào bằng trình duyệt.
- Muốn "xem kết quả" → **nhìn cửa sổ app**, không nhìn trình duyệt.
- Nếu app **có** server nội bộ (ví dụ một mock API tạm để FE làm trước khi BE xong) → **bắt buộc thêm dòng vào bảng §1** với cổng cụ thể. Cổng mặc định khi đó: **8080** (mock), và phải ghi rõ "mock — tắt trước khi demo".

**Cách kiểm tra app KMP đang nói chuyện với Odoo hay không:**
```powershell
# log của app phải có request tới 8069; nếu không, kiểm tra biến môi trường base URL
cd apps/kmp; ./gradlew :composeApp:run --info | Select-String "8069|call_kw|authenticate"
```

---

## 3. Mẫu để agent điền (copy khi mở dịch vụ mới)

```markdown
| <tên> | <cổng> | `<lệnh đầy đủ>` | `<cách kiểm tra>` | <role> | <ok/mock/tạm> |
```

**Điều kiện bắt buộc:**
- Có **lệnh đầy đủ** copy-paste được (không "chạy gradle như bình thường").
- Có **cách kiểm tra** cụ thể (URL trả 200, file sinh ra, cửa sổ mở…).
- Ghi rõ `mock` nếu là tạm — vì demo **không** được chạy trên mock (Luật 9).
- Cổng phải **không đụng** cổng đã có trong bảng.

---

## 4. Câu hỏi → trả lời nhanh

| Bạn muốn xem | Mở cái gì |
|---|---|
| Giao diện Odoo, màn hình chuyến/picking/Debit | `http://localhost:8069` (user demo: xem `infra/scripts/seed-users.md`) |
| App tài xế / CS console | chạy `./gradlew :composeApp:run` → cửa sổ desktop |
| Trang khách tra cứu (track) | `http://localhost:8069/lab/track/<access_token>` |
| Dashboard cho sếp | menu *Dashboard* trong Odoo (spreadsheet dashboard) |
| Kết quả test Odoo | terminal: `odoo … --test-tags=/lab_chuyen` → dòng `0 failed, 0 error(s)` |
| Video demo | `demo/out/applogictics-demo.mp4` |
| Dữ liệu dựng lại từ đầu | `powershell -File infra/scripts/reset-demo.ps1` |
| **Luồng nghiệp vụ đầy đủ để test** (14 chặng) | `workflow/full-flow.md` — mỗi chặng có: ai bấm · ở đâu · trạng thái trước→sau · chứng từ sinh ra · câu SQL kiểm |

---

## 5. Sự thật về việc hỏi agent

Bạn **có thể** hỏi một agent sau khi nó xong (agent vẫn tồn tại ở trạng thái resumable và sẽ trả lời ở lượt mới). Nhưng:

| Cách | Được | Mất |
|---|---|---|
| Hỏi agent qua chat | nhanh, có ngữ cảnh | câu trả lời **biến mất** sau phiên; 2 tuần sau không ai biết |
| Đọc `workflow/runbook.md` | bền, cả nhóm thấy | phải viết 1 phút |

**Luật:** trả lời **một lần** vào file, rồi lần sau chỉ vào file. Đó là lý do file này tồn tại.


---

## 6. Bootstrap môi trường app KMP (một lần)

Repo **không commit** `gradle-wrapper.jar` (file nhị phân). Lần đầu phải chạy:

```powershell
powershell -ExecutionPolicy Bypass -File apps\kmp\bootstrap.ps1
```

Script làm 5 việc: kiểm JDK 21 → đặt `JAVA_HOME` → tải Gradle 9.8.2 → sinh wrapper → chạy `gradlew --version` để xác nhận.

**Kiểm tra app không cần mở GUI:** `cd apps/kmp; .\gradlew.bat --offline :composeApp:selfCheck` → in bảng tương phản WCAG, 3 ngưỡng duo UI, lineHeight tiếng Việt, kết thúc bằng `KẾT QUẢ: ĐẠT — 0 lỗi`. Đây là gate tự động của FE, dùng được trong CI.

> **Mạng của shell agent:** mặc định KHÔNG có (lỗi `schannel: SEC_E_NO_CREDENTIALS`). **Cách xin:** gọi lệnh với `sandbox_permissions: danger-full-access` → harness hiện prompt để người dùng duyệt → lệnh đó có mạng + Docker trong 1 lần. Đã kiểm chứng 10/2026 (tải được Three.js 654 KB, `docker` đọc được daemon). Mỗi lệnh cần duyệt riêng.
> Hệ quả: **agent không tải dependency, không build được app.** Bước download phải do **người** chạy trên terminal thật.
> Vì vậy P0-05 để trạng thái `REVIEW` cho tới khi người xác nhận `JVM: 21.x` + cửa sổ app mở ra.
## Checklist nhanh
- [ ] Mọi dịch vụ đã chạy có dòng trong bảng §1
- [ ] Cổng không trùng nhau
- [ ] Lệnh khởi động copy-paste được
- [ ] Có cách kiểm tra sống (không chỉ "chạy được")
- [ ] Dịch vụ mock được đánh dấu rõ
- [ ] Cập nhật lần cuối có ngày
---

## 7. Đọc log Odoo cho dễ (không phải lỗi PowerShell)

`docker compose logs` in **log thô** của container. Pipe qua PowerShell thì mất màu → trông như "chữ trơn một màu".
Odoo có cron chạy **mỗi phút** nên log tự chảy liên tục — bình thường, không phải treo.

```powershell
docker compose -f infra/docker-compose.yml logs --tail=60 odoo                              # 60 dòng cuối
docker compose -f infra/docker-compose.yml logs -f odoo                                     # realtime (Ctrl+C thoát)
docker compose -f infra/docker-compose.yml logs odoo | Select-String 'ERROR|CRITICAL'       # chỉ lỗi
docker compose -f infra/docker-compose.yml logs odoo > docs/review/odoo.log                 # ghi file để gửi
```

| Dòng log | Nghĩa |
|---|---|
| `Starting job 'Base: Auto-vacuum...'` | cron Odoo, mỗi phút — bình thường |
| `time zone "Asia/Saigon" not recognized` | user có tz cũ; `setup.ps1` bước 6b tự sửa |
| `WARNING ... no _description` | model thiếu `_name`/`_description` — dấu hiệu class `_inherit` viết sai |