package vn.lab.logistics.core.repo

/**
 * =====================================================================================
 * HỢP ĐỒNG ĐĂNG NHẬP — `feature/login` chỉ biết interface này, KHÔNG biết HTTP/Odoo.
 *
 * Hai impl (chọn ở nơi lắp ráp `App.kt`, KHÔNG rải cờ trong màn hình):
 *  - `core/repo/mock/MockLoginRepo`  — dữ liệu MẪU, không gọi mạng (mặc định, demo an toàn).
 *  - `core/repo/real/RealLoginRepo`  — gọi **Odoo thật** đúng 2 bước:
 *      1) `POST /web/session/authenticate` → `uid` + cookie `session_id` (CHỈ có trong
 *         `Set-Cookie`, không có trong body — docs/02 §6.3);
 *      2) `POST /lab/api/v1/auth/login` (kèm cookie) → token 8 giờ + `driver_name`.
 *
 * ⚠️ Odoo 17 dùng **API key** (`res.users.apikeys`) thay mật khẩu; app KHÔNG lưu mật khẩu
 * người dùng (chỉ lưu token 8 giờ trong `AppSettings`, xem `core/settings/AppSettings.kt`).
 * `uid = false` trong body nghĩa là sai thông tin đăng nhập.
 * =====================================================================================
 */
interface LoginRepo {

    /**
     * @param server  URL máy chủ Odoo, lấy từ `AppSettings.baseUrl` (UI sửa được, mặc định
     *                của lab do `AppSettings.DEFAULT_BASE_URL` quyết định — không hardcode).
     * @param db      tên database
     * @param login   tên đăng nhập
     * @param apiKey  mật khẩu HOẶC API key của user (Odoo 17 cho API key dùng thay mật khẩu).
     * @return `Result.success` khi đăng nhập đủ 2 bước; `Result.failure` mang
     *         [vn.lab.logistics.core.net.ApiException] với message **tiếng Việt** đã map
     *         (`401`/trang HTML login ⇒ `PhienHetHan`; `AccessError` ⇒ "Bạn không có quyền…").
     */
    suspend fun login(server: String, db: String, login: String, apiKey: String): Result<Unit>
}
