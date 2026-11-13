/* Zorvi popup and forced-redirect guard.
 * Blocks script-opened popups without a live user activation.
 * Network filter rules independently block known ad destinations,
 * including main-frame and sub-frame navigations.
 */
"use strict";

(() => {
  const nativeOpen = window.open;
  try {
    window.open = function(url, target, features) {
      // A page cannot create popups from timers, load handlers or delayed ad scripts.
      if (!navigator.userActivation || !navigator.userActivation.isActive) return null;
      return nativeOpen.call(window, url, target, features);
    };
  } catch (_) {}

  document.addEventListener("click", event => {
    const anchor = event.target && event.target.closest
      ? event.target.closest("a[target='_blank'], area[target='_blank']")
      : null;
    if (!anchor) return;
    // Reject synthetic clicks used by many forced pop-under and redirect scripts.
    if (!event.isTrusted || (navigator.userActivation && !navigator.userActivation.isActive)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);
})();
