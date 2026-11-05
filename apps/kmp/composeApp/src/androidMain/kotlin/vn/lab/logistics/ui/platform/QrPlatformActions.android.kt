@file:Suppress("DEPRECATION")

package vn.lab.logistics.ui.platform

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.hardware.Camera
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lab.logistics.ui.components.decodeQrFromArgb
import vn.lab.logistics.ui.components.encodeQrMatrix
import vn.lab.logistics.ui.components.extractScanCode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

actual suspend fun printQrLabels(
    jobTitle: String,
    labels: List<PrintableQrLabel>,
): Result<String> = withContext(Dispatchers.Main) {
    if (labels.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Chưa có tem QR nào để in."))
    }
    val activity = AndroidImagePicker.currentActivity()
        ?: return@withContext Result.failure(
            IllegalStateException("Không tìm thấy cửa sổ ứng dụng để mở màn hình in."),
        )
    runCatching {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val safeTitle = jobTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "TemQR" }
        val jobName = "TemQR_$safeTitle"
        val adapter = object : PrintDocumentAdapter() {
            private var printAttrs: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                printAttrs = newAttributes
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(labels.size)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                val attrs = printAttrs
                if (attrs == null) {
                    callback.onWriteFailed("Chưa có thuộc tính trang in.")
                    return
                }
                val pdfDoc = PrintedPdfDocument(activity, attrs)
                try {
                    labels.forEachIndexed { idx, label ->
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        val page = pdfDoc.startPage(idx)
                        val canvas = page.canvas
                        val bmp = renderSingleLabelBitmap(label)
                        val srcRect = Rect(0, 0, bmp.width, bmp.height)
                        val margin = 16f
                        val availW = (canvas.width - margin * 2).coerceAtLeast(100f)
                        val availH = (canvas.height - margin * 2).coerceAtLeast(100f)
                        val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val left = (canvas.width - drawW) / 2f
                        val top = margin
                        val dstRect = RectF(left, top, left + drawW, top + drawH)
                        canvas.drawBitmap(bmp, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        pdfDoc.finishPage(page)
                        bmp.recycle()
                    }
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Throwable) {
                    callback.onWriteFailed(e.message ?: "Lỗi ghi dữ liệu trang in.")
                } finally {
                    pdfDoc.close()
                }
            }
        }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        printManager.print(jobName, adapter, attrs)
        "Đã mở màn hình in hệ thống cho ${labels.size} tem QR ($jobTitle)."
    }
}

actual suspend fun saveQrLabelsAsImage(
    filePrefix: String,
    labels: List<PrintableQrLabel>,
): Result<String> = withContext(Dispatchers.IO) {
    if (labels.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Chưa có tem QR nào để lưu ảnh."))
    }
    val context = AndroidImagePicker.currentContext()
        ?: return@withContext Result.failure(IllegalStateException("Mất context ứng dụng khi lưu ảnh."))
    runCatching {
        val bitmap = renderLabelSheetBitmap(labels)
        val safePrefix = filePrefix.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "TemQR" }
        val fileName = "TemQR_${safePrefix}_${System.currentTimeMillis()}.png"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/AppLogistics",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Không tạo được tệp ảnh trong Bộ sưu tập.")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: throw IllegalStateException("Không ghi được dữ liệu ảnh PNG.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Đã lưu ảnh tem QR vào Bộ sưu tập (Pictures/AppLogistics/$fileName)."
            } else {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir
                val dir = File(picturesDir, "AppLogistics").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                "Đã lưu ảnh tem QR tại: ${file.absolutePath}"
            }
        } finally {
            bitmap.recycle()
        }
    }
}

