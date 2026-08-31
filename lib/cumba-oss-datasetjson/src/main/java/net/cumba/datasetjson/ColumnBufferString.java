package net.cumba.datasetjson;

import org.jspecify.annotations.Nullable;

/**
 * A IColumnBuffer that uses an internal String[] to store values.<br/>
 * Null values are stored as null and {@code MissingValue}s, as well as all other objects are
 * converted to string by {@link Object#toString()}.
 */
public class ColumnBufferString implements IColumnBuffer
{

    /**
     * The array of values to be stored.
     */
    private final @Nullable String[] buffer;

    /**
     * Create a new buffer and use the given array (not a copy).
     *
     * @param aBuffer
     *            the buffer array to be used.
     */
    public ColumnBufferString(@Nullable String[] aBuffer)
    {
        buffer = aBuffer;
    }


    /**
     * Create a new buffer with an internal array of the given size.
     *
     * @param aSize
     *            the size of the array to be created internally.
     */
    public ColumnBufferString(int aSize)
    {
        buffer = new String[aSize];
    }


    @Override
    public void setStringValue(int aIndex, String aValue)
    {
        buffer[aIndex] = aValue;
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aValue)
    {
        buffer[aIndex] = aValue == null ? null : aValue.toString();
    }


    @Override
    public @Nullable String getValue(int aIndex)
    {
        return buffer[aIndex];
    }


    @Override
    public @Nullable String getStringValue(int aIndex)
    {
        return buffer[aIndex];
    }


    @Override
    public boolean isMissing(int aIndex)
    {
        return buffer[aIndex] == null;
    }
}
