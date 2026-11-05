package vn.lab.logistics.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Brand
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * C1 — `SignaturePad`: vẽ chữ ký bằng **chuột VÀ cảm ứng**, có **Hoàn tác** + **Xoá**,
 * xuất **PNG ByteArray** (nền trong suốt, nét 3–4dp, màu `Brand.ink`).
 *
 * VÌ SAO `awaitEachGesture` + `drag(id)` (không dùng `detectDragGestures`):
 * `detectDragGestures` chỉ theo MỘT con trỏ và bỏ qua nhánh "nhấn rồi nhấc ngay" (chấm).
 * Cặp API dưới đây lấy được đúng pointer đầu tiên ⇒ chạy giống nhau với chuột (1 pointer,
 * `pressure` luôn 1f) và ngón tay (nhiều pointer, chỉ vẽ pointer đầu). Đây là khuyến nghị
 * của `agents/fe/skills/06-capture-photo-signature.md` §5.
 *
 * XUẤT ẢNH: `encodeSignaturePng()` là hàm `expect` — `commonMain` KHÔNG biết `ImageIO`
 * (Luật: API Java chỉ ở `desktopMain`). PNG nền TRONG SUỐT theo đặc tả C1.
 *
 * KHÔNG GHI ĐĨA: nét vẽ sống trong [SignatureState] (RAM). Bytes PNG chỉ sinh ra lúc bấm
 * "Gửi & xác nhận", đi thẳng vào payload base64.
 * =====================================================================================
 */

/** Một nét vẽ liên tục (từ lúc nhấn xuống tới lúc nhấc lên), toạ độ px trong khung pad. */
data class SignatureStroke(val points: List<Offset>)

/**
 * State của pad — đặt ở ViewModel để **chuyển bước 1 ⇄ 2 ⇄ 3 không mất chữ ký**
 * (cùng lý do `listState`/`cursorId` nằm ở `DriverJobsViewModel`).
 */
class SignatureState {

    /** Danh sách nét theo thứ tự vẽ. Dùng `SnapshotStateList` để Compose tự vẽ lại. */
    val strokes: SnapshotStateList<SignatureStroke> = mutableStateListOf()

    /** Kích thước THẬT của khung vẽ (px) — cần để xuất PNG đúng tỉ lệ, không méo nét. */
    var canvasSize: IntSize? by mutableStateOf(null)

    val isEmpty: Boolean get() = strokes.isEmpty()

    val strokeCount: Int get() = strokes.size

    /** Hoàn tác nét vẽ cuối (không đụng gì nếu chưa có nét nào). */
    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    /** Xoá toàn bộ để ký lại. */
    fun clear() {
        strokes.clear()
    }

    /**
     * Xuất PNG (nền trong suốt). `null` khi chưa có nét nào hoặc khung vẽ chưa được đo —
     * UI dùng đúng `null` này để CHẶN nút "Gửi & xác nhận" (bất biến an toàn C6-a).
     */
    fun toPngBytes(): ByteArray? {
        val size = canvasSize ?: return null
        if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
        return encodeSignaturePng(
            strokes = strokes.toList(),
            widthPx = size.width,
            heightPx = size.height,
            strokeWidthPx = signatureStrokePx(size.width),
        )
    }
}

/**
 * Độ dày nét khi XUẤT PNG, suy từ bề rộng khung vẽ để nét luôn ~3–4dp trên mọi cỡ pad.
 *
 * (Pad 700px → 3.2px; pad 1400px → 6px nhưng hiển thị vẫn tương đương vì ảnh cũng lớn gấp đôi.)
 * Hàm THUẦN → test/self-check kiểm được mà không cần GUI.
 */
fun signatureStrokePx(canvasWidthPx: Int): Float =
    (canvasWidthPx / 220f).coerceIn(2.5f, 6f)

/** Màu mực chữ ký dạng ARGB đóng gói — cho `actual` desktop vẽ bằng `java.awt.Color`.
 *  Suy từ `Brand.ink` (không viết hex ở file ngoài `design/` — skill 07 §6). */
internal val SIGNATURE_INK_ARGB: Int = (Brand.ink.value shr 32).toInt()

