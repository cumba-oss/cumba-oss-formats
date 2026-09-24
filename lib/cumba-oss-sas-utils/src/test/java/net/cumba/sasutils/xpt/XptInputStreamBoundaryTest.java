package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Boundary behaviour of {@link XptInputStream}: the page-skip loop, the header peek, and the
 * position accounting they both rely on.
 * <p>
 * ⚠ <strong>An XPT file carries no row count.</strong> Records are fixed-width and packed, and the
 * only thing that says where a member's data begins is the 80-byte page arithmetic in this class. A
 * page skip that stops one byte short does not fail — it shifts every subsequent field by a byte
 * and yields values that look like data. That is why the skip loop must not trust a single
 * {@code skip()} call, and why these tests use streams that deliberately under-deliver.
 */
class XptInputStreamBoundaryTest
{

    private static final int PAGE = XptInputStream.DEFAULT_PAGE_SIZE;

    /** A stream whose {@code skip} always refuses, forcing the read-one-byte fallback. */
    private static final class NeverSkips extends FilterInputStream
    {

        NeverSkips(byte[] data)
        {
            super(new ByteArrayInputStream(data));
        }


        @Override
        public long skip(long n)
        {
            return 0;
        }
    }


    /** A stream that hands over at most one byte per call, forcing partial reads. */
    private static final class OneByteAtATime extends FilterInputStream
    {

        OneByteAtATime(byte[] data)
        {
            super(new ByteArrayInputStream(data));
        }


        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            if (len == 0)
            {
                return 0;
            }
            return super.read(b, off, 1);
        }
    }

    private static byte[] page(String head)
    {
        byte[] out = new byte[4 * PAGE];
        byte[] text = head.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, out, 0, text.length);
        for (int i = text.length; i < out.length; i++)
        {
            out[i] = 'x';
        }
        return out;
    }

    // ------------------------------------------------------------------
    // nextPage
    // ------------------------------------------------------------------


    /**
     * {@code skip()} is allowed to return fewer bytes than asked for — and a stream is allowed to
     * return zero forever. The loop must fall back to single reads rather than leaving the position
     * mid-page.
     */
    @Test
    void nextPageReachesTheBoundaryEvenWhenSkipAlwaysRefuses() throws IOException
    {
        try (XptInputStream in = new XptInputStream(new NeverSkips(page("")), PAGE))
        {
            assertEquals(7, in.read(new byte[7]));
            assertEquals(7, in.getPosition());

            assertTrue(in.nextPage(), "a jump was performed, so nextPage reports true");
            assertEquals(PAGE, in.getPosition());
            assertEquals(0, in.getPagePosition());
            assertEquals(1, in.getPage());
            assertEquals(PAGE, in.getPageStart());
        }
    }


    @Test
    void nextPageReportsWhetherItMoved() throws IOException
    {
        try (XptInputStream in = new XptInputStream(new ByteArrayInputStream(page("")), PAGE))
        {
            assertFalse(in.nextPage(), "already at a page start, so nothing to skip");
            assertEquals(1, in.read(new byte[1]));
            assertTrue(in.nextPage(), "mid-page, so the remainder is skipped");
            assertEquals(PAGE, in.getPosition());
        }
    }


    /** At the very end of the stream there is nothing left to skip, so no jump happens. */
    @Test
    void nextPageAtEndOfStreamReportsNoJump() throws IOException
    {
        byte[] exactlyOnePage = new byte[PAGE];
        try (XptInputStream in = new XptInputStream(new NeverSkips(exactlyOnePage), PAGE))
        {
            assertEquals(PAGE, in.read(new byte[PAGE]));
            assertEquals(PAGE, in.getPosition());
            assertFalse(in.nextPage(true), "the stream is exhausted, so nothing advances");
            assertEquals(PAGE, in.getPosition());
        }
    }


    @Test
    void getPageStartTracksTheCurrentPage() throws IOException
    {
        try (XptInputStream in = new XptInputStream(new ByteArrayInputStream(page("")), PAGE))
        {
            assertEquals(0, in.getPageStart());
            assertEquals(PAGE + 5, in.read(new byte[PAGE + 5]));
            assertEquals(PAGE, in.getPageStart());
            assertEquals(5, in.getPagePosition());
        }
    }

    // ------------------------------------------------------------------
    // Position accounting
    // ------------------------------------------------------------------


    @Test
    void zeroLengthReadsAndSkipsDoNotMoveThePosition() throws IOException
    {
        try (XptInputStream in = new XptInputStream(new ByteArrayInputStream(page("")), PAGE))
        {
            assertEquals(0, in.read(new byte[4], 0, 0));
            assertEquals(0, in.getPosition());
            assertEquals(0, in.skip(0));
            assertEquals(0, in.getPosition());
            assertEquals(6, in.skip(6));
            assertEquals(6, in.getPosition());
        }
    }

    // ------------------------------------------------------------------
    // isHeader
    // ------------------------------------------------------------------


    /**
     * The peek must consume nothing: {@code XptLibrary} calls it to decide what comes next and then
     * parses from the same position. A missing {@code reset()} would silently swallow the first
     * twenty bytes of whatever it just identified.
     */
    @Test
    void isHeaderLeavesThePositionUntouched() throws IOException
    {
        byte[] data = page(XptConstants.HEADER_TAG + "LIBRARY ");
        try (XptInputStream in = new XptInputStream(new ByteArrayInputStream(data), PAGE))
        {
            assertTrue(in.isHeader());
            assertEquals(0, in.getPosition(), "a peek must not consume");
            assertTrue(in.isHeader("LIBRARY "));
            assertEquals(0, in.getPosition());
            assertFalse(in.isHeader("MEMBER  "));
            assertEquals(0, in.getPosition());

            // and the bytes are still there to be read
            byte[] first = new byte[XptConstants.HEADER_TAG.length()];
            assertEquals(first.length, in.read(first));
            assertEquals(XptConstants.HEADER_TAG, new String(first, StandardCharsets.US_ASCII));
        }
    }


    /**
     * The tag arrives one byte per read here, so the loop has to keep asking for the
     * <em>remaining</em> bytes. Asking for the full length every time would overwrite what it
     * already has.
     */
    @Test
    void isHeaderReassemblesATagDeliveredOneByteAtATime() throws IOException
    {
        byte[] data = page(XptConstants.HEADER_TAG + "LIBRARY ");
        try (XptInputStream in = new XptInputStream(new OneByteAtATime(data), PAGE))
        {
            assertTrue(in.isHeader(), "the tag is complete, just fragmented");
            assertEquals(0, in.getPosition());
        }
    }


    /**
     * End of stream before a full tag is "no more headers", not corruption — library parsing
     * terminates on it — and the position must still be where it was.
     */
    @Test
    void isHeaderAtEndOfStreamIsFalseAndDoesNotConsume() throws IOException
    {
        byte[] truncated = "HEADER".getBytes(StandardCharsets.US_ASCII);
        try (XptInputStream in = new XptInputStream(new ByteArrayInputStream(truncated), PAGE))
        {
            assertFalse(in.isHeader());
            assertEquals(0, in.getPosition());
        }
    }


    @Test
    void isHeaderIsFalseForOtherContent() throws IOException
    {
        try (InputStream raw = new ByteArrayInputStream(page("NOT A HEADER RECORD"));
                XptInputStream in = new XptInputStream(raw, PAGE))
        {
            assertFalse(in.isHeader());
            assertEquals(0, in.getPosition());
        }
    }

}
