package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservationTest
{

    private Variable createNumericVariable(FormatType formatType)
    {
        Variable variable = mock(Variable.class);
        when(variable.getType()).thenReturn(VariableType.NUMERIC);
        if (formatType != null)
        {
            Format format = mock(Format.class);
            when(format.getType()).thenReturn(formatType);
            when(variable.getFormat()).thenReturn(format);
        }
        else
        {
            when(variable.getFormat()).thenReturn(null);
        }
        when(variable.getName()).thenReturn("VAR1");
        return variable;
    }


    private Variable createCharVariable()
    {
        Variable variable = mock(Variable.class);
        when(variable.getType()).thenReturn(VariableType.CHARACTER);
        when(variable.getFormat()).thenReturn(null);
        when(variable.getName()).thenReturn("CHARVAR");
        return variable;
    }


    @Test
    void getFormattedValue_nullValue()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(null);
        obs.putValue(variable, null);
        assertNull(obs.getFormattedValue(variable));
    }


    @Test
    void getFormattedValue_numericNoFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(null);
        obs.putValue(variable, 42.0);
        assertEquals(42.0, obs.getFormattedValue(variable));
    }


    @Test
    void getFormattedValue_dateFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.DATE);
        // SAS epoch is 1960-01-01, 366 days = 1961-01-01 (1960 is a leap year)
        obs.putValue(variable, 366.0);
        Object result = obs.getFormattedValue(variable);
        assertEquals(LocalDate.of(1961, 1, 1), result);
    }


    @Test
    void getFormattedValue_datetimeFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.DATETIME);
        // 86400 seconds = 1 day after epoch
        obs.putValue(variable, 86400.0);
        Object result = obs.getFormattedValue(variable);
        assertEquals(LocalDateTime.of(1960, 1, 2, 0, 0, 0), result);
    }


    @Test
    void getFormattedValue_timeFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.TIME);
        // 3661 seconds = 01:01:01
        obs.putValue(variable, 3661.0);
        Object result = obs.getFormattedValue(variable);
        assertEquals(LocalTime.of(1, 1, 1), result);
    }


    @Test
    void getFormattedValue_julianFormat_doesNotThrow()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.JULIAN);
        obs.putValue(variable, 2021001.0);
        // Should not throw, should return the raw value
        Object result = obs.getFormattedValue(variable);
        assertEquals(2021001.0, result);
    }


    @Test
    void getFormattedValue_characterFormat_doesNotThrow()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.CHARACTER);
        obs.putValue(variable, 42.0);
        // Should not throw
        Object result = obs.getFormattedValue(variable);
        assertEquals(42.0, result);
    }


    @Test
    void getFormattedValue_numericFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.NUMERIC);
        obs.putValue(variable, 3.14);
        assertEquals(3.14, obs.getFormattedValue(variable));
    }


    @Test
    void getFormattedValues_multipleVariables()
    {
        Observation obs = new Observation();
        Variable num = createNumericVariable(null);
        Variable chr = createCharVariable();
        obs.putValue(num, 1.0);
        obs.putValue(chr, "Hello");
        List<Object> values = obs.getFormattedValues();
        assertEquals(2, values.size());
    }


    @Test
    void getFormattedValue_yymmddFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.YYMMDD);
        obs.putValue(variable, 366.0);
        Object result = obs.getFormattedValue(variable);
        assertEquals(LocalDate.of(1961, 1, 1), result);
    }


    @Test
    void getFormattedValue_monyyFormat()
    {
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.MONYY);
        obs.putValue(variable, 366.0);
        Object result = obs.getFormattedValue(variable);
        assertEquals(LocalDate.of(1961, 1, 1), result);
    }


    @Test
    void getFormattedValue_dateFormat_shortValue()
    {
        // A length-2 numeric column decodes as Short, not Double; the formatter must still work.
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.DATE);
        obs.putValue(variable, (short) 366);
        assertEquals(LocalDate.of(1961, 1, 1), obs.getFormattedValue(variable));
    }


    @Test
    void getFormattedValue_datetimeFormat_booleanValue()
    {
        // A length-1 numeric column decodes as Boolean; coerced to 1/0 rather than throwing.
        Observation obs = new Observation();
        Variable variable = createNumericVariable(FormatType.DATETIME);
        obs.putValue(variable, true);
        assertEquals(SasConstants.EPOCH.plusSeconds(1), obs.getFormattedValue(variable));
    }
}
