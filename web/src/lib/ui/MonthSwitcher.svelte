<script lang="ts">
  import { formatMonth } from '../domain/dates'

  let { month, onprev, onnext }: { month: string; onprev: () => void; onnext: () => void } = $props()
</script>

<div class="switcher">
  <button aria-label="Previous month" onclick={onprev}>&lsaquo;</button>
  <span>{formatMonth(month)}</span>
  <button aria-label="Next month" onclick={onnext}>&rsaquo;</button>
</div>

<style>
  .switcher {
    display: flex;
    align-items: center;
    padding: 0 4px;
  }

  span {
    flex: 1;
    text-align: center;
    font-weight: 600;
  }

  button {
    width: 44px;
    height: 44px;
    border: 0;
    background: none;
    font-size: 26px;
  }
</style>
