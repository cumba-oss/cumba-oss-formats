/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Observation
{

    public static final Logger LOGGER = LoggerFactory.getLogger(Observation.class);

    protected Map<Variable, @Nullable Object> values = new LinkedHashMap<>();

    public void putValue(Variable variable, @Nullable Object val)
    {
        values.put(variable, val);
    }


    public Map<Variable, @Nullable Object> getValues()
    {
        return values;
    }


    public @Nullable Object getValue(Variable v)
    {
        return values.get(v);
    }

}
