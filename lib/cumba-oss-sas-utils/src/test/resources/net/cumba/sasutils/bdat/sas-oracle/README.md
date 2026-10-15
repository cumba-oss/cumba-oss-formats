# SAS-written oracle files for the RLE (`COMPRESS=CHAR`) decoder

The six `.sas7bdat` files here were written by **SAS 9.4** (release `9.0401M9`, Linux x86_64, encoding
UTF-8) by the project owner on **2026-10-15**, with the program `make-sas-oracle.sas` beside them,
and are used unchanged. The data is synthetic and our own; no third-party licence applies.

They exist to test the RLE literal-copy control codes against what SAS itself writes, not against
hand-built streams (internal plan `PLAN-sas7bdat-rle-control-codes`, register item Q4). Each dataset
is present twice: `*_char` with `COMPRESS=CHAR` and `*_plain` uncompressed. The uncompressed twin
is the oracle: every `*_char` row must decode to the `*_plain` row. The column-text subheader of
each `*_char` file names the RLE algorithm (`SASYZCRL`); the `*_plain` files name none.

| dataset | rows | what it exercises |
|---|---|---|
| `lit_*` | 400 | non-repeating stretches of every length 1..400 (literal-copy commands only) |
| `mix_*` | 243 | literal stretches of 60..140 followed by a run of 3, 20 or 300 `Q` and `END!!` |
| `run_*` | 1200 | runs of `A`, blank, `@` and `0x00` of every length 1..300, then `xyz` |

The program was run with `out` set to the SAS `WORK` library; its two side files
(`oracle-meta.csv`, `contents.txt`) were not handed back. The release, host and encoding above are
read from the files' own headers.

| file | md5 |
|---|---|
| `lit_char.sas7bdat` | `41d9f4f9d8a0a7ded7f0751436687d58` |
| `lit_plain.sas7bdat` | `c01922cb039c1abd4fb34d94ec93c9a0` |
| `mix_char.sas7bdat` | `e0be91c8211b00b41b8a0d5cd7dd47ad` |
| `mix_plain.sas7bdat` | `777272578b70f1922501046c9088fcfc` |
| `run_char.sas7bdat` | `313fcf043784885f0f94693cc5bd77aa` |
| `run_plain.sas7bdat` | `365e058ca789b47b3e3b9d980fc6e082` |
| `make-sas-oracle.sas` | `4a174d046ca3fb52e51d60891c14b9ab` |

Byte-identical copies live in the internal `cumba-parso` module (`src/test/resources/sas7bdat/sas-oracle/`)
and in the open-source `cumba-oss-sas-utils` module.
