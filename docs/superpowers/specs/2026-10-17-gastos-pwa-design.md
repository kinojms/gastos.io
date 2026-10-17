# Gastos.io PWA (iPhone) — Design

Date: 2026-10-17
Builds on: v1.0, v1.1, v1.2 specs in this folder. The PWA copies the Android app's behavior as of v1.2.0 (commit 756397d) unless this doc says otherwise. When this doc is silent, **the Android code and those specs are the source of truth.**

## Intent

Friends and family with iPhones want to use Gastos.io. There is no iOS app, and the owner doesn't want to go through the App Store. A Progressive Web App (PWA) gives them the same app: open a link in Safari, tap Share → Add to Home Screen, and it runs full-screen and offline like an app.

## Who uses it

- Friends and family of the owner, on iPhone (Safari, iOS 16.4+). Not technical. Most have no GitHub account.
- Each person's expenses live only on their own phone. No accounts, no sharing, no server.

## Goals

1. Every Android v1.2 feature except the home-screen widget (owner): Home numpad entry, swipe left/right for payment method (with tint), swipe up/down for label, "What did you buy?" with voice input, Expenses list (Today / Days / Month tabs, day view), Edit expense (amount, label, payment, description, date, time, notes), Labels screen, Payment methods screen, CSV/XLSX export (daily and monthly, same columns and sheets), GitHub backup and restore, Settings (theme, voice language, backup, Show guide, About the dev), the 5-step guided tour, five themes with light/dark.
2. Same confirm-popup rule as Android: every create/update/delete confirms, except adding from the numpad.
3. **New, PWA only: file backup** (owner). "Save backup file" writes the same `backup.json` content to a file (iOS share sheet → Save to Files). "Restore from file" picks a file and restores it, with the same confirm and checks as GitHub restore. This is the main backup for people without GitHub.
4. Works fully offline after the first visit. Installable to the Home Screen.
5. Backup JSON is interchangeable with Android (same format, schema 2). A backup made on one restores on the other (see "Backup state rules" for how files move).
6. Auto-deployed to GitHub Pages from this repo on push to `main` (owner).

## Non-goals

- The widget, or any iOS widget / Shortcuts integration.
- Accounts, sync between people, a server or hosted database.
- App Store / TestFlight distribution.
- Android support for the PWA (Android users keep the APK). It will likely still work in Chrome, but it isn't tested or targeted.
- Changes to the Android app. *(Adding file backup to Android too is an open question, see the end.)*
- Push notifications.

## Anti-goals

- Anything slower than Android at logging an expense. Open → type → Enter must stay one tap with no popup.
- Data loss: a local save must never depend on the network. Restores must never run without a confirm.
- Breaking Android backup compatibility. The PWA must never write a backup that Android v1.2 can't restore.
- An "iOS-native" restyle. It should look like the Android app (owner).

## Look and feel (decided by owner: match Android)

- Same layout, colors, themes (palette table in the v1.1 spec, `ui/theme/Palettes.kt`), type sizes, and copy as the Android screens. The goal is that family can help each other because the screens match.
- iPhone basics *(my call)*: respect safe areas (notch, home bar) with `env(safe-area-inset-*)`; swipe from the left edge goes back (browser history routing gives this); no rubber-band scroll or double-tap zoom on Home; numpad buttons react instantly (no 300ms delay, `touch-action: manipulation`); no text selection or long-press callouts on the numpad.
- Font: system font stack (`-apple-system, …`). *(my call)*
- Light/dark follows the phone (`prefers-color-scheme`), like Android.
- Haptic ticks: iOS Safari has no vibration API, so swipes have no haptic. Accept that. *(my call)*

## Platform and stack *(my calls; owner handed these to me)*

