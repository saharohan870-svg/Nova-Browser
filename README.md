# Zorvi Browser

An Android browser powered by Mozilla GeckoView (the Gecko engine used by Firefox for Android), with a Chrome-inspired browser interface.

## Engine

- Rendering engine: Mozilla GeckoView 128.0.20240725162350, bundled from Mozilla's Maven repository.
- JavaScript engine: SpiderMonkey (included in GeckoView); no Android System WebView or QuickJS rendering fallback.
- Compatibility mode: sends a Chrome-for-Android style user-agent to improve compatibility with sites that gate content on browser identification. This changes server-side browser detection only; it does not turn Gecko into Chromium or add Chromium-only APIs.
- Tracking protection: GeckoView tracking protection is enabled per session. A bundled Zorvi WebExtension also applies supported domain-level network rules from EasyList and EasyPrivacy, with a small offline fallback list. It also blocks known ad domains on top-level/frame navigations and suppresses script-opened popups without a live user activation, plus synthetic new-tab clicks. The parser does not implement the full uBlock Origin filter syntax or cosmetic/scriptlet rules.
- Private tabs: use GeckoView private-mode sessions.

## Browser features

- Search/address bar with Google, Bing, DuckDuckGo, Brave Search and Perplexity
- Back, forward, reload, bookmarks, history, and a persistent downloads list
- Multiple GeckoView tabs, named tab groups, private sessions, and restoration of normal tabs/groups after restart (private tabs are excluded)
- Chrome-style mobile user-agent compatibility mode

## Current implementation status

- **Available in the normal browser shell:** GeckoView page rendering, address/search input, Google/Bing/DuckDuckGo/Brave Search selection, back/forward/reload, tab switching, basic named tab groups, bookmarks, URL history, private GeckoView sessions, dark toolbar theme, an AI chat assistant with ChatGPT (OpenAI API) and Perplexity API provider choices (requires the user’s own API key; the key is held in memory only and current-page title/URL are shared only when explicitly requested), a filter-based ad/tracker blocker using supported EasyList/EasyPrivacy domain rules, a VPN shortcut that opens Android VPN settings, restoration of normal tabs/groups after restart, and a download manager that saves GeckoView download responses to Android Downloads (Android 10+) with a persistent in-app list. Private tabs are not serialized into the restore file.
- **Not implemented yet:** full uBlock Origin/Brave-level filter syntax coverage, cosmetic element hiding/scriptlets, comprehensive pop-under detection and advanced filter controls, automatic Codespaces creation/start/stop, and production release signing. Google sign-in is implemented but requires a Google Cloud Web application OAuth client ID to be configured by the user at sign-in. The AI chat interface connects to the official OpenAI or Perplexity HTTPS API; API access and billing are separate from ChatGPT Plus or Perplexity consumer subscriptions. Zorvi does not provide hosted models or bundled API credits. GeckoView tracking protection is enabled but is not equivalent to full Brave Shields. The release workflow is configured to build/test and publish an ARM64 APK when a `v*` tag is pushed; no signed production release is claimed until signing credentials are configured. For Turbo VPN, install and connect its Android app separately; Android routes browser traffic through the active system VPN. Zorvi does not control Turbo VPN servers, connection state, or subscription.
- **Experimental Zorvi renderer:** a separate text-oriented prototype, not suitable as a general-purpose daily browser. It does not yet render ordinary modern sites reliably.
- **APK packaging:** CI targets ARM64 Android phones. The standard workflow validates and uploads a debug APK artifact. The release workflow publishes a GitHub Release on a `v*` tag after tests and APK validation. Debug APKs are not production-signed.

## Privacy and cloud architecture

- Normal page loading remains local to the device and GeckoView; page contents are not sent to a cloud AI service by default.
- Private-tab URLs are excluded from saved-session restoration and normal browsing history. Downloaded files remain on-device; downloads initiated in private tabs may still appear in the download list.
- Google sign-in is implemented using Android Credential Manager and Google ID tokens. Users must create a Google Cloud Web application OAuth client ID and enter it in the Account sign-in dialog. Zorvi asks Google to validate the ID token, then checks issuer, audience, expiry, verified email, subject and the per-request nonce. Only the Google subject/name/email are stored locally; the ID token is never persisted. Sign-in is optional; normal browsing does not require an account. No browsing history, tabs, or page contents are uploaded. No API keys or secrets are committed to this repository.

## Build

Open this repository in Android Studio, allow Gradle sync, then build the app debug variant for Android 8.0+.

## Compatibility limits

GeckoView is a Firefox engine, not Chromium. The Chrome-style user-agent helps with browser checks but cannot supply Chromium-only APIs, proprietary codecs, Chrome extensions, or behaviour specific to Blink/V8. Some sites may still require a Chromium-based browser. Validate important sites on real devices before claiming compatibility.

## Upstream documentation

- https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/
