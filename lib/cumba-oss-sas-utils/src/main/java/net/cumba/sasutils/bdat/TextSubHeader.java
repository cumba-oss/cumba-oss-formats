/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.bdat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;

// length is a @StructToken field set by the deserialiser; string is set via setString()/the
// (String) constructor after the no-arg constructor; neither is initialised in the default
// constructor — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class TextSubHeader extends SubHeader
{

    protected static final Logger LOGGER = LoggerFactory.getLogger(TextSubHeader.class);

    public static final Struct<TextSubHeader> STRUCT = Struct.create(TextSubHeader.class);

    @StructToken(order = 0)

    public Short length;

    public String string;

    // Other logical fields (compression, creatorProcess, dataset, offset) are
    // embedded in the string and must be extracted using indexes carried by
    // the RowSizeSubheader rather than being represented as separate fields.

    public Integer getStringLength()
    {
        // String length is the overall subheader length minus the 12-byte header.
        return length - 12;
    }


    public Short getLength()
    {
        return length;
    }


    public void setLength(Short length)
    {
        this.length = length;
    }


    public TextSubHeader()
    {
    }


    public TextSubHeader(String string)
    {
        super();
        this.string = string;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("TextSubHeader [length=");
        builder.append(length);
        builder.append("]");
        return builder.toString();
    }


    public String getString()
    {
        return string;
    }


    public void setString(String string)
    {
        this.string = string;
    }


    public String getSubString(int start, int length)
    {
        start = start - STRUCT.byteCount();
        return string.substring(start, start + length);
    }

}
