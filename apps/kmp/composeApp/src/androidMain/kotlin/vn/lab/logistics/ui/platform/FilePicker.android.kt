package vn.lab.logistics.ui.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * =====================================================================================
 * CẦU NỐI CHỌN ẢNH TRÊN ANDROID.
 *
 * VẤN ĐỀ: `commonMain` khai `expect fun pickImageFile(): ByteArray?` — hàm **CHẶN, trả về
 * ngay** (đúng cho desktop: `java.awt.FileDialog` là hộp thoại đồng bộ của hệ điều hành).
 * Android không có API đồng bộ như vậy: bộ chọn ảnh là một Activity khác (DocumentsUI), và
 * kết quả chỉ được trả về qua callback `ActivityResultContracts.GetContent` **trên main thread**.
 *
 * CÁCH GIẢI QUYẾT (không đổi chữ ký `expect`, không đổi UI):
 *  - [attach] được `MainActivity` gọi trong `onCreate` để đưa vào launcher + application context.
 *  - [pickBlocking] (chạy ở LUỒNG NỀN — xem `PlatformDefaults.imagePickerRunsOnCallerMainThread`)
 *    đăng ký một "phiên chờ", post lệnh mở hộp thoại lên main thread rồi chờ `CountDownLatch`.
 *  - Khi người dùng chọn xong, callback [deliver] chạy trên main thread: đọc bytes của `Uri`
 *    rồi mở latch ⇒ luồng nền nhận `ByteArray` và trả về cho ViewModel.
 *
 * VÌ SAO PHẢI CHẠY Ở LUỒNG NỀN: nếu gọi hàm này trên main thread thì main thread bị chặn,
 * callback [deliver] không bao giờ được gọi ⇒ treo app (`pickImageFile()` sẽ hết thời gian chờ
 * và trả `null`). `DriverCaptureViewModel.pick()` lo việc chuyển luồng, dựa trên cờ
 * `platformDefaults().imagePickerRunsOnCallerMainThread` (= false trên Android).
 *
 * KHÔNG GHI RA ĐĨA: bytes được đọc từ `content://` vào RAM rồi trả về; ảnh sống trong
 * ViewModel tới lúc gửi (yêu cầu cứng C1–C3).
 * =====================================================================================
 */
object AndroidImagePicker {

    private const val TAG = "AndroidImagePicker"

    /** Chờ tối đa 10 phút — người dùng có thể mở hộp thoại rồi đi làm việc khác. */
    private const val TIMEOUT_MINUTES = 10L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Giữ applicationContext (không giữ cứng Activity → dùng WeakReference cho Print/Camera Dialog). */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var launcher: ActivityResultLauncher<String>? = null

    /** Launcher chụp ảnh (`TakePicture`): nhận `Uri` đích, trả `true` khi đã ghi ảnh vào đó. */
    @Volatile
    private var cameraLauncher: ActivityResultLauncher<Uri>? = null

    /** Launcher xin quyền CAMERA lúc chạy (Android 6.0+). */
    @Volatile
    private var permissionLauncher: ActivityResultLauncher<String>? = null

    @Volatile
    private var permissionLatch: CountDownLatch? = null

    @Volatile
    private var permissionGrantedResult: Boolean = false

    /** Tệp tạm đang chờ camera ghi vào; đọc vào RAM rồi XOÁ ngay trong [deliverCamera]. */
    @Volatile
    private var cameraFile: File? = null

    /** Phiên chờ hiện tại; chỉ một hộp thoại tại một thời điểm. */
    private var pending: Wait? = null

    private class Wait {
        val latch = CountDownLatch(1)

        @Volatile
        var bytes: ByteArray? = null

        @Volatile
        var error: Throwable? = null
    }

    /** Gọi từ `MainActivity.onCreate` — SỚM HƠN mọi lần chọn ảnh. */
    fun attach(
        activity: Activity,
        imagePicker: ActivityResultLauncher<String>,
        cameraPicker: ActivityResultLauncher<Uri>,
        cameraPermission: ActivityResultLauncher<String>? = null,
    ) {
        appContext = activity.applicationContext
        activityRef = WeakReference(activity)
        launcher = imagePicker
        cameraLauncher = cameraPicker
        permissionLauncher = cameraPermission
    }

    fun currentActivity(): Activity? = activityRef?.get()

    fun currentContext(): Context? = appContext

    fun deliverCameraPermission(granted: Boolean) {
        permissionGrantedResult = granted
        permissionLatch?.countDown()
    }

