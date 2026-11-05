package vn.lab.logistics.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens

/**
 * =====================================================================================
 * DUO SCAFFOLD — layout "iPhone 18 duo UI" theo docs/04-kmp-app.md §4.
 *
 * Ba ngưỡng (tiêu chí nghiệm thu, KHÔNG được tự đổi):
 *
 *  | Bề rộng cửa sổ | Arrangement    | Hành vi                                            |
 *  |----------------|----------------|----------------------------------------------------|
 *  | >= 1280 dp     | [SplitWide]    | danh sách 400dp + chi tiết + cột phụ 340dp         |
 *  | 900–1280 dp    | [SplitNarrow]  | danh sách 300dp + chi tiết; **ẩn** cột phụ         |
 *  | < 900 dp       | [Overlay]      | 1 pane; chọn item → chi tiết PHỦ LÊN, có Quay lại  |
 *
 * Vì sao dùng `widthDp: Int` (tham số) thay vì `LocalWindowInfo`:
 *  1. Layout trở thành **hàm thuần** của tham số → self-check/unit test kiểm được khi offline
 *     (không cần dựng cửa sổ GUI). Test resize = gọi lại composable với `widthDp` khác.
 *  2. CALLER là nơi duy nhất biết bề rộng thật → sau này `main.kt`/`WindowState` bơm vào,
 *     đổi nguồn bề rộng không phải sửa component.
 *
 * DISPLACEMENT PATTERNS (docs/04 §4) — vì sao STATE PHẢI NẰM NGOÀI COMPOSABLE NÀY:
 * Khi bề rộng đổi ngưỡng, nhánh `when` ở dưới đổi → subtree của nhánh cũ bị **dispose**.
 * Mọi thứ `remember` bên trong đó (item đang chọn, `LazyListState`, text đang gõ) sẽ mất.
 * Vì vậy component này cố tình **không giữ state nào**: selection/scroll/list-state do
 * caller (ViewModel) giữ rồi truyền vào qua tham số:
 *   - `hasSelection` → caller quyết định (đã "state lifted")
 *   - `listPane`     → caller truyền `LazyColumn(state = vm.listState)` đã tạo MỘT LẦN
 *   - `detailPane`   → nhận `selected` để tự vẽ empty-detail hay nội dung
 * Kéo 1600 → 700 → 1600 dp sẽ giữ nguyên item đang chọn + vị trí scroll.
 *
 * RESERVED REGIONS (docs/04 §4, skill 03 §5):
 *  - `[PaneDivider]` rộng [Tokens.reservedBand] (16dp): đây là vùng CHẾT — trên máy gập,
 *    đường gập rơi đúng vào giữa 2 pane. **Không đặt nút/nội dung quan trọng ở đây**, và
 *    mỗi pane tự chừa thêm `Tokens.s4` (16dp) ở mép giáp divider (xem `PaneBody`).
 *  - `padding` an toàn (status bar / thanh điều hướng / bàn phím) do **CALLER** lo, MỘT LẦN ở
 *    gốc: `MainActivity` gọi `safeDrawingPadding()` cho cả app Android (desktop có inset = 0).
 *    Scaffold này KHÔNG tự đệm nữa — trước đây đệm ở đây thì Android bị đệm HAI lần (mất thêm
 *    ~70dp chiều cao trên màn 720×1280), còn desktop thì không thấy gì nên lỗi kín.
 *  - Overlay: nút Quay lại nằm góc trên-trái, cao >= [Tokens.touchSecondary] (48dp);
 *    tay đeo găng thì nút hành động chính phải >= [Tokens.touchPrimary] (56dp).
 * =====================================================================================
 */
enum class DuoArrangement(val label: String) {
    SplitWide("Split rộng"),
    SplitNarrow("Split hẹp"),
    Overlay("Overlay 1 pane");

    companion object {
        /**
         * Quyết định arrangement từ bề rộng. HÀM THUẦN — nguồn sự thật duy nhất của 3 ngưỡng.
         * Không dùng ngưỡng 600/840/1200 của `WindowSizeClass`: đó là ngưỡng của framework,
         * ngưỡng nghiệp vụ của dự án là 1280/900 (docs/04 §4).
         */
        fun fromWidth(widthDp: Int): DuoArrangement = when {
            widthDp >= Tokens.BREAKPOINT_SPLIT_WIDE_DP -> SplitWide
            widthDp >= Tokens.BREAKPOINT_SPLIT_NARROW_DP -> SplitNarrow
            else -> Overlay
        }
    }
}

