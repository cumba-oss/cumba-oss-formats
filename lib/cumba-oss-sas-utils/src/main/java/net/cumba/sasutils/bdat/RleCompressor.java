/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.bdat;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RleCompressor implements Compressor
{

    private static final Logger LOGGER = LoggerFactory.getLogger(RleCompressor.class);

    @Override
    @SuppressWarnings("PMD.UselessParentheses")
    public byte[] decompressRow(int resultLength, byte[] row) throws IOException
    {
        int length = row.length;
        byte[] resultByteArray = new byte[resultLength];
        int currentResultArrayIndex = 0;
        int currentByteIndex = 0;
        while (currentByteIndex < length)
        {
            int controlByte = row[currentByteIndex] & 0xF0;
            int endOfFirstByte = row[currentByteIndex] & 0x0F;
            int countOfBytesToCopy;
            switch (controlByte)
            {
            case 0x30, 0x20, 0x10, 0x00 ->
            {
                if (currentByteIndex == length - 1)
                {
                    throw new IOException("Truncated RLE control byte at position "
                            + currentByteIndex + " of " + length);
                }
                countOfBytesToCopy = (row[currentByteIndex + 1] & 0xFF) + 64
                        + row[currentByteIndex] * 256;
                System.arraycopy(row, currentByteIndex + 2, resultByteArray,
                        currentResultArrayIndex, countOfBytesToCopy);
                currentByteIndex += countOfBytesToCopy + 1;
                currentResultArrayIndex += countOfBytesToCopy;
            }
            case 0x40 ->
            {
                // ⚠ The nibble scales by 256, not 16 - it is the high byte of a 16-bit run
                // length whose low byte is the operand. This read `* 16` until 2026-09-11,
                // which made the encoding ambiguous (nibble 1 / operand 0 and nibble 0 /
                // operand 16 both decoded to 34) and shortened every run of 274 or more
                // identical bytes by 240 per nibble step - a wide blank-padded character
                // column - after which the write cursor was wrong for the whole rest of the
                // row. Confirmed against ReadStat's decompressor AND its compressor
                // (readstat_sas_rle.c, SAS_RLE_COMMAND_INSERT_BYTE18:
                // `(*input++) + 18 + length * 256`, emitted as `(insert_run - 18) / 256`)
                // and against pandas (_libs/sas.pyx, control byte 0x40). The 0x5n/0x6n/0x7n
                // arms below - the same command with an implied fill byte - already scale
                // by 256, which is the tell.
                int copyCounter = endOfFirstByte * 256 + (row[currentByteIndex + 1] & 0xFF);
                for (int i = 0; i < copyCounter + 18; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = row[currentByteIndex + 2];
                }
                currentByteIndex += 2;
            }
            case 0x50 ->
            {
                for (int i = 0; i < endOfFirstByte * 256 + (row[currentByteIndex + 1] & 0xFF)
                        + 17; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = 0x40;
                }
                currentByteIndex++;
            }
            case 0x60 ->
            {
                for (int i = 0; i < endOfFirstByte * 256 + (row[currentByteIndex + 1] & 0xFF)
                        + 17; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = 0x20;
                }
                currentByteIndex++;
            }
            case 0x70 ->
            {
                for (int i = 0; i < endOfFirstByte * 256 + (row[currentByteIndex + 1] & 0xFF)
                        + 17; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = 0x00;
                }
                currentByteIndex++;
            }
            case 0x80, 0x90, 0xA0, 0xB0 ->
            {
                countOfBytesToCopy = endOfFirstByte + 1 + (controlByte - 0x80);
                if (countOfBytesToCopy > length - (currentByteIndex + 1))
                {
                    // A copy running past the end of the compressed row is a format error, the
                    // same one the 0x0n arm reports. Clamping it silently returned a short row
                    // whose tail was left at the zero fill - fabricated NUL bytes reported as
                    // success (SAS pads character fields with 0x20, so the tail was not even a
                    // plausible blank).
                    throw new IOException("Truncated RLE copy at position " + currentByteIndex
                            + ": control byte 0x"
                            + Integer.toHexString(row[currentByteIndex] & 0xFF) + " needs "
                            + countOfBytesToCopy + " source bytes but only "
                            + (length - (currentByteIndex + 1)) + " remain");
                }
                System.arraycopy(row, currentByteIndex + 1, resultByteArray,
                        currentResultArrayIndex, countOfBytesToCopy);
                currentByteIndex += countOfBytesToCopy;
                currentResultArrayIndex += countOfBytesToCopy;
            }
            case 0xC0 ->
            {
                for (int i = 0; i < endOfFirstByte + 3; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = row[currentByteIndex + 1];
                }
                currentByteIndex++;
            }
            case 0xD0 ->
            {
                for (int i = 0; i < endOfFirstByte + 2; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = 0x40;
                }
            }
            case 0xE0 ->
            {
                for (int i = 0; i < endOfFirstByte + 2; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = 0x20;
                }
            }
            case 0xF0 ->
            {
                for (int i = 0; i < endOfFirstByte + 2; i++)
                {
                    resultByteArray[currentResultArrayIndex++] = 0x00;
                }
            }
            default -> LOGGER.error("Error control byte: {}", controlByte);
            }
            currentByteIndex++;
        }

        return resultByteArray;
    }

}
