package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link DataTypeMapperFactory} diagnostics.
 *
 * <p>
 * Every warning here says "I was asked for a conversion I do not really support and handed back
 * something else". The returned mapper is asserted elsewhere; what was never asserted is that the
 * caller is told — so all five {@code LOGGER.log} calls could be deleted unnoticed, and a column
 * quietly converted by the wrong rule would leave no trace.
 * </p>
 */
class DataTypeMapperFactoryWarningLogTest
{

    @Test
    void testOtherTargetTypeWarns()
    {
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            IDataTypeMapper m = f.getMapper(ColumnDataType.STRING, ColumnTargetDataType.OTHER);
            assertNotNull(m);
            assertTrue(log.logged(Level.WARNING, "Target Type OTHER is not supported!"),
                    log.dump());
        }
    }


    @Test
    void testIntegerTargetFromNonTemporalTypeWarns()
    {
        // dataType=string with targetDataType=integer is not a combination the spec sanctions;
        // the factory falls back to the decimal mapper (J2) and must say so.
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            IDataTypeMapper m = f.getMapper(ColumnDataType.STRING, ColumnTargetDataType.INTEGER);
            assertEquals(Double.valueOf(3.5d), m.mapValueToTargetType("3.5"));
            assertTrue(log.logged(Level.WARNING, "is not really supported!"), log.dump());
        }
    }


    @Test
    void testDecimalTargetFromNonDecimalTypeWarns()
    {
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            IDataTypeMapper m = f.getMapper(ColumnDataType.STRING, ColumnTargetDataType.DECIMAL);
            assertEquals(Double.valueOf(1.25d), m.mapValueToTargetType("1.25"));
            assertTrue(log.logged(Level.WARNING, "is not really supported!"), log.dump());
        }
    }


    @Test
    void testDecimalTargetFromDecimalTypeIsSilent()
    {
        // The sanctioned combination must NOT warn: without this, "always warn" would pass the
        // test above and the warning would stop meaning anything.
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            IDataTypeMapper m = f.getMapper(ColumnDataType.DECIMAL, ColumnTargetDataType.DECIMAL);
            assertEquals(Double.valueOf(1.25d), m.mapValueToTargetType("1.25"));
            assertTrue(log.records().isEmpty(), log.dump());
        }
    }


    @Test
    void testUnknownTargetTypeIsSilent()
    {
        // UNKNOWN means "no targetDataType member", which is the normal SDTM case — not a defect,
        // so it must stay silent.
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            assertNotNull(f.getMapper(ColumnDataType.DATE, ColumnTargetDataType.UNKNOWN));
            assertTrue(log.records().isEmpty(), log.dump());
        }
    }


    @Test
    void testNullTargetTypeFallsBackToNoMapperAndWarns()
    {
        // The final defensive arm: a target type outside the enum's four values. Unreachable from
        // the parser (ColumnTargetDataType.getFor maps an absent member to UNKNOWN), but getMapper
        // is public API, and the arm must return the identity mapper rather than null.
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            IDataTypeMapper m = f.getMapper(ColumnDataType.DATE, null);
            assertNotNull(m);
            assertEquals("2025-06-15", m.mapValueToTargetType("2025-06-15"));
            assertTrue(log.logged(Level.WARNING, "is not supported!"), log.dump());
        }
    }


    @Test
    void testDecimalMapperLogsTheRejectedValueAtTrace()
    {
        // A non-numeric value in a decimal column degrades to NaN (missing). That degradation is
        // recorded at TRACE; deleting the call makes a silently dropped value entirely invisible.
        DataTypeMapperFactory f = new DataTypeMapperFactory();
        try (LogCapture log = LogCapture.on(DataTypeMapperFactory.class))
        {
            IDataTypeMapper m = f.getMapper(ColumnDataType.DECIMAL, ColumnTargetDataType.DECIMAL);
            assertEquals(Double.valueOf(Double.NaN), m.mapValueToTargetType("not-a-number"));
            // System.Logger.Level.TRACE maps onto java.util.logging FINER.
            assertEquals(1, log.records().size(), log.dump());
            assertEquals(Level.FINER, log.records().get(0).getLevel(), log.dump());
        }
    }
}
