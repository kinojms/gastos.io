import { expect, type Page } from '@playwright/test'

/** Opens the app on a fresh phone and skips the first-run tour (guide.spec.ts covers the tour itself). */
export async function openHome(page: Page) {
  await page.goto('./')
  await page.getByRole('button', { name: 'Skip' }).click()
  await expect(page.getByRole('dialog', { name: 'Guided tour' })).toHaveCount(0)
  await expect(page.locator('.name')).toHaveText('CASH')
}
