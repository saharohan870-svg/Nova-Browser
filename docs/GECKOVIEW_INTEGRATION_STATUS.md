# GeckoView Integration

The normal Nova Browser tab renderer is now Mozilla GeckoView. Each browser tab owns a GeckoSession attached to a GeckoView. The app uses one process-wide GeckoRuntime.

## Implemented in this integration
- Mozilla Maven repository and GeckoView dependency.
- MainActivity creates GeckoRuntime once and opens GeckoSession instances.
- Tab creation, switching, closing, reload, back and forward use GeckoSession.
- Navigation updates the address field and normal browsing history.
- GeckoSessionSettings enables Gecko private mode for private tabs.
- Gecko tracking protection is enabled for sessions.
- Existing Zorvi Kotlin renderer remains available as an explicitly experimental route.

## Not yet complete
- Real-device validation and the seven-site compatibility matrix.
- Gecko permission prompts (camera, microphone, location, notifications).
- File chooser and download manager integration.
- Fullscreen video/window management and picture-in-picture.
- Session restore, persistent tab groups and a production-grade private browsing UX.
- Cookie/storage isolation verification across normal and private sessions.
- Automated browser UI/instrumentation tests.

Do not describe the integration as production-ready until these items are addressed and the app is exercised on physical devices.
