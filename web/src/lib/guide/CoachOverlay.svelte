<script lang="ts">
  import { onMount } from 'svelte'
  import { GUIDE_TOTAL, type GuideStep } from './guide.svelte'
  import { bubbleTop, cutout, union, type Rect } from './coachLayout'

  let { step, onnext, onskip }: { step: GuideStep; onnext: () => void; onskip: () => void } = $props()

  const PADDING = 8
  const SMALL_PADDING = 3 // the history icon sits right beside the menu button
  const MARGIN = 16

  let hole = $state<Rect | null>(null)
  let screen = $state({ width: 0, height: 0, top: 0, bottom: 0 })
  let bubbleHeight = $state(0)
  let bubble: HTMLElement | undefined = $state()
  let probe: HTMLElement | undefined = $state()

  const reducedMotion =
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

  function measure() {
    const rects = step.targets.flatMap((name) => {
      const el = document.querySelector(`[data-guide="${name}"]`)
      if (!el) return []
      const r = el.getBoundingClientRect()
      return [{ left: r.left, top: r.top, right: r.right, bottom: r.bottom }]
    })
    const target = union(rects)
    const next = target ? cutout(target, step.targets.includes('history') ? SMALL_PADDING : PADDING) : null
    if (next?.left !== hole?.left || next?.top !== hole?.top || next?.right !== hole?.right || next?.bottom !== hole?.bottom) {
      hole = next
    }
    if (probe) {
      const cs = getComputedStyle(probe)
      screen = {
        width: window.innerWidth,
        height: window.innerHeight,
        top: parseFloat(cs.paddingTop) || 0,
        bottom: parseFloat(cs.paddingBottom) || 0,
      }
    }
    if (bubble && bubble.offsetHeight !== bubbleHeight) bubbleHeight = bubble.offsetHeight
  }

  // Layout shifts (keyboard, rotation, the reminder card appearing) move the targets, so re-read every frame.
  onMount(() => {
    let frame = 0
    const loop = () => {
      measure()
      frame = requestAnimationFrame(loop)
    }
    loop()
    return () => cancelAnimationFrame(frame)
  })

  const holeBox = $derived(
    hole ? { left: hole.left, top: hole.top, width: hole.right - hole.left, height: hole.bottom - hole.top } : null,
  )

  const bubbleY = $derived.by(() => {
    const usable = screen.height - screen.top - screen.bottom
    if (!hole) return (screen.height - bubbleHeight) / 2
    const shifted = { ...hole, top: hole.top - screen.top, bottom: hole.bottom - screen.top }
    return bubbleTop(shifted, bubbleHeight, usable, MARGIN) + screen.top
  })

  const swipe = $derived(step.swipeAxis !== null)
  const vertical = $derived(step.swipeAxis === 'LABEL')
  const reach = $derived(
    holeBox ? Math.min((vertical ? holeBox.height : holeBox.width) * 0.3, 120) : 0,
  )
</script>

