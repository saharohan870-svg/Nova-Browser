# Nova Browser — Chrome-Class Performance Plan

## Goal

Nova should feel as fast as Chrome on the same Android device wherever practical: cold start, warm start, navigation, page rendering, scrolling, JavaScript execution, media playback and tab switching. Chrome parity is a performance target, not a result that can be claimed before device testing.

## Device constraints

- Primary test device class: ARM64 Android phone with 4 GB physical RAM.
- Install download budget: below 100 MB, including all mandatory APK splits.
- The app must remain responsive under Android memory pressure. Background tabs may be discarded and restored when selected.
- Keep security protections enabled; performance must not be achieved by disabling sandboxing, site isolation, TLS validation, Safe Browsing or other essential protections.

## Performance engineering priorities

1. **Use a real Chromium Android engine.** The existing WebView build is only a prototype and cannot establish performance parity with Chrome. A Chromium-based fork must be built and integrated before meaningful engine-level optimization.
2. **Avoid extra work on the navigation path.** Keep address-bar handling, URL classification, tab selection and navigation dispatch lightweight. Do not block the UI thread with disk, network or parsing work.
3. **Keep the browser UI lean.** Avoid heavy animations, large image assets, excessive view nesting and unnecessary background services. Keep the toolbar responsive while pages load.
4. **Use Chromium's platform optimizations.** Build optimized ARM64 release binaries with production compiler settings; use supported Android graphics, media and networking paths. Do not ship debug builds or debug symbols in release packages.
5. **Control tab memory intelligently.** Keep the foreground renderer responsive; prioritize recently used tabs; freeze/discard background renderers under memory pressure and restore them on demand. Avoid arbitrary renderer caps that cause needless reloads.
6. **Do not add duplicate work.** Avoid duplicate engine instances, redundant content filters, repeated database queries, synchronous persistence and unnecessary polling.
7. **Keep content filtering efficient.** Use compact indexed host/rule data and perform checks without blocking page rendering. Measure the cost of ad/tracker filtering on real pages.
8. **Cache safely.** Let Chromium manage HTTP and resource caching; use bounded cache policies. Do not clear useful cache on every launch.
9. **Measure before tuning.** Record cold/warm launch, first navigation, page load milestones, scrolling smoothness, tab switch/restore, media playback and memory. Compare against Chrome using the same device, Android build, network, URLs and test sequence.
10. **Test real-world pages.** Include lightweight pages, heavy news pages, search results, social feeds and media pages. Run at least three repetitions and compare medians; note network variance separately from browser time.

## Benchmark reporting

For each browser/version and scenario, record:
- Cold start and warm start time.
- Navigation-to-first-content and page-load completion time, with the measurement method stated.
- Scroll smoothness / dropped frames where measurable.
- Tab switch and discarded-tab restore time.
- CPU and RAM (PSS/RSS/peak), separately from timing.
- APK download size, installed size, data and cache separately.

Use Chrome on the same phone as the reference. A page-load result is only comparable when the exact URL, network conditions, cache state and test procedure match. Do not use a single speed-test website as proof of overall browser speed.

## Acceptance criteria

- No obvious UI-thread stalls during typing, navigation, scrolling or tab switching.
- Common page navigation and rendering should be close to Chrome on the same phone; investigate any repeatable regression rather than masking it with a claim.
- 4 GB RAM operation and sub-100 MB complete install download remain mandatory constraints.
- Preserve security and web compatibility.
- Publish benchmark method, build identifier and results before claiming Chrome-equivalent speed.

## Current status

This is an engineering target only. No standalone Nova Chromium build has yet been benchmarked against Chrome on the target phone. The existing WebView prototype cannot substantiate Chrome-engine speed parity.
