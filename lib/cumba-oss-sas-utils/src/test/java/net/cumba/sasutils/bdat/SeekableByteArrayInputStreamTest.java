package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class SeekableByteArrayInputStreamTest
{

    @Test
    void read_singleByte()
    {
        byte[] data =
        {
                0x01, 0x02, 0x03
        };

        SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data);
        assertEquals(1, stream.read());
        assertEquals(2, stream.read());
        assertEquals(3, stream.read());
        assertEquals(-1, stream.read());
    }


    @Test
    void read_byteArray()
    {
        byte[] data =
        {
                0x01, 0x02, 0x03, 0x04, 0x05
        };
        SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data);
        byte[] buf = new byte[3];
        int read = stream.read(buf, 0, 3);
        assertEquals(3, read);
        assertEquals(1, buf[0]);
        assertEquals(2, buf[1]);
        assertEquals(3, buf[2]);
    }


    @Test
    void seek()
    {
        byte[] data =
        {
                0x0A, 0x0B, 0x0C, 0x0D
        };
        SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data);
        stream.seek(2);
        assertEquals(2, stream.getPosition());
        assertEquals(0x0C, stream.read());
    }


    @Test
    void available()
    {
        byte[] data = new byte[10];
        SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data);
        assertEquals(10, stream.available());
        stream.read();
        assertEquals(9, stream.available());
    }


    @Test
    void markAndReset()
    {
        byte[] data =
        {
                1, 2, 3, 4
        };
        SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data);
        assertTrue(stream.markSupported());
        stream.read();
        stream.mark(10);
        stream.read();
        stream.read();
        stream.reset();
        assertEquals(1, stream.getPosition());
        assertEquals(2, stream.read());
    }


    @Test
    void readPastEnd()
    {
        byte[] data =
        {
                1
        };
        SeekableByteArrayInputStream stream = new SeekableByteArrayInputStream(data);
        byte[] buf = new byte[5];
        int read = stream.read(buf, 0, 5);
        assertEquals(1, read);
        assertEquals(-1, stream.read(buf, 0, 5));
    }
}
