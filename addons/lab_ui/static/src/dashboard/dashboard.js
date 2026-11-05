/** @odoo-module **/
/* =====================================================================================
 * lab_ui/static/src/dashboard/dashboard.js — "Bảng điều khiển" (client action OWL 2, Odoo 17).
 *
 * Người viết: FE-01. Biên giới: addons/lab_ui/static/** (AGENTS.md Luật 10).
 * Mẫu bố cục: docs/review/refs/ref-01-olive-overview.png (+ ref-03) và
 * agents/_shared/skills/ui-dashboard-app-screens.md §3 (KPI) §4 (chart) §5 (bảng/chip) §6 (bố cục).
 * Token: agents/_shared/skills/ui-modern-industrial.md (không có màu nào ngoài token).
 *
 * ĐỐI CHIẾU SOURCE THẬT (Odoo 17.0):
 *   • addons/web/static/src/webclient/actions/action_service.js → _executeClientAction():
 *     client action lấy component bằng registry.category("actions").get(tag); component nhận
 *     props {action, actionId}. Vì vậy đăng ký bằng `registry.category("actions").add(...)`.
 *   • addons/web/static/src/core/orm_service.js → `call(model, method, args = [], kwargs = {})`.
 *     Đúng chữ ký `orm.call(model, method, [range])` theo hợp đồng nhiệm vụ.
 *
 * DỮ LIỆU: `lab.yeu.cau.lay.hang.lab_dashboard_kpi(range)` — hợp đồng trả Y HỆT `data` của
 * `GET /lab/api/v1/sep/kpi` (addons/lab_api/controllers/sep.py):
 *   { range, kpi:{pendingAssign,enRoute,pickedUpToday,confirmedToday,avgLoadPct},
 *     trend:[{date,created,picked,confirmed}], byState:[{state,label,count}],
 *     recent:[{id,ref,shop,stateLabel,driverName,windowFrom,updatedAt}] }
 *
 * ⚠️ CHỜ BE-01: method `lab_dashboard_kpi` chưa tồn tại trên model (grep 2026-10-29 = 0 kết quả).
 *    Code giữ đúng hợp đồng; khi RPC lỗi thì mỗi khối hiện khối lỗi + nút "Thử lại"
 *    — KHÔNG hiện số 0 giả (skill §3: "cấm ↑10.5% tự bịa").
 *
 * KHÔNG BỊA SỐ: thiếu/None ⇒ "Chưa có dữ liệu". Chart toàn 0 ⇒ hiện empty state, không vẽ
 *    đường phẳng 0 (skill §4).
 * ===================================================================================== */

import { Component, onWillStart, useState } from "@odoo/owl";
import { registry } from "@web/core/registry";
import { useService } from "@web/core/utils/hooks";

/** Model mang method tổng hợp + model mở form khi bấm 1 dòng của bảng. */
const MODEL = "lab.yeu.cau.lay.hang";

/** ⚠️ chờ BE method lab_dashboard_kpi (hợp đồng: xem docstring đầu file). */
const METHOD_KPI = "lab_dashboard_kpi";

/** 3 kỳ lọc — đúng 3 giá trị mà endpoint `sep/kpi` chấp nhận (RANGE_NGAY). */
const CAC_KY = [
    { value: "today", nhan: "Hôm nay" },
    { value: "7d", nhan: "7 ngày" },
    { value: "30d", nhan: "30 ngày" },
];
const KY_MAC_DINH = "7d";

/**
 * Nhãn 7 trạng thái — CHÉP NGUYÊN VĂN từ Selection của `lab.yeu.cau.lay.hang`
 * (addons/lab_laixe/models/lab_yeu_cau_lay_hang.py, field `state`).
 * Dùng làm (a) nhãn dự phòng khi `recent` thiếu `stateLabel`, (b) tên trong chú giải.
 */
const NHAN_TRANG_THAI = {
    new: "Mới",
    assigned: "Đã gán tài xế",
    en_route: "Đang đến lấy",
    picked_up: "Đã lấy được hàng",
    confirmed: "Đã về kho",
    cancelled: "Đã huỷ",
    failed: "Lấy hàng thất bại",
};

/**
 * Hợp đồng `recent` chỉ trả `stateLabel` (KHÔNG trả mã `state`) ⇒ để chip có màu đúng
 * trạng thái, tra ngược nhãn → mã. Nếu BE bổ sung `state` vào `recent` thì nhánh
 * `viec.state` trong `_maTrangThai()` tự thắng, bảng này thành dự phòng.
 */
