package vn.lab.logistics.core.model

/**
 * Một việc cần gán tài xế — hình dạng ĐÚNG theo hợp đồng `GET /lab/api/v1/cs/jobs`:
 *
 * ```json
 * {"items":[{"id":..,"ref":"LH/26/0005","shop":"…","address":"…","contactPhone":"…",
 *            "windowFrom":"08:30","windowTo":"10:00","state":"new","stateLabel":"Mới",
 *            "expectedPackages":4,"driverName":"Trần Văn Hùng"}],
 *  "drivers":[{"id":12,"name":"Trần Văn Hùng","login":"taixe01"}]}
 * ```
 *
 * `drivers` chỉ gồm nhân viên CÓ nhóm tài xế, và mỗi người kèm `login` để trưởng nhóm đối chiếu
 * "máy này đang đăng nhập bằng tài khoản nào" (hợp đồng chốt với BE-01).
 *
 * Bốn trường có thể VẮNG (nullable đúng chỗ, app không được bịa số):
 *  - `expectedPackages` — chưa có số kiện dự kiến ⇒ UI hiện "Chưa rõ số kiện" (không hiện 0);
 *  - `driverName`       — việc CHƯA gán tài xế ⇒ UI hiện "Chưa gán tài xế";
 *  - `contactPhone`     — shop chưa có số ⇒ UI hiện "Chưa có số điện thoại";
 *  - `windowFrom/To`    — chưa hẹn giờ (server có thể trả `"-"` như `driver/jobs`) ⇒
 *    "Chưa hẹn giờ".
 *
 * App KHÔNG tự đổi `state` sau khi gán: trạng thái mới luôn do server trả
 * (`POST /lab/api/v1/cs/jobs/<id>/assign` → `{state,stateLabel,driverName}`).
 */
data class CsJob(
    val id: Int,
    val ref: String,
    val shop: String,
    val address: String,
    val contactPhone: String?,
    /** Chuỗi `HH:mm` do backend định dạng sẵn (app không parse Datetime). */
    val windowFrom: String?,
    val windowTo: String?,
    /** Mã state của `lab.yeu.cau.lay.hang` (7 mã — xem [JobState]). */
    val state: String,
    /** Nhãn tiếng Việt của server; `null` ⇒ app fallback `stateLabelOf(state)`. */
    val stateLabel: String? = null,
    val expectedPackages: Int? = null,
    val driverName: String? = null,
    /**
     * Lúc TẠO việc, ISO `yyyy-MM-dd HH:mm:ss` **UTC** do server trả (khoá `createDate` của
     * `/cs/jobs` — BE ship 2026-11-03). `null` = server chưa trả trường này.
     *
     * Vì sao cần trên app (yêu cầu khách 2026-11-03): `id` của Odoo chỉ tăng dần theo lúc tạo nên
     * *thường* trùng thứ tự, nhưng không phải lúc nào cũng vậy (import/backfill/sequence đặt lại).
     * Muốn nói "đơn tạo mới hơn thì lên đầu" một cách trung thực thì phải có mốc thời gian thật.
     * Chuỗi ISO cùng độ dài ⇒ so từ điển = so thời gian, nên app KHÔNG parse Date
     * (commonMain không có `java.time`).
     */
    val createDate: String? = null,
)

/**
 * Một tài xế trong ô chọn tài xế của màn CS (`drivers` của cùng payload).
 *
 * @param login tên đăng nhập của tài khoản tài xế — hiện cạnh tên trong ô chọn
 *        ("Trần Văn Hùng · taixe01") để trưởng nhóm biết máy nào dùng tài khoản nào.
 *        **Nullable** vì server cũ chưa trả trường này: khi vắng, UI chỉ hiện tên và KHÔNG
 *        hiện dấu "·" treo.
 */
data class CsDriver(
    val id: Int,
    val name: String,
    val login: String? = null,
)

/** Toàn bộ `data` của `GET /lab/api/v1/cs/jobs`. */
data class CsJobsPayload(
    val items: List<CsJob> = emptyList(),
    val drivers: List<CsDriver> = emptyList(),
)

/**
 * `data` server trả sau `POST /lab/api/v1/cs/jobs/<id>/assign`.
 *
 * Ba trường đều nullable vì server cũ có thể trả thiếu — UI tự fallback:
 * `state` → `assigned`, `stateLabel` → "Đã gán tài xế", `driverName` → tên vừa chọn.
 */
data class AssignOutcome(
    val state: String?,
    val stateLabel: String?,
    val driverName: String?,
)
