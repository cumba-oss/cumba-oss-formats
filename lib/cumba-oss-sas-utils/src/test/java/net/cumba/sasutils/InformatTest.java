package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InformatTest
{

    @Test
    void constructorAndGetters()
    {
        Informat informat = new Informat("BEST", 12, 2);
        assertEquals("BEST", informat.getName());
        assertEquals(12, informat.getLength());
        assertEquals(2, informat.getDecimals());
    }


    @Test
    void constructorWithNulls()
    {
        Informat informat = new Informat(null, null, null);
        assertNull(informat.getName());
        assertNull(informat.getLength());
        assertNull(informat.getDecimals());
    }


    @Test
    void toStringContainsFields()
    {
        Informat informat = new Informat("BEST", 12, 2);
        String str = informat.toString();
        assertTrue(str.contains("BEST"));
        assertTrue(str.contains("12"));
        assertTrue(str.contains("2"));
    }
}
