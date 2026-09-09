package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the record-count integrity fixes F-dsj-01 (absent {@code records} is the {@code -1}
 * "unknown" sentinel, not a fabricated 0), F-dsj-02 (a rows-less document is delivered as a valid
 * empty dataset instead of silent nothing) and F-dsj-03 (the declared {@code records} is verified
 * against the rows actually parsed, in the sequential and the parallel parser, including the
 * empty-document path).
 */
class DataSetJsonRecordCountIntegrityTest
{

    private static final String META_PREFIX = "{" //
            + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\"," //
            + "\"datasetJSONVersion\":\"1.1.0\"," //
            + "\"itemGroupOID\":\"IG.TEST\"," //
            + "\"name\":\"TEST\"," //
            + "\"label\":\"Test\",";

    private static final String COLUMNS = //
            "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"string\"}]";

    private static ByteArrayInputStream bytes(String json)
    {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ F-dsj-01


    @Test
    void absentRecordsParsesAsUnknownSentinel() throws IOException
    {
        String json = META_PREFIX + COLUMNS + ",\"rows\":[[\"v\"]]}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);
        parser.parseDataSet(bytes(json));

        assertNotNull(table.get());
        assertEquals(-1, table.get().getRecords(),
                "absent records must surface as -1 (unknown), not as a claim of 0 records");
    }


    @Test
    void nonNumericRecordsParsesAsUnknownSentinel() throws IOException
    {
        String json = META_PREFIX + "\"records\":\"12\"," + COLUMNS + ",\"rows\":[[\"v\"]]}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);
        parser.parseDataSet(bytes(json));

        assertEquals(-1, table.get().getRecords(),
                "a malformed (non-numeric) records member must map to unknown, not to 0");
    }

    // ------------------------------------------------------------------ F-dsj-02


    @Test
    void rowsLessDocumentDeliversEmptyTable() throws IOException
    {
        // No "rows" member at all — a legitimate empty dataset (records absent).
        String json = META_PREFIX + COLUMNS + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        AtomicInteger rowCalls = new AtomicInteger();
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
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
        parser.parseDataSet(bytes(json));

        assertNotNull(table.get(), "metadata handler must fire for a rows-less document");
        assertEquals(1, table.get().getColumnCount());
        assertEquals(0, rowCalls.get(), "no row callbacks for an empty dataset");
    }


    @Test
    void rowsLessDocumentWithZeroRecordsDeliversEmptyTable() throws IOException
    {
        String json = META_PREFIX + "\"records\":0," + COLUMNS + "}";

        AtomicReference<DsjTable> table = new AtomicReference<>();
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.parseDataSet(bytes(json));

        assertNotNull(table.get());
        assertEquals(0, table.get().getRecords());
    }


    @Test
    void rowsLessDocumentAbortsWhenMetadataHandlerRequestsIt()
    {
        String json = META_PREFIX + COLUMNS + "}";

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerMetadata(_ -> 1);
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> parser.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("aborted"), ex.getMessage());
    }

    // ------------------------------------------------------------------ F-dsj-03


    @Test
    void rowsLessDocumentDeclaringRecordsFails()
    {
        // The guard that makes F-dsj-02's leniency safe: a file that lost its rows member but
        // still declares records > 0 must NOT read as a valid empty dataset.
        String json = META_PREFIX + "\"records\":5000," + COLUMNS + "}";

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerMetadata(_ -> 0);
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> parser.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("5000") && ex.getMessage().contains("0"),
                ex.getMessage());
    }


    @Test
    void declaredRecordsMismatchFailsSliceBased()
    {
        String json = META_PREFIX + "\"records\":3," + COLUMNS + ",\"rows\":[[\"a\"],[\"b\"]]}";

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerRows((_, _, _, _) -> 0);
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> parser.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("records"), ex.getMessage());
    }


    @Test
    void declaredRecordsMismatchFailsRowBased()
    {
        // Same check on the row-based dispatch path.
        String json = META_PREFIX + "\"records\":1," + COLUMNS + ",\"rows\":[[\"a\"],[\"b\"]]}";

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerRow((_, _, _) -> 0);
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> parser.parseDataSet(bytes(json)));
        assertTrue(ex.getMessage().contains("records"), ex.getMessage());
    }


    @Test
    void matchingDeclaredRecordsParsesCleanly() throws IOException
    {
        String json = META_PREFIX + "\"records\":2," + COLUMNS + ",\"rows\":[[\"a\"],[\"b\"]]}";

        AtomicLong rows = new AtomicLong();
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerRow((_, _, _) ->
        {
            rows.incrementAndGet();
            return 0;
        });
        parser.parseDataSet(bytes(json));
        assertEquals(2, rows.get());
    }


    @Test
    void truncatedNdjsonFails(@TempDir Path dir) throws IOException
    {
        // NDJSON declaring 3 records but carrying only 2 complete row lines — the shape of a
        // partial upload truncated at a 0x0A boundary.
        String ndjson = META_PREFIX + "\"records\":3," + COLUMNS + "}\n[\"a\"]\n[\"b\"]\n";
        Path file = dir.resolve("truncated.ndjson");
        Files.writeString(file, ndjson, StandardCharsets.UTF_8);

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerRows((_, _, _, _) -> 0);
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> parser.parseDataSet(file));
        assertTrue(ex.getMessage().contains("records"), ex.getMessage());
    }

    // ------------------------------------------------------- F-dsj-03, parallel path


    @Test
    void parallelParserDetectsTruncatedTail(@TempDir Path dir) throws IOException
    {
        Path file = writeNdjsonFile(dir, 1000, 1005); // declares 5 rows more than it holds

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1); // force the parallel path
        parser.setHandlerChunkRows((_, _, _, _) -> 0);

        try
        {
            parser.parseDataSet(file);
            fail("a truncated NDJSON must not parse cleanly in the parallel path");
        }
        catch (IOException ex)
        {
            assertTrue(ex.getMessage().contains("records"), ex.getMessage());
        }
    }


    @Test
    void parallelParserAcceptsMatchingCount(@TempDir Path dir) throws IOException
    {
        Path file = writeNdjsonFile(dir, 1000, 1000);

        AtomicLong total = new AtomicLong();
        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1);
        parser.setHandlerChunkRows((_, _, rowCount, _) ->
        {
            total.addAndGet(rowCount);
            return 0;
        });
        parser.parseDataSet(file);

        assertEquals(1000, total.get());
    }


    /**
     * Write a plain NDJSON file with {@code actualRows} row lines whose metadata line declares
     * {@code declaredRecords}. Fails loudly if the fixture cannot be written.
     */
    private static Path writeNdjsonFile(Path dir, int actualRows, int declaredRecords)
        throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append(META_PREFIX).append("\"records\":").append(declaredRecords).append(',')
                .append(COLUMNS).append("}\n");
        for (int i = 0; i < actualRows; i++)
        {
            sb.append("[\"row-").append(i).append("\"]\n");
        }
        Path file = dir.resolve("data.ndjson");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        assertTrue(Files.size(file) > 0, "fixture write failed");
        return file;
    }
}
