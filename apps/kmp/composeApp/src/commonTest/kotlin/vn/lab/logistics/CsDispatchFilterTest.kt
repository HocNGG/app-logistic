package vn.lab.logistics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import vn.lab.logistics.core.model.AssignOutcome
import vn.lab.logistics.core.model.CsDriver
import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.core.model.CsJobsPayload
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.core.repo.CsJobsRepo
import vn.lab.logistics.feature.cs.CsDispatchViewModel
import vn.lab.logistics.feature.cs.CsJobFilter
import vn.lab.logistics.feature.cs.countIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * =====================================================================================
 * ViewModel màn **Trưởng nhóm tài xế · Phân công**: thứ tự hiển thị + chip lọc + việc đang mở.
 *
 * Bốn bất biến (yêu cầu khách 2026-11-03):
 *  (a) danh sách hiển thị sắp `createDate` GIẢM DẦN, mốc vắng xuống cuối;
 *  (b) chip "Mới" chỉ còn việc `state = new`;
 *  (c) chip "Đã gán" chỉ còn việc `state = assigned`;
 *  (d) bấm chip KHÔNG được làm mất việc đang mở ở pane phải (và không đụng danh sách gốc để các
 *      thao tác gán không lệch id).
 *
 * Vì sao `Dispatchers.Unconfined`: repo giả trả kết quả NGAY (không `delay`) nên coroutine chạy
 * xong đồng bộ — test không cần `runBlocking` (không có trong commonTest) hay coroutines-test.
 * =====================================================================================
 */
class CsDispatchFilterTest {

    // ------------------------------------------------------------------ repo giả

    private class FakeCsRepo(private val payload: CsJobsPayload) : CsJobsRepo {
        var soLanTai = 0
            private set

        override suspend fun jobs(): Result<CsJobsPayload> {
            soLanTai++
            return Result.success(payload)
        }

        override suspend fun assign(jobId: Int, driverId: Int): Result<AssignOutcome> =
            Result.success(
                AssignOutcome(
                    state = JobState.ASSIGNED,
                    stateLabel = "Đã gán tài xế",
                    driverName = "Trần Văn Hùng",
                ),
            )
    }

    private fun job(id: Int, state: String, createDate: String?) = CsJob(
        id = id,
        ref = "LH/26/%04d".format(id),
        shop = "Shop $id",
        address = "Địa chỉ $id",
        contactPhone = null,
        windowFrom = null,
        windowTo = null,
        state = state,
        createDate = createDate,
    )

    /**
     * `id` và thời gian CỐ TÌNH ngược nhau:
     *  414 · 10:30 hôm nay (new, cùng giây với 411 nhưng id lớn hơn ⇒ đứng trước)
     *  411 · 10:30 hôm nay (assigned)
     *  410 · 09:00 hôm nay (new)
     *  413 · 22:00 hôm qua  (assigned)
     *  412 · (vắng mốc)     (new) — server cũ, phải xuống CUỐI
     */
    private val payload = CsJobsPayload(
        items = listOf(
            job(410, JobState.NEW, "2026-11-03 09:00:00"),
            job(411, JobState.ASSIGNED, "2026-11-03 10:30:00"),
            job(412, JobState.NEW, null),
            job(413, JobState.ASSIGNED, "2026-11-02 22:00:00"),
            job(414, JobState.NEW, "2026-11-03 10:30:00"),
        ),
        drivers = listOf(CsDriver(id = 1, name = "Trần Văn Hùng", login = "taixe01")),
    )

    private fun vm(repo: CsJobsRepo = FakeCsRepo(payload)): Triple<CsDispatchViewModel, CoroutineScope, CsJobsRepo> {
        val viewModel = CsDispatchViewModel(repo)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        viewModel.load(scope)
        return Triple(viewModel, scope, repo)
    }

    // ------------------------------------------------------------------ (a) thứ tự