<div class="overlay" role="dialog" aria-label="Guided tour">
  <div class="probe" bind:this={probe} aria-hidden="true"></div>

  {#if holeBox}
    <div
      class="hole"
      class:still={reducedMotion}
      style:left="{holeBox.left}px"
      style:top="{holeBox.top}px"
      style:width="{holeBox.width}px"
      style:height="{holeBox.height}px"
    ></div>

    <!-- Everything outside the hole swallows touches. Inside it, only swipe steps let touches through. -->
    <div class="block" class:still={reducedMotion} style:left="0" style:right="0" style:top="0" style:height="{holeBox.top}px"></div>
    <div class="block" class:still={reducedMotion} style:left="0" style:right="0" style:top="{holeBox.top + holeBox.height}px" style:bottom="0"></div>
    <div
      class="block"
      class:still={reducedMotion}
      style:left="0"
      style:width="{holeBox.left}px"
      style:top="{holeBox.top}px"
      style:height="{holeBox.height}px"
    ></div>
    <div
      class="block"
      class:still={reducedMotion}
      style:left="{holeBox.left + holeBox.width}px"
      style:right="0"
      style:top="{holeBox.top}px"
      style:height="{holeBox.height}px"
    ></div>
    {#if !swipe}
      <div
        class="block"
        class:still={reducedMotion}
        style:left="{holeBox.left}px"
        style:width="{holeBox.width}px"
        style:top="{holeBox.top}px"
        style:height="{holeBox.height}px"
      ></div>
    {/if}

    {#if swipe}
      <div
        class="hand"
        class:animated={!reducedMotion}
        style:left="{holeBox.left + holeBox.width / 2}px"
        style:top="{holeBox.top + holeBox.height / 2}px"
        style:--dx="{vertical ? 0 : -reach}px"
        style:--dy="{vertical ? -reach : 0}px"
        aria-hidden="true"
      >
        <span class="tip"></span>
        {#if reducedMotion}
          <span class="arrow a1">{vertical ? '˄' : '‹'}</span>
          <span class="arrow a2">{vertical ? '˅' : '›'}</span>
        {/if}
      </div>
    {/if}
  {:else}
    <div class="block" style:inset="0"></div>
  {/if}

  <div
    class="bubble"
    bind:this={bubble}
    style:top="{bubbleY}px"
  >
    <p class="text" aria-live="polite">{step.text}</p>
    <div class="row">
      <span class="count">{step.number} of {GUIDE_TOTAL}</span>
      <span class="spacer"></span>
      <button class="skip" onclick={onskip}>Skip</button>
      {#if !swipe}
        <button class="next" onclick={onnext}>{step.isLast ? 'Done' : 'Next'}</button>
      {/if}
    </div>
  </div>
</div>

<style>
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 70;
    pointer-events: none;
  }

  .probe {
    position: absolute;
    left: 0;
    top: 0;
    width: 0;
    height: 0;
    padding-top: env(safe-area-inset-top);
    padding-bottom: env(safe-area-inset-bottom);
    visibility: hidden;
  }

  .hole {
    position: fixed;
    border-radius: 16px;
    box-shadow:
      0 0 0 2px var(--accent),
      0 0 0 6px color-mix(in srgb, var(--accent) 35%, transparent),
      0 0 0 100vmax rgba(0, 0, 0, 0.65);
    transition: left 0.25s, top 0.25s, width 0.25s, height 0.25s;
  }

  .block {
    position: fixed;
    pointer-events: auto;
    transition: left 0.25s, top 0.25s, width 0.25s, height 0.25s;
  }

  .still {
    transition: none;
  }

  .hand {
    position: fixed;
    width: 0;
    height: 0;
    pointer-events: none;
  }

  .tip {
    position: absolute;
    left: -16px;
    top: -16px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.92);
    border: 2px solid var(--accent);
  }

  .hand.animated .tip {
    animation: slide 1.6s linear infinite;
  }

  .arrow {
    position: absolute;
    color: #fff;
    font-size: 34px;
    line-height: 1;
    transform: translate(-50%, -50%);
  }

  .a1 {
    left: calc(var(--dx) * -0.4);
    top: calc(var(--dy) * -0.4);
  }

  .a2 {
    left: calc(var(--dx) * 0.4);
    top: calc(var(--dy) * 0.4);
  }

  @keyframes slide {
    0% {
      transform: translate(calc(var(--dx) * -1), calc(var(--dy) * -1));
      opacity: 0;
    }
    10% {
      opacity: 1;
    }
    55% {
      transform: translate(var(--dx), var(--dy));
      opacity: 1;
    }
    70%,
    100% {
      transform: translate(var(--dx), var(--dy));
      opacity: 0;
    }
  }

  .bubble {
    position: fixed;
    left: 16px;
    right: 16px;
    margin: 0 auto;
    max-width: 360px;
    pointer-events: auto;
    background: var(--surface);
    color: var(--fg);
    border-radius: 16px;
    padding: 14px 8px 6px 16px;
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.4);
    transition: top 0.25s;
  }

  .text {
    margin: 0 8px 6px 0;
    font-size: 16px;
    font-weight: 600;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .count {
    font-size: 13px;
    color: var(--muted);
  }

  .spacer {
    flex: 1;
  }

  .skip {
    min-height: 44px;
    border: 0;
    background: none;
    padding: 0 12px;
    font-weight: 600;
  }

  .next {
    min-height: 44px;
    border: 0;
    border-radius: 12px;
    padding: 0 18px;
    background: var(--accent);
    color: var(--on-accent);
    font-weight: 700;
  }
</style>
