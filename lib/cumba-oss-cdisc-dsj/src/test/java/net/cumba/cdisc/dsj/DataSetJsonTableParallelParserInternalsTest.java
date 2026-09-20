package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Direct tests for {@link DataSetJsonTableParallelParser}'s internals.
 *
 * <p>
 * The chunking machinery decides which bytes of a clinical dataset each worker reads. Driven only
 * through {@code parseDataSet}, most of its arms are unobservable: every non-plain header falls
 * back to the same sequential path, so GZIP, ZLIB, "unknown magic" and "file too short to have
 * magic" are indistinguishable from outside, and a boundary that is off by a line is invisible as
 * long as the totals happen to add up. These tests go at the methods directly.
 * </p>
 */
class DataSetJsonTableParallelParserInternalsTest
{

    private static final String META = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
            + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
            + "\"label\":\"L\",\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\","
            + "\"dataType\":\"integer\"}]}";

    private static Path writeBytes(Path aDir, String aName, byte[] aContent) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.write(p, aContent);
        return p;
    }


    private static FileChannel open(Path aPath) throws IOException
    {
        return FileChannel.open(aPath, StandardOpenOption.READ);
    }


    private static JsonParser byteParser(String aText) throws IOException
    {
        return new JsonFactory()
                .createParser(new ByteArrayInputStream(aText.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------
    // peekHeader — the format classification
    // ------------------------------------------------------------------


    private void assertHeader(Path aDir, String aName, byte[] aContent,
            DataSetJsonTableParallelParser.FormatHeader aExpected)
        throws IOException
    {
        Path f = writeBytes(aDir, aName, aContent);
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (FileChannel ch = open(f))
        {
            assertEquals(aExpected, p.peekHeader(ch), aName);
        }
    }


    @Test
    void testPeekHeaderClassifiesEveryMagic(@TempDir Path tmp) throws IOException
    {
        assertHeader(tmp, "plain.json", new byte[]
        {
                0x7B, 0x22
        }, DataSetJsonTableParallelParser.FormatHeader.PLAIN);
        assertHeader(tmp, "gzip.gz", new byte[]
        {
                0x1F, (byte) 0x8B
        }, DataSetJsonTableParallelParser.FormatHeader.GZIP);
        assertHeader(tmp, "zlib.dsjc", new byte[]
        {
                0x78, (byte) 0x9C
        }, DataSetJsonTableParallelParser.FormatHeader.ZLIB);
        // 0x1F alone is not gzip: the second byte has to match too, or a file that merely starts
        // with the gzip ID1 byte would be handed to a GZIPInputStream.
        assertHeader(tmp, "half-gzip.bin", new byte[]
        {
                0x1F, 0x00
        }, DataSetJsonTableParallelParser.FormatHeader.UNKNOWN);
        assertHeader(tmp, "other.bin", new byte[]
        {
                0x41, 0x42
        }, DataSetJsonTableParallelParser.FormatHeader.UNKNOWN);
        // Fewer than two bytes: there is no magic to read, so the format is unknown — never a
        // guess at PLAIN, which would hand a truncated file to the JSON parser as if it were whole.
        assertHeader(tmp, "one-byte.json", new byte[]
        {
                0x7B
        }, DataSetJsonTableParallelParser.FormatHeader.UNKNOWN);
        assertHeader(tmp, "empty.json", new byte[0],
                DataSetJsonTableParallelParser.FormatHeader.UNKNOWN);
    }

    // ------------------------------------------------------------------
    // locateMetadata — null vs "not parallelisable" are different answers
    // ------------------------------------------------------------------


    @Test
    void testLocateMetadataFindsTheFirstRowOffset() throws IOException
    {
        String ndjson = META + "\n[1]\n[2]\n";
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (JsonParser jp = byteParser(ndjson))
        {
            DataSetJsonTableParallelParser.MetadataLocator ml = p.locateMetadata(jp);
            assertNotNull(ml);
            assertTrue(ml.isNDJson);
            assertNotNull(ml.table);
            assertEquals("T", ml.table.getName());
            // The offset must be the '[' of the first row, i.e. right after the metadata line's
            // newline. One byte either way and the first worker starts mid-token.
            assertEquals(META.length() + 1, ml.rowsByteStart);
            assertEquals('[', ndjson.charAt((int) ml.rowsByteStart));
        }
    }


    @Test
    void testLocateMetadataReportsNotParallelisableForRowsInsideMetadata() throws IOException
    {
        // Plain-JSON layout: "rows" nested in the metadata object. Not null — null means "no rows
        // found at all", and the caller's fallback is the same but the meaning is not.
        String json = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\",\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\","
                + "\"label\":\"X\",\"dataType\":\"integer\"}],\"rows\":[[1]]}";
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (JsonParser jp = byteParser(json))
        {
            DataSetJsonTableParallelParser.MetadataLocator ml = p.locateMetadata(jp);
            assertNotNull(ml);
            assertFalse(ml.isNDJson);
            assertNull(ml.table);
            assertEquals(-1L, ml.rowsByteStart);
        }
    }


    @Test
    void testLocateMetadataReportsNotParallelisableWithoutColumns() throws IOException
    {
        // NDJSON whose metadata line declares no columns: the row shape is unknown, so the split
        // cannot be trusted and the sequential path must take over.
        String ndjson = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\"}\n[1]\n";
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (JsonParser jp = byteParser(ndjson))
        {
            DataSetJsonTableParallelParser.MetadataLocator ml = p.locateMetadata(jp);
            assertNotNull(ml);
            assertFalse(ml.isNDJson);
        }
    }


    @Test
    void testLocateMetadataReportsNotParallelisableWhenTheSourceHasNoByteOffset() throws IOException
    {
        // A char-based source reports getByteOffset() == -1. Splitting a file on a byte offset of
        // -1 would start the first worker before the start of the file, so the locator must refuse.
        String ndjson = META + "\n[1]\n";
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (JsonParser jp = new JsonFactory().createParser(new StringReader(ndjson)))
        {
            DataSetJsonTableParallelParser.MetadataLocator ml = p.locateMetadata(jp);
            assertNotNull(ml);
            assertFalse(ml.isNDJson);
            assertEquals(-1L, ml.rowsByteStart);
        }
    }


    @Test
    void testLocateMetadataReturnsNullWhenThereAreNoRows() throws IOException
    {
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (JsonParser jp = byteParser(META))
        {
            assertNull(p.locateMetadata(jp));
        }
    }

    // ------------------------------------------------------------------
    // splitToNewlineBoundaries — where each worker starts reading
    // ------------------------------------------------------------------


    /** Ten four-byte lines: "[0]\n" … "[9]\n", 40 bytes total. */
    private static Path tenLines(Path aDir) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++)
        {
            sb.append('[').append(i).append("]\n");
        }
        return writeBytes(aDir, "ten-lines.ndjson", sb.toString().getBytes(StandardCharsets.UTF_8));
    }


    @Test
    void testSplitAlignsEveryBoundaryToTheByteAfterANewline(@TempDir Path tmp) throws IOException
    {
        Path f = tenLines(tmp);
        assertEquals(40L, Files.size(f));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (FileChannel ch = open(f))
        {
            // Candidates land at 10, 20 and 30; each is pushed forward to the byte after the next
            // newline — 12, 24 and 32 — and the candidate at 20 is swallowed by the boundary
            // already at 20, collapsing the rest.
            assertArrayEquals(new long[]
            {
                    0, 12, 24, 32, 40
            }, p.splitToNewlineBoundaries(ch, 0L, 40L, 4));
        }
    }


    @Test
    void testSplitCollapsesWhenACandidateFallsBehindTheLastBoundary(@TempDir Path tmp)
        throws IOException
    {
        // Four ten-byte lines. The first boundary is pushed to 20, which is exactly where the
        // second candidate lands — the remaining boundaries stay at `end`, giving empty chunks
        // rather than a boundary that walks backwards into an already-parsed row.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++)
        {
            sb.append("[1234567]").append('\n');
        }
        Path f = writeBytes(tmp, "four-tens.ndjson",
                sb.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(40L, Files.size(f));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (FileChannel ch = open(f))
        {
            assertArrayEquals(new long[]
            {
                    0, 20, 40, 40, 40
            }, p.splitToNewlineBoundaries(ch, 0L, 40L, 4));
        }
    }


    @Test
    void testSplitHonoursANonZeroStart(@TempDir Path tmp) throws IOException
    {
        Path f = tenLines(tmp);
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (FileChannel ch = open(f))
        {
            // The span is measured from `start`, not from zero: rows never begin at byte 0 of a
            // real file, so a split that ignored `start` would bunch every worker near the front.
            // Candidate 10 + (40-10)/2 = 25, pushed forward to the byte after the newline at 27.
            assertArrayEquals(new long[]
            {
                    10, 28, 40
            }, p.splitToNewlineBoundaries(ch, 10L, 40L, 2));
        }
    }


    @Test
    void testSplitWithOneChunkAndWithAnEmptyRange(@TempDir Path tmp) throws IOException
    {
        Path f = tenLines(tmp);
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        try (FileChannel ch = open(f))
        {
            assertArrayEquals(new long[]
            {
                    0, 40
            }, p.splitToNewlineBoundaries(ch, 0L, 40L, 1));
            // Degenerate range: every interior boundary must sit at `end`, so every chunk is
            // empty. Left at 0 they would send a worker back to the head of the file.
            assertArrayEquals(new long[]
            {
                    40, 40, 40, 40
            }, p.splitToNewlineBoundaries(ch, 40L, 40L, 3));
        }
    }

    // ------------------------------------------------------------------
    // scanForwardToNewline
    // ------------------------------------------------------------------


    @Test
    void testScanReturnsTheByteAfterTheNewline(@TempDir Path tmp) throws IOException
    {
        Path f = tenLines(tmp);
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        ByteBuffer probe = ByteBuffer.allocate(4096);
        try (FileChannel ch = open(f))
        {
            assertEquals(4L, p.scanForwardToNewline(ch, 0L, 40L, probe));
            assertEquals(4L, p.scanForwardToNewline(ch, 3L, 40L, probe));
            assertEquals(8L, p.scanForwardToNewline(ch, 4L, 40L, probe));
        }
    }


    @Test
    void testScanNeverReadsPastTheGivenEnd(@TempDir Path tmp) throws IOException
    {
        // The only newline sits at byte 15, beyond the requested end of 10. The answer must be
        // the end, not 16: a boundary past `end` would hand the next worker bytes this one
        // already consumed.
        Path f = writeBytes(tmp, "late-newline.bin",
                "0123456789ABCDE\nXYZ".getBytes(StandardCharsets.UTF_8));
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        ByteBuffer probe = ByteBuffer.allocate(4096);
        try (FileChannel ch = open(f))
        {
            assertEquals(10L, p.scanForwardToNewline(ch, 4L, 10L, probe));
            assertEquals(10L, p.scanForwardToNewline(ch, 0L, 10L, probe));
            // pos == end: nothing to scan.
            assertEquals(10L, p.scanForwardToNewline(ch, 10L, 10L, probe));
        }
    }


    @Test
    void testScanSpansMoreThanOneProbeBuffer(@TempDir Path tmp) throws IOException
    {
        // A row longer than the 4 KB probe: the scan has to advance and read again rather than
        // give up after the first probe.
        byte[] content = new byte[10_000];
        java.util.Arrays.fill(content, (byte) 'a');
        content[9000] = '\n';
        Path f = writeBytes(tmp, "long-line.ndjson", content);
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        ByteBuffer probe = ByteBuffer.allocate(4096);
        try (FileChannel ch = open(f))
        {
            assertEquals(9001L, p.scanForwardToNewline(ch, 0L, 10_000L, probe));
            // No newline at all in the scanned range -> the end.
            assertEquals(9000L, p.scanForwardToNewline(ch, 0L, 9000L, probe));
        }
    }


    @Test
    void testScanStopsWhenTheChannelIsExhausted(@TempDir Path tmp) throws IOException
    {
        // `end` beyond the real file size: the channel returns -1 and the scan must answer `end`
        // rather than spin.
        Path f = tenLines(tmp);
        DataSetJsonTableParallelParser p = new DataSetJsonTableParallelParser();
        ByteBuffer probe = ByteBuffer.allocate(4096);
        try (FileChannel ch = open(f))
        {
            assertEquals(100L, p.scanForwardToNewline(ch, 40L, 100L, probe));
        }
    }

    // ------------------------------------------------------------------
    // threadFactory
    // ------------------------------------------------------------------


    @Test
    void testThreadFactoryProducesNamedDaemonThreads()
    {
        // Daemon matters: a worker left non-daemon keeps the JVM alive after the caller is done.
        ThreadFactory tf = DataSetJsonTableParallelParser.threadFactory("dsj-test");
        Thread t1 = tf.newThread(() ->
        {
            // no work
        });
        Thread t2 = tf.newThread(() ->
        {
            // no work
        });
        assertNotNull(t1);
        assertNotNull(t2);
        assertTrue(t1.isDaemon(), "worker threads must be daemons");
        assertTrue(t2.isDaemon(), "worker threads must be daemons");
        assertEquals("dsj-test-1", t1.getName());
        assertEquals("dsj-test-2", t2.getName());
    }

    // ------------------------------------------------------------------
    // ChannelSliceInputStream
    // ------------------------------------------------------------------


    @Test
    void testChannelSliceReadsOnlyItsOwnSlice(@TempDir Path tmp) throws IOException
    {
        Path f = writeBytes(tmp, "slice.bin", "ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8));
        try (FileChannel ch = open(f); DataSetJsonTableParallelParser.ChannelSliceInputStream in = //
                new DataSetJsonTableParallelParser.ChannelSliceInputStream(ch, 2L, 7L))
        {
            assertEquals(5, in.available());
            byte[] buf = new byte[16];
            // Read into a non-zero offset, and ask for more than the slice holds.
            assertEquals(3, in.read(buf, 4, 3));
            assertEquals("CDE", new String(buf, 4, 3, StandardCharsets.UTF_8));
            assertEquals(2, in.available());
            // The position must have advanced: a second read returns the NEXT bytes, never the
            // same ones again.
            assertEquals(2, in.read(buf, 0, 8));
            assertEquals("FG", new String(buf, 0, 2, StandardCharsets.UTF_8));
            assertEquals(0, in.available());
            assertEquals(-1, in.read(buf, 0, 8));
        }
    }


    @Test
    void testChannelSliceSingleByteReadIsUnsigned(@TempDir Path tmp) throws IOException
    {
        // 0xFF must come back as 255, not as -1: -1 is this stream's end-of-slice answer, so a
        // sign-extended byte would truncate the chunk at the first high byte of a UTF-8 sequence.
        Path f = writeBytes(tmp, "high-bytes.bin", new byte[]
        {
                (byte) 0xFF, 0x41
        });
        try (FileChannel ch = open(f); DataSetJsonTableParallelParser.ChannelSliceInputStream in = //
                new DataSetJsonTableParallelParser.ChannelSliceInputStream(ch, 0L, 2L))
        {
            assertEquals(255, in.read());
            assertEquals(65, in.read());
            assertEquals(-1, in.read());
        }
    }


    @Test
    void testChannelSliceZeroLengthReadReturnsZero(@TempDir Path tmp) throws IOException
    {
        // InputStream's contract: a zero-length request reads nothing and returns 0 — not -1,
        // which would announce end-of-stream to a caller that had not read a byte.
        Path f = writeBytes(tmp, "zero-len.bin", "ABC".getBytes(StandardCharsets.UTF_8));
        try (FileChannel ch = open(f); DataSetJsonTableParallelParser.ChannelSliceInputStream in = //
                new DataSetJsonTableParallelParser.ChannelSliceInputStream(ch, 0L, 3L))
        {
            assertEquals(0, in.read(new byte[4], 0, 0));
            assertEquals(3, in.available());
        }
    }


    @Test
    void testChannelSliceAvailableIsClampedAtZero(@TempDir Path tmp) throws IOException
    {
        Path f = writeBytes(tmp, "avail.bin", "ABC".getBytes(StandardCharsets.UTF_8));
        try (FileChannel ch = open(f); DataSetJsonTableParallelParser.ChannelSliceInputStream in = //
                new DataSetJsonTableParallelParser.ChannelSliceInputStream(ch, 3L, 3L))
        {
            assertEquals(0, in.available());
            assertEquals(-1, in.read());
        }
    }
}
