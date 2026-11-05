/**
 * shot-auth.mjs — chup anh trang Odoo SAU DANG NHAP (Playwright).
 *
 * Vi sao can: Chrome headless khong dang nhap duoc, nen truoc day khong chup duoc
 * dashboard/list/form cua backend. Script nay dang nhap bang form that, roi chup.
 *
 * Dung:
 *   node infra/scripts/shot-auth.mjs --page login      --out docs/review/odoo-login.png
 *   node infra/scripts/shot-auth.mjs --page list       --out docs/review/odoo-list.png
 *   node infra/scripts/shot-auth.mjs --page dashboard  --out docs/review/odoo-dashboard.png
 *   node infra/scripts/shot-auth.mjs --page form       --out docs/review/odoo-form.png
 *   node infra/scripts/shot-auth.mjs --url "/web#action=123" --out x.png
 *
 * Tuy chon: --width 1440 --height 900 --scale 1 --wait 1200 --menu "Gom hàng lẻ>Bảng điều khiển"
 *           --login sep --password lab@2026 --base http://localhost:8069
 *
 * Quy uoc du an: chi stdout ASCII, khong phu thuoc CDN, khong sua Odoo.
 */
import { mkdirSync, existsSync } from 'node:fs'
import { dirname, isAbsolute, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')

// Playwright duoc cai trong demo/tools (package.json rieng). Node resolves package tu
// thu muc cua SCRIPT, nen phai import bang duong dan tuyet doi toi node_modules do.
const pwEntry = pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href
const { chromium } = await import(pwEntry)

function arg(name, fallback) {
  const i = process.argv.indexOf('--' + name)
  if (i === -1) return fallback
  const v = process.argv[i + 1]
  return v === undefined ? fallback : v
}

const BASE = arg('base', 'http://localhost:8069')
const LOGIN = arg('login', 'sep')
const PASSWORD = arg('password', 'lab@2026')
const PAGE = arg('page', 'login')
const OUT = arg('out', 'docs/review/odoo-shot.png')
const WIDTH = parseInt(arg('width', '1440'), 10)
const HEIGHT = parseInt(arg('height', '900'), 10)
const SCALE = parseFloat(arg('scale', '1'))
const WAIT = parseInt(arg('wait', '1200'), 10)
const URL_OVERRIDE = arg('url', '')
const MENU = arg('menu', '')

const outPath = isAbsolute(OUT) ? OUT : join(ROOT, OUT)
if (!existsSync(dirname(outPath))) mkdirSync(dirname(outPath), { recursive: true })

function log(msg) {
  process.stdout.write('  ' + msg + '\n')
}

async function main() {
  const browser = await chromium.launch()
  const context = await browser.newContext({
    viewport: { width: WIDTH, height: HEIGHT },
    deviceScaleFactor: SCALE,
    ignoreHTTPSErrors: true,
  })
  const page = await context.newPage()
  page.setDefaultTimeout(30000)

  // Thu loi JS: OWL action loi se hien modal "Odoo Client Error" — can biet WHY.
  const errors = []
  page.on('pageerror', (e) => errors.push('pageerror: ' + (e && e.message ? e.message : String(e))))
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push('console: ' + m.text().slice(0, 300))
  })

  // 1) Dang nhap qua form that (CSRF token do chinh trang sinh ra)
  await page.goto(BASE + '/web/login', { waitUntil: 'domcontentloaded' })
  if (LOGIN) {
    await page.fill('input[name="login"]', LOGIN)
    await page.fill('input[name="password"]', PASSWORD)
    await Promise.all([
      page.waitForLoadState('domcontentloaded'),
      page.click('button[type="submit"]'),
    ])
    await page.waitForSelector('.o_web_client', { timeout: 30000 })
    log('dang nhap thanh cong: ' + LOGIN)
  }

  // 2) Dieu huong
  if (URL_OVERRIDE) {
    // Vao /web truoc, roi doi hash + reload de router cua Odoo xu ly (goto cung URL chi doi hash
    // khong lam Odoo re-render action).
    const hash = URL_OVERRIDE.includes('#') ? '#' + URL_OVERRIDE.split('#')[1] : ''
    await page.goto(BASE + '/web' + hash, { waitUntil: 'domcontentloaded' })
    if (hash) {
      await page.reload({ waitUntil: 'domcontentloaded' })
    }
  } else if (PAGE === 'list' || PAGE === 'form') {
    await openMenu(page, ['Gom hàng lẻ', 'Yêu cầu lấy hàng'])
    await page.waitForSelector('.o_list_view', { timeout: 30000 })
    if (PAGE === 'form') {
      const row = page.locator('.o_data_row').first()
      await row.click()
      await page.waitForSelector('.o_form_view', { timeout: 30000 })
    }
  } else if (PAGE === 'dashboard') {
    const chain = MENU ? MENU.split('>').map((s) => s.trim()) : ['Gom hàng lẻ', 'Bảng điều khiển']
    await openMenu(page, chain)
    await page.waitForTimeout(2500) // doi chart/RPC xong
  } else if (PAGE === 'login') {
    // chi can trang login (dang xuat de chac chan thay form)
    await page.goto(BASE + '/web/session/logout?redirect=/web/login', { waitUntil: 'domcontentloaded' })
  }

  await page.waitForTimeout(WAIT)
  // Neu co modal loi OWL thi bam "See details" de lay thong diep that.
  const details = page.getByText('See details', { exact: true }).first()
  if (await details.count()) {
    await details.click({ force: true }).catch(() => {})
    await page.waitForTimeout(500)
    const body = await page.locator('.modal-body, .o_error_dialog').last().innerText().catch(() => '')
    if (body) errors.push('dialog: ' + body.replace(/\s+/g, ' ').slice(0, 600))
  }
  await page.screenshot({ path: outPath })
  const pixels = await page.evaluate(() => ({ w: window.innerWidth, h: window.innerHeight }))
  log('da chup: ' + outPath + ' (' + pixels.w + 'x' + pixels.h + ', scale ' + SCALE + ')')
  if (errors.length) {
    log('LOI JS (' + errors.length + '):')
    for (const e of errors.slice(0, 8)) log('  - ' + e)
  }
  await browser.close()
}

