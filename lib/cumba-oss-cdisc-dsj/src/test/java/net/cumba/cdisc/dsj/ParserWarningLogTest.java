package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

/**
 * Pins the parser's diagnostic warnings.
 *
 * <p>
 * Each warning here reports a document that could not be honoured as written. Nothing else in the
 * suite observed them, so every one of these {@code LOGGER.log} calls could be deleted with the
 * whole suite still green — a reader of a malformed file would then get a degraded result with no
 * trace at all of what was dropped.
 * </p>
 */
class ParserWarningLogTest
{

    private static InputStream bytes(String aJson)
    {
        return new ByteArrayInputStream(aJson.getBytes(StandardCharsets.UTF_8));
    }


    @Test
    void testAttributeAfterRowsWarns() throws IOException
    {
        // An attribute placed after "rows" is silently dropped from the metadata; the warning is
        // what tells the caller that the file carried something this parser did not deliver.
        String json = "{\"datasetJSONCreationDateTime\":\"2025-01-01T00:00:00\","
                + "\"datasetJSONVersion\":\"1.1.0\",\"itemGroupOID\":\"IG.T\",\"name\":\"T\","
                + "\"label\":\"L\","
                + "\"columns\":[{\"itemOID\":\"IT.X\",\"name\":\"X\",\"label\":\"X\","
                + "\"dataType\":\"string\"}],\"rows\":[[\"a\"]],\"studyOID\":\"S1\"}";

        DataSetJsonTableParser p = new DataSetJsonTableParser();
        AtomicReference<DsjTable> tref = new AtomicReference<>();
        p.setHandlerMetadata(t ->
        {
            tref.set(t);
            return 0;
        });
        p.setHandlerRows((_, _, _, _) -> 0);
        try (LogCapture log = LogCapture.on(DataSetJsonTableParser.class))
        {
            p.parseDataSet(bytes(json));
            assertTrue(log.logged(Level.WARNING, "after rows. This is ignored."), log.dump());
        }
        assertNotNull(tref.get());
        // The dropped value really is dropped — the warning is not cosmetic.
        assertNull(tref.get().getStudyOID());
    }
}
