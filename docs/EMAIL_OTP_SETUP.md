# Nova email OTP login

The numeric OTP flow uses Firebase Cloud Functions, Firestore and Resend. OTP generation and validation are server-side only.

## Required deployment setup

1. Create or select a Firebase project and create its Firestore database.
2. Upgrade the project to the Blaze billing plan; deployed Cloud Functions require billing. Set budget alerts.
3. Create and verify a sending domain in Resend at https://resend.com/domains.
4. Install Node.js 20 and Firebase CLI. Run firebase login and firebase use --add in the repository.
5. Run npm install and npm run build from the functions directory.
6. Set server-only secrets with Firebase CLI:
   - RESEND_API_KEY
   - OTP_HMAC_SECRET (a random secret of at least 32 bytes)
   - OTP_FROM_EMAIL (a sender address on the verified Resend domain)
7. Deploy with firebase deploy --only functions,firestore:rules.
8. Register the Android app in Firebase App Check and configure Play Integrity before testing. The callable functions enforce App Check.

## OTP protections

- Six-digit cryptographically random codes; 10-minute expiry.
- 60-second resend cooldown; maximum five requests per email per hour.
- Maximum five incorrect verification attempts.
- HMAC digests are stored instead of plaintext OTPs; comparison is constant-time.
- Firestore client access to OTP records is denied.
- Successful verification marks the Firebase Auth email as verified and returns a Firebase custom token.
- Existing Firebase accounts are reused; a new email address creates a passwordless Firebase account.

## Status and remaining integration

This is the server-side foundation only. It is not live until Firebase is configured, Firestore is created, secrets are set and functions are deployed. The Android app must add the Firebase Functions SDK, call requestEmailOtp and verifyEmailOtp, then sign in with FirebaseAuth.signInWithCustomToken. End-to-end delivery must be tested after deployment.

Never commit Resend API keys, HMAC secrets, service-account keys or .env files.