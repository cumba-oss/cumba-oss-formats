package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.x32.ColumnAttributes32;
import net.cumba.sasutils.bdat.x32.ColumnSizeSubHeader32;
import net.cumba.sasutils.bdat.x32.PageHeader32;
import net.cumba.sasutils.bdat.x32.RowSizeSubHeader32;
import net.cumba.sasutils.bdat.x32.SubHeaderPointer32;
import net.cumba.sasutils.bdat.x64.RowSizeSubHeader64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thshsh.struct.ByteOrder;

/**
 * Pins the values the BDAT value and accessor classes report for real SAS-written fixtures.
 *
 * <p>
 * Every expected value in this class was read out of the fixture itself; the fixture file names
 * encode the shape ({@code numeric1_15x4733} = 15 columns, 4733 rows), which is the cross-check for
 * the column and row counts.
 */
class BdatValueClassAccessorTest
{

    /** 32-bit, little-endian, Windows, uncompressed; 15 columns x 4733 rows. */
    private static final String NUMERIC1 = "numeric1_15x4733.sas7bdat";

    /** 64-bit, big-endian, UNIX, uncompressed; 11 columns x 729 rows. */
    private static final String BDAT64 = "64_numeric4_11x729.sas7bdat";

    private static DatasetBdat ds32;

    private static DatasetBdat ds64;

    @BeforeAll
    static void parseFixtures() throws IOException
    {
        ds32 = parse(NUMERIC1);
        ds64 = parse(BDAT64);
    }


    private static File fixture(String name)
    {
        return new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdat/" + name);
    }


    private static DatasetBdat parse(String name) throws IOException
    {
        return new ParserBdat().parseDataset(fixture(name));
    }


    private static <T extends SubHeader> T subHeader(DatasetBdat dataset, Class<T> type)
    {
        for (Page page : dataset.getPages())
        {
            for (SubHeaderPointer pointer : page.getSubHeaderPointers())
            {
                SubHeader sh = pointer.getSubHeader();
                if (type.isInstance(sh))
                {
                    return type.cast(sh);
                }
            }
        }
        throw new IllegalStateException("no " + type.getSimpleName() + " in " + dataset.getName());
    }


    private static VariableBdat variable(DatasetBdat dataset, String name)
    {
        return dataset.getVariables().stream().filter(v -> name.equals(v.getName())).findFirst()
                .orElseThrow(() -> new IllegalStateException("no column " + name));
    }

    // ---------------------------------------------------------------- DatasetBdat


    @Test
    void datasetBdatReportsTheIdentityFieldsOfThe32BitFixture()
    {
        assertEquals("CPS", ds32.getName());
        assertEquals(false, ds32.get64Bit());
        assertEquals(Platform.WINDOWS, ds32.getPlatform());
        assertEquals(ByteOrder.Little, ds32.getByteOrder());
        assertEquals(Long.valueOf(15), ds32.getColumnCount());
        assertEquals(Long.valueOf(15), ds32.getVariableCount());
        assertEquals(Long.valueOf(4733), ds32.getObservationCount());
        assertEquals(Long.valueOf(60), ds32.getPageCount());
        assertEquals(false, ds32.getCompressed());
        // BDAT carries no dataset-type field, so getType() is null by construction.
        assertNull(ds32.getType());
        assertEquals(LocalDateTime.parse("2008-05-13T15:32:52"), ds32.getCreated());
        assertEquals(LocalDateTime.parse("2008-05-13T15:32:52"), ds32.getModified());
        assertEquals(Optional.of("WAGE"), ds32.getCreatorProcess());
    }


