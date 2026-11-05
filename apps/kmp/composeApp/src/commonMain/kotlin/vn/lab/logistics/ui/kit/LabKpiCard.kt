package vn.lab.logistics.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * KPI CARD — cấu trúc BẮT BUỘC của spec `ui-dashboard-app-screens.md` §3, không tự thêm bớt:
 *
 * ```
 * nhãn 13sp, chữ thường, ink2        ← một nhãn, không phải câu
 * SỐ LỚN 32sp, tabular-nums, ink     ← MỘT con số
 * chip Δ (nền tint 12%)              ← chỉ khi có mốc so sánh THẬT
 * sparkline 40dp (1 màu nhấn)       ← chỉ khi có chuỗi thời gian THẬT
 * ```
 *
 * LUẬT SỐ LIỆU (Luật 6 + spec §3, §7): `value == null` ⇒ hiện "Chưa có dữ liệu", **không** hiện
 * số 0 giả. Không có chuỗi thời gian ⇒ không có sparkline (component tự bỏ, caller không cần
 * `if`). Không có mốc so sánh ⇒ không có chip Δ.
 *
 * Số dùng `fontFeatureSettings = "tnum"` để các card thẳng cột số khi xếp ngang — chi tiết nhỏ
 * nhưng là thứ phân biệt "dashboard được thiết kế" với "dashboard ghép bằng Text".
 * =====================================================================================
 */

/**
 * Một mốc so sánh đã tính sẵn (Δ). `text` phải nêu RÕ mốc ("+3 so với hôm qua") — spec §3 cấm
 * con số Δ không có mốc.
 */
data class LabDelta(val text: String, val tone: vn.lab.logistics.ui.components.Tone)

/**
 * @param label nhãn 13sp phía trên (vd "Đã về kho hôm nay").
 * @param value số đã định dạng theo vi-VN; `null` ⇒ card ghi "Chưa có dữ liệu".
 * @param delta chip Δ — `null` khi không tính được từ dữ liệu thật.
 * @param sparkline chuỗi thời gian (≥ 2 điểm mới vẽ); rỗng ⇒ card chỉ 2 dòng.
 * @param hint dòng giải thích thay cho số khi `value == null` (vd "chưa có dữ liệu tải chuyến").
 */
@Composable
fun LabKpiCard(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    delta: LabDelta? = null,
    sparkline: List<Float> = emptyList(),
    hint: String? = null,
) {
    LabCard(modifier = modifier, padding = androidx.compose.foundation.layout.PaddingValues(Tokens.s4)) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(fontSize = Lab.kpiLabelSize, lineHeight = Lab.kpiLabelLine),
            color = AppTheme.lab.ink2,
            // 2 dòng, KHÔNG cắt cụt: ở lưới 2 cột trên máy 390dp, nhãn dài ("Tải chuyến trung
            // bình") từng bị cắt thành "Tài chuyến trung b…" — nhãn bị cắt làm người đọc
            // không biết con số nói về cái gì, tệ hơn một dòng phụ.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Tokens.s2))
        if (value != null) {
            Text(
                text = value,
                style = KpiNumberStyle,
                color = AppTheme.lab.ink,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Text(
                text = "Chưa có dữ liệu",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink2,
                maxLines = 2,
            )
        }
        if (delta != null) {
            Spacer(Modifier.height(Tokens.s2))
            LabChip(text = delta.text, tone = delta.tone)
        } else if (hint != null && value == null) {
            Spacer(Modifier.height(Tokens.s1))
            Text(
                text = hint,
                style = MaterialTheme.typography.caption,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (sparkline.size >= 2) {
            Spacer(Modifier.height(Tokens.s3))
            SparklineRow(points = sparkline)
        }
    }
}

/** Sparkline 40dp, một màu (series chính) — tách ra để KPI card không phụ thuộc chi tiết chart. */
@Composable
private fun SparklineRow(points: List<Float>) {
    Column(Modifier.fillMaxWidth()) {
        vn.lab.logistics.ui.components.charts.Sparkline(
            points = points,
            color = AppTheme.lab.chart1,
            modifier = Modifier.height(Lab.sparklineHeight),
        )
    }
}

/** Số lớn của KPI: 32sp, đậm vừa, tabular-nums (spec §3) để các card thẳng cột số. */
val KpiNumberStyle = TextStyle(
    fontSize = Lab.kpiNumberSize,
    lineHeight = Lab.kpiNumberLine,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)
