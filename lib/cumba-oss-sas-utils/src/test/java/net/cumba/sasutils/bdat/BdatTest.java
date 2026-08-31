package net.cumba.sasutils.bdat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.cumba.sasutils.Library;
import net.cumba.sasutils.TestFile;
import net.cumba.sasutils.TestUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BdatTest extends TestUtils
{

    private static final Logger LOGGER = LoggerFactory.getLogger(BdatTest.class);

    ParserBdat parser = new ParserBdat();

    @Test
    void testNumeric1() throws IOException
    {
        test(new TestFile(findFile("numeric1")));
    }


    @Test
    void testFolder() throws IOException
    { // NOSONAR S2699 — exploratory parser test, observation-only
        File lib = new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdatlib");
        LibraryBdat library = parser.parseLibrary(lib);

        LOGGER.info("library: {}", library);
        LOGGER.info("created: {}", library.getCreated());
        LOGGER.info("modified: {}", library.getModified());

    }


    @Test
    void testDoubles1() throws IOException
    {
        test(new TestFile(findFile("doubles")));
    }


    @Test
    void testDoubles2() throws IOException
    {
        test(new TestFile(findFile("doubles2")));
    }


    @Test
    void testNumeric64BigEndian() throws IOException
    {
        test(new TestFile(findFile("64_numeric4")));
    }


    @Test
    void testExtend1() throws IOException
    {
        test(new TestFile(findFile("extend_no")));
    }


    @Test
    void testExtend2() throws IOException
    {
        test(new TestFile(findFile("extend_yes")));
    }


    @Test
    void testNumeric2() throws IOException
    {
        test(new TestFile(findFile("numeric2")), false);
    }


    @Test
    void testNumeric3() throws IOException
    {
        test(new TestFile(findFile("numeric3")));
    }


    @Test
    void testPercents() throws IOException
    {
        test(new TestFile(findFile("percents")), true);
    }


    @Test
    void fileWithLabel() throws IOException
    {
        test(new TestFile(findFile("file_with_label")));
    }


    @Test
    void testCompDeleted() throws IOException
    {
        test(new TestFile(findFile("comp_deleted")));
    }


    @Test
    void testDeleted() throws IOException
    {
        test(new TestFile(findFile("data_page_with_deleted")));
    }


    @Test
    void testMixed1() throws IOException
    {
        test(new TestFile(findFile("mixed1")));
    }


    @Test
    void testMixed3() throws IOException
    {
        test(new TestFile(findFile("mixed3")));
    }


    @Test
    void testMixed4() throws IOException
    {
        test(new TestFile(findFile("mixed4")), false);
    }


    @Test
    void testMixedMisc() throws IOException
    {
        test(new TestFile(findFile("mix_data_misc")), false);
    }


    @Test
    void testMixedFormats() throws IOException
    {
        test(new TestFile(findFile("mixed_formats")));
    }


    @Test
    void testMixedFormats2() throws IOException
    {
        test(new TestFile(findFile("mixed_formats2")));
    }


    @Test
    void testMixed2() throws IOException
    {
        test(new TestFile(findFile("mixed2")), true);
    }


    @Test
    void testDatasetWithTime() throws IOException
    {
        test(new TestFile(findFile("time")));
    }


    @Test
    void testDatasetNames() throws IOException
    { // NOSONAR S2699 — exploratory parser test, observation-only
        List<File> files = findFiles("");
        List<String> datasetnames = new ArrayList<String>();
        for (File file : files)
        {
            try
            {
                Library library = getLibrary(file);
                library.getDatasets().forEach(d ->
                {
                    datasetnames.add(file.getName() + " = " + d.getName());
                });
            }
            catch (NotImplementedException e)
            {
                LOGGER.warn("error on " + file, e);
            }
        }
        LOGGER.info("names: {}", datasetnames);
    }


    @Override
    public Library getLibrary(File file) throws IOException
    {
        return parser.parseLibrary(file);
    }


    public File findFile(String prefix)
    {
        List<File> files = findFiles(prefix);
        return files.get(0);
    }


    public List<File> findFiles(String prefix)
    {
        List<File> files = new ArrayList<>();
        File folder = new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdat");
        if (folder.exists() && folder.listFiles() != null)
        {
            for (File file : folder.listFiles())
            {
                if (file.getName().startsWith(prefix))
                {
                    files.add(file);
                }
            }
        }
        folder = new File(System.getProperty("projectBasedir"),
                "src/ignore/resources/net/cumba/sasutils/bdat");
        if (folder.exists() && folder.listFiles() != null)
        {
            for (File file : folder.listFiles())
            {
                if (file.getName().startsWith(prefix))
                {
                    files.add(file);
                }
            }
        }
        if (files.size() == 0)
        {
            throw new IllegalArgumentException("File with prefix: " + prefix + " not found");
        }
        return files;
    }

}
