"""Exercise the collector's streaming cap without Android or a root transport."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/termux/qualify-consolidated-candidate.sh'

class ConsolidatedCollectorTests(unittest.TestCase):
    def test_flooding_capture_is_bounded_and_identified_as_truncated(self):
        text = SCRIPT.read_text()
        function = text.split('capture() {', 1)[1].split('\ncapture identity', 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            code = 'work=$1; status="$work/STATUS.tsv"; max_capture_bytes=1024\ncapture() {' + function
            code += '\ncapture flood python3 -c "print(chr(120)*100000)"\n'
            subprocess.run(['bash', '-c', code, 'capture-test', tmp], check=True, timeout=12)
            self.assertEqual(1024, (Path(tmp) / 'flood.txt').stat().st_size)
            self.assertIn('WARN_TRUNCATED', (Path(tmp) / 'STATUS.tsv').read_text())

    def run_framework(self, root, command):
        """Runs framework() with a fake su that records its use and stands in for toybox."""
        text = SCRIPT.read_text()
        capture = text.split('capture() {', 1)[1].split('\n# Platform tools', 1)[0]
        root_capture = text.split('root_capture() {', 1)[1].split('\n# Framework dumps', 1)[0]
        quote = next(line for line in text.splitlines() if line.startswith('quote() {'))
        framework = text.split('framework() {', 1)[1].split('\ncapture identity', 1)[0]
        # The device paths do not exist on the host; the fake su maps them to host tools.
        root_capture = root_capture.replace('/system/bin/toybox ', '').replace('/system/bin/sh', 'sh')
        with tempfile.TemporaryDirectory() as tmp:
            fake_su = Path(tmp) / 'su'
            fake_su.write_text('#!/bin/sh\nshift\necho used >> "$(dirname "$0")/su-used"\nexec sh -c "$1"\n')
            fake_su.chmod(0o700)
            # An apostrophe and a command separator in the path must stay inert under root.
            work = Path(tmp) / "it's; touch injected #"
            work.mkdir()
            code = ('work=$1; status="$work/STATUS.tsv"; max_capture_bytes=1024; root=%d\n' % root
                    + 'android_path=$PATH; plain=(env); su_bin=$3\n'
                    + 'capture() {' + capture + '\n' + quote + '\nroot_capture() {' + root_capture
                    + '\nframework() {' + framework + '\nframework probe "$2"\n')
            subprocess.run(['bash', '-c', code, 'framework-test', str(work), command, str(fake_su)],
                           check=True, timeout=30, cwd=tmp)
            self.assertEqual([], [p.name for p in Path(tmp).rglob('injected')] + [
                p.name for p in Path.cwd().glob('injected')], 'a path fragment was executed')
            return ((work / 'STATUS.tsv').read_text(), (work / 'probe.txt').read_text(),
                    (Path(tmp) / 'su-used').exists(), sorted(path.name for path in work.iterdir()))

    def test_refused_framework_capture_without_root_is_blocked_not_passed(self):
        status, output, used_su, _ = self.run_framework(0, "echo \"Permission Denial: can't dump\"")
        self.assertEqual('probe\tBLOCKED\troot-unavailable\n', status)
        self.assertIn('Permission Denial', output)
        self.assertFalse(used_su)

    def test_permitted_unprivileged_capture_keeps_its_own_result(self):
        status, _, used_su, _ = self.run_framework(0, 'echo ready')
        self.assertEqual('probe\tPASS\t0\n', status)
        self.assertFalse(used_su)

    def test_root_capture_goes_through_su_and_completes_on_the_sentinel(self):
        status, output, used_su, files = self.run_framework(1, 'echo as-root')
        self.assertEqual('probe\tPASS\t0\n', status)
        self.assertEqual('as-root\n', output)
        self.assertTrue(used_su)
        self.assertEqual(['STATUS.tsv', 'probe.txt'], files, 'command and sentinel files are removed')

    def test_root_capture_reports_the_command_status_not_the_client_lifetime(self):
        status, output, _, _ = self.run_framework(1, 'echo partial; exit 3')
        self.assertEqual('probe\tWARN\t3\n', status)
        self.assertEqual('partial\n', output)

    def test_root_capture_is_bounded_when_a_descendant_keeps_the_output_open(self):
        # The command returns at once but leaves a child holding the inherited output; completion
        # must come from the sentinel rather than from end-of-file.
        status, output, _, _ = self.run_framework(1, 'echo started; (sleep 20 &) ; exit 0')
        self.assertEqual('probe\tPASS\t0\n', status)
        self.assertEqual('started\n', output)

    def test_flooding_root_capture_is_truncated(self):
        status, output, _, _ = self.run_framework(1, 'yes x | head -c 100000')
        self.assertIn('WARN_TRUNCATED', status)
        self.assertEqual(1024, len(output))

    def test_apk_identity_is_labelled_and_fails_for_a_missing_package(self):
        text = SCRIPT.read_text()
        identity = text.split("framework apk-identity '", 1)[1].split("'\nframework launcher", 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'base.apk'
            apk.write_bytes(b'launcher')
            stub = ('pm() { [ "$2" = com.cbkii.ts18launcher ] && echo "package:%s"; return 0; }\n' % apk)
            done = subprocess.run(['sh', '-c', stub + identity], capture_output=True, text=True, timeout=10)
        lines = done.stdout.splitlines()
        self.assertEqual(1, done.returncode)
        self.assertRegex(lines[0], r'^com\.cbkii\.ts18launcher [0-9a-f]{64} .*/base\.apk$')
        self.assertEqual('com.tw.media NOT_INSTALLED', lines[1])

    def test_other_application_logs_are_never_read_unprivileged(self):
        text = SCRIPT.read_text()
        unprivileged = text.split('\nelse\n', 1)[1].split('\nfi\n', 1)[0]
        self.assertNotIn('logcat', unprivileged.replace('# An unprivileged logcat', ''))
        self.assertIn("timeline\\tBLOCKED\\troot-unavailable", unprivileged)

    def test_root_path_and_archive_integrity_are_explicit(self):
        text = SCRIPT.read_text()
        self.assertIn('android_path=/system/bin:/system/xbin:/vendor/bin:/product/bin', text)
        self.assertIn("printf 'PATH=%s\\nexport PATH\\nunset LD_PRELOAD LD_LIBRARY_PATH\\nulimit -f %s\\n%s\\n'", text)
        # Root output is never a pipe this script reads to end-of-file.
        root_capture = text.split('root_capture() {', 1)[1].split('\n# Framework dumps', 1)[0]
        self.assertNotIn('| ', root_capture.split('"$su_bin" -c', 1)[1].split('pid=$!', 1)[0])
        self.assertIn('plain=(env -u LD_PRELOAD -u LD_LIBRARY_PATH "PATH=$android_path")', text)
        self.assertIn('toybox timeout -k 1 8', text)
        self.assertIn('partial="$archive.partial"', text)
        self.assertIn('tar -tzf "$partial"', text)
        self.assertIn('trap cleanup EXIT', text)
