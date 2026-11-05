package vn.lab.logistics.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * TOOLBAR CỦA MÀN — "page header" kiểu sản phẩm: tiêu đề + một dòng phụ + slot bộ lọc +
 * slot hành động, phân tách với nội dung bằng đúng MỘT đường kẻ ngang.
 *
 * Vì sao thay thanh công cụ cũ: bản cũ là `Surface(elevation)` cao 64dp, mỗi màn tự đặt màu và
 * tự xếp hàng — nhìn ra "app tự vẽ". Bản này bám nền trang (không nổi), tiêu đề là chữ thật
 * (h5), bộ lọc nằm ở hàng thứ hai như ref-01/ref-03, và **không có** pill ngưỡng/bề rộng
 * (những thứ đó đã chuyển vào màn "Cài đặt demo").
 *
 * @param title tiêu đề trang, 1 dòng, cắt bằng ellipsis.
 * @param subtitle dòng phụ 13sp `ink2` (số việc, nguồn dữ liệu, khung giờ…).
 * @param filters hàng thứ hai: ô tìm, chip lọc, chip khoảng thời gian (cuộn ngang nếu hẹp).
 * @param actions góc phải: hành động chính/phụ (Tải lại, Phím tắt, Đăng xuất).
 * @param stacked `true` ở điện thoại (< 600dp): hành động xuống hàng riêng để tiêu đề không bị
 *        bóp còn 0dp — đúng lỗi đã gặp trên máy ảo 360dp.
 */
@Composable
fun LabToolbar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    filters: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    stacked: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        if (stacked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Tokens.s4,
                        end = Tokens.s3,
                        top = Tokens.s2,
                        bottom = if (filters != null) Tokens.s1 else Tokens.s2,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.lab.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (actions != null) {
                    Spacer(Modifier.width(Tokens.s1))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Lab.toolbarMinHeight)
                    .padding(start = Tokens.s5, end = Tokens.s4, top = Tokens.s3, bottom = Tokens.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.lab.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Tokens.s1 / 2),
                        )
                    }
                }
                if (actions != null) {
                    Spacer(Modifier.width(Tokens.s3))
                    Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
        if (filters != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        start = if (stacked) Tokens.s4 else Tokens.s5,
                        end = if (stacked) Tokens.s3 else Tokens.s4,
                        bottom = if (stacked) Tokens.s2 else Tokens.s3,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.s2),
                content = filters,
            )
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(Tokens.dividerLine)
                .background(AppTheme.lab.line),
        )
    }
}
