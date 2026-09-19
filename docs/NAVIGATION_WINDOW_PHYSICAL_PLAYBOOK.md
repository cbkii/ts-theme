# Native navigation window physical playbook

## Goal

Qualify one selected navigation application's real Android task on the exact TS18. Repository and CI results do not prove the physical result.

The required machine state is:

`selected package -> one task -> display 0 -> windowingMode 5 -> exact HOME panel bounds`

The required user result is:

`map visible and interactive inside the panel + launcher visible and interactive outside it`

Physical TESTING attempt 2 is not a failed mode-5 result. The installed helper stopped at a false capability preflight because this OEM's `am help` advertised both required flags but exited 255. Retry repeated that same abort; no mode-5 launch or resize occurred. Use this playbook only with the corrected TESTING APK whose helper reports the help exit and advertised flags separately.

The current build also corrects the launcher-overlay lifecycle: returning from the in-HOME app drawer clears both overlay suppression and any queued drawer suspension. A task that merely reports mode 5 and exact bounds is shown as **configured**, not treated as proof that its surface is visible or touchable.

## Prepare

1. Park safely. Do not run this while driving.
2. From the fixed TESTING draft, download the newest `TS18-Standalone-Launcher-TESTING-PR11-<sha>.apk`, matching `BUILD_INFO-PR11-<sha>.txt` and `SHA256SUMS-PR11-<sha>.txt`; verify the asset group and exact source SHA. From that immutable source SHA, download `scripts/termux/collect-navigation-window-evidence.sh` and this playbook. Install the TESTING APK and keep DoFun installed/enabled as recovery HOME.
   Confirm its build information identifies a source newer than the attempt-2 source `e2018dc9a75725c53b06dfc6dd3608d69aa78197`.
3. In launcher Settings, choose Organic Maps InCar as Navigation and select **Native navigation window - TESTING**.
4. Return to the standalone HOME. Close other diagnostic scripts and do not manually run task/window commands.
5. From Termux, run:

   ```bash
   bash collect-navigation-window-evidence.sh --expect-package app.organicmaps.incar
   ```

6. Wait for `OBSERVATION READY`. Perform the actions below at your own pace. The focused collector creates a checkpoint when its combined activity/window/input signature changes. Each checkpoint also captures relevant SurfaceFlinger state and a screenshot.

## Ordinary actions

Record each result mentally as PASS, FAIL, BLOCKED or NOT RUN. Do not compensate for a failure with ADB, root commands or repeated taps.

1. Observe initial HOME convergence.
   - One map task may start once.
   - HOME must remain recoverable.
   - The navigator must not repeatedly relaunch over HOME.
   - `mode 5 configured` is task-state evidence only; it is not itself a visible-map PASS.
2. Run the decisive composition/input sequence.
   - Confirm the map is visibly bounded inside the intended panel.
   - Drag/pan inside the map.
   - Use one normal map control inside the map.
   - Use one launcher control outside the map.
   - Confirm the map remains visible after that outside touch.
   - Drag/pan inside the map once more.
   - If the map is absent, buried, untouchable, or covers the outside launcher control, stop ordinary actions here and continue to step 7. Do not compensate with Retry or root commands.
3. Only if step 2 passes, open and close the in-HOME app drawer.
   - The map must not cover the drawer.
   - Closing the drawer must restore the same navigation task to the current panel bounds.
4. Only if step 3 passes, press the fixed Navigation button to open fullscreen, then press HOME.
   - Fullscreen should use the same task where observable.
   - HOME return should restore mode 5 and the current panel bounds without creating a second task.
5. Only if step 4 passes, open one ordinary unrelated app or Android Settings, then press HOME.
   - The unrelated task must never become navigation authority.
   - The selected navigator should return as the same task unless Android genuinely destroyed it.
6. If the panel reports a failure, confirm HOME remains usable.
   - Press **Retry** at most once.
   - If Retry fails, use **Open fullscreen** only to confirm the explicit fallback; do not repeat Retry.
7. Press Ctrl-C once in Termux. The script first takes its final focused checkpoint, then performs the separate broad read-only discovery capture. Wait for both `BROAD DISCOVERY` completion and the printed ZIP/hash; this phase can take several minutes and may export up to 192 MiB of exact framework/APK bytes.

Do not test a second navigator in the same run. Test Google Maps, OsmAnd+ and Sygic separately only after Organic Maps has passed the core mode-5 result.

## Return the evidence

Provide both files printed by the collector:

- `TS18-navigation-window-<stamp>.zip`
- `TS18-navigation-window-<stamp>.zip.sha256`

Also report the ordinary-action results and anything a screenshot cannot prove, especially both inside-map touches, the outside-launcher touch, whether the map remained visible, focus, flicker or a HOME relaunch loop. Mark later steps `NOT_RUN` when the decisive step-2 gate failed.

The archive deliberately contains two evidence layers:

- focused checkpoints under `window/`, `input/`, `surface/` and `screens/` for the tested transition;
- `discovery/` for broader framework, service, package, Topway/DoFun, privilege and alternative-architecture evidence.

Broad discovery is not an implementation verdict. Missing strings or blocked reads remain UNKNOWN, and copied system bytes must not be committed to the repository.

## Decision gate

| Physical result | Engineering meaning |
| --- | --- |
| Mode 5, exact bounds, HOME visible, map touch and launcher touch all work | Keep and harden the Android-Q native task architecture. No OEM hook is required. |
| Mode 5 and exact bounds are verified, but composition, z-order or input differs from working DoFun | Investigate the exact Topway `:navi` predicate and current framework/services bytes. |
| Android rejects or reverts mode 5 | Capture the exact rejection and investigate the owning Android/Topway policy before any vendor-state write. |
| Parser/helper/root prerequisite fails | Classify dependent physical checks BLOCKED; fix the earliest prerequisite first. |

Standard Android PiP is not part of this qualification.
