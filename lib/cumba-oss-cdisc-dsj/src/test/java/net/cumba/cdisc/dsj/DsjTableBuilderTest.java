package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Date;
import java.util.List;
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
}
