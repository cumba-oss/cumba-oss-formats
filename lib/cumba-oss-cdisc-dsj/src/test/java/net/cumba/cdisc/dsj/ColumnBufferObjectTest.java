package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColumnBufferObjectTest
{

    @Test
    void testConstructor()
    {
        Object[] data =
        {
                "a", 1, 3.14
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals("a", buf.getValue(0));
        assertEquals(1, buf.getValue(1));
        assertEquals(3.14, buf.getValue(2));
    }


    @Test
    void testSetAndGetValue()
    {
        Object[] data = new Object[2];
        ColumnBufferObject buf = new ColumnBufferObject(data);
        buf.setValue(0, "hello");
        buf.setValue(1, 42);
        assertEquals("hello", buf.getValue(0));
        assertEquals(42, buf.getValue(1));
    }


    @Test
    void testSetNull()
    {
        Object[] data = new Object[]
        {
                "not null"
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        buf.setValue(0, null);
        assertNull(buf.getValue(0));
    }


    @Test
    void testIsMissing()
    {
        Object[] data =
        {
                "value", null
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertFalse(buf.isMissing(0));
        assertTrue(buf.isMissing(1));
    }


    @Test
    void testGetStringValue()
    {
        Object[] data =
        {
                42
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals("42", buf.getStringValue(0));
    }


    @Test
    void testGetStringValueNull()
    {
        Object[] data =
        {
                null
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertNull(buf.getStringValue(0));
    }
}
