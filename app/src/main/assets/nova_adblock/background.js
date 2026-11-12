"use strict";
const blockedHosts = new Set([
 "doubleclick.net","googlesyndication.com","googleadservices.com","google-analytics.com",
 "googletagmanager.com","adservice.google.com","adnxs.com","adsrvr.org","adform.net",
 "adroll.com","taboola.com","outbrain.com","criteo.com","pubmatic.com",
 "rubiconproject.com","openx.net","casalemedia.com","scorecardresearch.com",
 "quantserve.com","hotjar.com","mixpanel.com","segment.io","branch.io",
 "app-measurement.com","adjust.com","unityads.unity3d.com","applovin.com",
 "ads-twitter.com","ads.linkedin.com","connect.facebook.net"
]);
function isBlocked(url) {
 try {
  const host = new URL(url).hostname.toLowerCase();
  for (const domain of blockedHosts) if (host === domain || host.endsWith("." + domain)) return true;
 } catch (_) {}
 return false;
}
browser.webRequest.onBeforeRequest.addListener(
 details => isBlocked(details.url) ? { cancel: true } : {},
 { urls: ["<all_urls>"] },
 ["blocking"]
);
