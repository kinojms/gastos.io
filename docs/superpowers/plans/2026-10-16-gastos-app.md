# Gastos.io Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gastos.io, a native Android expense tracker with a numpad-first home screen, a numpad home-screen widget, editable lists, CSV/XLSX export, and GitHub backup.

**Architecture:** Single Gradle module `app`. Room is the local source of truth; repositories expose Flows to Compose ViewModels. Every data change calls one `onDataChanged()` hook that refreshes the widget and schedules a debounced GitHub backup via WorkManager. Manual DI through an `AppContainer` held by `GastosApp`.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, compileSdk/targetSdk 35, minSdk 26, Jetpack Compose (BOM 2024.12.01) + Material 3, Navigation Compose 2.8.5, Room 2.6.1 (KSP 2.0.21-1.0.28), DataStore Preferences 1.1.1, Glance 1.1.1, WorkManager 2.10.0, OkHttp 4.12.0, kotlinx-serialization-json 1.7.3, Reorderable (`sh.calvin.reorderable:reorderable:2.4.3`). Tests: JUnit 4.13.2, Robolectric 4.14.1, kotlinx-coroutines-test 1.9.0, OkHttp MockWebServer 4.12.0.

**Spec:** `docs/superpowers/specs/2026-10-16-expenses-tracker-design.md` (read it before starting). Mockups: https://claude.ai/artifact/A32jzAPiiHAf8xuofVGEAZ

## Global Constraints

- **No Claude attribution** in commits, PRs, or files. Plain commit messages only; commit with the local git identity.
- Package / applicationId: `io.gastos.app`. App label: `Gastos.io`.
- Currency ₱, displayed `₱1,234.50`. Amounts stored as `Long` centavos. Max 2 decimals. Max amount ₱9,999,999.99 (`999_999_999` centavos).
- Zero/empty amount on Enter is ignored (no save, no toast).
- Default labels, in order: Food, Transpo, Hobbies, Bills, Other. "Other" (`isOther = true`) cannot be deleted; deleting a label moves its expenses to Other.
- Confirm popup before every create/update/delete **except** adding an expense from the Home numpad or widget.
- Day boundaries use the device time zone (`ZoneId.systemDefault()`).
- Theme follows system light/dark. Enter button uses amber accent (`#E8A317` light, `#F2B33A` dark).
- Local save never depends on network. Backup is off on a fresh install until the owner restores or confirms "Start fresh".
- Android SDK is at `%LOCALAPPDATA%\Android\Sdk` (platform 35, build-tools 35.0.1 installed). JDK 21 is on PATH. No global `gradle` — the wrapper must be generated (Task 1).

## Review Focus

