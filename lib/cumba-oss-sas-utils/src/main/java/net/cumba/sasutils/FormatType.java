/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils;

import static java.lang.System.Logger.Level.TRACE;

import lombok.CustomLog;

@CustomLog
public enum FormatType
{

    NUMERIC, CHARACTER, TIME, DATETIME, YYMMDD, MMDDYY, DDMMYY, DATE, JULIAN, MONYY;

    public static final String CHARACTER_FORMAT = "$";

    public static FormatType fromString(String string)
    {
        if (string == null || string.isEmpty())
        {
            return NUMERIC;
        }

        try
        {
            return FormatType.valueOf(string.toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.log(TRACE, "not a known enum constant; fall through to the legacy mapping below",
                    e);
        }
        if (CHARACTER_FORMAT.equals(string))
        {
            return CHARACTER;
        }
        else
        {
            return NUMERIC;
        }

    }
}
