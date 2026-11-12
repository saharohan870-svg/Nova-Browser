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
| Website compatibility (16 targets) | Not yet device-verified | Run the Phase 2 compatibility matrix on physical Android hardware; shortcuts alone do not count as compatibility evidence. |
| Ad blocker validation | Not yet verified | Confirm extension registration, filter-list downloads, network blocking, exceptions and cosmetic filtering on-device. |
| Performance, memory and crash tests | Pending | Measure cold start, page load, tab memory, background/low-memory behavior and repeated navigation on real devices. |
| Security/privacy review | Pending | Review permissions, private sessions, storage isolation, navigation policy, downloads and site permission persistence. |
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
The app is GeckoView-based, not a Chromium build. The Zorvi renderer is experimental and does not yet replace GeckoView for general web browsing. Automated JVM tests cannot certify Android UI, media playback, site compatibility or real-device performance.