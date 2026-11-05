## Dùng khi nào

- Bất kỳ chuỗi nào người dùng thấy — từ màn Login đến snackbar lỗi (Luật 6: tiếng Việt là ngôn ngữ sản phẩm).
- Cần chuyển ngôn ngữ vi/en trong app (toolbar CS console), cần format `kg`, `đ`, `dd/MM/yyyy`.
- Review PR: thấy `Text("Tải lại")` viết thẳng trong composable → chặn.

## 1. Nguyên tắc

| Luật | Hệ quả |
|---|---|
| Không hardcode chuỗi trong `feature/**`, `ui/**` | Mọi text đi qua `strings().xxx` |
| Chuỗi phải có **dấu** tiếng Việt, đúng thuật ngữ (skill 07 glossary §5) | "chuyến", "kiện", "cước", "kho chờ", "giảm tải" — không dịch nửa vời |
| Số nhiều dùng **hàm**, không ghép chuỗi | `pendingSync(3)` chứ không `"$n" + " việc"` |
| Ngôn ngữ là state của app | lưu `Settings`, đổi là toàn app đổi ngay (không cần restart) |
| Test bằng chuỗi **dài nhất** | layout không vỡ ở tiếng Việt (§6) |

## 2. Thiết kế resource: `sealed interface Strings` + `Vi`/`En`

```kotlin
// commonMain/i18n/Strings.kt
sealed interface Strings {
    val appTitle: String
    val jobsTitle: String
    val emptyJobs: String
    val actionRefresh: String
    val actionConfirm: String
    val errNoConnection: String
    val labelPackages: String
    val labelWeightKg: String
    val labelCutoff: String
    fun pendingSync(n: Int): String
    fun overloaded(percent: String): String
    fun missingPhotos(n: Int): String

    data object Vi : Strings {
        override val appTitle = "Gom hàng lẻ"
        override val jobsTitle = "Việc hôm nay"
        override val emptyJobs = "Hôm nay chưa có việc nào"
        override val actionRefresh = "Tải lại"
        override val actionConfirm = "Hoàn tất"
        override val errNoConnection = "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại."
        override val labelPackages = "Số kiện thực tế"
        override val labelWeightKg = "Khối lượng ước tính (kg)"
        override val labelCutoff = "Giờ chốt chuyến"
        override fun pendingSync(n: Int) = "$n việc chờ đồng bộ"
        override fun overloaded(percent: String) = "Quá tải $percent"
        override fun missingPhotos(n: Int) = "Còn thiếu $n ảnh bắt buộc"
    }

    data object En : Strings {
        override val appTitle = "Freight consolidation"
        override val jobsTitle = "Today's jobs"
        override val emptyJobs = "No jobs scheduled for today"
        override val actionRefresh = "Refresh"
        override val actionConfirm = "Complete"
        override val errNoConnection = "Cannot reach the server. Check your connection and retry."
        override val labelPackages = "Actual packages"
        override val labelWeightKg = "Estimated weight (kg)"
        override val labelCutoff = "Cut-off time"
        override fun pendingSync(n: Int) = if (n == 1) "1 item pending sync" else "$n items pending sync"
        override fun overloaded(percent: String) = "Overloaded $percent"
        override fun missingPhotos(n: Int) = if (n == 1) "1 required photo missing" else "$n required photos missing"
    }
}
```

Vì sao `sealed interface` + `data object` chứ không phải map `String→String`: compiler ép **đủ key** (thiếu 1 chuỗi là build đỏ), IDE autocomplete, không có typo runtime như map. Muốn đa ngôn ngữ mở rộng (jp…) thì thêm `data object Jp : Strings` — vẫn không đổi chỗ dùng.

## 3. CompositionLocal + chuyển ngôn ngữ

```kotlin
// commonMain/i18n/LocalStrings.kt
val LocalStrings = staticCompositionLocalOf<Strings> { Strings.Vi }
@Composable fun strings(): Strings = LocalStrings.current

// App.kt
@Composable
fun App(settings: Settings, stringsVm: StringsViewModel = koinInject()) {
    val lang by stringsVm.language.collectAsState()                      // "vi" | "en", lưu trong Settings
    CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
        AppTheme { AppNavHost() }
    }
}
fun stringsFor(lang: String): Strings = if (lang == "en") Strings.En else Strings.Vi

// dùng
Text(strings().jobsTitle, style = MaterialTheme.typography.h6)
TextButton(onClick = vm::refresh) { Text(strings().actionRefresh) }
```

Đổi ngôn ngữ: `stringsVm.setLanguage("en")` → ghi `Settings` → `StateFlow` đổi → cả cây recompose. **Không** dùng `Locale.setDefault` (không phải nguồn sự thật của app; còn làm sai format của phần khác).

## 4. Format vi-VN: số, tiền, ngày (chỉ `desktopMain` — `java.text`)

