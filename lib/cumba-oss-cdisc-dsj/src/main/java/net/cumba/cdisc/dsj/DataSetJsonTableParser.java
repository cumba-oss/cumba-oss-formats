package net.cumba.cdisc.dsj;

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
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
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
 * <h2>Empty datasets — deliberate read/write asymmetry</h2> A Dataset-JSON dataset always carries a
 * {@code rows} member; the companion {@code DsjTableWriter} therefore emits an explicitly empty
 * {@code "rows": []} for a zero-record table (strict write). This parser additionally
 * <em>tolerates</em> a document without any {@code rows} member and delivers it as a valid empty
 * dataset: the metadata handler fires with the parsed columns and zero row callbacks follow
 * (lenient read). That asymmetry is deliberate — do not "simplify" the reader by removing the
 * tolerance, and do not weaken the writer to match it. The lenient path is guarded by the
 * declared-vs-parsed record check: a document declaring {@code "records" > 0} whose rows are
 * missing (or truncated) fails with an {@link IOException} instead of parsing to a silent nothing.
 * <p>
 * The tolerance covers {@code rows} and nothing else. {@code columns} is a required member and this
 * parser additionally needs it <em>before</em> the row data, since the row shape decides which
 * buffer each value goes into; a document without it fails with an {@link IOException} naming the
 * missing member.
 * </p>
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
        // Not aURL.openStream(): for a jar: URL (a file inside a zip) that leaves the archive in
        // JarURLConnection's JVM-wide cache after close -- locked on Windows, and a replaced zip
        // re-read with its OLD content. The rest of the stack uses URIHelper.openStream for this;
        // this module deliberately has no net.cumba dependency, so the two lines are inline
        // (PLAN-jar-url-stream-cache, owner Q1).
        URLConnection connection = aURL.openConnection();
        connection.setUseCaches(false);
        try (InputStream in = connection.getInputStream())
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
        // readNBytes, not read(byte[]): a single read() is contractually allowed to return fewer
        // bytes than requested whenever the source has not delivered them yet (a socket-backed
        // URL stream is the realistic case), which would reject a perfectly valid document as
        // "too short". readNBytes only comes up short at a genuine end-of-stream.
        int bytesRead = bin.readNBytes(header, 0, 2);
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
        long parsedRows = 0;
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
                    table = buildTable(metadata, requireColumnsBeforeRows(columns));
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

                    parsedRows = dispatchParseRows(aParser, table, false);
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

                table = buildTable(metadata, requireColumnsBeforeRows(columns));
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
                parsedRows = dispatchParseRows(aParser, table, true);
            }
        }

        if (!rowsParsed)
        {
            // A document with metadata and columns but no "rows" member is a legitimate empty
            // dataset (see the class javadoc on the deliberate read/write asymmetry): build the
            // table and fire the metadata handler with zero rows instead of returning silently.
            // The record-count verification below still guards this path — a document that
            // declares records > 0 but lost its rows fails loudly.
            if (columns == null)
            {
                throw new IOException("missing required Dataset-JSON field: columns");
            }
            table = buildTable(metadata, columns);
            if (handlerMetadata != null)
            {
                int res = handlerMetadata.metadata(table);
                if (res != 0)
                {
                    throw new IOException("User aborted!");
                }
            }
        }
        // Both branches above assign table (the loop branch via rowsParsed); assert for the
        // flow analysis.
        verifyRecordCount(Objects.requireNonNull(table, "table"), parsedRows);
    }


    /**
     * Reject a document whose row data starts before any {@code columns} member has been seen.
     *
     * <p>
     * {@code columns} is a required Dataset-JSON member, and this parser additionally needs it
     * <em>before</em> {@code rows} because the row shape decides which column buffer each value
     * goes into. This used to log a warning and continue with a zero-length column array, which
     * could not work: {@code DsjTable.DsjTableBuilder.columns()} treats an empty array as "unset",
     * so {@code build()} then failed the {@code @NonNull} check and a NullPointerException escaped
     * a method declared to throw {@link IOException} — naming a Lombok field rather than the
     * missing member. A caller that catches {@link IOException} to report a bad file never saw it.
     * </p>
     *
     * @param aColumns
     *            the columns parsed so far, or {@code null} if none were seen.
     * @return the columns, never {@code null}.
     * @throws IOException
     *             if no {@code columns} member preceded the row data.
     */
    private static DsjTableColumn[] requireColumnsBeforeRows(DsjTableColumn @Nullable [] aColumns)
        throws IOException
    {
        if (aColumns == null)
        {
            throw new IOException(
                    "missing required Dataset-JSON field: columns; it must be declared before "
                            + "\"rows\"");
        }
        return aColumns;
    }


    /**
     * Verify that the declared {@code records} count matches the number of rows actually parsed. A
     * declared count of {@code -1} means "unknown" and is never checked. A mismatch means the
     * document is corrupt (typically a truncated upload whose surviving tail is well-formed) and
     * must not be delivered as a complete dataset.
     *
     * @param aTable
     *            the parsed table carrying the declared {@code records} value.
     * @param aParsedRows
     *            the number of rows actually delivered.
     * @throws IOException
     *             if a non-negative declared count disagrees with the parsed row count.
     */
    protected static void verifyRecordCount(DsjTable aTable, long aParsedRows) throws IOException
    {
        long declared = aTable.getRecords();
        if (declared >= 0 && declared != aParsedRows)
        {
            throw new IOException(
                    "Declared \"records\" (%d) does not match the number of rows parsed (%d)."
                            .formatted(declared, aParsedRows));
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
     * @return the number of rows parsed.
     * @throws IOException
     *             in case of any parsing error.
     */
    private long dispatchParseRows(JsonParser aParser, DsjTable aTable, boolean aIsNDJson)
        throws IOException
    {
        if (handlerRow != null)
        {
            return parseRowsRowBased(aParser, aTable, aIsNDJson);
        }
        return parseRows(aParser, aTable, aIsNDJson);
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
        // -1 is the documented "unknown" sentinel and DsjTable defaults to it. An absent or
        // malformed "records" member must reach the model as "unknown", never as a fabricated
        // claim of 0 records — that part is unchanged and is why the sentinel still exists.
        //
        // ⚠⚠ Corrected 2026-09-14 (Q26): this comment used to add "and DsjTableWriter keys every
        // rowCount decision on it". That is now the opposite of the truth — the writer REFUSES
        // -1, because `records` is a Required member of the spec and omitting it wrote a knowingly
        // non-conformant file. So a table parsed from a file that lacks `records` can no longer be
        // handed straight back to DsjTableWriter: the caller must supply the real count.
        // ⚠ Audited when the ruling landed: all three production DsjTable.builder() sites (this
        // parser, its OSS twin, and DsjTableExporter) set .records(...), and the exporter derives
        // it from the live table, so no shipped path is affected. What is affected is a direct
        // parser→writer pass-through of a non-conformant file — including one this product itself
        // wrote before the writer became strict.
        long recordCount = -1;

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
                    // Fail loud (symmetric with the too-many branch below): a short row would
                    // otherwise leave the unwritten columns holding stale values from a reused
                    // slice buffer, indistinguishable from real data.
                    String msg = MessageFormat.format(
                            "Not enough values in row {0}, expected: {1} found: {2}.", aRowIndex,
                            aSetters.length, colIdx);
                    throw new IOException(msg);
                }
                return;
            }

            if (colIdx >= aSetters.length)
            {
                String msg = MessageFormat.format(
                        "Too many values in row {0}, expected: {1} found: {2}.", aRowIndex,
                        aSetters.length, colIdx + 1);
                throw new IOException(msg);
            }

            parseValueToSetter(aParser, aSetters[colIdx], aArrayIndex);
        }

        // Unreachable through any entry point of this class: Jackson raises JsonEOFException when
        // input ends inside an array context rather than returning null from nextToken(). Kept as
        // a hard stop so that a token source which DOES end silently can never hand a half-filled
        // row to the slice handler, where the unwritten columns still hold the previous slice's
        // values and are indistinguishable from real data.
        throw new IOException("Unexpected end of input inside row " + aRowIndex + ".");
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
                    String msg = MessageFormat.format(
                            "Not enough values in row, expected: {0} found: {1}.", aColumnCount,
                            colIdx);
                    throw new IOException(msg);
                }
                return values;
            }

            if (colIdx >= aColumnCount)
            {
                String msg = MessageFormat.format(
                        "Too many values in row, expected: {0} found: {1}.", aColumnCount,
                        colIdx + 1);
                throw new IOException(msg);
            }

            values[colIdx] = parseValueToObject(aParser);
        }

        // See parseRow: unreachable with Jackson, and a hard stop rather than a silently short row.
        throw new IOException("Unexpected end of input inside a row.");
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
     * @return the number of rows parsed.
     * @throws IOException
     *             in case of any parsing error.
     */
    protected long parseRowsRowBased(JsonParser aParser, DsjTable aTable, boolean aIsNDJson)
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
                return rowIndex + 1;
            }
            default -> throw new IOException("Unexpected token: " + token);
            }
        }
        while ((token = aParser.nextToken()) != null);

        if (!aIsNDJson)
        {
            throw new IOException("Unexpected end after row " + rowIndex);
        }
        return rowIndex + 1;
    }


    /**
     * Generate column buffers for the given table. Each column gets a buffer matching its data
     * type: {@link ColumnBufferDouble} for float/double <em>and integer</em> (J2: integer columns
     * are read as floating point so a non-conformant decimal is not truncated), and
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
                // J2: read integer-declared columns into a floating-point buffer so a
                // non-conformant decimal value is not truncated at parse time (paired with the
                // providers' getTypeFor(INTEGER) -> DOUBLE).
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


    protected long parseRows(JsonParser aParser, DsjTable aTable, boolean aIsNDJson)
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
                return rowIndex + 1;
            }
            default -> throw new IOException("Unexpected token: " + token);
            }
        }
        while ((token = aParser.nextToken()) != null);

        if (aIsNDJson)
        {
            if (handlerRows != null && firstRow >= 0)
            {
                // The result is checked here exactly as it is at every other dispatch site: the
                // handler contract says non-zero aborts, and a final slice that silently ignored
                // it would report a clean parse for a run the consumer had rejected.
                int res = handlerRows.nextRowsAvail(aTable, firstRow,
                        (int) (rowIndex - firstRow) + 1, buffers[bufIdx]);
                if (res != 0)
                {
                    throw new IOException("User aborted!");
                }
            }
        }
        else
        {
            throw new IOException("Unexpected end after row " + rowIndex);
        }
        return rowIndex + 1;
    }

}
