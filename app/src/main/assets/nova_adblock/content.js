"use strict";
(() => {
  const selectors = [
    "iframe[src*='doubleclick.net']",
    "iframe[src*='googlesyndication.com']",
    "iframe[id^='google_ads']",
    "ins.adsbygoogle",
    "[id^='google_ads_']",
    "[class*='ad-slot']",
    "[class*='ad-container']",
    "[data-ad-slot]",
    "[data-testid*='ad-']"
  ];
  const removeAds = () => {
    for (const selector of selectors) {
      document.querySelectorAll(selector).forEach(element => {
        element.style.setProperty("display", "none", "important");
      });
    }
  };
  removeAds();
  const observer = new MutationObserver(removeAds);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  setTimeout(() => observer.disconnect(), 15000);
})();
