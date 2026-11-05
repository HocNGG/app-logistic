package vn.lab.logistics.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * EMPTY / ERROR STATE — "không có màn trắng" (Luật 8: demo không bao giờ rơi vào màn chết).
 *
 * Ba phần bắt buộc của một khối rỗng theo `ui-modern-industrial.md` §5:
 *  1. **hình vẽ** (Canvas, không emoji, không icon library offline) — một kiện hàng rỗng;
 *  2. một câu giải thích chuyện đang xảy ra;
 *  3. một hành động tiếp theo (Tải lại / Về dữ liệu mẫu / Chọn ảnh…).
 *
 * SỬA 2026-11-01 (đo trên ảnh chụp app 1440×882 — `.tmp/shot/app-2.png`):
 *  • Hành động từng là `TextButton` ⇒ hiện ra như **chữ có gạch chân**, người dùng không
 *    nhận ra là bấm được. Nay là **button** thật: chính = nền nhấn, phụ = viền mảnh.
 *  • Chữ từng tràn ra ngoài pane 340dp ("Bấm trực tiếp vào dòng để xem địa chỉ, khung giờ
 *    và gán…" bị cắt ở mép cửa sổ) vì Column không có bề rộng tối đa. Nay [MAX_TEXT_WIDTH]
 *    buộc xuống dòng trong phạm vi 380dp — đúng luật 45–75 ký tự/dòng.
 * =====================================================================================
 */

/** Bề rộng tối đa của khối chữ trong màn rỗng: giữ dòng ở khoảng 45–75 ký tự. */
private val MAX_TEXT_WIDTH = 380.dp

@Composable
fun LabEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tone: vn.lab.logistics.ui.components.Tone = vn.lab.logistics.ui.components.Tone.Info,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(Tokens.s5),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = MAX_TEXT_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyGlyph(color = toneTint(tone))
            Spacer(Modifier.height(Tokens.s4))
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = AppTheme.lab.ink,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.body2,
                    color = AppTheme.lab.ink2,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Tokens.s2),
                )
            }
            if ((actionLabel != null && onAction != null) || (secondaryLabel != null && onSecondary != null)) {
                Spacer(Modifier.height(Tokens.s4))
            }
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.height(Tokens.touchSecondary),
                    shape = RoundedCornerShape(Tokens.rSm),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppTheme.lab.accent,
                        // ADR-009 §7: trên lime BẮT BUỘC chữ tối (13,96:1) — không chữ trắng.
                        contentColor = AppTheme.lab.onAccent,
                    ),
                    // Phẳng: bóng chỉ dành cho lớp nổi (ADR-009 §6 — dialog/menu).
                    elevation = ButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp,
                    ),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.button)
                }
            }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.height(Tokens.s2))
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.height(Tokens.touchSecondary),
                    shape = RoundedCornerShape(Tokens.rSm),
                    border = BorderStroke(Tokens.dividerLine, AppTheme.lab.lineStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Tokens.transparent,
                        contentColor = AppTheme.lab.ink,
                    ),
                ) {
                    Text(text = secondaryLabel, style = MaterialTheme.typography.button)
                }
            }
        }
    }
}

/**
 * Hình của khối rỗng: một kiện hàng vẽ bằng nét (không tô) — cùng ngôn ngữ với logo app nhưng
 * để trống ruột, đúng nghĩa "chưa có gì ở đây". Không emoji, không asset, không icon library.
 */
@Composable
private fun EmptyGlyph(color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(Lab.emptyIconSize)) {
        val s = size.minDimension / 64f
        val stroke = Stroke(width = 2.4f * s, cap = StrokeCap.Round)
        // Khối hộp isometric rỗng: đỉnh — hai vai — đáy.
        val box = Path().apply {
            moveTo(32f * s, 10f * s)
            lineTo(54f * s, 22f * s)
            lineTo(32f * s, 34f * s)
            lineTo(10f * s, 22f * s)
            close()
        }
        drawPath(box, color = color, style = stroke)
        drawLine(
            color = color,
            start = Offset(10f * s, 22f * s),
            end = Offset(10f * s, 42f * s),
            strokeWidth = 2.4f * s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(54f * s, 22f * s),
            end = Offset(54f * s, 42f * s),
            strokeWidth = 2.4f * s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(32f * s, 34f * s),
            end = Offset(32f * s, 54f * s),
            strokeWidth = 2.4f * s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(10f * s, 42f * s),
            end = Offset(32f * s, 54f * s),
            strokeWidth = 2.4f * s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(54f * s, 42f * s),
            end = Offset(32f * s, 54f * s),
            strokeWidth = 2.4f * s,
            cap = StrokeCap.Round,
        )
    }
}
