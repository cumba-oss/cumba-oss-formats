/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.bdat.x64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.cumba.sasutils.bdat.PageHeader;
import org.thshsh.struct.Struct;
import org.thshsh.struct.StructToken;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class PageHeader64 extends PageHeader
{

    public static final Struct<PageHeader64> STRUCT = Struct.create(PageHeader64.class);

    @StructToken(order = 1, length = 20)
    public byte[] unknown01;

    @StructToken(order = 2, length = 8)
    public byte[] deletedPointer;

    @Override
    public long getDeletedPointer(ByteOrder byteOrder)
    {
        return ByteBuffer.wrap(deletedPointer, 0, 8).order(byteOrder).getLong();
    }

}
