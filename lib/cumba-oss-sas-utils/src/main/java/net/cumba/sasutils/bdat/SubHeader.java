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

// pointer and dataset are wired up by the parser via setPointer()/setDataset() after construction,
// so the constructor does not initialise them — hence the Init suppression.
@SuppressWarnings("NullAway.Init")
public class SubHeader
{

    protected SubHeaderPointer pointer;

    protected DatasetBdat dataset;

    public DatasetBdat getDataset()
    {
        return dataset;
    }


    public void setDataset(DatasetBdat dataset)
    {
        this.dataset = dataset;
    }


    public SubHeaderPointer getPointer()
    {
        return pointer;
    }


    public void setPointer(SubHeaderPointer pointer)
    {
        this.pointer = pointer;
        pointer.setSubHeader(this);
    }

}
