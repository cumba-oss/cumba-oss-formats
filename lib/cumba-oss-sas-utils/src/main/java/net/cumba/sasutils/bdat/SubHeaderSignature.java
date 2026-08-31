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

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

public enum SubHeaderSignature
{

    // The 32-bit ids below are sign-extended from the SAS7BDAT 32-bit subheader header layout.
    // Replacing the high-byte 0xFFFFFFFF prefix with the bare 0x... pattern would change the
    // signed long value because 0xF... is a negative int that widens to a negative long.
    ROW_SIZE(0x00000000F7F7F7F7L, 0xF7F7F7F700000000L, 0xF7F7F7F7FFFFFBFEL, 0xFFFFFFFFF7F7F7F7L),
    COLUMN_SIZE(0x00000000F6F6F6F6L, 0xF6F6F6F600000000L, 0xF6F6F6F6FFFFFBFEL, 0xFFFFFFFFF6F6F6F6L),
    SUB_HEADER_COUNT(0x00FCFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFC00L),
    STRING(0xFDFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFDL),
    COLUMN_NAME(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL),
    COLUMN_ATTRIBUTES(0xFCFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFCL),
    FORMAT_AND_LABEL(0xFEFBFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFBFEL),
    COLUMN_LIST(0xFEFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFEL),
    DATA((Long) null);

    // List type intentional: DATA carries a null sentinel which List.copyOf rejects.
    // The list is set once in the constructor from a varargs array and only read via contains().
    @SuppressWarnings("ImmutableEnumChecker")
    private final List<@Nullable Long> ids;

    SubHeaderSignature(@Nullable Long... ids)
    {
        this.ids = Arrays.asList(ids);
    }


    public static @Nullable SubHeaderSignature fromId(Long id)
    {
        for (SubHeaderSignature s : values())
        {
            if (s.ids.contains(id)) return s;
        }
        return null;
    }
}