const MA_THEO_NHAN = {};
for (const [ma, nhan] of Object.entries(NHAN_TRANG_THAI)) {
    MA_THEO_NHAN[nhan] = ma;
}

/**
 * 4 nhóm của vòng donut — gom 7 trạng thái về 4 nhóm, mỗi nhóm MỘT bậc của thang forest
 * (skill §4: cấm chart cầu vồng > 5 màu; tối đa 4 bậc forest + hổ phách cho ngưỡng).
 * "Lấy hàng thất bại" và "Đã huỷ" nằm NGOÀI vòng, hiện bằng chip chữ + số (không tô màu
 * vào vòng) — vẫn nhìn thấy, không bị giấu.
 */
const NHOM_DONUT = [
    { khoa: "ve_kho", nhan: "Đã về kho", states: ["confirmed"], bac: 1 },
    { khoa: "da_lay", nhan: "Đã lấy được hàng", states: ["picked_up"], bac: 2 },
    { khoa: "dang_lay", nhan: "Đang đi lấy", states: ["assigned", "en_route"], bac: 3 },
    { khoa: "cho_gan", nhan: "Chờ gán", states: ["new"], bac: 4 },
];

/** Trạng thái nằm ngoài vòng donut (xem chú thích NHOM_DONUT). */
const NGOAI_VONG = ["failed", "cancelled"];

/**
 * Ngưỡng tải trung bình — LẤY TỪ NGHIỆP VỤ THẬT, không tự nghĩ ra:
 * addons/lab_chuyen/views/lab_chuyen_views.xml dùng `decoration-danger="ti_le_tai > 100"` và
 * `decoration-warning="ti_le_tai > 90 and ti_le_tai <= 100"`. Dashboard dùng đúng 2 mốc đó.
 */
const NGUONG_TAI = { vuot: 100, sat: 90 };

/** Khung vẽ chart (đơn vị px trong viewBox; chart co giãn bằng CSS, không đổi khi resize). */
const KHUNG = { rong: 760, cao: 260, trai: 44, phai: 744, tren: 16, duoi: 226 };

/** Hình học donut. */
const DONUT = { cx: 90, cy: 90, r: 64 };

// =====================================================================================
// Hàm phụ (không phụ thuộc component) — giữ getter của component ngắn và dễ đọc
// =====================================================================================

/** Định dạng số theo vi-VN (dấu chấm ngăn nghìn) — số liệu trong bảng/chart dùng chung. */
function soVN(giaTri) {
    if (giaTri === null || giaTri === undefined || Number.isNaN(giaTri)) {
        return "";
    }
    return new Intl.NumberFormat("vi-VN").format(giaTri);
}

/** Số nguyên dương, hoặc 0 nếu dữ liệu không dùng được. */
function soDem(giaTri) {
    const so = Number(giaTri);
    return Number.isFinite(so) && so > 0 ? so : 0;
}

/**
 * "YYYY-MM-DD HH:mm" -> "dd/MM/yyyy HH:mm".
 * Cắt chuỗi, KHÔNG dùng `new Date()`: BE đã đổi sang giờ người dùng, parse lại bằng JS sẽ
 * cộng/trừ múi giờ lần nữa và lệch đúng 7 tiếng ở VN.
 */
function ngayDayDu(chuoi) {
    if (!chuoi) {
        return "";
    }
    const [phanNgay, phanGio] = String(chuoi).trim().split(/\s+/);
    const phan = (phanNgay || "").split("-");
    if (phan.length !== 3) {
        return String(chuoi);
    }
    const [nam, thang, ngay] = phan;
    return `${ngay}/${thang}/${nam}${phanGio ? " " + phanGio : ""}`;
}

/** "YYYY-MM-DD" -> "dd/MM" (nhãn trục X của chart). */
function ngayNgan(chuoi) {
    if (!chuoi) {
        return "";
    }
    const phan = String(chuoi).split("-");
    return phan.length === 3 ? `${phan[2]}/${phan[1]}` : String(chuoi);
}

/** Chia trục Y thành 3 khoảng đều, mốc là số nguyên để nhãn không bao giờ là 1,3333. */
function trucY(cacSo) {
    const lonNhat = Math.max(0, ...cacSo.filter((so) => Number.isFinite(so)));
    const buoc = Math.max(1, Math.ceil(lonNhat / 3));
    const dinh = buoc * 3;
    return { dinh, moc: [0, buoc, buoc * 2, dinh] };
}

