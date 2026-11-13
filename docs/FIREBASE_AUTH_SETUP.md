# Nova email authentication setup

Nova uses Firebase Authentication for real email/password accounts. Firebase handles password hashing, credential verification, session tokens, email verification and password-reset delivery. Nova does not store passwords in SharedPreferences, browser history, or local files.

## 1. Create the Firebase project

1. Open https://console.firebase.google.com/ and create a Firebase project for Nova.
2. In Project settings, add an Android app with package name `com.zorvi.browser`.
3. Open Project settings > General > Your apps and copy:
   - Web API key (the Firebase API key; it identifies the project and is not a service-account secret)
   - App ID (format similar to `1:123456789:android:...`)
   - Project ID
4. Open Authentication > Sign-in method and enable **Email/Password**.
5. In Authentication > Templates, review the email verification and password reset templates. Configure a sender/domain if Firebase requires it for your project.

## 2. Configure Nova

Edit `app/src/main/java/com/zorvi/browser/NovaFirebaseConfig.kt` and set:

- `API_KEY` to the Firebase Web API key
- `APPLICATION_ID` to the Android app's Firebase App ID
- `PROJECT_ID` to the Firebase Project ID

These are client configuration values. Never put Firebase service-account JSON, private keys, admin credentials, or server secrets in the Android app or this repository.

If these values remain blank, Nova intentionally shows an account-setup message and disables authentication rather than pretending to log users in.

## 3. Build and test

1. Build the debug APK from the `chromium-engine-migration` branch.
2. Install it on an Android device with internet access.
3. Create an account with a valid email and a password of at least 8 characters.
4. Open the verification email and verify the address.
5. Sign in, sign out, sign in again, and test Forgot password.
6. Confirm an unverified account is not allowed into the signed-in state; Nova sends a verification link and signs it out.

## What this login currently provides

- Email/password registration and sign-in
- Email verification before a successful signed-in state
- Password reset email
- Firebase-managed authenticated session persistence
- Sign-out
- Friendly handling for common credential, weak-password, duplicate-account and network errors

## What it does not provide yet

- Google or Apple sign-in
- Cross-device synchronization of bookmarks, history, tabs, settings or downloads
- A Nova-hosted user profile database or account dashboard
- Custom backend authorization for cloud features

Authentication proves the user's identity. It does not automatically synchronize browser data or make local browser data available on another device.
