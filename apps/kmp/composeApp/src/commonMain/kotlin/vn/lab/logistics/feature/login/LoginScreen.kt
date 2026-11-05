package vn.lab.logistics.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.lab.logistics.design.AppTheme
import vn.lab.logistics.design.Lab
import vn.lab.logistics.design.Tokens
import vn.lab.logistics.platformInfo
import vn.lab.logistics.ui.components.CalloutBox
import vn.lab.logistics.ui.components.LogoMark
import vn.lab.logistics.ui.components.Tone
import vn.lab.logistics.ui.components.UiState
import vn.lab.logistics.ui.kit.LabCard
import vn.lab.logistics.ui.kit.LabChip
import vn.lab.logistics.ui.kit.LabEnter
import vn.lab.logistics.ui.kit.LabFieldLabel
import vn.lab.logistics.ui.kit.LabSectionTitle
import vn.lab.logistics.ui.kit.labTextFieldColors

/**
 * =====================================================================================
 * MÀN ĐĂNG NHẬP — 4 trạng thái bấm được (xem [LoginViewModel]).
 *
 * Luồng THẬT gồm **2 bước** (đã đo 2026-10-28), không đổi sau khi làm lại UI:
 *   1. `POST /web/session/authenticate` → `uid` + **cookie `session_id`** (chỉ có trong
 *      `Set-Cookie`, không có trong body);
 *   2. `POST /lab/api/v1/auth/login` (kèm cookie) → token 8 giờ + `driver_name`/`jobsToday`/`roles`.
 *
 * BỐ CỤC MỚI (brief hạng mục 2): **2 cột trên desktop, 1 cột trên điện thoại**.
 *  - Cột trái (chỉ desktop): card nhận diện + 4 vai trò có màn hình trên app + nguồn dữ liệu đang
 *    dùng. Đây là thông tin người dùng thật sự cần trước khi đăng nhập (biết mình sẽ vào màn nào),
 *    không phải ảnh trang trí.
 *  - Cột phải: form đăng nhập trong [LabCard] — giữ NGUYÊN 4 ô nhập + toggles + thông báo lỗi.
 *
 * ĐÃ BỎ: dải "4 trạng thái" (Form trống / Đang tải / Lỗi / Thành công) hiện bằng pill — trạng
 * thái đã hiện đúng chỗ của nó (nút đổi nhãn, callout lỗi, callout thành công), dải pill chỉ làm
 * màn trông như mockup.
 *
 * Ô mật khẩu nhận **mật khẩu HOẶC API key**: Odoo 17 cho phép API key (`res.users.apikeys`) dùng
 * thay mật khẩu. Bí mật không bao giờ được ghi ra tệp (xem `AppSettings`).
 * =====================================================================================
 *
 * @param onToggleDataSource `null` (mặc định, desktop) = công tắc nguồn dữ liệu nằm trong màn
 *        "Cài đặt demo", màn này KHÔNG hiện gì thêm. Khác `null` (điện thoại — không có màn cài
 *        đặt) = hiện một hàng "Nguồn dữ liệu + công tắc" ngay trên form, để bản Android vẫn có
 *        đường bật Odoo thật.
 */
@Composable
fun LoginScreen(
    vm: LoginViewModel,
    onLoggedIn: () -> Unit,
    usingRealApi: Boolean = false,
    modifier: Modifier = Modifier,
    onToggleDataSource: ((Boolean) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    // TỰ ĐIỀU HƯỚNG (bắt buộc): đăng nhập xong là sang NGAY màn của vai trò, KHÔNG bắt người dùng
    // bấm thêm. `roles` đã được lưu trong `AppSettings.saveSession()` nên `onLoggedIn` chỉ việc
    // đọc lại và chọn route (`Workspace.fromRoles`). Effect chạy một lần cho mỗi lần thành công.
    LaunchedEffect(vm.isSuccess) {
        if (vm.isSuccess) onLoggedIn()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(AppTheme.lab.ground)) {
        val twoColumns = maxWidth >= Tokens.BREAKPOINT_SPLIT_NARROW_DP.dp
        if (twoColumns) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Tokens.s6, vertical = Tokens.s6),
                horizontalArrangement = Arrangement.spacedBy(Tokens.s5),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.weight(1f)) {
                    ContextPanel(usingRealApi = usingRealApi)
                }
                Box(Modifier.weight(1f)) {
                    LabEnter {
                        LoginFormCard(
                            vm = vm,
                            onLoggedIn = onLoggedIn,
                            usingRealApi = usingRealApi,
                            onToggleDataSource = onToggleDataSource,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Tokens.s4, vertical = Tokens.s5),
            ) {
                LabEnter {
                    LoginFormCard(
                        vm = vm,
                        onLoggedIn = onLoggedIn,
                        usingRealApi = usingRealApi,
                        onToggleDataSource = onToggleDataSource,
                    )
                }
                Spacer(Modifier.height(Tokens.s4))
                Text(
                    text = platformInfo(),
                    style = MaterialTheme.typography.caption,
                    color = AppTheme.lab.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Tokens.s5))
            }
        }
    }
}

