# Nova Engine (independent prototype)

Nova Engine is a new Android-native browser engine experiment. It does not embed Chromium, Blink, Gecko, WebKit, or Android System WebView.

## First milestone

The initial implementation is a deliberately small vertical slice:
- HTTP/HTTPS document loading on a worker thread
- HTML tokenisation and a minimal document tree
- Basic block and inline text layout
- Native Android Canvas painting
- Basic link hit testing and navigation callback

This is **not** a standards-complete browser. JavaScript execution, CSS cascade, full HTML5 parsing, forms, media, accessibility tree, origin isolation, TLS policy hardening, and modern web APIs are not implemented yet. It must not be used as a secure general-purpose browser until those milestones are implemented and tested.

## Architecture

- `src/main/java/com/nova/engine/HtmlDocument.kt`: small HTML tokenizer/tree builder
- `src/main/java/com/nova/engine/NovaEngineView.kt`: networking, basic layout, Canvas rendering and link interaction

The code is intentionally dependency-free for this first milestone. Later milestones can evaluate standalone parser/JS libraries without importing an existing browser engine.

## Milestones

1. **M1 – static document slice:** fetch, parse, paint simple HTML, follow links.
2. **M2 – HTML correctness:** robust tokenizer/tree construction, entities, tables, forms and malformed markup tests.
3. **M3 – CSS:** selectors, cascade, box model, inline formatting, positioning and responsive viewport.
4. **M4 – JavaScript:** integrate a standalone ECMAScript runtime and build DOM bindings.
5. **M5 – security/process model:** origin policy, sandboxing, CSP, safe navigation, resource limits and fuzzing.
6. **M6 – compatibility:** Web Platform Tests, HTML/CSS/JS conformance, media and accessibility.

No Chromium-compatibility or performance claim is made. Each milestone needs tests and device validation before it is considered complete.
