# Phase 3 — Advanced Browser Features

Status: implementation work committed; CI build and physical-device verification are pending.

## Implemented in code

- **AI assistant:** Perplexity, ChatGPT and optional local GGUF inference are available as assistant choices. Perplexity remains the first/default online choice.
- **Native offline AI:** added llama.cpp Android integration. Users can import a GGUF model through Android's document picker; Nova copies it to app-private storage and runs inference on-device. No prompt is sent to a cloud provider when the Offline AI option is selected. A small quantized model is recommended; available RAM and model size determine whether it runs reliably.
- **AI page summaries:** GeckoView extracts readable page text. If a local model is installed, the summary prompt can run locally; otherwise the user can send the excerpt to Perplexity or ChatGPT.
- **Network request blocking:** added a bundled GeckoView WebExtension using `webRequest.onBeforeRequest` to cancel requests to a conservative curated list of known advertising/tracking hosts.
- **Cosmetic filtering:** added conservative CSS/DOM selector hiding for common ad slots and known ad iframe patterns.
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
- Offline AI is integrated in code but not yet CI/device-verified. Users must select and import their own compatible GGUF model; Nova copies it to app-private storage rather than running directly from removable storage. Model memory needs may exceed available RAM on low-memory phones.
- Offline translation needs language-pair and device testing; Assamese uses the online fallback.
- Full crash/session restoration validation and performance benchmarking remain.
- Phase 2's 16-site compatibility matrix still requires physical-device testing.

## Final verification still required

- Confirm the Gradle build succeeds with the llama.cpp AAR and GeckoView extension assets.
- Verify WebExtension installation and request blocking on GeckoView 149.
- Test model import, local inference, memory use and model release on a physical arm64 Android device.
- Test the filter against common ad/tracker requests and verify cosmetic selectors do not break site layouts.
- Run unit/instrumentation tests and the Phase 2 compatibility matrix.
