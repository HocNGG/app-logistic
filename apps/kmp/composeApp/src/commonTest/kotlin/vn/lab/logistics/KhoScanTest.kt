package vn.lab.logistics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.KhoKien
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.net.parseKienCuaViec
import vn.lab.logistics.core.net.parseScanResult
import vn.lab.logistics.core.repo.KhoRepo
import vn.lab.logistics.feature.kho.KhoJobFilter
import vn.lab.logistics.feature.kho.KhoReceiveViewModel
import vn.lab.logistics.feature.kho.scanToneOf
import vn.lab.logistics.feature.kho.statusLabel
import vn.lab.logistics.feature.kho.tienDoLabel
import vn.lab.logistics.ui.components.decodeQrFromArgb
import vn.lab.logistics.ui.components.encodeQrMatrix
import vn.lab.logistics.ui.components.extractScanCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * QUÉT TEM Ở MÀN KHO — test JVM, chạy offline (`:composeApp:desktopTest`).
 *
 * Năm bất biến nghiệp vụ phải khoá (đây là thứ dễ hỏng nhất khi sửa UI):
 *  1. `ok` **tăng** tiến độ; `trung` **KHÔNG** tăng (nếu sai, thủ kho tưởng đã đủ kiện);
 *  2. `sai_moc` hiện lỗi + câu giải thích của server, tiến độ giữ nguyên;
 *  3. "Hoàn tất nhận" chỉ có tác dụng khi đã đủ kiện — và **không gọi lại API nhận hàng**;
 *  4. Danh sách kiện lấy từ `GET /kho/jobs/<id>/kien` (BE ship 2026-11-03): việc CHƯA nhận ⇒
 *     0 kiện (empty state), việc đã nhận 3 kiện / 2 đã quét ⇒ tiến độ 2/3 + chip đúng;
 *  5. payload THẬT của `/scan` khi `sai_moc` là `ok:false` + HTTP 409 nhưng vẫn có `data`.
 *
 * Vì sao dùng `Dispatchers.Unconfined`: repo giả trả kết quả NGAY (không `delay`) nên coroutine
 * chạy xong đồng bộ — test không cần `runBlocking` (API chỉ có trên JVM/Native, không có trong
 * common code) và không cần thư viện coroutines-test.
 * =====================================================================================
 */
class KhoScanTest {

    // ------------------------------------------------------------------ repo giả

    private class FakeKhoRepo(
        private val jobs: List<KhoJob>,
        private var kienPayload: KienCuaViec = KienCuaViec(null, 0, 0, emptyList()),
        private var scanQueue: MutableList<Result<ScanResult>> = mutableListOf(),
    ) : KhoRepo {
        var soLanQuet = 0
            private set
        var soLanNhan = 0
            private set
        var soLanTaiKien = 0
            private set

        fun datKien(payload: KienCuaViec) {
            kienPayload = payload
        }

        // Result is a value class: Kotlin does NOT allow vararg Result<...>, so take a List.
        fun xepHangKetQua(results: List<Result<ScanResult>>) {
            scanQueue = results.toMutableList()
        }

        override suspend fun choNhan(): Result<List<KhoJob>> = Result.success(jobs)

        override suspend fun nhan(jobId: Int): Result<ReceiveOutcome> {
            soLanNhan++
            return Result.success(
                ReceiveOutcome(state = "confirmed", stateLabel = "Đã về kho", pickingName = "WH/IN/00001"),
            )
        }

        override suspend fun kienCuaViec(jobId: Int): Result<KienCuaViec> {
            soLanTaiKien++
            return Result.success(kienPayload)
        }

        override suspend fun quet(
            ma: String,
            moc: String,
            clientUuid: String,
            ghiChu: String?,
        ): Result<ScanResult> {
            soLanQuet++
            if (scanQueue.isEmpty()) {
                return Result.failure(IllegalStateException("Chưa xếp hàng kết quả cho lần quét."))
            }
            return scanQueue.removeAt(0)
        }

        // 3 hàm của màn XUẤT KHO không dùng trong test này — khai để thoả interface.
        override suspend fun choXuat(): Result<List<KhoXuatChuyen>> = Result.success(emptyList())

        override suspend fun chiTietXuat(chuyenId: Int): Result<KhoXuatChuyen> =
            Result.failure(IllegalStateException("không dùng trong test nhận hàng"))

        override suspend fun xacNhanXuat(
            chuyenId: Int,
            clientUuid: String,
            lyDoThieu: String?,
        ): Result<XuatKetQua> = Result.failure(IllegalStateException("không dùng trong test nhận hàng"))
    }

