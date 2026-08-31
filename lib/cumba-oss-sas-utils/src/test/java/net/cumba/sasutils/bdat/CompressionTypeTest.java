package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompressionTypeTest
{

    @Test
    void fromId_knownTypes()
    {
        assertEquals(CompressionType.NONE, CompressionType.fromId(0));
        assertEquals(CompressionType.TRUNCATED, CompressionType.fromId(1));
        assertEquals(CompressionType.COMPRESSED, CompressionType.fromId(4));
    }


    @Test
    void fromId_unknown()
    {
        assertNull(CompressionType.fromId(99));
    }
}
