"""Run the production helper against stateful Android command doubles."""
import json
import os
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
cmd = Path(sys.argv[0]).name
a = sys.argv[1:]
if cmd == 'id': print(0)
elif cmd == 'dumpsys':
    print('Display #0')
    for task, pkg, component, mode, stack, bounds in [
        (42, 'app.organicmaps.incar', 'app.organicmaps.MwmActivity', s['mode'], 4, s['bounds']),
        (7, 'com.cbkii.ts18launcher', s.get('home_component', 'com.cbkii.ts18launcher/.HomeAlias'), 1, 0, [0,0,0,0])]:
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
elif cmd == 'am':
    s['commands'].append(a)
    p.write_text(json.dumps(s))
    if a[0] == 'start':
        assert '--task' in a and a[a.index('--task')+1] == '42', a
        s['mode'] = int(a[a.index('--windowingMode')+1]); s['focus'] = 42
    elif a[:2] == ['task', 'focus']: s['focus'] = int(a[2])
    elif a[:2] == ['task', 'resize']:
        if s['mode'] != 5 or s.get('reject_resize'):
            print('IllegalArgumentException: resizeTask not allowed on task=42', file=sys.stderr)
            if s.get('long_error'): print('stack trace line\n' * 60, file=sys.stderr)
            sys.exit(1)
        s['bounds'] = list(map(int, a[3:]))
    p.write_text(json.dumps(s))
'''


class NavigationLifecycleTest(unittest.TestCase):
    def run_helper(self, args, **overrides):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            bin_dir = work / 'bin'
            bin_dir.mkdir()
            for name in ('awk', 'cat', 'cut', 'grep', 'head', 'rm', 'sleep', 'tr', 'python3'):
                (bin_dir / name).symlink_to(shutil.which(name))
            for name in ('id', 'getprop', 'dumpsys', 'am'):
                path = bin_dir / name
                path.write_text(ANDROID)
                path.chmod(0o700)
            state = dict(mode=5, bounds=[0,141,1131,702], focus=42, commands=[])
            state.update(overrides)
            state_path = work / 'state.json'
            state_path.write_text(json.dumps(state))
            source = HELPER.read_text().replace('PATH=/system/bin:/system/xbin:/vendor/bin', f'PATH={bin_dir}', 1)
            source = source.replace('ROOT_DIR=/data/adb/ts18-launcher', f'ROOT_DIR={work}', 1)
            script = work / 'helper.sh'
            script.write_text(source)
            result = subprocess.run(['/bin/sh', str(script), *args], capture_output=True, text=True,
                                    env={**os.environ, 'ANDROID_STATE': str(state_path)}, timeout=12)
            return result, json.loads(state_path.read_text())

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
            'app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity', '0', '141', '1131', '702', '42', '1'], mode=1)
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
