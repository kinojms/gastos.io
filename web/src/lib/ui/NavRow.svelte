<script lang="ts">
  let { title, value, onclick }: { title: string; value?: string; onclick: () => void } = $props()
</script>

<button class="row" {onclick}>
  <span class="title">{title}</span>
  {#if value}<span class="value">{value}</span>{/if}
  <span class="chevron" aria-hidden="true">&rsaquo;</span>
</button>

<style>
  .row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 14px 16px;
    border: 0;
    border-radius: 14px;
    background: var(--surface);
    text-align: left;
    font-size: 16px;
  }

  .title {
    flex: 1;
  }

  .value,
  .chevron {
    color: var(--muted);
  }

  .chevron {
    font-size: 22px;
    line-height: 1;
  }
</style>
