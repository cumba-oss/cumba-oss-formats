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

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction, so
// the constructor does not initialise them — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public abstract class PageHeader
{

    @StructToken(order = 0)
    // @StructTokenSuffix({@StructToken(type = TokenType.Bytes,constant =
    // "000000000000000000000000",validate = false)}) //TODO needs to be 24 bytes for 64bit
    public Integer pageSequence;

    @StructToken(order = 10)
    protected Short pageTypeId;

    @StructToken(order = 20)
    protected Short blockCount;

    @StructToken(order = 30)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000", validate = false)
    })
    protected Short subHeaderCount;

    public @Nullable PageType getPageType()
    {
        return PageType.fromId(pageTypeId.intValue());
    }


    public Short getPageTypeId()
    {
        return pageTypeId;
    }


    public void setPageTypeId(Short pageType)
    {
        this.pageTypeId = pageType;
    }


    public Short getBlockCount()
    {
        return blockCount;
    }


    public void setBlockCount(Short blockCount)
    {
        this.blockCount = blockCount;
    }


    public Short getSubHeaderCount()
    {
        return subHeaderCount;
    }


    public void setSubHeaderCount(Short subHeaderCount)
    {
        this.subHeaderCount = subHeaderCount;
    }


    /**
     * Returns true if this page type can contain deleted records.
     */
    public boolean hasDeletedRecords()
    {
        PageType pt = getPageType();
        return pt == PageType.MIXED2 || pt == PageType.DATA2;
    }


    /**
     * Returns the deleted pointer value from the page header. Subclasses must implement this to
     * extract the value from their platform-specific unknown bytes.
     *
     * @param byteOrder
     *            the byte order of the file
     */
    public abstract long getDeletedPointer(java.nio.ByteOrder byteOrder);


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("PageHeader [pageSequence=");
        builder.append(pageSequence);
        builder.append(", pageTypeId=");
        builder.append(pageTypeId);
        builder.append(", blockCount=");
        builder.append(blockCount);
        builder.append(", subHeaderCount=");
        builder.append(subHeaderCount);
        builder.append("]");
        return builder.toString();
    }


    // Abstract base with 32/64-bit binary-format subclasses (PageHeader2/PageHeader2_64);
    // getClass() identity is load-bearing — equal-by-value across subclasses would be wrong
    // since the on-disk layouts differ.
    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(@Nullable Object aObj)
    {
        if (this == aObj)
        {
            return true;
        }
        if (aObj == null || getClass() != aObj.getClass())
        {
            return false;
        }
        PageHeader other = (PageHeader) aObj;
        return Objects.equals(pageSequence, other.pageSequence)
                && Objects.equals(pageTypeId, other.pageTypeId)
                && Objects.equals(blockCount, other.blockCount)
                && Objects.equals(subHeaderCount, other.subHeaderCount);
    }


    @Override
    public int hashCode()
    {
        return Objects.hash(pageSequence, pageTypeId, blockCount, subHeaderCount);
    }

}
