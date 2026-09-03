package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Edge case tests for DataSetJsonTableParser.
 */
class DataSetJsonTableParserEdgeCasesTest
{

    private DataSetJsonTableParser parser;

    @BeforeEach
    void setUp()
    {
        parser = new DataSetJsonTableParser();
    }


    @Test
    void testParseEmptyStream()
    {
        byte[] data = {};
        assertThrows(IOException.class, () -> parser.parseDataSet(new ByteArrayInputStream(data)));
    }


    @Test
    void testParseSingleByteStream()
    {
        byte[] data =
        {
                0x7B
        }; // just '{'
        assertThrows(IOException.class, () -> parser.parseDataSet(new ByteArrayInputStream(data)));
    }


    @Test
    void testParseInvalidHeader()
    {
        byte[] data =
        {
                0x41, 0x42
        }; // "AB"
        assertThrows(IOException.class, () -> parser.parseDataSet(new ByteArrayInputStream(data)));
    }


    @Test
    void testParseBooleanValues() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.FLAG\",\"name\":\"FLAG\",\"label\":\"Flag\",\"dataType\":\"boolean\"}],"
                + "\"rows\":[[true]]" + "}";

        final Object[] captured =
        {
                null
        };
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, _, columnData) ->
        {
            captured[0] = columnData[0].getValue(0);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        // Boolean values are stored as String in ColumnBufferString (default buffer for boolean
        // type)
        assertEquals("true", captured[0]);
    }


    @Test
    void testParseFloatColumn() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.VAL\",\"name\":\"VAL\",\"label\":\"Value\",\"dataType\":\"float\"}],"
                + "\"rows\":[[2.71828]]" + "}";

        final double[] captured =
        {
                0.0
        };
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, _, columnData) ->
        {
            captured[0] = columnData[0].getDoubleValue(0);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2.71828, captured[0], 0.00001);
    }


    @Test
    void testParseZeroRecords() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":0,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[]" + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        AtomicInteger rowCalls = new AtomicInteger(0);

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) ->
        {
            rowCalls.incrementAndGet();
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(table.get());
        assertEquals(0, table.get().getRecords());
        assertEquals(0, rowCalls.get()); // no row callbacks for empty table
    }


    @Test
    void testParseWithTargetDataType() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":0,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"integer\",\"targetDataType\":\"integer\"}],"
                + "\"rows\":[]" + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals("integer", table.get().getColumn(0).getTargetDataType());
    }


    @Test
    void testParseWithDisplayFormat() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":0,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"integer\",\"displayFormat\":\"8.\"}],"
                + "\"rows\":[]" + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals("8.", table.get().getColumn(0).getDisplayFormat());
    }


    @Test
    void testParseManyRowsMultipleSlices() throws IOException
    {
        int numRows = 25;
        parser.setRowSliceSize(10);

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\",");
        sb.append("\"datasetJSONVersion\":\"1.1.0\",");
        sb.append("\"itemGroupOID\":\"IG.TEST\",");
        sb.append("\"name\":\"TEST\",");
        sb.append("\"label\":\"Test\",");
        sb.append("\"records\":").append(numRows).append(",");
        sb.append(
                "\"columns\":[{\"itemOID\":\"IT.V\",\"name\":\"V\",\"label\":\"V\",\"dataType\":\"integer\"}],");
        sb.append("\"rows\":[");
        for (int i = 0; i < numRows; i++)
        {
            if (i > 0)
            {
                sb.append(",");
            }
            sb.append("[").append(i).append("]");
        }
        sb.append("]");
        sb.append("}");

        AtomicInteger callbackCount = new AtomicInteger(0);
        AtomicInteger totalRowCount = new AtomicInteger(0);

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, count, _) ->
        {
            callbackCount.incrementAndGet();
            totalRowCount.addAndGet(count);
            return 0;
        });

        parser.parseDataSet(
                new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));

        assertEquals(numRows, totalRowCount.get());
        assertTrue(callbackCount.get() >= 3); // 25 rows / 10 per slice = at least 3 callbacks
    }


    @Test
    void testParseNdjsonFormat() throws IOException
    {
        // NDJSON: first line = metadata object, subsequent lines = row arrays
        String metadataLine = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":2,"
                + "\"columns\":[{\"itemOID\":\"IT.V\",\"name\":\"V\",\"label\":\"V\",\"dataType\":\"string\"}]}";
        String row1 = "[\"hello\"]";
        String row2 = "[\"world\"]";

        // Write manually to simulate NDJSON
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(metadataLine.getBytes(StandardCharsets.UTF_8));
        baos.write(0x0A);
        baos.write(row1.getBytes(StandardCharsets.UTF_8));
        baos.write(0x0A);
        baos.write(row2.getBytes(StandardCharsets.UTF_8));
        baos.write(0x0A);

        AtomicReference<DsjTable> table = new AtomicReference<>();
        AtomicInteger totalRows = new AtomicInteger(0);

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, count, _) ->
        {
            totalRows.addAndGet(count);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(baos.toByteArray()));

        assertNotNull(table.get());
        assertEquals("TEST", table.get().getName());
        assertEquals(2, totalRows.get());
    }
}