/** Nối danh sách điểm thành chuỗi lệnh SVG. */
function duongDi(cacDiem) {
    return cacDiem
        .map((diem, i) => `${i === 0 ? "M" : "L"}${diem.x.toFixed(1)},${diem.y.toFixed(1)}`)
        .join(" ");
}

// =====================================================================================
// Component
// =====================================================================================
export class LabDashboard extends Component {
    static template = "lab_ui.Dashboard";

    setup() {
        this.orm = useService("orm");
        this.action = useService("action");
        this.state = useState({
            ky: KY_MAC_DINH,
            trangThai: "loading", // loading | ready | error
            loi: "",
            duLieu: null,
        });
        onWillStart(() => this.tai());
    }

    // ---------------------------------------------------------------------------------
    // Tải dữ liệu
    // ---------------------------------------------------------------------------------
    /**
     * Gọi RPC lấy số liệu cho `ky` (mặc định: kỳ đang chọn).
     * Không bao giờ throw ra ngoài: mọi lỗi biến thành khối lỗi của từng khối.
     */
    async tai(kyMoi) {
        const ky = CAC_KY.some((k) => k.value === kyMoi) ? kyMoi : this.state.ky;
        this.state.ky = ky;
        this.state.trangThai = "loading";
        this.state.loi = "";
        try {
            const traVe = await this.orm.call(MODEL, METHOD_KPI, [ky]);
            this.state.duLieu = this._chuanHoa(traVe, ky);
            this.state.trangThai = "ready";
        } catch (loi) {
            this.state.duLieu = null;
            this.state.loi = this._thongDiepLoi(loi);
            this.state.trangThai = "error";
        }
    }

    /** Bấm chip kỳ: đổi kỳ rồi tải lại (không tải lại nếu bấm đúng kỳ đang chọn). */
    chonKy(kyMoi) {
        if (kyMoi === this.state.ky) {
            return;
        }
        this.tai(kyMoi);
    }

    /**
     * Danh sách kỳ cho 3 chip lọc (template `t-foreach="cacKy"`).
     * ⚠️ Trước đây template lặp `cacKy` nhưng class KHÔNG expose tên này ⇒ OWL ném
     * "Invalid loop expression: undefined is not iterable" và cả action chết ngay khi mở.
     */
    get cacKy() {
        return CAC_KY;
    }

    /** Nhãn tiếng Việt của kỳ đang chọn (dùng ở phụ đề toolbar). */
    get nhanKy() {
        const ky = CAC_KY.find((k) => k.value === this.state.ky);
        return ky ? ky.nhan : "";
    }

    /**
     * Chuẩn hoá payload về đúng 5 khoá của hợp đồng, chấp nhận 2 dạng bọc:
     *   • BE-01 trả thẳng payload (đúng nhiệm vụ);
     *   • BE-01 trả `{"data": payload}` nếu dùng lại helper `ok()` của lab_api.
     */
    _chuanHoa(traVe, ky) {
        let duLieu = traVe || {};
        if (!duLieu.kpi && duLieu.data) {
            duLieu = duLieu.data;
        }
        return {
            range: duLieu.range || ky,
            kpi: duLieu.kpi || null,
            trend: Array.isArray(duLieu.trend) ? duLieu.trend : [],
            byState: Array.isArray(duLieu.byState) ? duLieu.byState : [],
            recent: Array.isArray(duLieu.recent) ? duLieu.recent : [],
        };
    }

    /** Rút gọn lỗi RPC thành 1 dòng, giữ tiếng Việt ở đầu câu (Luật 6). */
    _thongDiepLoi(loi) {
        const goc = (loi && (loi.message || (loi.data && loi.data.message))) || String(loi || "");
        const dong = String(goc).split("\n")[0].trim();
        return dong.length > 180 ? `${dong.slice(0, 177)}…` : dong;
    }

