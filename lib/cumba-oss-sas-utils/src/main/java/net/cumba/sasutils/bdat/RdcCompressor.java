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

public class RdcCompressor implements Compressor
{

    @Override
    public byte[] decompressRow(final int resultLength, final byte[] srcRow)
    {

        int srcLength = srcRow.length;
        byte[] outRow = new byte[resultLength];
        int srcOffset = 0;
        int outOffset = 0;
        int ctrlBits = 0;
        int ctrlMask = 0;
        while (srcOffset < srcLength)
        {

            ctrlMask >>= 1;
            if (ctrlMask == 0)
            {
                ctrlBits = ((srcRow[srcOffset] & 0xff) << 8) | (srcRow[srcOffset + 1] & 0xff);
                srcOffset += 2;
                ctrlMask = 0x8000;
            }

            // just copy this char if control bit is zero
            if ((ctrlBits & ctrlMask) == 0)
            {
                outRow[outOffset++] = srcRow[srcOffset++];
                continue;
            }

            // undo the compression code
            final int cmd = (srcRow[srcOffset] >> 4) & 0x0F;
            int cnt = srcRow[srcOffset++] & 0x0F;

            switch (cmd)
            {
            case 0 ->
            { // short rle
                cnt += 3;
                for (int i = 0; i < cnt; i++)
                {
                    outRow[outOffset + i] = srcRow[srcOffset];
                }
                srcOffset++;
                outOffset += cnt;
            }
            case 1 ->
            { // long rle
                cnt += ((srcRow[srcOffset++] & 0xff) << 4);
                cnt += 19;
                for (int i = 0; i < cnt; i++)
                {
                    outRow[outOffset + i] = srcRow[srcOffset];
                }
                srcOffset++;
                outOffset += cnt;
            }
            case 2 ->
            { // long pattern
                int ofs = cnt + 3;
                ofs += ((srcRow[srcOffset++] & 0xff) << 4);
                cnt = srcRow[srcOffset++] & 0xff;
                cnt += 16;
                System.arraycopy(outRow, outOffset - ofs, outRow, outOffset, cnt);
                outOffset += cnt;
            }
            default ->
            { // short pattern
                int ofs = cnt + 3;
                ofs += ((srcRow[srcOffset++] & 0xff) << 4);
                System.arraycopy(outRow, outOffset - ofs, outRow, outOffset, cmd);
                outOffset += cmd;
            }
            }
        }
        return outRow;
    }

}
