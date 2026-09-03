package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColumnBufferStringTest
{

    @Test
    void testConstructorWithSize()
    {
        ColumnBufferString buf = new ColumnBufferString(3);
        assertNull(buf.getValue(0));
        assertTrue(buf.isMissing(0));
    }


    @Test
    void testConstructorWithArray()
    {
        String[] data =
        {
                "a", "b", "c"
        };
        ColumnBufferString buf = new ColumnBufferString(data);
        assertEquals("a", buf.getValue(0));
        assertEquals("b", buf.getValue(1));
        assertEquals("c", buf.getValue(2));
    }


    @Test
    void testSetStringValue()
    {
        ColumnBufferString buf = new ColumnBufferString(2);
        buf.setStringValue(0, "hello");
        buf.setStringValue(1, "world");
        assertEquals("hello", buf.getStringValue(0));
        assertEquals("world", buf.getStringValue(1));
    }


    @Test
    void testSetValueWithObject()
    {
        ColumnBufferString buf = new ColumnBufferString(1);
        buf.setValue(0, 42);
        assertEquals("42", buf.getValue(0));
    }


    @Test
    void testSetValueWithNull()
    {
        ColumnBufferString buf = new ColumnBufferString(1);
        buf.setStringValue(0, "not null");
        buf.setValue(0, null);
        assertNull(buf.getValue(0));
        assertTrue(buf.isMissing(0));
    }


    @Test
    void testIsMissing()
    {
        ColumnBufferString buf = new ColumnBufferString(2);
        buf.setStringValue(0, "hello");
        buf.setStringValue(1, null);
        assertFalse(buf.isMissing(0));
        assertTrue(buf.isMissing(1));
    }


    @Test
    void testGetStringValue()
    {
        ColumnBufferString buf = new ColumnBufferString(1);
        buf.setStringValue(0, "test");
        assertEquals("test", buf.getStringValue(0));
    }


    @Test
    void testGetStringValueNull()
    {
        ColumnBufferString buf = new ColumnBufferString(1);
        assertNull(buf.getStringValue(0));
    }
}
