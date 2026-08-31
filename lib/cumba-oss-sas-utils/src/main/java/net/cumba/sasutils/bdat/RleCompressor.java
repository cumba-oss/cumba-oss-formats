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
                int copyCounter = endOfFirstByte * 16 + (row[currentByteIndex + 1] & 0xFF);
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
                countOfBytesToCopy = Math.min(endOfFirstByte + 1 + (controlByte - 0x80),
                        length - (currentByteIndex + 1));
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
