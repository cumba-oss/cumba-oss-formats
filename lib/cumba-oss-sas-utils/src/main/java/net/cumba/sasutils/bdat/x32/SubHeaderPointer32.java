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

import net.cumba.sasutils.bdat.SubHeaderPointer;
import org.thshsh.struct.StructToken;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class SubHeaderPointer32 extends SubHeaderPointer
{

    @StructToken(order = 0)
    public Integer pageOffset;

    @StructToken(order = 1)
    public Integer length; // TODO long when 64

    @StructToken(order = 4, constant = "0000", validate = false)
    public byte[] padding;

    @Override
    public Long getPageOffset()
    {
        return pageOffset.longValue();
    }


    @Override
    public Long getLength()
    {
        return length.longValue();
    }

}
