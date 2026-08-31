package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DsjTableColumnTest
{

    @Test
    void testBuilder()
    {
        DsjTableColumn col = DsjTableColumn.builder().index(0).itemOID("IT.STUDYID").name("STUDYID")
                .label("Study Identifier").dataType("string").length(20).keySequence(1).build();

        assertEquals(0, col.getIndex());
        assertEquals("IT.STUDYID", col.getItemOID());
        assertEquals("STUDYID", col.getName());
        assertEquals("Study Identifier", col.getLabel());
        assertEquals("string", col.getDataType());
        assertEquals(20, col.getLength());
        assertEquals(1, col.getKeySequence());
    }


    @Test
    void testDefaults()
    {
        DsjTableColumn col = DsjTableColumn.builder().index(0).itemOID("IT.X").name("X")
                .label("X Label").dataType("string").build();

        assertEquals(-1, col.getLength());
        assertEquals(-1, col.getKeySequence());
        assertNull(col.getTargetDataType());
        assertNull(col.getDisplayFormat());
    }


    @Test
    void testBuilderMissingRequiredFields()
    {
        assertThrows(NullPointerException.class, () -> DsjTableColumn.builder().index(0).build());
    }


    @Test
    void testOptionalFields()
    {
        DsjTableColumn col = DsjTableColumn.builder().index(0).itemOID("IT.AGE").name("AGE")
                .label("Age").dataType("integer").targetDataType("integer").displayFormat("8.")
                .build();

        assertEquals("integer", col.getTargetDataType());
        assertEquals("8.", col.getDisplayFormat());
    }
}
