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

import java.time.LocalDateTime;
import net.cumba.sasutils.SasConstants;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction, so
// the constructor does not initialise them — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public abstract class Header3
{

    @StructToken(order = 1)
    public Double createdTimestamp;

    @StructToken(order = 2)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "00000000000000000000000000000000",
                    validate = false)
    })
    public Double modifiedTimestamp;

    @StructToken(order = 4)
    public Integer headerSize;

    @StructToken(order = 5)
    public Integer pageSize;

    protected Header3()
    {
    }


    public Double getCreatedTimestamp()
    {
        return createdTimestamp;
    }


    public void setCreatedTimestamp(Double created)
    {
        this.createdTimestamp = created;
    }


    public Double getModifiedTimestamp()
    {
        return modifiedTimestamp;
    }


    public void setModifiedTimestamp(Double modified)
    {
        this.modifiedTimestamp = modified;
    }


    public Integer getHeaderSize()
    {
        return headerSize;
    }


    public void setHeaderSize(Integer headerSize)
    {
        this.headerSize = headerSize;
    }


    public Integer getPageSize()
    {
        return pageSize;
    }


    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }


    public abstract Long getPageCount();


    public LocalDateTime getCreated()
    {
        return SasConstants.toDateTime(createdTimestamp);
    }


    public LocalDateTime getModified()
    {
        return SasConstants.toDateTime(modifiedTimestamp);
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Header3 [created=");
        builder.append(getCreated());
        builder.append(", modified=");
        builder.append(getModified());
        builder.append(", headerSize=");
        builder.append(headerSize);
        builder.append(", pageSize=");
        builder.append(pageSize);
        builder.append(", pageCount=");
        builder.append(getPageCount());
        builder.append("]");
        return builder.toString();
    }

}
