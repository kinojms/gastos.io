<script lang="ts">
  import type { Label, PaymentMethod } from '../data/models'
  import { displayAmount } from '../domain/amountInput'
  import { SWIPE_THRESHOLD_PX, decideSwipe, type Axis } from '../domain/swipe'

  let {
    method,
    label,
    methods,
    amount,
    onswipe,
    inert = false,
  }: {
    method: PaymentMethod | null
    label: Label | null
    methods: PaymentMethod[]
    amount: string
    /** Every swipe, from touch or from the VoiceOver buttons. */
    onswipe: (axis: Axis, delta: number) => void
    /** True during the tour on steps that don't use the swipe area. */
    inert?: boolean
  } = $props()

  let start: { x: number; y: number; id: number } | null = null

  function down(e: PointerEvent) {
    start = { x: e.clientX, y: e.clientY, id: e.pointerId }
    // Keep receiving the gesture even if the finger leaves the area (or crosses the tour overlay).
    ;(e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId)
  }

  function up(e: PointerEvent) {
    if (!start || start.id !== e.pointerId) return
    const d = decideSwipe(e.clientX - start.x, e.clientY - start.y, SWIPE_THRESHOLD_PX)
    start = null
    if (d.axis !== 'NONE') onswipe(d.axis, d.delta)
  }
</script>

<div
  class="swipe no-select"
  data-guide="swipe"
  role="group"
  aria-label="Payment method, label and amount"
  {inert}
  onpointerdown={down}
  onpointerup={up}
  onpointercancel={() => (start = null)}
>
  <div class="method">
    <span class="hint" aria-hidden="true">&lsaquo;</span>
    <span class="name">{(method?.name ?? '').toUpperCase()}</span>
    <span class="hint" aria-hidden="true">&rsaquo;</span>
  </div>
  <div class="pill">
    <span class="hint small"><svg class="chev" viewBox="0 0 12 8" width="11" height="8" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 6.5l5-5 5 5" /></svg></span>
    <span class="label">{label?.name ?? '...'}</span>
    <span class="hint small"><svg class="chev" viewBox="0 0 12 8" width="11" height="8" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 1.5l5 5 5-5" /></svg></span>
  </div>
  <div class="amount" aria-live="polite">
    <span class="peso">&#8369;</span>
    <span class="value">{displayAmount(amount)}</span>
  </div>
  <div class="dots" aria-hidden="true">
    {#each methods as m (m.id)}
      <span class="dot" class:on={m.id === method?.id}></span>
    {/each}
  </div>

  <!-- VoiceOver users can't swipe this area, so they get buttons. -->
  <button class="sr-only" onclick={() => onswipe('METHOD', -1)}>Previous payment method</button>
  <button class="sr-only" onclick={() => onswipe('METHOD', 1)}>Next payment method</button>
  <button class="sr-only" onclick={() => onswipe('LABEL', -1)}>Previous label</button>
  <button class="sr-only" onclick={() => onswipe('LABEL', 1)}>Next label</button>
</div>

<style>
  .swipe {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    touch-action: none;
    position: relative;
  }

  .method {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .name {
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 1.2px;
  }

  .hint {
    font-size: 18px;
    color: color-mix(in srgb, var(--fg) 40%, transparent);
  }

  .hint.small {
    display: inline-flex;
  }

  .pill {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 12px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--surface) 70%, transparent);
  }

  .label {
    font-size: 14px;
    font-weight: 600;
  }

  .amount {
    display: flex;
    align-items: flex-end;
  }

  .peso {
    font-size: 30px;
    color: var(--muted);
    margin-right: 4px;
    padding-bottom: 5px;
  }

  .value {
    font-size: 50px;
    font-weight: 700;
    letter-spacing: -1px;
    white-space: nowrap;
  }

  .dots {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .dot {
    height: 6px;
    width: 6px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--fg) 20%, transparent);
    transition: width 0.15s;
  }

  .dot.on {
    width: 16px;
    background: color-mix(in srgb, var(--fg) 80%, transparent);
  }
</style>
