<script lang="ts">
  import type { Key } from '../domain/amountInput'

  let { onKey }: { onKey: (key: Key) => void } = $props()

  const rows: { label: string; key: Key; aria?: string }[][] = [
    [1, 2, 3].map((n) => ({ label: String(n), key: { digit: n } })),
    [4, 5, 6].map((n) => ({ label: String(n), key: { digit: n } })),
    [7, 8, 9].map((n) => ({ label: String(n), key: { digit: n } })),
    [
      { label: '.', key: 'dot', aria: 'Decimal point' },
      { label: '0', key: { digit: 0 } },
      { label: '⌫', key: 'backspace', aria: 'Backspace' },
    ],
  ]

  // Keys fire on pointerdown for speed. The click that follows a touch is ignored; a click with no
  // pointerdown before it (VoiceOver, keyboard) still works.
  let lastDown = 0

  function down(key: Key) {
    lastDown = Date.now()
    const active = document.activeElement
    if (active instanceof HTMLElement && active !== document.body) active.blur()
    onKey(key)
  }

  function click(key: Key) {
    if (Date.now() - lastDown < 700) return
    onKey(key)
  }
</script>

<div class="pad no-select" data-guide="numpad">
  {#each rows as row}
    <div class="row">
      {#each row as k}
        <button
          class="key"
          aria-label={k.aria}
          onpointerdown={() => down(k.key)}
          onclick={() => click(k.key)}
        >{k.label}</button>
      {/each}
    </div>
  {/each}
</div>

<style>
  .pad {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 0 14px;
  }

  .row {
    display: flex;
    gap: 8px;
  }

  .key {
    flex: 1;
    height: 54px;
    border: 0;
    border-radius: 14px;
    background: var(--key);
    color: var(--fg);
    font-size: 22px;
    font-weight: 600;
  }

  .key:active {
    filter: brightness(0.92);
  }
</style>
