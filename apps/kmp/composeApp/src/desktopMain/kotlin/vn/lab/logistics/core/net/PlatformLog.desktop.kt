package vn.lab.logistics.core.net

/**
 * Bản `actual` cho DESKTOP: in ra `stderr`.
 *
 * Vì sao `stderr` chứ không `stdout`: `selfCheck` in báo cáo của nó ra `stdout`; log mạng đi
 * kênh riêng để không trộn vào kết quả tự kiểm tra (và vẫn đọc được khi chạy trong CI).
 */
actual fun logTechnicalInfo(tag: String, message: String) {
    System.err.println("[$tag] $message")
}

actual fun logTechnicalError(tag: String, message: String, error: Throwable?) {
    System.err.println("[$tag] $message")
    error?.printStackTrace()
}

/** Desktop: không có NetworkSecurityPolicy/cleartext để chẩn đoán — chỉ cần exception thật. */
actual fun logNetworkFailure(url: String, error: Throwable) {
    logTechnicalError(APP_LOG_TAG, "✗ $url thất bại: $error", error)
}
