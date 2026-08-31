package net.cumba.datasetjson;

import org.jspecify.annotations.Nullable;

/**
 * A IColumnBuffer that uses an internal Object[] to store values.<br/>
 * This buffer stores values as-is and treats null values as missing.
 */
public class ColumnBufferObject implements IColumnBuffer
{

    /**
     * The array of values to be stored.
     */
    private final @Nullable Object[] buffer;

    /**
     * Create a new buffer and use the given array (not a copy).
     *
     * @param aBuffer
     *            the buffer array to be used.
     */
    public ColumnBufferObject(@Nullable Object[] aBuffer)
    {
        buffer = aBuffer;
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aValue)
    {
        buffer[aIndex] = aValue;
    }


    @Override
    public @Nullable Object getValue(int aIndex)
    {
        return buffer[aIndex];
    }


    @Override
    public boolean isMissing(int aIndex)
    {
        return buffer[aIndex] == null;
    }
}
