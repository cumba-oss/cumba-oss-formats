package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.cumba.sasutils.bdat.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Covers the small {@link Platform} and {@link Justify} helpers that have no other tests.
 */
// Tests assert ordinal stability — the call to .ordinal() is the test subject.
@SuppressWarnings("EnumOrdinal")
class PlatformJustifyEnumTest
{

    @Test
    void platform_valuesEnumeratedInDocumentedOrder()
    {
        Platform[] values = Platform.values();
        assertEquals(3, values.length);
        assertSame(Platform.UNKNOWN, values[0]);
        assertSame(Platform.UNIX, values[1]);
        assertSame(Platform.WINDOWS, values[2]);
    }


    @ParameterizedTest
    @EnumSource(Platform.class)
    void platform_valueOfRoundTrip(Platform p)
    {
        assertSame(p, Platform.valueOf(p.name()));
        assertNotNull(p.toString());
    }


    @Test
    void platform_ordinalUsedByDatasetBdatLookup()
    {
        // DatasetBdat.getPlatform() indexes Platform.values() by the parsed header2.platform
        // numeric. Verify the documented index→constant mapping does not drift.
        assertSame(Platform.UNKNOWN, Platform.values()[0]);
        assertSame(Platform.UNIX, Platform.values()[1]);
        assertSame(Platform.WINDOWS, Platform.values()[2]);
    }


    @Test
    void justify_valuesEnumeratedInDocumentedOrder()
    {
        Justify[] values = Justify.values();
        assertEquals(2, values.length);
        assertSame(Justify.LEFT, values[0]);
        assertSame(Justify.RIGHT, values[1]);
    }


    @ParameterizedTest
    @EnumSource(Justify.class)
    void justify_valueOfRoundTrip(Justify j)
    {
        assertSame(j, Justify.valueOf(j.name()));
    }
}