    private fun job(id: Int = 601, packages: Int? = 3) = KhoJob(
        id = id,
        ref = "LH/26/0201",
        shop = "Shop Mỹ phẩm Lan Chi",
        packages = packages,
        weightKg = 12.5,
        driverName = "Trần Văn Hùng",
    )

    private fun kien(ma: String, daQuet: Boolean, gio: String? = null) = KhoKien(
        ma = ma,
        maXuat = null,
        trangThai = "in_stock",
        trangThaiLabel = "Trong kho",
        daQuet = daQuet,
        quetLuc = gio,
    )

    private fun scan(
        ma: String = "KIEN/26/000001",
        outcome: ScanOutcome,
        daQuet: Int,
        tong: Int,
        canhBao: String? = null,
    ) = ScanResult(
        maQuet = ma,
        kien = ma,
        maXuat = null,
        job = "LH/26/0201",
        chuyen = null,
        trangThai = "Trong kho",
        outcome = outcome,
        canhBao = canhBao,
        daQuet = daQuet,
        tong = tong,
    )

    private fun vmChuaNhan(repo: FakeKhoRepo): Pair<KhoReceiveViewModel, CoroutineScope> {
        val vm = KhoReceiveViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        vm.load(scope)
        vm.open(601)
        // Màn hình thật gọi `nhan` trước; ở đây mô phỏng đúng đường đó để `received` = true.
        vm.nhan(601, scope)
        // Xoá câu "Đã nhận …" của bước nhận hàng: test quét chỉ quan tâm thông báo của bước quét.
        vm.clearNotice()
        return vm to scope
    }

    // ------------------------------------------------------------------ 1. ok tăng tiến độ

    @Test
    fun `quet ok tang tien do va xoa o nhap`() {
        val repo = FakeKhoRepo(listOf(job()))
        val (vm, scope) = vmChuaNhan(repo)
        assertTrue(vm.isReceived(601), "phải nhận hàng trước khi quét tem")

        repo.xepHangKetQua(listOf(Result.success(scan(outcome = ScanOutcome.OK, daQuet = 1, tong = 3))))
        vm.onScanInputChange(601, "KIEN/26/000001")
        vm.quet(601, scope)

        val session = vm.scanOf(601)
        assertEquals(1, session.daQuet, "quét ok phải tăng tiến độ")
        assertEquals(3, session.tong)
        assertEquals(1, session.lines.size)
        assertEquals("", session.input, "quét xong phải xoá ô để sẵn sàng quét kiện sau")
        assertEquals(1, repo.soLanQuet)
        assertFalse(session.duKien, "1/3 kiện thì chưa đủ")
        assertNull(session.error)
    }

    // ------------------------------------------------------------------ 2. trùng KHÔNG tăng

    @Test
    fun `quet trung khong tang tien do nhung van hien ket qua`() {
        val repo = FakeKhoRepo(listOf(job()))
        val (vm, scope) = vmChuaNhan(repo)

        repo.xepHangKetQua(
            listOf(
                Result.success(scan(outcome = ScanOutcome.OK, daQuet = 1, tong = 3)),
                Result.success(scan(outcome = ScanOutcome.TRUNG, daQuet = 1, tong = 3)),
            ),
        )
        vm.onScanInputChange(601, "KIEN/26/000001")
        vm.quet(601, scope)
        assertEquals(1, vm.scanOf(601).daQuet)

        // Quét lại ĐÚNG kiện đó: server trả `trung`, tiến độ phải GIỮ NGUYÊN.
        vm.onScanInputChange(601, "KIEN/26/000001")
        vm.quet(601, scope)

        val session = vm.scanOf(601)
        assertEquals(1, session.daQuet, "quét trùng KHÔNG được tăng tiến độ")
        assertEquals(2, session.lines.size, "vẫn phải hiện dòng kết quả để người quét thấy")
        assertEquals(ScanOutcome.TRUNG, session.lines.first().outcome)
        assertNotNull(session.lines.first().giaiThich, "trùng phải có câu giải thích")
        assertEquals(
            "KIEN/26/000001",
            session.input,
            "quét trùng giữ nguyên mã trong ô để người dùng thấy mình vừa quét gì",
        )
    }

