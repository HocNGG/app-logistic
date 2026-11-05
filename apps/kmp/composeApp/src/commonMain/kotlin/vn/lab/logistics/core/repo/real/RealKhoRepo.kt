package vn.lab.logistics.core.repo.real

import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.net.ApiException
import vn.lab.logistics.core.net.LabApi
import vn.lab.logistics.core.net.toVietnameseMessage
import vn.lab.logistics.core.repo.KhoRepo
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * KHO — NHẬN HÀNG gọi THẬT `lab_api` (cookie phiên + `X-Lab-Token`):
 *  - `GET /lab/api/v1/kho/cho-nhan`          → `{items:[{…, pickingName?}]}`
 *  - `POST /lab/api/v1/kho/jobs/<id>/nhan`   → `{state, stateLabel, pickingName}`
 *
 * `pickingName` là **số phiếu nhập kho** do Odoo sinh (chứng từ của nghiệp vụ nhận hàng —
 * Luật 4 REA). App chỉ HIỂN THỊ số đó, tuyệt đối không tự sinh/đoán.
 *
 * Ngữ nghĩa lỗi giống `RealCsRepo`: 401/HTML login ⇒ `PhienHetHan` + xoá token; mất kết nối ⇒
 * nêu địa chỉ máy chủ; 400/409 nghiệp vụ ⇒ giữ nguyên câu tiếng Việt của BE.
 *
 * ⚠️ KHÔNG retry tự động cho `nhan`: mỗi lần bấm là một lần server kiểm tra state; retry mù có
 * thể sinh phiếu nhập kho thứ hai nếu server chưa idempotent.
 * =====================================================================================
 */
class RealKhoRepo(
    private val api: LabApi,
    private val settings: AppSettings,
) : KhoRepo {

    override suspend fun choNhan(): Result<List<KhoJob>> = goi {
        api.khoChoNhan(settings.token)
    }

    override suspend fun nhan(jobId: Int): Result<ReceiveOutcome> = goi {
        api.khoNhan(jobId, settings.token)
    }

    /**
     * Danh sách kiện của việc đã nhận (`GET /lab/api/v1/kho/jobs/<id>/kien`, BE ship 2026-11-03).
     *
     * Việc chưa nhận ⇒ server trả `soKien = 0` (không lỗi) ⇒ [KienCuaViec.chuaNhan] = true.
     */
    override suspend fun kienCuaViec(jobId: Int): Result<KienCuaViec> = goi {
        api.khoKienCuaViec(jobId, settings.token)
    }

    override suspend fun quet(
        ma: String,
        moc: String,
        clientUuid: String,
        ghiChu: String?,
    ): Result<ScanResult> = goi {
        api.scanKien(
            ma = ma,
            moc = moc,
            clientUuid = clientUuid,
            ghiChu = ghiChu,
            token = settings.token,
        )
    }

    // ------------------------------------------------------------------ XUẤT KHO

    override suspend fun choXuat(): Result<List<KhoXuatChuyen>> = goi {
        api.khoChoXuat(settings.token)
    }

    override suspend fun chiTietXuat(chuyenId: Int): Result<KhoXuatChuyen> = goi {
        api.khoChoXuatChiTiet(chuyenId, settings.token)
    }

    override suspend fun xacNhanXuat(
        chuyenId: Int,
        clientUuid: String,
        lyDoThieu: String?,
    ): Result<XuatKetQua> = goi {
        api.khoXuat(
            chuyenId = chuyenId,
            clientUuid = clientUuid,
            lyDoThieu = lyDoThieu,
            token = settings.token,
        )
    }

    private suspend fun <T> goi(block: suspend () -> T): Result<T> {
        if (!settings.hasToken) {
            return Result.failure(
                ApiException.SessionExpired(
                    "Chưa có mã truy cập. Đăng nhập bằng tài khoản kho rồi mở lại màn nhận hàng.",
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
