# GITHUB ACTIONS — DỰNG CI/CD CHO DỰ ÁN

> Viết 2026-11-03. Trạng thái: **file workflow đã tạo + đã validate cú pháp YAML**.
> Chưa chạy được trên GitHub vì **máy chưa có repo git** (xem §1 — đây là chỗ cần anh đăng nhập).

---

## 0. Đã có gì trong repo

| File | Việc | Trigger |
|---|---|---|
| `.github/workflows/pages.yml` | Deploy **landing page** lên GitHub Pages | push `main` (khi `demo/landing/**` đổi), PR (chỉ kiểm), chạy tay |
| `.github/workflows/android-apk.yml` | Build **APK debug** + upload artifact + chạy `selfCheck` | **chỉ chạy tay** (workflow_dispatch) |
| `.gitignore` | Bỏ rác build (repo 552 MB → ~70 MB) | — |

Kiểm tra Pages làm 4 việc trước khi deploy: `index.html` tồn tại · **`assets/video/hero.mp4` còn** (yêu cầu cứng của khách: giữ background video) · 3 ảnh app light còn · không file nào > 95 MB · còn ít nhất 1 link CTA tới Odoo (`:8069`).

**Không cần secret nào.** Pages dùng `GITHUB_TOKEN` do GitHub cấp; APK ký bằng debug keystore.

---

## 1. CẦN ANH ĐĂNG NHẬP / LÀM TAY (4 bước, ~5 phút)

Máy đã có `git 2.53.0` (`C:\Program Files\Git\cmd\git.exe`), chưa có repo.

### 1.1 Đăng nhập GitHub (browser)
Mở https://github.com → đăng nhập (hoặc tạo tài khoản nếu chưa có). Đây là chỗ **duy nhất** cần tài khoản.

### 1.2 Tạo repo trống trên GitHub
Vào https://github.com/new → **Repository name**: `AppLogictics` (hoặc tên anh muốn) → **Private** hay **Public** tuỳ anh →
**KHÔNG** tích "Add a README" / ".gitignore" / "license" (để repo trống, tránh xung đột) → **Create repository**.

> ⚠️ **Pages trên repo Private cần GitHub Pro** (tài khoản free chỉ deploy Pages cho repo Public).
> Nếu repo private mà không có Pro: hoặc để Public, hoặc chỉ dùng workflow `android-apk` (không cần Pages).

Sau khi tạo, GitHub hiện sẵn URL dạng:
```
https://github.com/<user>/<repo>.git
```

### 1.3 Chạy 6 lệnh (copy nguyên khối)
```powershell
cd C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics

git init -b main
git add .
git -c user.name="<TÊN ANH>" -c user.email="<email GitHub>" commit -m "chore: khoi tao repo + CI GitHub Actions (Pages + APK)"
git remote add origin https://github.com/<user>/<repo>.git
git push -u origin main
```
- Lần push đầu, Git sẽ **mở hộp thoại đăng nhập GitHub** (hoặc hỏi username/password = **Personal Access Token**). Tạo token tại https://github.com/settings/tokens → *Generate new token (classic)* → scope `repo` → copy dán vào ô password. **Không dùng mật khẩu tài khoản.**
- Muốn khỏi gõ token mỗi lần: cài **GitHub CLI** rồi `gh auth login` (đăng nhập 1 lần, git tự dùng).

### 1.4 Bật Pages (chỉ làm 1 lần)
GitHub repo → **Settings → Pages** → mục **Source** chọn **GitHub Actions** (không chọn "Deploy from a branch").
Sau đó vào tab **Actions** → chọn workflow **Deploy landing → GitHub Pages** → **Run workflow** (hoặc push một thay đổi trong `demo/landing/`).

URL site sau khi deploy (in ra trong log + ở `Settings → Pages`):
```
https://<user>.github.io/<repo>/
```

---

## 2. Điều anh phải biết trước khi demo bằng link Pages

Landing hiện trỏ CTA tới **`http://localhost:8069`** (hằng `ODOO_URL` trong `demo/landing/index.html`).
Nghĩa là: mở link Pages ở máy nào **không chạy Odoo local** thì bấm "Vào Odoo (web)" sẽ không mở được.

Ba cách xử lý — anh chọn 1, tôi làm:
1. **Giữ nguyên** (khuyến nghị cho demo tại chỗ): link Pages để xem giao diện, Odoo vẫn mở bằng `localhost:8069` trên máy demo.
2. **Đổi hằng** sang IP LAN demo (`http://192.168.1.20:8069`) — chỉ mở được máy **cùng Wi-Fi** với máy anh.
3. **Cho phép truyền tham số**: thêm 3 dòng JS đọc `?odoo=http://…` → link Pages dùng được với bất kỳ máy chủ nào (`…/AppLogictics/?odoo=http://192.168.1.20:8069`).

---

## 3. Job APK: chạy tay khi cần
Tab **Actions** → **Build APK (chạy tay)** → **Run workflow** → xong tải APK ở mục **Artifacts** (`composeApp-debug-apk`).

**Cảnh báo thật:** dự án đang `compileSdk/targetSdk = 37`. Runner `ubuntu-latest` có thể chưa có `platforms;android-37`; workflow đã cố cài bằng `sdkmanager` và sẽ cảnh báo nếu không có. Nếu build fail vì SDK 37 → hạ về 36 hoặc build local (nhanh hơn, 40 giây).

---

## 4. Những gì CHƯA có trong CI (và lý do)

| Việc | Vì sao chưa | Cách thêm sau |
|---|---|---|
| Gate tĩnh `check.ps1` + audit | Anh chưa chọn; chạy được trên runner Ubuntu nhờ `pwsh` có sẵn | thêm job: `pwsh: ./infra/scripts/check.ps1` |
| Test Odoo backend (Docker + Postgres) | Anh chưa chọn; tốn ~10 phút/lần | job `services: postgres` + `docker compose run --rm --no-deps odoo odoo --test-tags=…` |
| iOS build | Chưa có target iOS + cần macOS runner + ký Apple | thêm target `iosArm64` vào `build.gradle.kts` rồi job `runs-on: macos-latest` |
| Deploy Odoo/app lên server | Cần VPS/hosting + secret (SSH key) | `appleboy/ssh-action` với secret `SSH_KEY` |
| Release APK ký keystore | Anh chọn debug | thêm 4 secret: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |

---

## 5. Sự cố thường gặp

| Hiện tượng | Nguyên nhân | Xử lý |
|---|---|---|
| `remote: Permission to … denied` | token thiếu scope / hết hạn | tạo Personal Access Token mới, scope `repo` |
| `Pages build and deployment failed` | repo Private chưa có Pro, hoặc Source chưa = "GitHub Actions" | đổi Public hoặc bật Pro; kiểm Settings → Pages |
| `Workflow not found` ở tab Actions | file chưa nằm đúng `.github/workflows/` trên nhánh `main` | kiểm `git ls-files .github` |
| Push bị chậm / repo phình | commit nhầm `build/`, `.tmp/`, `node_modules/` | `git rm -r --cached <thư mục>` sau đó commit lại (đã có `.gitignore`) |
| APK build fail: `android-37 not found` | SDK preview chưa có trên runner | hạ `compileSdk` hoặc build local |
