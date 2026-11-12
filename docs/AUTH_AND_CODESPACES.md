# Zorvi sign-in and personal Codespaces design

## Product decision

Zorvi uses **Option B**:
- Web pages render locally in the Android browser engine.
- Google and GitHub identities are both required before cloud features are enabled.
- The signed-in user's own GitHub Codespace is used only for explicitly supported cloud features (initially the AI assistant).
- Ordinary browsing requests, cookies, passwords, page contents and navigation are never proxied through Codespaces by default.
- If the Codespace is stopped, unavailable or not authorized, local browsing continues and cloud features show an unavailable state.

## Identity linking

1. Authenticate the user with Google OpenID Connect using Authorization Code + PKCE and a verified ID token.
2. Authenticate the same app user with GitHub using a GitHub App user authorization flow. Request the minimum permissions needed; do not request broad repository access merely to sign in.
3. Link the two provider identities to one Zorvi account on a small HTTPS backend. Use stable provider subject/user IDs, not email equality, as the account-linking key.
4. Require explicit user confirmation before linking accounts if an identity is already associated with another Zorvi account.
5. Never collect Google or GitHub passwords in Zorvi. Never embed OAuth client secrets, GitHub App private keys, or long-lived access tokens in the APK.

## Codespaces ownership and lifecycle

- A Codespace must be created/selected under the user's own GitHub account and repository, with explicit consent and a clear estimate/notice of possible GitHub charges.
- Do not create, start, rebuild, or keep a Codespace running without user consent.
- Store short-lived tokens server-side in an encrypted secret store; use least-privilege access and rotate/revoke credentials.
- Prefer the GitHub Codespaces API and a documented, versioned Zorvi agent service inside the Codespace. Do not expose an unauthenticated forwarded port.
- Authenticate every agent request with a short-lived, audience-bound session token. Validate user, Codespace ID, allowed operation, request size and rate limits.
- Do not use a Codespace as a general-purpose HTTP proxy or silently send normal browser traffic/page contents to it.
- Show Codespace state (not created, starting, ready, stopped, unavailable), and provide explicit Start / Stop / Disconnect controls.
- Provide an idle timeout and a Stop action. Explain that billing and quotas are controlled by GitHub and depend on the user's plan and usage.

## Initial cloud feature boundary

The first Codespace integration should support only a narrow AI-assistant API:
- user submits a prompt explicitly;
- page context is excluded by default;
- if the user chooses “include this page”, show exactly what content will be sent and ask for confirmation;
- the agent returns text, not arbitrary commands to the Android app;
- no access to browser cookies, passwords, local files or other tabs.

## Android implementation sequence

1. Establish a Zorvi backend and register Google OIDC + GitHub App credentials.
2. Implement provider sign-in with PKCE / secure system browser flows and verified callback handling.
3. Implement account linking, logout, token revocation and account deletion.
4. Implement Codespace discovery/selection and consent screen.
5. Implement start/status/stop lifecycle calls and a health-checked agent connection.
6. Add the AI assistant as the first opt-in cloud feature.
7. Add security tests: callback spoofing, token leakage, account mix-up, cross-user Codespace access, replay, expired tokens, stopped Codespace, and network failure.

## Current blockers

- No Google OAuth client ID, GitHub App registration, backend URL, backend deployment, or user-approved Codespace is configured in this repository.
- A production OAuth flow cannot be made functional by hardcoding placeholder credentials in the app.
- A GitHub repository connection is not a user's Codespaces authorization and cannot create or operate their personal Codespace on their behalf.
- Until the backend and provider registrations exist, the app must not claim that dual sign-in or Codespaces integration is active.
