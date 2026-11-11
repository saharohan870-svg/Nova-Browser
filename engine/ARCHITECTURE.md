# Zorvi Engine Architecture and Engineering Rules

## Current status

This repository contains an experimental independent renderer prototype. The production browser shell still uses Android System WebView. Do not describe the independent engine as a complete or secure browser engine until its release gates are met.

## Logical module boundaries

The initial code remains in one Android Gradle module to keep build and dependency overhead low. Packages and interfaces should preserve these boundaries so extraction into Gradle modules can happen when dependency direction and build time justify it.

| Subsystem | Package / location | Owns |
|---|---|---|
| Document parsing | `com.zorvi.engine` parser files | HTML tokens, tree construction, source limits |
| DOM | `ZorviDomDocument` | Nodes, mutation, document queries |
| CSS | `ZorviCssStyleSheet` | CSS parsing, selector matching, computed styles |
| Layout | Planned `com.zorvi.engine.layout` | Geometry, formatting contexts, hit testing |
| Script | `ZorviScriptRuntime` | JS runtime adapter, scheduling, safe bindings |
| Networking | Planned `com.zorvi.engine.net` | Requests, redirects, resource loading |
| Graphics | Planned `com.zorvi.engine.graphics` | Paint records, raster, compositing |
| Android adapter | `ZorviEngineView` | Android input, lifecycle and presentation |
| Browser shell | `com.zorvi.browser` | Tabs, navigation UI, bookmarks, downloads |

## Dependency direction

- Parser produces a document tree; it does not depend on Android Views, networking, CSS, or JavaScript.
- DOM owns mutable document state and emits mutation/invalidation notifications.
- CSS consumes DOM snapshots and produces computed styles.
- Layout consumes DOM and computed styles and produces geometry.
- Script accesses DOM only through an explicit bridge; it must not receive arbitrary Android Context, filesystem, reflection, or network capabilities.
- Networking produces resource responses and does not mutate UI objects.
- Graphics consumes paint/layout output; it does not parse HTML.
- Android view adapts platform input and draws engine output; it should not own standards logic.
- Browser shell talks to the engine through a narrow navigation/rendering API.

## Threading and lifecycle

- Android View methods and presentation state are main-thread confined.
- Network and parsing work must not block the main thread.
- Each page/runtime must have an explicit lifecycle and cancellation path.
- DOM/script concurrency must be serialized or use immutable snapshots; avoid unsynchronized cross-thread tree mutation.
- Detaching a view must cancel outstanding work and release page resources.
- Every queue, cache, document and response body must have a bound or a clear eviction/close policy.

## Resource limits

Initial parser input limit: 2,000,000 UTF-16 code units. This is a defensive prototype limit, not a web standard limit. Limits must be configurable and covered by tests. Add request timeouts, response-size limits, script memory/stack/time limits and bounded caches before exposing the engine to arbitrary browsing.

## Quality gates

- Unit tests for parsing, DOM, CSS, URL and layout logic.
- Android instrumentation tests for lifecycle, touch, rendering and memory.
- Parser fuzz tests and malformed-input regression corpus.
- CI must run unit tests and build the debug APK.
- Track APK size, installed size, cold start, peak memory and frame time.
- Security review before the independent engine is enabled for general browsing.

## Current prototype gaps

No complete HTML5 tree construction, CSS layout engine, standards-compliant DOM/events, browser-grade JS APIs, resource loader, process isolation, storage partitioning, GPU compositor, or complete security model exists yet. Keep these gaps visible in docs and UI.
