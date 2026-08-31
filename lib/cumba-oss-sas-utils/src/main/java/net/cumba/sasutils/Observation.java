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

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
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


    public List<@Nullable Object> getFormattedValues()
    {
        return values.keySet().stream().<@Nullable Object> map(this::getFormattedValue).toList();
    }


    public @Nullable Object getValue(Variable v)
    {
        return values.get(v);
    }


    public @Nullable Object getFormattedValue(Variable vm)
    {
        Object val = getValue(vm);
        LOGGER.trace("getFormattedValue value: {}", val);
        if (val == null) return null;
        Format format = vm.getFormat();
        FormatType formatType = format != null ? format.getType() : null;
        if (vm.getType() == VariableType.NUMERIC && formatType != null)
        {
            switch (formatType)
            {
            // TODO need to not show time zone for these since SAS doesnt show them either
            case DATE, YYMMDD, DDMMYY, MMDDYY, MONYY:
                val = SasConstants.EPOCH.toLocalDate().plusDays(asLong(val));
                break;
            case DATETIME:
                val = SasConstants.EPOCH.plusSeconds(asLong(val));
                break;
            case TIME:
                val = LocalTime.MIDNIGHT.plusSeconds(asLong(val));
                break;
            case NUMERIC:
                break;
            case JULIAN, CHARACTER:
            default:
                break;
            }
        }

        return val;
    }


    /**
     * Coerces a decoded SAS numeric to a long for date/time arithmetic. Short numeric columns are
     * decoded as {@link Boolean} (length 1) or {@link Short} (length 2) rather than {@link Double},
     * so a plain {@code (Double)} or {@code (Number)} cast would fail for length-1 columns.
     */
    private static long asLong(Object val)
    {
        if (val instanceof Boolean b)
        {
            return b ? 1L : 0L;
        }
        return ((Number) val).longValue();
    }

}