- Lives in this repo under `web/`. The Android project is untouched.
- **Svelte 5 + TypeScript + Vite.** Small bundle, fast on older iPhones.
- **IndexedDB** for data (through the small `idb` library). Stores: `expenses`, `labels`, `paymentMethods`, plus a key-value `prefs` store. Same fields as the Room entities: amounts in integer centavos, timestamps in epoch millis, colors as ARGB ints.
- **Offline / install**: `vite-plugin-pwa` (Workbox) precaches the app; a web manifest with name "Gastos.io", short name "Gastos", `display: standalone`, theme colors, and icons from the Android launcher art (192, 512, and a 180px `apple-touch-icon`). iOS also needs `apple-mobile-web-app-capable` and status-bar meta tags.
- Ask for persistent storage (`navigator.storage.persist()`) on first launch.
- **XLSX**: port the Android hand-written `XlsxWriter.kt` to TypeScript, zipped with `fflate`. Don't pull in SheetJS. The output must match the Android files (bold frozen header, peso format `₱#,##0.00`, real dates, SUM total row, "By label" and "By payment" sheets on monthly).
- **Tests**: Vitest for logic (amount input, money format, totals, export, backup encode/decode/validate, swipe decision, label-wrap). Use `fake-indexeddb` for repository tests. Use Playwright (WebKit, iPhone viewport) for a few smoke tests: add an expense, edit it, export, run the tour.
- **Deploy**: GitHub Actions workflow builds `web/` and publishes to Pages on push to `main` when `web/**` changes. URL `https://kinojms.github.io/gastos.io/`, so Vite `base` is `/gastos.io/`. The repo is public, so Pages is free.
- **Routing**: hash routes (`#/`, `#/expenses`, …) so GitHub Pages never 404s on reload. *(my call)*

## How Android features map to the web

Everything not listed works the same as on Android.

| Feature | Android | PWA |
|---|---|---|
| Storage | Room | IndexedDB, same fields. Seed the same default labels (Food, Transpo, Hobbies, Bills, Other) and six payment methods on first run. |
| Prefs | DataStore | IndexedDB `prefs` store, same keys (`theme_id`, `voice_language`, `guide_seen_version`, `last_label_id`, `last_payment_method_id`, `backup_repo`, `backup_enabled`, `last_backup_at`, `last_backup_error`). |
| Swipes | Compose drag, 48dp threshold, dominant axis | Pointer events, 48 CSS px threshold, same dominant-axis rule. Port `HomeSwipe.decide`. |
| Accessibility actions on swipe area | Custom actions | Visually hidden "Previous/Next payment method/label" buttons for VoiceOver. |
| Voice input | `RecognizerIntent` | `webkitSpeechRecognition` with `lang` `en-PH` / `fil-PH`. Same replace and 60-char behavior. **Best effort**: hide the mic if the API is missing; if it errors (common in installed iOS PWAs), show "Voice input isn't available here" and leave the box unchanged. |
| Export | Share sheet | Build the file in memory, then `navigator.share({ files })` (iOS share sheet → Save to Files, AirDrop, Messages…). If file sharing isn't supported, fall back to a download link. Same file names and contents (CSV with BOM). |
| GitHub token | Encrypted with Keystore | Stored in IndexedDB as-is. There is no secure store in the browser. Settings explains this in one line, and the README tells people to use a fine-grained token limited to one repo. |
| GitHub API | OkHttp | `fetch` to `api.github.com` (CORS works with a token). Same endpoints, raw media type for download, same error messages, same commit message format. Port `GitHubClient`, `BackupManager`, `BackupSerializer`. |
| Auto backup | WorkManager, debounced, with network constraint | No background work on iOS. Back up (if enabled) about 30 seconds after the last change while the app is open, when the app is hidden (`visibilitychange`), and on launch if there are changes newer than the last backup. Skip quietly when offline and try again next time. "Back up now" stays. |
| New-install safety | Backup off until Restore or Start fresh | Same. |
| Guided tour | Compose overlay | HTML/CSS overlay: scrim with cutout, bubble, hand animation, same 5 steps and copy, same blocking rules. "Reduced motion" uses `prefers-reduced-motion`. The tour ends and is marked seen when the page is hidden. |
| About the dev | BuildConfig version | Version from `web/package.json`. |
| Back navigation | System back | Browser history plus iOS edge swipe. Sheets and dialogs close on back. |

