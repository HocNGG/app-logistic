/**
 * probe-navbar.mjs — chan doan 1 lan: navbar mau gi, stylesheet nao duoc nap,
 * co rule --lab-paper-2 khong. Dung Playwright trong demo/tools.
 */
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const { chromium } = await import(pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href)

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

const info = await page.evaluate(() => {
  const nav = document.querySelector('.o_main_navbar')
  const cs = nav ? getComputedStyle(nav) : null
  const sheetInfo = []
  let hasLabRule = false
  for (const sheet of Array.from(document.styleSheets)) {
    let href = sheet.href || '(inline)'
    try {
      const rules = sheet.cssRules
      for (const r of Array.from(rules)) {
        if (r.cssText && r.cssText.includes('--lab-paper-2') && r.cssText.includes('o_main_navbar')) {
          hasLabRule = true
        }
      }
    } catch (e) {
      // cross-origin khong doc duoc cssRules
    }
    sheetInfo.push(href)
  }
  return {
    exists: !!nav,
    inlineStyle: nav ? nav.getAttribute('style') : null,
    className: nav ? nav.className : null,
    bg: cs ? cs.backgroundColor : null,
    bgImage: cs ? cs.backgroundImage : null,
    parentTag: nav && nav.parentElement ? nav.parentElement.tagName + '.' + nav.parentElement.className : null,
    sheets: sheetInfo,
    hasLabRule,
  }
})

process.stdout.write(JSON.stringify(info, null, 2) + '\n')
await browser.close()