1. **Amount entry edge cases** — leading `.`, second `.`, a 3rd decimal, leading zeros, over the max: input is ignored, never crashes, never stores a wrong value. Pinned in Task 2.
2. **Midnight / edited dates** — an expense at 23:59 belongs to that day; editing its date moves it to the new day's list and totals. Pinned in Task 3.
3. **Deleted label still in use** — if the last-used label (app or widget) is deleted, both fall back to the first label instead of crashing or saving to a missing label. Pinned in Task 3 and Task 4.
4. **Backup on a new phone / bad token / offline** — empty DB never overwrites `backup.json`; a 401/404/offline restore shows an error and leaves local data untouched. Pinned in Task 9.
5. **Label names with special characters** — commas/quotes in CSV and `& < > "` in XLSX are escaped so files open correctly. Pinned in Task 7.

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `local.properties` (gitignored), `.gitignore`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/io/gastos/app/GastosApp.kt`, `app/src/main/java/io/gastos/app/MainActivity.kt`, `app/src/main/java/io/gastos/app/ui/theme/Theme.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/io/gastos/app/SmokeTest.kt`

**Interfaces:**
- Produces: `GastosApp : Application` with `lateinit var container: AppContainer` (AppContainer created in Task 3). `GastosTheme(content: @Composable () -> Unit)` with color tokens from the mockups (bg, surface, surface2, fg, muted, line, accent, key, danger).

- [ ] **Step 1:** Generate the wrapper: download Gradle 8.10.2 bin zip into the scratchpad, unzip, run `<unzipped>/bin/gradle wrapper --gradle-version 8.10.2` in the repo root.
- [ ] **Step 2:** Write `local.properties` with `sdk.dir=C\:\\Users\\Justin Sacdalan\\AppData\\Local\\Android\\Sdk`. `.gitignore`: `.gradle/`, `build/`, `local.properties`, `*.jks`, `keystore.properties`, `.idea/`, `captures/`.
- [ ] **Step 3:** Version catalog + build files with all Tech Stack deps (plugins: android-application, kotlin-android, kotlin-compose, kotlin-serialization, ksp). Enable `testOptions.unitTests.isIncludeAndroidResources = true`. Room schema export dir `app/schemas`.
- [ ] **Step 4:** `MainActivity` shows `GastosTheme { Text("Gastos.io") }`. `SmokeTest` asserts `1 + 1 == 2`.
- [ ] **Step 5:** Run `./gradlew testDebugUnitTest assembleDebug` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `git commit -m "Scaffold Android project"`

### Task 2: Money and numpad input logic

**Files:**
- Create: `app/src/main/java/io/gastos/app/domain/Money.kt`, `.../domain/AmountInput.kt`
- Test: `app/src/test/java/io/gastos/app/domain/MoneyTest.kt`, `AmountInputTest.kt`

**Interfaces:**
- Produces:
  - `object Money { const val MAX_CENTAVOS = 999_999_999L; fun format(centavos: Long): String; fun plain(centavos: Long): String }` — `format(4550) == "₱45.50"`, `format(123450) == "₱1,234.50"`, `plain(4550) == "45.50"` (no symbol, no commas; for CSV/XLSX).
  - `enum class Key { D0..D9 as DIGIT(n), DOT, BACKSPACE }` → implement as `sealed interface Key { data class Digit(val n: Int); data object Dot; data object Backspace }`.
  - `object AmountInput { fun apply(current: String, key: Key): String; fun toCentavos(text: String): Long?; fun display(text: String): String }` — pure string state, shared by Home and widget.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun digitsAppend() = assertEquals("45", AmountInput.apply("4", Key.Digit(5)))
@Test fun leadingZeroReplaced() = assertEquals("7", AmountInput.apply("0", Key.Digit(7)))
@Test fun dotFirstBecomesZeroDot() = assertEquals("0.", AmountInput.apply("", Key.Dot))
@Test fun secondDotIgnored() = assertEquals("4.5", AmountInput.apply("4.5", Key.Dot))
@Test fun thirdDecimalIgnored() = assertEquals("4.55", AmountInput.apply("4.55", Key.Digit(1)))
@Test fun overMaxIgnored() = assertEquals("9999999", AmountInput.apply("9999999", Key.Digit(1)))
@Test fun backspace() = assertEquals("4.", AmountInput.apply("4.5", Key.Backspace))
@Test fun backspaceEmpty() = assertEquals("", AmountInput.apply("", Key.Backspace))
@Test fun toCentavos() { assertEquals(4550L, AmountInput.toCentavos("45.5")); assertEquals(4500L, AmountInput.toCentavos("45.")); assertNull(AmountInput.toCentavos("")); assertNull(AmountInput.toCentavos("0.00")) }
@Test fun displayEmptyIsZero() = assertEquals("0", AmountInput.display(""))
@Test fun formatPeso() { assertEquals("₱45.50", Money.format(4550)); assertEquals("₱1,234.50", Money.format(123450)); assertEquals("₱0.00", Money.format(0)); assertEquals("45.50", Money.plain(4550)) }
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.domain.*"` — Expected: FAIL (unresolved references).
- [ ] **Step 3:** Implement. `Money.format` must not depend on device locale (build the string manually with `,` grouping).
- [ ] **Step 4:** Re-run — Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add money formatting and numpad input logic"`

### Task 3: Data layer (Room, repositories, prefs, AppContainer)

**Files:**
- Create: `app/src/main/java/io/gastos/app/data/` — `Entities.kt`, `ExpenseDao.kt`, `LabelDao.kt`, `GastosDatabase.kt`, `ExpenseRepository.kt`, `LabelRepository.kt`, `AppPrefs.kt`, `Models.kt`
- Create: `app/src/main/java/io/gastos/app/AppContainer.kt`
- Test: `app/src/test/java/io/gastos/app/data/ExpenseRepositoryTest.kt`, `LabelRepositoryTest.kt` (Robolectric, in-memory Room, fixed `Clock` in `Asia/Manila`)

