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

public enum CompressionType
{

    NONE(0), TRUNCATED(1), COMPRESSED(4);

    private final int id;

    CompressionType(int id)
    {
        this.id = id;
    }


    public static @Nullable CompressionType fromId(Integer id)
    {
        for (CompressionType s : values())
        {
            if (id.equals(s.id)) return s;
        }
        return null;
    }

}
