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

import net.cumba.sasutils.bdat.ColumnSizeSubHeader;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class ColumnSizeSubHeader32 extends ColumnSizeSubHeader
{

    @StructToken(order = 0)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "00000000", validate = false)
    })
    public Integer numColumns;

    @Override
    public Long getNumColumns()
    {
        return numColumns.longValue();
    }


    public void setNumColumns(Integer numColumns)
    {
        this.numColumns = numColumns;
    }

}