**Interfaces:**
- Produces:
  - `@Entity("labels") data class LabelEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val colorArgb: Int, val position: Int, val isOther: Boolean = false)`
  - `@Entity("expenses", indices=[Index("timestamp"), Index("labelId")]) data class ExpenseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val amountCentavos: Long, val labelId: Long, val timestamp: Long /* epoch millis */)`
  - `data class ExpenseRow(val id: Long, val amountCentavos: Long, val timestamp: Long, val labelId: Long, val labelName: String, val labelColor: Int)`
  - `data class DaySummary(val date: LocalDate, val count: Int, val totalCentavos: Long)`
  - `data class LabelTotal(val labelId: Long, val name: String, val colorArgb: Int, val totalCentavos: Long)`
  - `class ExpenseRepository(db: GastosDatabase, clock: Clock, zone: () -> ZoneId, onDataChanged: () -> Unit)`:
    `suspend fun add(amountCentavos: Long, labelId: Long): Long`, `suspend fun update(id: Long, amountCentavos: Long, labelId: Long, timestamp: Long)`, `suspend fun delete(id: Long)`, `fun observeDay(date: LocalDate): Flow<List<ExpenseRow>>` (newest first), `fun observeDayTotal(date: LocalDate): Flow<Long>`, `suspend fun dayTotal(date: LocalDate): Long`, `fun observeDaySummaries(month: YearMonth): Flow<List<DaySummary>>` (newest first, only days with expenses), `fun observeMonthByLabel(month: YearMonth): Flow<List<LabelTotal>>` (largest first), `suspend fun rowsForDay(date: LocalDate): List<ExpenseRow>`, `suspend fun rowsForMonth(month: YearMonth): List<ExpenseRow>` (oldest first, for export), `fun today(): LocalDate`
  - `class LabelRepository(db: GastosDatabase, onDataChanged: () -> Unit)`:
    `fun observeAll(): Flow<List<LabelEntity>>` (by position), `suspend fun all(): List<LabelEntity>`, `suspend fun add(name: String, colorArgb: Int): Long`, `suspend fun rename(id: Long, name: String)`, `suspend fun recolor(id: Long, colorArgb: Int)`, `suspend fun reorder(idsInOrder: List<Long>)`, `suspend fun delete(id: Long)` (moves expenses to Other in one transaction; throws `IllegalArgumentException` for Other), `suspend fun expenseCount(id: Long): Int`
  - `val LABEL_PALETTE: List<Int>` — 8 colors starting with the mockup ones: `#D9822B, #2F7FC1, #8A5CC9, #3A9A6A, #77808B, #C2412D, #C9A227, #2A9D9D`.
  - Default labels seeded in `RoomDatabase.Callback.onCreate`: Food `#D9822B`, Transpo `#2F7FC1`, Hobbies `#8A5CC9`, Bills `#3A9A6A`, Other `#77808B` (isOther).
  - `class AppPrefs(context: Context)` (DataStore, file `prefs`): `val lastLabelId: Flow<Long?>`, `suspend fun setLastLabelId(id: Long)`, `suspend fun resolveLabel(labels: List<LabelEntity>): LabelEntity` (last-used if it still exists, else first by position). Backup keys are added in Task 9.
  - `class AppContainer(context: Context)` exposing `db`, `expenses`, `labels`, `prefs`, and `fun onDataChanged()` (empty body for now; Tasks 8 and 9 fill it in). `GastosApp.onCreate` creates it.

- [ ] **Step 1: Write failing tests**

```kotlin
// ExpenseRepositoryTest (clock fixed at 2026-10-16T12:00 Asia/Manila)
@Test fun addAppearsInToday()            // add(4550, foodId) -> observeDay(2026-10-16).first() has 1 row, amount 4550, labelName "Food"
@Test fun lateNightStaysInThatDay()      // timestamp 2026-10-16T23:59 Manila -> in observeDay(10-16), not in observeDay(10-17)
@Test fun editingDateMovesDay()          // update timestamp to 2026-10-15 -> dayTotal(10-16)==0, dayTotal(10-15)==4550
@Test fun daySummariesNewestFirst()      // expenses on 10-14, 10-16 -> [10-16, 10-14] with counts and totals
@Test fun monthByLabelLargestFirst()     // Food 300, Transpo 500 -> [Transpo, Food]
@Test fun onDataChangedCalledOnEveryWrite() // counter == 3 after add, update, delete
// LabelRepositoryTest
@Test fun seedsDefaultLabelsInOrder()    // names == listOf("Food","Transpo","Hobbies","Bills","Other")
@Test fun deleteMovesExpensesToOther()   // expense on Hobbies, delete Hobbies -> expense.labelId == otherId
@Test fun cannotDeleteOther()            // assertThrows<IllegalArgumentException>
@Test fun reorderPersists()
@Test fun resolveLabelFallsBackWhenDeleted() // setLastLabelId(hobbies); delete hobbies; resolveLabel(all()).name == "Food"
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.data.*"` — Expected: FAIL.
- [ ] **Step 3:** Implement entities, DAOs (day/month ranges computed as `[startOfDay, startOfNextDay)` epoch millis in the zone), database (version 1, exportSchema true), repositories, prefs, container.
- [ ] **Step 4:** Re-run — Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add Room data layer, repositories, and prefs"`

