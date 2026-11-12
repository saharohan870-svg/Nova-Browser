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
- **Advanced tab actions:** Close other tabs, duplicate current tab, pin/unpin tabs, search tabs by title/URL/group, and reopen recently closed tabs. Pin state is saved with session data.
- **Site permission controls:** permission prompts can remember an allow/block decision per site and permission type. A Permissions dashboard lists saved decisions and lets users forget them.

## Not yet implemented / incomplete

- A full ad-filter engine and cosmetic filtering. The current privacy protection is GeckoView tracking protection; it is not a complete ad blocker.
- Complete security dashboard covering certificates, cookies, site storage and all permission types. The new dashboard currently manages only remembered Gecko content-permission decisions.
- Native in-browser AI that reads page content without handing the URL to an external AI website. GeckoView does not expose arbitrary page JavaScript evaluation to embedders; this needs a built-in WebExtension or a separate content-extraction architecture and an AI provider/model.
- Offline translation. The online translation language picker is implemented; offline translation needs downloadable language models.
- Reader-mode polish and in-reader font, theme and spacing controls.
- Full crash/session restoration validation and performance benchmarking.
- More advanced tab actions beyond pinning, tab search and recently closed tabs.

## Important compatibility notes

- The AI assistant and summary handoff let the user choose Perplexity or ChatGPT and depend on the selected website's query handling.
- Google Translate is an external service and needs internet access.
- Reader view depends on GeckoView's reader-mode support for the page.
- Tracking protection preference applies when a GeckoSession is created; already-open tabs are not retroactively reconfigured by this UI.
- Clearing local browser data does not remove website cookies or remote account data.
- Remembered site permissions are stored by host and permission type in Nova preferences. The dashboard only clears Nova's saved decisions; GeckoView-managed permission state may require separate engine-level cleanup.
- The domain-blocking/ad-filter and native AI items remain incomplete; do not describe them as implemented.

## Final verification still required

Build the APK, run unit/instrumentation tests, verify the crash and memory paths, and test all features on a physical Android device. Phase 2's 16-site compatibility matrix remains part of final verification.
