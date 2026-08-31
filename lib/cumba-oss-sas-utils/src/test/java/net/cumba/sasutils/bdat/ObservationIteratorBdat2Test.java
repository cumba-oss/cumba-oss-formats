package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises {@link ObservationIteratorBdat2} (and its inner block/sub-header iterators) against the
 * bundled sas7bdat fixtures. This mirrors the read path used by the SAS data-table provider:
 * {@code ParserBdat.parseDataset(file)} to obtain the dataset descriptor, then iterate the raw rows
 * from a fresh stream over the same file.
 */
class ObservationIteratorBdat2Test
{

    private static File fixture(String aPrefix)
    {
        File folder = new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdat");
        File[] files = folder.listFiles((_, n) -> n.startsWith(aPrefix) && n.endsWith(".sas7bdat"));
        assertNotNull(files, "fixture folder missing: " + folder);
        assertTrue(files.length > 0, "no fixture with prefix " + aPrefix);
        return files[0];
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "doubles_", "numeric1_", "numeric3_", "mixed1_", "mixed3_", "file_with_label_",
            "int_only_", "time_"
    })
    void iteratesAllNonDeletedRows(String aPrefix) throws IOException
    {
        File f = fixture(aPrefix);
        DatasetBdat ds = new ParserBdat().parseDataset(f);

        long expected = ds.getRowCount() - ds.getDeletedObservationCount();

        int count = 0;
        try (InputStream in = new FileInputStream(f))
        {
            ObservationIteratorBdat2 iter = new ObservationIteratorBdat2(ds, in);
            while (iter.hasNext())
            {
                byte[] row = iter.next();
                assertNotNull(row, "row bytes must not be null");
                assertTrue(row.length > 0, "row bytes must not be empty");
                count++;
            }
            assertEquals(expected, count, "iterated row count must match the dataset descriptor");
            assertEquals(ds.getDeletedObservationCount(), iter.getParsedDeletedRowCount(),
                    "deleted-row accounting must match the descriptor");
        }
    }


    @Test
    void nextThrowsWhenExhausted() throws IOException
    {
        File f = fixture("doubles_");
        DatasetBdat ds = new ParserBdat().parseDataset(f);
        try (InputStream in = new FileInputStream(f))
        {
            ObservationIteratorBdat2 iter = new ObservationIteratorBdat2(ds, in);
            while (iter.hasNext())
            {
                iter.next();
            }
            assertThrows(NoSuchElementException.class, iter::next);
        }
    }
}
