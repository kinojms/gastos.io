# Gastos.io PWA (iPhone) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A installable, offline PWA in `web/` that gives iPhone users every Android v1.3 feature except the widget, deployed to GitHub Pages.

**Architecture:** Svelte 5 single-page app with hash routing. Pure TypeScript domain modules (`web/src/lib/domain`) are ported 1:1 from the Kotlin `domain/`, `export/` and `backup/` code and unit-tested. Data is stored in IndexedDB via `idb`, behind repository modules that mirror the Kotlin repositories and emit a `dataChanged` event. UI components mirror the Compose screens. Workbox (via `vite-plugin-pwa`) precaches everything for offline use.

**Tech Stack:** Node 20+, Vite 5, Svelte 5 (runes), TypeScript (strict), `idb`, `fflate`, `vite-plugin-pwa`, Vitest + `fake-indexeddb` + `@testing-library/svelte` (optional), Playwright (WebKit).

**Spec:** `docs/superpowers/specs/2026-10-17-gastos-pwa-design.md`. The Android code under `app/src/main/java/io/gastos/app/` and the v1.0/v1.1/v1.2 specs in the same folder are the source of truth for any behavior the spec doesn't restate. **When a task says "port X.kt", read that Kotlin file first and keep its behavior, copy text and edge cases.**

**Prerequisite:** `2026-10-17-gastos-v1.3-android.md` is done. Its Task 1 created `web/public/icons/*`, and its Tasks 2–4 define the backup rules you mirror here.

## Global Constraints

- No Claude attribution anywhere (commits, files, README, workflow). Commit with the local git identity, with no `Co-Authored-By` lines.
- All web code lives in `web/`. Do not touch `app/` (Android).
- Vite `base: '/gastos.io/'`. Hash routes only (`#/`, `#/expenses`, `#/expenses/day/2026-10-16`, `#/labels`, `#/methods`, `#/settings`).
- Amounts are integer centavos (`number`). Timestamps are epoch millis. Colors are **signed 32-bit ARGB ints**, exactly as Kotlin `Int` writes them in backups (e.g. Cash = `0xFF77808B | 0` = `-8945525`). Convert to CSS only at render time.
- Dates are local device time. Represent a day as `"YYYY-MM-DD"` and a month as `"YYYY-MM"` strings.
- Money format `₱1,234.50`. Max `999_999_999` centavos. Description max 60 chars, notes max 1000.
- Backup JSON = Android `BackupFile`, `schemaVersion` 2, file path `backup.json` in the repo. Never write a field Android doesn't know about.
- Every create/update/delete shows a confirm dialog with the **same copy as `ui/ConfirmCopy.kt`**, except Enter on Home.
- Version `1.3.0` in `web/package.json`. Show it as `v1.3.0` in About the dev.
- Commands, run from `web/`: `npm test` (`vitest run`), `npm run build`, `npm run check` (`svelte-check`), `npm run e2e` (Playwright).
- Target iOS Safari 16.4+. No dependency that needs a server.

## Review Focus

1. **Colors from Android backups are negative ints.** A naive `#${argb.toString(16)}` renders garbage. Test `argbToCss(-8945525) === '#77808b'` in Task 2.
2. **Timezone/day boundaries.** An expense at 23:59 local must land on that day, not the next UTC day. Test `dayRange('2026-10-16')` and the grouping in Task 2/Task 3 with `TZ=Asia/Manila` set in the vitest config (`process.env.TZ`).
3. **Restore of an Android-made backup with explicit ids, followed by a new expense.** The new id must not collide (IndexedDB key generators move past explicit keys, but `replaceAll` must `clear()` and then `put` in the same transaction). Test in Task 4.
4. **Offline / GitHub unreachable during auto-backup.** No thrown error escapes, data is saved, the status shows "No internet connection." and there's no retry storm. Test in Task 5.
5. **Double-tap on Enter or a fast numpad.** Each tap is one key, two Enters can't save the same amount twice, and iOS double-tap zoom never triggers. Test the Enter guard in Task 8 (Playwright: two rapid clicks → one row).

---

### Task 1: Scaffold, PWA shell, theme tokens

**Files:**
- Create: `web/package.json`, `web/vite.config.ts`, `web/tsconfig.json`, `web/svelte.config.js`, `web/index.html`, `web/src/main.ts`, `web/src/App.svelte`, `web/src/app.css`
- Create: `web/src/lib/theme/palettes.ts`, `web/src/lib/theme/applyTheme.ts`
- Create: `web/src/lib/router.svelte.ts`
- Test: `web/src/lib/theme/palettes.test.ts`
- Modify: `.gitignore` (add `web/node_modules`, `web/dist`, `web/test-results`, `web/playwright-report`)

**Interfaces:**
- Produces:
  - `type ThemeId = 'CLASSIC'|'SAND'|'NORD'|'MOCHA'|'FOREST'`; `THEMES: {id: ThemeId, name: string}[]` in Android order; `palette(id: ThemeId, dark: boolean): Palette` with `Palette = {bg, surface, fg, muted, line, accent, onAccent: string}` (CSS hex). Copy the values from `ui/theme/Palettes.kt`, **not** from the v1.1 spec table (Palettes.kt has contrast fixes). Also `DANGER = {light: '#C2412D', dark: '#EF6B55'}`; `contrastRatio(a: string, b: string): number`.
  - `applyTheme(id: ThemeId): void` sets CSS custom properties `--bg --surface --fg --muted --line --accent --on-accent --danger` on `:root`, picks dark from `matchMedia('(prefers-color-scheme: dark)')`, re-applies on change, and updates `<meta name="theme-color">`. Port any `surface2` or other derived tokens from `ui/theme/Theme.kt` too.
  - `router`: `route` ($state, the parsed hash), `navigate(path: string)`, `back()` (`history.back()`). Route type: `{name: 'home'} | {name: 'expenses', tab?: 'today'|'days'|'month'} | {name: 'day', date: string} | {name: 'labels'} | {name: 'methods'} | {name: 'settings'}`.

