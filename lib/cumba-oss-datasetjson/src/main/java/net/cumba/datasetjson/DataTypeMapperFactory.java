package net.cumba.datasetjson;

import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

/**
 * Factory class that creates {@link IDataTypeMapper}'s per combination of {@link ColumnDataType}
 * and {@link ColumnTargetDataType}.
 */
@CustomLog
public class DataTypeMapperFactory
{

    /**
     * The default mapper that does not perform any mapping.
     */
    private static final NoMapper NO_MAPPER = new NoMapper();

    /**
     * Get a mapper for the given data type and target data type combination.
     *
     * @param aType
     *            the source data type.
     * @param aTargetType
     *            the target data type.
     * @return the appropriate mapper instance.
     */
    public IDataTypeMapper getMapper(ColumnDataType aType, ColumnTargetDataType aTargetType)
    {
        if (aTargetType == ColumnTargetDataType.UNKNOWN)
        {
            return NO_MAPPER;
        }
        if (aTargetType == ColumnTargetDataType.OTHER)
        {
            LOGGER.log(Level.WARNING, "Target Type OTHER is not supported!");
            return NO_MAPPER;
        }

        if (aTargetType == ColumnTargetDataType.INTEGER)
        {
            if (aType == ColumnDataType.DATETIME)
            {
                return new DateTimeMapper();
            }
            if (aType == ColumnDataType.TIME)
            {
                return new TimeMapper();
            }
            if (aType == ColumnDataType.DATE)
            {
                return new DateMapper();
            }

            LOGGER.log(Level.WARNING,
                    "Mapping from dataType={0} to targetDataType={1} is not really supported!",
                    aType, aTargetType);
            // J2: store an explicit targetDataType="integer" column as floating point too, so a
            // non-conformant decimal value is not truncated. Paired with the provider's
            // getTypeFor(INTEGER) -> DOUBLE so the value flows through the DOUBLE column path.
            return new DecimalMapper();

        }

        if (aTargetType == ColumnTargetDataType.DECIMAL)
        {
            if (aType != ColumnDataType.DECIMAL)
            {
                LOGGER.log(Level.WARNING,
                        "Mapping from dataType={0} to targetDataType={1} is not really supported!",
                        aType, aTargetType);
            }
            return new DecimalMapper();
        }

        LOGGER.log(Level.WARNING,
                "Mapping from dataType={0} to targetDataType={1} is not supported!", aType,
                aTargetType);

        return NO_MAPPER;
    }


    /**
     * Returns an inverse mapper that converts a numeric Unix-epoch (1970-01-01 UTC) value back to
     * an ISO 8601 string. Used on export from R-sourced columns ({@code Date} / {@code POSIXct})
     * whose values are days/seconds since the Unix epoch — distinct from the SAS epoch the standard
     * {@link #getMapper(ColumnDataType, ColumnTargetDataType)} mappers assume.
     *
     * <p>
     * Only {@link ColumnDataType#DATE} and {@link ColumnDataType#DATETIME} are supported; any other
     * type returns the no-op mapper.
     * </p>
     *
     * @param aType
     *            the logical type, expected to be {@link ColumnDataType#DATE} or
     *            {@link ColumnDataType#DATETIME}.
     * @return a Unix-epoch inverse mapper.
     */
    public IDataTypeMapper getUnixEpochMapper(ColumnDataType aType)
    {
        if (aType == ColumnDataType.DATE)
        {
            return new UnixDateMapper();
        }
        if (aType == ColumnDataType.DATETIME)
        {
            return new UnixDateTimeMapper();
        }
        return NO_MAPPER;
    }

