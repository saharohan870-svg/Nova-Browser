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
