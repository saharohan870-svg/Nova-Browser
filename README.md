# Nova Browser

An Android browser prototype with a Chrome-inspired interface, powered exclusively by Mozilla GeckoView.

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
- Bundled GeckoView ad-blocking WebExtension with EasyList and EasyPrivacy subscriptions, ABP-style network rules, domain/type/third-party options, exceptions, cosmetic selectors and automatic daily list refresh
- User-managed domain navigation blocklist and GeckoView tracking protection
- Local history/bookmark/tab-restore cleanup
- Page-crash recovery actions and low-memory session handling
- Close-other-tabs, duplicate, pin/unpin, tab search and recently closed tabs

## Tor-backed Guest Mode

Nova embeds Guardian Project's Tor Android service for Guest Mode. Nova waits for Tor bootstrap to complete before opening guest tabs, then creates a GeckoView runtime configured for the local SOCKS5 listener, remote DNS, and no direct proxy fallback. WebRTC, HTTP/3, DNS prefetch, and speculative connections are disabled in that runtime to reduce common bypass paths; Gecko's baseline `privacy.resistFingerprinting` preference is also enabled.

Entering Guest Mode saves and suspends regular tabs, then switches the browser runtime to Tor. Guest tabs use GeckoView private sessions. Guest Mode does not save browsing history or tab state, and it hides regular bookmarks, history, and downloads. Website permissions, Android permissions, camera/microphone access, native account actions, and on-device translation model downloads are denied or disabled while guest browsing. External app links are blocked. Ending Guest Mode closes guest tabs, stops the Tor service, and restores the regular browser runtime and saved tabs.

**Important limitation:** Tor-routed GeckoView is not the Tor Browser. It does not include Tor Browser's full fingerprinting defenses, hardened browser configuration, or equivalent anonymity set. The built-in Tor connection check opens `https://check.torproject.org/`; verify the route on a real device before relying on it. Do not assume that Tor hides information you deliberately submit to a website.

## Engine architecture

Nova uses GeckoView as its only page-rendering engine. GeckoView supplies standards-based HTML, CSS, JavaScript, HTTP/HTTPS, TLS, image/font loading, accessibility integration, graphics compositing, and browser networking. Nova no longer maintains a second in-app HTML renderer or JavaScript engine. The former Zorvi prototype's standalone parser, layout, graphics, QuickJS, HTTP loader, and experimental page Activity are retired because they duplicated incomplete browser-engine functionality. Their security, caching, resource-loading, URL, and accessibility requirements remain Nova/GeckoView integration and regression-test requirements.

## Compatibility status

The AI and major-site shortcuts are navigation conveniences, not compatibility certifications. Successful sign-in, streaming responses, voice, media, uploads and session restoration still require final testing on a real Android device. Phase 3 features are documented in [the Phase 3 progress log](docs/PHASE3_PROGRESS.md); some advanced features remain incomplete. See [the Phase 2 compatibility plan](docs/SEVEN_SITE_COMPATIBILITY_TEST_PLAN.md).

## Build

Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+). CI build artifacts are published by GitHub Actions when the workflow succeeds.

## Known limitations

This is not a full Chromium distribution. GeckoView is a separate Firefox/Gecko engine; it does not guarantee identical Chromium behavior or every site's support.  The bundled blocker implements a practical subset of Adblock Plus filter syntax for EasyList/EasyPrivacy. It does not yet support every uBlock Origin/ABP advanced feature (such as procedural cosmetic rules, redirects, scriptlets, CSP and all modifiers), so it is not feature-equivalent to uBlock Origin. Some site-specific ads may remain.


## Supabase authentication setup

Nova's Android account flow uses the Supabase Auth REST API directly. Firebase Authentication and Firebase Cloud Functions are not used.

1. In Supabase, open **Authentication → Sign In / Providers → Email** and make sure Email is enabled.
2. For passwordless email sign-in, open **Authentication → Email Templates → Magic Link**. Use the six-digit token in the message body (`{{ .Token }}`) instead of relying only on `{{ .ConfirmationURL }}`.
3. For password recovery by code, open **Authentication → Email Templates → Reset Password** and include `{{ .Token }}` in the message. Nova verifies this code as a recovery OTP, then updates the password using the recovery session.
4. Keep the email OTP length at six digits and choose an expiry suitable for your app. Configure a custom SMTP provider before production; Supabase's default email service is rate-limited.
5. The Android app is configured in `app/src/main/java/com/zorvi/browser/NovaSupabaseConfig.kt` with the project URL and publishable key. Only the publishable key belongs in the APK; never put a Supabase secret/service-role key in Android code.
6. After email templates are saved, build and install the APK. Test password sign-up and confirmation, password sign-in, email OTP sign-in, recovery OTP + password update, sign-out, and session restoration after force-closing/reopening Nova.

