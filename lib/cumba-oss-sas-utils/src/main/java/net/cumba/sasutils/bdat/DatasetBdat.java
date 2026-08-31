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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.cumba.sasutils.Dataset;
import net.cumba.sasutils.Observation;
import net.cumba.sasutils.Variable;
import net.cumba.sasutils.bdat.x32.PageHeader32;
import net.cumba.sasutils.bdat.x32.SubHeaderPointer32;
import net.cumba.sasutils.bdat.x64.PageHeader64;
import net.cumba.sasutils.bdat.x64.SubHeaderPointer64;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.ByteOrder;
import org.thshsh.struct.Struct;

// Header/sub-header fields (header1..4, rowSizeSubHeader, columnNamesSubHeader, …) are populated by
// the parser after construction via setters, and dereferences are guarded by null checks or an
// IllegalStateException ("metadata not yet parsed"), so the constructor does not initialise them.
@SuppressWarnings("NullAway.Init")
public class DatasetBdat extends Dataset
{

    protected static final Logger LOGGER = LoggerFactory.getLogger(DatasetBdat.class);

    public static final Charset METADATA_CHARSET = StandardCharsets.UTF_8;

    static final int COMPRESSION_TRUNCATED = 1;

    static final int TRUNCATED_SUBHEADER_ID = 1;

    List<VariableBdat> variables;

    List<Page> pages = new ArrayList<>();

    Header1 header1;

    Header2 header2;

    Header3 header3;

    Header4 header4;

    protected RowSizeSubHeader rowSizeSubHeader;

    protected ColumnNamesSubHeader columnNamesSubHeader;

    protected ColumnAttributesSubHeader columnAttributesSubHeader;

    protected ColumnSizeSubHeader columnSizeSubHeader;

    List<FormatAndLabelSubHeader> formatAndLabels = new ArrayList<>();

    protected long metadataPageCount = -1;

    protected @Nullable File file;

    public DatasetBdat()
    {
        super(null);
    }


    public DatasetBdat(@Nullable LibraryBdat lib)
    {
        super(lib);
    }


    public DatasetBdat(@Nullable LibraryBdat lib, @Nullable File file)
    {
        super(lib);
        this.file = file;
    }


    public long getMetadataPageCount()
    {
        return metadataPageCount;
    }


    public Long getColumnCount()
    {
        if (rowSizeSubHeader == null)
        {
            throw new IllegalStateException("Dataset metadata not yet parsed");
        }
        return rowSizeSubHeader.getColumnCount();
    }


    public void setColumnNamesSubHeader(ColumnNamesSubHeader columnNamesSubHeader)
    {
        this.columnNamesSubHeader = columnNamesSubHeader;
        columnNamesSubHeader.setDataset(this);
    }


    public void setColumnAttributesSubHeader(ColumnAttributesSubHeader columnAttributesSubHeader)
    {
        this.columnAttributesSubHeader = columnAttributesSubHeader;
        columnAttributesSubHeader.setDataset(this);
    }


    public void setColumnSizeSubHeader(ColumnSizeSubHeader columnSizeSubHeader)
    {
        this.columnSizeSubHeader = columnSizeSubHeader;
        columnSizeSubHeader.setDataset(this);
    }


    public Long getRowLength()
    {
        if (rowSizeSubHeader == null)
        {
            throw new IllegalStateException("Dataset metadata not yet parsed");
        }
        return rowSizeSubHeader.getRowLength();
    }


    @Override
    public Long getRowCount()
    {
        if (rowSizeSubHeader == null)
        {
            throw new IllegalStateException("Dataset metadata not yet parsed");
        }
        return rowSizeSubHeader.getRowCount();
    }


    public Long getPageCount()
    {
        return rowSizeSubHeader.getPageCount();
    }


    public Boolean getCompressed()
    {
        return rowSizeSubHeader.getCompressed();
    }


    public Long getObservationCount()
    {
        return rowSizeSubHeader.getRowCount();
    }


    public Long getVariableCount()
    {
        return rowSizeSubHeader.getColumnCount();
    }


    public Long getDeletedObservationCount()
    {
        return rowSizeSubHeader.getDeletedRowCount();
    }


    @Override
    public List<VariableBdat> getVariables()
    {
        if (variables == null)
        {
            variables = new ArrayList<>();
            Iterator<FormatAndLabelSubHeader> fals = getFormatAndLabelSubHeaders().iterator();
            Iterator<ColumnName> names = columnNamesSubHeader.getColumnNames().iterator();
            Iterator<ColumnAttributes> atts = columnAttributesSubHeader.columnAttributes.iterator();

            long colCount = getVariableCount();
            for (int i = 0; i < colCount; i++)
            {
                VariableBdat v = new VariableBdat(fals.next(), names.next(), atts.next());
                variables.add(v);
            }
        }
        return variables;
    }


    public Stream<TextSubHeader> getStringSubHeaders()
    {
        return getPages().stream().map(Page::getSubHeaderPointers).flatMap(l -> l.stream())
                .filter(p -> p.getSignature() == SubHeaderSignature.STRING)
                .map(p -> (TextSubHeader) p.getSubHeader());
    }


    public Stream<FormatAndLabelSubHeader> getFormatAndLabelSubHeaders()
    {
        return getPages().stream().map(Page::getSubHeaderPointers).flatMap(l -> l.stream())
                .filter(p -> p.getSignature() == SubHeaderSignature.FORMAT_AND_LABEL)
                .map(p -> (FormatAndLabelSubHeader) p.getSubHeader());
    }


    public Optional<String> getColumnName(ColumnName sh)
    {
        return getSubHeaderString(sh.index, sh.start, sh.length);
    }


