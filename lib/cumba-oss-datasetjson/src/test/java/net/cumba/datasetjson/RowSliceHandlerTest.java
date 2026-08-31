package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RowSliceHandlerTest
{

    @Test
    void testFunctionalInterface()
    {
        AtomicLong capturedFirstRow = new AtomicLong(-1);
        RowSliceHandler handler = (_, firstRow, _, _) ->
        {
            capturedFirstRow.set(firstRow);
            return 0;
        };

        int result = handler.nextRowsAvail(null, 42, 10, new IColumnBufferGetter[0]);
        assertEquals(0, result);
        assertEquals(42, capturedFirstRow.get());
    }


    @Test
    void testAbortReturn()
    {
        RowSliceHandler handler = (_, _, _, _) -> 1;
        assertEquals(1, handler.nextRowsAvail(null, 0, 0, new IColumnBufferGetter[0]));
    }
}
