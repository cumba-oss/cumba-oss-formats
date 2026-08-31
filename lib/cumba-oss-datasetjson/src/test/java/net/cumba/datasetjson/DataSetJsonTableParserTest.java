package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSetJsonTableParserTest
{

    private DataSetJsonTableParser parser;

    @BeforeEach
    void setUp()
    {
        parser = new DataSetJsonTableParser();
    }


    private String buildMinimalJson(int rowCount)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\",");
        sb.append("\"datasetJSONVersion\":\"1.1.0\",");
        sb.append("\"itemGroupOID\":\"IG.DM\",");
        sb.append("\"name\":\"DM\",");
        sb.append("\"label\":\"Demographics\",");
        sb.append("\"records\":").append(rowCount).append(",");
        sb.append("\"columns\":[");
        sb.append(
                "{\"itemOID\":\"IT.NAME\",\"name\":\"NAME\",\"label\":\"Name\",\"dataType\":\"string\"},");
        sb.append(
                "{\"itemOID\":\"IT.AGE\",\"name\":\"AGE\",\"label\":\"Age\",\"dataType\":\"integer\"}");
        sb.append("],");
        sb.append("\"rows\":[");
        for (int i = 0; i < rowCount; i++)
        {
            if (i > 0)
            {
                sb.append(",");
            }
            sb.append("[\"Person").append(i).append("\",").append(20 + i).append("]");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }


    @Test
    void testParseBasicJson() throws IOException
    {
        String json = buildMinimalJson(3);
        AtomicReference<DsjTable> table = new AtomicReference<>();
        AtomicInteger totalRows = new AtomicInteger(0);

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, rowCount, _) ->
        {
            totalRows.addAndGet(rowCount);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(table.get());
        assertEquals("DM", table.get().getName());
        assertEquals(2, table.get().getColumnCount());
        assertEquals("NAME", table.get().getColumn(0).getName());
        assertEquals("AGE", table.get().getColumn(1).getName());
        assertEquals(3, totalRows.get());
    }


    @Test
    void testParseWithSlicing() throws IOException
    {
        // Use a small slice size to trigger multiple callbacks
        parser.setRowSliceSize(2);

        String json = buildMinimalJson(5);
        AtomicInteger callbackCount = new AtomicInteger(0);
        List<Integer> rowCounts = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, rowCount, _) ->
        {
            callbackCount.incrementAndGet();
            rowCounts.add(rowCount);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertTrue(callbackCount.get() >= 2);
        int total = rowCounts.stream().mapToInt(Integer::intValue).sum();
        assertEquals(5, total);
    }


    @Test
    void testParseGzip() throws IOException
    {
        String json = buildMinimalJson(1);
        byte[] gzipped;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos))
        {
            gos.write(json.getBytes(StandardCharsets.UTF_8));
        }
        gzipped = baos.toByteArray();

        AtomicReference<DsjTable> table = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(new ByteArrayInputStream(gzipped));

        assertNotNull(table.get());
        assertEquals("DM", table.get().getName());
    }


    @Test
    void testParseZlib() throws IOException
    {
        String json = buildMinimalJson(1);
        byte[] deflated;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos))
        {
            dos.write(json.getBytes(StandardCharsets.UTF_8));
        }
        deflated = baos.toByteArray();

        AtomicReference<DsjTable> table = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(new ByteArrayInputStream(deflated));

        assertNotNull(table.get());
        assertEquals("DM", table.get().getName());
    }


    @Test
    void testParseUnexpectedHeader()
    {
        byte[] data =
        {
                0x00, 0x00, 0x00
        };
        assertThrows(IOException.class, () -> parser.parseDataSet(new ByteArrayInputStream(data)));
    }


    @Test
    void testMetadataHandlerAbort()
    {
        String json = buildMinimalJson(1);
        parser.setHandlerMetadata(_ -> -1);

        assertThrows(IOException.class, () -> parser
                .parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }


    @Test
    void testRowHandlerAbort()
    {
        String json = buildMinimalJson(3);
        parser.setRowSliceSize(1);
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, _, _) -> -1);

        assertThrows(IOException.class, () -> parser
                .parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }


    @Test
    void testParseColumnValues() throws IOException
    {
        String json = buildMinimalJson(1);

        final String[] capturedName =
        {
                null
        };
        final long[] capturedAge =
        {
                -1
        };

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, _, columnData) ->
        {
            capturedName[0] = columnData[0].getStringValue(0);
            capturedAge[0] = columnData[1].getLongValue(0);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals("Person0", capturedName[0]);
        assertEquals(20L, capturedAge[0]);
    }


    @Test
    void testParseWithOptionalFields() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.DM\","
                + "\"name\":\"DM\"," + "\"label\":\"Demo\"," + "\"records\":1,"
                + "\"fileOID\":\"F1\"," + "\"originator\":\"TestOrg\"," + "\"studyOID\":\"S1\","
                + "\"metaDataRef\":\"http://example.com\"," + "\"metaDataVersionOID\":\"MDV1\","
                + "\"dbLastModifiedDateTime\":\"2025-01-02T00:00:00\","
                + "\"sourceSystem\":{\"name\":\"SYS\",\"version\":\"1.0\"},"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}],"
                + "\"rows\":[[\"val\"]]" + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        DsjTable t = table.get();
        assertNotNull(t);
        assertEquals("F1", t.getFileOID());
        assertEquals("TestOrg", t.getOriginator());
        assertEquals("S1", t.getStudyOID());
        assertNotNull(t.getSourceSystem());
        assertEquals("SYS", t.getSourceSystem().getName());
    }


    @Test
    void testParseColumnWithLength() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.DM\","
                + "\"name\":\"DM\"," + "\"label\":\"Demo\"," + "\"records\":0,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\",\"length\":20,\"keySequence\":1}],"
                + "\"rows\":[]" + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        DsjTable t = table.get();
        assertEquals(20, t.getColumn(0).getLength());
        assertEquals(1, t.getColumn(0).getKeySequence());
    }


    @Test
    void testParseNoHandlers()
    {
        // Should not throw even without handlers
        String json = buildMinimalJson(1);
        assertDoesNotThrow(() -> parser
                .parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }


    @Test
    void testParseDoubleColumn() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.VAL\",\"name\":\"VAL\",\"label\":\"Value\",\"dataType\":\"double\"}],"
                + "\"rows\":[[3.14]]" + "}";

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
        assertEquals(3.14, captured[0], 0.001);
    }


    @Test
    void testParseNullValue() throws IOException
    {
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\"," + "\"itemGroupOID\":\"IG.TEST\","
                + "\"name\":\"TEST\"," + "\"label\":\"Test\"," + "\"records\":1,"
                + "\"columns\":[{\"itemOID\":\"IT.VAL\",\"name\":\"VAL\",\"label\":\"Value\",\"dataType\":\"string\"}],"
                + "\"rows\":[[null]]" + "}";

        final boolean[] missing =
        {
                false
        };
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, _, columnData) ->
        {
            missing[0] = columnData[0].isMissing(0);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertTrue(missing[0]);
    }


    @Test
    void testRowSliceSize()
    {
        parser.setRowSliceSize(500);
        assertEquals(500, parser.getRowSliceSize());
    }


    @Test
    void testDefaultRowSliceSize()
    {
        assertEquals(1000, parser.getRowSliceSize());
    }
}
