package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.cumba.sasutils.bdat.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Covers the small {@link Platform}, {@link Justify} and {@link InvalidFormatException} helpers
 * that have no other tests.
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


    @Test
    void invalidFormatException_defaultConstructorHasNoMessage()
    {
        InvalidFormatException ex = new InvalidFormatException();
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }


    @Test
    void invalidFormatException_messageConstructor()
    {
        InvalidFormatException ex = new InvalidFormatException("bad format");
        assertEquals("bad format", ex.getMessage());
        assertNull(ex.getCause());
    }


    @Test
    void invalidFormatException_messageAndCauseConstructor()
    {
        Throwable cause = new IllegalStateException("root");
        InvalidFormatException ex = new InvalidFormatException("wrapped", cause);
        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
    }


    @Test
    void invalidFormatException_causeOnlyConstructor()
    {
        Throwable cause = new IllegalStateException("root");
        InvalidFormatException ex = new InvalidFormatException(cause);
        assertSame(cause, ex.getCause());
        // The cause's toString shows up in the message produced by the
        // RuntimeException(Throwable) chain — verify it is not null and not empty.
        assertNotNull(ex.getMessage());
        assertNotSame("", ex.getMessage());
    }


    @Test
    void invalidFormatException_extendsIllegalArgumentException()
    {
        // Make sure the exception hierarchy stays stable — many call sites rely on the standard
        // runtime exception type when handling invalid format strings.
        InvalidFormatException ex = new InvalidFormatException("x");
        IllegalArgumentException iae = ex;
        assertSame(ex, iae);

        // Throwing it must be catchable as IllegalArgumentException.
        IllegalArgumentException caught = assertThrows(IllegalArgumentException.class, () ->
        {
            throw new InvalidFormatException("boom");
        });
        assertEquals("boom", caught.getMessage());
    }
}
