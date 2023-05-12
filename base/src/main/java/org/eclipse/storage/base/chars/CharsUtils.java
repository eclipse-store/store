package org.eclipse.storage.base.chars;

/*-
 * #%L
 * Eclipse Store Base utilities
 * %%
 * Copyright (C) 2019 - 2023 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.storage.base.bytes.VarByte;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.exceptions.NumberRangeException;
import org.eclipse.serializer.memory.XMemory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.StringTokenizer;
import java.util.function.Consumer;

public final class CharsUtils {

    static final transient char[] CHARS_MAX_VALUE_long    = Long   .toString(Long.MAX_VALUE)          .toCharArray();

    static final transient char[] CHARS_MAX_VALUE_byte    = Integer.toString(Byte.MAX_VALUE)          .toCharArray();

    static final transient char[] CHARS_MAX_VALUE_short   = Integer.toString(Short.MAX_VALUE)         .toCharArray();

    static final transient char[] CHARS_MAX_VALUE_int     = Integer.toString(Integer.MAX_VALUE)       .toCharArray();

    private static final transient int SIGNLESS_MAX_CHAR_COUNT_long  = CHARS_MAX_VALUE_long .length;

    private static final transient int SIGNLESS_MAX_CHAR_COUNT_byte  = CHARS_MAX_VALUE_byte .length;

    private static final transient int SIGNLESS_MAX_CHAR_COUNT_short = CHARS_MAX_VALUE_short.length;

    private static final transient int SIGNLESS_MAX_CHAR_COUNT_int   = CHARS_MAX_VALUE_int  .length;

    public static final String[] splitSimple(final String s, final String separator)
    {
        if(s.length() > shortStringLength())
        {
            // one-pass processing, but requires the detour of allocating a collection and copying stuff around.
            return splitSimple(s, separator, BulkList.New()).toArray(String.class);
        }

        final StringTokenizer pathTokenizer = new StringTokenizer(s, separator);

        // the StringTokenizer discards leading separators. This is the manual workaround
        final boolean startWithSeparator = s.startsWith(separator);
        final int     swsValue           = startWithSeparator ? 1 : 0;

        // quick token counting (two-pass) for short strings to spare the collection allocation detour.
        final int tokenCount = pathTokenizer.countTokens() + swsValue;
        final String[] pathParts = new String[tokenCount];
        if(startWithSeparator)
        {
            pathParts[0] = "";
        }

        for(int i = swsValue; pathTokenizer.hasMoreTokens(); i++)
        {
            pathParts[i] = pathTokenizer.nextToken();
        }

        return pathParts;
    }

    public static <C extends Consumer<? super String>> C splitSimple(
            final String s        ,
            final String separator,
            final C      collector
    )
    {
        final StringTokenizer pathTokenizer = new StringTokenizer(s, separator);

        // the StringTokenizer discards leading separators. This is the manual workaround
        if(s.startsWith(separator))
        {
            collector.accept("");
        }

        while(pathTokenizer.hasMoreTokens())
        {
            final String token = pathTokenizer.nextToken();
            collector.accept(token);
        }

        return collector;
    }

    /**
     * Arbitrary threshold of 1000 to discriminate "short" strings from "long" strings.<br>
     * The rationale behind that is that "short" strings usually allow for simpler and faster algorithms,
     * which become inefficient on larger strings. For example a two-pass processing of a splitting algorithm.
     * @return 1000
     */
    public static final int shortStringLength()
    {
        return 1000;
    }

    public static final VarString assembleSeparated(
            final VarString       vs       ,
            final char            separator,
            final CharSequence... elements
    )
    {
        if(XArrays.hasNoContent(elements))
        {
            return vs;
        }

        vs.add(elements[0]);
        for(int i = 1; i < elements.length; i++)
        {
            vs.add(separator).add(elements[i]);
        }

        return vs;
    }


    public static final String readStringFromInputStream(final InputStream inputStream, final Charset charset)
            throws IOException
    {
        return readAllBytesFromInputStream(VarByte.New(XMemory.defaultBufferSize()), inputStream).toString(charset);
    }

    public static final VarByte readAllBytesFromInputStream(final VarByte bytes, final InputStream inputStream)
            throws IOException
    {
        final byte[] buffer = new byte[XMemory.defaultBufferSize()];
        for(int bytesRead = -1; (bytesRead = inputStream.read(buffer)) >= 0;)
        {
            bytes.append(buffer, 0, bytesRead);
        }
        return bytes;
    }

    public static String mathRangeIncInc(final long minimum, final long maximum)
    {
        return mathRangeIncInc(
                Long.toString(minimum),
                Long.toString(maximum)
        );
    }

    public static String mathRangeIncInc(final String minimum, final String maximum)
    {
        return "[" + minimum + "; " + maximum + "]";
    }

    public static final VarString assembleNewLinedTabbed(
            final VarString       vs      ,
            final CharSequence... elements
    )
    {
        vs.lf().add(elements[0]);
        for(int i = 1; i < elements.length; i++)
        {
            vs.tab().add(elements[i]);
        }

        return vs;
    }

    /**
     * Two number literals of equal length can efficiently compared to each other by comparing the digits
     * from most significant to less significant place. The first pair of digits determines the result.
     * As every decimal digit has a 90% chance of being differen to another decimal digit when comparing random
     * numbers, this algorithm terminates very quickly in the common case. The worst case (equal value literals) is
     * a usual full equality check to the last digit.
     *
     */
    static final boolean isNumericalLessThan(
            final char[] chars1 ,
            final int    offset1,
            final char[] chars2 ,
            final int    offset2,
            final int    length
    )
    {
        for(int i = 0; i < length; i++)
        {
            if(chars1[offset1 + i] != chars2[offset2 + i])
            {
                // not equal must either be less or greater than
                if(chars1[offset1 + i] < chars2[offset2 + i])
                {
                    return true;
                }

                // greater it is, return false
                return false;
            }
        }

        // completely equal, so not less, hence return false
        return false;
    }

    private static void checkNumberRanges(
            final char[] input          ,
            final int    offset         ,
            final int    length         ,
            final char[] minValueLiteral,
            final char[] maxValueLiteral
    )
    {
        final int maxCharCount         = minValueLiteral.length;
        final int signlessMaxCharCount = maxCharCount - 1;

        // tricky special case: " + 0000000127" is a valid byte literal despite being very long.
        int pos = offset;
        if(length > maxCharCount)
        {
            if(input[pos] == '-' || input[pos] == '+')
            {
                pos++;
            }
            while(input[pos] == '0')
            {
                pos++;
            }
        }

        // oh those special cases :-[
        final int len = length - (pos - offset) - (input[pos] == '-' || input[pos] == '+' ? 1 : 0);

        // if there are more actual value digits than the possible maximum, the literal must be out of range.
        if(len > signlessMaxCharCount)
        {
            throw new NumberRangeException(String.copyValueOf(input, pos, len));
        }

        // if there are less actual value digits than the possible maximum, the literal can't be out of range.
        if(len < signlessMaxCharCount)
        {
            return;
        }

        // if there are as much actual value digits than the possible maximum, the literal must be checked in detail.
        if(input[offset] == '-')
        {
            if(len == signlessMaxCharCount && isNumericalLessThan(minValueLiteral, 1, input, pos, len))
            {
                throw new NumberRangeException(String.copyValueOf(input, pos, len));
            }
            // abort
        }
        else if(input[offset] == '+')
        {
            if(len == signlessMaxCharCount && isNumericalLessThan(maxValueLiteral, 0, input, pos, len))
            {
                throw new NumberRangeException(String.copyValueOf(input, pos, len));
            }
            // abort
        }
        else if(len == signlessMaxCharCount && isNumericalLessThan(maxValueLiteral, 0, input, pos, len))
        {
            throw new NumberRangeException(String.copyValueOf(input, pos, len));
        }
    }

    public static final int to_int(final char digit)
    {
        if(digit < XChars.DIGIT_LOWER_INDEX || digit >= XChars.DIGIT_UPPER_BOUND)
        {
            throw new NumberFormatException(String.valueOf(digit));
        }
        return digit - XChars.DIGIT_LOWER_INDEX;
    }

    /**
     * Special case higher performance implementation of decimal integer literal parsing.
     * Because as usual, the JDK implementation strategies are not acceptable when dealing with non-trivial
     * amounts of data.
     * Properly executed performance tests (large loop sizes, averages, nanosecond precision, etc.) showed
     * that this algorithms is more than twice as fast as the one used in JDK
     * (average of ~33µs vs ~75µs for long literals on same machine with measuring overhead of ~1.5µs)
     *
     * @param input the source char array
     * @param offset the start offset
     * @param length the length
     * @return the parsed long value
     */
    public static final long internalParse_longLiteral(final char[] input, final int offset, final int length)
    {
        // special cased trivial case and invalid single character cases (like letter or sole '+')
        if(length == 1)
        {
            return to_int(input[offset]);
        }

        int i;
        final int bound = (i = offset) + length;

        // handle sign
        final boolean negative;
        if((negative = input[i] == '-') || input[i] == '+')
        {
            /*
             * Special case handling of asymmetric min value
             * Note that the char array comparison is done in only very rare cases and aborts quickly on mismatch.
             */
            if(length == XChars.CHARS_MIN_VALUE_long.length && XChars.uncheckedEquals(input, offset, XChars.CHARS_MIN_VALUE_long, 0, length))
            {
                return Long.MIN_VALUE;
            }
            i++;
        }

        // actual value parsing (quite trivial and efficient if done properly)
        long value = 0;
        while(i < bound)
        {
            value = value * XChars.DECIMAL_BASE + to_int(input[i++]);
        }

        // adjust sign and return resulting value
        return negative ? -value : value;
    }


    public static final long uncheckedParse_longLiteral(final char[] input, final int offset, final int length)
    {
        // lots of special case checking, but only executed for max length literals, so hardly relevant performancewise.
        if(length >= SIGNLESS_MAX_CHAR_COUNT_long)
        {
            checkNumberRanges(input, offset, length, XChars.CHARS_MIN_VALUE_long, CHARS_MAX_VALUE_long);
        }

        try
        {
            return internalParse_longLiteral(input, offset, length);
        }
        catch(final NumberFormatException e)
        {
            // Use Exception with indication of initial value that failed parsing
            throw new NumberFormatException(String.copyValueOf(input, offset, length));
        }
    }

    public static final long parse_longDecimal(final char[] input, final int offset, final int length)
    {
        XChars.validateRange(input, offset, length);
        return uncheckedParse_longLiteral(input, offset, length);
    }

    public static final byte uncheckedParse_byteDecimal(final char[] input, final int offset, final int length)
    {
        // lots of special case checking, but only executed for max length literals, so hardly relevant performancewise.
        if(length >= SIGNLESS_MAX_CHAR_COUNT_byte)
        {
            checkNumberRanges(input, offset, length, XChars.CHARS_MIN_VALUE_byte, CHARS_MAX_VALUE_byte);
        }

        try
        {
            // checks above guarantee that the parsed long value is in range
            return (byte)internalParse_longLiteral(input, offset, length);
        }
        catch(final NumberFormatException e)
        {
            // Use Exception with indication of initial value that failed parsing
            throw new NumberFormatException(String.copyValueOf(input, offset, length));
        }
    }

    public static final byte parse_byteDecimal(final char[] input, final int offset, final int length)
    {
        XChars.validateRange(input, offset, length);
        return uncheckedParse_byteDecimal(input, offset, length);
    }

    public static final boolean equals(
            final char[] chars1 ,
            final int    offset1,
            final char[] chars2 ,
            final int    offset2,
            final int    length
    )
    {
        XChars.validateRange(chars1, offset1, length);
        XChars.validateRange(chars2, offset2, length);
        return XChars.uncheckedEquals(chars1, offset1, chars2, offset2, length);
    }

    public static final short uncheckedParse_shortDecimal(final char[] input, final int offset, final int length)
    {
        // lots of special case checking, but only executed for max length literals, so hardly relevant performancewise.
        if(length >= SIGNLESS_MAX_CHAR_COUNT_short)
        {
            checkNumberRanges(input, offset, length, XChars.CHARS_MIN_VALUE_short, CHARS_MAX_VALUE_short);
        }

        try
        {
            // checks above guarantee that the parsed long value is in range
            return (short)internalParse_longLiteral(input, offset, length);
        }
        catch(final NumberFormatException e)
        {
            // Use Exception with indication of initial value that failed parsing
            throw new NumberFormatException(String.copyValueOf(input, offset, length));
        }
    }

    public static final short parse_shortDecimal(final char[] input, final int offset, final int length)
    {
        XChars.validateRange(input, offset, length);
        return uncheckedParse_shortDecimal(input, offset, length);
    }

    public static final int uncheckedParse_intLiteral(final char[] input, final int offset, final int length)
    {
        // lots of special case checking, but only executed for max length literals, so hardly relevant performancewise.
        if(length >= SIGNLESS_MAX_CHAR_COUNT_int)
        {
            checkNumberRanges(input, offset, length, XChars.CHARS_MIN_VALUE_int, CHARS_MAX_VALUE_int);
        }

        try
        {
            // checks above guarantee that the parsed long value is in range
            return (int)internalParse_longLiteral(input, offset, length);
        }
        catch(final NumberFormatException e)
        {
            // Use Exception with indication of initial value that failed parsing
            throw new NumberFormatException(String.copyValueOf(input, offset, length));
        }
    }

    public static final int parse_intLiteral(final char[] input, final int offset, final int length)
    {
        XChars.validateRange(input, offset, length);
        return uncheckedParse_intLiteral(input, offset, length);
    }

    public static final float parse_float(final char[] input, final int offset, final int length)
    {
        XChars.validateRange(input, offset, length);

        // (12.10.2014 TM)TODO: implement efficient float parser
        return Float.parseFloat(String.valueOf(input, offset, length));
    }

    public static final double parse_double(final char[] input, final int offset, final int length)
    {
        XChars.validateRange(input, offset, length);

        // (12.10.2014 TM)TODO: implement efficient double parser
        return Double.parseDouble(String.valueOf(input, offset, length));
    }

    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private CharsUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
