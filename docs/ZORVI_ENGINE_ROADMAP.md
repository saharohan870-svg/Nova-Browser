# Zorvi Engine — Long-Term Roadmap

## Vision

Build an independent, standards-oriented browser engine for Android, with a modular architecture and a long-term capability target comparable to modern browser engines such as Chromium's Blink, Firefox's Gecko, and Safari's WebKit.

This is a capability target, not a claim that the current prototype is close to those engines. The current implementation is an early renderer prototype: limited HTML parsing, basic CSS rules, a small DOM bridge, and an embedded QuickJS runtime. The main browser shell currently uses Mozilla GeckoView; the independent Zorvi renderer is exposed only through a separately labelled experimental page path. Keep that distinction explicit until the independent engine is integrated and passes release gates.

A modern engine is a multi-year engineering effort for a small team, and potentially much longer for a solo project. Do not promise feature parity or set calendar dates before measuring the work.

## Design principles

1. **Standards first:** implement web-platform behavior against published specifications and conformance tests, not just hand-picked websites.
2. **Security before exposure:** untrusted websites must never gain access to Android app privileges, local files, secrets, or unrestricted native bindings.
3. **Modular subsystems:** parsing, DOM, CSS, layout, scripting, networking, graphics, storage, and platform integration have clear interfaces.
4. **Correctness before speed:** optimize only after behavior is tested and profiled.
5. **Mobile-aware:** target ARM64 Android devices, including devices with 4 GB RAM; use bounded caches and measurable memory budgets.
6. **Incremental integration:** retain the existing WebView browser as a temporary compatibility fallback until Zorvi Engine meets defined security and compatibility gates. Never silently present WebView as the independent engine.
7. **Reproducible builds:** every milestone must compile in CI and include automated tests.

## Target architecture

- **Browser shell:** tabs, address/search bar, navigation, downloads, bookmarks, history, private mode, permissions and browser settings.
- **Engine API:** navigation, document lifecycle, page viewport, input, rendering, console and lifecycle events.
- **HTML subsystem:** byte decoding, tokenization, tree construction, error recovery and document serialization.
- **DOM subsystem:** node types, document APIs, mutation, events, ranges, observers and selection.
- **CSS subsystem:** tokenization, parsing, cascade, selector matching, computed styles, values and animations.
- **Layout subsystem:** formatting contexts, block/inline layout, flexbox, grid, tables, fragmentation and intrinsic sizing.
- **JavaScript subsystem:** ECMAScript runtime, bindings, jobs/microtasks, modules, garbage collection and Web APIs.
- **Graphics subsystem:** text shaping, image decoding, SVG, canvas, paint records, compositing and GPU acceleration.
- **Networking subsystem:** URL handling, HTTP(S), redirects, TLS validation, cookies, cache, content decoding and resource scheduling.
- **Storage subsystem:** cookies, local/session storage, IndexedDB, Cache API, quota and partitioning.
- **Security subsystem:** process/thread isolation strategy, origin model, same-origin policy, CSP, CORS, sandboxing, permissions and safe native bridges.
- **Android platform layer:** input, accessibility, fonts, hardware acceleration, lifecycle, downloads, media, file selection and system integration.

## Current progress (2026-11-11)\n\n- **Phase 0 — foundation:** architecture boundaries, dependency direction, lifecycle/resource-limit rules, JVM test dependency, parser unit-test suite, and CI unit-test execution are committed. Real-device baseline measurements remain pending because they require running the build on the target Android phone.\n- **Phase 1 — HTML parser:** initial bounded tokenizer/tree-builder implementation and parser regression tests are committed. It is not a complete WHATWG HTML parser; standards fixtures, tokenizer edge cases, tree-construction modes and fuzzing remain open.
- **Phase 2 — DOM/event model:** the prototype now has parent-safe node moves, cycle prevention, ID/tag lookup, text/attribute bridge operations, and capture/target/bubble event dispatch with once/cancelation controls. This is an initial slice only; node types, selector APIs, mutation observers, serialization, ranges and full DOM semantics remain unimplemented. New event regression tests are committed but await CI.\n\n## Phases and exit criteria

### Phase 0 — Engineering foundation and architecture
**Goal:** make the prototype maintainable before adding large subsystems.

