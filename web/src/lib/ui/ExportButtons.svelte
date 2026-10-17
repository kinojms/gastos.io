<script lang="ts">
  import type { ExportFile, ExportFormat } from '../export/exportFiles'
  import { shareOrDownload } from '../share'
  import { showToast } from './toastState.svelte'

  /** Builds the file for the chosen format. */
  let { produce }: { produce: (format: ExportFormat) => Promise<ExportFile> } = $props()

  let busy = $state(false)

  async function run(format: ExportFormat) {
    if (busy) return
    busy = true
    try {
      await shareOrDownload(await produce(format))
    } catch {
      showToast("Couldn't export.")
    } finally {
      busy = false
    }
  }
</script>

<div class="export">
  <button disabled={busy} onclick={() => run('csv')}>Export CSV</button>
  <button disabled={busy} onclick={() => run('xlsx')}>Export XLSX</button>
</div>

<style>
  .export {
    display: flex;
    gap: 8px;
  }

  button {
    flex: 1;
    height: 42px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface2);
    font-size: 13px;
    font-weight: 600;
  }

  button:disabled {
    opacity: 0.5;
  }
</style>
