package vn.lab.logistics.ui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.lab.logistics.ui.components.decodeQrFromArgb
import vn.lab.logistics.ui.components.encodeQrMatrix
import vn.lab.logistics.ui.components.extractScanCode
import java.awt.BasicStroke
import java.awt.Color
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import javax.imageio.ImageIO

actual suspend fun printQrLabels(
    jobTitle: String,
    labels: List<PrintableQrLabel>,
): Result<String> = withContext(Dispatchers.IO) {
    if (labels.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Không có tem QR nào để in."))
    }
    try {
        val sheet = renderLabelsSheetBufferedImage(labels)
        val job = PrinterJob.getPrinterJob()
        job.jobName = jobTitle.ifBlank { "Tem_QR_AppLogistics" }
        job.setPrintable { graphics, pageFormat, pageIndex ->
            if (pageIndex > 0) return@setPrintable Printable.NO_SUCH_PAGE
            val g2d = graphics as Graphics2D
            g2d.translate(pageFormat.imageableX, pageFormat.imageableY)
            val availW = pageFormat.imageableWidth
            val availH = pageFormat.imageableHeight
            val scale = minOf(availW / sheet.width.toDouble(), availH / sheet.height.toDouble(), 1.0)
            val drawW = (sheet.width * scale).toInt()
            val drawH = (sheet.height * scale).toInt()
            g2d.drawImage(sheet, 0, 0, drawW, drawH, null)
            Printable.PAGE_EXISTS
        }
        val accepted = job.printDialog()
        if (!accepted) {
            return@withContext Result.failure(IllegalStateException("Đã huỷ hộp thoại in."))
        }
        job.print()
        Result.success("Đã gửi lệnh in ${labels.size} tem QR ra máy in.")
    } catch (e: Exception) {
        Result.failure(IllegalStateException("Không thể in tem QR: ${e.message ?: "Lỗi máy in"}", e))
    }
}

actual suspend fun saveQrLabelsAsImage(
    filePrefix: String,
    labels: List<PrintableQrLabel>,
): Result<String> = withContext(Dispatchers.IO) {
    if (labels.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Không có tem QR nào để lưu ảnh."))
    }
    val safePrefix = filePrefix.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "TemQR" }
    val defaultName = "${safePrefix}_${System.currentTimeMillis()}.png"
    val dialog = FileDialog(null as Frame?, "Lưu ảnh tem QR (PNG)", FileDialog.SAVE)
    try {
        dialog.file = defaultName
        dialog.isVisible = true
        val fileName = dialog.file ?: return@withContext Result.failure(IllegalStateException("Đã huỷ lưu ảnh tem QR."))
        val dir = dialog.directory ?: return@withContext Result.failure(IllegalStateException("Không xác định được thư mục lưu."))
        val outFile = File(dir, if (fileName.lowercase().endsWith(".png")) fileName else "$fileName.png")
        val image = renderLabelsSheetBufferedImage(labels)
        ImageIO.write(image, "png", outFile)
        Result.success("Đã lưu ảnh tem QR tại: ${outFile.absolutePath}")
    } catch (e: Exception) {
        Result.failure(IllegalStateException("Không lưu được ảnh tem QR: ${e.message}", e))
    } finally {
        dialog.dispose()
    }
}

actual suspend fun scanQrCodeWithLiveCamera(
    expectedCodes: List<String>?,
): Result<String?> = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Chọn ảnh tem QR để quét", FileDialog.LOAD)
    try {
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
        }
        dialog.isVisible = true
        val fileName = dialog.file ?: return@withContext Result.success(null)
        val dir = dialog.directory ?: return@withContext Result.success(null)
        val img = ImageIO.read(File(dir, fileName))
            ?: return@withContext Result.failure(IllegalStateException("Không đọc được tệp ảnh \"$fileName\"."))
        val w = img.width
        val h = img.height
        val pixels = IntArray(w * h)
        img.getRGB(0, 0, w, h, pixels, 0, w)
        val decoded = decodeQrFromArgb(pixels, w, h)
            ?: return@withContext Result.failure(
                IllegalStateException("Không tìm thấy mã QR hợp lệ trong ảnh \"$fileName\"."),
            )
        Result.success(extractScanCode(decoded))
    } catch (e: Exception) {
        Result.failure(IllegalStateException("Lỗi quét ảnh mã QR: ${e.message}", e))
    } finally {
        dialog.dispose()
    }
}

