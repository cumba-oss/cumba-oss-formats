package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class XptInputStreamTest
{

    @Test
    void nextPageSkipsFullPageDespitePartialUnderlyingSkip() throws IOException
    {
        // Underlying stream whose skip() returns at most one byte at a time (legal per the
        // InputStream contract). nextPage must still land exactly on the next 80-byte boundary; a
        // single skip() call would leave the stream misaligned.
        InputStream capped = new FilterInputStream(new ByteArrayInputStream(new byte[240]))
        {

            @Override
            public long skip(long aN) throws IOException
            {
                return super.skip(Math.min(aN, 1L));
            }
        };
        XptInputStream xis = new XptInputStream(capped, 80);

        assertTrue(xis.nextPage(true));
        assertEquals(0, xis.getPagePosition(), "must land on a page boundary");
        assertEquals(80, xis.getPosition());
    }


    @Test
    void positionTracking_read() throws IOException
    {
        byte[] data = new byte[160];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);
        assertEquals(0, xis.getPosition());

        xis.read();
        assertEquals(1, xis.getPosition());

        byte[] buf = new byte[10];
        xis.read(buf, 0, 10);
        assertEquals(11, xis.getPosition());
        xis.close();
    }


    @Test
    void getPagePosition() throws IOException
    {
        byte[] data = new byte[240];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);

        assertEquals(0, xis.getPagePosition());
        assertEquals(0, xis.getPage());

        byte[] buf = new byte[20];
        xis.read(buf);
        assertEquals(20, xis.getPagePosition());
        assertEquals(0, xis.getPage());

        // Read to 80 bytes = page boundary
        byte[] buf2 = new byte[60];
        xis.read(buf2);
        assertEquals(0, xis.getPagePosition());
        assertEquals(1, xis.getPage());
        xis.close();
    }


    @Test
    void nextPage_skipsToPageBoundary() throws IOException
    {
        byte[] data = new byte[400];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);

        byte[] buf = new byte[30];
        xis.read(buf);
        assertEquals(30, xis.getPosition());

        xis.nextPage();
        assertEquals(80, xis.getPosition());
        assertEquals(0, xis.getPagePosition());
        xis.close();
    }


    @Test
    void nextPage_atBoundary_doesNotSkip() throws IOException
    {
        byte[] data = new byte[400];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);

        byte[] buf = new byte[80];
        xis.read(buf);
        assertEquals(80, xis.getPosition());

        boolean skipped = xis.nextPage();
        assertFalse(skipped);
        assertEquals(80, xis.getPosition());
        xis.close();
    }


    @Test
    void nextPage_force() throws IOException
    {
        byte[] data = new byte[400];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);

        byte[] buf = new byte[80];
        xis.read(buf);
        assertEquals(80, xis.getPosition());

        boolean skipped = xis.nextPage(true);
        assertTrue(skipped);
        assertEquals(160, xis.getPosition());
        xis.close();
    }


    @Test
    void markAndReset() throws IOException
    {
        byte[] data = new byte[200];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);

        byte[] buf = new byte[50];
        xis.read(buf);
        xis.mark(100);

        byte[] buf2 = new byte[30];
        xis.read(buf2);
        assertEquals(80, xis.getPosition());

        xis.reset();
        assertEquals(50, xis.getPosition());
        xis.close();
    }


    @Test
    void skip_tracksPosition() throws IOException
    {
        byte[] data = new byte[400];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);

        long skipped = xis.skip(100);
        assertTrue(skipped > 0);
        assertEquals(skipped, xis.getPosition());
        xis.close();
    }


    @Test
    void getPosition_returnsLong() throws IOException
    {
        byte[] data = new byte[100];
        XptInputStream xis = new XptInputStream(new ByteArrayInputStream(data), 80);
        // Verify it compiles as long
        long pos = xis.getPosition();
        assertEquals(0L, pos);
        xis.close();
    }
}
