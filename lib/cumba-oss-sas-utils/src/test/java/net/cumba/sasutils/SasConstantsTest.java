package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SasConstantsTest
{

    @Test
    void toDateTime_wholeSeconds()
    {
        assertEquals(SasConstants.EPOCH, SasConstants.toDateTime(0.0));
        assertEquals(SasConstants.EPOCH.plusSeconds(86400), SasConstants.toDateTime(86400.0));
    }


    @Test
    void toDateTime_positiveFraction_floorsDown()
    {
        assertEquals(SasConstants.EPOCH.plusSeconds(1), SasConstants.toDateTime(1.5));
    }


    @Test
    void toDateTime_negativeFraction_floorsTowardEarlier()
    {
        // Floor (not truncate-toward-zero): -1.5s before the epoch rounds to -2s, the same
        // downward direction as positive values, so the error is consistent across the epoch.
        assertEquals(SasConstants.EPOCH.plusSeconds(-2), SasConstants.toDateTime(-1.5));
    }


    @Test
    void toDateTime_negativeWholeSecond()
    {
        LocalDateTime expected = SasConstants.EPOCH.plusSeconds(-86400);
        assertEquals(expected, SasConstants.toDateTime(-86400.0));
    }
}
