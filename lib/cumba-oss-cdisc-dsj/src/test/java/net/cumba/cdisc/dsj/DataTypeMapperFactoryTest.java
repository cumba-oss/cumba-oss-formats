package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataTypeMapperFactoryTest
{

    private DataTypeMapperFactory factory;

    @BeforeEach
    void setUp()
    {
        factory = new DataTypeMapperFactory();
    }

    // --- NoMapper ---


    @Test
    void testUnknownTargetTypeReturnsNoMapper()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.STRING,
                ColumnTargetDataType.UNKNOWN);
        assertEquals("hello", mapper.mapValueToTargetType("hello"));
        assertEquals(42, mapper.mapValueToTargetType(42));
        assertNull(mapper.mapValueToTargetType(null));
    }


    @Test
    void testOtherTargetTypeReturnsNoMapper()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.STRING,
                ColumnTargetDataType.OTHER);
        assertEquals("hello", mapper.mapValueToTargetType("hello"));
    }

    // --- DateTimeMapper ---


    @Test
    void testDateTimeMapper()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        // 2020-01-01T00:00:00 is 60 years after SAS epoch (1960-01-01)
        // 60 years * 365.25 days/year * 86400 seconds/day ~ 1893456000
        Object result = mapper.mapValueToTargetType("1960-01-01T00:00:00");
        assertEquals(0L, result);
    }


    @Test
    void testDateTimeMapperNonEpoch()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        // 1960-01-02T00:00:00 should be 86400 seconds after SAS epoch
        Object result = mapper.mapValueToTargetType("1960-01-02T00:00:00");
        assertEquals(86400L, result);
    }


    @Test
    void testDateTimeMapperInvalid()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        assertNull(mapper.mapValueToTargetType("not-a-date"));
    }

    // --- DateMapper ---


    @Test
    void testDateMapper()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATE,
                ColumnTargetDataType.INTEGER);
        // SAS epoch is 1960-01-01, so same date = 0 days
        Object result = mapper.mapValueToTargetType("1960-01-01");
        assertEquals(0L, result);
    }


    @Test
    void testDateMapperOneDay()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATE,
                ColumnTargetDataType.INTEGER);
        Object result = mapper.mapValueToTargetType("1960-01-02");
        assertEquals(1L, result);
    }


    @Test
    void testDateMapperInvalid()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATE,
                ColumnTargetDataType.INTEGER);
        assertNull(mapper.mapValueToTargetType("bad-date"));
    }

    // --- TimeMapper ---


    @Test
    void testTimeMapper()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.TIME,
                ColumnTargetDataType.INTEGER);
        // 01:02:03 = 1*3600 + 2*60 + 3 = 3723
        Object result = mapper.mapValueToTargetType("01:02:03");
        assertEquals(3723L, result);
    }


    @Test
    void testTimeMapperMidnight()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.TIME,
                ColumnTargetDataType.INTEGER);
        Object result = mapper.mapValueToTargetType("00:00:00");
        assertEquals(0L, result);
    }


    @Test
    void testTimeMapperInvalid()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.TIME,
                ColumnTargetDataType.INTEGER);
        assertNull(mapper.mapValueToTargetType("bad-time"));
    }

    // --- DecimalMapper ---


    @Test
    void testDecimalMapperWithNumber()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DECIMAL,
                ColumnTargetDataType.DECIMAL);
        assertEquals(42.0, mapper.mapValueToTargetType(42));
        assertEquals(3.14, mapper.mapValueToTargetType(3.14));
    }


    @Test
    void testDecimalMapperWithNull()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DECIMAL,
                ColumnTargetDataType.DECIMAL);
        Object result = mapper.mapValueToTargetType(null);
        assertTrue(result instanceof Double);
        assertTrue(Double.isNaN((Double) result));
    }


    @Test
    void testDecimalMapperWithString()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DECIMAL,
                ColumnTargetDataType.DECIMAL);
        assertEquals(3.14, mapper.mapValueToTargetType("3.14"));
    }


    @Test
    void testDecimalMapperWithInvalidString()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DECIMAL,
                ColumnTargetDataType.DECIMAL);
        Object result = mapper.mapValueToTargetType("not-a-number");
        assertTrue(result instanceof Double);
        assertTrue(Double.isNaN((Double) result));
    }

    // --- INTEGER target → DecimalMapper (J2: floating point, no truncation) ---


    @Test
    void testIntegerTargetMapsToFloatingPointWithNumber()
    {
        // J2: an INTEGER target maps through DecimalMapper so a non-conformant decimal is NOT
        // truncated (3.7 stays 3.7, was 3).
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.INTEGER,
                ColumnTargetDataType.INTEGER);
        assertEquals(42.0, mapper.mapValueToTargetType(42));
        assertEquals(3.7, mapper.mapValueToTargetType(3.7));
    }


    @Test
    void testIntegerTargetMapsToFloatingPointWithNull()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.INTEGER,
                ColumnTargetDataType.INTEGER);
        // DecimalMapper yields NaN for a null/unparseable value; the DOUBLE column path treats it
        // as missing.
        assertEquals(Double.NaN, mapper.mapValueToTargetType(null));
    }


    @Test
    void testIntegerTargetMapsToFloatingPointWithString()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.INTEGER,
                ColumnTargetDataType.INTEGER);
        assertEquals(123.0, mapper.mapValueToTargetType("123"));
    }


    @Test
    void testIntegerTargetMapsToFloatingPointWithInvalidString()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.INTEGER,
                ColumnTargetDataType.INTEGER);
        assertEquals(Double.NaN, mapper.mapValueToTargetType("not-a-number"));
    }

    // --- Unsupported combinations ---


    @Test
    void testUnsupportedCombinationReturnsNoMapper()
    {
        // A target type that doesn't match known mappings
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.STRING,
                ColumnTargetDataType.INTEGER);
        // DecimalMapper is returned for STRING->INTEGER with a warning (J2: floating point).
        assertEquals(42.0, mapper.mapValueToTargetType(42));
    }

    // --- Inverse direction: SAS-epoch DateMapper ---


    @Test
    void testDateMapperInverseEpoch()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER);
        assertEquals("1960-01-01", m.mapValueFromTargetType(0L));
    }


    @Test
    void testDateMapperInverseAdamSample()
    {
        // 2014-01-02 is 19724 days after 1960-01-01 (verified via java.time.LocalDate.until)
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER);
        long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.of(1960, 1, 1),
                java.time.LocalDate.of(2014, 1, 2));
        assertEquals("2014-01-02", m.mapValueFromTargetType(days));
    }


    @Test
    void testDateMapperInverseNegative()
    {
        // -365 days before 1960-01-01 = 1959-01-01
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER);
        assertEquals("1959-01-01", m.mapValueFromTargetType(-365L));
    }


    @Test
    void testDateMapperInverseDoubleRounding()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER);
        assertEquals("1960-01-02", m.mapValueFromTargetType(1.0d));
        assertEquals("1960-01-02", m.mapValueFromTargetType(0.6d));
    }


    @Test
    void testDateMapperInverseNullAndNaN()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER);
        assertNull(m.mapValueFromTargetType(null));
        assertNull(m.mapValueFromTargetType(Double.NaN));
        assertNull(m.mapValueFromTargetType("not-a-number"));
    }

    // --- Inverse direction: SAS-epoch DateTimeMapper ---


    @Test
    void testDateTimeMapperInverseEpoch()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        assertEquals("1960-01-01T00:00:00", m.mapValueFromTargetType(0L));
    }


    @Test
    void testDateTimeMapperInverseOneDay()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        assertEquals("1960-01-02T00:00:00", m.mapValueFromTargetType(86400L));
    }


    @Test
    void testDateTimeMapperInverseAlwaysIncludesSeconds()
    {
        // Even when seconds==0 the writer must emit "HH:mm:ss" for spec compliance
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        // 1960-01-01T12:00:00 = 12 * 3600 seconds after epoch
        assertEquals("1960-01-01T12:00:00", m.mapValueFromTargetType(12L * 3600L));
    }


    @Test
    void testDateTimeMapperInverseNullAndNaN()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        assertNull(m.mapValueFromTargetType(null));
        assertNull(m.mapValueFromTargetType(Double.NaN));
    }

    // --- Inverse direction: TimeMapper ---


    @Test
    void testTimeMapperInverse()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER);
        assertEquals("01:02:03", m.mapValueFromTargetType(3723L));
        assertEquals("00:00:00", m.mapValueFromTargetType(0L));
        assertEquals("23:59:59", m.mapValueFromTargetType(86399L));
    }


    @Test
    void testTimeMapperInverseAlwaysFullHmsFormat()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER);
        // 12:00:00 — ensure seconds aren't elided
        assertEquals("12:00:00", m.mapValueFromTargetType(12L * 3600L));
    }


    @Test
    void testTimeMapperInverseWrapsOutOfRange()
    {
        // Seconds beyond a day wrap; below 0 wraps too. Avoids ofSecondOfDay throwing.
        IDataTypeMapper m = factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER);
        assertEquals("00:00:00", m.mapValueFromTargetType(86400L));
        assertEquals("23:59:59", m.mapValueFromTargetType(-1L));
    }


    @Test
    void testTimeMapperInverseNullAndNaN()
    {
        IDataTypeMapper m = factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER);
        assertNull(m.mapValueFromTargetType(null));
        assertNull(m.mapValueFromTargetType(Double.NaN));
    }

    // --- Inverse direction: Unix-epoch (R) mappers ---


    @Test
    void testUnixDateMapperInverseEpoch()
    {
        IDataTypeMapper m = factory.getUnixEpochMapper(ColumnDataType.DATE);
        assertEquals("1970-01-01", m.mapValueFromTargetType(0L));
    }


    @Test
    void testUnixDateMapperInverseSample()
    {
        IDataTypeMapper m = factory.getUnixEpochMapper(ColumnDataType.DATE);
        long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.of(1970, 1, 1),
                java.time.LocalDate.of(2014, 1, 2));
        assertEquals("2014-01-02", m.mapValueFromTargetType(days));
    }


    @Test
    void testUnixDateMapperInverseNullAndNaN()
    {
        IDataTypeMapper m = factory.getUnixEpochMapper(ColumnDataType.DATE);
        assertNull(m.mapValueFromTargetType(null));
        assertNull(m.mapValueFromTargetType(Double.NaN));
    }


    @Test
    void testUnixDateTimeMapperInverseEpoch()
    {
        IDataTypeMapper m = factory.getUnixEpochMapper(ColumnDataType.DATETIME);
        assertEquals("1970-01-01T00:00:00", m.mapValueFromTargetType(0L));
    }


    @Test
    void testUnixDateTimeMapperInverseSample()
    {
        IDataTypeMapper m = factory.getUnixEpochMapper(ColumnDataType.DATETIME);
        // 2024-01-02T03:04:05 UTC -> seconds since 1970-01-01 UTC
        long secs = java.time.LocalDateTime.of(2024, 1, 2, 3, 4, 5)
                .toEpochSecond(java.time.ZoneOffset.UTC);
        assertEquals("2024-01-02T03:04:05", m.mapValueFromTargetType(secs));
    }


    @Test
    void testUnixEpochMapperFallsBackForUnsupportedKind()
    {
        // STRING is not a date/time kind; the helper returns the no-op mapper which is identity.
        IDataTypeMapper m = factory.getUnixEpochMapper(ColumnDataType.STRING);
        assertEquals("hello", m.mapValueFromTargetType("hello"));
        assertEquals(42, m.mapValueFromTargetType(42));
    }
}
