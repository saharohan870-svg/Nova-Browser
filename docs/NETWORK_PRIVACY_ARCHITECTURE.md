# Nova Browser network privacy architecture

## Product behavior

- **Nova VPN** is a browser-wide, opt-in VPN service, available outside Guest Mode too.
- **Tor in Guest Mode** is a separate route. Starting Guest Mode offers Tor; ending Guest Mode tears down the Tor route and closes guest tabs.
- VPN and Tor must never be represented as active unless their tunnel is established and traffic is confirmed to use it.
- Do not silently fall back from Tor to a direct connection. If Tor fails, Guest Mode must show a clear failure state and offer retry or exit.
- Do not run VPN and Tor simultaneously by default. The product must define an explicit precedence policy before enabling either route.

## VPN implementation

Android's `VpnService` provides the OS tunnel interface, not VPN exit servers. A production service therefore needs:
1. A contracted VPN provider or Nova-operated VPN infrastructure with documented terms, privacy policy, jurisdictions, abuse handling and operational support.
2. A supported client protocol/SDK and server-side credentials/bootstrap API.
3. Android `VpnService` foreground-service lifecycle, user consent via `VpnService.prepare()`, notification, reconnect handling and always-on/lockdown behavior decisions.
4. IPv4 and IPv6 routing, DNS handling, MTU, split-tunnel policy, captive portal behavior, network changes and kill-switch/leak tests.
5. Provider authentication, subscription/usage policy, secure credential storage and account deletion flow.
6. Independent verification that public IP and DNS use the tunnel, including WebRTC and IPv6 leak tests.

The browser UI may be designed before a provider is selected, but it must display **Not configured** and must not offer a fake Connect success state. No server, credentials, provider SDK or VPN endpoint is currently configured in this repository.

## Tor in Guest Mode

Tor is not an HTTP proxy toggle. A production Tor feature needs a maintained Android Tor client/runtime (for example, a vetted embedded Tor implementation), bootstrap/status APIs and a SOCKS or transparent-routing integration that covers every Guest WebView request and DNS lookup. GeckoView must be routed without allowing direct fallback. The integration must handle bootstrap progress, circuit changes, network loss, shutdown and cleanup.

Required tests:
- Verify Guest requests use Tor exit nodes and never the device's direct route.
- Verify DNS does not escape the Tor route.
- Verify WebRTC, IPv6 and non-HTTP traffic cannot leak.
- Verify normal tabs remain on their normal route unless Nova VPN is explicitly enabled.
- Verify ending Guest Mode closes guest tabs and stops the guest Tor runtime.
- Verify Tor bootstrap failure blocks guest navigation rather than silently using direct internet.

## Current status (2026-11-12)

- Existing Guest Mode is a private-session browsing mode only; it does **not** currently use Tor or mask the public IP.
- Existing project is GeckoView-based. Do not assume a browser-level proxy setting can route all Gecko networking.
- VPN provider/backend: **not selected or configured**.
- Tor runtime and GeckoView routing integration: **not implemented**.
- No VPN/Tor security claim may be made until the leak and routing tests above pass on a physical device.

## Implementation research notes
- Guardian Project's maintained `tor-android` library provides an Android TorService and native Tor binaries, but the sample integration demonstrates an Android WebView path, not GeckoView routing. It cannot be treated as an automatic drop-in for this browser.
- GeckoView does not expose a supported public app API for configuring a proxy. A GeckoView issue records Mozilla's guidance to use internal configuration mechanisms, which are not a supported production proxy contract. Therefore, simply starting Tor on localhost:9050 and setting a UI flag would not establish a safe Guest Mode Tor route.
- Nova must use a verified routing layer that covers GeckoView's full network stack, DNS and WebRTC, and fails closed. A separate HTTP fetch interceptor is not sufficient for a full browser session.
- Research references: Guardian Project tor-android README (https://github.com/guardianproject/tor-android); Mozilla GeckoView proxy discussion (https://github.com/mozilla/geckoview/issues/206); Tor Project mobile guidance (https://support.torproject.org/tormobile/).
