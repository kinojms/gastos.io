import { expect, test, type Page } from '@playwright/test'

async function add(page: Page, digits: string) {
  for (const ch of digits) await page.getByRole('button', { name: ch, exact: true }).click()
  await page.getByRole('button', { name: 'Enter', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Added')
}

test.beforeEach(async ({ page }) => {
  await page.goto('./')
  await expect(page.locator('.name')).toHaveText('CASH')
})

async function openLabels(page: Page) {
  await page.getByRole('button', { name: 'More' }).click()
  await page.getByRole('menuitem', { name: 'Labels' }).click()
  await expect(page.getByText('+ Add label')).toBeVisible()
}

test('adding a label asks to confirm, rejects duplicates', async ({ page }) => {
  await openLabels(page)
  await page.getByText('+ Add label').click()
  await page.getByLabel('Name').fill('food')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('That label already exists')

  await page.getByLabel('Name').fill('Coffee')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Add label Coffee?')
  await page.getByRole('button', { name: 'Add', exact: true }).click()
  await expect(page.locator('.name', { hasText: 'Coffee' })).toBeVisible()
})

test('deleting a label with an expense says it moves to Other', async ({ page }) => {
  await page.locator('[data-guide=swipe]').boundingBox().then(async (box) => {
    const x = box!.x + box!.width / 2
    const y = box!.y + box!.height / 2
    // Up twice: Food -> Transpo -> Hobbies
    for (let i = 0; i < 2; i++) {
      await page.mouse.move(x, y)
      await page.mouse.down()
      await page.mouse.move(x, y - 100, { steps: 6 })
      await page.mouse.up()
    }
  })
  await expect(page.locator('.label')).toHaveText('Hobbies')
  await add(page, '5')

  await openLabels(page)
  await page.locator('.row', { hasText: 'Hobbies' }).click()
  await page.getByRole('button', { name: 'Delete label' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Delete label Hobbies? 1 expense moves to Other.')
  await page.getByRole('button', { name: 'Delete', exact: true }).last().click()
  await expect(page.locator('.row', { hasText: 'Hobbies' })).toHaveCount(0)
})

test('Other cannot be deleted', async ({ page }) => {
  await openLabels(page)
  await page.locator('.row', { hasText: 'Other' }).click()
  await expect(page.getByRole('button', { name: 'Delete label' })).toHaveCount(0)
})

test('payment methods: add, and the delete button is hidden on the last one', async ({ page }) => {
  await page.getByRole('button', { name: 'More' }).click()
  await page.getByRole('menuitem', { name: 'Payment methods' }).click()
  await page.getByText('+ Add payment method').click()
  await page.getByLabel('Name').fill('Wise')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Add payment method Wise?')
  await page.getByRole('button', { name: 'Add', exact: true }).click()
  await expect(page.locator('.name', { hasText: 'Wise' })).toBeVisible()

  await page.locator('.row', { hasText: 'GCash' }).click()
  await expect(page.getByRole('button', { name: 'Delete payment method' })).toBeVisible()
})

test('reordering labels shows Save order, confirms, and changes the swipe order', async ({ page }) => {
  await openLabels(page)
  await page.getByRole('button', { name: 'Move down' }).first().dispatchEvent('click') // Food moves below Transpo (screen-reader button)
  await page.getByRole('button', { name: 'Save order' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Save new label order?')
  await page.getByRole('button', { name: 'Save', exact: true }).last().click()
  await expect(page.getByRole('button', { name: 'Save order' })).toHaveCount(0)
  await expect(page.locator('.name').first()).toHaveText('Transpo')
  await page.goBack()
  // With nothing picked yet, Home starts on the first label in the saved order.
  await expect(page.locator('.label')).toHaveText('Transpo')
})
