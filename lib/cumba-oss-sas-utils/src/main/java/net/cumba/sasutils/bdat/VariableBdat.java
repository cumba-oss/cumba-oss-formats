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

import net.cumba.sasutils.Format;
import net.cumba.sasutils.Variable;
import net.cumba.sasutils.VariableType;
import org.jspecify.annotations.Nullable;

public class VariableBdat extends Variable
{

    protected FormatAndLabelSubHeader formatAndLabel;

    protected ColumnName columnName;

    protected ColumnAttributes attributes;

    public VariableBdat(FormatAndLabelSubHeader formatAndLabel, ColumnName columnName,
            ColumnAttributes atts)
    {
        super();
        this.formatAndLabel = formatAndLabel;
        this.columnName = columnName;
        this.attributes = atts;
    }


    public Long getOffset()
    {
        return attributes.getOffset();
    }


    @Override
    public @Nullable String getName()
    {
        return columnName.getName();
    }


    public Integer getLength()
    {
        return attributes.width;
    }


    @Override
    public VariableType getType()
    {
        return attributes.getVariableType();
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("VariableBdat [formatAndLabel=");
        builder.append(formatAndLabel);
        builder.append(", columnName=");
        builder.append(columnName);
        builder.append(", attributes=");
        builder.append(attributes);
        builder.append("]");
        return builder.toString();
    }


    public FormatAndLabelSubHeader getFormatAndLabelSubHeader()
    {
        return formatAndLabel;
    }


    @Override
    public @Nullable Format getFormat()
    {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public @Nullable String getLabel()
    {
        return formatAndLabel.getLabel();
    }


    /**
     * Retrieve the sort order attribute of the variable.<br/>
     * <ul>
     * <li>{@code = 0} means not sorted
     * <li>{@code > 0} means sorted in ascending direction.
     * <li>{@code < 0} means sorted in descending direction.
     * </ul>
     * The {@code abs(value)} is the 1 based index of the variable in sorting order. 1 means first
     * (primary sort order), 2 means second (secondary sort order) and on.
     *
     * @return a number that describes the sort attribute of the variable.
     */
    public int getSortOrder()
    {
        Byte sov = columnName.getSortOrder();
        if (sov == null)
        {
            // not set (define as not sorted)
            return 0;
        }
        byte so = sov.byteValue();

        if (so == 0)
        {
            // not sorted
            return 0;
        }

        if ((so & 0x80) != 0)
        {
            // descending direction
            so = (byte) (-1 * (so & 0x7F));
        }
        return so;
    }


    /**
     * Returns true if this variable is part of the dataset's sort key.
     */
    public boolean isSortKey()
    {
        return getSortOrder() != 0;
    }

}