Work:
- Document module boundaries, ownership, threading and error handling.
- Establish a Gradle module structure for core, parser, DOM, CSS, layout, script, networking, graphics and Android integration as appropriate.
- Add unit-test and instrumentation-test infrastructure.
- Add CI build, lint, dependency/license inventory and reproducible debug artifacts.
- Add a small HTML/CSS/JS fixture corpus and a benchmark harness.
- Record baseline APK size, installed size, cold start, page load, peak RSS and frame time.

Exit criteria:
- Clean CI build on every change.
- Tests run automatically.
- Engine modules can be tested without launching the browser UI.
- Baseline metrics are recorded on a representative Android device.

### Phase 1 — HTML parser and document model
**Goal:** move beyond regex parsing to robust HTML parsing.

Work:
- Character encoding detection and Unicode handling.
- HTML tokenizer and token stream.
- Tree construction with insertion modes and error recovery.
- Void elements, raw-text elements, comments, entities and malformed markup.
- Head/body construction, metadata, title, links, images and forms.
- Parser tests using html5lib/WPT-compatible fixtures.

Exit criteria:
- Parser produces stable trees for valid and malformed HTML fixtures.
- No crashes on fuzzed parser input.
- Document structure and text extraction tests pass.

### Phase 2 — DOM and event model
**Goal:** provide a coherent, testable document API.

Work:
- Node, Element, Text, Document, DocumentFragment and common collections.
- Attributes, classList, dataset, selectors and traversal.
- DOM mutation methods and serialization.
- EventTarget, capture/target/bubble phases, cancellation and default actions.
- MutationObserver, basic selection and ranges.
- Implement standards behavior incrementally and document unsupported APIs.

Exit criteria:
- DOM operations work against conformance fixtures.
- Event ordering and mutation behavior have automated tests.
- DOM updates reliably invalidate style/layout/paint.

### Phase 3 — CSS parser, cascade and style system
**Goal:** replace the current limited CSS regex resolver with a real CSS pipeline.

Work:
- CSS tokenizer and parser.
- Stylesheet rules, at-rules, declarations and error recovery.
- Selector matching: combinators, attributes, pseudo-classes and pseudo-elements.
- Cascade origins, specificity, source order, inheritance and custom properties.
- Computed values, units, media queries and viewport-dependent styles.
- Fonts, colors, backgrounds, borders, margins, padding and box model.
- Incremental style invalidation.

Exit criteria:
- CSS parsing and cascade tests pass for the implemented feature set.
- Computed styles are deterministic.
- Invalid CSS does not crash the page.

### Phase 4 — Layout engine
**Goal:** implement standards-based geometry rather than drawing text blocks.

Work in this order:
1. Containing blocks, box model and block formatting.
2. Inline formatting, line breaking, text metrics and bidi basics.
3. Replaced elements and intrinsic sizing.
4. Positioning, overflow, scrolling and stacking contexts.
5. Flexbox.
6. CSS Grid.
7. Tables and lists.
8. Fragmentation, sticky positioning and more advanced layout.

Exit criteria:
- Layout fixtures match expected geometry within defined tolerances.
- Scrolling, hit testing and resize/reflow work.
- Flexbox and Grid test subsets pass before broad site compatibility is claimed.

### Phase 5 — JavaScript runtime and browser bindings
**Goal:** turn the current isolated QuickJS proof of concept into a usable scripting subsystem.

Work:
- Verify QuickJS library version, license, Android ABI support and maintenance status.
- Build a runtime adapter so the engine is not coupled to one JS implementation.
- Add correct script execution order, defer/async behavior and module loading.
- Implement task and microtask scheduling, Promise jobs and error reporting.
- Add safe DOM bindings and event integration.
- Add Web IDL-style binding conventions and conversion rules.
- Add garbage collection coordination and per-page resource limits.
- Evaluate runtime compatibility and performance using Test262.
- Define a documented policy for unsupported language features and APIs.

Important decision gate:
QuickJS is a useful lightweight starting point, but it is not automatically equivalent to V8 or SpiderMonkey in performance, compatibility, or ecosystem. Measure Test262 coverage and real-site requirements. If it becomes a blocker, evaluate alternative embeddable runtimes behind the adapter rather than rewriting the whole engine.

