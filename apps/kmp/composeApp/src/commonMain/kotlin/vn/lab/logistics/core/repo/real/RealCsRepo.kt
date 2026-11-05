package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsJobsPayload
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.CsJobsRepo
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * CS — ĐIỀU PHỐI gọi THẬT `lab_api` (cookie phiên + `X-Lab-Token`):
 *  - `GET /lab/api/v1/cs/jobs`               → `{items, drivers}`
 *  - `POST /lab/api/v1/cs/jobs/<id>/assign`  → `{state, stateLabel, driverName}`
 *
 * Ngữ nghĩa lỗi (giống `RealDriverJobsRepo`, đã đo 2026-10-28 trên nhóm route tài xế):
 *  - 401 `TOKEN_*` **và** "200 + trang HTML login Odoo" ⇒ [ApiException.SessionExpired]
 *    và **xoá token** (cookie `session_id` chỉ sống trong RAM ⇒ token còn hạn cũng vô dụng);
 *  - không kết nối được ⇒ [ApiException.Network] có nêu ĐỊA CHỈ máy chủ;
 *  - 403 (không thuộc nhóm CS) ⇒ [ApiException.Forbidden] giữ câu tiếng Việt;
 *  - 400/409 nghiệp vụ ⇒ giữ nguyên câu tiếng Việt của BE (Luật 6).
 *
 * ⚠️ KHÔNG retry tự động cho `assign`: POST ghi, người dùng bấm lại là quyết định của người dùng.
 * =====================================================================================
 */
class RealCsRepo(
    private val api: LabApi,
    private val settings: AppSettings,
) : CsJobsRepo {

    override suspend fun jobs(): Result<CsJobsPayload> = goi {
        api.csJobs(settings.token)
    }

    override suspend fun assign(jobId: Int, driverId: Int): Result<AssignOutcome> = goi {
        api.assignDriver(jobId = jobId, driverId = driverId, token = settings.token)
    }

    /**
     * Bọc một lời gọi: kiểm token trước, đổi lỗi thành `Result.failure` mang câu tiếng Việt
     * (nhờ vậy ViewModel không có `try/catch` nào).
     */
    private suspend fun <T> goi(block: suspend () -> T): Result<T> {
        if (!settings.hasToken) {
            return Result.failure(
                ApiException.SessionExpired(
                    "Chưa có mã truy cập. Đăng nhập bằng tài khoản CS rồi mở lại màn điều phối.",
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
