import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "scripts/ci/build-launcher-testing-input.sh"


class TestingBuildHelperTests(unittest.TestCase):
    def test_checker_is_release_only_and_metadata_uses_exact_built_sha(self):
        with tempfile.TemporaryDirectory() as temporary:
            temp = Path(temporary)
            source = temp / "source"
            output = temp / "output"
            fakebin = temp / "bin"
            sdk = temp / "sdk"
            source.mkdir()
            output.mkdir()
            fakebin.mkdir()
            (sdk / "build-tools/36.0.0").mkdir(parents=True)
            (source / "launcher").mkdir()
            (source / "tools").mkdir()
            (source / "launcher/build.gradle.kts").write_text("// test\n", encoding="utf-8")

            check_log = temp / "checker.log"
            (source / "tools/launcher_apk_check.py").write_text(
                textwrap.dedent(
                    """\
                    import os
                    import sys
                    with open(os.environ["CHECK_LOG"], "a", encoding="utf-8") as handle:
                        handle.write(sys.argv[1] + "\\n")
                    """
                ),
                encoding="utf-8",
            )

            gradle = fakebin / "gradle"
            gradle.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    set -euo pipefail
                    for arg in "$@"; do
                      case "$arg" in
                        :launcher:assembleDebug)
                          mkdir -p launcher/build/outputs/apk/debug
                          printf 'debug-apk' > launcher/build/outputs/apk/debug/launcher-debug.apk
                          ;;
                        :launcher:assembleRelease)
                          mkdir -p launcher/build/outputs/apk/release
                          printf 'release-apk' > launcher/build/outputs/apk/release/launcher-release.apk
                          ;;
                      esac
                    done
                    """
                ),
                encoding="utf-8",
            )
            gradle.chmod(0o755)

            keytool = fakebin / "keytool"
            keytool.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    set -euo pipefail
                    while (($#)); do
                      if [[ "$1" == '-keystore' ]]; then
                        shift
                        : > "$1"
                        exit 0
                      fi
                      shift
                    done
                    exit 1
                    """
                ),
                encoding="utf-8",
            )
            keytool.chmod(0o755)

            apksigner = sdk / "build-tools/36.0.0/apksigner"
            apksigner.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            apksigner.chmod(0o755)

            subprocess.run(["git", "init", "-q"], cwd=source, check=True)
            subprocess.run(["git", "config", "user.email", "ci@example.invalid"], cwd=source, check=True)
            subprocess.run(["git", "config", "user.name", "CI"], cwd=source, check=True)
            subprocess.run(["git", "add", "."], cwd=source, check=True)
            subprocess.run(["git", "commit", "-qm", "fixture"], cwd=source, check=True)
            built_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()

            env = os.environ.copy()
            env.update(
                {
                    "PATH": f"{fakebin}:{env['PATH']}",
                    "GRADLE_CMD": str(gradle),
                    "RUNNER_TEMP": str(temp / "runner"),
                    "ANDROID_SDK_ROOT": str(sdk),
                    "CHECK_LOG": str(check_log),
                    "GITHUB_REPOSITORY": "cbkii/ts-theme",
                    "GITHUB_RUN_ID": "12345",
                    "GITHUB_RUN_NUMBER": "67",
                    "GITHUB_ACTOR": "cbkii",
                    "TESTING_SOURCE_KIND": "pr",
                    "TESTING_REQUESTED_SOURCE": "PR #11",
                    "TESTING_SOURCE_SHA": built_sha,
                    "TESTING_SOURCE_REF": "feature/test",
                    "TESTING_PR_NUMBER": "11",
                    "TESTING_PR_HEAD_SHA": built_sha,
                    "TESTING_PR_BASE_REF": "main",
                    "TESTING_PR_BASE_SHA": built_sha,
                    "TESTING_TRIGGER_TYPE": "pr-comment-command",
                }
            )
            Path(env["RUNNER_TEMP"]).mkdir()

            subprocess.run(["bash", str(HELPER), str(source), str(output)], env=env, check=True)

            checked = check_log.read_text(encoding="utf-8").splitlines()
            self.assertEqual(checked, ["launcher/build/outputs/apk/release/launcher-release.apk"])
            self.assertTrue((output / "launcher-debug.apk").is_file())
            self.assertTrue((output / "launcher-release.apk").is_file())

            metadata = dict(
                line.split("=", 1)
                for line in (output / "BUILD_INFO.txt").read_text(encoding="utf-8").splitlines()
            )
            self.assertEqual(metadata["source_sha"], built_sha)
            self.assertEqual(metadata["built_sha"], built_sha)
            self.assertEqual(metadata["pr_number"], "11")
            self.assertEqual(metadata["trigger_type"], "pr-comment-command")
            self.assertEqual(metadata["testing_version_name"], "0.0.0-testing")
            self.assertEqual(metadata["testing_version_code"], "999999")
            self.assertEqual(metadata["build_run_id"], "12345")


if __name__ == "__main__":
    unittest.main()
