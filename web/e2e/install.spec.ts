import { expect, test } from '@playwright/test'
import { openHome } from './helpers'

test('Safari users see how to install, and dismissing it sticks', async ({ page }) => {
  await openHome(page)
  const banner = page.getByRole('note').filter({ hasText: 'Install: tap' })
  await expect(banner).toContainText("Expenses added here won't move to the Home Screen app.")
  await banner.getByRole('button', { name: 'Dismiss' }).click()
  await expect(banner).toHaveCount(0)
  await page.reload()
  await expect(page.locator('.name')).toHaveText('CASH')
  await expect(page.getByRole('note').filter({ hasText: 'Install: tap' })).toHaveCount(0)
})

test('no backup reminder on a brand-new install', async ({ page }) => {
  await openHome(page)
  await expect(page.getByRole('note').filter({ hasText: 'Back up your expenses' })).toHaveCount(0)
})
