package vn.lab.logistics.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.StateHost
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabEmptyState
import vn.lab.logistics.ui.kit.LabRowDivider
import vn.lab.logistics.ui.kit.LabToolbar
import vn.lab.logistics.ui.kit.labOutlinedButtonBorder
import vn.lab.logistics.ui.kit.labOutlinedButtonColors
import vn.lab.logistics.ui.kit.labTextFieldColors

// =====================================================================================
// Menu chọn tài khoản (của màn này và của nút chuyển vai trò ở thanh trên) dùng CÙNG hàm chặn
// kích thước ở `AdminAccountPresentation.kt` — [accountMenuWidthDp] / [accountMenuMaxHeightDp] —
// để hai nơi không lệch nhau khi màn hẹp.
// =====================================================================================

/**
 * =====================================================================================
 * MÀN **CHUYỂN TÀI KHOẢN** (chỉ quản trị viên) — công cụ test nhanh 8 vai trò.
 *
 * Luồng: mở màn → tải `GET /lab/api/v1/admin/accounts` → **chọn một tài khoản** → bấm
 * **"Chuyển"** → `POST /lab/api/v1/admin/switch` → app ghi phiên mới rồi mở ĐÚNG màn của vai trò
 * đó, y như vừa đăng nhập (xem `App.kt`).
 *
 * ==================== HAI LỐI CHỌN TÀI KHOẢN (cùng một lựa chọn, hai cách tới) ==========
 *  1. **Nút "Chọn tài khoản ▾"** trong thanh dưới cùng → mở [DropdownMenu] liệt kê **TOÀN BỘ**
 *     tài khoản đã tải: chọn nhanh 1–2 cú bấm, đúng ý sếp ("làm nút button chuyển đổi dropdown
 *     xuống để dễ chọn"). Menu KHÔNG bị ô tìm kiếm lọc — nó là lối chọn "toàn bộ".
 *  2. **Danh sách chi tiết** ở giữa màn hình: đọc được "Mở màn: …" của từng tài khoản, lọc theo
 *     tên/tài khoản/mã vai trò, và thấy rõ dòng "Đang chọn".
 *
 * Vì sao GIỮ CẢ HAI thay vì chỉ dropdown: dropdown nhanh nhưng che mất thông tin phụ (màn đích);
 * danh sách cho người chưa biết trong DB có những ai. Bỏ danh sách thì mất đường tra cứu, bỏ
 * dropdown thì 8 tài khoản phải cuộn mới thấy hết — sếp đã chọn cách có cả hai.
 *
 * ======================= MÀN HẸP 360×640: NGÂN SÁCH CHIỀU CAO =========================
 * Đo thật trên AVD 720×1280: vùng nội dung còn **486dp**. Bản đầu tốn toolbar ~129 + ô tìm 80 +
 * thanh chọn 160 + 1 đường kẻ = 370dp ⇒ danh sách chỉ còn ~116dp ⇒ **2 dòng** (67dp/dòng), quá ít
 * để chọn. Ba thay đổi chỉ áp dụng khi `compact` (< 700dp) — màn rộng giữ nguyên bản đầy đủ:
 *  1. [adminToolbarSubtitle] rút gọn ("Chỉ admin · đang là admin · Odoo thật") — câu đầy đủ bị
 *     `LabToolbar` cắt giữa từ;
 *  2. đệm dọc ô tìm 12dp → 4dp (80 → 64dp); đệm thanh chọn 12dp → 8dp và BỎ dòng "Đang chọn: …"
 *     (nút picker đã in "Đã chọn: <login> ▾") ⇒ thanh chọn 160 → 128dp (tổng −48dp);
 *  3. dòng danh sách chỉ còn 1 dòng nhãn + đệm 8dp ⇒ **44dp** mỗi dòng thay vì 67dp (chi tiết
 *     "Mở màn: …" vẫn có trong menu dropdown).
 * Kết quả kỳ vọng: 486 − 129 − 64 − 128 − 1 = **164dp** cho danh sách ⇒ **3,7 dòng 44dp** (thấy
 * trọn 3 dòng + một phần dòng thứ 4).
 * =====================================================================================
 *
 * Vì sao dòng chọn hiện cả MÃ vai trò: quản trị viên đang đối chiếu với nhóm quyền trong Odoo,
 * nơi mã là `driver`/`cs`/`kho`. Nhãn tiếng Việt của màn đích nằm ở dòng thứ hai ("Mở màn: …")
 * nên người xem video vẫn hiểu ngay chuyển sang đâu.
 *
 * BA TRẠNG THÁI qua [StateHost] (Loading · Empty · Error) + Content; lỗi của thao tác chuyển
 * (403 vì không phải admin, 401 phiên hết hạn, mạng…) hiện trong [CalloutBox] kèm **"Thử lại"**.
 *
 * BẢO MẬT: màn này KHÔNG hiện với tài khoản khác — `App.kt` chỉ route tới khi `roles` của phiên
 * có `admin`, và server cũng chặn 403 ở cả hai endpoint (hai lớp, không dựa vào UI).
 * =====================================================================================
 *
 * @param onSwitchAccount nhận `login` của tài khoản cần chuyển. Đây là ĐƯỜNG DUY NHẤT đổi phiên
 *        (`App.kt` gọi `adminVm.switchTo(login, …)` rồi ghi Settings + điều hướng) — dùng CHUNG
 *        với nút dropdown ở thanh trên cùng, nên màn hình không tự gọi repo và không tự đổi route.
 * @param currentLogin tài khoản đang dùng (hiện ở dòng phụ để admin biết đang đứng ở đâu).
 * @param widthDp bề rộng vùng nội dung (màn tự chọn bố cục theo ngưỡng design system; cũng dùng
 *        để chặn bề rộng menu dropdown cho không tràn màn hẹp).
 */
