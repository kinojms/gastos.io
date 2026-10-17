import { expect, test, type Page } from '@playwright/test'
import pkg from '../package.json' with { type: 'json' }
import { openHome } from './helpers'

// Requests from inside the service worker skip page.route, so keep it out of these tests.
test.use({ serviceWorkers: 'block' })

/** What the real relay answers, preflight included. */
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
}

async function openFeedback(page: Page) {
  await openHome(page)
  await page.getByRole('button', { name: 'More' }).click()
  await page.getByRole('menuitem', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Submit feedback' }).click()
  await expect(page.getByRole('heading', { name: 'Submit feedback' })).toBeVisible()
}

test('sending feedback confirms, posts to the relay and thanks the user', async ({ page }) => {
  let posted: Record<string, unknown> | null = null
  await page.route('https://relay.test/**', async (route) => {
    if (route.request().method() === 'POST') posted = route.request().postDataJSON()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: CORS,
      body: '{"ok":true}',
    })
  })
  await openFeedback(page)
  const send = page.getByRole('button', { name: 'Send', exact: true })
  await expect(send).toBeDisabled()

  await page.getByLabel('Type').selectOption('Bug report')
  await page.getByLabel('Message').fill('The total is wrong')
  await page.getByLabel('Your name or contact (optional)').fill('Ana')
  await expect(page.getByText('18 / 2000')).toBeVisible()
  await send.click()
  await expect(page.getByRole('alertdialog')).toContainText('Send this feedback?')
  await page.getByRole('button', { name: 'Send', exact: true }).last().click()

  await expect(page.getByRole('status')).toHaveText('Thanks! Your feedback was sent.')
  await expect(page.getByText('Voice language')).toBeVisible() // back on Settings
  expect(posted).toEqual({ type: 'Bug report', message: 'The total is wrong', contact: 'Ana', appVersion: pkg.version, platform: 'iPhone' })
})

test('a failed send shows why and keeps the text', async ({ page }) => {
  await page.route('https://relay.test/**', (route) =>
    route.fulfill({ status: 502, contentType: 'application/json', headers: CORS, body: '{"ok":false}' }),
  )
  await openFeedback(page)
  await page.getByLabel('Message').fill('Please add budgets')
  await page.getByRole('button', { name: 'Send', exact: true }).click()
  await page.getByRole('button', { name: 'Send', exact: true }).last().click()
  await expect(page.getByRole('alert')).toHaveText("Couldn't send feedback. Try again later.")
  await expect(page.getByLabel('Message')).toHaveValue('Please add budgets')
})