    // ---------------------------------------------------------------------------------
    // KHỐI 1 — 4 thẻ KPI (skill ui-dashboard-app-screens §3)
    // ---------------------------------------------------------------------------------
    /**
     * 4 thẻ theo đúng §6: Việc chờ gán · Đang đi lấy · Đã về kho hôm nay · Tải chuyến TB.
     * Δ (chip so sánh) CHỈ đặt ở thẻ có chuỗi thời gian thật (trend) — và chỉ khi trend ≥ 2
     * điểm. Ba thẻ còn lại là số tồn tại thời điểm: không có mốc so sánh ⇒ không chip Δ.
     */
    get theKpi() {
        const kpi = this.state.duLieu && this.state.duLieu.kpi;
        const trend = (this.state.duLieu && this.state.duLieu.trend) || [];
        const cuoi = trend.length ? trend[trend.length - 1] : null;
        const truoc = trend.length >= 2 ? trend[trend.length - 2] : null;
        const taiTho = kpi ? kpi.avgLoadPct : null;
        const tai =
            taiTho === null || taiTho === undefined || !Number.isFinite(Number(taiTho))
                ? null
                : Number(taiTho);

        return [
            {
                khoa: "pendingAssign",
                nhan: "Việc chờ gán",
                so: this._soKpi(kpi, "pendingAssign"),
                donVi: "việc",
                delta: null,
                canhBao: null,
            },
            {
                khoa: "enRoute",
                nhan: "Đang đi lấy hàng",
                so: this._soKpi(kpi, "enRoute"),
                donVi: "việc",
                delta: null,
                canhBao: null,
            },
            {
                khoa: "confirmedToday",
                nhan: "Đã về kho hôm nay",
                so: this._soKpi(kpi, "confirmedToday"),
                donVi: "việc",
                delta: this._deltaNgay(cuoi, truoc, "confirmed"),
                canhBao: null,
            },
            {
                khoa: "avgLoadPct",
                nhan: "Tải chuyến trung bình",
                so: this._soKpi(kpi, "avgLoadPct"),
                donVi: "%",
                delta: null,
                // Ngưỡng thật của lab_chuyen: > 100% = quá tải, > 90% = sát ngưỡng.
                canhBao:
                    tai === null
                        ? null
                        : tai > NGUONG_TAI.vuot
                          ? "vượt"
                          : tai > NGUONG_TAI.sat
                            ? "sat"
                            : null,
            },
        ];
    }

    /** Giá trị KPI đã định dạng, hoặc null khi hợp đồng không có số (⇒ "Chưa có dữ liệu"). */
    _soKpi(kpi, ten) {
        if (!kpi || kpi[ten] === null || kpi[ten] === undefined) {
            return null;
        }
        const so = Number(kpi[ten]);
        return Number.isFinite(so) ? soVN(so) : null;
    }

    /** Chip Δ so với điểm cuối trước đó của chuỗi trend (chỉ khi có ≥ 2 điểm). */
    _deltaNgay(cuoi, truoc, ten) {
        if (!cuoi || !truoc) {
            return null;
        }
        const homNay = soDem(cuoi[ten]);
        const homQua = soDem(truoc[ten]);
        if (homQua === 0) {
            // Không chia cho 0: so sánh tuyệt đối, không bịa phần trăm.
            return homNay === 0 ? null : { huong: "len", nhan: `+${soVN(homNay)} so với hôm qua` };
        }
        const lech = homNay - homQua;
        if (lech === 0) {
            return { huong: "bang", nhan: "0% so với hôm qua" };
        }
        const phanTram = Math.abs((lech / homQua) * 100)
            .toFixed(1)
            .replace(".", ",");
        return {
            huong: lech > 0 ? "len" : "xuong",
            nhan: `${lech > 0 ? "+" : "−"}${phanTram}% so với hôm qua`,
        };
    }

