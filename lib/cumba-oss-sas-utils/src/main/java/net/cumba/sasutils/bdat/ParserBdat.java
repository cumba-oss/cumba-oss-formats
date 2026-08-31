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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import net.cumba.sasutils.Parser;
import net.cumba.sasutils.PositionAwareInputStream;
import net.cumba.sasutils.bdat.x32.ColumnAttributes32;
import net.cumba.sasutils.bdat.x32.ColumnSizeSubHeader32;
import net.cumba.sasutils.bdat.x32.FormatAndLabelSubHeader32;
import net.cumba.sasutils.bdat.x32.Header332;
import net.cumba.sasutils.bdat.x32.PageHeader32;
import net.cumba.sasutils.bdat.x32.RowSizeSubHeader32;
import net.cumba.sasutils.bdat.x64.ColumnAttributes64;
import net.cumba.sasutils.bdat.x64.ColumnSizeSubHeader64;
import net.cumba.sasutils.bdat.x64.FormatAndLabelSubHeader64;
import net.cumba.sasutils.bdat.x64.Header364;
import net.cumba.sasutils.bdat.x64.PageHeader64;
import net.cumba.sasutils.bdat.x64.RowSizeSubHeader64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.apache.commons.lang3.NotImplementedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.Struct;
import org.thshsh.struct.TokenType;

