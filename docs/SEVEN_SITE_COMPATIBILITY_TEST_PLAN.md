# Nova Browser — Phase 2 Website Compatibility Plan

## Phase 2 objective

Prepare the GeckoView-based browser for modern web applications, with special attention to major AI chat websites. The list below is a test target, not a claim that every site has passed.

## AI website targets

| Website | Official web app | Core compatibility checks |
|---|---|---|
| ChatGPT | https://chatgpt.com/ | Sign-in, chat streaming, attachments, voice UI, long conversations |
| Claude | https://claude.ai/ | Sign-in, streaming, file uploads, artifacts |
| Google Gemini | https://gemini.google.com/ | Google sign-in, streaming, uploads, multimodal UI |
| Microsoft Copilot | https://copilot.microsoft.com/ | Microsoft sign-in, chat streaming, file input |
| Perplexity | https://www.perplexity.ai/ | Sign-in, streamed answers, citations, file input |
| Grok | https://grok.com/ | Sign-in, streaming, media UI |
| DeepSeek | https://chat.deepseek.com/ | Sign-in, long streamed responses, attachments |
| Mistral Le Chat | https://chat.mistral.ai/ | Sign-in, streaming, file input |
| Meta AI | https://www.meta.ai/ | Sign-in, chat and media UI |

## Other major web targets

| Website | Official web app | Core compatibility checks |
|---|---|---|
| Google Search | https://www.google.com/ | Search, sign-in, redirects and cookies |
| YouTube | https://www.youtube.com/ | Playback, controls, fullscreen and media |
| GitHub | https://github.com/ | Repository/code rendering, sign-in and OAuth |
| Instagram | https://www.instagram.com/ | Feed, image/video, scrolling and sign-in |
| Discord | https://discord.com/app | App boot, sign-in, realtime UI and voice entry |
| WhatsApp Web | https://web.whatsapp.com/ | QR rendering and device-link flow |
| Microsoft 365 | https://www.microsoft365.com/ | Sign-in, app launch and Word/Excel editing surface |

## Browser-side compatibility work

- GeckoView is the main page renderer in the current migration branch.
- JavaScript is explicitly enabled for web-app sessions.
- Tracking protection is enabled by default.
- Mobile and desktop user-agent modes are available, with desktop viewport mode configured separately.
- AI and major-site shortcut menus are available in the browser UI.
- Existing upload and camera/microphone permission handlers are intended to support web-app workflows.
- Popup/new-window handling and session persistence are implemented but still require device verification.

## Required test procedure

Use one APK and one device for all sites. Record app version, GeckoView version, Android version, device model, date, and result. Do not put passwords, one-time codes, QR codes, private chats, or private documents into reports.

For each AI site:
1. Load the official HTTPS web app in a fresh normal tab.
2. Check initial app boot, JavaScript execution and responsive layout.
3. Complete sign-in privately; record only whether it succeeded.
4. Send a harmless test prompt and verify that streamed output appears and completes.
5. Test file upload where the site offers it, using a non-sensitive sample.
6. Switch to desktop mode and reload; check whether the desktop layout works.
7. Open a second tab, return to the first, and check that the page/session remains usable.
8. Check browser back/forward, reload, and tab restoration.

For other major sites, run the site-specific checks in the tables above. YouTube requires an actual video playback/fullscreen check. WhatsApp QR codes and linked-device details must never be captured in screenshots or shared.

## Result labels

- **Pass:** all listed checks succeed on the target device.
- **Partial:** the site loads but one or more checks fail or are blocked.
- **Fail:** the site does not reach its basic usable app surface, crashes, or loops.
- **Not run:** no device test has been performed.

## Current status

All 16 websites are **Not run**. The current work adds shortcuts and browser compatibility controls; it does not prove successful login, streaming, voice, media, uploads or app functionality. Automated build verification and all manual site testing remain for the final testing phase.
