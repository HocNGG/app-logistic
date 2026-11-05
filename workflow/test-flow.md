# LUỒNG TEST TOÀN BỘ APP — ai làm gì, ở đâu, kiểm bằng gì

> Bản cập nhật sau khi thêm role **Trưởng nhóm tài xế** (người phân công) và công cụ
> **Chuyển tài khoản** của `admin` (nhảy role để test nhanh, chỉ admin thấy).
> Mọi con số trong bảng là **đã đo trên máy này** (2026-10-29). Sai lệch ⇒ sửa file này, đừng sửa niềm tin.

> **Luồng đầy đủ 14 chặng (shop → kho → máy bay → giao khách → xác nhận)**: `workflow/full-flow.md`
> Bộ dữ liệu lớn để test ngay: `workflow/demo-dataset.md`

---

## 0. Chuẩn bị (5 phút) — không cần role

| # | Việc | Lệnh / đường dẫn | Kết quả đúng |
|---|---|---|---|
| 0.1 | Dựng toàn bộ | `powershell -File infra\scripts\setup.ps1` | in bảng PASS, `exit 0` |
| 0.2 | Odoo sống | http://localhost:8069/web/login | 200, nền giấy ấm, nút **xanh rừng** |
| 0.3 | `adb` (nếu chưa có PATH) | `$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"` | `adb version` in ra phiên bản |
| 0.4 | Cài APK | `adb install -r apps\kmp\composeApp\build\outputs\apk\debug\composeApp-debug.apk` | `Success` |
| 0.5 | **Bắt buộc cho app trên máy ảo** | `adb reverse tcp:8069 tcp:8069` | `adb reverse --list` thấy `tcp:8069` |
| 0.6 | (tuỳ chọn) reset demo | `powershell -File infra\scripts\reset-demo.ps1` | 5 việc `LH/26/000x` + 2 chuyến + user demo |
| 0.7 | Test tự động (bằng chứng nền) | `docker compose -f infra/docker-compose.yml run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab --test-tags=/lab_core,/lab_chuyen,/lab_laixe,/lab_hanhtrinh,/lab_api --stop-after-init` | `0 failed, 0 error(s)` |
| 0.8 | Deploy + smoke **Chuyển tài khoản** (chỉ khi vừa sửa `lab_core`/`lab_api`) | `docker compose -f infra/docker-compose.yml run --rm --no-deps odoo odoo -c /etc/odoo/odoo.conf -d odoo17_lab -u lab_core,lab_api --stop-after-init` rồi `docker compose -f infra/docker-compose.yml restart odoo` | container `Started`, `/web/login` = 200 |
| 0.9 | Smoke API **bằng script** (không cần bấm) | `powershell -File infra\scripts\smoke-admin-switch.ps1` | in `=== KET QUA: TAT CA DAT ===` (26 dòng `[OK]`): 10 tài khoản, switch → `roles=driver`, sau switch 403 |
| 0.10 | **Bộ dữ liệu mẫu LỚN** (305 việc · 26 chuyến · 149 đơn · 164 bút toán · 6 nhánh ngoại lệ) | `powershell -File infra\scripts\seed-demo-lon.ps1` (thêm `-Reset` để xoá và tạo lại) | in `=== DA COMMIT ===` + bảng đếm; kiểm lại bằng `infra\scripts\verify-demo-lon.ps1`. Chi tiết: `workflow\demo-dataset.md` |

> Máy ảo này **chặn `10.0.2.2` với tiến trình app** ⇒ trong app dùng `http://127.0.0.1:8069` + `adb reverse`.

### Tài khoản (mật khẩu chung `lab@2026`, riêng `admin` = `admin`)

| Vai | Tài khoản | Vào bằng | Màn đầu tiên app tự mở |
|---|---|---|---|
| **Quản trị viên (công cụ test)** | **`admin`** — mật khẩu **`admin`** | **app** + web | **Chuyển tài khoản** (chọn 1 trong 10 tài khoản → **Chuyển**) |
| Tài xế | `taixe01` `taixe02` `taixe03` | **app** | Tài xế · Việc hôm nay |
| Tài xế (thêm) | `taixe04` `taixe05` | **app** | Tài xế · Việc hôm nay (14 và 12 việc — nhờ bộ dữ liệu lớn) |
| **Trưởng nhóm tài xế** | **`nhomtruong01`** | **app** (web: chưa cấp menu — xem §6) | **Phân công tài xế** |
| CS tiếp nhận | `cs01` | **app** + web | CS · Điều phối |
| Kho | `kho01` | **app** + web | Kho · Nhận hàng |
| Chứng từ | `chungtu01` | **app** + web | Kho · Nhận hàng (kế thừa nhóm kho) |
| Kế toán | `ketoan01` | **app** + web | "Vai trò này làm việc trên Odoo web" |
| Quản lý | `manager01` | **app** + web | CS · Điều phối (có cả vai trò CS) |
| Ban giám đốc | `sep` | **app** + web | **Tổng quan** (dashboard) |

