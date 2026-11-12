# Phase 1 — Nova VPN implementation plan

Status: **AdGuard selected; blocked on partner approval and SDK/API provisioning; no tunnel is implemented**

Branch: `chromium-engine-migration`
Last reviewed: 2026-11-12

## Product scope

Phase 1 means a real, opt-in VPN for Nova Browser's normal browsing sessions. The preferred initial scope is **Nova-only app traffic**, not every app on the phone. This keeps the product promise clear and avoids silently routing unrelated apps through Nova. The final scope must be confirmed against the selected provider's SDK and Android routing support.

The UI must never show Connected unless:
1. Android has granted this app VPN consent.
2. A provider tunnel has authenticated and established successfully.
3. The tunnel is carrying Nova's traffic.
4. The tunnel has valid DNS and IP routing.

A local TUN interface alone is not a VPN connection. Android's `VpnService` supplies the virtual interface; a VPN client must still encrypt, exchange and route packets to a real remote gateway.

## Work completed in this repository

- Added a Nova VPN entry point and honest Not configured status.
- Existing UI explicitly warns that Nova currently has no provider, server, credentials or tunnel SDK.
- Documented the required Android lifecycle and leak-test requirements.
- Latest status UI commit: `20c49eac81cabe666ac0e4e0ee8390e7fe201d81`.
- CI for that commit passed: run `43235055467`.

These are groundwork only. They do not establish a VPN connection.

## Required implementation sequence

### A. Provider provisioning — external prerequisite

Choose one:
- **Provider partnership / SDK:** provider supplies a supported Android SDK or embeddable client, authenticated account flow, gateway discovery and service credentials.
- **Nova-operated service:** provision and operate VPN gateways, authentication/control plane, monitoring, abuse handling, privacy policy, support and capacity.

Do not ship hard-coded shared credentials, a public config file, a demo endpoint, or a tunnel with no remote gateway.

Provider due diligence must cover:
- Company/operator identity and jurisdiction.
- Privacy policy, retention, logging and independent audits.
- Supported Android integration, protocols and maintenance commitment.
- Gateway locations, capacity, DNS, IPv6 and failover.
- Cost, quotas, abuse response, uptime and support.
- Terms allowing integration and redistribution in Nova.

### B. Android integration

After a provider is selected:
- Add the provider's official SDK/client dependency and pin a reviewed version.
- Implement a dedicated `VpnService` integration only if required by that SDK.
- Call `VpnService.prepare()` and obtain explicit Android user consent.
- Declare the service with `android.permission.BIND_VPN_SERVICE` and the `android.net.VpnService` action where applicable.
- Run a compliant foreground service and persistent notification.
- Store provider credentials using Android Keystore-backed storage; never commit credentials.
- Route only Nova's package unless product scope is deliberately changed.
- Handle revoke, disconnect, app process death, network handover, captive portals and provider errors.
- Track states: Not configured, Disconnected, Preparing, Connecting, Connected, Reconnecting and Error.
- Keep the UI disconnected unless the SDK confirms the tunnel is established and validated.

### C. Traffic protection

- Route IPv4 and IPv6 intentionally; no accidental family bypass.
- Route DNS through provider-approved resolvers inside the tunnel.
- Protect the VPN transport socket from being recursively routed into its own tunnel.
- Define MTU and packet handling using provider guidance.
- Do not silently fall back to direct traffic after a tunnel failure.
- Make kill-switch behavior explicit; do not claim Android lockdown unless actually configured and tested.
- Do not run Nova VPN and Guest Tor simultaneously until routing precedence is designed and tested.

### D. Validation before release

Automated / integration tests:
- Consent granted, denied and revoked.
- Authentication failure, invalid credentials and server unavailable.
- Connect/disconnect/reconnect lifecycle.
- Wi-Fi to mobile data and mobile data to Wi-Fi handover.
- IPv4 and IPv6 public-IP verification.
- DNS leak verification.
- WebRTC leak verification in GeckoView.
- Tunnel process death and Android service recreation.
- Confirm normal browsing cannot silently use the direct route when protection is shown as active.
- Confirm Private Mode and Guest Mode do not accidentally inherit an undocumented route.

Physical-device tests are mandatory before marking the feature production-ready.

## Selected provider: AdGuard VPN white-label SDK

AdGuard is the selected provider for Phase 1. Its public partner material describes an Android VPN SDK, provider-operated server infrastructure, a kill switch, custom DNS, website/app exclusions, and a server-side API for provisioning access tokens and reporting usage. The public material does not grant Nova SDK access or credentials.

Official entry points:
- White-label VPN: https://adguardpartner.com/en/white-label-vpn.html
- Technology partnership / VPN SDK: https://adguard.com/en/partners/technology.html
- Current SDK and provisioning details must be obtained directly from AdGuard after partner onboarding.

### Required from AdGuard before SDK integration

1. Written acceptance of Nova Browser as a technology/white-label partner.
2. Android VPN SDK package/repository access, exact supported SDK version, sample app and integration documentation.
3. SDK license and redistribution terms covering Nova Browser.
4. Partner/project identifier and any client-side configuration values.
5. Server-side API credentials and provisioning documentation for issuing/revoking per-user VPN access tokens. These must be held by a backend, not embedded as shared secrets in the APK.
6. Test account/token, test gateway access and a documented way to verify tunnel state and usage.
7. Commercial terms: setup fee, minimum commitment, per-active-user or revenue-share pricing, free-tier limits, supported countries/locations and support SLA.
8. Written privacy/data-processing terms, logging/retention policy, subprocessors, jurisdiction, incident handling and user-facing disclosure requirements.

### Integration plan once access is granted

- Review the SDK sample and license; pin the exact SDK version and verify minSdk 26 / targetSdk 35 compatibility.
- Implement an AdGuard-backed provider adapter; keep provider-specific types out of browser UI.
- Add a backend token-provisioning endpoint. Never place partner API secrets in the Android app, GitHub, or a public config file.
- Integrate the provider SDK's Android tunnel lifecycle, consent flow, foreground notification, connection callbacks, DNS and kill-switch controls exactly as documented by AdGuard.
- Initially route Nova Browser traffic only if the SDK supports reliable per-app routing. Otherwise pause and decide whether device-wide VPN is acceptable before implementation.
- Add explicit UI states and only report Connected after provider confirmation and routing validation.
- Test IPv4/IPv6, DNS, WebRTC, reconnect, network handover, kill switch, revoke and app process death on a physical Android device.

### Current external blocker

**AdGuard partner approval and private integration materials are not present in this repository or this chat.** We have not contacted AdGuard, received a quote, accepted terms, or received an SDK, project ID, API credentials, access token, or test gateway. I cannot truthfully implement or test the provider tunnel until those are supplied.

The next external action is to submit the partner enquiry using the request brief in `docs/ADGUARD_PARTNER_REQUEST.md`. Once AdGuard replies, provide the SDK package/documentation and credentials through a secure provider-approved channel. Do not paste production secrets into chat or commit them to GitHub.

Until then, keep Nova VPN visibly Not configured and do not advertise it as available.

## Release gate

Phase 1 is complete only when a release build connects to real provider gateways, shows truthful connection state, passes the routing/leak tests above on a physical Android device, and includes accurate provider/privacy disclosures. A successful compile or CI run alone is not evidence of VPN protection.


## Provider selection record

AdGuard VPN white-label SDK selected by the project owner on 2026-11-12. KeepSolid and WLVPN are no longer active candidates unless AdGuard cannot provide suitable terms or Android integration access.

