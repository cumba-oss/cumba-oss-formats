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

import org.thshsh.struct.Struct;
import org.thshsh.struct.StructEntity;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenPrefix;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
@StructEntity(trimAndPad = true)
public class Header1
{

    public static final Struct<Header1> STRUCT = Struct.create(Header1.class);

    static final byte ALIGN_CHECKER_VALUE = 51;

    static final int ALIGN_1_DEFAULT = 4;

    static final int ALIGN_2_DEFAULT = 4;

    @StructTokenPrefix(
    {
            @StructToken(type = TokenType.Bytes,
                    constant = "000000000000000000000000c2ea8160b31411cfbd92080009c7318c181f1011")
    })
    @StructToken(order = 1)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000", validate = false)
    })
    public Byte align1;

    @StructToken(order = 3)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "00", validate = false)
    })
    public Byte align2;

    @StructToken(order = 5)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "00", validate = false)
    })
    public Boolean littleEndian;

    public Byte getAlign1()
    {
        return align1;
    }


    public void setAlign1(Byte align1)
    {
        this.align1 = align1;
    }


    public Byte getAlign2()
    {
        return align2;
    }


    public void setAlign2(Byte align2)
    {
        this.align2 = align2;
    }


    public Boolean getLittleEndian()
    {
        return littleEndian;
    }


    public void setLittleEndian(Boolean littleEndian)
    {
        this.littleEndian = littleEndian;
    }


    public org.thshsh.struct.ByteOrder getByteOrder()
    {
        return littleEndian ? org.thshsh.struct.ByteOrder.Little : org.thshsh.struct.ByteOrder.Big;
    }


    public Boolean get64Bit()
    {
        return align1 == ALIGN_CHECKER_VALUE;
    }


    public Integer getIntegerLength()
    {
        return get64Bit() ? 8 : 4;
    }


    public int getHeader1Padding()
    {
        return (align2 == ALIGN_CHECKER_VALUE) ? ALIGN_1_DEFAULT : 0;
    }


    public int getHeader2Padding()
    {
        return (align1 == ALIGN_CHECKER_VALUE) ? ALIGN_2_DEFAULT : 0;
    }


    public int getSubHeaderPointerLength()
    {
        return get64Bit() ? 24 : 12;
    }


    public TokenType getIntegerTokenType()
    {
        return get64Bit() ? TokenType.Long : TokenType.Integer;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Header1 [align1=");
        builder.append(align1);
        builder.append(", align2=");
        builder.append(align2);
        builder.append(", littleEndian=");
        builder.append(littleEndian);

        builder.append(", 64Bit=");
        builder.append(get64Bit());
        builder.append(", getHeader1Padding=");
        builder.append(getHeader1Padding());
        builder.append(", getHeader2Padding=");
        builder.append(getHeader2Padding());
        builder.append("]");
        return builder.toString();
    }

}
