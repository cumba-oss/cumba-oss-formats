package net.cumba.cdisc.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The decision table of {@code plans/PLAN-dsj-thousand-separator.md}, executed.
 *
 * <p>
 * ⚠ Every {@code MIS_ERROR} row of that table is a string {@link ThousandSeparators#strip} must
 * reject by returning {@code null}. The rows are split into two parameterized cases rather than one
 * so a regression names which half moved: <i>accepted-and-stripped</i> versus <i>rejected</i>.
 * </p>
 */
class ThousandSeparatorsTest
{

    // ---- the comma-free path: identity, and therefore no allocation -------------------------

    /**
     * ⭐ The no-allocation guarantee, asserted the only way it can honestly be asserted from a unit
     * test: by reference identity. A comma-free string must come back as the SAME object, so the
     * caller's {@code parseDouble} receives exactly the string it received before this class
     * existed.
     *
     * <p>
     * ⛔ Do not relax this to {@code assertEquals}. An equal-but-copied return would pass such an
     * assertion while allocating on the hot path for every cell of every numeric column — the one
     * property the owner's ruling made a requirement.
     * </p>
     *
     * @param aValue
     *            a numeric string holding no comma.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "1234.5", "0", "-7", "0.5", ".5", "1e10", "0x1p3", "NaN", "Infinity", "-Infinity",
            "1234.5d", " 1234.5 ", ""
    })
    @DisplayName("a string with no comma is returned by identity, allocating nothing")
    void commaFreeStringIsReturnedByIdentity(String aValue)
    {
        assertSame(aValue, ThousandSeparators.strip(aValue),
                "a comma-free string must be returned unchanged AND by identity");
    }

    // ---- valid groupings, stripped ----------------------------------------------------------


    /**
     * @param aValue
     *            the raw string.
     * @param aExpected
     *            the comma-free form {@code parseDouble} must then see.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value =
    {
            // canonical grouping
            "1,234.5     | 1234.5", "1,234,567   | 1234567", "12,345      | 12345",
            // the leading group may be 1-3 digits
            "123,456     | 123456", "1,234       | 1234",
            // many groups, not a wide leading group
            "9,999,999,999 | 9999999999",
            // ⭐ ruled 2026-09-21: an exponent may accompany grouping
            "1,234e5     | 1234e5", "1,234E-5    | 1234E-5",
            // ⭐ ruled 2026-09-21: both signs
            "+1,234      | +1234", "-1,234      | -1234",
            // ⚠ parseDouble's own tolerances must survive the scan untouched
            "1,234.      | 1234.", "1,234.5d    | 1234.5d", "1,234.5f    | 1234.5f",
    })
    @DisplayName("a valid thousands grouping is stripped, and nothing else about the string changes")
    void validGroupingIsStripped(String aValue, String aExpected)
    {
        assertEquals(aExpected, ThousandSeparators.strip(aValue));
    }


    /**
     * ⚠ Whitespace is {@code parseDouble}'s business, not the scanner's, so it must be PRESERVED
     * rather than trimmed — the scanner only removes commas. Kept out of the CSV case above because
     * {@code @CsvSource} trims its cells and would hide exactly this.
     */
    @Test
    @DisplayName("surrounding whitespace is preserved, not trimmed, on the stripped form")
    void surroundingWhitespaceIsPreserved()
    {
        assertEquals(" 1234.5 ", ThousandSeparators.strip(" 1,234.5 "));
        assertEquals("\t-1234\n", ThousandSeparators.strip("\t-1,234\n"));
    }

    // ---- rejections: every one of these is a format violation -------------------------------


    /**
     * @param aValue
     *            a string whose comma is not a valid thousands separator.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "1,5", // group of 1 -- THE DEFECT: reads as 15.0 today
            "1,23", // group of 2
            "1,2345", // group of 4
            // ⭐ a leading group of 4 with an OTHERWISE VALID group after it: the only input
            // that isolates the 1-3 bound, because "1234,5678" fails the GROUP check too and
            // so cannot. The old code read this as 1234567.0. (Review round 1.)
            "1234,567", "1234,5678", // leading group of 4 -- not "a big number with a typo"
            ",5", // leading comma
            // ⭐⭐ ",123" is the ONLY input that isolates the `leadingDigits < 1` arm (review round
            // 2). Every other leading-comma case above dies on a DIFFERENT condition -- ",5" and
            // "," on the group-of-3 test, "abc,def" on the comma test -- so without this row that
            // arm could be deleted and all 51 other cases would still pass, while ",123" silently
            // became 123.0. The plan's own "leading comma" example did not exercise its own rule.
            ",123", "-,123",
            // ⚠ isAsciiDigit is a DELIBERATE choice over Character.isDigit, which accepts Unicode
            // decimal digits that parseDouble rejects. Nothing pinned it (review round 2): with
            // Character.isDigit this strips to "1\u0662\u0663\u0664" instead of rejecting.
            "1,\u0662\u0663\u0664", "1,", // trailing comma
            ",", // comma alone
            "1,,234", // doubled comma
            "1.234,5", // European decimal comma
            "1,234.5,6", // comma after the decimal point
            "1,234e5,6", // comma in the exponent
            "0,123", // ⭐ leading zero in the grouped first group (ruled 2026-09-21)
            "-0,123", // the same, signed
            "0,000", // the same, and it would otherwise read as 0
            "012,345", // a leading zero is a leading zero whatever follows
            "-,5", // sign then no digits
            "+,", // sign then nothing
            "abc,def", // not a number at all
            "1,234,56", // last group of 2
            "1,234,5678", // last group of 4
            ",,", // commas only
            "0x1,234p3", // grouped hex literal: first group is 0
    })
    @DisplayName("a misplaced comma is rejected with null, never repaired and never guessed at")
    void misplacedCommaIsRejected(String aValue)
    {
        assertNull(ThousandSeparators.strip(aValue),
                "a misplaced comma must be reported as invalid, not silently stripped");
    }


    /**
     * ⛔ The regression this whole plan exists to prevent, stated as its own test so it cannot be
     * lost in a parameterized list. {@code "1,5"} read as {@code 15.0} is a TEN-FOLD error in a
     * clinical value with no failure signal at all.
     */
    @Test
    @DisplayName("the ten-fold defect: \"1,5\" is invalid, and must never become 15.0 or 1.5")
    void theTenFoldDefectIsRejected()
    {
        assertNull(ThousandSeparators.strip("1,5"));
        // and the neighbouring valid case still works, so the fix is not a blanket rejection
        assertEquals("1234.5", ThousandSeparators.strip("1,234.5"));
    }

}
