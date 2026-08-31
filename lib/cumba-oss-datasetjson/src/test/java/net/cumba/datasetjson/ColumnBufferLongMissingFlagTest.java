package net.cumba.datasetjson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Additional tests for ColumnBufferLong focusing on the missing flag behavior fix.
 */
class ColumnBufferLongMissingFlagTest
{

    @Test
    void testSetValueNullSetsMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setLongValue(0, 42L);
        assertFalse(buf.isMissing(0));

        buf.setValue(0, null);
        assertTrue(buf.isMissing(0));
        assertEquals(Long.MIN_VALUE, buf.getLongValue(0));
        assertNull(buf.getValue(0));
    }


    @Test
    void testSetValueNaNNumberSetsMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setLongValue(0, 42L);
        assertFalse(buf.isMissing(0));

        buf.setValue(0, Double.NaN);
        assertTrue(buf.isMissing(0));
        assertEquals(Long.MIN_VALUE, buf.getLongValue(0));
        assertNull(buf.getValue(0));
    }


    @Test
    void testSetValueNumberClearsMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setValue(0, null);
        assertTrue(buf.isMissing(0));

        buf.setValue(0, 99L);
        assertFalse(buf.isMissing(0));
        assertEquals(99L, buf.getLongValue(0));
    }


    @Test
    void testSetValueNaNThenValueSequence()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);

        // Set NaN
        buf.setValue(0, Double.NaN);
        assertTrue(buf.isMissing(0));

        // Set real value
        buf.setValue(0, 123L);
        assertFalse(buf.isMissing(0));
        assertEquals(123L, buf.getLongValue(0));

        // Set null
        buf.setValue(0, null);
        assertTrue(buf.isMissing(0));

        // Set real value again
        buf.setLongValue(0, 456L);
        assertFalse(buf.isMissing(0));
        assertEquals(456L, buf.getLongValue(0));
    }


    @Test
    void testSetValueInvalidTypeThrows()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        assertThrows(IllegalArgumentException.class, () -> buf.setValue(0, "not a number"));
    }


    @Test
    void testGetStringValueForNonMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setLongValue(0, 42L);
        assertEquals("42", buf.getStringValue(0));
    }


    @Test
    void testGetStringValueForMissing()
    {
        ColumnBufferLong buf = new ColumnBufferLong(1);
        buf.setValue(0, null);
        // getValue returns null for missing, so getStringValue should return null
        assertNull(buf.getStringValue(0));
    }
}
