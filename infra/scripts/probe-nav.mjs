import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const pwEntry = pathToFileURL(join(ROOT, 'demo', 'tools', 'node_modules', 'playwright', 'index.mjs')).href
const { chromium } = await import(pwEntry)
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto('http://localhost:8069/web/login', { waitUntil: 'domcontentloaded' })
await page.fill('input[name="login"]', 'manager01')
await page.fill('input[name="password"]', 'lab@2026')
await page.click('button[type="submit"]')
await page.waitForTimeout(3000)
await page.goto('http://localhost:8069/web#menu_id=332&action=487', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(4000)
const out = await page.evaluate(() => {
  const nav = [...document.querySelectorAll('.o_main_navbar a, .o_main_navbar .dropdown-item')].map((a) => ({
    text: (a.textContent || '').trim().slice(0, 22),
    cls: String(a.className).slice(0, 80),
    bg: getComputedStyle(a).backgroundColor,
    color: getComputedStyle(a).color,
  }))
  const bc = document.querySelector('.o_control_panel .o_breadcrumb')
  const bcInfo = bc ? {
    blockCls: String(bc.className),
    items: [...bc.querySelectorAll('.breadcrumb-item, li, a')].map((e) => ({
      text: (e.textContent || '').trim().slice(0, 20),
      cls: String(e.className).slice(0, 50),
      borderBottom: getComputedStyle(e).borderBottom,
      bg: getComputedStyle(e).backgroundColor,
    })),
  } : null
  return { nav, bcInfo }
})
console.log(JSON.stringify(out, null, 1))
await browser.close()