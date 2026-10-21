"""Execute the production TESTING resolver with denied optional label permissions."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]


class TestingRequestAdmissionTest(unittest.TestCase):
    def test_optional_label_failure_preserves_only_authorised_same_repo_requests(self):
        if not shutil.which('jq'):
            self.skipTest('jq required to execute production resolver')
        workflow = (ROOT / '.github/workflows/testing-apk-draft.yml').read_text()
        script = textwrap.dedent(workflow.split('run: |', 1)[1].split('\n      - name:', 1)[0])
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            gh = directory / 'gh'
            gh.write_text('#!' + sys.executable + '\n' + textwrap.dedent('''\
                import json,os,sys
                if any('/pulls/' in arg for arg in sys.argv):
                    print(json.dumps({'head':{'repo':{'full_name':os.environ['MOCK_HEAD_REPO']},
                        'sha':'a'*40,'ref':'candidate'},'base':{'sha':'b'*40,'ref':'main'}}))
                elif '--method' in sys.argv:
                    print('Resource not accessible by integration',file=sys.stderr)
                    sys.exit(1)
                else:
                    print('{}')
                '''))
            gh.chmod(0o755)
            for association, peer, allowed in [('OWNER','cbkii/product',True),
                                               ('NONE','cbkii/product',False),
                                               ('OWNER','fork/product',False)]:
                output = directory / 'outputs'
                output.write_text('')
                env = dict(os.environ, PATH=str(directory)+os.pathsep+os.environ['PATH'],
                    GITHUB_OUTPUT=str(output), RUNNER_TEMP=temp, GITHUB_REPOSITORY='cbkii/product',
                    EVENT_NAME='issue_comment', EVENT_ACTION='created', SOURCE_INPUT='',
                    EVENT_IS_PR_ISSUE='true', EVENT_PR_NUMBER='31', EVENT_LABEL='',
                    COMMENT_BODY='/testing-apk', AUTHOR_ASSOCIATION=association,
                    TRIGGER_ACTOR='owner', DEFAULT_BRANCH='main', MOCK_HEAD_REPO=peer)
                run = subprocess.run(['bash','-euo','pipefail','-c',script], env=env,
                                     text=True,capture_output=True,timeout=15)
                self.assertEqual(allowed, 'requested=true' in output.read_text(), run.stderr)
                if allowed:
                    self.assertEqual(0,run.returncode,run.stderr)
                    self.assertIn('continuing the explicit trusted request',run.stdout)
                    self.assertIn('source_sha='+'a'*40,output.read_text())
                elif peer.startswith('fork/'):
                    self.assertNotEqual(0,run.returncode)
                    self.assertIn('same-repository PR heads',run.stdout)


if __name__ == '__main__':
    unittest.main()
