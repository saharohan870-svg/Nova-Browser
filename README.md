# Nova Browser

An Android browser prototype with a Chrome-inspired interface, powered by Mozilla GeckoView on the `chromium-engine-migration` branch. The separate Zorvi renderer remains experimental and is not the default web engine.

## Current browser features

- GeckoView-based web rendering
- Address/search bar with Google, Bing, DuckDuckGo and Brave Search
- Back, forward, reload, multiple tabs and tab groups
- Private GeckoSession mode and tracking protection
- Bookmarks, browsing history and saved tab/session state
- Downloads and website file-picker support
- Website permission prompts for supported browser permissions
- Fullscreen video and picture-in-picture entry point
- Mobile/desktop user-agent and viewport modes
- Quick links for ChatGPT, Claude, Gemini, Copilot, Perplexity, Grok, DeepSeek, Mistral Le Chat and Meta AI
- Quick links for Google, YouTube, GitHub, Instagram, Discord, WhatsApp Web and Microsoft 365
- Dark browser toolbar theme
- Custom home page and Home shortcut
- Online AI assistant links for Perplexity and ChatGPT
- Native page-text extraction for summaries, sent to a selected online AI provider
- Reader mode with extracted text, font-size and theme controls
- On-device translation for supported Google ML Kit language pairs after model download
- Bundled GeckoView WebExtension for request-level blocking of a conservative curated set of ad/tracker domains, plus basic cosmetic hiding
- User-managed domain navigation blocklist and GeckoView tracking protection
- Local history/bookmark/tab-restore cleanup
- Page-crash recovery actions and low-memory session handling
- Close-other-tabs, duplicate, pin/unpin, tab search and recently closed tabs

## Compatibility status

The AI and major-site shortcuts are navigation conveniences, not compatibility certifications. Successful sign-in, streaming responses, voice, media, uploads and session restoration still require final testing on a real Android device. Phase 3 features are documented in [the Phase 3 progress log](docs/PHASE3_PROGRESS.md); some advanced features remain incomplete. See [the Phase 2 compatibility plan](docs/SEVEN_SITE_COMPATIBILITY_TEST_PLAN.md).

## Build

Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+). CI build artifacts are published by GitHub Actions when the workflow succeeds.

## Known limitations

This is not a full Chromium distribution. GeckoView is a separate Firefox/Gecko engine; it does not guarantee identical Chromium behavior or every site's support. The experimental Zorvi renderer is not standards-complete and is not intended for modern AI web apps. The bundled blocker uses a small curated host list—not EasyList/uBlock filter syntax—and is not a comprehensive ad blocker. Cosmetic selectors are intentionally conservative and may miss ads or hide some site elements.
