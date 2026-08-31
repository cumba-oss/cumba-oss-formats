package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

// (long) 0xFFFFFFFF-style casts intentionally exercise the sign-extended 64-bit form that
// 32-bit SAS subheader IDs decode into when read as a long.
@SuppressWarnings("IntLiteralCast")
class SubHeaderSignatureTest
{

    @Test
    void fromId_rowSize()
    {
        assertEquals(SubHeaderSignature.ROW_SIZE, SubHeaderSignature.fromId(0x00000000F7F7F7F7L));
        assertEquals(SubHeaderSignature.ROW_SIZE, SubHeaderSignature.fromId(0xF7F7F7F700000000L));
        assertEquals(SubHeaderSignature.ROW_SIZE, SubHeaderSignature.fromId((long) 0xF7F7F7F7));
    }


    @Test
    void fromId_columnSize()
    {
        assertEquals(SubHeaderSignature.COLUMN_SIZE,
                SubHeaderSignature.fromId(0x00000000F6F6F6F6L));
        assertEquals(SubHeaderSignature.COLUMN_SIZE, SubHeaderSignature.fromId((long) 0xF6F6F6F6));
    }


    @Test
    void fromId_string()
    {
        assertEquals(SubHeaderSignature.STRING, SubHeaderSignature.fromId(0xFDFFFFFFFFFFFFFFL));
        assertEquals(SubHeaderSignature.STRING, SubHeaderSignature.fromId((long) 0xFFFFFFFD));
    }


    @Test
    void fromId_columnName()
    {
        assertEquals(SubHeaderSignature.COLUMN_NAME,
                SubHeaderSignature.fromId(0xFFFFFFFFFFFFFFFFL));
        assertEquals(SubHeaderSignature.COLUMN_NAME, SubHeaderSignature.fromId((long) 0xFFFFFFFF));
    }


    @Test
    void fromId_unknown()
    {
        assertNull(SubHeaderSignature.fromId(0x1234567890ABCDEFL));
    }


    @Test
    void fromId_data_containsNull()
    {
        // Data has null in its id list, so fromId(null) returns Data
        assertEquals(SubHeaderSignature.DATA, SubHeaderSignature.fromId((Long) null));
    }
}