> App chọn màn theo `roles` từ `/lab/api/v1/auth/login`, ưu tiên **admin → tài xế → trưởng nhóm tài xế → CS → kho → dashboard**.
>
> **Nút chuyển vai trò CHỈ hiện với phiên của admin hoặc phiên do admin chuyển sang** — điều kiện duy nhất là `canSwitch` từ server (`/auth/login` và `/admin/switch` đều trả khoá này), KHÔNG suy từ `roles` (suy từ roles thì nút mất ngay sau lần chuyển đầu). App hiện nút ở **thanh trên cùng** (điện thoại) và **chân sidebar** (màn rộng); bấm 1 dòng trong menu là chuyển ngay, không cần mật khẩu, và **nút vẫn còn** ở màn mới.
>
> Cơ chế phía server: `/admin/switch` ghi `session["lab_switch_nguon"] = <uid admin gốc>` **trước** khi xoay `sid`; `helpers.duoc_phep_chuyen()` cho phép khi (a) là admin thật **hoặc** (b) có dấu đó **và** uid nguồn vẫn còn là admin. `/auth/login` **xoá dấu** ⇒ đăng nhập trực tiếp `taixe01` không có quyền chuyển (server trả **403**, không chỉ ẩn nút). Không chuyển được **sang** `admin` (muốn quay lại admin: Đăng xuất → đăng nhập `admin`/`admin`). Trên web: `admin` vào được mọi menu như quản trị Odoo.

---

## 1. Sơ đồ luồng tổng thể

```
            ┌──────────────┐
            │  0. CHUẨN BỊ │  setup.ps1 · adb reverse · test
            └──────┬───────┘
                   ▼
   ┌──────────────────────────────────────────────┐
   │ 0b. ADMIN (admin) — APP · Chuyển tài khoản   │  mở dropdown "Chọn tài khoản ▾" (hoặc bấm
   └──────┬───────────────────────────────────────┘  dòng trong danh sách) → [Chuyển] → app ghi
          │                                          phiên mới + mở đúng màn của role đó
          ├──────────────────────────────────────────► (nhảy thẳng tới bước 1b/2/4/5 tuỳ tài khoản)
          ▼
   ┌──────────────────────────────────────┐
   │ 1. CS (cs01) — WEB                   │  tạo/kiểm yêu cầu lấy hàng, xem danh sách
   └──────┬───────────────────────────────┘  (state new)
          ▼
   ┌──────────────────────────────────────────────┐
   │ 1b. TRƯỞNG NHÓM TÀI XẾ (nhomtruong01) — APP  │  "Phân công tài xế": chọn việc → chọn
   └──────┬───────────────────────────────────────┘  "Trần Văn Hùng · taixe01" → [Gán tài xế]
          │                                          new/assigned ──► assigned
          ▼                                          (ghi driver_id + driver_user_id)
   ┌──────────────────────────────────────┐
   │ 2. TÀI XẾ (taixe01) — APP            │  Việc hôm nay (CHỈ việc của mình)
   └──────┬───────────────────────────────┘  → [Bắt đầu đi] : assigned ──► en_route
          ▼
   ┌──────────────────────────────────────┐
   │ 3. TÀI XẾ — APP · Capture 3 bước     │  Ảnh → Ký → Số kiện/kg → [Gửi & xác nhận]
   └──────┬───────────────────────────────┘  en_route ──► picked_up
          ▼                                  (+1 ảnh, +1 chữ ký, clientUuid chống trùng)
   ┌──────────────────────────────────────┐
   │ 4. KHO (kho01) — APP · Nhận hàng     │  picked_up ──► confirmed (+ phiếu WH/IN/…)
   └──────┬───────────────────────────────┘
          ▼
   ┌──────────────────────────────────────────────────────────────┐
   │ 5. XEM LẠI / BÁO CÁO                                          │
   │  5a. WEB: Yêu cầu lấy hàng (đọc trạng thái) · Chuyến (7 nút)  │
   │  5b. WEB (sep/manager01): Bảng điều khiển                     │
   │  5c. APP (sep): tab Tổng quan                                 │
   └──────────────────────────────────────────────────────────────┘
          │
          ├─ Nhánh lỗi: TÀI XẾ ── [Không lấy được] + lý do ──► failed
          │            → web menu "Yêu cầu lấy hàng thất bại"
          └─ Chuyến (web, độc lập): Chốt booking → Khởi hành → Đã đến → Đã giao → Khoá/Huỷ
```

