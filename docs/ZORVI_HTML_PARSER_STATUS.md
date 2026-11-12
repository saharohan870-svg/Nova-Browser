# Zorvi HTML parser status

The parser in `app/src/main/java/com/zorvi/engine/ZorviHtmlDocument.kt` is a bounded, lightweight HTML tokenizer/tree builder. It is an evolving engine component, not a complete WHATWG HTML implementation.

## Implemented

- Implied `html`, `head`, and `body` containers, including recovery after explicit document end tags.
- Quoted and unquoted attributes, duplicate-attribute handling, and character-reference decoding.
- Quote-aware tag/declaration scanning and comment skipping.
- Void elements, script/style raw text, title/textarea RCDATA, legacy raw-text elements, and plaintext.
- Common implied end tags for paragraphs, lists, descriptions, options, headings, table rows/cells/sections, and ruby annotations.
- Scoped implied-end-tag handling to avoid closing outer list items or table cells across nested structures.
- Implied `tbody`, `tr`, and `colgroup` insertion for common table input.
- HTML handling of the trailing slash on non-void elements, plus common nested-anchor/button recovery.
- Initial newline removal for `pre`, `listing`, and `textarea`.
- Common named entities, numeric references, and Windows-1252 numeric-reference remapping.
- Source-size and tree-depth limits, reported through `HtmlDocument.sourceTruncated` and `HtmlDocument.nestingLimitReached`.

## Current limits

This is not yet full HTML5 parsing. In particular, it does not implement the complete named-character-reference catalogue, all tokenizer states, the full insertion-mode state machine, foster parenting, adoption-agency formatting reconstruction, template contents, foreign-content namespaces, or every table/select recovery rule. Script escaped/double-escaped states and encoding sniffing are also incomplete.

The browser networking layer must continue to choose and decode the response character set before passing text to this parser. The parser itself accepts a Kotlin `String`.

## Tests

Run the Android module's unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

The parser tests include regression fixtures for malformed input, nested lists/tables, implied containers, raw-text elements, entities, resource limits, and deterministic malformed-markup fuzz samples and table/interactive-element recovery.
