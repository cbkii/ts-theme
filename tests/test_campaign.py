"""Run real process/integrity tests and simulated Android/transport failure paths."""
import argparse
import contextlib
import importlib.util
import io
import json
import os
import re
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/diagnostics/campaign/campaign.py'
spec = importlib.util.spec_from_file_location('campaign', SCRIPT)
campaign = importlib.util.module_from_spec(spec)
spec.loader.exec_module(campaign)


class RunnerTest(unittest.TestCase):
    def test_failed_prerequisite_is_blocked_with_actual_status(self):
        result = campaign.bounded([sys.executable, '-c', 'print("denied"); exit(7)'])
        self.assertEqual(('BLOCKED', 7), (result.status, result.rc))
        self.assertIn(b'denied', result.output)
        self.assertEqual('BLOCKED', campaign.bounded(['/missing/ts18-command']).status)

    def test_output_flood_is_drained_but_retention_is_capped(self):
        result = campaign.bounded([sys.executable, '-c', 'print("x" * 1000000)'], limit=1024)
        self.assertEqual('PASS', result.status)
        self.assertTrue(result.truncated)
        self.assertEqual(1024, len(result.output))

    def test_hang_preserves_partial_output_and_deadline(self):
        start = time.monotonic()
        result = campaign.bounded([sys.executable, '-u', '-c',
                                   'import time; print("partial"); time.sleep(30)'], seconds=0.2)
        self.assertEqual('TIMEOUT', result.status)
        self.assertIn(b'partial', result.output)
        self.assertLess(time.monotonic() - start, 4)

    def test_orphan_inherited_pipe_cannot_defeat_deadline(self):
        result = campaign.bounded([sys.executable, '-c',
            'import subprocess,sys; subprocess.Popen([sys.executable,"-c",'
            '"import time; time.sleep(30)"]); print("parent done", flush=True)'], seconds=0.2)
        self.assertEqual('TIMEOUT', result.status)
        self.assertIn(b'parent done', result.output)

    def test_cancellation_joins_owned_writer(self):
        stop = threading.Event()
        timer = threading.Timer(0.15, stop.set)
        timer.start()
        try:
            result = campaign.bounded([sys.executable, '-c', 'import time; time.sleep(30)'],
                                      seconds=20, cancel=stop)
        finally:
            timer.cancel()
        self.assertEqual('INTERRUPTED', result.status)


