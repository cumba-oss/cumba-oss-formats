package net.cumba.sasutils.xpt;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.sasutils.Observation;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

/**
 * A member's observation section must end where the next member's {@value XptConstants#HEADER_TAG}
 * record begins.
 *
 * <p>
 * Oracle: the XPORT v5 record layout (TS-140). "All transport data set records are 80 bytes in
 * length. If there is not sufficient data to reach 80 bytes, then a record is padded with ASCII
 * blanks to 80 bytes"; of the member header records, "Both of these records occur for every member
 * in the transport file"; and of the data records, "There is ASCII blank padding at the end of the
 * last record if necessary. There is no special trailing record." So nothing marks the end of a
 * member's rows - the reader must recognise the next member's header record, and because the
 * padding runs to an 80-byte RECORD boundary rather than to an observation boundary, that tag can
 * begin at <em>any</em> offset inside an observation-sized read, including one where it is only
 * partly there.
 * </p>
 *
 * <p>
 * {@code threemembers_unpadded.xpt} was generated from that record layout - library header, then
 * per member a MEMBER/DSCRPTR pair, the member descriptor, a NAMESTR header and 140-byte namestrs,
 * an OBS header and the blank-padded data records - with observation sizes chosen so two of those
 * tag offsets actually occur:
 * </p>
 * <ul>
 * <li>FIRST: 2 numeric columns = 16-byte observations, 5 rows = exactly 80 bytes, so the data
 * section needs no padding at all and the 20-byte tag is <em>split</em> across two reads.</li>
 * <li>SECOND: 5 numeric columns = 40-byte observations, 2 rows = exactly 80 bytes, so the whole tag
 * sits inside the next read.</li>
 * <li>THIRD: a 5-byte character column, 4 rows = 20 bytes blank-padded to 80, and no member after
 * it - the ordinary end-of-stream case.</li>
 * </ul>
 *
 * <p>
 * The expected cell values are the ones written into the fixture, independently confirmed by
 * ReadStat (via pyreadstat 1.3, which reports member FIRST as {@code VAL1}/{@code VAL2} with
 * exactly these five rows). ⚠ Neither ReadStat nor pandas 3.0.1 stops at the member boundary either
 * - both read the following member's descriptor records as further rows of the first member - so
 * the row *count* is taken from the spec, not from them.
 * </p>
 */
class ObservationIteratorXptMemberBoundaryTest
{

    private static final String FIXTURE = "threemembers_unpadded.xpt";

    private static File fixture(String aName)
    {
        return new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/xpt/" + aName);
    }


    @Test
    void theFixtureHoldsThreeMembersWithTheDeclaredShapes() throws IOException
    {
        LibraryXpt library = new ParserXpt().parseLibrary(fixture(FIXTURE));

        assertEquals(List.of("FIRST", "SECOND", "THIRD"),
                library.getDatasets().stream().map(DatasetXpt::getName).toList());
        assertEquals(2, library.getDatasets().get(0).getVariables().size());
        assertEquals(5, library.getDatasets().get(1).getVariables().size());
        assertEquals(1, library.getDatasets().get(2).getVariables().size());
    }


    @Test
    void aMemberWhoseDataNeedsNoPaddingStopsAtTheNextMembersSplitHeaderTag() throws IOException
    {
        LibraryXpt library = new ParserXpt().parseLibrary(fixture(FIXTURE));
        DatasetXpt first = library.getDatasets().get(0);

        List<List<Object>> rows = rows(first);

        // 5 rows and not one more: the 6th read is 16 bytes of "HEADER RECORD***", the first
        // part of the tag that introduces member SECOND, and reading it as data yielded a
        // garbage row followed by the rest of the file read as rows of FIRST.
        assertEquals(5, rows.size(),
                "member FIRST has 5 observations, the next record is a header");
        assertEquals(List.of(1.0d, 10.5d), rows.get(0));
        assertEquals(List.of(2.0d, -20.25d), rows.get(1));
        assertEquals(List.of(3.0d, 0.0d), rows.get(2));
        assertEquals(List.of(4.0d, 1000000.0d), rows.get(3));
        assertEquals(List.of(5.0d, -0.125d), rows.get(4));
    }


