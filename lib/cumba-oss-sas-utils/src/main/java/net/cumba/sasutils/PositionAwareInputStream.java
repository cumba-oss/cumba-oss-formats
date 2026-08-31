/*
 * Added by P300 as part of cumba-oss-sas-utils, which is derived from theshoeshiner/sas-utils
 * (https://github.com/theshoeshiner/sas-utils), licensed under the Apache License, Version 2.0.
 * This file has no upstream counterpart. See this module's README.md for the full attribution
 * notice.
 */
package net.cumba.sasutils;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.RandomAccessFileInputStream;

/**
 * A wrapping {@link InputStream} for an other stream. This implementation keeps track of the actual
 * position and allows to seek into forward direction.<br/>
 * Closing this stream always closes the wrapped stream as well.
 */
public class PositionAwareInputStream extends InputStream
{

    /**
     * The input stream that is wrapped by this instance.
     */
    private final InputStream stream;

    /**
     * The current position inside of the stream.
     */
    private long position = 0;

    /**
     * The mark for possibly resetting to a previous position.
     */
    private long mark = 0;

    /**
     * Create a new instance that wraps the given stream.
     *
     * @param aStream
     *            the stream to be wrapped.
     */
    public PositionAwareInputStream(InputStream aStream)
    {
        stream = aStream;
    }


    /**
     * Returns the current position inside of the stream.
     *
     * @return the current position inside of the stream.
     */
    public long getPosition()
    {
        return position;
    }


    /**
     * Set the position inside of the data buffer to read next.<br/>
     * For all base streams but RandomAccessFileInputStream, this implementation is only able to
     * seek forward, so the given position must be &gt;= the actual position.
     *
     * @param aNewPosition
     *            the new position to set. This is relative to the beginning of the data buffer.
     * @throws IOException
     *             in case setting the new position is not possible.
     */
    public void seek(long aNewPosition) throws IOException
    {
        if (aNewPosition < position)
        {
            if (stream instanceof RandomAccessFileInputStream rafis)
            {
                rafis.getRandomAccessFile().seek(aNewPosition);
                position = aNewPosition;
                return;
            }
            throw new IOException("Negative move not supported!");
        }

        skipNBytes(aNewPosition - position);
    }


    @Override
    public int read() throws IOException
    {
        int res = stream.read();
        if (res >= 0)
        {
            position++;
        }
        return res;
    }


    @Override
    public int read(byte[] aB) throws IOException
    {
        int res = stream.read(aB);
        if (res > 0)
        {
            position += res;
        }
        return res;
    }


    @Override
    public int read(byte[] aB, int aOff, int aLen) throws IOException
    {
        int res = stream.read(aB, aOff, aLen);
        if (res > 0)
        {
            position += res;
        }
        return res;
    }


    @Override
    public long skip(long aN) throws IOException
    {
        long res = stream.skip(aN);
        if (res > 0)
        {
            position += res;
        }
        return res;
    }


    @Override
    public boolean markSupported()
    {
        return stream.markSupported();
    }


    @Override
    public void mark(int aReadlimit)
    {
        mark = position;
        stream.mark(aReadlimit);
    }


    @Override
    public void reset() throws IOException
    {
        stream.reset();
        position = mark;
    }


    @Override
    public void close() throws IOException
    {
        stream.close();
        super.close();
    }
}
