/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.xpt;

import org.jspecify.annotations.Nullable;

/**
 * missing value characters allow XPTs to imply different meanings behind missing data. The meaning
 * of each character is not well defined across all files e.g. 'I' might mean "Incomplete" while 'A
 * might mean "Absent" Since the list of possible characters is short we simply return an
 * enumeration in place of these
 *
 * @author daniel.watson
 *
 */
public enum MissingValue
{

    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z, UC('_');

    private final char character;

    MissingValue()
    {
        this.character = this.name().charAt(0);
    }


    MissingValue(char c)
    {
        this.character = c;
    }


    public static @Nullable MissingValue fromCharacter(char c)
    {
        for (MissingValue mv : values())
        {
            if (mv.character == c) return mv;
        }
        return null;
    }

}
