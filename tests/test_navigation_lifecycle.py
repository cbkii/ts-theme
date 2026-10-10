"""Run the production helper against stateful Android command doubles."""
import json
import concurrent.futures
import os
import shlex
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "launcher/src/main/assets/nav/nav-window.sh"

ANDROID = r'''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
p = Path(os.environ['ANDROID_STATE'])
s = json.loads(p.read_text())
def write_state(value):
    staged = p.with_name(p.name + '.' + str(os.getpid()))
    staged.write_text(json.dumps(value))
    staged.replace(p)
cmd = Path(sys.argv[0]).name
a = sys.argv[1:]
if cmd == 'id': print(0)
elif cmd == 'dumpsys':
    if a[0] == 'window':
        print('  Window #0 Window{fake u0 app.organicmaps.incar/app.organicmaps.MwmActivity' + ('Other' if s.get('decoy_window') else '') + '}:')
        print('    mDisplayId=0 stackId=4')
        print('    mHasSurface=true isReadyForDisplay()=true')
        print('    isOnScreen=' + ('false' if s.get('window_hidden') else 'true'))
        print('    isVisible=' + ('false' if s.get('window_hidden') else 'true'))
        print('    Surface: shown=true layer=3')
        sys.exit(0)
    if a[1] == 'recents':
        print('ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)')
        if s.get('nav_alive', True): print('TaskRecord{fake #42 A=app.organicmaps.incar U=0 StackId=4}')
        sys.exit(0)
    print('Display #0')
    for task, pkg, component, mode, stack, bounds in [
        (42, 'app.organicmaps.incar', s.get('nav_component', 'app.organicmaps.MwmActivity'), s['mode'], 4, s['bounds']),
        (7, 'com.cbkii.ts18launcher', s.get('home_component', 'com.cbkii.ts18launcher/.HomeAlias'), 1, 0, [0,0,0,0])]:
        if task == 42 and (not s.get('nav_alive', True) or s.get('activities_miss')): continue
        if '/' not in component: component = pkg + '/' + component
        print('  Stack #%s: type=standard mode=%s' % (stack, 'freeform' if mode == 5 else 'fullscreen'))
        if not s.get('unknown_bounds'): print('  mBounds=Rect(0, 0 - 0, 0)')
        print('    Task id #%s' % task)
        if not s.get('unknown_bounds'): print('    mBounds=Rect(%s, %s - %s, %s)' % tuple(bounds))
        print('    * TaskRecord{fake #%s A=%s U=0 StackId=%s sz=1}' % (task, pkg, stack))
        print('      * Hist #0: ActivityRecord{fake u0 %s t%s}' % (component, task))
    pkg = 'app.organicmaps.incar/app.organicmaps.MwmActivity' if s['focus'] == 42 else 'com.cbkii.ts18launcher/.HomeAlias'
    print('  mResumedActivity: ActivityRecord{fake u0 %s t%s}' % (pkg, s['focus']))
    if 'top_focus' in s:
        print('  topResumedActivity=ActivityRecord{fake u0 %s t%s}' % (pkg, s['top_focus']))
elif cmd == 'pidof':
    if s.get('nav_alive', True) or s.get('process_only'): print('4889')
    else: sys.exit(1)
elif cmd == 'pm': print('package:' + str(p))
elif cmd == 'app_process':
    a=a[a.index('com.cbkii.ts18launcher.NavTaskBridge')+1:]
    if not s.get('bridge_available'):
        print('UNKNOWN SecurityException permission_denied'); sys.exit(1)
    if s.get('ambiguous') and a[3] == '0':
        print('UNKNOWN IllegalStateException TASK_AMBIGUOUS'); sys.exit(1)
    if not s.get('nav_alive', True): print('NONE'); sys.exit(0)
    if a[0] != 'status':
        if s.get('bridge_top_missing'):
            print('UNKNOWN IllegalStateException TOP_ACTIVITY_UNOBSERVED'); sys.exit(1)
        if s.get('foreign_top'):
            print('UNKNOWN IllegalStateException LEGITIMATE_FOREIGN_ACTIVITY'); sys.exit(1)
        if s.get('shared_stack'):
            print('UNKNOWN IllegalStateException TASK_STACK_NOT_EXCLUSIVE'); sys.exit(1)
        s['commands'].append(['bridge', *a])
        s['mode'] = int(a[4]) if a[0] == 'mode' else s['mode']
        if a[5] == '1': s['focus'] = 42
        write_state(s)
    component = 'unknown' if s.get('unknown_top') else 'app.organicmaps.incar/app.organicmaps.MwmActivity'
    print('FOUND 42 4 0 %s %s %s unknown' % (s['mode'], ','.join(map(str,s['bounds'])), component))
elif cmd == 'am':
    if a == ['help']:
        print('start --windowingMode --display'); sys.exit(0)
    if a[:2] == ['stack', 'list']:
        print('Stack id=4 bounds=[0,0][1280,720] displayId=0 userId=0')
        if s.get('nav_alive', True): print('taskId=42: app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity')
        sys.exit(0)
    if a[0] == 'start':
        # Independent append receipts reveal duplicate dispatch even if state
        # snapshots race. One O_APPEND write, no read-modify-write counter.
        receipt = os.open(str(p.with_name('start-dispatches.jsonl')), os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        os.write(receipt, json.dumps(a).encode() + bytes([10]))
        os.close(receipt)
    s['commands'].append(a)
    write_state(s)
    if a[0] == 'start':
        if s.get('exception_launch'):
            print('Exception occurred while executing start'); sys.exit(1)
        if s.get('reject_launch'):
            print('Error type 3: activity does not exist'); sys.exit(1)
        if '--task' in a: assert a[a.index('--task')+1] == '42', a
        else:
            s['nav_alive'] = not s.get('delayed_task')
            s['nav_component'] = s.get('cold_component', 'app.organicmaps.SplashActivity')
        s['mode'] = int(a[a.index('--windowingMode')+1]); s['focus'] = 7 if s.get('delayed_task') else 42
    elif a[:2] == ['task', 'focus']:
        s['focus'] = int(a[2])
        if s.get('transition_component_on_home_focus') and s['focus'] == 7:
            s['nav_component'] = 'app.organicmaps.incar/app.organicmaps.MwmActivity'
    elif a[:2] == ['task', 'resize']:
        if s['mode'] != 5 or s.get('reject_resize'):
            print('IllegalArgumentException: resizeTask not allowed on task=42', file=sys.stderr)
            if s.get('long_error'): print('stack trace line\n' * 60, file=sys.stderr)
            sys.exit(1)
        s['bounds'] = list(map(int, a[3:]))
    write_state(s)
'''


