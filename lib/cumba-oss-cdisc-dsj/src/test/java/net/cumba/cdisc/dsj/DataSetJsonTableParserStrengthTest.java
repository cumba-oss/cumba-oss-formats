package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Assertions on reader behaviour that the rest of the suite reached but never checked: the exact
 * boundary of the declared-record check, the diagnostics a malformed document produces, and the
 * row-cell value types that only the columnar slice path converts.
 */
class DataSetJsonTableParserStrengthTest
{

    private static final String COLUMN_X = "{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\","
            + "\"dataType\":\"string\"}";

    private static InputStream bytes(String aJson)
    {
        return new ByteArrayInputStream(aJson.getBytes(StandardCharsets.UTF_8));
    }


    private static String doc(String aExtraMeta, String aColumns, String aRows)
    {
        return "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\"," + aExtraMeta + "\"columns\":[" + aColumns + "]," + "\"rows\":"
                + aRows + "}";
    }


    @Test
    void testZeroDeclaredRecordsWithARowIsRejected()
    {
        // "records": 0 is a real declaration, not "unknown" — only -1 means unknown. A file
        // claiming zero records while carrying one must not be delivered as a clean read.
        String json = doc("\"records\":0,", COLUMN_X, "[[\"a\"]]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("(0)") && ex.getMessage().contains("(1)"),
                ex.getMessage());
    }


    @Test
    void testZeroDeclaredRecordsWithNoRowsIsAccepted() throws IOException
    {
        // The other half of the boundary: zero declared and zero delivered is a valid empty
        // dataset. Without this the check above could be satisfied by rejecting every zero.
        String json = doc("\"records\":0,", COLUMN_X, "[]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        List<Integer> slices = new ArrayList<>();
        p.setHandlerRows((_, _, n, _) ->
        {
            slices.add(n);
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertTrue(slices.isEmpty(), slices.toString());
    }


    @Test
    void testMissingRequiredTableFieldNamesTheField()
    {
        // A document missing a mandatory member must say which one. The message is the only
        // thing that distinguishes "your file is missing 'label'" from a bare null dereference.
        String json = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"columns\":[" + COLUMN_X + "],\"rows\":[[\"a\"]]}";
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        Exception ex = assertThrows(Exception.class, () -> p.parseDataSet(bytes(json)));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("missing required Dataset-JSON field: label"),
                ex.getMessage());
    }


    @Test
    void testMissingRequiredColumnFieldNamesTheField()
    {
        String json = doc("", "{\"itemOID\":\"IT.X\",\"name\":\"X\",\"dataType\":\"string\"}",
                "[[\"a\"]]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        Exception ex = assertThrows(Exception.class, () -> p.parseDataSet(bytes(json)));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("missing required Dataset-JSON field: label"),
                ex.getMessage());
    }


    @Test
    void testMissingRequiredSourceSystemFieldNamesTheField()
    {
        String json = doc("\"sourceSystem\":{\"name\":\"EDC\"},", COLUMN_X, "[[\"a\"]]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        Exception ex = assertThrows(Exception.class, () -> p.parseDataSet(bytes(json)));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("missing required Dataset-JSON field: version"),
                ex.getMessage());
    }


    @Test
    void testSliceHandlerStoresBooleanAndArrayCells() throws IOException
    {
        // false, true and a nested array all have their own arm in parseValueToSetter. A dropped
        // setValue call leaves the cell holding the previous slice's value, which reads as data.
        String json = doc("\"records\":1,",
                "{\"itemOID\":\"IT.A\",\"name\":\"A\",\"label\":\"A\",\"dataType\":\"boolean\"},"
                        + "{\"itemOID\":\"IT.B\",\"name\":\"B\",\"label\":\"B\","
                        + "\"dataType\":\"boolean\"},"
                        + "{\"itemOID\":\"IT.C\",\"name\":\"C\",\"label\":\"C\","
                        + "\"dataType\":\"string\"}",
                "[[false,true,[1,2]]]");

        DataSetJsonTableParser p = new DataSetJsonTableParser();
        List<String> cells = new ArrayList<>();
        p.setHandlerRows((_, _, _, data) ->
        {
            cells.add(data[0].getStringValue(0));
            cells.add(data[1].getStringValue(0));
            cells.add(data[2].getStringValue(0));
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertEquals(List.of("false", "true", "[1, 2]"), cells);
    }


    @Test
    void testTooManyValuesMessageCountsTheValuesAndNamesTheFileRow()
    {
        // Row 3 of the file, not row 1 of the current slice: with a slice size of 2 the two
        // numbers differ, and the one a user can act on is the file row.
        String json = doc("\"records\":4,", COLUMN_X,
                "[[\"a\"],[\"b\"],[\"c\"],[\"d\",\"e\",\"f\"]]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setRowSliceSize(2);
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("Too many values in row 3"), ex.getMessage());
        assertTrue(ex.getMessage().contains("found: 2"), ex.getMessage());
    }


    @Test
    void testNotEnoughValuesMessageNamesTheFileRow()
    {
        String json = doc("\"records\":4,",
                COLUMN_X + ",{\"itemOID\":\"IT.Y\",\"name\":\"Y\",\"label\":\"Y\","
                        + "\"dataType\":\"string\"}",
                "[[\"a\",\"a\"],[\"b\",\"b\"],[\"c\",\"c\"],[\"d\"]]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setRowSliceSize(2);
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("Not enough values in row 3"), ex.getMessage());
        assertTrue(ex.getMessage().contains("found: 1"), ex.getMessage());
    }


    @Test
    void testRowHandlerTooManyValuesMessageCountsTheValues()
    {
        String json = doc("\"records\":1,", COLUMN_X, "[[\"a\",\"b\",\"c\"]]");
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRow((_, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("found: 2"), ex.getMessage());
    }


    @Test
    void testNdjsonFinalSliceHonoursAnAbort()
    {
        // The final NDJSON slice is dispatched outside the main loop. It has to honour a non-zero
        // return exactly like every other dispatch, or the consumer's rejection is discarded and
        // the parse reports success.
        String ndjson = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\",\"columns\":[" + COLUMN_X + "]}\n[\"a\"]\n[\"b\"]\n";
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setRowSliceSize(1000); // one slice only, delivered by the post-loop NDJSON branch
        p.setHandlerRows((_, _, _, _) -> 7);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(ndjson)));
        assertEquals("User aborted!", ex.getMessage());
    }


    @Test
    void testHeaderPeekToleratesAStreamThatDribbles() throws IOException
    {
        // read(byte[]) may legitimately hand back one byte at a time; a socket-backed URL stream
        // is the realistic case. The format peek must not mistake that for a truncated file.
        String json = doc("\"records\":1,", COLUMN_X, "[[\"a\"]]");
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);

        DataSetJsonTableParser p = new DataSetJsonTableParser();
        List<String> cells = new ArrayList<>();
        p.setHandlerRows((_, _, _, data) ->
        {
            cells.add(data[0].getStringValue(0));
            return 0;
        });

        try (InputStream dribbling = oneBytePerRead(raw))
        {
            p.parseDataSet(dribbling);
        }

        assertEquals(List.of("a"), cells);
    }


    /** A stream whose {@code read(byte[], int, int)} hands back one byte per call. */
    private static InputStream oneBytePerRead(byte[] raw)
    {
        return new InputStream()
        {

            private int idx;

            @Override
            public int read()
            {
                return idx < raw.length ? raw[idx++] & 0xFF : -1;
            }


            @Override
            public int read(byte[] aBuf, int aOff, int aLen)
            {
                if (aLen == 0)
                {
                    return 0;
                }
                if (idx >= raw.length)
                {
                    return -1;
                }
                // One byte per call, whatever was asked for — permitted by the contract.
                aBuf[aOff] = raw[idx++];
                return 1;
            }
        };
    }


    @Test
    void testNdjsonWithoutColumnsThrowsNamingTheMissingMember()
    {
        // Same omission on the NDJSON layout, which reaches the row data through the separate
        // top-level-array branch rather than through a "rows" field.
        String ndjson = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\"}\n[\"a\"]\n";
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(ndjson)));
        assertTrue(ex.getMessage().contains("missing required Dataset-JSON field: columns"),
                ex.getMessage());
    }


