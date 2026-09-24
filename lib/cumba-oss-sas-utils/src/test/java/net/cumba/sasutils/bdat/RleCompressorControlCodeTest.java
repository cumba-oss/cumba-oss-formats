package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Covers the RLE control codes the existing {@link RleCompressorTest} does not reach: the three
 * long fills (0x5n / 0x6n / 0x7n), the long repeat (0x4n) and the long literal (0x0n-0x3n).
 * <p>
 * ⚠ <strong>Why a decompressor needs exact assertions.</strong> The result array starts life full
 * of zero bytes, so an opcode that writes nothing — or that advances the write cursor by the wrong
 * amount — produces a row that still has the right <em>length</em> and reads as valid data. SAS
 * pads character fields with 0x20, so a fabricated 0x00 tail is not even a plausible blank; it
 * becomes a silently wrong cell value. Every test here therefore asserts the whole row, and every
 * fill is followed by a second opcode so a wrong cursor advance shows up as a misplaced marker.
 * <p>
 * The opcode semantics are those of EPAM parso's {@code CharDecompressor}, which
 * {@code cumba-parso} in this same repository carries as a second, byte-identical copy.
 */
class RleCompressorControlCodeTest
{

    private final RleCompressor compressor = new RleCompressor();

    /** 0xD1 fills three 0x40 bytes; used as an end marker after a fill under test. */
    private static final byte[] MARKER_D1 =
    {
            (byte) 0xD1
    };

    private static final int MARKER_D1_LENGTH = 3;

    private static byte[] concat(byte[] head, byte[] tail)
    {
        byte[] out = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }


    /**
     * 0x5n: fill with 0x40 (the SAS blank) for <code>n * 256 + operand + 17</code> bytes. All three
     * terms matter — 0x51 0x02 is 1*256 + 2 + 17 = 275, and no other reading of those bytes gives
     * that count.
     */
    @Test
    void controlByte0x50_fillsBlanksWithA256ScaledCount() throws IOException
    {
        byte[] row = concat(new byte[]
        {
                0x51, 0x02
        }, MARKER_D1);
        int fill = 1 * 256 + 2 + 17;

        byte[] result = compressor.decompressRow(fill + MARKER_D1_LENGTH, row);

        byte[] expected = new byte[fill + MARKER_D1_LENGTH];
        Arrays.fill(expected, (byte) 0x40); // the fill and the marker both write 0x40
        assertArrayEquals(expected, result);
        assertEquals(fill + MARKER_D1_LENGTH, result.length);
    }


    /** 0x6n: the same count arithmetic, filling with 0x20 (ASCII space). */
    @Test
    void controlByte0x60_fillsSpacesWithA256ScaledCount() throws IOException
    {
        byte[] row = concat(new byte[]
        {
                0x62, 0x03
        }, MARKER_D1);
        int fill = 2 * 256 + 3 + 17;

        byte[] result = compressor.decompressRow(fill + MARKER_D1_LENGTH, row);

        byte[] expected = new byte[fill + MARKER_D1_LENGTH];
        Arrays.fill(expected, 0, fill, (byte) 0x20);
        Arrays.fill(expected, fill, expected.length, (byte) 0x40);
        assertArrayEquals(expected, result);
    }


    /**
     * 0x7n: fills with 0x00. The fill bytes are indistinguishable from the array's initial state,
     * so the only observable effect is where the <em>next</em> opcode writes — which is exactly the
     * failure mode this opcode has: a mis-sized 0x7n fill silently shifts the whole rest of the
     * row.
     */
    @Test
    void controlByte0x70_fillsNulsAndLeavesTheCursorAfterThem() throws IOException
    {
        byte[] row = concat(new byte[]
        {
                0x71, 0x01
        }, MARKER_D1);
        int fill = 1 * 256 + 1 + 17;

        byte[] result = compressor.decompressRow(fill + MARKER_D1_LENGTH, row);

        byte[] expected = new byte[fill + MARKER_D1_LENGTH];
        Arrays.fill(expected, fill, expected.length, (byte) 0x40);
        assertArrayEquals(expected, result);
    }


