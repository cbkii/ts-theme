from pathlib import Path
import hashlib
import re
import shutil
import subprocess
import tempfile
import unittest

def has_java_compiler():
    try:
        return subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-version'],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False

ROOT = Path(__file__).resolve().parents[1]

class RepairQualificationTests(unittest.TestCase):
    def test_embedded_payload_integrity_and_syntax(self):
        source = (ROOT / 'scripts/qualification/ts18-validate.sh').read_text()
        payloads = {name: body+'\n' for name, tag, body in re.findall(
            r'cat > "\$stage/([^"\n]+)" <<\'(TS18_EMBEDDED_PAYLOAD_\d+_END)\'\n(.*?)\n\2', source, re.S)}
        self.assertEqual(8, len(payloads))
        with tempfile.TemporaryDirectory() as tmp:
            for name, body in payloads.items():
                path = Path(tmp)/name; path.write_text(body)
                if name.endswith('.sh'): subprocess.run(['sh','-n',str(path)], check=True)
            for line in payloads['SHA256SUMS'].splitlines():
                expected, name = line.split('  ',1)
                self.assertEqual(expected, hashlib.sha256(payloads[name].encode()).hexdigest(), name)
        ids=[line.split('\t')[0] for line in payloads['cases.tsv'].splitlines() if line and not line.startswith('#')]
        for case in ['S1','S9','C1','W1','W8']: self.assertIn(case,ids)
        self.assertEqual(len(ids),len(set(ids)))

    def test_healthy_smoke_skips_unavailable_retry_and_exports(self):
        source = (ROOT / 'scripts/qualification/ts18-validate.sh').read_text()
        smoke = source[source.index('smoke_flow() {'):source.index('screen_flow() {')]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/'engine/active').mkdir(parents=True)
            cases = '\n'.join(f'S{i}\tSmoke\tStep {i}\tAction\tPass' for i in range(1,10))
            (root/'cases.tsv').write_text(cases)
            harness = r'''BB=/usr/bin/env
ROOT=$1
CASES=$ROOT/cases.tsv
ask() { IFS= read -r answer; }
start_trace() { echo TRACE; }
run() { echo "RUN $*"; }
wait_capture() { :; }
checkpoint_case() { echo UNEXPECTED_CHECKPOINT; }
'''+smoke+'\nsmoke_flow\n'
            result = subprocess.run(['sh','-c',harness,'harness',tmp],
                                    input='\n'+'p\n'*8+'n\n',text=True,capture_output=True,check=True)
            self.assertIn('RUN result S9 UNKNOWN',result.stdout)
            self.assertIn('Smoke observations passed.',result.stdout)
            self.assertIn('RUN export',result.stdout)
            self.assertNotIn('Smoke stopped.',result.stdout)
            self.assertNotIn('UNEXPECTED_CHECKPOINT',result.stdout)

    @unittest.skipUnless(has_java_compiler(), 'Java required')
    def test_service_admission_does_not_replay_uncertain_results(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)
            shutil.copyfile(ROOT/'launcher/src/main/java/com/cbkii/ts18launcher/MediaAdmissionPolicy.java',p/'MediaAdmissionPolicy.java')
            (p/'Harness.java').write_text('''package com.cbkii.ts18launcher;
public class Harness {
static void check(boolean x){if(!x)throw new AssertionError();}
public static void main(String[] a){
check(MediaAdmissionPolicy.accepted(true,0,"Starting service: Intent"));
check(!MediaAdmissionPolicy.accepted(true,0,"Security exception: Permission denied"));
check(MediaAdmissionPolicy.definitelyRejected(true,1,"Security exception: Permission denied"));
check(!MediaAdmissionPolicy.accepted(true,0,"Error: app is in background"));
check(MediaAdmissionPolicy.definitelyRejected(true,0,"Error: app is in background"));
check(!MediaAdmissionPolicy.definitelyRejected(false,-1,"root command timed out"));
check(!MediaAdmissionPolicy.definitelyRejected(true,124,"Permission Denial:"));
check(!MediaAdmissionPolicy.definitelyRejected(true,1,"Exception occurred while executing start"));
check(!MediaAdmissionPolicy.accepted(false,0,"Starting service: Intent"));
check(MediaAdmissionPolicy.definitelyRejected(false,-1,"process-not-started IOException: su missing"));
}}
''')
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',tmp,*map(str,p.glob('*.java'))],check=True)
            subprocess.run(['java','-cp',tmp,'com.cbkii.ts18launcher.Harness'],check=True)
