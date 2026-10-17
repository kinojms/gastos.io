<script lang="ts">
  import { onMount } from 'svelte'
  import { argbToCss } from '../lib/domain/color'
  import { DESCRIPTION_MAX } from '../lib/domain/expenseText'
  import { formatHeaderDate } from '../lib/domain/dates'
  import { formatMoney } from '../lib/domain/money'
  import { navigate } from '../lib/router.svelte'
  import Numpad from '../lib/ui/Numpad.svelte'
  import SwipeArea from '../lib/ui/SwipeArea.svelte'
  import VoiceButton from '../lib/ui/VoiceButton.svelte'
  import { showToast } from '../lib/ui/toastState.svelte'
  import { shouldShowReminder } from '../lib/backup/rules'
  import { oldestTimestamp } from '../lib/data/expenses'
  import { prefsStore, setPref } from '../lib/data/prefs'
  import CoachOverlay from '../lib/guide/CoachOverlay.svelte'
  import { guide } from '../lib/guide/guide.svelte'
  import type { Axis } from '../lib/domain/swipe'
  import { installState, shouldShowInstallBanner } from '../lib/install'
  import { live } from '../lib/live.svelte'
  import BackupReminder from '../lib/ui/BackupReminder.svelte'
  import InstallBanner from '../lib/ui/InstallBanner.svelte'
  import { createHomeState } from './homeState.svelte'

  const home = createHomeState({ tourActive: () => guide.step !== null })
  const step = $derived(guide.step)
  const touring = $derived(step !== null)
  /** While the tour runs only its swipe, Next/Done and Skip do anything; screen readers skip the dimmed controls. */
  const swipeStep = $derived(step?.swipeAxis != null)

  const install = installState()
  let nowMs = $state(Date.now())
  const prefs = $derived($prefsStore)
  const oldest = live(() => oldestTimestamp(), null as number | null)

  /** At most one banner at a time: the install note first, then the backup reminder. Neither shows during the tour. */
  const showInstall = $derived(!touring && install !== 'installed' && shouldShowInstallBanner(install, prefs.install_banner_dismissed_at, nowMs))
  const showReminder = $derived(
    !touring &&
      !showInstall &&
      shouldShowReminder({
        now: nowMs,
        hasGitHub: Boolean(prefs.backup_repo?.trim() && prefs.backup_token),
        lastFileBackupAt: prefs.last_file_backup_at ?? null,
        oldestExpenseAt: oldest.value,
        dismissedAt: prefs.backup_reminder_dismissed_at ?? null,
      }),
  )

  let menuOpen = $state(false)
  let descriptionInput: HTMLInputElement | undefined = $state()

  onMount(() => {
    void guide.startIfUnseen()
    const stop = home.start()
    const clock = setInterval(() => (nowMs = Date.now()), 60_000)
    return () => {
      stop()
      clearInterval(clock)
    }
  })

  $effect(() => {
    if (touring) descriptionInput?.blur()
  })

  /** Every swipe lands here. During the tour only the step's own axis counts, and it completes the step. */
  function onswipe(axis: Axis, delta: number) {
    const current = guide.step
    if (current !== null && current.swipeAxis !== axis) return
    void home.swipe(axis, delta)
    if (current !== null) setTimeout(() => guide.onSwipe(axis), 400) // let them see the change; ignored if the tour moved on
  }

  async function enter() {
    descriptionInput?.blur()
    const message = await home.enter()
    if (message) showToast(message)
  }

  /** Tapping anywhere outside the text box hides the keyboard. */
  function outside(e: PointerEvent) {
    if (e.target !== descriptionInput) descriptionInput?.blur()
  }

  function openMenuItem(path: string) {
    menuOpen = false
    navigate(path)
  }

  const tint = $derived(home.method ? argbToCss(home.method.colorArgb) : 'transparent')
</script>

<svelte:window onpointerdown={outside} />