    // ---------------------------------------------------------------------------------
    // KHỐI 2 — Chart xu hướng (area/line, vẽ bằng SVG trong template; KHÔNG thư viện ngoài)
    // ---------------------------------------------------------------------------------
    get bieuDo() {
        const trend = (this.state.duLieu && this.state.duLieu.trend) || [];
        if (!trend.length) {
            return null;
        }
        const diem = trend.map((d) => ({
            date: d.date,
            created: soDem(d.created),
            picked: soDem(d.picked),
            confirmed: soDem(d.confirmed),
        }));
        const coSoLieu = diem.some((d) => d.created > 0 || d.confirmed > 0);
        const { dinh, moc } = trucY(diem.flatMap((d) => [d.created, d.confirmed]));
        const soDiem = diem.length;
        const buocX = soDiem > 1 ? (KHUNG.phai - KHUNG.trai) / (soDiem - 1) : 0;
        const x = (i) => (soDiem > 1 ? KHUNG.trai + i * buocX : (KHUNG.trai + KHUNG.phai) / 2);
        const y = (v) => KHUNG.duoi - (Math.min(v, dinh) / dinh) * (KHUNG.duoi - KHUNG.tren);

        const dTaoMoi = diem.map((d, i) => ({ x: x(i), y: y(d.created) }));
        const dVeKho = diem.map((d, i) => ({ x: x(i), y: y(d.confirmed) }));
        // Vùng (area) của series 1. Kỳ "Hôm nay" chỉ có 1 điểm ⇒ vẽ thành cột mảnh 24px
        // thay vì một vùng rộng 0 (nhìn như vạch kẻ, dễ tưởng lỗi).
        let vung;
        if (soDiem > 1) {
            vung =
                `M${dTaoMoi[0].x.toFixed(1)},${KHUNG.duoi} ` +
                duongDi(dTaoMoi).replace(/^M/, "L") +
                ` L${dTaoMoi[dTaoMoi.length - 1].x.toFixed(1)},${KHUNG.duoi} Z`;
        } else {
            const xc = dTaoMoi[0].x;
            vung =
                `M${(xc - 12).toFixed(1)},${KHUNG.duoi} ` +
                `L${(xc - 12).toFixed(1)},${dTaoMoi[0].y.toFixed(1)} ` +
                `L${(xc + 12).toFixed(1)},${dTaoMoi[0].y.toFixed(1)} ` +
                `L${(xc + 12).toFixed(1)},${KHUNG.duoi} Z`;
        }

        // Nhãn trục X: tối đa 7 nhãn để không chồng chữ; luôn có nhãn ở điểm cuối.
        const buocNhan = Math.max(1, Math.ceil(soDiem / 7));
        const nhanX = [];
        for (let i = 0; i < soDiem; i += buocNhan) {
            nhanX.push({ x: x(i), nhan: ngayNgan(diem[i].date) });
        }
        if (nhanX.length && nhanX[nhanX.length - 1].x !== x(soDiem - 1)) {
            nhanX.push({ x: x(soDiem - 1), nhan: ngayNgan(diem[soDiem - 1].date) });
        }

        // Cột "bắt chuột" phủ toàn chiều cao: <title> cho browser hiện tooltip chữ,
        // không cần JS và không cần thư viện chart.
        const rongCot = soDiem > 1 ? buocX : KHUNG.phai - KHUNG.trai;
        const cot = diem.map((d, i) => ({
            x: (x(i) - rongCot / 2).toFixed(1),
            rong: rongCot.toFixed(1),
            title:
                `${ngayNgan(d.date)}: ${soVN(d.created)} tạo mới · ` +
                `${soVN(d.picked)} đã lấy · ${soVN(d.confirmed)} về kho`,
        }));

        const cuoiCung = diem[diem.length - 1];
        return {
            moTa: "Biểu đồ vùng theo ngày: số việc tạo mới và số việc đã về kho.",
            coSoLieu,
            trai: KHUNG.trai,
            phai: KHUNG.phai,
            tren: KHUNG.tren,
            duoi: KHUNG.duoi,
            trucY: moc.map((giaTri) => ({ y: y(giaTri), nhan: soVN(giaTri) })),
            nhanX,
            cot,
            duongTaoMoi: duongDi(dTaoMoi),
            vungTaoMoi: vung,
            duongVeKho: duongDi(dVeKho),
            diem: [
                ...dTaoMoi.map((d) => ({ x: d.x.toFixed(1), y: d.y.toFixed(1), bac: 1 })),
                ...dVeKho.map((d) => ({ x: d.x.toFixed(1), y: d.y.toFixed(1), bac: 2 })),
            ],
            tongHomNay: {
                taoMoi: soVN(cuoiCung.created),
                daLay: soVN(cuoiCung.picked),
                veKho: soVN(cuoiCung.confirmed),
            },
        };
    }

