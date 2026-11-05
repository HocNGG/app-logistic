## Dùng khi nào

- Viết bất kỳ màn hình nào trong `feature/` (DRIVER, CS CONSOLE, TRACK) — bộ đồ nghề Material 2 trên desktop.
- Cần dialog/dropdown/file picker/snackbar mà **không** kéo material3.
- Cần `StateHost` để mọi màn có đủ 4 trạng thái (`agents/fe/AGENT.md` §4, Luật 7).

## 1. Window → Scaffold → AppBar

`Window { }` đã ở `main.kt` (skill 01). Mỗi màn hình dùng **một** `Scaffold` M2:

```kotlin
@Composable
fun AppScaffold(
    title: String,
    snackbar: SnackbarHostState,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = Scaffold(
    topBar = {
        TopAppBar(                                     // Material 2
            title = { Text(title, style = MaterialTheme.typography.h6, maxLines = 1,
                            overflow = TextOverflow.Ellipsis) },
            actions = actions, elevation = 0.dp,
        )
    },
    snackbarHost = { SnackbarHost(snackbar) },
    content = content,
)
```

`TopAppBar` M2 **không** có `scrollBehavior`/`windowInsets` (đó là M3) — đừng copy snippet M3.

## 2. LazyColumn với `key` (bắt buộc)

```kotlin
LazyColumn(state = listState, contentPadding = PaddingValues(vertical = Tokens.s2)) {
    items(items = jobs, key = { it.id }) { job ->        // key = id ổn định, KHÔNG dùng index
        JobRow(job, selected = job.id == selectedId, onClick = { onSelect(job.id) })
        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.10f))
    }
}
```

`listState` **không** được tạo trong nhánh layout (skill 03 §4) — nếu không, resize là mất scroll.

## 3. Form + validation

```kotlin
var packages by rememberSaveable { mutableStateOf("") }
val error = when (val n = packages.toIntOrNull()) {
    null -> "Vui lòng nhập số kiện"
    else -> if (n <= 0) "Số kiện phải lớn hơn 0" else null
}
OutlinedTextField(
    value = packages, onValueChange = { packages = it.filter(Char::isDigit) },
    label = { Text("Số kiện thực tế") }, isError = error != null,
    modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error!!) },
)
if (error != null) Text(error, color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption)
```

## 4. Dialog · Dropdown · Snackbar · Menu

```kotlin
Dialog(onDismissRequest = onDismiss) {                       // kiểm soát layout hoàn toàn
    Surface(shape = RoundedCornerShape(Tokens.rMd), elevation = 8.dp) {
        Column(Modifier.padding(Tokens.s5).widthIn(max = 520.dp)) { /* nội dung */ }
    }
}
AlertDialog(onDismissRequest = onDismiss, title = { Text("Duyệt chuyến") },
    text = { Text("Duyệt ${chuyen.code}?") },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Duyệt") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } })

Box {
    TextButton(onClick = { expanded = true }) { Text("Sắp xếp"); Icon(Icons.Default.ArrowDropDown, null) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(onClick = { onSort(Sort.KgDesc); expanded = false }) { Text("Kg giảm dần") }
    }
}

LaunchedEffect(msg) { msg?.let { snackbar.showSnackbar(it); onMsgShown() } }   // snackbar từ AppScaffold

MenuBar {                                                    // menu bar cửa sổ, đặt trong main.kt
    Menu("Tệp", mnemonic = 'T') { Item("Đăng xuất", onClick = onLogout); Separator() }
    Menu("Trợ giúp") { Item("Phím tắt (?)", onClick = onHelp) }
}
```
## 5. FileDialog — chỉ ở `desktopMain` (AWT)

```kotlin
// desktopMain/.../ui/platform/FilePicker.desktop.kt
class DesktopFilePicker(private val window: Frame) : FilePicker {
    override suspend fun pickImages(): List<File> = withContext(Dispatchers.IO) {
        val dialog = FileDialog(window, "Chọn ảnh hàng hoá", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") } }
        }
        dialog.isVisible = true
        dialog.files.toList()                       // rỗng nếu người dùng Cancel
    }
}
```

Giữ interface `FilePicker` ở `commonMain` (suspend, trả `List<File>` hoặc `List<ByteArray>`); `desktopMain` là nơi duy nhất import `java.awt.*`. Nếu `FileDialog` không nhớ thư mục trên máy demo → đổi impl sang `JFileChooser` + `FileNameExtensionFilter`, UI không phải sửa.

## 6. Bốn trạng thái — `UiState` + `StateHost` (dùng lại khắp app)

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
}

@Composable
fun <T> StateHost(state: UiState<T>, modifier: Modifier = Modifier,
                  emptyText: String, content: @Composable (T) -> Unit) = when (state) {
    UiState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    UiState.Empty -> Placeholder(emptyText, "Bấm R để tải lại", modifier)
    is UiState.Error -> Placeholder(state.message, "Thử lại", modifier, onAction = state.retry)
    is UiState.Content -> content(state.data)
}

@Composable
private fun Placeholder(text: String, action: String, modifier: Modifier = Modifier, onAction: (() -> Unit)? = null) =
    Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.subtitle1)
        onAction?.let { TextButton(onClick = it) { Text(action) } }
    }
```


## 7. Màn hình mẫu đầy đủ — `DriverJobsScreen`

```kotlin
@Composable
fun DriverJobsScreen(vm: DriverJobsViewModel = koinInject(), onOpenJob: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    AppScaffold(title = "Việc hôm nay", snackbar = snackbar, actions = {
        IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Tải lại") }
        OutboxBadge(count = vm.pendingCount)                    // skill 05
    }) { padding ->
        StateHost(state, Modifier.padding(padding), emptyText = "Hôm nay chưa có việc nào") { jobs ->
            LazyColumn(state = vm.listState, modifier = Modifier.fillMaxSize()) {
                items(jobs, key = { it.id }) { job ->
                    JobRow(job, selected = job.id == vm.selectedId,
                           onClick = { vm.select(job.id); onOpenJob(job.id) })
                }
            }
        }
    }
    LaunchedEffect(Unit) { vm.refresh() }
}
```

`listState` + `selectedId` + text đang gõ nằm ở **ViewModel** (`LazyListState`, `mutableStateOf`) — điều kiện để duo UI không mất ngữ cảnh khi kéo cửa sổ (xem skill 03 §4).

## Checklist nhanh

- [ ] Mọi màn dùng `AppScaffold` + `StateHost`; không màn nào chỉ có nhánh "có dữ liệu".
- [ ] `LazyColumn` có `key` là `id` (không index); `state` hoist từ ViewModel.
- [ ] Không có `import androidx.compose.material3.*` trong file UI.
- [ ] Dialog/dropdown đóng được bằng `Esc` (skill 10).
- [ ] `FilePicker` ở `desktopMain`; `commonMain` không import `java.awt.*`.
- [ ] Chuỗi hiển thị tiếng Việt có dấu, lấy từ resource (skill 11).
- [ ] Trạng thái rỗng/lỗi có hành động tiếp theo, không phải màn trắng.
