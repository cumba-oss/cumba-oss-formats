/*
 * Added by P300 as part of cumba-oss-sas-utils, which is derived from theshoeshiner/sas-utils
 * (https://github.com/theshoeshiner/sas-utils), licensed under the Apache License, Version 2.0.
 * This file has no upstream counterpart. See this module's README.md for the full attribution
 * notice.
 */
package net.cumba.sasutils.bdat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An alternative version to {@link ByteArrayInputStream} with following changes:
 * <ul>
 * <li>Uses a {@link ByteBuffer} instead of an byte[]
 * <li>Allows to read / update the position in the buffer
 * </ul>
 */
public class SeekableByteArrayInputStream extends InputStream
{

    /**
     * The byte buffer that stores the data to be accessed.
     */
    private final ByteBuffer buffer;

    /**
     * Create a new instance that works on the given array. Changes to the array will directly be
     * reflected in the stream.
     *
     * @param aBuffer
     *            the data to be worked on.
     */
    public SeekableByteArrayInputStream(byte[] aBuffer)
    {
        buffer = ByteBuffer.wrap(aBuffer);
    }


    /**
     * Returns the actual position in the data buffer.
     *
     * @return the actual position in the data buffer.
     */
    public int getPosition()
    {
        return buffer.position();
    }


    /**
     * Set the position inside of the data buffer to read next.
     *
     * @param aNewPosition
     *            the new position to set. This is relative to the beginning of the data buffer.
     */
    public void seek(long aNewPosition)
    {
        buffer.position(Math.toIntExact(aNewPosition));
    }


    @Override
    public int read()
    {
        return buffer.hasRemaining() ? buffer.get() & 0xFF : -1;
    }


    @Override
    public int read(byte[] b, int off, int len)
    {
        if (!buffer.hasRemaining())
        {
            return -1;
        }
        int toRead = Math.min(len, buffer.remaining());
        buffer.get(b, off, toRead);
        return toRead;
    }


    @Override
    public int available()
    {
        return buffer.remaining();
    }


    @Override
    public void mark(int readlimit)
    {
        buffer.mark();
    }


    @Override
    public void reset()
    {
        buffer.reset();
    }


    @Override
    public boolean markSupported()
    {
        return true;
    }
}
