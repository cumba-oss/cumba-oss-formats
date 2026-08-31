package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import net.cumba.sasutils.SasConstants;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.x32.ColumnAttributes32;
import net.cumba.sasutils.bdat.x32.Header332;
import net.cumba.sasutils.bdat.x32.PageHeader32;
import net.cumba.sasutils.bdat.x32.SubHeaderPointer32;
import net.cumba.sasutils.bdat.x64.ColumnAttributes64;
import net.cumba.sasutils.bdat.x64.Header364;
import net.cumba.sasutils.bdat.x64.PageHeader64;
import net.cumba.sasutils.bdat.x64.SubHeaderPointer64;
import org.junit.jupiter.api.Test;

/**
 * Exercises the simple beans used during sas7bdat parsing: {@link Header2}, {@link Header3}
 * (32/64-bit subclasses), {@link Header4}, {@link PageHeader} (32/64), {@link SubHeaderPointer}
 * (32/64) and {@link ColumnAttributes} (32/64). These classes are pure data carriers with
 * getter/setter/toString/equals/hashCode methods plus a couple of derived accessors — exercising
 * them directly produces the bulk of the coverage these classes contribute to the module.
 */
class HeaderStructTest
{

    // ---------- Header2 ----------

    @Test
    void header2_settersAndGetters()
    {
        Header2 h = new Header2();
        h.setPlatform("1");
        h.setDatasetName("MYTABLE");
        h.setFileType("DATA");
        h.encoding = (short) 28;

        assertEquals("1", h.getPlatform());
        assertEquals("MYTABLE", h.getDatasetName());
        assertEquals("DATA", h.getFileType());
        // Encoding field is a public Short — verify via direct access.
        assertEquals((short) 28, h.encoding.shortValue());

        String s = h.toString();
        assertNotNull(s);
        assertTrue(s.contains("MYTABLE"));
        assertTrue(s.contains("DATA"));
    }

    // ---------- Header3 / Header332 / Header364 ----------


    @Test
    void header332_pageCountWidensToLong()
    {
        Header332 h = new Header332();
        h.setCreatedTimestamp(0.0); // 1960-01-01 epoch
        h.setModifiedTimestamp(0.0);
        h.setHeaderSize(8192);
        h.setPageSize(4096);
        h.setPageCount(7);

        assertEquals(0.0, h.getCreatedTimestamp());
        assertEquals(0.0, h.getModifiedTimestamp());
        assertEquals(8192, h.getHeaderSize());
        assertEquals(4096, h.getPageSize());
        assertEquals(7L, h.getPageCount());

        // Created/modified resolve to the SAS epoch (1960-01-01).
        LocalDateTime created = h.getCreated();
        LocalDateTime modified = h.getModified();
        assertNotNull(created);
        assertEquals(SasConstants.toDateTime(0.0), created);
        assertEquals(SasConstants.toDateTime(0.0), modified);

        String s = h.toString();
        assertNotNull(s);
        assertTrue(s.contains("headerSize"));
        assertTrue(s.contains("pageSize"));
    }


    @Test
    void header364_pageCountIsLong()
    {
        Header364 h = new Header364();
        h.setCreatedTimestamp(1.0);
        h.setModifiedTimestamp(2.0);
        h.setHeaderSize(65536);
        h.setPageSize(8192);
        h.setPageCount(123_456_789_012L);

        assertEquals(123_456_789_012L, h.getPageCount());
        assertEquals(1.0, h.getCreatedTimestamp());
        assertEquals(2.0, h.getModifiedTimestamp());
        // toString does not throw.
        assertNotNull(h.toString());
    }

    // ---------- Header4 ----------


