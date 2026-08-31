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

import net.cumba.sasutils.bdat.ColumnSizeSubHeader;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class ColumnSizeSubHeader64 extends ColumnSizeSubHeader
{

    @StructToken(order = 0)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000000000000000", validate = false)
    })
    public Long numColumns;

    @Override
    public Long getNumColumns()
    {
        return numColumns;
    }


    public void setNumColumns(Long numColumns)
    {
        this.numColumns = numColumns;
    }

}
