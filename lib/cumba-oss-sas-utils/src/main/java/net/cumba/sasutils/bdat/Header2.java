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

import org.thshsh.struct.StructEntity;
import org.thshsh.struct.StructToken;
import org.thshsh.struct.StructTokenSuffix;
import org.thshsh.struct.TokenType;

// @StructToken fields are populated by the org.thshsh.struct deserialiser after construction —
// hence the Init suppression.
@SuppressWarnings("NullAway.Init")
@StructEntity(trimAndPad = true)
public class Header2
{

    @StructToken(order = -4, length = 1)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "0000000000000000", validate = false),
            @StructToken(type = TokenType.Bytes, constant = "0000000000000000", validate = false),
            @StructToken(type = TokenType.Bytes, constant = "0000000000000000", validate = false), // supposedly
                                                                                                   // a
                                                                                                   // repeat
                                                                                                   // of
                                                                                                   // bytes
                                                                                                   // 32-40
                                                                                                   // in
                                                                                                   // this
                                                                                                   // struct
            @StructToken(type = TokenType.Bytes, constant = "000000000000", validate = false)
    })
    public String platform;

    @StructToken(order = -3)
    @StructTokenSuffix(
    {
            @StructToken(type = TokenType.Bytes, constant = "000000000000000000000000",
                    validate = false),
            @StructToken(type = TokenType.String, constant = "SAS FILE")
    })
    public Short encoding;

    @StructToken(order = -2, length = 64)
    public String datasetName;

    @StructToken(order = -1, length = 8)
    public String fileType;

    public String getPlatform()
    {
        return platform;
    }


    public void setPlatform(String platform)
    {
        this.platform = platform;
    }


    public String getDatasetName()
    {
        return datasetName;
    }


    public void setDatasetName(String datasetName)
    {
        this.datasetName = datasetName;
    }


    public String getFileType()
    {
        return fileType;
    }


    public void setFileType(String fileType)
    {
        this.fileType = fileType;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Header2 [platform=");
        builder.append(platform);
        builder.append(", encoding=");
        builder.append(encoding);
        builder.append(", datasetName=");
        builder.append(datasetName.trim());
        builder.append(", fileType=");
        builder.append(fileType);
        builder.append("]");
        return builder.toString();
    }

}
