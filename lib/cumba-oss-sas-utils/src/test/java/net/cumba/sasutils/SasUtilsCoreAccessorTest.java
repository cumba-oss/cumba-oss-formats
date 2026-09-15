package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.cumba.sasutils.bdat.DatasetBdat;
import net.cumba.sasutils.bdat.LibraryBdat;
import net.cumba.sasutils.bdat.ParserBdat;
import org.junit.jupiter.api.Test;

/**
 * Pins the accessors of the format-independent core classes ({@link Dataset}, {@link Library},
 * {@link Observation}, {@link SasConstants}) against a real BDAT fixture.
 *
 * <p>
 * {@code file_with_label_2x2.sas7bdat} is used throughout because it is the smallest fixture: 2
 * columns x 2 rows.
 */
class SasUtilsCoreAccessorTest
{

    private static final String SMALL = "file_with_label_2x2.sas7bdat";

    private static File fixture(String name)
    {
        return new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdat/" + name);
    }

    // ------------------------------------------------------------------- Dataset


    @Test
    void getLibraryReturnsTheLibraryThatWasHandedToTheConstructor() throws IOException
    {
        DatasetBdat parsed = new ParserBdat().parseDataset(fixture(SMALL));
        Library library = parsed.getLibrary();
        assertEquals(fixture(SMALL), library.getFile());
        // Same instance on every call - it is a plain field read, not a lookup.
        assertSame(library, parsed.getLibrary());

        assertNull(new DatasetBdat((LibraryBdat) null).getLibrary());
    }


    @Test
    void streamObservationsWithoutAFileReadsThroughTheLibrarysOwnFile() throws IOException
    {
        DatasetBdat parsed = new ParserBdat().parseDataset(fixture(SMALL));
        List<Observation> observations = new ArrayList<>();
        try (Stream<Observation> stream = parsed.streamObservations())
        {
            stream.forEach(observations::add);
        }
        // The fixture name encodes the shape: 2 columns x 2 rows.
        assertEquals(2, observations.size());
        assertEquals(2, observations.get(0).getValues().size());
    }


    @Test
    void streamObservationsFailsWhenTheDatasetHasNoLibraryToReadFrom()
    {
        DatasetBdat orphan = new DatasetBdat((LibraryBdat) null);
        assertThrows(IllegalStateException.class, orphan::streamObservations);
    }

    // ------------------------------------------------------------------- Library


    @Test
    void getFileReturnsTheFileTheLibraryWasOpenedOn() throws IOException
    {
        File file = fixture(SMALL);
        LibraryBdat library = new ParserBdat().parseLibrary(file);
        assertEquals(file, library.getFile());
        assertSame(library.getFile(), library.getFile());
    }

    // --------------------------------------------------------------- Observation


    @Test
    void getValuesExposesEveryValuePutOnTheObservationInInsertionOrder() throws IOException
    {
        DatasetBdat parsed = new ParserBdat().parseDataset(fixture(SMALL));
        Variable first = parsed.getVariables().get(0);
        Variable second = parsed.getVariables().get(1);

        Observation observation = new Observation();
        observation.putValue(first, 1.0d);
        observation.putValue(second, null);

        Map<Variable, Object> values = observation.getValues();
        assertEquals(2, values.size());
        assertEquals(List.of(first, second), new ArrayList<>(values.keySet()));
        assertEquals(1.0d, values.get(first));
        assertNull(values.get(second));
        // getValue() and getValues() must agree; both read the same map.
        assertEquals(1.0d, observation.getValue(first));
    }


    @Test
    void getValuesOfAParsedObservationIsKeyedByTheDatasetsOwnVariables() throws IOException
    {
        DatasetBdat parsed = new ParserBdat().parseDataset(fixture(SMALL));
        try (Stream<Observation> stream = parsed.streamObservations())
        {
            Observation first = stream.findFirst().orElseThrow();
            assertEquals(new ArrayList<>(parsed.getVariables()),
                    new ArrayList<>(first.getValues().keySet()));
        }
    }

    // -------------------------------------------------------------- SasConstants


    @Test
    void debugBytesLeavesTheStreamPositionWhereItFoundIt() throws IOException
    {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "ABCDEFGH".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals('A', in.read());
        SasConstants.debugBytes(in, 4);
        // mark/reset put the cursor back, so the next byte is still the one after 'A'.
        assertEquals('B', in.read());
    }


    @Test
    void debugBytesFailsWhenFewerThanTheRequestedBytesRemain()
    {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "ABC".getBytes(StandardCharsets.ISO_8859_1));
        // It reads the requested count fully; a short stream is an error, not a silent no-op.
        assertThrows(EOFException.class, () -> SasConstants.debugBytes(in, 8));
    }

}