    /**
     * Đảm bảo quyền `CAMERA` đã được cấp trước khi mở Camera thời gian thực hoặc `TakePicture`.
     * Phải gọi ở luồng nền (không gọi trên main thread).
     */
    fun ensureCameraPermissionBlocking(): Boolean {
        val ctx = appContext ?: return false
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val permLauncher = permissionLauncher ?: return false
        val latch = CountDownLatch(1)
        permissionLatch = latch
        permissionGrantedResult = false
        mainHandler.post {
            runCatching { permLauncher.launch(Manifest.permission.CAMERA) }
                .onFailure { latch.countDown() }
        }
        latch.await(2, TimeUnit.MINUTES)
        permissionLatch = null
        return permissionGrantedResult ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Callback của `ActivityResultContracts.GetContent`. `uri == null` ⇒ người dùng bấm Huỷ.
     * LUÔN được gọi trên main thread.
     */
    fun deliver(uri: Uri?) {
        val wait = synchronized(this) { pending } ?: return
        try {
            wait.bytes = if (uri == null) null else readBytes(uri)
        } catch (e: Throwable) {
            wait.error = IllegalStateException(
                "Không đọc được ảnh đã chọn. Vui lòng chọn ảnh khác.",
                e,
            )
        } finally {
            synchronized(this) { if (pending === wait) pending = null }
            wait.latch.countDown()
        }
    }

    /**
     * Callback của `ActivityResultContracts.TakePicture`. `success == false` ⇒ người dùng thoát
     * camera mà không chụp. Tệp tạm LUÔN bị xoá (ảnh chỉ sống trong RAM, giống luồng chọn ảnh).
     */
    fun deliverCamera(success: Boolean) {
        val wait = synchronized(this) { pending } ?: return
        val file = cameraFile
        cameraFile = null
        try {
            wait.bytes = if (success && file != null && file.length() > 0L) file.readBytes() else null
        } catch (e: Throwable) {
            wait.error = IllegalStateException(
                "Không đọc được ảnh vừa chụp. Vui lòng chụp lại.",
                e,
            )
        } finally {
            runCatching { file?.delete() }
            synchronized(this) { if (pending === wait) pending = null }
            wait.latch.countDown()
        }
    }

    /** Chọn ảnh từ thư viện — CHẶN luồng gọi, trả bytes hoặc null (Huỷ). */
    fun pickBlocking(): ByteArray? = launchAndWait("chọn ảnh") { _ ->
        launcher?.launch("image/*")
    }

    /**
     * Chụp ảnh trực tiếp bằng camera hệ thống — CHẶN luồng gọi, trả JPEG bytes hoặc null (Huỷ).
     *
     * Camera ghi vào `cache/captures/…jpg` qua `FileProvider` (không cần quyền CAMERA/bộ nhớ vì
     * app camera của hệ thống tự xin quyền). Không có app camera ⇒ báo lỗi tiếng Việt thay vì sập.
     */
    fun captureBlocking(): ByteArray? {
        if (!ensureCameraPermissionBlocking()) {
            throw IllegalStateException("Chưa cấp quyền Camera. Vui lòng cho phép quyền Camera để chụp ảnh.")
        }
        return launchAndWait("chụp ảnh") { context ->
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraFile = file
            cameraLauncher?.launch(uri)
        }
    }

    /**
     * Khung chung cho mọi hộp thoại Activity: đăng ký phiên chờ, mở trên main thread, chặn
     * luồng nền tới khi callback trả kết quả (hoặc hết [TIMEOUT_MINUTES]).
     */
    private fun launchAndWait(what: String, open: (Context) -> Unit): ByteArray? {
        val context = appContext
        if (context == null || launcher == null || cameraLauncher == null) {
            // Chưa `attach` (chỉ xảy ra ở preview/test không có Activity) ⇒ coi như Huỷ.
            Log.w(TAG, "Chưa attach Activity — bỏ qua lời gọi $what.")
            return null
        }

        // Bắt buộc: chặn main thread ở đây là tự khoá callback của chính mình (xem KDoc đầu tệp).
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException(
                "Lỗi nội bộ: $what phải gọi ngoài luồng UI. " +
                    "Kiểm tra PlatformDefaults.imagePickerRunsOnCallerMainThread.",
            )
        }

        val wait = Wait()
        synchronized(this) { pending = wait }

        // `ActivityResultLauncher.launch` BẮT BUỘC gọi trên main thread.
        mainHandler.post {
            try {
                open(context)
            } catch (e: Throwable) {
                // Vd không có app camera (ActivityNotFoundException): báo lỗi, đừng sập app.
                Log.w(TAG, "Không mở được màn $what", e)
                wait.error = IllegalStateException(
                    "Không mở được màn $what trên máy này. Vui lòng thử cách khác.",
                    e,
                )
                synchronized(this) { if (pending === wait) pending = null }
                wait.latch.countDown()
            }
        }

        if (!wait.latch.await(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            synchronized(this) { if (pending === wait) pending = null }
            Log.w(TAG, "Hết thời gian chờ $what (${TIMEOUT_MINUTES} phút) — coi như Huỷ.")
            return null
        }

        wait.error?.let { throw it }
        return wait.bytes
    }


    /** Đọc toàn bộ nội dung của `content://` vào RAM (ảnh chụp thường 1–6 MB). */
    private fun readBytes(uri: Uri): ByteArray {
        val context = appContext ?: throw IllegalStateException("Mất context của ứng dụng.")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException(
                "Không mở được ảnh đã chọn. Vui lòng chọn ảnh khác.",
            )
        val out = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = stream.read(buffer)
            while (read > 0) {
                out.write(buffer, 0, read)
                read = stream.read(buffer)
            }
        }
        return out.toByteArray()
    }
}

/**
 * `actual` cho Android của hàm chọn ảnh. Xem [AndroidImagePicker] để hiểu vì sao cần cầu nối và
 * vì sao hàm này phải được gọi ngoài luồng UI.
 */
actual fun pickImageFile(): ByteArray? = AndroidImagePicker.pickBlocking()

/** `actual` Android của [captureImageFromCamera]: mở camera hệ thống, trả JPEG trong RAM. */
actual fun captureImageFromCamera(): ByteArray? = AndroidImagePicker.captureBlocking()
