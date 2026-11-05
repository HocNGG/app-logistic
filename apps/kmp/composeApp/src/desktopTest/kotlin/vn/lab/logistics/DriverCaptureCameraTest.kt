package vn.lab.logistics

import kotlinx.coroutines.runBlocking
import vn.lab.logistics.core.model.DriverJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.repo.mock.MockDriverWorkRepo
import vn.lab.logistics.feature.capture.DriverCaptureViewModel
import vn.lab.logistics.feature.capture.PhotoSlot
import vn.lab.logistics.ui.components.UiState
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chụp ảnh TRỰC TIẾP bằng camera (yêu cầu 2026-11-03): ảnh chụp phải đi CÙNG đường với ảnh chọn
 * (kiểm định dạng → nén → preview → ô ảnh), chỉ khác nguồn. Nguồn camera được giả bằng lambda
 * để test chạy offline, không mở GUI.
 */
class DriverCaptureCameraTest {

    private fun job() = DriverJob(
        id = 1,
        ref = "LH/26/0001",
        shop = "Shop A",
        address = "1 Đường A",
        contactName = null,
        contactPhone = null,
        windowFrom = null,
        windowTo = null,
        expectedPackages = null,
        state = JobState.EN_ROUTE,
    )

    private fun jpeg(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun vm(camera: () -> ByteArray?) = DriverCaptureViewModel(
        work = MockDriverWorkRepo(),
        job = job(),
        pickImage = { null },
        captureImage = camera,
    )

    @Test
    fun `chup anh xong thi o anh co anh va man hinh co noi dung`() = runBlocking {
        val vm = vm { jpeg() }
        vm.capture(PhotoSlot.HangHoa, this).join()
        assertNotNull(vm.photo(PhotoSlot.HangHoa))
        assertNull(vm.formError)
        assertTrue(vm.state is UiState.Content<*>)
    }

    @Test
    fun `thoat camera khong chup thi khong doi gi`() = runBlocking {
        val vm = vm { null }
        vm.capture(PhotoSlot.HangHoa, this).join()
        assertNull(vm.photo(PhotoSlot.HangHoa))
        assertNull(vm.formError)
        assertTrue(vm.state is UiState.Empty)
    }

    @Test
    fun `camera loi thi hien cau tieng Viet cua loi, khong sap`() = runBlocking {
        val vm = vm { throw IllegalStateException("Không mở được màn chụp ảnh trên máy này.") }
        vm.capture(PhotoSlot.HangHoa, this).join()
        assertNull(vm.photo(PhotoSlot.HangHoa))
        assertEquals("Không mở được màn chụp ảnh trên máy này.", vm.formError)
    }

    @Test
    fun `du lieu camera khong phai anh thi bao loi chup lai`() = runBlocking {
        val vm = vm { byteArrayOf(1, 2, 3, 4) }
        vm.capture(PhotoSlot.HangHoa, this).join()
        assertNull(vm.photo(PhotoSlot.HangHoa))
        assertEquals("Ảnh vừa chụp không đọc được. Vui lòng chụp lại.", vm.formError)
    }

    @Test
    fun `desktop khong bat nut chup anh`() {
        assertEquals(false, platformDefaults().cameraCaptureSupported)
    }
}

