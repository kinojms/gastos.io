<script lang="ts">
  import { onMount, type Snippet } from 'svelte'
  import { openRow } from './openRow.svelte'
  import { ACTION_WIDTH, OPEN_OFFSET, dragOffset, lockAxis, settle } from './swipeRow'

  let {
    id,
    onedit,
    ondelete,
    peek = false,
    onpeekstart,
    label,
    children,
  }: {
    id: number
    /** Names the expense in the actions' accessible names, e.g. "₱30.00 · Food". */
    label: string
    onedit: () => void
    ondelete: () => void
    /** Slide open once on mount to show the actions exist. */
    peek?: boolean
    onpeekstart?: () => void
    children: Snippet
  } = $props()

  const reducedMotion = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

  let root: HTMLElement | undefined = $state()
  let offset = $state(0)
  let dragging = $state(false)
  let gesture: {
    pointerId: number
    x: number
    y: number
    start: number
    axis: 'h' | 'v' | null
    lastX: number
    lastT: number
    vx: number
  } | null = null
  /** Set when a touch did something other than a plain tap, so the row's click is swallowed. */
  let swallowClick = false
  /** A plain tap that started on an open row: it closes the row instead of opening Edit. */
  let tapClosesRow = false
  let peekTimers: ReturnType<typeof setTimeout>[] = []

  function setOpen(open: boolean) {
    offset = open ? OPEN_OFFSET : 0
    if (open) openRow.id = id
    else if (openRow.id === id) openRow.id = null
  }

  // Another row opened (or the list scrolled): close this one.
  $effect(() => {
    if (openRow.id !== id && offset !== 0 && !dragging && peekTimers.length === 0) offset = 0
  })

  function cancelPeek() {
    peekTimers.forEach(clearTimeout)
    peekTimers = []
  }

  onMount(() => {
    if (!peek) return
    onpeekstart?.()
    peekTimers = [
      setTimeout(() => (offset = OPEN_OFFSET), 300),
      setTimeout(() => {
        offset = 0
        peekTimers = []
      }, 1300),
    ]
    return cancelPeek
  })

  function down(e: PointerEvent) {
    if (peekTimers.length) {
      cancelPeek()
      offset = 0
    }
    tapClosesRow = offset !== 0
    swallowClick = tapClosesRow
    gesture = { pointerId: e.pointerId, x: e.clientX, y: e.clientY, start: offset, axis: null, lastX: e.clientX, lastT: e.timeStamp, vx: 0 }
  }

  function move(e: PointerEvent) {
    const g = gesture
    if (!g || g.pointerId !== e.pointerId) return
    const dx = e.clientX - g.x
    if (g.axis === null) {
      g.axis = lockAxis(dx, e.clientY - g.y)
      if (g.axis === 'v') swallowClick = true // a scroll or drag is never a tap
      if (g.axis === 'h') {
        dragging = true
        swallowClick = true
        tapClosesRow = false
        ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
      }
    }
    if (g.axis !== 'h') return
    const dt = e.timeStamp - g.lastT
    if (dt > 0) g.vx = (e.clientX - g.lastX) / dt
    g.lastX = e.clientX
    g.lastT = e.timeStamp
    offset = dragOffset(g.start, dx)
  }

  /** Touch drags fire no click, so forget the swallow once this gesture's click had its chance. */
  function forgetSwallowSoon() {
    setTimeout(() => {
      swallowClick = false
      tapClosesRow = false
    }, 0)
  }

  function up(e: PointerEvent) {
    const g = gesture
    if (!g || g.pointerId !== e.pointerId) return
    gesture = null
    forgetSwallowSoon()
    if (g.axis === 'h') {
      dragging = false
      setOpen(settle(offset, g.vx) === OPEN_OFFSET)
    }
  }

  function cancel() {
    forgetSwallowSoon()
    if (gesture?.axis === 'h') {
      dragging = false
      setOpen(offset < OPEN_OFFSET / 2)
    }
    gesture = null
  }

  function clickCapture(e: MouseEvent) {
    if (!swallowClick) return
    swallowClick = false
    e.stopPropagation()
    e.preventDefault()
    if (tapClosesRow) {
      tapClosesRow = false
      setOpen(false)
    }
  }

  function outside(e: PointerEvent) {
    if (offset !== 0 && root && !root.contains(e.target as Node)) setOpen(false)
  }

  function act(action: () => void) {
    setOpen(false)
    action()
  }
</script>

<svelte:window onpointerdown={outside} />

<div class="swipe-row" bind:this={root} data-open={offset !== 0}>
  <div
    class="content"
    class:animate={!dragging && !reducedMotion}
    style:transform="translateX({offset}px)"
    role="presentation"
    onpointerdown={down}
    onpointermove={move}
    onpointerup={up}
    onpointercancel={cancel}
    onclickcapture={clickCapture}
  >
    {@render children()}
  </div>
  <!-- After the row in the DOM (CSS puts them underneath), so VoiceOver reads the expense first; they stay
       reachable while hidden, so VoiceOver users don't need to swipe. -->
  <div class="actions" style:width="{2 * ACTION_WIDTH}px">
    <button class="action edit" aria-label="Edit {label}" tabindex={offset === 0 ? -1 : 0} onclick={() => act(onedit)}>
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
      </svg>
    </button>
    <button class="action delete" aria-label="Delete {label}" tabindex={offset === 0 ? -1 : 0} onclick={() => act(ondelete)}>
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6" />
      </svg>
    </button>
  </div>
</div>

<style>
  .swipe-row {
    position: relative;
    overflow: hidden;
  }

  .actions {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    display: flex;
  }

  .action {
    flex: 1;
    border: 0;
    display: grid;
    place-items: center;
  }

  .edit {
    background: var(--surface2);
    color: var(--fg);
  }

  .delete {
    background: var(--danger);
    color: #fff;
  }

  .content {
    position: relative;
    background: var(--bg);
    touch-action: pan-y;
  }

  .content.animate {
    transition: transform 0.2s ease-out;
  }
</style>
