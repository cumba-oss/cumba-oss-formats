package net.cumba.cdisc.dsj;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Bean for the DataSet JSON source system information.
 */
@Value
@Builder
@Jacksonized
public class DsjSourceSystem
{

    /**
     * The name of the sourceSystem that generated the DataSet JSON file.
     */
    @NonNull
    public final String name;

    /**
     * The version of the sourceSystem that generated the DataSet JSON file.
     */
    @NonNull
    public final String version;
}
