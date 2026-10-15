# Topway desktop-window contracts and navigation boundary

## Evidence authority

The cooperative desktop-window ABI below is recovered from the exact current-device `com.tw.video` client family:

- `TW_THEME.20241022` / versionCode 119;
- SHA-256 `07c37275f86c495f8e62a23bcfae32e75674f4ad30ccdbc269f25235cc159562`.

The generic navigation boundary is additionally established by exact TS18 runtime captures from the Organic Maps InCar/window-continuity work and by current-era DoFun static resources. Keep these evidence classes separate: Video proves its client/server contract; the DoFun/Organic Maps captures prove the ordinary-navigation task/window mechanism.

## Generic navigation is an Android freeform task

When DoFun launched Organic Maps on the exact TS18, Android logged `windowingMode = 5`; the resulting Organic Maps task was on display 0, and Topway configuration reported `isPipLauncher 1 :navi` while the application received bounded real Activity/Decor/Surface dimensions. Standard Android PiP was false.

Current-era DoFun static configuration separately maps Google Maps to internal function `tw_navi` with fixed behaviour, while the theme resources define the ordinary `soft_type: navi` widget separately. This is corroborating evidence that DoFun has a generic OEM navigation lane for ordinary apps.

Therefore the standalone HOME's navigation lane is:

`ordinary selected navigation Activity/task -> display 0 -> Android freeform windowingMode=5 -> Topway navi/WINDOW policy`.

The exact private DoFun/Topway actuator that establishes the OEM policy remains unresolved. Correlated force-PIP properties/files are not treated as command interfaces unless their writer/consumer relationship is independently recovered.

## HOME-side discovery and state used by Video

The Video client resolves the current Android HOME and package-scopes a service lookup for:

`cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE`

In this inspected client path the service is a capability marker; no bind to that HOME service supplies `WindowInfo`.

The client constructs:

`content://<HOME_PACKAGE>.ExportedProvider/kv_config/<key>`

and observes:

- `desktop_window_setting`;
- `isCurrentUsedThemeInstanceDesktopWindow`.

It calls `query(uri, null, null, null, null)`, advances the returned Cursor and consumes column 0 as a String. The first key is parsed as `WindowInfo`; the second with `Boolean.parseBoolean()`.

For the standalone launcher the provider is read-only. While no cooperative Topway window is active, `desktop_window_setting` returns zero rows and the theme key returns `false`. This deliberately avoids guessing DoFun host policy.

## WindowInfo schema

Recovered logical/JSON/Parcelable field order:

1. `appType`;
2. `identifier`;
3. `windowName`;
4. `packageName`;
5. `windowViewClassName`.

Exact Video metadata includes:

- `appType=video`;
- `identifier=video`;
- `packageName=com.tw.video`;
- `windowViewClassName=com.tw.video.CustomWindowView`.

The exact DoFun-generated `windowName`/selection policy is still not established and must not be fabricated.

## Cooperative app floating-window Binder

Compatible apps can export:

`cn.cardoor.desktop.window.floating.intent.action.FLOATING_WINDOW_SERVER`

Master descriptor:

`cn.cardoor.desktop.window.floating.IFloatingWindowMaster`

| Tx | Meaning |
|---:|---|
| 1 | query overlay permission |
| 2 | request overlay permission |
| 3 | open controller using host lifetime Binder token |

Controller descriptor:

`cn.cardoor.desktop.window.floating.IFloatingWindowController`

| Tx | Meaning |
|---:|---|
| 1 | select/create app-owned window view class |
| 2 | show at x/y/width/height |
| 3 | hide |
| 4 | destroy |
| 5 | size changed x/y/width/height |
| 6 | location changed x/y/width/height |
| 7 | clip rectangle left/top/right/bottom |

The transactions are synchronous in the inspected implementation. The host lifetime token is linked to death so abnormal host exit can release app-side floating state.

## Architectural boundary

Do not collapse these surfaces:

1. **Generic navigation:** ordinary third-party task in Android freeform mode 5 plus Topway navigation policy. Organic Maps, Google Maps, OsmAnd+ and Sygic do not need the Video floating server contract.
2. **Current-HOME compatibility:** marker service plus read-only provider keys consumed by clients such as Video.
3. **Cooperative app floating windows:** app-owned `IWindowView` exposed through the Video-style Binder protocol.

PR #11 implements #1 using selected package/task/display/mode/bounds authority and #2 as narrow HOME compatibility. It intentionally does not make #3 a dependency of navigation.

Read-only properties such as `persist.tw.forcepip`, `sys.tw.forcepip.*`, `sys.df.desktop` and `sys.df.variety.theme.window` may be correlated during qualification. The launcher must not write them merely because they describe a working DoFun state.