    @Test
    void header4_settersAndGetters()
    {
        Header4 h = new Header4();
        h.setSasRelease("9.4");
        h.setSasServer("X64_10HOME");
        h.setOsVersion("10.0");
        h.setOsVendor("Microsoft");
        h.setOsName("WINDOWS");
        h.otherTimestamp = 0.0;

        assertEquals("9.4", h.getSasRelease());
        assertEquals("X64_10HOME", h.getSasServer());
        assertEquals("10.0", h.getOsVersion());
        assertEquals("Microsoft", h.getOsVendor());
        assertEquals("WINDOWS", h.getOsName());

        LocalDateTime ts = h.getTimestamp();
        assertEquals(SasConstants.toDateTime(0.0), ts);

        String s = h.toString();
        assertNotNull(s);
        assertTrue(s.contains("WINDOWS"));
        assertTrue(s.contains("9.4"));
    }

    // ---------- PageHeader (32/64) ----------


    @Test
    void pageHeader32_deletedPointerReadsAsLittleEndianInt()
    {
        PageHeader32 p = new PageHeader32();
        p.pageSequence = 12345;
        p.setPageTypeId((short) 256); // PageType.DATA
        p.setBlockCount((short) 2);
        p.setSubHeaderCount((short) 1);
        // PageHeader.getDeletedPointer reads 4 LE bytes from the deletedPointer field.
        byte[] db = new byte[4];
        ByteBuffer.wrap(db).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(0x01020304);
        p.deletedPointer = db;

        assertEquals(12345, p.pageSequence);
        assertEquals((short) 256, p.getPageTypeId());
        assertEquals((short) 2, p.getBlockCount());
        assertEquals((short) 1, p.getSubHeaderCount());
        assertEquals(0x01020304L, p.getDeletedPointer(java.nio.ByteOrder.LITTLE_ENDIAN));
        assertNotNull(p.getPageType());
    }


    @Test
    void pageHeader64_deletedPointerReadsAsLittleEndianLong()
    {
        PageHeader64 p = new PageHeader64();
        p.pageSequence = 77;
        p.setPageTypeId((short) 512); // PageType.Mixed
        p.setBlockCount((short) 5);
        p.setSubHeaderCount((short) 3);
        byte[] db = new byte[8];
        ByteBuffer.wrap(db).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(0x00FFEEDDCCBBAA99L);
        p.deletedPointer = db;

        assertEquals(0x00FFEEDDCCBBAA99L, p.getDeletedPointer(java.nio.ByteOrder.LITTLE_ENDIAN));
        assertNotNull(p.toString());
    }


    @Test
    void pageHeader_hasDeletedRecords_onlyForMixed2AndData2()
    {
        PageHeader32 p = new PageHeader32();
        p.setBlockCount((short) 0);
        p.setSubHeaderCount((short) 0);
        p.pageSequence = 0;
        for (PageType pt : PageType.values())
        {
            p.setPageTypeId((short) pt.id);
            boolean expected = pt == PageType.MIXED2 || pt == PageType.DATA2;
            assertEquals(expected, p.hasDeletedRecords(), "hasDeletedRecords mismatch for " + pt);
        }
    }


    @Test
    void pageHeader_equalsAndHashCode()
    {
        PageHeader32 a = new PageHeader32();
        a.pageSequence = 1;
        a.setPageTypeId((short) 256);
        a.setBlockCount((short) 1);
        a.setSubHeaderCount((short) 0);
        a.deletedPointer = new byte[4];

        PageHeader32 b = new PageHeader32();
        b.pageSequence = 1;
        b.setPageTypeId((short) 256);
        b.setBlockCount((short) 1);
        b.setSubHeaderCount((short) 0);
        b.deletedPointer = new byte[4];

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Different subclass instance with equivalent toString — equals must reject by
        // class.
        PageHeader64 c = new PageHeader64();
        c.pageSequence = 1;
        c.setPageTypeId((short) 256);
        c.setBlockCount((short) 1);
        c.setSubHeaderCount((short) 0);
        c.deletedPointer = new byte[8];
        assertNotEquals(a, c);
        assertNotEquals(null, a);
        // self-equality
        assertEquals(a, a);
    }

    // ---------- SubHeaderPointer (32/64) ----------