    @Test
    void aMemberStopsAtAHeaderTagThatFitsWhollyInsideOneRead() throws IOException
    {
        LibraryXpt library = new ParserXpt().parseLibrary(fixture(FIXTURE));
        DatasetXpt second = library.getDatasets().get(1);

        List<List<Object>> rows = rows(second);

        assertEquals(2, rows.size(), "member SECOND has 2 observations");
        assertEquals(List.of(1.0d, 2.0d, 3.0d, 4.0d, 5.0d), rows.get(0));
        assertEquals(List.of(-1.5d, 2.25d, -3.75d, 4.5d, -5.625d), rows.get(1));
    }


    @Test
    void theLastMemberStillEndsAtItsBlankPadding() throws IOException
    {
        LibraryXpt library = new ParserXpt().parseLibrary(fixture(FIXTURE));
        DatasetXpt third = library.getDatasets().get(2);

        List<List<Object>> rows = rows(third);

        assertEquals(4, rows.size(), "member THIRD has 4 observations");
        assertEquals(List.of("alpha"), strip(rows.get(0)));
        assertEquals(List.of("beta"), strip(rows.get(1)));
        assertEquals(List.of("gamma"), strip(rows.get(2)));
        assertEquals(List.of("delta"), strip(rows.get(3)));
    }

    // ------------------------------------------------------------------ offsets the fixture
    // cannot reach, driven straight through the iterator


    @Test
    void aHeaderTagFillingTheWholeBufferIsNotAnObservation()
    {
        // observationSize == the tag length: the tag starts at index 0 and ends at the buffer's
        // last byte.
        DatasetXpt ds = mockDataset(XptConstants.HEADER_TAG.length());
        byte[] data = concat(fill(XptConstants.HEADER_TAG.length(), 'A'), tag());

        assertEquals(1, drain(new ObservationIteratorXpt(ds, new ByteArrayInputStream(data))));
    }


    @Test
    void aHeaderTagExactlyFillingTheRestOfTheBufferIsNotAnObservation()
    {
        // 25-byte observations with 5 bytes of padding: all 20 tag bytes are present, and the
        // tag ends exactly on the buffer's last byte.
        DatasetXpt ds = mockDataset(25);
        byte[] data = concat(fill(25, 'A'), fill(5, ' '), tag());

        assertEquals(1, drain(new ObservationIteratorXpt(ds, new ByteArrayInputStream(data))));
    }


    @Test
    void aHeaderTagSplitAcrossTwoReadsIsNotAnObservation()
    {
        // 30-byte observations with 20 bytes of padding: only the first 10 bytes of the tag are
        // in the buffer, the other 10 have to come from the stream.
        DatasetXpt ds = mockDataset(30);
        byte[] data = concat(fill(30, 'A'), fill(20, ' '), tag(), fill(60, ' '));

        assertEquals(1, drain(new ObservationIteratorXpt(ds, new ByteArrayInputStream(data))));
    }


