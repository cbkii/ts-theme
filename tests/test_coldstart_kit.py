"""Host execution of the shipped shell functions; no Android device is claimed."""
import hashlib
import io
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import tarfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1] / 'scripts/diagnostics/coldstart'
BOOT = (ROOT / 'ts18-coldstart-boot.sh').read_text()
GUIDE = (ROOT / 'ts18-coldstart-validate.sh').read_text()

# This execution host exposes synthetic /proc/<pid>/stat sessions (SID=1 even
# after setsid). Substitute only the identity I/O boundary in test copies, using
# actual kernel getpgid/getsid syscalls. Production keeps the strict /proc check.
HOST_IDENTITY = r'''
identity() {
  /usr/bin/python3 -c 'import os,sys
p=int(sys.argv[1])
try:
 os.kill(p,0)
 print(1,os.getpgid(p),os.getsid(p))
except (OSError,ValueError,IndexError): sys.exit(1)' "$1"
}
'''


class KitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.base = self.root / 'Download/TS18-ColdStart'
        self.base.mkdir(parents=True)
        self.bb = self.root / 'busybox-api-shim'
        self.bb.write_text('''#!/bin/bash
applet=$1; shift
case "$applet" in
 id) if [[ "${1:-}" == -u ]]; then printf '%s\\n' "${TS18_TEST_UID:-0}"; else exec /usr/bin/id "$@"; fi;;
 sh) exec /bin/bash "$@";;
 kill) exec /usr/bin/python3 -c 'import os,signal,sys
s=getattr(signal,"SIG"+sys.argv[1][1:]); os.kill(int(sys.argv[2]),s)' "$@";;
 *) exec "/usr/bin/$applet" "$@";;
esac
''')
        self.bb.chmod(0o700)
        self.boot = self.root / 'collector.sh'
        self.boot.write_text(self.adapt(BOOT).replace('\ncase "${1:-}" in\n  --worker)',
                                                   HOST_IDENTITY+'\ncase "${1:-}" in\n  --worker)'))
        self.guide = self.root / 'guide.sh'
        self.guide.write_text(self.adapt(GUIDE))

    def adapt(self, source):
        return source.replace('/data/adb/magisk/busybox', str(self.bb)).replace(
            '/storage/emulated/0/Download', str(self.root / 'Download'))

    def boot_functions(self):
        return self.adapt(BOOT[BOOT.index('# BEGIN PRIMITIVES'):BOOT.index('\ncase "${1:-}" in\n  --worker)')])

    def guide_functions(self):
        return self.adapt(GUIDE[GUIDE.index('die() {'):GUIDE.index('\nbase_ready\ncase')])

    def shell(self, body, guide=False, timeout=10, input=''):
        script = f'''BB='{self.bb}'
BASE='{self.base}'
SELF='{self.boot}'
COLLECTOR='{self.boot}'
'''+(self.guide_functions() if guide else self.boot_functions()+HOST_IDENTITY)+'\n'+body
        return subprocess.run(['/bin/bash', '-c', script], input=input, text=True,
                              capture_output=True, timeout=timeout)

    def test_both_scripts_parse_and_help_without_root(self):
        for path in [ROOT / 'ts18-coldstart-boot.sh', ROOT / 'ts18-coldstart-validate.sh']:
            subprocess.run(['/bin/sh', '-n', str(path)], check=True)
            r = subprocess.run(['/bin/sh', str(path), 'help'], capture_output=True, text=True, check=True)
            self.assertIn('Download', r.stdout)

    def test_unprivileged_device_identity_is_refused_before_capture(self):
        # The CI runner is unprivileged; the shim normally models Magisk UID0.
        # Exercise the actual production guard separately without requiring host root.
        for path in (self.boot, self.guide):
            result = subprocess.run(['/bin/bash', str(path)], capture_output=True,
                                    env={**os.environ, 'TS18_TEST_UID': '1000'}, timeout=3)
            self.assertEqual(77, result.returncode)
        self.assertFalse(list(self.base.glob('run-*')))

    def test_installed_source_and_double_copy_below_100000_bytes(self):
        size = len(BOOT.encode())
        self.assertLessEqual(size, 48000)
        self.assertLess(size * 2, 100000)
        self.assertNotRegex(BOOT, r'>\s*[/"\']*/data/adb')
        self.assertNotIn('mkfifo', BOOT + GUIDE)  # Emulated storage need not support FIFOs.
        self.assertIn('EXPECTED_COLLECTOR='+hashlib.sha256(BOOT.encode()).hexdigest(), GUIDE)

    def test_no_app_priming_or_state_mutation(self):
        text = BOOT + GUIDE
        for forbidden in [r'\bam\s+start\b', r'\bam\s+force-stop\b', r'\bmonkey\b',
                          r'\bcontent\s+(query|call)\b', r'\binput\s+keyevent\b',
                          r'\bsettings\s+(put|delete)\b', r'\blogcat\s+-c\b',
                          r'\bsetprop\b', r'\bresetprop\b', r'\bpm\s+(clear|grant|disable|uninstall)\b']:
            self.assertNotRegex(text, forbidden)

    def test_monotonic_clock_and_process_identity(self):
        r = self.shell('a=$(ms); sleep 0.1; b=$(ms); [ "$b" -gt "$a" ] && identity $$')
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(3, len(r.stdout.split()))

    def test_budget_refuses_excessive_outputs_and_probe_count(self):
        r = self.shell('RESERVED=0; COUNT=0; reserve 100663296; ! reserve 1; COUNT=180; ! reserve 0')
        self.assertEqual(0, r.returncode, r.stderr)

    def test_output_path_rejects_outside_and_symlink(self):
        outside = self.root / 'elsewhere'; outside.mkdir()
        inside = self.base / 'run-test'; inside.mkdir()
        r = self.shell(f'! safe_dir "{outside}" && safe_dir "{inside}"')
        self.assertEqual(0, r.returncode, r.stderr)
        inside.rmdir(); inside.symlink_to(outside, target_is_directory=True)
        r = self.shell(f'! safe_dir "{inside}"')
        self.assertEqual(0, r.returncode)

    def capture(self, command, cap=1024, budget=2):
        out = self.base / 'run-test'; (out / 'probes').mkdir(parents=True)
        body = f'''OUT='{out}'
WORK_END=$(( $(ms) + 9000 )); RESERVED=0; COUNT=0
: > "$OUT/workers.tsv"
probe probe {cap} {budget} {command}
'''
        result = self.shell(body, timeout=12)
        self.assertEqual(0, result.returncode, result.stderr)
        return out / 'probes/1-probe'

    def test_real_producer_exit_code_and_bounded_output(self):
        path = self.capture('/usr/bin/python3 -c \'import sys;sys.stdout.write("x"*1000000)\'', cap=2048)
        self.assertEqual(2048, (path / 'output.txt').stat().st_size)
        self.assertIn('result=TRUNCATED', (path / 'meta.txt').read_text())

    def test_command_failure_is_not_success(self):
        path = self.capture('/bin/bash -c \'echo permission-denied;exit 17\'')
        meta = (path / 'meta.txt').read_text()
        self.assertIn('rc=17', meta)
        self.assertIn('result=COMMAND_FAILED', meta)

    def test_hung_descendant_is_killed_with_group(self):
        sentinel = self.root / 'LEAK'
        command = f"/bin/bash -c 'trap \"\" TERM; (/usr/bin/sleep 4; echo leaked > {sentinel}) & wait'"
        begin = time.monotonic()
        path = self.capture(command, budget=1)
        self.assertLess(time.monotonic() - begin, 3)
        self.assertIn('TIMEOUT_OR_STOP', (path / 'meta.txt').read_text())
        time.sleep(4)
        self.assertFalse(sentinel.exists())

    def test_worker_watchdog_cleans_up_after_supervisor_loss(self):
        out = self.base / 'run-watch'; out.mkdir()
        sentinel = self.root / 'WATCHDOG_LEAK'
        p = subprocess.Popen(['/bin/bash', str(self.boot), '--worker', str(out), '1024', '1',
                              '/bin/bash', '-c', f'(/usr/bin/sleep 5; echo leak > "{sentinel}") & wait'],
                             start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        try:
            p.communicate(timeout=5)
            self.assertEqual(-signal.SIGKILL, p.returncode)
            time.sleep(2.2)
            self.assertFalse(sentinel.exists())
        finally:
            if p.poll() is None:
                os.killpg(p.pid, signal.SIGKILL)

    def test_no_capture_when_download_is_unavailable(self):
        # Shorten only the timing constants in an isolated test copy.
        copy = self.boot.read_text().replace('480000', '3500').replace('HARD_END - 12000', 'HARD_END - 800')
        self.boot.write_text(copy)
        (self.root / 'Download').rename(self.root / 'Unavailable')
        now = int(float(Path('/proc/uptime').read_text().split()[0]) * 1000)
        p = subprocess.Popen(['/bin/bash', str(self.boot), '--collect', str(now), str(now + 3500), 'manual'],
                             start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        p.communicate(timeout=5)
        self.assertNotEqual(0, p.returncode)
        self.assertFalse((self.root / 'Download').exists())

    def test_accelerated_full_collector_deadline_and_early_probe_suppression(self):
        copy = self.boot.read_text().replace('480000', '5500').replace('HARD_END - 12000', 'HARD_END - 1500')
        self.boot.write_text(copy)
        now = int(float(Path('/proc/uptime').read_text().split()[0]) * 1000)
        begin = time.monotonic()
        p = subprocess.Popen(['/bin/bash', str(self.boot), '--collect', str(now), str(now + 5500), 'manual'],
                             start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        p.communicate(timeout=8)
        self.assertLess(time.monotonic() - begin, 6.5)
        runs = list(self.base.glob('run-manual-*'))
        self.assertEqual(1, len(runs))
        self.assertIn('status=CLOSED', (runs[0] / 'STATUS.txt').read_text())
        self.assertTrue((runs[0] / 'COMPLETE.txt').is_file())
        self.assertFalse(list((runs[0] / 'probes').glob('*-activities')))
        self.assertFalse((self.base / 'active').exists())

    def test_skip_cannot_record_pass(self):
        session = self.base / 'validation-test'; session.mkdir()
        r = self.shell(f"SESSION='{session}'; trial T 'Action' 'Accept'", guide=True, input='n\n')
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn('NOT_RUN_OR_UNKNOWN', (session / 'results.tsv').read_text())
        self.assertNotIn('PASS_FUNCTIONAL', (session / 'results.tsv').read_text())

    def test_slow_functional_success_is_not_a_functional_failure(self):
        session = self.base / 'validation-test'; session.mkdir()
        r = self.shell(f"SESSION='{session}'; record M1", guide=True, input='s\n\n')
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn('SUCCESS_SLOW', (session / 'results.tsv').read_text())

    def test_note_is_data_not_executed(self):
        session = self.base / 'validation-test'; session.mkdir()
        sentinel = self.root / 'INJECTED'
        note = f'$(touch {sentinel})'
        r = self.shell(f"SESSION='{session}'; record M1", guide=True, input='p\n'+note+'\n')
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertFalse(sentinel.exists())
        self.assertIn(note, (session / 'results.tsv').read_text())

    def test_processed_requests_are_not_replayed(self):
        self.assertIn('*.processed*|*.pending*) continue', BOOT)

    def test_precise_package_private_paths(self):
        self.assertIn('ts18_launcher.xml', BOOT)
        self.assertNotIn("ps_files='app.organicmaps.incar_preferences.xml'", BOOT)
        self.assertNotIn('nav-window.sh status', BOOT + GUIDE)

    def test_production_proc_parser_with_spaces_parentheses_and_zombie(self):
        fixture = self.root / 'fixture'; fixture.mkdir()
        fields = ['S', '20', '31', '32'] + [str(n) for n in range(5, 20)] + ['9917', '200', '30']
        (fixture / 'stat').write_text('77 (name with ) brackets) ' + ' '.join(fields)+'\n')
        funcs = self.boot_functions().replace('/proc/$1/stat', str(fixture / 'stat'))
        r = subprocess.run(['/bin/bash', '-c', funcs+'\nidentity 77'], text=True, capture_output=True)
        self.assertEqual('9917 31 32', r.stdout.strip())
        (fixture / 'stat').write_text('77 (zombie) ' + ' '.join(['Z']+fields[1:])+'\n')
        r = subprocess.run(['/bin/bash', '-c', funcs+'\nidentity 77'], text=True, capture_output=True)
        self.assertNotEqual(0, r.returncode)

    def test_live_current_boot_owner_prevents_duplicate_capture(self):
        active = self.base / 'active'; active.mkdir()
        boot_id = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        (active / 'owner').write_text(f'{boot_id} {os.getpid()} 1 run-manual-existing\n')
        now = int(float(Path('/proc/uptime').read_text().split()[0]) * 1000)
        r = subprocess.run(['/bin/bash', str(self.boot), '--collect', str(now), str(now+480000), 'manual'],
                           capture_output=True, timeout=3, start_new_session=True)
        self.assertEqual(-signal.SIGKILL, r.returncode)
        self.assertFalse(list(self.base.glob('run-*')))

    def test_stop_request_prevents_new_probe(self):
        active = self.base / 'active'; active.mkdir(); (active / 'STOP').touch()
        r = self.shell('probe never 1024 1 /usr/bin/true')
        self.assertNotEqual(0, r.returncode)
        self.assertFalse(list(self.base.glob('run-*')))

    def test_data_control_request_is_atomic_and_preserves_processed_file(self):
        out = self.base / 'run-manual-control'; (out / 'requests').mkdir(parents=True)
        r = self.shell(f'''is_active() {{ RP='{out}'; return 0; }}
request MARK M1_ARMED 0
mv "$LAST_REQUEST" "$LAST_REQUEST.processed"
request QUIET M1_QUIET 0
''', guide=True)
        self.assertEqual(0, r.returncode, r.stderr)
        files = list((out / 'requests').iterdir())
        self.assertEqual(2, len(files))
        self.assertEqual(1, sum(p.name.endswith('.processed') for p in files))
        self.assertFalse(any(p.name.startswith('.') for p in files))

    def test_archive_contains_only_selected_run_and_available_optional_results(self):
        run = self.base / 'run-manual-pack'; run.mkdir(); (run / 'CONTEXT.txt').write_text('test')
        other = self.base / 'run-manual-other'; other.mkdir(); (other / 'secret').write_text('exclude')
        r = subprocess.run(['/bin/bash', str(self.guide), '--pack', run.name], capture_output=True, timeout=5)
        self.assertEqual(0, r.returncode, r.stderr)
        with tarfile.open(fileobj=io.BytesIO(r.stdout), mode='r:gz') as tf:
            self.assertEqual([run.name, run.name+'/CONTEXT.txt'], tf.getnames())
        v = self.base / 'validation-test'; v.mkdir(); (v / 'results.tsv').write_text('N')
        (self.base / 'INSTALL.txt').write_text('receipt')
        r = subprocess.run(['/bin/bash', str(self.guide), '--pack', run.name], capture_output=True, timeout=5)
        self.assertEqual(0, r.returncode, r.stderr)
        with tarfile.open(fileobj=io.BytesIO(r.stdout), mode='r:gz') as tf:
            self.assertIn('validation-test/results.tsv', tf.getnames())
            self.assertIn('INSTALL.txt', tf.getnames())
            self.assertFalse(any('other' in n for n in tf.getnames()))

    def test_verified_install_idempotence_size_and_unknown_file_refusal(self):
        adb = self.root / 'magisk-adb'; adb.mkdir()
        service = adb / 'service.d/97-ts18-coldstart.sh'
        digest = hashlib.sha256(self.boot.read_bytes()).hexdigest()
        funcs = self.guide_functions().replace('/data/adb', str(adb))
        prefix = f"BB='{self.bb}'; BASE='{self.base}'; COLLECTOR='{self.boot}'; SERVICE='{service}'; EXPECTED_COLLECTOR={digest}\n"
        r = subprocess.run(['/bin/bash', '-c', prefix+funcs+'\ninstall; install'], capture_output=True, text=True, timeout=5)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(self.boot.read_bytes(), service.read_bytes())
        self.assertLess(sum(p.stat().st_size for p in adb.rglob('*') if p.is_file()),100000)
        self.assertEqual(0o755,service.stat().st_mode & 0o777)
        service.write_text('unknown existing script')
        r = subprocess.run(['/bin/bash', '-c', prefix+funcs+'\ninstall'], capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, r.returncode)
        self.assertEqual('unknown existing script', service.read_text())

    def test_failed_hashing_cannot_create_completion_seal(self):
        out = self.base / 'run-hash-failure'; out.mkdir()
        self.bb.write_text(self.bb.read_text().replace('case "$applet" in', 'case "$applet" in\n sha256sum) exit 55;;'))
        r = self.shell(f'''OUT='{out}'; FINISHING=0; RESERVED=0; HARD_END=$(( $(ms) + 5000 )); finish''')
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertFalse((out / 'COMPLETE.txt').exists())
        self.assertIn('status=CLOSED', (out / 'STATUS.txt').read_text())


if __name__ == '__main__':
    unittest.main()
