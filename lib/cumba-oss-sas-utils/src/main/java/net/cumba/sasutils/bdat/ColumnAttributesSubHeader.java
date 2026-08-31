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

import java.util.ArrayList;
import java.util.List;

import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// remainingLength is a @StructToken populated by the deserialiser; columnAttributes is built
// lazily; neither is initialised in the constructor — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class ColumnAttributesSubHeader extends SubHeader
{

    public static final Struct<ColumnAttributesSubHeader> STRUCT = Struct
            .create(ColumnAttributesSubHeader.class);

    @StructToken(order = 0)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Short, constant = "0", validate = false),
            @StructToken(type = TokenType.Short, constant = "0", validate = false),
            @StructToken(type = TokenType.Short, constant = "0", validate = false)
    })
    public Short remainingLength;

    protected List<ColumnAttributes> columnAttributes;

    public List<ColumnAttributes> getColumnAttributes()
    {
        if (columnAttributes == null) columnAttributes = new ArrayList<>();
        return columnAttributes;
    }


    public void setColumnAttributes(List<ColumnAttributes> columnNames)
    {
        this.columnAttributes = columnNames;
    }


    @SuppressWarnings("PMD.UselessParentheses")
    public int getNumColumnAttributes()
    {
        long size = pointer.getLength()
                - ((STRUCT.byteCount() + dataset.header1.getIntegerTokenType().size())
                        + (dataset.get64Bit() ? 12 : 8));
        return (int) size / ColumnAttributes.STRUCT.byteCount();
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("ColumnAttributesSubHeader [remainingLength=");
        builder.append(remainingLength);
        builder.append(", columnAttributes=");
        builder.append(columnAttributes);
        builder.append("]");
        return builder.toString();
    }

}
