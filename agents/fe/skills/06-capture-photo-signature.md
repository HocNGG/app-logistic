## Dùng khi nào

- Làm màn `DriverCapture` / `DriverSign` (P3): 3 slot ảnh + pad chữ ký.
- Cần nén ảnh về ≤ 5MB trước khi đưa vào outbox/upload (docs/02 §6.3 "upload ảnh ≤ 5MB").
- Cần chữ ký chạy được **cả chuột và cảm ứng**, có undo/clear, xuất PNG.

## 1. Luồng UI (một tay, găng tay)

| Slot | Nội dung | Gợi ý |
|---|---|---|
| 1 | Ảnh hàng hoá trên xe | bắt buộc ≥ 1 ảnh |
| 2 | Ảnh kiện (nhãn/niêm phong) | khuyến khích |
| 3 | Biên nhận giấy có chữ ký tay | tuỳ chọn |
| Pad | Chữ ký người giao | **bắt buộc** (skill 05 §7) |

Nút chính ≥ 56dp, nút phụ ≥ 48dp. Mỗi slot: ảnh thu nhỏ 96dp + nút ✕ 40dp (có `contentDescription`).

## 2. Chọn ảnh: file dialog + kéo thả

```kotlin
// commonMain — UI không biết AWT
interface ImagePicker {
    suspend fun pick(): List<ByteArray>            // đã đọc bytes, chưa nén
}
// desktopMain — FileDialog (skill 02 §5) + lọc đuôi
class DesktopImagePicker(private val window: ComposeWindow) : ImagePicker {
    override suspend fun pick(): List<ByteArray> = withContext(Dispatchers.IO) {
        val dialog = FileDialog(window, "Chọn ảnh", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, n -> n.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png") }
        }
        dialog.isVisible = true
        dialog.files.map { it.readBytes() }
    }
}
```

Kéo–thả: Compose Desktop có `Modifier.onExternalDrag(...)` (experimental) trả `DragData.FilesList` (chuỗi URI cách nhau bởi `\n`). ⚠️ **kiểm tra lại API của Compose Multiplatform 1.12.2** (`onExternalDrag` / `dragAndDropTarget`, package `androidx.compose.ui.draganddrop`). Phương án B chắc chắn chạy: gắn `java.awt.dnd.DropTarget` lên `ComposeWindow` trong `desktopMain`, đọc `Transferable` → `javaFileListFlavor`. Dù bằng cách nào, kết quả cuối vẫn là `List<ByteArray>` đi vào ViewModel.

## 3. Nén JPEG ≤ 5MB (chỉ `desktopMain`, dùng `ImageIO` — không `android.graphics`)

```kotlin
object JpegCompressor {
    fun compress(srcBytes: ByteArray, maxBytes: Int = 5 * 1024 * 1024, maxDim: Int = 1920): ByteArray {
        val src = ImageIO.read(ByteArrayInputStream(srcBytes)) ?: error("Tệp không phải ảnh hợp lệ")
        var img: BufferedImage = if (maxOf(src.width, src.height) > maxDim) scaleTo(src, maxDim) else src
        var quality = 0.85f
        repeat(8) {
            val out = ByteArrayOutputStream()
            writeJpeg(img, out, quality)
            if (out.size() <= maxBytes) return out.toByteArray()
            quality -= 0.10f
            if (quality < 0.40f) { img = scaleTo(img, (img.width * 0.8).toInt()); quality = 0.75f }
        }
        error("Không nén được ảnh xuống ≤ 5MB")
    }
    private fun writeJpeg(img: BufferedImage, out: ByteArrayOutputStream, quality: Float) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.coerceIn(0f, 1f)
            }
            writer.write(null, IIOImage(img, null, null), param)
        }
    }
    private fun scaleTo(src: BufferedImage, maxDim: Int): BufferedImage {
        val ratio = maxDim.toDouble() / maxOf(src.width, src.height)
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)      // JPEG không có alpha
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, h, null); dispose()
        }
        return out
    }
}
```

Kiểm tra "magic bytes" trước khi nhận tệp: JPEG `FF D8 FF`, PNG `89 50 4E 47`. Không tin đuôi tệp. Hiển thị preview: `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()` — skiko, chạy ở `commonMain`; ⚠️ **kiểm tra lại API của skiko/compose 1.12.2** (`toComposeImageBitmap` nằm trong `androidx.compose.ui.graphics`).

## 4. Preview grid + xoá

