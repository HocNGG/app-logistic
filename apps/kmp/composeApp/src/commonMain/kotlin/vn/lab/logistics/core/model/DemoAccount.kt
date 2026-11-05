package vn.lab.logistics.core.model

/**
 * =====================================================================================
 * MỘT TÀI KHOẢN DEMO mà quản trị viên có thể CHUYỂN SANG.
 *
 * Hình dạng ĐÚNG theo hợp đồng `GET /lab/api/v1/admin/accounts`:
 * ```json
 * {"ok":true,"data":{"items":[
 *   {"login":"taixe01","name":"Trần Văn Hùng (tài xế)","roles":["driver"]},
 *   {"login":"nhomtruong01","name":"…","roles":["driver_leader"]}
 * ]}}
 * ```
 *
 * Hai trường "có thể vắng" và cách app xử lý (KHÔNG được vỡ, KHÔNG được bịa):
 *  - [name] vắng ⇒ dùng chính `login` (dòng chọn vẫn đọc được, không hiện chuỗi rỗng);
 *  - [roles] vắng/rỗng ⇒ danh sách rỗng ⇒ app coi là "vai trò khác" (`Workspace.Unsupported`)
 *    nếu chuyển sang tài khoản đó — đúng nhánh hợp đồng, KHÔNG đoán bừa là tài xế.
 *
 * [login] là BẮT BUỘC: dòng nào server trả thiếu `login` thì app bỏ dòng đó, vì không có `login`
 * thì không gửi được `POST /admin/switch`.
 * =====================================================================================
 */
data class DemoAccount(
    val login: String,
    val name: String,
    /** Mã vai trò y như `data.roles` của `/auth/login` (thứ tự server trả, app tự áp ưu tiên). */
    val roles: List<String> = emptyList(),
) {
    /** Màn mà app sẽ mở nếu chuyển sang tài khoản này — dùng đúng quy tắc của `Workspace`. */
    val targetWorkspace: Workspace get() = Workspace.fromRoles(roles)
}
