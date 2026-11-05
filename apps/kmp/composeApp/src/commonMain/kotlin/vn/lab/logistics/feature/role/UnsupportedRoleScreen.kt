package vn.lab.logistics.feature.role

import androidx.compose.foundation.background
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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.core.model.rolesLabelOf
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.LogoMark
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabEnter
import vn.lab.logistics.ui.kit.LabSectionTitle

/**
 * =====================================================================================
 * MÀN **VAI TRÒ KHÁC** — "Vai trò này làm việc trên Odoo web".
 *
 * Nhánh bắt buộc của hợp đồng chọn màn (xem `core/model/Workspace.kt`): tài khoản có vai trò
 * `ketoan`, hoặc **không có vai trò nào** thuộc `driver`/`cs`/`kho`/`manager`/`sep`.
 *
 * Vì sao phải có màn này thay vì im lặng mở màn tài xế: kế toán mở màn "Việc hôm nay" sẽ thấy
 * danh sách rỗng (server lọc theo `ir.rule`) và tưởng app hỏng. Câu trả lời đúng nghiệp vụ là:
 * "vai trò này làm việc trên Odoo web" + đường đi cụ thể (địa chỉ máy chủ đang cấu hình) + nút
 * **Đăng xuất** để đổi tài khoản.
 *
 * GIAO DIỆN: một cột chữ hẹp ([Lab.READING_MAX_WIDTH_DP], 45–75 ký tự/dòng) với 3 khối [LabCard]
 * — "làm việc trên Odoo web", "vai trò có màn hình trên app", "nếu bạn là tài xế/CS/kho". Không
 * còn khung tự chế + đường kẻ trang trí.
 *
 * Không hardcode địa chỉ máy chủ: [baseUrl] do `AppSettings` truyền vào (self-check khối 8 chặn
 * literal URL trong mã nguồn).
 * =====================================================================================
 *
 * @param roles vai trò server trả về (rỗng = tài khoản chưa được gán vai trò).
 * @param baseUrl địa chỉ Odoo đang cấu hình — hiện ra để người dùng mở đúng máy chủ.
 * @param accountName tên đăng nhập (không phải bí mật) — giúp người dùng chắc mình đang dùng
 *        đúng tài khoản khi báo quản trị cấp quyền.
 * @param onLogout đăng xuất: xoá token/vai trò rồi về màn Đăng nhập.
 */
