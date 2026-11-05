package vn.lab.logistics.core.media

/**
 * =====================================================================================
 * Base64 ENCODER thuần Kotlin — dùng cho payload capture của `lab_api`.
 *
 * VÌ SAO TỰ VIẾT THAY VÌ DÙNG THƯ VIỆN:
 *  1. `kotlin.io.encoding.Base64` còn là API thực nghiệm (`@ExperimentalEncodingApi`) ở nhiều
 *     bản Kotlin ⇒ bật lên là thêm opt-in + cảnh báo khi nâng Kotlin.
 *  2. `java.util.Base64` KHÔNG được vào `commonMain` (Luật: API Java chỉ ở `desktopMain`).
 *  3. `android.util.Base64` không tồn tại trên desktop.
 * Hàm dưới đây chỉ là bảng mã 64 ký tự — 40 dòng, không phụ thuộc gì, test được ở `commonTest`.
 *
 * ⚠️ Chỉ ENCODE. App không cần decode: ảnh nằm trong RAM, base64 chỉ để đi lên server.
 * Backend `addons/lab_api/tools.py::giai_ma_anh()` dùng `base64.b64decode(validate=True)`
 * ⇒ encoder này PHẢI xuất bảng chuẩn (A–Z a–z 0–9 + /) và padding `=` cho đủ bội số 4.
 * =====================================================================================
 */
object Base64Codec {

    private const val ALPHABET: String =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Mã hoá [bytes] thành chuỗi Base64 chuẩn (có padding `=`). Mảng rỗng ⇒ `""`. */
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        val builder = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        // 3 byte → 4 ký tự (24 bit → 4 nhóm 6 bit).
        while (i + 2 < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val b2 = bytes[i + 2].toInt() and 0xFF
            val triplet = (b0 shl 16) or (b1 shl 8) or b2
            builder.append(ALPHABET[(triplet ushr 18) and 0x3F])
            builder.append(ALPHABET[(triplet ushr 12) and 0x3F])
            builder.append(ALPHABET[(triplet ushr 6) and 0x3F])
            builder.append(ALPHABET[triplet and 0x3F])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val b0 = bytes[i].toInt() and 0xFF
                val triplet = b0 shl 16
                builder.append(ALPHABET[(triplet ushr 18) and 0x3F])
                builder.append(ALPHABET[(triplet ushr 12) and 0x3F])
                builder.append("==")
            }

            2 -> {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = bytes[i + 1].toInt() and 0xFF
                val triplet = (b0 shl 16) or (b1 shl 8)
                builder.append(ALPHABET[(triplet ushr 18) and 0x3F])
                builder.append(ALPHABET[(triplet ushr 12) and 0x3F])
                builder.append(ALPHABET[(triplet ushr 6) and 0x3F])
                builder.append('=')
            }
        }
        return builder.toString()
    }
}