actual suspend fun scanQrCodeWithLiveCamera(
    expectedCodes: List<String>?,
): Result<String?> = withContext(Dispatchers.IO) {
    if (!AndroidImagePicker.ensureCameraPermissionBlocking()) {
        return@withContext Result.failure(
            IllegalStateException("Chưa được cấp quyền Camera. Vui lòng cho phép quyền Camera để quét mã QR."),
        )
    }
    val activity = AndroidImagePicker.currentActivity()
        ?: return@withContext Result.failure(
            IllegalStateException("Không mở được màn hình Camera quét QR."),
        )

    val latch = CountDownLatch(1)
    var scannedCode: String? = null
    var scanError: Throwable? = null
    val mainHandler = Handler(Looper.getMainLooper())
    val requestGalleryPick = AtomicBoolean(false)

    mainHandler.post {
        try {
            showLiveQrScannerDialog(
                activity = activity,
                expectedCodes = expectedCodes,
                onResult = { code ->
                    scannedCode = code
                    latch.countDown()
                },
                onPickFromGallery = {
                    requestGalleryPick.set(true)
                    latch.countDown()
                },
                onError = { err ->
                    scanError = err
                    latch.countDown()
                },
            )
        } catch (e: Throwable) {
            scanError = e
            latch.countDown()
        }
    }

    latch.await(10, TimeUnit.MINUTES)

    // Nếu người dùng bấm "Chọn ảnh QR trong máy" từ màn hình camera:
    if (requestGalleryPick.get()) {
        return@withContext runCatching {
            val bytes = AndroidImagePicker.pickBlocking() ?: return@runCatching null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Không đọc được tệp ảnh vừa chọn.")
            try {
                val decoded = decodeBitmapQr(bmp)
                    ?: throw IllegalStateException(
                        "Không tìm thấy mã QR hợp lệ trong ảnh vừa chọn. Vui lòng chọn đúng ảnh tem QR hoặc đưa Camera vào mã QR.",
                    )
                val clean = extractScanCode(decoded)
                val isMatch = expectedCodes.isNullOrEmpty() || expectedCodes.any { exp ->
                    val cleanExp = extractScanCode(exp).uppercase()
                    cleanExp == clean.uppercase() || clean.uppercase().contains(cleanExp) || cleanExp.contains(clean.uppercase())
                }
                if (!isMatch) {
                    playWrongCodeBeep()
                    throw IllegalStateException("Quét lộn mã: Mã '$clean' trong ảnh không thuộc kiện của đơn hàng này!")
                }
                playBeep()
                clean
            } finally {
                bmp.recycle()
            }
        }
    }

    scanError?.let { return@withContext Result.failure(it) }
    Result.success(scannedCode)
}

// =====================================================================================
// HỘP THOẠI CAMERA QUÉT MÃ QR THỜI GIAN THỰC (NHƯ MÁY QUÉT SIÊU THỊ)
// =====================================================================================

