package vn.lab.logistics.core.repo

import vn.lab.logistics.core.model.DriverJob

/**
 * Danh sách việc lấy hàng hôm nay — hợp đồng `GET /lab/api/v1/driver/jobs` (docs/02 §6.2).
 *
 * Màn hình chỉ phụ thuộc interface này; hai impl được chọn ở nơi lắp ráp (`App.kt`) bằng
 * công tắc "Nguồn dữ liệu":
 *  - `core/repo/mock/MockDriverJobsRepo` — dữ liệu MẪU, không gọi mạng;
 *  - `core/repo/real/RealDriverJobsRepo` — gọi THẬT, cần **cookie phiên + `X-Lab-Token`**.
 *
 * Ngữ nghĩa lỗi đã đo (2026-10-28): `401` (TOKEN_REQUIRED/TOKEN_EXPIRED) **và**
 * "200 + trang HTML login Odoo" đều là **phiên hết hạn** ⇒ repo xoá token và trả
 * [vn.lab.logistics.core.net.ApiException.SessionExpired] để UI bắt người dùng đăng nhập lại.
 * Không đổi chữ ký hàm khi thay impl.
 */
interface DriverJobsRepo {
    suspend fun todayJobs(): Result<List<DriverJob>>
}
