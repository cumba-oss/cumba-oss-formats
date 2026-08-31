package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.cumba.sasutils.Observation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression for the legacy {@link ObservationIteratorBdat} (the iterator behind the public
 * {@code Dataset.streamObservations()} API): it must skip deleted records, not surface them as live
 * observations.
 */
class ObservationIteratorBdatDeletedTest
{

    @Test
    void streamObservationsSkipsDeletedRecords(@TempDir Path aDir) throws IOException
    {
        Path file = aDir.resolve("deleted.sas7bdat");
        Files.write(file, load("data_page_with_deleted_3x997"));

        DatasetBdat dataset = new ParserBdat().parseDataset(file.toFile());
        assertTrue(dataset.getDeletedObservationCount() > 0,
                "fixture must declare deleted records");

        long expected = dataset.getRowCount() - dataset.getDeletedObservationCount();
        long actual;
        try (Stream<Observation> observations = dataset.streamObservations(file.toFile()))
        {
            actual = observations.count();
        }

        assertEquals(expected, actual,
                "streamObservations() must skip the deleted records, not emit them");
    }


    /**
     * Regression for the page-advance loop ({@code advanceToNextNonEmptyPage}): when an entire
     * storage page is deleted, {@link ObservationIteratorBdat.DataBlockIterator} skips all of its
     * rows, so that page yields an <em>empty</em> chained iterator. The iterator must keep
     * advancing to subsequent pages instead of terminating early (which would silently drop the
     * live rows that follow the fully-deleted page).
     *
     * <p>
     * Disabled until the fixture exists. The required fixture
     * ({@code data_pages_fully_deleted_then_live.sas7bdat}) is a multi-page uncompressed SAS7BDAT
     * where at least one whole storage page is 100&nbsp;% deleted and at least one <em>later</em>
     * page still holds live observations. It cannot be synthesised without SAS — generate it there
     * (e.g. write &gt; 2 pages of rows, then {@code PROC SQL}/modify to delete every row on an
     * early page, leaving later pages intact), drop it next to the other fixtures in this package's
     * test resources, then remove {@link Disabled}. The single-page
     * {@code data_page_with_deleted_3x997} does not exercise the loop-back branch
     * ({@code ObservationIteratorBdat.java:158/162}).
     */
    @Test
    @Disabled("needs multi-page fixture with a fully-deleted page followed by a live page; "
            + "see Javadoc — cannot be synthesised without SAS")
    void streamObservationsSpansFullyDeletedPage(@TempDir Path aDir) throws IOException
    {
        Path file = aDir.resolve("fully-deleted-page.sas7bdat");
        Files.write(file, load("data_pages_fully_deleted_then_live"));

        DatasetBdat dataset = new ParserBdat().parseDataset(file.toFile());
        assertTrue(dataset.getDeletedObservationCount() > 0,
                "fixture must declare deleted records");

        long expected = dataset.getRowCount() - dataset.getDeletedObservationCount();
        long actual;
        try (Stream<Observation> observations = dataset.streamObservations(file.toFile()))
        {
            actual = observations.count();
        }

        assertEquals(expected, actual, "iteration must span the fully-deleted page and still emit "
                + "every live row on the pages that follow it");
    }


    private static byte[] load(String aName) throws IOException
    {
        try (InputStream in = ObservationIteratorBdatDeletedTest.class
                .getResourceAsStream(aName + ".sas7bdat"))
        {
            assertNotNull(in, "fixture missing: " + aName);
            return in.readAllBytes();
        }
    }
}
