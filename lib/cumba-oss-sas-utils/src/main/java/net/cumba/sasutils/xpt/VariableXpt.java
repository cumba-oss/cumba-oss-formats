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

import net.cumba.sasutils.Format;
import net.cumba.sasutils.FormatType;
import net.cumba.sasutils.Variable;
import net.cumba.sasutils.VariableType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// All @StructToken fields are populated by the org.thshsh.struct deserialiser after construction
// (reflective field assignment NullAway cannot see), so the no-arg constructor does not initialise
// them — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class VariableXpt extends Variable
{

    protected static final Logger LOGGER = LoggerFactory.getLogger(VariableXpt.class);

    @StructToken(order = 0)
    public Short variableTypeId;

    // Always zero
    @StructToken(order = 1)
    public Short nameHash;

    @StructToken(order = 2)
    public Short length;

    /**
     * The index of this variable (starts at 1)
     */
    @StructToken(order = 3)
    public Short number;

    @StructToken(order = 4, length = 8)
    public String name;

    @StructToken(order = 5, length = 40)
    public String label;

    @StructToken(order = 6, length = 8)
    public String formatTypeString;

    @StructToken(order = 7)
    public Short formatLength;

    @StructToken(order = 8)
    public Short formatDecimals;

    @StructToken(order = 9)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000")
    }) // 2 empty byte suffix
    public Short formatJustifyId; // always zero

    @StructToken(order = 11, length = 8)
    public String informatTypeString;

    @StructToken(order = 12)
    public Short informatLength;

    @StructToken(order = 13)
    public Short informatDecimals;

    /**
     * the byte position of this variable in the observation block
     */
    @StructToken(order = 14)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes,
                    constant = "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
    })
    public Integer position;

    @Override
    public VariableType getType()
    {
        int index = variableTypeId - 1;
        VariableType[] values = VariableType.values();
        if (index < 0 || index >= values.length)
        {
            throw new IllegalArgumentException("Invalid variable type id: " + variableTypeId);
        }
        return values[index];
    }


    @Override
    public Format getFormat()
    {
        return new FormatXpt();
    }


    public Short getVariableTypeId()
    {
        return variableTypeId;
    }


    public void setVariableTypeId(Short variableTypeId)
    {
        this.variableTypeId = variableTypeId;
    }


    public Short getNameHash()
    {
        return nameHash;
    }


    public void setNameHash(Short none)
    {
        this.nameHash = none;
    }


    public Short getLength()
    {
        return length;
    }


    public void setLength(Short length)
    {
        this.length = length;
    }


    public Short getNumber()
    {
        return number;
    }


    public void setNumber(Short number)
    {
        this.number = number;
    }


    @Override
    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    @Override
    public String getLabel()
    {
        return label;
    }


    public void setLabel(String label)
    {
        this.label = label;
    }


    public String getFormatTypeString()
    {
        return formatTypeString;
    }


    public void setFormatTypeString(String formatTypeString)
    {
        this.formatTypeString = formatTypeString;
    }


    public Short getFormatLength()
    {
        return formatLength;
    }


    public void setFormatLength(Short formatLength)
    {
        this.formatLength = formatLength;
    }


    public Short getFormatDecimals()
    {
        return formatDecimals;
    }


    public void setFormatDecimals(Short formatDecimals)
    {
        this.formatDecimals = formatDecimals;
    }


    public Short getFormatJustifyId()
    {
        return formatJustifyId;
    }


    public void setFormatJustifyId(Short formatJustifyId)
    {
        this.formatJustifyId = formatJustifyId;
    }


    public String getInformatTypeString()
    {
        return informatTypeString;
    }


    public void setInformatTypeString(String informatTypeString)
    {
        this.informatTypeString = informatTypeString;
    }


    public Short getInformatLength()
    {
        return informatLength;
    }


    public void setInformatLength(Short informatLength)
    {
        this.informatLength = informatLength;
    }


    public Short getInformatDecimals()
    {
        return informatDecimals;
    }


    public void setInformatDecimals(Short informatDecimals)
    {
        this.informatDecimals = informatDecimals;
    }


    public Integer getPosition()
    {
        return position;
    }


    public void setPosition(Integer position)
    {
        this.position = position;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("VariableXpt [name=");
        builder.append(name);
        builder.append(", length=");
        builder.append(length);
        builder.append(", nameHash=");
        builder.append(nameHash);
        builder.append(", number=");
        builder.append(number);
        builder.append(", label=");
        builder.append(label);
        builder.append(", formatTypeString=");
        builder.append(formatTypeString);
        builder.append(", formatLength=");
        builder.append(formatLength);
        builder.append(", formatDecimals=");
        builder.append(formatDecimals);
        builder.append(", formatJustifyId=");
        builder.append(formatJustifyId);
        builder.append(", informatTypeString=");
        builder.append(informatTypeString);
        builder.append(", informatLength=");
        builder.append(informatLength);
        builder.append(", informatDecimals=");
        builder.append(informatDecimals);
        builder.append(", position=");
        builder.append(position);
        builder.append(", getVariableType()=");
        builder.append(getType());
        builder.append("]");
        return builder.toString();
    }

    public class FormatXpt extends Format
    {

        @Override
        public @Nullable FormatType getType()
        {
            if (formatTypeString == null) return null;
            return FormatType.fromString(formatTypeString);
        }

    }

}
