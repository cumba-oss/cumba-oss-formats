package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ParserXptTest
{

    @Test
    void parseDateTime_twoDigitCurrentYear_windowsToPresent()
    {
        // XPT v5 header timestamps carry a 2-digit year. The reader windows the year to
        // [currentYear - 99, currentYear], so the current year's 2-digit form must round-trip back
        // to the current year — not 100 years earlier (the old base-1920 bug).
        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        String ts = String.format("27MAY%02d:13:45:09", currentYear % 100);

        LocalDateTime parsed = ParserXpt.parseDateTime(ts);

        assertEquals(currentYear, parsed.getYear());
        assertEquals(5, parsed.getMonthValue());
        assertEquals(27, parsed.getDayOfMonth());
        assertEquals(13, parsed.getHour());
        assertEquals(45, parsed.getMinute());
        assertEquals(9, parsed.getSecond());
    }


    @Test
    void parseDateTime_twoDigitNextYear_windowsToPastNotFuture()
    {
        // A 2-digit year that would fall one year in the future is read as the prior century
        // instead (file timestamps are never in the future): it maps to currentYear - 99.
        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        String ts = String.format("01JAN%02d:00:00:00", (currentYear + 1) % 100);

        assertEquals(currentYear - 99, ParserXpt.parseDateTime(ts).getYear());
    }


    @Test
    void parseDateTime_fourDigitYear_parsedLiterally()
    {
        // The formatter accepts up to 4 year digits, so an explicit 4-digit year parses as-is.
        LocalDateTime parsed = ParserXpt.parseDateTime("27MAY2026:00:00:00");

        assertEquals(2026, parsed.getYear());
    }
}
