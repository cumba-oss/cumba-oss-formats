package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.cumba.sasutils.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the two SAS decompression paths - {@link RleCompressor} ({@code COMPRESS=CHAR}) and
 * {@link RdcCompressor} ({@code COMPRESS=BINARY}) - against files SAS itself wrote, rather than
 * against hand-built synthetic streams.
 *
 * <p>
 * The oracle is {@code abcd_plain.sas7bdat}: the same 300 rows and 5 columns, written uncompressed.
 * {@code abcd_char.sas7bdat} and {@code abcd_bin.sas7bdat} must decode to exactly those values, so
 * the expectation cannot drift away from what SAS actually stored.
 *
 * <p>
 * {@code VAR3} is the load-bearing column: 200 characters of {@code "ABC"} repeating. Its period of
 * 3 equals RDC's minimum encodable back-reference offset, i.e. the maximally overlapping
 * copy-from-output case the format can express - exactly the input a broken back-reference (a bulk
 * {@code System.arraycopy} instead of a byte-by-byte copy) mangles, typically into {@code "ABC"}
 * followed by NUL padding. Mutual equality alone would not catch that if every reader were broken
 * the same way, so the decoded content and the absence of NUL bytes are asserted too.
 *
 * <p>
 * The {@code sas-oracle/} files (written by SAS 9.4 for register item Q4, see the README there)
 * extend the same pattern to every RLE command SAS emits for literal stretches of length 1..400,
 * literal-then-run rows and runs of length 1..300: each {@code *_char} file must decode to its
 * {@code *_plain} twin and to the values the generating program defines, and a census of the
 * control nibbles pins which commands SAS actually wrote - in particular that it never wrote the
 * literal-copy codes {@code 0x2n} / {@code 0x3n}, whose meaning is not confirmed by SAS output.
 */
class RealSasCompressionTest
{

    private static final String ORACLE = "sas-oracle/";

    private static final int ROWS = 300;

    private static final int WIDTH = 200;

    @Test
    void rleAndRdcDecodeToTheUncompressedFile(@TempDir Path aDir) throws IOException
    {
        List<String> plainColumns = columns(aDir, "abcd_plain");
        assertTrue(plainColumns.containsAll(List.of("VAR1", "VAR2", "VAR3", "VAR4")),
                "fixture must hold VAR1..VAR4, found " + plainColumns);
        assertEquals(plainColumns, columns(aDir, "abcd_char"), "COMPRESS=CHAR column order");
        assertEquals(plainColumns, columns(aDir, "abcd_bin"), "COMPRESS=BINARY column order");

        List<List<Object>> plain = readAll(aDir, "abcd_plain");
        List<List<Object>> rle = readAll(aDir, "abcd_char");
        List<List<Object>> rdc = readAll(aDir, "abcd_bin");

        assertEquals(ROWS, plain.size(), "rows in the uncompressed oracle");
        assertEquals(ROWS, rle.size(), "rows in the COMPRESS=CHAR file");
        assertEquals(ROWS, rdc.size(), "rows in the COMPRESS=BINARY file");

        for (int row = 0; row < ROWS; row++)
        {
            assertEquals(plain.get(row), rle.get(row),
                    "RLE row " + row + " must equal the uncompressed row");
            assertEquals(plain.get(row), rdc.get(row),
                    "RDC row " + row + " must equal the uncompressed row");
        }

        assertContent(plainColumns, plain, "uncompressed");
        assertContent(plainColumns, rle, "COMPRESS=CHAR");
        assertContent(plainColumns, rdc, "COMPRESS=BINARY");
    }


