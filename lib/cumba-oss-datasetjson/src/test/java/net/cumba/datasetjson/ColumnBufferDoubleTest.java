package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColumnBufferDoubleTest
{

    @Test
    void testConstructorWithSize()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(5);
        // default values are 0.0
        assertEquals(0.0, buf.getDoubleValue(0));
    }


    @Test
    void testConstructorWithArray()
    {
        double[] data =
        {
                1.0, 2.5, 3.7
        };
        ColumnBufferDouble buf = new ColumnBufferDouble(data);
        assertEquals(1.0, buf.getDoubleValue(0));
        assertEquals(2.5, buf.getDoubleValue(1));
        assertEquals(3.7, buf.getDoubleValue(2));
    }


    @Test
    void testSetAndGetDoubleValue()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(3);
        buf.setDoubleValue(0, 42.5);
        buf.setDoubleValue(1, -1.0);
        assertEquals(42.5, buf.getDoubleValue(0));
        assertEquals(-1.0, buf.getDoubleValue(1));
    }


    @Test
    void testSetAndGetLongValue()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(2);
        buf.setLongValue(0, 100L);
        assertEquals(100.0, buf.getDoubleValue(0));
        assertEquals(100L, buf.getLongValue(0));
    }


    @Test
    void testSetValueWithNumber()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(2);
        buf.setValue(0, 3.14);
        buf.setValue(1, 42);
        assertEquals(3.14, buf.getDoubleValue(0));
        assertEquals(42.0, buf.getDoubleValue(1));
    }


    @Test
    void testSetValueWithNull()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setValue(0, null);
        assertTrue(buf.isMissing(0));
        assertTrue(Double.isNaN(buf.getDoubleValue(0)));
    }


    @Test
    void testSetValueWithInvalidType()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        assertThrows(IllegalArgumentException.class, () -> buf.setValue(0, "not a number"));
    }


    @Test
    void testGetValue()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setDoubleValue(0, 7.5);
        Object val = buf.getValue(0);
        assertTrue(val instanceof Double);
        assertEquals(7.5, (Double) val);
    }


    @Test
    void testIsMissingWithNaN()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setDoubleValue(0, Double.NaN);
        assertTrue(buf.isMissing(0));
    }


    @Test
    void testIsNotMissing()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setDoubleValue(0, 0.0);
        assertFalse(buf.isMissing(0));
    }


    @Test
    void testGetLongValueForMissing()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setDoubleValue(0, Double.NaN);
        assertEquals(Long.MIN_VALUE, buf.getLongValue(0));
    }


    @Test
    void testGetStringValue()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setDoubleValue(0, 3.14);
        assertEquals("3.14", buf.getStringValue(0));
    }


    @Test
    void testGetStringValueForNaN()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        buf.setDoubleValue(0, Double.NaN);
        assertEquals("NaN", buf.getStringValue(0));
    }


    @Test
    void testOutOfBounds()
    {
        ColumnBufferDouble buf = new ColumnBufferDouble(1);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> buf.getDoubleValue(5));
    }
}
