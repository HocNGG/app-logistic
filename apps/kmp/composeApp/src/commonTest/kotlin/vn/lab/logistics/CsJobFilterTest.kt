package vn.lab.logistics

import vn.lab.logistics.core.model.CsJob
import vn.lab.logistics.core.model.JobState
import vn.lab.logistics.feature.cs.CsJobFilter
import vn.lab.logistics.feature.cs.countIn
import vn.lab.logistics.feature.cs.filterAndSort
import vn.lab.logistics.feature.cs.sortNewestFirst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * =====================================================================================
 * THỨ TỰ + BỘ LỌC của danh sách việc cần gán tài xế — test hàm THUẦN, chạy offline.
 *
 * Hai bất biến khách đã chốt (yêu cầu 2026-11-03):
 *  1. **Đơn tạo mới hơn thì lên đầu** — theo `createDate` của server (KHÔNG theo `id`: `id` của
 *     Odoo chỉ *thường* trùng thứ tự tạo, sau import/backfill là sai);
 *  2. chip lọc **Tất cả · Mới · Đã gán** lọc theo `state` của server, số trên chip cộng lại = tổng.
 *
 * Bốn ca biên phải khoá: mốc vắng (server cũ) xuống cuối · hai việc cùng giây thì `id` lớn trước ·
 * danh sách rỗng không nổ · thứ tự ỔN ĐỊNH giữa hai lần sắp (gọi 2 lần ra cùng kết quả).
 * =====================================================================================
 */
class CsJobFilterTest {

    private fun job(
        id: Int,
        state: String,
        createDate: String? = null,
    ) = CsJob(
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

    // Server trả lộn xộn cả `id` lẫn thời gian — đúng cái gây ra yêu cầu này.
    // id 7 tạo SỚM NHẤT dù id lớn nhất ⇒ nếu ai đó sắp theo `id`, test này đỏ ngay.
    private val jobs = listOf(
        job(3, JobState.ASSIGNED, "2026-11-03 06:10:00"),
        job(7, JobState.NEW, "2026-11-02 21:05:00"),
        job(1, JobState.NEW, "2026-11-03 09:45:00"),
        job(9, JobState.ASSIGNED, "2026-11-03 08:20:00"),
        job(5, JobState.NEW, "2026-11-01 15:00:00"),
    )

    // ------------------------------------------------------------------ 1. sắp xếp

    @Test
    fun `viec tao moi nhat theo createDate xep dau`() {
        // 1 (09:45) → 9 (08:20) → 3 (06:10) → 7 (21:05 hôm trước) → 5 (15:00 hôm kia)
        assertEquals(listOf(1, 9, 3, 7, 5), sortNewestFirst(jobs).map { it.id })
    }

    @Test
    fun `khoa createDate vang thi xuong cuoi, khong chen len truoc`() {
        val mixed = listOf(
            job(80, JobState.NEW, null),                          // vắng mốc
            job(81, JobState.NEW, "2026-10-30 08:00:00"),          // cũ nhưng CÓ mốc
            job(82, JobState.NEW, null),                          // vắng mốc
            job(83, JobState.NEW, "2026-11-03 07:00:00"),          // mới nhất
        )
        // Hai việc vắng mốc xuống cuối, giữa chúng sắp theo id giảm dần (ổn định).
        assertEquals(listOf(83, 81, 82, 80), sortNewestFirst(mixed).map { it.id })
    }

    @Test
    fun `hai viec cung giay thi id lon hon dung truoc`() {
        val sameSecond = listOf(
            job(21, JobState.NEW, "2026-11-03 10:30:00"),
            job(24, JobState.NEW, "2026-11-03 10:30:00"),
            job(22, JobState.NEW, "2026-11-03 10:30:00"),
        )
        assertEquals(listOf(24, 22, 21), sortNewestFirst(sameSecond).map { it.id })
    }

    @Test
    fun `server cu khong tra createDate thi van xep theo id nhu truoc`() {
        // Không mốc nào ⇒ khoá 1 bằng nhau, khoá 2 (`id` giảm) quyết định — hành vi cũ giữ nguyên.
        assertEquals(listOf(9, 7, 5, 3, 1), sortNewestFirst(jobs.map { it.copy(createDate = null) }).map { it.id })
    }

    @Test
    fun `sap hai lan ra cung mot thu tu`() {
        assertEquals(sortNewestFirst(jobs), sortNewestFirst(sortNewestFirst(jobs)))
    }

    // ------------------------------------------------------------------ 2. bộ lọc

    @Test
    fun `chip Tat ca giu du viec va xep moi nhat truoc`() {
        assertEquals(listOf(1, 9, 3, 7, 5), filterAndSort(jobs, CsJobFilter.ALL).map { it.id })
    }

    @Test
    fun `chip Moi chi con viec chua gan, moi nhat truoc`() {
        assertEquals(listOf(1, 7, 5), filterAndSort(jobs, CsJobFilter.NEW).map { it.id })
    }

    @Test
    fun `chip Da gan chi con viec da gan, moi nhat truoc`() {
        assertEquals(listOf(9, 3), filterAndSort(jobs, CsJobFilter.ASSIGNED).map { it.id })
    }

    @Test
    fun `so dem tren chip cong lai bang tong`() {
        val moi = CsJobFilter.NEW.countIn(jobs)
        val daGan = CsJobFilter.ASSIGNED.countIn(jobs)
        assertEquals(3, moi)
        assertEquals(2, daGan)
        assertEquals(CsJobFilter.ALL.countIn(jobs), moi + daGan)
    }

    @Test
    fun `danh sach rong thi loc ra rong, khong loi`() {
        assertTrue(filterAndSort(emptyList(), CsJobFilter.NEW).isEmpty())
        assertEquals(0, CsJobFilter.ALL.countIn(emptyList()))
    }
}
