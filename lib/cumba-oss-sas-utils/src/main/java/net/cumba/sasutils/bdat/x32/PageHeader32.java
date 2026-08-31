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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.cumba.sasutils.bdat.PageHeader;
import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class PageHeader32 extends PageHeader
{

    public static final Struct<PageHeader32> STRUCT = Struct.create(PageHeader32.class);

    @StructToken(order = 1, length = 8)
    public byte[] unknown;

    @StructToken(order = 2, length = 4)
    public byte[] deletedPointer;

    /**
     * The deleted pointer is a 4-byte int at page offset 12, which falls at index 8 within the
     * unknown bytes (page offset 12 - pageSequence 4 bytes = 8).
     */
    @Override
    public long getDeletedPointer(ByteOrder byteOrder)
    {
        return ByteBuffer.wrap(deletedPointer, 0, 4).order(byteOrder).getInt();
    }

}
