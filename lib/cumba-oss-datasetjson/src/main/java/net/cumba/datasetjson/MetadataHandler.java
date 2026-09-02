package net.cumba.datasetjson;

/**
 * Handler interface for DataSet-JSON table metadata.
 */
@FunctionalInterface
public interface MetadataHandler
{

    /**
     * Called once a new DsjTable metadata object is parsed.
     *
     * @param aTable
     *            the table metadata.
     * @return 0 to continue parsing, or any other value to stop parsing.
     */
    int metadata(DsjTable aTable);
}
