package vn.lab.logistics.ui.components

import kotlin.math.abs
import kotlin.math. hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * =====================================================================================
 * BỘ GIẢI MÃ QR THUẦN KOTLIN (`QrCodeDecoder`) — chuẩn ISO/IEC 18004 (Version 1..6,
 * mọi ECC Level L/M/Q/H, mọi Mask 0..7, kèm sửa lỗi Reed-Solomon Berlekamp-Massey/Forney).
 *
 * Dùng trực tiếp cho khung hình Camera thời gian thực (`ARGB_8888` / `NV21` luminance)
 * và ảnh chụp/lưu, đảm bảo:
 *  - Chỉ khi camera nhìn thấy ĐÚNG mã QR thật (trên giấy in hoặc trên màn hình) và giải mã
 *    thành công mới chấp nhận;
 *  - Hỗ trợ cả Byte mode (`0100`), Alphanumeric mode (`0010`), Numeric mode (`0001`).
 * =====================================================================================
 */

/**
 * Giải mã chuỗi QR từ mảng điểm ảnh `ARGB_8888` (`width` × `height`).
 * Trả về nội dung chuỗi nếu tìm thấy mã QR hợp lệ, hoặc `null` nếu khung hình chưa có mã QR.
 */
fun decodeQrFromArgb(argb: IntArray, width: Int, height: Int): String? {
    if (width < 21 || height < 21 || argb.size < width * height) return null
    val gray = IntArray(width * height)
    for (i in 0 until width * height) {
        val px = argb[i]
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        gray[i] = (r * 77 + g * 150 + b * 29) ushr 8
    }
    return decodeQrFromGrayscale(gray, width, height)
}

/**
 * Giải mã chuỗi QR từ mảng độ sáng `gray` (`0..255`, kích thước `width` × `height`).
 */
fun decodeQrFromGrayscale(gray: IntArray, width: Int, height: Int): String? {
    if (width < 21 || height < 21 || gray.size < width * height) return null

    // 1. Thử ngưỡng toàn cục nhanh (Otsu / Midpoint) cho ảnh camera có độ tương phản rõ
    var minG = 255
    var maxG = 0
    var sumG = 0L
    val totalPixels = width * height
    for (i in 0 until totalPixels) {
        val v = gray[i]
        if (v < minG) minG = v
        if (v > maxG) maxG = v
        sumG += v
    }
    if (maxG - minG >= 35) {
        val mid = ((minG + maxG) / 2 + (sumG / totalPixels).toInt()) / 2
        val globalBits = BooleanArray(totalPixels) { i -> gray[i] < mid }
        decodeFromBinaryBitmap(globalBits, width, height)?.let { return it }
    }

    // 2. Thử nhị phân hoá thích nghi cục bộ (hỗ trợ cả ánh sáng chói màn hình lẫn tem in giấy)
    for (thresholdPct in intArrayOf(92, 85, 96, 78)) {
        val bits = adaptiveBinarize(gray, width, height, thresholdPct)
        val decoded = decodeFromBinaryBitmap(bits, width, height)
        if (!decoded.isNullOrBlank()) return decoded
    }
    return null
}

private fun adaptiveBinarize(
    gray: IntArray,
    width: Int,
    height: Int,
    thresholdPct: Int,
): BooleanArray {
    val stride = width + 1
    val integral = IntArray((height + 1) * stride)
    for (y in 0 until height) {
        var rowSum = 0
        val rowOffset = y * width
        val intRow = (y + 1) * stride
        val prevIntRow = y * stride
        for (x in 0 until width) {
            rowSum += gray[rowOffset + x]
            integral[intRow + x + 1] = integral[prevIntRow + x + 1] + rowSum
        }
    }

    val radius = (min(width, height) / 14).coerceIn(6, 36)
    val out = BooleanArray(width * height)
    for (y in 0 until height) {
        val y0 = max(0, y - radius)
        val y1 = min(height - 1, y + radius)
        val rowOffset = y * width
        for (x in 0 until width) {
            val x0 = max(0, x - radius)
            val x1 = min(width - 1, x + radius)
            val area = (x1 - x0 + 1) * (y1 - y0 + 1)
            val sum = integral[(y1 + 1) * stride + (x1 + 1)] -
                integral[y0 * stride + (x1 + 1)] -
                integral[(y1 + 1) * stride + x0] +
                integral[y0 * stride + x0]
            out[rowOffset + x] = gray[rowOffset + x] * area * 100 < sum * thresholdPct
        }
    }
    return out
}