- [ ] **Step 1: Scaffold.** `npm create vite@latest web -- --template svelte-ts`, then trim the demo files. Add the deps: `idb fflate`, and dev deps `vite-plugin-pwa vitest fake-indexeddb @playwright/test svelte-check`. Scripts: `test`, `build`, `check`, `e2e`, `dev`. Set `"version": "1.3.0"` and expose it via `define: { __APP_VERSION__: JSON.stringify(pkg.version) }`.
- [ ] **Step 2: Configure the PWA in `vite.config.ts`.** `base: '/gastos.io/'`. `VitePWA({ registerType: 'autoUpdate', manifest: {...}, workbox: { globPatterns: ['**/*.{js,css,html,png,svg,woff2}'] } })`. Manifest values:
  - `name` "Gastos.io", `short_name` "Gastos", `start_url` "./", `scope` "./", `display` "standalone", `orientation` "portrait"
  - `background_color` and `theme_color` `#0B1016`
  - icons: `icons/icon-192.png` 192, `icons/icon-512.png` 512 (purpose `any`), `icons/icon-maskable-512.png` 512 (purpose `maskable`)

  Vitest: `test: { environment: 'jsdom', setupFiles: ['src/test/setup.ts'] }`. The setup file imports `fake-indexeddb/auto` and sets `process.env.TZ = 'Asia/Manila'`.
- [ ] **Step 3: `index.html`.**
  - `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">` (pinch zoom is blocked by `touch-action` in CSS, not by viewport tricks).
  - `apple-mobile-web-app-capable` = yes, `apple-mobile-web-app-status-bar-style` = black-translucent, `apple-mobile-web-app-title` = Gastos.
  - `<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">` and `<link rel="icon" href="icons/favicon-32.png">`.
  - `<meta name="theme-color">`.
- [ ] **Step 4: `app.css` basics.**
  - System font stack. `body { background: var(--bg); color: var(--fg); overscroll-behavior: none; }`.
  - A `.safe` padding helper using `env(safe-area-inset-*)`.
  - `button { touch-action: manipulation; -webkit-tap-highlight-color: transparent; }`.
  - `.no-select { user-select: none; -webkit-user-select: none; -webkit-touch-callout: none; }`.
- [ ] **Step 5: Write the failing test `palettes.test.ts`.**
  - `palette('CLASSIC', false).bg === '#EEF1F4'`, `palette('NORD', true).accent === '#88C0D0'`, `palette('SAND', false).accent === '#8F6C00'`.
  - For every theme × mode: `contrastRatio(fg, bg) >= 4.5`, `contrastRatio(onAccent, accent) >= 4.5`, `contrastRatio(muted, bg) >= 4.5`. If the Kotlin `ThemeTest` asserts different pairs, mirror those instead.
- [ ] **Step 6: Run** `npm test`. Expected: FAIL. **Implement**, then run again. Expected: PASS.
- [ ] **Step 7: App shell.** `App.svelte` renders a placeholder per route and calls `applyTheme('CLASSIC')`. `npm run build` → Expected: `dist/` contains `manifest.webmanifest`, `sw.js` and `icons/`. `npm run check` → 0 errors.
- [ ] **Step 8: Commit.** `git add web .gitignore && git commit -m "Scaffold Gastos.io PWA"`

---

### Task 2: Domain logic ports

**Files:**
- Create: `web/src/lib/domain/money.ts` (port `domain/Money.kt`), `amountInput.ts` (port `domain/AmountInput.kt`), `expenseText.ts` (port `domain/ExpenseText.kt`), `swipe.ts` (port `ui/home/HomeSwipe.kt`), `dates.ts`, `color.ts`, `confirmCopy.ts` (port `ui/ConfirmCopy.kt`)
- Test: one `*.test.ts` beside each

**Interfaces:**
- Produces:
  - `formatMoney(c: number): string`, `plainMoney(c: number): string`, `MAX_CENTAVOS = 999_999_999`
  - `type Key = {digit: number} | 'dot' | 'backspace'`; `applyKey(current: string, key: Key): string`, `toCentavos(text: string): number | null`, `displayAmount(text: string): string`
  - `cleanDescription(raw: string|null|undefined): string|null`, `cleanNotes(...)`, `DESCRIPTION_MAX = 60`, `NOTES_MAX = 1000`
  - `type Axis = 'NONE'|'METHOD'|'LABEL'`; `decideSwipe(dx: number, dy: number, threshold: number): {axis: Axis, delta: number}`; `stepIndex(len: number, current: number, delta: number): number`; `SWIPE_THRESHOLD_PX = 48`
  - `dates.ts`: `today(now?: number): string`, `dayOf(ts: number): string`, `dayRange(day: string): [number, number]`, `monthOf(day: string): string`, `monthRange(month: string): [number, number]`, `addMonths(month: string, n: number): string`, `formatHeaderDate(day: string): string` ("Fri, Oct 16"), `formatTime(ts: number): string` ("12:30 PM"), `formatTime24(ts: number): string` ("12:30", for CSV), `formatMonth(month: string): string` (copy whatever the Android Month switcher shows), `formatDayRow(day: string): string` (copy the Android Days tab row format). Use the local timezone via `new Date(y, m-1, d)` only. No date library.
  - `color.ts`: `argbToCss(argb: number): string` (lowercase `#rrggbb`, alpha ignored), `cssToArgb(hex: string): number` (signed int, alpha FF), `blend(fgCss, bgCss, amount): string` (for the Home tint, 0.20 light / 0.26 dark)
  - `confirmCopy.ts`: one function per `ConfirmCopy.kt` entry, same names in camelCase and the same text. Also `restoreFile(count, date)` from the Android v1.3 plan.

