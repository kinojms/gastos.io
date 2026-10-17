import { readFile } from 'node:fs/promises'
import { expect, test, type Page } from '@playwright/test'

async function add(page: Page, digits: string) {
  for (const ch of digits) await page.getByRole('button', { name: ch, exact: true }).click()
  await page.getByRole('button', { name: 'Enter', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Added')
}

async function openSettings(page: Page) {
  await page.getByRole('button', { name: 'More' }).click()
  await page.getByRole('menuitem', { name: 'Settings' }).click()
  await expect(page.getByText('Voice language')).toBeVisible()
}

test.beforeEach(async ({ page }) => {
  await page.goto('./')
  await expect(page.locator('.name')).toHaveText('CASH')
})

test('a backup file can be saved, then restored over newer data', async ({ page }) => {
  await add(page, '7')
  await openSettings(page)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Save backup file' }).click()
  const file = await download
  expect(file.suggestedFilename()).toMatch(/^gastos-backup-\d{4}-\d{2}-\d{2}\.json$/)
  const path = await file.path()
  const backup = JSON.parse(await readFile(path, 'utf8'))
  expect(backup.schemaVersion).toBe(2)
  expect(backup.expenses).toHaveLength(1)
  await expect(page.getByText(/^Last file backup:/)).toBeVisible()

  await page.goBack()
  await add(page, '3')
  await openSettings(page)
  await page.locator('input[type=file]').setInputFiles(path)
  await expect(page.getByRole('alertdialog')).toContainText('Replace all expenses with this backup (1 expense from')
  await page.getByRole('button', { name: 'Restore', exact: true }).last().click()
  await expect(page.getByText('Restored')).toBeVisible()

  await page.goBack()
  await page.getByRole('button', { name: 'Expenses list' }).click()
  await expect(page.locator('.row')).toHaveCount(1)
  await expect(page.locator('.row .amount')).toHaveText('₱7.00')
})

test('a file that is not a backup is refused and changes nothing', async ({ page }) => {
  await add(page, '7')
  await openSettings(page)
  await page.locator('input[type=file]').setInputFiles({ name: 'photo.json', mimeType: 'application/json', buffer: Buffer.from('not json') })
  await expect(page.locator('.result')).toHaveText("This backup file is damaged or isn't a Gastos.io backup.")
  await expect(page.getByRole('alertdialog')).toHaveCount(0)
})

test('changing the theme confirms, then applies it', async ({ page }) => {
  await openSettings(page)
  await page.getByRole('button', { name: 'Nord' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Change theme to Nord?')
  await page.getByRole('button', { name: 'Change', exact: true }).click()
  await expect
    .poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--bg').trim().toLowerCase()))
    .toBe('#eceff4')
  await page.reload()
  await expect
    .poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--bg').trim().toLowerCase()))
    .toBe('#eceff4')
})

test('saving a repo asks to confirm and rejects a malformed one', async ({ page }) => {
  await openSettings(page)
  await page.getByLabel('GitHub repo').fill('nope')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Use the format owner/repo')
  await page.getByLabel('GitHub repo').fill('me/data')
  await page.getByLabel('GitHub token').fill('ghp_x')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Save backup settings?')
  await page.getByRole('button', { name: 'Save', exact: true }).last().click()
  await expect(page.getByText('Token saved. Leave empty to keep it.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Start fresh' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Back up now' })).toBeDisabled()
})
