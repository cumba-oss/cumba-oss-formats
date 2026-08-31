package net.cumba.datasetjson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * A parser that parses a DataSet JSON file via stream parsing. It requires to have the rows
 * attribute as last attribute in the JSON, or better it requires to have the columns attribute
 * before the rows attribute and will ignore all attributes after the rows attribute.<br/>
 * The parser is callback based. There are three callbacks:
 * <ul>
 * <li>{@link MetadataHandler} called before the rows are parsed and provides access to all table
 * attributes and all columns.
 * <li>{@link RowSliceHandler} called once in a while while parsing the rows. It provides access to
 * a slice of rows. The number of rows per slice can be configured by calling
 * {@code setRowSliceSize(int)}.
 * <li>{@link RowHandler} called for each parsed row, providing the row values as an
 * {@code Object[]}. This is mutually exclusive with {@link RowSliceHandler}.
 * </ul>
 */
@CustomLog
public class DataSetJsonTableParser
{

    /**
     * Callback handler for table metadata. This includes all table, as well as variable, related
     * properties.
     */
    @Getter
    @Setter
    private @Nullable MetadataHandler handlerMetadata;

    /**
     * Handler for table data. This handler retrieves the row data as columnar buffer slices.
     */
    @Getter
    @Setter
    private @Nullable RowSliceHandler handlerRows;

    /**
     * Handler for row-based table data. This handler receives one row at a time as an
     * {@code Object[]} array. Mutually exclusive with {@link #handlerRows} — if both are set, an
     * {@link IOException} is thrown at parse time.
     */
    @Getter
    @Setter
    private @Nullable RowHandler handlerRow;

    /**
     * The number of rows per slice.
     */
    @Getter
    @Setter
    private int rowSliceSize = 1000;

    /**
     * Parse a DataSet JSON that is given as a {@link File}.
     *
     * @param aFile
     *            the file to parse as DataSet JSON.
     * @throws IOException
     *             in case of any error in parsing.
     */
    public void parseDataSet(@NonNull File aFile) throws IOException
    {
        try (InputStream in = new FileInputStream(aFile))
        {
            parseDataSet(in);
        }
    }


    /**
     * Parse a DataSet JSON that is given as a {@link Path}.
     *
     * @param aFile
     *            the file to parse as DataSet JSON.
     * @throws IOException
     *             in case of any error in parsing.
     */
    public void parseDataSet(@NonNull Path aFile) throws IOException
    {
        try (InputStream in = Files.newInputStream(aFile))
        {
            parseDataSet(in);
        }
    }


    /**
     * Parse a DataSet JSON that is given as a {@link URL}.
     *
     * @param aURL
     *            the URL to parse as DataSet JSON.
     * @throws IOException
     *             in case of any error in parsing.
     */
    public void parseDataSet(@NonNull URL aURL) throws IOException
    {
        try (InputStream in = aURL.openStream())
        {
            parseDataSet(in);
        }
    }


    /**
     * Parse a DataSet JSON that is given as a {@link InputStream}.
     *
     * @param aStream
     *            the stream to parse as DataSet JSON.
     * @throws IOException
     *             in case of any error in parsing.
     */
    public void parseDataSet(@NonNull InputStream aStream) throws IOException
    {
        JsonFactory factory = new JsonFactory();

        BufferedInputStream bin = new BufferedInputStream(aStream);

        bin.mark(100);
        byte[] header = new byte[2];
        int bytesRead = bin.read(header);
        if (bytesRead < 2)
        {
            throw new IOException(
                    "Stream too short to detect format (read %d bytes)".formatted(bytesRead));
        }
        if (header[0] == (byte) 0x1F && header[1] == (byte) 0x8B)
        {
            // this is GZIP
            bin.reset();
            try (InputStream in2 = new GZIPInputStream(bin))
            {
                try (JsonParser parser = factory.createParser(in2))
                {
                    parseDataSet(parser);
                }
            }
        }
        else if (header[0] == (byte) 0x78)
        {
            // this is ZLIB
            bin.reset();
            try (Inflater inflater = new Inflater();
                    InputStream in2 = new InflaterInputStream(bin, inflater, 8192);
                    JsonParser parser = factory.createParser(in2);)
            {
                parseDataSet(parser);
            }
        }
        else if (header[0] == (byte) 0x7B)
        {
            // this is plain json
            bin.reset();
            try (JsonParser parser = factory.createParser(bin))
            {
                parseDataSet(parser);
            }
        }
        else
        {
            throw new IOException("Unexpected Header: %02X %02X%n".formatted(header[0], header[1]));
        }
    }


