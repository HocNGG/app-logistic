## Dùng khi nào

- Polish P7/P9: bàn phím-first, `Ctrl/Cmd+K`, `J/K`, `Enter`, `Esc`, `R`, `D`, `?`.
- Cần focus ring, chặn focus trong dialog, label cho screen reader.
- Chạy a11y walkthrough trước khi handoff (Luật 7 + `agents/fe/AGENT.md` §7).

## 1. Bản đồ phím (đúng `docs/04` §11)

| Phím | `ShortcutAction` | Ghi chú |
|---|---|---|
| `Ctrl`/`Cmd` + `K` | `CommandPalette` | hoạt động **kể cả** khi đang gõ trong ô text |
| `J` / `↓`, `K` / `↑` | `Next` / `Prev` | di chuyển trong danh sách đang focus |
| `Enter` | `Open` | mở chi tiết item đang chọn |
| `Esc` | `Back` | đóng overlay/dialog, huỷ |
| `R` | `Refresh` | tải lại pane hiện tại |
| `D` | `Approve` | (CS console) mở dialog "Duyệt" |
| `?` | `Help` | mở bảng phím tắt |
| `Tab` / `Shift+Tab` | — | traversal mặc định, có focus ring 2dp |

Luật: **mọi hành động phải làm được không cần chuột**; phím chữ (`J/K/R/D`) **phải nhường** khi con trỏ đang ở ô nhập text.

## 2. `ShortcutHost` — modifier key handler helper

```kotlin
// commonMain/ui/keys/Shortcuts.kt
enum class ShortcutAction { CommandPalette, Next, Prev, Open, Back, Refresh, Approve, Help }
data class KeyBinding(val key: Key, val ctrlOrMeta: Boolean = false, val shift: Boolean = false)
val DefaultBindings: Map<ShortcutAction, KeyBinding> = mapOf(
    ShortcutAction.CommandPalette to KeyBinding(Key.K, ctrlOrMeta = true),
    ShortcutAction.Next to KeyBinding(Key.J),
    ShortcutAction.Prev to KeyBinding(Key.K),
    ShortcutAction.Open to KeyBinding(Key.Enter),
    ShortcutAction.Back to KeyBinding(Key.Escape),
    ShortcutAction.Refresh to KeyBinding(Key.R),
    ShortcutAction.Approve to KeyBinding(Key.D),
    ShortcutAction.Help to KeyBinding(Key.Slash, shift = true),          // "?" = Shift + /
)
// ô nhập text tự khai báo: LocalTextInputFocused provides true (bọc trong AppTextField của design/)
val LocalTextInputFocused = compositionLocalOf { false }
@Composable
fun ShortcutHost(onAction: (ShortcutAction) -> Unit, content: @Composable () -> Unit) {
    val textFocused = LocalTextInputFocused.current
    Box(Modifier.fillMaxSize().focusable().onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val meta = e.isCtrlPressed || e.isMetaPressed                     // Cmd trên macOS
        val hit = DefaultBindings.entries.firstOrNull { (_, b) ->
            b.key == e.key && b.ctrlOrMeta == meta && b.shift == e.isShiftPressed
        } ?: return@onPreviewKeyEvent false
        val allowedWhileTyping = hit.key in setOf(ShortcutAction.CommandPalette, ShortcutAction.Back)
        if (textFocused && !allowedWhileTyping) return@onPreviewKeyEvent false
        onAction(hit.key); true
    }) { content() }
}
```

Dùng ở `App.kt`: `ShortcutHost(onAction = { vm.onShortcut(it) }) { Scaffold(...) }`. Xử lý action trong **ViewModel** (để test được), không nhét logic vào composable.
⚠️ **kiểm tra lại API của Compose Multiplatform 1.12.2**: `onPreviewKeyEvent`, `KeyEvent.isCtrlPressed/isMetaPressed/isShiftPressed`, `KeyEventType.KeyDown` — đúng ở 1.6+, xác nhận lại khi nâng version.

## 3. Focus ring 2dp

```kotlin
@Composable
fun Modifier.focusRing(focused: Boolean): Modifier =
    if (focused) border(Tokens.focusRing, MaterialTheme.colors.primary, RoundedCornerShape(Tokens.rSm)).padding(1.dp)
    else this
@Composable
fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Button(
        onClick = onClick, interactionSource = interaction,
        modifier = modifier.heightIn(min = Tokens.touchSecondary).focusRing(focused == true)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) { onClick(); true } else false
            },
    ) { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}
```

Không dùng `Modifier.border` **trong** `Button` default (mất khi đổi state) — luôn bọc như trên. `Button` M2 nhận `interactionSource` (kiểm tra chữ ký tham số khi nâng version).

## 4. Focus trong dialog

