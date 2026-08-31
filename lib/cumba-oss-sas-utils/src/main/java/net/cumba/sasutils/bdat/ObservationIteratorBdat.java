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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.cumba.sasutils.Observation;
import net.cumba.sasutils.VariableType;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.ByteOrder;
import org.thshsh.struct.Struct;
import org.thshsh.struct.TokenType;

// currentPageObservationIterator is initialised by advanceToNextNonEmptyPage() (called from the
// constructor), which always assigns it the chained iterator or EMPTY_ITERATOR — NullAway cannot
// trace that through the helper, hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class ObservationIteratorBdat implements Iterator<Observation>
{

    static final Logger LOGGER = LoggerFactory.getLogger(ObservationIteratorBdat.class);

    protected RandomAccessFileInputStream stream;

    protected DatasetBdat dataset;

    protected Iterator<Page> preParsedPageIterator;

    protected long remainingPageIndex;

    protected long totalPageCount;

    protected Iterator<Observation> currentPageObservationIterator;

    protected long currentRowIndex;

    protected long rowCount;

    public ObservationIteratorBdat(DatasetBdat dataset, RandomAccessFileInputStream stream)
    {

        this.dataset = dataset;
        this.stream = stream;
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

        advanceToNextNonEmptyPage();

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
                stream.getRandomAccessFile().seek(startByte);
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
                page.startByte = startByte;
                page.pageBuffer = new SeekableByteArrayInputStream(pageBuffer);
                page.setHeader(pageHeader);

                // For meta pages (compressed files), load subheader pointers and signatures
                if (pageType.meta && page.getSubHeaderCount() > 0)
                {
                    ParserBdat.loadPageSubHeaderPointers(dataset, page);
                }

                // Read the deleted-record bitmap so DataBlockIterator can skip deleted rows
                // (pre-parsed pages get this in ParserBdat; lazily-loaded pages need it here).
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
        return currentRowIndex < rowCount && currentPageObservationIterator.hasNext();
    }


    @Override
    public Observation next()
    {
        Observation observation = currentPageObservationIterator.next();
        currentRowIndex++;
        if (!currentPageObservationIterator.hasNext() && currentRowIndex < rowCount)
        {
            advanceToNextNonEmptyPage();
        }
        return observation;
    }


    /**
     * Loads observation pages until one yields at least one live (non-deleted) observation, or all
     * pages are exhausted. A page whose rows are entirely deleted produces an empty chained
     * iterator (the deleted rows are skipped by {@link DataBlockIterator}); without this loop such
     * a page would prematurely terminate iteration and silently drop live rows on subsequent pages.
     */
    @SuppressWarnings("unchecked")
    private void advanceToNextNonEmptyPage()
    {
        Page page = findNextObservationPage();
        while (page != null)
        {
            loadPageObservations(page);
            if (currentPageObservationIterator.hasNext())
            {
                return;
            }
            page = findNextObservationPage();
        }
        currentPageObservationIterator = IteratorUtils.EMPTY_ITERATOR;
    }


    @SuppressWarnings("unchecked")
    private void loadPageObservations(Page page)
    {
        LOGGER.debug("loadPageObservations: {}", page);
        DataSubHeaderIterator dshi = new DataSubHeaderIterator(dataset, page, stream);
        DataBlockIterator dbi = new DataBlockIterator(dataset, page, stream);
        currentPageObservationIterator = IteratorUtils.chainedIterator(dshi, dbi);
    }


    public static Observation readRowFromStream(DatasetBdat member,
            RandomAccessFileInputStream stream, Integer rowDataLength)
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

        Observation currentObservation = new Observation();

        ByteArrayInputStream rowStream = new ByteArrayInputStream(rowBytes);

        for (VariableBdat variable : member.getVariables())
        {

            rowStream.reset();

            Object value;
            Integer length = variable.getLength();
            VariableType type = variable.getType();

            LOGGER.debug("var offset: {}", variable.attributes.getOffset());
            LOGGER.debug("length: {}", length);

            // skip to proper offset
            IOUtils.skipFully(rowStream, variable.attributes.getOffset());

            LOGGER.debug("type: {}", type);

            value = switch (type)
            {
            case CHARACTER ->
            {
                Struct<?> s = Struct.create(length + "S");
                List<Object> ob = s.unpack(rowStream);
                // Right-trim only: SAS pads char fields with trailing spaces to fixed width.
                // Stripping trailing whitespace removes the padding; leading whitespace is
                // intentional data and must be preserved.
                yield ob.get(0).toString().stripTrailing();
            }
            case NUMERIC ->
            {
                if (length == 1)
                {
                    yield Struct.unpack(TokenType.Boolean, member.getByteOrder(), rowStream);
                }
                else if (length == 2)
                {
                    yield Struct.unpack(TokenType.Short, member.getByteOrder(), rowStream);
                }
                else if (length == 8)
                {
                    yield Struct.unpack(TokenType.Double, member.getByteOrder(), rowStream);
                }
                else if (length < 8)
                {
                    byte[] src = new byte[length];
                    IOUtils.readFully(rowStream, src);
                    byte[] full = new byte[]
                    {
                            0, 0, 0, 0, 0, 0, 0, 0
                    };
                    System.arraycopy(src, 0, full,
                            member.getByteOrder() == ByteOrder.Big ? 0 : 8 - length, length);
                    yield Struct.unpack(TokenType.Double, member.getByteOrder(), full);
                }
                else
                {
                    throw new IllegalArgumentException("Numeric length is > 8");
                }
            }
            default -> throw new IllegalStateException();
            };

            LOGGER.debug("Value: {} for Variable: {}", value, variable);

            currentObservation.putValue(variable, value);

        }

        return currentObservation;
    }

    public static class DataBlockIterator implements Iterator<Observation>
    {

        private static final Logger LOGGER = LoggerFactory
                .getLogger(ObservationIteratorBdat.DataBlockIterator.class);

        protected DatasetBdat dataset;

        protected Page page;

        protected RandomAccessFileInputStream stream;

        protected Long blockCount;

        protected Long blockIndex = 0l;

        @Nullable
        Observation currentObservation;

        /** The next valid (non-deleted) observation, pre-fetched to support hasNext(). */
        private @Nullable Observation nextObservation;

        public DataBlockIterator(DatasetBdat dataset, Page page, RandomAccessFileInputStream in)
        {
            this.dataset = dataset;
            this.page = page;
            this.stream = in;
            this.blockCount = page.getBlockObservationCount();

            // Pre-fetch the first valid observation (skipping any leading deleted rows).
            nextObservation = fetchNextValidObservation();
        }


        @Override
        public boolean hasNext()
        {
            return nextObservation != null;
        }


        /**
         * Advances blockIndex, skipping deleted records (per the page's deletion bitmap), and reads
         * the next valid observation. Returns {@code null} when no more valid records exist.
         */
        @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
        private @Nullable Observation fetchNextValidObservation()
        {
            try
            {
                while (blockIndex < blockCount)
                {
                    long currentIndex = blockIndex;
                    blockIndex++;

                    if (page.isBlockRowDeleted(currentIndex))
                    {
                        LOGGER.debug("skipping deleted block: {}", currentIndex);
                        continue;
                    }

                    long dataAreaOffset = page.getDataAreaOffset();
                    stream.getRandomAccessFile().seek(page.startByte + dataAreaOffset
                            + (dataset.rowSizeSubHeader.getRowLength().intValue() * currentIndex));

                    LOGGER.debug("next block: {}", currentIndex);

                    return readRowFromStream(dataset, stream,
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
        public Observation next()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }
            // hasNext() above guarantees nextObservation is non-null.
            currentObservation = java.util.Objects.requireNonNull(nextObservation);
            Observation result = currentObservation;
            nextObservation = fetchNextValidObservation();
            return result;
        }

    }


    public static class DataSubHeaderIterator implements Iterator<Observation>
    {

        private static final Logger LOGGER = LoggerFactory
                .getLogger(ObservationIteratorBdat.DataSubHeaderIterator.class);

        protected DatasetBdat dataset;

        protected Page page;

        protected RandomAccessFileInputStream stream;

        protected Iterator<SubHeaderPointer> dataSubHeadersIterator;

        public DataSubHeaderIterator(DatasetBdat dataset, Page page, RandomAccessFileInputStream in)
        {
            this.dataset = dataset;
            this.page = page;
            this.stream = in;
            this.dataSubHeadersIterator = page.getDataSubHeaderPointers().iterator();
        }


        @Override
        public boolean hasNext()
        {
            // as long as there are more data subheaders then we have more observations
            return dataSubHeadersIterator.hasNext();
        }


        @Override
        public Observation next()
        {

            try
            {
                SubHeaderPointer pointer = dataSubHeadersIterator.next();
                stream.getRandomAccessFile().seek(page.startByte + pointer.getPageOffset());

                LOGGER.debug("reading row from subheader: {}", pointer);

                return readRowFromStream(dataset, stream, Math.toIntExact(pointer.getLength()));

            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }

        }

    }
}
