package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.sasutils.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 */
class RealSasCompressionTest
{

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


    private static List<String> columns(Path aDir, String aName) throws IOException
    {
        return new ParserBdat().parseDataset(materialise(aDir, aName).toFile()).getVariables()
                .stream().map(VariableBdat::getName).toList();
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
        Path file = aDir.resolve(aName + ".sas7bdat");
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
