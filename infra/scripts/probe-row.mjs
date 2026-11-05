/**
 * probe-row.mjs - soi DOM list view Odoo: dong nao dang sang va VI SAO.
 * node infra/scripts/probe-row.mjs --base http://localhost:8069 --url "/web#menu_id=334&action=489" --login cs01 --password lab@2026
 */
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const pwEntry = pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href
const { chromium } = await import(pwEntry)

const arg = (name, fallback) => {
  const i = process.argv.indexOf('--' + name)
  return i === -1 ? fallback : (process.argv[i + 1] ?? fallback)
}
const BASE = arg('base', 'http://localhost:8069')
const URL_ARG = arg('url', '/web#menu_id=334&action=489')

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto(BASE + '/web/login', { waitUntil: 'domcontentloaded' })
await page.fill('input[name="login"]', arg('login', 'cs01'))
await page.fill('input[name="password"]', arg('password', 'lab@2026'))
await page.click('button[type="submit"]')
await page.waitForTimeout(3000)
await page.goto(BASE + URL_ARG, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(5000)

const paint = await page.evaluate(() => {
  const tr = document.querySelector('.o_list_table > tbody > tr')
  const td = tr.cells[1]
  const cs = getComputedStyle(td)
  const cst = getComputedStyle(tr)
  const before = getComputedStyle(td, '::before')
  const after = getComputedStyle(td, '::after')
  return {
    trCls: tr.className,
    trBoxShadow: cst.boxShadow,
    trBg: cst.backgroundColor,
    trVars: {
      accentBg: cst.getPropertyValue('--table-accent-bg').trim(),
      bg: cst.getPropertyValue('--bs-table-bg').trim(),
      stripe: cst.getPropertyValue('--bs-table-striped-bg').trim(),
    },
    tdBoxShadow: cs.boxShadow,
    tdBg: cs.backgroundColor,
    tdBgImage: cs.backgroundImage,
    beforeBg: before.backgroundColor,
    beforeContent: before.content,
    afterBg: after.backgroundColor,
    afterContent: after.content,
  }
})
console.log('== paint ==')
console.log(JSON.stringify(paint, null, 2))

const out = await page.evaluate(() => {
  const tables = [...document.querySelectorAll('table.o_list_table')].map((t, i) => ({
    i,
    cls: t.className.slice(0, 90),
    rows: [...t.querySelectorAll(':scope > tbody > tr')].slice(0, 6).map((tr) => {
      const td = tr.cells[1] || tr.cells[0]
      return {
        text: (td ? td.textContent : tr.textContent).trim().slice(0, 20),
        cls: tr.className,
        bg: td ? getComputedStyle(td).backgroundColor : null,
        color: td ? getComputedStyle(td).color : null,
        styleAttr: tr.getAttribute('style'),
      }
    }),
  }))
  const sample = [...document.querySelectorAll('[class*="sample"]')].map((e) => e.tagName + '.' + String(e.className).slice(0, 40)).slice(0, 6)
  return { tables, sample }
})
console.log(JSON.stringify(out, null, 2))
await browser.close()