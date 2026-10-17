<script lang="ts">
  import { argbToCss } from '../domain/color'

  let { colors, selected, onPick }: { colors: number[]; selected: number | null; onPick: (argb: number) => void } = $props()
</script>

<div class="swatches" role="radiogroup" aria-label="Color">
  {#each colors as argb (argb)}
    <button
      class="swatch"
      class:on={argb === selected}
      role="radio"
      aria-checked={argb === selected}
      aria-label={argbToCss(argb)}
      style:background={argbToCss(argb)}
      onclick={() => onPick(argb)}
    ></button>
  {/each}
</div>

<style>
  .swatches {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .swatch {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: 3px solid transparent;
    padding: 0;
  }

  .swatch.on {
    border-color: var(--fg);
  }
</style>
