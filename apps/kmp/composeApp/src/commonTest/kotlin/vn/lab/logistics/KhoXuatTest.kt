package vn.lab.logistics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.KhoXuatKien
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.ScanOutcome
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.model.XuatKetQua
import vn.lab.logistics.core.repo.KhoRepo
import vn.lab.logistics.feature.kho.KhoXuatViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * XUẤT KHO THEO CHUYẾN — test JVM offline (`:composeApp:desktopTest`).
 *
 * Ba nhánh nghiệp vụ bắt buộc phải khoá (đây là chỗ dễ sai và hậu quả là SAI TỒN KHO):
 *  1. **Đủ kiện** ⇒ xác nhận gọi API luôn, có snackbar + đóng chi tiết;
 *  2. **Thiếu kiện, chưa có lý do** ⇒ CHẶN: mở hộp thoại bắt buộc nhập lý do, KHÔNG gọi API
 *     (quyết định M9);
 *  3. **Thiếu kiện + có lý do** ⇒ được xuất, kết quả nêu rõ số kiện thiếu;
 *  4. Server trả 409 `CON_KIEN_CHUA_QUET` (người khác quét/xuất cùng lúc) ⇒ hiện danh sách còn
 *     thiếu + mở hộp thoại, KHÔNG coi là lỗi hệ thống.
 *
 * Dùng `Dispatchers.Unconfined` vì repo giả trả kết quả ngay ⇒ không cần `runBlocking`.
 * =====================================================================================
 */
class KhoXuatTest {

    private class FakeKhoRepo(
        private var chuyen: KhoXuatChuyen,
        private var ketQuaXuat: XuatKetQua = XuatKetQua.DaXuat(
            phieu = "WH/OUT/00103",
            soKienXuat = 3,
            soKienThieu = 0,
            state = "done",
            trung = false,
        ),
    ) : KhoRepo {
        var soLanXuat = 0
            private set
        var lyDoGuiDi: String? = null
            private set
        var uuidGuiDi: String? = null
            private set
        var scanTraVe: ScanResult? = null

        fun datChuyen(moi: KhoXuatChuyen) {
            chuyen = moi
        }

        fun datKetQuaXuat(kq: XuatKetQua) {
            ketQuaXuat = kq
        }

        override suspend fun choNhan(): Result<List<KhoJob>> = Result.success(emptyList())

        override suspend fun nhan(jobId: Int): Result<ReceiveOutcome> =
            Result.failure(IllegalStateException("không dùng trong test xuất kho"))

        override suspend fun kienCuaViec(jobId: Int): Result<KienCuaViec> =
            Result.success(KienCuaViec(job = null, soKien = 0, daQuet = 0, kien = emptyList()))

        override suspend fun quet(
            ma: String,
            moc: String,
            clientUuid: String,
            ghiChu: String?,
        ): Result<ScanResult> {
            val scan = scanTraVe ?: return Result.failure(IllegalStateException("chưa đặt scan"))
            return Result.success(scan)
        }

        override suspend fun choXuat(): Result<List<KhoXuatChuyen>> = Result.success(listOf(chuyen))

        override suspend fun chiTietXuat(chuyenId: Int): Result<KhoXuatChuyen> = Result.success(chuyen)

        override suspend fun xacNhanXuat(
            chuyenId: Int,
            clientUuid: String,
            lyDoThieu: String?,
        ): Result<XuatKetQua> {
            soLanXuat++
            uuidGuiDi = clientUuid
            lyDoGuiDi = lyDoThieu
            return Result.success(ketQuaXuat)
        }
    }

    private fun kien(ma: String, shipped: Boolean) = KhoXuatKien(
        ma = ma,
        maXuat = "XUAT/26/" + ma.takeLast(6),
        trangThai = if (shipped) "shipped" else "packed",
        trangThaiLabel = if (shipped) "Đã xuất kho" else "Đã in tem xuất",
    )

    private fun chuyen(daQuet: Int, tong: Int = 3) = KhoXuatChuyen(
        chuyenId = 701,
        chuyen = "CH/AIR/26/0301",
        state = "booking_confirmed",
        stateLabel = "Đã chốt booking",
        diemDen = "NRT — Tokyo",
        soKien = tong,
        daQuet = daQuet,
        kien = (1..tong).map { i ->
            kien("KIEN/26/07010$i", shipped = i <= daQuet)
        },
    )

    private fun vmMoChuyen(repo: FakeKhoRepo): Pair<KhoXuatViewModel, CoroutineScope> {
        val vm = KhoXuatViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        vm.load(scope)
        vm.open(701, scope)
        return vm to scope
    }

    // ------------------------------------------------------- 1. đủ kiện ⇒ xuất được

    @Test
    fun `du kien thi xac nhan goi api va bao so phieu`() {
        val repo = FakeKhoRepo(chuyen(daQuet = 3))
        val (vm, scope) = vmMoChuyen(repo)

        vm.xacNhan(scope)

        assertEquals(1, repo.soLanXuat)
        assertNotNull(repo.uuidGuiDi, "phải sinh clientUuid cho lần xác nhận")
        assertNull(repo.lyDoGuiDi, "đủ kiện thì KHÔNG gửi lý do")
        assertTrue(vm.notice!!.contains("WH/OUT/00103"), "snackbar phải có số phiếu: ${vm.notice}")
        assertNull(vm.openedId, "xuất xong thì đóng chi tiết")
    }

