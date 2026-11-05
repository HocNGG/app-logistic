package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.DriverJobsRepo
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * VIỆC HÔM NAY — gọi THẬT `GET /lab/api/v1/driver/jobs` (kèm cookie phiên + `X-Lab-Token`).
 *
 * Ngữ nghĩa lỗi đã đo (2026-10-28) và cách xử lý ở đây:
 *
 * | Server                                             | Repo làm gì                        |
 * |----------------------------------------------------|------------------------------------|
 * | 401 `TOKEN_REQUIRED` / `TOKEN_INVALID` / `TOKEN_EXPIRED` | xoá token + `PhienHetHan`   |
 * | **200 + trang HTML login Odoo** (mất cookie phiên)  | xoá token + `PhienHetHan`          |
 * | không kết nối được                                  | `Network` có nêu ĐỊA CHỈ máy chủ   |
 * | 409/404/400 nghiệp vụ                               | giữ nguyên câu tiếng Việt của BE   |
 *
 * Vì sao xoá token khi hết phiên: token còn hạn 8 giờ nhưng **cookie `session_id` không được
 * lưu ra đĩa** (chỉ trong RAM). Phiên chết thì token vô dụng → xoá để UI hiện đúng "chưa có
 * mã truy cập, vào Đăng nhập", không để người dùng tưởng app hỏng.
 * =====================================================================================
 */
class RealDriverJobsRepo(
    private val api: LabApi,
    private val settings: AppSettings,
) : DriverJobsRepo {

    override suspend fun todayJobs(): Result<List<DriverJob>> {
        if (!settings.hasToken) {
            return Result.failure(
                ApiException.SessionExpired(
                    "Chưa có mã truy cập của tài xế. Bấm \"Đăng nhập\" bằng tài khoản tài xế " +
                        "rồi quay lại màn này.",
                ),
            )
        }
        return try {
            Result.success(api.driverJobs(settings.token))
        } catch (e: ApiException) {
            if (e is ApiException.SessionExpired) settings.clearSession()
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(ApiException.Server(e.toVietnameseMessage(), e))
        }
    }
}