class DeviceTest(unittest.TestCase):
    def test_transport_is_explicit_and_native_loader_environment_is_clean(self):
        with mock.patch.dict(os.environ, {'LD_PRELOAD': 'termux-exec.so', 'LD_LIBRARY_PATH': '/termux'}):
            device = campaign.Device('adb', 'exact-device')
        self.assertNotIn('LD_PRELOAD', device.env)
        self.assertNotIn('LD_LIBRARY_PATH', device.env)
        self.assertEqual(['adb', '-s', 'exact-device', 'exec-out'], device.argv('id')[:4])
        self.assertNotIn('su', campaign.Device('local').argv('id'))

    def test_bad_clock_is_unknown_even_when_command_returns_zero(self):
        device = campaign.Device('local')
        for value in (b'permission denied', b'NaN', b'', b'inf'):
            with mock.patch.object(device, 'read', return_value=campaign.Result('PASS', 0, value)):
                self.assertEqual('BLOCKED', device.clock()['status'])
        with mock.patch.object(device, 'read', return_value=campaign.Result('PASS', 0, b'123.45 88.1')):
            self.assertEqual(123450, device.clock()['device_uptime_ms'])

    def test_normal_log_deadline_is_expected_not_a_target_failure(self):
        device = campaign.Device('local')

        def run(_args, *_positional, **_kwargs):
            _positional[3]('TS18_OWNER 123 456')
            return campaign.Result('BLOCKED', 124, b'TS18_DONE 124\n')

        with mock.patch.object(campaign, 'bounded', side_effect=run):
            result, cleanup = device.worker(['/system/bin/logcat'], 30, threading.Event())
        self.assertEqual(('PASS', 'PASS'), (result.status, cleanup))

    def test_setup_refusal_seals_blocked_evidence_without_claiming_a_producer(self):
        device = campaign.Device('local')
        with mock.patch.object(campaign, 'bounded', return_value=campaign.Result('BLOCKED', 127)):
            result, cleanup = device.worker(['/system/bin/screenrecord'], 30, threading.Event())
        self.assertEqual(('BLOCKED', 'PASS'), (result.status, cleanup))

    def test_interrupted_worker_without_identity_cannot_claim_cleanup(self):
        device = campaign.Device('local')
        with mock.patch.object(campaign, 'bounded', return_value=campaign.Result('INTERRUPTED')):
            result, cleanup = device.worker(['/system/bin/screenrecord'], 30, threading.Event())
        self.assertEqual('BLOCKED', cleanup)

    def test_cancelled_before_start_creates_no_process(self):
        stop = threading.Event()
        stop.set()
        with mock.patch.object(campaign, 'bounded') as run:
            result, cleanup = campaign.Device('local').worker([], 30, stop)
        self.assertEqual(('NOT_RUN', 'PASS'), (result.status, cleanup))
        run.assert_not_called()

    def test_package_hash_failure_does_not_establish_package_absence(self):
        device = mock.Mock()
        device.read.side_effect = [campaign.Result('PASS', 0, b'0\n')] + [
            campaign.Result('BLOCKED', 1, b'Permission Denial')] * len(campaign.PACKAGES)
        identities = campaign.package_identities(device)
        self.assertEqual(0, identities['android_user'])
        self.assertTrue(all(p['status'] == 'BLOCKED' for p in identities['packages'].values()))
        self.assertTrue(all(not p['apk_sha256'] for p in identities['packages'].values()))

    def test_package_splits_hashes_are_retained_without_exporting_paths(self):
        device = mock.Mock()
        packages = len(campaign.PACKAGES)
        device.read.side_effect = [campaign.Result('PASS', 0, b'10\n')] + [
            item for _ in range(packages) for item in (
                campaign.Result('PASS', 0, b'package:/data/app/base.apk\npackage:/data/app/split.apk\n'),
                campaign.Result('PASS', 0, (('a' * 64 + '  /data/app/base.apk\n')
                                           + ('b' * 64 + '  /data/app/split.apk\n')).encode()))]
        identities = campaign.package_identities(device)
        self.assertEqual(10, identities['android_user'])
        self.assertEqual(['a' * 64, 'b' * 64], identities['packages'][campaign.PACKAGES[0]]['apk_sha256'])
        self.assertNotIn('/data/app', json.dumps(identities))


@unittest.skipUnless(shutil.which('setsid') and shutil.which('timeout') and
                     int(Path('/proc/self/stat').read_text().split()[0]) == os.getpid(),
                     'Native worker fixture requires a matching Linux /proc namespace')
