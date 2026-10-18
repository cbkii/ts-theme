"""Exercise the actual root runner, including Magisk's completed-but-alive client."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
COLLECTOR = ROOT / 'scripts/termux/collect-navigation-window-evidence.sh'

SU = r'''#!/usr/bin/env python3
import os, subprocess, sys, time
command = sys.argv[2].replace('/system/bin/toybox timeout', '/usr/bin/timeout')
command = command.replace('/system/bin/true', '/bin/true').replace('/system/bin/sh', '/bin/sh')
rc = subprocess.call(['/bin/sh', '-c', command])
if os.environ.get('STALE_SU') == '1': time.sleep(30)
sys.exit(rc)
'''


class NavigationCaptureTest(unittest.TestCase):
    def run_capture(self, command, stale=False, seconds=2):
        source = COLLECTOR.read_text()
        functions = source.split('stop_root_wrapper() {', 1)[1].split('capture_with_timeout() {', 1)[0]
        functions = 'stop_root_wrapper() {' + functions
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            su = work / 'su'
            su.write_text(SU)
            su.chmod(0o700)
            script = functions + '''
run_root_bounded "$TEST_SECONDS" "$WORK/stdout" "$WORK/stderr" "$TEST_COMMAND"
rc=$?
printf 'status=%s\\n' "$rc"
cat "$WORK/stdout" "$WORK/stderr"
[ -z "$ROOT_PID" ] || exit 99
exit "$rc"
'''
            env = {**os.environ, 'PATH': f'{work}:' + os.environ['PATH'], 'WORK': tmp,
                   'ANDROID_PATH': '/usr/bin:/bin', 'ROOT_PID': '', 'STALE_SU': str(int(stale)),
                   'TEST_SECONDS': str(seconds), 'TEST_COMMAND': command}
            start = time.monotonic()
            result = subprocess.run(['bash', '-c', script], capture_output=True, text=True, env=env, timeout=8)
            return result, time.monotonic() - start

    def test_completed_root_command_does_not_wait_for_stale_client(self):
        result, elapsed = self.run_capture('printf completed', stale=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('completed', result.stdout)
        self.assertLess(elapsed, 2)

    def test_real_exit_status_survives_stale_client(self):
        result, _ = self.run_capture('printf denied >&2; exit 7', stale=True)
        self.assertEqual(7, result.returncode)
        self.assertIn('denied', result.stdout)

    def test_actual_hang_is_bounded_and_preserves_partial_output(self):
        result, elapsed = self.run_capture('printf partial; sleep 30', seconds=1)
        self.assertEqual(124, result.returncode)
        self.assertIn('partial', result.stdout)
        self.assertLess(elapsed, 4)

    def test_native_loader_environment_is_cleared(self):
        result, _ = self.run_capture('test -z "${LD_PRELOAD:-}${LD_LIBRARY_PATH:-}"')
        self.assertEqual(0, result.returncode)

    def test_every_root_capture_uses_the_runner(self):
        source = COLLECTOR.read_text()
        self.assertEqual(1, len(re.findall(r'\bsu -c\b', source)))
        self.assertNotRegex(source, r'timeout[^\n]+su -c')
