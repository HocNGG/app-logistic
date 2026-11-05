import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const pwEntry = pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href
const { chromium } = await import(pwEntry)
const arg = (n, f) => { const i = process.argv.indexOf('--' + n); return i === -1 ? f : (process.argv[i + 1] ?? f) }
const BASE = arg('base', 'http://localhost:8069')
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 })
await page.goto(BASE + '/web/login', { waitUntil: 'domcontentloaded' })
await page.fill('input[name="login"]', 'cs01')
await page.fill('input[name="password"]', 'lab@2026')
await page.click('button[type="submit"]')
await page.waitForTimeout(3000)
await page.goto(BASE + '/web#menu_id=334&action=489', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(5000)
const rows = await page.evaluate(() => {
  const tr = document.querySelector('.o_list_table > tbody > tr')
  const r = tr.getBoundingClientRect()
  const thead = document.querySelector('.o_list_table > thead')
  const trh = thead ? thead.getBoundingClientRect() : null
  return { rowTop: r.top, rowBottom: r.bottom, rowHeight: r.height, theadTop: trh && trh.top, theadBottom: trh && trh.bottom }
})
console.log('== hinh hoc ==')
console.log(JSON.stringify(rows))
const probes = []
for (let y = Math.round(rows.theadBottom) + 2; y < rows.rowBottom + 6; y += 6) {
  const p = await page.evaluate((yy) => {
    const el = document.elementFromPoint(700, yy)
    if (!el) return null
    const cs = getComputedStyle(el)
    return { y: yy, tag: el.tagName, cls: String(el.className).slice(0, 60), bg: cs.backgroundColor, pos: cs.position, z: cs.zIndex }
  }, y)
  probes.push(p)
}
console.log('== hit-test theo y ==')
console.log(JSON.stringify(probes, null, 1))
await page.screenshot({ path: join(ROOT, 'docs', 'review', 'v2', 'band-crop.png'), clip: { x: 0, y: Math.max(0, rows.theadBottom - 10), width: 1440, height: 110 } })
console.log('da chup band-crop.png')
await browser.close()