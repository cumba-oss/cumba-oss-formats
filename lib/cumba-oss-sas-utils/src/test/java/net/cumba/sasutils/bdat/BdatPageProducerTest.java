package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import net.cumba.sasutils.PositionAwareInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BdatPageProducerTest
{

    private static byte[] load(String aName) throws IOException
    {
        try (InputStream in = BdatPageProducerTest.class.getResourceAsStream(aName + ".sas7bdat"))
        {
            assertNotNull(in, "fixture missing: " + aName);
            return in.readAllBytes();
        }
    }


    private static DatasetBdat parse(byte[] aBytes) throws IOException
    {
        return new ParserBdat().parseDataset(new ByteArrayInputStream(aBytes));
    }


    private static BdatPageProducer producer(DatasetBdat aDataset, byte[] aBytes)
    {
        return new BdatPageProducer(aDataset,
                new PositionAwareInputStream(new ByteArrayInputStream(aBytes)));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "mix_data_misc_14x12288", "mixed4_13x15564", "numeric1_15x4733", "comp_deleted_4x3",
            "doubles_12x25"
    })
    void everyYieldedPageHasObservationsAndPreParsedComeFirst(String aFixture) throws IOException
    {
        byte[] bytes = load(aFixture);
        DatasetBdat dataset = parse(bytes);

        List<Page> preParsedWithObs = new ArrayList<>();
        for (Page p : dataset.getPages())
        {
            if (p.getTotalObservationCount() > 0)
            {
                preParsedWithObs.add(p);
            }
        }

        BdatPageProducer producer = producer(dataset, bytes);
        List<Page> yielded = new ArrayList<>();
        while (producer.hasNext())
        {
            Page page = producer.next();
            assertTrue(page.getTotalObservationCount() > 0,
                    "producer must only yield observation-bearing pages");
            yielded.add(page);
        }

        assertFalse(yielded.isEmpty(), "expected at least one page for " + aFixture);

        // The pre-parsed observation pages must be yielded first, by identity, in order.
        for (int i = 0; i < preParsedWithObs.size(); i++)
        {
            assertEquals(preParsedWithObs.get(i).getStartByte(), yielded.get(i).getStartByte(),
                    "pre-parsed page order mismatch at index " + i);
        }
    }


    @Test
    void exhaustedProducerThrowsNoSuchElement() throws IOException
    {
        byte[] bytes = load("doubles_12x25");
        DatasetBdat dataset = parse(bytes);
        BdatPageProducer producer = producer(dataset, bytes);
        while (producer.hasNext())
        {
            producer.next();
        }
        assertFalse(producer.hasNext());
        assertThrows(NoSuchElementException.class, producer::next);
    }


    @Test
    void decodePageAtDecodesALazyPage() throws IOException
    {
        // numeric1 is multi-page, so there is at least one lazily-loaded page beyond the metadata
        // boundary that decodePageAt must decode directly from its raw buffer.
        byte[] bytes = load("numeric1_15x4733");
        DatasetBdat dataset = parse(bytes);

        long pageSize = dataset.header3.pageSize;
        long lazyIndex = dataset.getMetadataPageCount();
        long start = dataset.header3.headerSize + lazyIndex * pageSize;
        assertTrue(start + pageSize <= bytes.length, "expected a lazy page within the file");

        byte[] pageBuffer = Arrays.copyOfRange(bytes, Math.toIntExact(start),
                Math.toIntExact(start + pageSize));
        Page page = BdatPageProducer.decodePageAt(dataset, pageBuffer, start);

        assertNotNull(page, "decodePageAt should decode a known lazy page");
        assertEquals(start, page.getStartByte());
        assertNotNull(page.getPageType());
    }
}
