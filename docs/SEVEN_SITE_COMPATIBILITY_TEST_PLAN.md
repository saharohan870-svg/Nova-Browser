# Seven-Site Browser Compatibility Test Plan

## Scope

This checklist covers the seven requested compatibility targets on the Android browser:

1. Google — search, account sign-in, redirect/cookie persistence.
2. YouTube — page load, video playback, fullscreen, playback controls.
3. GitHub — repository pages, code rendering, sign-in, OAuth redirects.
4. Instagram — feed load, image/video media, scrolling, sign-in.
5. Discord — web-app boot, JavaScript execution, sign-in, voice UI availability.
6. WhatsApp Web — QR page load, QR rendering, device-link flow.
7. Microsoft 365 — Microsoft sign-in, app launcher, Word/Excel web app startup and editing surface.

## Important engine status

The current default browser screen in this branch is still backed by Android System WebView (android.webkit.WebView). The separate Zorvi experimental page is a custom Kotlin renderer and is not suitable for these modern sites. GeckoView is not yet integrated in the current app module. Therefore, results from this checklist must not be described as GeckoView results.

## Test status

No real-device site test is recorded yet. CI builds and unit tests do not prove that these sites render or authenticate correctly in a browser.

| Site | Required checks | Status |
|---|---|---|
| Google | Search, sign-in, account persistence | Not run on device |
| YouTube | Video, fullscreen, controls | Not run on device |
| GitHub | Repo/code pages, sign-in/OAuth | Not run on device |
| Instagram | Feed, image/video, scrolling | Not run on device |
| Discord | Web app boot, sign-in, voice UI | Not run on device |
| WhatsApp Web | QR rendering, device linking | Not run on device |
| Microsoft 365 | Sign-in, Word/Excel app launch, editing surface | Not run on device |

## Manual run procedure

Use the same APK build and the same Android device for all seven tests. Record the app version, Android version, device model, test date, and any error text.

For each site:
1. Open a fresh normal tab and navigate to the official HTTPS site.
2. Wait for the page's initial load to finish; note blank screens, reload loops, browser crashes, or persistent spinners.
3. Test the site-specific checks listed above.
4. Close and reopen the tab, then verify whether the expected session state persists.
5. Repeat once in a fresh tab if the first run is inconclusive.

Do not enter account passwords or one-time codes into test reports. For WhatsApp Web, do not share the QR code or linked-device session details. For Microsoft 365 and Google, report only whether authentication completed, not credentials or private content.

## Pass criteria

- Pass: all listed site-specific checks work in the target browser on the target device.
- Partial: the site loads but one or more listed features fail or are blocked by the site/device.
- Fail: the page does not load, crashes, loops, or cannot reach its basic app surface.
- Not run: no real-device browser test has been completed.

## Current automated verification

The GitHub Actions Android workflow runs Gradle unit tests, assembles the debug APK, checks that the APK exists and is below the configured size ceiling, and uploads it as an artifact. These checks are build verification only; they do not automate account sign-in, video playback, QR device linking, or Office editing.

## Next required step

Integrate and build the approved GeckoView engine before treating this matrix as the GeckoView compatibility test. Then install that APK on the target Android device and execute the seven site-specific checks. Automated CI can validate compilation and local tests, but authenticated flows and real media playback still require device-level verification.
