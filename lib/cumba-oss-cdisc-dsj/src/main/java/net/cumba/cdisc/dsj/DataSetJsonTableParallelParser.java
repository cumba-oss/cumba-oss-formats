package net.cumba.cdisc.dsj;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * A parallel variant of {@link DataSetJsonTableParser} for plain (non-compressed) NDJSON files.
 * Each line of NDJSON is a self-contained JSON array, so the byte stream can be split into N ranges
 * at line boundaries and each range parsed by its own {@link JsonParser} on its own thread.
 *
 * <p>
 * The parallel path is only entered for {@link Path} / {@link File} entry points (random access is
 * required) and when the file is plain NDJSON above a configurable size threshold. For everything
 * else — gzipped / zlib-compressed input (DSJC), plain JSON with rows nested inside the metadata
 * object, small files, or anonymous {@link InputStream} sources — this class delegates to its
 * parent's single-threaded implementation. Compressed streams are intrinsically sequential, and a
 * "rows are inside the metadata object" layout has no line boundaries to split on without full
 * tokenisation.
 * </p>
 *
 * <h2>Callback shape</h2>
 * <p>
 * Whereas the parent class delivers rows via either {@link RowSliceHandler} (slice-based) or
 * {@link RowHandler} (row-based, single-threaded by contract), this class introduces
 * {@link ChunkRowsHandler} for the parallel path. The handler is invoked per chunk; calls for
 * different {@code chunkIdx} values may run concurrently, but calls for the same {@code chunkIdx}
 * are serial. Implementations typically route each chunk's rows to its own private downstream
 * accumulator (one accumulator per chunk index), then merge after parsing completes — and the merge
 * must concatenate accumulators in increasing {@code chunkIdx} order to preserve file row order.
 * </p>
 *
 * <h2>Memory profile</h2>
 * <p>
 * Per worker: a {@link FileChannel} positional-read slice + a 256 KB {@link BufferedInputStream} +
 * Jackson's ~8 KB token buffer + one batch of up to 1024 row arrays. No whole-file read or
 * whole-file mmap. Total transient parsing memory is roughly {@code parallelism * 264 KB}.
 * </p>
 */
public class DataSetJsonTableParallelParser extends DataSetJsonTableParser
{

    /**
     * Maximum number of rows passed to {@link ChunkRowsHandler} per call. Each chunk worker fills a
     * reusable {@code Object[][]} buffer of this size, flushing whenever it fills. Smaller batches
     * mean more callback overhead per row; larger batches mean longer tail latency before the
     * downstream sees the first rows.
     */
    public static final int CHUNK_BATCH_SIZE = 1024;

    /**
     * Receives parsed row batches from chunk workers. Calls for different {@code chunkIdx} values
     * may run concurrently; calls for the same {@code chunkIdx} are serial. Returning a non-zero
     * value aborts the entire parse.
     */
    @FunctionalInterface
    public interface ChunkRowsHandler
    {

        /**
         * Called by a chunk worker with up to {@link #CHUNK_BATCH_SIZE} parsed row arrays. The
         * {@code rows} array is owned by the worker and reused across calls; the handler must not
         * retain references to it past the call. Only the first {@code rowCount} entries are valid.
         *
         * @param chunkIdx
         *            zero-based chunk index in {@code [0, parallelism)}.
         * @param table
         *            the parsed table metadata (same instance for all chunks).
         * @param rowCount
         *            the number of valid rows in {@code rows}.
         * @param rows
         *            a worker-owned buffer of row arrays; only indices {@code [0, rowCount)} are
         *            valid.
         * @return {@code 0} to continue, non-zero to abort the entire parse.
         */
        int chunkRows(int chunkIdx, DsjTable table, int rowCount, Object[][] rows);
    }

    /**
     * Number of parallel chunks. Defaults to {@code min(4, availableProcessors)} based on the
     * sweet-spot finding for the rule engine. The optimum varies with disk bandwidth, CPU cache
     * behaviour, and downstream cost; 4 is a safe default that beats 1 substantially without
     * burning cores.
     */
    @Getter
    @Setter
    private volatile int parallelism = Math.min(4, Runtime.getRuntime().availableProcessors());

