package vn.lab.logistics.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.LogoMark

/**
 * =====================================================================================
 * NAV 2 TẦNG (spec `ui-dashboard-app-screens.md` §1, `ui-modern-industrial.md` §5).
 *
 * Cấu trúc của nav dọc — đúng ảnh mẫu ref-01/ref-02:
 * ```
 * [wordmark  logo + tên app + dòng phụ]      ← tầng 1
 * NHÓM MỤC                                   ← nhãn nhóm 11sp, chữ thường, ink2
 *   item                 (nền trong suốt)    ← tầng 2
 * ▎item đang mở          (nền nhấn 10%, vạch trái 3dp, chữ đậm)
 * [footer: vai trò + nút "Cài đặt demo" (icon bánh răng vẽ bằng Canvas)]
 * ```
 *
 * Vì sao vạch trái + nền 10% (không phải "viên thuốc đầy màu"): đó là mẫu của cả 4 ảnh tham
 * chiếu, và nó giữ được nhịp đọc dọc. Vạch 3dp là **thành phần đồ hoạ mang thông tin** nên
 * tương phản của nó với nền panel đã được đo (self-check khối 1b).
 *
 * Điện thoại KHÔNG dùng component này (nav dọc cần 236dp): dùng [LabTopNav] 2 hàng.
 * =====================================================================================
 */

/** Một mục nav. `shortLabel` dùng cho nav ngang ở điện thoại (tab chia đều 1/3 màn 360dp). */
data class LabNavItem(
    val label: String,
    val shortLabel: String = label,
)

/** Một nhóm mục nav (nhãn nhóm + danh sách mục). */
data class LabNavGroup(
    val title: String,
    val items: List<LabNavItem>,
)

/**
 * Nav dọc của desktop (≥ 900dp).
 *
 * @param activeIndex chỉ số của mục đang mở, tính trên danh sách ĐÃ LÀM PHẲNG theo thứ tự nhóm
 *        (nhóm 1 trước, trong nhóm theo thứ tự khai báo) — cùng quy ước với `onSelect`.
 * @param footer chân nav: vai trò đang đăng nhập + nút "Cài đặt demo" (chỗ DUY NHẤT của các công
 *        tắc demo trên desktop, thay cho thanh demo cũ ở đỉnh cửa sổ).
 */
@Composable
fun LabSidebar(
    title: String,
    subtitle: String,
    groups: List<LabNavGroup>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (collapsed) {
        Column(
            modifier = modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(AppTheme.lab.panel)
                .padding(vertical = Tokens.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoMark(size = 24.dp)
            if (onToggleCollapse != null) {
                Spacer(Modifier.height(Tokens.s2))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(Lab.navItemRadius))
                        .background(AppTheme.lab.surface2)
                        .clickable(onClick = onToggleCollapse),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "»",
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.lab.ink,
                    )
                }
            }
            Spacer(Modifier.height(Tokens.s3))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var index = 0
                groups.forEach { group ->
                    group.items.forEach { item ->
                        val current = index
                        val active = current == activeIndex
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(36.dp)
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(Lab.navItemRadius))
                                .background(
                                    if (active) {
                                        AppTheme.lab.accent.copy(alpha = Lab.NAV_PILL_TINT)
                                    } else {
                                        Tokens.transparent
                                    },
                                )
                                .clickable { onSelect(current) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = item.shortLabel.take(3),
                                style = MaterialTheme.typography.caption,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) AppTheme.lab.ink else AppTheme.lab.ink2,
                                maxLines = 1,
                            )
                        }
                        index += 1
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .width(Lab.NAV_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(AppTheme.lab.panel)
            .padding(vertical = Tokens.s3),
    ) {
        // ------------------------------------------------------------------ tầng 1: wordmark
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoMark(size = Tokens.logoNavSize)
            Spacer(Modifier.width(Tokens.s2))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.lab.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onToggleCollapse != null) {
                Spacer(Modifier.width(Tokens.s1))
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(Lab.navItemRadius))
                        .background(AppTheme.lab.surface2)
                        .clickable(onClick = onToggleCollapse)
                        .padding(horizontal = Tokens.s2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "« Thu",
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(Lab.navGroupGap))

        // ------------------------------------------------------------------ tầng 2: nhóm mục
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            var index = 0
            groups.forEach { group ->
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Tokens.s4, end = Tokens.s4, bottom = Tokens.s1),
                )
                group.items.forEach { item ->
                    val current = index
                    LabSidebarItem(
                        label = item.label,
                        active = current == activeIndex,
                        onClick = { onSelect(current) },
                    )
                    index += 1
                }
                Spacer(Modifier.height(Tokens.s4))
            }
        }

        if (footer != null) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(Tokens.dividerLine)
                    .background(AppTheme.lab.line),
            )
            Column(Modifier.fillMaxWidth().padding(Tokens.s4)) { footer() }
        }
    }
}

