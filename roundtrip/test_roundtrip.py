"""Unit tests for the round-trip pipeline: python3 -m unittest discover -s roundtrip"""

import os
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import roundtrip  # noqa: E402


def write(path, content=b""):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as stream:
        stream.write(content)


class ClassFileTests(unittest.TestCase):
    def test_groups_nested_classes_with_their_outer_class(self):
        units = roundtrip.group_units([
            "a/B.class", "a/B$C.class", "a/B$C$1.class", "a/$Proxy.class", "a/D$E.class",
            "module-info.class", "a/package-info.class", "META-INF/versions/9/a/B.class",
        ])
        self.assertEqual(["a/$Proxy", "a/B", "a/D$E"], sorted(units))
        self.assertEqual(["a/B.class", "a/B$C.class", "a/B$C$1.class"],
                         sorted(units["a/B"], key=len))

    def test_maps_major_versions_to_javac_releases(self):
        self.assertEqual("1.5", roundtrip.release_for(49))
        self.assertEqual("1.8", roundtrip.release_for(52))
        self.assertEqual("11", roundtrip.release_for(55))

    def test_reads_major_version(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "A.class")
            write(path, b"\xca\xfe\xba\xbe\x00\x00\x00\x34")
            self.assertEqual(52, roundtrip.class_major_version(path))
            write(path, b"nonsense")
            with self.assertRaises(roundtrip.PipelineError):
                roundtrip.class_major_version(path)

    def test_overlay_replaces_all_classes_of_a_unit(self):
        with tempfile.TemporaryDirectory() as directory:
            original = os.path.join(directory, "original")
            compiled = os.path.join(directory, "compiled")
            mixed = os.path.join(directory, "mixed")
            for name in ("a/B.class", "a/B$1.class", "a/C.class", "resource.txt"):
                write(os.path.join(original, name), b"old")
            write(os.path.join(compiled, "a/B.class"), b"new")
            units = roundtrip.group_units(roundtrip.list_classes(original))
            for _ in range(2):
                roundtrip.overlay(original, mixed, units, {"a/B": compiled})
            self.assertEqual(["a/B.class", "a/C.class"], roundtrip.list_classes(mixed))
            self.assertTrue(os.path.isfile(os.path.join(mixed, "resource.txt")))
            with open(os.path.join(mixed, "a/B.class"), "rb") as stream:
                self.assertEqual(b"new", stream.read())


class JavacOutputTests(unittest.TestCase):
    def test_extracts_and_abstracts_the_first_error(self):
        message, category = roundtrip.classify_javac_output(
            "/tmp/a/B.java:12: error: incompatible types: Set<Object> cannot be converted to Set<String>\n"
            "/tmp/a/B.java:13: error: illegal start of expression\n")
        self.assertEqual("B.java:12: incompatible types: Set<Object> cannot be converted to Set<String>", message)
        self.assertEqual("incompatible types", category)

    def test_hides_names_in_categories(self):
        _, category = roundtrip.classify_javac_output(
            "B.java:1: error: variable x1 is already defined in method m()\n")
        self.assertEqual("variable X is already defined in method m()", category)

    def test_handles_output_without_errors(self):
        self.assertEqual(("javac: bad flag", "other"), roundtrip.classify_javac_output("javac: bad flag\n"))
        self.assertEqual(("no output", "other"), roundtrip.classify_javac_output(""))


class TestReportTests(unittest.TestCase):
    def test_parses_surefire_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            write(os.path.join(directory, "TEST-a.xml"),
                  b'<testsuite><testcase classname="a.T" name="ok"/>'
                  b'<testcase classname="a.T" name="bad"><failure/></testcase>'
                  b'<testcase classname="a.T" name="bad"/>'
                  b'<testcase classname="a.T" name="boom"><error/></testcase>'
                  b'<testcase classname="a.T" name="later"><skipped/></testcase></testsuite>')
            write(os.path.join(directory, "TEST-broken.xml"), b"<testsuite>")
            self.assertEqual({"a.T#ok": "passed", "a.T#bad": "failed", "a.T#boom": "failed",
                              "a.T#later": "skipped"}, roundtrip.parse_surefire_reports(directory))

    def test_reports_failed_and_missing_tests_as_regressions(self):
        baseline = {"a": "passed", "b": "passed", "c": "failed", "d": "passed"}
        self.assertEqual(["b", "d"], roundtrip.regressions(baseline, {"a": "passed", "b": "failed"}))

    def test_selects_outer_test_classes(self):
        self.assertEqual("a.T,a.U", roundtrip.test_filter(["a.U#x", "a.T$Nested#y", "a.T#z", "#init"]))


class TimeoutTests(unittest.TestCase):
    def test_allows_a_few_times_the_baseline_duration_within_the_limit(self):
        self.assertEqual(210, roundtrip.adapted_timeout(30, 1200))
        self.assertEqual(1200, roundtrip.adapted_timeout(900, 1200))


