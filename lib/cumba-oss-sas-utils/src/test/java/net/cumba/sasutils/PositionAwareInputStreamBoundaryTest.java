package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The boundary cases of {@link PositionAwareInputStream}'s position accounting, and its delegation
 * to the wrapped stream.
 * <p>
 * ⚠ <strong>Why the exact comparison operators matter.</strong> Every offset in a SAS7BDAT is
 * absolute, and the parser reaches them with {@code seek()}, which is implemented as "skip the
 * difference from where we think we are". If the position ever drifts by one byte, nothing throws:
 * subheaders are read from the wrong place and the values that come out are plausible and wrong.
 * The two operators this class gets to choose are {@code read()}'s {@code >= 0} (a byte of value
 * zero is a real byte and must advance) and the array reads' {@code > 0} (a zero-length read is not
 * a byte and must not).
 */
class PositionAwareInputStreamBoundaryTest
{

    /** Counts the calls that {@link PositionAwareInputStream} is supposed to forward. */
    private static final class RecordingStream extends InputStream
    {

        private final ByteArrayInputStream delegate;

        int markCalls;

        int resetCalls;

        int closeCalls;

        boolean markSupported = true;

        RecordingStream(byte[] data)
        {
            delegate = new ByteArrayInputStream(data);
        }


        @Override
        public int read()
        {
            return delegate.read();
        }


        @Override
        public int read(byte[] b, int off, int len)
        {
            return delegate.read(b, off, len);
        }


        @Override
        public long skip(long n)
        {
            return delegate.skip(n);
        }


        @Override
        public boolean markSupported()
        {
            return markSupported;
        }


        @Override
        public synchronized void mark(int readlimit)
        {
            markCalls++;
            delegate.mark(readlimit);
        }


        @Override
        public synchronized void reset()
        {
            resetCalls++;
            delegate.reset();
        }


        @Override
        public void close()
        {
            closeCalls++;
        }
    }

    /**
     * A stream byte of value zero is a byte. {@code read()} returns 0 for it, so the guard has to
     * be {@code >= 0}; with {@code > 0} the position silently stops counting NUL bytes — and a
     * SAS7BDAT is full of them.
     */
    @Test
    void readOfANulByteAdvancesThePosition() throws IOException
    {
        try (PositionAwareInputStream in = new PositionAwareInputStream(
                new ByteArrayInputStream(new byte[]
                {
                        0x00, 0x00, 0x41
                })))
        {
            assertEquals(0, in.read());
            assertEquals(1, in.getPosition());
            assertEquals(0, in.read());
            assertEquals(2, in.getPosition());
            assertEquals(0x41, in.read());
            assertEquals(3, in.getPosition());
        }
    }


    /** A zero-length array read consumes nothing, so it must not move the position. */
    @Test
    void zeroLengthArrayReadsDoNotMoveThePosition() throws IOException
    {
        try (PositionAwareInputStream in = new PositionAwareInputStream(
                new ByteArrayInputStream(new byte[]
                {
                        0x41, 0x42
                })))
        {
            assertEquals(0, in.read(new byte[0]));
            assertEquals(0, in.getPosition());
            assertEquals(0, in.read(new byte[4], 1, 0));
            assertEquals(0, in.getPosition());

            byte[] buffer = new byte[2];
            assertEquals(2, in.read(buffer), "read must report the byte count it moved by");
            assertArrayEquals(new byte[]
            {
                    0x41, 0x42
            }, buffer);
            assertEquals(2, in.getPosition());
        }
    }


    /** Likewise a zero-byte skip. */
    @Test
    void zeroLengthSkipDoesNotMoveThePosition() throws IOException
    {
        try (PositionAwareInputStream in = new PositionAwareInputStream(
                new ByteArrayInputStream(new byte[4])))
        {
            assertEquals(0, in.skip(0));
            assertEquals(0, in.getPosition());
            assertEquals(3, in.skip(3));
            assertEquals(3, in.getPosition());
        }
    }


    @Test
    void markSupportedIsWhateverTheWrappedStreamSays() throws IOException
    {
        RecordingStream recording = new RecordingStream(new byte[4]);
        try (PositionAwareInputStream in = new PositionAwareInputStream(recording))
        {
            assertTrue(in.markSupported());
            recording.markSupported = false;
            assertFalse(in.markSupported());
        }
    }


    /**
     * {@code mark} and {@code reset} must reach the wrapped stream, not only the position counter:
     * resetting the counter without rewinding the bytes would leave the two permanently out of
     * step.
     */
    @Test
    void markAndResetAreForwardedToTheWrappedStream() throws IOException
    {
        RecordingStream recording = new RecordingStream(new byte[]
        {
                0x41, 0x42, 0x43
        });
        try (PositionAwareInputStream in = new PositionAwareInputStream(recording))
        {
            assertEquals(0x41, in.read());
            in.mark(16);
            assertEquals(1, recording.markCalls);
            assertEquals(0x42, in.read());
            assertEquals(2, in.getPosition());

            in.reset();
            assertEquals(1, recording.resetCalls);
            assertEquals(1, in.getPosition());
            assertEquals(0x42, in.read(), "reset must rewind the bytes, not just the counter");
        }
    }


    @Test
    void closeClosesTheWrappedStream() throws IOException
    {
        try (RecordingStream recording = new RecordingStream(new byte[4]))
        {
            new PositionAwareInputStream(recording).close();
            // Read before the try block closes the stream a second time.
            assertEquals(1, recording.closeCalls);
        }
    }


    /**
     * Seeking backwards is supported only over a {@link RandomAccessFileInputStream}, and it must
     * move the underlying file pointer as well as the counter — otherwise the next read returns
     * bytes from the old position under the new offset.
     */
    @Test
    void seekBackwardsOverARandomAccessFileRewindsTheFile(@TempDir Path dir) throws IOException
    {
        Path file = dir.resolve("seek.bin");
        Files.write(file, new byte[]
        {
                0x41, 0x42, 0x43, 0x44, 0x45
        });

        try (PositionAwareInputStream in = new PositionAwareInputStream(
                new RandomAccessFileInputStream(new RandomAccessFile(file.toFile(), "r"), true)))
        {
            in.seek(4);
            assertEquals(4, in.getPosition());
            assertEquals(0x45, in.read());

            in.seek(1);
            assertEquals(1, in.getPosition());
            assertEquals(0x42, in.read(),
                    "a backward seek must rewind the file, not just the " + "position counter");
            assertEquals(2, in.getPosition());
        }
    }

}
