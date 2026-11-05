package vn.lab.logistics.core.repo.mock

import kotlinx.coroutines.delay
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.KhoKien
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.KhoXuatKien
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.repo.KhoRepo

/**
 * ⚠️ IMPL GIẢ LẬP — dữ liệu MẪU cho màn Kho, không gọi mạng (Luật 8).
 *
 * 3 việc đã `picked_up` chờ kho nhận (đúng nghĩa "kho chờ" của docs/00 §10):
 *  1. việc đủ số kiện + kg (cảnh chính);
 *  2. việc tên shop/địa chỉ rất dài (kiểm tràn chữ ở 360–420dp);
 *  3. việc chưa có số kiện/kg ⇒ UI hiện "Chưa rõ số kiện" / "Chưa rõ khối lượng" (không bịa 0).
 *
 * [session] dùng chung với [MockCsRepo]: nhận hàng xong, gọi lại [choNhan] thì việc đó đã có
 * `pickingName` — mô phỏng đúng việc server đã sinh `stock.picking` nhập kho (Luật 4 REA).
 */
class MockKhoRepo(
    private val scenario: DemoScenario = DemoScenario.Normal,
    private val session: MockOpsSession = MockOpsSession(),
) : KhoRepo {

    override suspend fun choNhan(): Result<List<KhoJob>> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)

        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không tải được danh sách hàng chờ nhận: máy chủ Odoo không phản hồi sau 15 giây. " +
                        "Kiểm tra Odoo đã chạy rồi bấm Tải lại.",
                ),
            )
        }
        if (scenario == DemoScenario.Empty) {
            return Result.success(emptyList())
        }
        return Result.success(
            JOBS.map { job ->
                val picking = session.pickingOf(job.id)
                job.copy(
                    pickingName = picking,
                    state = if (picking != null) JobState.CONFIRMED else JobState.PICKED_UP,
                    stateLabel = if (picking != null) "Đã về kho" else "Đã lấy được hàng",
                )
            },
        )
    }

    override suspend fun nhan(jobId: Int): Result<ReceiveOutcome> {
        delay(if (scenario == DemoScenario.Slow) SLOW_DELAY_MS else NORMAL_DELAY_MS)
        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không nhận được hàng: máy chủ Odoo không phản hồi. Việc vẫn đang chờ nhận.",
                ),
            )
        }
        if (JOBS.none { it.id == jobId }) {
            return Result.failure(
                IllegalStateException("Việc #$jobId không còn trong danh sách chờ nhận của kho."),
            )
        }

        // Số phiếu nhập kho mô phỏng ĐÚNG dạng Odoo sinh (WH/IN/000xx) — app chỉ hiển thị lại.
        // (Không dùng `String.format` — đó là API chỉ có trên JVM, commonMain không dùng được.)
        val phieu = "WH/IN/" + (900 + jobId).toString().padStart(5, '0')
        session.receive(jobId, phieu)
        return Result.success(
            ReceiveOutcome(
                state = JobState.CONFIRMED,
                stateLabel = "Đã về kho",
                pickingName = phieu,
            ),
        )
    }

    /**
     * Danh sách mã kiện MẪU của việc — đúng độ dài `packages` của việc đó.
     *
     * Vì sao mock trả `null` khi việc chưa có số kiện: ngoài đời server cũng không biết số kiện
     * (endpoint danh sách kiện chưa có) ⇒ phải mô phỏng CẢ nhánh "không có dữ liệu" để màn hình
     * được kiểm tra thật, không chỉ nhánh đẹp.
     */
    override suspend fun kienCuaViec(jobId: Int): Result<KienCuaViec> {
        delay(NORMAL_DELAY_MS / 2)
        val job = JOBS.firstOrNull { it.id == jobId }
            ?: return Result.success(KienCuaViec(null, 0, 0, emptyList()))
        // Việc chưa khai số kiện ⇒ mô phỏng đúng nhánh "chưa nhận": 0 kiện (không phải lỗi).
        val soKien = job.packages ?: return Result.success(KienCuaViec(job.ref, 0, 0, emptyList()))
        val kien = (1..soKien).map { i ->
            val ma = kienName(jobId, i)
            // Việc 601: 2 kiện đầu đã quét sẵn để màn hình có dữ liệu thật khi demo.
            val daQuet = jobId == 601 && i <= 2
            KhoKien(
                ma = ma,
                maXuat = null,
                trangThai = "in_stock",
                trangThaiLabel = "Trong kho",
                daQuet = daQuet,
                quetLuc = if (daQuet) "2026-11-03 0" + (3 + i) + ":15:00" else null,
            )
        }
        return Result.success(
            KienCuaViec(
                job = job.ref,
                soKien = soKien,
                daQuet = kien.count { it.daQuet },
                kien = kien,
            ),
        )
    }

    /**
     * Quét tem MẪU — luật giống server thật:
     *  - mã lạ ⇒ `khong_tim_thay`;
     *  - mã đã quét ở mốc này ⇒ `trung` (KHÔNG tăng tiến độ);
     *  - còn lại ⇒ `ok`, tăng tiến độ.
     */
    override suspend fun quet(
        ma: String,
        moc: String,
        clientUuid: String,
        ghiChu: String?,
    ): Result<ScanResult> {
        delay(NORMAL_DELAY_MS / 2)
        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException("Không quét được: máy chủ Odoo không phản hồi."),
            )
        }

        // MỐC XUẤT: kiện thuộc chuyến, không thuộc việc lấy hàng → tra ở CHUYEN_KIEN.
        if (moc == "xuat") {
            val chuyenId = CHUYEN_KIEN.entries.firstOrNull { (_, ds) -> ds.contains(ma.trim()) }?.key
            if (chuyenId == null) {
                return Result.success(
                    ScanResult(
                        maQuet = ma,
                        kien = ma,
                        maXuat = null,
                        job = null,
                        chuyen = null,
                        trangThai = null,
                        outcome = ScanOutcome.KHONG_TIM_THAY,
                        canhBao = "Không tìm thấy kiện với mã ${ma.trim()}.",
                        daQuet = 0,
                        tong = 0,
                    ),
                )
            }
            val dsKien = CHUYEN_KIEN.getValue(chuyenId)
            val khoa = "xuat:${ma.trim()}"
            val daQuetTruoc = daQuet.contains(khoa)
            if (!daQuetTruoc) daQuet += khoa
            return Result.success(
                ScanResult(
                    maQuet = ma,
                    kien = ma.trim(),
                    maXuat = null,
                    job = null,
                    chuyen = CHUYEN.first { it.chuyenId == chuyenId }.chuyen,
                    trangThai = "Đã xuất kho",
                    outcome = if (daQuetTruoc) ScanOutcome.TRUNG else ScanOutcome.OK,
                    canhBao = if (daQuetTruoc) "Kiện này đã xuất kho trước đó." else null,
                    daQuet = dsKien.count { daQuet.contains("xuat:$it") },
                    tong = dsKien.size,
                ),
            )
        }
        val jobId = JOBS.firstOrNull { job ->
            (1..(job.packages ?: 0)).any { kienName(job.id, it) == ma.trim() }
        }?.id
        if (jobId == null) {
            return Result.success(
                ScanResult(
                    maQuet = ma,
                    kien = ma,
                    maXuat = null,
                    job = null,
                    chuyen = null,
                    trangThai = null,
                    outcome = ScanOutcome.KHONG_TIM_THAY,
                    canhBao = "Không tìm thấy kiện với mã ${ma.trim()}.",
                    daQuet = 0,
                    tong = 0,
                ),
            )
        }
        val tong = JOBS.first { it.id == jobId }.packages ?: 0
        val khoa = "$moc:${ma.trim()}"
        val daQuetTruoc = daQuet.contains(khoa)
        if (!daQuetTruoc) daQuet += khoa
        // Đếm ĐÚNG kiện của việc này (không đếm kiện của việc khác trong cùng phiên demo).
        val soDaQuet = (1..tong).count { daQuet.contains("$moc:" + kienName(jobId, it)) }
        return Result.success(
            ScanResult(
                maQuet = ma,
                kien = ma.trim(),
                maXuat = null,
                job = JOBS.first { it.id == jobId }.ref,
                chuyen = null,
                trangThai = "Trong kho",
                outcome = if (daQuetTruoc) ScanOutcome.TRUNG else ScanOutcome.OK,
                canhBao = if (daQuetTruoc) "Kiện này đã quét tem nhận trước đó." else null,
                daQuet = soDaQuet,
                tong = tong,
            ),
        )
    }

    /** Đã quét gì trong phiên demo (khoá `"nhan:KIEN/…"` / `"xuat:XUAT/…"`). */
    private val daQuet: MutableSet<String> = mutableSetOf()

    private fun kienName(jobId: Int, thuTu: Int): String = "KIEN/26/" + (jobId * 100 + thuTu).toString().padStart(6, '0')

    // ==================================================================================
    // XUẤT KHO MẪU — 2 chuyến để thấy cả nhánh "đủ" và nhánh "thiếu kiện"
    // ==================================================================================

    override suspend fun choXuat(): Result<List<KhoXuatChuyen>> {
        delay(NORMAL_DELAY_MS)
        if (scenario == DemoScenario.Error) {
            return Result.failure(
                IllegalStateException(
                    "Không tải được danh sách chuyến chờ xuất: máy chủ Odoo không phản hồi.",
                ),
            )
        }
        if (scenario == DemoScenario.Empty) return Result.success(emptyList())
        return Result.success(CHUYEN.map { chuyenHienTai(it) })
    }

    override suspend fun chiTietXuat(chuyenId: Int): Result<KhoXuatChuyen> {
        delay(NORMAL_DELAY_MS / 2)
        val chuyen = CHUYEN.firstOrNull { it.chuyenId == chuyenId }
            ?: return Result.failure(IllegalStateException("Chuyến #$chuyenId không còn chờ xuất."))
        return Result.success(chuyenHienTai(chuyen))
    }

    /**
     * Xác nhận xuất MẪU — cùng luật với server thật:
     *  - còn kiện chưa quét + KHÔNG có lý do ⇒ chặn (`ConKienChuaQuet`, kèm danh sách thiếu);
     *  - đủ kiện ⇒ `DaXuat` (không thiếu);
     *  - thiếu + CÓ lý do ⇒ `DaXuat` với `soKienThieu > 0` (quyết định M9).
     */
    override suspend fun xacNhanXuat(
        chuyenId: Int,
        clientUuid: String,
        lyDoThieu: String?,
    ): Result<XuatKetQua> {
        delay(NORMAL_DELAY_MS)
        if (scenario == DemoScenario.Error) {
            return Result.failure(IllegalStateException("Không xác nhận được xuất kho."))
        }
        val chuyen = CHUYEN.firstOrNull { it.chuyenId == chuyenId }
            ?: return Result.failure(IllegalStateException("Chuyến #$chuyenId không còn chờ xuất."))
        val conThieu = CHUYEN_KIEN.getValue(chuyenId).filterNot { daQuet.contains("xuat:$it") }
        if (conThieu.isNotEmpty() && lyDoThieu.isNullOrBlank()) {
            return Result.success(
                XuatKetQua.ConKienChuaQuet(
                    conLai = conThieu.size,
                    danhSach = conThieu,
                    message = "Còn ${conThieu.size} kiện chưa quét tem xuất. Quét đủ, hoặc nhập " +
                        "lý do xuất thiếu để tiếp tục.",
                ),
            )
        }
        xuatClientUuid[chuyenId] = clientUuid
        return Result.success(
            XuatKetQua.DaXuat(
                phieu = "WH/OUT/" + (100 + chuyenId).toString().padStart(5, '0'),
                soKienXuat = CHUYEN_KIEN.getValue(chuyenId).size - conThieu.size,
                soKienThieu = conThieu.size,
                state = "done",
                trung = false,
            ),
        )
    }

    /** clientUuid đã dùng cho từng chuyến (demo: chỉ để mô phỏng, không chặn). */
    private val xuatClientUuid: MutableMap<Int, String> = mutableMapOf()

    /** Dựng lại chuyến với trạng thái kiện ĐÃ quét trong phiên demo. */
    private fun chuyenHienTai(chuyen: KhoXuatChuyen): KhoXuatChuyen {
        val kien = chuyen.kien.map { k ->
            if (daQuet.contains("xuat:${k.ma}")) {
                k.copy(trangThai = "shipped", trangThaiLabel = "Đã xuất kho")
            } else {
                k
            }
        }
        return chuyen.copy(kien = kien, daQuet = kien.count { it.daXuat })
    }

    private companion object {
        const val NORMAL_DELAY_MS = 700L
        const val SLOW_DELAY_MS = 2_500L

        /** Mã kiện của từng chuyến demo (khoá = chuyenId). */
        val CHUYEN_KIEN: Map<Int, List<String>> = mapOf(
            701 to listOf("KIEN/26/070101", "KIEN/26/070102", "KIEN/26/070103"),
            702 to listOf("KIEN/26/070201", "KIEN/26/070202"),
        )

        val CHUYEN: List<KhoXuatChuyen> = listOf(
            KhoXuatChuyen(
                chuyenId = 701,
                chuyen = "CH/AIR/26/0301",
                state = "booking_confirmed",
                stateLabel = "Đã chốt booking",
                diemDen = "NRT — Tokyo (Nhật Bản)",
                soKien = 3,
                daQuet = 0,
                kien = CHUYEN_KIEN.getValue(701).mapIndexed { i, ma ->
                    KhoXuatKien(
                        ma = ma,
                        maXuat = "XUAT/26/" + (70100 + i + 1).toString().padStart(6, '0'),
                        trangThai = "packed",
                        trangThaiLabel = "Đã in tem xuất",
                    )
                },
            ),
            KhoXuatChuyen(
                chuyenId = 702,
                chuyen = "CH/SEA/26/0302",
                state = "departed",
                stateLabel = "Đã khởi hành",
                diemDen = "SGN — Cảng Cát Lái (TP.HCM)",
                soKien = 2,
                daQuet = 1,
                kien = CHUYEN_KIEN.getValue(702).mapIndexed { i, ma ->
                    KhoXuatKien(
                        ma = ma,
                        maXuat = "XUAT/26/" + (70200 + i + 1).toString().padStart(6, '0'),
                        trangThai = if (i == 0) "shipped" else "packed",
                        trangThaiLabel = if (i == 0) "Đã xuất kho" else "Đã in tem xuất",
                    )
                },
            ),
        )

        val JOBS: List<KhoJob> = listOf(
            KhoJob(
                id = 601,
                ref = "LH/26/0201",
                shop = "Shop Mỹ phẩm Lan Chi",
                packages = 4,
                weightKg = 12.5,
                driverName = "Trần Văn Hùng",
            ),
            KhoJob(
                id = 602,
                ref = "LH/26/0202",
                shop = "Công ty TNHH Sản xuất Thương mại Dịch vụ Bao bì Nhựa Tân Phú",
                packages = 12,
                weightKg = 47.8,
                driverName = "Nguyễn Thị Mai",
            ),
            KhoJob(
                id = 603,
                ref = "LH/26/0203",
                shop = "Xưởng Nội thất Gỗ Việt",
                packages = null,
                weightKg = null,
                driverName = null,
            ),
        )
    }
}
