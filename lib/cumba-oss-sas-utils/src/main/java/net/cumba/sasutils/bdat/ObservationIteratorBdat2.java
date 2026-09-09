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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.cumba.sasutils.PositionAwareInputStream;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sequential iterator over the raw (decompressed) observation rows of a BDAT dataset.
 *
 * <p>
 * Page sequencing is delegated to {@link BdatPageProducer} and per-page row extraction to
 * {@link PageDataExtractor}, so the sequential path and the parallel provider pipeline share one
 * source of truth and produce byte-identical rows in the same order. Rows are buffered one page at
 * a time.
 * </p>
 */
public class ObservationIteratorBdat2 implements Iterator<byte[]>
{

    static final Logger LOGGER = LoggerFactory.getLogger(ObservationIteratorBdat2.class);

    protected DatasetBdat dataset;

    private final BdatPageProducer pageProducer;

    private final boolean compressed;

    // null when the dataset is uncompressed; only consulted on the compressed path.
    private final @Nullable CompressionAlgorithm compressionAlgorithm;

    private Iterator<byte[]> currentPageRows = Collections.emptyIterator();

    protected long parsedDeletedRowCount;

    @SuppressWarnings("this-escape")
    public ObservationIteratorBdat2(DatasetBdat dataset, InputStream stream)
    {
        this.dataset = dataset;
        // Resolve compression once up front (not per row): the derivation walks the dataset's
        // pages/subheaders and is constant for the whole dataset.
        this.compressed = dataset.getCompressed();
        this.compressionAlgorithm = dataset.getCompressionAlgorithm();
        PositionAwareInputStream pais = stream instanceof PositionAwareInputStream existing
                ? existing
                : new PositionAwareInputStream(stream);
        this.pageProducer = new BdatPageProducer(dataset, pais);
        advanceToNextNonEmptyPage();
    }


    public long getParsedDeletedRowCount()
    {
        return parsedDeletedRowCount;
    }


    /**
     * Extract pages until one yields at least one row (accumulating each page's deleted-row count),
     * or the producer is exhausted.
     */
    private void advanceToNextNonEmptyPage()
    {
        while (pageProducer.hasNext())
        {
            Page page = pageProducer.next();
            try
            {
                PageDataExtractor.PageRows pageRows = PageDataExtractor.extractRows(dataset, page,
                        compressed, compressionAlgorithm);
                parsedDeletedRowCount += pageRows.deletedCount();
                if (!pageRows.rows().isEmpty())
                {
                    currentPageRows = pageRows.rows().iterator();
                    return;
                }
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        currentPageRows = Collections.emptyIterator();
    }


    @Override
    public boolean hasNext()
    {
        return currentPageRows.hasNext();
    }


    @Override
    public byte[] next()
    {
        if (!currentPageRows.hasNext())
        {
            throw new NoSuchElementException();
        }
        byte[] observation = currentPageRows.next();
        if (!currentPageRows.hasNext())
        {
            advanceToNextNonEmptyPage();
        }
        return observation;
    }


    public static byte[] readRowFromStream(DatasetBdat member, InputStream stream,
            Integer rowDataLength)
        throws IOException
    {
        return readRowFromStream(member, stream, rowDataLength, member.getCompressed(),
                member.getCompressionAlgorithm());
    }


    /**
     * Read (and decompress if needed) one row, using a <b>pre-resolved</b> compression flag and
     * algorithm. Callers in the parallel pipeline must resolve {@code aCompressed} /
     * {@code aAlgorithm} once on the calling thread (via {@link DatasetBdat#getCompressed()} /
     * {@link DatasetBdat#getCompressionAlgorithm()}) and pass them in, rather than re-deriving them
     * per row off the shared dataset — the derivation walks {@code getPages()} /
     * {@code getStringSubHeaders()} and must not be invoked concurrently from worker threads.
     *
     * @param member
     *            the dataset (used only for the read-only {@link DatasetBdat#getRowLength()}).
     * @param stream
     *            the positioned row-data stream.
     * @param rowDataLength
     *            the on-disk byte length of this row (compressed length when compressed).
     * @param aCompressed
     *            whether the dataset is compressed.
     * @param aAlgorithm
     *            the compression algorithm (only consulted when {@code aCompressed}).
     * @return the decompressed row bytes ({@code member.getRowLength()} long).
     * @throws IOException
     *             on read failure.
     */
    public static byte[] readRowFromStream(DatasetBdat member, InputStream stream,
            Integer rowDataLength, boolean aCompressed, @Nullable CompressionAlgorithm aAlgorithm)
        throws IOException
    {

        byte[] rowBytes = new byte[rowDataLength];
        IOUtils.readFully(stream, rowBytes);

        if (rowDataLength < member.getRowLength())
        {
            if (aCompressed)
            {
                // this data is compressed
                if (aAlgorithm == null)
                {
                    throw new IOException("Unsupported or missing compression algorithm");
                }
                Compressor compressor = switch (aAlgorithm)
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
}
