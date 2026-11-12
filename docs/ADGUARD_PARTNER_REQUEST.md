# Nova Browser — AdGuard VPN partner enquiry brief

## Project
- Product: Nova Browser
- Platform: Android
- Package/application ID: com.zorvi.browser
- Current Android target: minSdk 26, targetSdk 35, compileSdk 36
- Browser engine: GeckoView
- Requested integration: AdGuard VPN white-label Android SDK embedded in Nova Browser

## Intended product
We want to offer an optional, clearly user-initiated VPN feature branded as Nova VPN and powered by AdGuard's VPN service and infrastructure.

Our initial preference is to route Nova Browser traffic only, if supported reliably by the SDK and Android platform. Please explain whether your SDK supports per-app routing for the host app, or whether it requires a device-wide VPN. We will not promise browser-only routing until this is confirmed.

## Information and access requested
1. Technology-partner eligibility for an independent Android browser project.
2. Android SDK package/repository access, current version, sample app and integration documentation.
3. SDK licensing, redistribution and branding rights.
4. Required Android service declarations, lifecycle requirements, supported Android versions and ABI requirements.
5. Server-side API documentation and credentials flow for secure per-user token provisioning and revocation.
6. Test credentials/tokens, staging gateways and tunnel-state verification methods.
7. Supported server locations, capacity, protocol details, DNS behavior, IPv4/IPv6 support, kill-switch behavior, exclusions and reconnect behavior.
8. Pricing: setup fees, minimum monthly commitment, per-active-user pricing, revenue-share options, free-tier options and usage limits.
9. Privacy/data-processing agreement, logging and retention policy, subprocessors, jurisdiction, audit/security materials and incident-response commitments.
10. Support channels, SDK update policy, compatibility guarantees and service-level commitments.

## Security requirements
- No shared production credentials embedded in the APK.
- Partner API secrets must remain on a backend service.
- Access tokens should be provisioned per user and revocable.
- The app must show Connected only after the SDK confirms an active tunnel.
- We need documented fail-closed / kill-switch semantics and test procedures.
- We require explicit disclosures of provider processing and any data retained.

## Current project status
The Android UI currently has a VPN entry point and an honest Not configured status. No provider SDK, partner account, credentials, tunnel service or VPN endpoint has been integrated. We are seeking the materials needed to implement and test this correctly.

Please advise the onboarding process and the next steps for a technical evaluation.
