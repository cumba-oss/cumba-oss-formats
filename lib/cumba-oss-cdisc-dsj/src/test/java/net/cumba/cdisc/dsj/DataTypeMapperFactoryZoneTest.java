package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Q8 — offset-qualified temporal values are read and converted rather than silently dropped to
 * missing, and they land on the SAME anchor as timezone-naive ones.
 *
 * <p>
 * ⭐⭐ <b>This file pins a COUPLING, which is why the assertions compare the two populations against
 * each other rather than each against a constant.</b> The ruling's operative sentence is not "use
 * UTC" but <i>"it is just important that it is the timezone that is used for values without a
 * specified one"</i>. A test that asserted "a naive value maps to X" and another that asserted "an
 * offset value maps to X" would both keep passing while someone moved one half and not the other —
 * and two populations that disagree about the anchor is the actual defect, worse than the
 * drop-to-missing it replaced, because nothing about it looks wrong.
 * </p>
 */
class DataTypeMapperFactoryZoneTest
{

    private final DataTypeMapperFactory factory = new DataTypeMapperFactory();

    private long datetime(String aValue)
    {
        Object mapped = factory.getMapper(ColumnDataType.DATETIME, ColumnTargetDataType.INTEGER)
                .mapValueToTargetType(aValue);
        assertNotNull(mapped, aValue + " must not map to a missing value");
        return ((Number) mapped).longValue();
    }


    private long time(String aValue)
    {
        Object mapped = factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER)
                .mapValueToTargetType(aValue);
        assertNotNull(mapped, aValue + " must not map to a missing value");
        return ((Number) mapped).longValue();
    }


    private long date(String aValue)
    {
        Object mapped = factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER)
                .mapValueToTargetType(aValue);
        assertNotNull(mapped, aValue + " must not map to a missing value");
        return ((Number) mapped).longValue();
    }


    /**
     * ⭐⭐ The coupling itself. One instant written three ways — naive, {@code Z}, and a +02:00
     * offset on the matching local clock — must produce ONE SAS datetime.
     *
     * <p>
     * Move the normalisation target and the second and third diverge from the first. Move the
     * anchor naive values are read at (say, to the host zone) and the first diverges from the other
     * two. Either edit reds here, which is the whole reason this is one test.
     * </p>
     */
    @Test
    void naiveAndOffsetQualifiedDatetimesLandOnOneAnchor()
    {
        long naive = datetime("2025-06-15T10:30:00");
        assertEquals(naive, datetime("2025-06-15T10:30:00Z"),
                "a Z-qualified value must land where the naive value of the same wall clock does");
        assertEquals(naive, datetime("2025-06-15T12:30:00+02:00"),
                "+02:00 12:30 IS 10:30 UTC — converted, not truncated and not dropped");
        assertEquals(naive, datetime("2025-06-15T05:30:00-05:00"),
                "and the same from the other side of the meridian");
    }


    /** The same coupling for TIME — ISO 8601 allows an offset there too. */
    @Test
    void naiveAndOffsetQualifiedTimesLandOnOneAnchor()
    {
        long naive = time("10:30:00");
        assertEquals(naive, time("10:30:00Z"));
        assertEquals(naive, time("12:30:00+02:00"));
    }


    /**
     * The constant is the coupling made visible, so it is asserted too — but only after the value
     * comparisons above, which would still catch a divergence if someone inlined it away.
     */
    @Test
    void theStorageZoneIsUtc()
    {
        assertEquals(ZoneOffset.UTC, DataTypeMapperFactory.STORAGE_ZONE,
                "UTC keeps a stored SAS datetime independent of the machine that read the file");
    }


    /**
     * The filed defect, stated as the value it produced: before Q8 this returned {@code null}, i.e.
     * a missing value, with no warning anywhere. A regulator reading that file saw no datetime at
     * all where one had been recorded.
     */
    @Test
    void anOffsetQualifiedDatetimeIsNoLongerAMissingValue()
    {
        assertEquals(datetime("2025-06-15T10:30:00"), datetime("2025-06-15T10:30:00Z"));
    }


    /**
     * ⚠ TWO more sites than the filing named. {@code DateMapper} and {@code TimeMapper} end in the
     * same bare catch returning null, so each swallowed its own offset form. A date's zone
     * designator carries no instant to convert, but dropping the whole date over a suffix is the
     * same silent loss.
     */
    @Test
    void aZoneDesignatorOnADateKeepsTheDateInsteadOfDiscardingIt()
    {
        assertEquals(date("2025-06-15"), date("2025-06-15Z"));
        assertEquals(date("2025-06-15"), date("2025-06-15+02:00"));
    }


    /**
     * A day wrap is unrepresentable in a second-of-day, so the wrapped clock time is the answer —
     * pinned as MEASURED and explained in the mapper, not left for someone to discover as a bug.
     * {@code 00:30+02:00} is 22:30 UTC on the previous day; a SAS time has no day to carry.
     */
    @Test
    void anOffsetTimeThatCrossesMidnightWrapsBecauseASasTimeHasNoDate()
    {
        assertEquals(time("22:30:00"), time("00:30:00+02:00"));
    }


    /**
     * The round trip stays consistent: an offset-qualified input comes back as the naive spelling
     * of the same instant in the storage zone. Not a separate convention — the same anchor read
     * back out.
     */
    @Test
    void anOffsetQualifiedValueRoundTripsAsItsNaiveSpellingInTheStorageZone()
    {
        IDataTypeMapper mapper = factory.getMapper(ColumnDataType.DATETIME,
                ColumnTargetDataType.INTEGER);
        Object sas = mapper.mapValueToTargetType("2025-06-15T12:30:00+02:00");
        assertNotNull(sas);
        assertEquals("2025-06-15T10:30:00", mapper.mapValueFromTargetType(sas));
    }


    /**
     * Non-vacuity: the lenient parse must not have become "accept anything". A value that is not a
     * temporal at all still maps to missing, so the tests above are pinning a conversion rather
     * than a parser that stopped checking.
     */
    @Test
    void genuinelyUnparseableValuesStillMapToMissing()
    {
        assertNull(factory.getMapper(ColumnDataType.DATETIME, ColumnTargetDataType.INTEGER)
                .mapValueToTargetType("not-a-datetime"));
        assertNull(factory.getMapper(ColumnDataType.TIME, ColumnTargetDataType.INTEGER)
                .mapValueToTargetType("25:99:99"));
        assertNull(factory.getMapper(ColumnDataType.DATE, ColumnTargetDataType.INTEGER)
                .mapValueToTargetType("2025-13-45"));
    }

}
