# Zorvi Browser upstream engine pin

Reviewed: 2026-11-12

## Selected stable baseline

| Component | Release/tag | Immutable commit |
|---|---|---|
| Brave browser orchestration | `v1.99.56` | `f14b4ba5b0256728ce74ca32bb0ccb43e3e6cff8` |
| Brave core | `v1.99.56` | `4d1dfc472152bcc8f3217be190c8a7ad3007039f` |
| Chromium | `157.0.8103.50` | `049750111b1e5c11565c792b2aeed5541f332bad` |
| Android target ABI | `arm64-v8a` | — |

The Brave browser commit explicitly pins `brave-core` to the `v1.99.56` tag. The Brave core release notes identify Chromium `157.0.8103.50`. The Chromium tag resolves to the commit shown above in the Chromium source mirror.

## Upstream evidence

- Brave release: https://github.com/brave/brave-browser/releases/tag/v1.99.56
- Brave browser commit: https://github.com/brave/brave-browser/commit/f14b4ba5b0256728ce74ca32bb0ccb43e3e6cff8
- Brave core commit: https://github.com/brave/brave-core/commit/4d1dfc472152bcc8f3217be190c8a7ad3007039f
- Chromium tag commit: https://github.com/chromium/chromium/commit/049750111b1e5c11565c792b2aeed5541f332bad
- Brave release notes: https://brave.com/latest/

## Verification

Run `scripts/verify_upstream_pins.sh` before any source sync. It checks that upstream Git refs still resolve to the recorded immutable commits. A changed ref is a stop condition requiring review, not an automatic pin update.

This is a source pin, not a completed source checkout. The full synchronized checkout must still record the Chromium revision from the actual `src/DEPS`/gclient solution and compare it with the release pin before compiling. The exact Chromium tag commit above is the release baseline; gclient may select a commit containing release-specific patch rolls, so do not silently replace the resolved DEPS revision with the tag commit.

## Build gate

No source has been downloaded or compiled by this pinning step. The free standard GitHub runner remains reserved for lightweight Android app checks. Do not attempt the full Chromium checkout/build there, and do not enable a paid runner.

## Upgrade policy

For each engine update, select a stable Brave Android release, resolve all annotated tags to commit SHAs, verify the Chromium version and resolved DEPS revision, update the pin table, then run the full build and smoke-test gate before adoption.
