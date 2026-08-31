package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColumnBufferLongTest
{

    @Test
    void testConstructorWithSize()
    {
        ColumnBufferLong buf = new ColumnBufferLong(5);
        assertEquals(0L, buf.getLongValue(0));
        assertFalse(buf.isMissing(0));
    }


    @Test
    void testConstructorWithArray()
    {
        long[] data =
        {
                10L, 20L, 30L
        };
        ColumnBufferLong buf = new ColumnBufferLong(data);
        assertEquals(10L, buf.getLongValue(0));
        assertEquals(20L, buf.getLongValue(1));
        assertEquals(30L, buf.getLongValue(2));
    }


    @Test
    void testSetAndGetLongValue()
    {
        ColumnBufferLong buf = new ColumnBufferLong(3);
        buf.setLongValue(0, 42L);
        buf.setLongValue(1, Long.MAX_VALUE);
        buf.setLongValue(2, Long.MIN_VALUE);

        assertEquals(42L, buf.getLongValue(0));
        assertEquals(Long.MAX_VALUE, buf.getLongValue(1));
        assertEquals(Long.MIN_VALUE, buf.getLongValue(2));
    }


    @Test
    void testSetDoubleValue()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setDoubleValue(0, 42.0);
        assertEquals(42L, buf.getLongValue(0));
        assertFalse(buf.isMissing(0));
    }


    @Test
    void testSetDoubleValueNaN()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setDoubleValue(0, Double.NaN);
        assertTrue(buf.isMissing(0));
        assertEquals(Long.MIN_VALUE, buf.getLongValue(0));
    }


    @Test
    void testGetValueForMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setDoubleValue(0, Double.NaN);
        assertNull(buf.getValue(0));
    }


    @Test
    void testGetValueForNonMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setLongValue(0, 99L);
        Object val = buf.getValue(0);
        assertTrue(val instanceof Long);
        assertEquals(99L, val);
    }


    @Test
    void testGetDoubleValue()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setLongValue(0, 42L);
        assertEquals(42.0, buf.getDoubleValue(0));
    }


    @Test
    void testGetDoubleValueForMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setDoubleValue(0, Double.NaN);
        assertTrue(Double.isNaN(buf.getDoubleValue(0)));
    }


    @Test
    void testSetLongValueClearsMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setDoubleValue(0, Double.NaN);
        assertTrue(buf.isMissing(0));

        buf.setLongValue(0, 5L);
        assertFalse(buf.isMissing(0));
    }


    @Test
    void testSetValueWithNumber()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setValue(0, 42L);
        assertEquals(42L, buf.getValue(0));
    }


    @Test
    void testSetValueWithNull()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setLongValue(0, 10L);
        buf.setValue(0, null);
        assertTrue(buf.isMissing(0));
        assertEquals(Long.MIN_VALUE, buf.getLongValue(0));
    }


    @Test
    void testOutOfBounds()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> buf.getLongValue(5));
    }
}