    @Test
    void subHeaderPointer32_basicAccessors()
    {
        SubHeaderPointer32 p = new SubHeaderPointer32();
        p.pageOffset = 0x100;
        p.length = 256;
        p.setCompressionTypeId((byte) 0);
        p.setCompressed((byte) 0);

        assertEquals(0x100L, p.getPageOffset());
        assertEquals(256L, p.getLength());
        assertEquals((byte) 0, p.getCompressionTypeId());
        assertEquals(CompressionType.NONE, p.getCompressionType());
        assertEquals(SubHeaderCategory.A, p.getCategory());
        assertNotNull(p.toString());
    }


    @Test
    void subHeaderPointer64_basicAccessors()
    {
        SubHeaderPointer64 p = new SubHeaderPointer64();
        p.pageOffset = 0x4000L;
        p.length = 8192L;
        p.setCompressionTypeId((byte) 4);
        p.setCompressed((byte) 1);

        assertEquals(0x4000L, p.getPageOffset());
        assertEquals(8192L, p.getLength());
        assertEquals(CompressionType.COMPRESSED, p.getCompressionType());
        assertEquals(SubHeaderCategory.B, p.getCategory());
    }


    @Test
    void subHeaderPointer_signatureAndSubHeader()
    {
        SubHeaderPointer32 p = new SubHeaderPointer32();
        p.pageOffset = 0;
        p.length = 0;
        p.setCompressionTypeId((byte) 0);
        p.setCompressed((byte) 0);

        assertSame(null, p.getSignature());
        p.setSignature(SubHeaderSignature.ROW_SIZE);
        assertSame(SubHeaderSignature.ROW_SIZE, p.getSignature());

        TextSubHeader sub = new TextSubHeader();
        sub.string = "abc";
        p.setSubHeader(sub);
        assertSame(sub, p.getSubHeader());
    }