    // ------------------------------------------------------------------ 3. sai mốc hiện lỗi

    @Test
    fun `quet sai moc hien canh bao va khong tang tien do`() {
        val repo = FakeKhoRepo(listOf(job()))
        val (vm, scope) = vmChuaNhan(repo)

        repo.xepHangKetQua(
            listOf(
                Result.success(
                    scan(
                        outcome = ScanOutcome.SAI_MOC,
                        daQuet = 0,
                        tong = 3,
                        canhBao = "Kiện đã in tem xuất (hoặc đã xuất kho) — không quét tem nhận được nữa.",
                    ),
                ),
            ),
        )
        vm.onScanInputChange(601, "KIEN/26/000001")
        vm.quet(601, scope)

        val session = vm.scanOf(601)
        val line = session.lines.first()
        assertEquals(ScanOutcome.SAI_MOC, line.outcome)
        assertEquals(0, session.daQuet, "sai mốc không được tăng tiến độ")
        assertTrue(
            line.giaiThich!!.contains("không quét tem nhận"),
            "phải giữ nguyên câu cảnh báo của server: ${line.giaiThich}",
        )
        assertEquals(vn.lab.logistics.ui.components.Tone.Bad, scanToneOf(line.outcome))
    }

    // ------------------------------------------------------------------ 4. hoàn tất chỉ khi đủ

    @Test
    fun `hoan tat chi khi du kien va khong goi lai API nhan hang`() {
        val repo = FakeKhoRepo(listOf(job()))
        val (vm, scope) = vmChuaNhan(repo)
        assertEquals(1, repo.soLanNhan, "chỉ nhận hàng đúng 1 lần")

        repo.xepHangKetQua(listOf(Result.success(scan(outcome = ScanOutcome.OK, daQuet = 1, tong = 3))))
        vm.onScanInputChange(601, "KIEN/26/000001")
        vm.quet(601, scope)

        // 1/3 ⇒ bấm Hoàn tất KHÔNG có tác dụng (nút ở UI cũng bị khoá).
        vm.hoanTatNhan(601)
        assertNull(vm.notice, "chưa đủ kiện thì không được báo hoàn tất")
        assertNotNull(vm.openedId, "chưa đủ kiện thì vẫn ở lại chi tiết")

        // Quét đủ 3 kiện (server trả tiến độ 3/3).
        repo.xepHangKetQua(
            listOf(
                Result.success(scan(ma = "KIEN/26/000002", outcome = ScanOutcome.OK, daQuet = 2, tong = 3)),
                Result.success(scan(ma = "KIEN/26/000003", outcome = ScanOutcome.OK, daQuet = 3, tong = 3)),
            ),
        )
        vm.onScanInputChange(601, "KIEN/26/000002")
        vm.quet(601, scope)
        vm.onScanInputChange(601, "KIEN/26/000003")
        vm.quet(601, scope)

        val session = vm.scanOf(601)
        assertTrue(session.duKien, "3/3 phải là đủ kiện")
        assertEquals(1f, session.tiLe)

        vm.hoanTatNhan(601)
        assertEquals(
            "Đã xác nhận đủ 3 kiện cho LH/26/0201.",
            vm.notice,
            "phải có câu xác nhận đúng số kiện",
        )
        assertNull(vm.openedId, "hoàn tất xong thì đóng chi tiết")
        assertEquals(1, repo.soLanNhan, "hoàn tất KHÔNG được gọi lại API nhận hàng")
    }

    // ------------------------------------------------------------------ 5. mã rỗng

