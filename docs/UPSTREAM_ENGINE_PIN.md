# Zorvi Browser upstream engine pin

Reviewed: 2026-11-12

## Selected baseline

| Component | Pin | Reason |
|---|---|---|
| Brave Android source release | `v1.99.56` | Latest stable Brave release observed during this review |
| Chromium | `157.0.8103.50` | Chromium revision reported by that Brave stable release |
| Android target ABI | `arm64-v8a` | Initial target for modern ARM64 Android phones |

Upstream release record: https://github.com/brave/brave-browser/releases/tag/v1.99.56

Brave release notes: https://brave.com/latest/

The Brave release notes dated 2026-11-11 identify Android release v1.99.56 and state that it upgrades Chromium to 157.0.8103.50. Use this stable baseline rather than a Nightly or Beta tag for the first Zorvi port.

## Important reproducibility rule

The release tag is the human-readable source pin. Before any source checkout/build, resolve and record the immutable Git commit SHA for `v1.99.56`, and record the Chromium DEPS revision resolved by that checkout. Do not infer either hash from the version string. The build record must include:

- Brave browser tag and resolved commit SHA
- Brave core commit SHA
- Chromium revision from the checked-out DEPS configuration
- Android SDK/NDK and JDK versions
- Build command and build environment image/runner
- SHA-256 of the resulting APK

## Build gate

This pin is a source-selection decision only. It does **not** mean the source has been downloaded, dependencies synchronized, or an APK built. The free standard GitHub runner remains reserved for lightweight Android app checks; do not attempt the full Chromium checkout/build there, and do not enable a paid runner.

## Upgrade policy

For each engine update, select a stable Brave Android release, review its Chromium security updates and Android changes, update this file, resolve all immutable revisions, then run the full build and smoke-test gate before adopting it.
