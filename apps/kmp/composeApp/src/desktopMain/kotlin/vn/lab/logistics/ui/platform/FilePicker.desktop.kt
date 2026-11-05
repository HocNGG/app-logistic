package vn.lab.logistics.ui.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * =====================================================================================
 * `actual` desktop: hộp thoại chọn tệp NATIVE của Windows/macOS/Linux (`java.awt.FileDialog`).
 *
 * Vì sao `FileDialog` chứ không `JFileChooser`: `FileDialog` là hộp thoại của hệ điều hành
 * (giống hộp thoại "Open" quen thuộc), nhớ thư mục gần nhất, và trên Windows hiện đúng kiểu
 * Explorer — nhìn chuyên nghiệp trong video demo. Token màn hình không phải sửa nếu đổi impl.
 *
 * `FileDialog(null as Frame?, …)` = hộp thoại không gắn cứng vào cửa sổ Compose: không cần
 * truyền `ComposeWindow` xuống tận ViewModel. Trình quản lý cửa sổ vẫn giữ nó ở trên cùng.
 * =====================================================================================
 */
actual fun pickImageFile(): ByteArray? {
    val dialog = FileDialog(null as Frame?, "Chọn ảnh hàng hoá (JPEG/PNG)", FileDialog.LOAD)
    try {
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
        }
        dialog.isVisible = true // CHẶN tới khi người dùng chọn hoặc huỷ.

        val fileName = dialog.file ?: return null // Huỷ
        val directory = dialog.directory ?: return null
        val file = File(directory, fileName)
        return try {
            file.readBytes()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Không đọc được tệp \"$fileName\". Vui lòng chọn ảnh khác.",
                e,
            )
        }
    } finally {
        dialog.dispose()
    }
}

/** Desktop không có luồng chụp ảnh trực tiếp — UI ẩn nút "Chụp ảnh" nhờ `cameraCaptureSupported`. */
actual fun captureImageFromCamera(): ByteArray? = null
