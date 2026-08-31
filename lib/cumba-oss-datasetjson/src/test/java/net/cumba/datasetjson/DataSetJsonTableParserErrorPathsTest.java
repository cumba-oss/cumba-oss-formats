package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Targets error paths and infrequently-used value branches in {@link DataSetJsonTableParser}:
 * nested values (objects/arrays inside cells), malformed JSON, too-many / too-few row values,
 * post-rows attributes (warning path), and the various exception branches in {@code parseArray},
 * {@code parseObject}, {@code parseColumns}, {@code parseRow}, {@code parseRowToArray},
 * {@code parseRowsRowBased}, and {@code parseRows}.
 */
class DataSetJsonTableParserErrorPathsTest
{

    private DataSetJsonTableParser parser()
    {
        return new DataSetJsonTableParser();
    }


    private static ByteArrayInputStream bytes(String s)
    {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    // --- Metadata attribute parsing (parseAsValue covering all JSON token types) ---


    @Test
    void testMetadataWithObjectValueParsed() throws IOException
    {
        // sourceSystem is parsed as a Map<String, Object>; this exercises parseObject ->
        // parseAsValue
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"sourceSystem\":{\"name\":\"SYS\",\"version\":\"1.0\","
                + "\"nested\":{\"deep\":42,\"flag\":true,\"missing\":null,\"f\":1.5,\"arr\":[1,2,3],\"falsey\":false}},"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[]" + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        p.setHandlerRows((_, _, _, _) -> 0);

        p.parseDataSet(bytes(json));

        assertNotNull(table.get());
        DsjSourceSystem ss = table.get().getSourceSystem();
        assertNotNull(ss);
        assertEquals("SYS", ss.getName());
    }


    @Test
    void testMetadataWithArrayValueParsed()
    {
        // an array-valued attribute exercises parseArray in parseAsValue
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"customArray\":[\"a\",1,2.5,true,false,null,{\"k\":\"v\"},[]],"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        // should not throw — parseAsValue handles all token types via the array
        assertDoesNotThrow(() -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testMetadataWithBooleanValuesParsed()
    {
        // booleans / null in metadata are handled by parseAsValue
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"isTrue\":true,\"isFalse\":false,\"isNull\":null,\"someFloat\":3.14,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        assertDoesNotThrow(() -> p.parseDataSet(bytes(json)));
    }

    // --- Post-rows attribute handling (warning path) ---


    @Test
    void testAttributesAfterRowsIgnored()
    {
        // The parser logs a warning for attributes that follow the rows array, but continues.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[\"v\"]]," + "\"trailing\":\"ignored\"" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        assertDoesNotThrow(() -> p.parseDataSet(bytes(json)));
    }

    // --- No "columns" defined before "rows" (warning path) ---


    @Test
    void testRowsWithoutColumnsLogsWarningThrows()
    {
        // rows present but columns missing — parser logs a warning and then tries to build
        // a DsjTable with an empty columns array. DsjTable's @NonNull@Singular setter rejects
        // this, surfacing a NullPointerException through parseDataSet.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"rows\":[]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        // exercise the warning branch even though the build then fails on @NonNull columns.
        ByteArrayInputStream data = bytes(json);
        assertThrows(NullPointerException.class, () -> p.parseDataSet(data));
    }

    // --- Row value parsing: too-many / too-few values ---


    @Test
    void testTooManyValuesInRowThrows()
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[\"a\",\"b\",\"c\"]]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testNotEnoughValuesInRowWarnsButContinues()
    {
        // Two columns expected but only one given -> warning logged, row still delivered.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":["
                + "{\"itemOID\":\"IT.A\",\"name\":\"A\",\"label\":\"A\",\"dataType\":\"string\"},"
                + "{\"itemOID\":\"IT.B\",\"name\":\"B\",\"label\":\"B\",\"dataType\":\"string\"}"
                + "]," + "\"rows\":[[\"only-one\"]]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        assertDoesNotThrow(() -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testRowHandlerTooManyValuesThrows()
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[\"a\",\"b\"]]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRow((_, _, _) -> 0);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testRowHandlerTooFewValuesWarnsButContinues()
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":["
                + "{\"itemOID\":\"IT.A\",\"name\":\"A\",\"label\":\"A\",\"dataType\":\"string\"},"
                + "{\"itemOID\":\"IT.B\",\"name\":\"B\",\"label\":\"B\",\"dataType\":\"string\"}"
                + "]," + "\"rows\":[[\"only-one\"]]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRow((_, _, _) -> 0);
        assertDoesNotThrow(() -> p.parseDataSet(bytes(json)));
    }

    // --- parseObject / parseArray / parseColumns errors ---


    @Test
    void testColumnsNotArrayThrows()
    {
        // "columns" must be an array; pass an object instead.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"columns\":{\"oops\":\"not-array\"}," + "\"rows\":[]" + "}";

        DataSetJsonTableParser p = parser();
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testColumnsContainsNonObjectThrows()
    {
        // Columns array contains a non-object token (an integer).
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"columns\":[42]," + "\"rows\":[]" + "}";

        DataSetJsonTableParser p = parser();
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testRowsNotArrayThrows()
    {
        // "rows" must be an array; pass a string instead. Plain-JSON object expectation: the
        // parser calls dispatchParseRows which expects START_ARRAY.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":\"not-an-array\"" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testRowHandlerRowsNotArrayThrows()
    {
        // Same as above but with handlerRow set — exercises parseRowsRowBased error branch.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":\"not-an-array\"" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRow((_, _, _) -> 0);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testRowNotArrayInRowsThrows()
    {
        // A row entry must be an array; here it is an object.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[{\"oops\":1}]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }


    @Test
    void testRowHandlerRowNotArrayInRowsThrows()
    {
        // Same as above but with handlerRow set — exercises parseRowsRowBased else branch.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[42]" + "}";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRow((_, _, _) -> 0);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(json)));
    }

    // --- Slice handler with NDJSON path ---


    @Test
    void testSliceHandlerNdjsonFormat() throws IOException
    {
        String ndjson = """
                {"datasetJSONCreationDateTime":"2025-01-01T00:00:00","datasetJSONVersion":"1.1.0","itemGroupOID":"IG.T","name":"T","label":"L","columns":[{"itemOID":"IT.X","name":"X","label":"X","dataType":"string"}]}
                ["a"]
                ["b"]
                ["c"]
                """;

        DataSetJsonTableParser p = parser();
        p.setRowSliceSize(2); // force multiple slice callbacks
        AtomicReference<DsjTable> tref = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        p.setHandlerMetadata(t ->
        {
            tref.set(t);
            return 0;
        });
        p.setHandlerRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        p.parseDataSet(bytes(ndjson));

        assertNotNull(tref.get());
        assertEquals(3, total.get());
    }


    @Test
    void testSliceHandlerNdjsonAbort()
    {
        // Slice handler returns non-zero on NDJSON path -> IOException
        String ndjson = """
                {"datasetJSONCreationDateTime":"2025-01-01T00:00:00","datasetJSONVersion":"1.1.0","itemGroupOID":"IG.T","name":"T","label":"L","columns":[{"itemOID":"IT.X","name":"X","label":"X","dataType":"string"}]}
                ["a"]
                ["b"]
                ["c"]
                """;

        DataSetJsonTableParser p = parser();
        p.setRowSliceSize(1);
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> -1);
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(ndjson)));
    }


    @Test
    void testNdjsonStrayCloseArrayThrows()
    {
        // NDJSON cannot legally start with `]` — it should throw.
        String ndjson = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\","
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}]}\n"
                + "]";

        DataSetJsonTableParser p = parser();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, _) -> 0);
        // Jackson's parser may report the stray `]` differently; ensure an IOException surfaces.
        assertThrows(IOException.class, () -> p.parseDataSet(bytes(ndjson)));
    }

    // --- Object/array values in row cells (parseValueToSetter object/array branch) ---


    @Test
    void testRowHandlerNestedObjectInCell() throws IOException
    {
        // The row-handler path stores any JSON token; object values come back as a Map.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[{\"nested\":\"value\"}]]" + "}";

        DataSetJsonTableParser p = parser();
        Object[][] captured = new Object[1][];
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRow((_, _, vals) ->
        {
            captured[0] = vals.clone();
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertInstanceOf(Map.class, captured[0][0]);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) captured[0][0];
        assertEquals("value", m.get("nested"));
    }


    @Test
    void testRowHandlerNestedArrayInCell() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[[1,2,3]]]" + "}";

        DataSetJsonTableParser p = parser();
        Object[][] captured = new Object[1][];
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRow((_, _, vals) ->
        {
            captured[0] = vals.clone();
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertInstanceOf(List.class, captured[0][0]);
        List<?> list = (List<?>) captured[0][0];
        assertEquals(3, list.size());
    }


    @Test
    void testSliceHandlerNestedObjectInCell() throws IOException
    {
        // Slice-handler path: parseValueToSetter dispatches START_OBJECT
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[{\"k\":\"v\"}]]" + "}";

        DataSetJsonTableParser p = parser();
        Object[] captured = new Object[1];
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, buf) ->
        {
            captured[0] = buf[0].getValue(0);
            return 0;
        });

        p.parseDataSet(bytes(json));

        // ColumnBufferString.setValue with an object — the buffer stores the toString form.
        assertNotNull(captured[0]);
    }


    @Test
    void testSliceHandlerBooleanValuesViaParseValueToSetter() throws IOException
    {
        // Set up a boolean column so values flow through parseValueToSetter VALUE_TRUE/FALSE
        // branches.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":2,"
                + "\"columns\":[{\"itemOID\":\"IT.B\",\"name\":\"B\",\"label\":\"B\",\"dataType\":\"boolean\"}],"
                + "\"rows\":[[true],[false]]" + "}";

        DataSetJsonTableParser p = parser();
        java.util.List<Object> seen = new java.util.ArrayList<>();
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, n, buf) ->
        {
            for (int i = 0; i < n; i++)
            {
                seen.add(buf[0].getValue(i));
            }
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertEquals(2, seen.size());
    }


    @Test
    void testSliceHandlerFloatValueViaParseValueToSetter() throws IOException
    {
        // Float-typed column on slice path — sends setDoubleValue and parses NUMBER_FLOAT branch.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.V\",\"name\":\"V\",\"label\":\"V\",\"dataType\":\"float\"}],"
                + "\"rows\":[[1.25]]" + "}";

        DataSetJsonTableParser p = parser();
        double[] captured = new double[1];
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, buf) ->
        {
            captured[0] = buf[0].getDoubleValue(0);
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertEquals(1.25, captured[0], 1e-9);
    }


    @Test
    void testSliceHandlerNullInIntegerColumn() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.V\",\"name\":\"V\",\"label\":\"V\",\"dataType\":\"integer\"}],"
                + "\"rows\":[[null]]" + "}";

        DataSetJsonTableParser p = parser();
        boolean[] missing = new boolean[1];
        p.setHandlerMetadata(_ -> 0);
        p.setHandlerRows((_, _, _, buf) ->
        {
            missing[0] = buf[0].isMissing(0);
            return 0;
        });

        p.parseDataSet(bytes(json));

        assertEquals(true, missing[0]);
    }

    // --- Default record count from metadata records=missing ---


    @Test
    void testTableBuiltWithoutRecordsAttribute() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\","
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[\"v\"]]" + "}";

        DataSetJsonTableParser p = parser();
        AtomicReference<DsjTable> table = new AtomicReference<>();
        p.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        p.setHandlerRows((_, _, _, _) -> 0);

        p.parseDataSet(bytes(json));

        assertEquals(0, table.get().getRecords()); // default 0 when records is absent
    }


    @Test
    void testTableBuiltWithSourceSystemNonMap() throws IOException
    {
        // sourceSystem given as a string (not a Map) -> buildSourceSystem returns null.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":0,"
                + "\"sourceSystem\":\"not-a-map\","
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[]" + "}";

        DataSetJsonTableParser p = parser();
        AtomicReference<DsjTable> table = new AtomicReference<>();
        p.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        p.setHandlerRows((_, _, _, _) -> 0);

        p.parseDataSet(bytes(json));

        // Non-map sourceSystem -> buildSourceSystem returns null.
        assertNull(table.get().getSourceSystem());
    }
}