class NativeWorkerTest(unittest.TestCase):
    def device(self):
        device = campaign.Device('local')

        def argv(command):
            # Execute the exact worker and cleanup programs with Linux equivalents of Android
            # tools. This exercises the lease indices, shell quoting, timeout and real cleanup.
            command = command.replace('/system/bin/toybox timeout', shutil.which('timeout'))
            command = command.replace('/system/bin/toybox setsid', shutil.which('setsid'))
            command = command.replace('/system/bin/', '/usr/bin/')
            command = command.replace(campaign.ANDROID_PATH, '/usr/bin:/bin')
            return ['/bin/sh', '-c', command]

        device.argv = argv
        return device

    def test_native_worker_completes_and_reports_real_exit(self):
        result, cleanup = self.device().worker(
            ['/system/bin/sh', '-c', 'printf "native complete\\n"; exit 7'], 1, threading.Event())
        self.assertEqual(('BLOCKED', 'PASS'), (result.status, cleanup))
        self.assertIn(b'TS18_OWNER', result.output)
        self.assertIn(b'TS18_DONE 7', result.output)
        self.assertIn(b'native complete', result.output)

    def test_native_worker_interrupts_only_its_owned_group(self):
        stop = threading.Event()
        timer = threading.Timer(0.25, stop.set)
        timer.start()
        try:
            result, cleanup = self.device().worker(
                ['/system/bin/sh', '-c', 'printf "partial\\n"; sleep 30'], 30, stop)
        finally:
            timer.cancel()
        self.assertEqual('INTERRUPTED', result.status)
        self.assertEqual('PASS', cleanup)
        self.assertIn(b'partial', result.output)
        owner = int(re.search(rb'TS18_OWNER (\d+)', result.output).group(1))
        live = []
        for entry in Path('/proc').iterdir():
            if not entry.name.isdecimal():
                continue
            try:
                stat = (entry / 'stat').read_text().rsplit(') ', 1)[1].split()
                if int(stat[2]) == owner and stat[0] != 'Z':
                    live.append(entry.name)
            except (OSError, IndexError, ValueError):
                pass
        self.assertEqual([], live, 'no native writer/descendant may outlive cleanup')


class RecorderTest(unittest.TestCase):
    def test_no_recorder_is_one_blocked_prerequisite_not_repeated_attempts(self):
        device = mock.Mock(transport='local')
        device.worker.return_value = (campaign.Result('BLOCKED', 1, b'recorder unavailable'), 'PASS')
        with tempfile.TemporaryDirectory() as temp:
            result = campaign.record_segments(device, Path(temp), 300, threading.Event(), True)
            self.assertEqual(1, len(result))
            self.assertEqual('BLOCKED', result[0]['status'])
        self.assertEqual(1, device.worker.call_count)

    def test_segment_limit_and_unknown_fps_are_honest(self):
        device = mock.Mock(transport='local')
        device.clock.return_value = dict(device_uptime_ms=123, uncertainty_ms=1)
        stop = threading.Event()

        def worker(args, seconds, cancel):
            self.assertLessEqual(int(args[args.index('--time-limit') + 1]), 150)
            Path(args[-1]).write_bytes(b'synthetic MP4 bytes')
            cancel.set()
            return campaign.Result('PASS', 0), 'PASS'

        device.worker.side_effect = worker
        with tempfile.TemporaryDirectory() as temp:
            result = campaign.record_segments(device, Path(temp), 300, stop, True)
        self.assertIsNone(result[0]['observed_fps'])
        self.assertEqual('NOT_CAPTURED', result[0]['audio'])
        self.assertEqual(19, result[0]['size_bytes'])

    def test_video_opt_out_starts_nothing(self):
        device = mock.Mock()
        result = campaign.record_segments(device, Path('/unused'), 300, threading.Event(), False)
        self.assertEqual('NOT_RUN', result[0]['status'])
        device.worker.assert_not_called()


