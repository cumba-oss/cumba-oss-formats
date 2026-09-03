package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSetJsonTableParserRowHandlerTest
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


    private String buildMixedTypesJson()
    {
        return """
                {
                  "datasetJSONCreationDateTime": "2025-01-01T00:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.TEST",
                  "name": "TEST",
                  "label": "Test Dataset",
                  "records": 2,
                  "columns": [
                    {"itemOID": "IT.S", "name": "STR", "label": "String", "dataType": "string"},
                    {"itemOID": "IT.I", "name": "INT", "label": "Integer", "dataType": "integer"},
                    {"itemOID": "IT.F", "name": "FLT", "label": "Float", "dataType": "float"},
                    {"itemOID": "IT.B", "name": "BOOL", "label": "Boolean", "dataType": "boolean"},
                    {"itemOID": "IT.N", "name": "NUL", "label": "Nullable", "dataType": "string"}
                  ],
                  "rows": [
                    ["hello", 42, 3.14, true, null],
                    ["world", -1, 0.0, false, "present"]
                  ]
                }
                """;
    }


    private String buildNDJson(int rowCount)
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
        sb.append("]");
        sb.append("}\n");
        for (int i = 0; i < rowCount; i++)
        {
            sb.append("[\"Person").append(i).append("\",").append(20 + i).append("]\n");
        }
        return sb.toString();
    }


    @Test
    void testRowHandlerBasicJson() throws IOException
    {
        String json = buildMinimalJson(3);
        AtomicReference<DsjTable> metaRef = new AtomicReference<>();
        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(t ->
        {
            metaRef.set(t);
            return 0;
        });
        parser.setHandlerRow((_, rowIndex, values) ->
        {
            assertEquals(rows.size(), rowIndex);
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(metaRef.get());
        assertEquals("DM", metaRef.get().getName());
        assertEquals(3, rows.size());

        for (int i = 0; i < 3; i++)
        {
            assertEquals("Person" + i, rows.get(i)[0]);
            assertEquals(20L + i, rows.get(i)[1]);
        }
    }


    @Test
    void testRowHandlerValueTypes() throws IOException
    {
        String json = buildMixedTypesJson();
        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, rows.size());

        // Row 0: ["hello", 42, 3.14, true, null]
        Object[] row0 = rows.get(0);
        assertInstanceOf(String.class, row0[0]);
        assertEquals("hello", row0[0]);
        assertInstanceOf(Long.class, row0[1]);
        assertEquals(42L, row0[1]);
        assertInstanceOf(Double.class, row0[2]);
        assertEquals(3.14, (Double) row0[2], 0.001);
        assertInstanceOf(Boolean.class, row0[3]);
        assertEquals(true, row0[3]);
        assertNull(row0[4]);

        // Row 1: ["world", -1, 0.0, false, "present"]
        Object[] row1 = rows.get(1);
        assertEquals("world", row1[0]);
        assertEquals(-1L, row1[1]);
        assertEquals(0.0, (Double) row1[2], 0.001);
        assertEquals(false, row1[3]);
        assertEquals("present", row1[4]);
    }


    @Test
    void testRowHandlerNDJson() throws IOException
    {
        String ndjson = buildNDJson(3);
        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, rows.size());
        for (int i = 0; i < 3; i++)
        {
            assertEquals("Person" + i, rows.get(i)[0]);
            assertEquals(20L + i, rows.get(i)[1]);
        }
    }


    @Test
    void testRowHandlerDsjcZlib() throws IOException
    {
        String ndjson = buildNDJson(3);
        byte[] compressed = compressZlib(ndjson.getBytes(StandardCharsets.UTF_8));

        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(compressed));

        assertEquals(3, rows.size());
        for (int i = 0; i < 3; i++)
        {
            assertEquals("Person" + i, rows.get(i)[0]);
            assertEquals(20L + i, rows.get(i)[1]);
        }
    }


    @Test
    void testRowHandlerGzip() throws IOException
    {
        String json = buildMinimalJson(3);
        byte[] compressed = compressGzip(json.getBytes(StandardCharsets.UTF_8));

        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(compressed));

        assertEquals(3, rows.size());
    }


    @Test
    void testRowHandlerAbort()
    {
        String json = buildMinimalJson(10);
        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return rows.size() >= 3 ? 1 : 0;
        });

        assertThrows(IOException.class, () -> parser
                .parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));

        assertEquals(3, rows.size());
    }


    @Test
    void testBothHandlersSetThrows()
    {
        String json = buildMinimalJson(1);

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, _, _) -> 0);
        parser.setHandlerRow((_, _, _) -> 0);

        assertThrows(IOException.class, () -> parser
                .parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }


    @Test
    void testRowHandlerEmptyRows() throws IOException
    {
        String json = buildMinimalJson(0);
        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, rows.size());
    }


    @Test
    void testRowHandlerColumnCount() throws IOException
    {
        String json = buildMixedTypesJson();
        AtomicReference<DsjTable> metaRef = new AtomicReference<>();
        List<Object[]> rows = new ArrayList<>();

        parser.setHandlerMetadata(t ->
        {
            metaRef.set(t);
            return 0;
        });
        parser.setHandlerRow((_, _, values) ->
        {
            rows.add(values.clone());
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(5, metaRef.get().getColumnCount());
        for (Object[] row : rows)
        {
            assertEquals(5, row.length);
        }
    }


    private byte[] compressZlib(byte[] data) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos))
        {
            dos.write(data);
        }
        return bos.toByteArray();
    }


    private byte[] compressGzip(byte[] data) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos))
        {
            gos.write(data);
        }
        return bos.toByteArray();
    }
}
