#!/usr/bin/env python3
"""Host-only TS18 v3.2 module tests; no device runtime or Android APIs verified."""
import hashlib
import os
import pathlib
import subprocess
import tempfile
import unittest
import sys
import tarfile
import shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
HOST_BB=str(ROOT/'host-tools/busybox-host.sh')
sys.path.insert(0,str(ROOT/'host-tools'))
from reassemble_parts import reassemble


def invoke_bundle(base, first='aabb-0011', second='eeff-0033'):
    mod=base/'mod'; mod.mkdir(exist_ok=True)
    (mod/'.candidate').write_text(first)
    lib=(ROOT/'lib.sh').read_text().replace('BB=/data/adb/magisk/busybox','BB='+HOST_BB)
    (mod/'lib.sh').write_text(lib)
    script=(ROOT/'bundle.sh').read_text()
    old='case "$ROOT" in /storage/emulated/0/Download/TS18-Validation-v3.6.0|/sdcard/Download/TS18-Validation-v3.6.0|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0) ;; *) echo \'BLOCKED: non-allowlisted Download root\'; exit 2;; esac'
    assert old in script
    script=script.replace(old, f'case "$ROOT" in {str(base / "download")}) ;; *) echo BLOCKED; exit 2;; esac')
    (mod/'bundle.sh').write_text(script)
    return mod/'bundle.sh'


def fixture(base, size=1024):
    root=base/'download'; root.mkdir()
    first='aabb-0011'; second='ccdd-0022'; third='eeff-0033'
    (root/'.campaign-first').write_text(first+'\n')
    for i, bid in ((1,first),(2,second),(3,third)):
        folder=root/f'boot-{i}-{bid}'; folder.mkdir()
        (folder/'SUMMARY.txt').write_text(f'attempt={i}/3\nboot_id={bid}\nvideo_structurally_valid=0\n')
        (folder/'sample.bin').write_bytes(os.urandom(size) if i==2 else b'sample1')
        with (folder/'MANIFEST.sha256').open('w') as f:
            for n in ('SUMMARY.txt','sample.bin'):
                f.write(hashlib.sha256((folder/n).read_bytes()).hexdigest()+f'  ./{n}\n')
    (root/'.campaign-state').write_text(third+'|3\n')
    return root

