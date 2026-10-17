# Gastos.io

A fast, personal expense tracker for Android and iPhone. Open the app, type an amount, hit enter.

## Features

- Payment methods: Cash, GCash, Maribank, BPI, Maya and BDO. You can customize the list. Swipe left or right to change the method. The screen tints to its color. Swipe up or down to change the label.
- Optional "What did you buy?" description. Type it or say it by voice, in English or Tagalog.
- Notes in Edit expense.
- CSV and XLSX exports include Description, Payment and Notes. The XLSX also has a "By payment" monthly sheet.
- Five themes: Classic, Sand, Nord, Mocha and Forest. Each has light and dark.
- Widget payment ticker.
- Save and restore a backup file (Settings → Backup).
- New wallet icon.
- Swipe an expense left to edit or delete it.
- Settings → Submit feedback sends ideas, bug reports and questions straight to the developer.
- A short guide shows the main features on first launch; replay it from Settings → Show guide.
- About the dev.

## Install

1. On the phone, open the browser.
2. Open the repo's Releases page and download the latest `gastos-vX.Y.Z.apk`.
3. Open the file. If asked, allow "Install unknown apps" for that browser or file manager (Samsung: Settings > Apps > the browser/My Files > Install unknown apps).
4. Install. Later versions install over the old one.

## iPhone (web app)

iPhone users can use Gastos.io as a web app. It has everything the Android app has except the widget.

1. On the iPhone, open https://kinojms.github.io/gastos.io/ in **Safari**.
2. Tap the Share button, then **Add to Home Screen**.
3. Open Gastos.io from its Home Screen icon. It runs full-screen and works offline.

Things to know:

- **Add expenses only in the installed app.** Safari and the Home Screen app keep separate data, so anything added in Safari won't show up in the installed app.
- **Back up your data.** Your expenses live only on your phone. If you delete the Home Screen icon or clear Safari's data, they are gone. Use Settings → Save backup file now and then (save it to Files or iCloud Drive), or set up GitHub backup as described below. iPhones can't back up in the background, so GitHub backup runs while the app is open.
- **Moving between Android and iPhone:** both use the same backup format. Restore the same GitHub repo on the new phone, or save a backup file on one and use Restore from file on the other.
- Voice input may not work on iPhone. Typing always does.
- The GitHub token is stored on the phone without encryption (a browser limit). Use a fine-grained token limited to the backup repo.

To build it yourself: `cd web && npm ci && npm test && npm run build`. Pushing changes under `web/` to `main` redeploys it through GitHub Actions.

## Widget (Android only)

Long-press the home screen, tap Widgets, find Gastos.io, and add it.

## Backup setup

1. On GitHub, create a private repo named `expenses-data` and add a README so it is not empty.
2. Create a fine-grained personal access token limited to that repo only, with Contents set to read and write.
3. In the app, open Settings, paste `owner/expenses-data` and the token, and tap Save.
4. Saving a new or changed repo or token pauses auto-backup until you tap Restore from GitHub or Start fresh.
5. On a new phone, tap Restore from GitHub to load the backup. On a first-time setup tap Start fresh. Backup stays off until you pick one.

## File backup

Settings → Backup → Save backup file writes all your data to a `.json` file you choose where to keep. Restore from file loads one back and replaces everything on the phone. The file is the same format as the GitHub backup, so it also works across Android and iPhone. If GitHub backup isn't set up, Home reminds you after 14 days without a backup.

## Feedback relay

Submit feedback posts to a small Cloudflare Worker in `feedback-relay/`, which forwards each message to the developer's Telegram. Its URL lives in `config/feedback-url.txt`; leave that file blank to hide the feature. Setup steps are in `feedback-relay/README.md`.

## Building

`./gradlew testDebugUnitTest assembleRelease`. For a signed release, put `storeFile`, `storePassword`, `keyAlias`, `keyPassword` in a gitignored `keystore.properties` at the repo root. Without it, release builds fail; pass `-PallowDebugSigning` to sign a test release with the debug key instead. Debug builds and unit tests don't need the file.