- [ ] **Step 1: Port the Kotlin unit tests.** Look for existing tests (`app/src/test/**`, mostly `SmokeTest.kt`) and port their cases. Add these exact cases:
  - money: `formatMoney(123450) === '₱1,234.50'`, `formatMoney(5) === '₱0.05'`, `formatMoney(-4550) === '-₱45.50'`, `plainMoney(4550) === '45.50'`
  - amount: `applyKey('', 'dot') === '0.'`, `applyKey('0', {digit: 5}) === '5'`, `applyKey('1.25', {digit: 9}) === '1.25'`, `applyKey('9999999', {digit: 9}) === '9999999'` (exceeds max, unchanged), `toCentavos('0') === null`, `toCentavos('12.5') === 1250`, `displayAmount('1234.5') === '1,234.5'`, `displayAmount('') === '0'`
  - text: `cleanDescription('  Jollibee  ') === 'Jollibee'`, `cleanDescription('   ') === null`, a 70-char string is cut to 60 and then trimEnd'ed
  - swipe: `decideSwipe(-60, 10, 48)` → METHOD +1, `decideSwipe(60, 10, 48)` → METHOD −1, `decideSwipe(5, -70, 48)` → LABEL +1, `decideSwipe(30, 30, 48)` → NONE, `stepIndex(3, 2, 1) === 0`, `stepIndex(3, 0, -1) === 2`, `stepIndex(0, 0, 1) === 0`
  - dates (TZ Asia/Manila): `dayRange('2026-10-16')[0] === Date.parse('2026-10-15T16:00:00Z')`, `dayOf(Date.parse('2026-10-16T15:59:00Z')) === '2026-10-16'`, `dayOf(Date.parse('2026-10-16T16:00:00Z')) === '2026-10-17'`, `addMonths('2026-01', -1) === '2025-12'`, `formatHeaderDate('2026-10-16') === 'Fri, Oct 16'`
  - color: `argbToCss(-8945525) === '#77808b'`, `cssToArgb('#77808B') === -8945525`, `argbToCss(cssToArgb('#007DFE')) === '#007dfe'`
  - confirm: `deleteExpense(4550, 'Transpo') === 'Delete ₱45.50 · Transpo?'`, `deleteLabel('Hobbies', 12) === 'Delete label Hobbies? 12 expenses move to Other.'`
