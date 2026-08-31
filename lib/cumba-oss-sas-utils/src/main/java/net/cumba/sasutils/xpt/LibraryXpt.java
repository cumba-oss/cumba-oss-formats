/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.xpt;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.cumba.sasutils.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// header is set via setHeader() and datasets is built lazily by getDatasets(); the constructor only
// passes the file to super, so neither is initialised there — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class LibraryXpt extends net.cumba.sasutils.Library
{

    public static final Logger LOGGER = LoggerFactory.getLogger(LibraryXpt.class);

    public static final String METADATA_CHARSET_NAME = "US-ASCII";

    public static final Charset METADATA_CHARSET = StandardCharsets.US_ASCII;

    /**
     * Formatter for the XPT header {@code ddMMMyy:HH:mm:ss} timestamps. XPT v5 stores the year as
     * two digits in fixed 16-char header fields, so the century is ambiguous. These fields only
     * ever carry file created/modified timestamps, which can never be in the future and are never
     * more than ~100 years old, so the year is windowed to {@code [currentYear - 99, currentYear]}:
     * a 2-digit year is read as the most recent year not after the present. Built per call so the
     * window tracks the current year. Four-digit years (max width 4) are still parsed literally.
     */
    public static DateTimeFormatter dateTimeFormat()
    {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("ddMMM")
                .appendValueReduced(ChronoField.YEAR, 2, 4,
                        Year.now(ZoneOffset.UTC).getValue() - 99)
                .appendPattern(":HH:mm:ss").toFormatter(Locale.ENGLISH);
    }

    protected LibraryHeaderXpt header;

    protected List<DatasetXpt> datasets;

    public LibraryXpt(File file)
    {
        super(file);
    }


    public LibraryHeaderXpt getHeader()
    {
        return header;
    }


    public void setHeader(LibraryHeaderXpt header)
    {
        this.header = header;
    }


    @Override
    public List<DatasetXpt> getDatasets()
    {
        if (datasets == null)
        {
            datasets = new ArrayList<>();
        }
        return datasets;
    }


    @Override
    public LocalDateTime getModified()
    {
        return ParserXpt.parseDateTime(header.modifiedString);
    }


    @Override
    public LocalDateTime getCreated()
    {
        return ParserXpt.parseDateTime(header.createdString);
    }


    @SuppressWarnings("unchecked")
    @Override
    public void setDatasets(List<? extends Dataset> ds)
    {
        this.datasets = (List<DatasetXpt>) ds;

    }

}
