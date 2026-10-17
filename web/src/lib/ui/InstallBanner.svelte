<script lang="ts">
  import { RELEASES_URL, type InstallState } from '../install'

  let { state, ondismiss }: { state: Exclude<InstallState, 'installed'>; ondismiss: () => void } = $props()
</script>

<div class="banner" role="note">
  <div class="text">
    {#if state === 'ios-safari'}
      <strong>
        Install: tap
        <svg class="share" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-label="Share">
          <path d="M12 3v12M8 7l4-4 4 4M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7" />
        </svg>
        Share, then Add to Home Screen
      </strong>
      <span>Install first. Expenses added here won't move to the Home Screen app.</span>
    {:else}
      <strong>Gastos.io for the web is made for iPhone.</strong>
      <span>On Android, <a href={RELEASES_URL} target="_blank" rel="noopener noreferrer">get the app</a>.</span>
    {/if}
  </div>
  <button class="dismiss" aria-label="Dismiss" onclick={ondismiss}>&#10005;</button>
</div>

<style>
  .banner {
    display: flex;
    align-items: center;
    gap: 4px;
    margin: 0 14px;
    padding: 8px 4px 8px 12px;
    border-radius: 12px;
    background: var(--surface2);
    font-size: 12px;
  }

  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  strong {
    font-size: 13px;
  }

  span {
    color: var(--muted);
  }

  .share {
    vertical-align: -3px;
  }

  a {
    color: var(--accent);
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
