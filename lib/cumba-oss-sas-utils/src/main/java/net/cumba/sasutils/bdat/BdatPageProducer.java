/*
 * Added by P300 as part of this module, which is derived from theshoeshiner/sas-utils
 * (https://github.com/theshoeshiner/sas-utils), licensed under the Apache License, Version 2.0.
 * Factored out of this module's BDAT reader; see this module's README.md for the full attribution
 * notice and LICENSE-APACHE-2.0.txt for the licence.
 */
package net.cumba.sasutils.bdat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.cumba.sasutils.PositionAwareInputStream;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yields the observation-bearing {@link Page}s of a BDAT dataset in exactly the order the
 * sequential {@link ObservationIteratorBdat2} visits them: first the pre-parsed pages held in
 * {@link DatasetBdat#getPages()} that carry observations (in file order), then the pages read
 * lazily from {@code metadataPageCount}..{@code header3.getPageCount()}.
 *
 * <p>
 * <b>Parity-critical:</b> deleted-record markers are read ({@link Page#readDeletedMarkers()}) only
 * for the lazily-loaded pages — never re-read for the pre-parsed pages, whose marker state was left
 * exactly as {@link ParserBdat} set it during the metadata pass (a pure-DATA first page has no
 * markers; a MIX page does). Reproducing this exactly is required for byte-identical output.
 * </p>
 *
 * <p>
 * The producer reads sequentially from a single {@link PositionAwareInputStream} and is therefore
 * not itself thread-safe; the cross-page parallelism in the provider pipeline comes from handing
 * the yielded {@link Page}s (each with its own buffer) to worker threads.
 * </p>
 */
public final class BdatPageProducer implements Iterator<Page>
{

    static final Logger LOGGER = LoggerFactory.getLogger(BdatPageProducer.class);

    private final DatasetBdat dataset;

    private final PositionAwareInputStream stream;

    private final Iterator<Page> preParsedPageIterator;

    private long remainingPageIndex;

    private final long totalPageCount;

    private @Nullable Page next;

    /**
     * Create a producer over the given dataset, reading lazy pages from the given stream.
     *
     * @param aDataset
     *            the parsed dataset metadata.
     * @param aStream
     *            the position-aware stream positioned anywhere; the producer seeks as needed.
     */
    public BdatPageProducer(DatasetBdat aDataset, PositionAwareInputStream aStream)
    {
        this.dataset = aDataset;
        this.stream = aStream;
        this.preParsedPageIterator = aDataset.getPages().stream()
                .filter(p -> p.getTotalObservationCount() > 0).iterator();
        this.remainingPageIndex = aDataset.getMetadataPageCount();
        this.totalPageCount = aDataset.header3.getPageCount();
        this.next = findNextObservationPage();
    }


    @Override
    public boolean hasNext()
    {
        return next != null;
    }


    @Override
    public Page next()
    {
        if (next == null)
        {
            throw new NoSuchElementException();
        }
        Page current = next;
        next = findNextObservationPage();
        return current;
    }


    private @Nullable Page findNextObservationPage()
    {
        // First, yield pre-parsed pages that carry observations.
        if (preParsedPageIterator.hasNext())
        {
            return preParsedPageIterator.next();
        }

        // Then, lazily load remaining pages from the stream one at a time.
        try
        {
            while (remainingPageIndex < totalPageCount)
            {
                long startByte = dataset.header3.headerSize
                        + (remainingPageIndex * dataset.header3.pageSize);

                stream.seek(startByte);

                byte[] pageBuffer = new byte[dataset.header3.pageSize];
                IOUtils.readFully(stream, pageBuffer);
                remainingPageIndex++;

                Page page = decodePageAt(dataset, pageBuffer, startByte);
                if (page == null)
                {
                    // Unknown page type — skip, matching the iterator's behaviour.
                    continue;
                }

                if (page.getTotalObservationCount() > 0)
                {
                    return page;
                }
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Error reading remaining pages", e);
        }

        return null;
    }


    /**
     * Decode a single lazily-loaded page from its raw buffer. Loads subheader pointers for meta
     * pages and reads the deleted-record bitmap, matching the lazy-page handling in
     * {@link ObservationIteratorBdat2}. Exposed (and {@code static}) so a future memory-mapped
     * fast-path can decode pages from arbitrary byte offsets without further changes here.
     *
     * @param aDataset
     *            the parsed dataset metadata.
     * @param aPageBuffer
     *            the raw bytes of exactly one page ({@code header3.pageSize} long).
     * @param aStartByte
     *            the file offset of this page (stored on the page for diagnostics).
     * @return the decoded {@link Page}, or {@code null} if the page type is unknown (skip it).
     * @throws IOException
     *             if unpacking the page header or its subheader pointers fails.
     */
    public static @Nullable Page decodePageAt(DatasetBdat aDataset, byte[] aPageBuffer,
            long aStartByte)
        throws IOException
    {
        PageHeader pageHeader = aDataset.getPageHeaderStruct()
                .unpackEntity(new ByteArrayInputStream(aPageBuffer));

        PageType pageType = pageHeader.getPageType();
        if (pageType == null)
        {
            LOGGER.debug("Skipping page with unknown page type id: {}", pageHeader.getPageTypeId());
            return null;
        }

        Page page = new Page(aDataset);
        page.pageBuffer = new SeekableByteArrayInputStream(aPageBuffer);
        page.startByte = aStartByte;
        page.setHeader(pageHeader);

        // For meta pages (compressed files), load subheader pointers and signatures.
        if (pageType.meta && page.getSubHeaderCount() > 0)
        {
            ParserBdat.loadPageSubHeaderPointers(aDataset, page);
        }

        // Read deleted record bitmap if this page has deleted records.
        page.readDeletedMarkers();

        return page;
    }
}
