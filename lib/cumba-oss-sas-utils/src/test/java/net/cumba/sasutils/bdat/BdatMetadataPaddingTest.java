package net.cumba.sasutils.bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How a metadata text slice that is padded with U+0000 instead of spaces reaches the caller.
 * <p>
 * ⚠ <strong>The defect this pins.</strong> {@link DatasetBdat#getSubHeaderString} right-trimmed
 * with {@code String.stripTrailing()}, which strips whitespace — and U+0000 is not whitespace. SAS
 * writers that pad with NUL therefore delivered {@code "Written by SAS\0\0"} as the dataset label
 * of three fixtures here, and {@code "ids\0"} as one fixture's creator process, all the way into
 * the UI and into every export. Owner ruling Q3, 2026-09-11: <strong>pad with spaces</strong>, so
 * the NULs become spaces and the existing right-trim removes them.
 * <p>
 * ReadStat is the external oracle and agrees for trailing padding: {@code readstat_convert} opens
 * with {@code while (src_len && (src[src_len-1] == ' ' || src[src_len-1] == '\0')) src_len--;} —
 * both characters, off the end of <em>every</em> metadata string it converts.
 * <p>
 * ⭐ The fix is in {@code getSubHeaderString}, the single slicing point every metadata string goes
 * through, so the dataset label's siblings — creator process and software, compression method,
 * column names, labels and formats — are fixed with it rather than left for the next reader to
 * find.
 */
class BdatMetadataPaddingTest
{

    /** The label these three fixtures store as {@code "Written by SAS"} plus two NUL bytes. */
    private static final String WRITTEN_BY_SAS = "Written by SAS";

    private static File fixtureDir()
    {
        return new File(System.getProperty("projectBasedir"),
                "src/test/resources/net/cumba/sasutils/bdat");
    }


    private static DatasetBdat parse(String name) throws IOException
    {
        return new ParserBdat().parseDataset(new File(fixtureDir(), name));
    }


    @ParameterizedTest(name = "{0}")
    @ValueSource(strings =
    {
            "numeric1_15x4733.sas7bdat", "numeric2_59x630.sas7bdat", "time_23x435.sas7bdat"
    })
    void getDataSetLabel_dropsTheNulPadding(String fixture) throws IOException
    {
        assertEquals(Optional.of(WRITTEN_BY_SAS), parse(fixture).getDataSetLabel());
    }


    /**
     * The sibling field, on a real file: without the shared fix this returns {@code "ids\0"}. It is
     * the reason the repair went into {@code getSubHeaderString} and not into
     * {@code getDataSetLabel}.
     */
    @Test
    void getCreatorProcess_dropsTheNulPadding() throws IOException
    {
        assertEquals(Optional.of("ids"), parse("time_23x435.sas7bdat").getCreatorProcess());
    }


    /**
     * Untrimmed, the slice keeps its full width — and the ruling is that the padding it keeps is
     * <em>spaces</em>. This is the only caller-visible difference between "pad with spaces" and
     * ReadStat's "strip both characters", and it is what the ruling asked for.
     */
    @Test
    void getSubHeaderString_untrimmedPadsWithSpacesRatherThanNuls() throws IOException
    {
        DatasetBdat ds = parse("numeric1_15x4733.sas7bdat");
        RowSizeSubHeader rs = ds.rowSizeSubHeader;
        Optional<String> raw = ds.getSubHeaderString(0, rs.getLabelOffset(), rs.getLabelLength(),
                false);
        assertEquals(Optional.of(WRITTEN_BY_SAS + "  "), raw);
    }


    /**
     * The sweep: no metadata string of any bundled fixture may carry a NUL. A fix applied to one
     * accessor and not its siblings is the shape this campaign keeps finding, and a per-fixture
     * assertion would not catch a new fixture arriving with the same padding.
     */
    @Test
    void noMetadataStringOfAnyFixtureCarriesANul() throws IOException
    {
        File[] files = fixtureDir().listFiles((_, n) -> n.endsWith(".sas7bdat"));
        assertNotNull(files, "fixture directory missing");
        assertTrue(files.length >= 20, "expected the bundled fixture set, found " + files.length);
        Arrays.sort(files);

        List<String> offenders = new ArrayList<>();
        for (File f : files)
        {
            DatasetBdat ds = new ParserBdat().parseDataset(f);
            List<String> strings = new ArrayList<>();
            strings.add(ds.getName());
            ds.getDataSetLabel().ifPresent(strings::add);
            ds.getCreatorProcess().ifPresent(strings::add);
            ds.getCreatorSoftware().ifPresent(strings::add);
            ds.getCompression().ifPresent(strings::add);
            for (VariableBdat v : ds.getVariables())
            {
                strings.add(String.valueOf(v.getName()));
                strings.add(String.valueOf(v.getLabel()));
                strings.add(String.valueOf(v.getFormat()));
            }
            for (String s : strings)
            {
                if (s.indexOf('\0') >= 0)
                {
                    offenders.add(f.getName() + ": " + s.replace("\0", "<NUL>"));
                }
            }
        }
        assertEquals(List.of(), offenders, "NUL padding reached a caller");
    }
}
