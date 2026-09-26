# Release approval

The user requested on 19 September 2026 that every further publication requires
their explicit approval again. Ask before publishing a GitHub release, replacing
a published APK, changing the release feed, or making a new deployment.

On 22 September 2026, the user explicitly authorized deployment of the
Board-profile/Block Messages friend-request change and installation on the AYN
Thor, and explicitly said **no alpha release**. The database migration and
developer-docs update were deployed; the signed APK was installed only on Thor.
That authorization does not cover a later publication or deployment.

On 23 September 2026, the user gave standing permission to install future
PocketPass builds directly on their connected AYN Thor without asking each
time. This supersedes earlier instructions to obtain fresh approval for each
Thor installation. It does not authorize server deployment, publishing an
alpha or GitHub release, replacing a published APK, or changing the update
feed; those actions still need fresh approval.

Later on 23 September 2026, the user explicitly requested a new release with
their supplied notes. That authorizes publication of `v0.1.11-alpha` and its
normal update-feed propagation only; it is not standing approval for future
releases or deployments.

The user then explicitly asked to change that release to `0.2.0-beta` and
supplied replacement release notes. This authorizes publishing the beta update
and its normal update-feed propagation. Build 26 is required so devices on
build 25 receive the new version. This is not standing approval for subsequent
releases or deployments.

On 26 September 2026, the user explicitly requested a new public release with
the notes stored in `releases/0.2.1-beta.md`. They separately approved deploying
the Block Invites migration and updated privacy notice, and then approved the
email-privacy migration and SMTP/TLS configuration before publication. These
approvals cover this release and these prerequisites, not future deployments or
releases.

Later on 26 September 2026, after reporting that new email sign-ups returned
"PocketPass sign in is temporarily unavailable," the user explicitly approved
deploying the focused email-OTP sign-up hotfix after testing and a fresh
encrypted backup. That approval covers the gateway sign-up restriction and
`20260926000200_email_otp_signup_guard.sql`, not another public app release.
