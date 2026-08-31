package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EncodingTableMapTest
{

    @ParameterizedTest(name = "byte=0x{0} -> charset={1}")
    @CsvSource(
    {
            "14, UTF-8", "1C, US-ASCII", "1D, ISO-8859-1", "1E, ISO-8859-2", "20, ISO-8859-4",
            "28, ISO-8859-15", "3C, windows-1250", "3E, windows-1252", "8A, Shift_JIS",
            "8C, EUC-KR", "CD, GB18030", "F2, ISO-8859-13"
    })
    void sasCharacterEncodings_lookupKnownBytes(String hexByte, String charsetName)
    {
        Byte key = (byte) Integer.parseInt(hexByte, 16);
        assertEquals(charsetName, EncodingTableMap.SAS_CHARACTER_ENCODINGS.get(key));
    }


    @Test
    void sasCharacterEncodings_returnsNullForUnknownByte()
    {
        // 0x00 is not registered in the table.
        assertNull(EncodingTableMap.SAS_CHARACTER_ENCODINGS.get((byte) 0x00));
        // 0x10 is not registered either (table starts at 0x14).
        assertNull(EncodingTableMap.SAS_CHARACTER_ENCODINGS.get((byte) 0x10));
    }


    @Test
    void sasCharacterEncodings_isImmutable()
    {
        Map<Byte, String> map = EncodingTableMap.SAS_CHARACTER_ENCODINGS;
        assertThrows(UnsupportedOperationException.class, () -> map.put((byte) 0x00, "ignored"));
    }


    @Test
    void sasCharacterEncodings_hasReasonableSize()
    {
        // Loose assertion — guard against accidental shrink of the table.
        assertTrue(EncodingTableMap.SAS_CHARACTER_ENCODINGS.size() > 40,
                "expected > 40 entries, got " + EncodingTableMap.SAS_CHARACTER_ENCODINGS.size());
    }


    @Test
    void mostCharsetsResolvableOnRunningJre()
    {
        // The bulk of the table must round-trip through Charset.forName on a default
        // JRE — but a handful of x- entries (e.g. x-ISO2022-CN-CNS) depend on the
        // sun.nio.cs.ext module being present, which is not guaranteed everywhere.
        // Assert >= 80 % resolvable rather than 100 %.
        int total = EncodingTableMap.SAS_CHARACTER_ENCODINGS.size();
        int resolved = 0;
        for (Map.Entry<Byte, String> e : EncodingTableMap.SAS_CHARACTER_ENCODINGS.entrySet())
        {
            if (Charset.isSupported(e.getValue()))
            {
                Charset cs = Charset.forName(e.getValue());
                assertNotNull(cs);
                resolved++;
            }
        }
        assertTrue(resolved * 100 / total >= 80,
                "expected >= 80 % of " + total + " charsets resolvable, got " + resolved);
    }


    @Test
    void reverseLookup_resolvesCanonicalNames()
    {
        // The reverse map keys are lowercased. Verify a representative sample.
        assertEquals(Byte.valueOf((byte) 0x14),
                EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("utf-8"));
        assertEquals(Byte.valueOf((byte) 0x1D),
                EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("iso-8859-1"));
        assertEquals(Byte.valueOf((byte) 0x8A),
                EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("shift_jis"));
    }


    @Test
    void reverseLookup_pickslowestCodeForDuplicateValue()
    {
        // The reverse map is built by iterating entries sorted by Byte's natural
        // ordering (signed). For unsigned codes above 0x7F that means negative-valued
        // Byte instances sort first, so 0xAD (-83) beats 0x4E (78) for "IBM037".
        assertEquals(Byte.valueOf((byte) 0xAD),
                EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("ibm037"));
        // 0xB7 (-73) and 0xC0 (-64) both map to "IBM01140"; -73 < -64 → 0xB7 wins.
        assertEquals(Byte.valueOf((byte) 0xB7),
                EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("ibm01140"));
        // 0x59 (89) and 0x67 (103) both map to "IBM-Thai"; both positive → 0x59 wins.
        assertEquals(Byte.valueOf((byte) 0x59),
                EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("ibm-thai"));
    }


    @Test
    void reverseLookup_returnsNullForUnknownName()
    {
        assertNull(EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("unknown-charset"));
        // Case matters in the reverse map (keys are pre-lowercased).
        assertNull(EncodingTableMap.SAS_ENCODING_FOR_CHARSET.get("UTF-8"));
    }


    @Test
    void reverseLookup_isImmutable()
    {
        Map<String, Byte> reverse = EncodingTableMap.SAS_ENCODING_FOR_CHARSET;
        assertThrows(UnsupportedOperationException.class, () -> reverse.put("ignored", (byte) 0));
    }


    @Test
    void reverseLookupSize_doesNotExceedForwardSize()
    {
        // Because duplicates collapse, the reverse map must be no larger than the
        // forward map.
        assertTrue(EncodingTableMap.SAS_ENCODING_FOR_CHARSET
                .size() <= EncodingTableMap.SAS_CHARACTER_ENCODINGS.size());
        assertFalse(EncodingTableMap.SAS_ENCODING_FOR_CHARSET.isEmpty());
    }


    @Test
    void utilityClass_constructorIsPrivateAndThrows() throws Exception
    {
        // Class is now a true utility class with a private throwing constructor (java:S1118).
        // We exercise it via reflection to keep coverage honest and to verify the contract.
        java.lang.reflect.Constructor<EncodingTableMap> ctor = EncodingTableMap.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        Throwable cause = assertThrows(java.lang.reflect.InvocationTargetException.class,
                ctor::newInstance).getCause();
        assertInstanceOf(UnsupportedOperationException.class, cause);
    }
}
