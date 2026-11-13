# Nova Browser engine architecture

Nova has one page-rendering engine: Mozilla GeckoView.

## Runtime model
- Each tab owns a GeckoSession attached to a GeckoView; one GeckoRuntime is shared.
- Private tabs use Gecko private-mode sessions and must stay out of normal session restore.
- GeckoView owns HTML/CSS layout, JavaScript execution, HTTP/HTTPS, TLS validation, redirects, cookies, web storage, image/font/media loading, graphics compositing and web-platform accessibility.
- Nova owns browser chrome and policy: tabs/groups, history, bookmarks, downloads, private/guest lifecycle, permissions, tracking protection, ad blocking, search, reader mode, translation, AI handoff and VPN configuration UI.

## Consolidation from Zorvi
The standalone ZorviPageActivity and ZorviEngineView route are retired. The prototype HTML/CSS parser, DOM, layout engine, QuickJS runtime, graphics renderer, HTTP/resource loader, URL implementation and private-session storage prototype duplicated incomplete versions of functionality GeckoView already provides. These components are not shipped as a parallel renderer. Their useful requirements remain Nova integration requirements: bounded resource handling, platform TLS validation, redirect safety, cache policy, origin isolation, private-session cleanup, accessible page semantics and regression testing. Do not expose a custom native JavaScript bridge.

## Security boundaries
- Never weaken GeckoView certificate validation.
- Never expose app filesystem, Android Context, credentials or arbitrary native methods to page scripts.
- AI page-content sharing remains explicit and user initiated.
- Private/guest URLs and content stay out of persistent history, tab restore and recently closed tabs.
- Verify cookie/storage isolation using GeckoView's supported private-session model.
- Ad blocking and tracking protection are layered controls, not guarantees.

## Verification still required
- Install and exercise the ARM64 APK on the target Android device.
- Test private vs normal cookies/storage, downloads, file chooser, permission prompts, fullscreen video and PiP.
- Run the seven-site compatibility matrix including sign-in, media playback, uploads, streaming and AI web apps.
- Verify crash recovery, low-memory behavior, tab restoration and filter-list refresh.
- Measure page-load performance and memory with realistic multi-tab workloads.

GeckoView is the only engine in Nova. This is not a Chromium/Blink/V8 build.
