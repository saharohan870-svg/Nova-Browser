# Nova Browser — self-configured WireGuard VPN

Status: app-side integration committed; awaiting CI and physical-device validation. A real VPN connection requires a valid server profile.

## Product decision

Nova uses the official WireGuard Android tunnel library, not a commercial VPN provider account. There is no Nova account, AdGuard login, subscription screen or provider partner login in this approach.

WireGuard is a VPN protocol and client implementation, not a free VPN server network. Nova does not provide VPN exit servers. The user must obtain a standard WireGuard `.conf` profile from a server they control or a VPN provider they independently trust. No free public server is bundled or recommended.

## Code added

- Dependency: `com.wireguard.android:tunnel:1.0.20260102` from Maven Central.
- Uses WireGuard's userspace `GoBackend`, which does not require root.
- Imports a standard WireGuard config via Android's document picker.
- Parses and validates the config before saving.
- Encrypts the full profile, including its private key, with AES-GCM using an Android Keystore key.
- Provides Connect / Disconnect / Import / Remove profile controls.
- Requests Android VPN consent before starting the tunnel.
- Runs tunnel state changes off the UI thread.
- Does not display Connected unless the backend returns `Tunnel.State.UP`.

## Critical routing limitation

The standard WireGuard Android backend builds a device VPN. A config with `AllowedIPs = 0.0.0.0/0, ::/0` will typically route all device traffic, not just Nova Browser. The UI warns about this. This implementation does **not** claim browser-only routing. Restricting the VPN to Nova alone would require a supported per-app allowlist integration or a separate GeckoView proxy design and additional DNS/WebRTC leak validation.

The configuration controls its own AllowedIPs and DNS. Users must understand the profile's routing before enabling it. No automatic kill switch / Android lockdown is enabled by this initial implementation; the UI must not claim otherwise.

## User setup

1. Obtain a WireGuard `.conf` profile from a server you control or a provider you trust.
2. In Nova, open VPN → Import .conf and select the file.
3. Tap Connect and approve Android's VPN permission.
4. Disconnect from the same Nova VPN dialog.
5. Remove profile to delete the encrypted saved profile.

The profile includes a private key. Nova stores it encrypted in Android Keystore-backed storage. Do not share the profile publicly or upload it to GitHub.

## Remaining release gates

- CI compile and unit tests must pass.
- Verify the WireGuard AAR manifest merge includes `GoBackend.VpnService` with `BIND_VPN_SERVICE`.
- Test profile import and malformed profile rejection.
- Test connect/disconnect with a real reachable WireGuard server on a physical Android device.
- Verify public IPv4/IPv6, DNS routing, WebRTC behavior, network handover, app process death and permission revocation.
- Decide whether device-wide routing is acceptable. If the product requirement remains browser-only VPN, this implementation is not sufficient for release.
- Verify privacy disclosures and provide a clear warning that traffic is routed through the configured server operator.

## Upstream references

- WireGuard Android official repository and embedding instructions: https://github.com/WireGuard/wireguard-android
- WireGuard Android tunnel library on Maven Central: https://central.sonatype.com/artifact/com.wireguard.android/tunnel
- WireGuard project: https://www.wireguard.com/