    @Test
    void textThatMerelyStartsLikeTheHeaderTagIsStillAnObservation()
    {
        // The discriminator must stay the FULL 20-byte tag. A prefix match would end the member
        // early on an ordinary character value that happens to share the tag's opening bytes,
        // silently dropping every remaining row.
        DatasetXpt ds = mockDataset(25);
        byte[] lookalike = "HEADER RECORD**HELLO12345".getBytes(ISO_8859_1);
        assertEquals(25, lookalike.length);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(concat(fill(25, 'A'), lookalike)));
        assertEquals(2, drain(iter), "a tag look-alike is data, not a header");
    }


    @Test
    void bytesReadToCompleteASplitTagAreGivenBackWhenTheyDoNotCompleteIt()
    {
        // Completing a split tag reads ahead. When those bytes turn out not to complete the tag
        // they are observation data and must reappear in the following rows - otherwise the fix
        // for the split case would itself lose data.
        DatasetXpt ds = mockDataset(30);
        byte[] data = concat(fill(30, 'A'), fill(20, ' '), "HEADER REC".getBytes(ISO_8859_1),
                fill(30, 'B'));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        List<String> values = new ArrayList<>();
        while (iter.hasNext())
        {
            values.add(String.valueOf(iter.next().getValues().values().iterator().next()).strip());
        }

        assertEquals(3, values.size());
        assertEquals("A".repeat(30), values.get(0));
        assertEquals("HEADER REC", values.get(1));
        assertEquals("B".repeat(30), values.get(2));
    }


    @Test
    void theTagBytesTakenFromTheStreamAreGivenBackWhenTheyDidCompleteTheTag() throws IOException
    {
        // Completing a split tag consumes bytes of the header record itself. They are pushed back
        // too, so a caller that goes on reading the same stream - as a library scan for the next
        // member does - still finds the header record whole rather than ten bytes into it.
        DatasetXpt ds = mockDataset(30);
        byte[] data = concat(fill(30, 'A'), fill(20, ' '), tag(), fill(60, ' '));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        assertEquals(1, drain(iter));

        // 20 blanks plus the tag's first 10 bytes were in the buffer; the other 10 were read to
        // complete the comparison and must be back on the stream.
        byte[] rest = new byte[10];
        assertEquals(rest.length, IOUtils.read(iter.frames.input(), rest));
        assertEquals(XptConstants.HEADER_TAG.substring(10), new String(rest, ISO_8859_1));
    }


    @Test
    void aShortAllBlankTailIsTheCleanEndOfTheLastMember()
    {
        // The last data record is blank-padded to 80 bytes, so the final read of a member whose
        // observation size does not divide that padding comes back short and all blanks. That is
        // a normal end of file, not a defect.
        DatasetXpt ds = mockDataset(30);
        byte[] data = concat(fill(30, 'A'), fill(50, ' '));

        assertEquals(1, drain(new ObservationIteratorXpt(ds, new ByteArrayInputStream(data))));
    }


    @Test
    void aStreamEndingPartWayThroughAnObservationIsReportedInsteadOfDropped()
    {
        // Observation DATA in a short read means the file stops in the middle of an observation.
        // XPT carries no row count, so a silently dropped partial row is undetectable downstream;
        // the truncation has to surface.
        DatasetXpt ds = mockDataset(30);
        byte[] data = concat(fill(30, 'A'), fill(12, 'B'));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        assertNotNull(iter.next());
        IllegalStateException e = assertThrows(IllegalStateException.class, iter::hasNext);
        assertInstanceOf(IOException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("Truncated XPT file"),
                e.getCause().getMessage());
    }

    // ------------------------------------------------------------------ helpers


    private static List<List<Object>> rows(DatasetXpt aDataset) throws IOException
    {
        List<List<Object>> rows = new ArrayList<>();
        try (Stream<Observation> stream = aDataset.streamObservations())
        {
            stream.forEach(o -> rows.add(new ArrayList<>(o.getValues().values())));
        }
        return rows;
    }


    private static List<Object> strip(List<Object> aRow)
    {
        return aRow.stream().map(v -> (Object) String.valueOf(v).strip()).toList();
    }


    private static byte[] tag()
    {
        return XptConstants.HEADER_TAG.getBytes(ISO_8859_1);
    }


    private static byte[] fill(int aCount, char aFill)
    {
        byte[] b = new byte[aCount];
        Arrays.fill(b, (byte) aFill);
        return b;
    }


    private static byte[] concat(byte[]... aParts)
    {
        int len = 0;
        for (byte[] part : aParts)
        {
            len += part.length;
        }
        byte[] out = new byte[len];
        int at = 0;
        for (byte[] part : aParts)
        {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }


    private static int drain(ObservationIteratorXpt aIterator)
    {
        int n = 0;
        while (aIterator.hasNext())
        {
            assertNotNull(aIterator.next());
            n++;
        }
        return n;
    }


    private static DatasetXpt mockDataset(int aVarLength)
    {
        VariableXpt variable = new VariableXpt();
        variable.name = "V1";
        variable.variableTypeId = 2; // character
        variable.length = (short) aVarLength;

        DatasetXpt dataset = mock(DatasetXpt.class);
        when(dataset.getVariables()).thenReturn(List.of(variable));
        when(dataset.getObservationStartByte()).thenReturn(0L);
        return dataset;
    }
}