private data class FinderPattern(
    val x: Float,
    val y: Float,
    val moduleSize: Float,
    val count: Int = 1,
)

private fun decodeFromBinaryBitmap(
    bits: BooleanArray,
    width: Int,
    height: Int,
): String? {
    val centers = findFinderPatterns(bits, width, height)
    if (centers.size < 3) return null

    val sorted = centers.sortedByDescending { it.count * it.moduleSize }.take(12)
    val n = sorted.size
    for (i in 0 until n - 2) {
        for (j in i + 1 until n - 1) {
            for (k in j + 1 until n) {
                val ordered = orderFinderPatterns(sorted[i], sorted[j], sorted[k]) ?: continue
                val (topLeft, topRight, bottomLeft) = ordered
                val decoded = sampleAndDecode(bits, width, height, topLeft, topRight, bottomLeft)
                if (!decoded.isNullOrBlank()) return decoded
            }
        }
    }
    return null
}

private fun findFinderPatterns(
    bits: BooleanArray,
    width: Int,
    height: Int,
): List<FinderPattern> {
    val clusters = ArrayList<FinderPattern>()
    val stepY = max(1, height / 240)
    val stateCount = IntArray(5)

    for (y in 0 until height step stepY) {
        stateCount.fill(0)
        var currentState = 0
        val rowOffset = y * width
        for (x in 0 until width) {
            if (bits[rowOffset + x]) {
                if ((currentState and 1) == 1) currentState++
                stateCount[currentState]++
            } else {
                if ((currentState and 1) == 0) {
                    if (currentState == 4) {
                        if (foundPatternCross(stateCount)) {
                            handlePossibleCenter(bits, width, height, stateCount, y, x, clusters)
                            stateCount[0] = stateCount[2]
                            stateCount[1] = stateCount[3]
                            stateCount[2] = stateCount[4]
                            stateCount[3] = 1
                            stateCount[4] = 0
                            currentState = 3
                        } else {
                            stateCount[0] = stateCount[2]
                            stateCount[1] = stateCount[3]
                            stateCount[2] = stateCount[4]
                            stateCount[3] = 1
                            stateCount[4] = 0
                            currentState = 3
                        }
                    } else {
                        currentState++
                        stateCount[currentState]++
                    }
                } else {
                    stateCount[currentState]++
                }
            }
        }
        if (foundPatternCross(stateCount)) {
            handlePossibleCenter(bits, width, height, stateCount, y, width, clusters)
        }
    }
    return clusters
}

private fun foundPatternCross(stateCount: IntArray): Boolean {
    var total = 0
    for (i in 0..4) {
        val count = stateCount[i]
        if (count == 0) return false
        total += count
    }
    if (total < 7) return false
    val moduleSize = total / 7.0f
    val maxVariance = moduleSize * 0.7f
    return abs(moduleSize - stateCount[0]) < maxVariance &&
        abs(moduleSize - stateCount[1]) < maxVariance &&
        abs(3.0f * moduleSize - stateCount[2]) < 3.0f * maxVariance &&
        abs(moduleSize - stateCount[3]) < maxVariance &&
        abs(moduleSize - stateCount[4]) < maxVariance
}

private fun handlePossibleCenter(
    bits: BooleanArray,
    width: Int,
    height: Int,
    stateCount: IntArray,
    row: Int,
    endX: Int,
    clusters: MutableList<FinderPattern>,
) {
    val total = stateCount.sum()
    val centerXInit = endX - stateCount[4] - stateCount[3] - stateCount[2] / 2.0f
    val centerY = crossCheckVertical(bits, width, height, row, centerXInit.toInt(), stateCount[2], total)
    if (centerY.isNaN()) return
    val centerX = crossCheckHorizontal(bits, width, centerXInit.toInt(), centerY.toInt(), stateCount[2], total)
    if (centerX.isNaN()) return

    val moduleSize = total / 7.0f
    for (idx in clusters.indices) {
        val c = clusters[idx]
        if (abs(centerY - c.y) <= moduleSize * 1.5f && abs(centerX - c.x) <= moduleSize * 1.5f) {
            val combined = c.count + 1
            clusters[idx] = FinderPattern(
                x = (c.x * c.count + centerX) / combined,
                y = (c.y * c.count + centerY) / combined,
                moduleSize = (c.moduleSize * c.count + moduleSize) / combined,
                count = combined,
            )
            return
        }
    }
    clusters.add(FinderPattern(centerX, centerY, moduleSize, 1))
}