/**
 * Cột trái (desktop): nhận diện + 4 vai trò có màn hình + nguồn dữ liệu.
 * Nội dung là thông tin nghiệp vụ (ai đăng nhập sẽ vào màn nào), không phải slogan.
 */
@Composable
private fun ContextPanel(usingRealApi: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(size = Tokens.logoNavSize)
            Text(
                text = "AppLogictics",
                style = MaterialTheme.typography.subtitle1,
                color = AppTheme.lab.ink,
                modifier = Modifier.padding(start = Tokens.s2, end = Tokens.s2),
            )
            LabChip(
                text = if (usingRealApi) "Odoo thật" else "Dữ liệu mẫu",
                tone = if (usingRealApi) Tone.Warn else Tone.Info,
            )
        }
        Text(
            text = "Một tài khoản chỉ làm việc ở MỘT màn — app tự chọn theo vai trò Odoo trả về.",
            style = MaterialTheme.typography.body1,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s3),
        )
        Spacer(Modifier.height(Tokens.s5))
        LabCard(title = "Vai trò có màn hình trên app") {
            RoleLine("Tài xế", "driver", "Việc hôm nay — nhận việc, chụp ảnh, ký biên nhận.")
            RoleLine("CS — điều phối", "cs", "Gán tài xế cho từng việc đã chốt.")
            RoleLine("Kho — nhận hàng", "kho", "Nhận hàng và lấy số phiếu nhập kho.")
            RoleLine("Sếp — tổng quan", "manager · sep", "KPI, xu hướng và 10 việc gần nhất.")
        }
        Spacer(Modifier.height(Tokens.s4))
        Text(
            text = "Vai trò khác (kế toán, tài khoản chưa gán quyền) làm việc trên giao diện web " +
                "của Odoo — app sẽ hiện màn thông báo thay vì đoán bừa.",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
        )
    }
}

@Composable
private fun RoleLine(title: String, code: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = Tokens.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = AppTheme.lab.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Tokens.s2))
            LabChip(text = code, tone = Tone.Info)
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s1),
        )
    }
}

