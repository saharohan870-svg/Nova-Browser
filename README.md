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
- **Foundation:** initial engine foundations and project architecture.
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
- **Phase 11 — DOM integration:** added the `dom` package:
  - `DomElement.kt`: shared element model implementing both the CSS and accessibility element contracts; preserves text/child order, attributes, parent links and traversal.
  - `HtmlDomParser.kt`: best-effort HTML tokenizer/tree builder for common tags, attributes, comments, void elements, raw-text style/script blocks and common/numeric entities.
  - `NovaDocumentPipeline.kt`: parses a document, loads inline style blocks into the CSS cascade, computes inherited and inline styles, then builds an accessibility tree from the same DOM.
  - Non-rendered metadata/script/style elements are omitted from the accessibility tree.

### Current engine limitations
This is a deliberately small engine foundation, not a standards-complete browser. The HTML parser does not implement the full WHATWG tree-building algorithm. CSS support remains limited to the selectors/properties described above; external stylesheets, full CSS parsing, layout and paint integration are not implemented. Phase 11 does not connect this pipeline to the Android app shell or Canvas renderer yet. Phase 10 models do not include a media decoder, actual audio/video playback, native controls, full form submission, complete ARIA name computation (such as `aria-labelledby`) or an Android AccessibilityNodeProvider/TalkBack bridge. JavaScript execution and a complete navigation/network stack are also not provided by these milestones.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+). A GitHub Actions workflow builds a debug APK on pushes to `main`.

## Important app limitations
The Android app remains an early prototype and uses the device's Android System WebView. The separate Nova Engine is incomplete and does not yet replace WebView for browsing. The host blocklist is small; full tab groups, a true isolated private profile and the AI assistant are not fully implemented.
