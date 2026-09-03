package net.cumba.cdisc.dsj;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * Bean class for a DataSet JSON Table column.
 */
@Value
@Builder
@Jacksonized
public class DsjTableColumn
{

    /**
     * The index of the column in the table.
     */
    @JsonIgnore
    private final int index;

    /**
     * The item OID of the column item.
     */
    @NonNull
    private final String itemOID;

    /**
     * The name of the column.
     */
    @NonNull
    private final String name;

    /**
     * The label of the column.
     */
    @NonNull
    private final String label;

    /**
     * The data type of the column.
     */
    @NonNull
    private final String dataType;

    /**
     * The optional target data type of the column.
     */
    private final @Nullable String targetDataType;

    /**
     * The optional display format of the column.
     */
    private final @Nullable String displayFormat;

    /**
     * The optional length of the column.<br/>
     * -1 is used to indicate no length was provided.
     */
    @Builder.Default
    private final int length = -1;

    /**
     * The optional key sequence of the column.<br/>
     * -1 is used to indicate not a key variable.
     */
    @Builder.Default
    private final int keySequence = -1;

}