```kotlin
@Composable
fun ApproveDialog(chuyen: Chuyen, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Tokens.rMd), elevation = 8.dp) {
            Column(Modifier.padding(Tokens.s5).focusGroup()) {
                Text("Duyệt chuyến ${chuyen.code}", style = MaterialTheme.typography.h6)
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("Lý do (nếu giảm tải)") },
                    modifier = Modifier.focusRequester(first))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Huỷ (Esc)") }
                    Button(onClick = onConfirm) { Text("Duyệt") }
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}
```

- Focus vào control đầu tiên bằng `FocusRequester` — **không** để focus nằm lại sau dialog.
- `Esc` do `ShortcutHost` lo (`Back` → `onDismiss`).
- `Dialog` của Compose Desktop là cửa sổ riêng → `Tab` không thoát ra ngoài cửa sổ chính (cơ chế của OS). ⚠️ **kiểm tra lại** hành vi này trên Windows với bản CMP đang dùng; nếu dialog là layer trong cùng cửa sổ, phải tự chặn `Tab` ở phần tử cuối (`moveFocus` + `focusGroup`).

## 5. Screen reader labels

```kotlin
IconButton(onClick = vm::refresh, modifier = Modifier.semantics {
    contentDescription = "Tải lại danh sách việc hôm nay"
    role = Role.Button
}) { Icon(Icons.Default.Refresh, contentDescription = null) }   // null: tránh đọc 2 lần
Text("Quá tải 112%", modifier = Modifier.semantics { contentDescription = "Chuyến đã quá tải 112 phần trăm" })
Text(error, color = MaterialTheme.colors.error,
     modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })     // thông báo lỗi có thể được đọc
```

- Không dùng `contentDescription` thay text hiển thị; không nhồi cả câu dài vào `Icon`.
- ⚠️ Hỗ trợ screen reader của Compose Desktop trên Windows còn hạn chế (Narrator/JAWS) — **kiểm tra thật** bằng Narrator; nếu không đọc được, ghi vào PR là giới hạn đã biết, **không** tuyên bố "đã a11y".

## 6. `Ctrl/Cmd + K` — command palette (không cần chuột)

```kotlin
@Composable
fun CommandPalette(open: Boolean, commands: List<Command>, onRun: (Command) -> Unit, onDismiss: () -> Unit) {
    if (!open) return
    var q by rememberSaveable { mutableStateOf("") }
    val filtered = remember(q) { commands.filter { it.label.contains(q, ignoreCase = true) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(min = 520.dp, max = 720.dp), shape = RoundedCornerShape(Tokens.rMd), elevation = 12.dp) {
            Column(Modifier.padding(Tokens.s4)) {
                OutlinedTextField(q, { q = it }, label = { Text("Gõ lệnh hoặc tên chuyến…") },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ô tìm lệnh" })
                if (filtered.isEmpty()) Text("Không có lệnh nào khớp", style = MaterialTheme.typography.caption)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        DropdownMenuItem(onClick = { onRun(c) }) { Text(c.label); Text("  ${c.hint}", style = MaterialTheme.typography.caption) }
                    }
                }
            }
        }
    }
}
```

Palette chỉ chứa **lệnh có thật** (mở màn, refresh, duyệt, chuyển ngôn ngữ) — danh sách khai báo cố định, không "AI bịa lệnh" (skill 13).

## 7. A11y walkthrough (làm trước mỗi handoff, quay video ngắn)

1. Rút chuột ra (hoặc không chạm): từ Login → Driver → mở job → chụp ảnh → ký → confirm.
2. `Tab` đi qua mọi control, **không** bị kẹt, focus ring luôn thấy trên nền sáng và tối.
3. `Ctrl+K` mở palette, gõ, `Enter` chạy lệnh; `Esc` đóng.
4. Dialog: mở bằng `D`, `Tab` không thoát ra ngoài, `Esc` đóng, focus trở về nút đã mở.
5. Bật Narrator (Windows): nghe được tên nút icon-only. Không đọc được → ghi rõ trong PR.

## Checklist nhanh

- [ ] Đủ 7 phím trong `docs/04` §11, `?` mở được bảng trợ giúp.
- [ ] Phím chữ không cướp input khi đang gõ text (`LocalTextInputFocused`).
- [ ] Focus ring 2dp hiện ở mọi control, cả light và dark.
- [ ] Dialog: focus vào control đầu tiên, `Esc` đóng, `Tab` không thoát.
- [ ] Icon-only control có `contentDescription`; thông báo lỗi có `liveRegion`.
- [ ] Đi hết luồng chính chỉ bằng `Tab` + `Enter` (đã quay video).
- [ ] Không tuyên bố "a11y đạt" nếu chưa thử Narrator thật.