class IntegrityTest(unittest.TestCase):
    def make_trial(self, root, markers=None, **changes):
        root.mkdir()
        metadata = dict(schema=1, writers_closed=True, campaign='baseline', trial=1,
                        boot_id='boot-A', operator_reported_lifecycle='cold_boot', workload='combined',
                        target_continuity='PASS', physical_qualification='NOT_RUN')
        metadata.update(changes)
        campaign.write_json(root / 'trial.json', metadata)
        campaign.write_json(root / 'markers.json', markers or [])
        (root / 'events.log').write_text('synthetic fixture\n')
        return campaign.seal(root)

    def test_manifest_covers_retained_bytes_and_export_matches(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = self.make_trial(Path(temp) / 'private')
            metadata, markers = campaign.verify(archive)
            self.assertEqual('NOT_RUN', metadata['physical_qualification'])
            target = campaign.export(archive, Path(temp) / 'out', 'example', 1)
            self.assertEqual(campaign.digest(archive), campaign.digest(target))
            original = target.read_bytes()
            with self.assertRaises(ValueError):
                campaign.export(archive, target.parent, 'example', 1)
            self.assertEqual(original, target.read_bytes())

    def test_unmanifested_and_traversal_members_are_rejected_without_extraction(self):
        for name in ('extra.txt', '../escape', '/escape', 'bad\\name'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temp:
                archive = self.make_trial(Path(temp) / 'private')
                with zipfile.ZipFile(archive, 'a') as zipped:
                    zipped.writestr(name, b'bad')
                with self.assertRaises(ValueError):
                    campaign.verify(archive)
                self.assertFalse((Path(temp) / 'escape').exists())

    def test_duplicate_and_changed_members_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = self.make_trial(Path(temp) / 'private')
            bad = Path(temp) / 'bad.zip'
            with zipfile.ZipFile(archive) as source, zipfile.ZipFile(bad, 'w') as target:
                for name in source.namelist():
                    target.writestr(name, b'changed' if name == 'events.log' else source.read(name))
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                campaign.verify(bad)

    def test_unclosed_writers_and_symlinks_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, 'unsealed'):
                self.make_trial(Path(temp) / 'bad', writers_closed=False)
            root = Path(temp) / 'link'
            root.mkdir()
            (root / 'secret').symlink_to('/etc/passwd')
            with self.assertRaisesRegex(ValueError, 'symlink'):
                campaign.seal(root)

    def test_repeated_events_are_distinct_and_missing_phases_stay_missing(self):
        markers = [dict(event=event, device_uptime_ms=ms, uncertainty_ms=2)
                   for event, ms in [('MUSIC_TAP', 100), ('MUSIC_ACK', 180),
                                     ('MUSIC_TAP', 300), ('MUSIC_ACK', 410)]]
        with tempfile.TemporaryDirectory() as temp:
            archive = self.make_trial(Path(temp) / 'private', markers)
            report = campaign.compare([archive])
        row = report['trials'][0]
        self.assertEqual([80, 110], [i['ms'] for i in row['metrics_ms']['MUSIC_TAP->MUSIC_ACK']])
        self.assertIn('NAV_TAP->GNSS_FIX', row['missing'])
        self.assertEqual(1, report['sample_count'])
        self.assertNotIn('p95', row)

    def test_boot_change_blocks_all_cross_epoch_latency_claims(self):
        markers = [dict(event=event, device_uptime_ms=ms, uncertainty_ms=2)
                   for event, ms in [('NAV_TAP', 100), ('NAV_PIXELS', 200)]]
        with tempfile.TemporaryDirectory() as temp:
            archive = self.make_trial(Path(temp) / 'private', markers, target_continuity='BLOCKED')
            report = campaign.compare([archive])
        self.assertEqual({}, report['trials'][0]['metrics_ms'])

    def test_text_privacy_filters_preserve_state_but_remove_identity_values(self):
        raw = ('state=3\ntitle=private music\nLocation[gps -35.12345,149.12345]\n'
               '/storage/usbdisk0/secret.mp3 a@example.com 00:11:22:aa:bb:cc\n')
        text = campaign.redact(raw)
        self.assertIn('state=3', text)
        for private in ('private music', '-35.12345', 'secret.mp3', 'a@example.com', '00:11:22'):
            self.assertNotIn(private, text)


class CaptureTest(unittest.TestCase):
    def args(self, root):
        return argparse.Namespace(state_root=root / 'state', campaign='fixture', trial=1,
            transport='local', serial=None, duration=30, lifecycle='home_resume', workload='combined',
            no_video=True, out=root / 'export')

    def fake_device(self):
        device = mock.Mock()
        device.boot.return_value = '12345678-1234-1234-1234-123456789abc'
        device.clock.return_value = dict(status='PASS', device_uptime_ms=1000, uncertainty_ms=1)
        return device

    def test_interruption_closes_writers_before_sealing_and_rejects_late_markers(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            device = self.fake_device()

            def log(_args, _seconds, stop, _limit):
                stop.set()
                time.sleep(0.05)  # The seal must wait for this producer's retained bytes.
                return campaign.Result('INTERRUPTED', -2, b'last event\n'), 'PASS'

            device.worker.side_effect = log
            with mock.patch.object(campaign, 'Device', return_value=device), \
                    mock.patch.object(campaign, 'package_identities', return_value={}), \
                    mock.patch.object(campaign, 'snapshot'), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(130, campaign.capture(self.args(root)))
            archive = next((root / 'export').glob('*.zip'))
            metadata, _ = campaign.verify(archive)
            self.assertTrue(metadata['interrupted'])
            with zipfile.ZipFile(archive) as zipped:
                self.assertIn(b'last event', zipped.read('events.log'))
            with self.assertRaisesRegex(ValueError, 'No active collector'):
                campaign.MarkerChannel.submit(root / 'state/fixture', 'HOME_TOUCH')

    def test_marker_channel_is_exclusive_and_does_not_replay_previous_trials(self):
        with tempfile.TemporaryDirectory() as temp:
            state = Path(temp)
            channel = campaign.MarkerChannel(state)
            try:
                with self.assertRaisesRegex(ValueError, 'not accepting'):
                    campaign.MarkerChannel.submit(state, 'HOME_TOUCH')
                channel.activate()
                campaign.MarkerChannel.submit(state, 'HOME_VISIBLE')
                campaign.MarkerChannel.submit(state, 'HOME_TOUCH')
                self.assertEqual(['HOME_VISIBLE', 'HOME_TOUCH'], [v[0] for v in channel.drain()])
                self.assertEqual([], channel.drain())
                with self.assertRaisesRegex(ValueError, 'Another collector'):
                    campaign.MarkerChannel(state)
                channel.drain(close=True)
                with self.assertRaisesRegex(ValueError, 'not accepting'):
                    campaign.MarkerChannel.submit(state, 'HOME_TOUCH')
            finally:
                channel.close()

    def test_unproven_cleanup_preserves_private_evidence_and_refuses_archive(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            device = self.fake_device()

            def log(_args, _seconds, stop, _limit):
                stop.set()
                return campaign.Result('INTERRUPTED'), 'BLOCKED'

            device.worker.side_effect = log
            with mock.patch.object(campaign, 'Device', return_value=device), \
                    mock.patch.object(campaign, 'package_identities', return_value={}), \
                    mock.patch.object(campaign, 'snapshot'), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(ValueError, 'cleanup unproven'):
                    campaign.capture(self.args(root))
            self.assertFalse((root / 'export').exists())
            self.assertTrue(list((root / 'state/fixture').glob('ts18-campaign-*')))

    def test_missing_transport_blocks_target_dependants_before_any_recording(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            device = self.fake_device()
            device.boot.return_value = None
            with mock.patch.object(campaign, 'Device', return_value=device), \
                    mock.patch.object(campaign, 'package_identities', return_value={}), \
                    mock.patch.object(campaign, 'snapshot') as snapshot, \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(ValueError, 'boot/clock unavailable'):
                    campaign.capture(self.args(root))
            snapshot.assert_not_called()
            device.worker.assert_not_called()


class ArgumentsTest(unittest.TestCase):
    def test_invalid_duration_trial_and_ambiguous_adb_are_rejected(self):
        base = [sys.executable, str(SCRIPT), 'capture', '--campaign', 'fixture',
                '--lifecycle', 'cold_boot', '--trial', '1', '--transport', 'adb']
        for extra in ([], ['--serial', 'one', '--duration', '301'],
                      ['--serial', 'one', '--trial', '6'], ['--serial', 'one', '--campaign', '../x']):
            result = subprocess.run(base + extra, capture_output=True, text=True)
            self.assertEqual(2, result.returncode)


if __name__ == '__main__':
    unittest.main()
