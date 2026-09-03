package net.cumba.cdisc.dsj;

import org.jspecify.annotations.Nullable;

/**
 * Handler interface for row-based parsing. Unlike {@link RowSliceHandler} which delivers data in
 * columnar buffer slices, this handler receives one row at a time as an {@code Object[]} array.
 *
 * <p>
 * The values in the array correspond to JSON token types:
 * <ul>
 * <li>{@link String} for JSON string values
 * <li>{@link Long} for JSON integer values
 * <li>{@link Double} for JSON floating-point values
 * <li>{@link Boolean} for JSON boolean values
 * <li>{@code null} for JSON null values
 * </ul>
 *
 * @see DataSetJsonTableParser
 */
@FunctionalInterface
public interface RowHandler
{

    /**
     * Called for each parsed row.
     *
     * @param aTable
     *            the table that is currently parsed.
     * @param aRowIndex
     *            the 0-based index of the row.
     * @param aValues
     *            the row values as an array with one element per column.
     * @return 0 to continue parsing or any other value to abort parsing.
     */
    int nextRow(DsjTable aTable, long aRowIndex, @Nullable Object[] aValues);
}