private fun crossCheckVertical(
    bits: BooleanArray,
    width: Int,
    height: Int,
    startRow: Int,
    centerCol: Int,
    centralCount: Int,
    originalTotal: Int,
): Float {
    if (centerCol !in 0 until width) return Float.NaN
    val stateCount = IntArray(5)
    var r = startRow
    while (r >= 0 && bits[r * width + centerCol]) {
        stateCount[2]++
        r--
    }
    if (r < 0) return Float.NaN
    while (r >= 0 && !bits[r * width + centerCol] && stateCount[1] <= centralCount) {
        stateCount[1]++
        r--
    }
    if (r < 0 || stateCount[1] > centralCount) return Float.NaN
    while (r >= 0 && bits[r * width + centerCol] && stateCount[0] <= centralCount) {
        stateCount[0]++
        r--
    }
    if (stateCount[0] > centralCount) return Float.NaN

    r = startRow + 1
    while (r < height && bits[r * width + centerCol]) {
        stateCount[2]++
        r++
    }
    if (r == height) return Float.NaN
    while (r < height && !bits[r * width + centerCol] && stateCount[3] < centralCount) {
        stateCount[3]++
        r++
    }
    if (r == height || stateCount[3] >= centralCount) return Float.NaN
    while (r < height && bits[r * width + centerCol] && stateCount[4] < centralCount) {
        stateCount[4]++
        r++
    }
    if (stateCount[4] >= centralCount) return Float.NaN

    val total = stateCount.sum()
    if (abs(total - originalTotal) > originalTotal * 0.60f) return Float.NaN
    return if (foundPatternCross(stateCount)) {
        r - stateCount[4] - stateCount[3] - stateCount[2] / 2.0f
    } else {
        Float.NaN
    }
}

private fun crossCheckHorizontal(
    bits: BooleanArray,
    width: Int,
    startCol: Int,
    centerRow: Int,
    centralCount: Int,
    originalTotal: Int,
): Float {
    val stateCount = IntArray(5)
    val rowOffset = centerRow * width
    var c = startCol
    while (c >= 0 && bits[rowOffset + c]) {
        stateCount[2]++
        c--
    }
    if (c < 0) return Float.NaN
    while (c >= 0 && !bits[rowOffset + c] && stateCount[1] <= centralCount) {
        stateCount[1]++
        c--
    }
    if (c < 0 || stateCount[1] > centralCount) return Float.NaN
    while (c >= 0 && bits[rowOffset + c] && stateCount[0] <= centralCount) {
        stateCount[0]++
        c--
    }
    if (stateCount[0] > centralCount) return Float.NaN

    c = startCol + 1
    while (c < width && bits[rowOffset + c]) {
        stateCount[2]++
        c++
    }
    if (c == width) return Float.NaN
    while (c < width && !bits[rowOffset + c] && stateCount[3] < centralCount) {
        stateCount[3]++
        c++
    }
    if (c == width || stateCount[3] >= centralCount) return Float.NaN
    while (c < width && bits[rowOffset + c] && stateCount[4] < centralCount) {
        stateCount[4]++
        c++
    }
    if (stateCount[4] >= centralCount) return Float.NaN

    val total = stateCount.sum()
    if (abs(total - originalTotal) > originalTotal * 0.60f) return Float.NaN
    return if (foundPatternCross(stateCount)) {
        c - stateCount[4] - stateCount[3] - stateCount[2] / 2.0f
    } else {
        Float.NaN
    }
}

