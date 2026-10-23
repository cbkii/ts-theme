# BootTrace v3 repair candidate — 23 October 2026

Physical baseline: launcher `c834ce5778aebde9ac4a5b6e476e1e4a78b5320b`,
Auxio `19babc11e8ff7bbbcbcd86cce8d7701b16d903f8`, Maps
`1aaec09a26ac82a41abd3ae4abce24ff5cf1d2bc`. The revised reviewer audit
establishes blank music metadata and failed first-HOME automatic Maps admission.

## Implemented ownership boundaries

- Auxio #281 publishes the actual raw URI/current queue entry before library
  hydration. The launcher still refuses a nonnegative active ID absent from
  the queue; it does not restore arbitrary listener text. Coherent metadata-only
  sessions require UNKNOWN_ID and no conflicting queue. Evidence distinguishes
  queue match, metadata-only, missing ID, torn read, token change, mismatch and
  unavailable access. Selected owner generations and hashed item IDs are logged
  locally under the actual launcher UID, without song paths/titles/raw tokens.
- The eight-second loader is cosmetic. Opt-in preparation has a separate finite
  30-second window; each staged source retains its original bounded 12-second
  maximum lease (or shorter explicit Play deadline). Explicit dismissal,
  foreign UI, screen-off and user departure still cancel authority. Accepted or
  uncertain admissions are never blindly repeated. The expiry of a mask cannot
  cancel the navigation controller's claim or change its presentation goal.
- Media deferral release reconciles Maps immediately. Authenticated enabled
  consent starts the actual admission budget; an existing bootstrap task gets
  bounded setup observations in the same absolute budget, never another cold
  launch. An Android USER_UNLOCKED signal can start a new bounded evidence watch;
  it is not classified as verified ACC. Unknown/disabled consent stays fail-closed.
- Both native and shell focus gates always use `window displays` for focus,
  separately corroborating exact overlay token/title/owner UID/type in `windows`.
  A second display read and a two-second freshness bound reject changed/stale
  evidence. Foreign focus and unknown observation remain distinct refusals.
- Compact peer failures log a safe phase/reason category. A failed observer
  registration cannot suppress a separately authenticated, valid preference
  query. Signature, boot/user, protocol and consent checks remain mandatory.
- NEXT/PREV are sent once and only confirmed when the same selected owner
  publishes a verified item transition. A seek-to-start Previous or a session
  without verifiable item identity can remain DISPATCHED_UNCONFIRMED; it is not
  replayed. PLAY/PAUSE native-state logic is unchanged.
- Navigation placeholders now expose preparation/refusal status and deliberate
  retry. The existing controller remains the sole task/route/window owner.

## Regression evidence and boundaries

The new shell gate tests fail against the original split-dump implementation.
The actual navigation controller test fails against the original loader goal
cancellation / missing deferral-release edge. Production observer → Activity
merge → real metadata view tests include raw URI items, automatic transition,
late old artwork, metadata-only sessions and a phantom queue rejection. The
existing ownership, native-state, listener and AUTO tests remain applicable.
Controlled clocks cover 9/15/25-second readiness and no focus after lease expiry.

The API29 test instrumentation also exercises raw singleton queues. Its optional
`-e observe_live 1` mode only samples the selected real Auxio controller as the
launcher UID; it creates no synthetic session and sends no transport. The test
APK must match the installed target/signature. Do not replace a signed TESTING
installation with a debug signer merely to run it. Actual TS18 launcher-UID
getter proof remains NOT_RUN here; local production traces provide this same
redacted observation in an ordinary installed candidate.

Cold-browser binding/root/token diagnostics classify the next observed failure;
they do not prove that a particular handshake phase caused the old capture.
No speculative service rewrite, root grants, synthetic keys, second player,
protected OEM change, permission bypass or rapid-double-tap redesign was added.

## Physical gate before finalisation

Use all three newly signed exact-head TESTING APKs and record their installed
digests. Run three untouched first-HOME Android boots before recovery taps.
Require current metadata, one-tap Music Play and usable compact Maps without a
HOME excursion. Then exercise natural A→B, pause/resume, Next/Previous, same-title
items, notification/wheel input with explicit provenance, Radio/Music handover,
Maps fullscreen→HOME, permission/call interruption and listener reconnect.
Qualify AUTO versus dedicated ownership with two eligible music apps separately.

Q6 full-power cold boot, Q7 ACC, audio/ducking and app-UID/SAF storage qualification
remain NOT_RUN until physically exercised. Keep #31 draft; green CI is not a
physical acceptance result. No merge, production release or installation.
