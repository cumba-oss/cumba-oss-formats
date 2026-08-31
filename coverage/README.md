# `coverage/` — aggregate JaCoCo coverage

A single module with no source of its own. It depends on every module
that should contribute to the aggregate and runs
`jacoco:report-aggregate` at `verify`.

> Previously this was two sub-modules, `coverage/dev` and
> `coverage/main`, one per build profile. Both had an **empty
> `<dependencies>` block**, and `report-aggregate` builds its report
> from that list — not from the reactor. The aggregate was therefore a
> 188-byte empty `<report/>` with zero classes, and since
> `sonar.coverage.jacoco.xmlReportPaths` pointed at it, Sonar was
> reading an empty coverage report. The profiles are gone and the
> dependency list is now populated.

## Updating the module list

Whenever you add or remove a module in the parent pom's top-level
`<modules>`, mirror the change in `coverage/pom.xml`'s
`<dependencies>`.

Forgetting used to be silent. It is now a build failure: a
`maven-enforcer` rule in `coverage/pom.xml` compares the parent's
`lib/*` modules against this module's `net.cumba` dependencies at
`validate` and fails on drift in either direction —

```
MISSING from coverage/pom.xml <dependencies>: [cumba-oss-newthing]
-> these modules build, but contribute NO coverage to the aggregate or to Sonar.
```

```
STALE in coverage/pom.xml <dependencies>: [cumba-oss-removed]
-> no matching lib/* entry in the parent's <modules>.
```

## Output paths

After `mvn -T1C verify`:

```
coverage/target/site/jacoco-aggregate/jacoco.xml
```

That path is what `sonar.coverage.jacoco.xmlReportPaths` points at, set
once in the root pom's `<properties>`.