<div class="home no-select" style:--method={tint}>
  <div class="tint" aria-hidden="true"></div>
  <header inert={touring}>
    <div class="when">
      <div class="date">{formatHeaderDate(home.today)}</div>
      <div class="total">Today {formatMoney(home.todayTotal)}</div>
    </div>
    <button class="icon" data-guide="history" aria-label="Expenses list" onclick={() => navigate('/expenses')}>
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
        <path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01" />
      </svg>
    </button>
    <div class="menu-wrap">
      <button class="icon" aria-label="More" aria-expanded={menuOpen} onclick={() => (menuOpen = !menuOpen)}>
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
          <circle cx="12" cy="5" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="12" cy="19" r="2" />
        </svg>
      </button>
      {#if menuOpen}
        <div class="menu-scrim" role="presentation" onclick={() => (menuOpen = false)}></div>
        <div class="menu" role="menu">
          <button role="menuitem" onclick={() => openMenuItem('/labels')}>Labels</button>
          <button role="menuitem" onclick={() => openMenuItem('/methods')}>Payment methods</button>
          <button role="menuitem" onclick={() => openMenuItem('/settings')}>Settings</button>
        </div>
      {/if}
    </div>
  </header>

  {#if showInstall && install !== 'installed'}
    <InstallBanner state={install} ondismiss={() => void setPref('install_banner_dismissed_at', Date.now())} />
  {:else if showReminder}
    <BackupReminder
      onopen={() => navigate('/settings')}
      ondismiss={() => void setPref('backup_reminder_dismissed_at', Date.now())}
    />
  {/if}

  <SwipeArea
    method={home.method}
    label={home.label}
    methods={home.methods}
    amount={home.amount}
    {onswipe}
    inert={touring && !swipeStep}
  />

  <div class="field" data-guide="description" inert={touring}>
    <input
      bind:this={descriptionInput}
      type="text"
      placeholder="What did you buy?"
      maxlength={DESCRIPTION_MAX}
      enterkeyhint="done"
      autocomplete="off"
      value={home.description}
      oninput={(e) => home.setDescription(e.currentTarget.value)}
      onkeydown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
    />
    <div class="mic-slot"><VoiceButton onresult={(text) => home.setDescriptionFromVoice(text)} /></div>
  </div>

  <div inert={touring}><Numpad onKey={(k) => home.key(k)} /></div>
  <div class="enter-wrap" data-guide="enter" inert={touring}>
    <button class="enter" onclick={enter}>Enter</button>
  </div>
{#if step}
  <CoachOverlay {step} onnext={() => guide.next()} onskip={() => guide.skip()} />
{/if}
</div>

<style>
  .home {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    margin: 0 -16px; /* App pads main by 16px; Home uses the full width */
  }

  /* The tint covers the whole screen, status bar and home bar included. */
  .tint {
    position: fixed;
    inset: 0;
    z-index: -1;
    background: color-mix(in srgb, var(--method) 20%, var(--bg));
    transition: background-color 0.25s;
  }

  @media (prefers-color-scheme: dark) {
    .tint {
      background: color-mix(in srgb, var(--method) 26%, var(--bg));
    }
  }

  header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 18px;
  }

  .when {
    flex: 1;
  }

  .date {
    font-size: 18px;
    font-weight: 700;
  }

  .total {
    font-size: 12px;
    color: var(--muted);
  }

  .icon {
    width: 38px;
    height: 38px;
    display: grid;
    place-items: center;
    border: 0;
    border-radius: 19px;
    background: var(--surface2);
    color: var(--fg);
  }

  .menu-wrap {
    position: relative;
  }

  .menu-scrim {
    position: fixed;
    inset: 0;
    z-index: 20;
  }

  .menu {
    position: absolute;
    right: 0;
    top: 44px;
    z-index: 21;
    min-width: 200px;
    padding: 6px;
    border-radius: 14px;
    background: var(--surface);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
    display: flex;
    flex-direction: column;
  }

  .menu button {
    border: 0;
    background: none;
    text-align: left;
    padding: 12px 14px;
    font-size: 16px;
    border-radius: 10px;
  }

  .menu button:active {
    background: var(--surface2);
  }

  .field {
    position: relative;
    padding: 0 14px 10px;
  }

  .mic-slot {
    position: absolute;
    right: 18px;
    top: 4px;
  }

  input {
    user-select: text; /* typing must keep working inside the no-select screen */
    -webkit-user-select: text;
    width: 100%;
    height: 52px;
    padding: 0 56px 0 14px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--surface);
    color: var(--fg);
    outline: none;
  }

  input:focus {
    border-color: var(--accent);
  }

  input::placeholder {
    color: var(--muted);
  }

  .enter-wrap {
    padding: 10px 14px 16px;
  }

  .enter {
    width: 100%;
    height: 58px;
    border: 0;
    border-radius: 16px;
    background: var(--accent);
    color: var(--on-accent);
    font-size: 18px;
    font-weight: 700;
  }
</style>
