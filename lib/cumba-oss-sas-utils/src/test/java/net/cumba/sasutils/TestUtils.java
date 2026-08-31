package net.cumba.sasutils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TestUtils
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    public abstract Library getLibrary(File file) throws IOException;


    public Library testLibraryToCsv(File file) throws Exception
    {
        return testLibraryToCsv(file, null, null);
    }


    public Library testLibraryToCsv(File file, Long expectedRows, Integer expectedColumns)
        throws Exception
    {
        try
        {
            Library library = getLibrary(file);
            testLibraryToCsv(library, file, expectedRows, expectedColumns);
            return library;
        }
        catch (Exception e)
        {
            LOGGER.error("", e);
            throw e;
        }

    }


    public void testLibraryToCsv(Library library, File file, Long expectedRows,
            Integer expectedColumns)
        throws IOException
    {
        for (Dataset dataset : library.getDatasets())
        {
            testDatasetToCsv(dataset, file, expectedRows, expectedColumns);
        }
    }


    public void testLibrarySingleDataset(Library library, File file, Long expectedRows,
            Integer expectedColumns, boolean csv)
        throws IOException
    {
        Dataset dataset = library.getDatasets().get(0);
        try
        {
            Long rc = dataset.getRowCount();
            if (rc != null)
            {
                // getRowCount() is the total physical row count (live + deleted); the fixture
                // filename encodes the live (non-deleted) count, so subtract deleted observations
                // where the dataset exposes them.
                long live = rc;
                if (dataset instanceof net.cumba.sasutils.bdat.DatasetBdat bdat)
                {
                    Long deleted = bdat.getDeletedObservationCount();
                    if (deleted != null)
                    {
                        live = rc - deleted;
                    }
                }
                Assertions.assertEquals(expectedRows, live);
            }
        }
        catch (UnsupportedOperationException _)
        {
            // Row count is optional; some providers don't expose it without iterating.
        }

        if (csv)
        {
            testDatasetToCsv(dataset, file, expectedRows, expectedColumns);
        }
    }


    public void testDatasetToCsv(Dataset dataset, File file, Long expectedRows,
            Integer expectedColumns)
        throws IOException
    {

        LOGGER.info("file: {} member: {}", file, dataset);

        if (expectedColumns != null)
        {
            Assertions.assertEquals(expectedColumns, dataset.getVariables().size(),
                    "Expected " + expectedColumns + " columns but only found "
                            + dataset.getVariables().size() + " in metadata");
        }

        File outFile = new File(System.getProperty("projectBasedir"),
                "target/" + file.getName() + "_" + dataset.getName().trim() + "_out.csv");

        LOGGER.info("outFile: {}", outFile);

        MutableLong rows = new MutableLong(0);

        try (PrintWriter pw = new PrintWriter(outFile, StandardCharsets.UTF_8))
        {
            // header
            pw.println(dataset.getVariables().stream().map(Variable::getName)
                    .collect(Collectors.joining(";")));

            LOGGER.info("iterating observations");

            dataset.streamObservations(file).forEach(obs ->
            {
                List<Object> vals = new ArrayList<>(obs.getFormattedValues());

                if (expectedColumns != null)
                {
                    Assertions.assertEquals(expectedColumns, vals.size(),
                            "Expected " + expectedColumns + " columns but only found " + vals.size()
                                    + " at row " + rows.longValue());
                }
                for (int i = 0; i < vals.size(); i++)
                {
                    Object val = vals.get(i);
                    if (val instanceof Double d)
                    {
                        String s = d.isNaN() ? "NaN"
                                : new BigDecimal(d.toString()).stripTrailingZeros().toPlainString();
                        vals.set(i, s);
                    }
                }

                pw.println(vals.stream().map(v -> v == null ? "" : v.toString())
                        .collect(Collectors.joining(";")));
                rows.increment();
            });
        }

        if (expectedRows != null)
        {
            Assertions.assertEquals(expectedRows, rows.longValue(),
                    "Expected " + expectedRows + " rows but found " + rows.longValue());
        }

    }


    public void test(TestFile test) throws IOException
    {
        test(test, true);
    }


    public void test(TestFile test, boolean csv) throws IOException
    {
        Library library = getLibrary(test.file);
        testLibrarySingleDataset(library, test.file, test.rows, test.columns, csv);
    }

}