    @Test
    void datasetBdatReportsTheIdentityFieldsOfThe64BitBigEndianFixture()
    {
        assertEquals("EXTEND_YES", ds64.getName());
        assertEquals(true, ds64.get64Bit());
        assertEquals(Platform.UNIX, ds64.getPlatform());
        assertEquals(ByteOrder.Big, ds64.getByteOrder());
        assertEquals(Long.valueOf(11), ds64.getColumnCount());
        assertEquals(Long.valueOf(729), ds64.getObservationCount());
        assertEquals(Long.valueOf(2), ds64.getPageCount());
        assertEquals(false, ds64.getCompressed());
        assertEquals(LocalDateTime.parse("2016-10-04T23:07:19"), ds64.getCreated());
        assertEquals(LocalDateTime.parse("2016-10-04T23:07:19"), ds64.getModified());
        assertEquals(Optional.of("DATASTEP"), ds64.getCreatorProcess());
    }


    @Test
    void headerByteCountsWidenFrom32BitTo64BitDatasets()
    {
        assertEquals(24, ds32.getPageHeaderByteCount());
        assertEquals(12, ds32.getSubHeaderPointerByteCount());
        assertEquals(40, ds64.getPageHeaderByteCount());
        assertEquals(24, ds64.getSubHeaderPointerByteCount());
        // Memoized: the second call must return the same value, not recompute a different one.
        assertEquals(24, ds32.getPageHeaderByteCount());
        assertEquals(24, ds64.getSubHeaderPointerByteCount());
    }


    @Test
    void getHeader1And2ExposeTheParsedFileHeaders()
    {
        assertEquals(true, ds32.getHeader1().getLittleEndian());
        assertEquals(false, ds64.getHeader1().getLittleEndian());
        assertEquals("CPS", ds32.getHeader2().getDatasetName());
        assertEquals("DATA", ds32.getHeader2().getFileType());
        // Header2.platform is the raw code behind getPlatform(): "2" = Windows, "1" = UNIX.
        assertEquals("2", ds32.getHeader2().getPlatform());
        assertEquals("1", ds64.getHeader2().getPlatform());
    }


    @Test
    void getHeader3AndHeader4ReturnWhatWasSetOnTheDataset()
    {
        assertEquals(LocalDateTime.parse("2008-05-13T15:32:52"), ds32.getHeader3().getCreated());

        // None of the bundled fixtures carries a Header4, so the accessor is exercised through
        // the setter instead.
        DatasetBdat dataset = new DatasetBdat();
        Header4 header4 = new Header4();
        header4.setSasRelease("9.0401M6");
        dataset.setHeader4(header4);
        assertSame(header4, dataset.getHeader4());
        assertEquals("9.0401M6", dataset.getHeader4().getSasRelease());
    }


    @Test
    void getDataSetLabelReturnsTheLabelStoredInTheFile() throws IOException
    {
        assertEquals(Optional.of("test"), parse("file_with_label_2x2.sas7bdat").getDataSetLabel());
        // The 64-bit fixture was written without a dataset label, so the slice has length 0.
        assertEquals(Optional.empty(), ds64.getDataSetLabel());
    }


    @Test
    void getCreatorSoftwareRightTrimsThePaddedTextSlice()
    {
        // The slice is non-empty but blank-padded, so the right-trim in getSubHeaderString
        // reduces it to "" while still yielding a present Optional.
        assertEquals(Optional.of(""), ds64.getCreatorSoftware());
        // numeric1 has a zero-length creator-software slice, hence no value at all.
        assertEquals(Optional.empty(), ds32.getCreatorSoftware());
    }


    @Test
    void getColumnCountThrowsBeforeTheRowSizeSubHeaderIsParsed()
    {
        DatasetBdat unparsed = new DatasetBdat();
        assertThrows(IllegalStateException.class, unparsed::getColumnCount);
        assertThrows(IllegalStateException.class, unparsed::getRowCount);
        assertThrows(IllegalStateException.class, unparsed::getRowLength);
    }


    @Test
    void setNameWritesThroughToHeader2() throws IOException
    {
        DatasetBdat dataset = parse(NUMERIC1);
        dataset.setName("RENAMED");
        assertEquals("RENAMED", dataset.getName());
        assertEquals("RENAMED", dataset.getHeader2().getDatasetName());
    }