    // -------------------------------------------- 2. thiếu kiện, chưa lý do ⇒ CHẶN

    @Test
    fun `thieu kien thi mo hop thoai bat buoc ly do va khong goi api`() {
        val repo = FakeKhoRepo(chuyen(daQuet = 1))
        val (vm, scope) = vmMoChuyen(repo)

        vm.xacNhan(scope)

        assertTrue(vm.lyDoDialogOpen, "thiếu kiện ⇒ phải mở hộp thoại nhập lý do")
        assertEquals(0, repo.soLanXuat, "chưa có lý do thì KHÔNG được gọi API")
        assertEquals(2, vm.kienConThieu.size, "phải nêu đúng 2 kiện còn thiếu")
        assertNotNull(vm.openedId, "vẫn ở lại chi tiết để nhập lý do")
    }

    @Test
    fun `ly do rong thi chan va khong goi api`() {
        val repo = FakeKhoRepo(chuyen(daQuet = 1))
        val (vm, scope) = vmMoChuyen(repo)

        vm.xacNhan(scope)
        vm.onLyDoChange("   ")
        vm.xacNhanThieu(scope)

        assertEquals(0, repo.soLanXuat, "lý do rỗng KHÔNG được gửi")
        assertNotNull(vm.lyDoError, "phải báo lỗi bắt buộc nhập lý do")
        assertTrue(vm.lyDoDialogOpen, "vẫn mở hộp thoại để người dùng sửa")
    }

    // ------------------------------------------- 3. thiếu kiện + có lý do ⇒ xuất được

    @Test
    fun `thieu kien co ly do thi xuat duoc va bao so kien thieu`() {
        val repo = FakeKhoRepo(chuyen(daQuet = 2))
        repo.datKetQuaXuat(
            XuatKetQua.DaXuat(
                phieu = "WH/OUT/00103",
                soKienXuat = 2,
                soKienThieu = 1,
                state = "done",
                trung = false,
            ),
        )
        val (vm, scope) = vmMoChuyen(repo)

        vm.xacNhan(scope)
        vm.onLyDoChange("Kiện bị rách, khách rút 1 kiện")
        vm.xacNhanThieu(scope)

        assertEquals(1, repo.soLanXuat)
        assertEquals("Kiện bị rách, khách rút 1 kiện", repo.lyDoGuiDi)
        assertTrue(
            vm.notice!!.contains("xuất thiếu 1 kiện"),
            "snackbar phải nêu số kiện thiếu: ${vm.notice}",
        )
        assertNull(vm.openedId)
    }

    // ------------------------------------------------- 4. server chặn 409 ⇒ hiện danh sách

    @Test
    fun `server chan con kien chua quet thi hien danh sach va mo hop thoai`() {
        val repo = FakeKhoRepo(chuyen(daQuet = 3))
        repo.datKetQuaXuat(
            XuatKetQua.ConKienChuaQuet(
                conLai = 2,
                danhSach = listOf("KIEN/26/070102", "KIEN/26/070103"),
                message = "Còn 2 kiện chưa quét tem xuất. Quét đủ, hoặc nhập lý do xuất thiếu.",
            ),
        )
        val (vm, scope) = vmMoChuyen(repo)

        vm.xacNhan(scope)

        assertEquals(1, repo.soLanXuat, "vẫn là 1 lần gọi — server trả nhánh nghiệp vụ")
        assertEquals(2, vm.kienConThieu.size)
        assertTrue(vm.lyDoDialogOpen, "phải mở hộp thoại nhập lý do sau khi bị chặn")
        assertNotNull(vm.actionError, "phải hiện câu giải thích của server")
        assertNull(vm.notice, "chưa xuất được thì chưa báo thành công")
        assertNotNull(vm.openedId, "vẫn ở lại chi tiết")
    }

    // ------------------------------------------------- 5. quét cập nhật tiến độ theo server

    @Test
    fun `quet tem xuat cap nhat tien do va trang thai kien`() {
        val repo = FakeKhoRepo(chuyen(daQuet = 0, tong = 3))
        repo.scanTraVe = ScanResult(
            maQuet = "XUAT/26/070101",
            kien = "KIEN/26/070101",
            maXuat = "XUAT/26/070101",
            job = null,
            chuyen = "CH/AIR/26/0301",
            trangThai = "Đã xuất kho",
            outcome = ScanOutcome.OK,
            canhBao = null,
            daQuet = 1,
            tong = 3,
        )
        val (vm, scope) = vmMoChuyen(repo)

        vm.onScanInputChange("XUAT/26/070101")
        vm.quet(scope)

        val detail = vm.detail!!
        assertEquals(1, detail.daQuet, "tiến độ lấy từ server")
        assertEquals(3, detail.soKien)
        assertTrue(detail.kien.first { it.ma == "KIEN/26/070101" }.daXuat)
        assertEquals("", vm.scanInput, "quét ok thì xoá ô nhập")
        assertEquals(1, vm.scanLines.size)
        assertFalse(detail.duKien, "1/3 kiện chưa đủ")
    }
}
