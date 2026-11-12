"use strict";
(() => {
  const selectors = [
    "iframe[src*='doubleclick.net']",
    "iframe[src*='googlesyndication.com']",
    "iframe[src*='adnxs.com']",
    "iframe[src*='taboola.com']",
    "iframe[src*='outbrain.com']",
    "iframe[id^='google_ads']",
    "ins.adsbygoogle",
    "[id^='google_ads_']",
    "[data-ad-slot]",
    "[data-ad-client]"
  ];
  const hideKnownAdElements = () => {
    for (const selector of selectors) {
      document.querySelectorAll(selector).forEach(element => {
        element.style.setProperty("display", "none", "important");
      });
    }
  };
  hideKnownAdElements();
  const observer = new MutationObserver(hideKnownAdElements);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(() => observer.disconnect(), 15000);
})();
