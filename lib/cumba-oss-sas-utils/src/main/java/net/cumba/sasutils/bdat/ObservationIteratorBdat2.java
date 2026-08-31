/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.bdat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.cumba.sasutils.PositionAwareInputStream;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// currentPageObservationIterator is assigned in the constructor (loadPageObservations or
// EMPTY_ITERATOR) on every path; NullAway cannot trace it through loadPageObservations, hence the
// Init suppression.
@SuppressWarnings("NullAway.Init")
public class ObservationIteratorBdat2 implements Iterator<byte[]>
{

    static final Logger LOGGER = LoggerFactory.getLogger(ObservationIteratorBdat2.class);

    protected PositionAwareInputStream stream;

    protected DatasetBdat dataset;

    protected Iterator<Page> preParsedPageIterator;

    protected long remainingPageIndex;

    protected long totalPageCount;

    protected Iterator<byte[]> currentPageObservationIterator;

    protected long currentRowIndex;

    protected long rowCount;

    protected long parsedDeletedRowCount;

    @SuppressWarnings(
    {
            "unchecked", "this-escape"
    })
    public ObservationIteratorBdat2(DatasetBdat dataset, InputStream stream)
    {

        this.dataset = dataset;
        if (stream instanceof PositionAwareInputStream pais)
        {
            this.stream = pais;
        }
        else
        {
            this.stream = new PositionAwareInputStream(stream);
        }
        // getRowCount() returns a boxed Long and rowCount is a primitive long, so a
        // null here becomes an NPE on unboxing with no useful message. Fail with one
        // that says what is actually wrong.
        Long datasetRowCount = dataset.getRowCount();
        if (datasetRowCount == null)
        {
            throw new IllegalStateException(
                    "Dataset row count is unavailable; metadata may be incomplete");
        }
        this.rowCount = datasetRowCount;

        // Pre-parsed pages (meta/mixed with observations) from the parser
        this.preParsedPageIterator = dataset.getPages().stream()
                .filter(p -> p.getTotalObservationCount() > 0).iterator();
        // Remaining pages are loaded lazily one at a time during iteration
        this.remainingPageIndex = dataset.getMetadataPageCount();
        this.totalPageCount = dataset.header3.getPageCount();

        Page firstPage = findNextObservationPage();
        if (firstPage != null)
        {
            loadPageObservations(firstPage);
        }
        else
        {
            currentPageObservationIterator = IteratorUtils.EMPTY_ITERATOR;
        }

    }


    public long getParsedDeletedRowCount()
    {
        return parsedDeletedRowCount;
    }