    /**
     * Parse a DataSet JSON that is given as a {@link JsonParser}.
     *
     * @param aParser
     *            the parser to parse as DataSet JSON.
     * @throws IOException
     *             in case of any error in parsing.
     */
    public void parseDataSet(@NonNull JsonParser aParser) throws IOException
    {
        if (handlerRows != null && handlerRow != null)
        {
            throw new IOException(
                    "Both handlerRows (slice-based) and handlerRow (row-based) are set. "
                            + "Only one row handling mode is supported at a time.");
        }

        JsonToken token;
        Map<String, Object> metadata = new HashMap<>();

        DsjTable table = null;

        DsjTableColumn[] columns = null;
        boolean rowsParsed = false;
        int objDepth = 0;
        while ((token = aParser.nextToken()) != null)
        {
            if (token == JsonToken.START_OBJECT)
            {
                objDepth++;
            }
            else if (token == JsonToken.END_OBJECT)
            {
                objDepth--;
            }
            else if (token == JsonToken.FIELD_NAME)
            {
                String fieldName = aParser.currentName();
                aParser.nextToken(); // move to value

                if (Objects.equals(fieldName, "columns"))
                {
                    // parse columns
                    // this is expected to be defined before rows attribute.
                    columns = parseColumns(aParser);
                }
                else if (Objects.equals(fieldName, "rows"))
                {
                    if (columns == null)
                    {
                        LOGGER.log(Level.WARNING,
                                "No \"columns\" attribute defined before \"rows\" attribute. This is not supported!");
                        columns = new DsjTableColumn[0];
                    }

                    table = buildTable(metadata, columns);
                    // parse the row data
                    // this is expected to be defined as last attribute.
                    if (handlerMetadata != null)
                    {
                        int res = handlerMetadata.metadata(table);
                        if (res != 0)
                        {
                            throw new IOException("User aborted!");
                        }
                    }

                    dispatchParseRows(aParser, table, false);
                    rowsParsed = true;
                }
                else
                {
                    Object value = parseAsValue(aParser);
                    if (rowsParsed)
                    {
                        LOGGER.log(Level.WARNING,
                                "Found attribute \"{0}\" with value \"{1}\" after rows. This is ignored.",
                                fieldName, value);
                    }
                    else
                    {
                        metadata.put(fieldName, value);
                    }
                }
            }
            else if (objDepth == 0 && token == JsonToken.START_ARRAY)
            {
                if (rowsParsed)
                {
                    throw new IOException("Rows already parsed");
                }

                if (columns == null)
                {
                    LOGGER.log(Level.WARNING,
                            "No \"columns\" attribute defined before \"rows\" attribute. This is not supported!");
                    columns = new DsjTableColumn[0];
                }

                table = buildTable(metadata, columns);
                // parse the row data
                // this is expected to be defined as last attribute.
                if (handlerMetadata != null)
                {
                    int res = handlerMetadata.metadata(table);
                    if (res != 0)
                    {
                        throw new IOException("User aborted!");
                    }
                }
                rowsParsed = true;
                dispatchParseRows(aParser, table, true);
            }
        }
    }


    /**
     * Dispatch row parsing to either slice-based or row-based mode depending on which handler is
     * set. If {@link #handlerRow} is set, uses {@link #parseRowsRowBased}. Otherwise, uses
     * {@link #parseRows} (the slice-based default).
     *
     * @param aParser
     *            the parser to parse the rows from.
     * @param aTable
     *            the table that is currently parsed.
     * @param aIsNDJson
     *            {@code true} if the rows are in newline delimited JSON format.
     * @throws IOException
     *             in case of any parsing error.
     */
    private void dispatchParseRows(JsonParser aParser, DsjTable aTable, boolean aIsNDJson)
        throws IOException
    {
        if (handlerRow != null)
        {
            parseRowsRowBased(aParser, aTable, aIsNDJson);
        }
        else
        {
            parseRows(aParser, aTable, aIsNDJson);
        }
    }


