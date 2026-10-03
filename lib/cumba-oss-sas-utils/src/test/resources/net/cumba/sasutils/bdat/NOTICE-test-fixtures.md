# Notice for the fixtures in this directory

Every `.sas7bdat` file in this directory is an input fixture of this module's SAS7BDAT tests. None
is part of any shipped artifact; the attribution manifest of this module covers shipped sources
only, which is why the notice lives beside the files instead. 21 of the 24 files are third-party
test data, used unchanged: each is byte-identical to the upstream file or files named below. The
other three are not from either upstream (see the last section).

| fixture | theshoeshiner/sas-utils file | EPAM parso 2.0.14 file | licence |
|---|---|---|---|
| `64_numeric4_11x729.sas7bdat` | `64_numeric4_11x729.sas7bdat` | `extend_yes.sas7bdat` | Apache-2.0 |
| `comp_deleted_4x3.sas7bdat` | `comp_deleted_4x3.sas7bdat` | `comp_deleted.sas7bdat` | Apache-2.0 |
| `data_page_with_deleted_3x997.sas7bdat` | `data_page_with_deleted_3x997.sas7bdat` | `data_page_with_deleted.sas7bdat` | Apache-2.0 |
| `doubles2_20x50.sas7bdat` | `doubles2_20x50.sas7bdat` | `doubles2.sas7bdat` | Apache-2.0 |
| `doubles_12x25.sas7bdat` | `doubles_12x25.sas7bdat` | `doubles.sas7bdat` | Apache-2.0 |
| `extend_no_11x729.sas7bdat` | `extend_no_11x729.sas7bdat` | `extend_no.sas7bdat` | Apache-2.0 |
| `extend_yes_11x729.sas7bdat` | `extend_yes_11x729.sas7bdat` | `extend_yes.sas7bdat` | Apache-2.0 |
| `file_with_label_2x2.sas7bdat` | `file_with_label_2x2.sas7bdat` | `file_with_label.sas7bdat` | Apache-2.0 |
| `int_only_25x52.sas7bdat` | `int_only_25x52.sas7bdat` | `int_only.sas7bdat` | Apache-2.0 |
| `mix_data_misc_14x12288.sas7bdat` | `mix_data_misc_14x12288.sas7bdat` | `mix_data_misc.sas7bdat` | Apache-2.0 |
| `mixed1_15x24.sas7bdat` | `mixed1_15x24.sas7bdat` | `mixed_data_one.sas7bdat` | Apache-2.0 |
| `mixed2_21x50.sas7bdat` | `mixed2_21x50.sas7bdat` | `mixed_data_two.sas7bdat` | Apache-2.0 |
| `numeric3_8x37.sas7bdat` | `numeric3_8x37.sas7bdat` | `all_rand_normal.sas7bdat` | Apache-2.0 |
| `percents_8x11.sas7bdat` | `percents_8x11.sas7bdat` | `test-columnar.sas7bdat` | Apache-2.0 |
| `mixed3_25x8.sas7bdat` | `mixed3_25x8.sas7bdat` | — | Apache-2.0 |
| `mixed4_13x15564.sas7bdat` | `mixed4_13x15564.sas7bdat` | — | Apache-2.0 |
| `mixed_formats2_85x55.sas7bdat` | `mixed_formats2_85x55.sas7bdat` | — | Apache-2.0 |
| `mixed_formats_44x11.sas7bdat` | `mixed_formats_44x11.sas7bdat` | — | Apache-2.0 |
| `numeric1_15x4733.sas7bdat` | `numeric1_15x4733.sas7bdat` | — | Apache-2.0 |
| `numeric2_59x630.sas7bdat` | `numeric2_59x630.sas7bdat` | — | Apache-2.0 |
| `time_23x435.sas7bdat` | `time_23x435.sas7bdat` | — | Apache-2.0 |
| `abcd_plain.sas7bdat` | — | — | see below |
| `abcd_char.sas7bdat` | — | — | see below |
| `abcd_bin.sas7bdat` | — | — | see below |

- theshoeshiner/sas-utils: https://github.com/theshoeshiner/sas-utils, files under
  `src/test/resources/org/thshsh/sas/bdat/` (git blob ids compared against its commit
  `f447f5213e`). This module is derived from that project and its test tree, which is where the
  `<name>_<columns>x<rows>` names come from. The licence is the one its `pom.xml` declares; the
  repository carries no `LICENSE` file. Where the seven files that only it carries were made
  before they entered that repository is not known.
- EPAM parso: https://github.com/epam/parso (Apache-2.0), files under
  `src/test/resources/sas7bdat/` at tag `v2.0.14` (git blob ids compared). The 14 files in the
  table that both upstreams carry are the same bytes under two names; sas-utils presumably took
  them from parso. They carry the parso names in this stack's `cumba-parso` module.
  `64_numeric4_11x729.sas7bdat` and `extend_yes_11x729.sas7bdat` are byte-identical to each other.
- The sibling directory `../bdatlib/` holds four more fixtures, byte copies of `mixed1_15x24`,
  `numeric1_15x4733`, `mixed2_21x50` and `numeric2_59x630` under the names theshoeshiner/sas-utils
  gives them in its own `bdatlib/` directory (`lib1_15x24`, `lib1_15x4733`, `lib2_21x50`,
  `lib2_59x630`); the rows above apply to them.

## The three `abcd_*` files

`abcd_plain`, `abcd_char` and `abcd_bin` are the same 300 rows written uncompressed, with
`COMPRESS=CHAR` and with `COMPRESS=BINARY`. They match no file of either upstream. Their own
headers say SAS 9.4 (release `9.0401M8`, Linux x86_64) wrote them on 2026-09-08, the day they entered
this stack's test data, together with byte-identical copies in `cumba-parso`. They were
made for this project's decompression tests; who ran SAS is not recorded, and no third-party
licence applies as far as the record shows.
