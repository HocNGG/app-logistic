/** @odoo-module **/
/**
 * kpi_strip.js — dải 4 thẻ KPI trên đầu màn DANH SÁCH (v5 light).
 *
 * ⚠️ Bản đầu `import { rpc } from "@web/core/network/rpc"` làm bundle báo
 * "modules could not be loaded because they have unmet dependencies [@lab_ui/js/kpi_strip]"
 * (đo 2026-10-30) ⇒ bản này KHÔNG import gì, gọi JSON-RPC bằng fetch tới
 * /web/dataset/call_kw (đúng route web client dùng), nên không thể vỡ vì thiếu module.
 */
/**
 * Tiêu đề các màn DANH SÁCH được gắn dải KPI.
 *
 * ⚠️ SO KHỚP BẰNG startsWith với text của breadcrumb. Đo 2026-11-03 (ảnh khách gửi):
 * màn "Khách hàng (Shop)" KHÔNG có dải KPI vì không nằm trong danh sách này — trong khi
 * đây là màn đầu tiên của luồng (CS tạo khách) nên người xem thấy thiếu thông tin vận hành.
 * Thêm "Khách hàng (Shop)" để mọi màn danh sách của menu Gom hàng lẻ đều có cùng 4 thẻ.
 */
const TITLES = [
    "Yêu cầu lấy hàng",
    "Chuyến",
    "Hành trình",
    "Khách hàng (Shop)",
];
const MODEL = "lab.yeu.cau.lay.hang";

function card(cls, k, v, unit, d, dcls) {
    return '<div class="lab-kpi ' + cls + '"><span class="glow"></span>' +
        '<div class="k">' + k + '</div>' +
        '<div class="v">' + v + '<small>' + unit + '</small></div>' +
        '<div class="d ' + (dcls || "") + '">' + d + '</div></div>';
}

async function kpi() {
    const body = {
        jsonrpc: "2.0", method: "call",
        params: { model: MODEL, method: "lab_dashboard_kpi", args: ["30d"], kwargs: {} },
    };
    const res = await fetch("/web/dataset/call_kw", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        credentials: "same-origin",
    });
    const json = await res.json();
    return json && json.result ? json.result.kpi || {} : null;
}

let busy = false;
async function paint() {
    if (busy) return;
    try {
        const panel = document.querySelector(".o_control_panel");
        if (!panel || panel.dataset.labKpi === "1") return;
        // CHỈ màn DANH SÁCH. Đo 2026-11-02 (probe DOM): tiêu đề breadcrumb của FORM chuyến
        // cũng là "Chuyến" ⇒ dải KPI từng bị chèn vào cả form (sai ngữ nghĩa).
        // ⚠️ ĐO LẠI cùng ngày: `.o_list_view` KHÔNG nằm trong `.o_content` — nó là
        // `.o_content` → `.o_list_renderer`, còn `.o_list_view` là `.o_action` nằm dưới
        // `.o_action_manager` (xem .tmp/probe-dom-list.mjs). Điều kiện đầu tiên
        // (`.o_content .o_list_view`) luôn false ⇒ strip KHÔNG BAO GIỜ hiện. Vì vậy kiểm
        // theo tài liệu renderer + loại trừ form, không dò theo cây cha.
        const dangOList = !!document.querySelector(".o_list_view, .o_list_renderer");
        const dangOFormView = !!document.querySelector(".o_form_view");
        if (!dangOList || dangOFormView) {
            document.querySelectorAll(".lab-kpi-strip").forEach((e) => e.remove());
            if (!dangOFormView) delete panel.dataset.labKpi;
            return;
        }
        const crumb = panel.querySelector(".o_breadcrumb");
        const title = (crumb ? crumb.textContent : "").trim();
        if (!TITLES.some((t) => title.startsWith(t))) return;
        busy = true;
        const k = await kpi();
        busy = false;
        if (!k || !panel.isConnected) return;
        const strip = document.createElement("div");
        strip.className = "lab-kpi-strip";
        // NỘI DUNG THẺ THEO MÀN (sửa 2026-11-03 theo phản hồi của khách: "màn Chuyến phải có
        // số chuyến, chờ gán tài xế, đang đi lấy hàng…"). Trước đây mọi màn đều hiện 4 thẻ
        // VIỆC, nên màn Chuyến không có con số nào về chuyến — nhìn là thấy sai ngữ cảnh.
        //
        // - Màn "Chuyến": 4 thẻ CHUYẾN (tổng · đang mở · đã chốt booking · đã khởi hành).
        // - Các màn còn lại (YC lấy hàng, Khách hàng, Hành trình): 4 thẻ VIỆC như cũ,
        //   vì người dùng các màn đó đang theo dõi dòng việc lấy hàng.
        const laManChuyen = title.startsWith("Chuyến");
        if (laManChuyen) {
            strip.innerHTML =
                card("g", "Tổng số chuyến", k.tripsTotal != null ? k.tripsTotal : "—", "chuyến", "tất cả", "") +
                card("b", "Chuyến đang mở", k.tripsOpen != null ? k.tripsOpen : "—", "chuyến", "nhận thêm đơn", "grey") +
                card("r", "Đã chốt booking", k.tripsBooked != null ? k.tripsBooked : "—", "chuyến", "chờ khởi hành", "grey") +
                card("mint", "Đã khởi hành", k.tripsDeparted != null ? k.tripsDeparted : "—", "chuyến", "rời kho", "grey");
        } else {
            strip.innerHTML =
                card("g", "Chờ gán tài xế", k.pendingAssign != null ? k.pendingAssign : "—", "việc", "30 ngày", "") +
                card("b", "Đang đi lấy hàng", k.enRoute != null ? k.enRoute : "—", "việc", "tài xế đang chạy", "grey") +
                card("r", "Đã lấy được hôm nay", k.pickedUpToday != null ? k.pickedUpToday : "—", "việc", "chờ kho nhận", "grey") +
                card("mint", "Đã về kho hôm nay", k.confirmedToday != null ? k.confirmedToday : "—", "việc", "hôm nay", "grey");
        }
        // Chen RA NGOAI control panel (sau no) — chen vao ben trong thì bi flex row cua
        // thanh cong cu bop nho (da thay 2026-10-30: the KPI bi ep 60px, tieu de bi cat).
        panel.insertAdjacentElement("afterend", strip);
        panel.dataset.labKpi = "1";
    } catch (err) {
        busy = false;
        console.debug("[lab_ui] kpi_strip:", err && err.message);
    }
}

let t = null;
function schedule() { clearTimeout(t); t = setTimeout(paint, 250); }
window.addEventListener("hashchange", () => {
    document.querySelectorAll(".lab-kpi-strip").forEach((e) => e.remove());
    const p = document.querySelector(".o_control_panel");
    if (p) delete p.dataset.labKpi;
    schedule();
});
setTimeout(schedule, 900);
setTimeout(schedule, 2500);
try { new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true }); } catch (e) {}