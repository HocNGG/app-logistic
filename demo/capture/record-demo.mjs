// record-demo.mjs — quay cảnh demo Odoo web bằng Playwright
//
// Vì sao Playwright: quay được LẠI nhiều lần, cùng một kịch bản, không phụ thuộc tay người.
// Lưu ý quan trọng: file video CHỈ được ghi khi browserContext.close() được gọi.
//
// Cách dùng:
//   cd demo/capture && npm init -y && npm i -D playwright && npx playwright install chromium
//   node record-demo.mjs --base http://localhost:8069 --scenes 3,4,6,7
//
// Cảnh:
//   3 = CS console: danh sách yêu cầu + chuyến (Odoo web, duo sẽ dùng app KMP)
//   4 = Ngoại lệ quá tải: chuyến 120% -> wizard giảm tải -> duyệt
//   6 = Portal/track: mở link access_token của khách
//   7 = Debit -> khoá -> hoá đơn -> thu tiền

import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    base:   { type: 'string', default: 'http://localhost:8069' },
    db:     { type: 'string', default: 'odoo17_lab' },
    login:  { type: 'string', default: 'cs01' },
    pass:   { type: 'string', default: 'lab@2026' },
    scenes: { type: 'string', default: '3,4,6,7' },
    out:    { type: 'string', default: 'raw' },
  },
});

const SCENES = new Set(values.scenes.split(',').map((s) => s.trim()));
mkdirSync(`${values.out}`, { recursive: true });

const viewport = { width: 1920, height: 1080 };

const browser = await chromium.launch({ headless: false, slowMo: 60 });
const context = await browser.newContext({
  viewport,
  recordVideo: { dir: values.out, size: viewport },
  locale: 'vi-VN',
  timezoneId: 'Asia/Ho_Chi_Minh',
});
const page = await context.newPage();

// --- đăng nhập -------------------------------------------------------------
async function login(user = values.login, pass = values.pass) {
  await page.goto(`${values.base}/web/login?db=${values.db}`);
  await page.fill('input[name="login"]', user);
  await page.fill('input[name="password"]', pass);
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/odoo/, { timeout: 30000 });
  await page.waitForTimeout(1200);
}

// --- helper: highlight để người xem thấy đang bấm ở đâu -------------------
async function highlight(selector, ms = 700) {
  const el = page.locator(selector).first();
  await el.waitFor({ state: 'visible', timeout: 15000 });
  await el.evaluate((n) => {
    n.style.outline = '3px solid #f59e0b';
    n.style.outlineOffset = '2px';
    n.scrollIntoView({ block: 'center' });
  });
  await page.waitForTimeout(ms);
}

async function step(label, fn) {
  const t0 = Date.now();
  process.stdout.write(`  · ${label} ... `);
  await fn();
  console.log(`ok (${((Date.now() - t0) / 1000).toFixed(1)}s)`);
}

try {
  console.log(`Quay từ ${values.base} / db=${values.db}`);
  await step('đăng nhập', () => login());

  // ======================= CẢNH 3 — CS console ==============================
  if (SCENES.has('3')) {
    console.log('CẢNH 3 — danh sách yêu cầu + chuyến');
    await step('mở app Chuyến', async () => {
      await page.goto(`${values.base}/odoo/action-lab_chuyen.action_lab_chuyen`);
      await page.waitForTimeout(1500);
    });
    await step('highlight dòng chuyến đầu', () => highlight('tbody tr.o_data_row'));
    await step('mở form chuyến', async () => {
      await page.locator('tbody tr.o_data_row').first().click();
      await page.waitForTimeout(1500);
    });
    await step('zoom thanh tải trọng', () => highlight('.o_field_widget[name="ti_le_tai"]'));
  }

  // ======================= CẢNH 4 — quá tải / giảm tải =====================
  if (SCENES.has('4')) {
    console.log('CẢNH 4 — quá tải 120% -> giảm tải -> duyệt');
    await step('mở chuyến quá tải', async () => {
      // tên chuyến quá tải được seed sẵn (CH/AIR/26/0002 = 120%)
      await page.goto(`${values.base}/odoo/action-lab_chuyen.action_lab_chuyen`);
      await page.fill('.o_searchview_input', 'CH/AIR/26/0002');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1200);
      await page.locator('tbody tr.o_data_row').first().click();
      await page.waitForTimeout(1200);
    });
    await step('highlight cảnh báo quá tải', () => highlight('.o_field_widget[name="ti_le_tai"]'));
    await step('mở wizard Giảm tải', async () => {
      await page.getByRole('button', { name: /Giảm tải/ }).click();
      await page.waitForTimeout(1200);
    });
    await step('chọn 1 đơn để gỡ', async () => {
      await page.locator('.o_list_view tbody tr.o_data_row input[type="checkbox"]').first().check();
      await page.waitForTimeout(500);
    });
    await step('xác nhận (tier validation sẽ chặn nếu chưa duyệt)', async () => {
      await page.getByRole('button', { name: /Xác nhận|Gỡ đơn/ }).click();
      await page.waitForTimeout(1500);
    });
  }

  // ======================= CẢNH 6 — portal khách ===========================
  if (SCENES.has('6')) {
    console.log('CẢNH 6 — khách tra cứu hành trình');
    const token = process.env.LAB_TRACK_TOKEN;
    if (!token) {
      console.log('    ⚠ bỏ qua: đặt biến môi trường LAB_TRACK_TOKEN để quay cảnh này');
    } else {
      await step('mở link tra cứu', async () => {
        await page.goto(`${values.base}/lab/track/${token}`);
        await page.waitForTimeout(1500);
      });
      await step('cuộn timeline', async () => {
        await page.mouse.wheel(0, 400);
        await page.waitForTimeout(1500);
      });
    }
  }

  // ======================= CẢNH 7 — Debit -> khoá -> thu tiền ==============
  if (SCENES.has('7')) {
    console.log('CẢNH 7 — Debit, khoá, hoá đơn, thu tiền');
    await step('đăng nhập kế toán', () => login('ketoan01', values.pass));
    await step('mở Debit', async () => {
      await page.goto(`${values.base}/odoo/action-account.action_move_out_invoice_type`);
      await page.waitForTimeout(1500);
    });
    await step('highlight Debit của chuyến', () => highlight('tbody tr.o_data_row'));
    await step('mở form Debit', async () => {
      await page.locator('tbody tr.o_data_row').first().click();
      await page.waitForTimeout(1500);
    });
    await step('highlight trạng thái đã khoá', () => highlight('.o_field_widget[name="state"]'));
    await step('mở Payment', async () => {
      await page.getByRole('button', { name: /Thanh toán|Register Payment/ }).click();
      await page.waitForTimeout(1500);
    });
  }

  await page.waitForTimeout(800);
} catch (err) {
  console.error('\nLỖI khi quay:', err.message);
  console.error('Mẹo: kiểm tra selector sau khi nâng cấp Odoo, hoặc quay cảnh này thủ công.');
  process.exitCode = 1;
} finally {
  // ⚠️ BẮT BUỘC: video chỉ được ghi khi context đóng
  await context.close();
  await browser.close();
  console.log('\nĐã ghi video vào thư mục:', values.out);
}
