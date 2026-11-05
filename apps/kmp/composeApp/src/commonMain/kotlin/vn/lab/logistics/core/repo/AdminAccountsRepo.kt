package vn.lab.logistics.core.repo

import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.net.LoginSession

/**
 * Hợp đồng của màn **Chuyển tài khoản** (chỉ quản trị viên — `roles` của phiên có `admin`):
 *
 * | Hàm | Endpoint | Ý nghĩa |
 * |---|---|---|
 * | [accounts] | `GET /lab/api/v1/admin/accounts` | mọi tài khoản demo trong DB để chọn |
 * | [switch] | `POST /lab/api/v1/admin/switch` | đổi phiên sang tài khoản đã chọn, trả session mới |
 *
 * Màn hình chỉ phụ thuộc interface này; hai impl được chọn ở nơi lắp ráp (`App.kt`) theo công tắc
 * nguồn dữ liệu — KHÔNG có `if (demo)` trong UI:
 *  - [vn.lab.logistics.core.repo.mock.MockAdminAccountsRepo] — danh sách mẫu, không gọi mạng;
 *  - [vn.lab.logistics.core.repo.real.RealAdminAccountsRepo] — gọi THẬT (cookie + `X-Lab-Token`).
 *
 * [switch] trả [LoginSession] vì phản hồi của BE có **đúng shape của login**: app ghi phiên mới
 * rồi điều hướng theo `Workspace.fromRoles(session.roles)` — y như vừa đăng nhập.
 */
interface AdminAccountsRepo {

    /** Mọi tài khoản demo có thể chuyển sang. */
    suspend fun accounts(): Result<List<DemoAccount>>

    /** Đổi phiên sang tài khoản [login]; trả session mới (token + vai trò của tài khoản đích). */
    suspend fun switch(login: String): Result<LoginSession>
}
