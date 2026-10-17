import { expect, test, type Page } from '@playwright/test'

const tour = (page: Page) => page.getByRole('dialog', { name: 'Guided tour' })

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

test('the tour walks through five steps, then stays away after a reload', async ({ page }) => {
  await page.goto('./')
  await expect(tour(page)).toContainText('Enter the amount of your expense')
  await expect(tour(page)).toContainText('1 of 6')

  // Input behind the tour is blocked: a tap on the "5" key does nothing.
  const key = (await page.getByRole('button', { name: '5', exact: true }).boundingBox())!
  await page.mouse.click(key.x + key.width / 2, key.y + key.height / 2)
  await expect(page.locator('.value')).toHaveText('0')

  await tour(page).getByRole('button', { name: 'Next' }).click()
  await expect(tour(page)).toContainText('You can change which payment method you used for this expense')
  await expect(tour(page).getByRole('button', { name: 'Next' })).toHaveCount(0)

  await drag(page, 5, -100) // wrong axis: nothing happens
  await expect(tour(page)).toContainText('2 of 6')
  await expect(page.locator('.label')).toHaveText('Food') // a wrong-axis swipe changes nothing

  await drag(page, -100, 5)
  await expect(tour(page)).toContainText('Then, you can choose from a set of expense types')
  await expect(page.locator('.name')).toHaveText('GCASH')

  await drag(page, 5, -100)
  await expect(tour(page)).toContainText('(ex: Jollibee or MTG Cards)')
  await expect(page.locator('.label')).toHaveText('Transpo') // swipes during the tour really change the label
  await tour(page).getByRole('button', { name: 'Next' }).click()
  await expect(tour(page)).toContainText('Finally, keep track of your expenses history here!')
  await tour(page).getByRole('button', { name: 'Next' }).click()
  await expect(tour(page)).toContainText('In your history, swipe an expense left to edit or delete it')
  await expect(tour(page)).toContainText('6 of 6')
  await tour(page).getByRole('button', { name: 'Done' }).click()
  await expect(tour(page)).toHaveCount(0)

  await page.reload()
  await expect(page.locator('.name')).toHaveText('GCASH')
  await expect(tour(page)).toHaveCount(0)
})

test('Skip ends the tour at once and it can be replayed from Settings', async ({ page }) => {
  await page.goto('./')
  await tour(page).getByRole('button', { name: 'Skip' }).click()
  await expect(tour(page)).toHaveCount(0)

  await page.getByRole('button', { name: 'More' }).click()
  await page.getByRole('menuitem', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Show guide' }).click()
  await expect(tour(page)).toContainText('1 of 6')
  await expect(page.locator('.name')).toBeVisible()
})
