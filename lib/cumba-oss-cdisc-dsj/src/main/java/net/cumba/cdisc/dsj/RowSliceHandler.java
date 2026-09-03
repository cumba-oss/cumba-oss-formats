package net.cumba.cdisc.dsj;

/**
 * Handler interface for row slices.
 */
@FunctionalInterface
public interface RowSliceHandler
{

    /**
     * Called once the next slice of row data is available.
     *
     * @param aTable
     *            the table that is currently parsed.
     * @param aFirstRow
     *            the index of the first row in this slice.
     * @param aRowCount
     *            the number of rows in this slice.
     * @param aColumnData
     *            the buffers that store the data in the row slice.
     * @return 0 to continue parsing or any other value to abort parsing.
     */
    int nextRowsAvail(DsjTable aTable, long aFirstRow, int aRowCount,
            IColumnBufferGetter[] aColumnData);
}
