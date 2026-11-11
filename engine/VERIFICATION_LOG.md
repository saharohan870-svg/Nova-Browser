# Zorvi Engine Verification Log

## Rule: verify before advancing

Every implementation increment must follow this sequence:

1. Review the last green commit and all existing tests.
2. Add regression tests for the behavior being changed.
3. Run JVM unit tests and the Android debug build in CI.
4. Inspect failures and fix the cause before adding another subsystem.
5. Re-run the full test suite after every fix.
6. Record the exact commit SHA, CI run URL, test count and outcome.
7. Do not mark a phase complete while its required tests are failing or unexecuted.

A later phase must not hide, bypass or weaken a failing earlier test. Keep regression tests permanently.

## Verification history

### 2026-11-11 — initial parser/Phase 0 integration

- First full CI attempt failed during Kotlin compilation because QuickJS artifacts use Kotlin metadata 2.4.0 while the project compiler was Kotlin 2.0.21.
- This occurred before JUnit tests could execute. Therefore the Phase 0 test result at that point is **not run**, not passed.
- Kotlin Gradle plugin was aligned to 2.4.10.
- That exposed a Kotlin DSL migration requirement: the old `kotlinOptions.jvmTarget` setting is now an error. It was migrated to the `compilerOptions` DSL.
- The next compiler pass exposed public/internal type visibility leaks and missing explicit Kotlin coroutines classes. The DOM prototype types were made internal, the public engine event type was exposed consistently, and the Android coroutines dependency was added explicitly.\n- A new CI run is required to verify these fixes and execute the tests.

### HTML parser regression coverage

The parser test suite covers:
- implied html/head/body containers
- quoted and unquoted attributes
- named and numeric character references
- comments and void elements
- raw-text script/style behavior
- RCDATA title behavior
- implied paragraph/list-item closing
- explicit document container attributes
- malformed and oversized input handling

**Current test result:** pending a successful CI run after toolchain fixes.

## Known limitations (not test failures)

The parser remains a deliberately incomplete HTML parser. It does not yet implement the full WHATWG tokenizer/tree-construction algorithms, all named character references, encoding sniffing, foster parenting, adoption agency algorithm, template insertion modes, or comprehensive quirks mode behavior. Do not call it HTML5-conformant until it passes an appropriate conformance suite.

## Required Phase 1 exit evidence

- CI unit tests pass on the exact candidate commit.
- Parser conformance fixture results are recorded.
- Malformed-input fuzz/regression suite is green.
- No regressions in DOM/CSS tests from earlier work.
- The parser API is stable enough for the next DOM milestone.

## Phase 2 — DOM and event model (initial implementation)

- Added simple DOM class lookup and simple-selector query APIs, including selector groups and wildcard handling for element nodes.
- Added basic `innerHTML`/`outerHTML` serialization with escaping for text and attributes and correct void-element handling.
- Added capture/target/bubble dispatch, one-time listeners, listener removal, propagation controls and cancelable-event default prevention.
- Added regression tests for selector lookup, node parent links, cycle prevention, event order, bubbling, once listeners and cancellation.
- CI experiments exposed test/implementation issues: an HTML parser assertion read text from the wrong tree level; the document root was omitted from the event capture path; and the first selector matcher was insufficiently reliable. These were corrected, with stricter selector parsing and regression tests.
- The ten-pass CI experiment was stopped as requested; CI has been returned to one test/build pass per commit.
- **Current candidate:** `6786d5630336697f1360114c57724249065a522b`
- **Verification:** pending CI for this candidate. Do not mark Phase 2 complete yet.
- **Scope note:** selector support is intentionally limited to simple tag, ID, class, combined simple selectors, comma groups and `*`; combinators, attribute selectors and pseudo-classes remain future work.


## Phase 3 — CSS parser and initial cascade integration

- Added `ZorviCssTokenizer` with bounded input and tokens for identifiers, at-keywords, hashes, strings, numbers, dimensions, percentages, whitespace and delimiters. Comments are skipped.
- Added a recovering CSS style-rule parser that scans quoted strings, comments and nested function/bracket delimiters so semicolons and colons inside values do not split declarations.
- The parser currently accepts ordinary style rules and comma-separated selector lists. At-rules are skipped; nested at-rule semantics are not implemented.
- Replaced the stylesheet's brace/declaration regex parsing with parsed rule/declaration data. Existing limited property application and selector matching remain prototype-level.
- Added four CSS parser/tokenizer regression tests.
- **Candidate:** `480d8f6a86088639f109dcb1a3d1def3f44b1fd8` (documentation commit; implementation commits precede it).
- **CI:** pending; no Phase 3 pass is claimed.
- **Blocking earlier result:** Phase 2's selector-group test failed on the last completed recorded run (`43062022982`). The new CSS changes do not resolve or bypass that test. The current candidate must pass the full suite before this increment is considered verified.
- **Known gaps:** no full CSS Syntax specification tokenizer, escaped identifier decoding, selector combinator/attribute/pseudo matching, nested at-rule evaluation, cascade origins/importance/inheritance/custom properties, media queries, computed-value resolution or full CSS value grammar.


## Phase 7 — graphics foundation (parallel, integration deferred)

- Added `ZorviPaintCommand` display-list records for rectangle fills, text and bitmap drawing, plus a small Android Canvas raster backend.
- Added platform-neutral `ZorviRect` geometry to keep display-list records independent of Android Canvas geometry classes.
- Added generic CSS font-family fallback mapping to Android Typeface with bold/italic selection.
- Added bounded bitmap decoding: maximum encoded input 16 MiB, maximum decoded dimensions 16 megapixels, target-size power-of-two sampling, and failure results instead of propagating decode/OOM exceptions.
- Added three unit tests for sampling bounds and immutable paint geometry.
- **Implementation commits:** `f9f1dea2275c24867bafa13f45c4a878d8396e38`, `5924a821aa27cd347ab712eba86384e1017acffa`, `4754984ce9ee0d095930d9b9921c3d08a801823d`, `b45402ad153926bc12440ef6d3f4c1c3caa4ad6f`.
- **Verification:** pending CI. No passing build or test result is claimed.
- **Integration constraint:** Phases 4–6 are being developed in another conversation. This increment deliberately adds isolated graphics primitives and does not modify layout, scripting, Web APIs, networking or the browser view.
- **Known gaps:** no resource loader integration, SVG, web Canvas API, text shaping, glyph runs, clipping/transform stack, image cache, GPU acceleration, damage tracking, layer compositing or broad image-format conformance.