    /**
     * 0x4n: repeat the third byte <code>n * 256 + operand + 18</code> times.
     * <p>
     * ⚠ <strong>This is the regression test for a confirmed data-corruption defect.</strong> The
     * nibble used to be scaled by 16 rather than 256, so a run of 274 or more identical bytes - an
     * ordinary wide blank-padded character column - decoded 240 bytes short per nibble step, and
     * every value after it in the row was then written at the wrong offset. Nothing threw. The
     * nibble-zero case decodes identically under both readings, which is why every existing fixture
     * passed. Confirmed against ReadStat (both its decompressor and its compressor) and pandas; see
     * the comment on the 0x40 arm.
     */
    @Test
    void controlByte0x40_repeatsOneByteWithA256ScaledCount() throws IOException
    {
        byte[] row = concat(new byte[]
        {
                0x41, 0x02, 0x5A
        }, MARKER_D1);
        int repeats = 1 * 256 + 2 + 18;

        byte[] result = compressor.decompressRow(repeats + MARKER_D1_LENGTH, row);

        byte[] expected = new byte[repeats + MARKER_D1_LENGTH];
        Arrays.fill(expected, 0, repeats, (byte) 0x5A);
        Arrays.fill(expected, repeats, expected.length, (byte) 0x40);
        assertArrayEquals(expected, result);
    }


    /**
     * The two encodings that the old <code>* 16</code> reading conflated must now decode to
     * different lengths. This is the shortest possible statement of the defect: an ambiguous
     * decoder cannot invert any encoder.
     */
    @Test
    void controlByte0x40_nibbleAndOperandAreNotInterchangeable() throws IOException
    {
        // nibble 1, operand 0 -> 256 + 0 + 18
        byte[] withNibble =
        {
                0x41, 0x00, 0x5A
        };
        // nibble 0, operand 16 -> 0 + 16 + 18; both used to give 34
        byte[] withOperand =
        {
                0x40, 0x10, 0x5A
        };

        assertEquals(274, countOf(compressor.decompressRow(274, withNibble), (byte) 0x5A));
        assertEquals(34, countOf(compressor.decompressRow(34, withOperand), (byte) 0x5A));
    }


    private static int countOf(byte[] data, byte value)
    {
        int n = 0;
        for (byte b : data)
        {
            if (b == value)
            {
                n++;
            }
        }
        return n;
    }


    /**
     * 0x0n-0x3n: a long literal of <code>operand + 64 + controlByte * 256</code> bytes copied
     * verbatim from the third byte on. The control byte is used <em>whole</em> here (not just its
     * low nibble), so 0x01 contributes 256 to the length.
     */
    @Test
    void controlBytes0x00To0x30_copyALongLiteralRun() throws IOException
    {
        int literal = 5 + 64 + 1 * 256;
        // control 0x01, operand 0x05, then the literal, then a marker the cursor must reach
        byte[] row = new byte[2 + literal + MARKER_D1.length];
        row[0] = 0x01;
        row[1] = 0x05;
        for (int i = 0; i < literal; i++)
        {
            row[2 + i] = (byte) (i % 251); // a pattern no fill or repeat opcode could produce
        }
        row[2 + literal] = MARKER_D1[0];

        byte[] result = compressor.decompressRow(literal + MARKER_D1_LENGTH, row);

        byte[] expected = new byte[literal + MARKER_D1_LENGTH];
        System.arraycopy(row, 2, expected, 0, literal);
        Arrays.fill(expected, literal, expected.length, (byte) 0x40);
        assertArrayEquals(expected, result);
    }


    /**
     * The short literal (0x8n-0xBn) advances the cursor by the number of bytes it copied. Chaining
     * two of them proves the advance, which a single-opcode test cannot.
     */
    @Test
    void shortLiteralsChainWithoutLosingTheCursor() throws IOException
    {
        // 0x80 | 0x02 copies 3 bytes; 0x90 | 0x00 copies 1 + 0 + 16 = 17 bytes
        byte[] row = new byte[1 + 3 + 1 + 17];
        row[0] = (byte) 0x82;
        row[1] = 0x41;
        row[2] = 0x42;
        row[3] = 0x43;
        row[4] = (byte) 0x90;
        for (int i = 0; i < 17; i++)
        {
            row[5 + i] = (byte) (0x60 + i);
        }

        byte[] result = compressor.decompressRow(20, row);

        byte[] expected = new byte[20];
        expected[0] = 0x41;
        expected[1] = 0x42;
        expected[2] = 0x43;
        for (int i = 0; i < 17; i++)
        {
            expected[3 + i] = (byte) (0x60 + i);
        }
        assertArrayEquals(expected, result);
    }


    /**
     * 0xC0 repeats the following byte <code>n + 3</code> times and consumes it; 0xD0/0xE0/0xF0 take
     * no operand at all. Mixing them pins both cursor advances against each other.
     */
    @Test
    void shortRepeatAndShortFillsAdvanceTheCursorDifferently() throws IOException
    {
        byte[] row =
        {
                (byte) 0xC0, 0x7A, // repeat 0x7A three times, consuming the operand
                (byte) 0xE1 // fill three 0x20 bytes, no operand
        };

        byte[] result = compressor.decompressRow(6, row);

        assertArrayEquals(new byte[]
        {
                0x7A, 0x7A, 0x7A, 0x20, 0x20, 0x20
        }, result);
    }

}
