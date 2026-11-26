#!/usr/bin/env python3
"""Decompile / recompile / unit-test round trip over open source Java projects.

Follows the methodology of Harrand et al., "The Strengths and Behavioral Quirks
of Java Bytecode Decompilers" (https://arxiv.org/abs/1908.06895):

  1. build a project and run its tests on the original classes (baseline);
  2. decompile every top-level class with Procyon;
  3. recompile each decompiled source on its own against the original classes;
  4. swap the recompiled classes into the build and run the tests again;
  5. bisect new test failures down to the responsible class files.

Only the Python standard library is used.
"""

import argparse
import concurrent.futures
import glob
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DECOMPILE_FAILURE_MARKER = "This method could not be decompiled"

# Build plugins that check the sources or the packaging; none of them affects the
# behaviour of the tests, and several of them reject old releases or swapped classes.
MAVEN_SKIPS = [
    "-Danimal.sniffer.skip=true",
    "-Dcheckstyle.skip=true",
    "-Dcyclonedx.skip=true",
    "-Denforcer.skip=true",
    "-Djacoco.skip=true",
    "-Djapicmp.skip=true",
    "-Dmaven.javadoc.skip=true",
    "-Dpmd.skip=true",
    "-Drat.skip=true",
    "-Dspotbugs.skip=true",
    "-Dspotless.check.skip=true",
    "-Dspdx.skip=true",
]


class PipelineError(Exception):
    pass


def run(command, cwd=None, log=None, timeout=None):
    """Runs a command, returning (exit code, combined output). A timeout yields code -1.

    The command gets its own process group so that a timeout also stops the processes
    it forked, such as the test JVM started by Maven.
    """
    process = subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               start_new_session=True)
    try:
        output = process.communicate(timeout=timeout)[0]
        code = process.returncode
    except subprocess.TimeoutExpired:
        kill_group(process)
        output = process.communicate()[0] + b"\n*** timed out ***\n"
        code = -1
    except BaseException:
        kill_group(process)
        raise
    output = output.decode("utf-8", "replace")
    if log:
        with open(log, "w", encoding="utf-8") as stream:
            stream.write(" ".join(command) + "\n" + output)
    return code, output


def read_json(path):
    """Returns the parsed content of a JSON file, or None if it is missing or invalid."""
    try:
        with open(path, encoding="utf-8") as stream:
            return json.load(stream)
    except (OSError, ValueError):
        return None


def kill_group(process):
    if hasattr(os, "killpg"):
        try:
            os.killpg(process.pid, signal.SIGKILL)
            return
        except ProcessLookupError:
            return
    process.kill()


# --------------------------------------------------------------------------- class files

def list_classes(classes_dir):
    """Returns the relative paths (with '/' separators) of all class files."""
    result = []
    for directory, _, files in os.walk(classes_dir):
        for name in files:
            if name.endswith(".class"):
                relative = os.path.relpath(os.path.join(directory, name), classes_dir)
                result.append(relative.replace(os.sep, "/"))
    return sorted(result)


def group_units(class_files):
    """Groups class files by compilation unit: {'a/B': ['a/B.class', 'a/B$C.class']}.

    A name containing '$' belongs to the longest existing prefix that ends before a '$',
    so a top-level class whose own name contains '$' keeps its identity.
    """
    names = {path[:-len(".class")] for path in class_files
             if not path.startswith("META-INF/")}  # multi-release variants stay as built
    units = {}
    for name in sorted(names):
        slash = name.rfind("/")
        owner = name
        position = name.find("$", slash + 2)
        while position >= 0:
            if name[:position] in names:
                owner = name[:position]
                break
            position = name.find("$", position + 1)
        units.setdefault(owner, []).append(name + ".class")
    for special in ("module-info", "package-info"):
        for unit in [u for u in units if u.rsplit("/", 1)[-1] == special]:
            del units[unit]
    return units


def class_major_version(path):
    with open(path, "rb") as stream:
        header = stream.read(8)
    if len(header) < 8 or header[:4] != b"\xca\xfe\xba\xbe":
        raise PipelineError("not a class file: " + path)
    return int.from_bytes(header[6:8], "big")


