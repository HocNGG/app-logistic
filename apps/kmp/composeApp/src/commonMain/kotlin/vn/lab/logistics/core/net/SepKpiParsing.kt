package vn.lab.logistics.core.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import vn.lab.logistics.core.model.KpiRange
import vn.lab.logistics.core.model.SepKpi
import vn.lab.logistics.core.model.SepKpiCounters
import vn.lab.logistics.core.model.SepRecentJob
import vn.lab.logistics.core.model.SepStateSlice
import vn.lab.logistics.core.model.SepTrendPoint

/**
 * =====================================================================================
 * PARSE `GET /lab/api/v1/sep/kpi?range=today|7d|30d` — dashboard của sếp.
 *
 * Hợp đồng (chốt với BE-01, mọi trường đều CÓ THỂ VẮNG):
 * ```
 * {range, kpi:{pendingAssign,enRoute,pickedUpToday,confirmedToday,avgLoadPct},
 *  trend:[{date,created,picked,confirmed}], byState:[{state,label,count}],
 *  recent:[{id,ref,shop,stateLabel,driverName,windowFrom,updatedAt}]}
 * ```
 *
 * VÌ SAO MỌI THỨ ĐỀU NULLABLE: BE đang làm endpoint này song song với app. Một field vắng mặt
 * phải cho ra "chưa có dữ liệu" trên UI, KHÔNG được ném lỗi (màn sếp trắng vì thiếu 1 con số là
 * lỗi demo) và KHÔNG được tự điền 0 (số 0 là một khẳng định nghiệp vụ sai).
 *
 * `data: null` (envelope `ok:true` mà chưa có số liệu) ⇒ [SepKpi] rỗng ⇒ UI hiện empty state
 * "Chưa có dữ liệu" cho từng khối, không hiện chart 0 (spec §4).
 * =====================================================================================
 */

@Serializable
internal data class SepKpiCountersDto(
    val pendingAssign: Int? = null,
    val enRoute: Int? = null,
    val pickedUpToday: Int? = null,
    val confirmedToday: Int? = null,
    val avgLoadPct: Double? = null,
) {
    fun toModel(): SepKpiCounters = SepKpiCounters(
        pendingAssign = pendingAssign,
        enRoute = enRoute,
        pickedUpToday = pickedUpToday,
        confirmedToday = confirmedToday,
        avgLoadPct = avgLoadPct,
    )
}

@Serializable
internal data class SepTrendDto(
    val date: String? = null,
    val created: Int? = null,
    val picked: Int? = null,
    val confirmed: Int? = null,
)

@Serializable
internal data class SepStateDto(
    val state: String? = null,
    val label: String? = null,
    val count: Int? = null,
)

@Serializable
internal data class SepRecentDto(
    val id: Int = 0,
    val ref: String? = null,
    val shop: String? = null,
    val stateLabel: String? = null,
    val driverName: String? = null,
    val windowFrom: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class SepKpiPayloadDto(
    val range: String? = null,
    val kpi: SepKpiCountersDto? = null,
    val trend: List<SepTrendDto> = emptyList(),
    val byState: List<SepStateDto> = emptyList(),
    val recent: List<SepRecentDto> = emptyList(),
)

/** Parse payload dashboard. Không ném lỗi vì thiếu field; chỉ ném khi envelope là lỗi thật. */
fun parseSepKpiPayload(body: String, status: Int = 200): SepKpi {
    val data = LabPayload.data(status, body)
    // `data: null` = chưa có số liệu ⇒ DTO rỗng, KHÔNG phải lỗi (UI có empty state riêng).
    if (data is JsonNull) return SepKpi()
    if (data !is JsonObject) {
        throw ApiException.Server("Dữ liệu dashboard của sếp không đúng định dạng.")
    }
    val dto = AppJson.decodeFromString(SepKpiPayloadDto.serializer(), data.toString())
    return SepKpi(
        range = KpiRange.fromApiValue(dto.range),
        counters = dto.kpi?.toModel(),
        // Bỏ điểm trend không có `date`: trục thời gian mà thiếu nhãn ngày là vô nghĩa.
        trend = dto.trend.mapNotNull { point ->
            val date = point.date.orNullIfBlank() ?: return@mapNotNull null
            SepTrendPoint(
                date = date,
                created = point.created ?: 0,
                picked = point.picked ?: 0,
                confirmed = point.confirmed ?: 0,
            )
        },
        // Bỏ lát donut không có nhãn chữ: spec §5 đòi "chip/lát LUÔN có chữ", không chỉ màu.
        byState = dto.byState.mapNotNull { slice ->
            val count = slice.count ?: return@mapNotNull null
            val label = slice.label.orNullIfBlank() ?: slice.state.orNullIfBlank() ?: return@mapNotNull null
            SepStateSlice(
                state = slice.state.orNullIfBlank() ?: label,
                label = label,
                count = count,
            )
        },
        recent = dto.recent.mapNotNull { row ->
            if (row.id <= 0) return@mapNotNull null
            SepRecentJob(
                id = row.id,
                ref = row.ref.orNullIfBlank() ?: "#${row.id}",
                shop = row.shop.orNullIfBlank() ?: "Shop chưa có tên",
                stateLabel = row.stateLabel.orNullIfBlank() ?: "Chưa rõ trạng thái",
                driverName = row.driverName.orNullIfBlank(),
                windowFrom = row.windowFrom.orNullIfBlank(),
                updatedAt = row.updatedAt.orNullIfBlank(),
            )
        }.take(MAX_RECENT_ROWS),
    )
}

/** Bảng "10 việc gần nhất" (spec §6) — server trả nhiều hơn thì cắt, không cuộn vô hạn. */
private const val MAX_RECENT_ROWS = 10
