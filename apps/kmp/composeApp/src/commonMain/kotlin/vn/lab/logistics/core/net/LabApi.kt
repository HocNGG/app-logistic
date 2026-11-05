package vn.lab.logistics.core.net

import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsJobsPayload
import vn.lab.logistics.core.model.DemoAccount
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.settings.AppSettings

/**
 * =====================================================================================
 * LabApi — endpoint THẬT của `lab_api` (docs/02 §6.2, ADR-004), đã gọi và đo 2026-10-28.
 *
 * | # | Method | Route                                    | Auth                 | Vai trò |
 * |---|--------|------------------------------------------|----------------------|---------|
 * | 1 | POST   | `/lab/api/v1/auth/login`                 | cookie `session_id`  | mọi     |
 * | 2 | GET    | `/lab/api/v1/driver/jobs`                | cookie + `X-Lab-Token` | driver |
 * | 3 | POST   | `/lab/api/v1/driver/jobs/<id>/accept`    | cookie + `X-Lab-Token` | driver |
 * | 4 | POST   | `/lab/api/v1/driver/jobs/<id>/capture`   | cookie + `X-Lab-Token` | driver |
 * | 5 | POST   | `/lab/api/v1/driver/jobs/<id>/fail`      | cookie + `X-Lab-Token` | driver |
 * | 6 | GET    | `/lab/api/v1/cs/jobs`                    | cookie + `X-Lab-Token` | cs · driver_leader |
 * | 7 | POST   | `/lab/api/v1/cs/jobs/<id>/assign`        | cookie + `X-Lab-Token` | cs · driver_leader |
 * | 8 | GET    | `/lab/api/v1/kho/cho-nhan`               | cookie + `X-Lab-Token` | kho    |
 * | 9 | POST   | `/lab/api/v1/kho/jobs/<id>/nhan`         | cookie + `X-Lab-Token` | kho    |
 * | 10| GET    | `/lab/api/v1/admin/accounts`             | cookie + `X-Lab-Token` | admin  |
 * | 11| POST   | `/lab/api/v1/admin/switch`               | cookie + `X-Lab-Token` | admin  |
 *
 * Vì sao cần CẢ cookie VÀ header (ADR-004 "Chốt sau khi chạy thật"):
 * các endpoint giữ `auth='user'` để Odoo tự dựng `env` = user thật ⇒ **`ir.rule` tự áp
 * dụng** (Luật 3). Header `X-Lab-Token` (8 giờ, hash trong DB) là lớp thứ hai của RIÊNG ta.
 * Thiếu/sai token ⇒ 401 `TOKEN_REQUIRED`/`TOKEN_EXPIRED` ⇒ app coi là **phiên hết hạn**.
 *
 * ⚠️ Body: controller đọc tham số bằng `tools.tham_so()` nên nhận **cả** body phẳng
 * (`{"reason": …}`) **và** JSON-RPC (`{"params": {…}}`). Ở đây gửi đúng kiểu đã đo được:
 *  - login: envelope JSON-RPC rỗng (route `auth/login` là route JSON-RPC);
 *  - accept / assign / nhan: phẳng, đúng khoá controller đọc;
 *  - capture/fail: phẳng, đúng 4–5 khoá controller đọc.
 * =====================================================================================
 */
