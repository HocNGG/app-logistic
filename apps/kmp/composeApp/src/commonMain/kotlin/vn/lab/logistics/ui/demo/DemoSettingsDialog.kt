package vn.lab.logistics.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.LabRole
import vn.lab.logistics.core.model.roleLabelOf
import vn.lab.logistics.core.repo.mock.DemoScenario
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabFilterChip
import vn.lab.logistics.ui.kit.LabSectionTitle

/**
 * =====================================================================================
 * MÀN **CÀI ĐẶT DEMO** — mọi công tắc của chế độ quay demo, gom về MỘT chỗ.
 *
 * VÌ SAO ĐỔI (yêu cầu của brief, hạng mục 4): trước đây các công tắc này nằm trên một "thanh
 * demo" cao 2–3 hàng ở đỉnh cửa sổ, luôn hiện. Hệ quả: mở app ra là thấy logo + 3 tab + 6 công
 * tắc + thanh kéo bề rộng → người xem nghĩ ngay "đây là mockup", không phải sản phẩm. Nay:
 *  - màn nghiệp vụ chỉ còn nav + nội dung;
 *  - mọi công tắc nằm sau nút **Cài đặt demo** (icon bánh răng vẽ bằng Canvas) ở chân sidebar
 *    — chỉ desktop (≥ 900dp), đúng chỗ người dùng đã quen tìm "cài đặt";
 *  - khả năng demo offline KHÔNG mất: đổi nguồn dữ liệu, đổi vai trò, đổi kịch bản, đổi bề rộng
 *    giả lập, bật giảm chuyển động — vẫn đủ, chỉ là không chiếm chỗ trên màn chính.
 *  - **KHÔNG còn công tắc "Nền tối"** (2026-10-30): app chỉ có một chế độ màu (tối, ADR-009 §6),
 *    nên chỗ đó nay là một dòng thông tin. Không có khoá theme nào trong `AppSettings` để bỏ —
 *    chế độ màu chưa bao giờ được lưu bền, nó chỉ là state trong `App`.
 *
 * KHÔNG phải màn nghiệp vụ: không gọi API, không đổi dữ liệu, chỉ đổi THAM SỐ HIỂN THỊ của app.
 * =====================================================================================
 */

/**
 * Danh sách vai trò chọn được ở chế độ Mẫu — đúng 8 lựa chọn để demo hết các nhánh chọn màn:
 *  - `driver` / `driver_leader` / `kho` → 3 màn làm việc;
 *  - `manager` → dashboard của sếp;
 *  - `admin` → màn **Chuyển tài khoản** (công cụ test nhanh vai trò, có danh sách mẫu);
 *  - `cs` / `ketoan` → nhánh "vai trò khác" (làm việc trên Odoo web) — D1: CS rời app;
 *  - `""` (rỗng) → nhánh **tài khoản chưa có vai trò** — cũng là màn thông báo đó.
 *
 * `driver_leader` đứng NGAY SAU `driver` cho khớp thứ tự ưu tiên của `Workspace.fromRoles`;
 * `admin` đứng cuối nhóm có màn hình vì nó là CÔNG CỤ TEST, không phải việc hằng ngày.
 *
 * ⚠️ `cs` VẪN nằm trong danh sách chọn: cần chọn được để KIỂM TRA màn "vai trò này làm việc trên
 * Odoo web" (đúng nghiệp vụ D1), không phải vì CS có màn làm việc trên app.
 */
val DEMO_ROLES: List<String> = listOf(
    LabRole.DRIVER,
    LabRole.DRIVER_LEADER,
    LabRole.CS,
    LabRole.KHO,
    LabRole.MANAGER,
    LabRole.ADMIN,
    LabRole.KETOAN,
    "",
)

/**
 * Lớp phủ "Cài đặt demo" (desktop).
 *
 * @param simulatedWidthDp `0` = theo bề rộng cửa sổ (mặc định). Khác 0 = bề rộng nội dung giả
 *        lập để chứng minh 3 ngưỡng duo UI mà không phải kéo cửa sổ.
 */