Exit criteria:
- A defined Test262 subset passes and grows by release.
- DOM scripts run in deterministic order.
- Infinite loops and memory exhaustion are contained.
- No unrestricted Android or filesystem APIs are exposed to page scripts.

### Phase 6 — Essential Web APIs
**Goal:** support the browser APIs expected by ordinary websites.

Prioritize:
- URL and URLSearchParams.
- Console, timers and event loop integration.
- Fetch, XMLHttpRequest and AbortController.
- History and Location.
- Storage APIs: localStorage and sessionStorage.
- Form controls and validation.
- Messaging, workers and structured clone.
- ResizeObserver and IntersectionObserver.
- WebSocket and server-sent events.
- Permissions and user-gesture handling.

Later:
- Service workers and push.
- WebRTC.
- WebAssembly.
- WebCodecs and advanced media APIs.
- More advanced graphics and device APIs.

Exit criteria:
- API-specific WPT subsets pass.
- Origin, CORS, credentials and permission behavior are tested.
- Unsupported APIs fail safely and predictably.

### Phase 7 — Graphics, images, fonts and compositing
**Goal:** produce accurate, smooth visual output.

Work:
- Text shaping, font fallback, glyph metrics and bidirectional text.
- Image decoding for common web formats and responsive image selection.
- SVG parsing, styling and rendering.
- Paint records, clipping, transforms, opacity and stacking.
- Canvas 2D; WebGL/WebGPU only after a security and architecture review.
- Layerization, damage tracking and compositing.
- Hardware acceleration with safe software fallback.
- Animations, transitions and requestAnimationFrame scheduling.

Exit criteria:
- Visual regression tests are stable.
- Scrolling and animation meet frame-time targets on reference devices.
- GPU failure or context loss does not crash the browser.

### Phase 8 — Networking and resource loading
**Goal:** implement browser-grade loading behavior.

Work:
- URL parsing and normalization.
- HTTP/1.1 and HTTP/2; HTTP/3 only when dependencies and testing justify it.
- TLS certificate validation using trusted platform facilities.
- Redirects, content negotiation, compression and streaming.
- Resource loader for scripts, stylesheets, images, fonts and media.
- Priorities, preload hints, cancellation and cache revalidation.
- Cookies, SameSite, secure flags and domain/path rules.
- CORS, CORB-like protections where applicable, mixed-content policy and referrer policy.
- Proxy and certificate error handling with secure defaults.

Exit criteria:
- Networking tests cover redirects, cookies, TLS failures, CORS and cancellation.
- Certificate errors are never bypassed.
- Resource loading is asynchronous and does not block the UI thread.

### Phase 9 — Storage, privacy and site isolation
**Goal:** protect user data and enforce web security boundaries.

Work:
- Origin model and same-origin policy.
- Cookie jar and partitioned storage.
- localStorage, sessionStorage, IndexedDB and Cache API.
- Storage quotas, eviction and clear-site-data.
- Private browsing with genuinely isolated, ephemeral storage.
- Site/process isolation strategy appropriate to Android memory constraints.
- Content Security Policy, sandboxed frames and permission policy.
- Safe browsing integration and download protections.
- Secure bridge design with strict origin checks.

Exit criteria:
- Security review completed before public beta.
- Private mode leaves no persistent browsing state after closure, within documented platform limits.
- Cross-origin isolation tests pass for supported features.
- No renderer can access arbitrary app-private files or secrets.

### Phase 10 — Media, forms, accessibility and internationalization
**Goal:** support real-world content and users.

Work:
- Audio/video playback, codecs through supported Android/platform components where appropriate.
- Media controls, captions, fullscreen and autoplay policy.
- Form controls, file picker, upload/download flows and validation.
- Keyboard, touch, stylus and pointer input.
- Screen-reader semantics and Android accessibility tree.
- Zoom, text scaling, selection and find-in-page.
- RTL, Unicode, emoji, locale-aware formatting and font fallback.
- Printing and PDF support if feasible.

Exit criteria:
- Accessibility tree is usable with TalkBack.
- Common media and form scenarios pass tests.
- International text fixtures render correctly.

### Phase 11 — Performance, reliability and conformance
**Goal:** make the engine dependable and competitive on mobile.