    /**
     * Every {@code COMPRESS=CHAR} oracle file decodes to its uncompressed twin, row by row, and
     * both hold exactly the values {@code make-sas-oracle.sas} defines.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "lit", "mix", "run"
    })
    void sasOracleCharFileDecodesToItsPlainTwin(String aDataset, @TempDir Path aDir)
        throws IOException
    {
        String charName = ORACLE + aDataset + "_char";
        String plainName = ORACLE + aDataset + "_plain";
        assertEquals(CompressionAlgorithm.SASYZCRL,
                dataset(aDir, charName).getCompressionAlgorithm(),
                charName + " must be RLE-compressed");
        assertEquals(null, dataset(aDir, plainName).getCompressionAlgorithm(),
                plainName + " must be uncompressed");

        List<String> plainColumns = columns(aDir, plainName);
        assertEquals(plainColumns, columns(aDir, charName), aDataset + ": column order");

        List<List<Object>> expected = expectedOracleRows(aDataset);
        List<List<Object>> plain = readAll(aDir, plainName);
        List<List<Object>> rle = readAll(aDir, charName);
        assertEquals(expected.size(), plain.size(), plainName + ": rows");
        assertEquals(expected.size(), rle.size(), charName + ": rows");
        for (int row = 0; row < expected.size(); row++)
        {
            assertEquals(plain.get(row), rle.get(row),
                    charName + " row " + row + " must equal the uncompressed row");
            assertEquals(expected.get(row), plain.get(row),
                    plainName + " row " + row + " must hold the generated values");
        }
    }


    /**
     * Pins which RLE commands SAS 9.4 wrote into the oracle files, by high nibble of the control
     * byte. The load-bearing facts: literal stretches of 65..400 bytes (lengths 96..111 included,
     * the range a one-byte {@code 0x2n} copy could encode) all came out as {@code 0x0n}, and no
     * {@code 0x1n}, {@code 0x2n} or {@code 0x3n} command occurs. The walk is independent of the
     * reader: it parses the page and subheader layout itself and must consume every compressed row
     * exactly, decoding it to the dataset's row length.
     */
    @Test
    void sasOracleControlNibbleCensus(@TempDir Path aDir) throws IOException
    {
        assertEquals(
                Map.of(0x0, 338, 0x6, 400, 0x8, 15, 0x9, 17, 0xA, 15, 0xB, 16, 0xD, 2, 0xF, 400),
                census(aDir, ORACLE + "lit_char"), "lit_char census");
        assertEquals(Map.of(0x0, 240, 0x4, 162, 0x6, 243, 0x8, 243, 0xB, 3, 0xC, 81, 0xF, 243),
                census(aDir, ORACLE + "mix_char"), "mix_char census");
        assertEquals(Map.of(0x4, 283, 0x5, 285, 0x6, 1484, 0x7, 283, 0x8, 2395, 0xC, 16, 0xD, 21,
                0xE, 16, 0xF, 1216), census(aDir, ORACLE + "run_char"), "run_char census");
    }


    /**
     * The rows {@code make-sas-oracle.sas} generates for one dataset, as this reader returns them.
     */
    private static List<List<Object>> expectedOracleRows(String aDataset)
    {
        List<List<Object>> rows = new ArrayList<>();
        switch (aDataset)
        {
        case "lit" ->
        {
            for (int n = 1; n <= 400; n++)
            {
                rows.add(List.of((double) n, literal(n)));
            }
        }
        case "mix" ->
        {
            for (int n = 60; n <= 140; n++)
            {
                for (int r : new int[]
                {
                        3, 20, 300
                })
                {
                    rows.add(
                            List.of((double) (n * 1000 + r), literal(n) + "Q".repeat(r) + "END!!"));
                }
            }
        }
        case "run" ->
        {
            for (char kind : new char[]
            {
                    'A', ' ', '@', '0'
            })
            {
                String fill = kind == '0' ? "\0" : String.valueOf(kind);
                for (int n = 1; n <= 300; n++)
                {
                    rows.add(List.of((double) n, String.valueOf(kind).stripTrailing(),
                            fill.repeat(n) + "xyz"));
                }
            }
        }
        default -> throw new IllegalArgumentException(aDataset);
        }
        return rows;
    }


    /** {@code byte(33 + mod(i * 7, 90))} for i = 1..n: no two consecutive bytes are equal. */
    private static String literal(int aLength)
    {
        StringBuilder sb = new StringBuilder(aLength);
        for (int i = 1; i <= aLength; i++)
        {
            sb.append((char) (33 + i * 7 % 90));
        }
        return sb.toString();
    }


