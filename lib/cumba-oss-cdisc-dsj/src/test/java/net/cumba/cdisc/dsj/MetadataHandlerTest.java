package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MetadataHandlerTest
{

    @Test
    void testFunctionalInterface()
    {
        AtomicReference<DsjTable> captured = new AtomicReference<>();
        MetadataHandler handler = table ->
        {
            captured.set(table);
            return 0;
        };

        DsjTable table = DsjTable.builder().datasetJSONCreationDateTime("2025-01-01T00:00:00")
                .itemGroupOID("IG.DM").name("DM").label("Demographics")
                .columns(DsjTableColumn.builder().index(0).itemOID("IT.X").name("X").label("X")
                        .dataType("string").build())
                .build();

        int result = handler.metadata(table);
        assertEquals(0, result);
        assertEquals(table, captured.get());
    }


    @Test
    void testAbortReturn()
    {
        MetadataHandler handler = _ -> -1;
        assertEquals(-1, handler.metadata(null));
    }
}
