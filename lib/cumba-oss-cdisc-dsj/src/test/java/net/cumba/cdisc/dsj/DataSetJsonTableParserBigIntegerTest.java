package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An integer token that does not fit a long is read as a {@link BigInteger} instead of failing the
 * whole parse (PLAN-oss-dsj-malformed-boolean-cell): {@code getLongValue()} threw for it, so one
 * oversized cell made the dataset unreadable. What it means for a column is the caller's decision.
 */
class DataSetJsonTableParserBigIntegerTest
{

    @Test
    void anIntegerBeyondLongIsABigIntegerNotAParseFailure() throws IOException
    {
        String json = """
                {"datasetJSONCreationDateTime":"2026-09-25T00:00:00","datasetJSONVersion":"1.1.0",
                 "itemGroupOID":"IG.BIG","name":"BIG","label":"Big","records":3,
                 "columns":[{"itemOID":"IT.V","name":"V","label":"V","dataType":"string"}],
                 "rows":[[99999999999999999999],[9223372036854775807],[-9223372036854775809]]}
                """;
        List<Object> values = new ArrayList<>();
        DataSetJsonTableParser parser = new DataSetJsonTableParser();
        parser.setHandlerMetadata(_ -> 0);
        parser.setHandlerRow((_, _, aValues) ->
        {
            values.add(aValues[0]);
            return 0;
        });

        parser.parseDataSet(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of(new BigInteger("99999999999999999999"), Long.MAX_VALUE,
                new BigInteger("-9223372036854775809")), values);
    }
}
