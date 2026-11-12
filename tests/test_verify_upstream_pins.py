import importlib.util
import pathlib
import unittest
from unittest.mock import patch
import subprocess

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "scripts" / "verify_upstream_pins.py"
SPEC = importlib.util.spec_from_file_location("verify_upstream_pins", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ResolveTagTests(unittest.TestCase):
    @patch.object(MODULE.subprocess, "run")
    def test_annotated_tag_uses_peeled_commit(self, run):
        run.return_value = subprocess.CompletedProcess(
            args=[], returncode=0,
            stdout="abc123 refs/tags/v1^{}\ndef456 refs/tags/v1\n",
            stderr=""
        )
        self.assertEqual(
            MODULE.resolve_tag("https://example.invalid/repo.git", "v1"),
            "abc123",
        )

    @patch.object(MODULE.subprocess, "run")
    def test_lightweight_tag_uses_direct_commit(self, run):
        run.return_value = subprocess.CompletedProcess(
            args=[], returncode=0,
            stdout="def456 refs/tags/v1\n",
            stderr=""
        )
        self.assertEqual(
            MODULE.resolve_tag("https://example.invalid/repo.git", "v1"),
            "def456",
        )

    @patch.object(MODULE.subprocess, "run")
    def test_missing_tag_returns_empty_string(self, run):
        run.return_value = subprocess.CompletedProcess(
            args=[], returncode=0, stdout="", stderr=""
        )
        self.assertEqual(
            MODULE.resolve_tag("https://example.invalid/repo.git", "v1"),
            "",
        )

    @patch.object(MODULE.subprocess, "run")
    def test_git_failure_propagates(self, run):
        run.side_effect = subprocess.CalledProcessError(128, ["git"])
        with self.assertRaises(subprocess.CalledProcessError):
            MODULE.resolve_tag("https://example.invalid/repo.git", "v1")


if __name__ == "__main__":
    unittest.main()
