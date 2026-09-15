package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Reference vectors for {@link ObservationIteratorXpt#ibmToIeee(byte[])}, the IBM 370 hex-float to
 * IEEE-754 conversion every numeric value in an XPT file goes through.
 * <p>
 * ⚠ <strong>Why vectors rather than one value.</strong> {@link ObservationIteratorXptTest} covers
 * 1.0, whose IBM exponent byte is 0x41 — and 0x41 is exactly the value for which
 * <code>exponent - 65</code> is zero, so every arithmetic mistake in the exponent path cancels out.
 * With only that value the conversion could shift the exponent the wrong way, by the wrong amount,
 * or not at all, and still look right. These vectors span four exponent bytes either side of it.
 * <p>
 * Each expected value is the exact rational the IBM encoding denotes:
 * <code>sign * mantissa * 16^(exponent - 64) / 2^56</code>, computed independently of this codec.
 */
class IbmFloatConversionTest
{

    private static byte[] hex(String s)
    {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
        {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }


    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
    {
            // exponent 0x41: the one case the existing test covers, kept as an anchor
            "4110000000000000, 1.0", "4120000000000000, 2.0", "C110000000000000, -1.0",
            // exponent 0x42 and above: 16^1, 16^2, 16^6 - the exponent shift has to be scaled
            "4210000000000000, 16.0", "4610000000000000, 1048576.0", "C620000000000000, -2097152.0",
            // exponent below 0x41: 16^-1 and 16^-5
            "4080000000000000, 0.5", "3E10000000000000, 0.000244140625",
            // a full 56-bit mantissa: pi to the precision IBM hex float can carry
            "413243F6A8885A00, 3.1415926535897825"
    })
    void ibmToIeeeMatchesTheReferenceValue(String ibmHex, double expected) throws IOException
    {
        Object result = ObservationIteratorXpt.ibmToIeee(hex(ibmHex));
        assertInstanceOf(Double.class, result);
        assertEquals(expected, (Double) result, Math.abs(expected) * 1e-15 + Double.MIN_NORMAL);
    }


    /**
     * The normalisation shift depends on which of the top three mantissa bits is set, and each
     * choice must produce the same number. These four encode 1.0 through 8.0 at the same exponent,
     * so they walk all four shift branches.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
    {
            "4110000000000000, 1.0", // top mantissa bit at 0x10... : shift 0
            "4120000000000000, 2.0", // 0x20... : shift 1
            "4140000000000000, 4.0", // 0x40... : shift 2
            "4180000000000000, 8.0" // 0x80... : shift 3
    })
    void everyNormalisationShiftProducesTheSameMagnitude(String ibmHex, double expected)
        throws IOException
    {
        assertEquals(expected, (Double) ObservationIteratorXpt.ibmToIeee(hex(ibmHex)));
    }


    /** The sign bit is independent of the exponent and mantissa handling. */
    @Test
    void negatingTheSignBitNegatesTheValue() throws IOException
    {
        double positive = (Double) ObservationIteratorXpt.ibmToIeee(hex("4610000000000000"));
        double negative = (Double) ObservationIteratorXpt.ibmToIeee(hex("C610000000000000"));
        assertEquals(-positive, negative);
    }

}
