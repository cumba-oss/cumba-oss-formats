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
import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;

// @StructToken fields plus signature/subHeader are populated by the deserialiser / parser after
// construction, so the constructor does not initialise them — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public abstract class SubHeaderPointer
{

    public static final Struct<SubHeaderPointer> STRUCT = Struct.create(SubHeaderPointer.class);

    @StructToken(order = 2)
    public Byte compressionTypeId; // 0=none,1=truncated(ignore data),4= compressed row data with
                                   // control byte

    /**
     * 0 Row Size, Column Size, Subheader Counts, Column Format and Label, in Uncompressed file 1
     * Column Text, Column Names, Column Attributes, Column List 1 all subheaders (including row
     * data), in Compressed file.
     */
    @StructToken(order = 3)
    // @StructTokenSuffix({@StructToken(type = TokenType.Bytes,constant = "0000",validate = false)})
    // //TODO 6 bytes when 64
    public Byte categoryId;

    public @Nullable SubHeaderSignature signature;

    public SubHeader subHeader;

    public abstract Long getPageOffset();


    public abstract Long getLength();


    public Byte getCompressionTypeId()
    {
        return compressionTypeId;
    }


    public void setCompressionTypeId(Byte compressionTypeId)
    {
        this.compressionTypeId = compressionTypeId;
    }


    public @Nullable CompressionType getCompressionType()
    {
        return CompressionType.fromId(compressionTypeId.intValue());
    }


    public @Nullable SubHeaderSignature getSignature()
    {
        return signature;
    }


    public void setSignature(@Nullable SubHeaderSignature signature)
    {
        this.signature = signature;
    }


    public SubHeader getSubHeader()
    {
        return subHeader;
    }


    public void setSubHeader(SubHeader subHeader)
    {
        this.subHeader = subHeader;
    }


    public Byte getCategoryId()
    {
        return categoryId;
    }


    public void setCompressed(Byte compressed)
    {
        this.categoryId = compressed;
    }


    public @Nullable SubHeaderCategory getCategory()
    {
        return SubHeaderCategory.fromId(categoryId.intValue());
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("SubHeaderPointer [pageOffset=");
        builder.append(getPageOffset());
        builder.append(", length=");
        builder.append(getLength());
        builder.append(", compressionTypeId=");
        builder.append(compressionTypeId);
        builder.append(", categoryId=");
        builder.append(categoryId);
        builder.append(", signature=");
        builder.append(signature);
        builder.append(", subHeader=");
        builder.append(subHeader);
        builder.append(", getCompressionType()=");
        builder.append(getCompressionType());
        builder.append(", getCategory()=");
        builder.append(getCategory());
        builder.append("]");
        return builder.toString();
    }


    // Abstract base with 32/64-bit binary-format subclasses; getClass() identity is load-bearing
    // for correctly distinguishing 32-bit vs 64-bit subheader pointers.
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
        SubHeaderPointer other = (SubHeaderPointer) aObj;
        return Objects.equals(getPageOffset(), other.getPageOffset())
                && Objects.equals(getLength(), other.getLength())
                && Objects.equals(compressionTypeId, other.compressionTypeId)
                && Objects.equals(categoryId, other.categoryId)
                && Objects.equals(signature, other.signature)
                && Objects.equals(subHeader, other.subHeader);
    }


    @Override
    public int hashCode()
    {
        return Objects.hash(getPageOffset(), getLength(), compressionTypeId, categoryId, signature,
                subHeader);
    }

}