### Task 4: Home screen, navigation shell, confirm dialog

**Files:**
- Create: `app/src/main/java/io/gastos/app/ui/Nav.kt`, `ui/components/ConfirmDialog.kt`, `ui/components/Numpad.kt`, `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`
- Modify: `MainActivity.kt` (host `GastosNavHost`)
- Test: `app/src/test/java/io/gastos/app/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `AmountInput`, `Money`, `ExpenseRepository`, `LabelRepository`, `AppPrefs.resolveLabel`.
- Produces:
  - Routes: `home` (start), `expenses`, `day/{isoDate}`, `labels`, `settings`.
  - `@Composable fun ConfirmDialog(message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false)` — used by all later tasks.
  - `@Composable fun Numpad(onKey: (Key) -> Unit)` (3×4 grid: 1–9, `.`, 0, ⌫).
  - `HomeViewModel` state: `data class HomeState(val input: String, val label: LabelEntity?, val labels: List<LabelEntity>, val todayTotal: Long, val today: LocalDate)`; events `onKey(Key)`, `selectLabel(id)`, `enter()`; one-shot `toasts: Flow<String>` emitting `"Added ₱45.50 · Food"`.
- Layout per mockup screen 1: date `"Fri, Oct 16"` + `"Today ₱485.00"`; list button (top right) → `expenses`; overflow menu with "Labels" and "Settings"; big amount; label chip opens a dropdown of labels; Numpad; full-width amber Enter. No confirm on Enter.

- [ ] **Step 1: Write failing tests** (fake repos or in-memory Room via Robolectric)

```kotlin
@Test fun enterSavesAndClears()        // keys 4,5,.,5 -> enter() -> one expense 4550 on Food; state.input == ""; toast "Added ₱45.50 · Food"
@Test fun enterWithZeroDoesNothing()   // enter() with "" -> no expense, no toast
@Test fun selectingLabelUpdatesLastUsed() // selectLabel(transpo) -> prefs.lastLabelId == transpo
@Test fun startsOnLastUsedLabel()
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.ui.home.*"` — Expected: FAIL.
- [ ] **Step 3:** Implement ViewModel, then screens and nav.
- [ ] **Step 4:** Re-run tests — PASS. Then `./gradlew assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit: `git commit -m "Add home screen with numpad entry"`

### Task 5: Expenses list (Today / Days / Month), day detail, edit sheet

**Files:**
- Create: `ui/expenses/ExpensesScreen.kt`, `ui/expenses/ExpensesViewModel.kt`, `ui/expenses/DayList.kt`, `ui/expenses/DayScreen.kt`, `ui/expenses/EditExpenseSheet.kt`, `ui/expenses/MonthTab.kt`
- Test: `app/src/test/java/io/gastos/app/ui/expenses/ExpensesViewModelTest.kt`, `ui/ConfirmCopyTest.kt`
- Create: `ui/ConfirmCopy.kt`

**Interfaces:**
- Consumes: `ExpenseRepository` observe/update/delete, `ConfirmDialog`.
- Produces: `object ConfirmCopy` with exact strings (used by Tasks 5, 6, 10):
  - `saveExpense(amount: Long, label: String) = "Save changes to ₱45.50 · Transpo?"`
  - `deleteExpense(amount: Long, label: String) = "Delete ₱45.50 · Transpo?"`
  - `addLabel(name) = "Add label $name?"`, `renameLabel(old, new) = "Rename $old to $new?"`, `recolorLabel(name) = "Change color of $name?"`, `reorderLabels() = "Save new label order?"`
  - `deleteLabel(name, count) = "Delete label $name? $count expenses move to Other."` (`count == 0` → `"Delete label $name?"`; `count == 1` → `"1 expense moves to Other."`)
  - `restore() = "Restore from GitHub? This replaces all data on this phone."`, `startFresh() = "Start fresh? Backups will begin from this phone's data."`, `saveBackupSettings() = "Save backup settings?"`
- Tabs: **Today** (= `DayList(today)`), **Days** (month switcher ‹ ›, `observeDaySummaries`, includes today; tap → `day/{iso}`), **Month** (switcher, total, per-label bars). Day list header shows date + total; rows: color dot, label, time `h:mm a`, amount. Each day list and the Month tab have Export CSV / Export XLSX buttons (wired in Task 7; render disabled until then).
- Edit sheet: amount (numpad-style text field accepting AmountInput rules), label dropdown, date picker, time picker; Save → ConfirmDialog(saveExpense) → update; Delete → ConfirmDialog(deleteExpense, destructive) → delete.

- [ ] **Step 1:** Write failing tests: `ConfirmCopyTest` asserts each string above with the example values (incl. 0/1/12 expense counts); `ExpensesViewModelTest` asserts month switcher prev/next from 2026-10 → 2026-09 / 2026-11 and that saving an edit with a new date updates that day's summary.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.ui.*"` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run — PASS; `./gradlew assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit: `git commit -m "Add expenses list, day view, and edit sheet"`

### Task 6: Labels screen

**Files:**
- Create: `ui/labels/LabelsScreen.kt`, `ui/labels/LabelsViewModel.kt`
- Test: `app/src/test/java/io/gastos/app/ui/labels/LabelsViewModelTest.kt`

**Interfaces:**
- Consumes: `LabelRepository`, `LABEL_PALETTE`, `ConfirmCopy`, `ConfirmDialog`.
- Behavior: list in position order with drag handles (Reorderable lib). Dragging changes a local order; a "Save order" button appears → ConfirmDialog(reorderLabels) → `reorder`. Tap a label → edit dialog (name + palette swatches); saving a changed name → confirm rename; changed color → confirm recolor. "+ Add label" → dialog → confirm add. Delete (hidden for Other) → confirm with `expenseCount`. Names trimmed; blank or duplicate (case-insensitive) names rejected with an inline error "That label already exists" / "Name can't be empty".

- [ ] **Step 1:** Failing tests: add rejects blank and duplicate ("food" when "Food" exists); delete of Other is not offered (`canDelete(other) == false`); reorder saves the new order.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.ui.labels.*"` — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run — PASS; `assembleDebug` — SUCCESS.
- [ ] **Step 5:** Commit: `git commit -m "Add labels screen"`

