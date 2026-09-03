# Cumba OSS Formats

Read-only codecs for clinical and statistical file formats: SAS transport (XPT), SAS data sets (SAS7BDAT), and CDISC Dataset-JSON.

Licensed under the **GNU Affero General Public License v3.0 only**
(see [`LICENSE`](LICENSE)).

> `cumba-oss-sas-utils` is derived from
> [theshoeshiner/sas-utils](https://github.com/theshoeshiner/sas-utils) under the
> Apache License 2.0. See that module's
> [attribution notice](lib/cumba-oss-sas-utils/README.md#attribution) and the
> licence copy retained beside it.

An **independent leaf** — it depends on no other Cumba OSS repository. Consumed by `cumba-oss-datatable`.

## Modules

| Module | Java package | Purpose |
|---|---|---|
| [`cumba-oss-sas-utils`](lib/cumba-oss-sas-utils/README.md) | `net.cumba.sasutils` | Low-level reader for XPT and SAS7BDAT binary files, built on [`org.thshsh:struct`](https://mvnrepository.com/artifact/org.thshsh/struct). Read-only: no writer, no SAS7BCAT catalog support. |
| [`cumba-oss-cdisc-dsj`](lib/cumba-oss-cdisc-dsj/README.md) | `net.cumba.cdisc.dsj` | Jackson DTO model for CDISC Dataset-JSON v1.x files. |

Each module has its own `README.md` with coordinates and dependency detail.

## The Cumba OSS repositories

```
cumba-oss-commons     help · web-api · cdisc-library · bootstrap
      ▲
cumba-oss-datatable   datatable · impl · cdisc-define · providers · manager-local
      ▲
cumba-oss-formats     sas-utils · cdisc-dsj             (independent leaf)
```

Dependencies run in one direction only. Build order is
`cumba-oss-commons` → `cumba-oss-formats` → `cumba-oss-datatable`.

## Quick start

```bash
mvn -T1C clean install
```

Artifacts are published under groupId `net.cumba` with the module's
artifactId, e.g.:

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-sas-utils</artifactId>
    <version>${revision}</version>
</dependency>
```

## Build profiles

| Profile             | Active when               | Purpose                                       |
|---------------------|---------------------------|-----------------------------------------------|
| `PMD`               | unless `-DskipPmd`        | runs PMD at `verify`, **report-only** by default |
| `SpotBugs`          | unless `-DskipSpotbugs`   | runs SpotBugs at `verify`, **report-only** by default |
| `Pitest`            | `-P Pitest` or `-Dpitest.enabled=true` | **opt-in** mutation testing at `verify`, **report-only** by default |
| `ecj`               | `-P ecj`                  | second ECJ compile at `verify`                |
| `ErrPrn`            | `-P ErrPrn`               | Error Prone as a javac plugin                 |
| `spotless-check-mode` | `-Dspotless.check=true` | swaps Spotless from `apply` to `check`        |
| `pitest-fail-on-error` | `-Dpitest.failOnError=true` | promotes per-module `pitest.*.target` to the effective threshold (no-op without `Pitest` active) |
| `spotbugs-module-ignore` | per-module file `spotbugs_ignore.xml` exists | layers per-module SpotBugs filter |

## Static-analysis fail toggles

Every always-on check (Spotless, SpotBugs, PMD, Error Prone) runs
by default but produces a **report only** — they do not block the
build on findings. Pitest is **opt-in** (mutation testing is too slow
for inner-loop builds) and likewise report-only once activated. The
CI gate is opt-in:

| Property                       | Default | When set to `true`                                |
|--------------------------------|---------|---------------------------------------------------|
| `-Dspotbugs.failOnError=true`  | `false` | SpotBugs findings fail the build                  |
| `-Dpmd.failOnViolation=true`   | `false` | PMD findings fail the build                       |
| `-Derrprn.failOnWarning=true`  | `false` | Error Prone findings fail the build (requires `-P ErrPrn`) |
| `-Dspotless.check=true`        | `false` | Spotless switches to `check` mode; unformatted files fail the build (does not rewrite) |
| `-Dpitest.failOnError=true`    | `false` | Pitest mutation/coverage/test-strength thresholds are promoted from each module's `pitest.*.target` and enforced (requires the Pitest profile — see below) |

And the disable / opt-in switches:

| Property                    | Effect                                            |
|-----------------------------|---------------------------------------------------|
| `-DskipPmd`                 | skip the PMD profile entirely                     |
| `-DskipSpotbugs`            | skip the SpotBugs profile entirely                |
| `-Dpitest.enabled=true`     | opt in to the Pitest profile (equivalent to `-P Pitest`) |
| `-Derrprn.extraArgs=...`    | append args to Error Prone, e.g. enable NullAway   |

> **Pitest opt-in:** the `Pitest` profile is dormant by default.
> Activate it with `-P Pitest` (manual profile selection) or
> `-Dpitest.enabled=true` (property activation). `-Dpitest.failOnError=true`
> is a no-op on its own — it only promotes the thresholds inside the
> `pitest-fail-on-error` sub-profile, and without `Pitest` active the
> plugin doesn't run, so no thresholds are evaluated. Always combine,
> e.g. `mvn -P Pitest verify -Dpitest.failOnError=true`.

> **Pitest threshold gotcha:** do **not** pass
> `-Dpitest.mutation.threshold=…` on the command line — same trap as
> `-Djacoco.line.coverage`: a CLI `-D` clobbers every per-module
> override at once. Tune per-module by setting
> `<pitest.mutation.target>` (and `<pitest.coverage.target>`,
> `<pitest.test.strength.target>`) in the module's `pom.xml`, then
> let `-Dpitest.failOnError=true` promote them.

> **No build profile is required.** The reactor is declared at the top
> level of the parent pom, so every command below builds all modules
> with no `-P`. This used to be split across `activeByDefault` `dev` and
> `main` profiles, which silently did not work: Maven drops an
> activeByDefault profile as soon as any other profile activates, and
> `PMD`/`SpotBugs`/`NullAway` self-activate unconditionally — so a plain
> `mvn install` built the parent pom alone, reporting success having
> compiled nothing. If you have `-P main` in a script, drop it; Maven
> will warn that the profile does not exist.

## Build commands

```bash
mvn -T1C clean install                            # full build, report-only checks
mvn -T1C test                                     # all tests
mvn -T1C verify -Dspotless.check=true             # CI: verify formatting without rewriting
mvn -T1C verify -Dspotbugs.failOnError=true -Dpmd.failOnViolation=true   # CI: hard gate
mvn -T1C -P Pitest verify                         # opt in to pitest, report-only
mvn -T1C -P Pitest verify -Dpitest.failOnError=true  # CI: pitest + enforce mutation/coverage targets
mvn -T1C verify -DskipPmd -DskipSpotbugs          # quick build, no static analysis (pitest already off)
mvn -T1C initialize sonar:sonar                   # SonarQube (initialize is required
                                                  # so sonar-exclusions.properties loads)
```

Standalone `mvn sonar:sonar` does **not** trigger `initialize`, so the
suppression file never loads — always invoke as
`mvn -T1C initialize sonar:sonar` (or any lifecycle command that
already includes the `initialize` phase, e.g.
`mvn -T1C verify sonar:sonar`).

## Build conventions

- **Java 25** (set via `<java.version>` and `maven.compiler.release`).
- **`<revision>` + `flatten-maven-plugin`** for CI-friendly versioning.
- **Lombok** as compile-time annotation processor; `@CustomLog`
  injects a `java.lang.System.Logger` field named `LOGGER` (see
  `lombok.config`).
- **Strict lint:** `failOnWarning=true` plus `-Xlint:all` in the `dev`
  profile makes any javac warning a build failure.
- **Spotless** reformats Java sources in-place at `process-sources`
  (before compile) using the Eclipse JDT formatter and
  `eclipse-formatter.xml`. Imports are sorted, unused imports
  removed, trailing whitespace stripped. `mvn install` modifies your
  working tree as a side-effect; CI gates with `-Dspotless.check=true`
  to fail rather than rewrite.
- **SpotBugs** runs at `verify`, layered with
  `spotbugs_project_filter.xml` (always) plus the module's
  `spotbugs_ignore.xml` (auto-activated when present). **Report-only
  by default**; CI flips with `-Dspotbugs.failOnError=true`.
- **Surefire test-CWD isolation.** The forked test JVM's working
  directory is pinned to `${project.build.directory}/test-cwd` (i.e.
  `target/test-cwd/`). A test that resolves a relative path
  (`new File("foo")`, `Files.write(Path.of("out.txt"), …)`, …) lands
  inside `target/` and gets wiped by `mvn clean` instead of polluting
  the repo checkout. Tests that legitimately need the module root or
  the multi-module root read them from system properties Surefire
  exposes per fork: `System.getProperty("projectBasedir")` (the
  module's `${project.basedir}`) and `System.getProperty("repoRoot")`
  (`${maven.multiModuleProjectDirectory}`, i.e. the reactor root).
- **JaCoCo** enforces a per-module line-coverage minimum.
  `<jacoco.line.coverage>` defaults to `0.80` (80%). Override
  per-module by setting the property in the module's pom, or globally
  on the CLI with `-Djacoco.line.coverage=0.0`. Greenfield projects
  typically start at 0 and raise the bar as the test suite matures.
- **Pitest** mutation testing is **opt-in** (`-P Pitest` or
  `-Dpitest.enabled=true`) because mutation analysis is too slow for
  the inner loop. Once active it runs at `verify`, report-only by
  default; pair the opt-in with `-Dpitest.failOnError=true` to
  promote each module's `pitest.mutation.target` /
  `pitest.coverage.target` to the effective thresholds. Incremental
  analysis is enabled via the OSS `io.github.mibimiflo:pitest-history`
  SPI plugin (pitest 1.17+ removed its built-in OSS history reader);
  per-module history is written to
  `.pitest-history/<artifactId>/history.bin` at the **repo root**
  (outside any module's `target/`, so `mvn clean` does not wipe it),
  and the CI workflow caches the directory so warm-cache runs reuse it.
- **License aggregation** via `license-maven-plugin`. Run
  `mvn license:add-third-party` to generate `src/license/THIRD-PARTY.txt`.
  The plugin is wired into `pluginManagement` but no licenseUrl
  rewrites are configured by default — add them as needed.

## Adding a new module

1. Create a directory under `lib/`, named exactly as the artifactId.
2. Add a `pom.xml` with `<parent>` pointing at this root pom.
3. Add the directory to the parent's top-level `<modules>`, **and** as a dependency of
   `coverage/pom.xml` — the aggregate report is built from that
   dependency list, not from the reactor. An enforcer rule in
   `coverage/pom.xml` fails the build if the two lists drift.
4. Add a `<dependency>` entry for it in the parent `<dependencyManagement>`.
5. Forgetting step 3's coverage half is caught by the enforcer rule in
   `coverage/pom.xml`, which fails the build naming the module.
