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
import net.cumba.sasutils.bdat.x32.SubHeaderPointer32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link Page#hasObservations()} — one row per page type, which is the whole point of this class.
 * <p>
 * ⚠ <strong>Why it was left unpinned until now.</strong> The method used to read
 * {@code getPageType() == PageType.DATA && blockCount > 0}, and a test written against that would
 * have pinned <strong>false</strong> for {@code DATA2}, {@code MIXED1} and {@code MIXED2} — all
 * three of which carry rows. The page type is a bit field and ReadStat masks it before comparing
 * ({@code (page_type & SAS_PAGE_TYPE_MASK) == SAS_PAGE_TYPE_DATA}, mask {@code 0x0F00},
 * <code>readstat_sas7bdat_read.c:923</code>), so {@code DATA2} (0x180) is a data page and
 * {@code MIXED2} (0x280) a mix page; the 0x80 bit only says the page also holds deleted records.
 * Corrected and pinned per owner ruling Q2, 2026-09-11.
 * <p>
 * The dataset underneath is a real fixture, because a {@code MIXED} page's row count comes from the
 * row-size subheader ({@code min(mixedPageRowCount, rowCount)}) and a synthetic one would let the
 * test choose its own answer.
 */
class PageHasObservationsTest
{

    private static final short BLOCK_COUNT = 9;

    private DatasetBdat dataset;

    @BeforeEach
    void parseHostDataset(@TempDir Path dir) throws IOException
    {
        Path file = dir.resolve("host.sas7bdat");
        try (InputStream in = PageHasObservationsTest.class
                .getResourceAsStream("data_page_with_deleted_3x997.sas7bdat"))
        {
            assertNotNull(in, "fixture missing");
            Files.copy(in, file);
        }
        dataset = new ParserBdat().parseDataset(file.toFile());
        // A MIXED page reports min(mixedPageRowCount, rowCount); both must be non-zero for the
        // MIXED rows below to mean anything at all.
        assertTrue(dataset.rowSizeSubHeader.getMixedPageRowCount() > 0,
                "the host fixture must declare a mixed-page row count");
        assertTrue(dataset.rowSizeSubHeader.getRowCount() > 0, "the host fixture must have rows");
    }

    private static final class StubHeader extends PageHeader
    {

        StubHeader(int pageTypeId, short blocks, short subHeaders)
        {
            setPageTypeId((short) pageTypeId);
            setBlockCount(blocks);
            setSubHeaderCount(subHeaders);
        }


        @Override
        public long getDeletedPointer(ByteOrder byteOrder)
        {
            return 0L;
        }
    }

    private Page page(int pageTypeId, short blockCount, List<SubHeaderPointer> pointers)
    {
        Page page = new Page(dataset);
        page.setHeader(new StubHeader(pageTypeId, blockCount, (short) pointers.size()));
        page.setSubHeaderPointers(pointers);
        return page;
    }


    /** A pointer to a DATA subheader: one compressed row stored in the page's metadata area. */
    private static List<SubHeaderPointer> oneCompressedRow()
    {
        SubHeaderPointer32 pointer = new SubHeaderPointer32();
        pointer.pageOffset = 0;
        pointer.length = 1;
        pointer.setSignature(SubHeaderSignature.DATA);
        List<SubHeaderPointer> pointers = new ArrayList<>();
        pointers.add(pointer);
        return pointers;
    }


    /**
     * Every page type this reader knows, with a block count set. The four data-bearing types must
     * answer true and the metadata-only types false — {@code DATA2}, {@code MIXED1} and
     * {@code MIXED2} are the three the old implementation got wrong.
     */
    @ParameterizedTest(name = "page type {0} ({1}) -> hasObservations={2}")
    @CsvSource(
    {
            "META,0,false", "META2,16384,false", "CMETA,128,false", "AMD,1024,false",
            "COMP,-28672,false", "DATA,256,true", "DATA2,384,true", "MIXED1,512,true",
            "MIXED2,640,true"
    })
    void hasObservations_answersPerPageType(String name, int pageTypeId, boolean expected)
    {
        assertEquals(PageType.valueOf(name), PageType.fromId(pageTypeId),
                "the id in this row does not name the page type it claims to");
        assertEquals(expected, page(pageTypeId, BLOCK_COUNT, new ArrayList<>()).hasObservations(),
                () -> "hasObservations() for " + name);
    }


    /** A data page whose block count is zero holds no rows, whichever of the two types it is. */
    @ParameterizedTest(name = "empty page type {0}")
    @CsvSource(
    {
            "256", "384"
    })
    void hasObservations_isFalseForADataPageWithNoBlocks(int pageTypeId)
    {
        assertFalse(page(pageTypeId, (short) 0, new ArrayList<>()).hasObservations());
    }


    /**
     * A compressed dataset stores its rows in DATA subheaders on otherwise-metadata pages. Those
     * rows count: this is the second half of {@link Page#getTotalObservationCount()}, and the old
     * implementation missed it as well.
     */
    @Test
    void hasObservations_isTrueForAMetaPageCarryingCompressedRows()
    {
        Page page = page(0, (short) 0, oneCompressedRow());
        assertEquals(Integer.valueOf(1), page.getHeaderObservationCount());
        assertTrue(page.hasObservations(), "a META page with a DATA subheader carries a row");
    }


    /** An unrecognised page-type id carries nothing this reader can extract. */
    @Test
    void hasObservations_isFalseForAnUnknownPageType()
    {
        Page page = page(999, BLOCK_COUNT, new ArrayList<>());
        assertNull(page.getPageType(),
                "999 must stay an unknown page type for this test to mean" + " what it says");
        assertFalse(page.hasObservations());
    }

}