/**
 * @param widthDp      bề rộng hiện tại (dp) — do CALLER quyết định nguồn (cửa sổ thật hoặc demo).
 * @param hasSelection caller đã có item đang chọn chưa (dùng cho arrangement Overlay).
 * @param onBack       đóng pane chi tiết ở overlay (nút Quay lại + phím Esc do caller xử lý).
 * @param topBar       thanh công cụ của màn hình.
 * @param snackbar     host state để màn hình hiện thông báo (null = không hiện).
 * @param extraPane    cột thứ 3 (thông tin chuyến) — **chỉ** xuất hiện ở [DuoArrangement.SplitWide].
 * @param listPane     pane danh sách; được compose ở CẢ 3 ngưỡng (overlay vẫn giữ scroll).
 * @param detailPane   pane chi tiết; `selected = false` → caller tự vẽ empty-detail.
 */
@Composable
fun DuoScaffold(
    widthDp: Int,
    hasSelection: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    extraPane: (@Composable () -> Unit)? = null,
    overlayTitle: String = "Chi tiết",
    listPane: @Composable () -> Unit,
    detailPane: @Composable (selected: Boolean) -> Unit,
) {
    val arrangement = DuoArrangement.fromWidth(widthDp)

    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = { snackbar?.let { SnackbarHost(hostState = it) } },
        backgroundColor = MaterialTheme.colors.background,
    ) { padding ->
        // Reserved region: padding an toàn do CALLER lo ở gốc (xem KDoc) — ở đây chỉ còn
        // padding của Scaffold (thanh trên + snackbar).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (arrangement) {
                DuoArrangement.SplitWide -> Row(Modifier.fillMaxSize()) {
                    PaneBody(Modifier.width(Tokens.listPaneWide)) { listPane() }
                    PaneDivider()
                    PaneBody(Modifier.weight(1f)) { detailPane(hasSelection) }
                    if (extraPane != null) {
                        PaneDivider()
                        PaneBody(Modifier.width(Tokens.extraPane)) { extraPane() }
                    }
                }

                DuoArrangement.SplitNarrow -> Row(Modifier.fillMaxSize()) {
                    PaneBody(Modifier.width(Tokens.listPaneNarrow)) { listPane() }
                    PaneDivider()
                    // Cột phụ (extraPane) bị ẨN có chủ đích ở ngưỡng này — chỉ hiện ở SplitWide.
                    PaneBody(Modifier.weight(1f)) { detailPane(hasSelection) }
                }

                DuoArrangement.Overlay -> Box(Modifier.fillMaxSize()) {
                    // Danh sách được compose ở CẢ 3 ngưỡng → scroll không bị mất khi mở/đóng overlay.
                    listPane()
                    AnimatedVisibility(
                        visible = hasSelection,
                        enter = fadeIn() + slideInHorizontally { it / 4 },
                        exit = fadeOut() + slideOutHorizontally { it / 4 },
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colors.background,
                            elevation = Tokens.elevOverlay,
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                OverlayBar(title = overlayTitle, onBack = onBack)
                                Box(Modifier.weight(1f)) { detailPane(true) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Một pane: tự chừa băng reserved ở mép giáp divider (16dp) để nút không rơi vào đường gập. */
@Composable
private fun PaneBody(width: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = width
            .fillMaxHeight()
            .padding(horizontal = Tokens.s4 / 2), // = 8dp mỗi bên, cộng với 16dp divider = 16dp mỗi phía
    ) {
        content()
    }
}

/**
 * Vách giữa 2 pane — vùng RESERVED (bản lề/đường gập). Rộng 16dp, chỉ vẽ một đường 1dp ở giữa.
 * Không đặt nút trong này: trên thiết bị gập, đường gập nằm đúng ở đây.
 */
@Composable
private fun PaneDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(Tokens.reservedBand),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(Tokens.dividerLine)
                .background(AppTheme.lab.lineStrong),
        )
    }
}

/** Thanh trên của overlay: nút Quay lại (>= 48dp) ở góc trên-trái + tiêu đề. */
@Composable
private fun OverlayBar(title: String, onBack: () -> Unit) {
    // Phẳng (elevation = 0): ADR-009 §6 — bóng CHỈ cho dialog/menu. Thanh này là "page header"
    // của pane, phân tách bằng đúng một đường kẻ.
    Surface(color = MaterialTheme.colors.surface, elevation = Tokens.elevNone) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.touchPrimary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = Tokens.s1)
                    .height(Tokens.touchSecondary),
            ) {
                // Không dùng icon library (compose.material-icons không có trong classpath offline):
                // mũi tên typographic + nhãn tiếng Việt, không phải emoji.
                Text("‹ Quay lại")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Tokens.s2, end = Tokens.s4),
            )
        }
    }
}
