package vn.lab.logistics

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.core.settings.FileSettingsStorage
import vn.lab.logistics.ui.platform.AndroidImagePicker

/**
 * =====================================================================================
 * ĐIỂM VÀO CỦA APP TRÊN ANDROID (Wave C).
 *
 * Đây là bản tương ứng của `desktopMain/.../main.kt`:
 *   desktop: `application { Window { App(settings) } }`
 *   android: `ComponentActivity` + `setContent { App(settings) }`
 * Cùng MỘT hàm `App()` của `commonMain` ⇒ cùng UI, cùng theme, cùng luồng nghiệp vụ. Không có
 * bản UI riêng cho Android.
 *
 * BỐN VIỆC CỦA CLASS NÀY:
 *
 * 1. **Edge-to-edge** (`enableEdgeToEdge`): app tài xế dùng cả màn hình; nền dưới thanh trạng
 *    thái là brand đêm. Thanh trạng thái/điều hướng trong suốt + icon SÁNG
 *    (`SystemBarStyle.dark(...)`) vì nền tối — icon tối trên nền đêm sẽ không nhìn thấy.
 *    Bù lại, nội dung Compose được đệm bằng `safeDrawingPadding()` để chữ/nút không nằm dưới
 *    thanh trạng thái hay cụm cử chỉ (yêu cầu a11y: mọi nút bấm được, không bị che).
 *
 * 2. **Nền tối của app**: app chỉ có MỘT chế độ màu (tối, ADR-009 §6) nên `App()` không còn
 *    tham số chế độ màu; `AppTheme` tự cắm bảng token tối. Thanh trạng thái/điều hướng vì vậy
 *    luôn trong suốt trên nền `#080D17`.
 *
 * 3. **Lưu cấu hình bền** bằng SharedPreferences: `FileSettingsStorage(this)`. Chỉ các khoá không
 *    bí mật (địa chỉ máy chủ, DB, tài khoản, token 8 giờ, uid, tên tài xế, số việc) — mật khẩu /
 *    API key KHÔNG bao giờ xuống đĩa (xem KDoc `AppSettings`).
 *
 * 4. **Bộ chọn ảnh**: `registerForActivityResult(GetContent())` phải đăng ký TRƯỚC khi Activity
 *    ở trạng thái STARTED, nên nó là property của class. Kết quả được đẩy cho
 *    [AndroidImagePicker] — cầu nối để `pickImageFile()` (expect của `commonMain`) trả về
 *    `ByteArray` mà không phải đổi chữ ký hàm dùng chung.
 *
 * Vì sao `ComponentActivity` mà không `AppCompatActivity`: app không dùng AppCompat/XML layout
 * nào (toàn bộ UI là Compose Material 2), nên kéo AppCompat chỉ làm APK nặng thêm.
 * =====================================================================================
 */
class MainActivity : ComponentActivity() {

    /**
     * Bộ chọn ảnh SAF (MIME dạng "image/…") — KHÔNG cần quyền đọc bộ nhớ và không cần `CAMERA`
     * runtime permission: người dùng tự chọn tệp trong hộp thoại của hệ điều hành.
     *
     * ⚠️ ĐỪNG viết GẠCH CHÉO NGAY TRƯỚC DẤU SAO (mở block comment) bên trong một comment Kotlin:
     * block comment của Kotlin LỒNG NHAU được, nên nó mở thêm một lớp và cả file bị
     * "Unclosed comment" — lỗi báo ở dòng `class MainActivity` chứ không chỉ dòng gây lỗi
     * (đã trúng thật 2 lần khi build APK đầu tiên, ngay cả trong câu cảnh báo này).
     * `null` uri = người dùng bấm Huỷ (UI coi như không chọn gì, không báo lỗi).
     */
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> AndroidImagePicker.deliver(uri) }

    /**
     * Chụp ảnh TRỰC TIẾP bằng camera hệ thống (`TakePicture`): ghi vào `Uri` do `FileProvider`
     * cấp (xem manifest + `res/xml/file_paths.xml`), trả `true` khi đã chụp xong.
     * Cũng phải là property của class vì đăng ký trước khi Activity STARTED.
     */
    private val cameraPicker = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> AndroidImagePicker.deliverCamera(ok) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> AndroidImagePicker.deliverCameraPermission(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )

        // Cầu nối Activity ⇄ hàm `pickImageFile()` / `scanQrCodeWithLiveCamera()` của commonMain.
        AndroidImagePicker.attach(this, imagePicker, cameraPicker, cameraPermissionLauncher)

        // Cùng một AppSettings như desktop, chỉ khác nơi lưu (SharedPreferences thay cho tệp).
        val settings = AppSettings(FileSettingsStorage(this))

        setContent {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                App(settings = settings)
            }
        }
    }
}
