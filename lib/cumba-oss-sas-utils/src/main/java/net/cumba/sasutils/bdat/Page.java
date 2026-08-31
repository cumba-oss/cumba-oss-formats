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
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

// header is set via setHeader(), subHeaderPointers is built lazily, and pageBuffer is assigned by
// the parser after construction; the constructor only sets dataset, so these are not initialised
// there — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class Page
{

    DatasetBdat dataset;

    long startByte;

    PageHeader header;

    List<SubHeaderPointer> subHeaderPointers;

    SeekableByteArrayInputStream pageBuffer;

    /**
     * Bit string marking deleted records: '0' = valid, '1' = deleted. Only populated for pages with
     * deleted records (Mixed2, Data2).
     */
    private @Nullable String deletedMarkers;

    public Page(DatasetBdat dataset)
    {
        this.dataset = dataset;
    }


    public void setHeader(PageHeader header)
    {
        this.header = header;
    }


    public long getStartByte()
    {
        return startByte;
    }


    public @Nullable PageType getPageType()
    {
        return header.getPageType();
    }


    public long getBlockCount()
    {
        return header.blockCount;
    }


    public int getSubHeaderCount()
    {
        return header.subHeaderCount;
    }


    public List<SubHeaderPointer> getSubHeaderPointers()
    {
        if (subHeaderPointers == null)
        {
            subHeaderPointers = new ArrayList<>();
        }
        return subHeaderPointers;
    }


    public void setSubHeaderPointers(List<SubHeaderPointer> subHeaderPointers)
    {
        this.subHeaderPointers = subHeaderPointers;
    }


    public Stream<SubHeaderPointer> getDataSubHeaderPointers()
    {
        return getSubHeaderPointers().stream()
                .filter(shp -> shp.getSignature() == SubHeaderSignature.DATA);
    }


    public Boolean hasObservations()
    {
        return getPageType() != null && getPageType() == PageType.DATA && header.blockCount > 0;
    }


    public Long getTotalObservationCount()
    {
        return getBlockObservationCount() + getHeaderObservationCount();
    }


    public Long getBlockObservationCount()
    {

        PageType pt = getPageType();
        if (pt == null)
        {
            return 0L;
        }
        if (pt.mixed())
        {
            return Math.min(dataset.rowSizeSubHeader.getMixedPageRowCount(),
                    dataset.rowSizeSubHeader.getRowCount());
        }
        else if (pt.data)
        {
            return getBlockCount();
        }
        return 0L;
    }


    public Integer getHeaderObservationCount()
    {
        return (int) getDataSubHeaderPointers().count();
    }


    /**
     * Returns the byte offset within the page where block row data begins. On MIX pages the data
     * area follows the subheader pointers and may need 4 bytes of alignment padding to reach an
     * 8-byte boundary.
     * <p>
     * Some tools (notably Stat/Transfer) omit this padding. To handle both cases, the padding is
     * only applied when the 4 bytes at the candidate position look like filler (all zeros or all
     * spaces).
     */
    public long getDataAreaOffset()
    {
        long offset = dataset.getPageHeaderStruct().byteCount()
                + ((long) dataset.getSubHeaderPointerStruct().byteCount() * getSubHeaderCount());
        long remainder = offset % 8;
        if (remainder != 0 && pageBuffer != null)
        {
            pageBuffer.seek(offset);
            if (pageBuffer.available() >= 4)
            {
                byte[] pad = new byte[4];
                int padRead = pageBuffer.read(pad, 0, 4);
                if (padRead < 4)
                {
                    throw new IllegalStateException(
                            "short read in getDataAreaOffset: expected 4, got " + padRead);
                }
                boolean allZeros = pad[0] == 0 && pad[1] == 0 && pad[2] == 0 && pad[3] == 0;
                boolean allSpaces = pad[0] == 0x20 && pad[1] == 0x20 && pad[2] == 0x20
                        && pad[3] == 0x20;
                if (allZeros || allSpaces)
                {
                    offset += remainder;
                }
            }
        }
        return offset;
    }


    /**
     * Reads the deleted record bitmap from the page buffer. The bitmap is located after the
     * subheader pointers and row data, at an offset indicated by the deleted pointer in the page
     * header.
     *
     * Each bit corresponds to a data block row: '0' = valid record, '1' = deleted record.
     */
    public void readDeletedMarkers()
    {
        if (!header.hasDeletedRecords())
        {
            deletedMarkers = null;
            return;
        }

        int rowLength = dataset.rowSizeSubHeader.getRowLength().intValue();
        int numDataRows = header.blockCount - header.subHeaderCount;

        java.nio.ByteOrder byteOrder = dataset.header1.littleEndian
                ? java.nio.ByteOrder.LITTLE_ENDIAN
                : java.nio.ByteOrder.BIG_ENDIAN;
        long deletedPointer = header.getDeletedPointer(byteOrder);

        // Locate the bitmap from the SAME data-area start the row reader uses (which applies the
        // 4-byte alignment padding only when it is actually present). Computing the alignment
        // independently here could disagree with getDataAreaOffset() on non-SAS-written MIX pages
        // that omit the padding, reading the bitmap from the wrong offset.
        long bitmapOffset = getDataAreaOffset() + ((long) numDataRows * rowLength) + deletedPointer;

        int bitmapByteCount = (numDataRows + 7) / 8;

        pageBuffer.seek(bitmapOffset);
        byte[] bitmapBytes = new byte[bitmapByteCount];
        int bitmapRead = pageBuffer.read(bitmapBytes, 0, bitmapByteCount);
        if (bitmapRead < bitmapByteCount)
        {
            throw new IllegalStateException("short read in readDeletedMarkers: expected "
                    + bitmapByteCount + ", got " + bitmapRead);
        }

        StringBuilder sb = new StringBuilder(bitmapByteCount * 8);
        for (byte b : bitmapBytes)
        {
            sb.append(String.format("%8s", Integer.toString(b & 0xFF, 2)).replace(' ', '0'));
        }
        deletedMarkers = sb.toString();
    }


    /**
     * Returns whether the block row at the given index is deleted.
     *
     * @param blockIndex
     *            the zero-based index of the row within the data blocks
     * @return true if the row is deleted, false if valid
     */
    public boolean isBlockRowDeleted(long blockIndex)
    {
        if (deletedMarkers == null)
        {
            return false;
        }
        if (blockIndex < 0 || blockIndex >= deletedMarkers.length())
        {
            return false;
        }
        return deletedMarkers.charAt((int) blockIndex) == '1';
    }


    /**
     * Returns the deleted markers string, or null if not a page with deleted records.
     */
    public @Nullable String getDeletedMarkers()
    {
        return deletedMarkers;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append(", getBlockCount()=");
        builder.append(getBlockCount());
        builder.append(", getSubHeaderCount()=");
        builder.append(getSubHeaderCount());
        builder.append(", startByte=");
        builder.append(startByte);
        builder.append(", subHeaderPointers=");
        builder.append(subHeaderPointers);
        builder.append("]");
        return builder.toString();
    }

}
