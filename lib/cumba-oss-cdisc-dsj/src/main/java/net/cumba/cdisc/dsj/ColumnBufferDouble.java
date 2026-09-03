package net.cumba.cdisc.dsj;

import org.jspecify.annotations.Nullable;

/**
 * A IColumnBuffer that uses an internal double[] to store values.<br/>
 * This buffer can store missing / null values and map them automatically to
 * {@link Double#NaN}.<br/>
 * If {@link #getLongValue(int)} is called for a missing / null value, {@link Long#MIN_VALUE} is
 * returned. In this case {@link #isMissing(int)} should be used to check for missing status.
 */
public class ColumnBufferDouble implements IColumnBuffer
{

    /**
     * The array of primitive double values to be stored.
     */
    private final double[] buffer;

    /**
     * Create a new buffer and use the given array (not a copy).
     *
     * @param aBuffer
     *            the buffer array to be used.
     */
    public ColumnBufferDouble(double[] aBuffer)
    {
        buffer = aBuffer;
    }


    /**
     * Create a new buffer with an internal array of the given size.
     *
     * @param aSize
     *            the size of the array to be created internally.
     */
    public ColumnBufferDouble(int aSize)
    {
        buffer = new double[aSize];
    }


    @Override
    public void setDoubleValue(int aIndex, double aValue)
    {
        buffer[aIndex] = aValue;
    }


    @Override
    public void setLongValue(int aIndex, long aValue)
    {
        buffer[aIndex] = aValue;
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aValue)
    {

        if (aValue instanceof Number num)
        {
            setDoubleValue(aIndex, num.doubleValue());
        }
        else if (aValue == null)
        {
            setDoubleValue(aIndex, Double.NaN);
        }
        else
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
    }


    @Override
    public Object getValue(int aIndex)
    {
        return buffer[aIndex];
    }


    @Override
    public double getDoubleValue(int aIndex)
    {
        return buffer[aIndex];
    }


    @Override
    public long getLongValue(int aIndex)
    {
        if (isMissing(aIndex))
        {
            return Long.MIN_VALUE;
        }
        return (long) buffer[aIndex];
    }


    @Override
    public boolean isMissing(int aIndex)
    {
        return Double.isNaN(buffer[aIndex]);
    }
}