    /**
     * Build the DsjTable from the parsed attributes. This method is called from
     * {@link #parseDataSet(JsonParser)} right before the first row data is parsed.
     *
     * @param aMetadata
     *            the metadata that was parsed before.
     * @param aColumns
     *            the columns that have been parsed before.
     * @return the DsjTable instance build form parsed metadata and columns.
     */
    protected DsjTable buildTable(Map<String, Object> aMetadata, DsjTableColumn[] aColumns)
    {
        long recordCount = 0;

        Object recs = aMetadata.get("records");
        if (recs instanceof Number num)
        {
            recordCount = num.longValue();
        }
        return DsjTable.builder()//
                .datasetJSONCreationDateTime(required(aMetadata, "datasetJSONCreationDateTime"))//
                .datasetJSONVersion(required(aMetadata, "datasetJSONVersion"))//
                .dbLastModifiedDateTime((String) aMetadata.get("dbLastModifiedDateTime"))//
                .fileOID((String) aMetadata.get("fileOID"))//
                .itemGroupOID(required(aMetadata, "itemGroupOID"))//
                .label(required(aMetadata, "label"))//
                .metaDataRef((String) aMetadata.get("metaDataRef"))//
                .metaDataVersionOID((String) aMetadata.get("metaDataVersionOID"))//
                .name(required(aMetadata, "name"))//
                .originator((String) aMetadata.get("originator"))//
                .records(recordCount)//
                .studyOID((String) aMetadata.get("studyOID"))//
                .sourceSystem(buildSourceSystem(aMetadata.get("sourceSystem")))//
                .columns(aColumns)//
                .build();
    }


    /**
     * Extract a required {@code String} field from a parsed JSON object. The Dataset-JSON spec
     * marks these fields as mandatory and the corresponding bean fields are {@code @NonNull}; a
     * missing field is a malformed document, surfaced here with a clear message rather than a
     * generic Lombok null-check failure at {@code build()} time.
     *
     * @param aMap
     *            the parsed JSON object.
     * @param aKey
     *            the required key.
     * @return the non-null string value.
     */
    private static String required(Map<String, Object> aMap, String aKey)
    {
        return Objects.requireNonNull((String) aMap.get(aKey),
                () -> "missing required Dataset-JSON field: " + aKey);
    }


