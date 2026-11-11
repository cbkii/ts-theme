# Topway capabilities (read-only)

The launcher is an ordinary app. It does not hold Topway's platform signature, UID 1000, signature
permissions or MCU authority, and it never writes Topway, tuner, volume, light, screen or MCU
state. `launcher/src/main/java/com/cbkii/ts18launcher/oem/` only reports what this app can
establish from its own identity.

## What it reports

`DeviceCapabilities` reads PackageManager facts and this process's identity. It binds nothing and
writes nothing. Each capability is `AVAILABLE`, `UNAVAILABLE`, `BLOCKED` or `UNKNOWN`, together
with the stage that decided it:

| Capability | Meaning |
|---|---|
| `caller` | launcher package, UID, Android user, SELinux context as read from `/proc/self/attr/current` |
| `topway.service.identity` | `com.tw.service.xt` version, shared UID, signer, and APK SHA-256 compared with the verified binary |
| `topway.service.endpoint` | `CommandService` declared, enabled, exported, and whether it requires a permission |
| `topway.source` | active Topway source, reported only after an explicit observation |
| `topway.reverse`, `topway.sleep` | values only if the service pushes them during an observation |
| `radio.navradio.session` | NavRadio+ installed; controlled through its own MediaSession |
| `radio.topway.session` | stock radio installed; session-only, never started or bound by the launcher |
| `radio.topway.native` | always `BLOCKED`, because no native tuner command is qualified |
| `illumination` | `UNKNOWN`: no verified read-only light signal, so appearance stays manual or scheduled |

`AVAILABLE` means "observed from this app". It never means "authorised to change".

## Operator actions (Settings → Experimental · Topway services)

Neither action runs automatically, at startup or in the background.

- **Check Topway services** reads the facts, then binds to `CommandService` with flag 0 (it never creates the service) and compares the Binder interface descriptor. No other transaction is sent. It then unbinds.
- **Observe Topway source once** does the same, then:
  1. registers a temporary status callback (transaction 1);
  2. sends the source query `{project=system, action=source_request}` through transaction 67;
  3. waits up to 2.5 s for the `Source_recieve` reply (exact vendor spelling);
  4. always unregisters (transaction 2) and unbinds.

  The query is withheld unless the installed APK's SHA-256 matches the verified binary. It never registers the music callback (transaction 5), because that routes steering-wheel media keys to the registrant.

The run is single-flight and every wait has a deadline. Late callbacks are ignored by generation. A refused bind, a wrong descriptor, a refused registration or query, a disconnect and a missing reply each map to a distinct stage and status. Results stay on the Settings screen and in the local media event trace.

### Gating

**Observe Topway source once** appears only after a separate, default-OFF switch, **Allow one-off
Topway source observation**, is turned on. That switch names the risk below. The check runs only
on that explicit request, never automatically, and is re-checked when it runs.

### Known open risk

Static evidence does not show whether the service keeps one status callback or a list. If it keeps
one, the temporary registration could briefly displace another app's status listener until that
app re-registers. Music-key routing (a separate callback) is not affected. This is one reason the
observation is manual only.

## Contract provenance

| Item | Value |
|---|---|
| Binary | `com.tw.service.xt` 2022.02.17 (versionCode 2) |
| SHA-256 | `341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267` |
| Service | `CommandService`, action `com.tw.service.xt.CommandService.Bind`, exported, no binding permission |
| Command interface | `com.tw.service.xt.aidl.ITWCommandAidl` |
| Status callback | `com.tw.service.xt.aidl.ITWCommandCallbackAidl` (callbacks 1–8; 6 = reverse, 7 = sleep, 8 = extended Bundle) |
| Source values | radio 1, local music 3, Bluetooth 8, video 9 |

These facts come from static analysis of that exact APK. They match the independently maintained
Auxio-TS Topway command-service bridge (`docs/ts18/launcher-integration/TOPWAY_COMMAND_SERVICE_BRIDGE.md`
in cbkii/Auxio-TS). No vendor code, AIDL or Auxio-TS code is copied here; the launcher
implementation is written independently.

## Qualification status

Host tests cover:

- the probe state machine, using fakes for every stage, deadline, late callback and single-flight path;
- callback parcel parsing, under Robolectric API 29, including wrong descriptors, truncated fields and wrong value types;
- classification;
- a static guard that only transactions 1, 2 and 67 exist, with no TWUtil, writer, music callback or service creation.

**No TS18 run has been performed (NOT_RUN).** Bind admission for this app, the descriptor,
callback acceptance, the source reply and the single-versus-list callback behaviour are all
unverified on the device.

## Native radio

`TopwayNativeRadio` is the boundary for tuner commands. Every operation returns `BLOCKED` and
there is no transport behind it. Enabling one requires all of the following:

- the exact caller authority;
- single-writer source arbitration;
- a reversible exercise;
- physical observation;
- owner approval.
