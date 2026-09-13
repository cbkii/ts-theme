# HOME navigation surface experiments

PR #11 exists to answer one physical question on CB's exact Topway TS18: how can standalone HOME show a real navigation/map surface in its large map rectangle while surrounding launcher controls remain usable?

No experimental backend is production merely because it reaches a particular Android windowing mode. Physical composition, touch routing, focus/z-order and lifecycle behaviour are acceptance requirements.

## Established evidence

A known-good DoFun launch of Organic Maps produced an ordinary external Organic Maps task on display 0 in Android freeform `windowingMode=5`. Topway's modified framework simultaneously reported `isPipLauncher ... 1 :navi`, and Organic Maps received real bounded Activity/Decor/Surface geometry. Separate captures correlated `persist.tw.forcepip=1` and `sys.tw.forcepip.{x,y,w,h}` with the task's non-fullscreen bounds while standard Android PiP remained false.

This proves a working DoFun navigation surface ultimately involves a mode-5 task. It does **not** prove raw AOSP task resize reproduces the complete DoFun/Topway launcher policy. Topway may additionally own z-order, focus, multi-resume, launch classification or navigation state. Current DoFun static evidence maps ordinary Google Maps to `tw_navi`; exact runtime logs also show privileged `com.tw.service.xt` navigation-state activity such as `sendNaviType()` around working transitions. These are investigation anchors, not command contracts.

## One explicit surface authority

`HomeNavigationSurfacePolicy` is the sole selector. Modes are mutually exclusive: `fullscreen` safe fallback; `leaflet` launcher-owned comparator; `raw_freeform` experimental mode-5 foreign task; `android_pip` experimental standard mode-2 pinned task.

Migration is fail-safe: an existing explicit `map.enabled=true` becomes Leaflet; otherwise an installation defaults to fullscreen-only. No upgrade silently begins root task manipulation. PR #10's legacy Leaflet switch is retained as a compatibility entry point and opens the four-way chooser. The configured Navigation app remains a separate preference and is the only foreign-package authority.

## Raw freeform

`RawFreeformTaskBackend` deliberately does not call itself a Topway backend. Initial package-only acquisition fails `TASK_AMBIGUOUS` when more than one task matches. Once acquired, package + exact task ID + top component remain authority. Returning HOME verifies the known task; mode/bounds drift repairs that **same task**. Only `TASK_NOT_FOUND` authorises another explicit app launch.

Machine-state acceptance requires selected package/task/component, display 0, `windowingMode=5` and exact panel bounds. Physical PASS additionally requires the map visibly in the rectangle, HOME visible around it, map interaction inside it and launcher interaction outside it.

## Standard Android PiP

Android standard PiP is distinct from Topway's OEM use of the word PIP. Android 10 PiP is pinned `windowingMode=2`; the known-good DoFun navigation case was mode 5.

`AndroidPipBackend` acquires exactly one configured task; positively requires the Activity dump to report PiP support; refuses to displace another package already in the pinned stack; focuses only the exact selected task before the stack-level pin; verifies the resulting mode-2 identity; and experimentally asks the pinned stack to settle at the HOME rectangle. It never enables Android's global force-resizable setting and never reserves PiP system-wide. If normal map gestures/search/zoom cannot work while HOME stays usable, classify PiP **glance-only** and do not promote it.

Current Organic Maps evidence does not establish standard PiP support. A separate Organic Maps experiment is required before PiP can be a Tier-1 end-to-end test.

## Leaflet and fullscreen

Leaflet remains useful for launcher geometry/composition but is not the target map architecture and cannot consume third-party offline map databases. Fullscreen-only is first-class rather than a blank/error state. A rejected experiment must expose its reason and an ordinary Open fullscreen action.

## Separate Topway contracts

The exact current `com.tw.video` client establishes separate current-HOME marker/provider state and a cooperative app-owned `FLOATING_WINDOW_SERVER` protocol. PR #11 retains the marker/provider compatibility but does not make Video's Binder a navigation prerequisite.

## OEM-policy investigation

The high-value unknown is the Topway/DoFun policy accompanying a known-good mode-5 task. Read-only investigation targets exact current framework/services and privileged Topway code around `isPipLauncher`, `:navi`/`tw_navi`, `forcepip`, `/data/tw/custom_pip_app_name`, `/data/tw/navi_name`, `sendNaviType` and ActivityTaskManager windowing transitions. Observed properties/files remain diagnostics until a writer/consumer contract is proven. There is no `setprop`, protected `/data/tw` writer, SELinux relaxation, UID/signature spoofing or persistent root daemon in PR #11.

If static exact-framework inspection cannot expose the actuator, the next escalation is a DoFun-only log-only LSPosed trace of SystemProperties, Settings, ActivityOptions, ActivityTaskManager, WindowManager and startActivity calls without argument mutation.

## Provenance and qualification

CI injects the exact PR head SHA/ref as generated resources and the HOME placeholder displays a shortened source identity. Run `scripts/termux/qualify-navigation-surfaces.sh` separately for each selected mode. Shell mode/bounds identity is necessary, not sufficient. Manually qualify visible composition, touch inside/outside, fullscreen/HOME, unrelated-app/app-drawer round trips, navigator switching, process recreation, reverse return, reboot, cold boot and ACC sleep/wake. BLOCKED is not negative evidence.