### Task 7: CSV and XLSX export

**Files:**
- Create: `app/src/main/java/io/gastos/app/export/CsvExporter.kt`, `export/XlsxWriter.kt`, `export/ExportService.kt`, `app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider `io.gastos.app.fileprovider`), Task 5 screens (enable export buttons)
- Test: `app/src/test/java/io/gastos/app/export/CsvExporterTest.kt`, `XlsxWriterTest.kt`

**Interfaces:**
- Consumes: `ExpenseRow`, `LabelTotal`, `Money.plain`.
- Produces:
  - `object CsvExporter { fun write(rows: List<ExpenseRow>, zone: ZoneId): ByteArray }` — UTF-8 **with BOM**, header `Date,Time,Label,Amount`, rows `2026-10-16,08:10,Transpo,45.50`, final row `,,Total,485.00`. RFC 4180 quoting for fields with `,` `"` or newline.
  - `object XlsxWriter { fun write(sheets: List<Sheet>): ByteArray }` — hand-written OOXML via `ZipOutputStream` (no library). `data class Sheet(val name: String, val header: List<String>, val rows: List<List<Cell>>, val totalLabelCol: Int, val sumCols: List<Int>, val colWidths: List<Double>)`; `sealed interface Cell { Text(s), Peso(centavos), Date(LocalDate) }`.
  - Styles: header bold; `Peso` numFmt `"₱"#,##0.00` (XML-escaped quotes); `Date` numFmt `yyyy-mm-dd` (Excel serial = days since 1899-12-30); total row bold with `SUM(D2:D{n})` formulas plus cached value; header row frozen (`<pane ySplit="1" topLeftCell="A2" state="frozen"/>`).
  - `object ExportFiles { fun dayName(d: LocalDate, ext: String) = "expenses-2026-10-16.$ext"; fun monthName(m: YearMonth, ext: String) = "expenses-2026-10.$ext" }`
  - `class ExportService(context, expenses: ExpenseRepository)`: `suspend fun exportDay(date, format: Format): Uri`, `suspend fun exportMonth(month, format): Uri` (writes to `cacheDir/exports`); monthly XLSX = sheet "Expenses" (Date, Time, Label, Amount) + sheet "By label" (Label, Amount, Total row). `enum class Format { CSV, XLSX }`.
