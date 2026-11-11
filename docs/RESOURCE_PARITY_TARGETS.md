# Nova Browser — Chrome/Firefox Resource Parity

## Requirement

Nova should aim for comparable installed storage and runtime RAM to current Chrome and Firefox on the same Android device, under equivalent workloads. Exact equality is not a meaningful fixed specification: browser version, Android build, pages, tabs, extensions/features, cache and downloads change measurements.

## Measurement protocol

Run on the same physical ARM64 Android device and OS build:

1. Install current Chrome, Firefox and Nova builds one at a time; record installed size and app data/cache separately using Android App info and package/storage reporting.
2. Reboot or force-stop before each run; use the same network and wait for pages to settle.
3. Test idle after launch; one identical news page; five identical pages in five tabs; ten tabs; media playback; then background/resume.
4. Record process PSS/RSS and peak memory with Android developer tools / `adb shell dumpsys meminfo` where available. Record cold start and tab restore separately.
5. Repeat each scenario three times and compare medians. Keep Android version, browser version, page URLs, test order and device temperature consistent.
6. Report app package size, installed size, user data, cache, active RAM and peak RAM as separate numbers. Never combine downloaded files with browser app storage.

## Optimization priorities

- Build ARM64 only for the initial Nova phone target; do not bundle unused ABIs or debug symbols in release packages.
- Use Chromium's normal Android low-memory behavior and background renderer prioritization; discard frozen/background renderers under memory pressure rather than keeping every tab resident.
- Keep tab metadata and session state lightweight; restore discarded tabs on demand.
- Bound HTTP cache and clean stale cache entries using Chromium's supported cache controls. Do not delete cookies, passwords, bookmarks or downloads as cache cleanup.
- Keep downloads in user-visible shared storage, not inside the APK or app cache.
- Avoid duplicate browser engines, duplicate media codecs, bundled runtime copies and unnecessary assets.
- Do not disable sandboxing, site isolation, Safe Browsing, TLS validation or other security protections to reduce memory.
- Avoid aggressive process-killing or arbitrary low memory caps that make pages reload constantly; validate user experience alongside RAM.
- Measure APK size and installed size after every release build. Use Android App Bundle/split delivery only if the chosen Chromium packaging architecture supports it correctly.

## Acceptance criteria

- Nova's median installed storage and runtime RAM should be in the same practical range as Chrome/Firefox for matched test scenarios on the target device.
- No claim of "same" or "lighter" without a recorded comparison table and build/version identifiers.
- No exact byte or MB target until Chrome and Firefox are measured on the target phone.
- Preserve browser correctness, security and tab usability while optimizing.

## Current status

This file defines targets only. No Chromium APK has yet been built, installed or benchmarked as Nova. The existing Nova v0.3.0 WebView APK is not a valid comparison for a standalone Chromium fork.
