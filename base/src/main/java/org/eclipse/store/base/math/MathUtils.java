package org.eclipse.store.base.math;

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

public final class MathUtils {

    private static final transient int PERCENT = 100;

    public static boolean isMathematicalInteger(final double value)
    {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && value == Math.rint(value)
                ;
    }


    public static final int log2pow2(final int pow2Value)
    {
        switch(pow2Value)
        {
            case          1: return  0;
            case          2: return  1;
            case          4: return  2;
            case          8: return  3;
            case         16: return  4;
            case         32: return  5;
            case         64: return  6;
            case        128: return  7;
            case        256: return  8;
            case        512: return  9;
            case       1024: return 10;
            case       2048: return 11;
            case       4096: return 12;
            case       8192: return 13;
            case      16384: return 14;
            case      32768: return 15;
            case      65536: return 16;
            case     131072: return 17;
            case     262144: return 18;
            case     524288: return 19;
            case    1048576: return 20;
            case    2097152: return 21;
            case    4194304: return 22;
            case    8388608: return 23;
            case   16777216: return 24;
            case   33554432: return 25;
            case   67108864: return 26;
            case  134217728: return 27;
            case  268435456: return 28;
            case  536870912: return 29;
            case 1073741824: return 30;
            default:
                throw new IllegalArgumentException("Not a power-of-2 value: " + pow2Value);
        }
    }

    /**
     * Determines if the passed value is a power-of-2 value.
     *
     * @param value the value to be tested.
     *
     * @return {@code true} for any n in [0;30] that satisfies {@code value = 2^n}.
     */
    public static final boolean isPow2(final int value)
    {
        // lookup-switch should be faster than binary search with 4-5 ifs (I hope).
        switch(value)
        {
            case          1: return true;
            case          2: return true;
            case          4: return true;
            case          8: return true;
            case         16: return true;
            case         32: return true;
            case         64: return true;
            case        128: return true;
            case        256: return true;
            case        512: return true;
            case       1024: return true;
            case       2048: return true;
            case       4096: return true;
            case       8192: return true;
            case      16384: return true;
            case      32768: return true;
            case      65536: return true;
            case     131072: return true;
            case     262144: return true;
            case     524288: return true;
            case    1048576: return true;
            case    2097152: return true;
            case    4194304: return true;
            case    8388608: return true;
            case   16777216: return true;
            case   33554432: return true;
            case   67108864: return true;
            case  134217728: return true;
            case  268435456: return true;
            case  536870912: return true;
            case 1073741824: return true;
            default        : return false;
        }
    }


    public static final double fractionToPercent(final double decimalFractionValue)
    {
        return decimalFractionValue * PERCENT;
    }

    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Dummy constructor to prevent instantiation of this static-only utility class.
     *
     * @throws UnsupportedOperationException when called
     */
    private MathUtils()
    {
        // static only
        throw new UnsupportedOperationException();
    }
}