- UI: Export button → small menu **Share** (`ACTION_SEND` chooser) / **Save to phone** (`ACTION_CREATE_DOCUMENT`, copy bytes). Exporting creates nothing in the DB, so no confirm popup.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun csvHasBomHeaderRowsTotal()  // bytes start EF BB BF; lines == ["Date,Time,Label,Amount","2026-10-16,08:10,Transpo,45.50",",,Total,45.50"]
@Test fun csvQuotesSpecialChars()     // label "Food, \"snacks\"" -> "\"Food, \"\"snacks\"\"\""
@Test fun xlsxIsValidZipWithParts()   // entries include [Content_Types].xml, xl/workbook.xml, xl/styles.xml, xl/worksheets/sheet1.xml
@Test fun xlsxEscapesXml()            // label "A&B <x>" appears as "A&amp;B &lt;x&gt;" in sheet1.xml
@Test fun xlsxPesoFormatAndSum()      // styles.xml contains formatCode="&quot;₱&quot;#,##0.00"; sheet1.xml contains <f>SUM(D2:D3)</f>
@Test fun xlsxDateSerial()            // 2026-10-16 -> <v>46311</v>
@Test fun xlsxMonthlyHasTwoSheets()   // workbook.xml has sheets "Expenses" and "By label"
@Test fun fileNames()                 // "expenses-2026-10-16.csv", "expenses-2026-10.xlsx"
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.export.*"` — FAIL.
- [ ] **Step 3:** Implement writers, service, FileProvider, UI menu.
- [ ] **Step 4:** Re-run — PASS; `assembleDebug` — SUCCESS.
- [ ] **Step 5:** Commit: `git commit -m "Add CSV and XLSX export"`

### Task 8: Home-screen widget

**Files:**
- Create: `app/src/main/java/io/gastos/app/widget/GastosWidget.kt`, `widget/GastosWidgetReceiver.kt`, `widget/WidgetActions.kt`, `widget/WidgetLogic.kt`, `app/src/main/res/xml/gastos_widget_info.xml`, `res/layout/widget_loading.xml`
- Modify: `AndroidManifest.xml` (receiver), `AppContainer.onDataChanged()` (call `GastosWidget().updateAll(context)` on an app-scoped coroutine)
- Test: `app/src/test/java/io/gastos/app/widget/WidgetLogicTest.kt`

**Interfaces:**
- Consumes: `AmountInput`, `Money`, `ExpenseRepository.add/dayTotal`, `LabelRepository.all`, `AppPrefs` (shared last-used label).
- Produces:
  - `object WidgetLogic { fun nextLabel(labels: List<LabelEntity>, currentId: Long?): LabelEntity }` — next by position, wraps to first; unknown/deleted id → first.
  - Glance state (`PreferencesGlanceStateDefinition`) key `input: String`.
  - ActionCallbacks: `DigitAction` (param `digit`), `DotAction`, `BackspaceAction`, `NextLabelAction` (writes `AppPrefs.setLastLabelId`), `EnterAction` (if `toCentavos` non-null: `expenses.add`, clear input; else no-op).
- Layout per mockup screen 7: top row = label ticker `‹ Food ›` (tap → NextLabel) + amount `₱45.50`; 4-column pad: `1 2 3 ⌫ / 4 5 6 Enter(2 rows) / 7 8 9 / . 0 [Today ₱485]`. Default 4×3 cells, `resizeMode="horizontal|vertical"`, `minWidth 250dp`, `minHeight 180dp`. Dark translucent background, amber Enter.

