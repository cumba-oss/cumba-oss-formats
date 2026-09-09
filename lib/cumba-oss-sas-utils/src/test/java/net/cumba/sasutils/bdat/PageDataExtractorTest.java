package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.cumba.sasutils.PositionAwareInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that {@link PageDataExtractor} over {@link BdatPageProducer} reproduces, for every
 * committed fixture, exactly the row sequence the public {@link ObservationIteratorBdat2} yields,
 * and that the row count / deleted count match the dataset metadata.
 */
class PageDataExtractorTest
{

    private static byte[] load(String aName) throws IOException
    {
        try (InputStream in = PageDataExtractorTest.class.getResourceAsStream(aName + ".sas7bdat"))
        {
            assertNotNull(in, "fixture missing: " + aName);
            return in.readAllBytes();
        }
    }


    private static DatasetBdat parse(byte[] aBytes) throws IOException
    {
        return new ParserBdat().parseDataset(new ByteArrayInputStream(aBytes));
    }


    /** Collect all rows by driving the producer + extractor directly. */
    private static List<byte[]> extractAll(DatasetBdat aDataset, byte[] aBytes, long[] aDeletedOut)
        throws IOException
    {
        List<byte[]> rows = new ArrayList<>();
        long deleted = 0;
        BdatPageProducer producer = new BdatPageProducer(aDataset,
                new PositionAwareInputStream(new ByteArrayInputStream(aBytes)));
        while (producer.hasNext())
        {
            PageDataExtractor.PageRows pr = PageDataExtractor.extractRows(aDataset,
                    producer.next());
            rows.addAll(pr.rows());
            deleted += pr.deletedCount();
        }
        aDeletedOut[0] = deleted;
        return rows;
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "comp_deleted_4x3", "data_page_with_deleted_3x997", "mix_data_misc_14x12288",
            "mixed4_13x15564", "numeric1_15x4733", "doubles_12x25", "int_only_25x52", "time_23x435"
    })
    void extractionMatchesMetadataAndIterator(String aFixture) throws IOException
    {
        byte[] bytes = load(aFixture);

        // Direct producer + extractor pass.
        DatasetBdat datasetA = parse(bytes);
        long[] deletedOut = new long[1];
        List<byte[]> direct = extractAll(datasetA, bytes, deletedOut);

        // Ground truth: the number of physically present (non-deleted) rows always equals the
        // declared row count minus the declared deleted count — whether the deleted rows are
        // marker-skipped (uncompressed) or physically removed on rewrite (compressed).
        long expectedRows = datasetA.getRowCount() - datasetA.getDeletedObservationCount();
        assertEquals(expectedRows, direct.size(), "row count mismatch for " + aFixture);

        // Every decompressed row must be the full dataset row length.
        int rowLength = Math.toIntExact(datasetA.getRowLength());
        for (byte[] row : direct)
        {
            assertEquals(rowLength, row.length, "row length mismatch for " + aFixture);
        }

        // Parity: the public iterator yields the same rows in the same order.
        DatasetBdat datasetB = parse(bytes);
        ObservationIteratorBdat2 iter = new ObservationIteratorBdat2(datasetB,
                new PositionAwareInputStream(new ByteArrayInputStream(bytes)));
        List<byte[]> viaIterator = new ArrayList<>();
        while (iter.hasNext())
        {
            viaIterator.add(iter.next());
        }

        assertEquals(direct.size(), viaIterator.size(), "iterator row count mismatch");
        for (int i = 0; i < direct.size(); i++)
        {
            assertArrayEquals(direct.get(i), viaIterator.get(i),
                    "row " + i + " mismatch for " + aFixture);
        }
        assertEquals(deletedOut[0], iter.getParsedDeletedRowCount(),
                "iterator deleted count mismatch for " + aFixture);
    }


    /**
     * Uncompressed deleted records are physically present but marked, so the extractor must skip
     * and count exactly the declared number of deleted rows.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "data_page_with_deleted_3x997"
    })
    void markerBasedDeletedRecordsAreSkippedAndCounted(String aFixture) throws IOException
    {
        byte[] bytes = load(aFixture);
        DatasetBdat dataset = parse(bytes);
        long[] deletedOut = new long[1];
        List<byte[]> rows = extractAll(dataset, bytes, deletedOut);

        assertTrue(dataset.getDeletedObservationCount() > 0,
                "fixture should declare deleted records: " + aFixture);
        assertEquals(dataset.getDeletedObservationCount(), deletedOut[0]);
        assertEquals(dataset.getRowCount() - dataset.getDeletedObservationCount(), rows.size());
    }


    /**
     * In a compressed file a deleted record is physically removed on rewrite (compressed rows are
     * stored as DATA subheaders, not block rows), so there is no deleted-marker to skip: the
     * marker-based deleted count is zero even though the metadata still declares a deleted row. The
     * physically present row count remains correct. This documents (and pins) the pre-existing
     * behaviour the sequential iterator has always produced.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "comp_deleted_4x3"
    })
    void compressedDeletedRecordsArePhysicallyAbsent(String aFixture) throws IOException
    {
        byte[] bytes = load(aFixture);
        DatasetBdat dataset = parse(bytes);
        long[] deletedOut = new long[1];
        List<byte[]> rows = extractAll(dataset, bytes, deletedOut);

        assertTrue(dataset.getDeletedObservationCount() > 0,
                "fixture should declare deleted records: " + aFixture);
        assertEquals(0, deletedOut[0], "compressed rewrite leaves no deleted-marker to skip");
        assertEquals(dataset.getRowCount() - dataset.getDeletedObservationCount(), rows.size());
    }
}