## Install help (new, PWA only)

- When opened in Safari but **not** installed (`navigator.standalone !== true`), show a dismissible banner on Home: "Install: tap Share, then Add to Home Screen", with the Share icon. Dismissing hides it for 30 days. *(my call)*
- Data added in Safari and data in the installed app are **separate** on iOS. The banner says so: "Install first. Expenses added here won't move to the Home Screen app." *(my call)*
- If opened on a non-iOS browser, the banner says it's made for iPhone and links to the Android APK releases. *(my call)*

## File backup (decided by owner, details my call)

- In Settings → Backup, above the GitHub section: **Save backup file** and **Restore from file**.
- Save: no confirm (it doesn't change data). Builds `BackupFile` JSON (schema 2), names it `gastos-backup-2026-10-17.json`, and shares/downloads it like exports. Records "Last file backup: <date>".
- Restore: file picker (`.json`), then decode and validate exactly like GitHub restore. Confirm "Replace all expenses with this backup (N expenses from <date>)?", then replace. Errors use the same messages as Android ("This backup is from a newer version…", etc.).
- Reminder *(my call)*: if GitHub backup is off and there's been no file backup in 14 days (and there's at least one expense), show a small dismissible note on Home: "Back up your expenses: Settings → Save backup file". It shows at most once a day.
- First-run "Start fresh / Restore" choice: on the web it offers **Restore from file** as well as Restore from GitHub.
- The picker accepts `.json`, `application/json` and `text/plain`, since iOS Files is picky. It must restore a `backup.json` downloaded from the GitHub website, i.e. Android output for schema 1 and 2. Tests use fixtures copied from Android `BackupSerializer` output. *(my call)*

### Backup state rules *(my calls, settling the reviewer's questions)*
- `backup_enabled` controls **GitHub auto-backup only**. Auto-backup runs only when `backup_enabled` is true **and** both repo and token are set.
- Any restore (file or GitHub) or "Start fresh" sets `backup_enabled = true`, as on Android. With no repo set, nothing runs and no error is recorded.
- Saving a **new or changed** repo/token turns `backup_enabled` off until the user chooses either "Restore from GitHub" or "Back up now". "Back up now" asks first: "Replace the backup in owner/repo with this phone's N expenses?" This way, setting up GitHub after a file restore can never silently overwrite an existing `backup.json`.
- The Home reminder shows when **repo or token is not set** (not based on `backup_enabled`).
- Cross-platform moves today: through a GitHub repo, or by downloading `backup.json` from GitHub and using Restore from file. The README documents both.

## Versioning and release

- The PWA has its own version in `web/package.json`, starting at **1.2.0** to match the Android feature level. *(my call)*
- On a new deploy, the app updates itself on the next launch (Workbox `autoUpdate`), and Home shows a small "Updated to vX" toast once. *(my call)*
- The README gets an "iPhone" section: the link, how to install, and that backup matters.

## Repo rules

- No Claude attribution anywhere (CLAUDE.md). Commits use the owner's git identity.
- The deploy workflow must not commit to the repo. It uploads a Pages artifact only.

## Left to the builder

- Component structure, state approach (Svelte runes and stores), CSS approach (plain CSS with custom properties for theme tokens is fine).
- Swipe animation and tint transition details (match Android timing roughly).
- Exact icon generation (the Android vector drawables can be exported to SVG/PNG).
- Playwright scope beyond the smoke tests listed.

## Open question for the owner

- Should the Android app also get "Save backup file / Restore from file"? It isn't in scope for this design.
