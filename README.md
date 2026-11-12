# Zorvi Browser

An Android browser prototype with a Chrome-inspired interface and Chromium-based Android WebView engine.

## Current prototype features
- Address/search bar with selectable Google, Bing, DuckDuckGo and Brave Search
- Back, forward, reload and bookmarks
- Multiple WebView tabs with a tab switcher
- Private tabs use a separate AndroidX WebKit multi-profile data store when the installed Android System WebView supports it; the feature fails closed when unsupported and clears profile cookies/storage on app startup, exit, and after the last private tab closes
- DownloadManager integration
- Basic host-based request blocking
- Dark toolbar/background toggle

## Build
Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+).

## Important limitations
This is an early prototype, not a full Chromium distribution. It uses the device's Android System WebView. The host blocklist is small and is not a complete ad/tracker filter. Tab groups and the planned authenticated AI assistant are not implemented yet. Private-profile isolation depends on the installed Android System WebView exposing the MULTI_PROFILE feature; it is not available on every device/provider version.
