package net.cumba.cdisc.dsj;

import org.jspecify.annotations.Nullable;

/**
 * Dataset-JSON thousand-separator handling for numbers that arrive as strings.
 *
 * <p>
 * The Dataset-JSON {@code dataType} note says: <i>"When a thousand separator is used in a decimal
 * represented as string, the comma is used."</i> So a comma in such a string is a <b>thousands
 * separator</b> and never a decimal separator, and a comma that is not in a valid grouping position
 * is a <b>format violation</b> — not a value to be guessed at.
 * </p>
 *
 * <p>
 * ⛔⛔ <b>Why this class does NOT implement a number grammar.</b> The obvious design is to validate
 * the whole numeric literal in one pass. That silently <b>regresses</b> inputs the code this class
 * replaces accepts today, because {@link Double#parseDouble(String)} accepts considerably more than
 * a naive grammar admits. Measured 2026-09-21 against the replaced expression
 * {@code Double.parseDouble(s.replace(",", ""))}:
 * </p>
 *
 * <table border="1">
 * <caption>forms a full grammar would have rejected</caption>
 * <tr>
 * <th>input</th>
 * <th>{@code parseDouble} yields</th>
 * <th>why a grammar misses it</th>
 * </tr>
 * <tr>
 * <td>{@code " 1,234.5 "}</td>
 * <td>1234.5</td>
 * <td>{@code parseDouble} trims its input</td>
 * </tr>
 * <tr>
 * <td>{@code "1,234.5d"}</td>
 * <td>1234.5</td>
 * <td>Java's {@code d}/{@code f} suffix is legal</td>
 * </tr>
 * <tr>
 * <td>{@code "0x1p3"}</td>
 * <td>8.0</td>
 * <td>hex floating-point literals are legal</td>
 * </tr>
 * <tr>
 * <td>{@code ".5"}</td>
 * <td>0.5</td>
 * <td>the integer part is optional</td>
 * </tr>
 * </table>
 *
 * <p>
 * ⇒ <b>This class judges the comma context and nothing else</b>, leaving the fraction, the
 * exponent, any suffix and surrounding whitespace to {@code parseDouble} exactly as before. That
 * makes it regression-free by construction: a comma-free string is returned <b>unchanged and by
 * identity</b>, so the caller's {@code parseDouble} sees the very string it sees today.
 * </p>
 *
 * <p>
 * <b>The rule</b>, for a string that contains at least one comma — leading whitespace, then an
 * optional sign, then {@code digit{1,3} ( "," digit{3} )+}, whose first group must not begin with
 * {@code 0}, and no further comma anywhere after the integer part. Everything else is invalid.
 * </p>
 *
 * <p>
 * ⭐ <b>Performance is a requirement here, not an optimisation</b> (owner, 2026-09-20: <i>"not with
 * a regex, but a performance optimized scan"</i>). A wide table is tens of millions of cells, so:
 * no {@code Pattern}, no {@code split}, no {@code replace}, no streams, and <b>no allocation at all
 * on the comma-free path</b> — which is the overwhelmingly common one. The first test is
 * {@link String#indexOf(int)}, an intrinsified vectorised scan, deliberately in preference to a
 * hand-written character loop.
 * </p>
 *
 * <p>
 * ⚠ <b>What this class must NOT grow into.</b> It does not parse the number itself.
 * {@code Double.parseDouble} is correctly rounded; a hand-rolled digit accumulator would introduce
 * rounding, subnormal and overflow errors into exactly the clinical values this class exists to
 * protect — a worse defect than the one it fixes.
 * </p>
 *
 * @see DataTypeMapperFactory the strict ({@code targetDataType=decimal}) caller
 */
public final class ThousandSeparators
{

    private ThousandSeparators()
    {
        // static utility.
    }


