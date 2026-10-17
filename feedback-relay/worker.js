// Paste-ready single file for the Cloudflare dashboard editor. Generated from src/ by `npm run bundle`; do not edit by hand.
var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/feedback.ts
var TYPES = ["Suggestion", "Bug report", "Question", "Something else"];
var PLATFORMS = ["Android", "iPhone", "Web"];
var MESSAGE_MAX = 2e3;
var CONTACT_MAX = 100;
var VERSION_MAX = 20;
var ALLOWED_ORIGINS = ["https://kinojms.github.io", "http://localhost:5173", "http://localhost:4173"];
function parseFeedback(body) {
  if (typeof body !== "object" || body === null || Array.isArray(body)) return null;
  const b = body;
  const contactRaw = b.contact === void 0 ? "" : b.contact;
  if (typeof b.type !== "string" || typeof b.message !== "string" || typeof contactRaw !== "string") return null;
  if (typeof b.appVersion !== "string" || typeof b.platform !== "string") return null;
  if (!TYPES.includes(b.type)) return null;
  if (!PLATFORMS.includes(b.platform)) return null;
  const message = b.message.trim();
  const contact = contactRaw.trim();
  const appVersion = b.appVersion.trim();
  if (message.length === 0 || message.length > MESSAGE_MAX) return null;
  if (contact.length > CONTACT_MAX || appVersion.length > VERSION_MAX) return null;
  return { type: b.type, message, contact, appVersion, platform: b.platform };
}
__name(parseFeedback, "parseFeedback");
function escapeHtml(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
__name(escapeHtml, "escapeHtml");
function manilaStamp(d) {
  const m = new Date(d.getTime() + 8 * 3600 * 1e3);
  const pad = /* @__PURE__ */ __name((n) => String(n).padStart(2, "0"), "pad");
  return `${m.getUTCFullYear()}-${pad(m.getUTCMonth() + 1)}-${pad(m.getUTCDate())} ${pad(m.getUTCHours())}:${pad(m.getUTCMinutes())}`;
}
__name(manilaStamp, "manilaStamp");
function formatMessage(f, receivedAt) {
  return [
    "[FEEDBACK - Gastos.io]",
    `Type: ${escapeHtml(f.type)}`,
    `From: ${f.contact ? escapeHtml(f.contact) : "\u2014"}`,
    `App: v${escapeHtml(f.appVersion)} \xB7 ${escapeHtml(f.platform)}`,
    `Time: ${manilaStamp(receivedAt)} (Asia/Manila)`,
    "",
    escapeHtml(f.message)
  ].join("\n");
}
__name(formatMessage, "formatMessage");
function corsHeaders(origin) {
  const headers = {
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin"
  };
  if (origin && ALLOWED_ORIGINS.includes(origin)) headers["Access-Control-Allow-Origin"] = origin;
  return headers;
}
__name(corsHeaders, "corsHeaders");

// src/index.ts
var MAX_BODY_BYTES = 8192;
var index_default = {
  async fetch(request, env) {
    const cors = corsHeaders(request.headers.get("Origin"));
    const reply = /* @__PURE__ */ __name((status, body2) => new Response(body2 ? JSON.stringify(body2) : null, {
      status,
      headers: { ...cors, ...body2 ? { "Content-Type": "application/json" } : {} }
    }), "reply");
    if (request.method === "OPTIONS") return reply(204);
    if (request.method !== "POST") return reply(405, { ok: false });
    const raw = await request.text();
    if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) return reply(413, { ok: false });
    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return reply(400, { ok: false });
    }
    const feedback = parseFeedback(body);
    if (!feedback) return reply(400, { ok: false });
    try {
      const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          chat_id: env.TELEGRAM_CHAT_ID,
          text: formatMessage(feedback, /* @__PURE__ */ new Date()),
          parse_mode: "HTML",
          disable_web_page_preview: true
        })
      });
      return res.ok ? reply(200, { ok: true }) : reply(502, { ok: false });
    } catch {
      return reply(502, { ok: false });
    }
  }
};
export {
  index_default as default
};
