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

import net.cumba.sasutils.VariableType;
import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public abstract class ColumnAttributes
{

    public static final Struct<ColumnAttributes> STRUCT = Struct.create(ColumnAttributes.class);

    @StructToken(order = 1)
    public Integer width;

    @StructToken(order = 3)
    public Short nameLengthId;

    @StructToken(order = 4)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Byte, constant = "0", validate = false)
    })
    public Byte variableTypeId;

    public void setVariableTypeId(Byte variableType)
    {
        this.variableTypeId = variableType;
    }


    public VariableType getVariableType()
    {
        int index = variableTypeId - 1;
        VariableType[] values = VariableType.values();
        if (index < 0 || index >= values.length)
        {
            throw new IllegalArgumentException("Invalid variable type id: " + variableTypeId);
        }
        return values[index];
    }


    public abstract Long getOffset();


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("ColumnAttributes [offset=");
        builder.append(getOffset());
        builder.append(", width=");
        builder.append(width);
        builder.append(", nameLengthId=");
        builder.append(nameLengthId);
        builder.append(", variableTypeId=");
        builder.append(variableTypeId);
        builder.append("]");
        return builder.toString();
    }

}
