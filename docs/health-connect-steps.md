# Health Connect step rewards

On Android, Step Rewards asks for read-only Health Connect Steps access when the
feature is enabled and Health Connect is available. If access is unavailable or
declined, the existing `TYPE_STEP_COUNTER` ledger remains the fallback. On iOS,
the existing Core Motion pedometer remains unchanged.

Only automatically or actively recorded Health Connect records with device
metadata are eligible. Manual and unknown-method records are excluded. The
reader uses local-midnight-to-now records, rejects future/cross-midnight and
implausibly dense intervals, and treats overlapping imports as one interval so
different apps cannot double-count the same walk. Raw Health Connect records,
device metadata, and origin app names are not sent to the server; the server
continues to receive only the daily total for its existing capped reward rule.

Health Connect's recording method and device metadata are supplied by the
writing app. An app that deliberately mislabels fabricated records cannot be
distinguished from a real sensor by this API. This is a best-effort exclusion
of ordinary manual additions, not tamper-proof proof of physical walking.

Foreground reads refresh the displayed total each minute. Health Connect
background-read permission is intentionally not requested; background work
uses the phone sensor when permitted, and otherwise catches up the next time
PocketPass is opened. Users may revoke access in Android's Health Connect
settings. A denied Health Connect prompt is not repeated automatically; the
phone sensor is offered instead.

Before a public Android release, deploy the matching privacy-policy source
update and declare the Health Connect `READ_STEPS` use in the Play Console.
The local source change alone does not publish the policy.
