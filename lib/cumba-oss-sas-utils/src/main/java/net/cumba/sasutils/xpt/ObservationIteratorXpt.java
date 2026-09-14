/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.xpt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.cumba.sasutils.Observation;
import net.cumba.sasutils.VariableType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.Struct;
import org.thshsh.struct.TokenType;

/**
 * This class is used so that we can stream Observations into memory and not have to read them all
 * at once
 *
 * @author daniel.watson
 *
 */
public class ObservationIteratorXpt implements Iterator<Observation>
{

    static final Logger LOGGER = LoggerFactory.getLogger(ObservationIteratorXpt.class);

    public static final String CHARSET = "ISO-8859-1";

    public static final byte SENTINEL = ' ';

    public static final char NO_VALUE = '.';

    // IBM numeric values are big endian unsigned longs
    public static final Struct<?> IBM = Struct.create(">Q");

    protected int observationSize = 0;

    protected byte[] buffer;

    protected ObservationFrameReaderXpt frames;

    protected DatasetXpt member;

    protected Struct<?> struct;

    protected @Nullable Boolean hasNext = null;

    protected Boolean needToRead = true;

    public ObservationIteratorXpt(DatasetXpt m, InputStream in)
    {

        this.member = m;

        // observations are stored as a packed struct consisting of either bytes or characters for
        // each variable
        struct = new Struct<>();
        for (VariableXpt variable : member.getVariables())
        {
            struct.appendToken(
                    variable.getType() == VariableType.NUMERIC ? TokenType.Bytes : TokenType.String,
                    variable.getLength());
        }
        observationSize = struct.byteCount();

        try
        {
            // The frame reader owns the stream, the pushback, the read buffer and all three
            // end-of-member decisions; this iterator only maps frames to Observations.
            frames = new ObservationFrameReaderXpt(in, observationSize,
                    member.getObservationStartByte());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException(e);
        }
        buffer = new byte[observationSize];

    }


    @Override
    public boolean hasNext()
    {
        readIfNecessary();
        // readIfNecessary() always assigns hasNext a non-null value.
        return Boolean.TRUE.equals(hasNext);
    }


    protected void readIfNecessary()
    {
        try
        {
            if (needToRead)
            {
                // The frame reader makes all three end-of-member decisions (truncated tail,
                // header-record end, blank padding) and returns null when the member's rows end.
                byte[] frame = frames.nextFrame();
                hasNext = frame != null;
                if (frame != null)
                {
                    buffer = frame;
                }
                needToRead = false;
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public Observation next()
    {

        readIfNecessary();
        if (!Boolean.TRUE.equals(hasNext))
        {
            throw new NoSuchElementException();
        }
        needToRead = true;

        List<Object> tokens = struct.unpack(buffer);

        if (tokens.size() != member.getVariables().size())
        {
            throw new IllegalStateException("Token Count: " + tokens.size()
                    + " Not Equal to Header Count: " + member.getVariables().size());
        }

        Observation ob = new Observation();

        for (int i = 0; i < member.getVariables().size(); i++)
        {
            VariableXpt vm = this.member.getVariables().get(i);
            Object val = tokens.get(i);
            if (vm.getType() == VariableType.NUMERIC)
            {
                try
                {
                    val = ibmToIeee((byte[]) val);
                }
                catch (IOException e)
                {
                    throw new IllegalStateException(
                            "Unreadable numeric value in XPT observation, variable " + vm.getName(),
                            e);
                }
            }
            ob.putValue(vm, val);
        }

        return ob;

    }


    public List<Object> nextNative()
    {

        readIfNecessary();
        if (!Boolean.TRUE.equals(hasNext))
        {
            throw new NoSuchElementException();
        }
        needToRead = true;

        List<Object> tokens = struct.unpack(buffer);

        if (tokens.size() != member.getVariables().size())
        {
            throw new IllegalStateException("Token Count: " + tokens.size()
                    + " Not Equal to Header Count: " + member.getVariables().size());
        }

        for (int i = 0; i < tokens.size(); i++)
        {
            Object val = tokens.get(i);
            if (val instanceof byte[] byteArray)
            {
                try
                {
                    val = ibmToIeee(byteArray);
                }
                catch (IOException e)
                {
                    throw new IllegalStateException(
                            "Unreadable numeric value in XPT observation, variable index " + i, e);
                }
                tokens.set(i, val);
            }
        }
        return tokens;
    }


    /**
     * Converts one 8-byte IBM 370 hex-float XPT value to a Java {@link Double}, or {@code null} for
     * a SAS missing value.
     * <p>
     * ⚠ Deliberately lossy for special missings: {@code .}, {@code ._} and {@code .A}-{@code .Z}
     * all map to the same {@code null}, discarding the tag. This class is inherited from the
     * original sas-utils source and is not on the product's XPT read path - the product reads XPT
     * through {@code provider-sas}'s {@code XptVarParser}, and the special-missing tags are handled
     * at that provider layer. Preserving the tag here would change this class's public contract for
     * no consumer (owner ruling on F-sas-03, 2026-09).
     *
     * @param bytes
     *            the IBM 370 value, up to 8 bytes (shorter input is zero-padded)
     * @return the decoded {@link Double}, or {@code null} for any SAS missing value
     * @throws IOException
     *             if the bytes are not a value an XPT file can legitimately contain (zero mantissa
     *             with an unrecognised lead byte)
     */
    public static @Nullable Object ibmToIeee(byte[] bytes) throws IOException
    {

        byte[] padded = Arrays.copyOf(bytes, 8);

        List<Object> tokens = IBM.unpack(padded);
        Long val = ((Number) tokens.get(0)).longValue();
        long sign = val & 0x8000000000000000l;
        long exponent = (val & 0x7f00000000000000l) >> 56;
        long mantissa = val & 0x00ffffffffffffffl;

        if (mantissa == 0)
        {
            if (bytes[0] == 0x00)
            {
                return 0d;
            }
            else if ((bytes[0] & 0xFF) == 0x80)
            {
                return -0d;
            }
            else if (bytes[0] == NO_VALUE)
            {
                return null;
            }
            else if (MissingValue.fromCharacter((char) bytes[0]) != null)
            {
                return null;
            }
            else
            {
                // A zero mantissa with any other lead byte is a byte sequence no SAS-written XPT
                // file contains. Report it as a checked format error the caller can attribute,
                // not an unchecked argument exception (F-sas-11).
                throw new IOException("Unreadable IBM value: zero mantissa with lead byte 0x"
                        + Integer.toHexString(bytes[0] & 0xFF));
            }
        }

        int shift;

        if ((val & 0x0080000000000000l) > 0)
        {
            shift = 3;
        }
        else if ((val & 0x0040000000000000l) > 0)
        {
            shift = 2;
        }
        else if ((val & 0x0020000000000000l) > 0)
        {
            shift = 1;
        }
        else
        {
            shift = 0;
        }

        mantissa = mantissa >> shift;
        mantissa = mantissa & 0xffefffffffffffffl;
        exponent -= 65;
        exponent <<= 2;
        exponent += shift + 1023;
        long ieee = sign | (exponent << 52) | mantissa;

        return Double.longBitsToDouble(ieee);

    }

}
