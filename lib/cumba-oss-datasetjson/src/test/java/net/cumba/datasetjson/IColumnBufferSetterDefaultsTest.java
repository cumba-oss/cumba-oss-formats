package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for the default methods on IColumnBufferSetter interface.
 */
class IColumnBufferSetterDefaultsTest
{

    @Test
    void testSetStringValueDefault()
    {
        Object[] data = new Object[1];
        ColumnBufferObject buf = new ColumnBufferObject(data);
        buf.setStringValue(0, "test");
        assertEquals("test", buf.getValue(0));
    }


    @Test
    void testSetDoubleValueDefault()
    {
        Object[] data = new Object[1];
        ColumnBufferObject buf = new ColumnBufferObject(data);
        buf.setDoubleValue(0, 3.14);
        assertEquals(3.14, buf.getValue(0));
    }


    @Test
    void testSetLongValueDefault()
    {
        Object[] data = new Object[1];
        ColumnBufferObject buf = new ColumnBufferObject(data);
        buf.setLongValue(0, 42L);
        assertEquals(42L, buf.getValue(0));
    }
}
