# Topway desktop-window contract recovered from current Video client

## Evidence authority

Static target: `com.tw.video` `TW_THEME.20241022` / versionCode 119.

SHA-256: `07c37275f86c495f8e62a23bcfae32e75674f4ad30ccdbc269f25235cc159562`.

That hash matches the exact current-device pin. The statements below are therefore byte-static evidence for the installed client family, not a TS10 donor extrapolation. This document records interface facts only; vendor APK/decrypted code is not committed.

## HOME-side discovery and state

The client resolves the current Android HOME and package-scopes a service lookup for:

`cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE`

In this inspected client path the service is a capability marker; no bind to that HOME service supplies `WindowInfo`.

The client then constructs:

`content://<HOME_PACKAGE>.ExportedProvider/kv_config/<key>`

and observes these exact keys:

- `desktop_window_setting`;
- `isCurrentUsedThemeInstanceDesktopWindow`.

It calls `query(uri, null, null, null, null)`, advances the returned Cursor and consumes column 0 as a String. The first key is parsed as `WindowInfo`; the second is parsed with `Boolean.parseBoolean()`.

For the standalone launcher the provider is read-only. While no cooperative Topway window is active, `desktop_window_setting` returns zero rows and the theme key returns `false`. That is a safe inactive state and deliberately avoids guessing DoFun host policy.

## WindowInfo schema

The recovered logical/JSON/Parcelable field order is:

1. `appType`;
2. `identifier`;
3. `windowName`;
4. `packageName`;
5. `windowViewClassName`.

For exact Video metadata:

- `appType=video`;
- `identifier=video`;
- `packageName=com.tw.video`;
- `windowViewClassName=com.tw.video.CustomWindowView`.

The exact DoFun-generated `windowName`/selection policy is still unknown and must not be fabricated.

## App-side floating-window Binder

Cooperative apps can export:

`cn.cardoor.desktop.window.floating.intent.action.FLOATING_WINDOW_SERVER`

Master descriptor:

`cn.cardoor.desktop.window.floating.IFloatingWindowMaster`

Recovered transactions:

| Tx | Meaning |
|---:|---|
| 1 | query overlay permission |
| 2 | request overlay permission |
| 3 | open controller using host lifetime Binder token |

Controller descriptor:

`cn.cardoor.desktop.window.floating.IFloatingWindowController`

Recovered transactions:

| Tx | Meaning |
|---:|---|
| 1 | select/create app-owned window view class |
| 2 | show at x/y/width/height |
| 3 | hide |
| 4 | destroy |
| 5 | size changed x/y/width/height |
| 6 | location changed x/y/width/height |
| 7 | clip rectangle left/top/right/bottom |

These are synchronous transactions in the exact inspected implementation. The host lifetime token is linked to death so abnormal host exit can release the app-side floating window.

## Architectural boundary

This Binder protocol is **not** the generic navigation transport. `com.tw.video` implements it; ordinary navigation applications need not.

CB's exact-unit user observation is that Organic Maps, Google Maps, OsmAnd+ and Sygic all work well in DoFun HOME despite not implementing this floating-window server contract. Therefore PR #11 preserves the separately proven generic bounded-task navigation lane. The recovered marker/provider is implemented as HOME compatibility; the cooperative Binder remains a narrow future adapter for apps that actually advertise it.

Do not infer from that observation which hidden DoFun mechanism hosts each navigation app. The current proven repository mechanism remains Android task/window bounds plus exact package/task validation, subject to physical TS18 qualification.
