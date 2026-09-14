/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: extracted from ObservationIteratorXpt so that every XPT observation iterator in
 * the product shares ONE copy of the member-framing decisions; repackaged from org.thshsh.sas to
 * net.cumba.sasutils, annotated for null-safety, and adapted to this project's build and
 * static-analysis gates. See this module's README.md for the full attribution notice and
 * LICENSE-APACHE-2.0.txt for the licence.
 */
package net.cumba.sasutils.xpt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

/**
 * Reads the observation section of one XPT member as raw observation-sized {@code byte[]} frames.
 *
 * <p>
 * This class owns the stream, the pushback, the read buffer and all three decisions that frame a
 * member's observations, so that every observation iterator maps frames to its own row model
 * without re-implementing any format knowledge:
 * </p>
 * <ol>
 * <li><b>Truncated tail.</b> A short final read only happens at end of stream (the next member's
 * header record would always supply more bytes). XPT pads the last data record with ASCII blanks,
 * so a short all-blank tail is a clean end - but any observation DATA in it means the stream stops
 * in the middle of an observation, i.e. the file is truncated. XPT stores no row count, so nothing
 * downstream could ever notice the lost rows: the truncation fails loudly here rather than silently
 * dropping a partial row.</li>
 * <li><b>Header-record end.</b> The only thing that ends a member's rows is the next member's
 * {@value XptConstants#HEADER_TAG} record. The discriminator is the FULL 20-byte tag - never a
 * prefix match - completed from the stream via pushback when it straddles two frames.</li>
 * <li><b>Blank padding.</b> ASCII blanks before that tag, or a whole blank frame, are record
 * padding, not data: the XPORT v5 layout pads a member's data section to an 80-byte RECORD boundary
 * rather than to an observation boundary.</li>
 * </ol>
 */
public class ObservationFrameReaderXpt
{

    /** The ASCII blank XPT pads records with. */
    public static final byte SENTINEL = ' ';

    /** Length of {@link XptConstants#HEADER_TAG} in bytes - it is pure 7-bit ASCII. */
    private static final int HEADER_TAG_LENGTH = XptConstants.HEADER_TAG.length();

    /** The tag is pure 7-bit ASCII, so ISO-8859-1 and ASCII encode it identically. */
    private final byte[] headerBytes = XptConstants.HEADER_TAG
            .getBytes(StandardCharsets.ISO_8859_1);

    private final PushbackInputStream input;

    private final byte[] buffer;

    private final int observationSize;

    /**
     * Positions the reader on the member's first observation.
     *
     * @param aInput
     *            the stream holding the whole XPT library; the reader takes ownership of the
     *            member's observation section and reads at most {@value XptConstants#HEADER_TAG}
     *            bytes past its end (pushed back when they turn out not to end it).
     * @param aObservationSize
     *            the packed byte size of one observation of this member.
     * @param aObservationStartByte
     *            the offset of the member's first observation, skipped before the first frame.
     * @throws IOException
     *             if skipping to the first observation fails.
     */
    public ObservationFrameReaderXpt(InputStream aInput, int aObservationSize,
            long aObservationStartByte)
        throws IOException
    {
        // A HEADER RECORD tag can straddle two observation-sized reads, and the comparison in
        // nextFrame() then needs bytes that are not in the buffer yet. Pushback lets it complete
        // the 20-byte tag from the stream and give the bytes back when they turn out to be real
        // observation data, so the discriminator stays the FULL tag - never a prefix.
        input = new PushbackInputStream(aInput, HEADER_TAG_LENGTH);
        observationSize = aObservationSize;
        buffer = new byte[aObservationSize];
        IOUtils.skip(input, aObservationStartByte);
    }


