## Dùng khi nào

- Làm layout chính của **CS CONSOLE** (duo UI, P7) và bất kỳ màn nào có "danh sách + chi tiết".
- Cửa sổ bị kéo to/nhỏ và bạn phải chứng minh **không mất** item đang chọn, scroll, text đang gõ.
- Cần dựng lại `ListDetailPaneScaffold` bằng Material 2 (vì `material3` là alpha — ADR-006).

## 1. "Duo" nghĩa là gì ở 3 ngưỡng (`docs/04` §4)

| Bề rộng cửa sổ | `DuoArrangement` | Hành vi |
|---|---|---|
| **≥ 1280 dp** | `SplitWide` | Danh sách 360–420dp + chi tiết + cột phụ 320–360dp (thông tin chuyến) |
| **900–1280 dp** | `SplitNarrow` | Danh sách 300dp + chi tiết; **ẩn** cột phụ |
| **< 900 dp** | `Overlay` | 1 pane; chọn item → chi tiết phủ lên, có nút Quay lại; danh sách **giữ scroll** |

Định nghĩa nghiệm thu: *kéo 1600dp → 700dp → 1600dp mà không mất dữ liệu đang gõ, không mất item đang chọn, không vỡ layout, luồng chính vẫn xong.*

## 2. Đọc bề rộng: nguồn sự thật + adaptive info

```kotlin
// commonMain/ui/DuoArrangement.kt
enum class DuoArrangement { SplitWide, SplitNarrow, Overlay }

// cấp ở main.kt từ WindowState (skill 01 §5) — chính xác theo dp thật của cửa sổ
val LocalWindowWidthDp = staticCompositionLocalOf { 1280.dp }

@Composable
fun rememberDuoArrangement(): DuoArrangement {
    val widthDp = LocalWindowWidthDp.current
    // WindowSizeClass: compact 600 / medium 840 / expanded 1200dp — THÔ hơn ngưỡng dự án (900/1280)
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return remember(widthDp, sizeClass) {
        when {
            widthDp >= 1280.dp -> DuoArrangement.SplitWide
            widthDp >= 900.dp -> DuoArrangement.SplitNarrow
            else -> DuoArrangement.Overlay
        }
    }
}

```

- `widthDp` là **nguồn sự thật** cho 3 ngưỡng của dự án; `windowSizeClass` dùng để biết máy đang compact/medium/expanded (dùng thêm `isWidthAtLeastBreakpoint(...)` khi cần).
- ⚠️ **kiểm tra lại API của `org.jetbrains.compose.material3.adaptive:adaptive:1.3.0`**: `currentWindowAdaptiveInfoV2`, `isWidthAtLeastBreakpoint`, `WIDTH_DP_MEDIUM_LOWER_BOUND` thuộc `androidx.window.core.layout.WindowSizeClass`. Nếu bản 1.3.0 chỉ có `currentWindowAdaptiveInfo` → đổi tên, **không** đổi ngưỡng. Không dùng được thì phương án B: chỉ cần `LocalWindowWidthDp`.

## 3. `DuoScaffold` (Material 2, ~50 dòng) — thay `ListDetailPaneScaffold`

```kotlin
@Composable
fun DuoScaffold(
    arrangement: DuoArrangement,
    hasSelection: Boolean,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    topBar: @Composable () -> Unit,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    extraPane: (@Composable () -> Unit)? = null,
) {
    Scaffold(topBar = topBar, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)) {      // reserved regions
            when (arrangement) {
                DuoArrangement.SplitWide -> Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(400.dp).fillMaxHeight()) { listPane() }
                    PaneDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) { detailPane() }
                    if (extraPane != null) { PaneDivider(); Box(Modifier.width(340.dp).fillMaxHeight()) { extraPane() } }
                }
                DuoArrangement.SplitNarrow -> Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(300.dp).fillMaxHeight()) { listPane() }
                    PaneDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) { detailPane() }
                }
                DuoArrangement.Overlay -> Box(Modifier.fillMaxSize()) {
                    listPane()                                          // vẫn compose → giữ scroll
                    AnimatedVisibility(
                        visible = hasSelection,
                        enter = fadeIn() + slideInHorizontally { it / 4 },
                        exit = fadeOut() + slideOutHorizontally { it / 4 },
                    ) {
                        Surface(Modifier.fillMaxSize(), elevation = 8.dp, color = MaterialTheme.colors.background) {
                            Column {
                                OverlayBar(title = "Chi tiết", onBack = onBack)   // nút Quay lại + Esc (skill 10)
                                Box(Modifier.weight(1f)) { detailPane() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneDivider() = Box(Modifier.fillMaxHeight().width(1.dp)
    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))
```

Material 2 **không có** `VerticalDivider` (đó là M3) — vì vậy `PaneDivider` là `Box`. `WindowInsets.safeDrawing` là của `foundation-layout`, không phải material3.