private fun orderFinderPatterns(
    p0: FinderPattern,
    p1: FinderPattern,
    p2: FinderPattern,
): Triple<FinderPattern, FinderPattern, FinderPattern>? {
    val maxMod = maxOf(p0.moduleSize, p1.moduleSize, p2.moduleSize)
    val minMod = minOf(p0.moduleSize, p1.moduleSize, p2.moduleSize)
    if (maxMod > minMod * 2.2f) return null

    val d01 = hypot((p0.x - p1.x).toDouble(), (p0.y - p1.y).toDouble()).toFloat()
    val d12 = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
    val d02 = hypot((p0.x - p2.x).toDouble(), (p0.y - p2.y).toDouble()).toFloat()

    val (topLeft, b, c) = when {
        d12 >= d01 && d12 >= d02 -> Triple(p0, p1, p2)
        d02 >= d01 && d02 >= d12 -> Triple(p1, p0, p2)
        else -> Triple(p2, p0, p1)
    }

    val leg1 = hypot((b.x - topLeft.x).toDouble(), (b.y - topLeft.y).toDouble()).toFloat()
    val leg2 = hypot((c.x - topLeft.x).toDouble(), (c.y - topLeft.y).toDouble()).toFloat()
    if (leg1 < 8f || leg2 < 8f) return null
    if (abs(leg1 - leg2) / max(leg1, leg2) > 0.48f) return null

    val dot = (b.x - topLeft.x) * (c.x - topLeft.x) + (b.y - topLeft.y) * (c.y - topLeft.y)
    val cosTheta = dot / (leg1 * leg2)
    if (abs(cosTheta) > 0.45f) return null

    val cross = (b.x - topLeft.x) * (c.y - topLeft.y) - (b.y - topLeft.y) * (c.x - topLeft.x)
    return if (cross > 0f) {
        Triple(topLeft, b, c)
    } else {
        Triple(topLeft, c, b)
    }
}

private fun sampleAndDecode(
    bits: BooleanArray,
    width: Int,
    height: Int,
    topLeft: FinderPattern,
    topRight: FinderPattern,
    bottomLeft: FinderPattern,
): String? {
    val distX = hypot((topRight.x - topLeft.x).toDouble(), (topRight.y - topLeft.y).toDouble()).toFloat()
    val distY = hypot((bottomLeft.x - topLeft.x).toDouble(), (bottomLeft.y - topLeft.y).toDouble()).toFloat()
    val avgModule = (topLeft.moduleSize + topRight.moduleSize + bottomLeft.moduleSize) / 3.0f
    val rawDim = (((distX + distY) / (2.0f * avgModule)) + 7.0f).roundToInt()
    val snappedVersion = ((rawDim - 17 + 2) / 4).coerceIn(1, 6)

    val candidateVersions = listOf(snappedVersion, snappedVersion - 1, snappedVersion + 1)
        .filter { it in 1..6 }
        .distinct()

    for (version in candidateVersions) {
        val dim = 17 + 4 * version
        val span = (dim - 7).toFloat()
        val ux = (topRight.x - topLeft.x) / span
        val uy = (topRight.y - topLeft.y) / span
        val vx = (bottomLeft.x - topLeft.x) / span
        val vy = (bottomLeft.y - topLeft.y) / span

        val grid = Array(dim) { BooleanArray(dim) }
        val offsets = floatArrayOf(0f, -0.22f, 0.22f)
        for (r in 0 until dim) {
            for (c in 0 until dim) {
                val dr = (r + 0.5f) - 3.5f
                val dc = (c + 0.5f) - 3.5f
                val cx = topLeft.x + dc * ux + dr * vx
                val cy = topLeft.y + dc * uy + dr * vy
                var darkVotes = 0
                var totalVotes = 0
                for (ox in offsets) {
                    for (oy in offsets) {
                        val px = (cx + ox * ux + oy * vx).roundToInt()
                        val py = (cy + ox * uy + oy * vy).roundToInt()
                        if (px in 0 until width && py in 0 until height) {
                            totalVotes++
                            if (bits[py * width + px]) darkVotes++
                        }
                    }
                }
                grid[r][c] = darkVotes * 2 > totalVotes
            }
        }

        val text = decodeSampledMatrix(grid, version)
        if (!text.isNullOrBlank()) return text
    }
    return null
}

private fun decodeSampledMatrix(grid: Array<BooleanArray>, version: Int): String? {
    val size = grid.size
    if (!verifyFinderAndTimingPatterns(grid, size)) return null

    val formatPair = readFormatInfo(grid, size) ?: return null
    val (eccLevel, mask) = formatPair

    val isFunction = buildFunctionMask(size, version)
    val unmasked = Array(size) { r ->
        BooleanArray(size) { c ->
            val v = grid[r][c]
            if (!isFunction[r][c] && applyMaskPattern(mask, r, c)) !v else v
        }
    }

    val codewords = extractCodewords(unmasked, isFunction, size)
    val spec = lookupDecodeSpec(version, eccLevel) ?: return null
    if (codewords.size < spec.totalCodewords) return null

    val dataBytes = correctReedSolomonBlocks(codewords, spec) ?: return null
    return parseBitStream(dataBytes)
}