    // ---------------------------------------------------------------------------------
    // KHỐI 3 — Donut cơ cấu trạng thái (1 vòng, thang 4 bậc forest)
    // ---------------------------------------------------------------------------------
    get donut() {
        const byState = (this.state.duLieu && this.state.duLieu.byState) || [];
        if (!byState.length) {
            return null;
        }
        const dem = {};
        for (const muc of byState) {
            dem[muc.state] = soDem(muc.count);
        }
        const chuVi = 2 * Math.PI * DONUT.r;
        const tongVong = this._tongVong(dem);
        // Tổng TẤT CẢ trạng thái (kể cả 2 trạng thái ngoài vòng): quyết định khối có dữ
        // liệu hay không. Nếu chỉ có việc thất bại/đã huỷ thì vòng rỗng nhưng vẫn có số
        // để báo — không được nói "Chưa có dữ liệu" khi dữ liệu đang nằm ở ngoài vòng.
        const tongTatCa = byState.reduce((tong, muc) => tong + soDem(muc.count), 0);
        let daVe = 0;
        const vong = [];
        for (const nhom of NHOM_DONUT) {
            const soLuong = nhom.states.reduce((tong, ma) => tong + (dem[ma] || 0), 0);
            const doDai = tongVong ? (soLuong / tongVong) * chuVi : 0;
            vong.push({
                khoa: nhom.khoa,
                nhan: nhom.nhan,
                bac: nhom.bac,
                soLuong,
                nhanSo: soVN(soLuong),
                phanTram: tongVong ? Math.round((soLuong / tongVong) * 100) : 0,
                dash: `${doDai.toFixed(2)} ${(chuVi - doDai).toFixed(2)}`,
                offset: `-${daVe.toFixed(2)}`,
                title: `${nhom.nhan}: ${soVN(soLuong)} việc`,
            });
            daVe += doDai;
        }
        return {
            moTa: "Vòng cơ cấu trạng thái: 4 nhóm trạng thái chính của yêu cầu lấy hàng.",
            tong: soVN(tongVong),
            tongSo: tongVong,
            tongTatCa,
            chuVi: chuVi.toFixed(2),
            vong,
            // Chỉ các nhóm có số > 0 mới vẽ cung: cung dài 0 của nhóm rỗng vẫn tạo một
            // stroke-dasharray "0 C" ⇒ một nét chấm ở đỉnh vòng, nhìn như lỗi vẽ.
            vongVe: vong.filter((muc) => muc.soLuong > 0),
            ngoaiVong: NGOAI_VONG.map((ma) => ({
                khoa: ma,
                nhan: NHAN_TRANG_THAI[ma],
                soLuong: dem[ma] || 0,
                nhanSo: soVN(dem[ma] || 0),
            })),
        };
    }

    /** Tổng số việc của 4 nhóm TRONG vòng (đúng bằng mẫu số của tỷ lệ %). */
    _tongVong(dem) {
        return NHOM_DONUT.reduce(
            (tong, nhom) => tong + nhom.states.reduce((cong, ma) => cong + (dem[ma] || 0), 0),
            0
        );
    }

    // ---------------------------------------------------------------------------------
    // KHỐI 4 — Bảng 10 việc cập nhật gần nhất (skill §5/§6)
    // ---------------------------------------------------------------------------------
    get bang() {
        const recent = (this.state.duLieu && this.state.duLieu.recent) || [];
        return recent.map((viec) => {
            const ma = this._maTrangThai(viec);
            return {
                id: viec.id,
                ref: viec.ref || "—",
                shop: viec.shop || "—",
                ma,
                nhanTrangThai: viec.stateLabel || NHAN_TRANG_THAI[ma] || "Chưa rõ",
                taiXe: viec.driverName || "Chưa gán",
                khungGio: ngayDayDu(viec.windowFrom) || "—",
                capNhat: ngayDayDu(viec.updatedAt) || "—",
            };
        });
    }

    /** Mã trạng thái của 1 dòng: ưu tiên `state` (nếu BE bổ sung), không thì tra nhãn. */
    _maTrangThai(viec) {
        if (viec.state && NHAN_TRANG_THAI[viec.state]) {
            return viec.state;
        }
        return MA_THEO_NHAN[viec.stateLabel] || "khong_ro";
    }

    /** Bấm 1 dòng: mở form của yêu cầu lấy hàng (đường về luồng chính, Luật 5). */
    moViec(id) {
        if (!id) {
            return;
        }
        this.action.doAction({
            type: "ir.actions.act_window",
            name: "Yêu cầu lấy hàng",
            res_model: MODEL,
            res_id: id,
            views: [[false, "form"]],
            target: "current",
        });
    }

    /** Enter/Space trên dòng bảng = mở việc (skill §6: bàn phím đi hết được). */
    onKeydownDong(ev, id) {
        if (ev.key === "Enter" || ev.key === " ") {
            ev.preventDefault();
            this.moViec(id);
        }
    }

    /** Nút "Xem 30 ngày" ở empty state của bảng/chart: đổi kỳ rồi tải lại. */
    xemBaMuoiNgay() {
        this.chonKy("30d");
    }
}

registry.category("actions").add("lab_ui.dashboard", LabDashboard);