class LabApi(
    private val client: OdooClient,
    private val settings: AppSettings,
) {

    /** BƯỚC 2 của luồng đăng nhập: đổi phiên web thành token 8 giờ của tài xế. */
    suspend fun authLogin(): LoginSession {
        val result = client.postJson(PATH_AUTH_LOGIN, LOGIN_BODY)
        return parseLoginPayload(result.body, result.status)
    }

    /** Việc hôm nay của tài xế đang đăng nhập. */
    suspend fun driverJobs(token: String = settings.token): List<DriverJob> {
        val result = client.getJson(PATH_DRIVER_JOBS, tokenHeaders(token))
        return parseDriverJobsPayload(result.body, result.status)
    }

    /** Tài xế nhận việc (`new → assigned → en_route`; controller idempotent). */
    suspend fun acceptJob(jobId: Int, token: String = settings.token): SubmitResult {
        val result = client.postJson(jobPath(jobId, "accept"), EMPTY_JSON_BODY, tokenHeaders(token))
        return parseSubmitResult(result.body, result.status)
    }

    /**
     * Gửi ảnh + chữ ký + số kiện + kg.
     * ⚠️ KHÔNG retry tự động: retry = tạo bộ ảnh thứ hai (skill 04 §6). `clientUuid` do app
     * sinh một lần, server dùng để chống trùng khi mạng chập chờn.
     */
    suspend fun captureJob(
        jobId: Int,
        request: CaptureRequest,
        token: String = settings.token,
    ): SubmitResult {
        val result = client.postJson(jobPath(jobId, "capture"), captureRequestBody(request), tokenHeaders(token))
        return parseSubmitResult(result.body, result.status)
    }

    /** Tài xế báo không lấy được hàng — `reason` bắt buộc (server trả 400 nếu rỗng). */
    suspend fun failJob(jobId: Int, reason: String, token: String = settings.token): SubmitResult {
        val result = client.postJson(jobPath(jobId, "fail"), failRequestBody(reason), tokenHeaders(token))
        return parseSubmitResult(result.body, result.status)
    }

    // ------------------------------------------------------------------ CS — điều phối

    /**
     * Danh sách việc CS cần gán tài xế + danh sách tài xế để chọn
     * (`GET /lab/api/v1/cs/jobs` → `{items, drivers}`).
     */
    suspend fun csJobs(token: String = settings.token): CsJobsPayload {
        val result = client.getJson(PATH_CS_JOBS, tokenHeaders(token))
        return parseCsJobsPayload(result.body, result.status)
    }

    /**
     * Gán tài xế cho một việc (`POST /lab/api/v1/cs/jobs/<id>/assign`, body `{"driverId": id}`).
     * KHÔNG retry tự động: đây là POST ghi (skill 04 §6) — người dùng bấm lại là quyết định của
     * người dùng, và server idempotent theo cùng driver.
     */
    suspend fun assignDriver(
        jobId: Int,
        driverId: Int,
        token: String = settings.token,
    ): AssignOutcome {
        val result = client.postJson(
            path = "$PATH_CS_JOBS/$jobId/assign",
            body = assignRequestBody(driverId),
            extraHeaders = tokenHeaders(token),
        )
        return parseAssignResult(result.body, result.status)
    }

    // ------------------------------------------------------------------ Kho — nhận hàng

    /** Việc đã `picked_up` chờ kho nhận + việc vừa về kho (`confirmed`) để quét tem (Bước 6 & 7). */
    suspend fun khoChoNhan(token: String = settings.token): List<KhoJob> {
        val result = client.getJson("$PATH_KHO_CHO_NHAN?includeConfirmed=1", tokenHeaders(token))
        return parseKhoChoNhanPayload(result.body, result.status)
    }

    /**
     * Kho xác nhận đã nhận hàng (`POST /lab/api/v1/kho/jobs/<id>/nhan`).
     * Server tạo `stock.picking` nhập kho và trả `pickingName` = số phiếu nhập kho.
     */
    suspend fun khoNhan(jobId: Int, token: String = settings.token): ReceiveOutcome {
        val result = client.postJson(
            path = "$PATH_KHO_JOBS/$jobId/nhan",
            body = EMPTY_JSON_BODY,
            extraHeaders = tokenHeaders(token),
        )
        return parseKhoReceiveResult(result.body, result.status)
    }

    /**
     * QUÉT TEM KIỆN (`POST /lab/api/v1/scan`) — mốc `nhan` (kho nhận) hoặc `xuat` (xuất chuyến).
     *
     * Kết quả nghiệp vụ (`ok`/`trung`/`sai_moc`/`khong_tim_thay`) nằm trong `data.ketQua`, nên hàm
     * này KHÔNG coi HTTP 404/409 là lỗi — `parseScanResult` đọc `ketQua` rồi trả [ScanResult].
     * App sinh `clientUuid` MỘT LẦN cho mỗi lần quét (chống trùng khi mạng chập, bấm 2 lần).
     */
    suspend fun scanKien(
        ma: String,
        moc: String,
        clientUuid: String,
        ghiChu: String? = null,
        token: String = settings.token,
    ): ScanResult {
        val result = client.postJson(
            path = PATH_SCAN,
            body = scanRequestBody(ma = ma, moc = moc, clientUuid = clientUuid, ghiChu = ghiChu),
            extraHeaders = tokenHeaders(token),
        )
        return parseScanResult(result.body, result.status, maQuet = ma)
    }

    /**
     * Danh sách KIỆN của một việc đã nhận + tiến độ quét
     * (`GET /lab/api/v1/kho/jobs/<id>/kien`, BE ship 2026-11-03).
     *
     * Việc chưa nhận ⇒ server trả `{soKien:0, daQuet:0, kien:[]}` (KHÔNG phải lỗi) — màn hình
     * hiện empty state "việc này chưa được kho nhận".
     */
    suspend fun khoKienCuaViec(jobId: Int, token: String = settings.token): KienCuaViec {
        val result = client.getJson("$PATH_KHO_JOBS/$jobId/kien", tokenHeaders(token))
        return parseKienCuaViec(result.body, result.status)
    }

    // ------------------------------------------------------------------ Kho — XUẤT KHO

    /** Chuyến còn kiện chưa xuất (`GET /lab/api/v1/kho/cho-xuat`). */
    suspend fun khoChoXuat(token: String = settings.token): List<KhoXuatChuyen> {
        val result = client.getJson(PATH_KHO_CHO_XUAT, tokenHeaders(token))
        return parseKhoChoXuatPayload(result.body, result.status)
    }

    /** Chi tiết 1 chuyến chờ xuất (`GET /lab/api/v1/kho/cho-xuat?chuyenId=<id>`). */
    suspend fun khoChoXuatChiTiet(chuyenId: Int, token: String = settings.token): KhoXuatChuyen {
        val result = client.getJson("$PATH_KHO_CHO_XUAT?chuyenId=$chuyenId", tokenHeaders(token))
        return parseKhoChoXuatChiTiet(result.body, result.status)
    }

    /**
     * XÁC NHẬN XUẤT KHO (`POST /lab/api/v1/kho/chuyen/<id>/xuat`).
     *
     * ⚠️ Nhánh "còn kiện chưa quét" (409) trả `ok:false` **kèm** danh sách kiện ⇒
     * `parseKhoXuatKetQua` trả [XuatKetQua.ConKienChuaQuet] thay vì ném lỗi, để UI mời nhập lý do
     * xuất thiếu (quyết định M9). `clientUuid` do app sinh 1 lần cho mỗi lần bấm.
     */
    suspend fun khoXuat(
        chuyenId: Int,
        clientUuid: String,
        lyDoThieu: String? = null,
        token: String = settings.token,
    ): XuatKetQua {
        val result = client.postJson(
            path = "$PATH_KHO_CHUYEN/$chuyenId/xuat",
            body = xuatRequestBody(clientUuid = clientUuid, lyDoThieu = lyDoThieu),
            extraHeaders = tokenHeaders(token),
        )
        return parseKhoXuatKetQua(result.body, result.status)
    }

    // ------------------------------------------------------------------ Sếp — dashboard

    /**
     * KPI tổng quan cho sếp (`GET /lab/api/v1/sep/kpi?range=…`).
     * `range` là lựa chọn trên toolbar (Hôm nay / 7 ngày / 30 ngày) — KHÔNG tự đổi.
     */
    suspend fun sepKpi(range: KpiRange, token: String = settings.token): SepKpi {
        val result = client.getJson("$PATH_SEP_KPI?range=${range.apiValue}", tokenHeaders(token))
        return parseSepKpiPayload(result.body, result.status)
    }

    // ------------------------------------------------------------------ Admin — chuyển tài khoản

    /**
     * Danh sách tài khoản demo để chuyển nhanh (`GET /lab/api/v1/admin/accounts`).
     *
     * CHỈ tài khoản `admin` gọi được: tài khoản khác nhận 403 ⇒ [ApiException.Forbidden] và app
     * hiện đúng câu tiếng Việt của lớp lỗi đó (không lộ "Access Denied" của Odoo).
     */
    suspend fun adminAccounts(token: String = settings.token): List<DemoAccount> {
        val result = client.getJson(PATH_ADMIN_ACCOUNTS, tokenHeaders(token))
        return parseAdminAccountsPayload(result.body, result.status)
    }

    /**
     * Chuyển sang tài khoản [login] (`POST /lab/api/v1/admin/switch`, body `{"login": "…"}`).
     *
     * Server trả **đúng shape của login** (token mới + `roles` của tài khoản đích) ⇒ app ghi
     * phiên mới rồi chọn màn theo `Workspace.fromRoles(roles)`, y như vừa đăng nhập.
     * KHÔNG retry tự động: đây là POST ghi (skill 04 §6) — bấm lại là quyết định của người dùng.
     */
    suspend fun switchAccount(login: String, token: String = settings.token): LoginSession {
        val result = client.postJson(
            path = PATH_ADMIN_SWITCH,
            body = switchAccountRequestBody(login),
            extraHeaders = tokenHeaders(token),
        )
        return parseSwitchSessionPayload(result.body, result.status)
    }

    // ------------------------------------------------------------------ nội bộ

    /** Header xác thực lớp 2 của `lab_api`. Token lấy từ Settings, KHÔNG hardcode. */
    private fun tokenHeaders(token: String): Map<String, String> =
        if (token.isBlank()) emptyMap() else mapOf(HEADER_LAB_TOKEN to token)

    private fun jobPath(jobId: Int, action: String): String = "$PATH_DRIVER_JOBS/$jobId/$action"

    companion object {
        /** Tên header do controller của dự án kiểm tra (không phải Bearer của Odoo 19). */
        const val HEADER_LAB_TOKEN: String = "X-Lab-Token"

        const val PATH_AUTH_LOGIN: String = "/lab/api/v1/auth/login"
        const val PATH_DRIVER_JOBS: String = "/lab/api/v1/driver/jobs"
        const val PATH_CS_JOBS: String = "/lab/api/v1/cs/jobs"
        const val PATH_KHO_CHO_NHAN: String = "/lab/api/v1/kho/cho-nhan"

        /** Dashboard của sếp (`manager`/`sep`) — hợp đồng chốt trong `SepKpiParsing.kt`. */
        const val PATH_SEP_KPI: String = "/lab/api/v1/sep/kpi"

        /** Nhóm route GHI của kho (`…/kho/jobs/<id>/nhan`). */
        const val PATH_KHO_JOBS: String = "/lab/api/v1/kho/jobs"

        /** Quét tem kiện — chung cho mốc nhận (`nhan`) và xuất (`xuat`). */
        const val PATH_SCAN: String = "/lab/api/v1/scan"

        /** Danh sách / chi tiết chuyến chờ xuất (`?chuyenId=` cho chi tiết). */
        const val PATH_KHO_CHO_XUAT: String = "/lab/api/v1/kho/cho-xuat"

        /** Nhóm route GHI của xuất kho: `…/kho/chuyen/<id>/xuat`. */
        const val PATH_KHO_CHUYEN: String = "/lab/api/v1/kho/chuyen"

        /** Admin — danh sách tài khoản demo để chuyển nhanh (chỉ tài khoản `admin`). */
        const val PATH_ADMIN_ACCOUNTS: String = "/lab/api/v1/admin/accounts"

        /** Admin — đổi phiên sang tài khoản khác (trả đúng shape của login). */
        const val PATH_ADMIN_SWITCH: String = "/lab/api/v1/admin/switch"

        private const val EMPTY_JSON_BODY = "{}"

        /** Route `auth/login` là HTTP-JSON nhưng đọc tham số kiểu JSON-RPC ⇒ gửi envelope rỗng. */
        private const val LOGIN_BODY = "{\"jsonrpc\":\"2.0\",\"method\":\"call\",\"params\":{}}"
    }
}
