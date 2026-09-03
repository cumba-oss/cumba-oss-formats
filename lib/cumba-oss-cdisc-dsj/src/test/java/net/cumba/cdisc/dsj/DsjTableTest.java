package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DsjTableTest
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
                .columns(makeCol(0, "STUDYID"), makeCol(1, "USUBJID"));
    }


    @Test
    void testBuilder()
    {
        DsjTable table = baseBuilder().build();

        assertEquals("2025-01-01T00:00:00", table.getDatasetJSONCreationDateTime());
        assertEquals("1.1.0", table.getDatasetJSONVersion());
        assertEquals("IG.DM", table.getItemGroupOID());
        assertEquals("DM", table.getName());
        assertEquals("Demographics", table.getLabel());
    }


    @Test
    void testGetColumnCount()
    {
        DsjTable table = baseBuilder().build();
        assertEquals(2, table.getColumnCount());
    }


    @Test
    void testGetColumn()
    {
        DsjTable table = baseBuilder().build();
        assertEquals("STUDYID", table.getColumn(0).getName());
        assertEquals("USUBJID", table.getColumn(1).getName());
    }


    @Test
    void testGetColumnsReturnsDefensiveCopy()
    {
        DsjTable table = baseBuilder().build();
        DsjTableColumn[] cols = table.getColumns();
        cols[0] = null;
        // internal array should not be affected
        assertNotNull(table.getColumn(0));
    }


    @Test
    void testGetColumnsStream()
    {
        DsjTable table = baseBuilder().build();
        assertEquals(2, table.getColumnsStream().count());
    }


    @Test
    void testRecordsDefault()
    {
        DsjTable table = baseBuilder().build();
        assertEquals(-1, table.getRecords());
    }


    @Test
    void testRecordsSet()
    {
        DsjTable table = baseBuilder().records(100).build();
        assertEquals(100, table.getRecords());
    }


    @Test
    void testOptionalFields()
    {
        DsjTable table = baseBuilder().fileOID("FILE1").originator("TestOrg").studyOID("STUDY1")
                .metaDataRef("http://example.com").metaDataVersionOID("MDV1")
                .dbLastModifiedDateTime("2025-01-02T00:00:00")
                .sourceSystem(DsjSourceSystem.builder().name("SYS").version("1.0").build()).build();

        assertEquals("FILE1", table.getFileOID());
        assertEquals("TestOrg", table.getOriginator());
        assertEquals("STUDY1", table.getStudyOID());
        assertNotNull(table.getSourceSystem());
    }


    @Test
    void testAddColumns()
    {
        DsjTable table = baseBuilder().addColumns(makeCol(2, "AGE")).build();
        assertEquals(3, table.getColumnCount());
        assertEquals("AGE", table.getColumn(2).getName());
    }


    @Test
    void testAddColumnsList()
    {
        List<DsjTableColumn> extras = Arrays.asList(makeCol(2, "AGE"), makeCol(3, "SEX"));
        DsjTable table = baseBuilder().addColumns(extras).build();
        assertEquals(4, table.getColumnCount());
    }


    @Test
    void testClearColumns()
    {
        DsjTableColumn col = makeCol(0, "X");
        DsjTable table = baseBuilder().clearColumns().columns(col).build();
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void testColumnsValidationBadIndex()
    {
        DsjTableColumn col = makeCol(5, "BAD");
        assertThrows(IllegalArgumentException.class,
                () -> baseBuilder().clearColumns().columns(col).build());
    }


    @Test
    void testColumnsValidationNullColumn()
    {
        assertThrows(IllegalArgumentException.class,
                () -> baseBuilder().clearColumns().columns(new DsjTableColumn[]
                {
                        null
                }).build());
    }


    @Test
    void testDefaultVersion()
    {
        DsjTable table = DsjTable.builder().datasetJSONCreationDateTime("2025-01-01T00:00:00")
                .itemGroupOID("IG.DM").name("DM").label("Demographics").columns(makeCol(0, "X"))
                .build();
        assertEquals("1.1.0", table.getDatasetJSONVersion());
    }
}
