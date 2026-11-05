package vn.lab.logistics.ui.platform

/**
 * =====================================================================================
 * CHỌN ẢNH TỪ MÁY — `expect` ở `commonMain`, `actual` (AWT `FileDialog`) ở `desktopMain`.
 *
 * Nguyên tắc (agents/fe/skills/02 §5 + 06 §2): `commonMain` **không** được import `java.awt.*`.
 * Màn hình chỉ gọi hàm này, nhận `ByteArray` trong RAM rồi đưa cho ViewModel nén.
 *
 * KHÔNG ghi ra đĩa: hàm chỉ ĐỌC tệp người dùng chọn và trả bytes. Ảnh/chữ ký sau đó sống
 * trong RAM tới lúc gửi `capture` (yêu cầu cứng của nhiệm vụ C1–C3).
 *
 * ⚠️ Hàm CHẶN (blocking) trong lúc hộp thoại hệ điều hành mở. Gọi nó trong `scope.launch`
 * của Compose (chạy trên luồng EDT — đúng luồng AWT yêu cầu), đừng gọi trong composition.
 *
 * @return nội dung tệp, hoặc `null` khi người dùng bấm **Huỷ**.
 * @throws IllegalStateException khi đọc tệp lỗi (UI hiện câu tiếng Việt, không sập app).
 * =====================================================================================
 */
expect fun pickImageFile(): ByteArray?

/**
 * CHỤP ẢNH TRỰC TIẾP bằng camera (yêu cầu 2026-11-03: tài xế chụp tại chỗ, không chỉ chọn ảnh có sẵn).
 *
 * Cùng hợp đồng với [pickImageFile]: trả `ByteArray` JPEG trong RAM, `null` khi người dùng bấm
 * Huỷ/Back trong màn camera, ném `IllegalStateException` (câu tiếng Việt) khi không mở được camera.
 *
 *  - **Android**: mở ứng dụng camera của hệ thống (`ActivityResultContracts.TakePicture`) — ảnh chụp
 *    ghi tạm vào cache rồi bị XOÁ ngay sau khi đọc vào RAM.
 *  - **Desktop**: không hỗ trợ (`platformDefaults().cameraCaptureSupported == false`), trả `null`.
 *
 * Giống [pickImageFile] trên Android: là hàm CHẶN, phải gọi ở luồng nền.
 */
expect fun captureImageFromCamera(): ByteArray?