## 4. DISPLACEMENT — sống sót khi resize (phần dễ sai nhất)

Nguyên tắc: **state không được sinh ra bên trong nhánh layout**. Nhánh bị đổi → subtree bị dispose → `remember` mất sạch.

| State | Nơi giữ | Ghi chú |
|---|---|---|
| Item đang chọn | ViewModel `mutableStateOf<Int?>` | **không** `remember` trong pane |
| Scroll danh sách | `LazyListState` tạo **một lần** ở ViewModel/feature root | không `rememberLazyListState()` trong `listPane` |
| Text đang gõ (ghi chú, dialog duyệt) | ViewModel field, `onValueChange` ghi ngay | `rememberSaveable(key)` chỉ khi state thật sự cục bộ |
| Filter/tab/sort | ViewModel | refresh dữ liệu không được reset |
| Ảnh đã chụp, chữ ký | ViewModel (`List<ByteArray>`, `SignatureDoc`) | skill 06 |

```kotlin
@Composable
fun CsBoardScreen(vm: CsBoardViewModel = koinInject()) {
    val arrangement = rememberDuoArrangement()
    val listState = vm.listState                          // ← ở ViewModel, không tạo trong pane
    val state by vm.board.collectAsState()
    val selected = vm.selectedId

    DuoScaffold(
        arrangement = arrangement,
        hasSelection = selected != null,
        onBack = vm::clearSelection,
        snackbar = vm.snackbar,
        topBar = { CsToolbar(vm) },
        listPane = {
            StateHost(state, emptyText = "Chưa có yêu cầu nào") { board ->
                LazyColumn(state = listState) {              // cùng state ở cả 3 arrangement
                    items(board.requests, key = { it.id }) { r ->
                        RequestRow(r, selected = r.id == selected, onClick = { vm.select(r.id) })
                    }
                }
            }
        },
        detailPane = { if (selected != null) ChuyenDetailPane(vm) else EmptyDetail() },
        extraPane = { ChuyenSummaryPane(vm) },               // tự ẩn khi SplitNarrow/Overlay
    )
}
```

Đừng: `key(arrangement) { ... }`, `Crossfade(arrangement) { }` bọc cả cây (re-create con), `remember { mutableStateOf(...) }` cho text trong dialog duyệt. Được: `animateContentSize()` / hoạt ảnh **trên nội dung pane** để chuyển ngưỡng không "nhảy".

## 5. Reserved regions (bản lề / mép màn hình)

- `.windowInsetsPadding(WindowInsets.safeDrawing)` ở **root của DuoScaffold** (không ở từng pane, tránh cộng dồn padding).
- Không đặt nút quan trọng sát `PaneDivider`: chừa `Tokens.s4` (16dp) mỗi bên — trên thiết bị gập, đường gập rơi đúng vào divider.
- Overlay: nút Quay lại nằm **góc trên trái**, kích thước ≥ 48dp (tay đeo găng: nút chính ≥ 56dp).

## 6. Checklist test cho skill này (chạy tay + tự động)

| # | Thao tác | Kỳ vọng |
|---|---|---|
| 1 | Mở 1440dp, chọn yêu cầu #3 | Panel phải hiện #3, `SplitWide` có cột phụ |
| 2 | Kéo xuống 1100dp | Vẫn #3, cột phụ biến mất, **không** mất scroll |
| 3 | Kéo xuống 800dp | Overlay, chi tiết phủ lên, nút Quay lại hoạt động |
| 4 | Kéo lên 1600dp | Vẫn #3, scroll đúng vị trí cũ |
| 5 | Ở 800dp: gõ vào ô ghi chú → kéo lên 1600dp | Text còn nguyên |
| 6 | `Esc` khi overlay | Đóng overlay, list giữ scroll |
| 7 | Chạy `desktopTest` | `duo_split_select` + `resize_keeps_selection` xanh (skill 12) |

## Checklist nhanh

- [ ] 3 ngưỡng đúng 1280/900 dp, không dùng ngưỡng 600/840/1200 của `WindowSizeClass` cho layout nghiệp vụ.
- [ ] `rememberDuoArrangement()` là nơi **duy nhất** quyết định arrangement.
- [ ] `LazyListState` + selection + text đang gõ nằm ở ViewModel (không `remember` trong pane).
- [ ] `listPane` được compose ở cả 3 arrangement (overlay không dispose danh sách).
- [ ] Không có `VerticalDivider`, `ListDetailPaneScaffold`, `material3.*` component.
- [ ] Có `windowInsetsPadding(WindowInsets.safeDrawing)` ở root, nút không sát divider.
- [ ] Kéo 1600→700→1600 bằng tay: giữ nguyên selection, scroll, text.