- [ ] **Step 2: Run** `npm test`. Expected: FAIL.
- [ ] **Step 3: Implement** each module by porting its Kotlin file.
- [ ] **Step 4: Run** `npm test`. Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "Port domain logic to the PWA"` (after `git add web`).

---

### Task 3: IndexedDB storage and repositories

**Files:**
- Create: `web/src/lib/data/db.ts`, `models.ts`, `expenses.ts`, `labels.ts`, `methods.ts`, `prefs.ts`, `events.ts`
- Test: `web/src/lib/data/*.test.ts`

**Interfaces:**
- Produces:
  - `models.ts`: `Label {id, name, colorArgb, position, isOther}`, `PaymentMethod {id, name, colorArgb, position, archived}`, `Expense {id, amountCentavos, labelId, timestamp, paymentMethodId, description: string|null, notes: string|null}`, `ExpenseRow = Expense & {labelName, labelColor, paymentMethodName, paymentMethodColor}`, `DaySummary {date: string, count, totalCentavos}`, `Total {id, name, colorArgb, totalCentavos}`. Also `DEFAULT_LABELS` (Food #D9822B, Transpo #2F7FC1, Hobbies #8A5CC9, Bills #3A9A6A, Other #77808B isOther), `DEFAULT_PAYMENT_METHODS` (Cash #77808B, GCash #007DFE, Maribank #FF6A13, BPI #A6192E, Maya #00B26B, BDO #0033A0), and `LABEL_PALETTE` (copied from `data/Models.kt`). All colors are stored as signed ARGB via `cssToArgb`.
  - `db.ts`: `openGastosDb(name = 'gastos'): Promise<IDBPDatabase>`, version 1. Stores: `labels` (keyPath id, autoIncrement), `paymentMethods` (same), `expenses` (same, index `timestamp`), `prefs` (out-of-line keys). Seeds the defaults in `upgrade` when the stores are created (positions 0..n). `replaceAll(db, labels, methods, expenses)`: one readwrite transaction over the 3 stores, `clear()`, then `put` each.
  - `events.ts`: `onDataChanged(cb): () => void`, `emitDataChanged(): void`.
  - `expenses.ts` (port `ExpenseRepository.kt` + the DAO queries): `addExpense(amount, labelId, methodId, description?, now = Date.now()): Promise<number>`, `updateExpense(id, {amountCentavos, labelId, paymentMethodId, timestamp, description, notes})`, `deleteExpense(id)`, `rowsForDay(day): Promise<ExpenseRow[]>` (newest first), `rowsForMonth(month)` (oldest first), `dayTotal(day): Promise<number>`, `daySummaries(month): Promise<DaySummary[]>` (newest day first), `monthByLabel(month): Promise<Total[]>`, `monthByMethod(month): Promise<Total[]>` (both largest first, archived methods included), `countByLabel(labelId)`, `oldestTimestamp(): Promise<number|null>`, `allExpenses()`. Every write calls `emitDataChanged()` and cleans text with `expenseText`.
  - `labels.ts` (port `LabelRepository.kt`): `allLabels()` by position, `addLabel(name, color)`, `renameLabel`, `recolorLabel`, `reorderLabels(ids)`, `deleteLabel(id)` (moves expenses to Other in the same transaction; throws on Other). Plus the name validation from `LabelsViewModel.kt` (trimmed, not blank, unique case-insensitive, same messages).
  - `methods.ts` (port `PaymentMethodRepository.kt`): `activeMethods()`, `allMethods()`, `addMethod`, `renameMethod`, `recolorMethod`, `reorderMethods`, `archiveMethod(id)` (throws when last active). Plus the validation from `PaymentMethodsViewModel.kt`.
  - `prefs.ts`: `getPref<K extends keyof Prefs>(k): Promise<Prefs[K] | undefined>`, `setPref(k, v)`, `prefsStore`, a Svelte-readable snapshot that updates on set. Keys: `theme_id, voice_language, guide_seen_version, last_label_id, last_payment_method_id, backup_repo, backup_token, backup_enabled, last_backup_at, last_backup_error, last_file_backup_at, backup_reminder_dismissed_at, install_banner_dismissed_at, last_seen_version`. Plus `resolveLabel(labels)` and `resolveMethod(active)`, ported from `AppPrefs.resolveLabel` and `LastUsed.method`.

- [ ] **Step 1: Write the failing tests.** Use a fresh db name per test (`openGastosDb('t' + Math.random())`).
  - A fresh db has 5 labels (Other last, `isOther`) and 6 methods in order. Cash `colorArgb === -8945525`.
  - `addExpense(4550, food, cash, '  Jollibee ', ts)` then `rowsForDay(dayOf(ts))` returns one row with description 'Jollibee', labelName 'Food', paymentMethodName 'Cash'.
  - An expense at Manila 23:59 on Oct 16 is in `rowsForDay('2026-10-16')`, not in Oct 17's.
  - `daySummaries('2026-10')` groups 3 expenses on 2 days → 2 summaries, newest first, with correct totals.
  - `monthByMethod` includes an archived method that has expenses.
  - `deleteLabel(hobbies)` moves its 2 expenses to Other, and `deleteLabel(other)` rejects.
  - `archiveMethod` on the last active method rejects.
  - Each write calls the `onDataChanged` callback once.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL. **Step 3: Implement.** **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "Add IndexedDB storage and repositories"`

---

### Task 4: Backup file format (encode, decode, validate, restore)

**Files:**
- Create: `web/src/lib/backup/backupFile.ts` (port `BackupModels.kt`, `BackupSerializer.kt`, and the validate/schema-1 logic of `BackupManager.kt`)
- Create: `web/src/lib/backup/fixtures/android-v1.json`, `android-v2.json`
- Test: `web/src/lib/backup/backupFile.test.ts`

**Interfaces:**
- Produces:
  - `BackupFile {schemaVersion, exportedAt: string, labels, expenses, paymentMethods}`, with the field names exactly as Kotlin serializes them (`BackupLabel {id,name,colorArgb,position,isOther}`, `BackupExpense {id,amountCentavos,labelId,timestamp,paymentMethodId,description,notes}`, `BackupPaymentMethod {id,name,colorArgb,position,archived}`).
  - `CURRENT_SCHEMA = 2`, `DAMAGED = "This backup file is damaged or isn't a Gastos.io backup."`, `NEWER = "This backup is from a newer version of Gastos.io. Update the app first."`
  - `class BackupError extends Error`.
  - `encodeBackup(f: BackupFile): string`. Write all fields including `null` description/notes and defaults (Kotlin `encodeDefaults = true`).
  - `parseBackup(text: string): {labels: Label[], methods: PaymentMethod[], expenses: Expense[], exportedAt: string}`. It throws `BackupError(DAMAGED)` for non-JSON, a missing schemaVersion or a failed validation, and `BackupError(NEWER)` for version > 2. Schema 1 (empty `paymentMethods`) → the six defaults with ids 1..6 and every expense on Cash (id 1). Unknown keys are ignored.
  - `buildBackup(labels, methods, expenses, now: Date): BackupFile`.
  - `exportJson(db): Promise<string>`, `restoreJson(db, text): Promise<{expenseCount: number}>` (parse, then `replaceAll`, then `emitDataChanged`), `previewJson(text): {expenseCount, exportedAt: string}`.

- [ ] **Step 1: Create the fixtures.** Write `android-v2.json` by hand, matching what `BackupSerializer.encode` produces (see `BackupManagerTest.kt` for real examples). Include 2 labels + Other, 2 methods (one archived), 3 expenses (one with description and notes containing a comma, a quote and a newline), and negative `colorArgb` values. `android-v1.json` is schema 1 with no `paymentMethods` and no `paymentMethodId`.
- [ ] **Step 2: Write the failing tests.**
  - `parseBackup(v2)` keeps ids and colors exactly.
  - `parseBackup(v1)` → 6 methods, Cash id 1, all expenses `paymentMethodId === 1`.
  - `JSON.parse(encodeBackup(buildBackup(...parseBackup(v2), now)))` deep-equals the fixture's data fields (round trip).
  - DAMAGED for: `'not json'`, `'{}'`, two Other labels, an expense pointing at a missing label, duplicate expense ids, an id `0`, all methods archived.
  - NEWER for `{"schemaVersion":3}`.
  - Restore into a db with existing data: after `restoreJson(db, v2)` only the fixture data remains. Then `addExpense(...)` returns an id greater than the fixture's max id.
- [ ] **Step 3: Run** (FAIL), **implement**, **run** (PASS).
- [ ] **Step 4: Commit.** `git commit -m "Add Android-compatible backup format"`

---

### Task 5: GitHub client, backup manager, auto-backup

**Files:**
- Create: `web/src/lib/backup/github.ts` (port `GitHubClient.kt`), `manager.ts` (port `BackupManager.kt` + Android v1.3 Task 2/3 rules), `auto.ts`, `rules.ts` (`credentialsChanged`, `shouldShowReminder`; port the Android `BackupRules`/`BackupReminder`)
- Test: `github.test.ts`, `manager.test.ts`, `rules.test.ts`

**Interfaces:**
- Produces:
  - `class GitHubError extends Error {code: number; retryable: boolean}`. Messages are copied from `GitHubClient.kt`, with `code 0` = "No internet connection." (`fetch` TypeError).
  - `github.ts`: `getSha(repo, path, token, fetchFn = fetch): Promise<string|null>`, `download(repo, path, token, fetchFn?): Promise<string|null>` (Accept `application/vnd.github.raw+json`, and the same repo-visibility double-check on 404), `upload(repo, path, token, content, sha, message, fetchFn?)` (base64 of the **UTF-8 bytes**, not `btoa(str)`). Headers: `Authorization: Bearer`, `X-GitHub-Api-Version: 2022-11-28`. Repo-format error: "Repo must look like owner/name."
  - `manager.ts`: `backupNow(): Promise<Result>`, `restoreFromGitHub(): Promise<Result>`, `restoreFromFile(text): Promise<Result>`, `startFresh(): Promise<Result>`, `saveSettings(repo, tokenTyped: string)` (applies `credentialsChanged` → `backup_enabled=false`), with `type Result = {ok: true} | {ok: false, message: string}`. Runs are serialized with a promise-chain mutex. `backupNow` behavior:
    - Disabled → ok with no network.
    - Enabled with no repo/token → ok, silently.
    - Success → `last_backup_at = now`, error cleared.
    - Failure → `last_backup_error = message`.

    The commit message is `"Backup yyyy-MM-dd HH:mm"` in local time.
  - `auto.ts`: `startAutoBackup(): () => void`.
    - On `dataChanged`: (re)start a 30 s timer, then `backupNow()`.
    - On `visibilitychange` to hidden: if a backup is pending, run it now (fetch `keepalive: true` is fine).
    - On start: if `max(expense/label/method change time)` is newer than `last_backup_at`, back up. Track the change time in pref `last_change_at`, set in `emitDataChanged`.
    - Skip when `navigator.onLine === false`.
    - Never throws.
  - `rules.ts`: same signatures and tests as the Android `BackupRules`/`BackupReminder`. `shouldShowReminder({now, hasGitHub, lastFileBackupAt, oldestExpenseAt, dismissedAt})`.

- [ ] **Step 1: Write the failing tests.**
  - `rules.test.ts`: copy all cases from Android Tasks 3–4 (credentialsChanged ×5, reminder ×8).
  - `github.test.ts` with a fake `fetchFn`:
    - 401 → "GitHub token is invalid or expired."
    - TypeError → code 0 "No internet connection."
    - `upload` body content decodes to the original UTF-8 string with `₱` and `ñ`.
    - `download` with 404 on the file plus 404 on the repo throws "Repo not found, or this token can't access it."
    - Bad repo string → "Repo must look like owner/name."
  - `manager.test.ts` (fake GitHub module injected):
    - Disabled → no calls.
    - Enabled without repo → ok, `last_backup_error` unset.
    - Network failure → result message "No internet connection." and the pref set.
    - `restoreFromFile(v2 fixture)` → `backup_enabled === true`.
    - `saveSettings('me/data', 'tok')` after enabled → `backup_enabled === false`.
    - `saveSettings` with the same repo and empty token keeps it true.
  - Auto-backup test with fake timers: 3 changes within 10 s → exactly one `backupNow` 30 s after the last change.
- [ ] **Step 2: Run** (FAIL), **implement**, **run** (PASS).
- [ ] **Step 3: Commit.** `git commit -m "Add GitHub backup, file restore and auto-backup"`

---

### Task 6: Export (CSV, XLSX) and file sharing

**Files:**
- Create: `web/src/lib/export/csv.ts` (port `export/CsvExporter.kt`), `xlsx.ts` (port `export/XlsxWriter.kt` with `fflate.zipSync`), `exportFiles.ts` (port `export/ExportService.kt`: names, sheets, "By label"/"By payment"), `web/src/lib/share.ts`
- Test: `csv.test.ts`, `xlsx.test.ts`, `exportFiles.test.ts`

**Interfaces:**
- Produces:
  - `writeCsv(rows: ExpenseRow[]): Uint8Array`: BOM `EF BB BF`, header `Date,Time,Label,Payment,Description,Amount,Notes`, total line `,,,,Total,<plain>,`, `\n` joins, Kotlin quoting rules.
  - `type Cell = {text: string} | {peso: number} | {date: string} | 'blank'`; `Sheet` mirrors the Kotlin `Sheet` data class; `writeXlsx(sheets: Sheet[]): Uint8Array`.
  - `dayFileName(day, ext)` → `expenses-2026-10-16.csv`; `monthFileName(month, ext)` → `expenses-2026-10.xlsx`. `exportDay(day, 'csv'|'xlsx'): Promise<{name, mime, bytes}>`, `exportMonth(month, fmt)`.
  - `share.ts`: `shareOrDownload(file: {name, mime, bytes}): Promise<'shared'|'downloaded'|'cancelled'>`.
    - If `navigator.canShare?.({files: [f]})`, call `navigator.share({files: [f]})` (AbortError → 'cancelled').
    - Else download via `<a download>` and an object URL (revoke after 1 s).
    - MIME: csv `text/csv`, xlsx `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, backup `application/json`.

- [ ] **Step 1: Write the failing tests.**
  - CSV: for 2 rows (one with notes `He said "hi", ok`), the exact expected bytes/string, compared to what the Kotlin exporter produces. Write the expected string by applying the Kotlin rules by hand.
  - XLSX: `unzipSync(writeXlsx(...))` has `[Content_Types].xml`, `xl/workbook.xml`, `xl/styles.xml` and `xl/worksheets/sheet1.xml`. The sheet XML contains `<f>SUM(F2:F3)</f>`. The styles contain the numFmt `"₱"#,##0.00` (whatever string XlsxWriter.kt uses). Month export has 3 sheets named as in ExportService.kt. Text with `<&>` is escaped.
  - File names as above.
- [ ] **Step 2: Run** (FAIL), **implement**, **run** (PASS). Then **manually** open one generated xlsx in Excel or Numbers if available, and note the result in the commit body. Skip that if neither is available.
- [ ] **Step 3: Commit.** `git commit -m "Add CSV/XLSX export and share-or-download"`

---

### Task 7: Shared UI components and app wiring

**Files:**
- Create: `web/src/lib/ui/ConfirmDialog.svelte`, `Toast.svelte` + `toast.svelte.ts`, `Sheet.svelte` (bottom sheet), `TopBar.svelte`, `ColorSwatches.svelte`, `NavRow.svelte`
- Modify: `web/src/App.svelte`, `web/src/main.ts`

**Interfaces:**
- Produces:
  - `ConfirmDialog` props `{open, message, confirmText = 'Confirm', danger = false, onConfirm, onCancel}`. Port the look from `ui/components/ConfirmDialog.kt`. Uses `<dialog>` with focus trap; Esc and the scrim cancel.
  - `showToast(text: string)`: one at a time, ~2.5 s, positioned above the numpad and the safe-area bottom.
  - `Sheet` props `{open, onClose, children}`: slides up, closes on scrim tap, back navigation, or drag down > 80px.
  - Back behavior: opening a Sheet or Dialog pushes a history entry (`history.pushState`). `popstate` closes the top overlay instead of changing the route, so the iOS edge swipe closes it.
  - `main.ts`:
    1. Open the db.
    2. Load prefs.
    3. `applyTheme(prefs.theme_id ?? 'CLASSIC')`.
    4. `navigator.storage?.persist?.()`.
    5. `startAutoBackup()`.
    6. Mount `App`.
    7. Register the SW (`virtual:pwa-register`, `immediate: true`).

- [ ] **Step 1: Build the components.** Wire App routes to stub screens with a TopBar and back button.
- [ ] **Step 2: Check.** `npm run check && npm run build`. Expected: 0 errors, build OK. In `npm run dev` (Chrome devtools iPhone emulation), check that a ConfirmDialog opens, closes on back, and that a toast shows.
- [ ] **Step 3: Commit.** `git commit -m "Add shared PWA UI components"`

---

### Task 8: Home screen

**Files:**
- Create: `web/src/screens/Home.svelte`, `web/src/lib/ui/Numpad.svelte` (port `ui/components/Numpad.kt`), `web/src/lib/ui/SwipeArea.svelte`, `web/src/screens/home.svelte.ts` (state, port `ui/home/HomeViewModel.kt`)
- Test: `web/src/screens/home.test.ts` (state logic), `web/e2e/home.spec.ts`

**Interfaces:**
- Consumes: Tasks 2, 3, 6, 7.
- Produces: `createHomeState()` returns `{amount, description, label, method, labels, methods, todayTotal, today, key(k: Key), swipe(axis, delta), setDescription(s), enter(): Promise<string|null> /* toast text or null */}`. Exposes the DOM targets for the tour via `data-guide="numpad|swipe|description|history"` attributes on the elements.

- [ ] **Step 1: Write the failing state tests.**
  - `enter()` with empty amount → null and nothing saved.
  - With amount '45.5', description 'Jollibee', method GCash → saves 4550 and returns `'Added ₱45.50 · Jollibee · GCash'`. Without a description → `'Added ₱45.50 · Food · GCash'` (check against the HomeViewModel line 190 format).
  - After enter: amount '' and description '', label kept.
  - `swipe('METHOD', +1)` from the last method wraps to the first and sets `last_payment_method_id`.
  - Two concurrent `enter()` calls save once (in-flight guard).
  - `today` rolls over at local midnight (ticker every 15 s, as in `dateTicker`).
- [ ] **Step 2: Run** (FAIL), **implement the state**, **run** (PASS).
- [ ] **Step 3: Build the UI.** Port the layout from `HomeScreen.kt`:
  - Header: date, today's total, list icon (`data-guide="history"`), overflow menu with the same items as Android.
  - Swipe area: method name uppercase with ‹ ›, label pill with ˄ ˅, big amount, page dots.
  - "What did you buy?" input with mic slot (Task 9 fills it), max length 60, enterkeyhint "done".
  - Numpad + Enter in the accent color.

  Behavior:
  - Swipes use Pointer Events on the swipe area with `touch-action: none`. Decide on `pointerup` with `decideSwipe(dx, dy, 48)`.
  - The tint blends the method color over `--bg` (0.20 light / 0.26 dark) with a 200 ms CSS transition.
  - Numpad keys fire on `pointerdown` for speed, with `touch-action: manipulation` and the `no-select` class.
  - Tapping outside the input blurs it.
  - Hidden VoiceOver buttons: "Previous payment method", "Next payment method", "Previous label", "Next label".
- [ ] **Step 4: Playwright setup and test.** `web/playwright.config.ts`: `webServer: npm run build && npm run preview -- --port 4173`, `baseURL: http://localhost:4173/gastos.io/`, project `webkit` with `devices['iPhone 13']`. `e2e/home.spec.ts`:
  - Type 4,5,.,5 → Enter → toast 'Added ₱45.50 · Food · Cash' and the header total ₱45.50.
  - Double-click Enter after typing 1 → only one ₱1.00 added.
  - Mouse-drag left 100px on `[data-guide=swipe]` → method name GCASH.

  Run: `npx playwright install webkit && npm run e2e`. Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "Add Home screen to the PWA"`

---

### Task 9: Voice input

**Files:**
- Create: `web/src/lib/ui/VoiceButton.svelte`, `web/src/lib/voice.ts`
- Modify: `web/src/screens/Home.svelte`, (Task 11) the edit sheet

**Interfaces:**
- Produces: `voiceSupported(): boolean` (`'webkitSpeechRecognition' in window || 'SpeechRecognition' in window`). `listenOnce(lang: 'en-PH'|'fil-PH'): Promise<string|null>`: `interimResults=false`, `maxAlternatives=1`. It resolves with the transcript, or null on no-match, abort or `onend` without a result. It rejects with `VoiceUnavailable` on `not-allowed`, `service-not-allowed`, `audio-capture` or `network`. `VOICE_LANGUAGES = [{id:'ENGLISH', name:'English (Philippines)', tag:'en-PH'}, {id:'TAGALOG', name:'Tagalog', tag:'fil-PH'}]`. Check the names against `ui/components/VoiceInput.kt`.

- [ ] **Step 1: Write the failing test** `voice.test.ts` with a fake `SpeechRecognition` class on `window`:
  - The result event → resolves 'Jollibee'.
  - `onerror('no-speech')` → null.
  - `onerror('not-allowed')` → rejects VoiceUnavailable.
  - Without the API, `voiceSupported() === false`.
- [ ] **Step 2: Run** (FAIL), **implement**, **run** (PASS).
- [ ] **Step 3: VoiceButton.**
  - Shows only when supported. The mic shows a pulsing state while listening; tapping again aborts.
  - The result **replaces** the field via `cleanDescription`, as on Android.
  - On VoiceUnavailable: `showToast("Voice input isn't available here")`, field unchanged.
- [ ] **Step 4: Commit.** `git commit -m "Add best-effort voice input"`

---

### Task 10: Expenses screen (Today, Days, Month, day view, export)

**Files:**
- Create: `web/src/screens/Expenses.svelte`, `DayView.svelte`, `web/src/lib/ui/DayList.svelte` (port `ui/expenses/DayList.kt` + `RowText.kt`), `MonthTab.svelte` (port `MonthTab.kt`), `ExportButtons.svelte` (port `ExportButtons.kt`)
- Test: `web/src/lib/ui/rowText.test.ts`, `web/e2e/expenses.spec.ts`

**Interfaces:**
- Consumes: Task 3 queries, Task 6 `exportDay`/`exportMonth`/`shareOrDownload`.
- Produces: `rowTitle(row)` and `rowSubtitle(row)` in `web/src/lib/ui/rowText.ts` (port `RowText.kt`). Lists re-query on `onDataChanged`.

- [ ] **Step 1: Write the failing test** `rowText.test.ts`, porting the exact formats from `RowText.kt`:
  - With description → title 'Jollibee', subtitle 'Food · GCash · 12:30 PM'.
  - Without → title 'Food', subtitle 'GCash · 12:30 PM'.
- [ ] **Step 2: Run** (FAIL), **implement**, **run** (PASS).
- [ ] **Step 3: Build the screens.**
  - Tabs Today / Days / Month, persisted in the route (`#/expenses?tab=days`).
  - Days: month switcher plus a day list → `#/expenses/day/YYYY-MM-DD`.
  - Month: total, "By label" and "By payment method" bar lists, and CSV/XLSX export.
  - Day views: total, rows (tap → edit sheet from Task 11), and CSV/XLSX export.
  - Export failures show a toast "Couldn't export." 'cancelled' shows nothing.
  - Empty states copy Android.
