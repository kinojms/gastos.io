import { expect, test, type Page } from '@playwright/test'
import { openHome } from './helpers'

async function add(page: Page, digits: string) {
  for (const ch of digits) await page.getByRole('button', { name: ch, exact: true }).click()
  await page.getByRole('button', { name: 'Enter', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Added')
}

test.beforeEach(async ({ page }) => {
  await openHome(page)
})

test('Today lists the day, Days lists it by date, Month totals by label and payment', async ({ page }) => {
  await add(page, '12')
  await add(page, '30')
  await page.getByRole('button', { name: 'Expenses list' }).click()

  await expect(page.getByRole('tab', { name: 'Today' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.locator('.row')).toHaveCount(2)
  await expect(page.locator('.total')).toHaveText('₱42.00')

  await page.getByRole('tab', { name: 'Days' }).click()
  await expect(page.locator('.row')).toHaveCount(1)
  await expect(page.locator('.row .sub')).toHaveText('2 expenses')
  await page.locator('.row').click()
  await expect(page.locator('.total')).toHaveText('₱42.00')
  await page.goBack()

  await page.getByRole('tab', { name: 'Month' }).click()
  await expect(page.locator('.total')).toHaveText('₱42.00')
  await expect(page.getByText('By label')).toBeVisible()
  await expect(page.getByText('Food').first()).toBeVisible()
  await expect(page.getByText('Cash').first()).toBeVisible()
})

test('exporting a CSV downloads a file named for the day', async ({ page }) => {
  await add(page, '5')
  await page.getByRole('button', { name: 'Expenses list' }).click()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Export CSV' }).click()
  expect((await download).suggestedFilename()).toMatch(/^expenses-\d{4}-\d{2}-\d{2}\.csv$/)
})
