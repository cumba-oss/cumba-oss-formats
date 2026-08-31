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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.cumba.sasutils.Dataset;
import net.cumba.sasutils.Observation;
import net.cumba.sasutils.Variable;
import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.jspecify.annotations.Nullable;

// variables is built lazily by getVariables(); the constructor only sets header, so it does not
// initialise variables — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class DatasetXpt extends Dataset
{

    protected DatasetHeaderXpt header;

    protected List<VariableXpt> variables;

    protected long observationStartByte;

    public DatasetXpt(LibraryXpt lib, DatasetHeaderXpt header)
    {
        super(lib);
        this.header = header;
    }


    @Override
    public String getName()
    {
        return header.getName();
    }


    @Override
    public void setName(String name)
    {
        header.setName(name);
    }


    public String getLabel()
    {
        return header.getLabel();
    }


    public void setLabel(String label)
    {
        header.setLabel(label);
    }


    @Override
    public String getType()
    {
        return header.getType();
    }


    @Override
    public void setType(String type)
    {
        header.setType(type);
    }


    @Override
    public List<VariableXpt> getVariables()
    {
        if (variables == null) variables = new ArrayList<>();
        return variables;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void setVariables(List<? extends Variable> variables)
    {
        this.variables = (List<VariableXpt>) variables;
    }


    public long getObservationStartByte()
    {
        return observationStartByte;
    }


    public void setObservationStartByte(long observationStartByte)
    {
        this.observationStartByte = observationStartByte;
    }


    @Override
    public LocalDateTime getModified()
    {
        return header.getModified();
    }


    @Override
    public LocalDateTime getCreated()
    {
        return header.getCreated();
    }


    @Override
    protected Stream<Observation> createObservationStream(RandomAccessFileInputStream is)
    {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                new ObservationIteratorXpt(this, is), Spliterator.NONNULL), false);
    }


    @Override
    public @Nullable LibraryXpt getLibrary()
    {
        return (LibraryXpt) super.getLibrary();
    }


    @Override
    public @Nullable Long getRowCount()
    {
        return null;
    }
}
