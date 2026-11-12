# Reference adoption (WP1)

This is a bounded study of pinned reference source. Each item was studied against something this
launcher actually implements. Nothing was copied verbatim: the launcher code was written
independently, and the references informed design decisions or cross-checked binary contracts.
A donor's privileges (system launcher, platform signature, Magisk module) are never treated as
TS18 authority.

| Reference | Pinned commit | Licence |
|---|---|---|
| vasyl91/FYT-Launcher-Mod | `65fbd59a6bb6ac24196d6630770ab6246014f8bc` | no licence file found; studied only |
| jorgejbz/GTS-Launcher | `7af683f0498820b2d815c10fc97b6346d862209c` | MIT |
| IrvingGuerra/litedash | `981b56fa36999cd66283ca2c2b09e8f447e745b7` | GPL-3.0; studied only, no code taken |
| donnyyu/CleanDash | `5c0e66f704a6067d6c5d9e198423f95f7ffcc87a` | MIT |

## N3 surface, session, host and input: FYT-Launcher-Mod

Files inspected: `app/src/main/java/com/syu/util/WindowHostVdClient.java`, `WindowHostVdDisplay.java`
and `WindowHostActivityView.java`.

**Historical host design (retired locally).** FYT states that a launcher which is not a system launcher cannot start other
apps in an ActivityView without `ACTIVITY_EMBEDDING`, and needs `INJECT_EVENTS` for touches. It
therefore delegates to a separately installed virtual-display host. The initial N3 split followed that
shape:

- the launcher's own private display covers cooperative (embeddable) targets;
- a separate `n3host` skeleton was proposed for generic targets.

That skeleton, client and protocol have since been removed. It never established usable privileges. Only the isolated launcher-owned private-display experiment remains; generic-target embedding/input are not solved. See [navigation](NAVIGATION.md).

**Adopted (lifecycle lessons).**

- Release every display and its callbacks with their owner. FYT's ActivityView TaskStackListener
  leak is the cautionary case.
- Keep the IME on the main display. Private displays have no IME on Android 10.

**Rejected.**

- Hidden `android.app.ActivityView` reflection, because it is system-launcher-only on API 29.
- A Shizuku or Magisk-module host as a default path.
- FYT's dual-pane window host. It manipulates freeform tasks, which is N1/N2 territory and is not
  allowed in N3.

**API 29 note.** Our emulator qualification found that an ordinary caller must already be present
on its private display before Android 10 admits another UID's embeddable Activity there. We
therefore launch through a launcher-owned trampoline, and that is the only adaptation. A display
owner's input forwarder is reached reflectively and fails closed. FYT's own note implies it may be
refused on this platform, in which case N3 reports view-only truthfully.

## Topway command service and illumination: GTS-Launcher

Files inspected: `launcher/app/src/main/java/com/jajua/gts/TwCommand.java` and `TwLights.java`.

**Cross-check, adopted.** GTS uses the same command-service transaction codes and source values
as `oem/TopwayContract`:

- register 1, unregister 2, extended interface 67, extended callback 8;
- sources: radio 1, music 3, Bluetooth 8, video 9.

GTS's evidence is from another firmware (TS10.2.2). It corroborates, but does not replace, the
exact TS18 APK hash gate.

**Rejected for now.**

- `TwLights` reads illumination by opening `TWUtil` and writing a request `write(516, 255)` from an
  ordinary app. That is a direct TWUtil write on donor firmware, with caller authority unproven on
  the TS18, so illumination stays `UNKNOWN`.
- GTS's media key codes (55–58). These are mutations and stay out, as does the music callback.

## Topway radio: litedash

File inspected: `feature/music/src/main/java/com/irvingguerra/litedash/feature/music/TopWayRadio.kt`.

**Rejected.** litedash drives the tuner and takes audio and focus through TWUtil writes such as
`write(RADIO, 1, 0)` and `write(AUDIO, 0xC0, source)`. These are exactly the unqualified native
mutations `TopwayNativeRadio` keeps BLOCKED. The file was useful only to confirm that native radio
is a separate transport from the NavRadio+ and stock-radio sessions.

## Media session choice and night mode: CleanDash

Files inspected: `app/src/main/java/io/github/donnyyu/cleandash/NowPlaying.java` and `NightMode.java`.

**Rejected.** `NowPlaying.choose()` prefers "any playing session, else the first with a track".
That routes control to whichever player is active, which the launcher's exact-package and
exact-token rule forbids.

**Recorded as a lead, not wired.** `NightMode` listens for the passive broadcasts
`com.zjinnova.zlink.action.OUT_DARK_START` and `OUT_DARK_STOP`, alongside a TWUtil query. A passive
broadcast would be an acceptable read-only Day/Night input, but it has not been observed on the
TS18. Until it is observed in the launcher's own context, illumination stays `UNKNOWN` and
appearance stays manual or scheduled.

## Scope of this original pass

The following were not studied in this four-reference pass. Later targeted comparison is recorded in [the preserved implementation-plan research](TS-THEME_IMPLEMENTATION_PLAN_2026-11-12.md), and AOSP/Taskbar/Media3 mechanisms in [continuous readiness](CONTINUOUS_READINESS.md); do not treat this list as a requirement to repeat that research:

- open-car-project
- device-reveng
- openlauncher
- dashline
- cabin

The setup checklist and app picker were built from this launcher's existing drawer and picker.