    @Test
    fun `ma rong thi bao loi va khong goi API`() {
        val repo = FakeKhoRepo(listOf(job()))
        val (vm, scope) = vmChuaNhan(repo)

        vm.onScanInputChange(601, "   ")
        vm.quet(601, scope)

        assertEquals(0, repo.soLanQuet, "mã rỗng thì không được gọi API")
        assertNotNull(vm.scanOf(601).error)
    }

    // ------------------------------------------- 6. danh sách kiện từ endpoint mới (2026-11-03)

    /** (a) Việc CHƯA nhận ⇒ 0 kiện + trạng thái empty state (KHÔNG phải lỗi). */
    @Test
    fun `viec chua nhan thi 0 kien va hien empty state`() {
        val repo = FakeKhoRepo(listOf(job(packages = null)))
        repo.datKien(KienCuaViec(job = "LH/26/0201", soKien = 0, daQuet = 0, kien = emptyList()))
        val (vm, scope) = vmChuaNhan(repo)
        vm.loadKienIfNeeded(601, scope)

        val session = vm.scanOf(601)
        assertNotNull(session.kienView, "đã tải xong danh sách kiện (rỗng)")
        assertTrue(session.chuaNhan, "soKien = 0 ⇒ việc chưa được kho nhận")
        assertEquals(0, session.tong)
        assertEquals(0, session.daQuet)
        assertEquals(0f, session.tiLe)
        assertFalse(session.duKien)
        assertNull(session.error, "việc chưa nhận KHÔNG phải lỗi tải dữ liệu")
        assertEquals(1, repo.soLanTaiKien, "chỉ tải danh sách kiện MỘT lần")
    }

    /** (b) Việc đã nhận 3 kiện, 2 đã quét ⇒ tiến độ 2/3 + chip đúng từng kiện. */
    @Test
    fun `viec da nhan ba kien hai da quet thi tien do hai tren ba`() {
        val repo = FakeKhoRepo(listOf(job()))
        repo.datKien(
            KienCuaViec(
                job = "LH/26/0201",
                soKien = 3,
                daQuet = 2,
                kien = listOf(
                    kien("KIEN/26/060101", daQuet = true, gio = "2026-11-03 04:54:01"),
                    kien("KIEN/26/060102", daQuet = false),
                    kien("KIEN/26/060103", daQuet = true, gio = "2026-11-03 04:37:33"),
                ),
            ),
        )
        val (vm, scope) = vmChuaNhan(repo)
        vm.loadKienIfNeeded(601, scope)

        val session = vm.scanOf(601)
        assertEquals(3, session.tong)
        assertEquals(2, session.daQuet)
        assertEquals("Đã quét 2/3 kiện", tienDoLabel(session.daQuet, session.tong))
        assertEquals(2f / 3f, session.tiLe)
        assertFalse(session.duKien, "2/3 chưa đủ kiện")
        assertFalse(session.chuaNhan)

        val kien = session.kienView!!.kien
        assertEquals(3, kien.size)
        assertTrue(kien[0].daQuet)
        assertFalse(kien[1].daQuet)
        assertEquals("Đã quét", kien[0].nhanQuet)
        assertEquals("Chưa quét", kien[1].nhanQuet)
        assertEquals("04:54 UTC", kien[0].gioQuetLabel, "giờ quét rút gọn + nhãn UTC")
        assertNull(kien[1].gioQuetLabel, "kiện chưa quét thì không có giờ")
    }