    /**
     * Files smaller than this size fall through to the single-threaded parent path. Below ~4 MB the
     * coordination overhead dominates the parsing win.
     */
    @Getter
    @Setter
    private volatile long minBytesForParallel = 4L << 20;

    /**
     * Per-worker {@link BufferedInputStream} buffer size. 256 KB is large enough that the per-read
     * syscall overhead is negligible against the token-stream cost.
     */
    @Getter
    @Setter
    private volatile int chunkBufferSize = 256 * 1024;

    /**
     * The chunk-rows callback. When unset, the parallel path is disabled and all entry points
     * delegate to the parent.
     */
    @Getter
    @Setter
    private @Nullable ChunkRowsHandler handlerChunkRows;

    /**
     * Parse a DataSet-JSON file given as a {@link Path}. Dispatches to the parallel path when the
     * file is plain NDJSON, large enough, and a {@link ChunkRowsHandler} is configured; otherwise
     * delegates to the parent's single-threaded path.
     */
    @Override
    public void parseDataSet(@NonNull Path aFile) throws IOException
    {
        if (handlerChunkRows == null || Files.size(aFile) < minBytesForParallel)
        {
            super.parseDataSet(aFile);
            return;
        }

        // Open a shared FileChannel for the duration of the parse. All positional reads on a
        // FileChannel are thread-safe and do not affect the channel's main position, so workers
        // can read concurrent slices without coordination.
        try (FileChannel ch = FileChannel.open(aFile, StandardOpenOption.READ))
        {
            if (peekHeader(ch) != FormatHeader.PLAIN)
            {
                // Compressed (gzip / zlib) streams are intrinsically sequential and offer no
                // wins worth the architectural cost we measured. Hand off to the parent's
                // single-threaded path for those.
                super.parseDataSet(aFile);
                return;
            }
            parseParallelPlainNDJson(ch, aFile);
        }
    }


    /**
     * Plain (uncompressed) NDJSON path: split the row-data byte range into {@link #parallelism}
     * line-aligned chunks and parse each on its own thread via positional {@link FileChannel}
     * reads.
     */
    private void parseParallelPlainNDJson(FileChannel ch, Path aFile) throws IOException
    {
        MetadataLocator ml = parseMetadataAndLocate(ch);
        if (ml == null || !ml.isNDJson)
        {
            // Plain JSON with rows nested inside the metadata object, or no rows at all.
            // Either case has no line boundaries to split on.
            super.parseDataSet(aFile);
            return;
        }

        // An NDJSON locator is always built via MetadataLocator.ndjson(table, …) with a non-null
        // table; the guard above rules out the notParallelisable() case (table == null).
        DsjTable table = Objects.requireNonNull(ml.table, "NDJSON locator must carry a table");

        // Read the handler ONCE. Null-checking one call and dereferencing a second is
        // what SpotBugs flags as NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE — nothing
        // guarantees the second call returns the same non-null value.
        MetadataHandler metadataHandler = getHandlerMetadata();
        if (metadataHandler != null)
        {
            int res = metadataHandler.metadata(table);
            if (res != 0)
            {
                throw new IOException("User aborted!");
            }
        }

        long fileEnd = ch.size();
        long[] byteBoundaries = splitToNewlineBoundaries(ch, ml.rowsByteStart, fileEnd,
                parallelism);

        try (ExecutorService pool = Executors.newFixedThreadPool(parallelism,
                threadFactory("dsj-parse")))
        {
            CompletableFuture<?>[] futures = new CompletableFuture<?>[parallelism];
            for (int i = 0; i < parallelism; i++)
            {
                final int idx = i;
                final long start = byteBoundaries[idx];
                final long end = byteBoundaries[idx + 1];
                futures[i] = CompletableFuture.runAsync(() ->
                {
                    try
                    {
                        parseRangeChunk(ch, table, idx, start, end);
                    }
                    catch (IOException ex)
                    {
                        throw new CompletionException(ex);
                    }
                }, pool);
            }
            joinAll(futures);
        }
    }


