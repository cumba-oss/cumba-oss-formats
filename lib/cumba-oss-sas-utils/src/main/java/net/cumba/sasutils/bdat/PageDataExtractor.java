/*
 * Added by P300 as part of this module, which is derived from theshoeshiner/sas-utils
 * (https://github.com/theshoeshiner/sas-utils), licensed under the Apache License, Version 2.0.
 * Factored out of this module's BDAT reader; see this module's README.md for the full attribution
 * notice and LICENSE-APACHE-2.0.txt for the licence.
 */
package net.cumba.sasutils.bdat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Stateless per-page row extraction. Produces the ordered list of raw (already decompressed)
 * observation byte rows for a single page, preserving the iterator's order: data-subheader rows
 * first (signature {@code DATA}, in pointer order), then block rows (deleted rows skipped). It is a
 * pure function of the page buffer plus read-only dataset metadata, so it is safe to call
 * concurrently for <em>different</em> {@link Page} objects (each has its own {@code pageBuffer}).
 *
 * <p>
 * This is the single source of truth for per-page extraction shared by the sequential
 * {@link ObservationIteratorBdat2} and the parallel provider pipeline, guaranteeing identical row
 * ordering and decompression by construction.
 * </p>
 */
public final class PageDataExtractor
{

    private PageDataExtractor()
    {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * The ordered, decompressed rows of a single page plus the number of deleted block rows that
     * were skipped.
     *
     * @param rows
     *            the decompressed observation rows, in iteration order.
     * @param deletedCount
     *            the number of deleted block rows skipped on this page.
     */
    public record PageRows(List<byte[]> rows, long deletedCount)
    {
    }

    /**
     * Extract all observation rows from a single page.
     *
     * @param dataset
     *            the read-only dataset metadata.
     * @param page
     *            the page to extract; its {@code pageBuffer} is positioned by this method.
     * @return the ordered rows plus the deleted-row count.
     * @throws IOException
     *             if reading/decompressing a row fails.
     */
    public static PageRows extractRows(DatasetBdat dataset, Page page) throws IOException
    {
        return extractRows(dataset, page, dataset.getCompressed(),
                dataset.getCompressionAlgorithm());
    }


    /**
     * Extract all observation rows from a single page using a <b>pre-resolved</b> compression flag
     * and algorithm. Concurrent callers (the parallel pipeline) must resolve {@code aCompressed} /
     * {@code aAlgorithm} once on the calling thread and pass them in: deriving them per call walks
     * the shared {@code dataset}'s pages/subheaders and must not run on worker threads.
     *
     * @param dataset
     *            the read-only dataset metadata.
     * @param page
     *            the page to extract; its {@code pageBuffer} is positioned by this method.
     * @param aCompressed
     *            whether the dataset is compressed.
     * @param aAlgorithm
     *            the compression algorithm (only consulted when {@code aCompressed}).
     * @return the ordered rows plus the deleted-row count.
     * @throws IOException
     *             if reading/decompressing a row fails.
     */
    public static PageRows extractRows(DatasetBdat dataset, Page page, boolean aCompressed,
            @Nullable CompressionAlgorithm aAlgorithm)
        throws IOException
    {
        List<byte[]> rows = new ArrayList<>();
        long deleted = 0;

        // 1) data-subheader rows (signature == DATA), in pointer order.
        Iterator<SubHeaderPointer> dataPointers = page.getDataSubHeaderPointers().iterator();
        while (dataPointers.hasNext())
        {
            SubHeaderPointer pointer = dataPointers.next();
            page.pageBuffer.seek(pointer.getPageOffset());
            rows.add(ObservationIteratorBdat2.readRowFromStream(dataset, page.pageBuffer,
                    Math.toIntExact(pointer.getLength()), aCompressed, aAlgorithm));
        }

        // 2) block rows, skipping deleted ones.
        long blockCount = page.getBlockObservationCount();
        long dataAreaOffset = page.getDataAreaOffset();
        int rowLength = dataset.rowSizeSubHeader.getRowLength().intValue();
        for (long i = 0; i < blockCount; i++)
        {
            if (page.isBlockRowDeleted(i))
            {
                deleted++;
                continue;
            }
            page.pageBuffer.seek(dataAreaOffset + rowLength * i);
            rows.add(ObservationIteratorBdat2.readRowFromStream(dataset, page.pageBuffer,
                    Math.toIntExact(dataset.getRowLength()), aCompressed, aAlgorithm));
        }

        return new PageRows(rows, deleted);
    }
}
