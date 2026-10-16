# Gastos.io

A fast, personal expense tracker for Android. Open the app, type an amount, hit enter.

## Features

- Payment methods: Cash, GCash, Maribank, BPI, Maya and BDO. You can customize the list. Swipe left or right to change the method. The screen tints to its color. Swipe up or down to change the label.
- Optional "What did you buy?" description. Type it or say it by voice, in English or Tagalog.
- Notes in Edit expense.
- CSV and XLSX exports include Description, Payment and Notes. The XLSX also has a "By payment" monthly sheet.
- Five themes: Classic, Sand, Nord, Mocha and Forest. Each has light and dark.
- Widget payment ticker.
- A short guide shows the main features on first launch; replay it from Settings → Show guide.
- About the dev.

## Install

1. On the phone, log into GitHub in the browser (the repo is private).
2. Open the repo's Releases page and download the latest `gastos-vX.Y.Z.apk`.
3. Open the file. If asked, allow "Install unknown apps" for that browser or file manager (Samsung: Settings > Apps > the browser/My Files > Install unknown apps).
4. Install. Later versions install over the old one.

## Widget

Long-press the home screen, tap Widgets, find Gastos.io, and add it.

## Backup setup

1. On GitHub, create a private repo named `expenses-data` and add a README so it is not empty.
2. Create a fine-grained personal access token limited to that repo only, with Contents set to read and write.
3. In the app, open Settings, paste `owner/expenses-data` and the token, and tap Save.
4. On a new phone, tap Restore from GitHub to load the backup. On a first-time setup tap Start fresh. Backup stays off until you pick one.

## Building

`./gradlew testDebugUnitTest assembleRelease`. For a signed release, put `storeFile`, `storePassword`, `keyAlias`, `keyPassword` in a gitignored `keystore.properties` at the repo root. Without it, release builds fail; pass `-PallowDebugSigning` to sign a test release with the debug key instead. Debug builds and unit tests don't need the file.