    /**
     * Build the source system from parsed object.
     *
     * @param aObj
     *            the metadata object that was parsed as sourceSystem attribute.
     * @return the source system bean or null.
     */
    protected @Nullable DsjSourceSystem buildSourceSystem(@Nullable Object aObj)
    {
        if (aObj instanceof Map<?, ?> m)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> sm = (Map<String, Object>) m;
            return DsjSourceSystem.builder().name(required(sm, "name"))
                    .version(required(sm, "version")).build();
        }
        return null;
    }


    /**
     * Parse the actual parser value as Object.<br/>
     * The parser will not be moved in this method.
     *
     * @param aParser
     *            the parser that is located at a value token.
     * @return the value as Object.
     * @throws IOException
     *             in case or any parsing error.
     */
    protected @Nullable Object parseAsValue(JsonParser aParser) throws IOException
    {
        JsonToken token = aParser.currentToken();

        if (token == null)
        {
            throw new IOException("Unexpected end!");
        }

        return switch (token)
        {
        // possibly use String.intern already here?
        case VALUE_STRING -> aParser.getText();
        case VALUE_NUMBER_INT -> aParser.getLongValue();
        case VALUE_NUMBER_FLOAT -> aParser.getDoubleValue();
        case VALUE_TRUE -> true;
        case VALUE_FALSE -> false;
        case VALUE_NULL -> null;
        case START_OBJECT -> parseObject(aParser);
        case START_ARRAY -> parseArray(aParser);
        default -> throw new IOException("Unexpected token: " + token);
        };
    }


    /**
     * Parse the current token value from the parser and set it on the given buffer setter at the
     * specified index. The method dispatches based on the JSON token type.
     *
     * @param aParser
     *            the parser positioned at a value token.
     * @param aSetter
     *            the buffer setter to set the value on.
     * @param aIndex
     *            the buffer index to store the value at.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected void parseValueToSetter(JsonParser aParser, IColumnBufferSetter aSetter, int aIndex)
        throws IOException
    {
        JsonToken token = aParser.currentToken();

        if (token == null)
        {
            throw new IOException("Unexpected end!");
        }

        switch (token)
        {
        // possibly use String.intern already here?
        case VALUE_STRING -> aSetter.setStringValue(aIndex, aParser.getText());
        case VALUE_NUMBER_INT -> aSetter.setLongValue(aIndex, aParser.getLongValue());
        case VALUE_NUMBER_FLOAT -> aSetter.setDoubleValue(aIndex, aParser.getDoubleValue());
        case VALUE_TRUE -> aSetter.setValue(aIndex, true);
        case VALUE_FALSE -> aSetter.setValue(aIndex, false);
        case VALUE_NULL -> aSetter.setValue(aIndex, null);
        case START_OBJECT -> aSetter.setValue(aIndex, parseObject(aParser));
        case START_ARRAY -> aSetter.setValue(aIndex, parseArray(aParser));
        default -> throw new IOException("Unexpected token: " + token);
        }
    }


    /**
     * Parse an array. This method expects the given parser to be located at a
     * {@link JsonToken#START_ARRAY}.<br/>
     * The parser will be moved to the end of the array {@link JsonToken#END_ARRAY}.
     *
     * @param aParser
     *            the parser to parse the array from.
     * @return the parsed array as a {@link List} of objects.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected List<Object> parseArray(JsonParser aParser) throws IOException
    {
        JsonToken token = aParser.currentToken();
        if (token != JsonToken.START_ARRAY)
        {
            throw new IOException("Unexpected token: " + token);
        }
        List<Object> res = new ArrayList<>();
        while ((token = aParser.nextToken()) != null)
        {
            if (token == JsonToken.END_ARRAY)
            {
                return res;
            }
            res.add(parseAsValue(aParser));
        }
        throw new IOException("Unexpected end!");
    }


    /**
     * Parse an object. This method expects the given parser to be located at a
     * {@link JsonToken#START_OBJECT}.<br/>
     * The parser will be moved to the end of the object {@link JsonToken#END_OBJECT}.
     *
     * @param aParser
     *            the parser to parse the object from.
     * @return the parsed object.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected Map<String, Object> parseObject(JsonParser aParser) throws IOException
    {
        JsonToken token = aParser.currentToken();
        if (token != JsonToken.START_OBJECT)
        {
            throw new IOException("Invalid token: " + token);
        }

        Map<String, Object> obj = new HashMap<>();
        while ((token = aParser.nextToken()) != null)
        {
            switch (token)
            {
            case FIELD_NAME ->
            {
                String fieldName = aParser.currentName();

                aParser.nextToken();
                Object value = parseAsValue(aParser);

                obj.put(fieldName, value);
            }
            case END_OBJECT ->
            {
                return obj;
            }
            default -> throw new IOException("Unexpected token: " + token);
            }
        }

        throw new IOException("Unexpected end!");
    }


    /**
     * Parse a column. This method parses the column as object using
     * {@link #parseObject(JsonParser)} and maps the object into a {@link DsjTableColumn}.
     *
     * @param aParser
     *            the parser to parse the column from.
     * @param aIndex
     *            the index of the column to be parsed.
     * @return the column object.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected DsjTableColumn parseColumn(JsonParser aParser, int aIndex) throws IOException
    {
        Map<String, Object> colObj = parseObject(aParser);
        DsjTableColumn.DsjTableColumnBuilder b = DsjTableColumn.builder()//
                .index(aIndex)//
                .itemOID(required(colObj, "itemOID"))//
                .name(required(colObj, "name"))//
                .label(required(colObj, "label"))//
                .dataType(required(colObj, "dataType"))//
                .targetDataType((String) colObj.get("targetDataType"))//
                .displayFormat((String) colObj.get("displayFormat"))//
        ;

        if (colObj.containsKey("length"))
        {
            Number length = (Number) colObj.get("length");
            b.length(length.intValue());
        }
        if (colObj.containsKey("keySequence"))
        {
            Number keySequence = (Number) colObj.get("keySequence");
            b.keySequence(keySequence.intValue());
        }
        return b.build();
    }


    /**
     * Parse all columns to a list of {@link DsjTableColumn}.
     *
     * @param aParser
     *            the parser to parse the columns.
     * @return the list of columns.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected DsjTableColumn[] parseColumns(JsonParser aParser) throws IOException
    {
        JsonToken token = aParser.currentToken();
        if (token != JsonToken.START_ARRAY)
        {
            throw new IOException("Unexpected token: " + token);
        }

        List<DsjTableColumn> columns = new ArrayList<>();
        while ((token = aParser.nextToken()) != null)
        {
            if (token == JsonToken.END_ARRAY)
            {
                return columns.toArray(DsjTableColumn[]::new);
            }

            if (token == JsonToken.START_OBJECT)
            {
                columns.add(parseColumn(aParser, columns.size()));
            }
            else
            {
                throw new IOException("Unexpected token: " + token);
            }
        }

        throw new IOException("Unexpected end!");
    }


    /**
     * Parse the values of a single row into the given buffer.
     *
     * @param aParser
     *            the parser to parse the row values from.
     * @param aRowIndex
     *            the index of the row to be parsed.
     * @param aArrayIndex
     *            the array index in aBuffer to store the values in.
     * @param aSetters
     *            the column buffer setters to store the values of the row in. These setters write
     *            into a buffer arranged as a buffer of columns.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected void parseRow(JsonParser aParser, long aRowIndex, int aArrayIndex,
            IColumnBufferSetter[] aSetters)
        throws IOException
    {
        int colIdx = -1;
        JsonToken token;
        while ((token = aParser.nextToken()) != null)
        {
            colIdx++;
            if (token == JsonToken.END_ARRAY)
            {
                if (colIdx < aSetters.length)
                {
                    LOGGER.log(Level.WARNING, "Not enough values for row {0}.", aArrayIndex);
                }
                return;
            }

            if (colIdx >= aSetters.length)
            {
                throw new IOException("Too many values in row " + aArrayIndex + " expected "
                        + aSetters.length + " found: " + (colIdx + 1));
            }

            parseValueToSetter(aParser, aSetters[colIdx], aArrayIndex);
        }
    }


    /**
     * Parse the values of a single row into an {@code Object[]} array. The values are stored as
     * their natural JSON types: {@link String}, {@link Long}, {@link Double}, {@link Boolean}, or
     * {@code null}.
     *
     * @param aParser
     *            the parser to parse the row values from.
     * @param aColumnCount
     *            the expected number of columns.
     * @return an array of row values.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected @Nullable Object[] parseRowToArray(JsonParser aParser, int aColumnCount)
        throws IOException
    {
        @Nullable
        Object[] values = new Object[aColumnCount];
        int colIdx = -1;
        JsonToken token;
        while ((token = aParser.nextToken()) != null)
        {
            colIdx++;
            if (token == JsonToken.END_ARRAY)
            {
                if (colIdx < aColumnCount)
                {
                    LOGGER.log(Level.WARNING, "Not enough values for row (expected {0}, got {1}).",
                            aColumnCount, colIdx);
                }
                return values;
            }

            if (colIdx >= aColumnCount)
            {
                throw new IOException("Too many values in row, expected " + aColumnCount
                        + " found: " + (colIdx + 1));
            }

            values[colIdx] = parseValueToObject(aParser);
        }
        return values;
    }


    /**
     * Parse the current token value from the parser and return it as an {@link Object}. The method
     * dispatches based on the JSON token type and returns the natural Java type for each JSON type.
     *
     * @param aParser
     *            the parser positioned at a value token.
     * @return the parsed value as {@link String}, {@link Long}, {@link Double}, {@link Boolean},
     *         {@code null}, {@link Map}, or {@link List}.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected @Nullable Object parseValueToObject(JsonParser aParser) throws IOException
    {
        JsonToken token = aParser.currentToken();

        if (token == null)
        {
            throw new IOException("Unexpected end!");
        }

        return switch (token)
        {
        case VALUE_STRING -> aParser.getText();
        case VALUE_NUMBER_INT -> aParser.getLongValue();
        case VALUE_NUMBER_FLOAT -> aParser.getDoubleValue();
        case VALUE_TRUE -> true;
        case VALUE_FALSE -> false;
        case VALUE_NULL -> null;
        case START_OBJECT -> parseObject(aParser);
        case START_ARRAY -> parseArray(aParser);
        default -> throw new IOException("Unexpected token: " + token);
        };
    }


    /**
     * Parse rows in row-based mode, delivering each row as an {@code Object[]} to the
     * {@link RowHandler}. This method is used when {@link #handlerRow} is set instead of
     * {@link #handlerRows}.
     *
     * @param aParser
     *            the parser to parse the rows from.
     * @param aTable
     *            the table that is currently parsed.
     * @param aIsNDJson
     *            {@code true} if the rows are in newline delimited JSON format.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected void parseRowsRowBased(JsonParser aParser, DsjTable aTable, boolean aIsNDJson)
        throws IOException
    {
        JsonToken token = aParser.getCurrentToken();
        if (token != JsonToken.START_ARRAY)
        {
            throw new IOException("Unexpected token: " + token);
        }

        int columnCount = aTable.getColumnCount();
        long rowIndex = -1;

        if (!aIsNDJson)
        {
            token = aParser.nextToken();
        }

        do
        {
            switch (token)
            {
            case START_ARRAY ->
            {
                rowIndex++;
                @Nullable
                Object[] values = parseRowToArray(aParser, columnCount);

                if (handlerRow != null)
                {
                    int res = handlerRow.nextRow(aTable, rowIndex, values);
                    if (res != 0)
                    {
                        throw new IOException("User aborted!");
                    }
                }
            }
            case END_ARRAY ->
            {
                if (aIsNDJson)
                {
                    throw new IOException("Unexpected token: " + token);
                }
                return;
            }
            default -> throw new IOException("Unexpected token: " + token);
            }
        }
        while ((token = aParser.nextToken()) != null);

        if (!aIsNDJson)
        {
            throw new IOException("Unexpected end after row " + rowIndex);
        }
    }


    /**
     * Generate column buffers for the given table. Each column gets a buffer matching its data
     * type: {@link ColumnBufferDouble} for float/double <em>and integer</em> (J2: integer-declared
     * columns read into a floating-point buffer so a non-conformant decimal value is not truncated
     * at parse time — paired with the providers' {@code getTypeFor(INTEGER) -> DOUBLE}), and
     * {@link ColumnBufferString} for everything else.
     *
     * @param aTable
     *            the table to generate buffers for.
     * @return an array of column buffers, one per column.
     */
    protected IColumnBuffer[] generateBuffers(DsjTable aTable)
    {
        int colCount = aTable.getColumnCount();
        IColumnBuffer[] setters = new IColumnBuffer[colCount];
        for (int i = 0; i < colCount; i++)
        {
            DsjTableColumn col = aTable.getColumn(i);
            ColumnDataType t = ColumnDataType.getFor(col.getDataType());
            switch (t)
            {
            case FLOAT, DOUBLE, INTEGER:
                setters[i] = new ColumnBufferDouble(rowSliceSize);
                break;
            case STRING:
            default:
                setters[i] = new ColumnBufferString(rowSliceSize);
                break;
            }
        }
        return setters;
    }


    protected void parseRows(JsonParser aParser, DsjTable aTable, boolean aIsNDJson)
        throws IOException
    {
        JsonToken token = aParser.getCurrentToken();
        if (token != JsonToken.START_ARRAY)
        {
            throw new IOException("Unexpected token: " + token);
        }
        // generate data[<column>][<rows>]

        IColumnBuffer[][] buffers = new IColumnBuffer[2][];
        buffers[0] = generateBuffers(aTable);
        buffers[1] = generateBuffers(aTable);
        int bufIdx = 0;

        long rowIndex = -1;
        long firstRow = -1;

        if (!aIsNDJson)
        {
            // not ND --> we move to next token
            token = aParser.nextToken();
        }

        do
        {
            switch (token)
            {
            case START_ARRAY ->
            {
                rowIndex++;
                int arrIdx = (int) (rowIndex % rowSliceSize);
                if (arrIdx == 0)
                {
                    if (handlerRows != null && firstRow >= 0)
                    {
                        int res = handlerRows.nextRowsAvail(aTable, firstRow,
                                (int) (rowIndex - firstRow), buffers[bufIdx]);
                        if (res != 0)
                        {
                            throw new IOException("User aborted!");
                        }
                        bufIdx = (bufIdx + 1) % 2;
                    }
                    firstRow = rowIndex;
                }

                parseRow(aParser, rowIndex, arrIdx, buffers[bufIdx]);
            }
            case END_ARRAY ->
            {
                if (aIsNDJson)
                {
                    throw new IOException("Unexpected token: " + token);
                }

                // end of internal array.
                if (handlerRows != null && firstRow >= 0)
                {
                    int res = handlerRows.nextRowsAvail(aTable, firstRow,
                            (int) (rowIndex - firstRow) + 1, buffers[bufIdx]);
                    if (res != 0)
                    {
                        throw new IOException("User aborted!");
                    }
                }
                return;
            }
            default -> throw new IOException("Unexpected token: " + token);
            }
        }
        while ((token = aParser.nextToken()) != null);

        if (aIsNDJson)
        {
            if (handlerRows != null && firstRow >= 0)
            {
                handlerRows.nextRowsAvail(aTable, firstRow, (int) (rowIndex - firstRow) + 1,
                        buffers[bufIdx]);
            }
        }
        else
        {
            throw new IOException("Unexpected end after row " + rowIndex);
        }
    }

}
