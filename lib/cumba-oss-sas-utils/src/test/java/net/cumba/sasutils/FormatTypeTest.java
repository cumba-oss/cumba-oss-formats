package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FormatTypeTest
{

    @Test
    void fromString_knownTypes()
    {
        assertEquals(FormatType.DATE, FormatType.fromString("DATE"));
        assertEquals(FormatType.DATE, FormatType.fromString("date"));
        assertEquals(FormatType.DATE, FormatType.fromString("Date"));
        assertEquals(FormatType.DATETIME, FormatType.fromString("DATETIME"));
        assertEquals(FormatType.TIME, FormatType.fromString("TIME"));
        assertEquals(FormatType.NUMERIC, FormatType.fromString("NUMERIC"));
        assertEquals(FormatType.CHARACTER, FormatType.fromString("CHARACTER"));
        assertEquals(FormatType.YYMMDD, FormatType.fromString("YYMMDD"));
        assertEquals(FormatType.MMDDYY, FormatType.fromString("MMDDYY"));
        assertEquals(FormatType.DDMMYY, FormatType.fromString("DDMMYY"));
        assertEquals(FormatType.JULIAN, FormatType.fromString("JULIAN"));
        assertEquals(FormatType.MONYY, FormatType.fromString("MONYY"));
    }


    @Test
    void fromString_characterFormat()
    {
        assertEquals(FormatType.CHARACTER, FormatType.fromString("$"));
    }


    @Test
    void fromString_unknownReturnsNumeric()
    {
        assertEquals(FormatType.NUMERIC, FormatType.fromString("BEST"));
        assertEquals(FormatType.NUMERIC, FormatType.fromString("UNKNOWN_FORMAT"));
    }


    @Test
    void fromString_nullReturnsNumeric()
    {
        assertEquals(FormatType.NUMERIC, FormatType.fromString(null));
    }


    @Test
    void fromString_emptyReturnsNumeric()
    {
        assertEquals(FormatType.NUMERIC, FormatType.fromString(""));
    }
}