    /**
     * Joins all chunk-worker futures, unwrapping the first {@link CompletionException}'s cause into
     * the appropriate exception type for the caller.
     */
    private static void joinAll(CompletableFuture<?>[] futures) throws IOException
    {
        try
        {
            CompletableFuture.allOf(futures).join();
        }
        catch (CompletionException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioe)
            {
                throw ioe;
            }
            if (cause instanceof RuntimeException re)
            {
                throw re;
            }
            throw new IOException(cause);
        }
    }


    private static ThreadFactory threadFactory(String prefix)
    {
        AtomicInteger counter = new AtomicInteger();
        return r ->
        {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }


    /** Delegates to {@link #parseDataSet(Path)}. */
    @Override
    public void parseDataSet(@NonNull File aFile) throws IOException
    {
        parseDataSet(aFile.toPath());
    }


    /**
     * {@link InputStream} sources have no random access — the parallel path requires a
     * {@link FileChannel}, so this delegates to the parent.
     */
    @Override
    @SuppressWarnings("PMD.UselessOverridingMethod")
    public void parseDataSet(@NonNull InputStream aStream) throws IOException
    {
        super.parseDataSet(aStream);
    }

    // ----------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------


    /**
     * Two-byte header peek. Distinguishes plain JSON from gzip / zlib without consuming the
     * channel.
     */
    private FormatHeader peekHeader(FileChannel ch) throws IOException
    {
        ByteBuffer hdr = ByteBuffer.allocate(2);
        int read = ch.read(hdr, 0L);
        if (read < 2)
        {
            return FormatHeader.UNKNOWN;
        }
        byte b0 = hdr.get(0);
        byte b1 = hdr.get(1);
        if (b0 == (byte) 0x1F && b1 == (byte) 0x8B)
        {
            return FormatHeader.GZIP;
        }
        if (b0 == (byte) 0x78)
        {
            return FormatHeader.ZLIB;
        }
        if (b0 == (byte) 0x7B)
        {
            return FormatHeader.PLAIN;
        }
        return FormatHeader.UNKNOWN;
    }


    /**
     * Plain-NDJSON variant: opens a {@link JsonParser} over the channel and runs the metadata
     * locator on it.
     */
    private @Nullable MetadataLocator parseMetadataAndLocate(FileChannel ch) throws IOException
    {
        long fileSize = ch.size();
        try (InputStream raw = new ChannelSliceInputStream(ch, 0L, fileSize);
                BufferedInputStream bin = new BufferedInputStream(raw, 64 * 1024);
                JsonParser p = new JsonFactory().createParser(bin))
        {
            return locateMetadata(p);
        }
    }


    /**
     * Replays the parent's metadata-collection logic up to (but not including) row dispatch, and
     * captures the byte offset where the first row's {@code [} begins. Returns {@code null} if
     * parsing fails or no rows are present; returns a {@link MetadataLocator} with
     * {@code isNDJson=false} when row data is nested inside the metadata object's {@code "rows"}
     * field (plain JSON layout, not parallelisable here).
     *
     * <p>
     * Operates on any {@link JsonParser} so it can be reused for both the byte-range and the
     * producer-consumer paths. For compressed input, the parser is wrapped over a decompressor
     * stream and the byte offset is in <em>decompressed</em> bytes — exactly what the producer
     * needs to {@code skip} when it opens its own decompressor.
     * </p>
     */
    private @Nullable MetadataLocator locateMetadata(JsonParser p) throws IOException
    {
        JsonToken token;
        Map<String, Object> metadata = new HashMap<>();
        DsjTableColumn[] columns = null;
        int objDepth = 0;

        while ((token = p.nextToken()) != null)
        {
            if (token == JsonToken.START_OBJECT)
            {
                objDepth++;
            }
            else if (token == JsonToken.END_OBJECT)
            {
                objDepth--;
            }
            else if (token == JsonToken.FIELD_NAME)
            {
                String fieldName = p.currentName();
                p.nextToken();
                if (Objects.equals(fieldName, "columns"))
                {
                    columns = parseColumns(p);
                }
                else if (Objects.equals(fieldName, "rows"))
                {
                    // Plain-JSON layout: rows are nested in the metadata object — no line
                    // boundaries to split on.
                    return MetadataLocator.notParallelisable();
                }
                else
                {
                    Object value = parseAsValue(p);
                    metadata.put(fieldName, value);
                }
            }
            else if (objDepth == 0 && token == JsonToken.START_ARRAY)
            {
                // NDJSON layout. Capture the byte offset of the just-consumed `[` — that's
                // where row data starts. Jackson's getTokenLocation() returns the start of
                // the current token in the source, which is exactly the position of `[`.
                if (columns == null)
                {
                    return MetadataLocator.notParallelisable();
                }
                long rowsStart = p.currentTokenLocation().getByteOffset();
                if (rowsStart < 0)
                {
                    return MetadataLocator.notParallelisable();
                }
                DsjTable table = buildTable(metadata, columns);
                return MetadataLocator.ndjson(table, rowsStart);
            }
        }
        return null;
    }


    /**
     * Splits the {@code [start, end)} byte range into {@code n} chunks aligned to line boundaries.
     * Each interior boundary is moved forward to the byte position immediately after the next
     * {@code 0x0A}, so every chunk begins at the start of a row. UTF-8 continuation bytes never
     * produce {@code 0x0A}, so byte-level scanning is safe without decoding.
     */
    private long[] splitToNewlineBoundaries(FileChannel ch, long start, long end, int n)
        throws IOException
    {
        long[] b = new long[n + 1];
        b[0] = start;
        b[n] = end;
        if (n == 1 || end - start <= 0)
        {
            return b;
        }
        ByteBuffer probe = ByteBuffer.allocate(4096);
        long span = end - start;
        for (int i = 1; i < n; i++)
        {
            long candidate = start + span * i / n;
            if (candidate <= b[i - 1])
            {
                // Tiny span — collapse remaining boundaries to end. Empty chunks are fine.
                for (int j = i; j < n; j++)
                {
                    b[j] = end;
                }
                return b;
            }
            b[i] = scanForwardToNewline(ch, candidate, end, probe);
            // Ensure boundaries are monotonic. If two candidates landed in the same line this can
            // collapse a chunk to empty — also fine.
            if (b[i] < b[i - 1])
            {
                b[i] = b[i - 1];
            }
        }
        return b;
    }


    /**
     * Scans forward from {@code pos} (inclusive) for the first {@code 0x0A} byte and returns the
     * byte position immediately after it. Returns {@code end} if no newline is found.
     */
    private long scanForwardToNewline(FileChannel ch, long pos, long end, ByteBuffer probe)
        throws IOException
    {
        while (pos < end)
        {
            probe.clear();
            int want = (int) Math.min(probe.capacity(), end - pos);
            probe.limit(want);
            int read = ch.read(probe, pos);
            if (read <= 0)
            {
                return end;
            }
            for (int i = 0; i < read; i++)
            {
                if (probe.get(i) == (byte) 0x0A)
                {
                    return pos + i + 1;
                }
            }
            pos += read;
        }
        return end;
    }


    /**
     * Per-chunk parser: opens a {@link JsonParser} over a positional slice of the
     * {@link FileChannel} and dispatches rows in batches via {@link ChunkRowsHandler}.
     */
    private void parseRangeChunk(FileChannel ch, DsjTable table, int chunkIdx, long start, long end)
        throws IOException
    {
        if (start >= end)
        {
            return;
        }
        try (InputStream raw = new ChannelSliceInputStream(ch, start, end);
                BufferedInputStream bin = new BufferedInputStream(raw, chunkBufferSize);
                JsonParser p = new JsonFactory().createParser(bin))
        {
            parseChunkFromParser(p, chunkIdx, table);
        }
    }


    /**
     * Inner loop for the byte-range chunk parser: pulls {@code [...]} row arrays out of the parser,
     * batches up to {@link #CHUNK_BATCH_SIZE} per dispatch, and fires {@link ChunkRowsHandler}.
     */
    // parseRowToArray returns @Nullable Object[] (rows legitimately hold JSON nulls), but the
    // reusable batch is a 2-D Object[][] which NullAway cannot reason about element-wise: it
    // evaluates batch[i] as Object[], so the @Nullable Object[] store below is rejected with no
    // honest annotation able to satisfy it (annotating the 2-D array only manufactures a spurious
    // Object[][] -> @Nullable Object[][] mismatch). The nulls are handled correctly downstream
    // (rows flow into List<@Nullable Object[]> which NullAway does track), so this is safe.
    @SuppressWarnings("NullAway")
    private void parseChunkFromParser(JsonParser p, int chunkIdx, DsjTable table) throws IOException
    {
        int columnCount = table.getColumnCount();
        Object[][] batch = new Object[CHUNK_BATCH_SIZE][];
        int batchLen = 0;
        JsonToken tok;
        while ((tok = p.nextToken()) != null)
        {
            if (tok != JsonToken.START_ARRAY)
            {
                throw new IOException("Unexpected token in chunk " + chunkIdx + ": " + tok);
            }
            batch[batchLen++] = parseRowToArray(p, columnCount);
            if (batchLen == batch.length)
            {
                flushBatch(chunkIdx, table, batchLen, batch);
                batchLen = 0;
            }
        }
        if (batchLen > 0)
        {
            flushBatch(chunkIdx, table, batchLen, batch);
        }
    }


    private void flushBatch(int chunkIdx, DsjTable table, int rowCount, Object[][] batch)
        throws IOException
    {
        // The parallel path is only entered when a chunk-rows handler is set (guarded in
        // parseDataSet, line ~143), so the handler is non-null here.
        ChunkRowsHandler handler = Objects.requireNonNull(handlerChunkRows);
        int res = handler.chunkRows(chunkIdx, table, rowCount, batch);
        if (res != 0)
        {
            throw new IOException("User aborted!");
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    /**
     * Two-byte header classification for the file-format peek.
     */
    private enum FormatHeader
    {
        PLAIN, GZIP, ZLIB, UNKNOWN;
    }


    /**
     * Carries the parsed metadata object plus the byte offset of the row-data start. The
     * {@code isNDJson} flag is {@code false} when the file uses the rows-inside-metadata layout
     * that this parser cannot parallelise.
     */
    private static final class MetadataLocator
    {

        final @Nullable DsjTable table;

        final long rowsByteStart;

        final boolean isNDJson;

        private MetadataLocator(@Nullable DsjTable table, long rowsByteStart, boolean isNDJson)
        {
            this.table = table;
            this.rowsByteStart = rowsByteStart;
            this.isNDJson = isNDJson;
        }


        static MetadataLocator ndjson(DsjTable table, long rowsByteStart)
        {
            return new MetadataLocator(table, rowsByteStart, true);
        }


        static MetadataLocator notParallelisable()
        {
            return new MetadataLocator(null, -1L, false);
        }
    }


    /**
     * An {@link InputStream} that reads a slice of a {@link FileChannel} via positional reads. The
     * channel's main position is never modified, so multiple instances over the same channel can be
     * consumed concurrently from different threads.
     */
    private static final class ChannelSliceInputStream extends InputStream
    {

        private final FileChannel ch;

        private long pos;

        private final long end;

        private final byte[] singleByte = new byte[1];

        ChannelSliceInputStream(FileChannel ch, long start, long end)
        {
            this.ch = ch;
            this.pos = start;
            this.end = end;
        }


        @Override
        public int read() throws IOException
        {
            int n = read(singleByte, 0, 1);
            if (n <= 0)
            {
                return -1;
            }
            return singleByte[0] & 0xFF;
        }


        @Override
        public int read(byte[] buf, int off, int len) throws IOException
        {
            if (pos >= end)
            {
                return -1;
            }
            int allowed = (int) Math.min(len, end - pos);
            if (allowed <= 0)
            {
                return -1;
            }
            ByteBuffer bb = ByteBuffer.wrap(buf, off, allowed);
            int read = ch.read(bb, pos);
            if (read > 0)
            {
                pos += read;
            }
            return read;
        }


        @Override
        public int available()
        {
            long remaining = end - pos;
            if (remaining <= 0)
            {
                return 0;
            }
            return (int) Math.min(Integer.MAX_VALUE, remaining);
        }
    }

}
