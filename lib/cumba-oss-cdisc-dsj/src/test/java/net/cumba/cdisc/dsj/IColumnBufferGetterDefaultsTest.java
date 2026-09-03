package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the default methods on IColumnBufferGetter interface, exercised through
 * ColumnBufferObject which relies on the defaults.
 */
class IColumnBufferGetterDefaultsTest
{

    @Test
    void testGetStringValueWithNumber()
    {
        Object[] data =
        {
                42
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals("42", buf.getStringValue(0));
    }


    @Test
    void testGetStringValueWithNull()
    {
        Object[] data =
        {
                null
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertNull(buf.getStringValue(0));
    }


    @Test
    void testGetStringValueWithString()
    {
        Object[] data =
        {
                "hello"
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals("hello", buf.getStringValue(0));
    }


    @Test
    void testGetDoubleValueWithNumber()
    {
        Object[] data =
        {
                3.14
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals(3.14, buf.getDoubleValue(0), 0.001);
    }


    @Test
    void testGetDoubleValueWithLong()
    {
        Object[] data =
        {
                42L
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals(42.0, buf.getDoubleValue(0), 0.001);
    }


    @Test
    void testGetDoubleValueWithNonNumber()
    {
        Object[] data =
        {
                "not a number"
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertTrue(Double.isNaN(buf.getDoubleValue(0)));
    }


    @Test
    void testGetDoubleValueWithNull()
    {
        Object[] data =
        {
                null
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertTrue(Double.isNaN(buf.getDoubleValue(0)));
    }


    @Test
    void testGetLongValueWithNumber()
    {
        Object[] data =
        {
                42L
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals(42L, buf.getLongValue(0));
    }


    @Test
    void testGetLongValueWithDouble()
    {
        Object[] data =
        {
                3.14
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertEquals(3L, buf.getLongValue(0));
    }


    @Test
    void testGetLongValueWithNonNumberThrows()
    {
        Object[] data =
        {
                "not a number"
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertThrows(IllegalArgumentException.class, () -> buf.getLongValue(0));
    }


    @Test
    void testGetLongValueWithNullThrows()
    {
        Object[] data =
        {
                null
        };
        ColumnBufferObject buf = new ColumnBufferObject(data);
        assertThrows(IllegalArgumentException.class, () -> buf.getLongValue(0));
    }
}
