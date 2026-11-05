/**
 * probe-point.mjs — tim phan tu that tai 1 toa do (mac dinh 700,12) va in chuoi
 * phan tu tu no len tren kem mau nen tinh ra. Chan doan 1 lan.
 */
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const { chromium } = await import(pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href)

const X = parseInt(process.argv[2] || '700', 10)
const Y = parseInt(process.argv[3] || '12', 10)
const BASE = 'http://localhost:8069'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto(BASE + '/web/login', { waitUntil: 'domcontentloaded' })
await page.fill('input[name="login"]', 'sep')
await page.fill('input[name="password"]', 'lab@2026')
await Promise.all([page.waitForLoadState('domcontentloaded'), page.click('button[type="submit"]')])
await page.waitForSelector('.o_web_client', { timeout: 30000 })
await page.goto(BASE + '/web#menu_id=338&action=493', { waitUntil: 'domcontentloaded' })
await page.reload({ waitUntil: 'domcontentloaded' })
await page.waitForTimeout(4000)

const chain = await page.evaluate(
  ({ x, y }) => {
    const out = []
    let el = document.elementFromPoint(x, y)
    while (el && out.length < 8) {
      const cs = getComputedStyle(el)
      out.push({
        tag: el.tagName,
        cls: (el.className || '').toString().slice(0, 90),
        bg: cs.backgroundColor,
        bgShort: cs.background,
      })
      el = el.parentElement
    }
    return out
  },
  { x: X, y: Y },
)

process.stdout.write('tai (' + X + ',' + Y + '):\n' + JSON.stringify(chain, null, 2) + '\n')
await browser.close()
