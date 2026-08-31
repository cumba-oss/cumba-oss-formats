package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RleCompressorTest
{

    private final RleCompressor compressor = new RleCompressor();

    @Test
    void decompressRow_controlByte0xC0_repeatByte() throws IOException
    {
        // 0xC0 | 0x02 = repeat byte 3+2=5 times
        // next byte = 0x41 ('A')
        byte[] row = new byte[]
        {
                (byte) 0xC2, 0x41
        };
        byte[] result = compressor.decompressRow(5, row);
        assertEquals(5, result.length);
        for (byte b : result)
        {
            assertEquals(0x41, b);
        }
    }


    @ParameterizedTest(name = "controlByte=0x{0}, fillByte=0x{1}")
    @CsvSource(
    {
            // 0xD0 | 0x03 = fill with 0x40 for 3+2=5 bytes
            "D3, 40",
            // 0xE0 | 0x03 = fill with 0x20 for 3+2=5 bytes
            "E3, 20",
            // 0xF0 | 0x03 = fill with 0x00 for 3+2=5 bytes
            "F3, 00"
    })
    void decompressRow_controlByteFill(String controlByteHex, String fillByteHex) throws IOException
    {
        byte controlByte = (byte) Integer.parseInt(controlByteHex, 16);
        byte fillByte = (byte) Integer.parseInt(fillByteHex, 16);
        byte[] row = new byte[]
        {
                controlByte
        };
        byte[] result = compressor.decompressRow(5, row);
        assertEquals(5, result.length);
        for (byte b : result)
        {
            assertEquals(fillByte, b);
        }
    }


    @Test
    void decompressRow_controlByte0x80_copyBytes() throws IOException
    {
        // 0x80 | 0x02 = copy endOfFirstByte + 1 + (0x80 - 0x80) = 2+1+0 = 3 bytes
        byte[] row = new byte[]
        {
                (byte) 0x82, 0x01, 0x02, 0x03
        };
        byte[] result = compressor.decompressRow(3, row);
        assertEquals(3, result.length);
        assertEquals(0x01, result[0]);
        assertEquals(0x02, result[1]);
        assertEquals(0x03, result[2]);
    }


    @Test
    void decompressRow_emptyInput() throws IOException
    {
        byte[] row = new byte[0];
        byte[] result = compressor.decompressRow(0, row);
        assertEquals(0, result.length);
    }


    @Test
    void decompressRow_truncatedControlByteAtRowEnd_throws()
    {
        // A 0x00-family control byte (high nibble 0x1) as the last byte has no
        // follow-on length byte. Previously this silently left the result zero-padded.
        byte[] row = new byte[]
        {
                0x10
        };
        assertThrows(IOException.class, () -> compressor.decompressRow(5, row));
    }
}
