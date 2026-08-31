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

import org.thshsh.struct.StructEntity;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenPrefix;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
@StructEntity(charset = LibraryXpt.METADATA_CHARSET_NAME, trimAndPad = true)
public class LibraryHeaderXpt
{

    public static final String HEADER_1 = XptConstants.HEADER_TAG
            + "LIBRARY HEADER RECORD!!!!!!!000000000000000000000000000000  ";

    public static final String HEADER_2 = "SAS     ";

    public static final String HEADER_3 = "SASLIB  ";

    @StructTokenPrefix(
    {
            @StructToken(type = TokenType.String, constant = HEADER_1),
            @StructToken(type = TokenType.String, constant = HEADER_2),
            @StructToken(type = TokenType.String, constant = HEADER_2),
            @StructToken(type = TokenType.String, constant = HEADER_3),
    })
    @StructToken(order = 1, length = 8)
    public String version;

    @StructToken(order = 2, length = 8)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.String, constant = XptConstants.SPACES_24)
    })
    public String os;

    @StructToken(order = 3, length = 16)
    public String createdString;

    @StructToken(order = 4, length = 16)
    public String modifiedString;

    public String getVersion()
    {
        return version;
    }


    public void setVersion(String version)
    {
        this.version = version;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Header [magic=");

        builder.append(", version=");
        builder.append(version);
        builder.append(", os=");
        builder.append(os);
        builder.append(", created=");
        builder.append(createdString);
        builder.append(", modified=");
        builder.append(modifiedString);
        builder.append("]");
        return builder.toString();
    }

}
