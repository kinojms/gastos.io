<script lang="ts">
  import { monthByLabel, monthByMethod } from '../data/expenses'
  import type { Total } from '../data/models'
  import { argbToCss } from '../domain/color'
  import { formatMoney } from '../domain/money'
  import { exportMonth } from '../export/exportFiles'
  import { live } from '../live.svelte'
  import ExportButtons from './ExportButtons.svelte'
  import MonthSwitcher from './MonthSwitcher.svelte'

  let { month, onprev, onnext }: { month: string; onprev: () => void; onnext: () => void } = $props()

  const byLabel = live(() => monthByLabel(month), [] as Total[])
  const byMethod = live(() => monthByMethod(month), [] as Total[])

  const total = $derived(byLabel.value.reduce((s, t) => s + t.totalCentavos, 0))
  const labelMax = $derived(Math.max(0, ...byLabel.value.map((t) => t.totalCentavos)))
  const methodMax = $derived(Math.max(0, ...byMethod.value.map((t) => t.totalCentavos)))
</script>

{#snippet bars(items: Total[], max: number)}
  {#each items as t (t.id)}
    <div class="bar">
      <div class="line"><span>{t.name}</span><span>{formatMoney(t.totalCentavos)}</span></div>
      <div class="track">
        <div class="fill" style:width="{max > 0 ? (t.totalCentavos / max) * 100 : 0}%" style:background={argbToCss(t.colorArgb)}></div>
      </div>
    </div>
  {/each}
{/snippet}

<div class="tab">
  <MonthSwitcher {month} {onprev} {onnext} />
  <div class="total">{formatMoney(total)}</div>
  <div class="scroll">
    <h2>By label</h2>
    {@render bars(byLabel.value, labelMax)}
    <h2>By payment method</h2>
    {@render bars(byMethod.value, methodMax)}
  </div>
  <div class="export-wrap"><ExportButtons produce={(format) => exportMonth(month, format)} /></div>
</div>

<style>
  .tab {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .total {
    font-size: 26px;
    font-weight: 700;
    padding: 8px 2px;
  }

  .scroll {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 4px 0;
  }

  h2 {
    margin: 4px 0 0;
    font-size: 13px;
    font-weight: 600;
    color: var(--muted);
  }

  .line {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    padding-bottom: 5px;
  }

  .track {
    height: 8px;
    border-radius: 99px;
    background: var(--surface2);
    overflow: hidden;
  }

  .fill {
    height: 100%;
    border-radius: 99px;
  }

  .export-wrap {
    padding: 16px 0;
  }
</style>