/** Một mục của nav dọc: vạch trái 3dp + nền nhấn 10% khi đang mở (khối bo góc 8dp). */
@Composable
private fun LabSidebarItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Lab.navItemHeight)
            .padding(horizontal = Lab.navItemInset, vertical = 1.dp)
            .clip(RoundedCornerShape(Lab.navItemRadius))
            .background(
                if (active) AppTheme.lab.ink.copy(alpha = Lab.NAV_ACTIVE_TINT) else Tokens.transparent,
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Vạch trái 3dp: chỉ có ở mục đang mở (không phải "đường kẻ trang trí").
        // Cao bằng lòng khối (44 - 2dp giãn dọc) để đầu–cuối vạch trùng mép khối bo góc.
        Box(
            Modifier
                .width(Lab.navActiveBar)
                .height(Lab.navItemHeight - 2.dp)
                .background(if (active) AppTheme.lab.ink else Tokens.transparent),
        )
        Spacer(Modifier.width(Tokens.s3))
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) AppTheme.lab.ink else AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Nav NGANG 2 hàng cho điện thoại (< 600dp): hàng 1 wordmark + slot phải, hàng 2 các tab chia
 * đều bề rộng. Tab đang mở = chữ nhấn + vạch dưới 2dp (cùng ngôn ngữ với vạch trái của sidebar,
 * nhưng nằm ngang — không dùng nền đầy để chữ 13sp vẫn nhẹ).
 *
 * Vì sao chia đều bằng `weight(1f)`: nhãn tiếng Việt dài ngắn khác nhau ("Giới thiệu",
 * "Đăng nhập", "Tài xế"); chia đều + ellipsis thì không bao giờ tràn và không xuống dòng theo
 * từng ký tự (lỗi đã gặp trên máy ảo 360dp).
 */
@Composable
fun LabTopNav(
    title: String,
    items: List<LabNavItem>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val activeShort = items.getOrNull(activeIndex)?.shortLabel
    Column(modifier.fillMaxWidth().background(AppTheme.lab.panel)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.s3, vertical = Tokens.s1 + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onToggleCollapse != null) {
                            Modifier.clickable(onClick = onToggleCollapse)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoMark(size = 20.dp)
                Spacer(Modifier.width(Tokens.s2))
                Text(
                    text = if (collapsed && !activeShort.isNullOrBlank()) {
                        "$title · $activeShort"
                    } else {
                        title
                    },
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.lab.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(Tokens.s1))
                trailing()
            }
            if (onToggleCollapse != null) {
                Spacer(Modifier.width(Tokens.s1))
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(Lab.navItemRadius))
                        .background(AppTheme.lab.surface2)
                        .clickable(onClick = onToggleCollapse)
                        .padding(horizontal = Tokens.s2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (collapsed) "Menu ▾" else "Ẩn ▲",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.lab.ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (!collapsed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Tokens.s3, end = Tokens.s3, bottom = Tokens.s1),
                horizontalArrangement = Arrangement.spacedBy(Tokens.s1),
            ) {
                items.forEachIndexed { index, item ->
                    LabTopNavItem(
                        label = item.shortLabel,
                        active = index == activeIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(Tokens.dividerLine)
                .background(AppTheme.lab.line),
        )
    }
}

@Composable
private fun LabTopNavItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(Lab.navItemRadius))
            .background(
                if (active) AppTheme.lab.accent.copy(alpha = Lab.NAV_PILL_TINT) else Tokens.transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) AppTheme.lab.ink else AppTheme.lab.ink2,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