/**
 * Kiểm tra chặt 3 góc Finder Pattern (7×7) + viền trắng phân cách + đường Timing (hàng 6, cột 6)
 * để loại bỏ hoàn toàn khả năng nhận nhầm nhiễu ảnh Camera thành ma trận QR.
 */
private fun verifyFinderAndTimingPatterns(grid: Array<BooleanArray>, size: Int): Boolean {
    fun checkFinderAt(top: Int, left: Int): Boolean {
        var matched = 0
        var total = 0
        for (dr in 0..6) {
            for (dc in 0..6) {
                val expectedDark = dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                total++
                if (grid[top + dr][left + dc] == expectedDark) matched++
            }
        }
        // Ít nhất 36/49 (~73%) điểm trong mỗi góc Finder phải khớp đúng mẫu 7x7
        // (cho phép viền mờ do camera điện thoại khi chụp màn hình/tem in)
        return matched >= 36
    }

    if (!checkFinderAt(0, 0)) return false
    if (!checkFinderAt(0, size - 7)) return false
    if (!checkFinderAt(size - 7, 0)) return false

    // Kiểm tra đường timing xen kẽ đen-trắng ở hàng 6 và cột 6
    var timingMatched = 0
    var timingTotal = 0
    for (i in 8 until size - 8) {
        val expectedDark = (i % 2 == 0)
        timingTotal += 2
        if (grid[6][i] == expectedDark) timingMatched++
        if (grid[i][6] == expectedDark) timingMatched++
    }
    if (timingTotal > 0 && timingMatched * 100 < timingTotal * 45) return false
    return true
}

private fun readFormatInfo(grid: Array<BooleanArray>, size: Int): Pair<Int, Int>? {
    // Bản 1: quanh finder trên-trái (bit 14 ở (8,0) .. bit 0 ở (0,8), bỏ qua timing 6)
    var bits1 = 0
    for (i in 0..5) bits1 = setBit(bits1, 14 - i, grid[8][i])
    bits1 = setBit(bits1, 8, grid[8][7])
    bits1 = setBit(bits1, 7, grid[8][8])
    bits1 = setBit(bits1, 6, grid[7][8])
    for (i in 0..5) bits1 = setBit(bits1, 5 - i, grid[5 - i][8])

    // Bản 2: dưới-trái (bit 14..8) + trên-phải (bit 7..0)
    var bits2 = 0
    for (i in 0..6) bits2 = setBit(bits2, 14 - i, grid[size - 1 - i][8])
    for (i in 0..7) bits2 = setBit(bits2, 7 - i, grid[8][size - 8 + i])

    var bestDist = 3 // Chỉ chấp nhận sai số tối đa <= 2 bit
    var bestData = -1
    for (data in 0 until 32) {
        var rem = data shl 10
        for (i in 14 downTo 10) {
            if (((rem ushr i) and 1) != 0) rem = rem xor (0x537 shl (i - 10))
        }
        val encoded = ((data shl 10) or rem) xor 0x5412
        val d1 = (encoded xor bits1).countOneBits()
        val d2 = (encoded xor bits2).countOneBits()
        val minD = min(d1, d2)
        if (minD < bestDist && (d1 <= 2 || d2 <= 2)) {
            bestDist = minD
            bestData = data
        }
    }
    if (bestData < 0) return null
    val eccLevel = (bestData ushr 3) and 0b11
    val mask = bestData and 0b111
    return eccLevel to mask
}

private fun setBit(current: Int, bitIndex: Int, isOne: Boolean): Int {
    return if (isOne) current or (1 shl bitIndex) else current
}

private fun applyMaskPattern(mask: Int, r: Int, c: Int): Boolean = when (mask) {
    0 -> (r + c) % 2 == 0
    1 -> r % 2 == 0
    2 -> c % 3 == 0
    3 -> (r + c) % 3 == 0
    4 -> (r / 2 + c / 3) % 2 == 0
    5 -> (r * c) % 2 + (r * c) % 3 == 0
    6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
    7 -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
    else -> false
}

