## Dùng khi nào

- Dựng `design/` (P3, việc đầu tiên sau skeleton) — mọi màu/khoảng cách/kiểu chữ đi qua đây.
- Cần thêm màu nghiệp vụ (ok/warn/bad/info) hoặc sửa typography cho tiếng Việt.
- Review PR thấy `Color(0x…)` ngoài `design/` → chặn (đây là luật, không phải sở thích).

## 1. `Tokens.kt` — spacing 4dp, radius, kích thước

```kotlin
// commonMain/design/Tokens.kt
object Tokens {
    // spacing scale 4dp (docs/04 §10)
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp
    val s5 = 24.dp; val s6 = 32.dp; val s7 = 48.dp
    // radius
    val rSm = 6.dp; val rMd = 10.dp; val rLg = 16.dp
    // kích thước điều khiển (tay đeo găng: nút chính ≥ 56dp)
    val touchPrimary = 56.dp; val touchSecondary = 48.dp; val focusRing = 2.dp
    // layout duo (skill 03)
    val listPaneWide = 400.dp; val listPaneNarrow = 300.dp; val extraPane = 340.dp
    // màu nền/đường kẻ (chỉ khai báo Ở ĐÂY)
    val ink900 = Color(0xFF102A43)   // chữ chính
    val ink600 = Color(0xFF486581)   // chữ phụ
    val ink200 = Color(0xFFD9E2EC)   // divider/border
}
```

## 2. Màu nghiệp vụ (semantic) — light + dark

```kotlin
@Immutable
data class SemanticColors(
    val okBg: Color, val okFg: Color,       // đã duyệt, đủ tải
    val warnBg: Color, val warnFg: Color,   // sắp quá tải 90–100%
    val badBg: Color, val badFg: Color,     // quá tải >100%, lỗi
    val infoBg: Color, val infoFg: Color,   // gợi ý, đang đồng bộ
)

val LightSemantic = SemanticColors(
    okBg = Color(0xFFE7F6EC), okFg = Color(0xFF17663A),
    warnBg = Color(0xFFFFF4E5), warnFg = Color(0xFF8A4B00),
    badBg = Color(0xFFFDECEC), badFg = Color(0xFF9B1C1C),
    infoBg = Color(0xFFEAF2FE), infoFg = Color(0xFF1D4ED8),
)

val DarkSemantic = SemanticColors(
    okBg = Color(0xFF10331F), okFg = Color(0xFF9BE7B8),
    warnBg = Color(0xFF3A2A10), warnFg = Color(0xFFFFD79B),
    badBg = Color(0xFF3B1414), badFg = Color(0xFFFFB4B4),
    infoBg = Color(0xFF11264D), infoFg = Color(0xFFA8C8FF),
)
```

Bảng này lấy đúng giá trị `docs/04` §10 cho light; dark **phải** đo lại contrast (§5), không suy ra bằng "đảo màu".

## 3. `AppTheme.kt` — Material 2 + CompositionLocal

```kotlin
// commonMain/design/AppTheme.kt
private val LocalSemantic = staticCompositionLocalOf { LightSemantic }

object AppTheme {
    val semantic: SemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemantic.current
}

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColors(
        primary = Color(0xFF7FB2FF), primaryVariant = Color(0xFF4A7FD1), secondary = Color(0xFF9BE7B8),
        surface = Color(0xFF141B24), background = Color(0xFF0E141B),
        onSurface = Color(0xFFE6EDF5), onBackground = Color(0xFFE6EDF5), error = Color(0xFFFFB4B4),
    ) else lightColors(
        primary = Color(0xFF1D4ED8), primaryVariant = Color(0xFF10399E), secondary = Color(0xFF17663A),
        surface = Color.White, background = Color(0xFFF7F9FC),
        onSurface = Tokens.ink900, onBackground = Tokens.ink900, error = Color(0xFF9B1C1C),
    )
    CompositionLocalProvider(LocalSemantic provides if (darkTheme) DarkSemantic else LightSemantic) {
        MaterialTheme(colors = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(Tokens.rSm), medium = RoundedCornerShape(Tokens.rMd),
    large = RoundedCornerShape(Tokens.rLg),
)
```