def release_for(major):
    """javac -source/-target value matching a class file major version."""
    version = major - 44
    return "1.%d" % version if version <= 8 else str(version)


def overlay(original_dir, mixed_dir, units, compiled_dirs):
    """Writes into mixed_dir a copy of original_dir with the given units replaced."""
    if os.path.exists(mixed_dir):
        shutil.rmtree(mixed_dir)
    shutil.copytree(original_dir, mixed_dir)
    for unit, source_dir in compiled_dirs.items():
        for relative in units[unit]:
            os.remove(os.path.join(mixed_dir, relative))
        shutil.copytree(source_dir, mixed_dir, dirs_exist_ok=True)


# --------------------------------------------------------------------------- javac errors

JAVAC_ERROR = re.compile(r"^(?P<file>.+?\.java):(?P<line>\d+): error: (?P<message>.*)$", re.MULTILINE)


def classify_javac_output(output):
    """Returns (first error line, category). Names and numbers are abstracted in the category."""
    match = JAVAC_ERROR.search(output)
    if not match:
        text = output.strip().splitlines()
        first = text[0] if text else "no output"
        return first, "other"
    message = match.group("message").strip()
    category = re.sub(r"\d+", "N", message)
    category = re.sub(r"(incompatible types|reference to .* is ambiguous|cannot be applied).*", r"\1", category)
    category = re.sub(r"(variable|class|method|constructor|symbol) [\w$.<>\[\](), ]+? (is already|has private|might)",
                      r"\1 X \2", category)
    return "%s:%s: %s" % (os.path.basename(match.group("file")), match.group("line"), message), category


# --------------------------------------------------------------------------- test reports

def parse_surefire_reports(directory):
    """Returns {'pkg.Class#method': 'passed' | 'failed' | 'skipped'} from surefire XML files."""
    results = {}
    for path in sorted(glob.glob(os.path.join(directory, "TEST-*.xml"))):
        try:
            tree = ElementTree.parse(path)
        except ElementTree.ParseError:
            continue
        for case in tree.getroot().iter("testcase"):
            key = "%s#%s" % (case.get("classname", ""), case.get("name", ""))
            if case.find("failure") is not None or case.find("error") is not None:
                status = "failed"
            elif case.find("skipped") is not None:
                status = "skipped"
            else:
                status = "passed"
            # Parameterized tests can repeat a name; one failure is enough to fail it.
            if results.get(key) != "failed":
                results[key] = status
    return results


def regressions(baseline, current):
    """Tests which passed on the original classes but did not pass (or did not run) now."""
    return sorted(test for test, status in baseline.items()
                  if status == "passed" and current.get(test) != "passed")


def test_filter(tests):
    """Surefire -Dtest value selecting the classes of the given tests."""
    classes = set()
    for test in tests:
        name = test.split("#", 1)[0].split("$", 1)[0]
        if name:
            classes.add(name)
    return ",".join(sorted(classes))


# --------------------------------------------------------------------------- bisection