- [ ] **Step 1:** Failing tests: `nextLabel` cycles Food→Transpo→…→Other→Food; deleted current id → Food; empty-input Enter path returns no save (test the pure helper `WidgetLogic.amountToSave(input: String): Long?` = `AmountInput.toCentavos`).
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.widget.*"` — FAIL.
- [ ] **Step 3:** Implement widget, receiver, actions, manifest.
- [ ] **Step 4:** Re-run — PASS; `assembleDebug` — SUCCESS.
- [ ] **Step 5:** Commit: `git commit -m "Add numpad home-screen widget"`

### Task 9: GitHub backup and restore engine

**Files:**
- Create: `app/src/main/java/io/gastos/app/backup/BackupModels.kt`, `backup/BackupSerializer.kt`, `backup/GitHubClient.kt`, `backup/TokenStore.kt`, `backup/BackupManager.kt`, `backup/BackupWorker.kt`, `backup/BackupScheduler.kt`
- Modify: `AppPrefs.kt` (add `backupRepo: Flow<String?>` as `"owner/name"`, `backupEnabled: Flow<Boolean>` default false, `lastBackupAt: Flow<Long?>`, `lastBackupError: Flow<String?>`, setters), `AppContainer.onDataChanged()` (call `BackupScheduler.schedule()`), `GastosDatabase` (`suspend fun replaceAll(labels, expenses)` transaction)
- Test: `app/src/test/java/io/gastos/app/backup/BackupSerializerTest.kt`, `GitHubClientTest.kt` (MockWebServer), `BackupManagerTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `@Serializable data class BackupFile(val schemaVersion: Int = 1, val exportedAt: String /* ISO-8601 */, val labels: List<BackupLabel>, val expenses: List<BackupExpense>)` with fields mirroring the entities.
  - `object BackupSerializer { const val CURRENT = 1; fun encode(f: BackupFile): String; fun decode(json: String): BackupFile }` — `decode` throws `UnsupportedBackupException("This backup is from a newer version of Gastos.io. Update the app first.")` when `schemaVersion > CURRENT`; ignores unknown keys.
  - `class GitHubClient(http: OkHttpClient, baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl())`:
    - `suspend fun getSha(repo: String, path: String, token: String): String?` — GET `/repos/{repo}/contents/{path}`, return `sha` (works for files >1 MB, where `content` is empty); 404 → null.
    - `suspend fun download(repo, path, token): String?` — GET with `Accept: application/vnd.github.raw+json` (works past 1 MB); 404 → null.
    - `suspend fun upload(repo, path, token, content: String, sha: String?, message: String)` — PUT base64 body `{message, content, sha?}`.
    - Errors: throw `GitHubException(code: Int, userMessage: String)`: 401 → "GitHub token is invalid or expired.", 403 → "Token can't access this repo.", 404 on upload → "Repo not found.", IOException → "No internet connection."
    - Headers: `Authorization: Bearer <token>`, `X-GitHub-Api-Version: 2022-11-28`.
  - `class TokenStore(context)` — AES/GCM key in Android Keystore (alias `gastos_github_token`), ciphertext in a private SharedPreferences. `fun save(token: String)`, `fun load(): String?`, `fun clear()`. (Robolectric has no Keystore: guard tests by injecting a `Cipher` provider, or exclude TokenStore from unit tests.)
  - `class BackupManager(db, prefs, tokens, github, clock)`:
    - `suspend fun backupNow(): Result<Unit>` — no-op success if `backupEnabled == false`; else encode all data, `getSha`, `upload` to `backup.json` with message `"Backup 2026-10-16 12:00"`, set `lastBackupAt`, clear error. On failure set `lastBackupError`.
    - `suspend fun restore(): Result<Unit>` — download; null → failure "No backup found in this repo."; decode; `replaceAll`; set `backupEnabled = true`; call `onDataChanged`. On any failure, local data is untouched.
    - `suspend fun startFresh()` — sets `backupEnabled = true`, then `backupNow()`.
  - `object BackupScheduler { fun schedule(context) }` — unique work `"gastos-backup"`, `ExistingWorkPolicy.REPLACE`, `setInitialDelay(3, MINUTES)`, `NetworkType.CONNECTED`, exponential backoff 30 s. `fun runNow(context)` — same without delay. `BackupWorker` calls `backupNow()`; failure → `Result.retry()` for IOException, `Result.failure()` otherwise.

- [ ] **Step 1: Write failing tests**

```kotlin
// BackupSerializerTest
@Test fun roundTrip()                 // encode then decode equals original
@Test fun rejectsNewerSchema()        // schemaVersion 2 -> UnsupportedBackupException
@Test fun ignoresUnknownKeys()
// GitHubClientTest (MockWebServer)
@Test fun uploadSendsBase64AndSha()   // request body content == base64(json), sha == "abc"; Authorization header == "Bearer t"
@Test fun getShaReturnsNullOn404()
@Test fun downloadUsesRawAccept()     // Accept header == "application/vnd.github.raw+json"
@Test fun maps401ToFriendlyMessage()  // GitHubException.userMessage == "GitHub token is invalid or expired."
// BackupManagerTest (fake GitHubClient)
@Test fun freshInstallDoesNotUpload() // backupEnabled false -> backupNow() makes 0 upload calls
@Test fun restoreReplacesData()       // remote has 2 labels/1 expense -> local equals remote, backupEnabled true
@Test fun failedRestoreKeepsLocal()   // download throws GitHubException(401) -> local data unchanged, result failure with that message
@Test fun missingBackupFails()        // download returns null -> failure "No backup found in this repo."
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.backup.*"` — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run — PASS; `assembleDebug` — SUCCESS.
- [ ] **Step 5:** Commit: `git commit -m "Add GitHub backup and restore"`

