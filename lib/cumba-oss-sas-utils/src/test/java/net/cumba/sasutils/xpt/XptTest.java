package net.cumba.sasutils.xpt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.cumba.sasutils.Dataset;
import net.cumba.sasutils.Library;
import net.cumba.sasutils.TestFile;
import net.cumba.sasutils.TestUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class XptTest extends TestUtils
{

    private static final Logger LOGGER = LoggerFactory.getLogger(XptTest.class);

    File folder = new File(System.getProperty("projectBasedir"),
            "src/test/resources/net/cumba/sasutils/xpt");

    File folder2 = new File(System.getProperty("projectBasedir"),
            "src/ignore/resources/net/cumba/sasutils/xpt");

    File folder3 = new File(System.getProperty("projectBasedir"),
            "src/test/resources/net/cumba/sasutils/xpt/pfizer");

    File folder4 = new File(System.getProperty("projectBasedir"),
            "src/ignore/resources/net/cumba/sasutils/xpt/pfizer");

    List<File> folders = Arrays.asList(folder, folder2, folder3, folder4);

    ParserXpt parser = new ParserXpt();

    @Test
    void testTwoTables() throws Exception
    {

        File file = findFile("twotables");

        Library library = getLibrary(file);

        Dataset air = library.getDataset("AIR").get();
        testDatasetToCsv(air, file, 144l, 2);

        Dataset class1 = library.getDataset("CLASS1").get();
        testDatasetToCsv(class1, file, 19l, 5);

    }


    @Test
    void testSmall() throws Exception
    {
        test(new TestFile(findFile("small")));
    }


    @Test
    void testNumeric() throws Exception
    {
        test(new TestFile(findFile("numeric")));
    }


    @Test
    void testMixed() throws Exception
    {
        test(new TestFile(findFile("mixed")));
    }


    @Test
    @Disabled("Takes to long to run on regular basis")
    void testExportAll() throws Exception
    {
        List<File> files = getAllFiles();
        for (File file : files)
        {
            test(new TestFile(file));
        }
    }


    @Test
    @Disabled("Takes to long to run on regular basis")
    void testAll() throws Exception
    { // NOSONAR S2699 — exploratory parser test, observation-only
        Set<Object> fjid = new HashSet<>();
        Set<Object> infostring = new HashSet<>();
        Set<Object> namehash = new HashSet<>();
        Set<Object> type = new HashSet<>();

        List<File> files = getAllFiles();

        LOGGER.info("files: {}", files);

        for (File file : files)
        {
            LibraryXpt lib = (LibraryXpt) getLibrary(file);
            for (DatasetXpt dataset : lib.getDatasets())
            {
                type.add(dataset.getType());
                for (VariableXpt variable : dataset.getVariables())
                {
                    fjid.add(variable.getFormatJustifyId());
                    infostring.add(variable.getInformatTypeString());
                    infostring.add(variable.getFormatTypeString());
                    namehash.add(variable.getNameHash());
                }
            }
        }

        LOGGER.info("format justify ids: {}", fjid);
        LOGGER.info("format string: {}", infostring);
        LOGGER.info("namehash: {}", namehash);
        LOGGER.info("types: {}", type);
    }


    public List<File> getAllFiles()
    {

        List<File> files = new ArrayList<File>();
        for (File f : folders)
        {
            if (f.exists() && f.listFiles() != null)
            {
                for (File file : f.listFiles())
                {
                    if (!file.isDirectory())
                    {
                        files.add(file);
                    }
                }
            }
        }

        return files;
    }


    @Test
    @Disabled("Takes to long to run on regular basis")
    void testValues() throws Exception
    { // NOSONAR S2699 — exploratory parser test, observation-only
        List<File> files = findFiles("");
        LOGGER.info("files: {}", files.size());
        Set<Object> fjid = new HashSet<>();
        Set<Object> infostring = new HashSet<>();
        Set<Object> namehash = new HashSet<>();
        for (File file : files)
        {
            LibraryXpt lib = (LibraryXpt) getLibrary(file);
            for (DatasetXpt dataset : lib.getDatasets())
            {
                for (VariableXpt variable : dataset.getVariables())
                {
                    fjid.add(variable.getFormatJustifyId());
                    infostring.add(variable.getInformatTypeString());
                    infostring.add(variable.getFormatTypeString());
                    namehash.add(variable.getNameHash());
                }
            }
        }
        LOGGER.info("format justify ids: {}", fjid);
        LOGGER.info("format string: {}", infostring);
        LOGGER.info("namehash: {}", namehash);
    }


    @Test
    @Disabled("Takes to long to run on regular basis")
    void testLarge() throws Exception
    {
        List<File> files = findFiles("large");
        for (File file : files)
        {
            test(new TestFile(file));
        }
    }


    @Override
    public Library getLibrary(File file) throws IOException
    {
        LOGGER.info("getLibrary: {}", file);
        return parser.parseLibrary(file);
    }


    public List<File> findFiles(String prefix)
    {
        List<File> files = new ArrayList<>();
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
        if (folder2.exists() && folder2.listFiles() != null)
        {
            for (File file : folder2.listFiles())
            {
                if (file.getName().startsWith(prefix))
                {
                    files.add(file);
                }
            }
        }
        return files;
    }


    public File findFile(String prefix)
    {
        if (folder.exists() && folder.listFiles() != null)
        {
            for (File file : folder.listFiles())
            {
                if (file.getName().startsWith(prefix))
                {
                    return file;
                }
            }
        }
        if (folder2.exists() && folder2.listFiles() != null)
        {
            for (File file : folder2.listFiles())
            {
                if (file.getName().startsWith(prefix))
                {
                    return file;
                }
            }
        }
        throw new IllegalArgumentException("File with prefix: " + prefix + " not found");
    }
}
