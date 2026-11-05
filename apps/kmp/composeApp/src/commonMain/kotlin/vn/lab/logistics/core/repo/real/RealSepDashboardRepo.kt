package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.SepDashboardRepo
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * DASHBOARD CỦA SẾP gọi THẬT `lab_api` (cookie phiên + `X-Lab-Token`):
 * `GET /lab/api/v1/sep/kpi?range=today|7d|30d`.
 *
 * Ngữ nghĩa lỗi giống các repo thật khác (`RealCsRepo`, `RealKhoRepo`):
 *  - 401 `TOKEN_*` / "200 + trang HTML login Odoo" ⇒ [ApiException.SessionExpired] + **xoá token**;
 *  - không kết nối được ⇒ [ApiException.Network] có nêu ĐỊA CHỈ máy chủ;
 *  - 403 (không thuộc nhóm quản lý) ⇒ [ApiException.Forbidden] giữ câu tiếng Việt của BE.
 *
 * ⚠️ `GET` nên có thể thử lại an toàn — nhưng KHÔNG tự retry: một lần bấm "Thử lại" của người
 * dùng rõ ràng hơn, và tránh nhân đôi tải khi Odoo đang chậm.
 * =====================================================================================
 */
class RealSepDashboardRepo(
    private val api: LabApi,
    private val settings: AppSettings,
) : SepDashboardRepo {

    override suspend fun kpi(range: KpiRange): Result<SepKpi> {
        if (!settings.hasToken) {
            return Result.failure(
                ApiException.SessionExpired(
                    "Chưa có mã truy cập. Đăng nhập bằng tài khoản quản lý rồi mở lại dashboard.",
                ),
            )
        }
        return try {
            Result.success(api.sepKpi(range = range, token = settings.token))
        } catch (e: ApiException) {
            if (e is ApiException.SessionExpired) settings.clearSession()
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(ApiException.Server(e.toVietnameseMessage(), e))
        }
    }
}