async function dismissModals(page) {
  // Odoo bat modal (thong bao/tour) ngay sau dang nhap -> chan pointer events.
  for (let i = 0; i < 3; i++) {
    await page.keyboard.press('Escape').catch(() => {})
    await page.waitForTimeout(150)
  }
  const closers = page.locator('.modal.show .btn-close, .o_technical_modal .btn-close, .modal.show .btn-secondary')
  if (await closers.count()) {
    await closers.first().click({ force: true }).catch(() => {})
    await page.waitForTimeout(300)
  }
}

async function openMenu(page, chain) {
  // Odoo 17 trong image nay phuc vu backend o `/web`; `/odoo` tra 404 (da do bang anh chup).
  // Phai vao /web truoc thi moi co navbar + menu.
  if (!page.url().includes('/web')) {
    await page.goto(page.url().replace(/\/(odoo|web).*$/, '/web'), { waitUntil: 'domcontentloaded' })
  }
  await page.waitForSelector('.o_main_navbar', { timeout: 30000 })
  for (let i = 0; i < chain.length; i++) {
    const label = chain[i]
    await dismissModals(page)
    let item = page.locator('.o_main_navbar').getByText(label, { exact: true }).first()
    if (!(await item.count())) {
      // App nam trong "app switcher" (icon luoi o goc trai navbar), khong phai link navbar.
      const switcher = page.locator('.o_navbar_apps_menu button, .o_navbar_apps_menu .dropdown-toggle').first()
      if (await switcher.count()) {
        await switcher.click({ force: true }).catch(() => {})
        await page.waitForTimeout(800)
      }
      item = page.locator('.o-dropdown--menu').getByText(label, { exact: true }).first()
    }
    if (!(await item.count())) {
      item = page.getByText(label, { exact: true }).first()
    }
    await item.waitFor({ state: 'visible', timeout: 30000 })
    // Uu tien href (khong bi modal chan): menu item cua Odoo la <a href="#menu_id=..&action=..">
    const href = await item.getAttribute('href').catch(() => null)
    await dismissModals(page)
    try {
      await item.click({ timeout: 5000 })
    } catch (e) {
      if (href) {
        await page.goto(page.url().split('#')[0] + '/web' + href, { waitUntil: 'domcontentloaded' })
      } else {
        throw e
      }
    }
    await page.waitForTimeout(1200)
  }
}

main().catch((err) => {
  process.stderr.write('LOI: ' + (err && err.message ? err.message : String(err)) + '\n')
  process.exit(1)
})