    /**
     * Finds the next page with observation data: first from pre-parsed pages, then by lazily
     * reading remaining pages from the file one at a time.
     */
    private @Nullable Page findNextObservationPage()
    {
        // First, yield pre-parsed pages that have observations
        if (preParsedPageIterator.hasNext())
        {
            return preParsedPageIterator.next();
        }

        // Then, lazily load remaining pages from the file
        try
        {
            while (remainingPageIndex < totalPageCount)
            {
                long startByte = dataset.header3.headerSize
                        + (remainingPageIndex * dataset.header3.pageSize);

                stream.seek(startByte);

                byte[] pageBuffer = new byte[dataset.header3.pageSize];
                IOUtils.readFully(stream, pageBuffer);

                PageHeader pageHeader = dataset.getPageHeaderStruct()
                        .unpackEntity(new ByteArrayInputStream(pageBuffer));
                remainingPageIndex++;

                PageType pageType = pageHeader.getPageType();
                if (pageType == null)
                {
                    LOGGER.debug("Skipping remaining page with unknown page type id: {}",
                            pageHeader.getPageTypeId());
                    continue;
                }

                Page page = new Page(dataset);
                page.pageBuffer = new SeekableByteArrayInputStream(pageBuffer);
                page.startByte = startByte;
                page.setHeader(pageHeader);

                // For meta pages (compressed files), load subheader pointers and signatures
                if (pageType.meta && page.getSubHeaderCount() > 0)
                {
                    ParserBdat.loadPageSubHeaderPointers(dataset, page);
                }

                // Read deleted record bitmap if this page has deleted records
                page.readDeletedMarkers();

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


    @Override
    public boolean hasNext()
    {
        return currentPageObservationIterator.hasNext();
    }


    @Override
    public byte[] next()
    {
        byte[] observation = currentPageObservationIterator.next();
        currentRowIndex++;
        if (!currentPageObservationIterator.hasNext())
        {
            Page nextPage = findNextObservationPage();
            if (nextPage != null)
            {
                loadPageObservations(nextPage);
            }
        }
        return observation;
    }


    @SuppressWarnings("unchecked")
    private void loadPageObservations(Page page)
    {
        LOGGER.debug("loadPageObservations: {}", page);
        DataSubHeaderIterator dshi = new DataSubHeaderIterator(dataset, page);
        DataBlockIterator dbi = new DataBlockIterator(dataset, page);
        currentPageObservationIterator = IteratorUtils.chainedIterator(dshi, dbi);
    }


    public static byte[] readRowFromStream(DatasetBdat member, InputStream stream,
            Integer rowDataLength)
        throws IOException
    {

        byte[] rowBytes = new byte[rowDataLength];
        IOUtils.readFully(stream, rowBytes);

        if (rowDataLength < member.getRowLength())
        {
            if (member.getCompressed())
            {
                // this data is compressed
                CompressionAlgorithm compressionAlgorithm = member.getCompressionAlgorithm();
                if (compressionAlgorithm == null)
                {
                    throw new IOException("Unsupported or missing compression algorithm");
                }
                Compressor compressor = switch (compressionAlgorithm)
                {
                case SASYZCR2 -> new RdcCompressor();
                case SASYZCRL -> new RleCompressor();
                default -> throw new IllegalArgumentException("Compression unknown");
                };

                LOGGER.debug("decompressing data using: {}", compressor);

                rowBytes = compressor.decompressRow(Math.toIntExact(member.getRowLength()),
                        rowBytes);
            }
            else
            {
                throw new IllegalArgumentException(
                        "Row data length (" + rowDataLength + ") should equal dataset row length ("
                                + member.getRowLength() + ") for uncompressed files");
            }

        }

        return rowBytes;

    }

    public class DataBlockIterator implements Iterator<byte[]>
    {

        private static final Logger LOGGER = LoggerFactory
                .getLogger(ObservationIteratorBdat2.DataBlockIterator.class);

        protected DatasetBdat dataset;

        protected Page page;

        protected Long blockCount;

        protected Long blockIndex = 0l;

        byte @Nullable [] currentObservation;

        /**
         * The next valid (non-deleted) observation, pre-fetched to support hasNext().
         */
        private byte @Nullable [] nextObservation;

        public DataBlockIterator(DatasetBdat dataset, Page page)
        {
            this.dataset = dataset;
            this.page = page;
            this.blockCount = page.getBlockObservationCount();

            // Pre-fetch the first valid observation
            nextObservation = fetchNextValidObservation();
        }


        @Override
        public boolean hasNext()
        {
            return nextObservation != null;
        }


        /**
         * Advances blockIndex, skipping deleted records, and reads the next valid observation.
         * Returns null if no more valid records exist.
         */
        @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
        private byte @Nullable [] fetchNextValidObservation()
        {
            try
            {
                while (blockIndex < blockCount)
                {
                    long currentIndex = blockIndex;
                    blockIndex++;

                    if (page.isBlockRowDeleted(currentIndex))
                    {
                        parsedDeletedRowCount++;
                        LOGGER.debug("skipping deleted block: {}", currentIndex);
                        continue;
                    }

                    long dataAreaOffset = page.getDataAreaOffset();
                    long offset = dataAreaOffset
                            + (dataset.rowSizeSubHeader.getRowLength().intValue() * currentIndex);

                    page.pageBuffer.seek(offset);

                    LOGGER.debug("next block: {}", currentIndex);

                    return readRowFromStream(dataset, page.pageBuffer,
                            Math.toIntExact(dataset.getRowLength()));
                }
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
            return null;
        }


        @Override
        public byte[] next()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }
            // hasNext() above guarantees nextObservation is non-null.
            currentObservation = java.util.Objects.requireNonNull(nextObservation);
            byte[] result = currentObservation;
            nextObservation = fetchNextValidObservation();
            return result;
        }

    }


    public static class DataSubHeaderIterator implements Iterator<byte[]>
    {

        private static final Logger LOGGER = LoggerFactory
                .getLogger(ObservationIteratorBdat2.DataSubHeaderIterator.class);

        protected DatasetBdat dataset;

        protected Page page;

        protected Iterator<SubHeaderPointer> dataSubHeadersIterator;

        public DataSubHeaderIterator(DatasetBdat dataset, Page page)
        {
            this.dataset = dataset;
            this.page = page;
            this.dataSubHeadersIterator = page.getDataSubHeaderPointers().iterator();
        }


        @Override
        public boolean hasNext()
        {
            // as long as there are more data subheaders then we have more observations
            return dataSubHeadersIterator.hasNext();
        }


        @Override
        public byte[] next()
        {

            try
            {
                SubHeaderPointer pointer = dataSubHeadersIterator.next();

                page.pageBuffer.seek(pointer.getPageOffset());

                LOGGER.debug("reading row from subheader: {}", pointer);

                return readRowFromStream(dataset, page.pageBuffer,
                        Math.toIntExact(pointer.getLength()));

            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }

        }

    }
}