    @Test
    void setPagesReplacesThePageListAndNullYieldsAnEmptyList()
    {
        DatasetBdat dataset = new DatasetBdat();
        List<Page> pages = new ArrayList<>();
        pages.add(new Page(dataset));
        dataset.setPages(pages);
        assertSame(pages, dataset.getPages());

        dataset.setPages(null);
        assertEquals(0, dataset.getPages().size());
    }


    @Test
    void setColumnSizeSubHeaderBackLinksTheSubHeaderToItsDataset()
    {
        DatasetBdat dataset = new DatasetBdat();
        ColumnSizeSubHeader32 columnSize = new ColumnSizeSubHeader32();
        dataset.setColumnSizeSubHeader(columnSize);
        assertSame(dataset, columnSize.getDataset());
    }


    @Test
    void getColumnNameSlicesTheNameOutOfTheTextSubHeader()
    {
        ColumnNamesSubHeader names = subHeader(ds32, ColumnNamesSubHeader.class);
        assertEquals(Optional.of("WAGE"), ds32.getColumnName(names.getColumnNames().get(0)));
        assertEquals(Optional.of("EDUC"), ds32.getColumnName(names.getColumnNames().get(1)));
    }


    @Test
    void getFormatNameAndGetLabelSliceOrReturnEmptyForZeroLengthEntries() throws IOException
    {
        DatasetBdat percents = parse("percents_8x11.sas7bdat");
        FormatAndLabelSubHeader formatted = variable(percents, "pctdone")
                .getFormatAndLabelSubHeader();
        assertEquals(Optional.of("PERCENT"), percents.getFormatName(formatted));

        // numeric1's columns carry labels but no formats: formatLength is 0, labelLength is not.
        FormatAndLabelSubHeader labelled = variable(ds32, "WAGE").getFormatAndLabelSubHeader();
        assertEquals(Optional.empty(), ds32.getFormatName(labelled));
        assertEquals(Optional.of("earnings per hour"), ds32.getLabel(labelled));

        // The 64-bit fixture has neither, so both slices are zero-length.
        FormatAndLabelSubHeader bare = ds64.getVariables().get(0).getFormatAndLabelSubHeader();
        assertEquals(Optional.empty(), ds64.getFormatName(bare));
        assertEquals(Optional.empty(), ds64.getLabel(bare));
    }


    @Test
    void getFormatAndLabelSubHeadersYieldsOnePerColumn()
    {
        List<FormatAndLabelSubHeader> headers = ds32.getFormatAndLabelSubHeaders().toList();
        // SAS wrote one more format-and-label subheader than the file has columns (16 for 15
        // columns); getVariables() pairs the first getVariableCount() of them with the column
        // names in order, so the stream's order is what is load-bearing.
        assertEquals(16, headers.size());
        assertEquals("earnings per hour", headers.get(0).getLabel());
        assertEquals("years of education", headers.get(1).getLabel());
    }


    @Test
    void getLibraryReturnsTheLibraryTheDatasetWasParsedInto()
    {
        LibraryBdat library = ds32.getLibrary();
        assertEquals(fixture(NUMERIC1), library.getFile());
        assertSame(library, ds32.getLibrary());
    }

    // ------------------------------------------------------- FormatAndLabelSubHeader


    @Test
    void formatAndLabelSubHeaderResolvesItsOwnFormatAndLabelStrings() throws IOException
    {
        DatasetBdat percents = parse("percents_8x11.sas7bdat");
        FormatAndLabelSubHeader formatted = variable(percents, "pctdone")
                .getFormatAndLabelSubHeader();
        assertEquals("PERCENT", formatted.getFormat());

        FormatAndLabelSubHeader labelled = variable(ds32, "WAGE").getFormatAndLabelSubHeader();
        assertEquals("earnings per hour", labelled.getLabel());
        // All of numeric1's label text lives in text subheader 0.
        assertEquals((short) 0, labelled.getLabelIndex());
        assertEquals((short) 17, labelled.getLabelLength());
    }

    // ------------------------------------------------------------------- Header1


