package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link RdcCompressor} via synthesised compressed payloads. The decoder is a pure-byte
 * translation of the format documented in <code>sas7bdat.pdf</code>; tests build each
 * control-bit/command combination by hand and verify the decoded output.
 *
 * <p>
 * RDC compression splits the stream into 16 1-bit flags + payload. Each pair of leading bytes is a
 * big-endian control word: bit set = command, bit clear = literal copy of the next source byte.
 */
class RdcCompressorTest
{

    private final RdcCompressor compressor = new RdcCompressor();

    /**
     * Pack two control bytes plus the payload into a single byte[]. The control mask is read
     * MSB-first, so flags[0] becomes the high bit of the first control byte.
     */
    private static byte[] buildRow(int[] flags, int... payload)
    {
        if (flags.length > 16)
        {
            throw new IllegalArgumentException("at most 16 control bits per word");
        }
        int ctrl = 0;
        for (int i = 0; i < flags.length; i++)
        {
            if (flags[i] != 0)
            {
                ctrl |= (1 << (15 - i));
            }
        }
        byte[] row = new byte[2 + payload.length];
        row[0] = (byte) ((ctrl >> 8) & 0xFF);
        row[1] = (byte) (ctrl & 0xFF);
        for (int i = 0; i < payload.length; i++)
        {
            row[2 + i] = (byte) (payload[i] & 0xFF);
        }
        return row;
    }


    @Test
    void decompressRow_allLiteralBytes()
    {
        // 5 literals — all control flags clear.
        byte[] row = buildRow(new int[]
        {
                0, 0, 0, 0, 0
        }, 0x41, 0x42, 0x43, 0x44, 0x45);
        byte[] result = compressor.decompressRow(5, row);
        assertArrayEquals(new byte[]
        {
                0x41, 0x42, 0x43, 0x44, 0x45
        }, result);
    }


    @Test
    void decompressRow_shortRle_command0()
    {
        // cmd=0 short-rle, cnt nibble=2 → 2+3=5 repeats of the next byte.
        // Control word: bit 0 set (command), rest unused for this segment.
        byte[] row = buildRow(new int[]
        {
                1
        }, /* cmd|cnt = 0x02 */ 0x02, /* byte to repeat */ 0x58); // 'X'
        byte[] result = compressor.decompressRow(5, row);
        assertArrayEquals(new byte[]
        {
                0x58, 0x58, 0x58, 0x58, 0x58
        }, result);
    }


    @Test
    void decompressRow_longRle_command1()
    {
        // cmd=1 long-rle: cnt += (next<<4); cnt += 19; then repeat following byte.
        // Use cnt-nibble 0, extension 0 → 0 + (0<<4) + 19 = 19 repeats of 0x5A ('Z').
        byte[] row = buildRow(new int[]
        {
                1
        }, /* cmd|cnt = 0x10 */ 0x10, /* extension */ 0x00, /* byte to repeat */ 0x5A);
        byte[] result = compressor.decompressRow(19, row);
        assertEquals(19, result.length);
        for (byte b : result)
        {
            assertEquals(0x5A, b);
        }
    }


    @Test
    void decompressRow_shortPattern_command3to15()
    {
        // For short-pattern (cmd 3..15): cnt nibble = ofs-base; copy cmd bytes from
        // (out-offset - (cnt+3 + (ext<<4))) into the output.
        // First emit 4 literals to populate the output buffer: 'A','B','C','D'.
        // Then issue a back-reference of 3 bytes with ofs = 1+3 = 4, ext=0 → copy from
        // outOffset-4 ('A','B','C').
        // Flags layout (16 bits): literal,literal,literal,literal,cmd,...
        // For the back-ref segment cmd=3 (short pattern). Control byte = (cmd<<4 | cnt)
        // = 0x31, extension byte = 0x00.
        int[] flags = new int[]
        {
                0, 0, 0, 0, 1
        };
        byte[] row = buildRow(flags, 0x41, 0x42, 0x43, 0x44, /* literals A,B,C,D */
                0x31, /* cmd=3 short-pattern, cnt nibble=1 -> ofs = 1+3 = 4 */
                0x00 /* extension nibble */);
        byte[] result = compressor.decompressRow(7, row);
        assertArrayEquals(new byte[]
        {
                0x41, 0x42, 0x43, 0x44, 0x41, 0x42, 0x43
        }, result);
    }


    @Test
    void decompressRow_longPattern_command2()
    {
        // cmd=2 long-pattern: ofs = cnt+3 + (next<<4); cnt = (following & 0xFF) + 16.
        // The decoder uses System.arraycopy(outRow, outOffset-ofs, outRow, outOffset, cnt) —
        // *not* an LZ77-style overlapping replay, so we must seed the source region with
        // literal bytes before issuing the back-reference.
        //
        // Layout (manually composed, two control words):
        // ctrl1 = 0x0000 → 16 zero-bits = 16 literals
        // 16 literal bytes (0xA0..0xAF)
        // ctrl2 = 0x8000 → bit 0 set = command, rest unused for this segment
        // cmd=2 long-pattern: cnt nibble = 13 → ofs base = 13+3 = 16, ext 0x00 keeps ofs=16,
        // count byte 0x00 → cnt = 0 + 16 = 16.
        byte[] row = new byte[2 + 16 + 2 + 3];
        // ctrl1 = 0x0000 (all literals)
        row[0] = 0x00;
        row[1] = 0x00;
        for (int i = 0; i < 16; i++)
        {
            row[2 + i] = (byte) (0xA0 + i);
        }
        // ctrl2: MSB set = first segment in next word is a command.
        row[18] = (byte) 0x80;
        row[19] = 0x00;
        // cmd byte: cmd nibble 2, cnt nibble 13 → 0x2D
        row[20] = 0x2D;
        // ofs extension nibble
        row[21] = 0x00;
        // count byte
        row[22] = 0x00;

        byte[] result = compressor.decompressRow(16 + 16, row);

        for (int i = 0; i < 16; i++)
        {
            assertEquals((byte) (0xA0 + i), result[i], "literal mismatch at " + i);
        }
        // The back-reference copies result[0..15] → result[16..31].
        for (int i = 0; i < 16; i++)
        {
            assertEquals((byte) (0xA0 + i), result[16 + i],
                    "back-reference mismatch at " + (16 + i));
        }
    }


    @Test
    void decompressRow_emptyInputProducesZeroLengthRow()
    {
        byte[] result = compressor.decompressRow(0, new byte[0]);
        assertEquals(0, result.length);
    }


    @Test
    void decompressRow_singleLiteralAfterControlWord()
    {
        // One literal: just one zero control-bit followed by the byte itself.
        byte[] row = buildRow(new int[]
        {
                0
        }, 0x7F);
        byte[] result = compressor.decompressRow(1, row);
        assertArrayEquals(new byte[]
        {
                0x7F
        }, result);
    }
}
