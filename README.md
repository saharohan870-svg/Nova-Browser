# Zorvi Browser

An Android browser prototype with a Chrome-inspired interface and Chromium-based Android WebView engine.

## Current prototype features
- Address/search bar with selectable Google, Bing, DuckDuckGo and Brave Search
- Back, forward, reload and bookmarks
- Multiple WebView tabs with a tab switcher
- Private-mode toggle for newly opened tabs (prototype; not a hardened isolated profile)
- DownloadManager integration
- Basic host-based request blocking
- Dark toolbar/background toggle

## Build
Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+).

## Important limitations
This is an early prototype, not a full Chromium distribution. It uses the device's Android System WebView. The host blocklist is small and is not a complete ad/tracker filter. History/bookmarks UI, tab groups, a true isolated private profile, and the AI assistant are not implemented yet.
