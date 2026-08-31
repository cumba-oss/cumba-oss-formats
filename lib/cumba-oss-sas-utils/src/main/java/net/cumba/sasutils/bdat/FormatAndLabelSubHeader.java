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

import org.jspecify.annotations.Nullable;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenPrefix;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class FormatAndLabelSubHeader extends SubHeader
{

    // Historical note on the wire format: the subheader begins with eight bytes of zero padding,
    // followed by four shorts (formatDigits, formatDecimals, informatDigits, informatDecimals),
    // fourteen bytes of padding (covered by the prefix/suffix constants below), and six shorts
    // describing the format and label (text-string index, offset into the text string, length).
    // Format/label strings live in the column text subheaders pointed to by formatIndex and
    // labelIndex; the actual substring is sliced using the matching offset and length.

    @StructTokenPrefix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000000000000000")
    })
    @StructToken(order = -4)
    public Short formatDigits;

    @StructToken(order = -3)
    public Short formatDecimals;

    @StructToken(order = -2)
    public Short informatDigits;

    @StructToken(order = -1)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000000000000000000000000000",
                    validate = false)
    })
    public Short informatDecimals;

    @StructToken(order = 1)
    public Short formatIndex;

    @StructToken(order = 2)
    public Short formatOffset;

    @StructToken(order = 3)
    public Short formatLength;

    @StructToken(order = 4)
    public Short labelIndex;

    @StructToken(order = 5)
    public Short labelOffset;

    @StructToken(order = 6)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "000000000000", validate = false)
    })
    public Short labelLength;

    public Short getFormatIndex()
    {
        return formatIndex;
    }


    public void setFormatIndex(Short formatStringIndex)
    {
        this.formatIndex = formatStringIndex;
    }


    public Short getFormatOffset()
    {
        return formatOffset;
    }


    public void setFormatOffset(Short formatStart)
    {
        this.formatOffset = formatStart;
    }


    public Short getFormatLength()
    {
        return formatLength;
    }


    public void setFormatLength(Short formatLength)
    {
        this.formatLength = formatLength;
    }


    public Short getLabelIndex()
    {
        return labelIndex;
    }


    public void setLabelIndex(Short labelStringIndex)
    {
        this.labelIndex = labelStringIndex;
    }


    public Short getLabelOffset()
    {
        return labelOffset;
    }


    public void setLabelOffset(Short labelStart)
    {
        this.labelOffset = labelStart;
    }


    public Short getLabelLength()
    {
        return labelLength;
    }


    public void setLabelLength(Short labelLength)
    {
        this.labelLength = labelLength;
    }


    public @Nullable String getFormat()
    {
        return dataset.getSubHeaderString(formatIndex, formatOffset, formatLength).orElse(null);
    }


    public @Nullable String getLabel()
    {
        return dataset.getSubHeaderString(labelIndex, labelOffset, labelLength).orElse(null);
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("[formatDigits=");
        builder.append(formatDigits);
        builder.append(", formatDecimals=");
        builder.append(formatDecimals);
        builder.append(", informatDigits=");
        builder.append(informatDigits);
        builder.append(", informatDecimals=");
        builder.append(informatDecimals);
        builder.append(", formatIndex=");
        builder.append(formatIndex);
        builder.append(", formatOffset=");
        builder.append(formatOffset);
        builder.append(", formatLength=");
        builder.append(formatLength);
        builder.append(", labelIndex=");
        builder.append(labelIndex);
        builder.append(", labelOffset=");
        builder.append(labelOffset);
        builder.append(", labelLength=");
        builder.append(labelLength);
        builder.append("]");
        return builder.toString();
    }

}
