package net.cumba.datasetjson;

import org.jspecify.annotations.Nullable;

/**
 * The getter interface for a column buffer.
 */
public interface IColumnBufferGetter
{

    /**
     * Retrieve a value at a given index in the original value type.
     *
     * @param aIndex
     *            the index of the value to retrieve.
     * @return the value at the given index.
     * @throws IndexOutOfBoundsException
     *             in case the given index is out of the buffer bounds.
     */
    @Nullable
    Object getValue(int aIndex) throws IndexOutOfBoundsException;


    /**
     * Retrieve a value at a given index in the as a String.
     *
     * @param aIndex
     *            the index of the value to retrieve.
     * @return the value at the given index as String.
     */
    default @Nullable String getStringValue(int aIndex)
    {
        Object val = getValue(aIndex);
        return (val != null) ? val.toString() : null;
    }


    /**
     * Retrieve a value at a given index in the as a double.
     *
     * @param aIndex
     *            the index of the value to retrieve.
     * @return the value at the given index as double. If the value can not be retrieved as double
     *         {@link Double#NaN} is returned.
     */
    default double getDoubleValue(int aIndex)
    {
        Object val = getValue(aIndex);
        if (val instanceof Number num)
        {
            return num.doubleValue();
        }
        return Double.NaN;
    }


    /**
     * Retrieve a value at a given index in the as a long.
     *
     * @param aIndex
     *            the index of the value to retrieve.
     * @return the value at the given index as long.
     * @throws IllegalArgumentException
     *             in case the value at the given index is not a Number and therefore can not be
     *             accessed as long.
     */
    default long getLongValue(int aIndex) throws IllegalArgumentException
    {
        Object val = getValue(aIndex);
        if (val instanceof Number num)
        {
            return num.longValue();
        }
        throw new IllegalArgumentException("Value at index %d is not a number!".formatted(aIndex));
    }


    /**
     * Check if the value at the given index is missing (e.g. null or {@link Double#NaN}).
     *
     * @param aIndex
     *            the index of the value to be checked.
     * @return true if the value at the given index is a missing value, false otherwise.
     */
    boolean isMissing(int aIndex);
}