- [ ] **Step 4: e2e** `expenses.spec.ts`: add 2 expenses on Home → open list → Today shows 2 rows and the right total → Month tab shows Food with the total. Export CSV triggers a download in WebKit, and its filename matches `expenses-\d{4}-\d{2}-\d{2}\.csv`.
- [ ] **Step 5: Commit.** `git commit -m "Add Expenses screens and export"`

---

### Task 11: Edit expense sheet

**Files:**
- Create: `web/src/lib/ui/EditExpenseSheet.svelte` (port `ui/expenses/EditExpenseSheet.kt`)
- Test: `web/e2e/edit.spec.ts`

**Interfaces:**
- Consumes: `Sheet`, `ConfirmDialog`, `confirmCopy.saveExpense/deleteExpense`, `updateExpense`, `deleteExpense`, `VoiceButton`.
- Produces: `<EditExpenseSheet expense={row} onClose />`

- [ ] **Step 1: Build it.**
  - Fields:
    - amount: a text input with `inputmode="decimal"`, validated through `toCentavos` and max 2 decimals. Invalid input disables Save.
    - label: a select.
    - payment: active methods plus the current one if archived.
    - description: 60, with mic.
    - date: `<input type=date>`.
    - time: `<input type=time>`.
    - notes: a textarea, 1000.
  - Save → confirm `saveExpense(amount, labelName)` → update. Delete → confirm `deleteExpense` (danger) → delete. Both close the sheet and toast as Android does (copy from the Kotlin file; if Android shows no toast, show none).