The app encrypts its saved access and refresh tokens with an Android Keystore AES-GCM key. Publishable keys are sent only in the `apikey` header; the `Authorization` header is reserved for a real user access token.



## Nova Cloud Sync

Nova Cloud Sync is intended to restore a signed-in user's browser snapshot on a second device automatically. The first device with no cloud snapshot creates the account backup; a newly signed-in device restores the existing cloud snapshot instead of uploading its empty local state. Subsequent local browser changes are uploaded when leaving the app, and the latest snapshot is downloaded on startup/resume or by choosing Account → Sync now. Deletions in synced collections propagate because snapshots replace, rather than union, the prior collections. If a different Nova account is signed in on the same installation, the previous account's local synced snapshot is cleared before restoring the new account's data.

The synced browser data includes bookmarks, browsing history, recently closed tabs, open tabs (including tab groups, pinned state and Gecko session state where available), active tab, homepage, search engine, theme, reader and translation preferences, tracking protection, cookie policy, desktop mode and remembered site permissions. Guest and private browsing data, authentication tokens, Android DownloadManager IDs and device-specific downloaded-file URIs are excluded. Downloaded file contents remain on their original device.

The public.nova_sync_state table is protected by row-level security and scoped to the authenticated Supabase user. The app uses the user's access token and the Supabase publishable key; the service-role key is never shipped in the app. The snapshot is not end-to-end encrypted in this implementation. Saved website passwords and portable website login/cookie sessions are not currently synchronized; those require a separate encrypted vault/profile-sync design and dedicated cross-device tests.


### Password and website-session sync

Nova uses GeckoView's native login storage/autocomplete delegates to detect submitted login forms, ask before saving, and offer stored credentials for autofill. Credentials are stored locally encrypted with an Android Keystore AES-GCM key. Private and Guest sessions do not save credentials.

The Website Logins screen lists and removes saved website credentials. When the user unlocks the encrypted vault, newly saved logins are included in the encrypted cloud snapshot. The sync passphrase is required to decrypt that cloud backup on another device.

The optional **Sessions** action uses a bundled, privileged GeckoView WebExtension and the WebExtensions cookies API. Session transfer is explicitly user-triggered, and exports only secure, non-partitioned cookies. The user must unlock the encrypted vault first. Private and Guest browsing are excluded. Cookie transfer can sign the user into websites on another device; only transfer to devices the user trusts. Some site sessions will still not transfer because websites may bind sessions to device, IP, browser state, or other server-side signals.

This is not a raw Gecko profile copy. GeckoView does not provide a supported portable whole-profile export/import API. Cache, localStorage/IndexedDB, service-worker state, device-bound credentials, passkeys, TLS client certificates, downloads, and other profile internals are not migrated. Those require separate supported per-feature sync designs and cannot safely be copied as a profile folder between Android devices.

These additions require a successful CI build and device testing before they should be treated as production-ready.


### Nova Shields (Brave-style protection controls)

Nova's Shields panel provides global Standard, Aggressive and Off modes, plus per-site overrides. Standard mode uses EasyList and EasyPrivacy network/cosmetic rules with GeckoView tracking protection. Aggressive mode also evaluates applicable filter rules against first-party requests and can cause site breakage. Per-site overrides can turn filtering off for a site or apply aggressive filtering. The panel reports the current filter counts and blocked-request count and can manually refresh filter subscriptions.

Nova also exposes GeckoView cookie controls (block known trackers, block third-party cookies, allow all cookies, or block all cookies) in Privacy and Protection settings. Entering ordinary hostnames in the address bar already uses HTTPS by default.

This is a Brave-inspired feature set, not Brave's proprietary Shields implementation. Nova does not currently implement Brave's fingerprint randomization, per-site cookie partitioning/ephemeral third-party storage, script-blocking UI, Forget-me-on-close, phishing/malware reputation checks, CNAME uncloaking, or Brave's full first-party resource replacement. GeckoView tracking protection and cookie policies are distinct native protections. Aggressive filter mode is not equivalent to Brave's complete aggressive Shields mode.
