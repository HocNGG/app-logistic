/**
 * shot-page.mjs — chup 1 trang voi VIEWPORT CHINH XAC (Playwright).
 *
 * Vi sao can: `chrome --headless --window-size=390,844` tren Windows bi keo len cua so toi
 * thieu (~500px) => layout rong hon viewport, anh 390px bi cat ben phai (da gap that).
 * Playwright dat viewport theo CSS px, khong bi gioi han do.
 *
 * Dung:
 *   node infra/scripts/shot-page.mjs --url "file:///.../demo/landing/index.html" --out docs/review/x.png --width 390 --height 844 --scale 2
 *   node infra/scripts/shot-page.mjs --url "/web/login" --base http://localhost:8069 --out docs/review/y.png
 *   node infra/scripts/shot-page.mjs --url "/web#menu_id=338&action=493" --base http://localhost:8069 --login sep --password lab@2026 --out docs/review/z.png
 *
 * Tuy chon: --full (chup ca trang), --wait 2500, --reduced (gia lap prefers-reduced-motion)
 */
import { mkdirSync, existsSync } from 'node:fs'
import { dirname, isAbsolute, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const { chromium } = await import(pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href)

function arg(name, fallback) {
  const i = process.argv.indexOf('--' + name)
  if (i === -1) return fallback
  const v = process.argv[i + 1]
  return v === undefined || v.startsWith('--') ? fallback : v
}
const has = (name) => process.argv.includes('--' + name)

const BASE = arg('base', '')
const URL_ARG = arg('url', '')
const OUT = arg('out', 'docs/review/page.png')
const WIDTH = parseInt(arg('width', '1440'), 10)
const HEIGHT = parseInt(arg('height', '900'), 10)
const SCALE = parseFloat(arg('scale', '1'))
const WAIT = parseInt(arg('wait', '1500'), 10)
const LOGIN = arg('login', '')
const PASSWORD = arg('password', 'lab@2026')

const target = URL_ARG.startsWith('http') || URL_ARG.startsWith('file:')
  ? URL_ARG
  : (BASE || 'http://localhost:8069') + URL_ARG

const outPath = isAbsolute(OUT) ? OUT : join(ROOT, OUT)
if (!existsSync(dirname(outPath))) mkdirSync(dirname(outPath), { recursive: true })

const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: { width: WIDTH, height: HEIGHT },
  deviceScaleFactor: SCALE,
  reducedMotion: has('reduced') ? 'reduce' : 'no-preference',
})
const page = await context.newPage()
page.setDefaultTimeout(30000)
const errors = []
page.on('pageerror', (e) => errors.push(String(e && e.message ? e.message : e)))
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)) })

if (LOGIN) {
  await page.goto((BASE || 'http://localhost:8069') + '/web/login', { waitUntil: 'domcontentloaded' })
  await page.fill('input[name="login"]', LOGIN)
  await page.fill('input[name="password"]', PASSWORD)
  await Promise.all([page.waitForLoadState('domcontentloaded'), page.click('button[type="submit"]')])
  await page.waitForSelector('.o_web_client', { timeout: 30000 })
  process.stdout.write('  dang nhap: ' + LOGIN + '\n')
}

await page.goto(target, { waitUntil: 'domcontentloaded' })
if (target.includes('#') && LOGIN) {
  await page.reload({ waitUntil: 'domcontentloaded' })
}
await page.waitForTimeout(WAIT)

// video nen: dam bao da co frame dau (neu co .hero-video-el)
const video = page.locator('video').first()
if (await video.count()) {
  await video.evaluate((v) => { try { v.currentTime = 1.2; v.pause(); } catch (e) {} }).catch(() => {})
  await page.waitForTimeout(400)
}

await page.screenshot({ path: outPath, fullPage: has('full') })
const vp = page.viewportSize()
process.stdout.write('  da chup: ' + outPath + ' (viewport ' + vp.width + 'x' + vp.height + ', scale ' + SCALE + (has('full') ? ', full page' : '') + ')\n')
if (errors.length) {
  process.stdout.write('  LOI JS (' + errors.length + '): ' + errors.slice(0, 4).join(' | ') + '\n')
}
await browser.close()
