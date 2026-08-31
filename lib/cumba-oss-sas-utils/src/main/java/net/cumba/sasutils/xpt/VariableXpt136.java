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

import org.apache.commons.lang3.NotImplementedException;
import org.thshsh.struct.StructEntity;

/**
 * Not sure if this will ever be needed, but supposedly some files written on VAX/VMX operating
 * systems use this?
 *
 * @author daniel.watson
 *
 */
@StructEntity(trimAndPad = true, charset = LibraryXpt.METADATA_CHARSET_NAME)
// TODO need to change the suffix to 48 bytes
public class VariableXpt136 extends VariableXpt
{

    public VariableXpt136()
    {
        throw new NotImplementedException();
    }

}
