import { describe, expect, it } from 'vitest'
import { parseHash } from './router.svelte'

describe('parseHash', () => {
  it('opens Submit feedback only while the relay is configured', () => {
    expect(parseHash('#/feedback', true)).toEqual({ name: 'feedback' })
    expect(parseHash('#/feedback', false)).toEqual({ name: 'home' })
  })

  it('still parses the other routes', () => {
    expect(parseHash('#/expenses?tab=days', false)).toEqual({ name: 'expenses', tab: 'days' })
    expect(parseHash('#/expenses/day/2026-10-16', false)).toEqual({ name: 'day', date: '2026-10-16' })
    expect(parseHash('', false)).toEqual({ name: 'home' })
  })
})
