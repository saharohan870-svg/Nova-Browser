# Phase 7 — Browser-exclusive features

Status: feature implementation is present in the browser shell; final end-to-end and physical-device verification remains required.

This track covers Nova Browser product features, separate from the independent Zorvi Engine graphics track.

## Implemented in the browser shell

- **AI assistant entry point:** users can choose ChatGPT or Perplexity. These are online services opened in browser tabs; their own accounts, availability and terms apply.
- **AI page summaries:** GeckoView extracts readable page text and sends a bounded excerpt only after the user explicitly requests a summary and selects a provider.
- **Page translation:** Google ML Kit language identification and on-device translation are used for supported language pairs after downloading the model. Assamese falls back to Google Translate online because an offline model is not provided by ML Kit.
- **Reader mode:** extracts readable page text into a simplified reading view with adjustable text size and light/dark themes.
- **Tab organization:** named groups, tab search, pin/unpin, duplicate, close-other-tabs, and recently closed tabs are implemented in the shell.
- **Custom home page:** users can configure the URL used by Home and new tabs.
- **Downloads:** download handling and the downloads list are available in the browser shell.
- **Browser privacy controls:** tracking protection, site permission controls, and local browsing-data cleanup are exposed through browser tools.

## Privacy and behavior boundaries

- AI summaries transmit extracted page text to the provider selected by the user. They are not on-device AI summaries.
- Offline translation requires a one-time model download and only works for supported language pairs.
- Reader mode depends on GeckoView's page text extraction and may not preserve the original page's layout, images, or interactive content.
- Tab restore and recently closed tab behavior must continue to exclude private and guest session URLs.
- Downloads, translation, summaries, and tab operations need real-device regression testing on supported Android versions.

## Verification checklist

- [ ] Verify AI summary text extraction and explicit provider handoff.
- [ ] Verify translation model download, offline translation, and Assamese online fallback.
- [ ] Verify reader text scaling and theme controls on long pages.
- [ ] Verify tab search, groups, pinned tabs, duplication, close-other-tabs, and recently closed tabs across restart.
- [ ] Verify private/guest URLs are not restored or exposed through recently closed tabs.
- [ ] Verify downloads on Android 10+ and storage failure handling.
- [ ] Run CI unit tests and install the resulting ARM64 APK on a physical Android device.

The feature list is not a claim that every website, language pair, or download type is fully supported.
