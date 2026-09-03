package net.cumba.cdisc.dsj;

/**
 * Enum for the {@link DsjTableColumn} property targetDataType.
 */
public enum ColumnTargetDataType
{

    /**
     * Column Target Data type for an integer column.
     */
    INTEGER,

    /**
     * Column Target Data type for an decimal column.
     */
    DECIMAL,

    /**
     * Column Target Data for not set (unknown).
     */
    UNKNOWN,

    /**
     * Column Target Data type for any other value.
     */
    OTHER;

    /**
     * Map a String representation of the columns targetDataType property to the
     * {@link ColumnTargetDataType}.
     *
     * @param aTypeName
     *            the string value of the targetDataType property.
     * @return the ColumnTargetDataType.
     */
    public static ColumnTargetDataType getFor(String aTypeName)
    {
        if (aTypeName == null)
        {
            return UNKNOWN;
        }
        try
        {
            return ColumnTargetDataType.valueOf(aTypeName.toUpperCase(java.util.Locale.ROOT));
        }
        catch (Exception _)
        {
            return OTHER;
        }
    }


    /**
     * Returns the target data type in DataSet-JSON specification form (lower case).
     *
     * @return a string that is the target data type in DataSet-JSON specification form (lower
     *         case).
     */
    public String toDsjString()
    {
        return toString().toLowerCase(java.util.Locale.ROOT);
    }
}
