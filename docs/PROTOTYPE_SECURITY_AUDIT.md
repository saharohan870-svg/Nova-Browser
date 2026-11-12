# Prototype security and readiness audit

Date: 2026-11-12  
Branch: `zorvi-brave-migration`

This audit describes the current Android prototype only. It is not a security certification and does not describe the future Brave-based build as implemented.

## Verified build state

- The lightweight GitHub Actions workflow completed successfully for commit `fb05f101caf03dcf8fc46cb234efdb65bc37ecd0`.
- The workflow verified the pinned Brave browser, Brave core, and Chromium release references before building.
- The uploaded artifact is named `zorvi-browser-interim-webview-debug`.
- This APK uses the current prototype and is **not** a Brave/Chromium-integrated Zorvi release.

## Findings in the current prototype

### 1. Private mode is not isolated — release blocker

The app uses Android's process-wide `CookieManager`. Private and regular WebViews therefore do not have separate cookie stores. The earlier implementation avoided saving private-tab history but shared the process-wide cookie store.

**Implementation now committed, validation pending:** the prototype now uses AndroidX WebKit's named multi-profile API for private WebViews, checks the runtime MULTI_PROFILE feature before enabling the mode, and clears private-profile cookies and WebStorage on startup, app exit, and after the last private tab closes. Unsupported WebView providers fail closed. The profile cleanup API can complete some deletion asynchronously, and an abrupt OS process kill cannot guarantee immediate cleanup; a fresh app launch performs cleanup again. This is a meaningful isolation improvement, but it still needs device-level verification on supported and unsupported WebView versions before being called release-ready.

### 2. Request blocking is only a small host list

The current request interceptor blocks a handful of hostnames. It is not a complete ad blocker, tracker-protection engine, cosmetic filter, or subscription-list implementation.

**Required:** integrate a maintained filtering engine/list strategy, support updates safely, and add tests for subdomains, redirects, first-party exceptions, and list parsing.

### 3. Tab groups are not implemented

The current tab switcher is a flat list. It has no persistent tab-group model, group colors, or group operations.

**Required:** add a tab model with stable IDs, group IDs, persistence, and UI tests before marking tab groups complete.

### 4. Authentication and Codespaces are architecture only

The dual Google/GitHub sign-in and personal Codespaces design is documented in `docs/AUTH_AND_CODESPACES.md`. No OAuth provider registrations, backend deployment, secure token vault, or Codespaces lifecycle integration is configured. Do not present these features as working.

### 5. AI assistant is not implemented

The current `Zorvi Engine` screen is a separate prototype activity, not the planned authenticated Codespaces-backed assistant. Do not send browsing data to a cloud service by default.

### 6. Engine is Android System WebView

The current browser activity creates Android `WebView` instances. The app does not yet embed Brave's Chromium engine, Brave Shields, or Brave's Android browser components.

## Release gates

- [ ] Brave Android source checkout is synchronized and the exact Chromium DEPS revision is recorded.
- [ ] Zorvi branding and product assets replace upstream Brave identity without altering upstream legal notices.
- [ ] ARM64 build completes on a verified no-cost host with adequate resources.
- [ ] Private profile isolation and cleanup pass device-level tests on supported WebView versions; unsupported providers fail closed.
- [ ] Ad/tracker protection has a maintained filter implementation and regression tests.
- [ ] Tab groups, downloads, bookmarks, history, and search selection pass device tests.
- [ ] Google/GitHub auth is deployed with PKCE, secure token handling, revocation, and account-linking tests.
- [ ] Codespaces start/run operations require explicit consent and disclose billing/quotas.
- [ ] Release APK is signed with a Zorvi-controlled key and verified on Android devices.

## Immediate safe next step

Continue with a focused prototype security improvement and test suite while keeping the Brave engine migration separate. No paid runners or cloud resources are authorized by this audit.
