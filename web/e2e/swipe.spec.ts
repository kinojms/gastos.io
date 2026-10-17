import { expect, test, type Locator, type Page } from '@playwright/test'
import { openHome } from './helpers'

async function add(page: Page, digits: string) {
  for (const ch of digits) await page.getByRole('button', { name: ch, exact: true }).click()
  await page.getByRole('button', { name: 'Enter', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Added')
}

async function drag(page: Page, row: Locator, dx: number, dy: number) {
  const box = (await row.boundingBox())!
  const x = box.x + box.width / 2
  const y = box.y + box.height / 2
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x + dx / 3, y + dy / 3, { steps: 3 })
  await page.mouse.move(x + dx, y + dy, { steps: 5 })
  await page.mouse.up()
}

const rows = (page: Page) => page.locator('.swipe-row')

test.beforeEach(async ({ page }) => {
  await openHome(page)
  await add(page, '12')
  await add(page, '30')
})

test('the first row peeks open once, and never again after a reload', async ({ page }) => {
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await expect(rows(page).first()).toHaveAttribute('data-open', 'true')
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false', { timeout: 3000 })

  await page.reload()
  await expect(rows(page)).toHaveCount(2)
  await page.waitForTimeout(1500)
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false')
})

test('swiping left reveals Edit, which opens the edit sheet', async ({ page }) => {
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false', { timeout: 3000 }) // peek finished
  const second = rows(page).nth(1)
  await drag(page, second, -120, 2)
  await expect(second).toHaveAttribute('data-open', 'true')
  await second.locator('.action.edit').click()
  await expect(page.getByText('Edit expense')).toBeVisible()
})

test('swiping left and tapping Delete confirms, then removes the row', async ({ page }) => {
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false', { timeout: 3000 })
  const first = rows(page).first()
  await drag(page, first, -120, 2)
  await first.locator('.action.delete').click()
  await expect(page.getByRole('alertdialog')).toContainText('Delete ₱30.00 · Food?')
  await page.getByRole('button', { name: 'Delete', exact: true }).last().click()
  await expect(rows(page)).toHaveCount(1)
  await expect(page.locator('.total')).toHaveText('₱12.00')
})

test('a vertical drag never opens a row, and opening one closes the other', async ({ page }) => {
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false', { timeout: 3000 })
  await drag(page, rows(page).first(), 4, 60)
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false')

  await drag(page, rows(page).first(), -120, 2)
  await expect(rows(page).first()).toHaveAttribute('data-open', 'true')
  await drag(page, rows(page).nth(1), -120, 2)
  await expect(rows(page).nth(1)).toHaveAttribute('data-open', 'true')
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false')
})

test('tapping an open row closes it instead of opening Edit', async ({ page }) => {
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await expect(rows(page).first()).toHaveAttribute('data-open', 'false', { timeout: 3000 })
  const first = rows(page).first()
  await drag(page, first, -120, 2)
  await first.locator('.title').click()
  await expect(first).toHaveAttribute('data-open', 'false')
  await expect(page.getByText('Edit expense')).toHaveCount(0)
})