---

## 2. Ánh xạ "tài xế" ↔ "tài khoản" — chỗ hay nhầm

Job **không** lưu tên tài khoản. Job lưu `driver_id` = **`hr.employee`**; mỗi nhân viên gắn 1 user (`hr_employee.user_id`). Khi gán, hệ thống ghi thêm cột stored `driver_user_id` (uid) để tài xế lọc được việc của mình.

| Nhân viên | Tài khoản | uid | Ghi chú |
|---|---|---|---|
| Trần Văn Hùng | `taixe01` | 12 | |
| Lê Minh Tâm | `taixe02` | 13 | |
| Phan Quốc Việt | `taixe03` | 14 | |
| Nguyễn Văn Long | `nhomtruong01` | — | **Trưởng nhóm tài xế** (đi phân công, không phải tài xế) |

**Danh sách gán trong app giờ hiện "Tên · tài khoản"** (ví dụ `Trần Văn Hùng · taixe01`) và **chỉ gồm người có nhóm tài xế** (trước đây lẫn cả `Mitchell Admin`, `Marc Demo`).

Kiểm bằng SQL (không tin lời):

```powershell
docker compose -f infra/docker-compose.yml exec -T db psql -U odoo -d odoo17_lab -c "select j.name, j.state, e.name as tai_xe, coalesce(u.login,'-') as tai_khoan from lab_yeu_cau_lay_hang j left join hr_employee e on e.id=j.driver_id left join res_users u on u.id=j.driver_user_id order by j.id;"
```

**Ai nhận được việc:** API `GET /lab/api/v1/driver/jobs` lọc `driver_user_id = current uid` (qua `ir.rule`), nên `taixe01` **chỉ** thấy việc gán cho nhân viên `Trần Văn Hùng`, không thấy việc của `taixe02/03`. Đây là bằng chứng "đúng người nhận" — kiểm bằng cách đăng nhập 2 tài khoản và so danh sách.

---

## 3. Bảng luồng test đầy đủ (≈25 phút)

