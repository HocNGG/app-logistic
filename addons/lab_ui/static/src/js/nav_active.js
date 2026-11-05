/** @odoo-module **/
/**
 * nav_active.js — gắn class `o_nav_entry_active` cho mục nav ĐANG MỞ.
 *
 * VÌ SAO PHẢI CÓ FILE NÀY (đo thật 2026-10-30, không đoán):
 *   Odoo 17 KHÔNG đánh dấu mục menu đang mở. Probe DOM
 *   (`infra/scripts/probe-nav.mjs`) trên `/web#menu_id=332&action=487` in ra 9 entry
 *   đều là `class="dropdown-item o_nav_entry"`, `background-color: rgba(0,0,0,0)`,
 *   `color: rgb(146,156,171)` — KHÔNG entry nào có `active`.
 *   Vì vậy rule CSS `.o_nav_entry.active { background: var(--lab-accent) }` không bao
 *   giờ khớp ⇒ khách yêu cầu "chọn nút nào thì nút đó sáng lime" phải làm bằng JS.
 *
 * CÁCH LÀM: đọc query của `location.hash` (`menu_id`, `action`), so với query trong
 * `href` của từng `a.o_nav_entry`; khớp thì thêm class `o_nav_entry_active` (KHÔNG dùng
 * `active` trần để không đụng `.active` của Bootstrap/Odoo ở chỗ khác).
 *
 * AN TOÀN: mọi thứ trong try/catch; chỉ `classList.toggle` khi trạng thái ĐỔI (nếu ghi
 * mỗi lần sẽ làm MutationObserver tự kích hoạt vô hạn); không có navbar thì im lặng.
 */

const NAV_ACTIVE_CLASS = "o_nav_entry_active";

function parseQuery(raw) {
    const s = String(raw || "");
    const i = s.indexOf("?");
    const qs = i >= 0 ? s.slice(i + 1) : s.replace(/^#/, "");
    return new URLSearchParams(qs);
}

function currentRoute() {
    const p = parseQuery(window.location.hash);
    return { menuId: p.get("menu_id"), action: p.get("action") };
}

function routeOfHref(href) {
    const p = parseQuery(href);
    return { menuId: p.get("menu_id"), action: p.get("action") };
}

/** Đồng bộ 1 lần. Trả về true nếu có thay đổi class (dùng để chống lặp observer). */
function syncNavActive() {
    try {
        const entries = document.querySelectorAll(".o_main_navbar a.o_nav_entry");
        if (!entries.length) {
            return false;
        }
        const cur = currentRoute();
        let changed = false;
        for (const entry of entries) {
            const r = routeOfHref(entry.getAttribute("href"));
            let active = false;
            if (cur.menuId && r.menuId) {
                active = cur.menuId === r.menuId;
            } else if (cur.action && r.action) {
                active = cur.action === r.action;
            }
            const has = entry.classList.contains(NAV_ACTIVE_CLASS);
            if (active !== has) {
                entry.classList.toggle(NAV_ACTIVE_CLASS, active);
                changed = true;
            }
        }
        return changed;
    } catch (err) {
        // Không bao giờ làm sập web client vì trang trí nav.
        console.debug("[lab_ui] nav_active:", err && err.message);
        return false;
    }
}

let scheduled = false;
function schedule() {
    if (scheduled) {
        return;
    }
    scheduled = true;
    window.requestAnimationFrame(() => {
        scheduled = false;
        syncNavActive();
    });
}

// 1) Lần đầu (navbar render sau khi OWL mount ⇒ dời bằng setTimeout + rAF).
window.addEventListener("load", schedule);
setTimeout(schedule, 400);
setTimeout(schedule, 1500);

// 2) Đổi route (href hash) — Odoo dùng hash router.
window.addEventListener("hashchange", schedule);
window.addEventListener("popstate", schedule);

// 3) Navbar bị render lại (đổi menu/app, resize, mở dropdown…).
try {
    const observer = new MutationObserver(schedule);
    observer.observe(document.body, { childList: true, subtree: true });
} catch (err) {
    console.debug("[lab_ui] nav_active observer:", err && err.message);
}

export { syncNavActive };
