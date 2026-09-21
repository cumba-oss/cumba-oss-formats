package net.cumba.cdisc.dsj;

import java.lang.System.Logger.Level;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;

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
     * ⭐⭐ <b>The single zone every temporal value in this file is anchored at — a COUPLING
     * INVARIANT, not a preference (Q8).</b>
     *
     * <p>
     * Dataset-JSON carries both timezone-NAIVE values ({@code 2025-06-15T10:30:00}) and
     * offset-qualified ones ({@code 2025-06-15T10:30:00Z}, {@code …+02:00}). A naive value's wall
     * clock is taken as written, i.e. anchored here; an offset-qualified value is converted to this
     * same zone before its wall clock is read. <b>The two must be the same zone.</b> If they ever
     * diverge, the two populations stop being comparable with each other — one file's
     * {@code 10:30:00} and another's {@code 10:30:00Z} would land on different SAS datetimes — and
     * that incomparability, not the old drop-to-missing, is the defect this ruling exists to
     * prevent.
     * </p>
     *
     * <p>
     * ⚠ So: <b>anyone changing the anchor must change the normalisation target in the same
     * commit</b>, which is why there is one constant rather than four literals.
     * {@code DataTypeMapperFactoryZoneTest} pins the two together rather than each separately — a
     * test per half would pass while the halves disagreed.
     * </p>
     *
     * <p>
     * UTC specifically, and not the host's zone: a stored SAS datetime must not depend on which
     * machine read the file, or the same study would produce different values — and potentially
     * different conformance verdicts — on a UTC server and a CET one. It is also what SAS's
     * {@code B8601DZ} informat does.
     * </p>
     */
    static final ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;

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
            // non-conformant decimal value is not truncated. Paired with the providers'
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
                .toEpochSecond(STORAGE_ZONE);

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

                // Q8: ISO 8601 permits an offset, and the spec permits it here. Parsing with
                // LocalDateTime.parse rejected one, and the catch below turned that into a MISSING
                // value with no warning — so "2025-06-15T10:30:00Z" silently became no data at
                // all. It is now read and converted to STORAGE_ZONE, which is where a naive value
                // already sits, so the two are comparable.
                TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(valStr,
                        OffsetDateTime::from, LocalDateTime::from);
                LocalDateTime localDateTime = parsed instanceof OffsetDateTime odt
                        ? odt.withOffsetSameInstant(STORAGE_ZONE).toLocalDateTime()
                        : LocalDateTime.from(parsed);
                long epochSeconds = localDateTime.toEpochSecond(STORAGE_ZONE);
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
                        STORAGE_ZONE);
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

                // Q8: ISO_DATE accepts an optional zone designator, ISO_LOCAL_DATE does not, and
                // LocalDate.parse uses the latter — so "2025-06-15Z" was dropped to missing with
                // no warning. A designator on a date carries no instant to convert (there is no
                // time of day to shift), so the calendar date is taken as written; the point is
                // that the date is KEPT rather than discarded over a suffix.
                LocalDate date = LocalDate.parse(valStr, DateTimeFormatter.ISO_DATE);
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
                // LocalTime.parse (ISO_LOCAL_TIME) accepts HH:mm, HH:mm:ss and fractional seconds
                // (HH:mm:ss.SSS); toSecondOfDay() drops any sub-second part, matching the
                // whole-second representation produced by mapValueFromTargetType. The old
                // split(":")+parseInt path threw NumberFormatException on fractional seconds and
                // silently returned null (data loss).
                //
                // Q8: ISO 8601 permits an offset on a TIME too ("10:30:00Z", "10:30:00+02:00"),
                // and ISO_LOCAL_TIME rejects one — the same silent drop as DateTimeMapper's, one
                // type over. Offsets are normalised to the same STORAGE_ZONE, so a time and a
                // datetime in the same file agree about what "the zone" means.
                //
                // ⚠ A second-of-day cannot carry a day wrap: 00:30+02:00 is 22:30 UTC on the
                // PREVIOUS day, and an OffsetTime has no date to move. The wrapped clock time is
                // therefore the only representable answer, and it is what SAS stores too — a SAS
                // time is a second-of-day, not an instant.
                TemporalAccessor parsed = DateTimeFormatter.ISO_TIME.parseBest(aValue.toString(),
                        OffsetTime::from, LocalTime::from);
                LocalTime localTime = parsed instanceof OffsetTime ot
                        ? ot.withOffsetSameInstant(STORAGE_ZONE).toLocalTime()
                        : LocalTime.from(parsed);
                return (long) localTime.toSecondOfDay();
            }
            catch (DateTimeException _)
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
                return LocalDateTime.ofEpochSecond(unixSeconds, 0, STORAGE_ZONE).format(ISO_LDT);
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
            // ⭐ Dataset-JSON's dataType note: "When a thousand separator is used in a decimal
            // represented as string, the comma is used." Until 2026-09-21 this path had NO comma
            // handling at all, so a spec-legal "1,234.5" threw and the cell degraded to missing --
            // the conformant file was the one that lost data. ThousandSeparators judges the comma
            // context only and leaves everything else to Double.valueOf, so the forms that parse
            // today keep parsing. A misplaced comma is a FORMAT VIOLATION, not a value to guess
            // at (owner, 2026-09-21), and degrades exactly as any other unparseable value does.
            String raw = aValue.toString();
            String withoutSeparators = ThousandSeparators.strip(raw);
            if (withoutSeparators == null)
            {
                // Recorded at TRACE for the same reason the catch below is: a silently dropped
                // value must not be entirely invisible.
                LOGGER.log(Level.TRACE,
                        "rejected a decimal whose comma is not a thousands" + " separator: " + raw);
                return Double.NaN;
            }
            try
            {
                // try to parse the string into a Double.
                return Double.valueOf(withoutSeparators);
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
