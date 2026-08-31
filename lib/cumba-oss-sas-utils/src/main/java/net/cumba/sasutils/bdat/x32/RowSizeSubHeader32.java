/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.bdat.x32;

import net.cumba.sasutils.bdat.RowSizeSubHeader;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenPrefix;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class RowSizeSubHeader32 extends RowSizeSubHeader
{

    @StructToken(order = -10, length = 16) // 32 bytes if 64
    public byte[] unknown0;

    @StructToken(order = 0) // TODO long if 64
    public Integer rowLength;

    @StructToken(order = 10) // long if 64
    public Integer rowCount;

    @StructToken(order = 15, length = 4) // double if 64
    public Integer deletedRowCount;

    @StructToken(order = 20, length = 4) // long if 64
    public Integer unknown00;

    @StructToken(order = 30) // long if 64
    public Integer columnCountP1;

    @StructToken(order = 40) // long if 64
    public Integer columnCountP2;

    @StructToken(order = 45, length = 8) // 16 bytes if 64
    public byte[] unknown1;

    @StructToken(order = 50)
    public Integer pageSize; // long if 64

    @StructToken(order = 55, length = 4) // double if 64
    public byte[] unknown2;

    @StructToken(order = 60)
    public Integer mixedPageRowCount; // long if 64

    @StructTokenPrefix(
    {
            // double if x64
            @StructToken(type = TokenType.Bytes, constant = "FFFFFFFFFFFFFFFF"),
            @StructToken(type = TokenType.Bytes,
                    constant = "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                    validate = false)
    })
    @StructToken(order = 70) // always equal to pageheader sig
    @StructTokenSuffix(
    {
            // double if x64
            @StructToken(type = TokenType.Bytes,
                    constant = "00000000000000000000000000000000000000000000000000000000000000000000000000000000",
                    validate = false)
    })
    public Integer pageSequence;

    @StructToken(order = 80)
    public Integer unknown3; // long if 64 , value = 1

    @StructToken(order = 85)
    public Short unknown4; // value = 2

    @StructToken(order = 86, constant = "0000") // 6 bytes if x64
    public byte[] unknown5;

    @StructToken(order = 90)
    public Integer pagesWithSubHeadersCount; // long if 64

    @StructToken(order = 100)
    public Short lastPageSubHeadersCount;

    @StructToken(order = 105, constant = "0000") // 6 bytes if x64
    public byte[] unknown6;

    @StructToken(order = 110)
    public Integer pagesWithSubHeadersCountDuplicate; // long if 64

    @StructToken(order = 120)
    public Short numLastPageSubHeadersPlusTwo; // offset

    @StructToken(order = 125, constant = "0000") // 6 bytes if x64
    public byte[] unknown7;

    @StructToken(order = 130)
    public Integer pageCount; // long if x64

    @StructToken(order = 131)
    public Short unknown8; // values 22,26,9,56 observed

    @StructToken(order = 132, constant = "0000") // 6 bytes if x64
    public byte[] unknown9;

    @StructToken(order = 133)
    public Integer unknown10; // long if x64

    @StructToken(order = 134)
    public Short unknown11; // values 7|8

    @StructToken(order = 135, constant = "0000", validate = false) // 6 bytes if x64, values
                                                                   // observerd 0100
    public byte[] unknown12;

    @StructToken(order = 136, type = TokenType.Bytes,
            constant = "00000000000000000000000000000000000000000000000000000000000000000000000000000000",
            validate = false) // double if x64
    public byte[] unknown13;

    @Override
    public Long getColumnCount()
    {
        return columnCountP1.longValue() + columnCountP2.longValue();
    }


    @Override
    public Long getColumnCountP1()
    {
        return columnCountP1.longValue();
    }


    @Override
    public Long getColumnCountP2()
    {
        return columnCountP2.longValue();
    }


    @Override
    public Long getRowLength()
    {
        return rowLength.longValue();
    }


    public void setRowLength(Integer rowLength)
    {
        this.rowLength = rowLength;
    }


    @Override
    public Long getRowCount()
    {
        return rowCount.longValue();
    }


    public void setRowCount(Integer rowCount)
    {
        this.rowCount = rowCount;
    }


    @Override
    public Long getPageCount()
    {
        return pageCount.longValue();
    }


    public void setPageCount(Integer pageCount)
    {
        this.pageCount = pageCount;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("RowSizeSubHeader32 [");
        builder.append(", rowLength=");
        builder.append(rowLength);
        builder.append(", rowCount=");
        builder.append(rowCount);
        builder.append(", deletedRowCount=");
        builder.append(deletedRowCount);
        builder.append(", columnCountP1=");
        builder.append(columnCountP1);
        builder.append(", columnCountP2=");
        builder.append(columnCountP2);
        builder.append(", pageSize=");
        builder.append(pageSize);
        builder.append(", mixedPageRowCount=");
        builder.append(mixedPageRowCount);
        builder.append(", pageSequence=");
        builder.append(pageSequence);
        builder.append(", pagesWithSubHeadersCount=");
        builder.append(pagesWithSubHeadersCount);
        builder.append(", lastPageSubHeadersCount=");
        builder.append(lastPageSubHeadersCount);
        builder.append(", pagesWithSubHeadersCountDuplicate=");
        builder.append(pagesWithSubHeadersCountDuplicate);
        builder.append(", numLastPageSubHeadersPlusTwo=");
        builder.append(numLastPageSubHeadersPlusTwo);
        builder.append(", labelOffset=");
        builder.append(labelOffset);
        builder.append(", labelLength=");
        builder.append(labelLength);
        builder.append(", creatorSoftwareOffset=");
        builder.append(creatorSoftwareOffset);
        builder.append(", creatorSoftwareLength=");
        builder.append(creatorSoftwareLength);
        builder.append(", compressionMethodOffset=");
        builder.append(compressionMethodOffset);
        builder.append(", compressionMethodLength=");
        builder.append(compressionMethodLength);
        builder.append(", creatorProcOffset=");
        builder.append(creatorProcOffset);
        builder.append(", creatorProcLength=");
        builder.append(creatorProcLength);
        builder.append(", columnTextHeadersCount=");
        builder.append(columnTextHeadersCount);
        builder.append(", columnNameMaxLength=");
        builder.append(columnNameMaxLength);
        builder.append(", columnLabelMaxLength=");
        builder.append(columnLabelMaxLength);
        builder.append(", fullPageRowsCount=");
        builder.append(fullPageRowsCount);
        builder.append("]");
        return builder.toString();
    }


    @Override
    public Long getDeletedRowCount()
    {
        return deletedRowCount.longValue();
    }


    public void setDeletedRowCount(Integer deletedRowCount)
    {
        this.deletedRowCount = deletedRowCount;
    }


    public byte[] getUnknown1()
    {
        return unknown1;
    }


    public void setUnknown1(byte[] unknown1)
    {
        this.unknown1 = unknown1;
    }


    @Override
    public Long getPageSize()
    {
        return pageSize.longValue();
    }


    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }


    public byte[] getUnknown2()
    {
        return unknown2;
    }


    public void setUnknown2(byte[] unknown2)
    {
        this.unknown2 = unknown2;
    }


    @Override
    public Long getMixedPageRowCount()
    {
        return mixedPageRowCount.longValue();
    }


    public void setMixedPageRowCount(Integer mixedPageRowCount)
    {
        this.mixedPageRowCount = mixedPageRowCount;
    }


    @Override
    public Integer getPageSequence()
    {
        return pageSequence;
    }


    public void setPageSequence(Integer pageSequence)
    {
        this.pageSequence = pageSequence;
    }


    public Integer getUnknown3()
    {
        return unknown3;
    }


    public void setUnknown3(Integer unknown3)
    {
        this.unknown3 = unknown3;
    }


    public Short getUnknown4()
    {
        return unknown4;
    }


    public void setUnknown4(Short unknown4)
    {
        this.unknown4 = unknown4;
    }


    public byte[] getUnknown5()
    {
        return unknown5;
    }


    public void setUnknown5(byte[] unknown5)
    {
        this.unknown5 = unknown5;
    }


    @Override
    public Long getPagesWithSubHeadersCount()
    {
        return pagesWithSubHeadersCount.longValue();
    }


    public void setPagesWithSubHeadersCount(Integer pagesWithSubHeadersCount)
    {
        this.pagesWithSubHeadersCount = pagesWithSubHeadersCount;
    }


    @Override
    public Long getPagesWithSubHeadersCountDuplicate()
    {
        return pagesWithSubHeadersCountDuplicate.longValue();
    }


    public void setPagesWithSubHeadersCountDuplicate(Integer pagesWithSubHeadersCountDuplicate)
    {
        this.pagesWithSubHeadersCountDuplicate = pagesWithSubHeadersCountDuplicate;
    }


    public byte[] getUnknown7()
    {
        return unknown7;
    }


    public void setUnknown7(byte[] unknown7)
    {
        this.unknown7 = unknown7;
    }


    public Short getUnknown8()
    {
        return unknown8;
    }


    public void setUnknown8(Short unknown8)
    {
        this.unknown8 = unknown8;
    }


    public byte[] getUnknown9()
    {
        return unknown9;
    }


    public void setUnknown9(byte[] unknown9)
    {
        this.unknown9 = unknown9;
    }


    public Integer getUnknown10()
    {
        return unknown10;
    }


    public void setUnknown10(Integer unknown10)
    {
        this.unknown10 = unknown10;
    }


    public Short getUnknown11()
    {
        return unknown11;
    }


    public void setUnknown11(Short unknown11)
    {
        this.unknown11 = unknown11;
    }


    public byte[] getUnknown12()
    {
        return unknown12;
    }


    public void setUnknown12(byte[] unknown12)
    {
        this.unknown12 = unknown12;
    }


    public byte[] getUnknown13()
    {
        return unknown13;
    }


    public void setUnknown13(byte[] unknown13)
    {
        this.unknown13 = unknown13;
    }

}