    /** (c) Quét kiện thứ 3 ⇒ tiến độ 3/3 và nút "Hoàn tất nhận" bật. */
    @Test
    fun `quet kien cuoi thi du tien do va hoan tat bat`() {
        val repo = FakeKhoRepo(listOf(job()))
        repo.datKien(
            KienCuaViec(
                job = "LH/26/0201",
                soKien = 3,
                daQuet = 2,
                kien = listOf(
                    kien("KIEN/26/060101", daQuet = true, gio = "2026-11-03 04:54:01"),
                    kien("KIEN/26/060102", daQuet = true, gio = "2026-11-03 04:55:10"),
                    kien("KIEN/26/060103", daQuet = false),
                ),
            ),
        )
        val (vm, scope) = vmChuaNhan(repo)
        vm.loadKienIfNeeded(601, scope)
        assertFalse(vm.scanOf(601).duKien, "2/3 ⇒ nút Hoàn tất còn khoá")

        repo.xepHangKetQua(
            listOf(Result.success(scan(ma = "KIEN/26/060103", outcome = ScanOutcome.OK, daQuet = 3, tong = 3))),
        )
        vm.onScanInputChange(601, "KIEN/26/060103")
        vm.quet(601, scope)

        val session = vm.scanOf(601)
        assertEquals(3, session.daQuet)
        assertTrue(session.duKien, "3/3 ⇒ nút Hoàn tất nhận được bật")
        assertEquals(1f, session.tiLe)
        // Kiện vừa quét được đánh dấu NGAY tại chỗ (không phải tải lại cả danh sách).
        assertTrue(session.kienView!!.kien.first { it.ma == "KIEN/26/060103" }.daQuet)
        assertEquals(1, repo.soLanTaiKien, "không tải lại danh sách kiện sau khi quét")

        vm.hoanTatNhan(601)
        assertEquals("Đã xác nhận đủ 3 kiện cho LH/26/0201.", vm.notice)
        assertNull(vm.openedId)
    }

    // ------------------------------------------------------------------ 7. parse payload THẬT

    /**
     * Payload THẬT đo 2026-11-03 (`GET /lab/api/v1/kho/jobs/<id>/kien`, user kho01): 3 kiện,
     * 2 đã quét — đúng thứ tự server trả (mới nhất trước).
     */
    @Test
    fun `parse danh sach kien that cua endpoint moi`() {
        val body = """
            {"ok": true, "data": {"job": "LH/26/4484", "soKien": 3, "daQuet": 2, "kien": [
             {"ma": "KIEN/26/000003", "maXuat": "", "trangThai": "in_stock",
              "trangThaiLabel": "Trong kho", "daQuet": true, "quetLuc": "2026-11-03 04:54:01"},
             {"ma": "KIEN/26/000002", "maXuat": "", "trangThai": "in_stock",
              "trangThaiLabel": "Trong kho", "daQuet": false, "quetLuc": null},
             {"ma": "KIEN/26/000001", "maXuat": "", "trangThai": "in_stock",
              "trangThaiLabel": "Trong kho", "daQuet": true, "quetLuc": "2026-11-03 04:37:33"}]},
             "error": null}
        """.trimIndent()

        val view = parseKienCuaViec(body)

        assertEquals("LH/26/4484", view.job)
        assertEquals(3, view.soKien)
        assertEquals(2, view.daQuet)
        assertFalse(view.chuaNhan)
        assertEquals(3, view.kien.size)
        assertEquals("KIEN/26/000002", view.kien[1].ma)
        assertFalse(view.kien[1].daQuet)
        assertNull(view.kien[1].quetLuc)
        assertEquals("04:54 UTC", view.kien[0].gioQuetLabel)
    }

    /** Việc chưa nhận: `{soKien:0, daQuet:0, kien:[]}` ⇒ empty state, KHÔNG phải lỗi. */
    @Test
    fun `parse viec chua nhan tra ve rong chu khong loi`() {
        val body = """
            {"ok": true, "data": {"job": "LH/26/4539", "soKien": 0, "daQuet": 0, "kien": []},
             "error": null}
        """.trimIndent()

        val view = parseKienCuaViec(body)

        assertTrue(view.chuaNhan)
        assertEquals(0, view.soKien)
        assertTrue(view.kien.isEmpty())
    }

