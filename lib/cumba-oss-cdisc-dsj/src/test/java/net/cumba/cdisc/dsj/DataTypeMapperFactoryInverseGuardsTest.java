package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The export direction's refusal cases.
 *
 * <p>
 * {@code mapValueFromTargetType} turns a stored number back into the ISO 8601 string a Dataset-JSON
 * file carries. Handed something it cannot turn into a date — a string, an infinity, a magnitude no
 * calendar has a day for — it must answer {@code null} (missing). The alternative is worse than
 * missing: a wrapped or clamped value is a date that looks real.
 * </p>
 */
class DataTypeMapperFactoryInverseGuardsTest
{

    private final DataTypeMapperFactory factory = new DataTypeMapperFactory();

    private IDataTypeMapper sasDate()
    {
        return factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER);
    }


    private IDataTypeMapper sasDateTime()
    {
        return factory.getMapper(ColumnDataType.DATETIME, ColumnTargetDataType.INTEGER);
    }


    private IDataTypeMapper sasTime()
    {
        return factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER);
    }


    @Test
    void testNonNumericInputIsRefusedByEveryInverseMapper()
    {
        assertNull(sasDate().mapValueFromTargetType("2025-06-15"));
        assertNull(sasDateTime().mapValueFromTargetType("2025-06-15T10:30:00"));
        assertNull(sasTime().mapValueFromTargetType("10:30:00"));
        assertNull(factory.getUnixEpochMapper(ColumnDataType.DATE)
                .mapValueFromTargetType("2025-06-15"));
        assertNull(factory.getUnixEpochMapper(ColumnDataType.DATETIME)
                .mapValueFromTargetType("2025-06-15T10:30:00"));
    }


    @Test
    void testInfinitiesAreRefusedByEveryInverseMapper()
    {
        for (Double d : new Double[]
        {
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN
        })
        {
            assertNull(sasDate().mapValueFromTargetType(d), () -> "SAS date " + d);
            assertNull(sasDateTime().mapValueFromTargetType(d), () -> "SAS datetime " + d);
            assertNull(sasTime().mapValueFromTargetType(d), () -> "SAS time " + d);
            assertNull(factory.getUnixEpochMapper(ColumnDataType.DATE).mapValueFromTargetType(d),
                    () -> "Unix date " + d);
            assertNull(
                    factory.getUnixEpochMapper(ColumnDataType.DATETIME).mapValueFromTargetType(d),
                    () -> "Unix datetime " + d);
        }
    }


    @Test
    void testMagnitudesOutsideTheCalendarAreRefused()
    {
        // A value far outside the representable range must come back missing rather than as a
        // wrapped-around date: 1e15 days is roughly 2.7 billion years.
        Double hugeDays = 1.0e15d;
        Double hugeSeconds = 1.0e17d;

        assertNull(sasDate().mapValueFromTargetType(hugeDays));
        assertNull(sasDateTime().mapValueFromTargetType(hugeSeconds));
        assertNull(
                factory.getUnixEpochMapper(ColumnDataType.DATE).mapValueFromTargetType(hugeDays));
        assertNull(factory.getUnixEpochMapper(ColumnDataType.DATETIME)
                .mapValueFromTargetType(hugeSeconds));
    }
}
