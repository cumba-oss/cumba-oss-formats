package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColumnDataTypeTest
{

    @Test
    void testGetForString()
    {
        assertEquals(ColumnDataType.STRING, ColumnDataType.getFor("string"));
    }


    @Test
    void testGetForInteger()
    {
        assertEquals(ColumnDataType.INTEGER, ColumnDataType.getFor("integer"));
    }


    @Test
    void testGetForUpperCase()
    {
        assertEquals(ColumnDataType.DOUBLE, ColumnDataType.getFor("DOUBLE"));
    }


    @Test
    void testGetForMixedCase()
    {
        assertEquals(ColumnDataType.DATETIME, ColumnDataType.getFor("DateTime"));
    }


    @Test
    void testGetForInvalid()
    {
        assertEquals(ColumnDataType.OTHER, ColumnDataType.getFor("unknown_type"));
    }


    @Test
    void testGetForNull()
    {
        assertEquals(ColumnDataType.OTHER, ColumnDataType.getFor(null));
    }


    @Test
    void testToDsjString()
    {
        assertEquals("string", ColumnDataType.STRING.toDsjString());
        assertEquals("integer", ColumnDataType.INTEGER.toDsjString());
        assertEquals("datetime", ColumnDataType.DATETIME.toDsjString());
    }


    @Test
    void testAllValuesExist()
    {
        // Ensure all expected values exist
        ColumnDataType[] values = ColumnDataType.values();
        assertEquals(11, values.length);
    }
}
