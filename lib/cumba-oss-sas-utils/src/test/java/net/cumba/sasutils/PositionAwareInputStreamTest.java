package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class PositionAwareInputStreamTest
{

    @Test
    void positionTracking_singleRead() throws IOException
    {
        byte[] data =
        {
                1, 2, 3
        };
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        assertEquals(0, pais.getPosition());
        pais.read();
        assertEquals(1, pais.getPosition());
        pais.read();
        assertEquals(2, pais.getPosition());
        pais.close();
    }


    @Test
    void positionTracking_arrayRead() throws IOException
    {
        byte[] data = new byte[50];
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        byte[] buf = new byte[20];
        pais.read(buf);
        assertEquals(20, pais.getPosition());
        pais.close();
    }


    @Test
    void positionTracking_arrayReadWithOffset() throws IOException
    {
        byte[] data = new byte[50];
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        byte[] buf = new byte[20];
        pais.read(buf, 5, 10);
        assertEquals(10, pais.getPosition());
        pais.close();
    }


    @Test
    void skip_updatesPosition() throws IOException
    {
        byte[] data = new byte[50];
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        long skipped = pais.skip(15);
        assertEquals(skipped, pais.getPosition());
        pais.close();
    }


    @Test
    void seek_forward() throws IOException
    {
        byte[] data = new byte[50];
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        pais.seek(30);
        assertEquals(30, pais.getPosition());
        pais.close();
    }


    @Test
    void seek_backward_throwsForNonRAF() throws IOException
    {
        byte[] data = new byte[50];
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        pais.seek(30);
        assertThrows(IOException.class, () -> pais.seek(10));
        pais.close();
    }


    @Test
    void markAndReset() throws IOException
    {
        byte[] data = new byte[50];
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        PositionAwareInputStream pais = new PositionAwareInputStream(bais);

        pais.read(new byte[10]);
        pais.mark(100);
        assertEquals(10, pais.getPosition());

        pais.read(new byte[20]);
        assertEquals(30, pais.getPosition());

        pais.reset();
        assertEquals(10, pais.getPosition());
        pais.close();
    }


    @Test
    void read_returnsMinusOneAtEnd() throws IOException
    {
        byte[] data =
        {
                1
        };
        PositionAwareInputStream pais = new PositionAwareInputStream(
                new ByteArrayInputStream(data));
        pais.read();
        assertEquals(-1, pais.read());
        // Position should not increase on -1 read
        assertEquals(1, pais.getPosition());
        pais.close();
    }
}
