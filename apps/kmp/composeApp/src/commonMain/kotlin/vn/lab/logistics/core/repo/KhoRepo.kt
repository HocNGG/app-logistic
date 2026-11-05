package vn.lab.logistics.core.repo

import vn.lab.logistics.core.model.KienCuaViec
import vn.lab.logistics.core.model.KhoJob
import vn.lab.logistics.core.model.KhoXuatChuyen
import vn.lab.logistics.core.model.ReceiveOutcome
import vn.lab.logistics.core.model.ScanResult
import vn.lab.logistics.core.model.XuatKetQua

/**
 * Hợp đồng của màn **Kho — nhận hàng** (2 endpoint, hợp đồng đã chốt với BE-01):
 *
 * | Hàm | Endpoint | Ý nghĩa |
 * |---|---|---|
 * | [choNhan] | `GET /lab/api/v1/kho/cho-nhan` | việc đã `picked_up` đang chờ kho nhận |
 * | [nhan] | `POST /lab/api/v1/kho/jobs/<id>/nhan` | nhận hàng, server sinh phiếu nhập kho |
 * | [kienCuaViec] | `GET /lab/api/v1/kho/jobs/<id>/kien` | kiện của việc đã nhận + tiến độ quét |
 * | [quet] | `POST /lab/api/v1/scan` | quét tem kiện (mốc `nhan` hoặc `xuat`) |
 *
 * Màn hình chỉ phụ thuộc interface này; hai impl được chọn ở nơi lắp ráp (`App.kt`):
 *  - [vn.lab.logistics.core.repo.mock.MockKhoRepo] — dữ liệu MẪU;
 *  - [vn.lab.logistics.core.repo.real.RealKhoRepo] — gọi THẬT.
 *
 * ⚠️ [nhan] là thao tác GHI có sinh chứng từ (`stock.picking` nhập kho — Luật 4 REA): app
 * KHÔNG retry tự động, và KHÔNG tự bịa số phiếu — số phiếu do server trả trong [ReceiveOutcome].
 *
 * ⚠️ [quet] cũng là thao tác GHI (mỗi lần quét = 1 dòng `lab.scan.event`) nhưng **idempotent**
 * theo `clientUuid`: gửi lặp cùng uuid thì server trả lại kết quả cũ (`trung = true`), không tạo
 * dòng mới. App vì vậy KHÔNG tự đổi uuid khi người dùng bấm lại cùng một mã.
 */
interface KhoRepo {

    /** Việc đang chờ kho nhận. */
    suspend fun choNhan(): Result<List<KhoJob>>

    /** Nhận hàng cho một việc. */
    suspend fun nhan(jobId: Int): Result<ReceiveOutcome>

    /**
     * Danh sách KIỆN của một việc đã nhận + tiến độ quét
     * (`GET /lab/api/v1/kho/jobs/<id>/kien` — BE ship 2026-11-03).
     *
     * Việc chưa nhận ⇒ [KienCuaViec.chuaNhan] = true (server trả `soKien = 0`), KHÔNG phải lỗi.
     * Trả `Result.failure` chỉ khi mất mạng/hết phiên/không có quyền.
     */
    suspend fun kienCuaViec(jobId: Int): Result<KienCuaViec>

    /**
     * Quét tem kiện.
     *
     * @param ma mã kiện (`KIEN/26/…`) hoặc mã tem xuất (`XUAT/26/…`) — máy quét không phân biệt.
     * @param moc `"nhan"` (kho nhận) hoặc `"xuat"` (xuất chuyến).
     * @param clientUuid UUID v4 do app sinh CHO MỖI LẦN QUÉT (chống trùng).
     */
    suspend fun quet(
        ma: String,
        moc: String,
        clientUuid: String,
        ghiChu: String? = null,
    ): Result<ScanResult>

    // ------------------------------------------------------------------ XUẤT KHO THEO CHUYẾN

    /** Chuyến còn kiện chưa xuất (`GET /lab/api/v1/kho/cho-xuat`). */
    suspend fun choXuat(): Result<List<KhoXuatChuyen>>

    /** Chi tiết một chuyến (`GET …/kho/cho-xuat?chuyenId=<id>`). */
    suspend fun chiTietXuat(chuyenId: Int): Result<KhoXuatChuyen>

    /**
     * Xác nhận xuất kho (`POST …/kho/chuyen/<id>/xuat`).
     *
     * Trả [XuatKetQua]: `DaXuat` (đủ kiện, hoặc thiếu nhưng có lý do) hoặc `ConKienChuaQuet`
     * (server chặn 409 — UI mời nhập lý do). **Không phải** `Result.failure` cho nhánh nghiệp vụ
     * thứ hai: đó là đường quay về của luồng, không phải lỗi hệ thống.
     */
    suspend fun xacNhanXuat(
        chuyenId: Int,
        clientUuid: String,
        lyDoThieu: String? = null,
    ): Result<XuatKetQua>
}
