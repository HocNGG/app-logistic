package vn.lab.logistics.feature.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.model.Workspace
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.kit.labOutlinedButtonBorder
import vn.lab.logistics.ui.kit.labOutlinedButtonColors

/**
 * =====================================================================================
 * NÚT **CHUYỂN VAI TRÒ** Ở THANH TRÊN CÙNG — đường chuyển phiên 1 CLICK (yêu cầu của sếp).
 *
 * Khác màn "Chuyển tài khoản" (bản chi tiết: ô tìm, danh sách, chọn rồi bấm "Chuyển"), nút này:
 *  - LUÔN hiện khi phiên được phép chuyển vai trò — kể cả khi đang đứng ở màn của tài khoản VỪA
 *    được admin chuyển sang (`taixe01` sang `cs01` chỉ 1 lần bấm, không quay lại màn Chuyển);
 *  - bấm vào là mở danh sách, bấm MỘT dòng là chuyển NGAY (không có nút xác nhận thứ hai).
 *
 * VÌ SAO NÚT CÒN HIỆN SAU KHI ĐÃ CHUYỂN: hợp đồng với BE-01 là `/admin/switch` trả
 * `canSwitch = true`, và hai endpoint admin vẫn trả 200 cho phiên do admin chuyển. Vì vậy app chỉ
 * cần gọi lại repo hiện có. Nếu app suy quyền từ `roles` (lúc đó chỉ còn `driver`) thì nút sẽ biến
 * mất ngay sau lần chuyển đầu — đúng cái sếp không muốn.
 *
 * VÌ SAO NÚT MẤT KHI ĐĂNG NHẬP THẲNG BẰNG TÀI KHOẢN THƯỜNG: `/auth/login` trả `canSwitch = false`
 * và server trả 403 cho 2 endpoint admin — không có gì để chuyển. Đó là trường hợp DUY NHẤT nút
 * mất; điều kiện nằm ở `App.kt` qua [shouldShowRoleSwitcher], KHÔNG dựa vào `roles`.
 *
 * TRẠNG THÁI: danh sách tài khoản tải LƯỜI ở lần mở đầu tiên rồi dùng lại (ViewModel sống theo
 * token, nên đổi phiên là cache mới). Đang chuyển thì nút hiện "Đang chuyển…" và khoá bấm; lỗi
 * (403/401/mạng) hiện [CalloutBox] tiếng Việt ngay trong menu kèm "Thử lại", KHÔNG đổi route.
 *
 * THỨ TỰ DÒNG: menu sắp theo thứ tự điều hướng của app (driver → driver_leader → cs → kho →
 * ketoan → manager → sep → admin, cùng vai trò thì theo `login`) — xem [sapXepMenuTaiKhoan]. Lý do:
 * luồng demo là admin → TÀI XẾ → CS, nên tài xế phải nằm đầu menu; xếp theo `login` như server trả
 * thì phải cuộn mới thấy, làm mất ý nghĩa "bấm 1 lần". Khi đang là `admin` (không có trong danh
 * sách chuyển được) thì menu có thêm MỘT dòng không bấm được
 * `✓ Quản trị viên (admin) — đang đăng nhập` để biết đang đứng ở đâu.
 * =====================================================================================
 *
 * @param vm ViewModel dùng CHUNG với màn "Chuyển tài khoản" — nhờ vậy danh sách, trạng thái
 *        "đang chuyển" và lỗi chỉ có một nguồn, hai lối chọn không thể nói khác nhau.
 * @param roles vai trò của phiên hiện tại (để hiện nhãn ngắn trên nút, vd `Tài xế ▾`).
 * @param currentLogin `login` đang dùng — dòng tương ứng được đánh dấu `✓` trong menu.
 * @param onSwitch nhận `login` cần chuyển; `App.kt` là nơi ghi phiên + điều hướng.
 * @param menuWidthDp bề rộng menu (đã chặn theo bề rộng cửa sổ ở nơi lắp ráp).
 * @param menuMaxHeightDp trần chiều cao menu (khoảng 55% vùng nội dung) — danh sách 8 tài khoản
 *        phải cuộn được, không tràn màn 360×640.
 * @param fillWidth `true` khi đặt trong sidebar (nút rộng hết cột cho thẳng hàng với các mục
 *        dưới nó); `false` ở thanh trên cùng để không chiếm chỗ của tiêu đề app.
 */
