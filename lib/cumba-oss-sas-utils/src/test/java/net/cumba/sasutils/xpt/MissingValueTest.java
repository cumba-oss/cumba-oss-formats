package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MissingValueTest
{

    @Test
    void fromCharacter_allLetters()
    {
        for (char c = 'A'; c <= 'Z'; c++)
        {
            MissingValue mv = MissingValue.fromCharacter(c);
            assertNotNull(mv, "Should find MissingValue for " + c);
            assertEquals(String.valueOf(c), mv.name());
        }
    }


    @Test
    void fromCharacter_underscore()
    {
        MissingValue mv = MissingValue.fromCharacter('_');
        assertNotNull(mv);
        assertEquals(MissingValue.UC, mv);
    }


    @Test
    void fromCharacter_unknown()
    {
        assertNull(MissingValue.fromCharacter('0'));
        assertNull(MissingValue.fromCharacter(' '));
        assertNull(MissingValue.fromCharacter('.'));
    }
}
