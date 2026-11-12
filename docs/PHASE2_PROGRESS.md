# Phase 2 — Website and AI Compatibility

## Goal

Prepare Nova Browser's GeckoView engine and browser shell for modern web applications, especially major AI assistant websites.

## Implemented in this phase

- Added an **AI Sites** launcher for ChatGPT, Claude, Google Gemini, Microsoft Copilot, Perplexity, Grok, DeepSeek, Mistral Le Chat and Meta AI.
- Added a broader **Sites** launcher containing those AI services plus Google Search, YouTube, GitHub, Instagram, Discord, WhatsApp Web and Microsoft 365.
- Added persistent mobile/desktop mode selection.
- Desktop mode changes both GeckoView's desktop user-agent mode and desktop viewport mode; mobile mode restores mobile settings.
- Explicitly enabled JavaScript in browser sessions.
- Expanded the compatibility plan to cover all 16 websites and the checks required for each.
- Updated README so it accurately describes GeckoView as the browser engine and distinguishes it from the experimental Zorvi renderer.

## Compatibility verification intentionally deferred

The following are not marked complete until the final testing phase:
- Successful sign-in and session persistence for each service.
- AI response streaming and long conversation stability.
- File upload and download behavior inside AI apps.
- Voice, microphone, camera and media behavior.
- OAuth popups and redirects.
- Desktop-mode rendering and responsive layouts.
- Real-device performance, memory use, crashes and battery behavior.

## Status

Phase 2 implementation work is committed to `chromium-engine-migration`. All 16 target websites remain **Not run**. No claim of universal website compatibility is made. See [SEVEN_SITE_COMPATIBILITY_TEST_PLAN.md](SEVEN_SITE_COMPATIBILITY_TEST_PLAN.md) for the full matrix and safe test procedure.
