# Nova Browser

An Android browser prototype with a Chrome-inspired interface. The current app shell still uses Android System WebView; the separate Nova Engine is being developed incrementally.

## Android app prototype
- Address/search bar with selectable Google, Bing, DuckDuckGo and Brave Search
- Back, forward, reload and bookmarks
- Multiple WebView tabs with a tab switcher
- Limited private-mode prototype (not a hardened isolated profile)
- DownloadManager integration
- Basic host-based request blocking
- Dark toolbar/background toggle

## Nova Engine milestones
- **Foundation:** basic HTML parsing, Android Canvas rendering, HTTP/HTTPS loading and link handling.
- **Step 5 — CSS cascade foundation:** added `app/src/main/java/com/nova/engine/css/CssStyleEngine.kt`.
  - Parses basic CSS rule blocks and declaration lists.
  - Supports type, universal, ID, class, compound and descendant selectors.
  - Applies specificity, source order and `!important`.
  - Carries a defined set of inherited text properties from parent styles.
  - Exposes computed declarations through `ComputedStyle`.

### Step 5 limitations
This is a first CSS milestone, not full CSS compatibility. At-rules, pseudo-classes/elements, attribute selectors, child/sibling combinators, CSS variables, shorthand expansion, layout calculation and full CSS value parsing are not implemented. The CSS engine is currently a standalone module and has not yet been connected to the existing Canvas renderer or the WebView-based app shell.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+). A GitHub Actions workflow builds a debug APK on pushes to `main`.

## Important limitations
The Android app remains an early prototype and uses the device's Android System WebView. The separate Nova Engine is incomplete and does not yet replace WebView for browsing. The host blocklist is small; history/bookmarks UI, tab groups, a true isolated private profile, and the AI assistant are not fully implemented.
