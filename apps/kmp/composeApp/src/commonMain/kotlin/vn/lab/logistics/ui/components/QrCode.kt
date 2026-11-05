package vn.lab.logistics.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * =====================================================================================
 * Bộ sinh mã QR thuần Kotlin (ISO/IEC 18004 — Byte mode, ECC Level L, Version 1..4)
 * + Composable [QrCodeBox] vẽ trực tiếp lên [Canvas] + hàm chuẩn hoá mã quét [extractScanCode].
 *
 * VÌ SAO THUẦN KOTLIN (`commonMain`, 0 phụ thuộc ngoài):
 *  - Chạy đồng nhất trên cả Android APK lẫn Desktop JVM, build `--offline` 100% không cần mạng;
 *  - Sinh đúng ma trận QR chuẩn quốc tế ISO/IEC 18004 để camera điện thoại hoặc máy quét mã vạch
 *    đều đọc được ngay trên màn in tem Nhận (`KIEN/26/…`) và tem Xuất (`XUAT/26/…`).
 * =====================================================================================
 */

private val QR_CODE_REGEX = Regex("((?:KIEN|XUAT)/[A-Za-z0-9/_-]+)", RegexOption.IGNORE_CASE)

/**
 * Trích mã kiện (`KIEN/26/…`) hoặc mã tem xuất (`XUAT/26/…`) từ chuỗi bất kỳ do máy quét / camera
 * đọc được (mã thuần, URL tra cứu `/lab/track/<token>/KIEN/26/…`, hoặc chuỗi nhiều dòng).
 */
fun extractScanCode(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if ("/lab/track/" in trimmed) {
        val afterTrack = trimmed.substringAfter("/lab/track/")
            .substringBefore("?")
            .substringBefore("#")
        if ("/" in afterTrack) {
            val code = afterTrack.substringAfter("/").trim().trim('/')
            if (code.isNotEmpty()) return code
        }
    }
    val match = QR_CODE_REGEX.find(trimmed)
    if (match != null) {
        return match.value.uppercase()
    }
    return trimmed
}

/**
 * Hiển thị mã QR sắc nét trên nền trắng (có quiet zone chuẩn 2 ô) cho chuỗi [content].
 */
