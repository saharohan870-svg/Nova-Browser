# Phase 6 — Web API implementation notes

## Current increment: URL and URLSearchParams foundation

This increment adds an internal Kotlin implementation for hierarchical HTTP(S)
URLs and ordered query parameters, plus JVM unit tests. It is intentionally not
exposed to page JavaScript yet.

### Implemented in this increment
- Absolute HTTP and HTTPS URL parsing.
- Relative URL resolution against an explicit base URL.
- Normalized origin, protocol, host, path, query and fragment accessors.
- Query parameter append, delete, get, getAll, has and set operations.
- Duplicate parameter preservation and UTF-8 form encoding/decoding.
- Rejection of non-HTTP(S), opaque, malformed and credential-bearing URLs.

### Deliberate limitations
- This is not a complete WHATWG URL implementation.
- IDNA and unusual host parsing, special-scheme edge cases, full percent-encoding
  validation and browser-compatible exception details remain unimplemented.
- No page JavaScript bindings are installed yet.
- No Fetch/XHR, cookies, CORS, storage, workers or History/Location integration
  is claimed by this increment.

### Security and dependency gates
Do not expose Fetch/XHR or persistent storage to untrusted page scripts until the
origin model, same-origin policy, CORS behavior, TLS validation, request
cancellation, storage partitioning and safe native binding design are implemented
and tested. The current renderer is not a secure general-purpose browser.

### Verification
The ZorviUrlTest suite covers parsing, origin calculation, relative resolution,
unsafe-scheme rejection, credentials rejection and query parameter behavior.
The commit must pass the repository's complete CI test/build workflow before
this increment is considered verified.