private fun showLiveQrScannerDialog(
    activity: android.app.Activity,
    expectedCodes: List<String>?,
    onResult: (String?) -> Unit,
    onPickFromGallery: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    val finished = AtomicBoolean(false)
    val executor = Executors.newSingleThreadScheduledExecutor()
    var camera: Camera? = null
    var torchOn = false
    var lastWarnedCode = ""
    var lastWarnTime = 0L

    val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val root = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    val textureView = TextureView(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    root.addView(textureView)

    val overlay = SupermarketScannerOverlayView(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    root.addView(overlay)

    // Thanh cảnh báo "Quét lộn mã" nổi bật màu đỏ trên màn hình camera
    val warningBanner = TextView(activity).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setBackgroundColor(Color.argb(235, 220, 38, 38)) // Đỏ nổi bật
        setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))
        visibility = View.GONE
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ).apply { topMargin = dp(activity, 64) }
        layoutParams = lp
    }
    root.addView(warningBanner)
    val hideBannerRunnable = Runnable { warningBanner.visibility = View.GONE }

    // Thanh tiêu đề trên cùng: Tiêu đề + Đèn Flash + Đóng
    val topBar = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.argb(190, 8, 13, 23))
        setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14))
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        )
    }

    val titleView = TextView(activity).apply {
        text = "Quét mã QR kiện hàng"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    topBar.addView(titleView)

    val flashBtn = Button(activity).apply {
        text = "Đèn Flash"
        setTextColor(Color.parseColor("#080D17"))
        setBackgroundColor(Color.parseColor("#A6F000"))
        setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6))
        setOnClickListener {
            runCatching {
                val cam = camera ?: return@setOnClickListener
                val params = cam.parameters
                torchOn = !torchOn
                params.flashMode = if (torchOn) {
                    Camera.Parameters.FLASH_MODE_TORCH
                } else {
                    Camera.Parameters.FLASH_MODE_OFF
                }
                cam.parameters = params
                text = if (torchOn) "Tắt Flash" else "Đèn Flash"
            }
        }
    }
    topBar.addView(flashBtn)

    val closeBtn = Button(activity).apply {
        text = "Đóng"
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(180, 55, 65, 81))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { leftMargin = dp(activity, 8) }
        layoutParams = lp
        setOnClickListener {
            if (finished.compareAndSet(false, true)) {
                runCatching { dialog.dismiss() }
                onResult(null)
            }
        }
    }
    topBar.addView(closeBtn)
    root.addView(topBar)

    // Thanh hướng dẫn dưới cùng + nút quét từ ảnh đã lưu
    val bottomBar = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.argb(210, 8, 13, 23))
        setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 24))
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
    }

    val statusView = TextView(activity).apply {
        text = "Đưa mã QR vào khung ngắm xanh ở giữa màn hình.\nMáy sẽ tự động BÍP và xác nhận ngay khi đọc được mã QR thật."
        setTextColor(Color.parseColor("#F5F7FA"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
    }
    bottomBar.addView(statusView)

    val galleryBtn = Button(activity).apply {
        text = "Quét từ ảnh tem QR đã lưu trong máy"
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#1F2937"))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(activity, 12) }
        layoutParams = lp
        setOnClickListener {
            if (finished.compareAndSet(false, true)) {
                runCatching { dialog.dismiss() }
                onPickFromGallery()
            }
        }
    }
    bottomBar.addView(galleryBtn)
    root.addView(bottomBar)

    val autoFocusFuture = arrayOfNulls<java.util.concurrent.ScheduledFuture<*>>(1)

    fun releaseCameraAndExecutor() {
        runCatching { autoFocusFuture[0]?.cancel(true) }
        runCatching { executor.shutdownNow() }
        runCatching {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
            camera = null
        }
    }

    dialog.setOnDismissListener {
        releaseCameraAndExecutor()
        if (finished.compareAndSet(false, true)) {
            onResult(null)
        }
    }

    // Chạm vào màn hình để lấy nét lại (AutoFocus)
    textureView.setOnClickListener {
        runCatching { camera?.autoFocus(null) }
    }

    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            try {
                val cam = Camera.open()
                camera = cam
                cam.setDisplayOrientation(90)
                val params = cam.parameters
                val focusModes = params.supportedFocusModes ?: emptyList()
                when {
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in focusModes ->
                        params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in focusModes ->
                        params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                    Camera.Parameters.FOCUS_MODE_AUTO in focusModes ->
                        params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }
                val sizes = params.supportedPreviewSizes ?: emptyList()
                val optimalSize = sizes.minByOrNull { s ->
                    abs(s.width - 1280) + abs(s.height - 720)
                }
                if (optimalSize != null) {
                    params.setPreviewSize(optimalSize.width, optimalSize.height)
                }
                cam.parameters = params
                cam.setPreviewTexture(surface)
                cam.startPreview()

                // Tự động lấy nét định kỳ mỗi 2 giây để quét ảnh tem trên màn hình/giấy luôn sắc nét
                if (Camera.Parameters.FOCUS_MODE_AUTO in focusModes) {
                    autoFocusFuture[0] = executor.scheduleWithFixedDelay(
                        {
                            if (!finished.get()) {
                                runCatching { camera?.autoFocus(null) }
                            }
                        },
                        1500L,
                        2200L,
                        TimeUnit.MILLISECONDS,
                    )
                }
            } catch (e: Throwable) {
                if (finished.compareAndSet(false, true)) {
                    runCatching { dialog.dismiss() }
                    onError(IllegalStateException("Không khởi động được Camera: ${e.message}", e))
                }
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            releaseCameraAndExecutor()
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    // Vòng lặp quét khung hình trực tiếp từ TextureView mỗi 160ms (bắt đầu sau 450ms)
    val mainHandler = Handler(Looper.getMainLooper())
    val scanBusy = AtomicBoolean(false)
    executor.scheduleWithFixedDelay(
        {
            if (finished.get() || !scanBusy.compareAndSet(false, true)) return@scheduleWithFixedDelay
            val holder = arrayOfNulls<Bitmap>(1)
            val snapLatch = CountDownLatch(1)
            mainHandler.post {
                try {
                    if (textureView.isAvailable && !finished.get()) {
                        val vw = textureView.width
                        val vh = textureView.height
                        if (vw > 0 && vh > 0) {
                            // Giữ NGUYÊN tỉ lệ hiển thị (Aspect Ratio), tránh co dẹt hình vuông thành chữ nhật
                            val maxDim = 640
                            val scale = maxDim.toFloat() / kotlin.math.max(vw, vh)
                            val targetW = (vw * scale).toInt().coerceAtLeast(64)
                            val targetH = (vh * scale).toInt().coerceAtLeast(64)
                            holder[0] = textureView.getBitmap(targetW, targetH)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    snapLatch.countDown()
                }
            }
            snapLatch.await(300, TimeUnit.MILLISECONDS)
            val bmp = holder[0]
            try {
                if (bmp != null && !finished.get() && isUsableFrame(bmp)) {
                    val decoded = decodeBitmapQr(bmp)
                    if (!decoded.isNullOrBlank()) {
                        val clean = extractScanCode(decoded)
                        if (clean.isNotBlank() && isValidLogisticsQr(clean)) {
                            // Kiểm tra xem mã vừa quét có thuộc danh sách kiện cần nhận của đơn không
                            val isMatch = expectedCodes.isNullOrEmpty() || expectedCodes.any { exp ->
                                val cleanExp = extractScanCode(exp).uppercase()
                                cleanExp == clean.uppercase() || clean.uppercase().contains(cleanExp) || cleanExp.contains(clean.uppercase())
                            }
                            if (isMatch) {
                                if (finished.compareAndSet(false, true)) {
                                    playBeep()
                                    mainHandler.post {
                                        runCatching { dialog.dismiss() }
                                        onResult(clean)
                                    }
                                }
                            } else {
                                // Quét lộn mã! Phát âm báo lỗi và hiện cảnh báo đỏ, KHÔNG đóng camera
                                val now = System.currentTimeMillis()
                                if (clean != lastWarnedCode || now - lastWarnTime > 2000L) {
                                    lastWarnedCode = clean
                                    lastWarnTime = now
                                    playWrongCodeBeep()
                                    mainHandler.post {
                                        warningBanner.text = "⚠️ QUÉT LỘN MÃ: $clean\nMã này không thuộc kiện của đơn hàng! Vui lòng quét đúng tem kiện."
                                        warningBanner.visibility = View.VISIBLE
                                        mainHandler.removeCallbacks(hideBannerRunnable)
                                        mainHandler.postDelayed(hideBannerRunnable, 3500L)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                bmp?.recycle()
                scanBusy.set(false)
            }
        },
        450L,
        160L,
        TimeUnit.MILLISECONDS,
    )

    dialog.setContentView(root)
    dialog.show()
}

private val LOGISTICS_CODE_REGEX = Regex("^(KIEN|XUAT|LH|SO|PK|WH)/[A-Za-z0-9/_-]+$", RegexOption.IGNORE_CASE)

private fun isValidLogisticsQr(code: String): Boolean {
    val trimmed = code.trim()
    if (trimmed.length < 5) return false
    if ("/lab/track/" in trimmed) return true
    if (LOGISTICS_CODE_REGEX.matches(trimmed)) return true
    if (trimmed.startsWith("KIEN/", ignoreCase = true) || trimmed.startsWith("XUAT/", ignoreCase = true)) return true
    // Cho phép mã có cả chữ lẫn số dài tối thiểu 6 ký tự
    val hasLetter = trimmed.any { it.isLetter() }
    val hasDigit = trimmed.any { it.isDigit() }
    return hasLetter && hasDigit && trimmed.length >= 6
}

private fun isUsableFrame(bmp: Bitmap): Boolean {
    val w = bmp.width
    val h = bmp.height
    if (w < 64 || h < 64) return false
    var sumLuma = 0L
    var minLuma = 255
    var maxLuma = 0
    val step = (w / 16).coerceAtLeast(1)
    var count = 0
    for (y in (h / 4) until (3 * h / 4) step step) {
        for (x in (w / 4) until (3 * w / 4) step step) {
            val px = bmp.getPixel(x, y)
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val luma = (r * 77 + g * 150 + b * 29) ushr 8
            sumLuma += luma
            if (luma < minLuma) minLuma = luma
            if (luma > maxLuma) maxLuma = luma
            count++
        }
    }
    if (count == 0) return false
    val avgLuma = sumLuma / count
    // Khung hình quá tối (màn đen khi camera đang bật) hoặc độ tương phản quá kém -> bỏ qua
    return avgLuma in 8..248 && (maxLuma - minLuma) >= 15
}

private fun decodeBitmapQr(bmp: Bitmap): String? {
    val w = bmp.width
    val h = bmp.height
    if (w < 21 || h < 21) return null

    // 1. Thử vùng khung ngắm trung tâm trước (70% diện tích giữa) để nhanh và ít nhiễu nền
    val cropSize = (min(w, h) * 0.72f).toInt().coerceAtLeast(21)
    val startX = (w - cropSize) / 2
    val startY = (h - cropSize) / 2
    val centerPixels = IntArray(cropSize * cropSize)
    bmp.getPixels(centerPixels, 0, cropSize, startX, startY, cropSize, cropSize)
    decodeQrFromArgb(centerPixels, cropSize, cropSize)?.let { return it }

    // 2. Thử toàn bộ khung hình (thu nhỏ về tối đa 640px nếu ảnh từ thư viện quá lớn)
    val maxDim = 640
    val targetBmp = if (w > maxDim || h > maxDim) {
        val scale = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val nw = (w * scale).toInt().coerceAtLeast(21)
        val nh = (h * scale).toInt().coerceAtLeast(21)
        Bitmap.createScaledBitmap(bmp, nw, nh, true)
    } else {
        bmp
    }
    return try {
        val tw = targetBmp.width
        val th = targetBmp.height
        val fullPixels = IntArray(tw * th)
        targetBmp.getPixels(fullPixels, 0, tw, 0, 0, tw, th)
        decodeQrFromArgb(fullPixels, tw, th)
    } finally {
        if (targetBmp !== bmp) targetBmp.recycle()
    }
}

private fun playBeep() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 250L)
    }
}

private fun playWrongCodeBeep() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 320)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 400L)
    }
}

/**
 * Khung ngắm máy quét siêu thị: nền tối xung quanh, ô vuông trong suốt ở giữa,
 * 4 góc xanh neon (`#A6F000`) và tia quét laser chạy dọc khung.
 */
private class SupermarketScannerOverlayView(context: Context) : View(context) {
    private val maskPaint = Paint().apply {
        color = Color.argb(145, 8, 13, 23)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(context, 1).toFloat()
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A6F000")
        style = Paint.Style.STROKE
        strokeWidth = dp(context, 4).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A6F000")
        style = Paint.Style.FILL
        strokeWidth = dp(context, 3).toFloat()
    }
    private var laserFraction = 0.1f
    private var laserDirection = 1f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val boxSize = min(w, h) * 0.68f
        val left = (w - boxSize) / 2f
        val top = (h - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize

        // 4 mảng tối bao quanh khung ngắm
        canvas.drawRect(0f, 0f, w, top, maskPaint)
        canvas.drawRect(0f, bottom, w, h, maskPaint)
        canvas.drawRect(0f, top, left, bottom, maskPaint)
        canvas.drawRect(right, top, w, bottom, maskPaint)

        // Viền mảnh khung ngắm
        canvas.drawRect(left, top, right, bottom, borderPaint)

        // 4 góc chữ L màu xanh lime
        val cornerLen = boxSize * 0.14f
        // Trên-trái
        canvas.drawLine(left, top, left + cornerLen, top, cornerPaint)
        canvas.drawLine(left, top, left, top + cornerLen, cornerPaint)
        // Trên-phải
        canvas.drawLine(right - cornerLen, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLen, cornerPaint)
        // Dưới-trái
        canvas.drawLine(left, bottom - cornerLen, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + cornerLen, bottom, cornerPaint)
        // Dưới-phải
        canvas.drawLine(right - cornerLen, bottom, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom - cornerLen, right, bottom, cornerPaint)

        // Tia laser chạy lên xuống
        val laserY = top + boxSize * laserFraction
        canvas.drawLine(left + 12f, laserY, right - 12f, laserY, laserPaint)

        laserFraction += 0.025f * laserDirection
        if (laserFraction >= 0.92f) {
            laserFraction = 0.92f
            laserDirection = -1f
        } else if (laserFraction <= 0.08f) {
            laserFraction = 0.08f
            laserDirection = 1f
        }
        postInvalidateDelayed(30L)
    }
}

private fun dp(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density).toInt()
}

// =====================================================================================
// VẼ TEM QR RA BITMAP SẮC NÉT (CHO CẢ IN ẤN LẪN LƯU ẢNH PNG)
// =====================================================================================

private fun renderLabelSheetBitmap(labels: List<PrintableQrLabel>): Bitmap {
    val cardW = 840
    val cardH = 420
    val pad = 24
    val totalH = pad + labels.size * (cardH + pad)
    val sheet = Bitmap.createBitmap(cardW + pad * 2, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.parseColor("#F3F4F6"))

    labels.forEachIndexed { idx, label ->
        val cardBmp = renderSingleLabelBitmap(label, cardW, cardH)
        val top = pad + idx * (cardH + pad)
        canvas.drawBitmap(cardBmp, pad.toFloat(), top.toFloat(), null)
        cardBmp.recycle()
    }
    return sheet
}

private fun renderSingleLabelBitmap(
    label: PrintableQrLabel,
    width: Int = 840,
    height: Int = 420,
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawRect(2f, 2f, width - 2f, height - 2f, borderPaint)

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(label.header, 28f, 44f, headerPaint)

    val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(label.indexLabel, width - 28f, 44f, indexPaint)

    canvas.drawLine(24f, 60f, width - 24f, 60f, borderPaint.apply { strokeWidth = 2f })

    // Vẽ mã QR bên trái (chuẩn quiet zone 3 ô trắng xung quanh)
    val matrix = encodeQrMatrix(label.qrContent.ifBlank { label.primaryCode })
    val qrBoxSize = 296
    val qrLeft = 28
    val qrTop = 82
    val quietZone = 3
    val totalModules = matrix.size + quietZone * 2
    val cell = qrBoxSize / totalModules
    val actualQrSize = cell * totalModules
    val blackPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    for (r in matrix.indices) {
        for (c in matrix[r].indices) {
            if (matrix[r][c]) {
                val x0 = qrLeft + (c + quietZone) * cell
                val y0 = qrTop + (r + quietZone) * cell
                canvas.drawRect(
                    x0.toFloat(),
                    y0.toFloat(),
                    (x0 + cell).toFloat(),
                    (y0 + cell).toFloat(),
                    blackPaint,
                )
            }
        }
    }

    // Chữ bên phải mã QR
    val textLeft = (qrLeft + actualQrSize + 28).toFloat()
    val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 38f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    canvas.drawText(label.primaryCode, textLeft, 136f, codePaint)

    var nextY = 180f
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#374151")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    if (!label.secondaryCode.isNullOrBlank()) {
        canvas.drawText(label.secondaryCode, textLeft, nextY, subPaint)
        nextY += 38f
    }
    canvas.drawText(label.subtitle.take(36), textLeft, nextY, subPaint)
    nextY += 38f

    if (label.meta.isNotBlank()) {
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B5563")
            textSize = 21f
        }
        canvas.drawText(label.meta.take(42), textLeft, nextY, metaPaint)
        nextY += 38f
    }

    val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F766E")
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("Quét mã QR bằng Camera App Kho", textLeft, height - 36f, hintPaint)

    return bmp
}