    public Optional<String> getFormatName(FormatAndLabelSubHeader sh)
    {
        if (sh.formatLength == 0)
        {
            return Optional.empty();
        }
        return getSubHeaderString(sh.formatIndex, sh.formatOffset, sh.formatLength);
    }


    public Optional<String> getLabel(FormatAndLabelSubHeader sh)
    {
        if (sh.labelLength == 0)
        {
            return Optional.empty();
        }
        return getSubHeaderString(sh.labelIndex, sh.labelOffset, sh.labelLength);
    }


    public Optional<String> getSubHeaderString(int index, int start, int length)
    {
        return getSubHeaderString(index, start, length, true);
    }


    public Optional<String> getSubHeaderString(int index, int start, int length, boolean trim)
    {
        if (length == 0)
        {
            return Optional.empty();
        }
        LOGGER.trace("getSubHeaderString index: {} start: {} length: {}", index, start, length);
        return getStringSubHeaders().skip(index).map(h ->
        {
            LOGGER.trace("string: {}", h.string);
            String s = h.getSubString(start, length);
            // Right-trim only: SAS pads metadata strings (names, labels, formats) on the
            // trailing side. Leading whitespace, however unusual, is intentional and must be
            // preserved (mirrors the record-value fix in ObservationIteratorBdat).
            return trim ? s.stripTrailing() : s;
        }).findFirst();
    }


    public void setHeader1(Header1 h1)
    {
        header1 = h1;
    }


    public ByteOrder getByteOrder()
    {
        return header1.littleEndian ? ByteOrder.Little : ByteOrder.Big;
    }


    public Platform getPlatform()
    {
        return switch (header2.platform)
        {
        case "1" -> Platform.UNIX;
        case "2" -> Platform.WINDOWS;
        default -> Platform.UNKNOWN;
        };
    }


    public Optional<String> getCompression()
    {
        return getSubHeaderString(0, rowSizeSubHeader.getCompressionMethodOffset(),
                rowSizeSubHeader.getCompressionMethodLength());
    }


    public @Nullable CompressionAlgorithm getCompressionAlgorithm()
    {
        Optional<String> comp = getCompression();
        if (!comp.isPresent())
        {
            return null;
        }
        else
        {
            try
            {
                return CompressionAlgorithm.valueOf(comp.get());
            }
            catch (IllegalArgumentException _)
            {
                return null;
            }
        }
    }


    public Optional<String> getDataSetLabel()
    {
        return getSubHeaderString(0, rowSizeSubHeader.getLabelOffset(),
                rowSizeSubHeader.getLabelLength());
    }


    public Optional<String> getCreatorProcess()
    {
        return getSubHeaderString(0, rowSizeSubHeader.getCreatorProcOffset(),
                rowSizeSubHeader.getCreatorProcLength());
    }


    public Optional<String> getCreatorSoftware()
    {
        return getSubHeaderString(0, rowSizeSubHeader.getCreatorSoftwareOffset(),
                rowSizeSubHeader.getCreatorSoftwareLength());
    }


    public List<Page> getPages()
    {
        return pages;
    }


    public void setPages(List<Page> aPages)
    {
        pages = (aPages != null) ? aPages : new ArrayList<>();
    }


    public void setHeader3(Header3 h2)
    {
        this.header3 = h2;
    }


    public void setHeader4(Header4 h3)
    {
        this.header4 = h3;
    }


    @Override
    public String getName()
    {
        return header2.getDatasetName();
    }


    @Override
    public void setName(String name)
    {
        header2.setDatasetName(name);
    }


    public Boolean get64Bit()
    {
        return header1.get64Bit();
    }


    public Header1 getHeader1()
    {
        return header1;
    }


    public Header2 getHeader2()
    {
        return header2;
    }


    public Header3 getHeader3()
    {
        return header3;
    }


    public Header4 getHeader4()
    {
        return header4;
    }


    @Override
    public LocalDateTime getCreated()
    {
        return header3.getCreated();
    }


    @Override
    public LocalDateTime getModified()
    {
        return header3.getModified();
    }


    @Override
    public @Nullable String getType()
    {
        return null;
    }


    @Override
    public void setType(String type)
    {
        // No-op: BDAT files have no dataset-type field; the abstract setter is satisfied but
        // unused.
    }


    @SuppressWarnings("unchecked")
    @Override
    public void setVariables(List<? extends Variable> variables)
    {
        this.variables = (List<VariableBdat>) variables;
    }


    @Override
    protected Stream<Observation> createObservationStream(RandomAccessFileInputStream file)
    {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                new ObservationIteratorBdat(this, file), Spliterator.NONNULL), false);
    }


    @Override
    public @Nullable LibraryBdat getLibrary()
    {
        return (LibraryBdat) super.getLibrary();
    }


    public Struct<? extends PageHeader> getPageHeaderStruct()
    {
        return getStruct(PageHeader32.class, PageHeader64.class);
    }


    public Struct<? extends SubHeaderPointer> getSubHeaderPointerStruct()
    {
        return getStruct(SubHeaderPointer32.class, SubHeaderPointer64.class);
    }


    public <T> Struct<T> getStruct(Class<? extends T> classs)
    {
        return getStruct(classs, null);
    }


    @SuppressWarnings("unchecked")
    public <T> Struct<T> getStruct(Class<? extends T> classs, @Nullable Class<? extends T> class64)
    {
        Class<? extends T> theClass = class64 != null && header1.get64Bit() ? class64 : classs;
        return (Struct<T>) Struct.create(theClass).byteOrder(getByteOrder());
    }

}