/** Form đăng nhập — giữ nguyên 4 ô nhập và toàn bộ logic của [LoginViewModel]. */
@Composable
private fun LoginFormCard(
    vm: LoginViewModel,
    onLoggedIn: () -> Unit,
    usingRealApi: Boolean,
    onToggleDataSource: ((Boolean) -> Unit)?,
) {
    val scope = rememberCoroutineScope()

    // TỰ ĐIỀU HƯỚNG (bắt buộc): đăng nhập xong là sang NGAY màn của vai trò, KHÔNG bắt người dùng
    // bấm thêm. `roles` đã được lưu trong `AppSettings.saveSession()` nên `onLoggedIn` chỉ việc
    // đọc lại và chọn route (`Workspace.fromRoles`). Effect chạy một lần cho mỗi lần thành công.
    LaunchedEffect(vm.isSuccess) {
        if (vm.isSuccess) onLoggedIn()
    }
    LabCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = Lab.READING_MAX_WIDTH_DP.dp),
        title = "Đăng nhập",
        subtitle = if (usingRealApi) {
            "Odoo 17 thật: mở phiên web (cookie session_id) rồi đổi sang mã truy cập 8 giờ."
        } else {
            "Đang ở nguồn DỮ LIỆU MẪU — không gọi mạng."
        },
    ) {
        // ---- Công tắc nguồn dữ liệu (CHỈ điện thoại: desktop dùng màn Cài đặt demo) ----
        if (onToggleDataSource != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabSectionTitle(
                    text = "Nguồn dữ liệu",
                    hint = if (usingRealApi) "Odoo thật" else "Mẫu (an toàn)",
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = usingRealApi,
                    onCheckedChange = onToggleDataSource,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.lab.card,
                        checkedTrackColor = AppTheme.lab.accent,
                        uncheckedThumbColor = AppTheme.lab.ink2,
                        uncheckedTrackColor = AppTheme.lab.panel,
                    ),
                )
            }
            Spacer(Modifier.height(Tokens.s3))
        }

        LabFieldLabel("Máy chủ Odoo")
        OutlinedTextField(
            value = vm.server,
            onValueChange = vm::onServerChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !vm.isLoading,
            colors = labTextFieldColors(error = vm.usesPlainHttp),
            placeholder = { Text("https://erp.congty.vn:8069") },
            isError = vm.usesPlainHttp,
        )

        Spacer(Modifier.height(Tokens.s3))

        LabFieldLabel("Cơ sở dữ liệu")
        OutlinedTextField(
            value = vm.db,
            onValueChange = vm::onDbChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !vm.isLoading,
            colors = labTextFieldColors(),
            placeholder = { Text("tên cơ sở dữ liệu, vd: odoo17_lab") },
        )

        Spacer(Modifier.height(Tokens.s3))

        LabFieldLabel("Tên đăng nhập")
        OutlinedTextField(
            value = vm.login,
            onValueChange = vm::onLoginChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !vm.isLoading,
            colors = labTextFieldColors(),
            placeholder = { Text("vd: taixe01") },
        )

        Spacer(Modifier.height(Tokens.s3))

        LabFieldLabel("Mật khẩu hoặc API key")
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = vm::onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !vm.isLoading,
            colors = labTextFieldColors(),
            placeholder = { Text("mật khẩu Odoo, hoặc API key") },
            visualTransformation = if (vm.apiKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { vm.apiKeyVisible = !vm.apiKeyVisible }) {
                    Text(
                        text = if (vm.apiKeyVisible) "Ẩn" else "Hiện",
                        color = AppTheme.lab.accent,
                    )
                }
            },
        )
        Text(
            text = "Mật khẩu/API key chỉ dùng một lần để mở phiên, KHÔNG lưu ra tệp. " +
                "App chỉ lưu mã truy cập 8 giờ và địa chỉ máy chủ.",
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            modifier = Modifier.padding(top = Tokens.s1),
        )

        vm.usesPlainHttp.let { plain ->
            if (plain) {
                CalloutBox(
                    title = "Kết nối không mã hoá",
                    body = "Địa chỉ bắt đầu bằng http:// nên mật khẩu/API key đi qua mạng " +
                        "dưới dạng rõ. Chỉ dùng cho máy chủ thử nghiệm trên máy này.",
                    tone = Tone.Warn,
                    modifier = Modifier.padding(top = Tokens.s3),
                )
            }
        }

        Button(
            onClick = { vm.submit(scope) },
            enabled = !vm.isLoading,
            modifier = Modifier.fillMaxWidth().height(Tokens.touchPrimary),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppTheme.lab.accent,
                contentColor = AppTheme.lab.onAccent,
            ),
        ) {
            Text(if (vm.isLoading) "Đang đăng nhập…" else "Đăng nhập")
        }

        if (vm.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.s3),
                color = AppTheme.lab.accent,
                backgroundColor = AppTheme.lab.panel,
            )
        }

        // ---------------------------------------------------------------- Trạng thái lỗi
        val error = vm.state as? UiState.Error
        if (error != null) {
            CalloutBox(
                title = "Không đăng nhập được",
                body = error.message,
                tone = Tone.Bad,
                modifier = Modifier.padding(top = Tokens.s3),
                actionLabel = "Thử lại",
                onAction = { vm.submit(scope) },
            )
        }

        // ------------------------------------------------------------ Trạng thái thành công
        if (vm.isSuccess) {
            CalloutBox(
                title = "Đăng nhập thành công",
                body = "Đã có mã truy cập. Mật khẩu không được giữ lại trong bộ nhớ sau bước " +
                    "này. App tự chọn màn theo VAI TRÒ của tài khoản (tài xế · CS · kho · " +
                    "tổng quan) — vai trò khác làm việc trên Odoo web.",
                tone = Tone.Ok,
                modifier = Modifier.padding(top = Tokens.s3),
            )
            Button(
                onClick = onLoggedIn,
                // Đường lui nếu effect trên chưa kịp chạy — nút KHÔNG bao giờ bị disable.
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Tokens.s3)
                    .height(Tokens.touchPrimary),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AppTheme.lab.accent,
                    contentColor = AppTheme.lab.onAccent,
                ),
            ) {
                // Không còn là "vào màn hình tài xế": màn đích do `roles` quyết định.
                Text("Vào màn làm việc")
            }
        }

        Spacer(Modifier.height(Tokens.s4))
        Text(
            text = if (usingRealApi) {
                "Luồng thật: /web/session/authenticate → /lab/api/v1/auth/login → " +
                    "GET /lab/api/v1/driver/jobs (kèm cookie phiên + header X-Lab-Token)."
            } else {
                "Nguồn mẫu: mật khẩu từ 8 ký tự → đăng nhập thành công; chứa \"sai\" → xem " +
                    "trạng thái lỗi. Đổi kịch bản trong \"Cài đặt demo\"."
            },
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
        )
        Spacer(Modifier.height(Tokens.s1))
        Text(
            text = platformInfo(),
            style = MaterialTheme.typography.caption,
            color = AppTheme.lab.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