@Composable
fun DemoSettingsDialog(
    onDismiss: () -> Unit,
    useRealApi: Boolean,
    onUseRealApi: (Boolean) -> Unit,
    demoRole: String?,
    onDemoRole: (String) -> Unit,
    scenario: DemoScenario,
    onScenario: (DemoScenario) -> Unit,
    reducedMotion: Boolean,
    onReducedMotion: (Boolean) -> Unit,
    simulatedWidthDp: Int,
    onSimulatedWidth: (Int) -> Unit,
    baseUrl: String,
    hasToken: Boolean,
    driverName: String,
    realRoleLabel: String,
    modifier: Modifier = Modifier,
) {
    // Scrim: nền TRANG ở alpha 0.78 (token `ground`, không phải Color.Black) — lớp phủ vẫn là
    // "cùng một họ màu" với app nên không có vệt xám lạnh khi trùm lên nền navy.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.lab.ground.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(Tokens.s5)
                .verticalScroll(rememberScrollState()),
        ) {
            LabCard(
                title = "Cài đặt demo",
                subtitle = "Chỉ dùng khi quay demo — không phải màn nghiệp vụ.",
                trailing = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(Tokens.touchSecondary),
                    ) {
                        Text("Đóng", color = AppTheme.lab.accent)
                    }
                },
            ) {
                // ------------------------------------------------------------ nguồn dữ liệu
                LabSectionTitle(text = "Nguồn dữ liệu", hint = if (useRealApi) "Odoo thật" else "Dữ liệu mẫu")
                Spacer(Modifier.height(Tokens.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                    LabFilterChip(
                        text = "Dữ liệu mẫu (an toàn)",
                        selected = !useRealApi,
                        onClick = { onUseRealApi(false) },
                    )
                    LabFilterChip(
                        text = "Odoo thật",
                        selected = useRealApi,
                        onClick = { onUseRealApi(true) },
                    )
                }
                Spacer(Modifier.height(Tokens.s2))
                Text(
                    text = when {
                        !useRealApi ->
                            "Mọi màn đọc dữ liệu mẫu trong app, KHÔNG gọi mạng. Máy chủ đã cấu hình: $baseUrl"
                        hasToken ->
                            "Đang gọi Odoo thật: $baseUrl" +
                                (if (driverName.isNotBlank()) " · tài xế $driverName" else "")
                        else ->
                            "Đang gọi Odoo thật: $baseUrl · chưa có mã truy cập → vào tab Đăng nhập"
                    },
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                )

                // ---------------------------------------------------------------- vai trò demo
                if (demoRole != null) {
                    Spacer(Modifier.height(Tokens.s5))
                    LabSectionTitle(
                        text = "Vai trò demo",
                        hint = roleLabelOf(demoRole),
                    )
                    Spacer(Modifier.height(Tokens.s2))
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                        DEMO_ROLES.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                                row.forEach { role ->
                                    LabFilterChip(
                                        text = roleLabelOf(role),
                                        selected = role == demoRole,
                                        onClick = { onDemoRole(role) },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Tokens.s1))
                    Text(
                        text = "Dữ liệu mẫu không có vai trò từ server, nên chọn ở đây để xem " +
                            "cả 6 màn (Tài xế · Trưởng nhóm tài xế · CS · Kho · Tổng quan · " +
                            "Chuyển tài khoản) và nhánh \"vai trò khác\".",
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                    )
                } else {
                    Spacer(Modifier.height(Tokens.s5))
                    LabSectionTitle(text = "Vai trò", hint = realRoleLabel)
                    Spacer(Modifier.height(Tokens.s1))
                    Text(
                        text = "Đang dùng Odoo thật: vai trò do TÀI KHOẢN quyết định, app không " +
                            "cho chọn (chọn bừa là mở sai màn nghiệp vụ).",
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                    )
                }

                // ------------------------------------------------- đổi nhanh tài khoản (admin)
                Spacer(Modifier.height(Tokens.s4))
                CalloutBox(
                    title = "Đổi nhanh tài khoản (chỉ quản trị viên)",
                    body = "Đăng nhập bằng tài khoản \"admin\" để mở màn Chuyển tài khoản: chọn " +
                        "một tài khoản demo trong danh sách là app mở đúng màn của vai trò đó — " +
                        "không phải đăng xuất rồi gõ lại mật khẩu khi quay video nhiều vai trò.",
                    tone = Tone.Info,
                )

                // -------------------------------------------------------------- kịch bản mẫu
                Spacer(Modifier.height(Tokens.s5))
                LabSectionTitle(text = "Kịch bản dữ liệu mẫu", hint = scenario.label)
                Spacer(Modifier.height(Tokens.s1))
                Text(
                    text = "Chỉ ảnh hưởng nguồn MẪU — dùng để quay đủ 4 trạng thái " +
                        "(Loading · Empty · Error · Content).",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                )
                Spacer(Modifier.height(Tokens.s2))
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                    DemoScenario.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                            row.forEach { item ->
                                LabFilterChip(
                                    text = item.label,
                                    selected = item == scenario,
                                    onClick = { onScenario(item) },
                                )
                            }
                        }
                    }
                }

                // ------------------------------------------------------------------- giao diện
                Spacer(Modifier.height(Tokens.s5))
                LabSectionTitle(text = "Giao diện", hint = "Tối (cố định)")
                Spacer(Modifier.height(Tokens.s1))
                // KHÔNG còn công tắc "Nền tối": theo chỉ đạo 2026-10-30, app chỉ có MỘT chế độ
                // màu (tối, ADR-009 §6). Giữ một dòng thông tin thay vì một công tắc chết —
                // người quay demo vẫn biết vì sao không đổi được sang nền sáng.
                Text(
                    text = "Bảng màu tối navy + lime là cố định (ADR-009 §6): mọi màn hình, mọi " +
                        "kích thước cửa sổ dùng đúng một bảng token, không có chế độ sáng.",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                )
                Spacer(Modifier.height(Tokens.s2))
                DemoToggleRow(
                    label = "Giảm chuyển động",
                    checked = reducedMotion,
                    onChecked = onReducedMotion,
                )

                // ------------------------------------------------------------ bề rộng giả lập
                Spacer(Modifier.height(Tokens.s5))
                LabSectionTitle(
                    text = "Bề rộng giả lập",
                    hint = if (simulatedWidthDp == 0) {
                        "Theo cửa sổ"
                    } else {
                        "$simulatedWidthDp dp"
                    },
                )
                Spacer(Modifier.height(Tokens.s1))
                Text(
                    text = "Đặt bề rộng NỘI DUNG để chứng minh 3 ngưỡng duo UI (1280 / 900 / 600) " +
                        "mà không phải kéo cửa sổ. Nội dung luôn căn giữa và không vượt cửa sổ.",
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                )
                Spacer(Modifier.height(Tokens.s2))
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                        LabFilterChip(
                            text = "Theo cửa sổ",
                            selected = simulatedWidthDp == 0,
                            onClick = { onSimulatedWidth(0) },
                        )
                        LabFilterChip(
                            text = "Rộng 1440",
                            selected = simulatedWidthDp == Tokens.DEMO_PRESET_WIDE_DP,
                            onClick = { onSimulatedWidth(Tokens.DEMO_PRESET_WIDE_DP) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.s2)) {
                        LabFilterChip(
                            text = "Hẹp 1100",
                            selected = simulatedWidthDp == Tokens.DEMO_PRESET_NARROW_DP,
                            onClick = { onSimulatedWidth(Tokens.DEMO_PRESET_NARROW_DP) },
                        )
                        LabFilterChip(
                            text = "Máy tính bảng 800",
                            selected = simulatedWidthDp == Tokens.DEMO_PRESET_OVERLAY_DP,
                            onClick = { onSimulatedWidth(Tokens.DEMO_PRESET_OVERLAY_DP) },
                        )
                    }
                }
            }
        }
    }
}

/** Một hàng công tắc: nhãn bên trái, `Switch` bên phải (vùng chạm 48dp của Material). */
@Composable
private fun DemoToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Normal,
            color = AppTheme.lab.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Tokens.s2))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.lab.card,
                checkedTrackColor = AppTheme.lab.accent,
                uncheckedThumbColor = AppTheme.lab.ink2,
                uncheckedTrackColor = AppTheme.lab.panel,
            ),
        )
    }
}

/** Độ đục của lớp phủ "Cài đặt demo" — alpha của `ground`, không phải màu mới. */
private const val SCRIM_ALPHA = 0.78f
