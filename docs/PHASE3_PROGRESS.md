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

- Full network-level ad filtering, cosmetic element hiding, and maintained filter-list updates. Nova has GeckoView tracking protection plus a user-managed domain blocklist for top-level and iframe navigation; this is not equivalent to a full ad blocker.
- Complete security dashboard covering certificates, cookies, site storage and all permission types. The new dashboard currently manages only remembered Gecko content-permission decisions.
- Native on-device AI inference remains incomplete. Nova now extracts readable page text with GeckoView's PageExtraction API and passes a capped excerpt to the selected Perplexity or ChatGPT website for summarization. Page text is shared with that selected provider.
- Offline translation is implemented for Google ML Kit-supported language pairs after their models are downloaded once. Assamese falls back to online translation because the on-device model does not support it. Device testing and language-pair coverage remain.
- Reader view now extracts article text into a clean reading dialog with adjustable font size and light/dark themes.
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


## Latest additions

- GeckoView upgraded to 149 to access its experimental page extraction API.
- AI page summary now extracts readable text from the active page (up to 9,000 characters) before handing it to the user-selected AI website. The excerpt is shared with that provider.
- Reader view displays extracted article text with persistent font-size and light/dark controls.
- On-device translation uses Google ML Kit language identification and downloadable translation models; model download requires internet once per language pair. Assamese falls back to online translation.
- User-maintained domain blocklist blocks matching top-level and subframe navigation requests. It does not intercept every network resource or hide ad elements.
- Build and real-device validation are still required after the GeckoView and ML Kit upgrades.