    /**
     * Reads the next observation frame.
     *
     * @return the reader's internal buffer holding the next observation's bytes - overwritten by
     *         the next call, so callers that keep a frame must copy it - or {@code null} when the
     *         member's observation section has ended.
     * @throws IOException
     *             if reading fails, or if the stream ends mid-observation (a truncated file).
     */
    public byte @Nullable [] nextFrame() throws IOException
    {
        int read = IOUtils.read(input, buffer);
        if (read != observationSize)
        {
            // IOUtils.read only comes back short at end of stream, and the next member's header
            // record would always supply another 80 bytes - so a short read is the end of the
            // file. XPT pads the last data record with ASCII blanks, so a short all-blank tail is
            // a clean end; any DATA in it means the stream stops in the middle of an observation,
            // i.e. the file is truncated. XPT records no row count, so nothing downstream could
            // ever notice the lost rows: the truncation has to fail here rather than silently
            // drop a partial row.
            for (int i = 0; i < read; i++)
            {
                if (buffer[i] != SENTINEL)
                {
                    throw new IOException(
                            "Truncated XPT file: the stream ends %d byte(s) into an observation of %d bytes."
                                    .formatted(read, observationSize));
                }
            }
            return null;
        }
        for (int i = 0; i < buffer.length; i++)
        {
            byte b = buffer[i];
            if (b != SENTINEL)
            {
                // 'H' == the first byte of "HEADER RECORD*******": this member's observation
                // section has ended and the next library record starts inside this read.
                // Everything before i is record padding.
                if (b == 'H' && startsHeaderRecordAt(i))
                {
                    // a header record, not data --> the member's rows end here
                    return null;
                }
                return buffer;
            }
        }
        // A whole all-blank frame is the trailing record padding of the member's last data
        // record: the member has no more rows.
        return null;
    }


    /**
     * The member's stream as this reader sees it, pushback included - package-private, for tests
     * that assert the stream position stays honest after the framing stops.
     */
    InputStream input()
    {
        return input;
    }


    /**
     * Whether the bytes at {@code aIndex} in {@link #buffer} begin the
     * {@value XptConstants#HEADER_TAG} record that terminates a member's observation section.
     *
     * <p>
     * The XPORT v5 layout pads a member's data section to an 80-byte RECORD boundary rather than to
     * an observation boundary, and records no row count ("There is ASCII blank padding at the end
     * of the last record if necessary. There is no special trailing record."). So the only thing
     * that ends a member's rows is the next member's header record, and where its 20-byte tag falls
     * inside an observation-sized read is just {@code (paddedDataBytes mod observationSize)} -
     * every offset is reachable, including ones where the tag is only partly in the buffer.
     * </p>
     *
     * <p>
     * When the tag is split, the missing bytes are read from the stream and pushed back unless they
     * complete it - so this stays an exact 20-byte match and never a prefix match. A prefix match
     * would be unsafe in the other direction: a character value of {@code "H"} in an observation's
     * last byte would end the member early and silently drop every remaining row.
     * </p>
     *
     * @param aIndex
     *            the index in {@link #buffer} of the candidate first tag byte.
     * @return {@code true} when the full tag is present (in the buffer, or completed from the
     *         stream); {@code false} for observation data.
     * @throws IOException
     *             if completing the tag from the stream fails.
     */
    private boolean startsHeaderRecordAt(int aIndex) throws IOException
    {
        int inBuffer = buffer.length - aIndex;
        int compare = Math.min(inBuffer, HEADER_TAG_LENGTH);
        if (!Arrays.equals(buffer, aIndex, aIndex + compare, headerBytes, 0, compare))
        {
            return false;
        }
        if (inBuffer >= HEADER_TAG_LENGTH)
        {
            return true;
        }
        byte[] rest = new byte[HEADER_TAG_LENGTH - inBuffer];
        int read = IOUtils.read(input, rest);
        if (read == rest.length
                && Arrays.equals(rest, 0, read, headerBytes, inBuffer, HEADER_TAG_LENGTH))
        {
            // A real header record: the framing stops here, so these bytes are not needed again.
            // Pushing them back anyway keeps the stream position honest for any caller that reads
            // on after us.
            input.unread(rest, 0, read);
            return true;
        }
        if (read > 0)
        {
            input.unread(rest, 0, read);
        }
        return false;
    }

}
