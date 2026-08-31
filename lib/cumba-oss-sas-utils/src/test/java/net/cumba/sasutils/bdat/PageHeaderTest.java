package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.cumba.sasutils.bdat.x32.PageHeader32;
import org.junit.jupiter.api.Test;

class PageHeaderTest
{

    private static PageHeader32 header(int seq, short type, short blocks, short subs)
    {
        PageHeader32 h = new PageHeader32();
        h.pageSequence = seq;
        h.setPageTypeId(type);
        h.setBlockCount(blocks);
        h.setSubHeaderCount(subs);
        return h;
    }


    @Test
    void equalsAndHashCode_sameFields()
    {
        PageHeader32 a = header(1, (short) 0, (short) 5, (short) 2);
        PageHeader32 b = header(1, (short) 0, (short) 5, (short) 2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void notEquals_differentBlockCount()
    {
        assertNotEquals(header(1, (short) 0, (short) 5, (short) 2),
                header(1, (short) 0, (short) 6, (short) 2));
    }


    @Test
    void notEquals_null()
    {
        assertNotEquals(header(1, (short) 0, (short) 5, (short) 2), null);
    }
}
