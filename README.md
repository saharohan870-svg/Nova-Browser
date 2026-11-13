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

## Engine architecture

Nova uses GeckoView as its only page-rendering engine. GeckoView supplies standards-based HTML, CSS, JavaScript, HTTP/HTTPS, TLS, image/font loading, accessibility integration, graphics compositing, and browser networking. Nova no longer maintains a second in-app HTML renderer or JavaScript engine. The former Zorvi prototype's standalone parser, layout, graphics, QuickJS, HTTP loader, and experimental page Activity are retired because they duplicated incomplete browser-engine functionality. Their security, caching, resource-loading, URL, and accessibility requirements remain Nova/GeckoView integration and regression-test requirements.

## Compatibility status

The AI and major-site shortcuts are navigation conveniences, not compatibility certifications. Successful sign-in, streaming responses, voice, media, uploads and session restoration still require final testing on a real Android device. Phase 3 features are documented in [the Phase 3 progress log](docs/PHASE3_PROGRESS.md); some advanced features remain incomplete. See [the Phase 2 compatibility plan](docs/SEVEN_SITE_COMPATIBILITY_TEST_PLAN.md).

## Build

Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (Android 8.0+). CI build artifacts are published by GitHub Actions when the workflow succeeds.

## Known limitations

This is not a full Chromium distribution. GeckoView is a separate Firefox/Gecko engine; it does not guarantee identical Chromium behavior or every site's support.  The bundled blocker implements a practical subset of Adblock Plus filter syntax for EasyList/EasyPrivacy. It does not yet support every uBlock Origin/ABP advanced feature (such as procedural cosmetic rules, redirects, scriptlets, CSP and all modifiers), so it is not feature-equivalent to uBlock Origin. Some site-specific ads may remain.


## Supabase authentication setup

Nova's Android account flow uses the Supabase Auth REST API directly; Firebase Authentication and Firebase Cloud Functions are not used.

1. Create a Supabase project at https://supabase.com/dashboard.
2. Open Project Settings → API (or API Keys in the current dashboard) and copy the Project URL and the publishable key (or legacy anon public key).
3. Put those values in `app/src/main/java/com/zorvi/browser/NovaSupabaseConfig.kt` as `PROJECT_URL` and `ANON_KEY`.
4. In Authentication → Providers, enable Email. For six-digit codes, configure the email OTP template to render the token using `{{ .Token }}` rather than only a magic-link URL.
5. Set the desired email confirmation and OTP expiry policies in Supabase Auth settings. Configure SMTP for reliable production delivery; the default email service is rate-limited.
6. Build and install the APK. Test sign-up, password sign-in, OTP request/verification, password recovery and sign-out.

Only the project URL and publishable/anon client key belong in the Android app. Never put the Supabase `service_role` or secret key in the APK or repository. Supabase project credentials are still required before account features can work.
