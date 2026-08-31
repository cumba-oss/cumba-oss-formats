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

enum PageType
{

    // metadata with potentially compressed row data in subheaders
    META(0, true, false),
    META2(16384, true, false),
    CMETA(128, true, false),

    // data pages storing uncompressed row data with no subheaders
    DATA(256, false, true),
    DATA2(384, false, true),

    MIXED1(512, true, true), // Mix pages that contain all valid records
    MIXED2(640, true, true), // Mix pages that contain valid and deleted records

    AMD(1024, true, false), // amended metadata information

    // unknown
    COMP(-28672, false, false);

    final int id;

    final boolean meta;

    final boolean data;

    PageType(int id, boolean meta, boolean data)
    {
        this.id = id;
        this.meta = meta;
        this.data = data;
    }


    public boolean mixed()
    {
        return data && meta;
    }


    public static @Nullable PageType fromId(Integer id)
    {
        for (PageType s : values())
        {
            if (id.equals(s.id))
            {
                return s;
            }
        }
        return null;
    }
}
