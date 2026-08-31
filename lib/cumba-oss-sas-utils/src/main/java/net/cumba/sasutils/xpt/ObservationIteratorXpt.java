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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import net.cumba.sasutils.Observation;
import net.cumba.sasutils.VariableType;
import org.apache.commons.io.IOUtils;
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

    protected InputStream input;

    protected DatasetXpt member;

    protected Struct<?> struct;

    protected @Nullable Boolean hasNext = null;

    protected Boolean needToRead = true;

    public ObservationIteratorXpt(DatasetXpt m, InputStream in)
    {

        this.member = m;
        this.input = in;

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
            IOUtils.skip(input, member.getObservationStartByte());
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
                int read = IOUtils.read(input, buffer);
                if (read != observationSize)
                {
                    hasNext = false;
                }
                else
                {
                    hasNext = false;
                    for (byte b : buffer)
                    {
                        if (b != SENTINEL)
                        {
                            hasNext = true;
                            break;
                        }
                    }
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
                val = ibmToIeee((byte[]) val);
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
                val = ibmToIeee(byteArray);
                tokens.set(i, val);
            }
        }
        return tokens;
    }


    public static @Nullable Object ibmToIeee(byte[] bytes)
    {

        byte[] padded = java.util.Arrays.copyOf(bytes, 8);

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
                throw new IllegalArgumentException("Zero Mantissa Value was not readable");
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