```kotlin
@Composable
fun PhotoGrid(photos: List<Photo>, onRemove: (Int) -> Unit, onAdd: () -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), // foundation-layout, không phải material3
        contentPadding = PaddingValues(Tokens.s2), verticalArrangement = Arrangement.spacedBy(Tokens.s2)) {
        itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(Tokens.rMd))) {
                Image(photo.bitmap, contentDescription = "Ảnh ${index + 1}: ${photo.label}", contentScale = ContentScale.Crop)
                IconButton(onClick = { onRemove(index) }, Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, contentDescription = "Xoá ảnh ${index + 1}")
                }
            }
        }
        item { AddPhotoTile(onAdd) }               // luôn ở cuối → "Thêm ảnh"
    }
}
```

`Photo(id, label, bitmap, jpegBytes)`: `id` ổn định (UUID) để `key` không đổi khi xoá giữa danh sách.

## 5. `SignaturePad` — Canvas + PointerInput (chuột **và** touch)

```kotlin
data class Stroke(val points: List<Offset>, val widths: List<Float>)
class SignatureState {
    val strokes = mutableStateListOf<Stroke>()
    val isEmpty: Boolean get() = strokes.isEmpty()
    fun undo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
    fun clear() = strokes.clear()
}
@Composable
fun SignaturePad(state: SignatureState, modifier: Modifier = Modifier) {
    var live by remember { mutableStateOf<List<Pair<Offset, Float>>?>(null) }
    Canvas(modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(Tokens.rMd))
        .background(Color.White).border(1.dp, Tokens.ink200, RoundedCornerShape(Tokens.rMd))
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pts = mutableListOf(down.position to down.pressure.coerceIn(0.2f, 1f))
                live = pts
                drag(down.id) { change ->                          // chuột: pressure = 1f → bù theo tốc độ
                    pts += change.position to change.pressure.coerceIn(0.2f, 1f); change.consume()
                }
                state.strokes += Stroke(pts.map { it.first }, pts.map { it.second })
                live = null
            }
        }
    ) {
        state.strokes.forEach { drawStroke(it) }
        live?.let { pts -> drawStroke(Stroke(pts.map { it.first }, pts.map { it.second })) }
    }
}
private fun DrawScope.drawStroke(s: Stroke) {
    for (i in 1 until s.points.size) {
        drawLine(color = Color(0xFF102A43) /* ← ngoại lệ: mực chữ ký, khai báo trong design/ */,
                 start = s.points[i - 1], end = s.points[i],
                 strokeWidth = 2.2f + 2.8f * s.widths[i], cap = StrokeCap.Round)
    }
}
```

- `awaitEachGesture` + `drag(id)` xử lý được cả chuột lẫn ngón tay (multi-touch chỉ lấy pointer đầu).
- Áp lực trên desktop luôn `1f` → muốn nét "tự nhiên" hơn: tính tốc độ giữa 2 điểm (`distance / dt`), tốc độ cao → nét mảnh. Ghi chú này trong code, đừng giả vờ có pressure thật.
- `undo`/`clear` là **state**, không ảnh hưởng bitmap đã export.
- Mực chữ ký dùng `Color` khai báo trong `design/` (skill 07 §6) — không rải `0x` khắp file.

## 6. Xuất PNG

```kotlin
// commonMain khai báo; desktopMain cài đặt bằng ImageIO (không dùng android.graphics)
expect fun encodeSignaturePng(strokes: List<Stroke>, widthPx: Int, heightPx: Int): ByteArray
```

Phương án B (chạy ngay trong `commonMain`, không cần expect/actual): vẽ lại lên `org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)` → `Image.encodeToData(EncodedImageFormat.PNG)` — ⚠️ **kiểm tra lại API của skiko** trước khi dùng. Xuất PNG **nền trong suốt** hoặc trắng (chọn trắng để in POD — `docs/02` §8).

## Checklist nhanh

- [ ] Không import `android.graphics.*`/`Intent` ở `commonMain`; `ImageIO`/`FileDialog` chỉ ở `desktopMain`.
- [ ] Ảnh sau nén ≤ 5MB và ≤ 1920px cạnh dài; có kiểm tra magic bytes.
- [ ] Preview grid có `key` ổn định (UUID), xoá đúng index, có `contentDescription`.
- [ ] `SignaturePad` vẽ được bằng chuột **và** touchpad/cảm ứng; `undo` + `clear` hoạt động.
- [ ] Chữ ký xuất PNG có nền trắng, kích thước tỉ lệ pad (không méo nét).
- [ ] Confirm bị chặn khi thiếu ảnh hoặc chữ ký rỗng (skill 05 §7).
- [ ] Ảnh/chữ ký đi vào outbox dưới dạng file, không nhét base64 khổng lồ vào JSON log.
