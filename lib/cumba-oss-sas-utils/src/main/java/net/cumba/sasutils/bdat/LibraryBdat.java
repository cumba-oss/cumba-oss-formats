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

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.cumba.sasutils.Dataset;
import net.cumba.sasutils.Library;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Bdat "library" is actually just a folder containing sas7bdat files Each file is a single
 * dataset TODO handle the case where the file passed in to the library is a directory or a single
 * dataset
 *
 * @author daniel.watson
 *
 */
public class LibraryBdat extends Library
{

    protected static final Logger LOGGER = LoggerFactory.getLogger(LibraryBdat.class);

    List<DatasetBdat> datasets = new ArrayList<>();

    public LibraryBdat(File f)
    {
        super(f);
    }


    @Override
    public @Nullable LocalDateTime getModified()
    {
        Optional<LocalDateTime> zdt = datasets.stream().map(DatasetBdat::getHeader3)
                .map(Header3::getModified).max(Comparator.comparing(Function.identity()));
        return zdt.orElse(null);
    }


    @Override
    public @Nullable LocalDateTime getCreated()
    {
        Optional<LocalDateTime> zdt = datasets.stream().map(DatasetBdat::getHeader3)
                .map(Header3::getCreated).min(Comparator.comparing(Function.identity()));
        return zdt.orElse(null);
    }


    @Override
    public List<? extends Dataset> getDatasets()
    {
        return datasets;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void setDatasets(List<? extends Dataset> ds)
    {
        this.datasets = (List<DatasetBdat>) ds;

    }

}