@Composable
fun UnsupportedRoleScreen(
    roles: List<String>,
    baseUrl: String,
    onLogout: () -> Unit,
    accountName: String = "",
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(AppTheme.lab.ground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.s5, vertical = Tokens.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Lab.READING_MAX_WIDTH_DP.dp).fillMaxWidth()) {
                LabEnter {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoMark(size = Tokens.logoNavSize)
                        Text(
                            text = "AppLogictics",
                            style = MaterialTheme.typography.subtitle1,
                            color = AppTheme.lab.ink,
                            modifier = Modifier.padding(start = Tokens.s2, end = Tokens.s2),
                        )
                        LabChip(
                            text = if (roles.isEmpty()) "Chưa có vai trò" else rolesLabelOf(roles),
                            tone = Tone.Warn,
                        )
                    }

                    Spacer(Modifier.height(Tokens.s5))

                    Text(
                        text = "Vai trò này làm việc trên Odoo web",
                        style = MaterialTheme.typography.h4,
                        color = AppTheme.lab.ink,
                    )
                    Text(
                        text = buildString {
                            append("Tài khoản")
                            if (accountName.isNotBlank()) append(" \"$accountName\"")
                            append(" có vai trò: ")
                            append(rolesLabelOf(roles))
                            append(". App trên máy này chỉ có 4 màn làm việc: Tài xế, Trưởng nhóm " +
                                "tài xế, Kho và Tổng quan của sếp — mọi vai trò văn phòng " +
                                "(CS, quản lý, chứng từ, kế toán) làm trên Odoo web.")
                        },
                        style = MaterialTheme.typography.body2,
                        color = AppTheme.lab.ink2,
                        modifier = Modifier.padding(top = Tokens.s2),
                    )

                    // CS là vai trò ĐẦU TIÊN rơi vào màn này theo quyết định D1 — giải thích
                    // thẳng để người xem không tưởng app thiếu màn.
                    if (roles.any { it.trim().equals("cs", ignoreCase = true) }) {
                        Spacer(Modifier.height(Tokens.s3))
                        CalloutBox(
                            title = "CS làm việc trên web",
                            body = "Nghiệp vụ CS là tạo khách, tạo yêu cầu lấy hàng, gán tài xế và " +
                                "gom nhiều đơn vào một chuyến — cần bàn phím và màn hình lớn. " +
                                "Mở Odoo web, menu \"Gom hàng lẻ\" để làm các việc đó. " +
                                "Trên app, việc phân công tài xế do Trưởng nhóm tài xế đảm nhận.",
                            tone = Tone.Info,
                        )
                    }

                    Spacer(Modifier.height(Tokens.s5))

                    LabCard(title = "Làm việc trên Odoo web") {
                        Text(
                            text = "Mở trình duyệt tới $baseUrl, đăng nhập bằng tài khoản này rồi " +
                                "vào menu \"Gom hàng lẻ\". Kế toán làm toàn bộ nghiệp vụ debit và " +
                                "đối soát (chuyến, cước, phiếu nhập kho) trên giao diện web của Odoo.",
                            style = MaterialTheme.typography.body2,
                            color = AppTheme.lab.ink,
                        )
                    }

                    Spacer(Modifier.height(Tokens.s3))

                    LabCard(title = "Vai trò có màn hình trên app") {
                        RoleRow("Tài xế", "driver", "Việc hôm nay — nhận việc, chụp ảnh, ký biên nhận.")
                        RoleRow(
                            "Trưởng nhóm tài xế",
                            "driver_leader",
                            "Phân công tài xế cho từng việc trong nhóm.",
                        )
                        RoleRow("Kho — nhận hàng", "kho", "Nhận hàng và lấy số phiếu nhập kho.")
                        RoleRow("Sếp — tổng quan", "manager · sep", "KPI, xu hướng, việc gần nhất.")
                        RoleRow(
                            "Quản trị viên",
                            "admin",
                            "Chuyển tài khoản để test nhanh các vai trò (chỉ admin).",
                        )
                        Text(
                            text = "CS — điều phối làm trên Odoo web: tạo yêu cầu lấy hàng, gán tài " +
                                "xế, gom chuyến.",
                            style = MaterialTheme.typography.caption,
                            color = AppTheme.lab.ink2,
                            modifier = Modifier.padding(top = Tokens.s2),
                        )
                    }

                    Spacer(Modifier.height(Tokens.s3))

                    CalloutBox(
                        title = "Nếu bạn là tài xế hoặc kho",
                        body = "Tài khoản chưa được gán vai trò nên app chưa biết mở màn nào. " +
                            "Báo quản trị thêm nhóm quyền tương ứng trong Odoo, rồi bấm Đăng xuất " +
                            "và đăng nhập lại — app chọn màn theo đúng vai trò máy chủ trả về.",
                        tone = Tone.Warn,
                    )

                    Spacer(Modifier.height(Tokens.s5))

                    // Nút chính >= 56dp: đây là HÀNH ĐỘNG DUY NHẤT của màn này.
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = AppTheme.lab.accent,
                            contentColor = AppTheme.lab.onAccent,
                        ),
                    ) {
                        Text("Đăng xuất")
                    }

                    Text(
                        text = "App không đoán vai trò: màn hình chỉ mở khi Odoo xác nhận vai trò " +
                            "của tài khoản. Tài khoản chưa có vai trò hoặc vai trò khác 4 mã trên " +
                            "đều dừng ở màn này.",
                        style = MaterialTheme.typography.caption,
                        color = AppTheme.lab.ink2,
                        modifier = Modifier.padding(top = Tokens.s3),
                    )
                    Spacer(Modifier.height(Tokens.s5))
                }
            }
        }
    }
}

/** Một dòng "vai trò — mã — việc làm" của bảng nhỏ trên màn này. */
@Composable
private fun RoleRow(label: String, code: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = Tokens.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.body1,
                color = AppTheme.lab.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Tokens.s2))
            LabChip(text = code, tone = Tone.Info)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s1),
        )
    }
}
