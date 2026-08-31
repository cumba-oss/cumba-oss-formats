# `lib/` — library modules

One sub-directory per library artifact, named exactly as its
artifactId. Library modules produce a plain jar, have no `mainClass`,
and inherit from the project parent pom (`../../pom.xml`).

## Modules

- [`cumba-oss-sas-utils`](cumba-oss-sas-utils/README.md) — XPT / SAS7BDAT reader — no dependencies
- [`cumba-oss-datasetjson`](cumba-oss-datasetjson/README.md) — Dataset-JSON DTO model — no dependencies

Modules are listed in the parent pom's `<modules>` in dependency order,
though Maven's reactor derives the real build order itself.

## Adding a new library module

1. Create `lib/<artifact-id>/` with a `pom.xml` whose `<parent>` points
   at `../../pom.xml` and whose `<artifactId>` matches the directory
   name.
2. Add the directory to the parent pom's top-level `<modules>` list.
3. Add a `<dependency>` entry for it in the parent
   `<dependencyManagement>` so consumers need no version.
4. If it should contribute to the aggregate coverage report, list it as
   a dependency in `coverage/pom.xml`. An enforcer rule there fails the
   build if that list and the parent's `<modules>` drift apart.
