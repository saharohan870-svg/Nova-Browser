# Zorvi Browser — Chrome/Firefox Resource Parity

## Requirement

Zorvi must support a low-memory Android phone with **4 GB physical RAM** and keep the downloadable install package **below 100 MB**. Runtime RAM and installed storage should be comparable to Chrome or Firefox on the same device under equivalent workloads. User data, browser cache and downloaded files may grow after installation; the initial APK/package size is the constrained deliverable.

The 100 MB limit applies to the actual user-facing install download. If the Chromium packaging architecture requires split APKs, report the base APK and the total required download size; do not claim compliance based on the base split alone. Do not strip security features or essential browser functionality just to meet the limit. If a genuine Chromium build cannot meet the cap, report that blocker instead of shipping a WebView build as if it were standalone Chromium.

Exact RAM equality is not a meaningful fixed specification: browser version, Android build, pages, tabs and cache change measurements.

## Measurement protocol

Run on the same physical ARM64 Android device and OS build:

1. Install current Chrome, Firefox and Zorvi builds one at a time; record installed size and app data/cache separately using Android App info and package/storage reporting.
2. Reboot or force-stop before each run; use the same network and wait for pages to settle.
3. Test idle after launch; one identical news page; five identical pages in five tabs; ten tabs; media playback; then background/resume.
4. Record process PSS/RSS and peak memory with Android developer tools / `adb shell dumpsys meminfo` where available. Record cold start and tab restore separately.
5. Repeat each scenario three times and compare medians. Keep Android version, browser version, page URLs, test order and device temperature consistent.
6. Report app package size, installed size, user data, cache, active RAM and peak RAM as separate numbers. Never combine downloaded files with browser app storage.

## Optimization priorities

- Target 4 GB RAM devices: keep background tabs discardable and restore them on demand; avoid retaining renderer processes unnecessarily.
- Build ARM64 only for the initial Zorvi phone target; do not bundle unused ABIs, debug symbols, test assets or duplicate resources in release packages.
- Enforce a sub-100 MB user-facing download budget and measure the complete required download, not just one split.
- Prefer Chromium-supported Android component and split packaging where it genuinely reduces delivered size; account for all mandatory splits and runtime components.
- Avoid duplicate browser engines, duplicate media codecs, bundled runtime copies and unnecessary assets.
- Keep cache bounded and user data outside the APK; cache and downloads may grow after installation.
- Use Chromium's normal Android low-memory behavior and background renderer prioritization; discard frozen/background renderers under memory pressure rather than keeping every tab resident.
- Keep tab metadata and session state lightweight; restore discarded tabs on demand.
- Bound HTTP cache and clean stale cache entries using Chromium's supported cache controls. Do not delete cookies, passwords, bookmarks or downloads as cache cleanup.
- Keep downloads in user-visible shared storage, not inside the APK or app cache.
- Avoid duplicate browser engines, duplicate media codecs, bundled runtime copies and unnecessary assets.
- Do not disable sandboxing, site isolation, Safe Browsing, TLS validation or other security protections to reduce memory.
- Avoid aggressive process-killing or arbitrary low memory caps that make pages reload constantly; validate user experience alongside RAM.
- Measure APK size and installed size after every release build. Use Android App Bundle/split delivery only if the chosen Chromium packaging architecture supports it correctly.

## Acceptance criteria

- Zorvi's median installed storage and runtime RAM should be in the same practical range as Chrome/Firefox for matched test scenarios on the target device.
- No claim of "same" or "lighter" without a recorded comparison table and build/version identifiers.
- The install download must be below 100 MB; runtime RAM and installed storage parity must be measured against Chrome and Firefox on the target phone.
- Preserve browser correctness, security and tab usability while optimizing.

## Current status

This file defines targets only. No Chromium APK has yet been built, installed or benchmarked as Zorvi. The existing Zorvi v0.3.0 WebView APK is not a valid comparison for a standalone Chromium fork. The sub-100 MB download and 4 GB RAM targets are requirements, not yet verified results.