## 4. Typography cho tiếng Việt (dấu không bị cắt, không chạm dòng)

```kotlin
val AppTypography = Typography(
    h6 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),   // 1.44×
    subtitle1 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    body1 = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp),  // 1.53×
    body2 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp), // 1.5×
)
```

Luật riêng cho tiếng Việt:
- `lineHeight ≥ 1.4 × fontSize` — dấu mũ/móc (ế, ộ, ằ) cao hơn chữ Latin; lineHeight chật là **cắt dấu**.
- Không dùng `letterSpacing` âm; không `textAllCaps` cho nhãn có dấu ("ĐƠN HÀNG CHƯA XÁC NHẬN" sẽ dày và xấu).
- Nhãn nút: tối đa 2 dòng + `overflow = Ellipsis`; test bằng chuỗi dài nhất (skill 11 §4).
- Số liệu (kg, cước) dùng font tabular nếu có; ít nhất là căn phải và cùng `body1`.

## 5. Contrast ≥ 4.5:1 — đo, đừng đoán

```kotlin
// commonMain/design/Contrast.kt — dùng trong unit test, không cần ở UI
fun relativeLuminance(c: Color): Double {
    fun channel(v: Float) = (if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)).toDouble()
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}
fun contrastRatio(a: Color, b: Color): Double {
    val (l1, l2) = listOf(relativeLuminance(a), relativeLuminance(b)).sortedDescending()
    return (l1 + 0.05) / (l2 + 0.05)
}
```

```kotlin
// desktopTest/design/ContrastTest.kt
@Test fun light_semantic_pairs_pass_wcag_aa() {
    with(LightSemantic) {
        listOf(okFg to okBg, warnFg to warnBg, badFg to badBg, infoFg to infoBg).forEach { (fg, bg) ->
            assertTrue("contrast ${fg} trên ${bg} = ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= 4.5)
        }
    }
}
```

Test tương tự cho `DarkSemantic`, cho `onSurface`/`background` của cả 2 theme. Contrast **< 4.5:1** = fail review, không "tạm ổn".

## 6. Luật cấm hardcode

| Được | Không được |
|---|---|
| `Color(0x…)` trong `design/Tokens.kt`, `design/AppTheme.kt` | `Color(0xFF1D4ED8)` trong `feature/**` |
| `Tokens.s4`, `AppTheme.semantic.okBg` | `16.dp`, `Color.Green` trong màn hình |
| `MaterialTheme.colors.primary` | `Color(0xFF1D4ED8)` copy từ Figma |

Cách ép: thêm bước review grep — `grep -rn "Color(0x" apps/kmp/src --include=*.kt` phải chỉ ra file trong `design/` (và ngoại lệ duy nhất: màu mực chữ ký, khai báo hằng trong `design/`, có comment). Ô `StatusPill` là component duy nhất được map state → màu, đặt ở `ui/components/`.

## 7. Dark mode

- Toggle ở toolbar (lưu `Settings`), mặc định theo `isSystemInDarkTheme()`.
- Dark **không** dùng nền đen tuyệt đối: `background #0E141B` (giảm mỏi mắt, giữ contrast với chữ).
- Ảnh (chữ ký, ảnh kiện) luôn có nền trắng riêng — không để ảnh trắng nổi trên nền trắng ở light mode mà không có border `Tokens.ink200`.

## Checklist nhanh

- [ ] `grep "Color(0x"` chỉ ra file trong `design/` (+ ngoại lệ có comment).
- [ ] Không có `dp`/`sp` "số lẻ" trong `feature/**` (dùng `Tokens.*`).
- [ ] `lineHeight ≥ 1.4 × fontSize` ở mọi `TextStyle`; không `letterSpacing` âm, không `textAllCaps`.
- [ ] `ContrastTest` xanh cho light **và** dark (semantic pairs + onSurface/background).
- [ ] `AppTheme.semantic` là đường duy nhất lấy màu ok/warn/bad/info.
- [ ] Dark mode: ảnh/chữ ký vẫn đọc được, không mất divider.
- [ ] Chụp màn hình light + dark cho cảnh demo (Luật 8).