| # | Bước | Role (tài khoản) | Ở đâu | Thao tác | Kết quả mong đợi |
|---|---|---|---|---|---|
| **A0** | **Đăng nhập admin** | **`admin`** (mật khẩu `admin`) | app | tab Đăng nhập → `admin` / `admin` | mở màn **Chuyển tài khoản** + nút **`Quản trị viên ▾`** ở **thanh trên cùng** (điện thoại) / chân sidebar (màn rộng) — nút này chỉ hiện khi `canSwitch` |
| **A0b** | **Dropdown · 1 click** | `admin` | app | bấm **`Quản trị viên ▾`** → menu 10 tài khoản → bấm **`taixe01`** | snackbar *"Đã chuyển sang tài khoản taixe01 — vai trò tài xế."* → màn **Tài xế**, góc trên *"Xin chào Trần Văn Hùng (tài xế)"*; **không** gõ lại mật khẩu; nút đổi thành **`Tài xế ▾`** và **vẫn còn** (ảnh `app-switch-2-menu.png`, `app-switch-3-driver.png`) |
| **A0c** | **Chuyển TIẾP khi đang là tài xế** | `taixe01` (vừa chuyển, không phải admin) | app | bấm **`Tài xế ▾`** → bấm **`cs01`** | màn **CS · Điều phối**, nút thành **`CS ▾`** — chuyển được vì phiên mang dấu *"do admin chuyển"* (`session["lab_switch_nguon"]`); server trả **200** ở cả 2 endpoint (ảnh `app-switch-4-cs.png`) |
| **A0d** | **Nút chỉ dành cho admin** | `taixe01` đăng nhập trực tiếp | app | Đăng xuất → đăng nhập `taixe01` / `lab@2026` | **KHÔNG có** nút `… ▾` (login thường trả `canSwitch=false`) — ảnh `app-switch-5-taixe-truc-tiep.png` |
| **A0e** | **Chặn quyền ở SERVER (không chỉ ẩn nút)** | `taixe01` trực tiếp | script | `powershell -File infra\scripts\smoke-admin-switch.ps1` | `canSwitch=false` · `/admin/accounts` = **403** · `/admin/switch` = **403**; còn phiên do admin chuyển: `admin→taixe01→cs01→sep` đều **200** |
| A1 | Xem landing | — | trình duyệt | mở `demo/landing/index.html` | hero video nền + panel kính, máy tính cước chạy |
| A2 | Đăng nhập web | `cs01` | web | /web/login → menu **Gom hàng lẻ** | navbar nền giấy, không tím |
| A3 | Danh sách việc | `cs01` | web | **Yêu cầu lấy hàng** | 5 việc `LH/26/000x` + chip trạng thái |
| **B0** | **Đăng nhập app** | **`nhomtruong01`** | app | tab Đăng nhập → mật khẩu → (tự chuyển) | vào thẳng **Phân công tài xế** |
| **B1** | **Xem danh sách cần gán** | `nhomtruong01` | app | màn Phân công | việc state **Mới/Đã gán** + dropdown tài xế |
| **B2** | **Xem danh sách tài xế** | `nhomtruong01` | app | mở dropdown chọn tài xế | chỉ 3 người: **Trần Văn Hùng · taixe01**, Lê Minh Tâm · taixe02, Phan Quốc Việt · taixe03 (**không** có Admin/Marc Demo) |
| **B3** | **Bấm Gán** | `nhomtruong01` | app | chọn `Trần Văn Hùng · taixe01` → **Gán tài xế** | chip → **Đã gán tài xế**; web F5 thấy cột Tài xế = Trần Văn Hùng |
| C1 | Tài xế nhận việc | `taixe01` | app | Việc hôm nay | thấy việc vừa gán; góc trên có **"Xin chào Trần Văn Hùng · taixe01"** |
| C2 | Cách ly dữ liệu | `taixe02` | app | Việc hôm nay | **không** thấy việc của `taixe01` (đúng `ir.rule`) |
| C3 | Bắt đầu đi | `taixe01` | app | mở việc → **Bắt đầu đi** | *Đang đến lấy* |
| C4–C6 | Capture | `taixe01` | app | **Chụp ảnh & ký biên nhận** → Chọn ảnh → **Ký ›** → vẽ → **Xác nhận ›** → 4 kiện / 12,5 kg → **Gửi & xác nhận** | *Đã lấy được hàng* |
| C7 | Chống trùng | `taixe01` | app/script | gửi lại cùng `clientUuid` | không tạo bộ ảnh thứ 2 (`duplicate=true`) |
| D1 | Kho nhận | `kho01` | app | **Nhận hàng** | chip *Đã nhận* + số phiếu **WH/IN/…** |
| E1 | Web xác nhận | `kho01` | web | xem lại việc | state **Đã về kho** |
| F1 | Dashboard web | `sep` | web | **Bảng điều khiển** | 4 KPI + area + donut + bảng |
| F2 | Dashboard app | `sep` | app | tab **Tổng quan** | KPI 2×2, chip Hôm nay/7/30 |
| G1 | Nhánh lỗi | `taixe01` | app | **Không lấy được** + lý do | *Lấy hàng thất bại*; web menu thất bại có việc |
| H1 | Chuyến (web) | `manager01` | web | **Chuyến** → mở chuyến | 7 nút: Chốt booking · Khởi hành · Đã đến · Đã giao · Khoá · Huỷ |
| I1 | Kế toán | `ketoan01` | web | Hoá đơn/bút toán | có Debit |
| I2 | Kế toán trên app | `ketoan01` | app | đăng nhập | màn "làm việc trên Odoo web" + Đăng xuất |
| J1 | Phiên | bất kỳ | app | Đăng xuất → đăng nhập lại | token bị xoá (kiểm `shared_prefs`) |
| K1 | Đường lui | `taixe01` | app | `docker compose stop odoo` → mở app | lỗi tiếng Việt + **Thử lại** + **Về dữ liệu mẫu** |

