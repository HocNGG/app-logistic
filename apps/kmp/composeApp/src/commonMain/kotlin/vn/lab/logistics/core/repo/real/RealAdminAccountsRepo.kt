package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.LoginSession
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.AdminAccountsRepo
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * ADMIN — CHUYỂN TÀI KHOẢN, gọi THẬT `lab_api` (cookie phiên + `X-Lab-Token`):
 *  - `GET  /lab/api/v1/admin/accounts` → `{items:[{login,name,roles}]}`
 *  - `POST /lab/api/v1/admin/switch`   → đúng shape login (token + roles của tài khoản đích)
 *
 * Ngữ nghĩa lỗi (giống `RealCsRepo`):
 *  - 401 `TOKEN_*` **và** "200 + trang HTML login Odoo" ⇒ [ApiException.SessionExpired] và
 *    **xoá token** (cookie `session_id` chỉ sống trong RAM ⇒ token còn hạn cũng vô dụng);
 *  - 403 (không phải admin) ⇒ [ApiException.Forbidden]: màn hình hiện câu tiếng Việt của lớp
 *    lỗi này, KHÔNG lộ "Access Denied" của Odoo (Luật 6);
 *  - không kết nối được ⇒ [ApiException.Network] có nêu ĐỊA CHỈ máy chủ;
 *  - 400 nghiệp vụ (login không tồn tại…) ⇒ giữ nguyên câu tiếng Việt của BE.
 *
 * ⚠️ KHÔNG retry tự động cho `switch`: POST ghi, người dùng bấm lại là quyết định của người dùng.
 * =====================================================================================
 */
class RealAdminAccountsRepo(
    private val api: LabApi,
    private val settings: AppSettings,
) : AdminAccountsRepo {

    override suspend fun accounts(): Result<List<DemoAccount>> = goi {
        api.adminAccounts(settings.token)
    }

    override suspend fun switch(login: String): Result<LoginSession> = goi {
        api.switchAccount(login = login, token = settings.token)
    }

    /**
     * Bọc một lời gọi: kiểm token trước, đổi lỗi thành `Result.failure` mang câu tiếng Việt
     * (nhờ vậy ViewModel không có `try/catch` nào).
     */
    private suspend fun <T> goi(block: suspend () -> T): Result<T> {
        if (!settings.hasToken) {
            return Result.failure(
                ApiException.SessionExpired(
                    "Chưa có mã truy cập. Đăng nhập bằng tài khoản quản trị rồi mở lại màn này.",
                ),
            )
        }
        return try {
            Result.success(block())
        } catch (e: ApiException) {
            if (e is ApiException.SessionExpired) settings.clearSession()
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(ApiException.Server(e.toVietnameseMessage(), e))
        }
    }
}
