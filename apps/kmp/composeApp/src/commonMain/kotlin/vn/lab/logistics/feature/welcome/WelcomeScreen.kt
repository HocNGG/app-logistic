package vn.lab.logistics.feature.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.platformInfo
import vn.lab.logistics.ui.components.BrandLogo
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabEnter
import vn.lab.logistics.ui.kit.LocalReducedMotion

/**
 * =====================================================================================
 * MÀN WELCOME — "cửa vào" của app, cảnh MỞ ĐẦU của video demo.
 *
 * ĐÃ LÀM LẠI (brief hạng mục 2: "hero gọn hơn, bớt khung trang trí, dùng LabCard"):
 *  - BỎ nền gradient dọc + hai quầng sáng radial + lưới phủ toàn màn — đó đúng là bộ *tell* của
 *    "giao diện AI đại trà" mà `ui-modern-industrial.md` cấm, và nó làm màn này nặng hơn mọi
 *    màn nghiệp vụ.
 *  - BỎ khung trang trí quanh logo (card 176dp có viền + tilt 3D + nhấp nhô vô hạn). Logo nay
 *    nằm trong wordmark 30dp ở góc, đúng chỗ nó nên nằm trong một sản phẩm.
 *  - BỎ dải "3 trạng thái màn hình" (Đang khởi động / Sẵn sàng / Giảm chuyển động): đó là chi
 *    tiết kỹ thuật của app, không phải thông tin người dùng cần thấy.
 *  - GIỮ: 3 chặng nghiệp vụ thật (lấy hàng → gom chuyến → về kho) nay là 3 thẻ trong
 *    [LabCard], và CTA "Vào app".
 *
 * Ba trạng thái thật của màn ([WelcomePhase]) vẫn còn — chỉ dùng cho hiệu ứng vào màn, không
 * hiện nhãn ra UI. Màn này KHÔNG gọi API nên không có empty/error dữ liệu; 4 trạng thái dữ liệu
 * nằm ở `LoginScreen` và 3 màn làm việc (qua `StateHost`).
 * =====================================================================================
 */
enum class WelcomePhase {
    /** Logân "lắp" trong ~650ms trước khi hero sẵn sàng (chỉ ảnh hưởng hiệu ứng vào màn). */
    DangKhoiDong,

    /** Hero đầy đủ, CTA bấm được. */
    SanSang,

    /** Người dùng bật "giảm chuyển động" → vào thẳng trạng thái sẵn sàng, không hiệu ứng. */
    GiamChuyenDong,
}

/** Thời gian "lắp" trước khi hero sẵn sàng. Ngắn thôi — đừng bắt người xem chờ. */
private const val ENTER_ANIMATION_MS = 650L

/**
 * Ba chặng nghiệp vụ — MỖI chặng là một bước thật của luồng 19 bước (docs/00), không phải
 * "feature card" trang trí: đó là lý do màn này tồn tại.
 */
private data class Stage(val title: String, val detail: String)

private val STAGES = listOf(
    Stage(
        title = "Lấy hàng tại shop",
        detail = "Tài xế nhận việc, chụp ảnh kiện hàng và ký biên nhận ngay tại shop.",
    ),
    Stage(
        title = "Gom vào một chuyến",
        detail = "Nhiều shop gom vào một chuyến; cước chốt trên Odoo, app chỉ hiển thị đúng số.",
    ),
    Stage(
        title = "Về kho & đối soát",
        detail = "Kho nhận hàng, Odoo sinh phiếu nhập kho; kế toán đối soát trên cùng dữ liệu.",
    ),
)

@Composable
fun WelcomeScreen(
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    var phase by remember {
        mutableStateOf(if (reduced) WelcomePhase.GiamChuyenDong else WelcomePhase.DangKhoiDong)
    }

    LaunchedEffect(reduced) {
        if (reduced) {
            phase = WelcomePhase.GiamChuyenDong
        } else {
            phase = WelcomePhase.DangKhoiDong
            delay(ENTER_ANIMATION_MS)
            phase = WelcomePhase.SanSang
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(AppTheme.lab.ground)) {
        val compact = maxWidth < Tokens.BREAKPOINT_COMPACT_DP.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) Tokens.s4 else Tokens.s6, vertical = Tokens.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Lab.READING_MAX_WIDTH_DP.dp).fillMaxWidth()) {
                // ---------------------------------------------------------------- wordmark
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo(
                        markSize = Tokens.logoNavSize,
                        title = "AppLogictics",
                        subtitle = "Gom hàng lẻ · Odoo 17",
                        titleColor = AppTheme.lab.ink,
                    )
                    Spacer(Modifier.padding(start = Tokens.s2))
                    LabChip(text = "Odoo 17", tone = Tone.Info)
                }

                Spacer(Modifier.height(Tokens.s6))

                // ------------------------------------------------------------------- hero
                LabEnter {
                    Text(
                        text = "Gom hàng lẻ xuất khẩu, gọn trong một chuyến",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = if (compact) Tokens.heroTitleSizeCompact else Tokens.heroTitleSize,
                            lineHeight = if (compact) Tokens.heroTitleLineCompact else Tokens.heroTitleLine,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = AppTheme.lab.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Gom nhiều shop vào một chuyến — chốt cước, xuất kho, giao tận " +
                            "nơi. App làm việc cùng Odoo 17, không tự tính cước hay tồn kho.",
                        style = MaterialTheme.typography.body1,
                        color = AppTheme.lab.ink2,
                        modifier = Modifier.padding(top = Tokens.s3),
                    )

                    Spacer(Modifier.height(Tokens.s5))

                    // -------------------------------------------------- 3 chặng nghiệp vụ
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(Tokens.s3)) {
                            STAGES.forEach { stage -> StageCard(stage, modifier = Modifier.fillMaxWidth()) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s3)) {
                            STAGES.forEach { stage ->
                                StageCard(stage, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(Tokens.s5))

                    // ----------------------------------------------------------------- CTA
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onEnter,
                            enabled = phase != WelcomePhase.DangKhoiDong,
                            modifier = Modifier
                                .widthIn(min = 220.dp)
                                .height(Tokens.touchPrimary),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = AppTheme.lab.accent,
                                contentColor = AppTheme.lab.onAccent,
                            ),
                        ) {
                            Text("Vào app")
                        }
                        Text(
                            text = "Không cần mạng để mở app. Dữ liệu thật lấy từ Odoo qua API key.",
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                            maxLines = 2,
                            modifier = Modifier.padding(start = Tokens.s4),
                        )
                    }

                    Spacer(Modifier.height(Tokens.s5))

                    // -------------------------------------------------------------- footer
                    Text(
                        text = platformInfo(),
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Tokens.s7))
                }
            }
        }
    }
}

/** Một chặng nghiệp vụ: tiêu đề 16sp + một câu giải thích. Không số thứ tự trang trí. */
@Composable
private fun StageCard(stage: Stage, modifier: Modifier = Modifier) {
    LabCard(modifier = modifier, padding = androidx.compose.foundation.layout.PaddingValues(Tokens.s4)) {
        Text(
            text = stage.title,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.lab.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stage.detail,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s2),
        )
    }
}