    @Test
    void subHeaderPointer_equalsAndHashCode()
    {
        SubHeaderPointer32 a = new SubHeaderPointer32();
        a.pageOffset = 1;
        a.length = 2;
        a.setCompressionTypeId((byte) 0);
        a.setCompressed((byte) 0);

        SubHeaderPointer32 b = new SubHeaderPointer32();
        b.pageOffset = 1;
        b.length = 2;
        b.setCompressionTypeId((byte) 0);
        b.setCompressed((byte) 0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(null, a);
        assertEquals(a, a);

        SubHeaderPointer64 c = new SubHeaderPointer64();
        c.pageOffset = 1L;
        c.length = 2L;
        c.setCompressionTypeId((byte) 0);
        c.setCompressed((byte) 0);
        assertNotEquals(a, c);
    }

    // ---------- ColumnAttributes (32/64) ----------


    @Test
    void columnAttributes32_offsetAndCommonFields()
    {
        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.offset = 16;
        ca.width = 8;
        ca.nameLengthId = 0;
        ca.setVariableTypeId((byte) 1);

        assertEquals(16L, ca.getOffset());
        assertEquals(8, ca.width);
        assertEquals(VariableType.NUMERIC, ca.getVariableType());
        String s = ca.toString();
        assertTrue(s.contains("offset=16"));
        assertTrue(s.contains("width=8"));
    }


    @Test
    void columnAttributes64_offsetIsLong()
    {
        ColumnAttributes64 ca = new ColumnAttributes64();
        ca.offset = 1_000_000_000L;
        ca.width = 32;
        ca.nameLengthId = 1;
        ca.setVariableTypeId((byte) 2);

        assertEquals(1_000_000_000L, ca.getOffset());
        assertEquals(VariableType.CHARACTER, ca.getVariableType());
    }

    // ---------- SubHeader sanity ----------


    @Test
    void subHeader_setDatasetRoundTrips()
    {
        TextSubHeader sub = new TextSubHeader();
        DatasetBdat ds = new DatasetBdat();
        sub.setDataset(ds);
        assertSame(ds, sub.dataset);
    }


    @Test
    void pageHeader_toStringIncludesNumericFields()
    {
        PageHeader32 p = new PageHeader32();
        p.pageSequence = 99;
        p.setPageTypeId((short) 256);
        p.setBlockCount((short) 1);
        p.setSubHeaderCount((short) 1);
        p.deletedPointer = new byte[4];
        assertTrue(p.toString().contains("pageSequence=99"));
        // hasDeletedRecords returns false for plain Data (id 256).
        assertFalse(p.hasDeletedRecords());
    }

    // ---------- Header1 ----------


    @Test
    void header1_32BitMode()
    {
        Header1 h = new Header1();
        h.setAlign1((byte) 0); // not 51 → 32-bit
        h.setAlign2((byte) 0);
        h.setLittleEndian(Boolean.TRUE);

        assertEquals(Byte.valueOf((byte) 0), h.getAlign1());
        assertEquals(Byte.valueOf((byte) 0), h.getAlign2());
        assertEquals(true, h.getLittleEndian());
        assertEquals(false, h.get64Bit());
        assertEquals(4, h.getIntegerLength());
        assertEquals(0, h.getHeader1Padding());
        assertEquals(0, h.getHeader2Padding());
        assertEquals(12, h.getSubHeaderPointerLength());
        assertNotNull(h.getByteOrder());
        assertNotNull(h.getIntegerTokenType());
        assertNotNull(h.toString());
    }


    @Test
    void header1_64BitMode()
    {
        Header1 h = new Header1();
        h.setAlign1((byte) 51); // ALIGN_CHECKER_VALUE → 64-bit
        h.setAlign2((byte) 51); // also triggers header1 padding
        h.setLittleEndian(Boolean.FALSE);

        assertEquals(true, h.get64Bit());
        assertEquals(8, h.getIntegerLength());
        assertEquals(4, h.getHeader1Padding());
        assertEquals(4, h.getHeader2Padding());
        assertEquals(24, h.getSubHeaderPointerLength());
        assertNotNull(h.getByteOrder());
        assertNotNull(h.getIntegerTokenType());
    }

    // ---------- ColumnName ----------


    @Test
    void columnName_settersGetters()
    {
        ColumnName cn = new ColumnName();
        cn.setIndex((short) 1);
        cn.setStart((short) 16);
        cn.setLength((short) 8);
        cn.setSortOrder((byte) 0);

        assertEquals((short) 1, cn.getIndex());
        assertEquals((short) 16, cn.getStart());
        assertEquals((short) 8, cn.getLength());
        assertEquals((byte) 0, cn.getSortOrder());
        // dataset is null → toString uses null branch for getName()
        String s = cn.toString();
        assertNotNull(s);
        assertTrue(s.contains("index=1"));
        assertTrue(s.contains("start=16"));
        assertTrue(s.contains("length=8"));
    }

    // ---------- FormatAndLabelSubHeader ----------


    @Test
    void formatAndLabelSubHeader_settersGetters()
    {
        FormatAndLabelSubHeader f = new FormatAndLabelSubHeader();
        f.formatDigits = 12;
        f.formatDecimals = 2;
        f.informatDigits = 8;
        f.informatDecimals = 0;
        f.setFormatIndex((short) 1);
        f.setFormatOffset((short) 10);
        f.setFormatLength((short) 6);
        f.setLabelIndex((short) 0);
        f.setLabelOffset((short) 24);
        f.setLabelLength((short) 12);

        assertEquals((short) 1, f.getFormatIndex());
        assertEquals((short) 10, f.getFormatOffset());
        assertEquals((short) 6, f.getFormatLength());
        assertEquals((short) 0, f.getLabelIndex());
        assertEquals((short) 24, f.getLabelOffset());
        assertEquals((short) 12, f.getLabelLength());

        String s = f.toString();
        assertNotNull(s);
        assertTrue(s.contains("formatIndex=1"));
        assertTrue(s.contains("labelLength=12"));
    }

    // ---------- VariableBdat ----------


    @Test
    void variableBdat_basicAccessors()
    {
        FormatAndLabelSubHeader f = new FormatAndLabelSubHeader();
        f.setFormatIndex((short) 0);
        f.setFormatOffset((short) 0);
        f.setFormatLength((short) 0);
        f.setLabelIndex((short) 0);
        f.setLabelOffset((short) 0);
        f.setLabelLength((short) 0);

        ColumnName cn = new ColumnName();
        cn.setIndex((short) 0);
        cn.setStart((short) 0);
        cn.setLength((short) 0);
        cn.setSortOrder((byte) 0);

        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.offset = 0;
        ca.width = 8;
        ca.nameLengthId = 1;
        ca.setVariableTypeId((byte) 1);

        VariableBdat v = new VariableBdat(f, cn, ca);
        assertEquals(0L, v.getOffset());
        assertEquals(8, v.getLength());
        assertEquals(VariableType.NUMERIC, v.getType());
        assertSame(f, v.getFormatAndLabelSubHeader());
        // getFormat is currently a stub returning null — exercise the path.
        assertEquals(null, v.getFormat());

        // toString does not throw and includes sub-bean info.
        String s = v.toString();
        assertNotNull(s);
        assertTrue(s.contains("VariableBdat"));
    }


    @Test
    void variableBdat_sortOrder_unsorted()
    {
        ColumnName cn = new ColumnName();
        cn.setSortOrder((byte) 0);
        VariableBdat v = makeVariable(cn);
        assertEquals(0, v.getSortOrder());
        assertFalse(v.isSortKey());
    }


    @Test
    void variableBdat_sortOrder_nullTreatedAsUnsorted()
    {
        ColumnName cn = new ColumnName();
        // Do not set sortOrder → null
        VariableBdat v = makeVariable(cn);
        assertEquals(0, v.getSortOrder());
        assertFalse(v.isSortKey());
    }


    @Test
    void variableBdat_sortOrder_ascending()
    {
        ColumnName cn = new ColumnName();
        cn.setSortOrder((byte) 0x01);
        VariableBdat v = makeVariable(cn);
        assertEquals(1, v.getSortOrder());
        assertTrue(v.isSortKey());
    }


    @Test
    void variableBdat_sortOrder_descending()
    {
        ColumnName cn = new ColumnName();
        // high bit set + 1 = descending primary
        cn.setSortOrder((byte) 0x81);
        VariableBdat v = makeVariable(cn);
        assertEquals(-1, v.getSortOrder());
        assertTrue(v.isSortKey());
    }


    private static VariableBdat makeVariable(ColumnName cn)
    {
        FormatAndLabelSubHeader f = new FormatAndLabelSubHeader();
        f.setFormatIndex((short) 0);
        f.setFormatOffset((short) 0);
        f.setFormatLength((short) 0);
        f.setLabelIndex((short) 0);
        f.setLabelOffset((short) 0);
        f.setLabelLength((short) 0);
        cn.setIndex((short) 0);
        cn.setStart((short) 0);
        cn.setLength((short) 0);
        ColumnAttributes32 ca = new ColumnAttributes32();
        ca.offset = 0;
        ca.width = 8;
        ca.nameLengthId = 1;
        ca.setVariableTypeId((byte) 1);
        return new VariableBdat(f, cn, ca);
    }

    // ---------- ColumnSizeSubHeader / ColumnAttributesSubHeader / RowSizeSubHeader ----------


    @Test
    void columnSizeSubHeader32_toStringFromAbstractParent()
    {
        net.cumba.sasutils.bdat.x32.ColumnSizeSubHeader32 sh = new net.cumba.sasutils.bdat.x32.ColumnSizeSubHeader32();
        sh.setNumColumns(7);
        assertEquals(7L, sh.getNumColumns());
        String s = sh.toString();
        // Abstract parent toString prints "getNumColumns()=7".
        assertTrue(s.contains("7"));
    }


    @Test
    void columnAttributesSubHeader_basicAccessorsAndToString()
    {
        ColumnAttributesSubHeader cas = new ColumnAttributesSubHeader();
        cas.remainingLength = 16;
        ColumnAttributes32 a1 = new ColumnAttributes32();
        a1.offset = 0;
        a1.width = 8;
        a1.nameLengthId = 1;
        a1.setVariableTypeId((byte) 1);
        cas.getColumnAttributes().add(a1);

        assertEquals(1, cas.getColumnAttributes().size());
        // setter resets the list
        cas.setColumnAttributes(java.util.List.of());
        assertEquals(0, cas.getColumnAttributes().size());
        // getter recreates the list when null
        cas.columnAttributes = null;
        assertNotNull(cas.getColumnAttributes());

        assertNotNull(cas.toString());
    }

    // ---------- TextSubHeader ----------


    @Test
    void textSubHeader_stringRoundTrip()
    {
        TextSubHeader t = new TextSubHeader("hello world");
        assertEquals("hello world", t.getString());
        t.setLength((short) 64);
        assertEquals((short) 64, t.getLength());
        // getStringLength = length - 12 → 52
        assertEquals(52, t.getStringLength());
        t.setString("xyz");
        assertEquals("xyz", t.getString());
        assertNotNull(t.toString());
    }

    // ---------- RowSizeSubHeader (parent + 32 + 64 subclasses) ----------


    private static net.cumba.sasutils.bdat.x32.RowSizeSubHeader32 fullyPopulatedSubHeader32()
    {
        net.cumba.sasutils.bdat.x32.RowSizeSubHeader32 sh = new net.cumba.sasutils.bdat.x32.RowSizeSubHeader32();
        sh.setRowLength(28);
        sh.setRowCount(100);
        sh.setDeletedRowCount(2);
        sh.unknown00 = 0;
        sh.columnCountP1 = 5;
        sh.columnCountP2 = 0;
        sh.setPageSize(8192);
        sh.setMixedPageRowCount(3);
        sh.setPageSequence(1);
        sh.setUnknown3(1);
        sh.setUnknown4((short) 2);
        sh.setPagesWithSubHeadersCount(2);
        sh.lastPageSubHeadersCount = 1;
        sh.setPagesWithSubHeadersCountDuplicate(2);
        sh.numLastPageSubHeadersPlusTwo = 2;
        sh.setPageCount(4);
        sh.setUnknown8((short) 22);
        sh.setUnknown10(0);
        sh.setUnknown11((short) 7);
        sh.setUnknown1(new byte[0]);
        sh.setUnknown2(new byte[0]);
        sh.setUnknown5(new byte[0]);
        sh.setUnknown7(new byte[0]);
        sh.setUnknown9(new byte[0]);
        sh.setUnknown12(new byte[0]);
        sh.setUnknown13(new byte[0]);

        // Parent setters (Short fields used at toString time).
        sh.labelOffset = 10;
        sh.labelLength = 20;
        sh.creatorSoftwareOffset = 30;
        sh.creatorSoftwareLength = 8;
        sh.compressionMethodOffset = 40;
        sh.compressionMethodLength = 4;
        sh.creatorProcOffset = 50;
        sh.creatorProcLength = 8;
        sh.columnTextHeadersCount = 1;
        sh.columnNameMaxLength = 32;
        sh.columnLabelMaxLength = 40;
        sh.fullPageRowsCount = 100;
        // Parent setters/getters for "unknown" fields.
        sh.setUnknown14((short) 0);
        sh.setUnknown15((short) 0);
        sh.setUnknown16((short) 4);
        sh.setUnknown17((short) 0);
        sh.setUnknown18((short) 0);
        sh.setUnknown20((short) 0);
        sh.setUnknown21((short) 0);
        sh.setUnknown22((short) 12);
        sh.setUnknown23((short) 8);
        sh.setUnknown24((short) 0);
        sh.setUnknown26((short) 4);
        sh.setUnknown27((short) 1);
        sh.setUnknown28((byte) 0);
        sh.setColumnTextHeadersCount((short) 1);
        sh.setColumnNameMaxLength((short) 32);
        sh.setColumnLabelMaxLength((short) 40);
        sh.setFullPageRowsCount((short) 100);
        sh.setCreatorSoftwareOffset((short) 30);
        sh.setCreatorSoftwareLength((short) 8);
        sh.setCompressionMethodOffset((short) 40);
        sh.setCompressionMethodLength((short) 4);
        sh.setCreatorProcOffset((short) 50);
        sh.setCreatorProcLength((short) 8);
        return sh;
    }


    @Test
    void rowSizeSubHeader32_selfDefinedFields_haveCorrectGetters()
    {
        var sh = fullyPopulatedSubHeader32();
        assertEquals(28L, sh.getRowLength());
        assertEquals(100L, sh.getRowCount());
        assertEquals(2L, sh.getDeletedRowCount());
        assertEquals(5L, sh.getColumnCountP1());
        assertEquals(0L, sh.getColumnCountP2());
        assertEquals(5L, sh.getColumnCount());
        assertEquals(8192L, sh.getPageSize());
        assertEquals(3L, sh.getMixedPageRowCount());
        assertEquals(1, sh.getPageSequence().intValue());
        assertEquals(1, sh.getUnknown3().intValue());
        assertEquals((short) 2, sh.getUnknown4());
        assertEquals(2L, sh.getPagesWithSubHeadersCount());
        assertEquals(2L, sh.getPagesWithSubHeadersCountDuplicate());
        assertEquals(4L, sh.getPageCount());
        assertEquals((short) 22, sh.getUnknown8());
        assertEquals(0, sh.getUnknown10().intValue());
        assertEquals((short) 7, sh.getUnknown11());
    }


    @Test
    void rowSizeSubHeader32_parentGetters_returnConfiguredValues()
    {
        var sh = fullyPopulatedSubHeader32();
        assertEquals((short) 10, sh.getLabelOffset());
        assertEquals((short) 20, sh.getLabelLength());
        assertEquals((short) 30, sh.getCreatorSoftwareOffset());
        assertEquals((short) 8, sh.getCreatorSoftwareLength());
        assertEquals((short) 40, sh.getCompressionMethodOffset());
        assertEquals((short) 4, sh.getCompressionMethodLength());
        assertEquals((short) 50, sh.getCreatorProcOffset());
        assertEquals((short) 8, sh.getCreatorProcLength());
        assertEquals((short) 4, sh.getUnknown16());
        assertEquals((short) 0, sh.getUnknown14());
        assertEquals((short) 0, sh.getUnknown15());
        assertEquals((short) 0, sh.getUnknown17());
        assertEquals((short) 0, sh.getUnknown18());
        assertEquals((short) 0, sh.getUnknown20());
        assertEquals((short) 0, sh.getUnknown21());
        assertEquals((short) 12, sh.getUnknown22());
        assertEquals((short) 8, sh.getUnknown23());
        assertEquals((short) 0, sh.getUnknown24());
        assertEquals((short) 4, sh.getUnknown26());
        assertEquals((short) 1, sh.getUnknown27());
        assertEquals(Byte.valueOf((byte) 0), sh.getUnknown28());
        assertEquals(true, sh.getCompressed());
    }


    @Test
    void rowSizeSubHeader32_byteArrayAccessors_areNonNull()
    {
        var sh = fullyPopulatedSubHeader32();
        assertNotNull(sh.getUnknown1());
        assertNotNull(sh.getUnknown2());
        assertNotNull(sh.getUnknown5());
        assertNotNull(sh.getUnknown7());
        assertNotNull(sh.getUnknown9());
        assertNotNull(sh.getUnknown12());
        assertNotNull(sh.getUnknown13());
    }


    @Test
    void rowSizeSubHeader32_toString_surfacesKeyFields()
    {
        var sh = fullyPopulatedSubHeader32();
        String s = sh.toString();
        assertTrue(s.contains("rowLength=28"));
        assertTrue(s.contains("rowCount=100"));
    }


    private static net.cumba.sasutils.bdat.x64.RowSizeSubHeader64 fullyPopulatedSubHeader64()
    {
        net.cumba.sasutils.bdat.x64.RowSizeSubHeader64 sh = new net.cumba.sasutils.bdat.x64.RowSizeSubHeader64();
        sh.setRowLength(56L);
        sh.setRowCount(1000L);
        sh.setDeletedRowCount(0L);
        sh.unknown00 = 0L;
        sh.setColumnCountP1(10L);
        sh.setColumnCountP2(0L);
        sh.setPageSize(65536L);
        sh.setMixedPageRowCount(8L);
        sh.setPageSequence(1);
        sh.setUnknown3(1L);
        sh.setUnknown4((short) 2);
        sh.setPagesWithSubHeadersCount(3L);
        sh.setLastPageSubHeadersCount((short) 2);
        sh.setPagesWithSubHeadersCountDuplicate(3L);
        sh.setNumLastPageSubHeadersPlusTwo((short) 4);
        sh.setPageCount(8L);
        sh.setUnknown8((short) 22);
        sh.setUnknown10(0L);
        sh.setUnknown11((short) 8);
        sh.setUnknown0(new byte[32]);
        sh.setUnknown1(new byte[16]);
        sh.setUnknown2(new byte[8]);
        sh.setUnknown5(new byte[6]);
        sh.setUnknown6(new byte[6]);
        sh.setUnknown7(new byte[6]);
        sh.setUnknown9(new byte[6]);
        sh.setUnknown12(new byte[6]);
        sh.setUnknown13(new byte[80]);

        // Parent shorts to exercise toString
        sh.labelOffset = 10;
        sh.labelLength = 20;
        sh.creatorSoftwareLength = 8;
        sh.compressionMethodOffset = 40;
        sh.compressionMethodLength = 4;
        sh.creatorProcOffset = 50;
        sh.creatorProcLength = 8;
        sh.columnTextHeadersCount = 1;
        sh.columnNameMaxLength = 32;
        sh.columnLabelMaxLength = 40;
        sh.fullPageRowsCount = 100;
        return sh;
    }


    @Test
    void rowSizeSubHeader64_selfDefinedFields_haveCorrectGetters()
    {
        var sh = fullyPopulatedSubHeader64();
        assertEquals(56L, sh.getRowLength());
        assertEquals(1000L, sh.getRowCount());
        assertEquals(0L, sh.getDeletedRowCount());
        assertEquals(10L, sh.getColumnCountP1());
        assertEquals(0L, sh.getColumnCountP2());
        assertEquals(10L, sh.getColumnCount());
        assertEquals(65536L, sh.getPageSize());
        assertEquals(8L, sh.getMixedPageRowCount());
        assertEquals(1, sh.getPageSequence().intValue());
        assertEquals(1L, sh.getUnknown3());
        assertEquals((short) 2, sh.getUnknown4());
        assertEquals(3L, sh.getPagesWithSubHeadersCount());
        assertEquals(3L, sh.getPagesWithSubHeadersCountDuplicate());
        assertEquals(8L, sh.getPageCount());
        assertEquals((short) 22, sh.getUnknown8());
        assertEquals(0L, sh.getUnknown10());
        assertEquals((short) 8, sh.getUnknown11());
        assertEquals((short) 2, sh.getLastPageSubHeadersCount());
        assertEquals((short) 4, sh.getNumLastPageSubHeadersPlusTwo());
    }


    @Test
    void rowSizeSubHeader64_byteArrayAccessors_areNonNull()
    {
        var sh = fullyPopulatedSubHeader64();
        assertNotNull(sh.getUnknown0());
        assertNotNull(sh.getUnknown1());
        assertNotNull(sh.getUnknown2());
        assertNotNull(sh.getUnknown5());
        assertNotNull(sh.getUnknown6());
        assertNotNull(sh.getUnknown7());
        assertNotNull(sh.getUnknown9());
        assertNotNull(sh.getUnknown12());
        assertNotNull(sh.getUnknown13());
    }


    @Test
    void rowSizeSubHeader64_toString_surfacesKeyFields()
    {
        var sh = fullyPopulatedSubHeader64();
        String s = sh.toString();
        assertTrue(s.contains("rowLength=56"));
        assertTrue(s.contains("rowCount=1000"));
    }
}