/**
 * Pad vẽ chữ ký + thanh công cụ (Hoàn tác / Xoá / số nét).
 *
 * @param inkColor màu nét (mặc định `Brand.ink` theo đặc tả C1).
 * @param paperColor màu nền khung vẽ. Đặt nền brand tối để **mực sáng nhìn rõ ngay khi vẽ**
 *        (chữ ký trắng trên nền trong suốt sẽ khó thấy nếu in ra giấy trắng — xem báo cáo ⚠️).
 * @param showToolbar bật cụm nút Hoàn tác/Xoá ngay dưới pad.
 */
@Composable
fun SignaturePad(
    state: SignatureState,
    modifier: Modifier = Modifier,
    inkColor: Color = Brand.ink,
    paperColor: Color = Brand.ground,
    strokeWidth: Dp = Tokens.signatureStroke,
    showToolbar: Boolean = true,
) {
    val livePoints = remember { mutableStateOf<List<Offset>>(emptyList()) }
    val shape = RoundedCornerShape(Tokens.rMd)

    Column(modifier.fillMaxWidth()) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Tokens.signaturePadHeight)
                    .clip(shape)
                    .background(paperColor)
                    .border(Tokens.dividerLine, Brand.hairline, shape)
                    .onSizeChanged { state.canvasSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val points = mutableListOf(down.position)
                            livePoints.value = points.toList()
                            drag(down.id) { change ->
                                points.add(change.position)
                                livePoints.value = points.toList()
                                change.consume()
                            }
                            // Nhấn rồi nhấc ngay (không kéo) vẫn tính là 1 nét: nhân đôi điểm
                            // để có đoạn thẳng cực ngắn mà vẫn vẽ được (không "mất cú chấm").
                            val finalPoints = if (points.size >= 2) {
                                points.toList()
                            } else {
                                listOf(points.first(), points.first() + Offset(0.7f, 0f))
                            }
                            state.strokes.add(SignatureStroke(finalPoints))
                            livePoints.value = emptyList()
                        }
                    },
            ) {
                val widthPx = strokeWidth.toPx()
                state.strokes.forEach { stroke ->
                    drawSignatureStroke(stroke, inkColor, widthPx)
                }
                val live = livePoints.value
                if (live.size >= 2) {
                    drawSignatureStroke(SignatureStroke(live), inkColor, widthPx)
                }
            }

            // Gợi ý chỉ hiện khi pad còn trống (chữ mờ của màu mực — không phát minh màu mới).
            if (state.isEmpty && livePoints.value.isEmpty()) {
                Text(
                    text = "Ký vào đây bằng chuột hoặc ngón tay",
                    style = MaterialTheme.typography.body2,
                    color = inkColor.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        if (showToolbar) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.isEmpty) "Chưa có nét nào" else "${state.strokeCount} nét đã vẽ",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = state::undo,
                    enabled = !state.isEmpty,
                    modifier = Modifier.height(Tokens.touchSecondary),
                ) {
                    Text("Hoàn tác")
                }
                TextButton(
                    onClick = state::clear,
                    enabled = !state.isEmpty,
                    modifier = Modifier.height(Tokens.touchSecondary),
                ) {
                    Text("Xoá")
                }
            }
        }
    }
}

/** Vẽ một nét: nối các điểm bằng đoạn thẳng có đầu tròn (mượt như bút thật). */
private fun DrawScope.drawSignatureStroke(stroke: SignatureStroke, color: Color, widthPx: Float) {
    val points = stroke.points
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color = color, radius = widthPx / 2f, center = points.first())
        return
    }
    for (index in 1 until points.size) {
        drawLine(
            color = color,
            start = points[index - 1],
            end = points[index],
            strokeWidth = widthPx,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Xuất PNG **nền trong suốt** từ danh sách nét.
 *
 * `expect` vì cần ghi ảnh — `desktopMain` dùng `ImageIO` (`SignaturePad.desktop.kt`).
 * @return bytes PNG, hoặc `null` nếu không có nét/kích thước không hợp lệ.
 */
expect fun encodeSignaturePng(
    strokes: List<SignatureStroke>,
    widthPx: Int,
    heightPx: Int,
    strokeWidthPx: Float,
): ByteArray?