    @Test
    fun `a - danh sach hien thi xep theo createDate moi nhat truoc, moc vang xuong cuoi`() {
        val (vm, _, repo) = vm()
        assertEquals(1, (repo as FakeCsRepo).soLanTai, "chỉ tải một lần")
        assertEquals(
            listOf(414, 411, 410, 413, 412),
            vm.visibleJobs.map { it.id },
            "10:30 (id 414 trước 411) → 09:00 → hôm qua → vắng mốc",
        )
        // Danh sách GỐC không bị sắp lại: giữ đúng thứ tự server trả (thao tác gán dùng danh sách này).
        assertEquals(listOf(410, 411, 412, 413, 414), vm.jobs.map { it.id })
        // Việc mở sẵn là việc ĐẦU danh sách hiển thị (mới nhất), không phải id lớn nhất.
        assertEquals(414, vm.openedId)
    }

    // ------------------------------------------------------------------ (b) lọc "Mới"

    @Test
    fun `b - chip Moi chi con viec state new`() {
        val (vm, _, _) = vm()
        vm.selectFilter(CsJobFilter.NEW)
        assertEquals(CsJobFilter.NEW, vm.filter)
        assertEquals(listOf(414, 410, 412), vm.visibleJobs.map { it.id })
        assertEquals(
            listOf(JobState.NEW),
            vm.visibleJobs.map { it.state }.distinct(),
            "không được lẫn việc đã gán",
        )
    }

    // ------------------------------------------------------------------ (c) lọc "Đã gán"

    @Test
    fun `c - chip Da gan chi con viec state assigned`() {
        val (vm, _, _) = vm()
        vm.selectFilter(CsJobFilter.ASSIGNED)
        assertEquals(listOf(411, 413), vm.visibleJobs.map { it.id })
        assertEquals(listOf(JobState.ASSIGNED), vm.visibleJobs.map { it.state }.distinct())
    }

    // ------------------------------------------------------------------ (d) việc đang mở

    @Test
    fun `d - doi chip khong lam mat viec dang mo o pane phai`() {
        val (vm, _, _) = vm()
        vm.open(413) // việc CŨ (hôm qua) — nằm cuối danh sách
        assertEquals(413, vm.openedId)
        assertNotNull(vm.detailJob())

        // Sang "Mới": việc 413 (đã gán) không còn trong danh sách hiển thị…
        vm.selectFilter(CsJobFilter.NEW)
        assertEquals(CsJobFilter.NEW, vm.filter)
        // …nhưng pane phải KHÔNG được mất việc đang mở.
        assertEquals(413, vm.openedId, "đổi bộ lọc không được đóng/mở việc khác")
        assertEquals(413, vm.detailJob()?.id, "pane chi tiết vẫn là việc người dùng đang xem")

        // Về "Tất cả" thì việc đó xuất hiện lại đúng vị trí cũ.
        vm.selectFilter(CsJobFilter.ALL)
        assertEquals(listOf(414, 411, 410, 413, 412), vm.visibleJobs.map { it.id })
        assertEquals(413, vm.detailJob()?.id)
    }

    @Test
    fun `d2 - doi chip roi quay lai Tat ca thi giao dien khong doi viec dang mo`() {
        val (vm, _, _) = vm()
        vm.open(410)
        vm.selectFilter(CsJobFilter.ASSIGNED)
        assertEquals(410, vm.openedId)
        vm.selectFilter(CsJobFilter.ALL)
        assertEquals(410, vm.openedId)
        assertNotNull(vm.detailJob())
        assertNull(vm.actionError)
    }

    // ------------------------------------------------------------------ phụ: đếm chip

    @Test
    fun `so dem tren chip khop voi so viec loc duoc`() {
        val (vm, _, _) = vm()
        CsJobFilter.values().forEach { option ->
            vm.selectFilter(option)
            assertEquals(
                option.countIn(vm.jobs),
                vm.visibleJobs.size,
                "chip ${option.label} phải hiện đúng ${option.countIn(vm.jobs)} việc",
            )
        }
    }
}