    /**
     * Removes valid thousand separators from a numeric string.
     *
     * @param aValue
     *            the raw string, never {@code null}.
     * @return {@code aValue} itself — by identity, with nothing allocated — when it holds no comma;
     *         the comma-free form when every comma is a valid thousands separator; or {@code null}
     *         when any comma is misplaced, which the caller must treat as a format violation.
     */
    public static @Nullable String strip(String aValue)
    {
        // ⭐ THE COMMON PATH. No comma: hand back the very same reference, so the caller's
        // parseDouble sees exactly the string it would have seen before this class existed.
        // ⚠ Returning `aValue` rather than an equal copy is asserted by identity in the tests;
        // it is the no-allocation guarantee, not an incidental detail.
        int firstComma = aValue.indexOf(',');
        if (firstComma < 0)
        {
            return aValue;
        }

        int length = aValue.length();
        int pos = 0;

        // parseDouble trims its input, so a leading blank must not make a valid value invalid.
        // `c <= ' '` is String.trim's own notion of whitespace, which is what parseDouble applies.
        while (pos < length && aValue.charAt(pos) <= ' ')
        {
            pos++;
        }

        // ⭐ THE INVARIANT THAT MAKES THE INDEXING BELOW UNCONDITIONAL, stated because it looks
        // like a missing bounds check and is not: this method is only reached when aValue holds a
        // comma, ',' (0x2C) is greater than ' ' (0x20) so the whitespace loop above cannot advance
        // past it, and a sign is followed by that comma. Hence pos < length holds here and after
        // the digit loop below. ⛔ Do not "restore" a pos < length guard AT THE TWO UNGUARDED
        // READS -- the sign read just below, and the comma test after the digit loop: there it can
        // never be false, so it is unreachable code AND an unkillable CONDITIONALS_BOUNDARY mutant.
        // (Review round 1.)
        // ⚠ The two `while` headers KEEP their pos < length, deliberately: by the same proof those
        // are also never false, but a bound check in a loop header is cheap and is what keeps this
        // method safe if a future caller ever reaches it without the indexOf pre-check. Their
        // pitest survivors are expected, not a regression. (Review round 2 -- the earlier wording
        // read as an absolute rule that this very file breaks twice.)
        //
        // An optional single sign. Both are accepted (owner, 2026-09-21).
        char sign = aValue.charAt(pos);
        if (sign == '+' || sign == '-')
        {
            pos++;
        }

        // The leading group: 1 to 3 digits.
        int groupStart = pos;
        while (pos < length && isAsciiDigit(aValue.charAt(pos)))
        {
            pos++;
        }
        int leadingDigits = pos - groupStart;
        if (leadingDigits < 1 || leadingDigits > 3)
        {
            return null;
        }

        // ⭐ A grouped number does not begin with a zero (owner, 2026-09-21). Without this,
        // "0,123" reads as 123.0 — a value no author can have meant.
        if (aValue.charAt(groupStart) == '0')
        {
            return null;
        }

        // At least one group must follow, or the comma we found is not in the integer part at all
        // (e.g. "1.234,5", where it is a European decimal comma).
        // pos < length by the invariant above, so no bounds test is needed or wanted here.
        if (aValue.charAt(pos) != ',')
        {
            return null;
        }

        // Every following group is exactly three digits. The inner loop consumes ALL consecutive
        // digits on purpose: that is what makes a FOUR-digit group ("1,2345") fail rather than
        // pass as three digits with a stray digit after it.
        int commaCount = 0;
        while (pos < length && aValue.charAt(pos) == ',')
        {
            pos++;
            commaCount++;
            int groupDigits = 0;
            while (pos < length && isAsciiDigit(aValue.charAt(pos)))
            {
                pos++;
                groupDigits++;
            }
            if (groupDigits != 3)
            {
                return null;
            }
        }

        // No comma may appear after the integer part. One clause covers a comma in the fraction
        // ("1,234.5,6") and a comma in the exponent alike.
        if (aValue.indexOf(',', pos) >= 0)
        {
            return null;
        }

        // Valid. Build the stripped form once, sized exactly.
        char[] stripped = new char[length - commaCount];
        int out = 0;
        for (int i = 0; i < length; i++)
        {
            char c = aValue.charAt(i);
            if (c != ',')
            {
                stripped[out++] = c;
            }
        }
        return new String(stripped);
    }


    /**
     * ⚠ ASCII only, deliberately. {@link Character#isDigit(char)} accepts Unicode decimal digits
     * (Arabic-Indic and many others) that {@code Double.parseDouble} rejects, so using it here
     * would admit a grouping the subsequent parse then fails on.
     *
     * @param aChar
     *            the character to test.
     * @return whether it is one of {@code 0}-{@code 9}.
     */
    private static boolean isAsciiDigit(char aChar)
    {
        return aChar >= '0' && aChar <= '9';
    }

}