class BisectionTests(unittest.TestCase):
    def test_finds_every_independent_culprit(self):
        bad = {"c", "f"}
        self.assertEqual(["c", "f"], roundtrip.find_culprits("abcdefgh", lambda units: bool(bad & set(units))))

    def test_reports_interacting_units_together(self):
        culprits = roundtrip.find_culprits("abcd", lambda units: {"a", "d"} <= set(units))
        self.assertEqual(["a + b + c + d"], culprits)

    def test_returns_nothing_when_the_failure_does_not_reproduce(self):
        self.assertEqual([], roundtrip.find_culprits("abcd", lambda units: False))


class ProcessTests(unittest.TestCase):
    def test_captures_output_and_writes_a_log(self):
        with tempfile.TemporaryDirectory() as directory:
            log = os.path.join(directory, "x.log")
            code, output = roundtrip.run([sys.executable, "-c", "print('hi')"], log=log)
            self.assertEqual((0, "hi"), (code, output.strip()))
            self.assertTrue(os.path.isfile(log))

    def test_timeout_stops_the_whole_process_group(self):
        script = ("import subprocess, sys, time; "
                  "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)']); time.sleep(60)")
        code, output = roundtrip.run([sys.executable, "-c", script], timeout=2)
        self.assertEqual(-1, code)
        self.assertIn("timed out", output)


class ProjectTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = directory.name
        options = roundtrip.parse_arguments(["--work-dir", self.directory])
        self.config = {"repo": "https://example.invalid/p", "ref": "v1"}
        self.project = roundtrip.Project("p", self.config, options)
        os.makedirs(self.project.logs)

    def test_reclones_only_when_the_configured_revision_changes(self):
        clones = []

        def clone(command, **_):
            clones.append(command)
            os.makedirs(command[-1])
            return 0, ""

        with mock.patch.object(roundtrip, "run", clone):
            self.project.checkout()
            self.project.checkout()
            self.assertEqual(1, len(clones))
            write(os.path.join(self.project.source, "stale.txt"))
            self.config["ref"] = "v2"
            self.project.checkout()
        self.assertEqual(2, len(clones))
        self.assertIn("v2", clones[-1])
        self.assertFalse(os.path.exists(os.path.join(self.project.source, "stale.txt")))

    def test_forgets_the_checkout_when_cloning_fails(self):
        with mock.patch.object(roundtrip, "run", lambda *_, **__: (128, "no such tag")):
            with self.assertRaises(roundtrip.PipelineError):
                self.project.checkout()
        self.assertIsNone(roundtrip.read_json(os.path.join(self.project.work, "checkout.json")))

    def test_rejects_an_interrupted_or_empty_baseline(self):
        for outcome in ((False, {"a.T#x": "passed"}), (True, {})):
            with mock.patch.object(self.project, "test", lambda *_: outcome):
                with self.assertRaises(roundtrip.PipelineError):
                    self.project.baseline()
        with mock.patch.object(self.project, "test", lambda *_: (True, {"a.T#x": "failed"})):
            self.assertEqual({"a.T#x": "failed"}, self.project.baseline())

    def test_requires_the_recompiled_class_file(self):
        write(os.path.join(self.project.original, "a/B.class"), b"\xca\xfe\xba\xbe\x00\x00\x00\x34")
        decompiled = os.path.join(self.directory, "decompiled")
        write(os.path.join(decompiled, "a/B.java"))
        self.project.dependencies = []
        with mock.patch.object(roundtrip, "run", lambda *_, **__: (0, "")):
            self.assertEqual("decompile-error", self.project.recompile_unit("a/B", decompiled)["status"])

        def compile_class(command, **_):
            write(os.path.join(command[command.index("-d") + 1], "a/B.class"))
            return 0, ""

        with mock.patch.object(roundtrip, "run", compile_class):
            self.assertEqual("recompiled", self.project.recompile_unit("a/B", decompiled)["status"])


class ReportingTests(unittest.TestCase):
    def test_renders_results_and_errors(self):
        markdown = roundtrip.render_markdown([
            {"project": "p", "units": 3, "recompiled": 1, "incomplete": ["a/I"],
             "failures": {"a/B": {"status": "compile-error", "message": "B.java:1: oops"}},
             "tests": {"baseline_passed": 7, "regressions": ["a.T#x"], "culprits": ["a/C"]}},
            {"project": "q", "error": "clone failed"},
        ])
        for expected in ("| p | 3 | 1 | 1 | 7 | 1 |", "B.java:1: oops", "behaviour change in `a/C`",
                         "regression: `a.T#x`", "clone failed", "a/I"):
            self.assertIn(expected, markdown)

    def test_rejects_unknown_projects(self):
        with self.assertRaises(SystemExit):
            roundtrip.main(["no-such-project"])


if __name__ == "__main__":
    unittest.main()
