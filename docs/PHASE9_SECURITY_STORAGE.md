# Phase 9 — Security, privacy and storage (initial foundation)

## Implemented in this increment

- HTTP(S) tuple-origin representation with effective default ports.
- Fail-closed same-origin checks for document reads and native-bridge eligibility.
- Storage partition keys containing both top-level origin and requesting origin.
- A bounded, synchronized, memory-only key/value store with get, set, remove,
  clear-origin and clear-all operations.
- Per-origin quota enforcement (default 256 Ki UTF-16 characters).
- Unit tests for origin comparisons, bridge boundary checks, partition isolation,
  same-origin sharing, quota behavior and clearing.
- An explicit private-session owner that keeps partitioned storage and an HTTP memory
  cache in memory, clears both on close, rejects use after close, and supports idempotent
  destruction. Five lifecycle tests cover cleanup and closed-session behavior.

## Explicit limitations

This is not a browser-grade security implementation and does not complete Phase 9.
The store is volatile for every session; durable localStorage/sessionStorage,
IndexedDB, Cache API, cookie semantics, eviction policy and Clear-Site-Data remain
unimplemented. No page JavaScript bindings are exposed for this store.

The origin model supports only the HTTP(S) tuple origins accepted by the current
ZorviUrl parser. Opaque origins, IDNA/public-suffix site computation, blob/data
origin inheritance, sandboxed iframe origins and file origins are not implemented.
The partition key intentionally uses the explicit top-level origin rather than
guessing an eTLD+1 site boundary.

No process isolation, CSP enforcement, sandboxed frames, permission policy,
Safe Browsing service, download reputation checks or renderer/native IPC boundary
is implemented by this increment. The current QuickJS bridge must remain restricted
and must not expose Android Context, filesystem, reflection, secrets or networking.

## Required next steps

1. Integrate origin assignment with committed navigation and redirects.
2. Make every resource request pass through a single policy-enforcing loader.
3. Add cookie parsing/storage with Secure, HttpOnly, SameSite and partition rules.
4. Add persistent non-private storage with encryption/Android Keystore review.
5. Integrate the private-session owner with private tabs, the resource loader and browser
   lifecycle so the final private tab closing reliably calls close().
6. Implement CSP and sandbox policy before iframe/script execution expands.
7. Threat-model renderer/bridge boundaries and document Android process-isolation limits.
8. Run cross-origin, redirect, cookie and private-session regression suites.

## Verification gate

The new ZorviSecurityStorageTest suite must pass together with the complete existing
JVM suite and Android debug build. A CI run is not considered green until its final
conclusion is successful.
