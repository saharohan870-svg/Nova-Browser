# Zorvi Engine Language Architecture

Status: Accepted direction; migration is incremental and gated by tests.

## Decision

Keep Nova Browser's Android application shell in Kotlin. Move performance-sensitive, platform-neutral engine subsystems to Rust gradually, behind a narrow engine interface. Do not rewrite the whole Android application in Rust.

## Responsibility split

### Kotlin / Android
- Activities, views, tab UI, menus, settings and browser chrome.
- Android lifecycle, permissions, downloads, file chooser and intents.
- Accessibility integration and Android-specific input/output.
- JNI loading, error reporting and lifecycle coordination.

### Rust / engine core (target architecture)
- URL parsing and canonicalization.
- HTML tokenization/tree construction and DOM data structures.
- CSS tokenization, parsing, cascade and computed styles.
- Layout tree and geometry calculations.
- Resource scheduling/cache policy where platform-independent.
- Paint display-list generation and hit-test geometry.

### Runtime boundary
- Keep JavaScript behind a replaceable runtime adapter. QuickJS is currently embedded through the Android dependency; do not assume it should be rewritten in Rust.
- Expose only explicit, bounded browser APIs to page scripts. Never expose arbitrary JNI, filesystem or Android objects to untrusted content.

## Migration order

1. Establish Rust workspace and Android ARM64 build in CI without changing runtime behavior.
2. Port URL parsing and canonicalization; run the same fixtures against Kotlin and Rust.
3. Port HTML tokenizer/tree builder and compare serialized trees.
4. Port CSS parsing/cascade and compare computed-style fixtures.
5. Port layout calculations and compare geometry fixtures.
6. Port paint-list/hit-test generation; keep Android Canvas as the initial compositor.
7. Add resource scheduling/cache pieces only after parity tests.
8. Remove Kotlin implementations only when the Rust implementation passes parity, fuzz, performance and memory gates.

## JNI/API rules

- Prefer a small set of coarse-grained calls per navigation/frame, not one JNI call per DOM node or text run.
- Pass UTF-8 buffers and compact serialized/typed structures; avoid repeated object conversion.
- Give every page/document a stable native handle with explicit create/destroy ownership.
- Never let Rust panic unwind across JNI. Convert errors to typed results.
- Bound input sizes, allocations, recursion, task queues and execution time.
- Cancellation and page teardown must invalidate outstanding work before native resources are freed.
- Keep native handles inaccessible to page JavaScript.

## Build and compatibility

- Target Android arm64-v8a first; add other ABIs only when tested.
- Pin Rust toolchain and crate versions. Audit licenses and native dependencies.
- Use Android NDK plus cargo-ndk (or an equivalent reproducible Gradle integration) in CI.
- Keep Kotlin/JVM unit tests and add Rust unit, integration, fuzz and differential tests.
- Record APK size, native library size, peak RSS, cold start, page load and frame-time impact before/after each port.

## Exit gates for each subsystem

A subsystem is eligible to migrate only when:
- Its public behavior is documented and covered by fixtures.
- Kotlin and Rust implementations produce equivalent results over the fixture corpus.
- Rust fuzz tests show no crashes or unbounded resource growth in the defined run.
- CI builds the relevant Android ABI reproducibly.
- Device testing confirms no unacceptable startup, memory or rendering regression.

## Current status

The current Zorvi implementation is Kotlin and uses Android Canvas for drawing. This document records the recommended future architecture; it does not claim that any Rust code, JNI bridge or native library has been implemented. The first Rust port should be a small platform-neutral subsystem, not the entire engine at once.
