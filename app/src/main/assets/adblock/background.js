/* Zorvi Filter Engine
 * Applies supported domain-level network rules from EasyList and EasyPrivacy.
 * This intentionally does not claim full uBlock Origin filter-syntax coverage.
 */
"use strict";

const LISTS = [
  "https://easylist.to/easylist/easylist.txt",
  "https://easylist.to/easylist/easyprivacy.txt"
];
const FALLBACK = [
  "doubleclick.net", "googlesyndication.com", "googleadservices.com",
  "adservice.google.com", "adnxs.com", "adsrvr.org", "taboola.com",
  "outbrain.com", "criteo.com", "scorecardresearch.com", "zedo.com",
  "quantserve.com", "analytics.google.com"
];
let blockedHosts = new Set(FALLBACK);
let allowedHosts = new Set();
let enabled = true;
let blockedCount = 0;

function hostFromRule(line) {
  const exception = line.startsWith("@@");
  const source = exception ? line.slice(2) : line;
  if (!source.startsWith("||")) return null;
  const rest = source.slice(2);
  const match = rest.match(/^([a-z0-9.-]+)\^/i);
  if (!match || !match[1].includes(".")) return null;
  return { host: match[1].toLowerCase().replace(/^\.+|\.+$/g, ""), exception };
}

function parseList(text) {
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("!") || line.startsWith("[") || line.includes("$")) continue;
    const rule = hostFromRule(line);
    if (!rule) continue;
    (rule.exception ? allowedHosts : blockedHosts).add(rule.host);
  }
}

function matches(host, set) {
  for (const domain of set) {
    if (host === domain || host.endsWith("." + domain)) return true;
  }
  return false;
}

async function refreshLists() {
  for (const url of LISTS) {
    try {
      const response = await fetch(url, { cache: "no-cache" });
      if (response.ok) parseList(await response.text());
    } catch (_) {
      // Keep the embedded fallback rules when offline or a list is unavailable.
    }
  }
  await browser.storage.local.set({ lastUpdated: Date.now(), ruleCount: blockedHosts.size });
}

browser.webRequest.onBeforeRequest.addListener(
  details => {
    if (!enabled || !/^https?:/i.test(details.url)) return {};
    let host;
    try { host = new URL(details.url).hostname.toLowerCase(); } catch (_) { return {}; }
    if (matches(host, allowedHosts)) return {};
    if (matches(host, blockedHosts)) {
      blockedCount++;
      browser.storage.local.set({ blockedCount }).catch(() => {});
      return { cancel: true };
    }
    return {};
  },
  { urls: ["<all_urls>"], types: ["script", "image", "stylesheet", "object", "xmlhttprequest", "sub_frame", "media", "font", "other"] },
  ["blocking"]
);

browser.runtime.onInstalled.addListener(() => {
  browser.storage.local.set({ enabled: true, blockedCount: 0 });
  refreshLists();
});
browser.runtime.onStartup.addListener(refreshLists);
browser.storage.local.get(["enabled"]).then(value => {
  enabled = value.enabled !== false;
  refreshLists();
});