@Composable
fun RoleSwitcher(
    vm: SwitchAccountViewModel,
    roles: List<String>,
    currentLogin: String,
    onSwitch: (String) -> Unit,
    modifier: Modifier = Modifier,
    menuWidthDp: Int = 300,
    menuMaxHeightDp: Int = 360,
    fillWidth: Boolean = false,
    compact: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var mo by remember { mutableStateOf(false) }
    // `login` của lần bấm gần nhất — để "Thử lại" gọi đúng tài khoản vừa lỗi.
    var lanThu by remember { mutableStateOf<String?>(null) }
    val busy = vm.busy

    // Phiên đổi (App đã áp phiên mới) thì đóng menu. Lỗi thì `currentLogin` KHÔNG đổi, nên menu
    // còn mở để người dùng đọc câu lỗi + bấm "Thử lại".
    LaunchedEffect(currentLogin) {
        if (mo) {
            mo = false
            lanThu = null
        }
    }

    Box(modifier) {
        OutlinedButton(
            onClick = {
                vm.clearActionError()
                lanThu = null
                mo = true
                // Tải LƯỜI: chỉ gọi API ở lần mở đầu tiên; các lần sau dùng danh sách đã có
                // (`SwitchAccountViewModel.loadIfNeeded`).
                vm.loadIfNeeded(scope)
            },
            enabled = !busy,
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(if (compact) 32.dp else Tokens.touchSecondary),
            contentPadding = PaddingValues(
                horizontal = if (compact) Tokens.s2 else Tokens.s3,
                vertical = 0.dp,
            ),
            colors = labOutlinedButtonColors(),
            border = labOutlinedButtonBorder(enabled = !busy),
        ) {
            Text(
                text = if (busy) "Đang chuyển…" else switcherLabel(roles),
                style = if (compact) MaterialTheme.typography.caption else MaterialTheme.typography.body2,
                color = if (busy) AppTheme.lab.ink2 else AppTheme.lab.ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(expanded = mo, onDismissRequest = { mo = false }) {
            Column(
                modifier = Modifier
                    .width(menuWidthDp.dp)
                    .heightIn(max = menuMaxHeightDp.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Lỗi của lần chuyển trước — hiện NGAY TRONG menu (menu vẫn mở vì phiên chưa đổi),
                // kèm "Thử lại" đúng tài khoản vừa lỗi. Không đổi route khi lỗi.
                vm.actionError?.let { loi ->
                    CalloutBox(
                        title = "Không chuyển được vai trò",
                        body = loi,
                        tone = Tone.Bad,
                        actionLabel = "Thử lại",
                        onAction = {
                            val dich = lanThu
                            if (dich != null) onSwitch(dich)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.s3, vertical = Tokens.s2),
                    )
                }

                if (busy) MenuNote("Đang chuyển…")

                when (val state = vm.state) {
                    is UiState.Loading -> MenuNote("Đang tải danh sách tài khoản…")
                    is UiState.Empty -> MenuNote("Chưa có tài khoản demo để chuyển")
                    is UiState.Error -> MenuNote(state.message)
                    is UiState.Content<*> -> {
                        // SẮP theo thứ tự điều hướng (driver → driver_leader → cs → kho → …):
                        // luồng demo là admin → tài xế → CS nên tài xế phải nằm ĐẦU menu, không
                        // phải cuộn. Cùng vai trò thì theo `login`. Chỉ sắp ở UI này — danh sách
                        // của màn chi tiết vẫn giữ nguyên thứ tự server trả.
                        val accounts = sapXepMenuTaiKhoan(vm.accounts)
                        if (accounts.isEmpty()) {
                            MenuNote("Chưa tải được danh sách tài khoản")
                        } else {
                            // `admin` KHÔNG nằm trong whitelist của `/admin/accounts` ⇒ không dòng
                            // nào có dấu ✓. Thêm MỘT dòng không bấm được để biết đang đứng ở đâu.
                            if (Workspace.fromRoles(roles) == Workspace.Admin) {
                                DangDungRow(text = dongDangDungLabel(roles, currentLogin))
                            }
                            accounts.forEach { account ->
                                AccountMenuItem(
                                    account = account,
                                    dangDung = account.login == currentLogin,
                                    enabled = !busy,
                                    onClick = {
                                        lanThu = account.login
                                        // Bấm MỘT lần là chuyển ngay, không có bước xác nhận thứ hai.
                                        onSwitch(account.login)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Một dòng tài khoản trong menu: `✓` khi đang dùng + nhãn "tên · tài khoản · mã" + màn đích. */
@Composable
private fun AccountMenuItem(
    account: DemoAccount,
    dangDung: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = account.pickerMenuLabel(dangDung),
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

/**
 * Dòng "đang đăng nhập" ở ĐẦU menu — KHÔNG bấm được (chỉ để đọc).
 *
 * Dùng khi tài khoản hiện tại không có trong danh sách chuyển được (thực tế: `admin` không nằm
 * trong whitelist của `/admin/accounts`), để menu không phải một danh sách không dòng nào ✓.
 */
@Composable
private fun DangDungRow(text: String) {
    DropdownMenuItem(onClick = {}, enabled = false) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dòng chữ CHỈ ĐỌC trong menu (đang tải, đang chuyển, hoặc chưa có tài khoản). */
@Composable
private fun MenuNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = AppTheme.lab.ink2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.s4, vertical = Tokens.s2),
    )
}