class NavigationLifecycleTest(unittest.TestCase):
    def run_helper(self, args, **overrides):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            bin_dir = work / 'bin'
            bin_dir.mkdir()
            for name in ('awk', 'cat', 'cut', 'grep', 'head', 'rm', 'sleep', 'tr', 'python3', 'timeout', 'mkdir', 'rmdir'):
                resolved = shutil.which(name) or (shutil.which('gtimeout') if name == 'timeout' else None)
                if resolved is None and name == 'timeout':
                    self.skipTest('GNU timeout required for bounded Android command doubles')
                (bin_dir / name).symlink_to(resolved)
            if overrides.get('boot_unavailable'):
                fake_cat = bin_dir / 'cat'; fake_cat.unlink()
                fake_cat.write_text('#!/bin/sh\nif [ "$1" = /proc/sys/kernel/random/boot_id ]; then exit 1; fi\nexec '
                                    + shlex.quote(shutil.which('cat')) + ' "$@"\n')
                fake_cat.chmod(0o700)
            for name in ('id', 'getprop', 'dumpsys', 'am', 'pm', 'pidof', 'app_process'):
                path = bin_dir / name
                path.write_text(ANDROID.replace('app.organicmaps.incar', overrides.get('nav_package', 'app.organicmaps.incar')))
                path.chmod(0o700)
            replays = overrides.pop("replays", 1)
            parallel = overrides.pop("parallel", False)
            seed_claim = overrides.pop("seed_claim", False)
            close_after_first = overrides.pop("close_after_first", False)
            reveal_after_first = overrides.pop("reveal_after_first", False)
            disable_claim = overrides.pop("disable_claim", False)
            state = dict(mode=5, bounds=[0,141,1131,702], focus=42, commands=[])
            state.update(overrides)
            state_path = work / 'state.json'
            state_path.write_text(json.dumps(state))
            source = HELPER.read_text().replace('PATH=/system/bin:/system/xbin:/vendor/bin', f'PATH={bin_dir}', 1)
            source = source.replace('/system/bin/toybox timeout -k 1', 'timeout -k 1')
            source = source.replace('ROOT_DIR=/data/adb/ts18-launcher', f'ROOT_DIR={work}', 1)
            if disable_claim:
                source = source.replace('mkdir "$launch_marker" 2>/dev/null || fail LAUNCH_PENDING', 'true', 1)
            script = work / 'helper.sh' 
            script.write_text(source)
            if seed_claim:
                boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
                claim = work / ('launch-0-' + overrides.get('nav_package', 'app.organicmaps.incar') + '-' + boot)
                claim.mkdir()
                os.utime(claim, (1, 1))
            def invoke():
                return subprocess.run(['/bin/sh', str(script), *args], capture_output=True, text=True,
                                        env={**os.environ, 'ANDROID_STATE': str(state_path)}, timeout=12)
            if parallel:
                with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                    results = list(pool.map(lambda _: invoke(), range(2)))
                result = next((r for r in results if 'LAUNCH_PENDING' in r.stdout), results[-1])
            else:
                for iteration in range(replays):
                    result = invoke()
                    if close_after_first and iteration == 0:
                        state = json.loads(state_path.read_text())
                        state.update(nav_alive=False, focus=7)
                        state_path.write_text(json.dumps(state))
                    if reveal_after_first and iteration == 0:
                        state = json.loads(state_path.read_text())
                        state.update(nav_alive=True, nav_component='app.organicmaps.MwmActivity', focus=7)
                        state_path.write_text(json.dumps(state))
            final_state = json.loads(state_path.read_text())
            final_state['claims_remaining'] = len(list(work.glob('launch-*')))
            receipt = work / 'start-dispatches.jsonl'
            final_state['start_dispatches'] = len(receipt.read_text().splitlines()) if receipt.exists() else 0
            return result, final_state

    def test_bootstrap_is_observed_without_focus_resize_or_redelivery(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_component='app.organicmaps.SplashActivity', focus=42)
        self.assertIn('code=BOOTSTRAP_PENDING', result.stdout)
        self.assertIn('task=42', result.stdout)
        self.assertEqual([], state['commands'])

    def test_process_without_task_can_launch_after_structured_absence(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, process_only=True, bridge_available=True, focus=7)
        self.assertIn('code=BOOTSTRAP_PENDING', result.stdout)
        self.assertEqual(1, len(state['commands']))
        self.assertEqual('start', state['commands'][0][0])
        self.assertNotIn('--task', state['commands'][0])

    def test_accepted_launch_with_delayed_task_is_not_dispatched_twice(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, delayed_task=True, bridge_available=True, focus=7, replays=2)
        self.assertIn('code=LAUNCH_PENDING', result.stdout)
        self.assertEqual(1, len(state['commands']))

    def test_delayed_task_appears_and_is_adopted_without_second_launch(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, delayed_task=True, bridge_available=True, focus=7,
            replays=2, reveal_after_first=True)
        self.assertIn('code=PRESENTED_NATIVE', result.stdout)
        self.assertIn('task=42', result.stdout)
        self.assertEqual(1, sum(c[0] == 'start' for c in state['commands']))

    def test_concurrent_acquisition_has_one_atomic_launch_claim(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, delayed_task=True, bridge_available=True, focus=7, parallel=True)
        self.assertIn('code=LAUNCH_PENDING', result.stdout)
        self.assertEqual(1, state['start_dispatches'])
        self.assertEqual(1, len(state['commands']))

    def test_dispatch_oracle_detects_duplicates_when_claim_is_disabled(self):
        _, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, delayed_task=True, bridge_available=True, focus=7,
            parallel=True, disable_claim=True)
        self.assertEqual(2, state['start_dispatches'])

    def test_old_same_boot_claim_does_not_expire(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, bridge_available=True, focus=7, seed_claim=True)
        self.assertIn('code=LAUNCH_PENDING', result.stdout)
        self.assertEqual([], state['commands'])

    def test_definite_launch_rejection_releases_claim_for_retry(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, bridge_available=True, focus=7, reject_launch=True, replays=2)
        self.assertIn('code=FREEFORM_LAUNCH_FAILED', result.stdout)
        self.assertEqual(2, len(state['commands']))

    def test_unclassified_dispatch_exception_preserves_claim(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, bridge_available=True, focus=7, exception_launch=True, replays=2)
        self.assertIn('code=LAUNCH_PENDING', result.stdout)
        self.assertEqual(1, len(state['commands']))

    def test_other_package_claim_clears_after_validated_activity(self):
        result, state = self.run_helper(
            ['present-native', '0', 'other.navigation',
             'other.navigation/app.organicmaps.MwmActivity', '0', '141', '1131', '702', '0', '1'],
            nav_package='other.navigation', nav_alive=False, bridge_available=True,
            cold_component='app.organicmaps.MwmActivity', focus=7, replays=2, close_after_first=True)
        self.assertIn('code=PRESENTED_NATIVE', result.stdout)
        self.assertEqual(2, sum(c[0] == 'start' and '--task' not in c for c in state['commands']))

    def test_fullscreen_delayed_cold_launch_blocks_following_native_replay(self):
        result, state = self.run_helper(
            ['fullscreen', '0', 'app.organicmaps.incar', '0',
             'app.organicmaps.incar/app.organicmaps.SplashActivity'],
            nav_alive=False, delayed_task=True, bridge_available=True, focus=7, replays=2)
        self.assertIn('code=LAUNCH_PENDING', result.stdout)
        self.assertEqual(1, len(state['commands']))
        self.assertEqual(1, state['claims_remaining'])

    def test_unknown_other_package_component_cannot_resolve_claim(self):
        result, state = self.run_helper(
            ['present-native', '0', 'other.navigation', 'other.navigation/.Main',
             '0', '141', '1131', '702', '0', '1'],
            nav_package='other.navigation', activities_miss=True, bridge_available=True,
            unknown_top=True, seed_claim=True, focus=7)
        self.assertIn('code=COMPONENT_UNKNOWN', result.stdout)
        self.assertEqual(1, state['claims_remaining'])
        self.assertEqual([], state['commands'])

    def test_unobserved_bridge_top_cannot_redeliver_fullscreen_activity(self):
        result, state = self.run_helper(
            ['fullscreen', '0', 'app.organicmaps.incar', '42',
             'app.organicmaps.incar/app.organicmaps.MwmActivity'],
            bridge_available=True, bridge_top_missing=True)
        self.assertIn('code=FULLSCREEN_POLICY_BLOCKED', result.stdout)
        self.assertEqual([], state['commands'])

    def test_proven_warm_fullscreen_does_not_depend_on_boot_metadata_cleanup(self):
        result, state = self.run_helper(
            ['fullscreen', '0', 'app.organicmaps.incar', '42',
             'app.organicmaps.incar/app.organicmaps.MwmActivity'],
            bridge_available=True, boot_unavailable=True)
        self.assertIn('code=FULLSCREEN', result.stdout)
        self.assertEqual(0, state['start_dispatches'])

    def test_cold_launch_requires_boot_metadata_before_dispatch(self):
        result, state = self.run_helper(
            ['present-native', '0', 'app.organicmaps.incar',
             'app.organicmaps.incar/app.organicmaps.SplashActivity', '0', '141', '1131', '702', '0', '1'],
            nav_alive=False, bridge_available=True, boot_unavailable=True, focus=7)
        self.assertIn('code=LAUNCH_MARKER_FAILED', result.stdout)
        self.assertEqual(0, state['start_dispatches'])

    def test_window_visibility_is_independent_of_valid_task_geometry(self):
        args = ['verify-native', '0', 'app.organicmaps.incar', '0', '141', '1131', '702', '42']
        result, state = self.run_helper(args)
        self.assertIn('visible=1 drawn=1', result.stdout)
        hidden, state = self.run_helper(args, window_hidden=True)
        self.assertIn('visible=unknown drawn=unknown', hidden.stdout)
        decoy, _ = self.run_helper(args, decoy_window=True)
        self.assertIn('visible=unknown drawn=unknown', decoy.stdout)
        self.assertEqual([], state['commands'])

    def test_read_only_verify_preserves_same_package_non_map_flow(self):
        result, state = self.run_helper(
            ['verify-native', '0', 'app.organicmaps.incar', '0', '141', '1131', '702', '42'],
            nav_component='app.organicmaps.settings.SettingsActivity')
        self.assertIn('code=VERIFIED_NATIVE', result.stdout)
        self.assertNotIn('code=BOOTSTRAP_PENDING', result.stdout)
        self.assertEqual([], state['commands'])

    def test_warm_park_validates_home_and_preserves_freeform_task(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('OK code=SUSPENDED user=0 task=42', result.stdout)
        self.assertIn('user=0', result.stdout)
        self.assertEqual(5, state['mode'])
        self.assertEqual(7, state['focus'])
        self.assertEqual([['task', 'focus', '7']], state['commands'])

    def test_invalid_home_fails_before_mutating_navigation(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            home_component='other.package/.Activity')
        self.assertIn('COMPONENT_MISMATCH', result.stdout)
        self.assertEqual([], state['commands'])

    def test_park_accepts_same_task_package_activity_transition(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            nav_component='app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity',
            transition_component_on_home_focus=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('OK code=SUSPENDED user=0 task=42', result.stdout)
        self.assertEqual(7, state['focus'])
        self.assertEqual([['task', 'focus', '7']], state['commands'])

    def test_park_rejects_transition_outside_owned_package(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            nav_component='other.package/.Activity')
        self.assertIn('COMPONENT_MISMATCH', result.stdout)
        self.assertEqual([], state['commands'])

    def test_suspension_does_not_steal_focus_from_unrelated_app(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            focus=99)
        self.assertIn('FOREGROUND_CHANGED', result.stdout)
        self.assertEqual([], state['commands'])

    def test_warm_park_rejects_fullscreen_task_without_activity_transaction(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            mode=1, focus=7)
        self.assertIn('SUSPEND_MODE_MISMATCH', result.stdout)
        self.assertEqual([], state['commands'])

    def test_warm_park_rejects_unknown_bounds_without_activity_transaction(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            unknown_bounds=True)
        self.assertIn('BOUNDS_UNKNOWN', result.stdout)
        self.assertEqual([], state['commands'])

    def test_top_resumed_overrides_stale_per_stack_resumed_task(self):
        result, state = self.run_helper(
            ['park-windowed', '0', 'app.organicmaps.incar', '42', 'com.cbkii.ts18launcher', '7'],
            top_focus=99)
        self.assertIn('FOREGROUND_CHANGED', result.stdout)
        self.assertEqual([], state['commands'])

    def test_existing_fullscreen_task_enters_mode5_before_resize(self):
        result, state = self.run_helper(['present-native', '0', 'app.organicmaps.incar',
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity', '0', '141', '1131', '702', '42', '1'], mode=1, focus=7)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual('start', state['commands'][0][0])
        self.assertEqual(1, sum(c[0] == 'start' for c in state['commands']))
        self.assertIn('task=42', result.stdout)
        self.assertIn('windowingMode=5', result.stdout)
        self.assertIn('launched=0', result.stdout)

    def test_existing_freeform_task_is_focused_without_relaunch(self):
        result, state = self.run_helper(['present-native', '0', 'app.organicmaps.incar',
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity', '0', '141', '1131', '702', '42', '1'], focus=7)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertFalse(any(c[0] == 'start' for c in state['commands']))
        self.assertEqual(42, state['focus'])

    def test_resize_failure_retains_actual_command_error(self):
        result, _ = self.run_helper(['present-native', '0', 'app.organicmaps.incar',
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity', '0', '141', '1131', '702', '42', '1'], reject_resize=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('RESIZE_FAILED', result.stdout)
        self.assertIn('IllegalArgumentException: resizeTask not allowed', result.stdout)

    def test_long_command_error_does_not_hide_protocol_from_java_reader(self):
        result, _ = self.run_helper(['present-native', '0', 'app.organicmaps.incar',
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity', '0', '141', '1131', '702', '42', '1'],
            reject_resize=True, long_error=True)
        self.assertIn('FAIL code=RESIZE_FAILED', '\n'.join(result.stdout.splitlines()[:48]))

    def test_activities_miss_recovers_via_structured_atm(self):
        result, state = self.run_helper(['status','0','app.organicmaps.incar','42'], activities_miss=True, bridge_available=True)
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn('task=42', result.stdout)
        self.assertEqual([], state['commands'])

    def test_same_parser_miss_with_live_task_stays_uncertain(self):
        result, state = self.run_helper(['status','0','app.organicmaps.incar','42'], activities_miss=True)
        self.assertIn('TASK_OBSERVATION_UNCERTAIN', result.stdout)
        self.assertEqual([], state['commands'])

    def test_only_confirmed_absence_returns_not_found(self):
        result, state = self.run_helper(['status','0','app.organicmaps.incar','42'], nav_alive=False)
        self.assertIn('TASK_NOT_FOUND', result.stdout)
        self.assertEqual([], state['commands'])

    def test_fullscreen_reacquires_warm_task_without_launcher_activity(self):
        result, state = self.run_helper(['fullscreen','0','app.organicmaps.incar','0',
             'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity'], bridge_available=True)
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual(1, state['mode'])
        self.assertEqual(42, state['focus'])
        self.assertFalse(any(c[0]=='start' for c in state['commands']))

    def test_departure_preserves_unrelated_foreground(self):
        result, state = self.run_helper(['background-fullscreen','0','app.organicmaps.incar','42',
             'com.cbkii.ts18launcher','7','0'], bridge_available=True, focus=99)
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual(1, state['mode'])
        self.assertEqual(99, state['focus'])
        self.assertEqual('0', state['commands'][0][-1])
        self.assertFalse(any(c[0]=='start' or c[:2]==['task','focus'] for c in state['commands']))

    def test_home_return_cancels_stale_departure(self):
        result, state = self.run_helper(['background-fullscreen','0','app.organicmaps.incar','42',
             'com.cbkii.ts18launcher','7','0'], bridge_available=True, focus=7)
        self.assertIn('FOREGROUND_CHANGED', result.stdout)
        self.assertEqual(5, state['mode'])
        self.assertEqual([], state['commands'])

    def test_native_focus_loss_is_not_home_departure(self):
        result, state = self.run_helper(['background-fullscreen','0','app.organicmaps.incar','42',
             'com.cbkii.ts18launcher','7','1'], bridge_available=True, focus=42)
        self.assertIn('FOREGROUND_CHANGED', result.stdout)
        self.assertEqual([], state['commands'])

    def test_unsupported_background_mode_has_no_focus_or_activity_fallback(self):
        result, state = self.run_helper(['background-fullscreen','0','app.organicmaps.incar','42',
             'com.cbkii.ts18launcher','7','0'], focus=99)
        self.assertIn('BACKGROUND_MODE_UNSUPPORTED', result.stdout)
        self.assertEqual([], state['commands'])

    def test_fullscreen_does_not_redeliver_over_foreign_permission_flow(self):
        result, state = self.run_helper(['fullscreen','0','app.organicmaps.incar','42',
             'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity'], bridge_available=True, foreign_top=True)
        self.assertIn('FULLSCREEN_POLICY_BLOCKED', result.stdout)
        self.assertEqual([], state['commands'])

    def test_transient_home_visibility_cannot_rewindow_foreground_fullscreen_maps(self):
        result, state = self.run_helper(['present-native','0','app.organicmaps.incar',
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity','0','141','1131','702','42','1'], mode=1, focus=42)
        self.assertIn('FOREGROUND_CHANGED', result.stdout)
        self.assertEqual(1, state['mode'])
        self.assertEqual([], state['commands'])

    def test_present_refuses_foreground_theft_from_external_app(self):
        result, state = self.run_helper(['present-native','0','app.organicmaps.incar',
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity','0','141','1131','702','42','1'], focus=99)
        self.assertIn('FOREGROUND_CHANGED', result.stdout)
        self.assertEqual([], state['commands'])
