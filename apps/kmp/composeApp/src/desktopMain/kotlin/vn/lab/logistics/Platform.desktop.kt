package vn.lab.logistics

import vn.lab.logistics.core.settings.AppSettings
import vn.lab.logistics.design.Tokens

/** Bản `actual` cho desktop — được biên dịch cùng target JVM. */
actual fun platformInfo(): String =
    "Desktop / JVM ${System.getProperty("java.version")} · ${System.getProperty("os.name")}"

/**
 * =====================================================================================
 * Bản `actual` cho DESKTOP của [platformDefaults] — hành vi GIỮ NGUYÊN như trước Wave C:
 *
 *  - `baseUrl` = [AppSettings.DEFAULT_BASE_URL] (KHÔNG chép lại literal ở đây — self-check
 *    khối 8 grep toàn bộ `src/` và đòi địa chỉ lab chỉ xuất hiện trong `AppSettings.kt`).
 *  - `demoStartWidthDp` = ngưỡng 2-pane rộng 1280dp như cũ.
 *  - `imagePickerRunsOnCallerMainThread` = true: `java.awt.FileDialog` phải mở trên EDT và
 *    chặn tới khi người dùng chọn xong — đó là hành vi đã kiểm chứng của bản desktop.
 *  - `useRealApiByDefault` = **false**: desktop KHÔNG đổi hành vi — mở app vẫn ở dữ liệu Mẫu
 *    (an toàn cho buổi demo), bật Odoo thật là hành động có chủ đích trên thanh demo.
 * =====================================================================================
 */
actual fun platformDefaults(): PlatformDefaults = PlatformDefaults(
    baseUrl = AppSettings.DEFAULT_BASE_URL,
    demoStartWidthDp = Tokens.BREAKPOINT_SPLIT_WIDE_DP,
    imagePickerRunsOnCallerMainThread = true,
    useRealApiByDefault = false,
)

/** Đồng hồ của JVM desktop — dùng để kiểm hạn token (`AppSettings.hasValidSession`). */
actual fun nowEpochMillis(): Long = System.currentTimeMillis()
