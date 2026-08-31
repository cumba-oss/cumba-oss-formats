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
import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;

// @StructToken fields plus dataset are populated by the deserialiser / parser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class ColumnName
{

    public static final Struct<ColumnName> STRUCT = Struct.create(ColumnName.class);

    @StructToken(order = 0)
    public Short index;

    @StructToken(order = 1)
    public Short start;

    @StructToken(order = 2)
    public Short length;

    /**
     * 1-based sort order index.
     * <ul>
     * <li>0 = not part of sort key,
     * <li>1 = primary sort column,
     * <li>2 = secondary, etc.
     * </ul>
     * The highest bit defines the sort order direction. 0 means ascending 1 means descending.
     */
    @StructToken(order = 3)
    public Byte sortOrder;

    @StructToken(order = 4)
    public Byte unknown;

    DatasetBdat dataset;

    public Short getIndex()
    {
        return index;
    }


    public void setIndex(Short index)
    {
        this.index = index;
    }


    public Short getStart()
    {
        return start;
    }


    public void setStart(Short start)
    {
        this.start = start;
    }


    public Short getLength()
    {
        return length;
    }


    public void setLength(Short length)
    {
        this.length = length;
    }


    public Byte getSortOrder()
    {
        return sortOrder;
    }


    public void setSortOrder(Byte sortOrder)
    {
        this.sortOrder = sortOrder;
    }


    public @Nullable String getName()
    {
        return dataset.getSubHeaderString(index, start, length).orElse(null);
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("ColumnName [index=");
        builder.append(index);
        builder.append(", start=");
        builder.append(start);
        builder.append(", length=");
        builder.append(length);
        builder.append(", sortOrder=");
        builder.append(sortOrder);
        builder.append(", getName()=");
        builder.append(dataset != null ? getName() : null);
        builder.append("]");
        return builder.toString();
    }

}
