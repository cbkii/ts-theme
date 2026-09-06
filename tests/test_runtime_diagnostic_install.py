import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "magisk" / "ts18-theme-runtime-diagnostic.sh"


class RuntimeDiagnosticInstallTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SCRIPT.read_text(encoding="utf-8")

    def test_install_uses_deterministic_foreground_timeout(self):
        text = self.text
        self.assertIn('INSTALL_ROOT_TIMEOUT_SECONDS=20', text)
        self.assertIn('run_install_timeout()', text)
        self.assertIn('find_install_timeout()', text)
        self.assertIn('su -c "$install_cmd"', text)
        self.assertIn('</dev/null', text)
        self.assertNotIn("uid=\"$(run_timeout 10 su -c 'id -u'", text)
        self.assertNotIn('run_timeout 15 su -c', text)
        self.assertNotIn('run_timeout 10 su -c "printf', text)

    def test_install_is_one_root_transaction_with_visible_progress(self):
        text = self.text
        install = text[text.index('install_self() {'):text.index('\nshow_status() {')]
        self.assertEqual(1, install.count('su -c "$install_cmd"'))
        self.assertIn('INSTALL[1/4]', install)
        self.assertIn('INSTALL[2/4]', install)
        self.assertIn('INSTALL[3/4]', install)
        self.assertIn('INSTALL[4/4]', install)
        self.assertIn('INSTALL_OK', install)
        self.assertIn('timed out', install)
        self.assertIn('pkg install -y coreutils', install)

    def test_generic_fallback_timeout_cannot_depend_on_epoch_clock(self):
        text = self.text
        run_timeout = text[text.index('run_timeout() {'):text.index('\ncapture() {')]
        self.assertIn('rt_elapsed=0', run_timeout)
        self.assertIn('rt_elapsed=$((rt_elapsed + 1))', run_timeout)
        self.assertNotIn('rt_start=', run_timeout)
        self.assertNotIn('rt_now=', run_timeout)


if __name__ == "__main__":
    unittest.main()