- [ ] **Step 2: e2e** `edit.spec.ts`: add ₱10 → open the list → tap the row → change the amount to 12 → Save → the confirm text is 'Save changes to ₱12.00 · Food?' → confirm → the row shows ₱12.00. Delete → confirm → the list is empty.
- [ ] **Step 3: Commit.** `git commit -m "Add Edit expense sheet"`

---

### Task 12: Labels and Payment methods screens

**Files:**
- Create: `web/src/screens/Labels.svelte` (port `ui/labels/LabelsScreen.kt` + VM), `web/src/screens/Methods.svelte` (port `ui/methods/*`), `web/src/lib/ui/ReorderList.svelte`
- Test: `web/e2e/labels.spec.ts`

**Interfaces:**
- Produces: `ReorderList` props `{items: {id, name, colorArgb}[], onReorder(ids: number[])}`. A drag handle uses Pointer Events with `touch-action: none` on the handle only. It also has "Move up"/"Move down" buttons in the VoiceOver accessible actions. `onReorder` fires on drop, and the screen confirms `reorderLabels()`/`reorderMethods()` before saving. Cancelling reverts the order.

- [ ] **Step 1: Build both screens** with add, rename, recolor (`ColorSwatches`: `LABEL_PALETTE` for labels, palette + the six brand colors for methods) and delete. Use the exact confirm copy and validation messages from the Kotlin VMs. Other cannot be deleted. The last active method has no delete.
- [ ] **Step 2: e2e** `labels.spec.ts`: add label "Coffee" → confirm 'Add label Coffee?' → it appears. Adding "food" → error message (duplicate). Delete Hobbies with 1 expense → 'Delete label Hobbies? 1 expense moves to Other.'
- [ ] **Step 3: Commit.** `git commit -m "Add Labels and Payment methods screens"`

