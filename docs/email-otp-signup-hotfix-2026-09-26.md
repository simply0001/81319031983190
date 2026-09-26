# Email OTP sign-up hotfix — 26 September 2026

New email accounts failed at `POST /auth/v1/otp` with a database error because
`guard_password_signups` rejected GoTrue's internally generated temporary
password hash. Existing email OTP sign-ins continued to work. This was an old
username-account guard, not an SMTP/TLS failure.

`20260926000200_email_otp_signup_guard.sql` now clears that hash before storing
non-username accounts. The gateway accepts public `POST /auth/v1/signup` only
for `@users.pocketpass.xyz` username accounts and blocks fallback path variants,
so a caller cannot turn the passwordless email OTP flow into email/password
registration. The Thor/phone APK and public release did not change.

Before deployment, an encrypted backup was taken at
`/var/backups/pocketpass/pocketpass-20260926T160001Z.tar.gz.age` and copied to
`C:\Users\super\Documents\PocketPass-backups\production-backups\`; both SHA-256
checks matched (`11f1ce9f613900f16549940a014ebef95ac7b2f124c54005d7221aae89557adb`).
The previous gateway file is retained root-only at
`/opt/pocketpass/deploy-backups/signup-hotfix-20260926T1602/kong.before.yml`.

The new migration and username-account pgTAP suite passed 16/16 in a
rollback-only transaction before deployment, then 16/16 after deployment.
Kong parsed the updated configuration. Direct real-email/password sign-up was
rejected at the gateway; `/signup/` and other tested path variants were blocked.
An invalid-password username request reached GoTrue's own password validator,
confirming that username sign-up still routes normally without creating a user.
An email OTP request to Resend's documented test recipient returned HTTP 200.
Its temporary account stored an empty password hash and had a profile; the
test account and profile were then removed. Auth and full backend health checks
passed. No real user's email address was used for the probe.
