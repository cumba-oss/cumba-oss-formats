package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PageTypeTest
{

    @Test
    void fromId_knownTypes()
    {
        assertEquals(PageType.META, PageType.fromId(0));
        assertEquals(PageType.META2, PageType.fromId(16384));
        assertEquals(PageType.CMETA, PageType.fromId(128));
        assertEquals(PageType.DATA, PageType.fromId(256));
        assertEquals(PageType.DATA2, PageType.fromId(384));
        assertEquals(PageType.MIXED1, PageType.fromId(512));
        assertEquals(PageType.MIXED2, PageType.fromId(640));
        assertEquals(PageType.AMD, PageType.fromId(1024));
        assertEquals(PageType.COMP, PageType.fromId(-28672));
    }


    @Test
    void fromId_unknown()
    {
        assertNull(PageType.fromId(999));
    }


    @Test
    void mixed_metaOnly()
    {
        assertFalse(PageType.META.mixed());
        assertFalse(PageType.DATA.mixed());
        assertTrue(PageType.MIXED1.mixed());
        assertTrue(PageType.MIXED2.mixed());
    }


    @Test
    void metaAndDataFlags()
    {
        assertTrue(PageType.META.meta);
        assertFalse(PageType.META.data);
        assertFalse(PageType.DATA.meta);
        assertTrue(PageType.DATA.data);
        assertTrue(PageType.MIXED1.meta);
        assertTrue(PageType.MIXED1.data);
    }
}
