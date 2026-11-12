# Phase 3 — Advanced Browser Features

Status: implementation in progress; not build-verified or device-tested.

## Implemented in this pass

- **AI assistant:** accepts a question and lets the user choose Perplexity or ChatGPT; opens the selected website with the prompt.
- **AI page-summary handoff:** lets the user choose Perplexity or ChatGPT and passes the current page URL with a summary request. This is a URL handoff, not native extraction of page text; the AI site must be able to access the page.
- **Page translation:** hands the current URL to Google Translate and currently targets English.
- **Reader view entry:** opens Gecko's `about:reader` route for the current page where supported.
- **Custom home page:** user can set a URL used by Home and new tabs.
- **Privacy controls:** exposes GeckoView's built-in tracking-protection toggle for newly created tabs.
- **Local data cleanup:** can clear Nova's locally stored history, bookmarks and tab-restore list. It does not clear Gecko cookies, cache, passwords or website account data.
- **Crash recovery:** a crashed tab now offers Reload, Close tab or Keep tab.
- **Memory-pressure handling:** saves the tab list and deactivates background sessions when Android reports running-low memory.
- **Advanced tab action:** Close other tabs is available from the tab switcher.

## Not yet implemented / incomplete

- A dedicated, user-maintained ad-filter engine and cosmetic filtering. GeckoView tracking protection is not advertised as a complete ad blocker.
- Per-site security/permission dashboard and persistent permission management.
- Native in-browser AI that reads page content without handing the URL to an external AI website.
- Translation language picker and offline translation.
- Reader-mode polish and controls.
- Full crash/session restoration validation and performance benchmarking.
- More advanced tab actions such as pinning, tab search and recently closed tabs.

## Important compatibility notes

- The AI assistant and summary handoff depend on ChatGPT's website and its query handling.
- Google Translate is an external service and needs internet access.
- Reader view depends on GeckoView's reader-mode support for the page.
- Tracking protection preference applies when a GeckoSession is created; already-open tabs are not retroactively reconfigured by this UI.
- Clearing local browser data does not remove website cookies or remote account data.

## Final verification still required

Build the APK, run unit/instrumentation tests, verify the crash and memory paths, and test all features on a physical Android device. Phase 2's 16-site compatibility matrix remains part of final verification.