```kotlin
// commonMain/i18n/Formatters.kt
interface Formatters {
    fun kg(value: Double): String        // "12,5 kg"
    fun money(value: Double): String     // "1.234.567 đ"
    fun date(iso: String): String        // "05/03/2026"
    fun dateTime(iso: String): String    // "05/03/2026 14:30"
}

// desktopMain/i18n/DesktopFormatters.kt
class DesktopFormatters(private val lang: String) : Formatters {
    private val locale = if (lang == "en") Locale.US else Locale.forLanguageTag("vi-VN")
    private val number: NumberFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val moneyFmt: NumberFormat = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)

    override fun kg(value: Double) = number.format(value) + "\u00A0kg"        // NBSP: không tách số khỏi đơn vị
    override fun money(value: Double) = moneyFmt.format(value) + "\u00A0đ"
    override fun date(iso: String) = LocalDate.parse(iso.take(10)).format(dateFmt)
    override fun dateTime(iso: String) = LocalDateTime.parse(iso.take(16)).format(dateTimeFmt)  // ISO từ Odoo
}
```

- Odoo trả ngày dạng `"2026-03-05 14:30:00"` (UTC) → **parse** rồi format, không cắt chuỗi bằng tay để hiển thị ngày (chỉ dùng `take` cho phần parse ban đầu như trên).
- `vi-VN` dùng `,` thập phân và `.` phân cách nghìn: `1.234.567,5` — đừng format kiểu Mỹ rồi "dịch".
- Tiền tệ: demo 1 loại (VND) là đủ; nếu thêm USD phải hiển thị rõ mã, không chỉ `$`.

## 5. Glossary — dùng đúng thuật ngữ (`docs/00` §10)

| Tiếng Việt (UI) | English | Ghi chú hiển thị |
|---|---|---|
| **Chuyến** | shipment / trip | không gọi "container"; `CH/AIR/26/0001` |
| **Kiện** | package / piece | đơn vị đóng gói vật lý → `stock.quant.package` |
| **Cước** | freight charge | không hiện số tiền ở app tài xế |
| **Debit** | debit note | giữ nguyên từ "Debit" như nghiệp vụ dùng |
| **Kho chờ** | holding warehouse | VIẾT HOA "KHO CHỜ" chỉ trong tài liệu nghiệp vụ; UI dùng "Kho chờ" |
| **Giảm tải** | de-load / offload | rời đơn khỏi chuyến khi quá tải |
| **AWB / HBL** | Air Waybill / House B/L | viết tắt **không** dịch |
| **Tải trọng** | payload / capacity | `%` tính từ Odoo, app không tự tính |

Quy tắc: nếu một thuật ngữ có trong bảng này thì **không** được xuất hiện biến thể khác trong UI (grep trước khi merge).

## 6. Test bằng chuỗi **dài nhất**

- Bảng chuỗi dài nhất (dùng để kiểm layout): `"Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại."`, `"Còn thiếu 2 ảnh bắt buộc"`, `"Chốt chuyến thì không sửa được đơn. Tiếp tục?"`, `"Hôm nay chưa có việc nào"`.
- Quy tắc layout: nhãn nút `maxLines = 2` + `overflow = Ellipsis`; **không** đặt `width` cố định cho text; cột bảng dùng `weight`, không `width` cứng.
- Test: chụp màn hình ở 3 ngưỡng (skill 03) × 2 ngôn ngữ, đối chiếu không có chữ bị cắt ở nhãn **hành động chính** (nếu cắt → đổi chữ hoặc tăng chỗ, không thu nhỏ font xuống dưới 12sp).
- Có thể thêm test tự động: `onNodeWithText(Strings.Vi.errNoConnection).assertIsDisplayed()` (skill 12) — bắt được lỗi "quên resource".

## Checklist nhanh

- [ ] `grep -rn 'Text("' apps/kmp/src` trong `feature/**` không còn chuỗi tiếng Việt trực tiếp.
- [ ] `Strings.Vi` và `Strings.En` cùng có đủ key (compiler bắt buộc); không chuỗi rỗng.
- [ ] Đổi ngôn ngữ trên toolbar → toàn app đổi ngay, giữ nguyên màn đang mở (state ở ViewModel).
- [ ] Ngày hiển thị `dd/MM/yyyy`; số/tiền theo `vi-VN`; có NBSP giữa số và đơn vị.
- [ ] Thuật ngữ khớp glossary §5; không có biến thể ("đơn hàng" vs "đơn" lẫn lộn ở cùng ngữ cảnh).
- [ ] Layout đã kiểm với 4 chuỗi dài nhất ở 3 ngưỡng cửa sổ, cả vi và en.
- [ ] Không có chuỗi tiếng Anh mặc định của Odoo lọt ra UI demo.