public class ParserBdat implements Parser
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserBdat.class);

    public static final String STANDARD_EXTENSION = "sas7bdat";

    @Override
    public LibraryBdat parseLibrary(File f) throws IOException
    {

        LOGGER.debug("parseLibrary: {}", f);

        LibraryBdat lib = new LibraryBdat(f);

        if (f.isDirectory())
        {
            File[] childs = f.listFiles();
            if (childs != null)
            {
                for (File child : childs)
                {
                    if (!child.isDirectory() && child.getName().endsWith(STANDARD_EXTENSION))
                    {
                        DatasetBdat header = parseDataset(lib, child);
                        lib.datasets.add(header);
                    }
                }
            }
        }
        else
        {
            DatasetBdat header = parseDataset(lib, f);
            lib.datasets.add(header);
        }

        return lib;

    }


    public DatasetBdat parseDataset(File file) throws IOException
    {
        LibraryBdat lib = new LibraryBdat(file);
        try (InputStream in = new FileInputStream(file))
        {
            return parseDataset(lib, file, in);
        }
    }


    public DatasetBdat parseDataset(LibraryBdat lib, File file) throws IOException
    {
        try (InputStream in = new FileInputStream(file))
        {
            return parseDataset(lib, file, in);
        }
    }


    public DatasetBdat parseDataset(InputStream aStream) throws IOException
    {
        return parseDataset(null, null, aStream);
    }


    public DatasetBdat parseDataset(LibraryBdat lib, InputStream aStream) throws IOException
    {
        return parseDataset(lib, null, aStream);
    }


    public DatasetBdat parseDataset(@Nullable LibraryBdat lib, @Nullable File file,
            InputStream aStream)
        throws IOException
    {
        if (aStream instanceof PositionAwareInputStream pais)
        {
            return parseDatasetImpl(lib, file, pais);
        }
        try (PositionAwareInputStream pais = new PositionAwareInputStream(aStream))
        {
            return parseDatasetImpl(lib, file, pais);
        }
    }


    private DatasetBdat parseDatasetImpl(@Nullable LibraryBdat lib, @Nullable File file,
            PositionAwareInputStream aStream)
        throws IOException
    {
        DatasetBdat dataset = new DatasetBdat(lib, file);

        dataset.setHeader1(Header1.STRUCT.unpackEntity(aStream));
        LOGGER.debug("Header1: {}", dataset.header1);

        dataset.header2 = dataset.getStruct(Header2.class).unpackEntity(aStream);
        LOGGER.debug("Header2: {}", dataset.header2);

        IOUtils.skip(aStream, dataset.header1.getHeader1Padding());

        dataset.setHeader3(
                dataset.getStruct(Header332.class, Header364.class).unpackEntity(aStream));
        LOGGER.debug("Header3: {}", dataset.header3);

        Header4 h4 = dataset.getStruct(Header4.class).unpackEntity(aStream);
        LOGGER.debug("Header4: {}", h4);

        LOGGER.debug("jumping to end of page header: from {} to {}", aStream.getPosition(),
                dataset.header3.headerSize);

        byte[] padding = (byte[]) Struct.unpack(TokenType.Bytes,
                (int) (dataset.header3.headerSize - aStream.getPosition()), dataset.getByteOrder(),
                Charset.defaultCharset(), aStream);

        LOGGER.debug("Header Padding: {}", padding.length);

        for (int p = 0; p < dataset.header3.getPageCount(); p++)
        {
            long mark = aStream.getPosition();

            LOGGER.trace("page: {}", p);

            Page page = new Page(dataset);
            page.startByte = mark;

            byte[] pageBuffer = new byte[dataset.header3.pageSize];
            IOUtils.readFully(aStream, pageBuffer);

            SeekableByteArrayInputStream pageStream = new SeekableByteArrayInputStream(pageBuffer);
            page.pageBuffer = pageStream;

            dataset.getPages().add(page);
            LOGGER.trace("page start: {}", page.startByte);
            LOGGER.trace("page end: {}", page.startByte + dataset.header3.pageSize);

            LOGGER.trace("potential sub headers: {}", dataset.header3.pageSize);

            {
                PageHeader pageHeader = dataset.getStruct(PageHeader32.class, PageHeader64.class)
                        .unpackEntity(pageStream);

                page.setHeader(pageHeader);

                LOGGER.trace("PageHeader: {}", pageHeader);
                LOGGER.trace("Page: {}", page);

                // Read the page type ONCE. Null-checking one call and dereferencing a
                // second is what SpotBugs flags as
                // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE: nothing guarantees the later
                // calls return the same non-null value, so the check above protected
                // nothing. parseSubHeaders below already uses this idiom.
                PageType pageType = page.getPageType();
                if (pageType == null)
                {
                    LOGGER.debug("Skipping page {} with unknown page type id: {}", p,
                            pageHeader.getPageTypeId());
                    continue;
                }

                if (pageType.data && !pageType.meta)
                {
                    // Pure data page (not mixed) — no metadata here, but keep it in
                    // the pre-parsed list so the iterator doesn't need to seek backward.
                    dataset.metadataPageCount = (long) p + 1;
                    break;
                }

                if (pageType.meta)
                {

                    if (page.getSubHeaderCount() > 0)
                    {
                        LOGGER.trace("reading {} page sub header pointers",
                                page.getSubHeaderCount());

                        for (int i = 0; i < page.getSubHeaderCount(); i++)
                        {
                            page.getSubHeaderPointers()
                                    .add(processSubHeaderPointer(dataset, pageStream));
                        }
                    }

                    LOGGER.trace("current position: {}", pageStream.getPosition());
                    LOGGER.trace("page.startByte: {}", page.startByte);

                    // process subheaderpointers
                    for (SubHeaderPointer pointer : page.getSubHeaderPointers())
                    {

                        long seekTo = pointer.getPageOffset().longValue();

                        LOGGER.trace("Processing subheader POINTER: {}", pointer);

                        LOGGER.trace("position: {} seekTo: {} length: {}", pageStream.getPosition(),
                                seekTo, pointer.getLength() + seekTo);

                        pageStream.seek(seekTo);

                        // unpack signature first so we can select the correct struct to unpack
                        Number signature = (Number) Struct.unpack(
                                dataset.header1.getIntegerTokenType(), dataset.getByteOrder(),
                                pageStream);

                        SubHeaderSignature type = SubHeaderSignature.fromId(signature.longValue());

                        if (type == null && dataset.getCompressed()
                                && (pointer.getCompressionType() == CompressionType.COMPRESSED
                                        || (pointer.getCompressionType() == CompressionType.NONE
                                                && pointer.getLength().longValue() == dataset
                                                        .getRowLength()))
                                && pointer.getCategory() == SubHeaderCategory.B)
                        {
                            // There are data sub headers in compressed files:
                            // compressionType Compressed = compressed row (length < rowLength)
                            // compressionType None + length == rowLength = uncompressed row
                            type = SubHeaderSignature.DATA;
                        }

                        pointer.setSignature(type);

                        if (pointer.getCompressionType() == CompressionType.TRUNCATED)
                        {
                            LOGGER.trace("Skipping Truncated SubHeader pointer: {}", pointer);
                        }
                        else
                        {

                            LOGGER.trace("Signature: {} = {}", signature, type);

                            if (type == null)
                            {
                                LOGGER.debug("Skipping subheader with unknown signature: {}",
                                        signature);
                                continue;
                            }

                            if (type != SubHeaderSignature.DATA)
                            {
                                // type is non-null here (the null case continues above) and equals
                                // the signature just set on the pointer.
                                switch (type)
                                {
                                case COLUMN_ATTRIBUTES -> processColumnAttributesSubHeader(dataset,
                                        page, pointer, pageStream);
                                case COLUMN_NAME -> processColumnNameSubHeader(dataset, page,
                                        pointer, pageStream);
                                case STRING -> processTextSubHeader(dataset, page, pointer,
                                        pageStream);
                                case COLUMN_SIZE -> processColumnCountSubHeader(dataset, page,
                                        pointer, pageStream);
                                case ROW_SIZE -> processRowSizeSubHeader(dataset, page, pointer,
                                        pageStream);
                                case FORMAT_AND_LABEL -> processFormatAndLabelSubHeader(dataset,
                                        page, pointer, pageStream);
                                case SUB_HEADER_COUNT, COLUMN_LIST ->
                                {
                                    /* no-op */ }
                                default ->
                                {
                                    /* no-op */ }
                                }
                            }

                        }

                    }

                    LOGGER.trace("done with sub header pointers");
                    LOGGER.trace("compression: {}", dataset.getCompression());

                }

                // Read deleted record bitmap for pages with deleted records (Mixed2, Data2)
                page.readDeletedMarkers();

                // Stop at first page containing observation data — remaining pages loaded
                // lazily
                if (page.getTotalObservationCount() > 0)
                {
                    dataset.metadataPageCount = (long) p + 1;
                    break;
                }
            }

        }

        // If all pages were meta (e.g. compressed files), metadataPageCount was never set
        if (dataset.metadataPageCount < 0)
        {
            dataset.metadataPageCount = dataset.header3.getPageCount();
        }

        LOGGER.debug("dataset strings: comp: {} soft: {} proc: {}", dataset.getCompression(),
                dataset.getDataSetLabel(), dataset.getCreatorProcess());

        //
        if (dataset.columnSizeSubHeader.getNumColumns().intValue() != dataset.rowSizeSubHeader
                .getColumnCount())
        {
            throw new IllegalArgumentException("Column Count Mismatch");
        }

        return dataset;
    }


    /**
     * Reads subheader pointers and their signatures for a page without processing metadata
     * subheaders. Used for lazily loading pages during observation iteration where only data
     * subheader pointers are needed.
     */
    protected static void loadPageSubHeaderPointers(DatasetBdat dataset, Page page)
        throws IOException
    {
        PageType pageType = page.getPageType();
        if (pageType == null || !pageType.meta || page.getSubHeaderCount() <= 0)
        {
            return;
        }

        // Seek past the page header to the subheader pointer area
        long pointerStart = dataset.getPageHeaderStruct().byteCount();
        page.pageBuffer.seek(pointerStart);

        // Read all subheader pointers
        for (int i = 0; i < page.getSubHeaderCount(); i++)
        {
            SubHeaderPointer pointer = dataset.getSubHeaderPointerStruct()
                    .unpackEntity(page.pageBuffer);
            page.getSubHeaderPointers().add(pointer);
        }

        // Determine the signature for each pointer
        for (SubHeaderPointer pointer : page.getSubHeaderPointers())
        {
            if (pointer.getCompressionType() == CompressionType.TRUNCATED)
            {
                continue;
            }

            long seekTo = pointer.getPageOffset().longValue();
            page.pageBuffer.seek(seekTo);

            Number signature = (Number) Struct.unpack(dataset.header1.getIntegerTokenType(),
                    dataset.getByteOrder(), page.pageBuffer);
            SubHeaderSignature type = SubHeaderSignature.fromId(signature.longValue());

            if (type == null && dataset.getCompressed()
                    && (pointer.getCompressionType() == CompressionType.COMPRESSED
                            || (pointer.getCompressionType() == CompressionType.NONE
                                    && pointer.getLength().longValue() == dataset.getRowLength()))
                    && pointer.getCategory() == SubHeaderCategory.B)
            {
                type = SubHeaderSignature.DATA;
            }

            pointer.setSignature(type);
        }
    }


    protected SubHeaderPointer processSubHeaderPointer(DatasetBdat dataset, InputStream stream)
        throws IOException
    {
        // python process_subheader_pointers
        SubHeaderPointer subHeaderPointer = dataset.getSubHeaderPointerStruct()
                .unpackEntity(stream);
        LOGGER.trace("subHeaderPointer: {}", subHeaderPointer);
        return subHeaderPointer;
    }


    protected static void processRowSizeSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, InputStream stream)
        throws IOException
    {

        RowSizeSubHeader rowSize = dataset
                .getStruct(RowSizeSubHeader32.class, RowSizeSubHeader64.class).unpackEntity(stream);
        LOGGER.debug("rowSize: {}", rowSize);

        pointer.subHeader = rowSize;
        dataset.rowSizeSubHeader = rowSize;

    }


    protected static void processColumnCountSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, InputStream stream)
        throws IOException
    {
        dataset.columnSizeSubHeader = dataset
                .getStruct(ColumnSizeSubHeader32.class, ColumnSizeSubHeader64.class)
                .unpackEntity(stream);

        LOGGER.debug("ColumnSizeSubHeader: {}", dataset.columnSizeSubHeader);

    }


    protected static void processTextSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, InputStream stream)
        throws IOException
    {

        TextSubHeader subHeader = dataset.getStruct(TextSubHeader.class).unpackEntity(stream);

        LOGGER.debug("TextSubHeader: {}", subHeader);
        pointer.subHeader = subHeader;
        // Use the larger of the remainder field and the actual remaining bytes.
        // Real SAS files use remainder = len - (4 + 2*sigSize) which may be smaller
        // than the full content after the Short. Using the pointer length ensures
        // all text data is accessible regardless of the remainder value.
        int sigSize = dataset.header1.getIntegerLength();
        int fullRemaining = Math.toIntExact(pointer.getLength()) - sigSize - 2;
        int stringLen = Math.max(Math.toIntExact(subHeader.getLength()), fullRemaining);
        subHeader.string = (String) Struct.unpack(TokenType.String, stringLen,
                StandardCharsets.US_ASCII, stream);
        LOGGER.debug("string: {}", subHeader.string);

    }


    protected static void processColumnNameSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, InputStream stream)
        throws IOException
    {

        Struct<ColumnNamesSubHeader> struct = dataset.getStruct(ColumnNamesSubHeader.class);
        Struct<ColumnName> columnNameStruct = dataset.getStruct(ColumnName.class);

        ColumnNamesSubHeader subHeader = struct.unpackEntity(stream);
        dataset.setColumnNamesSubHeader(subHeader);
        subHeader.setPointer(pointer);

        LOGGER.debug("ColumnNamesSubHeader: {}", subHeader);
        LOGGER.debug("header length: {}", pointer.getLength());
        LOGGER.debug("subHeader.remainingLength: {}", subHeader.remainingLength);

        int count1 = (subHeader.remainingLength - 8) / columnNameStruct.byteCount();

        LOGGER.debug("count1: {}", count1);

        int count = subHeader.getNumColumnNames();
        LOGGER.debug("column name count: {}", count);

        for (int i = 0; i < count; i++)
        {
            ColumnName cns = columnNameStruct.unpackEntity(stream);
            cns.dataset = dataset;
            LOGGER.debug("ColumnName: {}", cns);
            subHeader.getColumnNames().add(cns);

        }

    }


    protected static void processColumnAttributesSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, InputStream stream)
        throws IOException
    {

        LOGGER.debug("processColumnAttributesSubHeader");

        Struct<ColumnAttributes> columnAttributesStruct = dataset
                .getStruct(ColumnAttributes32.class, ColumnAttributes64.class);
        ColumnAttributesSubHeader subHeader = dataset.getStruct(ColumnAttributesSubHeader.class)
                .unpackEntity(stream);
        dataset.setColumnAttributesSubHeader(subHeader);
        subHeader.setPointer(pointer);

        LOGGER.debug("ColumnAttributesSubHeader {}", subHeader);

        int count = (subHeader.remainingLength - 8) / columnAttributesStruct.byteCount();

        LOGGER.debug("count: {}", count);

        for (int i = 0; i < count; i++)
        {
            ColumnAttributes ca = columnAttributesStruct.unpackEntity(stream);
            LOGGER.debug("ColumnAttributes: {}", ca);
            subHeader.getColumnAttributes().add(ca);
        }
    }


    protected static void processFormatAndLabelSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, InputStream stream)
        throws IOException
    {

        LOGGER.debug("processFormatAndLabelSubHeader");

        Struct<FormatAndLabelSubHeader> s = dataset.getStruct(FormatAndLabelSubHeader32.class,
                FormatAndLabelSubHeader64.class);

        FormatAndLabelSubHeader fal = s.unpackEntity(stream);
        fal.dataset = dataset;
        pointer.subHeader = fal;

        dataset.formatAndLabels.add(fal);

        LOGGER.debug("FormatAndLabelSubHeader: {}", fal);
        LOGGER.debug("format: {}", fal.getFormat());
        LOGGER.debug("label: {}", fal.getLabel());

    }


    protected static void processDataSubHeader(DatasetBdat dataset, Page page,
            SubHeaderPointer pointer, RandomAccessFileInputStream stream)
        throws IOException
    {

        LOGGER.debug("processDataSubHeader");

        if (dataset.getCompressed())
        {
            // TODO
            throw new NotImplementedException();
        }

        // skip 24 bytes
        IOUtils.skipFully(stream, 24);

    }

}
