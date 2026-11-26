# Decompiler round-trip pipeline

Exercises the Procyon decompiler on open source projects, following Harrand et al.,
[*The Strengths and Behavioral Quirks of Java Bytecode Decompilers*](https://arxiv.org/abs/1908.06895).

For each project of [`projects.json`](projects.json), the pipeline:

1. clones the release tag, builds it with Maven and runs its tests (the baseline);
2. decompiles every top-level class of `target/classes` with Procyon;
3. recompiles each decompiled source on its own against the original classes,
   targeting the class file version of the original;
4. replaces the original classes with the recompiled ones and runs the tests again;
5. bisects the tests which no longer pass down to the responsible compilation units.

A compilation unit which does not recompile (or which has a method that could not be
decompiled) is a *syntactic* failure. A test which passed on the original classes and
no longer does is a *semantic* failure: the code compiles but behaves differently.

## Running

Requirements: Python 3.8+, Git, Maven and a JDK 8 (`JAVA_HOME`). The pipeline builds and
runs third-party code, and needs network access to GitHub and Maven Central.

```sh
./gradlew :Procyon.Decompiler:jar
python3 roundtrip/roundtrip.py                      # all projects
python3 roundtrip/roundtrip.py commons-codec jsoup  # some of them
```

The test runs following the baseline time out after three times its duration plus two
minutes (at most `--test-timeout`), as a decompiler bug can make a test loop forever.

Useful options: `--skip-tests` (stop after recompilation), `--no-bisect`, `--jobs`,
`--jar` (decompile with another Procyon build); see `--help`.

Results are written to `build/roundtrip/`: `report.md` for the run, and for each project
`report.json` (with the complete `javac` output of every failure), `decompiled/` sources
and `logs/`.

## Adding a project

Add an entry to `projects.json` with `repo`, `ref` (a tag or branch), and optionally
`module` (sub-directory of a multi-module build) and `maven_args`. The project must
build with Maven and run its tests with Surefire.

## Testing the pipeline itself

```sh
python3 -m unittest discover -s roundtrip
```
