# Native navigation window physical playbook

## Goal

Qualify one selected navigation application's real Android task on the exact TS18. Repository and CI results do not prove the physical result.

The required machine state is:

`selected package -> one task -> display 0 -> windowingMode 5 -> exact HOME panel bounds`

The required user result is:

`map visible and interactive inside the panel + launcher visible and interactive outside it`

## Prepare

1. Park safely. Do not run this while driving.
2. Install the current `TS18-Standalone-Launcher-TESTING.apk` and keep DoFun installed/enabled as recovery HOME.
3. In launcher Settings, choose Organic Maps InCar as Navigation and select **Native navigation window - TESTING**.
4. Return to the standalone HOME. Close other diagnostic scripts and do not manually run task/window commands.
5. From Termux, run:

   ```bash
   bash scripts/termux/collect-navigation-window-evidence.sh --expect-package app.organicmaps.incar
   ```

6. Wait for `OBSERVATION READY`. Perform the actions below at your own pace. The collector creates a checkpoint only when relevant task/window state changes.

## Ordinary actions

Record each result mentally as PASS, FAIL, BLOCKED or NOT RUN. Do not compensate for a failure with ADB, root commands or repeated taps.

1. Observe initial HOME convergence.
   - One map task may start once.
   - HOME must remain recoverable.
   - The navigator must not repeatedly relaunch over HOME.
2. Use the bounded map.
   - Drag/pan inside the map.
   - Use one normal map control inside the map.
   - Use one launcher control outside the map.
3. Open and close the in-HOME app drawer.
   - The map must not cover the drawer.
   - Closing the drawer must restore the same navigation task to the current panel bounds.
4. Press the fixed Navigation button to open fullscreen, then press HOME.
   - Fullscreen should use the same task where observable.
   - HOME return should restore mode 5 and the current panel bounds without creating a second task.
5. Open one ordinary unrelated app or Android Settings, then press HOME.
   - The unrelated task must never become navigation authority.
   - The selected navigator should return as the same task unless Android genuinely destroyed it.
6. If the panel reports a failure, confirm HOME remains usable.
   - Press **Retry** at most once.
   - If Retry fails, use **Open fullscreen** only to confirm the explicit fallback; do not repeat Retry.
7. Press Ctrl-C once in Termux. Let the script take its final checkpoint, verify the internal manifest and create the ZIP.

Do not test a second navigator in the same run. Test Google Maps, OsmAnd+ and Sygic separately only after Organic Maps has passed the core mode-5 result.

## Return the evidence

Provide both files printed by the collector:

- `TS18-navigation-window-<stamp>.zip`
- `TS18-navigation-window-<stamp>.zip.sha256`

Also report the six ordinary-action results and anything visible that a screenshot may not show, especially touch, focus, flicker or a HOME relaunch loop.

## Decision gate

| Physical result | Engineering meaning |
| --- | --- |
| Mode 5, exact bounds, HOME visible, map touch and launcher touch all work | Keep and harden the Android-Q native task architecture. No OEM hook is required. |
| Mode 5 and exact bounds are verified, but composition, z-order or input differs from working DoFun | Investigate the exact Topway `:navi` predicate and current framework/services bytes. |
| Android rejects or reverts mode 5 | Capture the exact rejection and investigate the owning Android/Topway policy before any vendor-state write. |
| Parser/helper/root prerequisite fails | Classify dependent physical checks BLOCKED; fix the earliest prerequisite first. |

Standard Android PiP is not part of this qualification.
