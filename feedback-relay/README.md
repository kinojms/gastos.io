# Gastos.io feedback relay

A Cloudflare Worker that receives feedback from the apps and forwards it to the owner's Telegram chat. The bot token stays in the Worker's secrets; the apps only know this Worker's URL (`config/feedback-url.txt`).

## One-time setup

1. **Bot:** in Telegram, open @BotFather → `/newbot` → pick a name and a username ending in `bot` → copy the token (keep it private).
2. **Chat ID:** open your bot, tap Start, send any message, then visit `https://api.telegram.org/bot<TOKEN>/getUpdates` and copy the number after `"chat":{"id":`.
3. **Cloudflare:** create a free account at dash.cloudflare.com.
4. **Worker**, either way:
   - *Dashboard (works on a phone):* Workers & Pages → Create → Start with Hello World → name it `gastos-feedback` → Deploy. Edit code → replace everything with the contents of `worker.js` → Deploy. Settings → Variables and Secrets → add `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` as type **Secret** → Deploy.
   - *Terminal:* in this folder run `npx wrangler login`, `npx wrangler secret put TELEGRAM_BOT_TOKEN`, `npx wrangler secret put TELEGRAM_CHAT_ID`, `npx wrangler deploy`.
5. Put the Worker URL (e.g. `https://gastos-feedback.<you>.workers.dev`) in `config/feedback-url.txt` and release the apps.

## Test

```sh
curl -X POST https://gastos-feedback.<you>.workers.dev \
  -H 'Content-Type: application/json' \
  -d '{"type":"Question","message":"relay test","contact":"","appVersion":"1.4.0","platform":"Web"}'
```
Expect `{"ok":true}` and a message in Telegram.

## Changing the code

Edit `src/`, run `npm test`, then `npm run bundle` and copy `dist/index.js` into `worker.js` (or `npm run deploy`).

## If it gets spammed

Rename `name` in `wrangler.toml` (or create a Worker with a new name in the dashboard), redeploy, and update `config/feedback-url.txt`. The old URL stops working once you delete the old Worker.
