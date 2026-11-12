# Phase 3 — Advanced Browser Features

Status: implementation work committed; CI build and physical-device verification are pending.

## Implemented in code

- **AI assistant:** Online Perplexity and ChatGPT choices; Perplexity remains the first/default online choice.
- **AI page summaries:** GeckoView extracts readable page text and sends the excerpt to the online provider selected by the user.
- **Network request blocking:** bundled GeckoView WebExtension uses `webRequest.onBeforeRequest` to cancel requests to a conservative curated list of known advertising/tracking hosts.
- **Cosmetic filtering:** conservative CSS/DOM selector hiding for common ad slots and known ad iframe patterns.
- **Page translation:** Google ML Kit language identification and on-device translation for supported language pairs after their models are downloaded once. Assamese falls back to online translation because the on-device model does not support it.
- **Reader view:** extracted article text is shown in a clean reading dialog with adjustable font size and light/dark themes.
- **Custom home page:** user can set a URL used by Home and new tabs.
- **Privacy controls:** exposes GeckoView's built-in tracking-protection toggle for newly created tabs.
- **Local data cleanup:** clears Nova's locally stored history, bookmarks and tab-restore list. It does not clear Gecko cookies, cache, passwords or website account data.
- **Crash recovery:** a crashed tab offers Reload, Close tab or Keep tab.
- **Memory-pressure handling:** saves the tab list and deactivates background sessions when Android reports running-low memory.
- **Advanced tab actions:** Close other tabs, duplicate current tab, pin/unpin tabs, search tabs by title/URL/group, and reopen recently closed tabs. Pin state is saved with session data.
- **Site permission controls:** permission prompts can remember an allow/block decision per site and permission type. A Permissions dashboard lists saved decisions and lets users forget them.

## Still incomplete / limitations

- The ad blocker currently uses a small bundled host list and conservative selectors. It is **not** a full EasyList/uBlock-compatible filter engine, has no automatic filter-list updates yet, and may miss ads or cosmetic patterns. The previous user-entered domain blocklist still blocks top-level and iframe navigations.
- The security dashboard is partial: it manages remembered Gecko content-permission decisions, not certificates, cookies, cache, passwords or every site permission.
- Offline AI model support is intentionally not included.
- Offline translation needs language-pair and device testing; Assamese uses the online fallback.
- Full crash/session restoration validation and performance benchmarking remain.
- Phase 2's 16-site compatibility matrix still requires physical-device testing.

## Final verification still required

- Confirm the Gradle build succeeds with the GeckoView extension assets.
- Verify WebExtension installation and request blocking on GeckoView 149.
- Test the filter against common ad/tracker requests and verify cosmetic selectors do not break site layouts.
- Run unit/instrumentation tests and the Phase 2 compatibility matrix.
