/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0.
 *
 * Changed by P300: repackaged from org.thshsh.sas to net.cumba.sasutils, reduced to a read-only
 * reader, annotated for null-safety, and adapted to this project's build and static-analysis gates.
 * See this module's README.md for the full attribution notice and LICENSE-APACHE-2.0.txt for the
 * licence.
 */
package net.cumba.sasutils.xpt;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import net.cumba.sasutils.Parser;
import net.cumba.sasutils.SasConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thshsh.struct.ByteOrder;
import org.thshsh.struct.Struct;

public class ParserXpt implements Parser
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserXpt.class);

    public static final String STANDARD_EXTENSION = "xpt";

    @Override
    public LibraryXpt parseLibrary(File file) throws IOException
    {
        return parseLibrary(file, true);
    }


    public LibraryXpt parseLibrary(File file, boolean aParseAllTables) throws IOException
    {

        LibraryXpt library = new LibraryXpt(file);

        try (XptInputStream input = new XptInputStream(library.getRandomAccessFileInputStream(),
                80))
        {

            Struct<LibraryHeaderXpt> s = Struct.create(LibraryHeaderXpt.class);
            Struct<DatasetHeaderXpt> dsHeaderStruct = Struct.create(DatasetHeaderXpt.class);

            library.header = s.unpackEntity(input);

            LOGGER.debug("library header: {}", library.header);

            boolean nextMember = false;

            SasConstants.debugBytes(input, 80);

            // skip padding after header
            input.nextPage();

            do
            {

                DatasetHeaderXpt header = dsHeaderStruct.unpackEntity(input);

                LOGGER.debug("dataset header: {}", header);

                DatasetXpt dataset = new DatasetXpt(library, header);

                library.getDatasets().add(dataset);

                if (!header.getDescriptor140())
                {
                    throw new IllegalStateException("using 136");
                }

                Class<? extends VariableXpt> variableClass = header.getDescriptor140()
                        ? VariableXpt140.class
                        : VariableXpt136.class;
                Struct<? extends VariableXpt> variableStruct = Struct.create(variableClass)
                        .byteOrder(ByteOrder.Big);

                for (int i = 0; i < header.getVariableCount(); i++)
                {
                    VariableXpt variable = variableStruct.unpackEntity(input);
                    LOGGER.debug("variable: {}", variable);
                    dataset.getVariables().add(variable);
                }

                LOGGER.debug("position: {}", input.getPosition());
                LOGGER.debug("page position: {}", input.getPagePosition());

                SasConstants.debugBytes(input, 79);

                // skip to the next page if we are not already there
                input.nextPage();

                LOGGER.debug("position after page: {}", input.getPosition());
                LOGGER.debug("searching for observations");

                SasConstants.debugBytes(input, 79);

                // this page should be a header
                if (!input.isHeader())
                {
                    throw new IllegalStateException("Observation header not found");
                }

                // force skip to the next page where observations should be
                input.nextPage(true);

                dataset.observationStartByte = input.getPosition();
                LOGGER.debug("observationStartByte: {}", dataset.observationStartByte);

                // now skip all data pages until we find another header, which implies multiple
                // datasets

                // check for labelheader

                LOGGER.debug("searching for next dataset");

                if (aParseAllTables)
                {
                    // force skip pages until we fund another header
                    nextMember = input.isHeader();
                    while (!nextMember && input.nextPage(true))
                    {
                        nextMember = input.isHeader();
                    }

                    LOGGER.debug("nextMember: {}", nextMember);
                }

            }
            while (nextMember);

            return library;
        }
    }


    public static LocalDateTime parseDateTime(String s)
    {
        return LocalDateTime.from(LibraryXpt.dateTimeFormat().parse(s));

    }

}
