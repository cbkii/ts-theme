#!/usr/bin/env python3
"""Bounded TS18 capture, manual observation markers, integrity and offline comparison.

Standard library only; POSIX host required. No installation, launch, playback or device reboot.
Local mode uses the existing caller (Termux, shell or root), never a su wrapper.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shlex
import shutil
import signal
import stat
import subprocess
import tempfile
import threading
import time
import uuid
import zipfile

SCHEMA = 1
ANDROID_PATH = '/system/bin:/system/xbin:/vendor/bin:/product/bin'
PACKAGES = ('com.cbkii.ts18launcher', 'com.tw.media', 'com.navimods.radio',
            'com.tw.radio', 'app.organicmaps.incar')
EVENTS = ('HOME_START', 'HOME_VISIBLE', 'HOME_TOUCH', 'MUSIC_TAP', 'MUSIC_ACK',
          'MUSIC_AUDIBLE', 'RADIO_TAP', 'RADIO_ACK', 'RADIO_AUDIBLE', 'NAV_TAP',
          'NAV_PIXELS', 'NAV_TOUCH', 'GNSS_FIX', 'DRIVING_READY')
PAIRS = (('HOME_START', 'HOME_VISIBLE'), ('HOME_START', 'HOME_TOUCH'),
         ('MUSIC_TAP', 'MUSIC_ACK'), ('MUSIC_TAP', 'MUSIC_AUDIBLE'),
         ('RADIO_TAP', 'RADIO_ACK'), ('RADIO_TAP', 'RADIO_AUDIBLE'),
         ('NAV_TAP', 'NAV_PIXELS'), ('NAV_TAP', 'NAV_TOUCH'),
         ('NAV_TAP', 'GNSS_FIX'), ('NAV_TAP', 'DRIVING_READY'))
LIFECYCLES = ('cold_boot', 'reboot', 'acc_wake', 'home_resume', 'process_restart')
SAFE_NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9_-]{0,31}\Z')
DEFAULT_STATE = (Path('/data/ts18-campaign') if os.getuid() == 0 and Path('/system/bin').exists()
                 else Path.home() / '.local/state/ts18-campaign')


def private_directory(path):
    """Reject symlinks/foreign writable ancestors before any privileged child-file write."""
    if not path.is_absolute() or '..' in path.parts:
        raise ValueError('Private state path must be absolute without parent traversal')
    trusted = {0, os.getuid()}
    if Path('/system/bin').exists():
        trusted.add(1000)  # Android system owns /data; shell/app-owned ancestors are not trusted by root.
    current = Path('/')
    for index, part in enumerate(path.parts[1:]):
        current /= part
        try:
            current.mkdir(mode=0o700)
        except FileExistsError:
            pass
        info = current.lstat()
        if not stat.S_ISDIR(info.st_mode) or info.st_uid not in trusted:
            raise ValueError('Untrusted owner or symlink in private state path')
        android_data = (Path('/system/bin').exists() and current == Path('/data')
                        and info.st_uid == 1000 and info.st_gid == 1000 and not info.st_mode & 0o002)
        if (info.st_mode & 0o022 and not (info.st_mode & stat.S_ISVTX and info.st_uid == 0)
                and not android_data):
            raise ValueError('Writable ancestor in private state path')
        if index == len(path.parts) - 2 and (info.st_uid != os.getuid() or info.st_mode & 0o077):
            raise ValueError('Private state directory must be caller-owned with mode 0700')


def private_open(path, mode):
    flags = {'a+b': os.O_RDWR | os.O_CREAT | os.O_APPEND, 'r+b': os.O_RDWR,
             'w': os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 'r': os.O_RDONLY}[mode]
    # Do not truncate until ownership, type, mode and hard-link checks have passed.
    flags &= ~os.O_TRUNC
    fd = os.open(path, flags | os.O_NOFOLLOW | os.O_CLOEXEC, 0o600)
    info = os.fstat(fd)
    if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
            or info.st_mode & 0o077 or info.st_nlink != 1):
        os.close(fd)
        raise ValueError('Untrusted private state file')
    if mode == 'w':
        os.ftruncate(fd, 0)
    return os.fdopen(fd, mode)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')


class MarkerChannel:
    """Private, file-locked markers: works without networking or Unix socket path limits."""
    def __init__(self, state):
        self.state = state
        private_directory(state)
        self.owner = private_open(state / 'collector.lock', 'a+b')
        try:
            fcntl.flock(self.owner, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            self.owner.close()
            raise ValueError('Another collector owns this campaign') from None
        self.gate = private_open(state / 'markers.lock', 'a+b')
        self.closed = False
        self.active = False
        try:
            self.drain(close=True)  # Clear old admission and queued markers under one lock.
        except Exception:
            self.gate.close()
            self.owner.close()
            raise

    def activate(self):
        fcntl.flock(self.gate, fcntl.LOCK_EX)
        try:
            with private_open(self.state / 'active', 'w') as stream:
                stream.write('1\n')
            self.active = True
        finally:
            fcntl.flock(self.gate, fcntl.LOCK_UN)

    def drain(self, close=False):
        fcntl.flock(self.gate, fcntl.LOCK_EX)
        try:
            if close:
                (self.state / 'active').unlink(missing_ok=True)
                self.active = False
            queue = self.state / 'markers.queue'
            if queue.exists() or queue.is_symlink():
                with private_open(queue, 'r') as stream:
                    retained = stream.read(65537)
                    if len(retained) > 65536:
                        raise ValueError('Marker queue exceeds byte limit; evidence remains unsealed')
                    lines = retained.splitlines()
            else:
                lines = []
            with private_open(queue, 'w'):
                pass
            values = []
            for line in lines:
                try:
                    event, submitted = line.split('\t')
                    if event in EVENTS and submitted.isdecimal():
                        values.append((event, int(submitted)))
                except ValueError:
                    pass
            return values
        finally:
            fcntl.flock(self.gate, fcntl.LOCK_UN)

    def close(self):
        if not self.closed:
            self.drain(close=True)
            self.closed = True
            self.gate.close()
            self.owner.close()

    @staticmethod
    def submit(state, event):
        if event not in EVENTS:
            raise ValueError('Unknown marker')
        submitted = time.monotonic_ns() // 1000000
        # Opening existing files only: a marker cannot create a fake active collector.
        private_directory(state)
        with private_open(state / 'collector.lock', 'r+b') as owner, private_open(state / 'markers.lock', 'r+b') as gate:
            try:
                fcntl.flock(owner, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                pass
            else:
                raise ValueError('No active collector')
            fcntl.flock(gate, fcntl.LOCK_EX)
            try:
                with private_open(state / 'active', 'r') as active:
                    admitted = active.read(16) == '1\n'
            except FileNotFoundError:
                admitted = False
            if not admitted:
                raise ValueError('Collector is not accepting markers')
            queue = state / 'markers.queue'
            value = f'{event}\t{submitted}\n'.encode()
            if queue.stat().st_size + len(value) > 65536:
                raise ValueError('Marker queue full')
            with private_open(queue, 'a+b') as stream:
                stream.write(value)


class DeviceLock:
    """One capture per host/target, across campaign names and --state-root choices."""
    def __init__(self, transport, serial):
        directory = DEFAULT_STATE / 'device-locks'
        private_directory(directory)
        key = hashlib.sha256((transport + ':' + (serial or 'local')).encode()).hexdigest()
        self.file = private_open(directory / (key + '.lock'), 'a+b')
        try:
            fcntl.flock(self.file, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            self.file.close()
            raise ValueError('Another capture owns this host/target') from None
        with private_open(directory / (key + '.target'), 'a+b') as target:
            target.seek(0)
            value = target.read(64).decode('ascii')
            if not value:
                value = uuid.uuid4().hex
                target.write(value.encode())
            if not re.fullmatch('[a-f0-9]{32}', value):
                self.file.close()
                raise ValueError('Invalid private target identity')
            self.target_id = value

    def close(self):
        self.file.close()


def redact(text):
    """Conservative text export. Video is deliberately excluded from this claim."""
    text = re.sub(r'(?i)\b(?:content|file|https?)://\S+', '[URI]', text)
    text = re.sub(r'(?i)\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b', '[EMAIL]', text)
    text = re.sub(r'(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b', '[MAC]', text)
    text = re.sub(r'\b(?:\d{1,3}\.){3}\d{1,3}\b', '[IP]', text)
    text = re.sub(r'/(?:storage|sdcard|mnt|data|home)/[^\s,;\]})]+', '[PATH]', text)
    text = re.sub(r'(?i)\b(?:title|subtitle|artist|album|description|ssid|bssid|'
                  r'address|destination|latitude|longitude|lat|lon|lng)\s*[=:].*',
                  '[PRIVATE_VALUE]', text)
    text = re.sub(r'\b-?\d{1,3}\.\d{4,}\s*[, ]\s*-?\d{1,3}\.\d{4,}\b',
                  '[COORDINATES]', text)
    # Location dumps often contain prose addresses/coordinates without stable keys.
    return '\n'.join('[LOCATION_VALUE]' if 'Location[' in line else line
                     for line in text.splitlines()) + ('\n' if text.endswith('\n') else '')


class Result:
    def __init__(self, status, rc=None, output=b'', elapsed_ms=0, truncated=False):
        self.status, self.rc, self.output = status, rc, output
        self.elapsed_ms, self.truncated = elapsed_ms, truncated

    def metadata(self):
        result = dict(status=self.status, exit_code=self.rc, elapsed_ms=self.elapsed_ms,
                      truncated=self.truncated)
        if hasattr(self, 'native_owner'):
            result.update(native_owner=self.native_owner, native_completions=self.native_completions)
        return result


def terminate_group(process):
    """Only a process group created by this collector; never pkill or a discovered app PID."""
    for sig, grace in ((signal.SIGINT, 1.5), (signal.SIGKILL, 1)):
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=grace)
        except subprocess.TimeoutExpired:
            continue
        if sig == signal.SIGKILL:
            return
        # The group leader may exit before a descendant closes its inherited pipe.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        return


def bounded(argv, seconds=6, limit=512 * 1024, cancel=None, line_callback=None, env=None, before_stop=None):
    """Drain without unbounded RAM/disk; deadline also covers orphaned inherited pipes."""
    start = time.monotonic()
    try:
        process = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   stdin=subprocess.DEVNULL, start_new_session=True, env=env)
    except OSError as error:
        return Result('BLOCKED', output=str(error).encode())
    output, pending = bytearray(), bytearray()
    total = 0
    status = 'PASS'
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    try:
        while selector.get_map():
            if cancel is not None and cancel.is_set():
                status = 'INTERRUPTED'
                break
            if time.monotonic() - start >= seconds:
                status = 'TIMEOUT'
                break
            for key, _ in selector.select(0.05):
                block = os.read(key.fileobj.fileno(), 16384)
                if not block:
                    selector.unregister(key.fileobj)
                    continue
                total += len(block)
                output.extend(block[:max(0, limit - len(output))])
                if line_callback is not None:
                    pending.extend(block)
                    while b'\n' in pending:
                        line, _, rest = pending.partition(b'\n')
                        pending[:] = rest
                        line_callback(line.decode('utf-8', 'replace'))
                    # A malformed producer cannot grow an unterminated line indefinitely.
                    if len(pending) > 65536:
                        pending.clear()
        if status == 'PASS':
            try:
                process.wait(timeout=max(0.01, seconds - (time.monotonic() - start)))
            except subprocess.TimeoutExpired:
                status = 'TIMEOUT'
    finally:
        if before_stop is not None and status in ('INTERRUPTED', 'TIMEOUT'):
            before_stop()
        terminate_group(process)
        selector.close()
        process.stdout.close()
    if status == 'PASS' and process.returncode:
        status = 'BLOCKED'
    return Result(status, process.returncode, bytes(output),
                  round((time.monotonic() - start) * 1000), total > limit)


class Device:
    """Explicit caller/transport; no automatic elevation or ADB device selection."""
    def __init__(self, transport, serial=None):
        self.transport = transport
        self.adb = ['adb', '-s', serial] if serial else None
        self.env = dict(os.environ)
        for name in ('LD_PRELOAD', 'LD_LIBRARY_PATH'):
            self.env.pop(name, None)

    def argv(self, command):
        if self.transport == 'adb':
            return self.adb + ['exec-out', '/system/bin/sh', '-c', shlex.quote(command)]
        return ['/system/bin/sh', '-c', command]

    def read(self, args, seconds=6, limit=512 * 1024, deadline=None):
        # Native deadline also bounds a target-side process if ADB disconnects.
        allowance = seconds + 2 if deadline is None else min(seconds + 2, deadline - time.monotonic())
        if allowance <= 0:
            return Result('BLOCKED', output=b'Phase deadline exhausted')
        native_seconds = min(seconds, max(0.05, allowance - 1))
        command = 'PATH=' + ANDROID_PATH + ' exec /system/bin/toybox timeout -s KILL '
        command += str(native_seconds) + ' ' + shlex.join(args)
        return bounded(self.argv(command), allowance, limit, env=self.env)

    def clock(self, deadline=None):
        before = time.monotonic_ns() // 1000000
        result = self.read(['/system/bin/cat', '/proc/uptime'], seconds=2, limit=256, deadline=deadline)
        after = time.monotonic_ns() // 1000000
        try:
            if result.status != 'PASS':
                raise ValueError()
            uptime = round(float(result.output.split()[0]) * 1000)
        except (ValueError, IndexError, OverflowError):
            uptime = None
        return dict(device_uptime_ms=uptime, host_before_ms=before, host_after_ms=after,
                    uncertainty_ms=after - before, status='PASS' if uptime is not None else 'BLOCKED')

    def boot(self):
        result = self.read(['/system/bin/cat', '/proc/sys/kernel/random/boot_id'], limit=128)
        value = result.output.decode('ascii', 'replace').strip()
        return value if result.status == 'PASS' and re.fullmatch(r'[0-9a-f-]{36}', value) else None

    def worker(self, args, seconds, cancel, limit=512 * 1024):
        """Own a target-side session, including over ADB/Magisk-independent client loss.

        The native leader stays alive until its command finishes. A short lease hold allows
        checked group cleanup; its own timeout and exit remain bounded if transport is lost.
        """
        if cancel.is_set():
            return Result('NOT_RUN'), 'PASS'
        lease = []
        completions = []
        cleanup_proof = []

        def observe(line):
            match = re.fullmatch(r'TS18_OWNER (\d+) (\d+)', line)
            if match and not lease:
                lease.extend(map(int, match.groups()))
            completed = re.fullmatch(r'TS18_DONE (-?\d+)', line)
            if completed:
                completions.append(int(completed.group(1)))

        def cleanup_native():
            if not lease:
                cleanup_proof.append('BLOCKED')
                return
            pid, ticks = lease
            check = f'''set -f
export LC_ALL=C
if ids=$(/system/bin/sed 's/.*) //' /proc/{pid}/stat 2>/dev/null); then
  set -- $ids
  [ "$3 $4" = "{pid} {pid}" ] || exit 5
  shift 19
  [ "$1" = "{ticks}" ] || exit 6
  /system/bin/kill -INT -- -{pid} || exit 7
  /system/bin/sleep 1
  if ids=$(/system/bin/sed 's/.*) //' /proc/{pid}/stat 2>/dev/null); then
    set -- $ids
    [ "$3 $4" = "{pid} {pid}" ] || exit 5
    shift 19
    [ "$1" = "{ticks}" ] || exit 6
    /system/bin/kill -KILL -- -{pid} || exit 7
  fi
fi
# A vanished leader alone is insufficient: require ESRCH for the whole owned group.
for attempt in 1 2 3 4 5; do
  if error=$(/system/bin/kill -0 -- -{pid} 2>&1); then
    /system/bin/sleep 0.2
  else
    case "$error" in *'No such process'*) printf 'TS18_STOPPED\\n'; exit 0 ;; esac
    exit 8
  fi
done
exit 9
'''
            stopped = bounded(self.argv(check), 5, 2048, env=self.env)
            cleanup_proof.append('PASS' if stopped.status == 'PASS'
                                 and b'TS18_STOPPED' in stopped.output else 'BLOCKED')

        body = '''
set -f
umask 077
# Keep the registered leader available for identity-checked cleanup after interruption.
trap ':' INT TERM HUP
ids=$(/system/bin/sed 's/.*) //' /proc/$$/stat) || exit 70
set -- $ids
[ "$3 $4" = "$$ $$" ] || exit 70
shift 19
printf 'TS18_OWNER %s %s\n' "$$" "$1"
'''
        # Shell-quoted argv is data. Neither markers nor device output become shell commands.
        # --foreground keeps the producer in the verified leader's group. Without it,
        # timeout creates a second group which would escape cancellation of this lease.
        body += '/system/bin/toybox timeout --foreground -s INT -k 2 ' + str(seconds) + ' ' + shlex.join(args)
        body += '\nrc=$?\nprintf "TS18_DONE %s\\n" "$rc"\n/system/bin/sleep 2\nexit "$rc"\n'
        command = 'PATH=' + ANDROID_PATH + ' exec /system/bin/toybox setsid -w /system/bin/sh -c '
        command += shlex.quote(body)
        result = bounded(self.argv(command), seconds + 5, limit, cancel, observe, self.env,
                         before_stop=cleanup_native)
        if not cleanup_proof:
            cleanup_native()
        cleanup = cleanup_proof[-1]
        result.native_owner, result.native_completions = lease, completions
        expected_log_stop = (args[0] == '/system/bin/logcat' and completions
                             and completions[-1] == 124 and result.elapsed_ms >= seconds * 1000)
        if expected_log_stop and result.status == 'BLOCKED':
            result.status = 'PASS'
        if result.status == 'PASS' and (not completions or (completions[-1] != 0 and not expected_log_stop)):
            result.status = 'BLOCKED'
        # Retain protocol in metadata, not in the selected application event stream.
        result.output = re.sub(rb'^TS18_(?:OWNER \d+ \d+|DONE -?\d+)\n', b'',
                               result.output, flags=re.MULTILINE)
        return result, cleanup

    def pull(self, remote, local):
        return bounded(self.adb + ['pull', remote, str(local)], 20, 4096, env=self.env)


def snapshot(device, root, phase):
    """Targeted snapshots supplement, and stay outside, the timed recording phase."""
    probes = {
        'caller': ['/system/bin/id'],
        'selinux': ['/system/bin/id', '-Z'],
        'namespace': ['/system/bin/readlink', '/proc/self/ns/mnt'],
        'android_user': ['/system/bin/cmd', 'activity', 'get-current-user'],
        'home': ['/system/bin/cmd', 'package', 'resolve-activity', '--brief',
                 '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'],
        'display': ['/system/bin/wm', 'size'],
        'density': ['/system/bin/wm', 'density'],
        'boot_completed': ['/system/bin/getprop', 'sys.boot_completed'],
        'build': ['/system/bin/getprop', 'ro.build.fingerprint'],
        'history': ['/system/bin/logcat', '-d', '-t', '1200', '-v', 'monotonic', '-s',
                    'TS18Media:I', 'TS18Nav:I', 'TS18Launcher:I', 'InCarCompactStartup:I', '*:S'],
        'media': ['/system/bin/dumpsys', 'media_session'],
        'audio': ['/system/bin/dumpsys', 'audio'],
        'tasks': ['/system/bin/dumpsys', 'activity', 'activities'],
        'windows': ['/system/bin/dumpsys', 'window', 'windows'],
        'frames': ['/system/bin/dumpsys', 'gfxinfo', PACKAGES[0]],
        'memory': ['/system/bin/dumpsys', 'meminfo', PACKAGES[0]],
        'location': ['/system/bin/dumpsys', 'location'],
    }
    for package in PACKAGES:
        probes['package_' + package] = ['/system/bin/dumpsys', 'package', package]
    directory = root / phase
    directory.mkdir()
    statuses = []
    until = time.monotonic() + 30
    for name, args in probes.items():
        remaining = int(until - time.monotonic())
        result = (device.read(args, seconds=min(6, remaining), deadline=until) if remaining > 0 else
                  Result('BLOCKED', output=b'Snapshot budget exhausted; not a target failure'))
        text = result.output.decode('utf-8', 'replace')
        # Some vendor dumpsys commands print a denial but return zero.
        denied = re.search(r'(?i)permission denial|permission denied|not allowed|securityexception', text)
        status = result.metadata()
        if denied:
            status['status'] = 'BLOCKED'
        if result.truncated and status['status'] == 'PASS':
            status['status'] = 'PARTIAL'
        status.update(name=name, role='OPTIONAL', argv=args)
        statuses.append(status)
        (directory / (name + '.txt')).write_text(redact(text), encoding='utf-8')
    write_json(directory / 'STATUS.json', statuses)


def package_identities(device):
    """Hash the bytes visible to the actual PM caller; unreadable identity stays UNKNOWN."""
    until = time.monotonic() + 30
    user = ''
    for command in (['/system/bin/cmd', 'activity', 'get-current-user'],
                    ['/system/bin/am', 'get-current-user']):
        result = device.read(command, seconds=2, limit=128, deadline=until)
        value = result.output.decode('ascii', 'replace').strip()
        if result.status == 'PASS' and value.isdecimal():
            user = value
            break
    if not user:
        return dict(android_user=None, status='BLOCKED', packages={})
    identities = {}
    for package in PACKAGES:
        info = dict(status='BLOCKED', apk_sha256=[])
        identities[package] = info
        if time.monotonic() >= until:
            continue
        paths = device.read(['/system/bin/pm', 'path', '--user', user, package], seconds=2, limit=4096,
                            deadline=until)
        if paths.status != 'PASS' or paths.truncated:
            continue
        values = paths.output.decode('utf-8', 'replace').splitlines()
        if not values or len(values) > 8 or any(not v.startswith('package:/') for v in values):
            continue
        hashes = device.read(['/system/bin/sha256sum'] + [v[8:] for v in values], seconds=2, limit=4096,
                             deadline=until)
        found = re.findall(r'^([a-f0-9]{64})  ', hashes.output.decode('utf-8', 'replace'), re.MULTILINE)
        if hashes.status == 'PASS' and len(found) == len(values) and not hashes.truncated:
            info.update(status='PASS', apk_sha256=sorted(found))
    return dict(android_user=int(user), status='PASS', packages=identities)


def record_segments(device, root, duration, cancel, enabled, segment_seconds=150, deadline=None):
    records = []
    if not enabled:
        return [dict(status='NOT_RUN', reason='operator selected --no-video')]
    started = time.monotonic()
    deadline = started + duration if deadline is None else deadline
    previous_end = None
    while not cancel.is_set():
        remaining = deadline - time.monotonic()
        if remaining < 1:
            break
        name = f'segment-{len(records) + 1:02d}.mp4'
        local = root / name
        local.touch(mode=0o600)
        # ADB writes only its unique file in its caller-owned temporary directory.
        remote = f'/data/local/tmp/ts18-campaign-{uuid.uuid4().hex}.mp4'
        destination = remote if device.transport == 'adb' else str(local)
        start = device.clock(deadline=deadline)
        remaining = deadline - time.monotonic()
        if remaining < 1 or cancel.is_set():
            local.unlink()
            break
        seconds = max(1, min(segment_seconds, int(remaining)))
        segment_start = time.monotonic()
        result, cleanup = device.worker(['/system/bin/screenrecord', '--size', '1280x720',
                                         '--bit-rate', '2000000', '--time-limit', str(seconds),
                                         destination], seconds + 2, cancel)
        end = device.clock(deadline=deadline)
        info = dict(file=name, **result.metadata(), cleanup=cleanup, start=start, end=end,
                    requested_seconds=seconds, requested_size='1280x720', requested_bitrate=2000000,
                    requested_fps=None, observed_fps=None, audio='NOT_CAPTURED',
                    gap_host_ms=None if previous_end is None else round((segment_start - previous_end) * 1000))
        (root / (name + '.stderr.txt')).write_text(redact(result.output.decode('utf-8', 'replace')))
        if device.transport == 'adb':
            if cleanup == 'PASS':
                info['pull'] = device.pull(remote, local).metadata()
                info['remote_cleanup'] = device.read(['/system/bin/rm', '-f', remote]).metadata()
                if info['pull']['status'] != 'PASS':
                    info['status'] = 'BLOCKED'
                if info['remote_cleanup']['status'] != 'PASS':
                    info.update(status='BLOCKED', cleanup='BLOCKED',
                                reason='Private remote recording cleanup failed')
            else:
                info['pull'] = dict(status='BLOCKED', reason='producer termination unproven')
        if cleanup == 'PASS' and info.get('cleanup') == 'PASS' and local.stat().st_size:
            info.update(size_bytes=local.stat().st_size, sha256=digest(local))
        elif not local.stat().st_size:
            local.unlink()
            info['status'] = 'BLOCKED'
        if device.transport == 'adb' and info.get('cleanup') != 'PASS':
            info['remote_private_path'] = remote
        write_json(root / (name + '.json'), info)
        records.append(info)
        previous_end = time.monotonic()
        # An unavailable recorder is one failed prerequisite, never 300 repeated starts.
        if info['status'] != 'PASS' or cleanup != 'PASS':
            break
    return records


def seal(root):
    """Exact manifest coverage, after callers have closed every writer."""
    paths = sorted(p for p in root.rglob('*') if p.is_file() and p.name != 'MANIFEST.sha256')
    if any(p.is_symlink() for p in root.rglob('*')):
        raise ValueError('symlink in evidence')
    (root / 'MANIFEST.sha256').write_text(''.join(
        f'{digest(p)}  {p.relative_to(root).as_posix()}\n' for p in paths))
    expected = {p.relative_to(root).as_posix(): digest(p) for p in paths}
    for path, sha in expected.items():
        if digest(root / path) != sha:
            raise ValueError('writer changed evidence during sealing')
    archive = root.with_suffix('.zip')
    with zipfile.ZipFile(archive, 'x') as zipped:
        for path in sorted(root.rglob('*')):
            if path.is_file():
                zipped.write(path, path.relative_to(root), compress_type=(
                    zipfile.ZIP_STORED if path.suffix == '.mp4' else zipfile.ZIP_DEFLATED))
    verify(archive)
    return archive


def verify(archive):
    """Inspect without extraction. Bound sizes; require complete manifest coverage."""
    with zipfile.ZipFile(archive) as zipped:
        items = zipped.infolist()
        names = [i.filename for i in items]
        if len(items) > 500 or len(names) != len(set(names)):
            raise ValueError('duplicate members or file budget exceeded')
        for item in items:
            name = item.filename
            if (not re.fullmatch(r'[A-Za-z0-9._/-]+', name) or name.startswith('/') or '\\' in name
                    or any(part in ('', '.', '..') for part in name.split('/'))
                    or item.is_dir() or item.external_attr >> 16 & 0o170000 not in (0, 0o100000)):
                raise ValueError('unsafe member')
            if item.file_size > 100 * 1024 * 1024:
                raise ValueError('member budget exceeded')
        if sum(i.file_size for i in items) > 250 * 1024 * 1024:
            raise ValueError('archive budget exceeded')
        for name in ('MANIFEST.sha256', 'trial.json', 'markers.json'):
            if zipped.getinfo(name).file_size > 1024 * 1024:
                raise ValueError('metadata budget exceeded')
        manifest = zipped.read('MANIFEST.sha256').decode('utf-8')
        hashes = {}
        for line in manifest.splitlines():
            sha, name = line.split('  ', 1)
            if not re.fullmatch('[a-f0-9]{64}', sha) or name in hashes:
                raise ValueError('invalid manifest')
            hashes[name] = sha
        if set(hashes) != set(names) - {'MANIFEST.sha256'}:
            raise ValueError('incomplete manifest coverage')
        for name, expected in hashes.items():
            h = hashlib.sha256()
            with zipped.open(name) as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    h.update(block)
            if h.hexdigest() != expected:
                raise ValueError('hash mismatch: ' + name)
        metadata = json.loads(zipped.read('trial.json'))
        markers = json.loads(zipped.read('markers.json'))
        if (not isinstance(metadata, dict) or type(metadata.get('schema')) is not int
                or metadata.get('schema') != SCHEMA or metadata.get('writers_closed') is not True):
            raise ValueError('unsealed or incompatible trial')
        if (not isinstance(metadata.get('campaign'), str) or not SAFE_NAME.fullmatch(metadata['campaign'])
                or type(metadata.get('trial')) is not int or not 1 <= metadata['trial'] <= 5
                or metadata.get('operator_reported_lifecycle') not in LIFECYCLES
                or metadata.get('workload') not in ('idle', 'music', 'radio', 'navigation', 'combined')
                or not isinstance(metadata.get('boot_id'), str)
                or metadata.get('target_continuity') not in ('PASS', 'BLOCKED')):
            raise ValueError('invalid trial metadata')
        for key in ('trial_id', 'target_id'):
            if key in metadata and (not isinstance(metadata[key], str)
                                    or not re.fullmatch('[a-f0-9]{32}', metadata[key])):
                raise ValueError('invalid trial or target identity')
        if not isinstance(markers, list) or len(markers) > 10000:
            raise ValueError('invalid marker list')
        for marker in markers:
            if (not isinstance(marker, dict) or marker.get('event') not in EVENTS
                    or (marker.get('device_uptime_ms') is not None and
                        (type(marker['device_uptime_ms']) is not int or marker['device_uptime_ms'] < 0))
                    or type(marker.get('uncertainty_ms')) is not int or marker['uncertainty_ms'] < 0
                    or type(marker.get('submission_delay_ms', 0)) is not int
                    or marker.get('submission_delay_ms', 0) < 0):
                raise ValueError('invalid marker observation')
        return metadata, markers


def export(archive, output, campaign, trial):
    output.mkdir(parents=True, exist_ok=True)
    group = output / f'{campaign}-trial-{trial:02d}'
    if group.exists() or group.is_symlink():
        raise ValueError('trial already exported; choose another trial/campaign')
    # Stage both files, then atomically publish the complete directory on this filesystem.
    # A power loss during copying cannot reserve the final trial name with a partial ZIP.
    staging = Path(tempfile.mkdtemp(prefix='.ts18-export-', dir=output))
    name = f'{campaign}-trial-{trial:02d}.zip'
    try:
        destination = staging / name
        with destination.open('xb') as stream, archive.open('rb') as source:
            shutil.copyfileobj(source, stream)
        if digest(destination) != digest(archive):
            raise ValueError('export hash mismatch')
        verify(destination)
        with destination.with_suffix('.zip.sha256').open('x') as sidecar:
            sidecar.write(f'{digest(destination)}  {destination.name}\n')
        if group.exists() or group.is_symlink():
            raise ValueError('trial was exported concurrently')
        staging.rename(group)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return group / name


def capture(args):
    os.umask(0o077)
    state = args.state_root / args.campaign
    device_lock = DeviceLock(args.transport, args.serial)
    try:
        channel = MarkerChannel(state)
    except Exception:
        device_lock.close()
        raise
    stop = threading.Event()
    producer_stop = threading.Event()
    previous = {}

    def interrupted(_signum, _frame):
        stop.set()
        producer_stop.set()

    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        previous[signum] = signal.signal(signum, interrupted)
    private = Path(tempfile.mkdtemp(prefix='ts18-campaign-', dir=state))
    device = Device(args.transport, args.serial)
    markers, records, logs = [], [], []
    workers = []
    try:
        boot = device.boot()
        anchor = device.clock()
        if boot is None or anchor['status'] != 'PASS':
            raise ValueError('BLOCKED: target boot/clock unavailable; no device trial started')
        if shutil.disk_usage(private).free < 256 * 1024 * 1024:
            raise ValueError('BLOCKED: less than 256 MiB free private storage')
        identities = package_identities(device)
        fingerprint = device.read(['/system/bin/getprop', 'ro.build.fingerprint'], seconds=2, limit=1024)
        build = fingerprint.output.decode('utf-8', 'replace').strip() if fingerprint.status == 'PASS' else None
        snapshot(device, private, 'before')
        started = device.clock()
        host_start = time.monotonic()
        phase_deadline = host_start + args.duration
        channel.activate()
        print(f'Capturing {args.duration}s; marker channel ready for {args.campaign}.', flush=True)

        def video():
            try:
                records.extend(record_segments(device, private, args.duration, producer_stop, not args.no_video,
                                               deadline=phase_deadline))
            except Exception as error:
                records.append(dict(status='BLOCKED', cleanup='BLOCKED',
                                    reason=type(error).__name__))
                stop.set()

        def log():
            try:
                result, cleanup = device.worker(['/system/bin/logcat', '-T', '1', '-v', 'monotonic',
                                                '-s', 'TS18Media:I', 'TS18Nav:I', 'TS18Launcher:I',
                                                'InCarCompactStartup:I', '*:S'],
                                                max(1, int(phase_deadline - time.monotonic())),
                                                producer_stop, 4 * 1024 * 1024)
                (private / 'events.log').write_text(redact(result.output.decode('utf-8', 'replace')))
                logs.append(dict(**result.metadata(), cleanup=cleanup, role='OPTIONAL'))
            except Exception as error:
                logs.append(dict(status='BLOCKED', cleanup='BLOCKED', reason=type(error).__name__))
                stop.set()

        workers = [threading.Thread(target=video), threading.Thread(target=log)]
        for worker in workers:
            worker.start()
        def accept(values):
            for name, submitted in values:
                observation = device.clock(deadline=phase_deadline)
                observation.update(event=name, source='manual_operator',
                                   operator_submitted_host_ms=submitted,
                                   submission_delay_ms=max(0, observation.get('host_after_ms', submitted) - submitted),
                                   host_elapsed_ms=round((time.monotonic() - host_start) * 1000))
                markers.append(observation)
                print('Marker: ' + name, flush=True)
        while not stop.is_set() and time.monotonic() - host_start < args.duration:
            accept(channel.drain())
            stop.wait(0.2)
        interrupted_run = stop.is_set()
        producer_stop.set()
        # Closing admission precedes joining every writer and computing any hash.
        accept(channel.drain(close=True))
        for worker in workers:
            worker.join(args.duration + 10)
        if any(w.is_alive() for w in workers):
            raise ValueError('BLOCKED: collector worker did not terminate; evidence unsealed')
        write_json(private / 'markers.json', markers)
        write_json(private / 'segments.json', records)
        cleanup_ok = all(r.get('cleanup', 'PASS') == 'PASS' for r in records + logs)
        if not cleanup_ok:
            raise ValueError('BLOCKED: target producer cleanup unproven; evidence unsealed')
        end = device.clock()
        end_boot = device.boot()
        end_identities = package_identities(device) if not interrupted_run else {}
        end_fingerprint = device.read(['/system/bin/getprop', 'ro.build.fingerprint'], seconds=2, limit=1024)
        end_build = (end_fingerprint.output.decode('utf-8', 'replace').strip()
                     if end_fingerprint.status == 'PASS' else None)
        if not interrupted_run:
            snapshot(device, private, 'after')
        write_json(private / 'trial.json', dict(
            schema=SCHEMA, trial_id=uuid.uuid4().hex, campaign=args.campaign, trial=args.trial,
            operator_reported_lifecycle=args.lifecycle, workload=args.workload,
            boot_id=boot, end_boot_id=end_boot, clock_anchor=anchor, start=started, end=end,
            target_continuity='PASS' if end_boot == boot else 'BLOCKED',
            transport=args.transport, local_caller_uid=os.getuid(),
            toolkit_sha256=digest(Path(__file__)), capture_duration_seconds=args.duration,
            interrupted=interrupted_run, writers_closed=True, logs=logs,
            installed_identity=identities,
            end_installed_identity=end_identities,
            identity_stability='PASS' if identities == end_identities and identities else 'BLOCKED',
            target_id=device_lock.target_id, android_build=build,
            build_stability='PASS' if build and build == end_build else 'BLOCKED',
            physical_qualification='NOT_RUN', video_privacy='REVIEW_BEFORE_SHARING',
            timing='manual observations; device clock round-trip uncertainty recorded'))
        archive = seal(private)
        try:
            destination = export(archive, args.out, args.campaign, args.trial)
        except (OSError, ValueError) as error:
            raise ValueError(f'Export unavailable: {error}; verified private archive: {archive}') from error
        print(f'Exported {destination}\nSHA256 {digest(destination)}', flush=True)
        return 130 if interrupted_run else 0
    finally:
        stop.set()
        producer_stop.set()
        for worker in workers:
            worker.join(args.duration + 10)
        channel.close()
        device_lock.close()
        for signum, handler in previous.items():
            signal.signal(signum, handler)
        print(f'Private evidence retained: {private}', flush=True)


def compare(paths):
    rows, groups, seen = [], {}, set()
    for path in paths:
        metadata, markers = verify(path)
        trial_id = metadata.get('trial_id') or digest(path)
        if trial_id in seen:
            continue
        seen.add(trial_id)
        row = dict(archive=path.name, campaign=metadata['campaign'], trial=metadata['trial'],
                   boot_id=metadata['boot_id'], lifecycle=metadata['operator_reported_lifecycle'],
                   workload=metadata['workload'], target_continuity=metadata['target_continuity'],
                   metrics_ms={}, missing=[], missing_occurrences={}, observations=markers)
        identities = metadata.get('installed_identity', {})
        if not isinstance(identities, dict):
            identities = {}
        packages = identities.get('packages', {})
        if not isinstance(packages, dict):
            packages = {}
        row['installed_identity'] = identities
        row['target_id'], row['android_build'] = metadata.get('target_id'), metadata.get('android_build')
        row['comparison_identity'] = ('VERIFIED' if packages and all(
            isinstance(item, dict) and item.get('status') == 'PASS'
            and isinstance(item.get('apk_sha256'), list) and 1 <= len(item['apk_sha256']) <= 8
            and all(isinstance(sha, str) and re.fullmatch('[a-f0-9]{64}', sha) for sha in item['apk_sha256'])
            for item in packages.values())
            and type(identities.get('android_user')) is int
            and identities == metadata.get('end_installed_identity')
            and metadata['target_continuity'] == 'PASS'
            and metadata.get('identity_stability') == 'PASS' and metadata.get('build_stability') == 'PASS'
            and isinstance(metadata.get('android_build'), str) and metadata['android_build']
            and metadata.get('target_id') else 'UNKNOWN')
        # Repeated actions are paired in order. Never overwrite occurrences or reuse one start.
        for begin, end in PAIRS:
            waiting = []
            intervals = []
            unusable = 0
            for marker in markers:
                if marker['event'] == begin:
                    waiting.append(marker)
                elif marker['event'] == end and waiting:
                    first = waiting.pop(0)
                    a, b = first.get('device_uptime_ms'), marker.get('device_uptime_ms')
                    if (a is not None and b is not None and b >= a
                            and metadata['target_continuity'] == 'PASS'):
                        intervals.append(dict(ms=b - a, uncertainty_ms=(
                            first['uncertainty_ms'] + marker['uncertainty_ms']),
                            manual_submission_delay_ms=(first.get('submission_delay_ms', 0)
                                                        + marker.get('submission_delay_ms', 0))))
                    else:
                        unusable += 1
            key = begin + '->' + end
            if intervals:
                row['metrics_ms'][key] = intervals
            if waiting or unusable or not intervals:
                row['missing'].append(key)
            if waiting or unusable:
                row['missing_occurrences'][key] = dict(unmatched_starts=len(waiting),
                                                       unusable_intervals=unusable)
        rows.append(row)
        # Never imply comparability between unverified/mixed candidate bytes.
        identity_key = (json.dumps([identities, metadata['target_id'], metadata['android_build']], sort_keys=True)
                        if row['comparison_identity'] == 'VERIFIED' else str(path))
        groups.setdefault((row['lifecycle'], row['workload'], identity_key), []).append(row)
    return dict(schema=SCHEMA, trials=rows, sample_count=len(rows),
                comparable_groups=[dict(lifecycle=k[0], workload=k[1], sample_count=len(v),
                                        identity=(dict(installed_identity=v[0]['installed_identity'],
                                                       target_id=v[0]['target_id'], android_build=v[0]['android_build'])
                                                  if v[0]['comparison_identity'] == 'VERIFIED' else 'UNKNOWN'),
                                        trials=[dict(archive=r['archive'], trial=r['trial']) for r in v])
                                   for k, v in groups.items()],
                qualification='NOT_RUN: observations require human evidence review',
                statistics='Individual samples only; no inferred p95 or GNSS/audio success')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='operation', required=True)
    cap = sub.add_parser('capture')
    cap.add_argument('--transport', choices=('local', 'adb'), required=True)
    cap.add_argument('--serial', help='exact ADB serial; required for adb')
    cap.add_argument('--campaign', required=True)
    cap.add_argument('--trial', type=int, choices=range(1, 6), required=True)
    cap.add_argument('--lifecycle', choices=LIFECYCLES, required=True)
    cap.add_argument('--workload', choices=('idle', 'music', 'radio', 'navigation', 'combined'),
                     default='combined')
    cap.add_argument('--duration', type=int, default=180)
    cap.add_argument('--no-video', action='store_true')
    cap.add_argument('--out', type=Path, default=Path('/storage/emulated/0/Download/ts-theme/campaigns'))
    for target in (cap, sub.add_parser('mark')):
        target.add_argument('--state-root', type=Path,
                            default=DEFAULT_STATE)
    mark = sub.choices['mark']
    mark.add_argument('--campaign', required=True)
    mark.add_argument('event', choices=EVENTS)
    check = sub.add_parser('verify')
    check.add_argument('archive', type=Path)
    comp = sub.add_parser('compare-runs')
    comp.add_argument('archives', type=Path, nargs='+')
    args = parser.parse_args(argv)
    if args.operation in ('capture', 'mark') and not SAFE_NAME.fullmatch(args.campaign):
        parser.error('campaign must be 1..32 ASCII letters/digits/underscore/hyphen')
    if args.operation == 'capture':
        if not 30 <= args.duration <= 300:
            parser.error('duration must be 30..300 seconds')
        if args.transport == 'adb' and not args.serial:
            parser.error('adb requires an explicit --serial')
        if not args.state_root.is_absolute() or not args.out.is_absolute():
            parser.error('state-root and out must be absolute')
    try:
        if args.operation == 'capture':
            return capture(args)
        if args.operation == 'mark':
            MarkerChannel.submit(args.state_root / args.campaign, args.event)
            print('Marker submitted; the collector records its observation timestamp.')
        elif args.operation == 'verify':
            verify(args.archive)
            print('PASS: retained bytes verified; physical qualification is separate')
        else:
            print(json.dumps(compare(args.archives), indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(str(error))
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
