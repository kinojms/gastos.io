<script lang="ts">
  let { onopen, ondismiss }: { onopen: () => void; ondismiss: () => void } = $props()
</script>

<div class="reminder" role="note">
  <button class="text" onclick={onopen}>Back up your expenses: Settings &rarr; Save backup file</button>
  <button class="dismiss" aria-label="Dismiss" onclick={ondismiss}>&#10005;</button>
</div>

<style>
  .reminder {
    display: flex;
    align-items: center;
    margin: 0 14px;
    padding-left: 12px;
    border-radius: 12px;
    background: var(--surface2);
  }

  .text {
    flex: 1;
    min-width: 0;
    border: 0;
    background: none;
    padding: 10px 0;
    text-align: left;
    font-size: 12px;
    color: var(--muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .dismiss {
    flex: none;
    width: 40px;
    height: 40px;
    border: 0;
    background: none;
    color: var(--muted);
    font-size: 14px;
  }
</style>
