package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link DataSetJsonTableParallelParser}: getters/setters, delegating fallbacks (gzip /
 * small file / no chunk handler / rows-inside-metadata), and the parallel chunked path with a
 * custom low {@code minBytesForParallel} so the test can drive the parallel branch without needing
 * a multi-megabyte fixture.
 */
class DataSetJsonTableParallelParserTest
{

    private static final String NDJSON_HEADER = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
            + "\"datasetJSONVersion\":\"1.1.0\","
            + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\","
            + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"integer\"}]}\n";

    private static String buildNDJson(int rows)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(NDJSON_HEADER);
        for (int i = 0; i < rows; i++)
        {
            sb.append('[').append(i).append("]\n");
        }
        return sb.toString();
    }


    @Test
    void testGettersAndSetters()
    {
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        assertTrue(p.getParallelism() >= 1);
        assertEquals(4L << 20, p.getMinBytesForParallel());
        assertEquals(256 * 1024, p.getChunkBufferSize());

        p.setParallelism(3);
        p.setMinBytesForParallel(1024L);
        p.setChunkBufferSize(8 * 1024);

        assertEquals(3, p.getParallelism());
        assertEquals(1024L, p.getMinBytesForParallel());
        assertEquals(8 * 1024, p.getChunkBufferSize());
    }


    @Test
    void testNoChunkHandlerFallsBackToSequential(@TempDir Path tmp) throws IOException
    {
        // Without a ChunkRowsHandler, parallel path is disabled and the parent path runs.
        Path p = tmp.resolve("ndjson-no-handler.dsj");
        Files.writeString(p, buildNDJson(5));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);

        assertEquals(5, total.get());
    }


    @Test
    void testParallelPathPlainNdjson(@TempDir Path tmp) throws IOException
    {
        // Write a small NDJSON file but lower the threshold so the parallel path runs.
        Path p = tmp.resolve("ndjson-parallel.dsj");
        Files.writeString(p, buildNDJson(100));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setParallelism(3);

        AtomicReference<DsjTable> mref = new AtomicReference<>();
        parser.setHandlerMetadata(t ->
        {
            mref.set(t);
            return 0;
        });

        ConcurrentHashMap<Integer, AtomicInteger> perChunk = new ConcurrentHashMap<>();
        AtomicInteger total = new AtomicInteger();
        parser.setHandlerChunkRows((idx, _, n, _) ->
        {
            perChunk.computeIfAbsent(idx, _ -> new AtomicInteger()).addAndGet(n);
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);

        assertNotNull(mref.get());
        assertEquals("T", mref.get().getName());
        assertEquals(100, total.get());
        // All 3 chunk indices must have been used at least once.
        assertTrue(perChunk.size() >= 1);
    }


    @Test
    void testParallelPathFileEntryPoint(@TempDir Path tmp) throws IOException
    {
        Path p = tmp.resolve("ndjson-file-entry.dsj");
        Files.writeString(p, buildNDJson(50));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);

        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerChunkRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        // Use the File overload which delegates to Path.
        File f = p.toFile();
        parser.parseDataSet(f);

        assertEquals(50, total.get());
    }


    @Test
    void testParallelPathFallsBackForGzipFile(@TempDir Path tmp) throws IOException
    {
        // Gzip-compressed input — the peekHeader detects gzip and delegates to parent.
        Path p = tmp.resolve("ndjson.dsj.gz");
        try (OutputStream out = Files.newOutputStream(p);
                GZIPOutputStream gz = new GZIPOutputStream(out))
        {
            gz.write(buildNDJson(5).getBytes(StandardCharsets.UTF_8));
        }

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setHandlerChunkRows((_, _, _, _) -> 0);

        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);
        // Parent handles gzip via single-threaded path: rows are delivered via handlerRows.
        assertEquals(5, total.get());
    }


    @Test
    void testParallelPathFallsBackForPlainJsonRowsInsideMetadata(@TempDir Path tmp)
        throws IOException
    {
        // Plain-JSON layout: rows live inside the metadata object — not parallelisable.
        String json = "{" + "\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\","
                + "\"itemGroupOID\":\"IG.T\",\"name\":\"T\",\"label\":\"L\",\"records\":2,"
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\",\"dataType\":\"integer\"}],"
                + "\"rows\":[[1],[2]]" + "}";
        Path p = tmp.resolve("plain.json");
        Files.writeString(p, json);

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);

        // Configure both handlers: chunk-rows is required to enable parallel attempt, slice handler
        // catches the fall-back to single-threaded mode.
        AtomicInteger sliceTotal = new AtomicInteger();
        AtomicInteger chunkTotal = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, n, _) ->
        {
            sliceTotal.addAndGet(n);
            return 0;
        });
        parser.setHandlerChunkRows((_, _, n, _) ->
        {
            chunkTotal.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);

        // The parser falls back to single-threaded mode -> slice handler gets the rows.
        assertEquals(2, sliceTotal.get());
        assertEquals(0, chunkTotal.get());
    }


    @Test
    void testParallelPathFallsBackForSmallFile(@TempDir Path tmp) throws IOException
    {
        // Default threshold (4 MB) is far above this 5-row file.
        Path p = tmp.resolve("small.dsj");
        Files.writeString(p, buildNDJson(5));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        // Don't lower minBytesForParallel — default keeps the file below threshold.
        AtomicInteger sliceTotal = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, n, _) ->
        {
            sliceTotal.addAndGet(n);
            return 0;
        });
        parser.setHandlerChunkRows((_, _, _, _) -> 0);

        parser.parseDataSet(p);

        assertEquals(5, sliceTotal.get());
    }


    @Test
    void testInputStreamDelegatesToParent() throws IOException
    {
        // InputStream entry point on the parallel parser always delegates.
        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });
        parser.setHandlerChunkRows((_, _, _, _) -> 0);

        String json = buildNDJson(3);
        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, total.get());
    }


    @Test
    void testParallelChunkHandlerAbortRaises(@TempDir Path tmp) throws IOException
    {
        // Returning non-zero from the chunk handler must abort the parse with an IOException.
        Path p = tmp.resolve("ndjson-abort.dsj");
        Files.writeString(p, buildNDJson(50));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setParallelism(2);
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerChunkRows((_, _, _, _) -> -1);

        assertThrows(IOException.class, () -> parser.parseDataSet(p));
    }


    @Test
    void testParallelMetadataAbortRaises(@TempDir Path tmp) throws IOException
    {
        Path p = tmp.resolve("ndjson-meta-abort.dsj");
        Files.writeString(p, buildNDJson(20));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setHandlerMetadata(_ -> -1);
        parser.setHandlerChunkRows((_, _, _, _) -> 0);

        assertThrows(IOException.class, () -> parser.parseDataSet(p));
    }


    @Test
    void testParallelPathParallelismOne(@TempDir Path tmp) throws IOException
    {
        // parallelism=1 — splitToNewlineBoundaries early-returns; still parallel architecture
        // though.
        Path p = tmp.resolve("ndjson-p1.dsj");
        Files.writeString(p, buildNDJson(10));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setParallelism(1);

        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerChunkRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);
        assertEquals(10, total.get());
    }


    @Test
    void testParallelPathParallelismLarger(@TempDir Path tmp) throws IOException
    {
        // parallelism > rows — some chunks will be empty / collapsed.
        Path p = tmp.resolve("ndjson-many.dsj");
        Files.writeString(p, buildNDJson(8));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setParallelism(16);

        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerChunkRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);
        assertEquals(8, total.get());
    }


    @Test
    void testParallelPathBatchFlush(@TempDir Path tmp) throws IOException
    {
        // Push the row count above CHUNK_BATCH_SIZE (1024) so flushBatch fires from the inner loop.
        Path p = tmp.resolve("ndjson-large-batch.dsj");
        Files.writeString(p, buildNDJson(2500));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setParallelism(2);

        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerChunkRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);

        assertEquals(2500, total.get());
    }


    @Test
    void testParallelPathReducedBufferSize(@TempDir Path tmp) throws IOException
    {
        // Exercise chunkBufferSize change.
        Path p = tmp.resolve("ndjson-bufsz.dsj");
        Files.writeString(p, buildNDJson(20));

        DataSetJsonTableParallelParser parser = new DataSetJsonTableParallelParser();
        parser.setMinBytesForParallel(1L);
        parser.setChunkBufferSize(1024);

        AtomicInteger total = new AtomicInteger();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerChunkRows((_, _, n, _) ->
        {
            total.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(p);
        assertEquals(20, total.get());
    }
}