def find_culprits(candidates, fails, limit=20):
    """Delta debugging: returns units which, when swapped in, make the tests fail.

    fails(units) tells whether the failure reproduces with exactly those units replaced.
    Culprits are removed one at a time until the failure disappears.
    """
    remaining = list(candidates)
    culprits = []
    while remaining and len(culprits) < limit and fails(remaining):
        suspects = list(remaining)
        while len(suspects) > 1:
            half = suspects[:len(suspects) // 2]
            other = suspects[len(suspects) // 2:]
            if fails(half):
                suspects = half
            elif fails(other):
                suspects = other
            else:
                break  # the failure needs units from both halves
        culprits.append(suspects[0] if len(suspects) == 1 else " + ".join(suspects))
        remaining = [unit for unit in remaining if unit not in suspects]
    return culprits


# --------------------------------------------------------------------------- project pipeline

class Project:
    def __init__(self, name, config, options):
        self.name = name
        self.config = config
        self.options = options
        self.work = os.path.join(options.work_dir, name)
        self.source = os.path.join(self.work, "src")
        self.module = os.path.join(self.source, config.get("module", ""))
        self.logs = os.path.join(self.work, "logs")
        self.classes = os.path.join(self.module, "target", "classes")
        self.original = os.path.join(self.work, "original-classes")
        self.reports = os.path.join(self.module, "target", "surefire-reports")

    def log(self, name):
        return os.path.join(self.logs, name + ".log")

    def step(self, message):
        print("[%s] %s" % (self.name, message), flush=True)

    def maven(self, goals, log, timeout=None):
        command = [self.options.maven, "-B", "-ntp"] + MAVEN_SKIPS + self.config.get("maven_args", []) + goals
        return run(command, cwd=self.module, log=self.log(log), timeout=timeout)

    def checkout(self):
        """Clones the configured revision, unless it is what a previous run already cloned."""
        wanted = {"repo": self.config["repo"], "ref": self.config["ref"]}
        marker = os.path.join(self.work, "checkout.json")
        if os.path.isdir(self.source):
            if read_json(marker) == wanted:
                return
            self.step("discarding the checkout of another revision")
            shutil.rmtree(self.source)
        if os.path.exists(marker):
            os.remove(marker)
        self.step("cloning %s at %s" % (wanted["repo"], wanted["ref"]))
        code, output = run(["git", "clone", "--quiet", "--depth", "1", "--branch", wanted["ref"],
                            wanted["repo"], self.source], log=self.log("clone"))
        if code != 0:
            raise PipelineError("clone failed:\n" + output)
        with open(marker, "w", encoding="utf-8") as stream:
            json.dump(wanted, stream)

    def build(self):
        self.step("building the original project")
        classpath_file = os.path.join(self.work, "classpath.txt")
        code, _ = self.maven(["clean", "test-compile", "dependency:build-classpath",
                              "-Dmdep.outputFile=" + classpath_file], "build")
        if code != 0 or not os.path.isdir(self.classes):
            raise PipelineError("build failed, see " + self.log("build"))
        if os.path.exists(self.original):
            shutil.rmtree(self.original)
        shutil.copytree(self.classes, self.original)
        with open(classpath_file, encoding="utf-8") as stream:
            self.dependencies = [entry for entry in stream.read().strip().split(os.pathsep) if entry]

    def install_classes(self, directory):
        shutil.rmtree(self.classes)
        shutil.copytree(directory, self.classes)

    def test(self, log, only=None):
        """Runs the tests against whatever is in target/classes, without rebuilding it.

        Returns (completed, results). Test failures do not count against completion,
        but a timeout or a broken build does: its results are then partial.
        """
        if os.path.exists(self.reports):
            shutil.rmtree(self.reports)
        goals = ["test", "-Dmaven.main.skip=true", "-Dmaven.test.failure.ignore=true"]
        if only:
            goals += ["-Dtest=" + only, "-DfailIfZeroTests=false", "-Dsurefire.failIfNoSpecifiedTests=false"]
        code, _ = self.maven(goals, log, timeout=self.options.test_timeout)
        return code == 0, parse_surefire_reports(self.reports)

    def baseline(self):
        self.step("running the baseline tests")
        completed, results = self.test("test-baseline")
        if not completed or not results:
            raise PipelineError("the baseline tests did not complete, see " + self.log("test-baseline"))
        return results

    def decompile(self, units):
        self.step("decompiling %d compilation units" % len(units))
        output = os.path.join(self.work, "decompiled")
        if os.path.exists(output):
            shutil.rmtree(output)
        archive = os.path.join(self.work, "original-classes.jar")
        with zipfile.ZipFile(archive, "w") as jar:
            for relative in list_classes(self.original):
                jar.write(os.path.join(self.original, relative), relative)
        classpath = os.pathsep.join([self.options.jar] + self.dependencies)
        command = [self.options.java, "-cp", classpath, "com.strobel.decompiler.DecompilerDriver",
                   "-jar", archive, "-o", output]
        code, _ = run(command, log=self.log("decompile"), timeout=self.options.decompile_timeout)
        if code != 0:
            self.step("decompiler exited with code %d, see %s" % (code, self.log("decompile")))
        return output

    def recompile_unit(self, unit, decompiled):
        """Returns a result dictionary describing one compilation unit."""
        source = os.path.join(decompiled, unit + ".java")
        if not os.path.isfile(source):
            return {"status": "decompile-error", "message": "no source produced"}
        with open(source, encoding="utf-8", errors="replace") as stream:
            incomplete = DECOMPILE_FAILURE_MARKER in stream.read()
        destination = os.path.join(self.work, "recompiled", unit.replace("/", "."))
        os.makedirs(destination, exist_ok=True)
        release = release_for(class_major_version(os.path.join(self.original, unit + ".class")))
        command = [self.options.javac, "-nowarn", "-g", "-proc:none", "-implicit:none", "-encoding", "UTF-8",
                   "-source", release, "-target", release, "-sourcepath", "",
                   "-cp", os.pathsep.join([self.original] + self.dependencies), "-d", destination, source]
        code, output = run(command, timeout=300)
        if code != 0:
            message, category = classify_javac_output(output)
            result = {"status": "compile-error", "message": message, "category": category, "output": output}
        elif not os.path.isfile(os.path.join(destination, unit + ".class")):
            # For instance the empty file left behind when the decompiler crashes.
            result = {"status": "decompile-error", "message": "the source does not declare the class"}
        else:
            result = {"status": "recompiled", "directory": destination}
        if incomplete:
            result["incomplete"] = True
        return result

    def recompile(self, units, decompiled):
        self.step("recompiling")
        shutil.rmtree(os.path.join(self.work, "recompiled"), ignore_errors=True)
        with concurrent.futures.ThreadPoolExecutor(self.options.jobs) as pool:
            futures = {unit: pool.submit(self.recompile_unit, unit, decompiled) for unit in units}
            return {unit: future.result() for unit, future in futures.items()}

    def run(self):
        os.makedirs(self.logs, exist_ok=True)
        self.checkout()
        self.build()
        units = group_units(list_classes(self.original))

        baseline = {} if self.options.skip_tests else self.baseline()

        results = self.recompile(units, self.decompile(units))
        compiled = {unit: result["directory"] for unit, result in results.items()
                    if result["status"] == "recompiled" and not result.get("incomplete")}
        report = {
            "project": self.name,
            "ref": self.config["ref"],
            "units": len(units),
            "recompiled": len(compiled),
            "incomplete": sorted(unit for unit, result in results.items() if result.get("incomplete")),
            "failures": {unit: {key: value for key, value in result.items() if key != "directory"}
                         for unit, result in sorted(results.items()) if result["status"] != "recompiled"},
        }
        self.step("%d/%d units recompiled" % (len(compiled), len(units)))

        if baseline:
            self.verify(units, compiled, baseline, report)
        return report

    def verify(self, units, compiled, baseline, report):
        mixed = os.path.join(self.work, "mixed-classes")

        def tests_with(selected, log, only=None):
            overlay(self.original, mixed, units, {unit: compiled[unit] for unit in selected})
            self.install_classes(mixed)
            return self.test(log, only)

        try:
            self.step("running the tests on the recompiled classes")
            completed, current = tests_with(sorted(compiled), "test-recompiled")
            broken = regressions(baseline, current)
            report["tests"] = {
                "completed": completed,
                "baseline_passed": sum(1 for status in baseline.values() if status == "passed"),
                "regressions": broken,
            }
            self.step("%d test regressions" % len(broken))
            if broken and self.options.bisect:
                self.step("bisecting")
                only = test_filter(broken)

                def fails(selected):
                    outcome = tests_with(selected, "test-bisect", only)[1]
                    return any(outcome.get(test) != "passed" for test in broken)

                report["tests"]["culprits"] = find_culprits(sorted(compiled), fails)
        finally:
            self.install_classes(self.original)


# --------------------------------------------------------------------------- reporting

def render_markdown(reports):
    lines = ["# Procyon round-trip report", "",
             "| Project | Units | Recompiled | Incomplete | Baseline passing | Regressions |",
             "|---|---|---|---|---|---|"]
    for report in reports:
        if "error" in report:
            lines.append("| %s | pipeline error | | | | |" % report["project"])
            continue
        tests = report.get("tests")
        lines.append("| %s | %d | %d | %d | %s | %s |" % (
            report["project"], report["units"], report["recompiled"], len(report["incomplete"]),
            tests["baseline_passed"] if tests else "-", len(tests["regressions"]) if tests else "-"))
    for report in reports:
        lines += ["", "## " + report["project"], ""]
        if "error" in report:
            lines += ["```", report["error"], "```"]
            continue
        for unit, failure in report["failures"].items():
            lines.append("- `%s` %s: %s" % (unit, failure["status"], failure["message"]))
        for unit in report["incomplete"]:
            lines.append("- `%s` has methods which could not be decompiled" % unit)
        tests = report.get("tests", {})
        if tests and not tests.get("completed", True):
            lines.append("- the tests of the recompiled classes did not complete")
        for culprit in tests.get("culprits", []):
            lines.append("- behaviour change in `%s`" % culprit)
        for test in tests.get("regressions", [])[:50]:
            lines.append("- regression: `%s`" % test)
    return "\n".join(lines) + "\n"


def find_decompiler_jar():
    jars = [jar for jar in glob.glob(os.path.join(ROOT, "build", "Procyon.Decompiler", "libs", "procyon-decompiler-*.jar"))
            if not jar.endswith(("-sources.jar", "-javadoc.jar"))]
    return max(jars, key=os.path.getmtime) if jars else None


def java_tool(name):
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", name) if home else name


def parse_arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("projects", nargs="*", help="project names (default: all)")
    parser.add_argument("--config", default=os.path.join(HERE, "projects.json"))
    parser.add_argument("--work-dir", default=os.path.join(ROOT, "build", "roundtrip"))
    parser.add_argument("--jar", default=find_decompiler_jar(), help="Procyon decompiler jar")
    parser.add_argument("--java", default=java_tool("java"))
    parser.add_argument("--javac", default=java_tool("javac"))
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--jobs", type=int, default=os.cpu_count() or 2)
    parser.add_argument("--test-timeout", type=int, default=1200, help="seconds")
    parser.add_argument("--decompile-timeout", type=int, default=1200, help="seconds")
    parser.add_argument("--skip-tests", action="store_true", help="stop after recompilation")
    parser.add_argument("--no-bisect", dest="bisect", action="store_false")
    return parser.parse_args(argv)


def main(argv=None):
    options = parse_arguments(argv)
    with open(options.config, encoding="utf-8") as stream:
        configs = json.load(stream)
    names = options.projects or sorted(configs)
    unknown = [name for name in names if name not in configs]
    if unknown:
        sys.exit("unknown project(s): " + ", ".join(unknown))
    if not options.jar or not os.path.isfile(options.jar):
        sys.exit("decompiler jar not found; run ./gradlew :Procyon.Decompiler:jar or pass --jar")
    options.jar = os.path.abspath(options.jar)
    options.work_dir = os.path.abspath(options.work_dir)

    reports = []
    for name in names:
        try:
            report = Project(name, configs[name], options).run()
        except (PipelineError, OSError) as error:
            report = {"project": name, "error": str(error)}
            print("[%s] %s" % (name, error), flush=True)
        reports.append(report)
        os.makedirs(os.path.join(options.work_dir, name), exist_ok=True)
        with open(os.path.join(options.work_dir, name, "report.json"), "w", encoding="utf-8") as stream:
            json.dump(report, stream, indent=2)
        with open(os.path.join(options.work_dir, "report.md"), "w", encoding="utf-8") as stream:
            stream.write(render_markdown(reports))
    return 1 if any("error" in report for report in reports) else 0


if __name__ == "__main__":
    sys.exit(main())
