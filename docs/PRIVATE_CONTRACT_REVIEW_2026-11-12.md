# Private contract review — 12 November 2026

## XTService: static coexistence established

The supplied `XTService_2022.02.17.apk` SHA-256 is `341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267`, matching the project's exact-device binary pin. Its `classes.dex` SHA-256 is `f10252f8c6331e2246df81210e67b27ce04ea096d987061f9c563ea90d5dc808`.

Read-only DEX inspection with Androguard confirms that `CommandService` constructs separate `RemoteCallbackList` instances for command, Radio, BT, Music and Video callbacks. `registerTWCommandCallback` registers the non-null callback into `iTWCommandCallbackAidlLis`; `unRegisterTWCommandCallback` unregisters that callback. This is a multi-registrant list, not a single replaceable callback field. The Binder wrapper delegates to the service. The inspected generated transaction path enforces the interface descriptor; no caller-UID or permission check was found in that inspected method. This does not prove runtime SELinux/Binder admission or complete vendor authority.

The existing hash-gated, operator-requested bounded observation may remain. No runtime registration, source request, tuner operation or protected-device change was performed. Final TS18 check: request observation while the normal clients remain active, then verify their callbacks/audio behaviour before and after cleanup.

## NavRadio+: available binary is incompatible reference evidence

Available `NavRadio_Plus_v4_00_PREMIUM (1).apk`: package `com.navimods.radio`, version 4.00/code 1032, minSdk 32, targetSdk 35. This is not an installable API29 reference and is not matched to the installed package.

Its manifest exports `com.navimods.radio.RadioService` for `androidx.media3.session.MediaSessionService`; no framework MediaBrowser action appears on that service. A plain service bind is not a Media3 controller connection. Neither session publication nor tuner/source side effects of creating the installed API29 service are established by these bytes.

Current decision after the readiness integration: use an existing exact session, then the real framework MediaBrowser handshake against one accessible/unambiguous framework-browser or Media3 service candidate. A declaration/plain bind is insufficient. A non-dispatched explicit NavRadio Play can enter the authorised bounded masked Activity transaction; manual app priming is not the normal solution. Passive tuner activation stays gated. See [continuous readiness](CONTINUOUS_READINESS.md).

Deferred discriminator: record installed version/signer/service declarations, test any evidenced background protocol while Music plays, then confirm session token ownership, unchanged source/tuner/audio during preparation and one requested Radio Play. This isolated gap does not block Music, catalogue or N2 work.

## N3 bounded assessment

The existing API29 IPC probe already exercises a launcher-owned private display, non-embeddable refusal and pixel observation. Existing qualified API29 evidence reports the input forwarder unavailable and therefore view-only; Organic Maps embedding permission is not established. A new privileged host skeleton cannot confer platform signing, input injection or embedding authority.

N2 remains the product path. The inert n3host module, Binder protocol and client have been removed. Keep the already isolated, default-off public-API private-display experiment for explicit developer inspection; it has no automatic startup ownership. No root input injection or hidden-API exemption was introduced. Exact TS18 N3 is NOT_RUN and provides no demonstrated advantage sufficient to replace N2.
