package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour of the parallel path that the row totals alone do not pin: how a worker's failure
 * reaches the caller, how many callbacks a chunk makes, and where the size threshold switches the
 * two implementations over.
 */
class DataSetJsonTableParallelParserBehaviourTest
{

    private static final String META = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
            + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
            + "\"label\":\"L\",\"records\":%d,"
            + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\","
            + "\"dataType\":\"integer\"}]}";

    private static String ndjson(int aRows)
    {
        StringBuilder sb = new StringBuilder(META.formatted(aRows)).append('\n');
        for (int i = 0; i < aRows; i++)
        {
            sb.append('[').append(i).append("]\n");
        }
        return sb.toString();
    }


    private static Path write(Path aDir, String aName, String aContent) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.write(p, aContent.getBytes(StandardCharsets.UTF_8));
        return p;
    }


    @Test
    void testThresholdIsInclusiveOfTheFileSize(@TempDir Path tmp) throws IOException
    {
        // A file exactly at the threshold is big enough. Off by one here and the switchover point
        // between the two implementations is not where the setter says it is.
        Path f = write(tmp, "exact.ndjson", ndjson(6));
        long size = Files.size(f);

        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(size);
        p.setParallelism(1);
        AtomicInteger chunkRows = new AtomicInteger();
        AtomicBoolean sliceCalled = new AtomicBoolean();
        p.setHandlerChunkRows((_, _, n, _) ->
        {
            chunkRows.addAndGet(n);
            return 0;
        });
        p.setHandlerRows((_, _, _, _) ->
        {
            sliceCalled.set(true);
            return 0;
        });

        p.parseDataSet(f);

        assertEquals(6, chunkRows.get(), "the parallel path must handle a file at the threshold");
        assertFalse(sliceCalled.get(), "the sequential slice handler must not have run");
    }


    @Test
    void testJustBelowTheThresholdUsesTheSequentialPath(@TempDir Path tmp) throws IOException
    {
        Path f = write(tmp, "small.ndjson", ndjson(6));
        long size = Files.size(f);

        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(size + 1);
        AtomicInteger chunkRows = new AtomicInteger();
        AtomicInteger sliceRows = new AtomicInteger();
        p.setHandlerChunkRows((_, _, n, _) ->
        {
            chunkRows.addAndGet(n);
            return 0;
        });
        p.setHandlerRows((_, _, n, _) ->
        {
            sliceRows.addAndGet(n);
            return 0;
        });

        p.parseDataSet(f);

        assertEquals(0, chunkRows.get());
        assertEquals(6, sliceRows.get());
    }


    @Test
    void testWorkerAbortSurfacesAsTheAbortItself(@TempDir Path tmp) throws IOException
    {
        // A handler that aborts must reach the caller as the abort, not as an IOException wrapping
        // an IOException — the caller reads getMessage() to tell a rejection from a corrupt file.
        Path f = write(tmp, "abort.ndjson", ndjson(10));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(1L);
        p.setParallelism(1);
        p.setHandlerChunkRows((_, _, _, _) -> 1);

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(f));
        assertEquals("User aborted!", ex.getMessage());
    }


    @Test
    void testWorkerRuntimeExceptionIsRethrownUnwrapped(@TempDir Path tmp) throws IOException
    {
        // An unchecked failure inside a handler keeps its own type: wrapping it in an IOException
        // would make a programming error look like a bad file.
        Path f = write(tmp, "boom.ndjson", ndjson(10));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(1L);
        p.setParallelism(1);
        p.setHandlerChunkRows((_, _, _, _) ->
        {
            throw new IllegalStateException("handler blew up");
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.parseDataSet(f));
        assertEquals("handler blew up", ex.getMessage());
    }


    @Test
    void testWorkerErrorIsWrappedInAnIoException(@TempDir Path tmp) throws IOException
    {
        // Anything that is neither an IOException nor unchecked still has to reach the caller
        // through the declared throws clause, carrying its cause.
        Path f = write(tmp, "error.ndjson", ndjson(10));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(1L);
        p.setParallelism(1);
        p.setHandlerChunkRows((_, _, _, _) ->
        {
            throw new AssertionError("not an exception");
        });

        IOException ex = assertThrows(IOException.class, () -> p.parseDataSet(f));
        assertInstanceOf(AssertionError.class, ex.getCause());
    }


    @Test
    void testOneChunkDeliversOneBatch(@TempDir Path tmp) throws IOException
    {
        // Ten rows, one chunk, batch size 1024: exactly one callback. Flushing per row would
        // still add up to ten rows, which is why the totals alone never caught it.
        Path f = write(tmp, "one-batch.ndjson", ndjson(10));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(1L);
        p.setParallelism(1);
        List<Integer> calls = Collections.synchronizedList(new ArrayList<>());
        p.setHandlerChunkRows((_, _, n, _) ->
        {
            calls.add(n);
            return 0;
        });

        p.parseDataSet(f);

        assertEquals(List.of(10), calls);
    }


    @Test
    void testFullBatchIsFlushedFromTheInnerLoop(@TempDir Path tmp) throws IOException
    {
        // One row past CHUNK_BATCH_SIZE: the first callback must be a full batch and the second
        // the remainder, which pins the flush threshold rather than just the total.
        int rows = DataSetJsonTableParallelParser.CHUNK_BATCH_SIZE + 1;
        Path f = write(tmp, "full-batch.ndjson", ndjson(rows));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(1L);
        p.setParallelism(1);
        List<Integer> calls = Collections.synchronizedList(new ArrayList<>());
        p.setHandlerChunkRows((_, _, n, _) ->
        {
            calls.add(n);
            return 0;
        });

        p.parseDataSet(f);

        assertEquals(List.of(DataSetJsonTableParallelParser.CHUNK_BATCH_SIZE, 1), calls);
    }


    @Test
    void testAChunkWithNoRowsNeverFiresTheHandler(@TempDir Path tmp) throws IOException
    {
        // Trailing whitespace after the last row puts a few bytes into the final chunk without a
        // single row in them. That chunk must stay silent: a zero-row callback looks to a
        // downstream accumulator exactly like a chunk that legitimately parsed nothing.
        String content = META.formatted(2) + "\n[1]\n[" + "1".repeat(17) + "]\n  ";
        Path f = write(tmp, "trailing-space.ndjson", content);

        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        p.setMinBytesForParallel(1L);
        p.setParallelism(2);
        List<Integer> calls = Collections.synchronizedList(new ArrayList<>());
        p.setHandlerChunkRows((_, _, n, _) ->
        {
            calls.add(n);
            return 0;
        });

        p.parseDataSet(f);

        assertFalse(calls.isEmpty(), "the rows must still have been delivered");
        assertEquals(2, calls.stream().mapToInt(Integer::intValue).sum());
        assertTrue(calls.stream().noneMatch(n -> n == 0),
                "an empty chunk must not call the handler: " + calls);
    }
}
