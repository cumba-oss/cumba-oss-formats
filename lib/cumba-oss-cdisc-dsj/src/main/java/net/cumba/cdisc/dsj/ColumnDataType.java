package net.cumba.cdisc.dsj;

/**
 * Enum for the {@link DsjTableColumn} property dataType.
 */
public enum ColumnDataType
{

    /**
     * Column Data type for a string column.
     */
    STRING,

    /**
     * Column Data type for an integer column.
     */
    INTEGER,

    /**
     * Column Data type for a decimal column.
     */
    DECIMAL,

    /**
     * Column Data type for a float column.
     */
    FLOAT,

    /**
     * Column Data type for a double column.
     */
    DOUBLE,

    /**
     * Column Data type for a boolean column.
     */
    BOOLEAN,

    /**
     * Column Data type for a date and time column.
     */
    DATETIME,

    /**
     * Column Data type for a date column.
     */
    DATE,

    /**
     * Column Data type for a time column.
     */
    TIME,

    /**
     * Column Data type for a URI column.
     */
    URI,

    /**
     * Column Data type for any other value including if no value is available.
     */
    OTHER;

    /**
     * Map a String representation of the columns dataType property to the {@link ColumnDataType}.
     *
     * @param aTypeName
     *            the string value of the dataType property.
     * @return the ColumnDataType.
     */
    public static ColumnDataType getFor(String aTypeName)
    {
        if (aTypeName == null)
        {
            return OTHER;
        }

        try
        {
            return ColumnDataType.valueOf(aTypeName.toUpperCase(java.util.Locale.ROOT));
        }
        catch (Exception _)
        {
            return OTHER;
        }
    }


    /**
     * Returns the data type in DataSet-JSON specification form (lower case).
     *
     * @return a string that is the data type in DataSet-JSON specification form (lower case).
     */
    public String toDsjString()
    {
        return toString().toLowerCase(java.util.Locale.ROOT);
    }
}
