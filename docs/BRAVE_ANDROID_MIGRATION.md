# Zorvi Browser — Brave/Chromium Android Migration

## Decision

Zorvi Browser will move from the experimental in-repository renderer to a branded Android browser built from the upstream Brave Android/Chromium source. The existing application and experimental Zorvi engine are retained on the original `chromium-engine-migration` branch as a fallback; this migration branch is intentionally separate.

## Upstream source of truth

- Brave browser source/build orchestration: https://github.com/brave/brave-core
- Brave browser project, releases and Android build documentation: https://github.com/brave/brave-browser
- Android build guide: https://github.com/brave/brave-browser/wiki/Android-Development-Environment
- Brave core is MPL-2.0; Chromium and bundled dependencies have their own licenses and notices. Preserve all upstream notices and publish modifications/source as required by the applicable licenses.
- Do not use Brave logos, name, product icons, package identifiers, signing keys, update endpoints or service credentials as Zorvi branding.

## Product target

- Product name: **Zorvi Browser**
- Platform: Android
- Initial device ABI: ARM64 (modern Android phones)
- Browser foundation: Brave's Chromium/Blink/V8 stack and Brave's Android integration, pinned to a known upstream release/commit.
- User-facing features to retain or integrate:
  - Ad blocking and tracker protection (based on upstream Brave Shields; verify Android parity and default settings)
  - AI assistant (separate Zorvi feature; do not imply Brave ships this feature)
  - Tab groups
  - Private browsing with correct ephemeral/incognito storage semantics
  - Downloads
  - Bookmarks and browsing history
  - Search-engine selector
  - Dark mode

## Migration principles

1. Never overwrite `main` or `chromium-engine-migration` during migration.
2. Pin upstream Brave and Chromium revisions; do not track moving branches for reproducible builds.
3. First produce an unmodified upstream Android debug APK in the cloud build environment.
4. Then change application identity, branding, resources and supported build configuration.
5. Integrate Zorvi features incrementally, with a test and build gate after every phase.
6. Preserve security updates by documenting an upstream sync/rebase process.
7. Do not claim the build or any feature is complete until CI logs and a device/emulator test prove it.

## Phases and acceptance gates

### Phase A — Build environment and upstream pin
- Provision a Linux cloud build host with the disk, RAM and CPU needed for Chromium Android builds.
- Install Git 2.41+, Python 3, Node.js 24+, pnpm and Brave's documented Android dependencies.
- Clone the official Brave build repository and initialize its pinned Chromium/Brave dependencies.
- Record the exact Brave commit, Chromium revision, toolchain versions and build command.
- Acceptance: reproducible ARM64 Android debug APK from upstream with a successful clean build log.

### Phase B — Zorvi identity
- Change app label, application/package identity, launcher assets, strings, colors and browser chrome to Zorvi.
- Remove Brave-specific product branding while preserving third-party legal notices and required attributions.
- Acceptance: Zorvi-branded APK installs alongside stock Brave and launches on Android 10+ / current Android.

### Phase C — Core browser validation
- Verify navigation, omnibox/search, back/forward/reload, tabs, downloads, bookmarks and history.
- Verify private mode does not persist cookies, history, cache or site data after private session closure.
- Acceptance: automated tests plus manual smoke test on ARM64 Android.

### Phase D — Zorvi feature integration
- Configure Shields/ad and tracker protection.
- Implement/port tab groups and search-engine selector if upstream behavior does not meet Zorvi requirements.
- Add Zorvi AI assistant behind an explicit user action and permission/privacy controls.
- Validate dark mode and accessibility.
- Acceptance: feature-specific tests, no regressions, documented limitations.

### Phase E — Release engineering
- Configure reproducible signed release builds using Zorvi-owned signing material (never commit keys).
- Add SBOM/license notices, privacy policy, security contact and update strategy.
- Run Android compatibility, stability, performance and security checks.
- Publish an APK only after release checks pass.

## Sign-in and personal cloud integration

The selected design is **Option B**: the Brave/Chromium engine runs on-device; Google and GitHub sign-in unlock Zorvi cloud features; the user's own Codespace is used only for explicitly requested cloud tasks (initially the AI assistant). Normal web traffic remains local and must not be routed through Codespaces.

The security, account-linking, Codespace lifecycle, privacy boundaries and implementation sequence are specified in [AUTH_AND_CODESPACES.md](AUTH_AND_CODESPACES.md). Production sign-in is blocked until provider applications and a secure backend are registered and configured. No OAuth secrets or GitHub tokens may be embedded in the APK.

## Free-only build strategy

The project must not incur charges without explicit approval. The repository's standard GitHub-hosted runner can build the lightweight Android WebView interim app, run its tests and publish a clearly labelled debug APK artifact. It cannot be treated as a Brave/Chromium source-build machine: the full upstream checkout and Android build need substantially more persistent disk and memory than this runner provides.

The existing `.github/workflows/android.yml` is therefore enabled on this migration branch and explicitly labels its artifact as an **interim WebView build**, not Brave. This gives us a free, testable Android shell while the real engine migration is prepared.

Do not attempt to download or compile the full Brave/Chromium source on the standard runner, silently switch to a paid runner, or publish the interim APK as a Brave-powered browser. A true Brave APK still requires a suitable build host. Possible no-cost routes can be evaluated separately, but only a verified host with adequate resources and no billing requirement may be used.

## Current status

- [x] Migration decision recorded.
- [x] Dedicated migration branch created from the existing experimental-engine branch.
- [ ] Find and verify a genuinely no-cost Linux build host with sufficient persistent resources.
- [ ] Pin upstream Brave/Chromium revisions.
- [ ] Build unmodified upstream ARM64 Android APK.
- [ ] Apply Zorvi branding.
- [ ] Port and verify requested Zorvi features.
- [ ] Release and publish.

