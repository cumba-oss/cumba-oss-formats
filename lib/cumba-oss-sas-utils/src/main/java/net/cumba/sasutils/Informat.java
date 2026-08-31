/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils;

/**
 * Informats tell SAS how to READ the data
 *
 * @author daniel.watson
 *
 */
public class Informat
{

    protected String name;

    protected Integer length;

    protected Integer decimals;

    public Informat(String name, Integer length, Integer decimals)
    {
        super();
        this.name = name;
        this.length = length;
        this.decimals = decimals;
    }


    public String getName()
    {
        return name;
    }


    public Integer getLength()
    {
        return length;
    }


    public Integer getDecimals()
    {
        return decimals;
    }


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("[name=");
        builder.append(name);
        builder.append(", length=");
        builder.append(length);
        builder.append(", decimals=");
        builder.append(decimals);
        builder.append("]");
        return builder.toString();
    }

}
