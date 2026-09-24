package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * The long-pattern (cmd 2) back-reference of the RDC decompressor, with a <em>non-zero</em> offset
 * extension byte.
 * <p>
 * ⚠ {@link RdcCompressorTest#decompressRow_longPattern_command2()} sets the extension byte to zero,
 * which makes <code>ofs += ext &lt;&lt; 4</code> a no-op: shifting the wrong way, shifting by the
 * wrong amount or subtracting instead of adding all give the same answer for zero. Every
 * back-reference further than 18 bytes behind the cursor needs that byte, so with only the zero
 * case the reader could mis-locate every long repeat in a compressed dataset — and a back-reference
 * to the wrong place produces bytes that are real data from elsewhere in the row, which is the
 * least detectable kind of wrong value there is.
 */
class RdcLongPatternOffsetTest
{

    private final RdcCompressor compressor = new RdcCompressor();

    /**
     * Forty literals, then a long-pattern back-reference 36 bytes behind the cursor copying 20
     * bytes, then one more literal so a wrong output-cursor advance is visible too.
     * <p>
     * Layout: three 16-bit control words, MSB first, one bit per segment — 0 = literal, 1 =
     * command.
     */
    @Test
    void longPatternUsesTheExtensionByteAsTheHighNibblesOfTheOffset()
    {
        byte[] row = new byte[50];
        // control word 1: sixteen literals
        row[0] = 0x00;
        row[1] = 0x00;
        for (int i = 0; i < 16; i++)
        {
            row[2 + i] = (byte) i;
        }
        // control word 2: sixteen more literals
        row[18] = 0x00;
        row[19] = 0x00;
        for (int i = 0; i < 16; i++)
        {
            row[20 + i] = (byte) (0x10 + i);
        }
        // control word 3: eight literals, then a command, then one literal.
        // The command is the ninth segment of the word, so its bit is 0x8000 >> 8 = 0x0080.
        row[36] = 0x00;
        row[37] = (byte) 0x80;
        for (int i = 0; i < 8; i++)
        {
            row[38 + i] = (byte) (0x20 + i);
        }
        row[46] = 0x21; // cmd nibble 2 = long pattern, cnt nibble 1 -> offset base 1 + 3 = 4
        row[47] = 0x02; // extension: ofs += 2 << 4 = 32, so the back-reference is 36 bytes back
        row[48] = 0x04; // length byte: cnt = 4 + 16 = 20 bytes
        row[49] = 0x7F; // a trailing literal, written wherever the output cursor ended up

        byte[] result = compressor.decompressRow(61, row);

        byte[] expected = new byte[61];
        for (int i = 0; i < 40; i++)
        {
            expected[i] = (byte) i; // 0x00 .. 0x27
        }
        System.arraycopy(expected, 40 - 36, expected, 40, 20); // 0x04 .. 0x17
        expected[60] = 0x7F;

        assertArrayEquals(expected, result);
    }

}
