package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * Additional tests for DsjTable.DsjTableBuilder methods.
 */
// Test exercises legacy java.util.Date code paths in the production code.
@SuppressWarnings("JavaUtilDate")
class DsjTableBuilderTest
{

    private DsjTableColumn makeCol(int index, String name)
    {
        return DsjTableColumn.builder().index(index).itemOID("IT." + name).name(name)
                .label(name + " label").dataType("string").build();
    }


    private DsjTable.DsjTableBuilder baseBuilder()
    {
        return DsjTable.builder().datasetJSONCreationDateTime("2025-01-01T00:00:00")
                .datasetJSONVersion("1.1.0").itemGroupOID("IG.DM").name("DM").label("Demographics")
                .columns(makeCol(0, "STUDYID"));
    }


    @Test
    void testSetDatasetJSONCreationDateTime()
    {
        Date date = new Date(0); // 1970-01-01T00:00:00 UTC
        DsjTable table = baseBuilder().setDatasetJSONCreationDateTime(date).build();
        assertNotNull(table.getDatasetJSONCreationDateTime());
    }


    @Test
    void testSetDbLastModifiedDateTime()
    {
        Date date = new Date(0);
        DsjTable table = baseBuilder().setDbLastModifiedDateTime(date).build();
        assertNotNull(table.getDbLastModifiedDateTime());
    }


    @Test
    void testAddColumnsEmpty()
    {
        DsjTable table = baseBuilder().addColumns(new DsjTableColumn[0]).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testAddColumnsNull()
    {
        DsjTable table = baseBuilder().addColumns((DsjTableColumn[]) null).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testAddColumnsListEmpty()
    {
        DsjTable table = baseBuilder().addColumns(Collections.emptyList()).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testAddColumnsListNull()
    {
        DsjTable table = baseBuilder().addColumns((List<DsjTableColumn>) null).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testColumnsListNull()
    {
        // Passing null list should clear columns
        DsjTable.DsjTableBuilder b = baseBuilder().columns((List<DsjTableColumn>) null);
        // Need to re-add at least one column since columns are mandatory
        DsjTable table = b.columns(makeCol(0, "X")).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testColumnsListEmpty()
    {
        DsjTable.DsjTableBuilder b = baseBuilder().columns(Collections.emptyList());
        // Need to re-add at least one column since columns are mandatory
        DsjTable table = b.columns(makeCol(0, "X")).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testColumnsWithWrongIndexThrows()
    {
        DsjTableColumn col0 = makeCol(0, "A");
        DsjTableColumn col2 = makeCol(2, "B"); // wrong index, should be 1

        assertThrows(IllegalArgumentException.class,
                () -> baseBuilder().clearColumns().columns(col0, col2).build());
    }


    @Test
    void testAddColumnsToNull()
    {
        // Start from cleared columns, then add
        DsjTable table = baseBuilder().clearColumns().addColumns(makeCol(0, "X")).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testSetDatasetJSONCreationDateTimeFromInstantIsUtc()
    {
        DsjTable table = baseBuilder()
                .setDatasetJSONCreationDateTime(Instant.parse("2025-06-15T10:30:00Z")).build();
        assertEquals("2025-06-15T10:30:00", table.getDatasetJSONCreationDateTime());
    }


    @Test
    void testSetDbLastModifiedDateTimeFromInstantIsUtc()
    {
        DsjTable table = baseBuilder()
                .setDbLastModifiedDateTime(Instant.parse("2023-05-31T00:00:00Z")).build();
        assertEquals("2023-05-31T00:00:00", table.getDbLastModifiedDateTime());
    }


    @Test
    void testDateAndInstantOverloadsAgreeRegardlessOfTheDefaultTimeZone()
    {
        // Dataset-JSON's creation timestamp carries no offset, so a value rendered in the writing
        // machine's local zone cannot be read back unambiguously — and the Date and Instant
        // overloads of the same setter used to disagree for exactly that reason. The default zone
        // is forced to a non-UTC one here so the assertion cannot pass vacuously on a UTC box.
        TimeZone saved = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Instant when = Instant.parse("2025-06-15T10:30:00Z");
            DsjTable fromDate = baseBuilder().setDatasetJSONCreationDateTime(Date.from(when))
                    .setDbLastModifiedDateTime(Date.from(when)).build();
            DsjTable fromInstant = baseBuilder().setDatasetJSONCreationDateTime(when)
                    .setDbLastModifiedDateTime(when).build();

            assertEquals("2025-06-15T10:30:00", fromDate.getDatasetJSONCreationDateTime());
            assertEquals("2025-06-15T10:30:00", fromDate.getDbLastModifiedDateTime());
            assertEquals(fromInstant.getDatasetJSONCreationDateTime(),
                    fromDate.getDatasetJSONCreationDateTime());
            assertEquals(fromInstant.getDbLastModifiedDateTime(),
                    fromDate.getDbLastModifiedDateTime());
        }
        finally
        {
            TimeZone.setDefault(saved);
        }
    }


    @Test
    void testColumnsFromNonEmptyList()
    {
        List<DsjTableColumn> cols = List.of(makeCol(0, "A"), makeCol(1, "B"));
        DsjTable table = baseBuilder().columns(cols).build();
        assertEquals(2, table.getColumnCount());
        assertEquals("A", table.getColumn(0).getName());
        assertEquals("B", table.getColumn(1).getName());
    }


    @Test
    void testAddColumnsFromNonEmptyList()
    {
        DsjTable table = baseBuilder().addColumns(List.of(makeCol(1, "SECOND"))).build();
        assertEquals(2, table.getColumnCount());
        assertEquals("SECOND", table.getColumn(1).getName());
    }


    @Test
    void testColumnSettersReturnTheSameBuilderForChaining()
    {
        // These are @Builder customisations, so nothing generated guarantees they return `this`;
        // a builder method that returns anything else breaks every chained call site.
        DsjTable.DsjTableBuilder b = DsjTable.builder();
        assertSame(b, b.columns(makeCol(0, "A")));
        assertSame(b, b.columns(List.of(makeCol(0, "A"))));
        assertSame(b, b.addColumns(makeCol(1, "B")));
        assertSame(b, b.addColumns(List.of(makeCol(2, "C"))));
        assertSame(b, b.clearColumns());
        assertSame(b, b.setDatasetJSONCreationDateTime(Instant.EPOCH));
        assertSame(b, b.setDbLastModifiedDateTime(Instant.EPOCH));
        assertSame(b, b.setDatasetJSONCreationDateTime(new Date(0L)));
        assertSame(b, b.setDbLastModifiedDateTime(new Date(0L)));
    }


    @Test
    void testColumnsWithAnEmptyArrayUnsetsThem()
    {
        // An empty column set is the same as no column set at all: columns(empty) is
        // clearColumns().
        // That is the builder's own convention, and it is a trap for any caller that means "this
        // table has zero variables" — build() then fails the @NonNull contract with a Lombok
        // message naming a field rather than the problem. The Dataset-JSON reader used to walk
        // into exactly that.
        DsjTable.DsjTableBuilder b = baseBuilder();
        assertSame(b, b.columns(new DsjTableColumn[0]));
        assertSame(b, b.columns(makeCol(0, "A")));
        assertEquals(1, b.build().getColumnCount());
    }
}