    /**
     * Counts the RLE commands of every compressed row by the high nibble of their control byte,
     * walking the file's pages and subheader pointers without the reader. A row stored with
     * compression flag 4 / type 1 and shorter than the row length is an RLE stream.
     */
    private static Map<Integer, Integer> census(Path aDir, String aName) throws IOException
    {
        int rowLength = Math.toIntExact(dataset(aDir, aName).getRowLength());
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(materialise(aDir, aName)))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, b.get(37), aName + ": the walk reads little-endian files only");
        boolean u64 = b.get(32) == 0x33;
        int align = b.get(35) == 0x33 ? 4 : 0;
        int headerLength = b.getInt(196 + align);
        int pageLength = b.getInt(200 + align);
        long pageCount = u64 ? b.getLong(204 + align) : b.getInt(204 + align);
        int pageBitOffset = u64 ? 32 : 16;
        int intLength = u64 ? 8 : 4;

        Map<Integer, Integer> census = new TreeMap<>();
        int streams = 0;
        for (long page = 0; page < pageCount; page++)
        {
            int base = Math.toIntExact(headerLength + page * pageLength);
            int pointers = b.getShort(base + pageBitOffset + 4) & 0xFFFF;
            for (int i = 0; i < pointers; i++)
            {
                int pointer = base + pageBitOffset + 8 + i * 3 * intLength;
                int offset = Math.toIntExact(u64 ? b.getLong(pointer) : b.getInt(pointer));
                int length = Math.toIntExact(
                        u64 ? b.getLong(pointer + intLength) : b.getInt(pointer + intLength));
                int compression = b.get(pointer + 2 * intLength);
                int type = b.get(pointer + 2 * intLength + 1);
                if (compression == 4 && type == 1 && length > 0 && length < rowLength)
                {
                    walk(b, base + offset, length, rowLength, census, aName);
                    streams++;
                }
            }
        }
        assertTrue(streams > 0, aName + ": no RLE stream found - the walk is blind");
        return census;
    }


    private static void walk(ByteBuffer aBytes, int aStart, int aLength, int aRowLength,
            Map<Integer, Integer> aCensus, String aName)
    {
        int at = 0;
        int produced = 0;
        while (at < aLength)
        {
            int control = aBytes.get(aStart + at) & 0xFF;
            int high = control >>> 4;
            int low = control & 0x0F;
            aCensus.merge(high, 1, Integer::sum);
            int operand = at + 1 < aLength ? aBytes.get(aStart + at + 1) & 0xFF : 0;
            switch (high)
            {
            case 0x0 ->
            {
                produced += operand + 64 + low * 256;
                at += 2 + operand + 64 + low * 256;
            }
            case 0x1, 0x2, 0x3 ->
            {
                // Not seen from SAS; the cursor after it is unknown, so the row ends here and the
                // census assertion reports the code.
                return;
            }
            case 0x4 ->
            {
                produced += low * 256 + operand + 18;
                at += 3;
            }
            case 0x5, 0x6, 0x7 ->
            {
                produced += low * 256 + operand + 17;
                at += 2;
            }
            case 0x8, 0x9, 0xA, 0xB ->
            {
                produced += low + 1 + (high - 0x8) * 16;
                at += 1 + low + 1 + (high - 0x8) * 16;
            }
            case 0xC ->
            {
                produced += low + 3;
                at += 2;
            }
            default ->
            {
                produced += low + 2;
                at += 1;
            }
            }
        }
        assertEquals(aLength, at, aName + ": an RLE stream must be consumed exactly");
        assertEquals(aRowLength, produced, aName + ": an RLE stream must decode to the row length");
    }


    /**
     * Asserts what SAS actually wrote, so a uniformly broken set of readers cannot pass on mutual
     * equality alone. Every character cell repeats its own short period out to the full 200 bytes
     * and must contain no NUL - a NUL is the signature of a back-reference that copied its source
     * region in bulk before the overlapping bytes had been produced.
     */
    private static void assertContent(List<String> aColumns, List<List<Object>> aRows, String aWhat)
    {
        for (int period = 1; period <= 4; period++)
        {
            int index = aColumns.indexOf("VAR" + period);
            assertTrue(index >= 0, aWhat + ": column VAR" + period);
            String expected = repeatTo("ABCD".substring(0, period), WIDTH);

            for (int row = 0; row < aRows.size(); row++)
            {
                String where = aWhat + ": VAR" + period + " at row " + row;
                Object value = aRows.get(row).get(index);
                assertEquals(expected, value, where);
                assertEquals(-1, ((String) value).indexOf(0), where + " must hold no NUL byte");
            }
        }
    }


    private static String repeatTo(String aUnit, int aLength)
    {
        StringBuilder sb = new StringBuilder(aLength + aUnit.length());
        while (sb.length() < aLength)
        {
            sb.append(aUnit);
        }
        return sb.substring(0, aLength);
    }


    private static DatasetBdat dataset(Path aDir, String aName) throws IOException
    {
        return new ParserBdat().parseDataset(materialise(aDir, aName).toFile());
    }


    private static List<String> columns(Path aDir, String aName) throws IOException
    {
        return dataset(aDir, aName).getVariables().stream().map(VariableBdat::getName).toList();
    }


    private static List<List<Object>> readAll(Path aDir, String aName) throws IOException
    {
        Path file = materialise(aDir, aName);
        DatasetBdat dataset = new ParserBdat().parseDataset(file.toFile());
        List<VariableBdat> variables = dataset.getVariables();

        List<List<Object>> rows = new ArrayList<>();
        try (Stream<Observation> observations = dataset.streamObservations(file.toFile()))
        {
            observations.forEach(obs ->
            {
                List<Object> row = new ArrayList<>(variables.size());
                variables.forEach(v -> row.add(obs.getValue(v)));
                rows.add(row);
            });
        }
        return rows;
    }


    private static Path materialise(Path aDir, String aName) throws IOException
    {
        Path file = aDir.resolve(aName.replace('/', '_') + ".sas7bdat");
        if (!Files.exists(file))
        {
            try (InputStream in = RealSasCompressionTest.class
                    .getResourceAsStream(aName + ".sas7bdat"))
            {
                // Fail loudly if the fixture is missing - a skipped test is worse than none.
                assertNotNull(in, "fixture missing: " + aName + ".sas7bdat");
                Files.write(file, in.readAllBytes());
            }
        }
        return file;
    }

}