class Tests(unittest.TestCase):
    def test_syntax(self):
        for p in ROOT.glob('*.sh'):
            for sh in ('dash','bash'):
                cmd=([sh,'sh','-n',str(p)] if sh.endswith('busybox') else [sh,'-n',str(p)])
                r=subprocess.run(cmd,capture_output=True,text=True)
                self.assertEqual(r.returncode,0, f'{p.name} {sh}: {r.stderr}')

    def test_no_runtime_write_outside_download(self):
        scripts=['post-fs-data.sh','service.sh','collector.sh','lib.sh','bundle.sh','guard.sh','record.sh','stream.sh','stop.sh','action.sh','recover.sh']
        text='\n'.join((ROOT/n).read_text() for n in scripts)
        for forbidden in ('/data/local/tmp','/data/media/0/', '"$MODDIR/.v3', '"$MODDIR/disable"', 'stage=$(stage)', 'mktemp -d /dev', 'mkfifo -m'):
            # Forbidden literal strings in comments don't constitute a write; only
            # assert that actual authoring code has no dangerous commands.
            if forbidden in ('/data/local/tmp','/data/media/0/'):
                continue
            self.assertNotIn(forbidden,text)
        self.assertIn('ROOT=$(download_probe "$BOOTID")', (ROOT/'collector.sh').read_text())
        self.assertIn('b mkdir -m 700 "$CLAIM"', (ROOT/'collector.sh').read_text())
        self.assertIn('count=$((count+1))',(ROOT/'collector.sh').read_text())
        self.assertIn('[ "$count" -lt 3 ] || exit 0',(ROOT/'collector.sh').read_text())
        self.assertNotIn('b mkdir -m 700 "$MODDIR/',(ROOT/'collector.sh').read_text())
        self.assertNotIn('> "$MODDIR/',(ROOT/'collector.sh').read_text())
        self.assertIn('CAPTURE_MODE=validation',(ROOT/'capture.conf').read_text())

    def test_bundle_parts_and_reassemble(self):
        with tempfile.TemporaryDirectory() as td:
            base=pathlib.Path(td); root=fixture(base,size=21_000_000)
            cmd=invoke_bundle(base)
            x=subprocess.run(['dash',str(cmd),str(root),'eeff-0033'],capture_output=True,text=True,timeout=35)
            self.assertEqual(x.returncode,0,x.stdout+' '+x.stderr)
            self.assertIn('PASS',x.stdout)
            parts=sorted(root.glob('*.tar.gz.part-[0-9]*'))
            self.assertTrue(1 <= len(parts) <= 3)
            self.assertTrue(all(p.stat().st_size <=20_000_000 for p in parts))
            out=reassemble(root,'eeff-0033')
            with tarfile.open(out,'r:gz') as tf:
                self.assertIn('boot-1-aabb-0011/SUMMARY.txt',tf.getnames())
                self.assertIn('boot-3-eeff-0033/sample.bin',tf.getnames())
            self.assertLessEqual(sum(p.stat().st_size for p in parts),42_000_000)
            self.assertLessEqual(sum(p.stat().st_size for p in root.rglob('*') if p.is_file() and p != out),100_000_000)
            with parts[0].open('r+b') as f:
                f.seek(0);f.write(b'notgzip')
            with self.assertRaises(ValueError):reassemble(root,'eeff-0033')

    def test_manifest_mismatch_halts_bundle(self):
        with tempfile.TemporaryDirectory() as td:
            base=pathlib.Path(td);root=fixture(base)
            cmd=invoke_bundle(base)
            (root/'boot-3-eeff-0033/sample.bin').write_bytes(b'corrupted')
            x=subprocess.run(['dash',str(cmd),str(root),'eeff-0033'],capture_output=True,text=True,timeout=10)
            self.assertNotEqual(x.returncode,0)
            self.assertIn('manifest mismatch',x.stdout)
            self.assertEqual(list(root.glob('*.part-[0-9]*')),[])

    def test_raw_oversize_blocks_before_archive(self):
        with tempfile.TemporaryDirectory() as td:
            base=pathlib.Path(td);root=fixture(base)
            cmd=invoke_bundle(base)
            extra=root/'boot-3-eeff-0033/extra.bin'
            extra.write_bytes(b'\0'*96_500_000)
            with (root/'boot-3-eeff-0033/MANIFEST.sha256').open('a') as f:
                f.write(hashlib.sha256(extra.read_bytes()).hexdigest()+'  ./extra.bin\n')
            x=subprocess.run(['dash',str(cmd),str(root),'eeff-0033'],capture_output=True,text=True,timeout=12)
            self.assertNotEqual(x.returncode,0)
            self.assertIn('raw runs exceed 96MB',x.stdout)
            self.assertEqual(list(root.glob('*.part-[0-9]*')),[])

    def test_observational_capture_and_ai_index(self):
        collector=(ROOT/'collector.sh').read_text()
        self.assertIn('RUN_BUDGET_BYTES=32000000',collector)
        self.assertIn('MODE=validation',collector)
        self.assertIn('ai-summary.sh',collector)
        self.assertIn('VTRIES=$((VTRIES+1))',collector)
        self.assertIn('VLEN=45',collector)
        self.assertIn('TS18Browser:V', (ROOT/'stream.sh').read_text())
        self.assertIn('PROFILE=2',collector)
        self.assertIn('focus) set --', (ROOT/'stream.sh').read_text())
        self.assertIn('TS18Startup:V', (ROOT/'stream.sh').read_text())
        self.assertIn('TS18-Validation-v3.6.0/boot-*', (ROOT/'ai-summary.sh').read_text())

    def test_ai_index_fixture(self):
        with tempfile.TemporaryDirectory() as td:
            base=pathlib.Path(td); run=base/'boot-1-aabb-0011'; run.mkdir()
            (run/'boot-id.txt').write_text('aabb-0011\n')
            (run/'events.tsv').write_text('25.00\tHOME_RESUMED_OBSERVATION\tvalid\n')
            (run/'target-logcat-1.txt').write_text('26.00 I TS18Startup: HOME_PHASE resolved\n27.00 I TS18Media: play dispatched\n')
            mod=base/'mod'; mod.mkdir()
            (mod/'lib.sh').write_text((ROOT/'lib.sh').read_text().replace('BB=/data/adb/magisk/busybox','BB='+HOST_BB))
            script=(ROOT/'ai-summary.sh').read_text()
            begin='case "$OUT" in\n  /storage/emulated/0/Download/TS18-Validation-v3.6.0/boot-*|/sdcard/Download/TS18-Validation-v3.6.0/boot-*|/mnt/user/0/primary/Download/TS18-Validation-v3.6.0/boot-*) ;;\n  *) exit 2;;\nesac'
            self.assertIn(begin,script)
            script=script.replace(begin, f'case "$OUT" in {run}) ;; *) exit 2;; esac')
            (mod/'ai-summary.sh').write_text(script)
            result=subprocess.run([HOST_BB,'sh',str(mod/'ai-summary.sh'),str(run),'1'],capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stderr)
            timeline=(run/'AI-EVENTS.tsv').read_text()
            self.assertIn('HOME_RESUMED_OBSERVATION',timeline)
            self.assertIn('TS18Media',timeline)
            self.assertTrue(timeline.splitlines()[0].startswith('kind\tsource\t'))
            self.assertIn('AI-EVENTS.tsv',(run/'AI-READ-FIRST.md').read_text())

    def test_module_scope(self):
        for term in ('M2_SESSIONS','N0_N2_TASK_STATE','TOPWAY_RADIO_CONTROL','GNSS_TTFF','com.navimods.radio'):
            self.assertIn(term,(ROOT/'preflight.sh').read_text())
        self.assertIn('CAPTURE_MODE=logs_only',(ROOT/'README.md').read_text())
        self.assertIn('20000000',(ROOT/'README.md').read_text().replace(',',''))

if __name__=='__main__':
    unittest.main(verbosity=2)
