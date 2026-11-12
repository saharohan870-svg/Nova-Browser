# Phase 4 — Testing, release and Zorvi Engine

## Scope
Phase 4 validates the current Android browser build, exercises the Zorvi engine's implemented components, checks the target-site matrix, resolves defects, and prepares a verifiable APK release.

## Status (started 2026-11-12)

| Workstream | Status | Evidence / next action |
|---|---|---|
| CI unit-test job | In progress | Existing JUnit suite is present under `app/src/test/java/com/zorvi/engine`; CI now runs `testDebugUnitTest`. |
| Android debug APK build | In progress | CI runs `assembleDebug` after unit tests and uploads the APK artifact. |
| Build failure triage | In progress | Previous build exposed Kotlin generic inference failures in three GeckoResult continuations; explicit continuation type was added in commit `8b5614eb9c00bac51798b8fd792be47e182398bd`. Awaiting CI result. |
| Zorvi engine unit coverage | Partial | URL, networking, security/storage, DOM, HTML, CSS, layout, graphics, accessibility, resource discovery and private-session tests exist. Passing status must be confirmed from the current commit's CI. |
| Website compatibility (16 targets) | Test matrix prepared; device testing pending | Added per-site result table and regression checklist in `docs/SEVEN_SITE_COMPATIBILITY_TEST_PLAN.md`. All 16 remain Not run until tested on a physical Android device. |
| Ad blocker validation | Not yet verified | Confirm extension registration, filter-list downloads, network blocking, exceptions and cosmetic filtering on-device. |
| Downloads and tab groups | Implementation expanded; device tests pending | Nova download list now shows DownloadManager status/progress and supports remove/delete actions. Tab groups support switch, move current tab, rename and delete (tabs move to General). |\n| Performance, memory and crash tests | Pending | Measure cold start, page load, tab memory, background/low-memory behavior and repeated navigation on real devices. |
| Security/privacy review | Partial | Private-tab downloads are blocked; cookie policy and website-data controls added; external app links require a user gesture. Full review and device verification remain pending. |
| Release APK / GitHub Release | Pending | Release only after CI passes and artifact installation/smoke tests succeed. |

## Acceptance criteria
- Unit tests pass on the current branch head.
- Debug APK builds reproducibly and is uploaded as a workflow artifact.
- No known Kotlin compilation failures remain.
- Target-site matrix records pass/fail for sign-in, media, uploads, downloads and desktop mode where relevant.
- Ad blocker is tested against representative ad/tracker requests and exception rules; known unsupported filter syntax is documented.
- Physical-device smoke, stability and performance checks are recorded.
- Release notes identify the actual engine (GeckoView) and clearly label Zorvi renderer as experimental.

## Important limitations
The app is GeckoView-based, not a Chromium build. The Zorvi renderer is experimental and does not yet replace GeckoView for general web browsing. Automated JVM tests cannot certify Android UI, media playback, site compatibility or real-device performance.\n## Additional Phase 4 implementation commits\n- `779dd676` — richer download manager and tab-group management.\n- `9994769f` — explicit 16-site compatibility result matrix and device regression checklist.\n

## Guest Mode implementation (2026-11-12)
- Added a separate **Guest Mode** control; Private Mode remains a separate control.
- Guest tabs use GeckoView private sessions, are excluded from saved history and session restore, and cannot create bookmarks or downloads.
- Guest site-permission choices are not persisted.
- **End Guest** closes all guest tabs and returns to the previously active tab.
- Guest tabs are labeled in the tab switcher.
- Important outstanding privacy requirement: this does not yet route traffic through Tor or otherwise mask the public IP. GeckoView has no supported public proxy-configuration API; a real Tor route needs a separately integrated and verified routing layer. Do not describe Guest Mode as Tor-protected until that is implemented and tested.
- Guest Mode and Private Mode currently both rely on GeckoView's private-session facility; separate isolated private profiles are not yet verified.
- Android build and on-device lifecycle/data-cleletion tests are pending.

## Network privacy features requested (2026-11-12)
- Product direction confirmed: an included, Brave-like VPN service for normal browsing, plus Tor routing specifically for Guest Mode.
- Architecture and security acceptance criteria are documented in `docs/NETWORK_PRIVACY_ARCHITECTURE.md`.
- **Not implemented yet:** VPN tunnel, provider authentication/server infrastructure, Tor runtime, GeckoView traffic routing and leak protection.
- A real included VPN cannot be activated from Android client code alone; a VPN provider or Nova-operated server fleet and protocol/SDK are required. Do not show a connected state until a real tunnel is verified.
- Tor Guest Mode must fail closed (never silently use direct internet) if bootstrap or routing fails.