**Smoke 10 phút:** `nhomtruong01` gán việc cho `taixe01` (app) → `taixe01` Bắt đầu đi + capture (app) → `kho01` Nhận hàng (app) → `sep` xem Dashboard (web) → chạy `e2e-capture.ps1`.

---

## 4. Test không cần bấm chuột

| Script | Kiểm gì | Lệnh |
|---|---|---|
| `infra/scripts/e2e-capture.ps1` | login → capture → chống trùng → SQL | `powershell -File infra\scripts\e2e-capture.ps1` |
| `infra/scripts/app-shot.ps1` | đăng nhập 1 vai trò trên máy ảo + chụp màn hình | `... -Login nhomtruong01 -Password lab@2026 -Out docs/review/x.png` |
| `infra/scripts/app-switch-demo.ps1` | **chuỗi chuyển vai trò**: admin → `Quản trị viên ▾` → taixe01 → `Tài xế ▾` (vẫn còn) → cs01 → `CS ▾`; đăng nhập trực tiếp taixe01 ⇒ **không có nút** | `powershell -File infra\scripts\app-switch-demo.ps1` |
| `infra/scripts/smoke-admin-switch.ps1` | **Chuyển tài khoản**: đủ 10 tài khoản, switch → `driver`, sau switch 403, cs01 403, 400/404/403 tham số | `powershell -File infra\scripts\smoke-admin-switch.ps1` |
| `infra/scripts/deploy-admin-switch.ps1` | deploy `-u lab_core,lab_api` + restart + smoke (chạy khi vừa sửa BE) | `pwsh -File infra\scripts\deploy-admin-switch.ps1` |
| `infra/scripts/shot-auth.mjs` | chụp trang web sau đăng nhập | `node infra\scripts\shot-auth.mjs --page dashboard --out docs/review/x.png` |
| `infra/scripts/shot-page.mjs` | chụp trang công khai, viewport chính xác | `--url <url> --out <png> --width 390 --height 844 --scale 2` |

---

## 5. Bẫy đã gặp thật

1. **App không kết nối Odoo** ⇒ thiếu `adb reverse tcp:8069 tcp:8069` (máy ảo chặn `10.0.2.2` theo UID app).
2. **`adb` không nhận** ⇒ thêm `%LOCALAPPDATA%\Android\Sdk\platform-tools` vào PATH rồi **mở lại** terminal.
3. **Form "Yêu cầu lấy hàng" trên web không có nút chuyển trạng thái** (view ghi chú ★) ⇒ phân công/gán phải làm trên **app**; web chỉ xem.
4. **Capture chỉ bật khi việc ở `assigned`/`en_route`** (việc đã "Đã lấy được hàng" thì không có nút chụp — đúng nghiệp vụ).
5. **Test Odoo phải chạy container riêng** (`run --rm --no-deps`), nếu `exec` vào container đang phục vụ thì `HttpCase` tranh cổng 8069 → đỏ oan.
6. **Rate limit login 30 lượt/phút/IP** — test tự động đăng nhập nhiều lần sẽ bị 429.
7. **Đổi theme không thấy tác dụng** ⇒ xoá cache bundle: `delete from ir_attachment where url like '/web/assets/%'` + restart.
8. **Gán nhầm người không phải tài xế** (đã sửa): danh sách gán giờ chỉ gồm nhân viên có nhóm tài xế và hiện kèm tài khoản.
9. **Gọi API bằng `curl` mà chỉ gửi `X-Lab-Token` sẽ nhận 303 về `/web/login`** (không phải 401): mọi route `lab_api` đều `auth="user"` nên cần **cookie phiên** từ `/auth/login` (script `smoke-admin-switch.ps1` dùng `curl -c/-b` cookie jar; sau `/admin/switch` phiên bị **xoay sid** nên phải ghi lại cookie).
10. **Nút chuyển vai trò mất sau lần chuyển đầu** — nguyên nhân điển hình: suy quyền từ `roles` (sau khi chuyển, roles là `driver` nên nút biến mất). Đúng phải là: hiện/ẩn theo **`canSwitch`** do server trả, và `AppSettings` phải **lưu bền** cờ này (11 khoá) + `saveSession` ghi đè mỗi lần. Chỉ `/auth/login` mới xoá quyền; không chuyển được **sang** `admin` (403).
11. **`docker compose` báo `permission denied … npipe:////./pipe/dockerDesktopLinuxEngine`** ⇒ Docker Desktop chưa chạy (hoặc sandbox chặn named pipe). Mở Docker Desktop, chờ icon xanh, rồi chạy lại. Kiểm nhanh: `docker compose -f infra/docker-compose.yml ps` phải in `lab_odoo  running`.
12. **Màn có `StateHost` + thanh nút dưới cùng bị mất thanh** (đã sửa 2026-10-29): nhánh `UiState.Content` của `ui/components/StateHost.kt` gọi `content(...)` trực tiếp, **bỏ qua `modifier`** ⇒ `Modifier.weight(1f)` của 5 màn (admin/CS/tài xế/capture/kho) không có hiệu lực ⇒ LazyColumn chiếm hết chiều cao ⇒ thanh dưới cùng bị đẩy ra ngoài màn. Đã sửa bằng `Box(modifier)`. **Cách phát hiện**: `adb logcat -s System.out` với `println` đo `availH`/`onSizeChanged` — đừng đoán từ ảnh.
13. **Build Kotlin đỏ vì comment KDoc**: dòng `* ... **44dp**/dòng ...` chứa cụm `*/` ⇒ comment đóng sớm ⇒ `Syntax error: Expecting a top level declaration` báo ở **dòng sau** đó (không phải dòng gây lỗi). Sửa: viết `**44dp** mỗi dòng`, và **tự grep trước khi handoff** (mọi dòng comment không được có `*/` giữa dòng).
14. **Script chụp ảnh "không thấy nút" oan**: `TapPattern ... | Out-Null` **nuốt luôn `Write-Output` bên trong hàm** ⇒ log trống, không biết script dừng ở đâu; và dòng cần bấm có thể nằm ngoài vùng thấy của `DropdownMenu` (phải cuộn). Dùng `Write-Host` cho tiến trình + `TapScroll` (cuộn rồi tìm lại) — xem `infra/scripts/app-switch-demo.ps1`.