Work:
- Profile parsing, style, layout, script, paint, raster and network separately.
- Incremental DOM/style/layout updates.
- Memory accounting, bounded caches and background tab suspension.
- Reduce allocations and avoid main-thread blocking.
- Crash reporting without collecting page contents or sensitive user data.
- Fuzz HTML, CSS, URL, image and protocol parsers.
- Run WPT, Test262, CSS tests and browser interoperability suites.
- Benchmark against Android Chrome and Firefox using the same device, network and pages.

Exit criteria:
- Publish measured compatibility results, not vague parity claims.
- Define and meet memory, startup, responsiveness and crash-rate targets.
- No known critical security issues.
- Performance regressions are caught in CI.

### Phase 12 — Browser integration and release
**Goal:** make Zorvi Engine the actual rendering engine behind Zorvi Browser.

Work:
- Connect navigation, tabs, history, bookmarks, downloads and address bar to engine APIs.
- Add per-tab page lifecycle and renderer recovery.
- Implement permissions, site settings and user-visible security indicators.
- Keep compatibility fallback explicit and measurable during beta.
- Release internal alpha, closed beta, open beta and stable only after their gates pass.
- Provide a compatibility report and known-limitations page.

Exit criteria:
- Zorvi Browser can use Zorvi Engine without silently routing pages through WebView.
- Security, conformance, stability and performance release gates are met.
- Users can identify when a page is rendered by the experimental engine versus a fallback.

## Testing strategy

Use automated tests from the beginning:
- **HTML:** html5lib parser tests and relevant Web Platform Tests.
- **DOM and Web APIs:** Web Platform Tests.
- **JavaScript:** Test262.
- **CSS and layout:** CSS test suites and WPT visual/layout tests.
- **Networking/security:** TLS, CORS, cookie, origin and redirect test servers.
- **Graphics:** screenshot-based visual regression tests.
- **Fuzzing:** parser, URL, CSS, image and protocol inputs.
- **Android:** instrumentation tests on ARM64 devices with constrained memory.

Every feature should have: specification reference, implementation notes, tests, known gaps and a clear completion criterion.

## Performance and device targets

The initial reference device class is Android ARM64 with 4 GB RAM. Treat these as measurement categories, not promises:
- APK download size and installed size.
- Runtime memory: foreground tab, background tabs and peak usage.
- Cold start and warm start.
- Time to first content and page load milestones.
- Scroll frame time and animation smoothness.
- CPU/battery use on representative pages.
- Cache growth and storage quota behavior.

Do not set arbitrary tiny APK limits that force removal of required security or standards functionality. Prefer modular packaging, native ABI splits where appropriate, and bounded runtime caches.

## Immediate next engineering tasks

1. Confirm the QuickJS dependency/build works on Android API 36 and supported ABIs.
2. Fix all compile and CI failures before adding more engine features.
3. Add unit tests for HTML parsing, DOM bridge and CSS parsing.
4. Replace regex-based script extraction with parser-produced script nodes and correct execution ordering.
5. Add safe, bounded page navigation and cancellation to the engine loader.
6. Implement real scroll gestures, hit testing and layout invalidation.
7. Build a minimal layout tree and box model before adding more CSS properties.
8. Keep the independent engine behind an experimental flag until security and compatibility gates are satisfied.

## Completion definition

“Modern-engine capable” means a documented, measurable level of standards support, security, performance and reliability across the target web-platform test suites and real-world sites. It does not mean that a feature list exists or that a few demonstration pages render.

The roadmap is complete only when the engine has a published compatibility matrix, reproducible benchmarks, security review results, automated conformance coverage and release-quality Android integration.


## Phase 7 implementation note (parallel foundation)

Phase 7 has begun with graphics-layer components isolated from the in-progress layout, scripting, and Web API work in Phases 4–6:

- An immutable paint-command model and Android Canvas raster backend for solid rectangles, text and bitmap drawing.
- A platform-neutral CSS-pixel rectangle type so display-list geometry does not depend on Android graphics classes.
- A deterministic generic-family font resolver using Android Typeface fallback.
- A bounded bitmap decoder that rejects oversized encoded data and images above a 16-megapixel decoded-pixel budget, and selects a power-of-two sample size for target dimensions.
- Initial unit tests for image sample sizing and display-list geometry.

