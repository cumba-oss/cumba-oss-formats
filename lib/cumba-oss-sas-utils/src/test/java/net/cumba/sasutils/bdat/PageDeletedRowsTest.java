package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The deleted-record bitmap and the data-area alignment that locates it.
 * <p>
 * ⚠ <strong>Why this is synthesised rather than taken from a fixture.</strong> No
 * <code>.sas7bdat</code> in this repository has a <code>MIXED2</code> or <code>DATA2</code> page,
 * and those are the only two page types that carry a deleted-record bitmap
 * ({@link PageHeader#hasDeletedRecords()}). The bitmap decides which rows a reader emits, so
 * getting it wrong means deleted subject records reappearing in a submission — or live ones
 * vanishing. The page is therefore built by hand on top of a real 32-bit dataset, which supplies
 * the row length and the header/pointer sizes the offset arithmetic depends on.
 * <p>
 * The 32-bit layout is what makes the alignment branch reachable at all: the data area starts at
 * <code>24 + 12 * subHeaderCount</code>, which is 4 mod 8 for an odd subheader count. The 64-bit
 * layout (<code>40 + 24n</code>) is always 0 mod 8.
 */
class PageDeletedRowsTest
{

    /** The row length of the fixture this test borrows its dataset from. */
    private static final int ROW_LENGTH = 21;

    /** 24 (page header) + 12 * 1 (one subheader pointer) = 36, which is 4 mod 8. */
    private static final int UNALIGNED_DATA_AREA_OFFSET = 36;

    private static final int ALIGNED_DATA_AREA_OFFSET = 40;

    private static final short SUB_HEADER_COUNT = 1;

    private static final short BLOCK_COUNT = 9;

    private static final int DATA_ROWS = BLOCK_COUNT - SUB_HEADER_COUNT;

    private static final long DELETED_POINTER = 100L;

    private DatasetBdat dataset;

    @BeforeEach
    void parseHostDataset(@TempDir Path dir) throws IOException
    {
        Path file = dir.resolve("host.sas7bdat");
        try (InputStream in = PageDeletedRowsTest.class
                .getResourceAsStream("data_page_with_deleted_3x997.sas7bdat"))
        {
            assertNotNull(in, "fixture missing");
            Files.copy(in, file);
        }
        dataset = new ParserBdat().parseDataset(file.toFile());
        // the constants above are only correct for this dataset's geometry
        assertEquals(ROW_LENGTH, dataset.rowSizeSubHeader.getRowLength().intValue());
        assertEquals(24, dataset.getPageHeaderByteCount());
        assertEquals(12, dataset.getSubHeaderPointerByteCount());
    }

    /** A page header of the given type, recording the byte order it is asked for. */
    private static final class RecordingHeader extends PageHeader
    {

        private final List<ByteOrder> deletedPointerRequests = new ArrayList<>();

        RecordingHeader(int pageTypeId, short blocks, short subHeaders)
        {
            setPageTypeId((short) pageTypeId);
            setBlockCount(blocks);
            setSubHeaderCount(subHeaders);
        }


        @Override
        public long getDeletedPointer(ByteOrder byteOrder)
        {
            deletedPointerRequests.add(byteOrder);
            return DELETED_POINTER;
        }
    }

    /**
     * Builds a page whose buffer holds <code>pad</code> at the unaligned data-area offset and the
     * given bitmap byte where {@code readDeletedMarkers()} should look for it.
     */
    private Page page(int pageTypeId, byte[] pad, int bitmapOffset, byte bitmap)
    {
        Page page = new Page(dataset);
        page.setHeader(new RecordingHeader(pageTypeId, BLOCK_COUNT, SUB_HEADER_COUNT));
        page.setSubHeaderPointers(new ArrayList<>());
        byte[] buffer = new byte[1024];
        System.arraycopy(pad, 0, buffer, UNALIGNED_DATA_AREA_OFFSET, pad.length);
        buffer[bitmapOffset] = bitmap;
        page.pageBuffer = new SeekableByteArrayInputStream(buffer);
        return page;
    }


    private static byte[] filler(byte b)
    {
        return new byte[]
        {
                b, b, b, b
        };
    }

    // ------------------------------------------------------------------
    // Data-area alignment
    // ------------------------------------------------------------------


    /**
     * Four NUL bytes at the candidate position are SAS's alignment padding, so the data area starts
     * after them.
     */
    @Test
    void dataAreaSkipsFourBytesOfNulPadding()
    {
        Page page = page(PageType.MIXED1.id, filler((byte) 0x00), 512, (byte) 0);
        assertEquals(ALIGNED_DATA_AREA_OFFSET, page.getDataAreaOffset());
    }


    /** Four spaces are padding too — some writers blank-fill instead of zero-filling. */
    @Test
    void dataAreaSkipsFourBytesOfSpacePadding()
    {
        Page page = page(PageType.MIXED1.id, filler((byte) 0x20), 512, (byte) 0);
        assertEquals(ALIGNED_DATA_AREA_OFFSET, page.getDataAreaOffset());
    }


    /**
     * Anything else is row data, not padding: Stat/Transfer omits the padding, and skipping four
     * bytes that are not there would shift every row by four bytes.
     */
    @Test
    void dataAreaDoesNotSkipBytesThatAreNotPadding()
    {
        Page page = page(PageType.MIXED1.id, new byte[]
        {
                0x00, 0x00, 0x00, 0x41
        }, 512, (byte) 0);
        assertEquals(UNALIGNED_DATA_AREA_OFFSET, page.getDataAreaOffset(),
                "a non-filler fourth byte means there is no padding to skip");
    }


    @Test
    void dataAreaDoesNotSkipAMixOfNulsAndSpaces()
    {
        Page page = page(PageType.MIXED1.id, new byte[]
        {
                0x20, 0x20, 0x00, 0x00
        }, 512, (byte) 0);
        assertEquals(UNALIGNED_DATA_AREA_OFFSET, page.getDataAreaOffset());
    }

    // ------------------------------------------------------------------
    // The deleted-record bitmap
    // ------------------------------------------------------------------


    /**
     * The bitmap sits at <code>dataAreaOffset + dataRows * rowLength + deletedPointer</code> and is
     * rendered most-significant bit first, one character per data row.
     */
    @Test
    void readsTheDeletedBitmapFromTheAlignedDataArea()
    {
        int bitmapOffset = (int) (ALIGNED_DATA_AREA_OFFSET + (long) DATA_ROWS * ROW_LENGTH
                + DELETED_POINTER);
        assertEquals(308, bitmapOffset, "40 + 8 * 21 + 100");

        Page page = page(PageType.MIXED2.id, filler((byte) 0x00), bitmapOffset, (byte) 0x51);
        page.readDeletedMarkers();

        // (8 rows + 7) / 8 = one byte, 0x51 = 0101 0001
        assertEquals("01010001", page.getDeletedMarkers());
    }


    @Test
    void marksExactlyTheRowsWhoseBitIsSet()
    {
        Page page = page(PageType.MIXED2.id, filler((byte) 0x00), 308, (byte) 0x51);
        page.readDeletedMarkers();

        boolean[] expected =
        {
                false, true, false, true, false, false, false, true
        };
        for (int row = 0; row < expected.length; row++)
        {
            int index = row;
            assertEquals(expected[row], page.isBlockRowDeleted(row),
                    () -> "row " + index + " of 01010001");
        }
    }


    /** An index outside the bitmap is not deleted — it is not "deleted by default" either way. */
    @Test
    void rowsOutsideTheBitmapAreNotDeleted()
    {
        Page page = page(PageType.MIXED2.id, filler((byte) 0x00), 308, (byte) 0xFF);
        page.readDeletedMarkers();

        assertEquals("11111111", page.getDeletedMarkers());
        assertTrue(page.isBlockRowDeleted(7), "the last row in the bitmap is deleted");
        assertFalse(page.isBlockRowDeleted(8), "one past the bitmap");
        assertFalse(page.isBlockRowDeleted(-1), "before the bitmap");
        assertFalse(page.isBlockRowDeleted(Long.MAX_VALUE));
    }


    /**
     * A page type that cannot hold deleted records must leave the markers unset, so
     * {@code isBlockRowDeleted} answers false for every row rather than reading a bitmap that is
     * not there.
     */
    @Test
    void pageTypesWithoutDeletedRecordsHaveNoBitmap()
    {
        for (PageType type : new PageType[]
        {
                PageType.MIXED1, PageType.DATA, PageType.META
        })
        {
            Page page = page(type.id, filler((byte) 0x00), 308, (byte) 0xFF);
            page.readDeletedMarkers();
            assertNull(page.getDeletedMarkers(), () -> type + " must not carry a bitmap");
            assertFalse(page.isBlockRowDeleted(0), () -> type + " row 0");
        }
    }


    /** The bitmap is read with the file's byte order, not the platform's. */
    @Test
    void theBitmapPointerIsReadWithTheFilesByteOrder()
    {
        Page page = page(PageType.MIXED2.id, filler((byte) 0x00), 308, (byte) 0x00);
        page.readDeletedMarkers();

        RecordingHeader header = (RecordingHeader) page.header;
        assertEquals(List
                .of(dataset.header1.littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN),
                header.deletedPointerRequests);
    }


    @Test
    void toStringNamesTheBlockAndSubHeaderCounts()
    {
        Page page = page(PageType.MIXED2.id, filler((byte) 0x00), 308, (byte) 0x00);
        String text = page.toString();
        assertTrue(text.contains("getBlockCount()=" + BLOCK_COUNT), text);
        assertTrue(text.contains("getSubHeaderCount()=" + SUB_HEADER_COUNT), text);
    }

}
