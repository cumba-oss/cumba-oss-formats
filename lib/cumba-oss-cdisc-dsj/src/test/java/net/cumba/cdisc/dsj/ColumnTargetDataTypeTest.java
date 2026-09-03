package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColumnTargetDataTypeTest
{

    @Test
    void testGetForInteger()
    {
        assertEquals(ColumnTargetDataType.INTEGER, ColumnTargetDataType.getFor("integer"));
    }


    @Test
    void testGetForDecimal()
    {
        assertEquals(ColumnTargetDataType.DECIMAL, ColumnTargetDataType.getFor("decimal"));
    }


    @Test
    void testGetForUpperCase()
    {
        assertEquals(ColumnTargetDataType.INTEGER, ColumnTargetDataType.getFor("INTEGER"));
    }


    @Test
    void testGetForMixedCase()
    {
        assertEquals(ColumnTargetDataType.DECIMAL, ColumnTargetDataType.getFor("Decimal"));
    }


    @Test
    void testGetForNull()
    {
        assertEquals(ColumnTargetDataType.UNKNOWN, ColumnTargetDataType.getFor(null));
    }


    @Test
    void testGetForInvalid()
    {
        assertEquals(ColumnTargetDataType.OTHER, ColumnTargetDataType.getFor("invalid_type"));
    }


    @Test
    void testToDsjString()
    {
        assertEquals("integer", ColumnTargetDataType.INTEGER.toDsjString());
        assertEquals("decimal", ColumnTargetDataType.DECIMAL.toDsjString());
        assertEquals("unknown", ColumnTargetDataType.UNKNOWN.toDsjString());
        assertEquals("other", ColumnTargetDataType.OTHER.toDsjString());
    }


    @Test
    void testAllValuesExist()
    {
        assertEquals(4, ColumnTargetDataType.values().length);
    }
}
