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

import net.cumba.sasutils.bdat.Header3;
import org.thshsh.struct.StructToken;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class Header364 extends Header3
{

    @StructToken(order = 6)
    public Long pageCount;

    @Override
    public Long getPageCount()
    {
        return pageCount;
    }


    public void setPageCount(Long pageCount)
    {
        this.pageCount = pageCount;
    }

}
