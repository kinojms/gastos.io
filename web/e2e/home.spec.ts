import { expect, test, type Page } from '@playwright/test'
import { openHome } from './helpers'

async function type(page: Page, text: string) {
  for (const ch of text) {
    const name = ch === '.' ? 'Decimal point' : ch
    await page.getByRole('button', { name, exact: true }).click()
  }
}

async function drag(page: Page, dx: number, dy: number) {
  const box = (await page.locator('[data-guide=swipe]').boundingBox())!
  const x = box.x + box.width / 2
  const y = box.y + box.height / 2
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x + dx / 2, y + dy / 2, { steps: 4 })
  await page.mouse.move(x + dx, y + dy, { steps: 4 })
  await page.mouse.up()
}

test.beforeEach(async ({ page }) => {
  await openHome(page)
})

test('typing an amount and pressing Enter saves it for today', async ({ page }) => {
  await type(page, '45.5')
  await expect(page.locator('.value')).toHaveText('45.5')
  await page.getByRole('button', { name: 'Enter', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('Added ₱45.50 · Food · Cash')
  await expect(page.locator('.total')).toHaveText('Today ₱45.50')
  await expect(page.locator('.value')).toHaveText('0')
})

test('two quick Enter taps save one expense', async ({ page }) => {
  await type(page, '1')
  const enter = page.getByRole('button', { name: 'Enter', exact: true })
  await enter.dblclick()
  await expect(page.locator('.total')).toHaveText('Today ₱1.00')
})

test('swiping left changes the payment method, swiping up changes the label', async ({ page }) => {
  await drag(page, -100, 5)
  await expect(page.locator('.name')).toHaveText('GCASH')
  await drag(page, 100, 5)
  await expect(page.locator('.name')).toHaveText('CASH')
  await drag(page, 5, -100)
  await expect(page.locator('.label')).toHaveText('Transpo')
  await drag(page, 0, 20) // too short to count
  await expect(page.locator('.label')).toHaveText('Transpo')
})

test('the selection survives a reload', async ({ page }) => {
  await drag(page, -100, 5)
  await expect(page.locator('.name')).toHaveText('GCASH')
  await page.reload()
  await expect(page.locator('.name')).toHaveText('GCASH')
})

test('double-tap zoom is off for the whole page', async ({ page }) => {
  const touchAction = await page.evaluate(() => getComputedStyle(document.documentElement).touchAction)
  expect(touchAction).toBe('manipulation')
})

test('Home never pans or bounces the page (iOS drags on the keypad moved the whole screen)', async ({ page }) => {
  const styles = await page.evaluate(() => ({
    home: getComputedStyle(document.querySelector('.home')!).touchAction,
    htmlOverflow: getComputedStyle(document.documentElement).overflow,
    bodyOverflow: getComputedStyle(document.body).overflow,
  }))
  expect(styles).toEqual({ home: 'none', htmlOverflow: 'hidden', bodyOverflow: 'hidden' })
})