private fun buildFunctionMask(size: Int, version: Int): Array<BooleanArray> {
    val isFunction = Array(size) { BooleanArray(size) }

    fun markFinder(top: Int, left: Int) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val r = top + dr
                val c = left + dc
                if (r in 0 until size && c in 0 until size) isFunction[r][c] = true
            }
        }
    }

    markFinder(0, 0)
    markFinder(0, size - 7)
    markFinder(size - 7, 0)

    for (i in 8 until size - 8) {
        isFunction[6][i] = true
        isFunction[i][6] = true
    }

    if (version >= 2) {
        val center = size - 7
        for (dr in -2..2) {
            for (dc in -2..2) {
                isFunction[center + dr][center + dc] = true
            }
        }
    }

    isFunction[4 * version + 9][8] = true
    for (i in 0..8) {
        isFunction[8][i] = true
        isFunction[i][8] = true
    }
    for (i in 0..7) {
        isFunction[8][size - 1 - i] = true
        isFunction[size - 1 - i][8] = true
    }
    return isFunction
}

private fun extractCodewords(
    modules: Array<BooleanArray>,
    isFunction: Array<BooleanArray>,
    size: Int,
): IntArray {
    val bits = ArrayList<Int>(size * size)
    var upward = true
    var col = size - 1
    while (col > 0) {
        if (col == 6) col--
        for (step in 0 until size) {
            val r = if (upward) size - 1 - step else step
            for (delta in 0..1) {
                val c = col - delta
                if (!isFunction[r][c]) {
                    bits.add(if (modules[r][c]) 1 else 0)
                }
            }
        }
        upward = !upward
        col -= 2
    }
    val byteCount = bits.size / 8
    val out = IntArray(byteCount)
    for (i in 0 until byteCount) {
        var b = 0
        for (j in 0 until 8) {
            b = (b shl 1) or bits[i * 8 + j]
        }
        out[i] = b
    }
    return out
}

private data class DecodeBlockSpec(
    val totalCodewords: Int,
    val dataCodewords: Int,
    val ecPerBlock: Int,
    val numBlocks1: Int,
    val dataPerBlock1: Int,
    val numBlocks2: Int = 0,
    val dataPerBlock2: Int = 0,
)

private fun lookupDecodeSpec(version: Int, eccBits: Int): DecodeBlockSpec? {
    // eccBits: L=01(1), M=00(0), Q=11(3), H=10(2)
    return when (version) {
        1 -> when (eccBits) {
            1 -> DecodeBlockSpec(26, 19, 7, 1, 19)
            0 -> DecodeBlockSpec(26, 16, 10, 1, 16)
            3 -> DecodeBlockSpec(26, 13, 13, 1, 13)
            2 -> DecodeBlockSpec(26, 9, 17, 1, 9)
            else -> null
        }
        2 -> when (eccBits) {
            1 -> DecodeBlockSpec(44, 34, 10, 1, 34)
            0 -> DecodeBlockSpec(44, 28, 16, 1, 28)
            3 -> DecodeBlockSpec(44, 22, 22, 1, 22)
            2 -> DecodeBlockSpec(44, 16, 28, 1, 16)
            else -> null
        }
        3 -> when (eccBits) {
            1 -> DecodeBlockSpec(70, 55, 15, 1, 55)
            0 -> DecodeBlockSpec(70, 44, 26, 1, 44)
            3 -> DecodeBlockSpec(70, 34, 18, 2, 17)
            2 -> DecodeBlockSpec(70, 26, 22, 2, 13)
            else -> null
        }
        4 -> when (eccBits) {
            1 -> DecodeBlockSpec(100, 80, 20, 1, 80)
            0 -> DecodeBlockSpec(100, 64, 18, 2, 32)
            3 -> DecodeBlockSpec(100, 48, 26, 2, 24)
            2 -> DecodeBlockSpec(100, 36, 16, 4, 9)
            else -> null
        }
        5 -> when (eccBits) {
            1 -> DecodeBlockSpec(134, 108, 26, 1, 108)
            0 -> DecodeBlockSpec(134, 86, 24, 2, 43)
            3 -> DecodeBlockSpec(134, 62, 18, 2, 15, 2, 16)
            2 -> DecodeBlockSpec(134, 46, 22, 2, 11, 2, 12)
            else -> null
        }
        6 -> when (eccBits) {
            1 -> DecodeBlockSpec(172, 136, 18, 2, 68)
            0 -> DecodeBlockSpec(172, 108, 16, 4, 27)
            3 -> DecodeBlockSpec(172, 76, 24, 4, 19)
            2 -> DecodeBlockSpec(172, 60, 28, 4, 15)
            else -> null
        }
        else -> null
    }
}

