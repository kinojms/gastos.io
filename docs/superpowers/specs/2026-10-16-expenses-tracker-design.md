# Gastos.io — Design

Date: 2026-10-16

## Name

**Gastos.io** ("gastos" is Tagalog for expenses). App label on the phone: "Gastos.io". Android package `io.gastos.app`. *(package is default)*

## Intent

A personal expense tracker for one person (the owner) on a Samsung Galaxy S25. Logging speed is everything: open the app (or use the home-screen widget), type an amount, pick a label, hit Enter, and the expense is saved for today. Everything else (lists, totals, exports, backup) supports that core loop.

Mockups (approved): https://claude.ai/artifact/A32jzAPiiHAf8xuofVGEAZ

## Goals

- Log an expense in a few taps, from the app or the home-screen widget.
- See today's expenses and total, past days, and monthly totals.
- Edit or delete any expense.
- Customize labels (categories).
- Export daily or monthly data to CSV or XLSX, properly formatted.
- Back up data to a private GitHub repo, and restore it on a new phone.

## Non-goals

- Multiple users, accounts, sharing.
- Budgets, income tracking, charts beyond the monthly per-label split.
- iOS, web, Play Store distribution (installed by APK).
- Any hosted backend or database service.

## Anti-goals

- Anything that slows down the entry flow (extra confirmation dialogs, required fields beyond amount, splash screens).
- Backends that pause or delete data when unused (the reason Supabase and MongoDB Atlas were rejected).
- Data loss: local save must never depend on network.

## Platform (decided)

- **Native Android app** (owner chose native specifically to get a real home-screen widget).
- Kotlin, Jetpack Compose for UI, Jetpack Glance for the widget. *(default, owner agreed by going native.)*
- Installed by APK (sideloaded). Target the S25 (Android 15+); min SDK is the builder's call (26+ is fine).

## Currency and amounts (decided)

- Philippine Peso, displayed as `₱1,234.50`.
- Decimals allowed, max 2 decimal places.
- Store amounts as integer centavos to avoid float errors. *(default)*
- Zero / empty amounts are ignored on Enter.

## Screens

### 1. Home (opens on launch)
- Focus: big amount display, numpad (1–9, `.`, 0, backspace), large Enter button.
- Header: current date (e.g. "Fri, Oct 16") with today's total under it; button to open the expenses list.
- Label picker under the amount (tap to choose label). Defaults to the last-used label. *(default)*
- Enter saves the expense with the current date/time and label, clears the amount, shows a brief "Added ₱X · Label" toast/snackbar.
- Access to the Labels screen from Home or the list (builder's call where; e.g. overflow menu).

### 2. Expenses list — three tabs
- **Today**: today's expenses (newest first) with label, time, amount; day total at top. Tap a row to edit. Export CSV / XLSX buttons.
- **Days**: past days grouped by month (month switcher), each row showing date, expense count, total. Tap a day → that day's list, which behaves exactly like Today (edit, export).
- **Month**: month total plus per-label totals with bars (largest first). Month switcher. Export CSV / XLSX for the whole month.

### 3. Edit expense (bottom sheet)
- Edit amount, label, time, date. Save / Delete.
- Save and Delete each show a confirm popup first (owner's rule, see "Confirm popups").

### 4. Labels
- Add, rename, recolor, reorder (drag), delete.
- Default set: Food, Transpo, Hobbies, Bills, Other. *(default; owner named the first three as examples)*
- Deleting a label that has expenses: those expenses move to "Other". "Other" itself cannot be deleted. *(default)*
- Label order is the order used by the widget ticker and the picker.

### 5. Home-screen widget (decided by owner)
- Contains: amount display, a **label ticker** (tap to cycle to the next label), numpad 0–9 and `.`, **backspace**, **Enter**.
- Enter saves the expense exactly as the app does, clears the amount, and updates today's total shown on the widget.
- Glance widgets update with slight lag per tap; owner accepted this.
- Suggested size 4×3, resizable is a nice-to-have. In-progress amount is held in widget state.
- The widget and app share one "last-used label". *(default)*

## Export (decided by owner: CSV and XLSX, daily and monthly)

- Available from any day's list (daily) and the Month tab (monthly).
- Output saved via Android's share sheet / "Save to" picker so the owner can send it anywhere. *(default)*
- File names: `expenses-2026-10-16.csv`, `expenses-2026-10.xlsx`.
- Columns: Date, Time, Label, Amount.
- **CSV**: UTF-8 with BOM (so Excel shows `₱` correctly), ISO-ish date `2026-10-16`, amount as plain number `45.50`.
- **XLSX** ("proper formatting"):
  - Bold header row, frozen; columns auto-sized.
  - Amount cells numeric with peso number format `₱#,##0.00`; date cells real dates.
  - Total row at the bottom (bold, SUM formula).
  - Monthly export adds a second sheet "By label" with per-label totals and a grand total.
- XLSX library is the builder's call; prefer something lightweight over full Apache POI if it handles the above.

## Storage and backup (decided by owner: GitHub)

- **Local first**: Room (SQLite) database on the phone is the source of truth. All features work offline.
- **Backup target**: a separate private GitHub repo (e.g. `expenses-data`), not the code repo.
- **Auth**: owner pastes a fine-grained GitHub personal access token (Contents read/write, that repo only) once in Settings. Store it encrypted using Android Keystore.
- **Format**: one JSON file (e.g. `backup.json`) containing all expenses and labels, plus a schema version. Written via the GitHub Contents API (one commit per backup).
- **When**: automatically a few minutes after any change, when online (WorkManager, debounced, with network constraint and retry). Also a manual "Back up now" button with last-backup time shown.
- **Restore**: Settings → "Restore from GitHub" replaces local data with the repo's backup, after an in-app confirm step.
- Single device; no merge or conflict handling needed beyond last-write-wins.
- Commits to the data repo are made under the owner's token, so they show only the owner's name.
- **New-install safety**: on a fresh install, auto-backup stays off until the owner either restores or confirms "start fresh", so an empty database never overwrites an existing backup. *(default)*
- Handle backup files over 1 MB from the start (Contents API raw media type, or Git Data API). *(default)*
- Backup includes a schema version; restore migrates older versions and refuses newer ones with a clear message. *(default)*
- Sign all APKs with one release key so updates install over the existing app. *(default)*

## Settings screen (minimal)
- GitHub repo (owner/name) and token, backup status, Back up now, Restore.
- Manage labels link.
- Theme follows system light/dark. *(default)*

## Confirm popups (decided by owner)
- Every create/update/delete shows an in-app confirm popup **except** adding an expense from the Home numpad or widget (that stays one tap, followed by the "Added" toast).
- Covers: editing or deleting an expense; adding, renaming, recoloring, reordering, or deleting a label; restoring from GitHub; changing backup settings.
- The popup says what will happen, e.g. "Delete ₱45.50 · Transpo?" or "Delete label Hobbies? 12 expenses move to Other."

## Visual direction
- Follow the mockups: clean, high-contrast, large touch targets, amber accent for Enter, dark mode supported.

## Repo rules
- No Claude attribution in commits, PRs, or files (see CLAUDE.md).

## Left to the builder
- Module/package structure, DI approach, architecture pattern.
- Exact XLSX library, min SDK, animations, icons.
- Where the Labels and Settings entry points sit in the UI.
- Testing approach (unit tests for amount parsing, totals, export formatting, and backup serialization at minimum).