private fun renderLabelsSheetBufferedImage(labels: List<PrintableQrLabel>): BufferedImage {
    val cardW = 900
    val cardH = 440
    val gap = 28
    val pad = 28
    val totalH = pad * 2 + labels.size * cardH + (labels.size - 1).coerceAtLeast(0) * gap
    val sheet = BufferedImage(cardW + pad * 2, totalH.coerceAtLeast(cardH + pad * 2), BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color(0xF3, 0xF4, 0xF6)
    g.fillRect(0, 0, sheet.width, sheet.height)

    labels.forEachIndexed { idx, label ->
        val top = pad + idx * (cardH + gap)
        drawSingleLabel(g, pad, top, cardW, cardH, label)
    }
    g.dispose()
    return sheet
}

private fun drawSingleLabel(
    g: Graphics2D,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    label: PrintableQrLabel,
) {
    g.color = Color.WHITE
    g.fillRect(left, top, width, height)
    g.color = Color(0x11, 0x18, 0x27)
    g.stroke = BasicStroke(4f)
    g.drawRect(left, top, width, height)

    g.color = Color(0x11, 0x18, 0x27)
    g.fillRect(left, top, width, 68)

    g.color = Color.WHITE
    g.font = Font("SansSerif", Font.BOLD, 26)
    g.drawString(label.header, left + 24, top + 44)

    val fmIdx = g.fontMetrics
    val idxW = fmIdx.stringWidth(label.indexLabel)
    g.drawString(label.indexLabel, left + width - 24 - idxW, top + 44)

    val matrix = encodeQrMatrix(label.qrContent)
    val n = matrix.size
    val quiet = 4
    val totalModules = n + quiet * 2
    val qrBox = 310
    val qrLeft = left + 28
    val qrTop = top + 92
    val modulePx = qrBox.toDouble() / totalModules.toDouble()

    g.color = Color.WHITE
    g.fillRect(qrLeft, qrTop, qrBox, qrBox)
    g.color = Color.BLACK
    for (r in 0 until n) {
        for (c in 0 until n) {
            if (matrix[r][c]) {
                val x0 = (qrLeft + (c + quiet) * modulePx).toInt()
                val y0 = (qrTop + (r + quiet) * modulePx).toInt()
                val x1 = (qrLeft + (c + quiet + 1) * modulePx).toInt().coerceAtLeast(x0 + 1)
                val y1 = (qrTop + (r + quiet + 1) * modulePx).toInt().coerceAtLeast(y0 + 1)
                g.fillRect(x0, y0, x1 - x0, y1 - y0)
            }
        }
    }

    val textX = qrLeft + qrBox + 32
    var textY = qrTop + 56

    g.color = Color(0x11, 0x18, 0x27)
    g.font = Font("Monospaced", Font.BOLD, 34)
    g.drawString(label.primaryCode, textX, textY)

    if (!label.secondaryCode.isNullOrBlank() && label.secondaryCode != label.primaryCode) {
        textY += 42
        g.color = Color(0x37, 0x41, 0x51)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Kiện gốc: ${label.secondaryCode}", textX, textY)
    }

    textY += 44
    g.color = Color(0x1F, 0x29, 0x37)
    g.font = Font("SansSerif", Font.PLAIN, 22)
    g.drawString(label.subtitle.take(40), textX, textY)

    if (label.meta.isNotBlank()) {
        textY += 38
        g.color = Color(0x04, 0x78, 0x57)
        g.font = Font("SansSerif", Font.BOLD, 21)
        g.drawString(label.meta.take(42), textX, textY)
    }

    g.color = Color(0x6B, 0x72, 0x80)
    g.font = Font("SansSerif", Font.PLAIN, 18)
    g.drawString("Quét mã QR bằng Camera hoặc nhập mã kiện", textX, top + height - 28)
}