This is a foundation only. It is not yet wired to the document/layout pipeline, and does not provide SVG, Canvas web APIs, image resource fetching, text shaping, clipping/transform stacks, GPU compositing, or browser-grade image format coverage. Integration should wait until the parallel Phase 4–6 interfaces are known.


## Phase 8 implementation note (initial networking slice)

The networking subsystem now has an initial, isolated implementation:

- HTTP/1.1 requests using the Android/JVM `HttpURLConnection` stack.
- HTTPS certificate validation delegated to the platform trust store; no permissive trust manager or certificate-error bypass is installed.
- Explicit handling for 301, 302, 303, 307 and 308 redirects, with a redirect-count limit, HTTPS downgrade rejection, and cross-origin credential-header stripping.
- Gzip and deflate response decoding, request timeouts, cancellation via connection disconnect, and a configurable decompressed-response byte limit.
- A per-loader bounded LRU memory cache that stores only explicitly fresh public GET responses and respects no-store, no-cache and private directives.
- An asynchronous resource loader with a bounded worker pool and request Accept profiles for documents, stylesheets, scripts, images, fonts and media.
- Local HTTP regression tests for response bodies/headers, redirects, size limits, cache freshness and no-store behavior.

This is not Phase 8 completion. HTTP/2, full cookie semantics (including SameSite), cache validators/revalidation (ETag/Last-Modified), CORS enforcement, mixed-content/referrer policy, streaming consumer APIs, resource priority/preload scheduling, and Android-device TLS/cancellation verification remain open. The current transport is HTTP/1.1; HTTP/2/3 support must be added only with a tested dependency/transport decision. The loader is not yet connected to HTML subresource discovery or the renderer. Certificate failures must continue to fail closed.


## Phase 12 implementation note (first browser integration slice)

An explicit experimental Zorvi page path is now present in the Android browser shell:

- The main browser toolbar has an **Zorvi Engine** action that opens the current address in a dedicated page Activity.
- The dedicated page host provides address/search input, Go, reload, back navigation, page title, and a visible “Zorvi Engine · Experimental renderer” label.
- This Activity creates and hosts `ZorviEngineView` directly and does not instantiate Android WebView. The existing GeckoView-backed shell remains the normal compatibility path and is not misrepresented as the independent Zorvi renderer.
- Zorvi page navigation now uses the bounded Phase 8 HTTP client, platform TLS validation, response-size limits, and HTML content-type checks.
- A navigation generation token prevents stale responses from replacing a newer page; the active network call is cancelled when navigating again or detaching the renderer.
- Basic touch-drag scrolling is enabled in the engine view.

This is an integration prototype, not Phase 12 completion. The renderer still displays a limited text-oriented HTML subset; external CSS, scripts, images and fonts are not yet fetched/applied as a complete page pipeline. The layout engine and graphics display-list are not yet the source of geometry/painting in this view. Per-tab Zorvi lifecycle, tabs/history/bookmarks/downloads integration, page permissions/security indicators, renderer crash recovery, and a compatibility switch within each tab remain open. Do not use this prototype as a secure general-purpose daily browser or claim broad website compatibility.


## Phase 10 implementation note (initial accessibility semantics slice)

- Added a bounded semantic snapshot for native HTML headings, links, buttons, form controls, images and navigation/main landmarks.
- Accessible names prefer `aria-label`, `alt`, associated `label[for]`, placeholder, title, then text content.
- Native input types map to text-field, checkbox, radio-button, slider, spin-button and button roles; disabled state and heading level are retained.
- Hidden subtrees, `aria-hidden=true`, and hidden inputs are excluded.
- The experimental page view exposes a combined semantic page description through Android's standard View accessibility description.
- Added four JVM tests for landmarks/headings/links, form labels, native control roles/disabled state, and hidden content.
- Fixed the malformed single-quote trim in response charset parsing that blocked Kotlin compilation on the previous branch head.

This is an initial accessibility foundation, not Phase 10 completion. The current renderer exposes one page-level accessibility description rather than individually navigable virtual nodes; TalkBack focus order/actions, editable form widgets and submission/validation, file upload, media playback/captions/fullscreen/autoplay, zoom/text scaling/selection/find-in-page, RTL/locale handling, printing/PDF and device-level accessibility verification remain open. The renderer still does not visually render or interact with most form controls.
