# Phase 1 — Nova VPN implementation plan

Status: **blocked on VPN service provisioning; no tunnel is implemented**

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

## Current blocker — one decision / external input

**A real VPN operator must be selected and provide integration access.** No provider, server fleet, credentials, SDK or endpoint is currently available in this project. This cannot be solved by adding Android permissions or writing a placeholder service.

Once provider access is available, implementation can continue against its actual SDK/API. Until then, keep the feature visibly Not configured and do not advertise Nova VPN as available.

## Release gate

Phase 1 is complete only when a release build connects to real provider gateways, shows truthful connection state, passes the routing/leak tests above on a physical Android device, and includes accurate provider/privacy disclosures. A successful compile or CI run alone is not evidence of VPN protection.


## Provider candidates researched (not approved or contacted)

These are integration leads, not endorsements. Public partner pages do not establish that Nova has been accepted, that pricing is suitable, or that SDK access is available to an individual project.

| Candidate | Publicly described option | Important caveat |
|---|---|---|
| AdGuard VPN | White-label VPN SDK and provider infrastructure: https://adguardpartner.com/en/white-label-vpn.html | Requires a partner enquiry and commercial agreement; SDK docs/access are not public. |
| KeepSolid VPN Unlimited OEM | VPN SDK/API and white-label infrastructure: https://oem.vpnunlimited.com/ | Requires provider onboarding and commercial terms. |
| WLVPN / VIPRE | VPN SDKs and reseller API: https://wlvpn.com/ | Requires partner access and credentials. |
| Windscribe | Backend API integration or reseller partnership: https://windscribe.com/white-label-vpn | Windscribe states it does not provide an SDK; API integration is a longer engineering path. |

**Preliminary engineering fit:** AdGuard, KeepSolid and WLVPN publicly describe SDK-style integration, which is closer to Nova's desired in-app experience. No candidate is selected. Compare written quotes, SDK documentation, Android sample app, service geography, privacy/audit evidence, and minimum commitments before choosing.