    @Test
    void header1ReportsEndiannessAndWordSizeForBothFixtures()
    {
        Header1 little = ds32.getHeader1();
        assertEquals(true, little.getLittleEndian());
        assertEquals(ByteOrder.Little, little.getByteOrder());
        assertEquals(false, little.get64Bit());

        Header1 big = ds64.getHeader1();
        assertEquals(false, big.getLittleEndian());
        assertEquals(ByteOrder.Big, big.getByteOrder());
        assertEquals(true, big.get64Bit());
    }


    @Test
    void header1ToStringCarriesTheFieldsThatIdentifyTheLayout()
    {
        String s = ds32.getHeader1().toString();
        assertTrue(s.startsWith("Header1 ["), s);
        assertTrue(s.contains("littleEndian=true"), s);
        assertTrue(s.contains("64Bit=false"), s);

        String s64 = ds64.getHeader1().toString();
        assertTrue(s64.contains("littleEndian=false"), s64);
        assertTrue(s64.contains("64Bit=true"), s64);
    }

    // ---------------------------------------------------------------- LibraryBdat


    @Test
    void libraryBdatFoldsTheEarliestCreatedAndLatestModifiedOverItsDatasets() throws IOException
    {
        File dir = new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdatlib");
        LibraryBdat library = new ParserBdat().parseLibrary(dir);
        assertEquals(4, library.getDatasets().size());
        // Earliest of the four Header3 creation stamps, latest of the four modification stamps.
        assertEquals(LocalDateTime.parse("1993-06-05T13:00:41"), library.getCreated());
        assertEquals(LocalDateTime.parse("2008-05-13T15:33:09"), library.getModified());
    }

    // ------------------------------------------------------------ ColumnAttributes


    @Test
    void getVariableTypeMapsTheOneBasedIdAndRejectsIdsOutsideIt() throws IOException
    {
        // Id 1 -> index 0 -> NUMERIC; id 2 -> index 1 -> CHARACTER.
        assertEquals(VariableType.NUMERIC, variable(ds32, "WAGE").getType());
        DatasetBdat formats = parse("mixed_formats_44x11.sas7bdat");
        assertEquals(VariableType.CHARACTER, variable(formats, "project").getType());

        ColumnAttributes32 tooLow = new ColumnAttributes32();
        tooLow.setVariableTypeId((byte) 0);
        assertThrows(IllegalArgumentException.class, tooLow::getVariableType);

        // Id 3 is one past the last VariableType; it must be rejected, not indexed.
        ColumnAttributes32 tooHigh = new ColumnAttributes32();
        tooHigh.setVariableTypeId((byte) 3);
        assertThrows(IllegalArgumentException.class, tooHigh::getVariableType);
    }

    // --------------------------------------------- Column{Attributes,Names}SubHeader


    @Test
    void columnAttributesSubHeaderCountsOneEntryPerColumn()
    {
        ColumnAttributesSubHeader atts32 = subHeader(ds32, ColumnAttributesSubHeader.class);
        assertEquals(15, atts32.getNumColumnAttributes());
        assertEquals(15, atts32.getColumnAttributes().size());

        ColumnAttributesSubHeader atts64 = subHeader(ds64, ColumnAttributesSubHeader.class);
        assertEquals(11, atts64.getNumColumnAttributes());
        assertEquals(11, atts64.getColumnAttributes().size());
    }


    @Test
    void columnAttributesSubHeaderToStringNamesItsTypeAndEntries()
    {
        String s = subHeader(ds32, ColumnAttributesSubHeader.class).toString();
        assertTrue(s.startsWith("ColumnAttributesSubHeader [remainingLength="), s);
        assertTrue(s.contains("columnAttributes="), s);
        assertTrue(s.endsWith("]"), s);
    }


    @Test
    void columnNamesSubHeaderCountsOneEntryPerColumn()
    {
        ColumnNamesSubHeader names32 = subHeader(ds32, ColumnNamesSubHeader.class);
        assertEquals(15, names32.getNumColumnNames());
        assertEquals(15, names32.getColumnNames().size());

        ColumnNamesSubHeader names64 = subHeader(ds64, ColumnNamesSubHeader.class);
        assertEquals(11, names64.getNumColumnNames());
        assertEquals(11, names64.getColumnNames().size());
    }