@Composable
fun QrCodeBox(
    content: String,
    size: Dp = 136.dp,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) { encodeQrMatrix(content.ifBlank { "LAB" }) }
    Box(
        modifier = modifier
            .size(size)
            .background(Color.White)
            .border(1.dp, Color(0xFFD0D5DD))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val n = matrix.size
            if (n == 0) return@Canvas
            val quietZone = 2
            val totalModules = n + quietZone * 2
            val cell = minOf(this.size.width, this.size.height) / totalModules.toFloat()
            drawRect(color = Color.White, size = this.size)
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset((c + quietZone) * cell, (r + quietZone) * cell),
                            size = Size(cell * 1.02f, cell * 1.02f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sinh ma trận QR `size x size` (`true` = ô đen, `false` = ô trắng) theo chuẩn ISO/IEC 18004
 * (Byte mode `0100`, Error Correction Level L).
 */
fun encodeQrMatrix(content: String): Array<BooleanArray> {
    val bytes = content.encodeToByteArray()
    val spec = selectQrVersion(bytes.size)
    val dataCodewords = buildDataCodewords(bytes, spec.dataCodewords)
    val ecCodewords = computeReedSolomon(dataCodewords, spec.ecCodewords)
    val allCodewords = IntArray(dataCodewords.size + ecCodewords.size)
    dataCodewords.copyInto(allCodewords, 0)
    ecCodewords.copyInto(allCodewords, dataCodewords.size)

    val size = 17 + 4 * spec.version
    val modules = Array(size) { BooleanArray(size) }
    val isFunction = Array(size) { BooleanArray(size) }

    placeFinderPattern(modules, isFunction, 0, 0)
    placeFinderPattern(modules, isFunction, 0, size - 7)
    placeFinderPattern(modules, isFunction, size - 7, 0)

    // Timing patterns
    for (i in 8 until size - 8) {
        val dark = i % 2 == 0
        modules[6][i] = dark
        isFunction[6][i] = true
        modules[i][6] = dark
        isFunction[i][6] = true
    }

    // Alignment pattern cho Version 2..4
    if (spec.version >= 2) {
        val center = size - 7
        placeAlignmentPattern(modules, isFunction, center, center)
    }

    // Dark module + vùng dự trữ Format Info
    modules[4 * spec.version + 9][8] = true
    isFunction[4 * spec.version + 9][8] = true
    reserveFormatAreas(isFunction, size)

    // Đặt chuỗi bit zig-zag
    placeDataBits(modules, isFunction, allCodewords)

    // Áp Mask 0: (r + c) % 2 == 0
    for (r in 0 until size) {
        for (c in 0 until size) {
            if (!isFunction[r][c] && (r + c) % 2 == 0) {
                modules[r][c] = !modules[r][c]
            }
        }
    }

    // Ghi 15 bit Format Info (ECC L = 01, Mask 0 = 000)
    writeFormatBits(modules, size, mask = 0)
    return modules
}

private data class QrVersionSpec(
    val version: Int,
    val dataCodewords: Int,
    val ecCodewords: Int,
)

private val QR_SPECS = listOf(
    QrVersionSpec(version = 1, dataCodewords = 19, ecCodewords = 7),
    QrVersionSpec(version = 2, dataCodewords = 34, ecCodewords = 10),
    QrVersionSpec(version = 3, dataCodewords = 55, ecCodewords = 15),
    QrVersionSpec(version = 4, dataCodewords = 80, ecCodewords = 20),
)

private fun selectQrVersion(byteLen: Int): QrVersionSpec {
    return QR_SPECS.firstOrNull { byteLen + 2 <= it.dataCodewords } ?: QR_SPECS.last()
}

private fun buildDataCodewords(rawBytes: ByteArray, capacity: Int): IntArray {
    val maxBytes = (capacity - 2).coerceAtLeast(1)
    val bytes = if (rawBytes.size > maxBytes) rawBytes.copyOf(maxBytes) else rawBytes
    val bits = ArrayList<Int>(capacity * 8)

    fun appendBits(value: Int, length: Int) {
        for (i in length - 1 downTo 0) {
            bits.add((value ushr i) and 1)
        }
    }

    // Byte mode indicator (0100) + 8-bit length
    appendBits(0b0100, 4)
    appendBits(bytes.size, 8)
    for (b in bytes) {
        appendBits(b.toInt() and 0xFF, 8)
    }

    // Terminator (tối đa 4 bit 0)
    val maxBits = capacity * 8
    val terminator = minOf(4, maxBits - bits.size)
    repeat(terminator) { bits.add(0) }
    while (bits.size % 8 != 0) {
        bits.add(0)
    }

    val result = IntArray(capacity)
    val byteCount = bits.size / 8
    for (i in 0 until byteCount) {
        var v = 0
        for (b in 0 until 8) {
            v = (v shl 1) or bits[i * 8 + b]
        }
        result[i] = v
    }
    var padIndex = 0
    val pads = intArrayOf(0xEC, 0x11)
    for (i in byteCount until capacity) {
        result[i] = pads[padIndex % 2]
        padIndex++
    }
    return result
}

private fun computeReedSolomon(data: IntArray, ecCount: Int): IntArray {
    val exp = IntArray(512)
    val log = IntArray(256)
    var x = 1
    for (i in 0 until 255) {
        exp[i] = x
        log[x] = i
        x = x shl 1
        if (x and 0x100 != 0) x = x xor 0x11D
    }
    for (i in 255 until 512) {
        exp[i] = exp[i - 255]
    }

    fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return exp[log[a] + log[b]]
    }

    var gen = intArrayOf(1)
    for (i in 0 until ecCount) {
        val next = IntArray(gen.size + 1)
        val root = exp[i]
        for (j in gen.indices) {
            next[j] = next[j] xor gen[j]
            next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
        }
        gen = next
    }

    val rem = IntArray(ecCount)
    for (b in data) {
        val factor = b xor rem[0]
        for (i in 0 until ecCount - 1) {
            rem[i] = rem[i + 1] xor gfMul(gen[i + 1], factor)
        }
        rem[ecCount - 1] = gfMul(gen[ecCount], factor)
    }
    return rem
}

private fun placeFinderPattern(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    top: Int,
    left: Int,
) {
    val size = modules.size
    for (dr in -1..7) {
        for (dc in -1..7) {
            val r = top + dr
            val c = left + dc
            if (r !in 0 until size || c !in 0 until size) continue
            val dark = (dr in 0..6 && (dc == 0 || dc == 6)) ||
                (dc in 0..6 && (dr == 0 || dr == 6)) ||
                (dr in 2..4 && dc in 2..4)
            modules[r][c] = dark
            isFunction[r][c] = true
        }
    }
}

private fun placeAlignmentPattern(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    centerR: Int,
    centerC: Int,
) {
    for (dr in -2..2) {
        for (dc in -2..2) {
            val r = centerR + dr
            val c = centerC + dc
            val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
            modules[r][c] = dark
            isFunction[r][c] = true
        }
    }
}

private fun reserveFormatAreas(isFunction: Array<BooleanArray>, size: Int) {
    for (i in 0..8) {
        isFunction[8][i] = true
        isFunction[i][8] = true
    }
    for (i in 0..7) {
        isFunction[8][size - 1 - i] = true
        isFunction[size - 1 - i][8] = true
    }
}

private fun placeDataBits(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    codewords: IntArray,
) {
    val size = modules.size
    var bitIndex = 0
    val totalBits = codewords.size * 8
    var upward = true
    var col = size - 1
    while (col > 0) {
        if (col == 6) col--
        for (step in 0 until size) {
            val r = if (upward) size - 1 - step else step
            for (delta in 0..1) {
                val c = col - delta
                if (!isFunction[r][c]) {
                    val dark = if (bitIndex < totalBits) {
                        val byteVal = codewords[bitIndex / 8]
                        val shift = 7 - (bitIndex % 8)
                        ((byteVal ushr shift) and 1) == 1
                    } else {
                        false
                    }
                    modules[r][c] = dark
                    bitIndex++
                }
            }
        }
        upward = !upward
        col -= 2
    }
}

private fun writeFormatBits(modules: Array<BooleanArray>, size: Int, mask: Int) {
    // ECC Level L = 01
    val data = (0b01 shl 3) or mask
    var rem = data shl 10
    for (i in 14 downTo 10) {
        if (((rem ushr i) and 1) != 0) {
            rem = rem xor (0x537 shl (i - 10))
        }
    }
    val bits = ((data shl 10) or rem) xor 0x5412

    fun bit(i: Int): Boolean = ((bits ushr i) and 1) == 1

    // Quanh finder trên-trái (chuẩn ISO/IEC 18004 Fig. 25: bit 14 ở (8,0) .. bit 0 ở (0,8), bỏ qua timing ở 6)
    for (i in 0..5) modules[8][i] = bit(14 - i)
    modules[8][7] = bit(8)
    modules[8][8] = bit(7)
    modules[7][8] = bit(6)
    for (i in 0..5) modules[5 - i][8] = bit(5 - i)

    // Quanh finder dưới-trái (bit 14..8) & trên-phải (bit 7..0)
    for (i in 0..6) modules[size - 1 - i][8] = bit(14 - i)
    for (i in 0..7) modules[8][size - 8 + i] = bit(7 - i)
}

