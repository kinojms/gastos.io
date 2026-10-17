<script lang="ts" generics="T extends { id: number }">
  import type { Snippet } from 'svelte'

  let {
    items,
    onmove,
    row,
  }: {
    items: T[]
    /** The dragged item moved from one index to another. */
    onmove: (from: number, to: number) => void
    row: Snippet<[T]>
  } = $props()

  let elements: HTMLElement[] = []
  let dragging: { index: number; pointerId: number } | null = $state(null)

  function down(e: PointerEvent, index: number) {
    dragging = { index, pointerId: e.pointerId }
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  function move(e: PointerEvent) {
    if (!dragging || dragging.pointerId !== e.pointerId) return
    const target = elements.findIndex((el) => {
      const r = el?.getBoundingClientRect()
      return r && e.clientY >= r.top && e.clientY < r.bottom
    })
    if (target >= 0 && target !== dragging.index) {
      onmove(dragging.index, target)
      dragging.index = target
    }
  }

  function up() {
    dragging = null
  }
</script>

<div class="list">
  {#each items as item, i (item.id)}
    <div class="item" class:lifted={dragging?.index === i} bind:this={elements[i]}>
      <button
        class="handle"
        aria-label="Drag to reorder"
        onpointerdown={(e) => down(e, i)}
        onpointermove={move}
        onpointerup={up}
        onpointercancel={up}
      >&#8942;&#8942;</button>
      {@render row(item)}
      <button class="sr-only" disabled={i === 0} onclick={() => onmove(i, i - 1)}>Move up</button>
      <button class="sr-only" disabled={i === items.length - 1} onclick={() => onmove(i, i + 1)}>Move down</button>
    </div>
  {/each}
</div>

<style>
  .list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .item {
    position: relative;
    display: flex;
    align-items: center;
    border-radius: 12px;
    background: var(--surface);
    padding: 0 12px 0 0;
  }

  .item.lifted {
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
    z-index: 1;
  }

  .handle {
    width: 44px;
    height: 52px;
    border: 0;
    background: none;
    color: var(--muted);
    touch-action: none;
    letter-spacing: -2px;
    cursor: grab;
  }
</style>