    /**
     * Payload THẬT của `/scan` khi `sai_moc`: HTTP **409**, envelope **`ok:false`**, `error.code`
     * `SAI_MOC`, nhưng `data` vẫn đủ trường và có `ketQua`. App phải ra chip "Sai mốc quét" + câu
     * cảnh báo của server — KHÔNG ném lỗi hệ thống (đây là đường quay về của luồng).
     */
    @Test
    fun `parse sai moc 409 ok false van ra ket qua nghiep vu`() {
        val body = """
            {"ok": false, "data": {"kien": "KIEN/26/000007", "maXuat": "XUAT/26/000004",
             "job": "CH/AIR/26/0257", "chuyen": "CH/AIR/26/0257", "trangThai": "Không xuất",
             "ketQua": "sai_moc", "canhBao": "Kiện chưa ở trạng thái 'đã in tem xuất' — không quét xuất được.",
             "trung": false, "tienDo": {"daQuet": 2, "tong": 6}},
             "error": {"code": "SAI_MOC", "message": "Kiện chưa ở trạng thái 'đã in tem xuất' — không quét xuất được."}}
        """.trimIndent()

        val scan = parseScanResult(body, status = 409, maQuet = "KIEN/26/000007")

        assertEquals(ScanOutcome.SAI_MOC, scan.outcome)
        assertEquals("KIEN/26/000007", scan.kien)
        assertEquals(2, scan.daQuet)
        assertEquals(6, scan.tong)
        assertTrue(
            scan.giaiThich!!.contains("đã in tem xuất"),
            "phải giữ câu cảnh báo của server: ${scan.giaiThich}",
        )
        assertFalse(scan.tinhVaoTienDo)
    }

    /** `khong_tim_thay` (404) cũng là `ok:false` + `data` ⇒ vẫn ra kết quả nghiệp vụ. */
    @Test
    fun `parse khong tim thay 404 van ra ket qua nghiep vu`() {
        val body = """
            {"ok": false, "data": {"kien": "KIEN/SAI", "maXuat": "", "job": "", "chuyen": "",
             "trangThai": "", "ketQua": "khong_tim_thay",
             "canhBao": "Không tìm thấy kiện với mã KIEN/SAI.", "trung": false,
             "tienDo": {"daQuet": 0, "tong": 0}},
             "error": {"code": "NOT_FOUND", "message": "Không tìm thấy kiện với mã KIEN/SAI."}}
        """.trimIndent()

        val scan = parseScanResult(body, status = 404, maQuet = "KIEN/SAI")

        assertEquals(ScanOutcome.KHONG_TIM_THAY, scan.outcome)
        assertEquals("Không tìm thấy kiện này trong hệ thống", scan.nhanKetQua)
        assertEquals(0, scan.tong)
    }

    // --------------------- 8. sắp xếp mã mới nhất lên đầu + bộ lọc + mở Bước 7 ngay khi nhận

    @Test
    fun `danh sach kho xep ma moi nhat len dau va loc theo trang thai buoc 6 va 7`() {
        val cu = KhoJob(
            id = 601,
            ref = "LH/26/0201",
            shop = "Shop Cũ",
            packages = 2,
            weightKg = 5.0,
            driverName = "Trần Văn Hùng",
        )
        val vuaVeKho = KhoJob(
            id = 5425,
            ref = "LH/26/5302",
            shop = "Shop Vừa Nhận Tại Quầy",
            packages = 1,
            weightKg = 10.0,
            driverName = "Trần Văn Hùng",
            pickingName = "WH/IN/01113",
            state = "confirmed",
            stateLabel = "Đã về kho",
        )
        val moiNhat = KhoJob(
            id = 5430,
            ref = "LH/26/5305",
            shop = "Shop Mới Nhất",
            packages = 3,
            weightKg = 15.0,
            driverName = "Trần Văn Hùng",
            state = "picked_up",
            stateLabel = "Đã lấy được hàng",
        )
        val repo = FakeKhoRepo(listOf(cu, vuaVeKho, moiNhat))
        val vm = KhoReceiveViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        vm.load(scope)

        // Sắp xếp: mã mới nhất (5305 -> 5302 -> 0201) ở đầu danh sách, và tự mở đơn mới nhất
        assertEquals(listOf(5430, 5425, 601), vm.visibleJobs.map { it.id })
        assertEquals(5430, vm.openedId, "mặc định mở đơn mới nhất ở đầu danh sách")

        // Việc server trả state=confirmed đã được coi là Đã về kho ngay khi tải (phục vụ Bước 7 & Luồng B2)
        assertTrue(vm.isReceived(5425))
        assertFalse(vm.isReceived(5430))
        assertEquals("Đã lấy được hàng", moiNhat.statusLabel(vm.isReceived(5430)))
        assertEquals("Đã về kho", vuaVeKho.statusLabel(vm.isReceived(5425)))

        // Lọc theo Đã lấy được hàng (Bước 6) và Đã về kho (Bước 7)
        vm.selectFilter(KhoJobFilter.PICKED_UP)
        assertEquals(listOf(5430, 601), vm.visibleJobs.map { it.id })

        vm.selectFilter(KhoJobFilter.CONFIRMED)
        assertEquals(listOf(5425), vm.visibleJobs.map { it.id })

        // Bấm "Nhận hàng & in tem" từ danh sách (khi đang đóng chi tiết) -> phải tự mở chi tiết (Bước 7)
        vm.selectFilter(KhoJobFilter.ALL)
        vm.closeDetail()
        assertNull(vm.openedId)
        vm.nhan(5430, scope)
        assertEquals(5430, vm.openedId, "nhận hàng xong phải tự mở chi tiết để sang Bước 7 quét tem")
        assertTrue(vm.isReceived(5430))
    }

