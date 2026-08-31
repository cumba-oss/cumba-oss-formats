/*
 * Added by P300 as part of cumba-oss-sas-utils, which is derived from theshoeshiner/sas-utils
 * (https://github.com/theshoeshiner/sas-utils), licensed under the Apache License, Version 2.0.
 *
 * The SAS character-encoding table in this file is copied from EPAM's parso
 * (https://github.com/epam/parso), also licensed under the Apache License, Version 2.0. See this
 * module's README.md for the full attribution notice.
 */
package net.cumba.sasutils;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A map between the java {@link Charset} and the SAS encoding byte.
 */
public class EncodingTableMap
{

    private EncodingTableMap()
    {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * The integer (one or two bytes) at the encoding offset in the SAS file header indicates the
     * character encoding of string data. This map links the values that are known to occur to the
     * associated encoding, and excludes encodings present in SAS but unsupported by
     * {@code java.nio.charset}.
     *
     * <p>
     * The table itself is copied from the <a href="https://github.com/epam/parso">parso</a> library
     * (Apache License 2.0); see this module's README for the attribution notice.
     * </p>
     */
    public static final Map<Byte, String> SAS_CHARACTER_ENCODINGS;

    /**
     * Reverse lookup: Java charset name → SAS encoding byte. Built from
     * {@link #SAS_CHARACTER_ENCODINGS}. When multiple SAS codes map to the same charset name, the
     * lowest code wins (first entry in insertion order).
     */
    public static final Map<String, Byte> SAS_ENCODING_FOR_CHARSET;

    static
    {
        Map<Byte, String> map = new HashMap<>();

        map.put((byte) 0x14, "UTF-8");
        map.put((byte) 0x1C, "US-ASCII");
        map.put((byte) 0x1D, "ISO-8859-1");
        map.put((byte) 0x1E, "ISO-8859-2");
        map.put((byte) 0x1F, "ISO-8859-3");

        map.put((byte) 0x20, "ISO-8859-4");
        map.put((byte) 0x21, "ISO-8859-5");
        map.put((byte) 0x22, "ISO-8859-6");
        map.put((byte) 0x23, "ISO-8859-7");
        map.put((byte) 0x24, "ISO-8859-8");
        map.put((byte) 0x25, "ISO-8859-9");
        map.put((byte) 0x27, "x-iso-8859-11");
        map.put((byte) 0x28, "ISO-8859-15");
        map.put((byte) 0x2B, "IBM437");
        map.put((byte) 0x2C, "IBM850");
        map.put((byte) 0x2D, "IBM852");
        map.put((byte) 0x2E, "IBM00858");
        map.put((byte) 0x2F, "IBM862");

        map.put((byte) 0x33, "IBM866");
        map.put((byte) 0x3A, "IBM857");
        map.put((byte) 0x3C, "windows-1250");
        map.put((byte) 0x3D, "windows-1251");
        map.put((byte) 0x3E, "windows-1252");
        map.put((byte) 0x3F, "windows-1253");

        map.put((byte) 0x40, "windows-1254");
        map.put((byte) 0x41, "windows-1255");
        map.put((byte) 0x42, "windows-1256");
        map.put((byte) 0x43, "windows-1257");
        map.put((byte) 0x44, "windows-1258");
        map.put((byte) 0x45, "x-MacRoman");
        map.put((byte) 0x46, "x-MacArabic");
        map.put((byte) 0x47, "x-MacHebrew");
        map.put((byte) 0x48, "x-MacGreek");
        map.put((byte) 0x49, "x-MacThai");
        map.put((byte) 0x4B, "x-MacTurkish");
        map.put((byte) 0x4C, "x-MacUkraine");
        map.put((byte) 0x4E, "IBM037");

        map.put((byte) 0x57, "IBM424");
        map.put((byte) 0x58, "IBM500");
        map.put((byte) 0x59, "IBM-Thai");
        map.put((byte) 0x5A, "IBM870");
        map.put((byte) 0x5B, "x-IBM875");
        map.put((byte) 0x5F, "x-IBM1025");

        map.put((byte) 0x62, "x-IBM1112");
        map.put((byte) 0x63, "x-IBM1122");
        map.put((byte) 0x66, "IBM424");
        map.put((byte) 0x67, "IBM-Thai");
        map.put((byte) 0x68, "IBM870");
        map.put((byte) 0x69, "x-IBM875");
        map.put((byte) 0x6C, "x-IBM1025");
        map.put((byte) 0x6D, "IBM1026");
        map.put((byte) 0x6E, "IBM1047");
        map.put((byte) 0x6F, "x-IBM1112");

        map.put((byte) 0x70, "x-IBM1122");
        map.put((byte) 0x75, "x-IBM937");
        map.put((byte) 0x76, "x-windows-950");
        map.put((byte) 0x77, "x-EUC-TW");
        map.put((byte) 0x7B, "Big5");
        map.put((byte) 0x7C, "x-IBM935");
        map.put((byte) 0x7D, "GBK");
        map.put((byte) 0x7E, "x-mswin-936");

        map.put((byte) 0x80, "x-IBM1381");
        map.put((byte) 0x81, "x-IBM939");
        map.put((byte) 0x82, "x-IBM930");
        map.put((byte) 0x86, "EUC-JP");
        map.put((byte) 0x88, "x-windows-iso2022jp");
        map.put((byte) 0x89, "x-IBM942");
        map.put((byte) 0x8A, "Shift_JIS");
        map.put((byte) 0x8B, "x-IBM933");
        map.put((byte) 0x8C, "EUC-KR");
        map.put((byte) 0x8D, "x-windows-949");
        map.put((byte) 0x8E, "x-IBM949");

        map.put((byte) 0xA3, "x-MacIceland");
        map.put((byte) 0xA7, "ISO-2022-JP");
        map.put((byte) 0xA8, "ISO-2022-KR");
        map.put((byte) 0xA9, "x-ISO2022-CN-GB");
        map.put((byte) 0xAC, "x-ISO2022-CN-CNS");
        map.put((byte) 0xAD, "IBM037");

        map.put((byte) 0xCD, "GB18030");
        map.put((byte) 0xCF, "x-IBM1097");

        map.put((byte) 0xB7, "IBM01140");
        map.put((byte) 0xB8, "IBM01141");
        map.put((byte) 0xB9, "IBM01142");
        map.put((byte) 0xBA, "IBM01143");
        map.put((byte) 0xBB, "IBM01144");
        map.put((byte) 0xBC, "IBM01145");
        map.put((byte) 0xBD, "IBM01146");
        map.put((byte) 0xBE, "IBM01147");
        map.put((byte) 0xBF, "IBM01148");

        map.put((byte) 0xC0, "IBM01140");
        map.put((byte) 0xC1, "IBM01141");
        map.put((byte) 0xC2, "IBM01142");
        map.put((byte) 0xC3, "IBM01143");
        map.put((byte) 0xC4, "IBM01144");
        map.put((byte) 0xC5, "IBM01145");
        map.put((byte) 0xC6, "IBM01146");
        map.put((byte) 0xC7, "IBM01147");
        map.put((byte) 0xC8, "IBM01148");

        map.put((byte) 0xD0, "x-IBM1097");
        map.put((byte) 0xD3, "IBM01149");
        map.put((byte) 0xD4, "IBM01149");

        map.put((byte) 0xEA, "x-IBM930");
        map.put((byte) 0xEB, "x-IBM933");
        map.put((byte) 0xEC, "x-IBM935");
        map.put((byte) 0xED, "x-IBM937");
        map.put((byte) 0xEE, "x-IBM939");

        map.put((byte) 0xF2, "ISO-8859-13");
        map.put((byte) 0xF5, "x-MacCroatian");
        map.put((byte) 0xF6, "x-MacCyrillic");
        map.put((byte) 0xF7, "x-MacRomania");
        map.put((byte) 0xF8, "JIS_X0201");

        SAS_CHARACTER_ENCODINGS = Collections.unmodifiableMap(map);

        // Build reverse lookup: charset name → SAS encoding byte.
        // Lowest SAS code wins when multiple codes map to the same charset name.
        Map<String, Byte> reverse = new HashMap<>();
        map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(
                e -> reverse.putIfAbsent(e.getValue().toLowerCase(Locale.ROOT), e.getKey()));
        SAS_ENCODING_FOR_CHARSET = Collections.unmodifiableMap(reverse);
    }

}
