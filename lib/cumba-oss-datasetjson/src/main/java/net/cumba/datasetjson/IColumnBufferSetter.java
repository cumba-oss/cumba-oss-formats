package net.cumba.datasetjson;

import org.jspecify.annotations.Nullable;

/**
 * The setter interface for a column buffer.
 */
public interface IColumnBufferSetter
{

    /**
     * Set the value at the given index.
     *
     * @param aIndex
     *            the index to set the value at.
     * @param aValue
     *            the value to be set at the given index.
     */
    void setValue(int aIndex, @Nullable Object aValue);


    /**
     * Set the value at the given index. This method ensures that the value is a String.
     *
     * @param aIndex
     *            the index to set the value at.
     * @param aValue
     *            the value to be set at the given index.
     */
    default void setStringValue(int aIndex, String aValue)
    {
        setValue(aIndex, aValue);
    }


    /**
     * Set the value at the given index. This method ensures that the value is a double. In case the
     * buffer can store primitive types, this allows to avoid boxing.
     *
     * @param aIndex
     *            the index to set the value at.
     * @param aValue
     *            the value to be set at the given index.
     */
    default void setDoubleValue(int aIndex, double aValue)
    {
        setValue(aIndex, aValue);
    }


    /**
     * Set the value at the given index. This method ensures that the value is a long. In case the
     * buffer can store primitive types, this allows to avoid boxing.
     *
     * @param aIndex
     *            the index to set the value at.
     * @param aValue
     *            the value to be set at the given index.
     */
    default void setLongValue(int aIndex, long aValue)
    {
        setValue(aIndex, aValue);
    }
}
