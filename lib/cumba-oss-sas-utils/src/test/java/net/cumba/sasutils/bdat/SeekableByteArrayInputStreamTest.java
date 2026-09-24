package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class SeekableByteArrayInputStreamTest
{

    @Test
    void read_singleByte() throws IOException
    {
        byte[] data =
        {
                0x01, 0x02, 0x03
        };

        try (SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data))
        {
            assertEquals(1, stream.read());
            assertEquals(2, stream.read());
            assertEquals(3, stream.read());
            assertEquals(-1, stream.read());
        }
    }


    @Test
    void read_byteArray() throws IOException
    {
        byte[] data =
        {
                0x01, 0x02, 0x03, 0x04, 0x05
        };
        try (SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data))
        {
            byte[] buf = new byte[3];
            int read = stream.read(buf, 0, 3);
            assertEquals(3, read);
            assertEquals(1, buf[0]);
            assertEquals(2, buf[1]);
            assertEquals(3, buf[2]);
        }
    }


    @Test
    void seek() throws IOException
    {
        byte[] data =
        {
                0x0A, 0x0B, 0x0C, 0x0D
        };
        try (SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data))
        {
            stream.seek(2);
            assertEquals(2, stream.getPosition());
            assertEquals(0x0C, stream.read());
        }
    }


    @Test
    void available() throws IOException
    {
        byte[] data = new byte[10];
        try (SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data))
        {
            assertEquals(10, stream.available());
            stream.read();
            assertEquals(9, stream.available());
        }
    }


    @Test
    void markAndReset() throws IOException
    {
        byte[] data =
        {
                1, 2, 3, 4
        };
        try (SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data))
        {
            assertTrue(stream.markSupported());
            stream.read();
            stream.mark(10);
            stream.read();
            stream.read();
            stream.reset();
            assertEquals(1, stream.getPosition());
            assertEquals(2, stream.read());
        }
    }


    @Test
    void readPastEnd() throws IOException
    {
        byte[] data =
        {
                1
        };
        try (SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data))
        {
            byte[] buf = new byte[5];
            int read = stream.read(buf, 0, 5);
            assertEquals(1, read);
            assertEquals(-1, stream.read(buf, 0, 5));
        }
    }
}
