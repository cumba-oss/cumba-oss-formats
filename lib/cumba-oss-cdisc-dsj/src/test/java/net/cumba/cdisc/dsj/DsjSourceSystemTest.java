package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DsjSourceSystemTest
{

    @Test
    void testBuilder()
    {
        DsjSourceSystem ss = DsjSourceSystem.builder().name("TestSystem").version("1.0").build();

        assertEquals("TestSystem", ss.getName());
        assertEquals("1.0", ss.getVersion());
    }


    @Test
    void testBuilderMissingNameThrows()
    {
        assertThrows(NullPointerException.class,
                () -> DsjSourceSystem.builder().version("1.0").build());
    }


    @Test
    void testBuilderMissingVersionThrows()
    {
        assertThrows(NullPointerException.class,
                () -> DsjSourceSystem.builder().name("TestSystem").build());
    }
}