### Task 10: Settings screen

**Files:**
- Create: `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/io/gastos/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `AppPrefs` backup keys, `TokenStore`, `BackupManager`, `BackupScheduler.runNow`, `ConfirmCopy`.
- Layout: "Backup" section — Repo field (`owner/name`), Token field (password-masked; shows "Token saved" when one exists), Save → ConfirmDialog(saveBackupSettings). Status line: "Last backup: Oct 16, 12:00 PM" / "Not backed up yet" / last error in danger color. Buttons: **Back up now**, **Restore from GitHub** (confirm `restore()`), and — while `backupEnabled == false` — **Start fresh** (confirm `startFresh()`) with helper text "Restore your old data or start fresh to turn on backups." "Labels" row → `labels`. Short help text: "Use a fine-grained token with Contents read/write on this repo only."
- Validation: repo must match `^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$` → else inline error "Use the format owner/repo".

- [ ] **Step 1:** Failing tests: invalid repo string shows the error and does not save; save stores repo + token; status text formats for null / timestamp / error.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests "io.gastos.app.ui.settings.*"` — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run — PASS; `assembleDebug` — SUCCESS.
- [ ] **Step 5:** Commit: `git commit -m "Add settings screen with backup controls"`

### Task 11: Release build, install notes, final check

**Files:**
- Modify: `app/build.gradle.kts` (release `signingConfig` read from gitignored `keystore.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; `isMinifyEnabled = true` with keep rules for kotlinx.serialization models and Room), `README.md` (Install + Backup setup sections), `app/proguard-rules.pro`
- Create: launcher icon (adaptive, simple ₱ glyph on amber) in `res/mipmap-anydpi-v26/`

- [ ] **Step 1:** Generate the release key once (outside the repo, e.g. `D:\keys\gastos-release.jks`) with `keytool -genkeypair -v -keystore D:\keys\gastos-release.jks -alias gastos -keyalg RSA -keysize 2048 -validity 10000`; ask the owner for passwords; write `keystore.properties`. Tell the owner to back up the `.jks` and passwords — losing them means updates can't install over the app.
- [ ] **Step 2:** README: how to install the APK on the S25 (allow "Install unknown apps" for the file manager), add the widget, create the private `expenses-data` repo and a fine-grained token, and restore on a new phone.
- [ ] **Step 3:** Run `./gradlew testDebugUnitTest assembleRelease` — Expected: all tests pass, `app/build/outputs/apk/release/app-release.apk` exists.
- [ ] **Step 4:** Confirm `git status` shows no `.jks`, `keystore.properties`, or `local.properties`.
- [ ] **Step 5:** Commit: `git commit -m "Add release signing, icon, and install guide"`
- [ ] **Step 6:** Set `versionName = "1.0.0"`, `versionCode = 1`. Copy the APK to `gastos-v1.0.0.apk` and publish: `gh release create v1.0.0 gastos-v1.0.0.apk --title "Gastos.io v1.0.0" --notes "First release."` (no Claude attribution in notes). Expected: `gh release view v1.0.0` lists the APK asset. Future releases bump `versionCode`/`versionName` and repeat with the same signing key.
- [ ] **Step 7:** README install section points to the repo's Releases page (owner must be logged into GitHub on the phone, since the repo is private).
