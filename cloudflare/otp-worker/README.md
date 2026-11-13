# Mawashi email OTP Worker

This Worker sends six-digit email verification codes and marks the matching Firebase Auth user as verified only after a successful code check.

## Security model

- Requires a Firebase ID token in the Authorization header for both endpoints.
- Uses Firebase Identity Toolkit to validate the caller and obtain the UID/email.
- Stores only an HMAC digest of the OTP in D1 (never the plain code).
- OTP lifetime: 10 minutes; resend cooldown: 60 seconds; maximum guesses: 5.
- Uses a service-account OAuth assertion only on the server to set `emailVerified=true`.
- The Resend shared `onboarding@resend.dev` sender is restricted to `TEST_RECIPIENT_EMAIL`. This is a test-only restriction; do not remove it until a sending domain is verified in Resend.
- No secrets belong in this repository or in the Android app.

## Cloudflare setup

1. Create a D1 database named `mawashi-otp`.
2. In the D1 console, execute `schema.sql`.
3. Copy the database ID into `wrangler.toml`, replacing `REPLACE_WITH_D1_DATABASE_ID`.
4. Add these Worker secrets (Settings → Variables and Secrets):
   - `RESEND_API_KEY`: the Resend API key (Sending access only).
   - `FIREBASE_API_KEY`: the Firebase Web API key from the Mawashi Firebase project.
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: the complete private service-account JSON. Create a dedicated service account with only the permissions needed to manage Firebase Authentication users. Keep this secret private.
   - `OTP_HMAC_SECRET`: a randomly generated secret of at least 32 bytes.
5. Add the plain-text variable `TEST_RECIPIENT_EMAIL` with the exact email address allowed by Resend's shared test sender.
6. Deploy the Worker from this directory using Wrangler, or paste `src/index.js` into the existing Cloudflare Worker editor after the D1 binding and secrets are configured.

## Endpoints

- `GET /health` — basic health check; does not expose configuration.
- `POST /v1/otp/send` — Firebase ID token required; sends a code to the authenticated user's email.
- `POST /v1/otp/verify` — Firebase ID token and JSON body `{"code":"123456"}` required.

Both POST endpoints require `Authorization: Bearer <Firebase ID token>`.

## Important rollout order

Do not switch the Android app away from Firebase email-link verification until this Worker has been deployed, its secrets and D1 binding have been configured, and the send/verify flow has been tested end-to-end. The Android client integration is a separate follow-up change.

## Git-based deployment

Cloudflare Workers builds are configured to deploy this Worker from the `develop/final-package-id` branch, using this directory as the project root and `npx wrangler deploy` as the deploy command. A commit to this branch triggers the configured build.