    @Test
    fun `quetMa 1 cham tren dong kien gui ma ngay`() {
        val repo = FakeKhoRepo(listOf(job()))
        val (vm, scope) = vmChuaNhan(repo)
        repo.xepHangKetQua(
            listOf(Result.success(scan(ma = "KIEN/26/001352", outcome = ScanOutcome.OK, daQuet = 1, tong = 1))),
        )

        vm.quetMa(601, "KIEN/26/001352", scope)

        val session = vm.scanOf(601)
        assertEquals(1, session.daQuet)
        assertTrue(session.duKien)
        assertTrue(vm.isScanCompleted(601), "quét đủ 1/1 kiện thì phải khoá quét tiếp")
        assertEquals(1, repo.soLanQuet)

        // Khi đã đủ kiện (1/1), bấm quét tiếp hay gõ mã tiếp đều bị chặn — KHÔNG gọi thêm API `/scan`.
        vm.quetMa(601, "KIEN/26/001352", scope)
        vm.onScanInputChange(601, "KIEN/26/001352")
        vm.quet(601, scope)
        assertEquals(1, repo.soLanQuet, "đã đủ kiện thì không được gọi tiếp API quét")

        // Bấm Hoàn tất nhận -> đóng chi tiết và giữ trạng thái hoàn tất kể cả khi mở lại
        vm.hoanTatNhan(601)
        assertTrue(vm.scanOf(601).daHoanTat)
        assertTrue(vm.isScanCompleted(601))
        vm.open(601)
        vm.quetMa(601, "KIEN/26/001352", scope)
        assertEquals(1, repo.soLanQuet, "sau khi hoàn tất nhận vẫn khoá không cho quét tiếp")
    }

    @Test
    fun `don da ve kho va quet du tu truoc tren server thi khoa quet ngay khi tai danh sach`() {
        val daQuetDu = KhoJob(
            id = 5425,
            ref = "LH/26/5302",
            shop = "AloBro",
            packages = 1,
            weightKg = 4.0,
            driverName = "Trần Văn Hùng",
            pickingName = "WH/IN/00762",
            state = "confirmed",
            stateLabel = "Đã về kho",
            saleOrderName = "S01500",
            scannedPackages = 1,
            totalPackages = 1,
        )
        val repo = FakeKhoRepo(listOf(daQuetDu))
        val vm = KhoReceiveViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        vm.load(scope)

        assertTrue(vm.isReceived(5425))
        assertTrue(vm.isScanCompleted(5425), "server báo đã quét 1/1 kiện thì khoá quét ngay trên danh sách")
        assertEquals("S01500", vm.saleOrderNameOf(5425))

        vm.quetMa(5425, "KIEN/26/001352", scope)
        assertEquals(0, repo.soLanQuet, "không được gọi API quét cho đơn đã quét đủ kiện")
    }

