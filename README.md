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
- **Phase 10 — media, forms and accessibility foundation:** added `app/src/main/java/com/nova/engine/media/MediaAccessibility.kt`.
  - Adds platform-neutral audio/video state, playback transitions, seeking, volume, looping and error state.
  - Adds basic form-control state and required, length, pattern, email and URL validation.
  - Builds a semantic accessibility tree with inferred/explicit roles, labels, descriptions, heading levels, disabled/checked/required/invalid state and action hints.
  - Hides `hidden` and `aria-hidden` nodes and treats images without alt text as decorative.

### Current engine limitations
The engine milestones are standalone foundations, not a complete browser implementation. Phase 10 does **not** include a media decoder, audio/video rendering pipeline, native playback controls, full HTML form submission, complete ARIA name computation (for example, `aria-labelledby`), or an Android AccessibilityNodeProvider/TalkBack bridge. These models still need to be connected to the DOM, layout/paint pipeline and Android view layer. The CSS module also remains separate from the Canvas renderer.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+). A GitHub Actions workflow builds a debug APK on pushes to `main`.

## Important app limitations
The Android app remains an early prototype and uses the device's Android System WebView. The separate Nova Engine is incomplete and does not yet replace WebView for browsing. The host blocklist is small; full tab groups, a true isolated private profile and the AI assistant are not fully implemented.
