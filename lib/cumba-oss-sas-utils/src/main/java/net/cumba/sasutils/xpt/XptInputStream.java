/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.xpt;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An input stream that keeps track of our position and allows us to jump forward to a new "page"
 * defined by a page size parameter
 *
 * @author daniel.watson
 *
 */
public class XptInputStream extends BufferedInputStream
{

    protected static final Logger LOGGER = LoggerFactory.getLogger(XptInputStream.class);

    public static final int DEFAULT_PAGE_SIZE = 80;

    protected int pageSize;

    protected volatile long position;

    protected long mark;

    public XptInputStream(InputStream in)
    {
        this(in, DEFAULT_PAGE_SIZE);
    }


    public XptInputStream(InputStream in, int size)
    {
        super(in, size);
        this.pageSize = size;
    }


    public synchronized long getPosition()
    {
        return position;
    }


    public boolean nextPage() throws IOException
    {
        return nextPage(false);
    }


    /**
     * Jumps to the next page if we are not at a page start
     *
     * @param force
     *            forces the jump to happen even if we are at page start already
     * @return true if a jump was performed, false otherwise.
     * @throws IOException
     *             in case of any I/O error while skipping to the next page.
     */
    public boolean nextPage(boolean force) throws IOException
    {
        if (getPagePosition() != 0 || force)
        {
            // Skip the whole remaining gap to the next 80-byte page boundary. skip() may return
            // fewer bytes than requested, so loop (falling back to a 1-byte read at EOF-of-buffer)
            // until the boundary is reached; a partial skip would silently misalign all parsing.
            long remaining = pageSize - position % pageSize;
            boolean advanced = false;
            while (remaining > 0)
            {
                long skipped = skip(remaining);
                if (skipped > 0)
                {
                    remaining -= skipped;
                    advanced = true;
                }
                else if (read() < 0)
                {
                    break; // end of stream
                }
                else
                {
                    remaining -= 1; // read() advanced position by one byte
                    advanced = true;
                }
            }
            return advanced;
        }
        else
            return false;
    }


    @Override
    public synchronized int read() throws IOException
    {
        int b = super.read();
        if (b >= 0) position += 1;
        return b;
    }


    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException
    {
        int n = super.read(b, off, len);
        if (n > 0) position += n;
        return n;
    }


    public synchronized long getPage()
    {
        return position / pageSize;
    }


    public synchronized long getPageStart()
    {
        return position - position % pageSize;
    }


    public synchronized long getPagePosition()
    {
        return position % pageSize;
    }


    public boolean isHeader() throws IOException
    {
        return isHeader(null);
    }


    public boolean isHeader(@Nullable String type) throws IOException
    {
        mark(pageSize);
        String string = XptConstants.HEADER_TAG + (type != null ? type : "");
        byte[] buffer = new byte[string.length()];
        int total = 0;
        while (total < buffer.length)
        {
            int n = this.read(buffer, total, buffer.length - total);
            if (n < 0)
            {
                // EOF before a full header could be read — caller treats this as
                // "no more headers", not corruption. Tests rely on this being
                // a non-throwing peek (XptLibrary parsing terminates on it).
                reset();
                return false;
            }
            total += n;
        }
        boolean found = Arrays.equals(buffer, string.getBytes(StandardCharsets.US_ASCII));
        reset();
        return found;
    }


    @Override
    public synchronized long skip(long skip) throws IOException
    {
        long n = super.skip(skip);
        if (n > 0)
        {
            position += n;
        }
        return n;
    }


    @Override
    public synchronized void mark(int readlimit)
    {
        super.mark(readlimit);
        mark = position;
    }


    @Override
    public synchronized void reset() throws IOException
    {
        if (!markSupported()) throw new IOException("Mark not supported.");
        super.reset();
        position = mark;
    }

}