    @Test
    void testDocumentWithNeitherColumnsNorRowsThrowsNamingTheMissingMember()
    {
        // A metadata-only document. "columns" is Required by the specification, so this is a
        // malformed file, not a table that happens to have no variables — and it must say so
        // rather than surfacing a Lombok @NonNull failure.
        String json = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\"}";
        DataSetJsonTableParser p = new DataSetJsonTableParser();

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("missing required Dataset-JSON field: columns"),
                ex.getMessage());
    }


    @Test
    void testColumnsDeclaredAfterRowsThrowsNamingTheMissingMember()
    {
        // "columns" present but too late: the row shape was needed before the first row. Failing
        // here is what stops the values being bucketed by a shape that was not yet known.
        String json = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\",\"rows\":[[\"a\"]],\"columns\":[" + COLUMN_X + "]}";
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("it must be declared before"), ex.getMessage());
    }


    @Test
    void testNdjsonMetadataAbortStopsTheParse()
    {
        // The NDJSON layout reaches the metadata handler through its own branch. A handler that
        // refuses the table there has to stop the parse, exactly as on the plain-JSON layout.
        String ndjson = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\",\"columns\":[" + COLUMN_X + "]}\n[\"a\"]\n";
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerMetadata(_ -> 1);
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(ndjson)));
        assertEquals("User aborted!", ex.getMessage());
    }


    @Test
    void testASecondBlockOfRowDataIsRejected()
    {
        // A file whose rows were already delivered and which then presents another top-level
        // array is two datasets concatenated. Parsing the second block on top of the first would
        // silently merge two tables' rows into one.
        String json = doc("\"records\":1,", COLUMN_X, "[[\"a\"]]") + "\n[\"b\"]\n";
        DataSetJsonTableParser p = new DataSetJsonTableParser();
        p.setHandlerRows((_, _, _, _) -> 0);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("Rows already parsed"), ex.getMessage());
    }
}
