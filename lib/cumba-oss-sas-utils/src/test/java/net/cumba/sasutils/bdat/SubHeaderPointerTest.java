package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.cumba.sasutils.bdat.x32.SubHeaderPointer32;
import org.junit.jupiter.api.Test;

class SubHeaderPointerTest
{

    private static SubHeaderPointer32 pointer(int offset, int length, byte compId, byte catId)
    {
        SubHeaderPointer32 p = new SubHeaderPointer32();
        p.pageOffset = offset;
        p.length = length;
        p.setCompressionTypeId(compId);
        p.setCompressed(catId); // sets categoryId
        return p;
    }


    @Test
    void equalsAndHashCode_sameFields()
    {
        SubHeaderPointer32 a = pointer(100, 12, (byte) 0, (byte) 0);
        SubHeaderPointer32 b = pointer(100, 12, (byte) 0, (byte) 0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void notEquals_differentOffset()
    {
        assertNotEquals(pointer(100, 12, (byte) 0, (byte) 0), pointer(200, 12, (byte) 0, (byte) 0));
    }


    @Test
    void notEquals_null()
    {
        assertNotEquals(pointer(100, 12, (byte) 0, (byte) 0), null);
    }
}