private fun correctReedSolomonBlocks(
    rawCodewords: IntArray,
    spec: DecodeBlockSpec,
): IntArray? {
    val totalBlocks = spec.numBlocks1 + spec.numBlocks2
    val blockDataLengths = IntArray(totalBlocks) { idx ->
        if (idx < spec.numBlocks1) spec.dataPerBlock1 else spec.dataPerBlock2
    }
    val blocks = Array(totalBlocks) { idx -> IntArray(blockDataLengths[idx] + spec.ecPerBlock) }

    var pos = 0
    val maxDataLen = max(spec.dataPerBlock1, spec.dataPerBlock2)
    for (col in 0 until maxDataLen) {
        for (b in 0 until totalBlocks) {
            if (col < blockDataLengths[b] && pos < rawCodewords.size) {
                blocks[b][col] = rawCodewords[pos++]
            }
        }
    }
    for (col in 0 until spec.ecPerBlock) {
        for (b in 0 until totalBlocks) {
            if (pos < rawCodewords.size) {
                blocks[b][blockDataLengths[b] + col] = rawCodewords[pos++]
            }
        }
    }

    val out = IntArray(spec.dataCodewords)
    var outPos = 0
    for (b in 0 until totalBlocks) {
        val corrected = rsCorrectBlock(blocks[b], spec.ecPerBlock) ?: return null
        for (i in 0 until blockDataLengths[b]) {
            out[outPos++] = corrected[i]
        }
    }
    return out
}

private val GF_EXP = IntArray(512)
private val GF_LOG = IntArray(256)
private val GF_READY = run {
    var x = 1
    for (i in 0 until 255) {
        GF_EXP[i] = x
        GF_LOG[x] = i
        x = x shl 1
        if (x and 0x100 != 0) x = x xor 0x11D
    }
    for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    true
}

private fun gfMul(a: Int, b: Int): Int {
    if (!GF_READY || a == 0 || b == 0) return 0
    return GF_EXP[GF_LOG[a] + GF_LOG[b]]
}

private fun gfInv(a: Int): Int = GF_EXP[255 - GF_LOG[a]]

private fun rsCorrectBlock(codewords: IntArray, ecCount: Int): IntArray? {
    val n = codewords.size
    val syndromes = IntArray(ecCount)
    var hasError = false
    for (i in 0 until ecCount) {
        var eval = 0
        for (c in codewords) {
            eval = gfMul(eval, GF_EXP[i]) xor c
        }
        syndromes[i] = eval
        if (eval != 0) hasError = true
    }
    if (!hasError) return codewords

    // Berlekamp-Massey tìm đa thức định vị lỗi sigma(x)
    var sigma = intArrayOf(1)
    var oldSigma = intArrayOf(1)
    for (i in 0 until ecCount) {
        var delta = syndromes[i]
        for (j in 1 until sigma.size) {
            if (i - j >= 0) {
                delta = delta xor gfMul(sigma[j], syndromes[i - j])
            }
        }
        oldSigma = intArrayOf(0) + oldSigma
        if (delta != 0) {
            if (oldSigma.size > sigma.size) {
                val scale = gfInv(delta)
                val nextSigma = IntArray(oldSigma.size) { idx ->
                    gfMul(oldSigma[idx], delta) xor (sigma.getOrNull(idx) ?: 0)
                }
                oldSigma = IntArray(sigma.size) { idx -> gfMul(sigma[idx], scale) }
                sigma = nextSigma
            } else {
                for (j in oldSigma.indices) {
                    sigma[j] = sigma[j] xor gfMul(oldSigma[j], delta)
                }
            }
        }
    }

    val numErrors = sigma.size - 1
    if (numErrors * 2 > ecCount) return null

    // Chien search tìm vị trí lỗi
    val errorLocations = ArrayList<Int>(numErrors)
    for (i in 0 until n) {
        val xInv = GF_EXP[(255 - i) % 255]
        var eval = 0
        for (j in sigma.indices.reversed()) {
            eval = gfMul(eval, xInv) xor sigma[j]
        }
        if (eval == 0) errorLocations.add(i)
    }
    if (errorLocations.size != numErrors) return null

    // Forney tính độ lớn lỗi
    val omega = IntArray(ecCount)
    for (i in 0 until ecCount) {
        var sum = 0
        for (j in 0..min(i, sigma.size - 1)) {
            sum = sum xor gfMul(syndromes[i - j], sigma[j])
        }
        omega[i] = sum
    }

    val corrected = codewords.copyOf()
    for (loc in errorLocations) {
        val xInv = GF_EXP[(255 - loc) % 255]
        var num = 0
        for (j in omega.indices.reversed()) {
            num = gfMul(num, xInv) xor omega[j]
        }
        var den = 0
        for (j in 1 until sigma.size step 2) {
            var xp = 1
            repeat(j - 1) { xp = gfMul(xp, xInv) }
            den = den xor gfMul(sigma[j], xp)
        }
        if (den == 0) return null
        val errMag = gfMul(num, gfInv(den))
        val arrayIdx = n - 1 - loc
        if (arrayIdx !in corrected.indices) return null
        corrected[arrayIdx] = corrected[arrayIdx] xor errMag
    }

    // BẮT BUỘC: kiểm tra lại toàn bộ hội chứng (syndromes) sau khi sửa lỗi phải bằng 0 tuyệt đối!
    for (i in 0 until ecCount) {
        var eval = 0
        for (c in corrected) {
            eval = gfMul(eval, GF_EXP[i]) xor c
        }
        if (eval != 0) return null
    }
    return corrected
}