    @Test
    void columnNamesSubHeaderToStringNamesItsTypeAndEntries()
    {
        String s = subHeader(ds32, ColumnNamesSubHeader.class).toString();
        assertTrue(s.startsWith("ColumnNamesSubHeader [remainingLength="), s);
        assertTrue(s.contains("columnNames="), s);
        assertTrue(s.endsWith("]"), s);
    }

    // ----------------------------------------------------------------- PageHeader


    @Test
    void pageHeaderHashCodeAgreesWithEqualsOnItsFourIdentityFields()
    {
        PageHeader32 a = pageHeader(7, (short) 256, (short) 3, (short) 2);
        PageHeader32 b = pageHeader(7, (short) 256, (short) 3, (short) 2);
        PageHeader32 c = pageHeader(7, (short) 256, (short) 3, (short) 5);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a.hashCode(), c.hashCode());
    }


    private static PageHeader32 pageHeader(int sequence, short typeId, short blocks, short subs)
    {
        PageHeader32 header = new PageHeader32();
        header.pageSequence = sequence;
        header.setPageTypeId(typeId);
        header.setBlockCount(blocks);
        header.setSubHeaderCount(subs);
        return header;
    }

    // ------------------------------------------------------------------ SubHeader


    @Test
    void setPointerLinksTheSubHeaderAndThePointerToEachOther()
    {
        SubHeader sh = new SubHeader();
        SubHeaderPointer32 pointer = new SubHeaderPointer32();
        sh.setPointer(pointer);
        assertSame(pointer, sh.getPointer());
        assertSame(sh, pointer.getSubHeader());
    }


    @Test
    void setDatasetIsReadBackByGetDataset()
    {
        DatasetBdat dataset = new DatasetBdat();
        SubHeader sh = new SubHeader();
        sh.setDataset(dataset);
        assertSame(dataset, sh.getDataset());
    }

    // ----------------------------------------------------------- SubHeaderPointer


    @Test
    void subHeaderPointerCategoryIdIsReadBackAndParticipatesInHashCode()
    {
        SubHeaderPointer32 a = pointer((byte) 0);
        SubHeaderPointer32 b = pointer((byte) 0);
        SubHeaderPointer32 c = pointer((byte) 1);

        assertEquals((byte) 0, a.getCategoryId());
        assertEquals((byte) 1, c.getCategoryId());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a.hashCode(), c.hashCode());
    }


    private static SubHeaderPointer32 pointer(byte categoryId)
    {
        SubHeaderPointer32 pointer = new SubHeaderPointer32();
        pointer.pageOffset = 3616;
        pointer.length = 480;
        pointer.setCompressionTypeId((byte) 0);
        pointer.setCompressed(categoryId);
        pointer.setSignature(SubHeaderSignature.ROW_SIZE);
        return pointer;
    }


    @Test
    void subHeaderPointerToStringCarriesOffsetLengthAndCategory()
    {
        SubHeaderPointer real = ds32.getPages().get(0).getSubHeaderPointers().get(0);
        String s = real.toString();
        assertTrue(s.startsWith("SubHeaderPointer [pageOffset=" + real.getPageOffset()), s);
        assertTrue(s.contains(", length=" + real.getLength()), s);
        assertTrue(s.contains(", categoryId=" + real.getCategoryId()), s);
        assertTrue(s.contains(", signature=" + real.getSignature()), s);
    }

    // ------------------------------------------------------------- TextSubHeader


    @Test
    void textSubHeaderToStringReportsItsLength()
    {
        TextSubHeader text = subHeader(ds32, TextSubHeader.class);
        assertEquals("TextSubHeader [length=" + text.getLength() + "]", text.toString());
    }

    // -------------------------------------------------------------- VariableBdat


    @Test
    void variableBdatReportsOffsetLengthAndLabelFromItsSubHeaders()
    {
        VariableBdat wage = variable(ds32, "WAGE");
        assertEquals(Long.valueOf(0), wage.getOffset());
        assertEquals(8, wage.getLength());
        assertEquals("earnings per hour", wage.getLabel());

        // EDUC follows WAGE's 8 numeric bytes, so its record offset is 8.
        VariableBdat educ = variable(ds32, "EDUC");
        assertEquals(Long.valueOf(8), educ.getOffset());
        assertEquals("years of education", educ.getLabel());

        // The 64-bit fixture carries no column labels.
        assertNull(ds64.getVariables().get(0).getLabel());
    }


    @Test
    void getSortOrderDecodesTheSignBitOfTheColumnNameSortByte()
    {
        // None of the bundled fixtures is a sorted dataset, so the byte is supplied directly.
        assertEquals(0, variableWithSortOrder(null).getSortOrder());
        assertEquals(0, variableWithSortOrder((byte) 0).getSortOrder());
        assertEquals(1, variableWithSortOrder((byte) 1).getSortOrder());
        assertEquals(2, variableWithSortOrder((byte) 2).getSortOrder());
        // High bit set means descending: 0x82 is the second key, descending.
        assertEquals(-2, variableWithSortOrder((byte) 0x82).getSortOrder());
        assertFalse(variableWithSortOrder((byte) 0).isSortKey());
        assertTrue(variableWithSortOrder((byte) 0x82).isSortKey());
    }


    private static VariableBdat variableWithSortOrder(Byte sortOrder)
    {
        ColumnName name = new ColumnName();
        name.setSortOrder(sortOrder);
        return new VariableBdat(ds32.getVariables().get(0).getFormatAndLabelSubHeader(), name,
                new ColumnAttributes32());
    }

    // ---------------------------------------------------------- RowSizeSubHeader


    @Test
    void rowSizeSubHeaderReportsTheCountsOfThe32BitFixture()
    {
        RowSizeSubHeader32 rs = (RowSizeSubHeader32) subHeader(ds32, RowSizeSubHeader.class);
        assertEquals(Long.valueOf(15), rs.getColumnCount());
        assertEquals(Long.valueOf(15), rs.getColumnCountP1());
        assertEquals(Long.valueOf(0), rs.getColumnCountP2());
        assertEquals(Long.valueOf(4733), rs.getRowCount());
        assertEquals(false, rs.getCompressed());
        assertEquals(1, rs.getUnknown10());
        assertEquals((short) 0, rs.getUnknown14());
        assertEquals((short) 0, rs.getUnknown15());
        assertEquals((short) 0, rs.getUnknown17());
        assertEquals((short) 0, rs.getUnknown18());
        assertEquals((short) 0, rs.getUnknown20());
        assertEquals((short) 0, rs.getUnknown21());
        assertEquals((short) 0, rs.getUnknown24());
    }


    @Test
    void rowSizeSubHeaderReportsTheCountsOfThe64BitFixture()
    {
        RowSizeSubHeader64 rs = (RowSizeSubHeader64) subHeader(ds64, RowSizeSubHeader.class);
        // getColumnCount() is not overridden on the 64-bit struct, so this exercises the base
        // implementation's P1 + P2 sum.
        assertEquals(Long.valueOf(11), rs.getColumnCount());
        assertEquals(Long.valueOf(11), rs.getColumnCountP1());
        assertEquals(Long.valueOf(0), rs.getColumnCountP2());
        assertEquals(Long.valueOf(0), rs.getDeletedRowCount());
        assertEquals(Long.valueOf(1), rs.getUnknown10());
        assertEquals((short) 8, rs.getUnknown15());
    }


    @Test
    void getCompressedIsTrueExactlyWhenACompressionMethodNameIsStored() throws IOException
    {
        assertEquals(false, subHeader(ds32, RowSizeSubHeader.class).getCompressed());
        DatasetBdat compressed = parse("int_only_25x52.sas7bdat");
        assertEquals(true, subHeader(compressed, RowSizeSubHeader.class).getCompressed());
        assertEquals(true, compressed.getCompressed());
    }

}
