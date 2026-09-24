package net.cumba.sasutils.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ObservationIteratorXptTest
{

    @Test
    void ibmToIeee_zero() throws IOException
    {
        byte[] bytes =
        {
                0, 0, 0, 0, 0, 0, 0, 0
        };
        Object result = ObservationIteratorXpt.ibmToIeee(bytes);
        assertEquals(0d, result);
    }


    @Test
    void ibmToIeee_negativeZero() throws IOException
    {
        // 0x80 with zero mantissa: sign bit set, exponent 0x00, mantissa 0
        // The code checks bytes[0] == 0x80 only when sign=0x8000000000000000 and mantissa==0
        // Actually bytes[0] == (byte) 0x80 is checked specifically
        byte[] bytes =
        {
                (byte) 0x80, 0, 0, 0, 0, 0, 0, 0
        };
        Object result = ObservationIteratorXpt.ibmToIeee(bytes);
        // The ibmToIeee code returns -0d for 0x80 followed by zeros
        assertEquals(-0d, result);
    }


    @Test
    void ibmToIeee_missingDot() throws IOException
    {
        // '.' = 0x2E is the SAS missing value indicator
        byte[] bytes =
        {
                '.', 0, 0, 0, 0, 0, 0, 0
        };
        Object result = ObservationIteratorXpt.ibmToIeee(bytes);
        assertNull(result);
    }


    @Test
    void ibmToIeee_missingA() throws IOException
    {
        // 'A' = tagged missing .A
        byte[] bytes =
        {
                'A', 0, 0, 0, 0, 0, 0, 0
        };
        Object result = ObservationIteratorXpt.ibmToIeee(bytes);
        assertNull(result);
    }


    @Test
    void ibmToIeee_positiveValue() throws IOException
    {
        // IBM float for 1.0: exponent=65 (0x41), mantissa=0x10000000000000
        // 0x41 10 00 00 00 00 00 00
        byte[] bytes =
        {
                0x41, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        Object result = ObservationIteratorXpt.ibmToIeee(bytes);
        assertInstanceOf(Double.class, result);
        assertEquals(1.0, (Double) result, 1e-10);
    }


    @Test
    void ibmToIeee_shortInput() throws IOException
    {
        // Test that shorter-than-8-byte input is padded
        byte[] bytes =
        {
                0x41, 0x10, 0x00, 0x00
        };
        Object result = ObservationIteratorXpt.ibmToIeee(bytes);
        assertInstanceOf(Double.class, result);
        // Padded to 8 bytes with zeros, mantissa is 0x1000000000 -> still 1.0
        assertEquals(1.0, (Double) result, 1e-10);
    }


    @Test
    void ibmToIeee_zeroMantissaUnknownLeadByte_throwsCheckedFormatError()
    {
        // A zero mantissa with lead byte 0x01 is a byte sequence no SAS-written XPT file
        // contains. The codec must report it as a checked format failure the caller can attribute
        // to a file and a row, not an unchecked IllegalArgumentException (F-sas-11).
        byte[] bytes =
        {
                0x01, 0, 0, 0, 0, 0, 0, 0
        };
        IOException e = assertThrows(IOException.class,
                () -> ObservationIteratorXpt.ibmToIeee(bytes));
        assertTrue(e.getMessage().contains("0x1"), e.getMessage());
    }
}