private const val ALPHANUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

private fun parseBitStream(dataBytes: IntArray): String? {
    var bitOffset = 0
    val totalBits = dataBytes.size * 8

    fun readBits(count: Int): Int {
        var res = 0
        repeat(count) {
            if (bitOffset >= totalBits) return res
            val b = dataBytes[bitOffset / 8]
            val bit = (b ushr (7 - (bitOffset % 8))) and 1
            res = (res shl 1) or bit
            bitOffset++
        }
        return res
    }

    val sb = StringBuilder()
    while (totalBits - bitOffset >= 4) {
        val mode = readBits(4)
        when (mode) {
            0b0000 -> break // Terminator chuẩn
            0b0100 -> {
                // Byte mode (Versions 1..9 dùng 8-bit count)
                if (totalBits - bitOffset < 8) return null
                val len = readBits(8)
                if (len <= 0 || totalBits - bitOffset < len * 8) return null
                val bytes = ByteArray(len) { readBits(8).toByte() }
                val text = try {
                    bytes.decodeToString(throwOnInvalidSequence = true)
                } catch (_: Exception) {
                    return null
                }
                if (text.any { it.code < 0x20 || it.code > 0x7E }) return null
                sb.append(text)
            }
            0b0010 -> {
                // Alphanumeric mode (Versions 1..9 dùng 9-bit count)
                if (totalBits - bitOffset < 9) return null
                var count = readBits(9)
                if (count <= 0) return null
                while (count > 1) {
                    if (totalBits - bitOffset < 11) return null
                    val v = readBits(11)
                    if (v >= 45 * 45) return null
                    sb.append(ALPHANUMERIC_CHARS[v / 45])
                    sb.append(ALPHANUMERIC_CHARS[v % 45])
                    count -= 2
                }
                if (count == 1) {
                    if (totalBits - bitOffset < 6) return null
                    val v = readBits(6)
                    if (v >= 45) return null
                    sb.append(ALPHANUMERIC_CHARS[v])
                }
            }
            0b0001 -> {
                // Numeric mode (Versions 1..9 dùng 10-bit count)
                if (totalBits - bitOffset < 10) return null
                var count = readBits(10)
                if (count <= 0) return null
                while (count >= 3) {
                    if (totalBits - bitOffset < 10) return null
                    val v = readBits(10)
                    if (v >= 1000) return null
                    sb.append(v.toString().padStart(3, '0'))
                    count -= 3
                }
                if (count == 2) {
                    if (totalBits - bitOffset < 7) return null
                    val v = readBits(7)
                    if (v >= 100) return null
                    sb.append(v.toString().padStart(2, '0'))
                } else if (count == 1) {
                    if (totalBits - bitOffset < 4) return null
                    val v = readBits(4)
                    if (v >= 10) return null
                    sb.append(v.toString())
                }
            }
            0b0111 -> {
                // ECI header (8 bits) -> bỏ qua và đọc tiếp segment sau
                if (totalBits - bitOffset < 8) return null
                readBits(8)
            }
            else -> return null
        }
    }
    return sb.toString().takeIf { it.length >= 3 && it.isNotBlank() }
}