---

### Task 13: Settings

**Files:**
- Create: `web/src/screens/Settings.svelte` (port `ui/settings/SettingsScreen.kt` + VM, including the v1.3 file-backup additions)

**Interfaces:**
- Consumes: Task 5 `manager`, Task 4 `exportJson`/`previewJson`, Task 6 `shareOrDownload`, Task 9 `VOICE_LANGUAGES`.

- [ ] **Step 1: Build it**, in Android order:
  1. Theme tiles (confirm `changeTheme`, then `applyTheme`), "Light or dark follows your phone."
  2. **Backup**, starting with "Save backup file" and "Restore from file":
     - Save: `shareOrDownload({name: 'gastos-backup-YYYY-MM-DD.json', mime: 'application/json', bytes})`. On 'shared'/'downloaded', set `last_file_backup_at`.
     - Restore: `<input type=file accept=".json,application/json,text/plain">`, refuse > 20 MB with DAMAGED, `previewJson` → confirm `restoreFile(count, date)` → `manager.restoreFromFile`.
     - File status line.
  3. Then the GitHub repo and token fields, unchanged from Android:
     - Token is a password field with the same hint, plus one extra muted line: "The token is stored on this phone only, without encryption. Use a token limited to one repo."
     - Save (confirm `saveBackupSettings`, then `manager.saveSettings`), status line, Back up now, Restore from GitHub (confirm), and Start fresh plus its hint while backup is off.
  4. Voice language picker (confirm), Labels, Payment methods, Show guide (navigates Home and starts the tour, Task 14).
  5. About the dev card: name, tagline, GitHub/LinkedIn links (`target=_blank rel=noopener`), `v1.3.0`.
- [ ] **Step 2: e2e** `settings.spec.ts`: Save backup file triggers a download named `gastos-backup-*.json`. Then add an expense, restore that file via `setInputFiles` → confirm → the expense count is back to the original. Changing the theme to Nord sets `--bg` to `#ECEFF4` in light mode.
- [ ] **Step 3: Commit.** `git commit -m "Add Settings with file and GitHub backup"`

---

### Task 14: Guided tour

**Files:**
- Create: `web/src/lib/guide/guide.svelte.ts` (port `ui/guide/GuideController.kt` + `GuideStep.kt`), `web/src/lib/guide/CoachOverlay.svelte` (port `CoachOverlay.kt`/`CoachLayout.kt`)
- Test: `web/src/lib/guide/guide.test.ts`, `web/e2e/guide.spec.ts`

**Interfaces:**
- Produces: `guide` state `{step: GuideStep|null, start(), startIfUnseen(), next(), onSwipe(axis), skip(), finish()}`, `GUIDE_VERSION = 1`, `GUIDE_STEPS` with the exact 5 texts and the `data-guide` targets `numpad`, `swipe`, `swipe`, `description`, `history`. Persists `guide_seen_version`.

