# Zorvi Browser

An Android browser powered by Mozilla GeckoView (the Gecko engine used by Firefox for Android), with a Chrome-inspired browser interface.

## Engine

- Rendering engine: Mozilla GeckoView 128.0.20240725162350, bundled from Mozilla's Maven repository.
- JavaScript engine: SpiderMonkey (included in GeckoView); no Android System WebView or QuickJS rendering fallback.
- Compatibility mode: sends a Chrome-for-Android style user-agent to improve compatibility with sites that gate content on browser identification. This changes server-side browser detection only; it does not turn Gecko into Chromium or add Chromium-only APIs.
- Tracking protection: GeckoView tracking protection is enabled per session. This is not a full Brave-style ad-filter subscription engine.
- Private tabs: use GeckoView private-mode sessions.

## Browser features

- Search/address bar with Google, Bing, DuckDuckGo and Brave Search
- Back, forward, reload, bookmarks and history
- Multiple GeckoView tabs, named tab groups and private sessions
- Chrome-style mobile user-agent compatibility mode

## Current implementation status

- **Available in the normal browser shell:** GeckoView page rendering, address/search input, Google/Bing/DuckDuckGo/Brave Search selection, back/forward/reload, tab switching, basic named tab groups, bookmarks, URL history, private GeckoView sessions, dark toolbar theme, and a VPN shortcut that opens Android's VPN settings.
- **Not implemented yet:** browser download manager, full ad-blocking filter lists/element hiding, AI assistant, Google/GitHub account linking, Codespaces integration, saved-tab restore, production release signing, or a bundled/embedded VPN client. For Turbo VPN, install and connect its Android app separately; Android routes browser traffic through the active system VPN. Zorvi does not control Turbo VPN servers, connection state, or subscription.
- **Experimental Zorvi renderer:** a separate text-oriented prototype, not suitable as a general-purpose daily browser. It does not yet render ordinary modern sites reliably.
- **APK packaging:** CI targets ARM64 Android phones. A successful Gradle compile alone is not a release; the workflow must also pass APK validation and upload the artifact.

## Build

Open this repository in Android Studio, allow Gradle sync, then build the app debug variant for Android 8.0+.

## Compatibility limits

GeckoView is a Firefox engine, not Chromium. The Chrome-style user-agent helps with browser checks but cannot supply Chromium-only APIs, proprietary codecs, Chrome extensions, or behaviour specific to Blink/V8. Some sites may still require a Chromium-based browser. Validate important sites on real devices before claiming compatibility.

## Upstream documentation

- https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/
