package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The memoized struct byte counts must equal what the structs themselves report.
 *
 * <p>
 * {@code Page#getDataAreaOffset} needs both sizes once per page, on every worker thread of the
 * parallel loader, and {@code Struct.create(...)} allocates a fresh {@code Struct} and token list
 * on each call — so the values are cached. They are only safe to cache because they depend solely
 * on the file's 32-vs-64-bitness, which is fixed once the header is parsed; these tests pin that
 * equivalence so a future change to {@code getStruct} cannot silently invalidate it.
 * </p>
 */
class DatasetBdatStructByteCountTest
{

    private static DatasetBdat parse(String aName) throws IOException
    {
        try (InputStream in = DatasetBdatStructByteCountTest.class
                .getResourceAsStream(aName + ".sas7bdat"))
        {
            assertNotNull(in, "fixture missing: " + aName);
            return new ParserBdat().parseDataset(new ByteArrayInputStream(in.readAllBytes()));
        }
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "doubles_12x25", "mix_data_misc_14x12288", "comp_deleted_4x3", "64_numeric4_11x729"
    })
    void memoizedByteCountsMatchTheStructs(String aFixture) throws IOException
    {
        DatasetBdat dataset = parse(aFixture);

        assertEquals(dataset.getPageHeaderStruct().byteCount(), dataset.getPageHeaderByteCount(),
                "page-header byte count");
        assertEquals(dataset.getSubHeaderPointerStruct().byteCount(),
                dataset.getSubHeaderPointerByteCount(), "subheader-pointer byte count");
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "doubles_12x25", "64_numeric4_11x729"
    })
    void memoizedByteCountsAreStableAcrossCalls(String aFixture) throws IOException
    {
        DatasetBdat dataset = parse(aFixture);

        int firstPage = dataset.getPageHeaderByteCount();
        int firstPointer = dataset.getSubHeaderPointerByteCount();
        assertTrue(firstPage > 0, "page-header byte count must be positive");
        assertTrue(firstPointer > 0, "subheader-pointer byte count must be positive");

        assertEquals(firstPage, dataset.getPageHeaderByteCount());
        assertEquals(firstPointer, dataset.getSubHeaderPointerByteCount());

        // The point of the cache: the Struct itself is rebuilt per call, the byte count is not.
        assertNotSame(dataset.getPageHeaderStruct(), dataset.getPageHeaderStruct(),
                "Struct.create allocates a fresh instance each call — that is what makes the "
                        + "memoized byte count worth having");
    }
}