- [ ] **Step 1: Write the failing tests:**
  - `startIfUnseen` starts only when `guide_seen_version < 1`.
  - `onSwipe('LABEL')` on step 2 does nothing, and `onSwipe('METHOD')` advances.
  - `skip` sets the seen version to 1.
  - `document` `visibilitychange` → hidden finishes and marks seen.
- [ ] **Step 2: Run** (FAIL), **implement the state**, **run** (PASS).
- [ ] **Step 3: Overlay.**
  - A fixed full-screen SVG scrim (black, 0.65) with a rounded-rect cutout. The rect comes from `getBoundingClientRect()` of `[data-guide=...]`, padded 8px, and is recomputed on resize. The outline is in `--accent`. Transitions are 250 ms.
  - The bubble goes above or below the cutout, whichever has more room. It contains the text, "N of 5", Skip, Next/Done (no Next on swipe steps), and has `aria-live="polite"`.
  - Hand animation: CSS keyframes. Static with arrows under `prefers-reduced-motion`.
  - Input: the overlay blocks pointer events everywhere except the cutout on steps 2–3 (four blocker rects around the hole). The Home swipe handler calls `guide.onSwipe(axis)` (advance after 400 ms).
  - The tour blurs the description input on start.
  - Home calls `guide.startIfUnseen()` on mount. Settings → Show guide calls `navigate('/')` then `guide.start()`.
- [ ] **Step 4: e2e** `guide.spec.ts`: fresh context → tour visible with step 1 text → Next → drag on the swipe target → step 3 → drag vertically → step 4 → Next → step 5 → Done → reload → no tour.
- [ ] **Step 5: Commit.** `git commit -m "Add guided tour to the PWA"`

---

### Task 15: Install banner, backup reminder, update toast

**Files:**
- Create: `web/src/lib/install.ts`, `web/src/lib/ui/InstallBanner.svelte`, `web/src/lib/ui/BackupReminder.svelte`
- Modify: `web/src/screens/Home.svelte`, `web/src/main.ts`
- Test: `web/src/lib/install.test.ts`

**Interfaces:**
- Produces: `installState(nav = navigator, mm = matchMedia): 'installed'|'ios-safari'|'other'`. It returns 'installed' if `nav.standalone === true` or `(display-mode: standalone)` matches. It returns 'ios-safari' if the UA matches `/iPhone|iPad|iPod/` or (`Macintosh` and `maxTouchPoints > 1`). Otherwise 'other'. `shouldShowInstallBanner(state, dismissedAt, now)`: false when installed or dismissed less than 30 days ago.

- [ ] **Step 1: Write the failing tests:**
  - Standalone → 'installed'.
  - iPhone UA → 'ios-safari'.
  - Android Chrome UA → 'other'.
  - The banner is hidden 29 days after dismissal and shown at 31.
- [ ] **Step 2: Run** (FAIL), **implement**, **run** (PASS).
- [ ] **Step 3: UI.** Home shows at most one banner, in this priority:
  1. Install banner.
     - 'ios-safari': "Install: tap Share, then Add to Home Screen" with an inline SVG share icon, plus a second line "Install first. Expenses added here won't move to the Home Screen app."
     - 'other': "Gastos.io for the web is made for iPhone. On Android, get the app" linking to `https://github.com/kinojms/gastos.io/releases`.
     - ✕ sets `install_banner_dismissed_at`.
  2. Backup reminder: `shouldShowReminder(...)`, where `hasGitHub` = repo and token set. Text "Back up your expenses: Settings → Save backup file". ✕ sets `backup_reminder_dismissed_at`, and a tap opens Settings.

  Both are hidden during the tour.

  Update toast: in `main.ts`, compare `__APP_VERSION__` to pref `last_seen_version`. If they differ and the pref exists → `showToast('Updated to v' + version)`. Always store the current version.
- [ ] **Step 4: Check.** `npm test && npm run check`. Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "Add install banner, backup reminder and update toast"`

---

### Task 16: GitHub Pages deploy and README

**Files:**
- Create: `.github/workflows/pages.yml`
- Modify: `README.md`

- [ ] **Step 1: Write the workflow.**
  - Triggers: `on: push: branches: [main], paths: ['web/**', '.github/workflows/pages.yml']`, plus `workflow_dispatch`.
  - Permissions: `pages: write`, `id-token: write`, `contents: read`.
  - Concurrency group `pages`.
  - Job `build`: `actions/checkout@v4`, `actions/setup-node@v4` (node 20, cache npm, `cache-dependency-path: web/package-lock.json`), `npm ci`, `npm test`, `npm run build` (working-directory web), `actions/upload-pages-artifact@v3` with `path: web/dist`.
  - Job `deploy`: needs build, environment `github-pages`, `actions/deploy-pages@v4`.
  - The workflow never commits to the repo.
- [ ] **Step 2: README.** Add an "iPhone (web app)" section:
  - The link `https://kinojms.github.io/gastos.io/`.
  - Install steps (open in Safari → Share → Add to Home Screen; open from the icon).
  - "Add expenses only in the installed app."
  - Backups: Save backup file regularly, or set up GitHub backup as described above.
  - Moving between Android and iPhone: restore the same backup file or GitHub repo.
  - Voice input may not work on iPhone.
  - Build: `cd web && npm ci && npm test && npm run build`.
- [ ] **Step 3: Full local verification.** `cd web && npm ci && npm test && npm run check && npm run build && npm run e2e`. Expected: all PASS.
- [ ] **Step 4: Commit.** `git add .github/workflows/pages.yml README.md && git commit -m "Deploy the PWA to GitHub Pages"`
- [ ] **Step 5: Enable Pages and push. Ask the owner first.**
  - The owner (or you, with their OK) sets the Pages source to "GitHub Actions": `gh api -X POST repos/kinojms/gastos.io/pages -f build_type=workflow`, or `-X PUT` if Pages already exists.
  - `git push`, then `gh run watch`.
  - Expected: the deploy succeeds, and `curl -sI https://kinojms.github.io/gastos.io/` returns 200, as does `.../manifest.webmanifest`.
- [ ] **Step 6: Phone check (owner).** Ask the owner to open the link on an iPhone:
  - Add to Home Screen, then open from the icon: full-screen, wallet icon, no white corners.
  - Add an expense in airplane mode.
  - Save a backup file to Files.