    /**
     * A default implementation that does not perform any mapping and simply returns the given
     * value.
     */
    private static class NoMapper implements IDataTypeMapper
    {

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            return aValue;
        }
    }


    /**
     * Expect a ISO 8601 formatted datetime like <b><code>yyyy-MM-dd'T'HH:mm:ss</code></b> and maps
     * to a SAS datetime value.
     */
    private static class DateTimeMapper implements IDataTypeMapper
    {

        private static final long SAS_EPOCH_SECONDS = LocalDateTime.of(1960, 1, 1, 0, 0, 0)
                .toEpochSecond(ZoneOffset.UTC);

        private static final DateTimeFormatter ISO_LDT = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            if (aValue == null)
            {
                return null;
            }
            try
            {
                String valStr = aValue.toString();

                LocalDateTime localDateTime = LocalDateTime.parse(valStr);
                long epochSeconds = localDateTime.toEpochSecond(ZoneOffset.UTC);
                return Long.valueOf(epochSeconds - SAS_EPOCH_SECONDS);
            }
            catch (Exception _)
            {
                return null;
            }
        }


        @Override
        public @Nullable Object mapValueFromTargetType(Object aValue)
        {
            if (aValue == null)
            {
                return null;
            }
            if (!(aValue instanceof Number num))
            {
                return null;
            }
            double dn = num.doubleValue();
            if (Double.isNaN(dn) || Double.isInfinite(dn))
            {
                return null;
            }
            try
            {
                long sasSeconds = Math.round(dn);
                LocalDateTime ldt = LocalDateTime.ofEpochSecond(sasSeconds + SAS_EPOCH_SECONDS, 0,
                        ZoneOffset.UTC);
                return ldt.format(ISO_LDT);
            }
            catch (Exception _)
            {
                return null;
            }
        }
    }


    /**
     * Expect a ISO 8601 formatted datetime like <b><code>yyyy-MM-dd'T'HH:mm:ss</code></b> and maps
     * to a SAS datetime value.
     */
    private static class DateMapper implements IDataTypeMapper
    {

        private static final LocalDate SAS_EPOCH = LocalDate.of(1960, 1, 1);

        private static final long SAS_EPOCH_DAY = SAS_EPOCH.toEpochDay();

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            if (aValue == null)
            {
                return null;
            }
            try
            {
                String valStr = aValue.toString();

                LocalDate date = LocalDate.parse(valStr);
                return ChronoUnit.DAYS.between(SAS_EPOCH, date);
            }
            catch (Exception _)
            {
                return null;
            }
        }


        @Override
        public @Nullable Object mapValueFromTargetType(Object aValue)
        {
            if (aValue == null)
            {
                return null;
            }
            if (!(aValue instanceof Number num))
            {
                return null;
            }
            double dn = num.doubleValue();
            if (Double.isNaN(dn) || Double.isInfinite(dn))
            {
                return null;
            }
            try
            {
                long sasDays = Math.round(dn);
                LocalDate date = LocalDate.ofEpochDay(SAS_EPOCH_DAY + sasDays);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            catch (Exception _)
            {
                return null;
            }
        }
    }


    /**
     * Expect a ISO 8601 formatted time like <b><code>HH:mm:ss</code></b> and maps to a SAS time
     * value.
     */
    private static class TimeMapper implements IDataTypeMapper
    {

        private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

        private static final DateTimeFormatter ISO_HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            if (aValue == null)
            {
                return null;
            }
            try
            {
                String valStr = aValue.toString();
                String[] parts = valStr.split(":", 0);
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);

                return hours * 3600L + minutes * 60L + seconds;
            }
            catch (Exception _)
            {
                return null;
            }
        }


        @Override
        public @Nullable Object mapValueFromTargetType(Object aValue)
        {
            if (aValue == null)
            {
                return null;
            }
            if (!(aValue instanceof Number num))
            {
                return null;
            }
            double dn = num.doubleValue();
            if (Double.isNaN(dn) || Double.isInfinite(dn))
            {
                return null;
            }
            try
            {
                long sasSeconds = Math.round(dn);
                // Wrap into a single day so that out-of-range values still produce a valid HH:mm:ss
                long sod = ((sasSeconds % SECONDS_PER_DAY) + SECONDS_PER_DAY) % SECONDS_PER_DAY;
                return LocalTime.ofSecondOfDay(sod).format(ISO_HMS);
            }
            catch (Exception _)
            {
                return null;
            }
        }
    }


    /**
     * Inverse-only mapper for R {@code Date} columns: numeric value is days since the Unix epoch
     * (1970-01-01). The forward direction is intentionally not implemented; this mapper is only
     * used on export.
     */
    private static class UnixDateMapper implements IDataTypeMapper
    {

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            // Not used on the read side: DSJ never carries Unix-epoch encoded dates.
            return aValue;
        }


        @Override
        public @Nullable Object mapValueFromTargetType(Object aValue)
        {
            if (!(aValue instanceof Number num))
            {
                return null;
            }
            double dn = num.doubleValue();
            if (Double.isNaN(dn) || Double.isInfinite(dn))
            {
                return null;
            }
            try
            {
                long unixDays = Math.round(dn);
                return LocalDate.ofEpochDay(unixDays).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            catch (Exception _)
            {
                return null;
            }
        }
    }


    /**
     * Inverse-only mapper for R {@code POSIXct}/{@code POSIXt} columns: numeric value is seconds
     * since the Unix epoch (1970-01-01 UTC). Output is rendered in UTC; any tzdata attribute on the
     * source column is intentionally dropped because the DSJ integer-target representation does not
     * preserve a timezone.
     */
    private static class UnixDateTimeMapper implements IDataTypeMapper
    {

        private static final DateTimeFormatter ISO_LDT = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            return aValue;
        }


        @Override
        public @Nullable Object mapValueFromTargetType(Object aValue)
        {
            if (!(aValue instanceof Number num))
            {
                return null;
            }
            double dn = num.doubleValue();
            if (Double.isNaN(dn) || Double.isInfinite(dn))
            {
                return null;
            }
            try
            {
                long unixSeconds = Math.round(dn);
                return LocalDateTime.ofEpochSecond(unixSeconds, 0, ZoneOffset.UTC).format(ISO_LDT);
            }
            catch (Exception _)
            {
                return null;
            }
        }
    }


    /**
     * Try to map from a String to a Double value. This mapper supports {@link String}s and
     * {@link Number}s.
     */
    private static class DecimalMapper implements IDataTypeMapper
    {

        @Override
        public @Nullable Object mapValueToTargetType(@Nullable Object aValue)
        {
            if (aValue instanceof Number num)
            {
                // return the number as Double.
                return num.doubleValue();
            }
            if (aValue == null)
            {
                // map a null to Doube.NaN
                return Double.NaN;
            }
            try
            {
                // try to parse the string into a Double.
                return Double.valueOf(aValue.toString());
            }
            catch (Exception ex)
            {
                // return Double.NaN in case of any parsing error.
                LOGGER.log(Level.TRACE, ex);
                return Double.NaN;
            }
        }
    }

}
