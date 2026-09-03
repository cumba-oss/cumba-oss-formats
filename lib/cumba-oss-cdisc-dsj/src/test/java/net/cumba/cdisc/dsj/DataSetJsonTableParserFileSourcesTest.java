package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that exercise the File, Path and URL entry points of
 * {@link DataSetJsonTableParser#parseDataSet(java.io.File)},
 * {@link DataSetJsonTableParser#parseDataSet(java.nio.file.Path)} and
 * {@link DataSetJsonTableParser#parseDataSet(java.net.URL)}.
 */
class DataSetJsonTableParserFileSourcesTest
{

    private static final String SIMPLE_JSON = """
            {"datasetJSONCreationDateTime":"2025-01-01T00:00:00",
             "datasetJSONVersion":"1.1.0",
             "itemGroupOID":"IG.DM",
             "name":"DM",
             "label":"Demographics",
             "records":1,
             "columns":[{"itemOID":"IT.V","name":"V","label":"V","dataType":"string"}],
             "rows":[["x"]]}
            """;

    @Test
    void testParseDataSetFromFile(@TempDir Path tmp) throws IOException
    {
        File f = tmp.resolve("simple.json").toFile();
        Files.write(f.toPath(), SIMPLE_JSON.getBytes(StandardCharsets.UTF_8));

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        AtomicReference<DsjTable> table = new AtomicReference<>();
        AtomicInteger rows = new AtomicInteger();

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, n, _) ->
        {
            rows.addAndGet(n);
            return 0;
        });

        parser.parseDataSet(f);

        assertNotNull(table.get());
        assertEquals("DM", table.get().getName());
        assertEquals(1, rows.get());
    }


    @Test
    void testParseDataSetFromPath(@TempDir Path tmp) throws IOException
    {
        Path p = tmp.resolve("simple-path.json");
        Files.writeString(p, SIMPLE_JSON);

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        AtomicReference<DsjTable> table = new AtomicReference<>();

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(p);

        assertNotNull(table.get());
        assertEquals("DM", table.get().getName());
    }


    @Test
    void testParseDataSetFromUrl(@TempDir Path tmp) throws IOException
    {
        Path p = tmp.resolve("simple-url.json");
        Files.writeString(p, SIMPLE_JSON);
        URL url = p.toUri().toURL();

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        AtomicReference<DsjTable> table = new AtomicReference<>();

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(url);

        assertNotNull(table.get());
        assertEquals("DM", table.get().getName());
    }


    @Test
    void testParseDataSetFromGzipFile(@TempDir Path tmp) throws IOException
    {
        Path p = tmp.resolve("simple.json.gz");
        try (var out = Files.newOutputStream(p); GZIPOutputStream gz = new GZIPOutputStream(out))
        {
            gz.write(SIMPLE_JSON.getBytes(StandardCharsets.UTF_8));
        }

        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        AtomicReference<DsjTable> table = new AtomicReference<>();

        parser.setHandlerMetadata(t ->
        {
            table.set(t);
            return 0;
        });
        parser.setHandlerRows((_, _, _, _) -> 0);

        parser.parseDataSet(p.toFile());

        assertNotNull(table.get());
    }


    @Test
    void testParseDataSetFromPathOfMissingFile(@TempDir Path tmp)
    {
        Path missing = tmp.resolve("does-not-exist.json");
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        assertThrows(IOException.class, () -> parser.parseDataSet(missing));
    }

}
