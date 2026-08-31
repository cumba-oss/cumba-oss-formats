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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SasConstants
{

    private SasConstants()
    {
        throw new UnsupportedOperationException("utility class");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SasConstants.class);

    public static final LocalDateTime EPOCH = LocalDateTime.of(1960, 1, 1, 0, 0, 0, 0);

    public static void debugBytes(InputStream input, int num) throws IOException
    {
        input.mark(num);
        byte[] bytes = new byte[num];
        IOUtils.readFully(input, bytes);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Next {} Bytes String: '{}'", num,
                    new String(bytes, StandardCharsets.ISO_8859_1));
            LOGGER.debug("Next {} Bytes Hex: {}", num, Hex.encodeHexString(bytes));
        }
        input.reset();

    }


    public static LocalDateTime toDateTime(Double d)
    {
        // Math.floor (not Double.longValue, which truncates toward zero) so the rounding
        // direction is consistent on both sides of the SAS epoch.
        return EPOCH.plusSeconds((long) Math.floor(d));
    }

}
