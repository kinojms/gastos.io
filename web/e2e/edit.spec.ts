import { expect, test, type Page } from '@playwright/test'
import { openHome } from './helpers'

async function add(page: Page, digits: string) {
  for (const ch of digits) await page.getByRole('button', { name: ch, exact: true }).click()
  await page.getByRole('button', { name: 'Enter', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Added')
}

test.beforeEach(async ({ page }) => {
  await openHome(page)
  await add(page, '10')
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await page.locator('.row').click()
})

test('editing an expense asks to confirm, then updates the row', async ({ page }) => {
  await page.getByLabel('Amount').fill('12')
  await page.getByPlaceholder('Add notes…').fill('late, "really"')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Save changes to ₱12.00 · Food?')
  await page.getByRole('button', { name: 'Save', exact: true }).last().click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.row .amount')).toHaveText('₱12.00')
})

test('an invalid amount disables Save', async ({ page }) => {
  await page.getByLabel('Amount').fill('')
  await expect(page.getByRole('button', { name: 'Save', exact: true })).toBeDisabled()
})

test('deleting asks to confirm, then empties the list', async ({ page }) => {
  await page.getByRole('dialog').getByRole('button', { name: 'Delete', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Delete ₱10.00 · Food?')
  await page.getByRole('button', { name: 'Delete', exact: true }).last().click()
  await expect(page.getByText('No expenses')).toBeVisible()
})

test('cancelling the confirm keeps the sheet open', async ({ page }) => {
  await page.getByRole('dialog').getByRole('button', { name: 'Delete', exact: true }).click()
  await page.getByRole('button', { name: 'Cancel' }).click()
  await expect(page.getByRole('alertdialog')).toHaveCount(0)
  await expect(page.getByText('Edit expense')).toBeVisible()
})