@Composable
fun SwitchAccountScreen(
    vm: SwitchAccountViewModel,
    widthDp: Int,
    onSwitchAccount: (String) -> Unit,
    onExitToLogin: () -> Unit,
    currentLogin: String = "",
    dataSourceLabel: String = "Mẫu",
    onFallbackToSample: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val compact = widthDp < Tokens.BREAKPOINT_COMPACT_DP

    LaunchedEffect(vm) { vm.loadIfNeeded(scope) }

    // BoxWithConstraints = nguồn CHIỀU CAO duy nhất của vùng nội dung: menu dropdown phải bị chặn
    // theo chiều cao khả dụng, nếu không trên máy 390×844 nó thò ra ngoài đáy cửa sổ và dòng cuối
    // không bấm được (lỗi thật của menu dài trong Material 2).
    BoxWithConstraints(modifier.fillMaxSize().background(AppTheme.lab.ground)) {
        val availableHeightDp = maxHeight.value
        val menuMaxHeightDp = accountMenuMaxHeightDp(availableHeightDp)
        // Bề rộng menu ≈ bề rộng vùng nội dung trừ padding hai bên của thanh chọn.
        val menuWidthDp = accountMenuWidthDp(widthDp)

        Column(Modifier.fillMaxSize()) {
            LabToolbar(
                title = "Chuyển tài khoản",
                // Ở màn hẹp (< 700dp) phụ đề phải NGẮN: toolbar chỉ 1 dòng + ellipsis, câu đầy đủ
                // bị cắt giữa từ ("… đang là a…"). Xem `adminToolbarSubtitle`.
                subtitle = adminToolbarSubtitle(
                    currentLogin = currentLogin,
                    dataSourceLabel = dataSourceLabel,
                    compact = compact,
                ),
                stacked = compact,
                actions = {
                    // Chip nói rõ màn này là của admin — cùng lý do với tiêu đề "Chỉ quản trị viên".
                    LabChip(text = "admin", tone = Tone.Warn)
                    Spacer(Modifier.width(Tokens.s2))
                    ToolbarAction("Tải lại") { vm.load(scope) }
                    ToolbarAction("Đăng xuất", onExitToLogin)
                },
            )

            // ---------------------------------------------------------------- tìm nhanh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Màn hẹp: đệm dọc 4dp thay 12dp — giành 16dp cho danh sách (360×640 chỉ còn
                    // 486dp cho cả màn, xem KDoc đầu file "ngân sách chiều cao").
                    .padding(
                        horizontal = Tokens.s5,
                        vertical = if (compact) Tokens.s1 else Tokens.s3,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = vm.searchQuery,
                    onValueChange = vm::onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body2,
                    colors = labTextFieldColors(),
                    label = {
                        Text(
                            text = "Tìm theo tên, tài khoản hoặc vai trò (vd taixe01, cs, kho)",
                            style = MaterialTheme.typography.body2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }

            // Lỗi của thao tác CHUYỂN — hiện phía trên danh sách để không bị khuất khi cuộn.
            vm.actionError?.let { message ->
                CalloutBox(
                    title = "Không chuyển được tài khoản",
                    body = message,
                    tone = Tone.Bad,
                    actionLabel = "Thử lại",
                    // "Thử lại" = gọi lại ĐÚNG đường chuyển phiên dùng chung (App.kt), không tự
                    // gọi repo ở đây.
                    onAction = { vm.selectedLogin?.let(onSwitchAccount) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.s5)
                        .padding(bottom = Tokens.s3),
                )
            }

            StateHost(
                state = vm.state,
                modifier = Modifier.weight(1f),
                emptyTitle = "Chưa có tài khoản demo để chuyển",
                emptyHint = "Seed lại dữ liệu demo (infra/scripts/reset-demo.ps1) rồi bấm Tải lại.",
                emptyActionLabel = "Tải lại",
                onEmptyAction = { vm.load(scope) },
                loadingLabel = "Đang tải danh sách tài khoản…",
                errorHint = "Kiểm tra Odoo đã chạy và tài khoản đăng nhập có quyền admin, rồi bấm Thử lại.",
                secondaryActionLabel = if (onFallbackToSample != null) "Về dữ liệu mẫu" else null,
                onSecondaryAction = onFallbackToSample,
            ) { _ ->
                val visible = vm.visibleAccounts()
                if (visible.isEmpty()) {
                    LabEmptyState(
                        title = "Không tìm thấy tài khoản khớp \"${vm.searchQuery.trim()}\"",
                        hint = "Xoá ô tìm kiếm để xem toàn bộ danh sách.",
                        actionLabel = "Xoá tìm kiếm",
                        onAction = vm::clearSearch,
                    )
                } else {
                    LazyColumn(
                        // state của VIEWMODEL — không tạo ở đây (displacement, docs/04 §4).
                        state = vm.listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = visible, key = { account -> account.login }) { account ->
                            AccountRow(
                                account = account,
                                isSelected = account.login == vm.selectedLogin,
                                // Màn hẹp: dòng chỉ 1 dòng nhãn (44dp) để thấy ≥ 3 dòng — chi
                                // tiết "Mở màn: …" đã có trong menu dropdown.
                                compact = compact,
                                onClick = { vm.select(account.login) },
                            )
                            LabRowDivider()
                        }
                    }
                }
            }

            // ------------------------------------------------------- chọn xong → bấm Chuyển
            LabRowDivider()
            ChooseBar(
                // MENU lấy TOÀN BỘ tài khoản đã tải (`vm.accounts`), KHÔNG phải danh sách đã lọc:
                // ô tìm chỉ thu hẹp danh sách chi tiết, không thu hẹp lối chọn nhanh.
                accounts = pickerAccounts(vm.accounts),
                selected = vm.selectedAccount(),
                busy = vm.busy,
                compact = compact,
                menuWidthDp = menuWidthDp,
                menuMaxHeightDp = menuMaxHeightDp,
                onSelect = vm::select,
                // Chuyển = giao `login` cho App (đường dùng chung với dropdown thanh trên).
                onSwitch = { vm.selectedLogin?.let(onSwitchAccount) },
            )
        }
    }
}

/**
 * Thanh hành động dưới cùng — **HAI LỐI CHỌN** cùng một lựa chọn, rồi nút **"Chuyển"** (>= 56dp).
 *
 * LỐI 1 (mới, sếp yêu cầu): nút **"Chọn tài khoản ▾"** mở [DropdownMenu] liệt kê **TOÀN BỘ**
 * tài khoản đã tải — chọn nhanh, kể cả khi ô tìm phía trên đang lọc danh sách chi tiết. Mỗi dòng
 * menu: [pickerMenuLabel] ("tên (vai trò) · login · mã", có `✓` ở đầu nếu đang chọn) + dòng phụ
 * [targetScreenLabel] ("Mở màn: …"). Nút hiển thị nhãn theo lựa chọn ([pickerLabel]) để người
 * dùng biết đang trỏ vào tài khoản nào mà không phải cuộn đi tìm.
 *
 * LỐI 2 (giữ nguyên): danh sách chi tiết ở giữa màn hình — để đọc "Mở màn: …", lọc theo mã vai
 * trò và thấy dòng "Đang chọn". Vì sao giữ cả hai: dropdown nhanh nhưng che thông tin phụ; danh
 * sách là đường tra cứu khi chưa biết trong DB có những ai. Bỏ một lối là mất một trong hai nhu
 * cầu đó.
 *
 * Menu phải nằm trong màn hẹp: bề rộng bị chặn theo [menuWidthDp], chiều cao theo
 * [menuMaxHeightDp] và cuộn được ([rememberScrollState]) — trên máy 390dp, 8 tài khoản × 2 dòng
 * chắc chắn dài hơn màn hình.
 *
 * ============================ CHIỀU CAO THANH Ở MÀN HẸP ==============================
 * `compact` (< 700dp, vd AVD 360×640 chỉ còn 486dp chiều cao nội dung): thanh này bỏ dòng
 * "Đang chọn: …" và đệm dọc còn 8dp ⇒ **128dp** (picker 48 + 8 + Chuyển 56 + 16), thay vì 160dp.
 * Vì sao: nút picker đã in "Đã chọn: <login> ▾" nên dòng đó chỉ là lặp lại; bỏ nó + đệm nhỏ lại
 * là cách rẻ nhất để danh sách phía trên thấy được ≥ 3 dòng (xem "ngân sách chiều cao" ở
 * `AdminAccountPresentation.kt`). Nút "Chuyển" vẫn 56dp và caption vẫn ẩn ở compact như trước.
 * =====================================================================================
 *
 * Nút "Chuyển" BỊ KHOÁ khi chưa chọn tài khoản: gọi server với `login` rỗng chỉ làm quản trị
 * tưởng đã chuyển (cùng luật với nút "Gán tài xế" của màn CS). Khi [busy], cả hai lối chọn bị
 * khoá và menu đang mở được ĐÓNG lại (đổi lựa chọn giữa lúc phiên đang đổi là hiểu nhầm).
 */
@Composable
private fun ChooseBar(
    accounts: List<DemoAccount>,
    selected: DemoAccount?,
    busy: Boolean,
    compact: Boolean,
    menuWidthDp: Int,
    menuMaxHeightDp: Int,
    onSelect: (String) -> Unit,
    onSwitch: () -> Unit,
) {
    // Menu mở/đóng là state TẠM THỜI của UI (không phải lựa chọn) ⇒ `remember` tại đây, cùng cách
    // ô chọn tài xế của màn CS đang làm. Lựa chọn thật nằm ở ViewModel.
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) open = false }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Màn hẹp: đệm dọc 8dp (thay 12dp) — thanh chọn thấp lại để danh sách có thêm chỗ
            // (xem "ngân sách chiều cao" ở KDoc đầu file).
            .padding(
                horizontal = Tokens.s5,
                vertical = if (compact) Tokens.s2 else Tokens.s3,
            ),
    ) {
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(Tokens.touchSecondary),
                colors = labOutlinedButtonColors(),
                border = labOutlinedButtonBorder(enabled = !busy),
            ) {
                Text(
                    text = pickerLabel(selected?.login),
                    style = MaterialTheme.typography.body2,
                    // Nút bị khoá (đang chuyển) thì chữ mờ đi — tín hiệu "không bấm được" ngoài
                    // viền mờ, không để người dùng đoán.
                    color = if (busy) AppTheme.lab.ink2 else AppTheme.lab.ink,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Column(
                    modifier = Modifier
                        .width(menuWidthDp.dp)
                        .heightIn(max = menuMaxHeightDp.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (accounts.isEmpty()) {
                        // Không có dòng nào để chọn (đang Loading/Error) ⇒ nói rõ, không menu trống.
                        DropdownMenuItem(onClick = {}, enabled = false) {
                            Text(
                                text = "Chưa tải được danh sách tài khoản",
                                style = MaterialTheme.typography.body2,
                                color = AppTheme.lab.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        accounts.forEach { account ->
                            val dangChon = account.login == selected?.login
                            DropdownMenuItem(
                                onClick = {
                                    onSelect(account.login)
                                    open = false
                                },
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = account.pickerMenuLabel(dangChon),
                                        style = MaterialTheme.typography.body2,
                                        color = AppTheme.lab.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = account.targetScreenLabel(),
                                        style = MaterialTheme.typography.caption,
                                        color = AppTheme.lab.ink2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dòng "Đang chọn: …" CHỈ hiện khi có chỗ. Ở màn hẹp nút picker ngay trên đã in
        // "Đã chọn: <login> ▾" ⇒ dòng này là lặp lại; bỏ nó đi để danh sách thấy được ≥ 3 dòng
        // (360×640: -28dp). Đổi lại, màn hẹp không thấy tên đầy đủ ở đây — nhưng nó nằm trong
        // menu dropdown và trong chính nút picker.
        if (!compact) {
            Spacer(Modifier.height(Tokens.s2))
            Text(
                text = selected?.let { "Đang chọn: ${it.accountLabel()}" } ?: "Chưa chọn tài khoản nào",
                style = MaterialTheme.typography.body2,
                color = AppTheme.lab.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Tokens.s2))
        Button(
            onClick = onSwitch,
            enabled = !busy && selected != null,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text(if (busy) "Đang chuyển…" else "Chuyển")
        }
        if (!compact) {
            Text(
                text = "App ghi phiên của tài khoản vừa chọn rồi mở đúng màn của vai trò đó — " +
                    "không cần đăng xuất, không cần gõ lại mật khẩu.",
                style = MaterialTheme.typography.caption,
                color = AppTheme.lab.ink2,
                modifier = Modifier.padding(top = Tokens.s2),
            )
        }
    }
}

/**
 * Một dòng tài khoản trong DANH SÁCH CHI TIẾT.
 *
 * Màn rộng: 2 dòng — nhãn "tên · tài khoản · mã vai trò" + dòng "Mở màn: …" + chip "Đang chọn".
 *
 * Màn hẹp (`compact`, < 700dp): CHỈ 1 dòng nhãn (cắt ellipsis) + chip, đệm dọc 8dp ⇒ dòng 44dp
 * (`Lab.tableRowMinHeight`) thay vì 67dp. Vì sao phải bỏ dòng thứ hai ở đây: trên 360×640 chỉ còn
 * ~164dp cho danh sách; dòng 67dp ⇒ chỉ thấy 2 dòng, không đủ để chọn. Thông tin "Mở màn: …" KHÔNG
 * mất — nó vẫn nằm trong menu dropdown ([ChooseBar]) và mã vai trò vẫn nằm ngay trong nhãn.
 */
@Composable
private fun AccountRow(
    account: DemoAccount,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Lab.tableRowMinHeight)
            .background(
                if (isSelected) AppTheme.lab.accent.copy(alpha = Lab.NAV_ACTIVE_TINT) else Tokens.transparent,
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = Tokens.s5,
                vertical = if (compact) Tokens.s2 else Tokens.s3,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = account.accountLabel(),
                style = MaterialTheme.typography.subtitle2,
                color = AppTheme.lab.ink,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Spacer(Modifier.width(Tokens.s2))
                LabChip(text = "Đang chọn", tone = Tone.Ok)
            }
        }
        if (!compact) {
            Text(
                text = account.targetScreenLabel(),
                style = MaterialTheme.typography.caption,
                color = AppTheme.lab.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Tokens.s1),
            )
        }
    }
}

/** Nút chữ của toolbar — cùng chiều cao 48dp, cùng màu nhấn như các màn khác. */
@Composable
private fun ToolbarAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(Tokens.touchSecondary)) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = AppTheme.lab.accent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
