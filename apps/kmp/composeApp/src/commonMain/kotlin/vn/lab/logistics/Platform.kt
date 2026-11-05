package vn.lab.logistics

/**
 * `expect` = "sẽ có một hàm tên này, nhưng mỗi nền tảng tự viết phần thân".
 * Đây chính là điều làm nên KMP: phần khai báo dùng chung, phần thân đặt riêng.
 *
 * Bản `actual` cho desktop nằm ở desktopMain/.../Platform.desktop.kt
 * Bản `actual` cho Android nằm ở androidMain/.../Platform.android.kt
 */
expect fun platformInfo(): String

/**
 * =====================================================================================
 * GIÁ TRỊ MẶC ĐỊNH KHÁC NHAU THEO NỀN TẢNG — gói trong MỘT `expect` để review thấy ngay
 * toàn bộ chỗ khác biệt giữa desktop và Android (thay vì rải nhiều `expect` riêng lẻ).
 *
 * @property baseUrl Địa chỉ Odoo MẶC ĐỊNH của lần chạy đầu tiên (chưa lưu cấu hình).
 *   desktop: `http://localhost:8069` (Odoo chạy cùng máy) ·
 *   Android: `http://10.0.2.2:8069` (loopback máy host nhìn từ máy ảo).
 *   Đây chỉ là GIÁ TRỊ KHỞI TẠO: người dùng sửa được ở màn Đăng nhập (ô "Máy chủ Odoo") và
 *   giá trị vừa sửa được lưu bền NGAY (`AppSettings.rememberBaseUrl`).
 * @property demoStartWidthDp Bề rộng NỘI DUNG gợi ý cho màn "Cài đặt demo" (nút preset để chứng
 *   minh 3 ngưỡng duo UI). Không phải bề rộng mặc định của app: mặc định nội dung đi theo cửa sổ.
 *   desktop 1280dp (2 pane rộng) · Android 400dp (vừa màn hình điện thoại, 1 pane).
 * @property imagePickerRunsOnCallerMainThread `true` khi hộp thoại chọn ảnh BẮT BUỘC chạy trên
 *   luồng UI và chặn luôn luồng đó (desktop: AWT `FileDialog` trên EDT).
 *   `false` khi phải chạy ở luồng nền (Android: kết quả Activity chỉ về main thread ⇒ chặn
 *   main thread là tự khoá callback). `DriverCaptureViewModel.pick()` đọc cờ này để chọn luồng.
 * @property useRealApiByDefault Nguồn dữ liệu MẶC ĐỊNH ở lần chạy ĐẦU TIÊN (chưa lưu lựa chọn).
 *   desktop `false` — giữ nguyên hành vi demo an toàn (mở app là dữ liệu Mẫu) ·
 *   Android `true` — app tài xế trên điện thoại là **app thật**: mở ra phải gọi Odoo ngay,
 *   không bắt người dùng đi tìm công tắc. Lựa chọn của người dùng được LƯU BỀN
 *   (`AppSettings.useRealApi`) nên đổi một lần là nhớ cho các lần mở sau; đường lui về dữ liệu
 *   Mẫu vẫn còn khi gọi Odoo lỗi (Luật 8 — demo không bao giờ rơi vào màn chết).
 * =====================================================================================
 */
data class PlatformDefaults(
    val baseUrl: String,
    val demoStartWidthDp: Int,
    val imagePickerRunsOnCallerMainThread: Boolean,
    val useRealApiByDefault: Boolean,
    /**
     * `true` khi máy có thể CHỤP ẢNH TRỰC TIẾP bằng camera (Android). Desktop = `false` (không
     * có camera tích hợp đáng tin cậy) ⇒ màn capture ẩn nút "Chụp ảnh", chỉ còn "Chọn ảnh".
     */
    val cameraCaptureSupported: Boolean = false,
)

expect fun platformDefaults(): PlatformDefaults

/**
 * Mốc thời gian hiện tại, mili giây từ epoch.
 *
 * Vì sao phải là `expect`: `commonMain` không có API đồng hồ nào (không thêm `kotlinx-datetime`
 * chỉ để đọc giờ). Chỉ dùng để biết **token `lab_api` còn hạn hay không** —
 * xem `AppSettings.hasValidSession`. KHÔNG dùng cho tính toán nghiệp vụ: giờ hẹn lấy hàng,
 * cut-off… đều do server trả, app không tự tính.
 */
expect fun nowEpochMillis(): Long
