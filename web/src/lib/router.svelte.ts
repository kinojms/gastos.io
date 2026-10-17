import { feedbackEnabled } from './feedback/config'

export type Tab = 'today' | 'days' | 'month'

export type Route =
  | { name: 'home' }
  | { name: 'expenses'; tab?: Tab }
  | { name: 'day'; date: string }
  | { name: 'labels' }
  | { name: 'methods' }
  | { name: 'settings' }
  | { name: 'feedback' }

/** `feedbackOn` is false while no relay is configured; #/feedback then falls back to Home. */
export function parseHash(hash: string, feedbackOn: boolean = feedbackEnabled): Route {
  const raw = hash.replace(/^#/, '') || '/'
  const [path, query = ''] = raw.split('?')
  const params = new URLSearchParams(query)
  if (path === '/expenses') {
    const tab = params.get('tab')
    return { name: 'expenses', tab: tab === 'today' || tab === 'days' || tab === 'month' ? tab : undefined }
  }
  const day = /^\/expenses\/day\/(\d{4}-\d{2}-\d{2})$/.exec(path)
  if (day) return { name: 'day', date: day[1] }
  if (path === '/labels') return { name: 'labels' }
  if (path === '/methods') return { name: 'methods' }
  if (path === '/settings') return { name: 'settings' }
  if (path === '/feedback') return feedbackOn ? { name: 'feedback' } : { name: 'home' }
  return { name: 'home' }
}

export const router = $state<{ route: Route }>({ route: parseHash(location.hash) })

window.addEventListener('hashchange', () => {
  router.route = parseHash(location.hash)
})

export function navigate(path: string): void {
  location.hash = path
}

export function back(): void {
  history.back()
}
