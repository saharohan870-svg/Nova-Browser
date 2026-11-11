# Nova Browser — Chromium Engine Migration

Status: migration branch initialized; no engine replacement has been shipped yet.

## Target

Nova Browser should become an Android browser built from a Chromium browser source tree, with Nova branding and its own browser identity. The existing Kotlin/WebView application remains the fallback/reference implementation until a Chromium APK is built and tested.

## Upstream evaluated

- Candidate base: [Cromite](https://github.com/uazo/cromite), an Android Chromium fork.
- Upstream README documents Android ARM64, ARM32 and x86_64 builds, Android 10+, built-in ad blocking/privacy work, and downloadable APK releases.
- Cromite is GPL-3.0. A Nova fork must preserve applicable copyright/license notices, provide the corresponding source and modifications under the applicable GPL terms, and must not imply that Nova is an official Cromite build.
- Upstream build guide: https://github.com/uazo/cromite/blob/master/docs/HOW_TO_BUILD.md
- Upstream patch inventory: https://github.com/uazo/cromite/blob/master/docs/PATCHES.md

## Build reality

Cromite is not an Android Gradle library that can be added to the current WebView app. It is a Chromium source fork with GN/Siso/Ninja build steps and a large source checkout.

The upstream Cromite build guide says its release builds use prebuilt Docker images and a dedicated workflow. The inspected upstream workflow uses custom runner labels (dev/ci), persistent mounted build-output volumes, and upstream-only signing/build secrets. It is not a drop-in GitHub-hosted Actions workflow for this Nova repository.

The official Chromium Android build guide currently specifies a Linux x86-64 build host, at least 8 GB RAM (more than 16 GB recommended), and at least 100 GB free disk. It uses depot_tools, GN and Ninja/Siso. This is far beyond a normal small Android Gradle build job.

## Migration sequence

1. Pin and record the exact Cromite commit, Chromium base version, patch list and license notices.
2. Create a Nova downstream source fork/overlay; change application label, package/application identity, icons, colors and browser surfaces without removing upstream legal notices.
3. Preserve the Chromium security sandbox and upstream security defaults. Do not disable web security to make custom features easier.
4. Implement Nova-specific UI/features in the Chromium Android browser layer: search engine selector, tab groups, bookmarks/history, downloads, private profiles, dark theme and AI entry point.
5. Build ARM64 first (the target device family), then test install/launch, browsing, downloads, media, permissions, private-profile data isolation, ad blocking and update behavior on physical Android.
6. Publish a debug APK only after a successful reproducible build. A production APK requires a Nova-controlled signing key and secure release process.

## Important distinction

The existing Nova v0.3.0 app uses Android System WebView. WebView is Chromium-based, but it does not mean Nova bundles and controls a standalone Chromium engine. Do not describe that build as a fully independent Chromium browser.

## Current branch scope

This branch records the selected technical direction and constraints. It deliberately does not replace the working app or import a multi-hundred-gigabyte Chromium source tree into this small Android app repository. The next implementation step is to establish a reproducible build environment for the pinned Cromite source and then begin Nova branding/customization in that source tree.
