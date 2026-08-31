# cumba-oss-sas-utils

Low-level reader for SAS transport (XPT) and SAS data set (SAS7BDAT)
binary files, built on [`org.thshsh:struct`](https://mvnrepository.com/artifact/org.thshsh/struct).

## Attribution

This module is **derived from [theshoeshiner/sas-utils](https://github.com/theshoeshiner/sas-utils)**
by Daniel Jackson, used and adapted under the **Apache License, Version 2.0**.
A copy of that licence is included alongside this README as
[`LICENSE-APACHE-2.0.txt`](LICENSE-APACHE-2.0.txt).

> The upstream project carries no `LICENSE` file; the Apache-2.0 grant is declared
> in its `pom.xml` `<licenses>` block. GitHub's licence detector reads `LICENSE`
> files only, so it reports the repository as unlicensed — that is a limitation of
> the detector, not an absence of the grant.

Every source file in this module carries a **per-file notice** at the top stating
whether it is derived from upstream and that it was changed — that is what
Apache-2.0 §4(b) asks for, and a module-level summary alone does not satisfy it.
Files with no upstream counterpart say so instead, so upstream is not credited with
code it did not write.

**Changes made relative to upstream**, as required by Apache-2.0 §4(b):

- Repackaged from the upstream namespace to `net.cumba.sasutils.*`.
- Reduced to a **read-only** reader: no writer and no SAS7BCAT catalog support.
- Added JSpecify nullability annotations and brought the code under this
  repository's static-analysis gates (Error Prone, NullAway, PMD, SpotBugs),
  including null-safety fixes in `ParserBdat` and the observation iterators.
- Adapted the build to this repository's Maven layout and conventions.

No diff against upstream is shipped. The port repackaged `org.thshsh.sas` to
`net.cumba.sasutils`, so **every file differs** and a diff would be the whole module —
noise that hides the substantive changes and goes stale as soon as either side moves.
The upstream link above lets anyone diff against whichever revision they care about.

### Also incorporated

The SAS character-encoding table in `EncodingTableMap` is copied from the
[parso](https://github.com/epam/parso) library by EPAM, used under the
**Apache License, Version 2.0** (declared both in parso's published POM on Maven
Central and in its repository). The same `LICENSE-APACHE-2.0.txt` copy retained
beside this README covers it.

The combined work in this repository is distributed under the **AGPL-3.0-only**
licence (see the repository [`LICENSE`](../../LICENSE)). Apache-2.0 permits this:
it is one-way compatible with GPL-family v3 licences, so Apache-2.0 material may be
incorporated into an AGPL-3.0 work while the original grant and this notice are
retained.

[`org.thshsh:struct`](https://mvnrepository.com/artifact/org.thshsh/struct) is consumed
as an ordinary Maven dependency — it is not vendored into this repository, so its own
licence governs it and nothing here needs to reproduce it. The published artifact
declares the **Apache License, Version 2.0** in its POM, which is the licence that
applies to what we actually consume.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-sas-utils</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.sasutils.*`
- `net.cumba.sasutils.bdat.*`
- `net.cumba.sasutils.bdat.x32.*`
- `net.cumba.sasutils.bdat.x64.*`
- `net.cumba.sasutils.xpt.*`

## SPI registrations

None — this module registers no SPI supplier.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `org.thshsh:struct` | compile | binary struct parsing |
| `org.projectlombok:lombok` | provided | `@Value` / `@CustomLog` |
| `org.jspecify:jspecify` | compile | nullability annotations (NullAway) |

## Notes

- **Read-only.** No writer and no SAS7BCAT catalog classes are included.
- The 32- and 64-bit SAS7BDAT layouts are handled by separate packages
  (`bdat.x32` / `bdat.x64`) behind a common abstraction.
- This module knows nothing about `IDataTable`; the adapter to the
  data-table SPI is `cumba-oss-datatable-provider-sas`, in the
  `cumba-oss-datatable` repository.

See the root [README](../../README.md) for project-wide context.
