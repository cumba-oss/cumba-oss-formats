package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import net.cumba.sasutils.FormatType;
import net.cumba.sasutils.Observation;
import net.cumba.sasutils.VariableType;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the values the XPT value and accessor classes report for real transport files, and covers
 * {@link ObservationIteratorXpt#nextNative()}, which no other test reaches.
 *
 * <p>
 * Every expected value was read out of the fixture itself. {@code twotables.xpt} holds two members,
 * AIR (144 rows x 2 numeric columns) and CLASS1 (19 rows x 5 mixed columns).
 */
class XptValueClassAccessorTest
{

    private static LibraryXpt twoTables;

    private static DatasetXpt air;

    @BeforeAll
    static void parseFixture() throws IOException
    {
        twoTables = new ParserXpt().parseLibrary(fixture("twotables.xpt"));
        air = twoTables.getDatasets().get(0);
    }


    private static File fixture(String name)
    {
        return new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/xpt/" + name);
    }


    private static VariableXpt variable(DatasetXpt dataset, String name)
    {
        return dataset.getVariables().stream().filter(v -> name.equals(v.getName())).findFirst()
                .orElseThrow(() -> new IllegalStateException("no column " + name));
    }

    // ---------------------------------------------------------------- DatasetXpt


    @Test
    void datasetXptReportsTheMemberHeaderFieldsOfTheFixture()
    {
        assertEquals("AIR", air.getName());
        assertEquals("airline data (monthly: JAN49-DEC60)", air.getLabel());
        // The member was written without a dataset type, which XPT stores as a blank field.
        assertEquals("", air.getType());
        assertEquals(LocalDateTime.parse("2021-01-20T10:48:57"), air.getCreated());
        assertEquals(LocalDateTime.parse("2021-01-20T10:48:57"), air.getModified());
        assertSame(twoTables, air.getLibrary());

        DatasetXpt class1 = twoTables.getDatasets().get(1);
        assertEquals("CLASS1", class1.getName());
        assertEquals("Student Data", class1.getLabel());
    }


    @Test
    void datasetXptSettersWriteThroughToTheMemberHeader() throws IOException
    {
        DatasetXpt dataset = new ParserXpt().parseLibrary(fixture("twotables.xpt")).getDatasets()
                .get(0);

        dataset.setName("RENAMED");
        assertEquals("RENAMED", dataset.getName());

        dataset.setLabel("new label");
        assertEquals("new label", dataset.getLabel());

        dataset.setType("DATA");
        assertEquals("DATA", dataset.getType());
    }

    // ---------------------------------------------------------------- LibraryXpt


    @Test
    void libraryXptDatesAreParsedFromItsOwnHeaderStrings()
    {
        LibraryHeaderXpt header = twoTables.getHeader();
        assertEquals(ParserXpt.parseDateTime(header.createdString), twoTables.getCreated());
        assertEquals(ParserXpt.parseDateTime(header.modifiedString), twoTables.getModified());
        assertEquals(LocalDateTime.parse("2021-01-20T10:48:57"), twoTables.getCreated());
        assertEquals(LocalDateTime.parse("2021-01-20T10:48:57"), twoTables.getModified());
    }


    @Test
    void setHeaderIsReadBackByGetHeader()
    {
        LibraryXpt library = new LibraryXpt(fixture("twotables.xpt"));
        LibraryHeaderXpt header = new LibraryHeaderXpt();
        library.setHeader(header);
        assertSame(header, library.getHeader());
    }

    // --------------------------------------------------------------- VariableXpt


    @Test
    void variableXptTypeFollowsTheOneBasedTypeIdForBothTypes()
    {
        // Type id 1 -> NUMERIC (index 0), type id 2 -> CHARACTER (index 1).
        VariableXpt numeric = variable(air, "AIR");
        assertEquals((short) 1, numeric.getVariableTypeId());
        assertEquals(VariableType.NUMERIC, numeric.getType());

        VariableXpt character = variable(twoTables.getDatasets().get(1), "NAME");
        assertEquals((short) 2, character.getVariableTypeId());
        assertEquals(VariableType.CHARACTER, character.getType());
    }


    @Test
    void variableXptRejectsATypeIdOutsideTheOneBasedRange()
    {
        VariableXpt variable = new VariableXpt();
        variable.setVariableTypeId((short) 0);
        assertThrows(IllegalArgumentException.class, variable::getType);

        // Id 3 is one past the last VariableType; it must be rejected, not indexed.
        variable.setVariableTypeId((short) 3);
        assertThrows(IllegalArgumentException.class, variable::getType);
    }


    @Test
    void variableXptReportsTheFormatAndPositionFieldsOfTheFixture()
    {
        VariableXpt date = variable(air, "DATE");
        VariableXpt value = variable(air, "AIR");

        // Neither column was written with decimals or a justification override.
        assertEquals((short) 0, date.getFormatDecimals());
        assertEquals((short) 0, date.getFormatJustifyId());
        assertEquals((short) 0, date.getInformatDecimals());
        assertEquals((short) 0, date.getNameHash());

        // DATE occupies the first eight record bytes, so AIR starts at offset 8.
        assertEquals(0, date.getPosition());
        assertEquals(8, value.getPosition());
    }


    @Test
    void formatXptMapsTheStoredFormatNameToAFormatType()
    {
        // DATE carries the MONYY format; AIR carries none, which maps to NUMERIC.
        assertEquals("MONYY", variable(air, "DATE").getFormatTypeString());
        assertEquals(FormatType.MONYY, variable(air, "DATE").getFormat().getType());
        assertEquals("", variable(air, "AIR").getFormatTypeString());
        assertEquals(FormatType.NUMERIC, variable(air, "AIR").getFormat().getType());
    }

    // ----------------------------------------------------- ObservationIteratorXpt


    @Test
    void nextNativeReturnsTheSameConvertedValuesAsNextForEveryRow() throws IOException
    {
        List<List<Object>> nativeRows = new ArrayList<>();
        try (RandomAccessFileInputStream is = twoTables.getRandomAccessFileInputStream())
        {
            ObservationIteratorXpt it = new ObservationIteratorXpt(air, is);
            while (it.hasNext())
            {
                nativeRows.add(new ArrayList<>(it.nextNative()));
            }
        }

        List<List<Object>> observationRows = new ArrayList<>();
        try (RandomAccessFileInputStream is = twoTables.getRandomAccessFileInputStream())
        {
            ObservationIteratorXpt it = new ObservationIteratorXpt(air, is);
            while (it.hasNext())
            {
                Observation observation = it.next();
                observationRows.add(new ArrayList<>(observation.getValues().values()));
            }
        }

        assertEquals(144, nativeRows.size(), "AIR holds 144 monthly rows");
        assertEquals(observationRows, nativeRows);
        // The IBM 370 hex floats are already Doubles, not the raw byte[] the struct unpacks.
        // -4017 is JAN1949 as a SAS date, and 112 is that month's passenger count.
        assertEquals(List.of(-4017.0d, 112.0d), nativeRows.get(0));
        assertEquals(List.of(-3986.0d, 118.0d), nativeRows.get(1));
    }


    @Test
    void nextNativeKeepsCharacterColumnsAsStringsAlongsideConvertedNumerics() throws IOException
    {
        DatasetXpt class1 = twoTables.getDatasets().get(1);
        try (RandomAccessFileInputStream is = twoTables.getRandomAccessFileInputStream())
        {
            ObservationIteratorXpt it = new ObservationIteratorXpt(class1, is);
            List<Object> row = it.nextNative();
            assertEquals(5, row.size());
            assertTrue(row.get(0) instanceof String, "NAME is a character column");
            assertTrue(row.get(2) instanceof Double, "AGE is a numeric column");
        }
    }


    @Test
    void nextNativeThrowsNoSuchElementOnceTheMemberIsExhausted() throws IOException
    {
        try (RandomAccessFileInputStream is = twoTables.getRandomAccessFileInputStream())
        {
            ObservationIteratorXpt it = new ObservationIteratorXpt(air, is);
            while (it.hasNext())
            {
                it.nextNative();
            }
            assertThrows(NoSuchElementException.class, it::nextNative);
        }
    }

}