---

## 6. Trạng thái từng phần

| Phần | Trạng thái | Ghi chú |
|---|---|---|
| **Chuyển vai trò** (dropdown `… ▾` trên thanh trên cùng) | ✅ mới (deploy `lab_api 17.0.1.7.0` + smoke **20/20 `[OK]`**) | `GET /admin/accounts` + `POST /admin/switch` + `helpers.duoc_phep_chuyen()`; hiện/ẩn theo **`canSwitch`**, **chuyển liên tiếp** được (admin→taixe01→cs01→sep), mất khi đăng nhập thường (server 403); ảnh `app-switch-1-admin.png` … `app-switch-5-taixe-truc-tiep.png` |
| Màn **Chuyển tài khoản** (bản chi tiết, có tìm kiếm) | ✅ | vẫn giữ làm lối thứ hai; đã sửa lỗi thanh nút dưới cùng bị đẩy ra ngoài màn (bẫy 12, ảnh `app-admin-switch-TRUOC-fix.png` → `app-admin-switch.png`) |
| Vai trò **Trưởng nhóm tài xế** (`nhomtruong01`) | ✅ mới (đã deploy) | phân công trên app; CS vẫn gán được (giữ tương thích). Menu web cho role này **chưa cấp** (`lab_core/views/menuitems.xml` là file FE) — cần thêm xmlid `lab_core.group_driver_leader` nếu muốn thấy menu trên web |
| App tài xế | ✅ | nhận việc → capture → chống trùng |
| App CS / Phân công | ✅ | dropdown hiện "Tên · tài khoản" |
| App Kho | ✅ | nhận hàng → `stock.picking` |
| App Dashboard (Sếp) | ✅ | đọc `lab_dashboard_kpi` |
| Web list/form/kanban + theme | ✅ (chỉ đọc) | chưa có nút chuyển trạng thái cho Yêu cầu lấy hàng |
| Web **Chuyến** | ✅ đủ 7 nút | demo state machine ngay trên web |
| Web Dashboard OWL | ✅ | ảnh `odoo-dashboard-after.png` |
| 6 nhánh ngoại lệ | 🔶 1 nhánh có UI (tài xế báo không lấy được); 5 nhánh còn lại có field/rule + test nhưng chưa có màn riêng | cần thêm trước khi demo đủ |
| Video demo 3 phút | ❌ chưa có | |
