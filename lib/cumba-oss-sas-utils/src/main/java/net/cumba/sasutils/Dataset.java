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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.jspecify.annotations.Nullable;

// variableMap / variableMapIgnoreCase are lazily built by initVariableMaps() and null-guarded at
// every read, so the constructor does not initialise them — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public abstract class Dataset
{

    protected @Nullable Library library;

    protected Map<String, Variable> variableMap;

    protected Map<String, Variable> variableMapIgnoreCase;

    protected Dataset(@Nullable Library l)
    {
        this.library = l;
    }


    public Optional<? extends Variable> getVariable(String name)
    {
        return Optional.ofNullable(getVariableMap().get(name));
    }


    public Optional<? extends Variable> getVariableIgnoreCase(String name)
    {
        if (name == null)
        {
            return Optional.empty();
        }
        return Optional.ofNullable(getVariableMapIgnoreCase().get(name.toLowerCase(Locale.ROOT)));
    }


    public abstract String getName();


    public abstract void setName(String name);


    public abstract @Nullable String getType();


    public abstract void setType(String type);


    public abstract LocalDateTime getModified();


    public abstract LocalDateTime getCreated();


    public abstract @Nullable Long getRowCount();


    public abstract List<? extends Variable> getVariables();


    public Map<String, Variable> getVariableMap()
    {
        if (variableMap == null)
        {
            initVariableMaps();
        }
        return variableMap;
    }


    public Map<String, Variable> getVariableMapIgnoreCase()
    {
        if (variableMapIgnoreCase == null)
        {
            initVariableMaps();
        }
        return variableMapIgnoreCase;
    }


    protected void initVariableMaps()
    {
        variableMap = new HashMap<>();
        variableMapIgnoreCase = new HashMap<>();
        getVariables().forEach(variable ->
        {
            String name = variable.getName();
            if (name == null)
            {
                return;
            }
            variableMapIgnoreCase.put(name.toLowerCase(Locale.ROOT), variable);
            variableMap.put(name, variable);
        });
    }


    public @Nullable Library getLibrary()
    {
        return library;
    }


    public abstract void setVariables(List<? extends Variable> variables);


    public Stream<Observation> streamObservations() throws IOException
    {
        Library lib = getLibrary();
        if (lib == null || lib.getFile() == null)
        {
            throw new IllegalStateException("Library does not hold a reference to a file");
        }
        return streamObservations(lib.getRandomAccessFileInputStream());
    }


    public Stream<Observation> streamObservations(File file) throws IOException
    {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        RandomAccessFileInputStream rafis = new RandomAccessFileInputStream(raf, true);
        return streamObservations(rafis);
    }


    public Stream<Observation> streamObservations(RandomAccessFileInputStream is)
    {
        return createObservationStream(is).onClose(() ->
        {
            try
            {
                // NOTE this ensures that we close the file if we close the obs stream
                is.close();
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
        });
    }


    protected abstract Stream<Observation> createObservationStream(RandomAccessFileInputStream is);


    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Dataset [getName()=");
        builder.append(getName());
        builder.append(", getType()=");
        builder.append(getType());
        builder.append("]");
        return builder.toString();
    }

}
