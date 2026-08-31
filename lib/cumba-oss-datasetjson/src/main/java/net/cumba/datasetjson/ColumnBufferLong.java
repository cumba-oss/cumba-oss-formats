package net.cumba.datasetjson;

import org.jspecify.annotations.Nullable;

/**
 * A IColumnBuffer that uses an internal long[] to store values.<br/>
 * This buffer can store missing / null values. If {@link #getLongValue(int)} is called for a
 * missing / null value, {@link Long#MIN_VALUE} is returned. In this case {@link #isMissing(int)}
 * should be used to check for missing status.
 */
public class ColumnBufferLong implements IColumnBuffer
{

    /**
     * The array of primitive long values to be stored.
     */
    private final long[] buffer;

    // TODO: change to use 1bit storage?
    /**
     * An array that stores missing flags. This is filled in {@link #setValue(int, Object)} if the
     * given value is null and can be accessed by {@link #isMissing(int)}.
     */
    private final byte[] missings;

    /**
     * Create a new buffer and use the given array (not a copy).
     *
     * @param aBuffer
     *            the buffer array to be used.
     */
    public ColumnBufferLong(long[] aBuffer)
    {
        buffer = aBuffer;
        missings = new byte[aBuffer.length];
    }


    /**
     * Create a new buffer with an internal array of the given size.
     *
     * @param aSize
     *            the size of the array to be created internally.
     */
    public ColumnBufferLong(int aSize)
    {
        buffer = new long[aSize];
        missings = new byte[aSize];
    }


    @Override
    public void setLongValue(int aIndex, long aValue)
    {
        buffer[aIndex] = aValue;
        missings[aIndex] = 0;
    }


    @Override
    public void setDoubleValue(int aIndex, double aValue)
    {
        if (Double.isNaN(aValue))
        {
            buffer[aIndex] = Long.MIN_VALUE;
            missings[aIndex] = 1;
        }
        else
        {
            setLongValue(aIndex, (long) aValue);
        }
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aValue)
    {
        if (aValue instanceof Number num)
        {
            if (Double.isNaN(num.doubleValue()))
            {
                buffer[aIndex] = Long.MIN_VALUE;
                missings[aIndex] = 1;
            }
            else
            {
                setLongValue(aIndex, num.longValue());
            }
        }
        else if (aValue == null)
        {
            buffer[aIndex] = Long.MIN_VALUE;
            missings[aIndex] = 1;
        }
        else
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
    }


    @Override
    public @Nullable Object getValue(int aIndex)
    {
        if (isMissing(aIndex))
        {
            return null;
        }
        return buffer[aIndex];
    }


    @Override
    public long getLongValue(int aIndex)
    {
        if (isMissing(aIndex))
        {
            return Long.MIN_VALUE;
        }
        return buffer[aIndex];
    }


    @Override
    public double getDoubleValue(int aIndex)
    {
        if (isMissing(aIndex))
        {
            return Double.NaN;
        }
        return buffer[aIndex];
    }


    @Override
    public boolean isMissing(int aIndex)
    {
        return missings[aIndex] != 0;
    }
}
