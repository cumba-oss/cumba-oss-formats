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
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.input.RandomAccessFileInputStream;
import org.jspecify.annotations.Nullable;

public abstract class Library
{

    protected File file;

    protected Library(File f)
    {
        this.file = f;
    }


    public RandomAccessFileInputStream getRandomAccessFileInputStream() throws IOException
    {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        return new RandomAccessFileInputStream(raf, true);
    }


    public abstract @Nullable LocalDateTime getModified();


    public abstract @Nullable LocalDateTime getCreated();


    public abstract List<? extends Dataset> getDatasets();


    public abstract void setDatasets(List<? extends Dataset> ds);


    public Optional<? extends Dataset> getDataset(String name)
    {
        return getDatasets().stream().filter(d -> d.getName().equals(name)).findFirst();
    }


    public File getFile()
    {
        return file;
    }

}
