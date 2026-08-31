package net.cumba.datasetjson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * Bean class for a DataSet JSON Table.
 */
// NullAway.Init: the Lombok @Builder/@Value all-args constructor initialises every @NonNull field,
// but NullAway's field-initialization check cannot see through the generated constructor combined
// with @Builder.Default + the custom DsjTableBuilder (NullAway#917). The @NonNull contracts are
// still enforced at build() time by Lombok.
@SuppressWarnings("NullAway.Init")
@Value
@Builder
@Jacksonized
public class DsjTable
{

    /**
     * The date/time the Dataset-JSON file was created.
     */
    @NonNull
    private final String datasetJSONCreationDateTime;

    /**
     * Version of Dataset-JSON standard.
     */
    @NonNull
    @Builder.Default
    private final String datasetJSONVersion = "1.1.0";

    /**
     * Foreign key to ItemGroupDef.OID in Define / MDR.
     */
    @NonNull
    private final String itemGroupOID;

    /**
     * The table name.
     */
    @NonNull
    private final String name;

    /**
     * The table description.
     */
    @NonNull
    private final String label;

    /**
     * Basic information about variables of this table.
     */
    @NonNull
    private final DsjTableColumn[] columns;

    /**
     * The total number of records in this table.
     */
    @Builder.Default
    private final long records = -1;

    /**
     * The date/time the source database was last modified.
     */
    private final @Nullable String dbLastModifiedDateTime;

    /**
     * A unique identifier for the DataSet JSON file of this table.
     */
    private final @Nullable String fileOID;

    /**
     * URL for a metadata file describing the data.
     */
    private final @Nullable String metaDataRef;

    /**
     * See ODM definition for metadata version OID (ODM/Study/MetaDataVersion/@OID).
     */
    private final @Nullable String metaDataVersionOID;

    /**
     * The organization that generated the Dataset-JSON file.
     */
    private final @Nullable String originator;

    /**
     * See ODM definition for study OID (ODM/Study/@OID).
     */
    private final @Nullable String studyOID;

    /**
     * The computer system or database management system that is the source of the information in
     * the DataSet JSON file.
     */
    private final @Nullable DsjSourceSystem sourceSystem;

    /**
     * Returns a copy of the internal columns array.
     *
     * @return a copy of the internal columns array.
     */
    public DsjTableColumn[] getColumns()
    {
        return Arrays.copyOf(columns, columns.length);
    }


    @JsonIgnore
    public int getColumnCount()
    {
        return columns.length;
    }


    @JsonIgnore
    public DsjTableColumn getColumn(int aIndex)
    {
        return columns[aIndex];
    }


    @JsonIgnore
    public Stream<DsjTableColumn> getColumnsStream()
    {
        return Arrays.stream(columns);
    }

    public static class DsjTableBuilder
    {

        /**
         * Check if the given array is either null or has a length of 0.
         *
         * @param <T>
         *            the type of the array to be checked.
         * @param anArray
         *            the array to be checked.
         * @return true if the given array is null or has a length of 0.
         */
        private static <T> boolean isEmptyOrNull(T[] anArray)
        {
            return anArray == null || anArray.length == 0;
        }


        /**
         * Check if the given Collection is either null or has a size of 0.
         *
         * @param <T>
         *            the type of the Collection to be checked.
         * @param aList
         *            the Collection to be checked.
         * @return true if the given Collection is null or has a size of 0.
         */
        private static <T> boolean isEmptyOrNull(Collection<T> aList)
        {
            return aList == null || aList.isEmpty();
        }

        private static final DateTimeFormatter DSJ_TS = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

        public DsjTableBuilder setDatasetJSONCreationDateTime(Date aDate)
        {
            String val = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(aDate);
            return datasetJSONCreationDateTime(val);
        }


        public DsjTableBuilder setDatasetJSONCreationDateTime(Instant aInstant)
        {
            return datasetJSONCreationDateTime(DSJ_TS.format(aInstant));
        }


        public DsjTableBuilder setDbLastModifiedDateTime(Date aDate)
        {
            String val = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(aDate);
            return dbLastModifiedDateTime(val);
        }


        public DsjTableBuilder setDbLastModifiedDateTime(Instant aInstant)
        {
            return dbLastModifiedDateTime(DSJ_TS.format(aInstant));
        }


        // NullAway: the Lombok-generated builder field `columns` mirrors the
        // @NonNull model field, but a builder legitimately holds null before
        // build(); clearing it to null is the documented @Builder-customization
        // interaction. addColumns()/columns() guard on `columns == null`.
        @SuppressWarnings("NullAway")
        public DsjTableBuilder clearColumns()
        {
            columns = null;
            return this;
        }


        public DsjTableBuilder addColumns(DsjTableColumn... aColumns)
        {
            if (isEmptyOrNull(aColumns))
            {
                return this;
            }

            if (columns == null)
            {
                return columns(aColumns);
            }

            int oldLen = columns.length;
            int newLen = oldLen + aColumns.length;
            DsjTableColumn[] newCols = Arrays.copyOf(columns, newLen);
            System.arraycopy(aColumns, 0, newCols, oldLen, aColumns.length);
            return columns(newCols);
        }


        public DsjTableBuilder addColumns(List<DsjTableColumn> aColumns)
        {
            if (isEmptyOrNull(aColumns))
            {
                return this;
            }

            return addColumns(aColumns.toArray(DsjTableColumn[]::new));
        }


        public DsjTableBuilder columns(DsjTableColumn... aColumns)
        {
            if (isEmptyOrNull(aColumns))
            {
                return clearColumns();
            }

            for (int i = 0; i < aColumns.length; i++)
            {
                DsjTableColumn c = aColumns[i];
                if (c == null)
                {
                    throw new IllegalArgumentException("Null column found at %d".formatted(i));
                }
                if (c.getIndex() != i)
                {
                    throw new IllegalArgumentException("Invalid column index %d found in column %d."
                            .formatted(c.getIndex(), i));
                }
            }

            columns = Arrays.copyOf(aColumns, aColumns.length);
            return this;
        }


        public DsjTableBuilder columns(List<DsjTableColumn> aColumns)
        {
            if (isEmptyOrNull(aColumns))
            {
                return clearColumns();
            }
            return columns(aColumns.toArray(DsjTableColumn[]::new));
        }

    }
}
