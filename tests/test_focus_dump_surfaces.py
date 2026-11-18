"""Run the actual shell focus gate with TS18's split display/window dump format."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'launcher/src/main/assets/nav/nav-window.sh'
TITLE = 'TS18Ready_10211_' + 'a' * 32


class FocusDumpSurfaceTest(unittest.TestCase):
    def gate(self, focus, window='', second=None):
        function = SOURCE.read_text().split('require_startup_window_focus() {', 1)[1].split('\nvalidate_startup_source()', 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/'displays').write_text('  mCurrentFocus=Window{' + focus + '}\n')
            (root/'second').write_text('  mCurrentFocus=Window{' + (second or focus) + '}\n')
            (root/'windows').write_text(window)
            script = '''ANDROID_USER=0
TS18_MASK_TITLE=''' + TITLE + '''
WINDOWS="$PWD/result"
bounded() { if [ "$3" = displays ]; then
  if [ -e "$PWD/seen" ]; then cat "$PWD/second"; else touch "$PWD/seen"; cat "$PWD/displays"; fi
else cat "$PWD/windows"; fi; }
check_deadline() { :; }
fail() { echo "FAIL $1"; exit 1; }
log_event() { :; }
require_startup_window_focus() {''' + function + '''
require_startup_window_focus com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity
'''
            return subprocess.run(['/bin/sh'], input=script, cwd=root, capture_output=True, text=True, timeout=5)

    def test_home_focus_is_in_displays_even_with_mask_capability(self):
        r = self.gate('abc u0 com.cbkii.ts18launcher/.HomeAlias')
        self.assertEqual(r.returncode, 0, r.stdout+r.stderr)

    def test_only_exact_owned_overlay_token_is_accepted(self):
        window = '  Window #0 Window{abc u0 '+TITLE+'}:\n    mOwnerUid=10211 mShowToOwnerOnly=true\n    mAttrs={ty=2038 }\n'
        r = self.gate('abc u0 '+TITLE, window)
        self.assertEqual(r.returncode, 0, r.stdout+r.stderr)
        for bad in (window.replace('10211 ', '10212 '), window.replace('2038', '2002'), window.replace('Window{abc', 'Window{def')):
            self.assertNotEqual(self.gate('abc u0 '+TITLE, bad).returncode, 0)
        self.assertNotEqual(self.gate('abc u0 '+TITLE, window, 'def u0 '+TITLE).returncode, 0)

    def test_permission_or_foreign_user_cannot_mutate(self):
        for focus in ('abc u0 com.android.permissioncontroller/.Grant', 'abc u10 com.cbkii.ts18launcher/.HomeAlias'):
            self.assertNotEqual(self.gate(focus).returncode, 0)