    @Test
    fun `nhan hang tu mo man in tem QR va quet ma QR URL chuyen buoc tiep theo`() {
        val repo = FakeKhoRepo(listOf(job(id = 601, packages = 1)))
        val vm = KhoReceiveViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        vm.load(scope)

        // Bấm Nhận hàng & in tem -> tự mở màn in tem QR (printDialogJobId = 601)
        vm.nhan(601, scope)
        assertEquals(601, vm.printDialogJobId, "nhận hàng xong phải tự đưa vào màn in tem QR")

        // Chuyển từ màn in tem sang máy quét QR trên điện thoại
        vm.switchPrintToScanner(601)
        assertNull(vm.printDialogJobId)
        assertEquals(601, vm.scannerDialogJobId)

        // Kiểm tra sinh ma trận QR ISO/IEC 18004 (Version 1 = 21x21) và trích mã từ URL QR
        val matrix = encodeQrMatrix("KIEN/26/001352")
        assertEquals(21, matrix.size)
        assertTrue(matrix[0][0] && matrix[0][6] && matrix[6][0], "finder pattern góc trên trái phải đen")
        assertEquals(
            "KIEN/26/001352",
            extractScanCode("http://192.168.1.5:8069/lab/track/tok123/KIEN/26/001352"),
        )

        // Quét bằng URL QR đầy đủ -> tự trích mã KIEN/26/001352 và gửi
        repo.xepHangKetQua(
            listOf(Result.success(scan(ma = "KIEN/26/001352", outcome = ScanOutcome.OK, daQuet = 1, tong = 1))),
        )
        vm.quetMa(601, "http://192.168.1.5:8069/lab/track/tok123/KIEN/26/001352", scope)
        assertTrue(vm.scanOf(601).duKien)

        // Xác nhận OK -> đóng cả hộp thoại quét QR và chi tiết để sang bước tiếp theo
        vm.hoanTatNhan(601)
        assertNull(vm.scannerDialogJobId)
        assertNull(vm.openedId)
    }

    @Test
    fun `qr decoder giai ma chinh xac anh tem QR nhan va xuat`() {
        for (expected in listOf("KIEN/26/001352", "XUAT/26/000089", "LH/26/5302")) {
            val matrix = encodeQrMatrix(expected)
            val n = matrix.size
            val quiet = 6
            val scale = 8
            val size = (n + quiet * 2) * scale
            val argb = IntArray(size * size) { 0xFFFFFFFF.toInt() }
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        val y0 = (r + quiet) * scale
                        val x0 = (c + quiet) * scale
                        for (dy in 0 until scale) {
                            for (dx in 0 until scale) {
                                argb[(y0 + dy) * size + (x0 + dx)] = 0xFF000000.toInt()
                            }
                        }
                    }
                }
            }
            val decoded = decodeQrFromArgb(argb, size, size)
            assertEquals(expected, decoded, "camera QR decoder phải đọc đúng mã $expected từ ảnh tem")
        }

        // Kiểm tra ảnh đen (khi camera mới mở), ảnh trắng hoặc nhiễu ngẫu nhiên -> tuyệt đối không giải mã ra chuỗi rác
        val black = IntArray(300 * 300) { 0xFF000000.toInt() }
        assertNull(decodeQrFromArgb(black, 300, 300), "ảnh đen không được trả về mã QR")

        val white = IntArray(300 * 300) { 0xFFFFFFFF.toInt() }
        assertNull(decodeQrFromArgb(white, 300, 300), "ảnh trắng không được trả về mã QR")

        // Mảng nhiễu ngẫu nhiên
        var seed = 123456789L
        val noise = IntArray(300 * 300) {
            seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFF
            if (seed % 2 == 0L) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        assertNull(decodeQrFromArgb(noise, 300, 300), "ảnh nhiễu không được nhận nhầm thành mã QR")
    }

    @Test
    fun `quet lon ma hien thi loi va khong tang tien do`() {
        val repo = FakeKhoRepo(listOf(job(id = 602, packages = 2)))
        val vm = KhoReceiveViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        vm.load(scope)

        // Quét nhầm mã không tìm thấy -> phải báo quét lộn mã
        repo.xepHangKetQua(
            listOf(Result.success(scan(ma = "KIEN/26/999999", outcome = ScanOutcome.KHONG_TIM_THAY, daQuet = 0, tong = 2))),
        )
        vm.quetMa(602, "KIEN/26/999999", scope)
        val session = vm.scanOf(602)
        assertEquals(0, session.daQuet)
        assertTrue(session.error?.contains("Quét lộn mã") == true, "phải báo quét lộn mã khi kết quả là KHONG_TIM_THAY")
    }
}
